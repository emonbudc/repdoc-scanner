package com.repdoc.scanner.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.repdoc.scanner.R
import com.repdoc.scanner.data.Document
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    documents: List<Document>,
    onOpen: (String) -> Unit,
    onScan: () -> Unit,
    onImport: () -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit
) {
    var menuFor by remember { mutableStateOf<String?>(null) }
    var renameDoc by remember { mutableStateOf<Document?>(null) }
    var deleteDoc by remember { mutableStateOf<Document?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Repdoc Scanner") },
                actions = {
                    IconButton(onClick = onImport) {
                        Icon(Icons.Outlined.PhotoLibrary, contentDescription = "Import images")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScan,
                icon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null) },
                text = { Text("Scan") }
            )
        }
    ) { padding ->
        if (documents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(R.drawable.img_home_nodoc),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth(0.72f)
                    )
                    Spacer(Modifier.height(20.dp))
                    Text("No documents yet", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Scan a page or import an image to begin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(documents, key = { it.id }) { doc ->
                    DocumentRow(
                        document = doc,
                        menuOpen = menuFor == doc.id,
                        onOpen = { onOpen(doc.id) },
                        onMenu = { menuFor = doc.id },
                        onDismissMenu = { menuFor = null },
                        onRename = {
                            menuFor = null
                            renameDoc = doc
                        },
                        onDelete = {
                            menuFor = null
                            deleteDoc = doc
                        }
                    )
                }
            }
        }
    }

    renameDoc?.let { doc ->
        var name by remember(doc.id) { mutableStateOf(doc.name) }
        AlertDialog(
            onDismissRequest = { renameDoc = null },
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
                        onRename(doc.id, name)
                        renameDoc = null
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameDoc = null }) { Text("Cancel") }
            }
        )
    }

    deleteDoc?.let { doc ->
        AlertDialog(
            onDismissRequest = { deleteDoc = null },
            title = { Text("Delete document?") },
            text = { Text("This removes \"${doc.name}\" and its pages from this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(doc.id)
                        deleteDoc = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteDoc = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DocumentRow(
    document: Document,
    menuOpen: Boolean,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onOpen,
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
                    .size(76.dp, 96.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                val firstPage = document.pages.firstOrNull()
                if (firstPage != null) {
                    AsyncImage(
                        model = File(firstPage.imagePath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.ic_imgpage_ocr_small),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "${document.pageCount} ${if (document.pageCount == 1) "page" else "pages"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = formatDate(document.updatedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = onMenu) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = onDismissMenu) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = onRename)
                    DropdownMenuItem(text = { Text("Delete") }, onClick = onDelete)
                }
            }
        }
    }
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
