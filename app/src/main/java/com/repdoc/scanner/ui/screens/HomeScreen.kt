package com.repdoc.scanner.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
private val Muted = Color(0xFF9C9C9C)
private val DarkBrand = Color(0xFF192B2A)

private enum class HomeTab(
    val label: String,
    val normalIcon: Int,
    val selectedIcon: Int
) {
    HOME("Home", R.drawable.ic_tab_home_normal_24px, R.drawable.ic_tab_home_highlight_24px),
    FILES("Files", R.drawable.ic_tab_doc_normal_24px, R.drawable.ic_tab_doc_highlight_24px),
    TOOLS("Tools", R.drawable.ic_tab_tools_normal_24px, R.drawable.ic_tab_tools_highlight_24px),
    ME("Me", R.drawable.ic_tab_me_normal_24px, R.drawable.ic_tab_me_highlight_24px)
}

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
    var selectedTabName by rememberSaveable { mutableStateOf(HomeTab.FILES.name) }
    val selectedTab = HomeTab.valueOf(selectedTabName)
    val filtered = remember(documents, query) {
        if (query.isBlank()) documents
        else documents.filter { it.name.contains(query, ignoreCase = true) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            HomeHeader(
                title = selectedTab.label,
                query = query,
                showSearch = selectedTab == HomeTab.HOME || selectedTab == HomeTab.FILES,
                brandHeader = selectedTab != HomeTab.ME,
                onQueryChange = { query = it },
                onImport = onImport
            )
        },
        bottomBar = {
            HomeBottomBar(
                selectedTab = selectedTab,
                onSelect = { selectedTabName = it.name },
                onScan = onScan
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                selectedTab == HomeTab.TOOLS -> ToolsContent(
                    modifier = Modifier.fillMaxSize(),
                    onScan = onScan,
                    onImport = onImport,
                    onFiles = { selectedTabName = HomeTab.FILES.name }
                )
                selectedTab == HomeTab.ME -> MeContent(
                    modifier = Modifier.fillMaxSize(),
                    documentCount = documents.size
                )
                documents.isEmpty() -> EmptyHome(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp)
                )
                filtered.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No documents match \"$query\"",
                        color = Muted
                    )
                }
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
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
    title: String,
    query: String,
    showSearch: Boolean,
    brandHeader: Boolean,
    onQueryChange: (String) -> Unit,
    onImport: () -> Unit
) {
    val headerColor = when {
        !brandHeader -> MaterialTheme.colorScheme.surface
        isSystemInDarkTheme() -> DarkBrand
        else -> Brand
    }
    val contentColor = if (brandHeader) Color.White else MaterialTheme.colorScheme.onSurface

    Surface(color = headerColor) {
        Column(modifier = Modifier.statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = contentColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                if (showSearch) {
                    Spacer(Modifier.width(14.dp))
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_search_16_v665),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp
                            ),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (query.isEmpty()) {
                                    Text(
                                        "Search",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp
                                    )
                                }
                                inner()
                            }
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                IconButton(onClick = onImport) {
                    Icon(
                        painter = painterResource(R.drawable.ic_import_663),
                        contentDescription = "Import",
                        tint = contentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeBottomBar(
    selectedTab: HomeTab,
    onSelect: (HomeTab) -> Unit,
    onScan: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = 4.dp)
            ) {
                HomeTab.entries.forEach { tab ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onSelect(tab) }
                            .padding(top = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(
                                if (tab == selectedTab) tab.selectedIcon else tab.normalIcon
                            ),
                            contentDescription = tab.label,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = tab.label,
                            fontSize = 11.sp,
                            color = if (tab == selectedTab) Brand else Muted,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(56.dp)
                .clip(CircleShape)
                .background(Brand)
                .clickable(onClick = onScan),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_tab_scan_24px),
                contentDescription = "Scan",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun ToolsContent(
    modifier: Modifier,
    onScan: () -> Unit,
    onImport: () -> Unit,
    onFiles: () -> Unit
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToolCard(
                title = "Scan",
                subtitle = "Capture a new document",
                icon = R.drawable.ic_tab_scan_24px,
                onClick = onScan,
                modifier = Modifier.weight(1f)
            )
            ToolCard(
                title = "Import",
                subtitle = "Add images from gallery",
                icon = R.drawable.ic_import_663,
                onClick = onImport,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToolCard(
                title = "Files",
                subtitle = "Open your documents",
                icon = R.drawable.ic_tab_doc_normal_24px,
                onClick = onFiles,
                modifier = Modifier.weight(1f)
            )
            ToolCard(
                title = "OCR",
                subtitle = "Recognize text from pages",
                icon = R.drawable.ic_imgpage_ocr_small,
                onClick = onFiles,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ToolCard(
    title: String,
    subtitle: String,
    icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Brand.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = Brand,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = subtitle,
            fontSize = 11.sp,
            color = Muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MeContent(modifier: Modifier, documentCount: Int) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher),
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = "Repdoc Scanner",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "Offline document scanner",
                    fontSize = 12.sp,
                    color = Muted
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        MeRow("Documents", documentCount.toString())
        MeRow("Storage", "On this device")
        MeRow("Version", "1.0")
    }
}

@Composable
private fun MeRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = Muted
        )
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
        thickness = 1.dp
    )
}

@Composable
private fun EmptyHome(modifier: Modifier) {
    Box(
        modifier = modifier,
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
                color = Muted
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
            .padding(start = 9.dp),
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
                .padding(vertical = 10.dp)
        ) {
            Text(
                text = document.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatDate(document.updatedAt),
                    fontSize = 12.sp,
                    color = Muted
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
                        fontSize = 8.sp,
                        color = Muted
                    )
                }
            }
        }
        Box {
            IconButton(onClick = onMenu) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "More",
                    tint = Muted
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
