package com.repdoc.scanner.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
            val bitmap = ImageProcessor.decodeBitmap(context, uri)
            if (bitmap != null) {
                viewModel.pendingBitmap = bitmap
                screen = Screen.Crop
            }
        }
    }


    BackHandler(enabled = screen != Screen.Home) { goBack() }

    when (val current = screen) {
        Screen.Home -> HomeScreen(
            documents = documents,
            onOpen = { id ->
                viewModel.activeDocumentId = id
                screen = Screen.Document(id)
            },
            onScan = {
                viewModel.beginNewPage()
                viewModel.activeDocumentId = null
                screen = Screen.Camera
            },
            onImport = {
                viewModel.beginNewPage()
                viewModel.activeDocumentId = null
                importLauncher.launch("image/*")
            },
            onDelete = viewModel::delete,
            onRename = viewModel::rename
        )

        Screen.Camera -> CameraScreen(
            onClose = { goBack() },
            onCaptured = { path ->
                val bitmap = ImageProcessor.decodeFile(path)
                if (bitmap != null) {
                    viewModel.pendingBitmap = bitmap
                    screen = Screen.Crop
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
                            viewModel.resetPending()
                            screen = Screen.Document(doc.id)
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
