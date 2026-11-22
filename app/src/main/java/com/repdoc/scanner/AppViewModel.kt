package com.repdoc.scanner

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.repdoc.scanner.data.Document
import com.repdoc.scanner.data.DocumentRepository
import com.repdoc.scanner.data.Folder
import com.repdoc.scanner.data.SearchHit
import com.repdoc.scanner.util.DocumentFilter
import com.repdoc.scanner.util.ImageProcessor
import com.repdoc.scanner.util.NativeFilters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DocumentRepository(application)
    val documents: StateFlow<List<Document>> = repository.documents
    val folders: StateFlow<List<Folder>> = repository.folders

    var pendingBitmap by mutableStateOf<Bitmap?>(null)
    var pendingBitmaps by mutableStateOf<List<Bitmap>>(emptyList())
    var pendingIndex by mutableStateOf(0)
    var activeDocumentId by mutableStateOf<String?>(null)
    var selectedFilter by mutableStateOf(DocumentFilter.MAGIC_COLOR)
    var autoSave by mutableStateOf(
        application.getSharedPreferences("repdoc_settings", Context.MODE_PRIVATE)
            .getBoolean("auto_save", true)
    )
    var editingDocumentId by mutableStateOf<String?>(null)
    var editingPageId by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)

    init {
        NativeFilters.initialize(application)
    }

    fun document(id: String): Document? = repository.document(id)

    fun updateAutoSave(enabled: Boolean) {
        autoSave = enabled
        getApplication<Application>()
            .getSharedPreferences("repdoc_settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("auto_save", enabled)
            .apply()
    }

    fun reload() = repository.reload()

    fun createDocument(): Document {
        val doc = repository.createDocument()
        activeDocumentId = doc.id
        return doc
    }

    fun savePage(bitmap: Bitmap, filter: DocumentFilter, onSaved: (Document) -> Unit) {
        viewModelScope.launch {
            busy = true
            val filtered = withContext(Dispatchers.Default) {
                NativeFilters.apply(bitmap, filter)
            }
            val editDocumentId = editingDocumentId
            val editPageId = editingPageId
            val updated = if (editDocumentId != null && editPageId != null) {
                repository.replacePageImage(editDocumentId, editPageId, filtered)
            } else {
                val id = activeDocumentId ?: createDocument().id
                repository.addPage(id, filtered)
            }
            editingDocumentId = null
            editingPageId = null
            busy = false
            if (updated != null) {
                onSaved(updated)
            }
        }
    }

    fun startEditingPage(documentId: String, pageId: String, bitmap: Bitmap) {
        activeDocumentId = documentId
        editingDocumentId = documentId
        editingPageId = pageId
        pendingBitmap = bitmap
        pendingBitmaps = emptyList()
        pendingIndex = 0
        selectedFilter = DocumentFilter.MAGIC_COLOR
    }

    fun startImport(bitmaps: List<Bitmap>) {
        if (bitmaps.isEmpty()) return
        pendingBitmaps = bitmaps
        pendingIndex = 0
        pendingBitmap = bitmaps.first()
        selectedFilter = DocumentFilter.MAGIC_COLOR
        editingDocumentId = null
        editingPageId = null
    }

    fun advancePending(): Bitmap? {
        if (pendingIndex + 1 < pendingBitmaps.size) {
            pendingIndex += 1
            pendingBitmap = pendingBitmaps[pendingIndex]
            selectedFilter = DocumentFilter.MAGIC_COLOR
            return pendingBitmap
        }
        pendingBitmaps = emptyList()
        pendingIndex = 0
        pendingBitmap = null
        return null
    }

    fun beginNewPage() {
        pendingBitmap = null
        pendingBitmaps = emptyList()
        pendingIndex = 0
        selectedFilter = DocumentFilter.MAGIC_COLOR
        editingDocumentId = null
        editingPageId = null
    }

    fun addImportedPage(bitmap: Bitmap, filter: DocumentFilter, onSaved: (Document) -> Unit) {
        savePage(bitmap, filter, onSaved)
    }

    fun rename(id: String, name: String) {
        repository.renameDocument(id, name)
    }

    fun delete(id: String) {
        repository.deleteDocument(id)
        if (activeDocumentId == id) activeDocumentId = null
    }

    fun updateOcr(id: String, pageId: String, text: String) {
        repository.updatePageOcr(id, pageId, text)
    }

    fun deletePage(documentId: String, pageId: String) {
        repository.deletePage(documentId, pageId)
    }

    fun movePage(documentId: String, pageId: String, toIndex: Int) {
        repository.movePage(documentId, pageId, toIndex)
    }

    fun duplicatePage(documentId: String, pageId: String) {
        repository.duplicatePage(documentId, pageId)
    }

    fun applyFilterToPage(documentId: String, pageId: String, filter: DocumentFilter) {
        viewModelScope.launch {
            busy = true
            val page = repository.document(documentId)?.pages?.firstOrNull { it.id == pageId }
            if (page != null) {
                val bitmap = withContext(Dispatchers.IO) {
                    ImageProcessor.decodeFile(page.imagePath)
                }
                if (bitmap != null) {
                    val filtered = withContext(Dispatchers.Default) {
                        NativeFilters.apply(bitmap, filter)
                    }
                    repository.replacePageImage(documentId, pageId, filtered)
                }
            }
            busy = false
        }
    }

    fun replacePageImage(documentId: String, pageId: String, bitmap: Bitmap) {
        viewModelScope.launch {
            busy = true
            repository.replacePageImage(documentId, pageId, bitmap)
            busy = false
        }
    }

    fun rotatePage(documentId: String, pageId: String, degrees: Int = 90) {
        viewModelScope.launch {
            busy = true
            val page = repository.document(documentId)?.pages?.firstOrNull { it.id == pageId }
            if (page != null) {
                val bitmap = withContext(Dispatchers.IO) {
                    ImageProcessor.decodeFile(page.imagePath)
                }
                if (bitmap != null) {
                    val rotated = withContext(Dispatchers.Default) {
                        ImageProcessor.rotate(bitmap, degrees)
                    }
                    repository.replacePageImage(documentId, pageId, rotated)
                }
            }
            busy = false
        }
    }

    fun createFolder(name: String) = repository.createFolder(name)

    fun renameFolder(folderId: String, name: String) = repository.renameFolder(folderId, name)

    fun deleteFolder(folderId: String) = repository.deleteFolder(folderId)

    fun moveDocumentToFolder(documentId: String, folderId: String?) {
        repository.moveDocumentToFolder(documentId, folderId)
    }

    fun search(query: String, folderId: String? = null): List<SearchHit> =
        repository.search(query, folderId)

    fun resetPending() {
        pendingBitmap = null
        pendingBitmaps = emptyList()
        pendingIndex = 0
        selectedFilter = DocumentFilter.MAGIC_COLOR
        editingDocumentId = null
        editingPageId = null
    }
}
