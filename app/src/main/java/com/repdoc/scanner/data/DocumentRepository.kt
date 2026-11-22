package com.repdoc.scanner.data

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class DocumentRepository(context: Context) {

    private val root = File(context.filesDir, "documents").apply { mkdirs() }
    private val _documents = MutableStateFlow<List<Document>>(emptyList())
    val documents: StateFlow<List<Document>> = _documents.asStateFlow()

    init {
        reload()
    }

    @Synchronized
    fun reload() {
        val docs = root.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull(::readDocument)
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()
        _documents.value = docs
    }

    @Synchronized
    fun createDocument(name: String = defaultName()): Document {
        val id = UUID.randomUUID().toString()
        val dir = File(root, id).apply { mkdirs() }
        val now = System.currentTimeMillis()
        val doc = Document(id, name, now, now, emptyList())
        writeDocument(dir, doc)
        reload()
        return doc
    }

    @Synchronized
    fun addPage(documentId: String, bitmap: Bitmap): Document? {
        val dir = File(root, documentId)
        val current = readDocument(dir) ?: return null
        val pageId = UUID.randomUUID().toString()
        val imageFile = File(dir, "page_$pageId.jpg")
        FileOutputStream(imageFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        val page = Page(pageId, imageFile.absolutePath)
        val updated = current.copy(
            pages = current.pages + page,
            updatedAt = System.currentTimeMillis()
        )
        writeDocument(dir, updated)
        reload()
        return updated
    }

    @Synchronized
    fun updatePageOcr(documentId: String, pageId: String, text: String): Document? {
        val dir = File(root, documentId)
        val current = readDocument(dir) ?: return null
        val updated = current.copy(
            pages = current.pages.map { page ->
                if (page.id == pageId) page.copy(ocrText = text) else page
            },
            updatedAt = System.currentTimeMillis()
        )
        writeDocument(dir, updated)
        reload()
        return updated
    }

    @Synchronized
    fun renameDocument(documentId: String, name: String): Document? {
        val dir = File(root, documentId)
        val current = readDocument(dir) ?: return null
        val updated = current.copy(name = name.trim().ifEmpty { current.name }, updatedAt = System.currentTimeMillis())
        writeDocument(dir, updated)
        reload()
        return updated
    }

    @Synchronized
    fun deleteDocument(documentId: String) {
        File(root, documentId).deleteRecursively()
        reload()
    }

    fun document(documentId: String): Document? = _documents.value.firstOrNull { it.id == documentId }

    private fun defaultName(): String =
        "Scan " + SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.US).format(Date())

    private fun readDocument(dir: File): Document? {
        val meta = File(dir, "meta.json")
        if (!meta.isFile) return null
        return runCatching {
            val json = JSONObject(meta.readText())
            val pagesJson = json.optJSONArray("pages") ?: JSONArray()
            val pages = buildList {
                for (i in 0 until pagesJson.length()) {
                    val p = pagesJson.getJSONObject(i)
                    val image = p.optString("image")
                    if (image.isNotBlank()) {
                        add(
                            Page(
                                id = p.getString("id"),
                                imagePath = File(dir, image).absolutePath,
                                ocrText = p.optString("ocr").takeIf { it.isNotBlank() }
                            )
                        )
                    }
                }
            }
            Document(
                id = json.getString("id"),
                name = json.optString("name", "Document"),
                createdAt = json.optLong("createdAt", dir.lastModified()),
                updatedAt = json.optLong("updatedAt", dir.lastModified()),
                pages = pages
            )
        }.getOrNull()
    }

    private fun writeDocument(dir: File, doc: Document) {
        val pages = JSONArray()
        doc.pages.forEach { page ->
            pages.put(
                JSONObject()
                    .put("id", page.id)
                    .put("image", File(page.imagePath).name)
                    .put("ocr", page.ocrText ?: "")
            )
        }
        val json = JSONObject()
            .put("id", doc.id)
            .put("name", doc.name)
            .put("createdAt", doc.createdAt)
            .put("updatedAt", doc.updatedAt)
            .put("pages", pages)
        File(dir, "meta.json").writeText(json.toString())
    }
}
