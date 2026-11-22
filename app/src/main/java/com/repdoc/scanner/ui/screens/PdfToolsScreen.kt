package com.repdoc.scanner.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repdoc.scanner.R
import com.repdoc.scanner.util.ExportUtils
import com.repdoc.scanner.util.PdfImporter
import com.repdoc.scanner.util.PdfTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val Brand = Color(0xFF00B796)
private val Muted = Color(0xFF9C9C9C)

enum class PdfToolType(
    val title: String,
    val subtitle: String,
    @DrawableRes val icon: Int
) {
    TO_IMAGE("PDF to images", "Save each page as JPEG", R.drawable.ic_pdf_to_image),
    MERGE("Merge PDFs", "Combine PDFs into one file", R.drawable.ic_pdf_merge),
    REORDER("Reorder PDF", "Change page order", R.drawable.ic_pdf_reorder)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolsScreen(
    tool: PdfToolType,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var uris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var names by remember { mutableStateOf<List<String>>(emptyList()) }
    var pageCounts by remember { mutableStateOf<List<Int>>(emptyList()) }
    var sourceName by remember { mutableStateOf("PDF") }
    var status by remember { mutableStateOf<String?>(null) }
    var openedPicker by remember { mutableStateOf(false) }

    fun reset() {
        pages = emptyList()
        uris = emptyList()
        names = emptyList()
        pageCounts = emptyList()
        status = null
    }

    val singlePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            status = null
            val name = withContext(Dispatchers.IO) { PdfTools.displayName(context, uri) }
            val rendered = withContext(Dispatchers.IO) {
                when (tool) {
                    PdfToolType.TO_IMAGE -> PdfImporter.import(context, uri, 100, 2200)
                    PdfToolType.REORDER -> PdfImporter.import(context, uri, 100, 1400)
                    PdfToolType.MERGE -> emptyList()
                }
            }
            sourceName = name.removeSuffix(".pdf").ifBlank { "PDF" }
            pages = rendered
            uris = listOf(uri)
            names = listOf(name)
            if (rendered.isEmpty()) {
                Toast.makeText(context, "Could not read PDF", Toast.LENGTH_SHORT).show()
            }
            busy = false
        }
    }

    val multiplePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { picked ->
        if (picked.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            status = null
            val displayNames = withContext(Dispatchers.IO) {
                picked.map { PdfTools.displayName(context, it) }
            }
            val counts = withContext(Dispatchers.IO) {
                picked.map { PdfTools.pageCount(context, it) }
            }
            uris = picked
            names = displayNames
            pageCounts = counts
            pages = emptyList()
            busy = false
        }
    }

    fun openPicker() {
        when (tool) {
            PdfToolType.MERGE -> multiplePicker.launch(arrayOf("application/pdf"))
            else -> singlePicker.launch(arrayOf("application/pdf"))
        }
    }

    LaunchedEffect(tool) {
        if (!openedPicker) {
            openedPicker = true
            openPicker()
        }
    }

    fun movePage(from: Int, to: Int) {
        if (to !in pages.indices) return
        val updated = pages.toMutableList()
        val page = updated.removeAt(from)
        updated.add(to, page)
        pages = updated
    }

    fun saveImages() {
        scope.launch {
            busy = true
            val saved = withContext(Dispatchers.IO) {
                PdfTools.saveImages(context, pages, sourceName)
            }
            busy = false
            status = if (saved > 0) "Saved $saved images to Downloads" else "Could not save images"
        }
    }

    fun mergePdfs() {
        scope.launch {
            busy = true
            val temp = File(context.cacheDir, "merged_${System.currentTimeMillis()}.pdf")
            val count = withContext(Dispatchers.IO) {
                PdfTools.merge(context, uris, temp)
            }
            if (count > 0) {
                val name = "Merged_${System.currentTimeMillis()}.pdf"
                val saved = withContext(Dispatchers.IO) {
                    ExportUtils.saveToDownloads(context, temp, name, "application/pdf")
                }
                status = if (saved != null) "Merged $count pages to Downloads" else "Could not save merged PDF"
            } else {
                status = "Could not merge these PDFs"
            }
            temp.delete()
            busy = false
        }
    }

    fun saveReordered() {
        scope.launch {
            busy = true
            val temp = File(context.cacheDir, "reordered_${System.currentTimeMillis()}.pdf")
            val created = withContext(Dispatchers.IO) {
                PdfTools.createPdf(pages, temp)
            }
            if (created) {
                val name = "${sourceName}_reordered.pdf"
                val saved = withContext(Dispatchers.IO) {
                    ExportUtils.saveToDownloads(context, temp, name, "application/pdf")
                }
                status = if (saved != null) "Saved reordered PDF to Downloads" else "Could not save PDF"
            } else {
                status = "Could not create PDF"
            }
            temp.delete()
            busy = false
        }
    }

    val canSave = when (tool) {
        PdfToolType.TO_IMAGE -> pages.isNotEmpty()
        PdfToolType.MERGE -> uris.isNotEmpty()
        PdfToolType.REORDER -> pages.isNotEmpty()
    }
    val actionLabel = when (tool) {
        PdfToolType.TO_IMAGE -> "Save ${pages.size} images"
        PdfToolType.MERGE -> "Merge ${uris.size} PDFs"
        PdfToolType.REORDER -> "Save reordered PDF"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(tool.title, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        reset()
                        openPicker()
                    }) {
                        Icon(Icons.Outlined.FolderOpen, contentDescription = "Choose PDF")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                Column(modifier = Modifier.padding(12.dp)) {
                    status?.let {
                        Text(it, fontSize = 12.sp, color = if (it.startsWith("Could")) Color(0xFFD34A4A) else Brand)
                        Spacer(Modifier.height(6.dp))
                    }
                    Button(
                        onClick = {
                            when (tool) {
                                PdfToolType.TO_IMAGE -> saveImages()
                                PdfToolType.MERGE -> mergePdfs()
                                PdfToolType.REORDER -> saveReordered()
                            }
                        },
                        enabled = canSave && !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Brand)
                    ) {
                        Text(actionLabel, fontSize = 14.sp)
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
            when {
                busy -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Brand)
                }
                tool == PdfToolType.MERGE && uris.isNotEmpty() -> MergeContent(
                    names = names,
                    pageCounts = pageCounts
                )
                tool == PdfToolType.REORDER && pages.isNotEmpty() -> ReorderContent(
                    pages = pages,
                    onMove = ::movePage
                )
                pages.isNotEmpty() -> ImageGrid(pages)
                else -> EmptyPdfContent(
                    tool = tool,
                    onPick = { openPicker() }
                )
            }
        }
    }
}

