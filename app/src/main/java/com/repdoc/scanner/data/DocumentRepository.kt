package com.repdoc.scanner.data

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class DocumentRepository(context: Context) {

    private val root = File(context.filesDir, "documents").apply { mkdirs() }
    private val foldersFile = File(context.filesDir, "folders.json")
    private val mutex = Mutex()
    private val _documents = MutableStateFlow<List<Document>>(emptyList())
    val documents: StateFlow<List<Document>> = _documents.asStateFlow()
    private val _folders = MutableStateFlow<List<Folder>>(emptyList())
    val folders: StateFlow<List<Folder>> = _folders.asStateFlow()

    suspend fun reload() = withContext(Dispatchers.IO) {
        mutex.withLock { reloadLocked() }
    }

    suspend fun reloadFolders() = withContext(Dispatchers.IO) {
        mutex.withLock { reloadFoldersLocked() }
    }

    suspend fun createDocument(name: String = defaultName(), folderId: String? = null): Document =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val id = UUID.randomUUID().toString()
                val dir = File(root, id).apply { mkdirs() }
                val now = System.currentTimeMillis()
                val doc = Document(id, name, now, now, emptyList(), folderId)
                writeDocument(dir, doc)
                reloadLocked()
                doc
            }
        }

    suspend fun createFolder(name: String): Folder = withContext(Dispatchers.IO) {
        mutex.withLock {
            val folder = Folder(
                UUID.randomUUID().toString(),
                name.trim().ifBlank { "Folder" },
                System.currentTimeMillis()
            )
            _folders.value = (_folders.value + folder).sortedBy { it.name.lowercase(Locale.US) }
            writeFolders(_folders.value)
            folder
        }
    }

    suspend fun renameFolder(folderId: String, name: String): Folder? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val folder = _folders.value.firstOrNull { it.id == folderId } ?: return@withLock null
            val updated = folder.copy(name = name.trim().ifEmpty { folder.name })
            _folders.value = _folders.value.map { if (it.id == folderId) updated else it }
            writeFolders(_folders.value)
            updated
        }
    }

    suspend fun deleteFolder(folderId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
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
            reloadLocked()
        }
    }

    suspend fun moveDocumentToFolder(documentId: String, folderId: String?): Document? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val dir = File(root, documentId)
                val current = readDocument(dir) ?: return@withLock null
                val updated = current.copy(
                    folderId = folderId,
                    updatedAt = System.currentTimeMillis()
                )
                writeDocument(dir, updated)
                reloadLocked()
                updated
            }
        }

    suspend fun addPage(
        documentId: String,
        bitmap: Bitmap,
        original: Bitmap = bitmap,
        filterName: String? = null
    ): Document? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val dir = File(root, documentId)
            val current = readDocument(dir) ?: return@withLock null
            val pageId = UUID.randomUUID().toString()
            val imageFile = File(dir, "page_$pageId.jpg")
            writeJpeg(bitmap, imageFile)
            val originalFile = File(dir, "page_${pageId}_original.jpg")
            try {
                writeJpeg(original, originalFile)
            } catch (error: Throwable) {
                imageFile.delete()
                originalFile.delete()
                throw error
            }
            val page = Page(
                pageId,
                imageFile.absolutePath,
                originalImagePath = originalFile.absolutePath,
                filterName = filterName
            )
            val updated = current.copy(
                pages = current.pages + page,
                updatedAt = System.currentTimeMillis()
            )
            writeDocument(dir, updated)
            reloadLocked()
            updated
        }
    }

    suspend fun replacePageImage(
        documentId: String,
        pageId: String,
        bitmap: Bitmap,
        original: Bitmap? = null,
        updateOriginal: Boolean = true,
        filterName: String? = null,
        updateFilter: Boolean = false
    ): Document? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val dir = File(root, documentId)
            val current = readDocument(dir) ?: return@withLock null
            val oldPage = current.pages.firstOrNull { it.id == pageId } ?: return@withLock null
            val imageFile = File(dir, "page_${pageId}_${System.currentTimeMillis()}.jpg")
            writeJpeg(bitmap, imageFile)
            var originalPath = oldPage.originalImagePath?.takeIf { File(it).isFile }
            if (updateOriginal) {
                val source = original ?: bitmap
                val originalFile = File(dir, "page_${pageId}_original_${System.currentTimeMillis()}.jpg")
                writeJpeg(source, originalFile)
                if (originalPath != null && originalPath != originalFile.absolutePath) {
                    runCatching { File(originalPath).delete() }
                }
                originalPath = originalFile.absolutePath
            } else if (originalPath == null) {
                val originalFile = File(dir, "page_${pageId}_original_${System.currentTimeMillis()}.jpg")
                if (copyFileAtomic(File(oldPage.imagePath), originalFile)) {
                    originalPath = originalFile.absolutePath
                }
            }
            val updated = current.copy(
                pages = current.pages.map { page ->
                    if (page.id == pageId) {
                        page.copy(
                            imagePath = imageFile.absolutePath,
                            originalImagePath = originalPath,
                            filterName = if (updateFilter) filterName else page.filterName
                        )
                    } else {
                        page
                    }
                },
                updatedAt = System.currentTimeMillis()
            )
            writeDocument(dir, updated)
            reloadLocked()
            if (oldPage.imagePath != imageFile.absolutePath) {
                runCatching { File(oldPage.imagePath).delete() }
            }
            updated
        }
    }

    suspend fun deletePage(documentId: String, pageId: String): Document? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val dir = File(root, documentId)
                val current = readDocument(dir) ?: return@withLock null
                val page = current.pages.firstOrNull { it.id == pageId } ?: return@withLock null
                val updated = current.copy(
                    pages = current.pages.filterNot { it.id == pageId },
                    updatedAt = System.currentTimeMillis()
                )
                writeDocument(dir, updated)
                runCatching { File(page.imagePath).delete() }
                page.originalImagePath?.let { runCatching { File(it).delete() } }
                reloadLocked()
                updated
            }
        }

    suspend fun movePage(documentId: String, pageId: String, toIndex: Int): Document? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val dir = File(root, documentId)
                val current = readDocument(dir) ?: return@withLock null
                val pages = current.pages.toMutableList()
                val fromIndex = pages.indexOfFirst { it.id == pageId }
                if (fromIndex < 0) return@withLock null
                val page = pages.removeAt(fromIndex)
                pages.add(toIndex.coerceIn(0, pages.size), page)
                val updated = current.copy(pages = pages, updatedAt = System.currentTimeMillis())
                writeDocument(dir, updated)
                reloadLocked()
                updated
            }
        }

    suspend fun duplicatePage(documentId: String, pageId: String): Document? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val dir = File(root, documentId)
                val current = readDocument(dir) ?: return@withLock null
                val index = current.pages.indexOfFirst { it.id == pageId }
                if (index < 0) return@withLock null
                val source = current.pages[index]
                val newId = UUID.randomUUID().toString()
                val newFile = File(dir, "page_$newId.jpg")
                if (!copyFileAtomic(File(source.imagePath), newFile)) return@withLock null
                val newOriginal = File(dir, "page_${newId}_original.jpg")
                source.originalImagePath?.let { path ->
                    copyFileAtomic(File(path), newOriginal)
                }
                val copy = source.copy(
                    id = newId,
                    imagePath = newFile.absolutePath,
                    originalImagePath = newOriginal.takeIf { it.isFile }?.absolutePath
                )
                val pages = current.pages.toMutableList().apply { add(index + 1, copy) }
                val updated = current.copy(pages = pages, updatedAt = System.currentTimeMillis())
                writeDocument(dir, updated)
                reloadLocked()
                updated
            }
        }

    suspend fun updatePageOcr(documentId: String, pageId: String, text: String): Document? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val dir = File(root, documentId)
                val current = readDocument(dir) ?: return@withLock null
                val updated = current.copy(
                    pages = current.pages.map { page ->
                        if (page.id == pageId) page.copy(ocrText = text) else page
                    },
                    updatedAt = System.currentTimeMillis()
                )
                writeDocument(dir, updated)
                reloadLocked()
                updated
            }
        }

    suspend fun renameDocument(documentId: String, name: String): Document? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val dir = File(root, documentId)
                val current = readDocument(dir) ?: return@withLock null
                val updated = current.copy(
                    name = name.trim().ifEmpty { current.name },
                    updatedAt = System.currentTimeMillis()
                )
                writeDocument(dir, updated)
                reloadLocked()
                updated
            }
        }

    suspend fun deleteDocument(documentId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            File(root, documentId).deleteRecursively()
            reloadLocked()
        }
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

    private fun reloadLocked() {
        val docs = root.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull(::readDocument)
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()
        _documents.value = docs
    }

    private fun reloadFoldersLocked() {
        _folders.value = readFolders()
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
        writeTextAtomic(foldersFile, array.toString())
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
                                ocrText = p.optString("ocr").takeIf { it.isNotBlank() },
                                originalImagePath = p.optString("original")
                                    .takeIf { it.isNotBlank() }
                                    ?.let { File(dir, it).absolutePath },
                                filterName = p.optString("filter").takeIf { it.isNotBlank() }
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
                    .put("original", page.originalImagePath?.let { File(it).name } ?: "")
                    .put("filter", page.filterName ?: "")
            )
        }
        val json = JSONObject()
            .put("id", doc.id)
            .put("name", doc.name)
            .put("createdAt", doc.createdAt)
            .put("updatedAt", doc.updatedAt)
            .put("folderId", doc.folderId ?: "")
            .put("pages", pages)
        writeTextAtomic(File(dir, "meta.json"), json.toString())
    }

    private fun writeTextAtomic(file: File, text: String) {
        val parent = file.parentFile ?: throw IOException("No parent directory for ${file.name}")
        parent.mkdirs()
        val temp = File(parent, "${file.name}.tmp")
        try {
            FileOutputStream(temp).use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
                out.fd.sync()
            }
            moveAtomic(temp, file)
        } catch (error: Throwable) {
            temp.delete()
            throw error
        }
    }

    private fun writeJpeg(bitmap: Bitmap, file: File) {
        val parent = file.parentFile ?: throw IOException("No parent directory for ${file.name}")
        parent.mkdirs()
        val temp = File(parent, "${file.name}.tmp")
        try {
            FileOutputStream(temp).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)) {
                    throw IOException("Failed to encode JPEG: ${file.name}")
                }
                out.fd.sync()
            }
            moveAtomic(temp, file)
        } catch (error: Throwable) {
            temp.delete()
            throw error
        }
    }

    private fun copyFileAtomic(source: File, target: File): Boolean {
        val parent = target.parentFile ?: return false
        parent.mkdirs()
        val temp = File(parent, "${target.name}.tmp")
        return try {
            source.inputStream().use { input ->
                FileOutputStream(temp).use { out ->
                    input.copyTo(out)
                    out.fd.sync()
                }
            }
            moveAtomic(temp, target)
            true
        } catch (_: Throwable) {
            temp.delete()
            false
        }
    }

    private fun moveAtomic(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Throwable) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
