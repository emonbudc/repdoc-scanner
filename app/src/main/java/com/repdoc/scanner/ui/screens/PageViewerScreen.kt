package com.repdoc.scanner.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.repdoc.scanner.AppViewModel
import com.repdoc.scanner.data.Page
import com.repdoc.scanner.util.DocumentFilter
import com.repdoc.scanner.util.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

private val Brand = Color(0xFF00B796)
private val Muted = Color(0xFF9C9C9C)
private const val MaxPageZoom = 5f
private const val DoubleTapZoom = 2.5f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageViewerScreen(
    documentId: String,
    pageId: String,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onPageChanged: (String) -> Unit,
    onOpenText: (String) -> Unit
) {
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val document = documents.firstOrNull { it.id == documentId }
    val initialIndex = document?.pages?.indexOfFirst { it.id == pageId } ?: -1
    val pagerState = rememberPagerState(initialPage = initialIndex.coerceAtLeast(0)) {
        document?.pages?.size ?: 0
    }
    val pageIndex = pagerState.currentPage
    val page = document?.pages?.getOrNull(pageIndex)
    val scope = rememberCoroutineScope()
    var cropSource by remember(page?.id) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(page?.id) { mutableStateOf(false) }
    var appliedFilter by remember(page?.id, page?.filterName) {
        mutableStateOf(
            page?.filterName
                ?.let { name -> DocumentFilter.entries.firstOrNull { it.name == name } }
                ?: DocumentFilter.ORIGINAL
        )
    }
    var sizeText by remember(page?.id) { mutableStateOf("") }
    var zoomScale by remember(documentId) { mutableFloatStateOf(1f) }
    var zoomOffset by remember(documentId) { mutableStateOf(Offset.Zero) }
    var viewerSize by remember { mutableStateOf(IntSize.Zero) }

    fun clampZoomOffset(value: Offset, scale: Float): Offset {
        if (scale <= 1.01f || viewerSize.width == 0 || viewerSize.height == 0) {
            return Offset.Zero
        }
        val maxX = viewerSize.width * (scale - 1f) / 2f
        val maxY = viewerSize.height * (scale - 1f) / 2f
        return Offset(
            value.x.coerceIn(-maxX, maxX),
            value.y.coerceIn(-maxY, maxY)
        )
    }

    fun animateZoom(target: Float) {
        val startPage = pageIndex
        scope.launch {
            animate(
                initialValue = zoomScale,
                targetValue = target.coerceIn(1f, MaxPageZoom),
                animationSpec = tween(220)
            ) { value, _ ->
                if (pagerState.currentPage == startPage) {
                    zoomScale = value
                    zoomOffset = clampZoomOffset(zoomOffset, value)
                }
            }
        }
    }

    LaunchedEffect(pageId, document?.pages?.size) {
        val target = document?.pages?.indexOfFirst { it.id == pageId } ?: -1
        if (target >= 0 && pagerState.currentPage != target) {
            pagerState.scrollToPage(target)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        zoomScale = 1f
        zoomOffset = Offset.Zero
    }

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
            val source = page.originalImagePath?.takeIf { File(it).isFile } ?: page.imagePath
            val bitmap = withContext(Dispatchers.IO) {
                ImageProcessor.decodeFile(source, 2800)
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
                viewModel.replacePageImage(document.id, page.id, cropped, appliedFilter)
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
                        "Page ${pageIndex + 1} of ${document.pages.size}",
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
                    IconButton(
                        onClick = { animateZoom(zoomScale + 0.5f) },
                        enabled = zoomScale < MaxPageZoom - 0.01f
                    ) {
                        Icon(Icons.Outlined.ZoomIn, contentDescription = "Zoom in")
                    }
                    IconButton(
                        onClick = { animateZoom(zoomScale - 0.5f) },
                        enabled = zoomScale > 1.01f
                    ) {
                        Icon(Icons.Outlined.ZoomOut, contentDescription = "Zoom out")
                    }
                    IconButton(onClick = { onOpenText(page.id) }) {
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
                            "Page ${pageIndex + 1}",
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
                            onClick = { onOpenText(page.id) },
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
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .onSizeChanged { viewerSize = it },
                userScrollEnabled = false,
                beyondViewportPageCount = 1,
                key = { pagerIndex -> document.pages[pagerIndex].id }
            ) { pagerIndex ->
                val pagerPage = document.pages[pagerIndex]
                val isCurrent = pagerIndex == pagerState.currentPage
                ZoomablePage(
                    page = pagerPage,
                    pageNumber = pagerIndex + 1,
                    isCurrent = isCurrent,
                    scale = if (isCurrent) zoomScale else 1f,
                    offset = if (isCurrent) zoomOffset else Offset.Zero,
                    onTransform = { newScale, newOffset ->
                        if (isCurrent) {
                            zoomScale = newScale
                            zoomOffset = clampZoomOffset(newOffset, newScale)
                        }
                    },
                    onZoomRequest = { target -> if (isCurrent) animateZoom(target) },
                    onSwipe = { direction ->
                        val target = (pagerState.currentPage + direction)
                            .coerceIn(0, document.pages.lastIndex)
                        if (target != pagerState.currentPage) {
                            scope.launch {
                                pagerState.animateScrollToPage(target)
                                onPageChanged(document.pages[target].id)
                            }
                        }
                    }
                )
            }
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
private fun ZoomablePage(
    page: Page,
    pageNumber: Int,
    isCurrent: Boolean,
    scale: Float,
    offset: Offset,
    onTransform: (Float, Offset) -> Unit,
    onZoomRequest: (Float) -> Unit,
    onSwipe: (Int) -> Unit
) {
    val currentScale by rememberUpdatedState(scale)
    val currentOffset by rememberUpdatedState(offset)
    val transform by rememberUpdatedState(onTransform)
    val zoomRequest by rememberUpdatedState(onZoomRequest)
    val swipe by rememberUpdatedState(onSwipe)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(page.id, isCurrent) {
                if (!isCurrent) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var dragX = 0f
                    var multiTouch = false
                    var panned = false
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.none { it.pressed }) break
                        val pressed = event.changes.count { it.pressed }
                        if (pressed > 1) {
                            multiTouch = true
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            if (zoomChange != 1f || panChange != Offset.Zero) {
                                transform(
                                    (currentScale * zoomChange).coerceIn(1f, MaxPageZoom),
                                    currentOffset + panChange
                                )
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } else if (currentScale > 1.01f) {
                            panned = true
                            val panChange = event.calculatePan()
                            if (panChange != Offset.Zero) {
                                transform(currentScale, currentOffset + panChange)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } else {
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: event.changes.firstOrNull { it.pressed }
                            if (change != null) {
                                dragX += change.positionChange().x
                                if (abs(dragX) > viewConfiguration.touchSlop) {
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            }
                        }
                    }
                    if (!multiTouch && !panned && abs(dragX) > viewConfiguration.touchSlop * 2f) {
                        swipe(if (dragX < 0f) 1 else -1)
                    }
                }
            }
            .pointerInput(page.id, isCurrent) {
                if (!isCurrent) return@pointerInput
                detectTapGestures(
                    onDoubleTap = {
                        zoomRequest(if (currentScale > 1.01f) 1f else DoubleTapZoom)
                    }
                )
            }
    ) {
        AsyncImage(
            model = File(page.imagePath),
            contentDescription = "Page $pageNumber",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .graphicsLayer {
                    scaleX = currentScale
                    scaleY = currentScale
                    translationX = currentOffset.x
                    translationY = currentOffset.y
                }
        )
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