@Composable
private fun EmptyPdfContent(tool: PdfToolType, onPick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.PictureAsPdf,
            contentDescription = null,
            tint = Muted,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(tool.subtitle, fontSize = 14.sp, color = Muted)
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onPick,
            colors = ButtonDefaults.buttonColors(containerColor = Brand)
        ) {
            Text(if (tool == PdfToolType.MERGE) "Choose PDFs" else "Choose PDF")
        }
    }
}

@Composable
private fun ImageGrid(pages: List<Bitmap>) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(pages) { index, bitmap ->
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.72f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Page ${index + 1}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Page ${index + 1}",
                    fontSize = 11.sp,
                    color = Muted,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun ReorderContent(pages: List<Bitmap>, onMove: (Int, Int) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(pages) { index, bitmap ->
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.72f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Page ${index + 1}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Page ${index + 1}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onMove(index, index - 1) },
                        enabled = index > 0,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Outlined.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { onMove(index, index + 1) },
                        enabled = index < pages.lastIndex,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Outlined.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MergeContent(names: List<String>, pageCounts: List<Int>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "The PDFs are merged in this order. Use Choose PDF to pick them again.",
                fontSize = 12.sp,
                color = Muted
            )
        }
        items(names.size) { index ->
            val name = names[index]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.PictureAsPdf,
                    contentDescription = null,
                    tint = Brand,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${pageCounts.getOrNull(index) ?: 0} pages",
                        fontSize = 11.sp,
                        color = Muted
                    )
                }
            }
        }
        item {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            Spacer(Modifier.height(6.dp))
            Text(
                "Total: ${pageCounts.sum()} pages",
                fontSize = 13.sp,
                color = Muted
            )
        }
    }
}
