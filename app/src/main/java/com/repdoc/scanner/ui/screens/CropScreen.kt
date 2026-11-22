package com.repdoc.scanner.ui.screens

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.repdoc.scanner.util.AutoCrop
import com.repdoc.scanner.util.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(
    bitmap: Bitmap,
    onBack: () -> Unit,
    onDone: (Bitmap) -> Unit
) {
    var corners by remember(bitmap) {
        mutableStateOf(ImageProcessor.defaultCorners(bitmap))
    }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var dragIndex by remember { mutableStateOf(-1) }
    var detecting by remember(bitmap) { mutableStateOf(true) }
    var fullPage by remember(bitmap) { mutableStateOf(false) }
    var autoRequest by remember(bitmap) { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val context = LocalContext.current
    val handleRadiusPx = with(density) { 34.dp.toPx() }

    LaunchedEffect(bitmap, autoRequest) {
        detecting = true
        val detection = withContext(Dispatchers.Default) {
            AutoCrop.detect(context, bitmap)
        }
        if (detection != null) {
            corners = detection.corners
        }
        fullPage = false
        detecting = false
    }

    Scaffold(
        containerColor = Color(0xFF111111),
        topBar = {
            TopAppBar(
                title = { Text("Adjust crop") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    TextButton(onClick = { onDone(if (fullPage) bitmap else AutoCrop.crop(bitmap, corners)) }) {
                        Text("Done", color = Color(0xFF00B796))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1C1C1E),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            Surface(color = Color(0xFF1C1C1E)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { autoRequest++ },
                        enabled = !detecting
                    ) {
                        Text("Auto", color = Color(0xFF00B796))
                    }
                    TextButton(
                        onClick = {
                            corners = ImageProcessor.fullCorners(bitmap)
                            fullPage = true
                        }
                    ) {
                        Text("Full page", color = Color.White)
                    }
                    TextButton(
                        onClick = {
                            corners = ImageProcessor.defaultCorners(bitmap)
                            fullPage = false
                        }
                    ) {
                        Text("Reset", color = Color.White)
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
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(bitmap, canvasSize) {
                        detectDragGestures(
                            onDragStart = { position ->
                                val layout = imageLayout(bitmap, canvasSize)
                                val points = corners.map {
                                    Offset(
                                        layout.left + it.x * layout.scale,
                                        layout.top + it.y * layout.scale
                                    )
                                }
                                val nearest = points.indices.minByOrNull {
                                    (points[it] - position).getDistance()
                                }
                                dragIndex = if (nearest != null &&
                                    (points[nearest] - position).getDistance() <= handleRadiusPx
                                ) nearest else -1
                            },
                            onDragEnd = { dragIndex = -1 },
                            onDragCancel = { dragIndex = -1 },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val index = dragIndex
                                if (index >= 0) {
                                    fullPage = false
                                    val layout = imageLayout(bitmap, canvasSize)
                                    if (layout.scale > 0f) {
                                        val current = corners[index]
                                        val next = PointF(
                                            (current.x + dragAmount.x / layout.scale)
                                                .coerceIn(0f, bitmap.width.toFloat()),
                                            (current.y + dragAmount.y / layout.scale)
                                                .coerceIn(0f, bitmap.height.toFloat())
                                        )
                                        corners = corners.toMutableList().also { it[index] = next }
                                    }
                                }
                            }
                        )
                    }
            ) {
                val layout = imageLayout(bitmap, canvasSize)
                if (layout.scale > 0f) {
                    drawImage(
                        image = bitmap.asImageBitmap(),
                        dstOffset = IntOffset(layout.left.roundToInt(), layout.top.roundToInt()),
                        dstSize = IntSize(
                            (bitmap.width * layout.scale).roundToInt(),
                            (bitmap.height * layout.scale).roundToInt()
                        )
                    )
                    val points = corners.map {
                        Offset(
                            layout.left + it.x * layout.scale,
                            layout.top + it.y * layout.scale
                        )
                    }
                    val path = Path().apply {
                        moveTo(points[0].x, points[0].y)
                        lineTo(points[1].x, points[1].y)
                        lineTo(points[2].x, points[2].y)
                        lineTo(points[3].x, points[3].y)
                        close()
                    }
                    drawPath(
                        path = path,
                        color = Color(0xFF00B796),
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawPath(
                        path = path,
                        color = Color(0xFF00B796).copy(alpha = 0.12f)
                    )
                    points.forEach { point ->
                        drawCircle(Color.White, radius = 10.dp.toPx(), center = point)
                        drawCircle(Color(0xFF00B796), radius = 6.dp.toPx(), center = point)
                    }
                }
            }
            if (detecting) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF00B796))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Detecting edges", color = Color.White)
                    }
                }
            }
        }
    }
}

private data class ImageLayout(val scale: Float, val left: Float, val top: Float)

private fun imageLayout(bitmap: Bitmap, size: IntSize): ImageLayout {
    if (size.width <= 0 || size.height <= 0) return ImageLayout(0f, 0f, 0f)
    val scale = min(
        size.width.toFloat() / bitmap.width,
        size.height.toFloat() / bitmap.height
    )
    val width = bitmap.width * scale
    val height = bitmap.height * scale
    return ImageLayout(scale, (size.width - width) / 2f, (size.height - height) / 2f)
}
