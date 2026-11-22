package com.repdoc.scanner.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.repdoc.scanner.R
import com.repdoc.scanner.data.Document
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Brand = Color(0xFF00B796)

@Composable
fun HomeScreen(
    documents: List<Document>,
    onOpen: (String) -> Unit,
    onScan: () -> Unit,
    onImport: () -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var renameDoc by remember { mutableStateOf<Document?>(null) }
    var deleteDoc by remember { mutableStateOf<Document?>(null) }
    val filtered = remember(documents, query) {
        if (query.isBlank()) documents
        else documents.filter { it.name.contains(query, ignoreCase = true) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            HomeHeader(
                query = query,
                onQueryChange = { query = it },
                onImport = onImport
            )
        },
        bottomBar = {
            HomeBottomBar(onScan = onScan, onImport = onImport)
        }
    ) { padding ->
        when {
            documents.isEmpty() -> EmptyHome(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
            filtered.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No documents match \"$query\"",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(filtered, key = { it.id }) { doc ->
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
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                        thickness = 1.dp
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
private fun HomeHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onImport: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        ),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text(
                                    "Search documents",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp
                                )
                            }
                            inner()
                        }
                    )
                }
                IconButton(onClick = onImport) {
                    Icon(
                        Icons.Outlined.PhotoLibrary,
                        contentDescription = "Import images",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                thickness = 1.dp
            )
        }
    }
}

@Composable
private fun HomeBottomBar(onScan: () -> Unit, onImport: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(72.dp)
                .padding(horizontal = 28.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomAction(
                icon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                label = "Docs",
                selected = true,
                onClick = {}
            )
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(Brand)
                    .clickable(onClick = onScan),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.PhotoCamera,
                    contentDescription = "Scan",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            BottomAction(
                icon = { Icon(Icons.Outlined.PhotoLibrary, contentDescription = null) },
                label = "Import",
                selected = false,
                onClick = onImport
            )
        }
    }
}

@Composable
private fun BottomAction(
    icon: @Composable () -> Unit,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CompositionLocalProvider(
            LocalContentColor provides
                if (selected) Brand else MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            icon()
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = if (selected) Brand else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyHome(modifier: Modifier) {
    Box(
        modifier = modifier.padding(32.dp),
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
                "Tap the scan button to capture your first page.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onOpen)
            .padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 99.dp, height = 78.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
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
        Spacer(Modifier.width(17.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp)
        ) {
            Text(
                text = document.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatDate(document.updatedAt),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = document.pageCount.toString(),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Box {
            IconButton(onClick = onMenu) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "More",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = onDismissMenu) {
                DropdownMenuItem(text = { Text("Rename") }, onClick = onRename)
                DropdownMenuItem(text = { Text("Delete") }, onClick = onDelete)
            }
        }
    }
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
