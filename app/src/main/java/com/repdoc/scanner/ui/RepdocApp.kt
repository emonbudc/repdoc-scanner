package com.repdoc.scanner.ui

import android.graphics.Bitmap
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
import com.repdoc.scanner.ui.screens.IdCardScreen
import com.repdoc.scanner.ui.screens.OcrScreen
import com.repdoc.scanner.ui.screens.PageViewerScreen
import com.repdoc.scanner.ui.screens.PdfToolType
import com.repdoc.scanner.ui.screens.PdfToolsScreen
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
    data object IdCard : Screen
    data class PdfTools(val tool: PdfToolType) : Screen
    data class Document(val id: String) : Screen
    data class PageViewer(val documentId: String, val pageId: String) : Screen
    data class Ocr(val documentId: String, val pageId: String, val autoRun: Boolean = false) : Screen
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
            Screen.IdCard -> Screen.Home
            is Screen.PdfTools -> Screen.Home
            is Screen.Document -> Screen.Home
            is Screen.PageViewer -> Screen.Document(current.documentId)
            is Screen.Ocr -> Screen.Document(current.documentId)
        }
    }

    fun saveCurrent(bitmap: Bitmap) {
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
                screen = Screen.Ocr(documentId, pageId, autoRun = true)
            },
            onScan = {
                viewModel.beginNewPage()
                viewModel.activeDocumentId = null
                screen = Screen.Camera
            },
            onImportImage = { importImage() },
            onImportPdf = { importPdf() },
            onIdCard = {
                viewModel.beginNewPage()
                viewModel.activeDocumentId = null
                screen = Screen.IdCard
            },
            onPdfTool = { tool -> screen = Screen.PdfTools(tool) },
            onDelete = viewModel::delete,
            onRename = viewModel::rename,
            onCreateFolder = { name -> viewModel.createFolder(name) },
            onRenameFolder = { id, name -> viewModel.renameFolder(id, name) },
            onDeleteFolder = { id -> viewModel.deleteFolder(id) },
            onMoveDocument = { id, folderId -> viewModel.moveDocumentToFolder(id, folderId) },
            onSearch = { query, folderId -> viewModel.search(query, folderId) },
            autoSave = viewModel.autoSave,
            onAutoSaveChange = viewModel::updateAutoSave
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
                        viewModel.selectedFilter = com.repdoc.scanner.util.DocumentFilter.MAGIC_COLOR
                        if (viewModel.autoSave) {
                            saveCurrent(cropped)
                        } else {
                            screen = Screen.Filter
                        }
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
                    onSave = { saveCurrent(bitmap) }
                )
            }
        }

        Screen.IdCard -> IdCardScreen(
            onBack = { screen = Screen.Home },
            onCreate = { combined ->
                viewModel.pendingBitmap = combined
                viewModel.selectedFilter = com.repdoc.scanner.util.DocumentFilter.ORIGINAL
                saveCurrent(combined)
            }
        )

        is Screen.PdfTools -> PdfToolsScreen(
            tool = current.tool,
            onBack = { screen = Screen.Home }
        )

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
            onOpenPage = { pageId -> screen = Screen.PageViewer(current.id, pageId) },
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

        is Screen.PageViewer -> PageViewerScreen(
            documentId = current.documentId,
            pageId = current.pageId,
            viewModel = viewModel,
            onBack = { screen = Screen.Document(current.documentId) },
            onOpenText = {
                screen = Screen.Ocr(current.documentId, current.pageId, autoRun = true)
            }
        )

        is Screen.Ocr -> OcrScreen(
            documentId = current.documentId,
            pageId = current.pageId,
            viewModel = viewModel,
            onBack = { screen = Screen.Document(current.documentId) },
            autoRun = current.autoRun,
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
