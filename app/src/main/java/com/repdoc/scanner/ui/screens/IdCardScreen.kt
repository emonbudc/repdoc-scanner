package com.repdoc.scanner.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.repdoc.scanner.util.AutoCrop
import com.repdoc.scanner.util.ImageProcessor
import com.repdoc.scanner.util.PdfImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

private val Brand = Color(0xFF00B796)
private val Muted = Color(0xFF9C9C9C)

private enum class IdSide { FRONT, BACK }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdCardScreen(
    onBack: () -> Unit,
    onCreate: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var front by remember { mutableStateOf<Bitmap?>(null) }
    var back by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var captureSide by remember { mutableStateOf(IdSide.FRONT) }
    var captureFile by remember { mutableStateOf<File?>(null) }
    var gallerySide by remember { mutableStateOf(IdSide.FRONT) }
    var cropSource by remember { mutableStateOf<Bitmap?>(null) }
    var cropSide by remember { mutableStateOf<IdSide?>(null) }

    fun setSide(side: IdSide, bitmap: Bitmap) {
        if (side == IdSide.FRONT) front = bitmap else back = bitmap
    }

    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = captureFile
        captureFile = null
        if (!success) {
            file?.delete()
            return@rememberLauncherForActivityResult
        }
        if (file == null || !file.isFile) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val bitmap = withContext(Dispatchers.IO) {
                ImageProcessor.decodeFile(file.absolutePath, 2400)
            }
            if (bitmap != null) {
                cropSource = bitmap
                cropSide = captureSide
            } else {
                Toast.makeText(context, "Could not read captured photo", Toast.LENGTH_SHORT).show()
            }
            file.delete()
            busy = false
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val bitmap = withContext(Dispatchers.IO) {
                ImageProcessor.decodeBitmap(context, uri, 2400)
            }
            if (bitmap != null) {
                cropSource = bitmap
                cropSide = gallerySide
            } else {
                Toast.makeText(context, "Could not open image", Toast.LENGTH_SHORT).show()
            }
            busy = false
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val pages = withContext(Dispatchers.IO) {
                PdfImporter.import(context, uri, maxPages = 2, maxDimension = 2400)
            }
            if (pages.isEmpty()) {
                Toast.makeText(context, "Could not read PDF", Toast.LENGTH_SHORT).show()
            } else {
                val prepared = withContext(Dispatchers.Default) {
                    pages.map { prepareBitmap(context, it) }
                }
                front = prepared.getOrNull(0)
                back = prepared.getOrNull(1)
            }
            busy = false
        }
    }

    fun launchCapture(side: IdSide) {
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        val file = File(dir, "id_${side.name.lowercase()}_${System.currentTimeMillis()}.jpg")
        captureFile = file
        captureSide = side
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        captureLauncher.launch(uri)
    }

    val pendingCrop = cropSource
    val pendingSide = cropSide
    if (pendingCrop != null && pendingSide != null) {
        CropScreen(
            bitmap = pendingCrop,
            onBack = {
                cropSource = null
                cropSide = null
            },
            onDone = { cropped ->
                setSide(pendingSide, cropped)
                cropSource = null
                cropSide = null
            }
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("ID Card", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { pdfLauncher.launch(arrayOf("application/pdf")) }) {
                        Icon(Icons.Outlined.PictureAsPdf, contentDescription = "Import PDF front and back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Capture both sides or import them from your gallery. PDF import uses the first two pages.",
                    fontSize = 13.sp,
                    color = Muted
                )
                IdCardSlot(
                    title = "Front side",
                    hint = "Add the front of the card",
                    bitmap = front,
                    onCapture = { launchCapture(IdSide.FRONT) },
                    onGallery = {
                        gallerySide = IdSide.FRONT
                        galleryLauncher.launch("image/*")
                    }
                )
                IdCardSlot(
                    title = "Back side",
                    hint = "Add the back of the card",
                    bitmap = back,
                    onCapture = { launchCapture(IdSide.BACK) },
                    onGallery = {
                        gallerySide = IdSide.BACK
                        galleryLauncher.launch("image/*")
                    }
                )
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = {
                        val f = front
                        val b = back
                        if (f != null && b != null) {
                            busy = true
                            scope.launch {
                                val combined = withContext(Dispatchers.Default) {
                                    combineIdCard(f, b)
                                }
                                busy = false
                                onCreate(combined)
                            }
                        }
                    },
                    enabled = front != null && back != null && !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Brand)
                ) {
                    Text("Create ID card", fontSize = 15.sp)
                }
                Text(
                    "The two sides are placed on one page. You can choose a filter next.",
                    fontSize = 11.sp,
                    color = Muted
                )
            }
            if (busy) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.28f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Brand)
                }
            }
        }
    }
}

@Composable
private fun IdCardSlot(
    title: String,
    hint: String,
    bitmap: Bitmap?,
    onCapture: () -> Unit,
    onGallery: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (bitmap == null) "Empty" else "Ready",
                fontSize = 11.sp,
                color = if (bitmap == null) Muted else Brand
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.586f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.CreditCard,
                        contentDescription = null,
                        tint = Muted,
                        modifier = Modifier.size(38.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(hint, fontSize = 12.sp, color = Muted)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCapture, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Capture")
            }
            OutlinedButton(onClick = onGallery, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Image")
            }
        }
    }
}

private suspend fun prepareBitmap(context: Context, bitmap: Bitmap): Bitmap =
    withContext(Dispatchers.IO) {
        val scaled = ImageProcessor.scaled(bitmap, 2400)
        runCatching {
            val detection = AutoCrop.detect(context, scaled)
            if (detection != null) AutoCrop.crop(scaled, detection.corners) else scaled
        }.getOrDefault(scaled)
    }

private fun combineIdCard(front: Bitmap, back: Bitmap): Bitmap {
    val width = max(front.width, back.width)
    val margin = (width * 0.04f).toInt().coerceAtLeast(24)
    val gap = margin
    val frontScaled = scaleToWidth(front, width)
    val backScaled = scaleToWidth(back, width)
    val output = Bitmap.createBitmap(
        width + margin * 2,
        frontScaled.height + gap + backScaled.height + margin * 2,
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(output)
    canvas.drawColor(AndroidColor.WHITE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(frontScaled, margin.toFloat(), margin.toFloat(), paint)
    canvas.drawBitmap(
        backScaled,
        margin.toFloat(),
        (margin + frontScaled.height + gap).toFloat(),
        paint
    )
    if (frontScaled !== front) frontScaled.recycle()
    if (backScaled !== back) backScaled.recycle()
    return output
}

private fun scaleToWidth(bitmap: Bitmap, width: Int): Bitmap {
    if (bitmap.width == width) return bitmap
    val height = (bitmap.height.toFloat() * width / bitmap.width).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, width, height, true)
}
