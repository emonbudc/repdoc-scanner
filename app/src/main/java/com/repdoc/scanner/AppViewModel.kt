package com.repdoc.scanner

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.repdoc.scanner.data.Document
import com.repdoc.scanner.data.DocumentRepository
import com.repdoc.scanner.util.DocumentFilter
import com.repdoc.scanner.util.NativeFilters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DocumentRepository(application)
    val documents: StateFlow<List<Document>> = repository.documents

    var pendingBitmap by mutableStateOf<Bitmap?>(null)
    var activeDocumentId by mutableStateOf<String?>(null)
    var selectedFilter by mutableStateOf(DocumentFilter.ORIGINAL)
    var busy by mutableStateOf(false)

    init {
        NativeFilters.initialize(application)
    }

    fun document(id: String): Document? = repository.document(id)

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
            val id = activeDocumentId ?: createDocument().id
            val updated = repository.addPage(id, filtered)
            busy = false
            if (updated != null) {
                onSaved(updated)
            }
        }
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

    fun resetPending() {
        pendingBitmap = null
        selectedFilter = DocumentFilter.ORIGINAL
    }
}
