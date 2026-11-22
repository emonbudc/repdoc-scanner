package com.repdoc.scanner.ui.screens

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Brand = Color(0xFF00B796)
private val Muted = Color(0xFF9C9C9C)

private data class GalleryImage(
    val id: Long,
    val uri: Uri,
    val name: String,
    val bucketId: String,
    val bucketName: String
)

private data class GalleryAlbum(
    val id: String,
    val name: String,
    val count: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    onBack: () -> Unit,
    onImport: (List<Uri>) -> Unit
) {
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var hasPermission by remember { mutableStateOf(hasGalleryPermission(context)) }
    var requested by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var images by remember { mutableStateOf<List<GalleryImage>>(emptyList()) }
    var selectedIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var selectedAlbumId by remember { mutableStateOf<String?>(null) }
    val albums = remember(images) { buildAlbums(images) }
    val selectedAlbum = albums.firstOrNull { it.id == selectedAlbumId }
    val visibleImages = remember(images, selectedAlbumId) {
        if (selectedAlbumId == null) images
        else images.filter { it.bucketId == selectedAlbumId }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        hasPermission = hasGalleryPermission(context)
    }

    LaunchedEffect(Unit) {
        if (!hasPermission && !requested) {
            requested = true
            permissionLauncher.launch(permission)
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasPermission = hasGalleryPermission(context)
    }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        loading = true
        images = withContext(Dispatchers.IO) { queryGallery(context) }
        loading = false
    }

    LaunchedEffect(albums, selectedAlbumId) {
        if (selectedAlbumId != null && albums.none { it.id == selectedAlbumId }) {
            selectedAlbumId = null
        }
    }

    fun toggle(id: Long) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectedIds.isEmpty()) selectedAlbum?.name ?: "Gallery"
                        else "${selectedIds.size} selected",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
            if (hasPermission && albums.isNotEmpty()) {
                AlbumStrip(
                    albums = albums,
                    totalCount = images.size,
                    selectedAlbumId = selectedAlbumId,
                    onSelect = {
                        selectedAlbumId = it
                        selectedIds = emptyList()
                    }
                )
            }
        },
        bottomBar = {
            if (hasPermission) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            if (selectedIds.isEmpty()) {
                                "Select images to import"
                            } else {
                                "${selectedIds.size} image${if (selectedIds.size == 1) "" else "s"} selected"
                            },
                            fontSize = 12.sp,
                            color = Muted,
                            modifier = Modifier.weight(1f)
                        )
                        if (selectedIds.isNotEmpty()) {
                            TextButton(onClick = { selectedIds = emptyList() }) {
                                Text("Clear", color = Muted)
                            }
                        }
                        Button(
                            onClick = {
                                val uris = visibleImages
                                    .filter { it.id in selectedIds }
                                    .map { it.uri }
                                if (uris.isNotEmpty()) onImport(uris)
                            },
                            enabled = selectedIds.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = Brand)
                        ) {
                            Text("Import")
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
            when {
                !hasPermission -> PermissionContent(
                    onAllow = { permissionLauncher.launch(permission) },
                    onSettings = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                )
                            )
                        }
                    }
                )
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Brand)
                }
                images.isEmpty() -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Outlined.PhotoLibrary,
                        contentDescription = null,
                        tint = Muted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("No images found on this device", color = Muted, fontSize = 13.sp)
                }
                visibleImages.isEmpty() -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Outlined.PhotoLibrary,
                        contentDescription = null,
                        tint = Muted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No images in ${selectedAlbum?.name ?: "this album"}",
                        color = Muted,
                        fontSize = 13.sp
                    )
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(visibleImages, key = { it.id }) { image ->
                        val selected = image.id in selectedIds
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { toggle(image.id) }
                        ) {
                            AsyncImage(
                                model = image.uri,
                                contentDescription = image.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (selected) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Brand.copy(alpha = 0.35f))
                                )
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionContent(
    onAllow: () -> Unit,
    onSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.PhotoLibrary,
            contentDescription = null,
            tint = Muted,
            modifier = Modifier.size(52.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "Gallery access needed",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Allow photo access to pick images from your device and import them in batches.",
            fontSize = 13.sp,
            color = Muted,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onAllow,
            colors = ButtonDefaults.buttonColors(containerColor = Brand)
        ) {
            Text("Allow access")
        }
        TextButton(onClick = onSettings) {
            Text("Open app settings", color = Muted)
        }
    }
}

@Composable
private fun AlbumStrip(
    albums: List<GalleryAlbum>,
    totalCount: Int,
    selectedAlbumId: String?,
    onSelect: (String?) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "all") {
            AlbumChip(
                name = "All",
                count = totalCount,
                selected = selectedAlbumId == null,
                onClick = { onSelect(null) }
            )
        }
        rowItems(albums, key = { it.id }) { album ->
            AlbumChip(
                name = album.name,
                count = album.count,
                selected = album.id == selectedAlbumId,
                onClick = { onSelect(album.id) }
            )
        }
    }
}

@Composable
private fun AlbumChip(
    name: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .border(
                1.dp,
                if (selected) Brand else MaterialTheme.colorScheme.outlineVariant,
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) Brand else MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = count.toString(),
            fontSize = 10.sp,
            color = if (selected) Brand.copy(alpha = 0.75f)
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun buildAlbums(images: List<GalleryImage>): List<GalleryAlbum> {
    val albums = LinkedHashMap<String, GalleryAlbum>()
    for (image in images) {
        val current = albums[image.bucketId]
        albums[image.bucketId] = if (current == null) {
            GalleryAlbum(image.bucketId, image.bucketName, 1)
        } else {
            current.copy(count = current.count + 1)
        }
    }
    return albums.values.sortedWith(
        compareBy<GalleryAlbum> { albumPriority(it.name) }
            .thenBy { it.name.lowercase() }
    )
}

private fun albumPriority(name: String): Int {
    val value = name.lowercase()
    return when {
        value.contains("messenger") -> 0
        value.contains("facebook") -> 1
        value.contains("whatsapp") && value.contains("business") -> 3
        value.contains("whatsapp") -> 2
        value.contains("camera") || value.contains("dcim") -> 4
        else -> 5
    }
}

private fun hasGalleryPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_IMAGES
        ) == PackageManager.PERMISSION_GRANTED ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                ) == PackageManager.PERMISSION_GRANTED)
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}

private fun queryGallery(context: Context): List<GalleryImage> {
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.BUCKET_ID,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME
    )
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
    return runCatching {
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            buildList {
                while (cursor.moveToNext() && size < 1200) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "Image"
                    val bucketName = cursor.getString(bucketNameColumn)
                        ?.trim()
                        .orEmpty()
                        .ifBlank { "Other" }
                    val bucketId = cursor.getString(bucketIdColumn)
                        ?.takeIf { it.isNotBlank() }
                        ?: bucketName
                    add(
                        GalleryImage(
                            id = id,
                            uri = ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                id
                            ),
                            name = name,
                            bucketId = bucketId,
                            bucketName = bucketName
                        )
                    )
                }
            }
        }.orEmpty()
    }.getOrDefault(emptyList())
}
