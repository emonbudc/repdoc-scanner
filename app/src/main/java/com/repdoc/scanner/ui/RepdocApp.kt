package com.repdoc.scanner.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.repdoc.scanner.AppViewModel
import com.repdoc.scanner.ui.screens.CameraScreen
import com.repdoc.scanner.ui.screens.CropScreen
import com.repdoc.scanner.ui.screens.DocumentScreen
import com.repdoc.scanner.ui.screens.FilterScreen
import com.repdoc.scanner.ui.screens.HomeScreen
import com.repdoc.scanner.ui.screens.OcrScreen
import com.repdoc.scanner.util.ImageProcessor
import com.repdoc.scanner.util.PdfImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Home : Screen
    data object Camera : Screen
    data object Crop : Screen
    data object Filter : Screen
    data class Document(val id: String) : Screen
    data class Ocr(val documentId: String, val pageId: String) : Screen
}

@Composable
fun RepdocApp(viewModel: AppViewModel = viewModel()) {
    val context = LocalContext.current
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val scope = rememberCoroutineScope()

    fun goBack() {
        screen = when (val current = screen) {
            Screen.Home -> Screen.Home
            Screen.Camera -> currentDocumentScreen(viewModel)
            Screen.Crop -> {
                viewModel.resetPending()
                currentDocumentScreen(viewModel)
            }
            Screen.Filter -> Screen.Crop
            is Screen.Document -> Screen.Home
            is Screen.Ocr -> Screen.Document(current.documentId)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) { ImageProcessor.decodeBitmap(context, uri) }
                if (bitmap != null) {
                    viewModel.startImport(listOf(bitmap))
                    screen = Screen.Crop
                } else {
                    Toast.makeText(context, "Could not read image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val bitmaps = withContext(Dispatchers.IO) { PdfImporter.import(context, uri) }
                if (bitmaps.isEmpty()) {
                    Toast.makeText(context, "Could not read PDF", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.startImport(bitmaps)
                    screen = Screen.Crop
                }
            }
        }
    }

    fun importImage() {
        viewModel.beginNewPage()
        viewModel.activeDocumentId = null
        importLauncher.launch("image/*")
    }

    fun importPdf() {
        viewModel.beginNewPage()
        viewModel.activeDocumentId = null
        pdfLauncher.launch(arrayOf("application/pdf"))
    }

    BackHandler(enabled = screen != Screen.Home) { goBack() }

    when (val current = screen) {
        Screen.Home -> HomeScreen(
            documents = documents,
            folders = folders,
            onOpen = { id ->
                viewModel.activeDocumentId = id
                screen = Screen.Document(id)
            },
            onOpenPage = { documentId, pageId ->
                viewModel.activeDocumentId = documentId
                screen = Screen.Ocr(documentId, pageId)
            },
            onScan = {
                viewModel.beginNewPage()
                viewModel.activeDocumentId = null
                screen = Screen.Camera
            },
            onImportImage = { importImage() },
            onImportPdf = { importPdf() },
            onDelete = viewModel::delete,
            onRename = viewModel::rename,
            onCreateFolder = { name -> viewModel.createFolder(name) },
            onRenameFolder = { id, name -> viewModel.renameFolder(id, name) },
            onDeleteFolder = { id -> viewModel.deleteFolder(id) },
            onMoveDocument = { id, folderId -> viewModel.moveDocumentToFolder(id, folderId) },
            onSearch = { query, folderId -> viewModel.search(query, folderId) }
        )

        Screen.Camera -> CameraScreen(
            onClose = { goBack() },
            onCaptured = { paths ->
                scope.launch {
                    val bitmaps = withContext(Dispatchers.IO) {
                        paths.mapNotNull { ImageProcessor.decodeFile(it) }
                    }
                    if (bitmaps.isNotEmpty()) {
                        viewModel.startImport(bitmaps)
                        screen = Screen.Crop
                    } else {
                        Toast.makeText(context, "Could not read captures", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onImport = { importLauncher.launch("image/*") }
        )

        Screen.Crop -> {
            val bitmap = viewModel.pendingBitmap
            if (bitmap == null) {
                screen = Screen.Home
            } else {
                CropScreen(
                    bitmap = bitmap,
                    onBack = { goBack() },
                    onDone = { cropped ->
                        viewModel.pendingBitmap = cropped
                        viewModel.selectedFilter = com.repdoc.scanner.util.DocumentFilter.ORIGINAL
                        screen = Screen.Filter
                    }
                )
            }
        }

        Screen.Filter -> {
            val bitmap = viewModel.pendingBitmap
            if (bitmap == null) {
                screen = Screen.Home
            } else {
                FilterScreen(
                    bitmap = bitmap,
                    selectedFilter = viewModel.selectedFilter,
                    busy = viewModel.busy,
                    onSelect = { viewModel.selectedFilter = it },
                    onBack = { screen = Screen.Crop },
                    onSave = {
                        viewModel.savePage(bitmap, viewModel.selectedFilter) { doc ->
                            val next = viewModel.advancePending()
                            if (next != null) {
                                screen = Screen.Crop
                            } else {
                                viewModel.resetPending()
                                screen = Screen.Document(doc.id)
                            }
                        }
                    }
                )
            }
        }

        is Screen.Document -> DocumentScreen(
            documentId = current.id,
            viewModel = viewModel,
            onBack = { screen = Screen.Home },
            onAddPage = {
                viewModel.beginNewPage()
                viewModel.activeDocumentId = current.id
                screen = Screen.Camera
            },
            onImportPage = {
                viewModel.beginNewPage()
                viewModel.activeDocumentId = current.id
                importLauncher.launch("image/*")
            },
            onImportPdf = {
                viewModel.beginNewPage()
                viewModel.activeDocumentId = current.id
                pdfLauncher.launch(arrayOf("application/pdf"))
            },
            onOpenPage = { pageId -> screen = Screen.Ocr(current.id, pageId) },
            onEditPage = { pageId ->
                scope.launch {
                    val page = viewModel.document(current.id)?.pages?.firstOrNull { it.id == pageId }
                        ?: return@launch
                    val bitmap = withContext(Dispatchers.IO) {
                        ImageProcessor.decodeFile(page.imagePath)
                    }
                    if (bitmap != null) {
                        viewModel.startEditingPage(current.id, pageId, bitmap)
                        screen = Screen.Crop
                    }
                }
            },
            onDeleted = { screen = Screen.Home }
        )

        is Screen.Ocr -> OcrScreen(
            documentId = current.documentId,
            pageId = current.pageId,
            viewModel = viewModel,
            onBack = { screen = Screen.Document(current.documentId) },
            onEdit = { bitmap ->
                viewModel.startEditingPage(current.documentId, current.pageId, bitmap)
                screen = Screen.Crop
            }
        )
    }
}

private fun currentDocumentScreen(viewModel: AppViewModel): Screen {
    val id = viewModel.activeDocumentId
    return if (id != null) Screen.Document(id) else Screen.Home
}
