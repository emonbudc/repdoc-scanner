package com.repdoc.scanner.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CreateNewFolder
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
import androidx.compose.runtime.LaunchedEffect
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
import com.repdoc.scanner.data.Folder
import com.repdoc.scanner.data.SearchHit
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
    folders: List<Folder>,
    onOpen: (String) -> Unit,
    onOpenPage: (String, String) -> Unit,
    onScan: () -> Unit,
    onImportImage: () -> Unit,
    onImportPdf: () -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameFolder: (String, String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onMoveDocument: (String, String?) -> Unit,
    onSearch: (String, String?) -> List<SearchHit>
) {
    var query by remember { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var renameDoc by remember { mutableStateOf<Document?>(null) }
    var deleteDoc by remember { mutableStateOf<Document?>(null) }
    var moveDoc by remember { mutableStateOf<Document?>(null) }
    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    var folderDialog by remember { mutableStateOf(false) }
    var renameFolder by remember { mutableStateOf<Folder?>(null) }
    var deleteFolder by remember { mutableStateOf<Folder?>(null) }
    var folderMenuFor by remember { mutableStateOf<String?>(null) }
    var selectedTabName by rememberSaveable { mutableStateOf(HomeTab.FILES.name) }
    val selectedTab = HomeTab.valueOf(selectedTabName)
    val selectedFolder = folders.firstOrNull { it.id == selectedFolderId }

    LaunchedEffect(folders, selectedFolderId) {
        if (selectedFolderId != null && selectedFolder == null) selectedFolderId = null
    }

    val filtered = remember(documents, selectedFolderId) {
        if (selectedFolderId == null) documents
        else documents.filter { it.folderId == selectedFolderId }
    }
    val hits = remember(documents, query, selectedFolderId) {
        if (query.isBlank()) emptyList() else onSearch(query, selectedFolderId)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            HomeHeader(
                title = selectedFolder?.name ?: selectedTab.label,
                query = query,
                showSearch = selectedTab == HomeTab.HOME || selectedTab == HomeTab.FILES,
                brandHeader = selectedTab != HomeTab.ME,
                onQueryChange = { query = it },
                onImportImage = onImportImage,
                onImportPdf = onImportPdf,
                onNewFolder = { folderDialog = true },
                showFolderAction = selectedTab == HomeTab.HOME || selectedTab == HomeTab.FILES,
                onBackFolder = selectedFolder?.let { { selectedFolderId = null } }
            )
        },
        bottomBar = {
            HomeBottomBar(
                selectedTab = selectedTab,
                onSelect = {
                    selectedTabName = it.name
                    if (it != HomeTab.HOME && it != HomeTab.FILES) selectedFolderId = null
                },
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
                    onImport = onImportImage,
                    onFiles = { selectedTabName = HomeTab.FILES.name }
                )
                selectedTab == HomeTab.ME -> MeContent(
                    modifier = Modifier.fillMaxSize(),
                    documentCount = documents.size,
                    folderCount = folders.size
                )
                query.isNotBlank() -> SearchResults(
                    hits = hits,
                    onOpen = onOpen,
                    onOpenPage = onOpenPage
                )
                documents.isEmpty() && folders.isEmpty() -> EmptyHome(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp)
                )
                filtered.isEmpty() && selectedFolder == null -> EmptyHome(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp)
                )
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (folders.isNotEmpty() && selectedFolder == null) {
                        item(key = "folders") {
                            FolderStrip(
                                folders = folders,
                                selectedFolderId = selectedFolderId,
                                menuFor = folderMenuFor,
                                onSelect = { selectedFolderId = it.id },
                                onMenu = { folderMenuFor = it.id },
                                onDismissMenu = { folderMenuFor = null },
                                onRename = {
                                    folderMenuFor = null
                                    renameFolder = it
                                },
                                onDelete = {
                                    folderMenuFor = null
                                    deleteFolder = it
                                }
                            )
                        }
                    }
                    if (filtered.isEmpty()) {
                        item(key = "empty") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (selectedFolder != null) "No documents in this folder"
                                    else "No documents yet",
                                    color = Muted
                                )
                            }
                        }
                    } else {
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
                                },
                                onMove = {
                                    menuFor = null
                                    moveDoc = doc
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
    }

    if (folderDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { folderDialog = false },
            title = { Text("New folder") },
            text = {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Folder name") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isNotBlank()) onCreateFolder(name)
                        folderDialog = false
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { folderDialog = false }) { Text("Cancel") }
            }
        )
    }

    renameFolder?.let { folder ->
        var name by remember(folder.id) { mutableStateOf(folder.name) }
        AlertDialog(
            onDismissRequest = { renameFolder = null },
            title = { Text("Rename folder") },
            text = {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Folder name") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRenameFolder(folder.id, name)
                        renameFolder = null
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameFolder = null }) { Text("Cancel") }
            }
        )
    }

    deleteFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { deleteFolder = null },
            title = { Text("Delete folder?") },
            text = { Text("Documents in \"${folder.name}\" will move back to Files.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteFolder(folder.id)
                        if (selectedFolderId == folder.id) selectedFolderId = null
                        deleteFolder = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteFolder = null }) { Text("Cancel") }
            }
        )
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

    moveDoc?.let { doc ->
        AlertDialog(
            onDismissRequest = { moveDoc = null },
            title = { Text("Move to folder") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    TextButton(
                        onClick = {
                            onMoveDocument(doc.id, null)
                            moveDoc = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("No folder", modifier = Modifier.fillMaxWidth())
                    }
                    folders.forEach { folder ->
                        TextButton(
                            onClick = {
                                onMoveDocument(doc.id, folder.id)
                                moveDoc = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(folder.name, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { moveDoc = null }) { Text("Cancel") }
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
    onImportImage: () -> Unit,
    onImportPdf: () -> Unit,
    onNewFolder: () -> Unit,
    showFolderAction: Boolean,
    onBackFolder: (() -> Unit)?
) {
    val headerColor = when {
        !brandHeader -> MaterialTheme.colorScheme.surface
        isSystemInDarkTheme() -> DarkBrand
        else -> Brand
    }
    val contentColor = if (brandHeader) Color.White else MaterialTheme.colorScheme.onSurface
    var importMenu by remember { mutableStateOf(false) }

    Surface(color = headerColor) {
        Column(modifier = Modifier.statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBackFolder != null) {
                    IconButton(onClick = onBackFolder) {
                        Icon(
                            Icons.Outlined.ArrowBack,
                            contentDescription = "Back to files",
                            tint = contentColor
                        )
                    }
                }
                Text(
                    text = title,
                    color = contentColor,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (showSearch) {
                    Spacer(Modifier.width(10.dp))
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
                    Spacer(Modifier.width(4.dp))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (showFolderAction) {
                    IconButton(onClick = onNewFolder) {
                        Icon(
                            Icons.Outlined.CreateNewFolder,
                            contentDescription = "New folder",
                            tint = contentColor
                        )
                    }
                }
                Box {
                    IconButton(onClick = { importMenu = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_import_663),
                            contentDescription = "Import",
                            tint = contentColor
                        )
                    }
                    DropdownMenu(expanded = importMenu, onDismissRequest = { importMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Import image") },
                            onClick = {
                                importMenu = false
                                onImportImage()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Import PDF") },
                            onClick = {
                                importMenu = false
                                onImportPdf()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderStrip(
    folders: List<Folder>,
    selectedFolderId: String?,
    menuFor: String?,
    onSelect: (Folder) -> Unit,
    onMenu: (Folder) -> Unit,
    onDismissMenu: () -> Unit,
    onRename: (Folder) -> Unit,
    onDelete: (Folder) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(folders, key = { it.id }) { folder ->
            FolderCard(
                folder = folder,
                selected = folder.id == selectedFolderId,
                menuOpen = menuFor == folder.id,
                onClick = { onSelect(folder) },
                onMenu = { onMenu(folder) },
                onDismissMenu = onDismissMenu,
                onRename = { onRename(folder) },
                onDelete = { onDelete(folder) }
            )
        }
    }
}

@Composable
private fun FolderCard(
    folder: Folder,
    selected: Boolean,
    menuOpen: Boolean,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Box {
        Column(
            modifier = Modifier
                .width(108.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (selected) Brand.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface
                )
                .clickable(onClick = onClick)
                .padding(12.dp)
        ) {
            Icon(
                Icons.Outlined.CreateNewFolder,
                contentDescription = null,
                tint = Brand,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = folder.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(
            onClick = onMenu,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(28.dp)
        ) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "Folder actions",
                tint = Muted,
                modifier = Modifier.size(16.dp)
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = onDismissMenu) {
            DropdownMenuItem(text = { Text("Rename") }, onClick = onRename)
            DropdownMenuItem(text = { Text("Delete") }, onClick = onDelete)
        }
    }
}

@Composable
private fun SearchResults(
    hits: List<SearchHit>,
    onOpen: (String) -> Unit,
    onOpenPage: (String, String) -> Unit
) {
    if (hits.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No results", color = Muted)
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(hits, key = { it.document.id + (it.page?.id ?: "") }) { hit ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val page = hit.page
                        if (page != null) onOpenPage(hit.document.id, page.id)
                        else onOpen(hit.document.id)
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = hit.document.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (hit.snippet.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = hit.snippet,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                thickness = 1.dp
            )
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
private fun MeContent(modifier: Modifier, documentCount: Int, folderCount: Int) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_launcher),
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
        MeRow("Folders", folderCount.toString())
        MeRow("Storage", "On this device")
        MeRow("Version", "1.1")
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
    onDelete: () -> Unit,
    onMove: () -> Unit
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
                DropdownMenuItem(text = { Text("Move to folder") }, onClick = onMove)
                DropdownMenuItem(text = { Text("Rename") }, onClick = onRename)
                DropdownMenuItem(text = { Text("Delete") }, onClick = onDelete)
            }
        }
    }
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
