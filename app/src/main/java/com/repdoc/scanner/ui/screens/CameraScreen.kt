package com.repdoc.scanner.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

private val Brand = Color(0xFF00B796)

@Composable
fun CameraScreen(
    onClose: () -> Unit,
    onCaptured: (String) -> Unit,
    onImport: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var flashMode by remember { mutableStateOf(ImageCapture.FLASH_MODE_OFF) }
    var capturing by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(previewView, hasPermission, lifecycleOwner) {
        if (!hasPermission) return@DisposableEffect onDispose { }
        val future = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        future.addListener({
            runCatching {
                val provider = future.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                imageCapture = capture
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    capture
                )
            }.onFailure {
                Toast.makeText(context, "Camera unavailable", Toast.LENGTH_SHORT).show()
            }
        }, executor)
        onDispose {
            runCatching {
                if (future.isDone) future.get().unbindAll()
            }
            imageCapture = null
        }
    }

    LaunchedEffect(flashMode, imageCapture) {
        imageCapture?.flashMode = flashMode
    }

    fun capture() {
        val capture = imageCapture ?: return
        if (capturing) return
        capturing = true
        capture.targetRotation = previewView.display.rotation
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    capturing = false
                    onCaptured(file.absolutePath)
                }

                override fun onError(exception: ImageCaptureException) {
                    capturing = false
                    Toast.makeText(context, "Capture failed", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasPermission) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                val inset = size.minDimension * 0.08f
                val left = inset
                val top = size.height * 0.16f
                val right = size.width - inset
                val bottom = size.height * 0.78f
                val length = size.minDimension * 0.08f
                val stroke = 3.dp.toPx()
                fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                    drawLine(Brand, Offset(x, y), Offset(x + dx, y), stroke, StrokeCap.Round)
                    drawLine(Brand, Offset(x, y), Offset(x, y + dy), stroke, StrokeCap.Round)
                }
                corner(left, top, length, length)
                corner(right, top, -length, length)
                corner(left, bottom, length, -length)
                corner(right, bottom, -length, -length)
            }
            Text(
                text = "Align document within frame",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 88.dp)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Camera permission is required", color = Color.White, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Allow camera access to scan documents.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = Brand)
                ) {
                    Text("Allow camera")
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = "Close", tint = Color.White)
            }
            Spacer(Modifier.width(4.dp))
            Text("Auto", color = Color.White, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = {
                    flashMode = when (flashMode) {
                        ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                        ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                        else -> ImageCapture.FLASH_MODE_OFF
                    }
                }
            ) {
                Icon(
                    imageVector = when (flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Outlined.FlashOn
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Outlined.FlashAuto
                        else -> Icons.Outlined.FlashOff
                    },
                    contentDescription = "Flash",
                    tint = Color.White
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 28.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    IconButton(onClick = onImport) {
                        Icon(
                            Icons.Outlined.PhotoLibrary,
                            contentDescription = "Import",
                            tint = Color.White
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(78.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f))
                        .clickable(enabled = hasPermission && !capturing) { capture() },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(if (capturing) Brand else Color.White)
                    )
                    if (capturing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = Brand,
                            strokeWidth = 3.dp
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Scan",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp
            )
        }
    }
}
