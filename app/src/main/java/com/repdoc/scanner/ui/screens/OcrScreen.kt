package com.repdoc.scanner.ui.screens

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repdoc.scanner.AppViewModel
import com.repdoc.scanner.util.ImageProcessor
import com.repdoc.scanner.util.OcrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScreen(
    documentId: String,
    pageId: String,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onEdit: (android.graphics.Bitmap) -> Unit
) {
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val document = documents.firstOrNull { it.id == documentId }
    val page = document?.pages?.firstOrNull { it.id == pageId }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var text by remember(page?.ocrText) { mutableStateOf(page?.ocrText.orEmpty()) }
    var recognizing by remember { mutableStateOf(false) }
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(pageId) {
        if (page == null) return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) { ImageProcessor.decodeFile(page.imagePath, 2400) }
        if (text.isBlank()) {
            val image = bitmap ?: return@LaunchedEffect
            recognizing = true
            val result = runCatching { OcrEngine.recognize(image) }.getOrDefault("")
            recognizing = false
            if (result.isNotBlank()) {
                text = result
                viewModel.updateOcr(documentId, pageId, result)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Page text") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { bitmap?.let(onEdit) },
                        enabled = bitmap != null
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit page")
                    }
                    IconButton(
                        onClick = { clipboard.setText(AnnotatedString(text)) },
                        enabled = text.isNotBlank()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy text")
                    }
                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share text"))
                        },
                        enabled = text.isNotBlank()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share text")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            val image = bitmap
            if (image != null) {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = "Page image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .padding(16.dp)
                )
            }
            if (recognizing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (text.isBlank()) {
                Text(
                    text = "No text recognized yet.",
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                SelectionContainer {
                    Text(
                        text = text,
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
