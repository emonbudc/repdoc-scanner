package com.repdoc.scanner.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.repdoc.scanner.AppViewModel
import com.repdoc.scanner.R
import com.repdoc.scanner.data.Page
import com.repdoc.scanner.util.ImageProcessor
import com.repdoc.scanner.util.OcrEngine
import com.repdoc.scanner.util.PdfExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(
    documentId: String,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onAddPage: () -> Unit,
    onImportPage: () -> Unit,
    onOpenPage: (String) -> Unit,
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
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share PDF"))
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
                val text = runCatching { OcrEngine.recognize(bitmap) }.getOrDefault("")
                if (text.isNotBlank()) {
                    viewModel.updateOcr(document.id, page.id, text)
                }
            }
            ocrBusy = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        document.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onImportPage) {
                        Icon(Icons.Outlined.PhotoLibrary, contentDescription = "Import page")
                    }
                    IconButton(onClick = { runOcr() }, enabled = !ocrBusy) {
                        Icon(Icons.Outlined.TextFields, contentDescription = "OCR all")
                    }
                    IconButton(onClick = { exportPdf() }, enabled = !exporting) {
                        Icon(Icons.Outlined.PictureAsPdf, contentDescription = "Export PDF")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
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
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddPage,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add page") }
            )
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(document.pages, key = { _, page -> page.id }) { index, page ->
                        PageRow(
                            index = index,
                            page = page,
                            onClick = { onOpenPage(page.id) }
                        )
                    }
                }
            }
            if (ocrBusy || exporting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(10.dp))
                        Text(if (ocrBusy) "Recognizing text…" else "Creating PDF…")
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
private fun PageRow(index: Int, page: Page, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(82.dp, 108.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = File(page.imagePath),
                    contentDescription = "Page ${index + 1}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Page ${index + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = page.ocrText?.takeIf { it.isNotBlank() }?.lineSequence()?.firstOrNull()
                        ?: "Tap to view or run OCR",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
