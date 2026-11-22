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
    private val foldersFile = File(context.filesDir, "folders.json")
    private val _documents = MutableStateFlow<List<Document>>(emptyList())
    val documents: StateFlow<List<Document>> = _documents.asStateFlow()
    private val _folders = MutableStateFlow<List<Folder>>(emptyList())
    val folders: StateFlow<List<Folder>> = _folders.asStateFlow()

    init {
        reloadFolders()
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
    fun reloadFolders() {
        _folders.value = readFolders()
    }

    @Synchronized
    fun createDocument(name: String = defaultName(), folderId: String? = null): Document {
        val id = UUID.randomUUID().toString()
        val dir = File(root, id).apply { mkdirs() }
        val now = System.currentTimeMillis()
        val doc = Document(id, name, now, now, emptyList(), folderId)
        writeDocument(dir, doc)
        reload()
        return doc
    }

    @Synchronized
    fun createFolder(name: String): Folder {
        val folder = Folder(UUID.randomUUID().toString(), name.trim().ifBlank { "Folder" }, System.currentTimeMillis())
        _folders.value = (_folders.value + folder).sortedBy { it.name.lowercase(Locale.US) }
        writeFolders(_folders.value)
        return folder
    }

    @Synchronized
    fun renameFolder(folderId: String, name: String): Folder? {
        val folder = _folders.value.firstOrNull { it.id == folderId } ?: return null
        val updated = folder.copy(name = name.trim().ifEmpty { folder.name })
        _folders.value = _folders.value.map { if (it.id == folderId) updated else it }
        writeFolders(_folders.value)
        return updated
    }

    @Synchronized
    fun deleteFolder(folderId: String) {
        root.listFiles()
            ?.filter { it.isDirectory }
            ?.forEach { dir ->
                val doc = readDocument(dir) ?: return@forEach
                if (doc.folderId == folderId) {
                    writeDocument(dir, doc.copy(folderId = null))
                }
            }
        _folders.value = _folders.value.filterNot { it.id == folderId }
        writeFolders(_folders.value)
        reload()
    }

    @Synchronized
    fun moveDocumentToFolder(documentId: String, folderId: String?): Document? {
        val dir = File(root, documentId)
        val current = readDocument(dir) ?: return null
        val updated = current.copy(
            folderId = folderId,
            updatedAt = System.currentTimeMillis()
        )
        writeDocument(dir, updated)
        reload()
        return updated
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
    fun replacePageImage(documentId: String, pageId: String, bitmap: Bitmap): Document? {
        val dir = File(root, documentId)
        val current = readDocument(dir) ?: return null
        val oldPage = current.pages.firstOrNull { it.id == pageId } ?: return null
        val imageFile = File(dir, "page_${pageId}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(imageFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        val updated = current.copy(
            pages = current.pages.map { page ->
                if (page.id == pageId) page.copy(imagePath = imageFile.absolutePath) else page
            },
            updatedAt = System.currentTimeMillis()
        )
        writeDocument(dir, updated)
        reload()
        if (oldPage.imagePath != imageFile.absolutePath) {
            runCatching { File(oldPage.imagePath).delete() }
        }
        return updated
    }

    @Synchronized
    fun deletePage(documentId: String, pageId: String): Document? {
        val dir = File(root, documentId)
        val current = readDocument(dir) ?: return null
        val page = current.pages.firstOrNull { it.id == pageId } ?: return null
        val updated = current.copy(
            pages = current.pages.filterNot { it.id == pageId },
            updatedAt = System.currentTimeMillis()
        )
        writeDocument(dir, updated)
        runCatching { File(page.imagePath).delete() }
        reload()
        return updated
    }

    @Synchronized
    fun movePage(documentId: String, pageId: String, toIndex: Int): Document? {
        val dir = File(root, documentId)
        val current = readDocument(dir) ?: return null
        val pages = current.pages.toMutableList()
        val fromIndex = pages.indexOfFirst { it.id == pageId }
        if (fromIndex < 0) return null
        val page = pages.removeAt(fromIndex)
        pages.add(toIndex.coerceIn(0, pages.size), page)
        val updated = current.copy(pages = pages, updatedAt = System.currentTimeMillis())
        writeDocument(dir, updated)
        reload()
        return updated
    }

    @Synchronized
    fun duplicatePage(documentId: String, pageId: String): Document? {
        val dir = File(root, documentId)
        val current = readDocument(dir) ?: return null
        val index = current.pages.indexOfFirst { it.id == pageId }
        if (index < 0) return null
        val source = current.pages[index]
        val newId = UUID.randomUUID().toString()
        val newFile = File(dir, "page_$newId.jpg")
        if (!runCatching { File(source.imagePath).copyTo(newFile, overwrite = true) }.isSuccess) return null
        val copy = source.copy(id = newId, imagePath = newFile.absolutePath)
        val pages = current.pages.toMutableList().apply { add(index + 1, copy) }
        val updated = current.copy(pages = pages, updatedAt = System.currentTimeMillis())
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

    fun search(query: String, folderId: String? = null): List<SearchHit> {
        val term = query.trim()
        if (term.isEmpty()) return emptyList()
        val source = _documents.value.filter { folderId == null || it.folderId == folderId }
        val hits = ArrayList<SearchHit>()
        source.forEach { doc ->
            val pageMatch = doc.pages.firstOrNull { page ->
                page.ocrText?.contains(term, ignoreCase = true) == true
            }
            val nameMatch = doc.name.contains(term, ignoreCase = true)
            if (nameMatch || pageMatch != null) {
                val snippet = pageMatch?.ocrText
                    ?.let { text -> snippetAround(text, term) }
                    .orEmpty()
                hits.add(SearchHit(doc, pageMatch, snippet))
            }
        }
        return hits
    }

    private fun snippetAround(text: String, term: String): String {
        val index = text.indexOf(term, ignoreCase = true).coerceAtLeast(0)
        val start = (index - 48).coerceAtLeast(0)
        val end = (index + term.length + 96).coerceAtMost(text.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < text.length) "…" else ""
        return prefix + text.substring(start, end).replace(Regex("\\s+"), " ").trim() + suffix
    }

    private fun defaultName(): String =
        "Scan " + SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.US).format(Date())

    private fun readFolders(): List<Folder> {
        if (!foldersFile.isFile) return emptyList()
        return runCatching {
            val array = JSONArray(foldersFile.readText())
            buildList {
                for (i in 0 until array.length()) {
                    val json = array.getJSONObject(i)
                    add(
                        Folder(
                            id = json.getString("id"),
                            name = json.optString("name", "Folder"),
                            createdAt = json.optLong("createdAt", 0L)
                        )
                    )
                }
            }.sortedBy { it.name.lowercase(Locale.US) }
        }.getOrDefault(emptyList())
    }

    private fun writeFolders(folders: List<Folder>) {
        val array = JSONArray()
        folders.forEach { folder ->
            array.put(
                JSONObject()
                    .put("id", folder.id)
                    .put("name", folder.name)
                    .put("createdAt", folder.createdAt)
            )
        }
        foldersFile.writeText(array.toString())
    }

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
                pages = pages,
                folderId = json.optString("folderId").takeIf { it.isNotBlank() }
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
            .put("folderId", doc.folderId ?: "")
            .put("pages", pages)
        File(dir, "meta.json").writeText(json.toString())
    }
}
