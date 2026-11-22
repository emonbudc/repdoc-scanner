package com.repdoc.scanner.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.repdoc.scanner.AppViewModel
import com.repdoc.scanner.R
import com.repdoc.scanner.data.Page
import com.repdoc.scanner.util.ExportUtils
import com.repdoc.scanner.util.ImageExporter
import com.repdoc.scanner.util.ImageProcessor
import com.repdoc.scanner.util.OcrEngine
import com.repdoc.scanner.util.PdfExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val Brand = Color(0xFF00B796)
private val Muted = Color(0xFF9C9C9C)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(
    documentId: String,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onAddPage: () -> Unit,
    onImportPage: () -> Unit,
    onImportPdf: () -> Unit,
    onOpenPage: (String) -> Unit,
    onEditPage: (String) -> Unit,
    onDeleted: () -> Unit
) {
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val document = documents.firstOrNull { it.id == documentId }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    var ocrBusy by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var exportingImages by remember { mutableStateOf(false) }
    var selectedPage by remember(documentId) { mutableStateOf<Page?>(null) }
    var deletePageTarget by remember { mutableStateOf<Page?>(null) }

    if (document == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Document not found")
        }
        return
    }

    fun exportPdf() {
        if (exporting) return
        exporting = true
        scope.launch {
            val file = withContext(Dispatchers.IO) { PdfExporter.export(context, document) }
            exporting = false
            if (file == null) {
                Toast.makeText(context, "Could not create PDF", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val downloadsUri = withContext(Dispatchers.IO) {
                ExportUtils.saveToDownloads(context, file, file.name, "application/pdf")
            }
            if (downloadsUri != null) {
                Toast.makeText(context, "Saved to Downloads", Toast.LENGTH_SHORT).show()
            }
            val uri = downloadsUri
                ?: FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share PDF"))
        }
    }

    fun exportImages() {
        if (exportingImages) return
        exportingImages = true
        scope.launch {
            val files = withContext(Dispatchers.IO) { ImageExporter.export(context, document) }
            exportingImages = false
            if (files.isEmpty()) {
                Toast.makeText(context, "Could not export images", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val safeName = document.name.replace(Regex("[^A-Za-z0-9._ -]"), "_").ifBlank { "Document" }
            val uris = ArrayList(files.mapIndexed { index, file ->
                ExportUtils.saveToDownloads(
                    context,
                    file,
                    "${safeName}_page_${index + 1}.jpg",
                    "image/jpeg"
                ) ?: FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            })
            if (uris.isNotEmpty()) {
                Toast.makeText(context, "Saved ${uris.size} images to Downloads", Toast.LENGTH_SHORT).show()
            }
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/jpeg"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share images"))
        }
    }

    fun runOcr() {
        if (ocrBusy || document.pages.isEmpty()) return
        ocrBusy = true
        scope.launch {
            for (page in document.pages) {
                val bitmap = withContext(Dispatchers.IO) {
                    ImageProcessor.decodeFile(page.imagePath, 2200)
                } ?: continue
                val text = runCatching { OcrEngine.recognize(context, bitmap) }.getOrDefault("")
                if (text.isNotBlank()) {
                    viewModel.updateOcr(document.id, page.id, text)
                }
            }
            ocrBusy = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectedPage != null) "1 selected" else document.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (selectedPage != null) selectedPage = null else onBack() }) {
                        Icon(
                            Icons.Outlined.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (selectedPage == null) {
                        IconButton(onClick = onImportPage) {
                            Icon(Icons.Outlined.PhotoLibrary, contentDescription = "Import page")
                        }
                        IconButton(onClick = { runOcr() }, enabled = !ocrBusy) {
                            Icon(Icons.Outlined.TextFields, contentDescription = "OCR all")
                        }
                        IconButton(onClick = { exportPdf() }, enabled = !exporting) {
                            Icon(Icons.Outlined.PictureAsPdf, contentDescription = "Export PDF")
                        }
                        IconButton(onClick = { exportImages() }, enabled = !exportingImages) {
                            Icon(Icons.Outlined.Image, contentDescription = "Export images")
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Import PDF") },
                                    onClick = {
                                        menuOpen = false
                                        onImportPdf()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    onClick = {
                                        menuOpen = false
                                        renameOpen = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        menuOpen = false
                                        deleteOpen = true
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                val page = selectedPage
                if (page != null) {
                    val index = document.pages.indexOfFirst { it.id == page.id }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PageAction(Icons.Outlined.ArrowBack, "Left", enabled = index > 0) {
                            viewModel.movePage(document.id, page.id, index - 1)
                            selectedPage = null
                        }
                        PageAction(Icons.Outlined.ArrowForward, "Right", enabled = index < document.pages.lastIndex) {
                            viewModel.movePage(document.id, page.id, index + 1)
                            selectedPage = null
                        }
                        PageAction(Icons.Outlined.RotateRight, "Rotate") {
                            viewModel.rotatePage(document.id, page.id)
                            selectedPage = null
                        }
                        PageAction(Icons.Default.ContentCopy, "Copy") {
                            viewModel.duplicatePage(document.id, page.id)
                            selectedPage = null
                        }
                        PageAction(Icons.Outlined.Delete, "Delete") {
                            deletePageTarget = page
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onImportPage,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Import")
                        }
                        Button(
                            onClick = onAddPage,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Brand)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Add page")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (document.pages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_capture_add),
                        contentDescription = null,
                        modifier = Modifier.size(96.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("No pages yet", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Add a page to start building this document.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(document.pages, key = { _, page -> page.id }) { index, page ->
                        PageTile(
                            index = index,
                            page = page,
                            selected = selectedPage?.id == page.id,
                            onClick = {
                                if (selectedPage != null) selectedPage = page
                                else onOpenPage(page.id)
                            },
                            onLongClick = { selectedPage = page },
                            onEdit = { onEditPage(page.id) }
                        )
                    }
                }
            }
            if (ocrBusy || exporting || exportingImages || viewModel.busy) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Brand)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            when {
                                ocrBusy -> "Recognizing text…"
                                exportingImages -> "Exporting images…"
                                exporting -> "Creating PDF…"
                                else -> "Working…"
                            }
                        )
                    }
                }
            }
        }
    }

    if (renameOpen) {
        var name by remember(document.id) { mutableStateOf(document.name) }
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("Rename document") },
            text = {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Document name") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.rename(document.id, name)
                        renameOpen = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameOpen = false }) { Text("Cancel") }
            }
        )
    }

    deletePageTarget?.let { page ->
        AlertDialog(
            onDismissRequest = { deletePageTarget = null },
            title = { Text("Delete page?") },
            text = { Text("This removes the selected page from the document.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePage(document.id, page.id)
                        selectedPage = null
                        deletePageTarget = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletePageTarget = null }) { Text("Cancel") }
            }
        )
    }

    if (deleteOpen) {
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            title = { Text("Delete document?") },
            text = { Text("This removes \"${document.name}\" and all its pages.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(document.id)
                        deleteOpen = false
                        onDeleted()
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteOpen = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PageAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface else Muted,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            fontSize = 10.sp,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else Muted
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageTile(
    index: Int,
    page: Page,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEdit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(
                if (selected) Modifier.border(2.dp, Brand, RoundedCornerShape(4.dp))
                else Modifier
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = File(page.imagePath),
                contentDescription = "Page ${index + 1}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brand.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .size(28.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(onClick = onEdit),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "Edit page",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${index + 1}",
                    color = Color.White,
                    fontSize = 11.sp
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Page ${index + 1}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (!page.ocrText.isNullOrBlank()) {
                Icon(
                    Icons.Outlined.TextFields,
                    contentDescription = null,
                    tint = Brand,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
