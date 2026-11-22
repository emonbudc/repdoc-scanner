package com.repdoc.scanner.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.repdoc.scanner.AppViewModel
import com.repdoc.scanner.util.DocumentFilter
import com.repdoc.scanner.util.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val Brand = Color(0xFF00B796)
private val Muted = Color(0xFF9C9C9C)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageViewerScreen(
    documentId: String,
    pageId: String,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenText: () -> Unit
) {
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val document = documents.firstOrNull { it.id == documentId }
    val page = document?.pages?.firstOrNull { it.id == pageId }
    val index = document?.pages?.indexOfFirst { it.id == pageId } ?: -1
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cropSource by remember(pageId) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(pageId) { mutableStateOf(false) }
    var appliedFilter by remember(pageId) { mutableStateOf<DocumentFilter?>(null) }
    var sizeText by remember(pageId) { mutableStateOf("") }

    LaunchedEffect(page?.imagePath) {
        val path = page?.imagePath ?: return@LaunchedEffect
        val size = withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                "${bounds.outWidth} × ${bounds.outHeight}"
            } else ""
        }
        if (size.isNotBlank()) sizeText = size
    }

    if (page == null || document == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Page not found")
        }
        return
    }

    fun startCrop() {
        if (loading) return
        loading = true
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                ImageProcessor.decodeFile(page.imagePath, 2800)
            }
            loading = false
            if (bitmap != null) {
                cropSource = bitmap
            }
        }
    }

    val pendingCrop = cropSource
    if (pendingCrop != null) {
        CropScreen(
            bitmap = pendingCrop,
            onBack = { cropSource = null },
            onDone = { cropped ->
                viewModel.replacePageImage(document.id, page.id, cropped)
                cropSource = null
            }
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Page ${index + 1} of ${document.pages.size}",
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
                    IconButton(onClick = onOpenText) {
                        Icon(Icons.Outlined.TextFields, contentDescription = "Page text")
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
                Column(modifier = Modifier.navigationBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Page ${index + 1}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            buildString {
                                if (sizeText.isNotBlank()) append(sizeText)
                                if (sizeText.isNotBlank()) append("  •  ")
                                append(if (page.ocrText.isNullOrBlank()) "No OCR" else "OCR ready")
                            },
                            fontSize = 11.sp,
                            color = Muted
                        )
                    }
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(DocumentFilter.entries, key = { it.name }) { filter ->
                            PageFilterTile(
                                filter = filter,
                                selected = appliedFilter == filter,
                                onClick = {
                                    if (!viewModel.busy) {
                                        appliedFilter = filter
                                        viewModel.applyFilterToPage(document.id, page.id, filter)
                                    }
                                }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { startCrop() },
                            enabled = !loading && !viewModel.busy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Re-crop")
                        }
                        Button(
                            onClick = onOpenText,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Brand)
                        ) {
                            Icon(Icons.Outlined.TextFields, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Text")
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
            AsyncImage(
                model = File(page.imagePath),
                contentDescription = "Page ${index + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            )
            if (loading || viewModel.busy) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Brand)
                }
            }
        }
    }
}

@Composable
private fun PageFilterTile(
    filter: DocumentFilter,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (selected) Brand.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = if (selected) 2.dp else 0.dp,
                    color = if (selected) Brand else Color.Transparent,
                    shape = RoundedCornerShape(9.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(filter.iconRes),
                contentDescription = filter.displayName,
                modifier = Modifier.size(46.dp),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = filter.displayName,
            fontSize = 9.sp,
            color = if (selected) Brand else Muted,
            maxLines = 1
        )
    }
}
