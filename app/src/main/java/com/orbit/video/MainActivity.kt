package com.orbit.video

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Size
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

private val OrbitBlack = Color(0xFF080808)
private val OrbitPanel = Color(0xFF141414)
private val OrbitAccent = Color(0xFFE53935)
private val OrbitText = Color(0xFFF4F4F0)
private val OrbitMuted = Color(0xFF92928D)
private val OrbitLine = Color(0xFF303030)

private data class LocalVideo(val uri: Uri, val name: String, val durationMs: Long, val size: Long, val folder: String)
private data class LocalAudio(val uri: Uri, val name: String, val durationMs: Long, val size: Long)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8, 8, 8)
        window.navigationBarColor = android.graphics.Color.rgb(8, 8, 8)
        setContent { OrbitApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun OrbitApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var mediaUri by remember { mutableStateOf<Uri?>(null) }
    var selectedName by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var showTrackInfo by remember { mutableStateOf(false) }
    var videos by remember { mutableStateOf<List<LocalVideo>>(emptyList()) }
    var audios by remember { mutableStateOf<List<LocalAudio>>(emptyList()) }
    var playlists by remember { mutableStateOf(context.getSharedPreferences("orbit_player", Context.MODE_PRIVATE).getStringSet("playlists", emptySet())?.toList()?.sorted() ?: emptyList()) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var hasPermission by remember { mutableStateOf(hasVideoPermission(context)) }
    var searchQuery by remember { mutableStateOf("") }
    var currentTab by remember { mutableStateOf("Video") }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinEntry by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf("") }
    var pendingHide by remember { mutableStateOf<LocalVideo?>(null) }
    var privacyUnlocked by remember { mutableStateOf(false) }
    var hiddenUris by remember { mutableStateOf(context.getSharedPreferences("orbit_privacy", Context.MODE_PRIVATE).getStringSet("hidden_uris", emptySet())?.toSet() ?: emptySet()) }
    val prefs = remember { context.getSharedPreferences("orbit_player", Context.MODE_PRIVATE) }

    fun refreshVideos() {
        hasPermission = hasVideoPermission(context)
        if (hasPermission) { videos = queryVideos(context); audios = queryAudios(context) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPermission = hasVideoPermission(context)
        if (hasPermission) { videos = queryVideos(context); audios = queryAudios(context) }
    }

    fun requestMediaPermission() {
        val permissions = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(permissions)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            mediaUri = uri
            selectedName = uri.lastPathSegment?.substringAfterLast('/') ?: "Selected media"
        }
    }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            })
        }
    }

    BackHandler(enabled = mediaUri != null) {
        mediaUri?.let { prefs.edit().putLong("position:" + it.toString(), player.currentPosition.coerceAtLeast(0L)).apply() }
        player.pause()
        mediaUri = null
        selectedName = null
        showTrackInfo = false
    }

    LaunchedEffect(Unit) { refreshVideos() }
    LaunchedEffect(mediaUri) {
        mediaUri?.let { uri ->
            val savedPosition = prefs.getLong("position:" + uri.toString(), 0L)
            player.setMediaItem(MediaItem.fromUri(uri), savedPosition)
            player.prepare()
            player.playWhenReady = true
        }
    }
    LaunchedEffect(mediaUri) {
        while (mediaUri != null) {
            val uri = mediaUri
            if (uri != null && player.currentPosition >= 0L) {
                prefs.edit().putLong("position:" + uri.toString(), player.currentPosition).apply()
            }
            delay(1000)
        }
    }

    DisposableEffect(mediaUri) {
        onDispose { mediaUri?.let { uri -> prefs.edit().putLong("position:" + uri.toString(), player.currentPosition.coerceAtLeast(0L)).apply() } }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = OrbitText, secondary = OrbitAccent, background = OrbitBlack,
        surface = OrbitPanel, onPrimary = OrbitBlack, onBackground = OrbitText, onSurface = OrbitText
    )) {
        Scaffold(
            containerColor = OrbitBlack,
            topBar = {
                Row(
                    Modifier.fillMaxWidth().background(OrbitBlack)
                        .padding(start = 22.dp, end = 12.dp, top = 20.dp, bottom = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("ORBIT", color = OrbitText, fontWeight = FontWeight.Black, letterSpacing = 4.sp, fontSize = 27.sp)
                        Text("V!DEO PLAYER  /  002", fontSize = 10.sp, color = OrbitMuted, letterSpacing = 2.sp)
                    }
                    Box(Modifier.size(34.dp).background(OrbitAccent, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                        Text("●", color = OrbitBlack, fontSize = 15.sp)
                    }
                    IconButton(onClick = { if (!hasPermission) requestMediaPermission() else refreshVideos() }) {
                        Icon(Icons.Default.VideoLibrary, contentDescription = "Refresh video library", tint = OrbitText)
                    }
                }
            },
            bottomBar = {
                if (mediaUri == null) {
                    NavigationBar(containerColor = OrbitPanel, contentColor = OrbitText, windowInsets = WindowInsets(0, 0, 0, 0)) {
                        NavigationBarItem(selected = currentTab == "Video", onClick = { currentTab = "Video"; privacyUnlocked = false }, icon = { Icon(Icons.Default.VideoLibrary, contentDescription = null) }, label = { Text("Video", maxLines = 1) })
                        NavigationBarItem(selected = currentTab == "Music", onClick = { currentTab = "Music"; privacyUnlocked = false }, icon = { Icon(Icons.Default.MusicNote, contentDescription = null) }, label = { Text("Music", maxLines = 1) })
                        NavigationBarItem(selected = currentTab == "Folder", onClick = { currentTab = "Folder"; privacyUnlocked = false }, icon = { Icon(Icons.Default.Folder, contentDescription = null) }, label = { Text("Folder", maxLines = 1) })
                        NavigationBarItem(selected = currentTab == "Playlist", onClick = { currentTab = "Playlist"; privacyUnlocked = false }, icon = { Icon(Icons.Default.QueueMusic, contentDescription = null) }, label = { Text("Playlist", maxLines = 1) })
                        NavigationBarItem(selected = currentTab == "Me", onClick = { currentTab = "Me"; privacyUnlocked = false }, icon = { Icon(Icons.Default.Person, contentDescription = null) }, label = { Text("Me", maxLines = 1) })
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp, vertical = 8.dp).animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (mediaUri == null && (currentTab == "Video" || currentTab == "Music")) {
                    OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, modifier = Modifier.fillMaxWidth().animateContentSize(), placeholder = { Text("Search videos", color = OrbitMuted) }, leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = OrbitMuted) }, singleLine = true, shape = RoundedCornerShape(10.dp), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = OrbitText, unfocusedTextColor = OrbitText, focusedBorderColor = OrbitAccent, unfocusedBorderColor = OrbitLine, cursorColor = OrbitAccent))
                }
                if (currentTab == "Video" || currentTab == "Music") Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (mediaUri == null) "YOUR LIBRARY" else "NOW / PLAYING", color = OrbitMuted, fontSize = 10.sp, letterSpacing = 2.sp)
                    Spacer(Modifier.weight(1f))
                    Text("●  LOCAL MEDIA", color = OrbitAccent, fontSize = 10.sp, letterSpacing = 1.sp)
                }

                if (currentTab == "Music" && mediaUri == null) {
                    Column(Modifier.fillMaxWidth().weight(1f).background(Color(0xFF050505), RoundedCornerShape(3.dp)).padding(14.dp)) {
                        Text("MUSIC ON DEVICE", color = OrbitText, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Text("${audios.size} audio files found", color = OrbitMuted, fontSize = 11.sp)
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            items(audios.filter { it.name.contains(searchQuery, ignoreCase = true) }, key = { it.uri.toString() }) { audio ->
                                Row(Modifier.fillMaxWidth().background(OrbitPanel, RoundedCornerShape(8.dp)).clickable { mediaUri = audio.uri; selectedName = audio.name }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = OrbitAccent, modifier = Modifier.size(32.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(audio.name, color = OrbitText, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text("${formatDuration(audio.durationMs)} • ${formatSize(audio.size)}", color = OrbitMuted, fontSize = 10.sp)
                                    }
                                    Text("PLAY", color = OrbitAccent, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                } else if (currentTab == "Folder" && mediaUri == null) {
                    val folders = videos.filter { it.uri.toString() !in hiddenUris }.groupBy { it.folder.ifBlank { "Other / Unknown" } }.toSortedMap()
                    Column(Modifier.fillMaxWidth().weight(1f).background(Color(0xFF050505), RoundedCornerShape(3.dp)).padding(14.dp)) {
                        Text("VIDEO FOLDERS", color = OrbitText, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Text("${folders.size} folders found", color = OrbitMuted, fontSize = 11.sp)
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            items(folders.keys.toList(), key = { it }) { folder ->
                                Column(Modifier.fillMaxWidth().background(OrbitPanel, RoundedCornerShape(8.dp)).padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Folder, contentDescription = null, tint = OrbitAccent)
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(folder.trimEnd('/').substringAfterLast('/'), color = OrbitText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            Text("${folders[folder]?.size ?: 0} videos • ${folder}", color = OrbitMuted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    (folders[folder] ?: emptyList()).take(3).forEach { video ->
                                        TextButton(onClick = { mediaUri = video.uri; selectedName = video.name }) { Text("▶  ${video.name}", color = OrbitText, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                    }
                                }
                            }
                        }
                    }
                } else if (currentTab == "Playlist" && mediaUri == null) {
                    Column(Modifier.fillMaxWidth().weight(1f).background(OrbitPanel, RoundedCornerShape(12.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("YOUR PLAYLISTS", color = OrbitText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Create named collections for your video library.", color = OrbitMuted, fontSize = 12.sp)
                        Button(onClick = { newPlaylistName = ""; showCreatePlaylist = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = OrbitAccent)) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("CREATE PLAYLIST")
                        }
                        if (playlists.isEmpty()) Text("No playlists yet", color = OrbitMuted, fontSize = 12.sp)
                        playlists.forEach { name ->
                            Row(Modifier.fillMaxWidth().background(OrbitBlack, RoundedCornerShape(8.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.QueueMusic, contentDescription = null, tint = OrbitAccent)
                                Spacer(Modifier.width(10.dp))
                                Text(name, color = OrbitText, modifier = Modifier.weight(1f))
                                TextButton(onClick = {
                                    playlists = playlists.filterNot { it == name }
                                    context.getSharedPreferences("orbit_player", Context.MODE_PRIVATE).edit().putStringSet("playlists", playlists.toSet()).apply()
                                }) { Text("REMOVE", color = OrbitAccent) }
                            }
                        }
                    }
                }

                if (currentTab == "Me" && mediaUri == null) {
                    Column(Modifier.fillMaxWidth().weight(1f).background(OrbitPanel, RoundedCornerShape(12.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("ABOUT ORBIT", color = OrbitText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Developer: Joy", color = OrbitMuted, fontSize = 14.sp)
                        OutlinedButton(onClick = { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com/IMALONEJOY"))) }, modifier = Modifier.fillMaxWidth()) { Text("DEVELOPER FACEBOOK", color = OrbitText) }
                        HorizontalDivider(color = OrbitLine)
                        Text("PRIVACY", color = OrbitText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Long-press any video in Library to hide it. Hidden videos are excluded from search and the main list.", color = OrbitMuted, fontSize = 12.sp)
                        Button(onClick = { pinEntry = ""; pinError = ""; showPinDialog = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = OrbitAccent)) {
                            Icon(Icons.Default.Lock, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (privacyUnlocked) "MANAGE HIDDEN VIDEOS" else "SET / ENTER PRIVACY PIN")
                        }
                        if (privacyUnlocked) {
                            Text(hiddenUris.size.toString() + " hidden videos", color = OrbitMuted, fontSize = 12.sp)
                            hiddenUris.forEach { hidden ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(hidden.substringAfterLast("/"), color = OrbitText, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    TextButton(onClick = {
                                        hiddenUris = hiddenUris - hidden
                                        context.getSharedPreferences("orbit_privacy", Context.MODE_PRIVATE).edit().putStringSet("hidden_uris", hiddenUris).apply()
                                    }) { Text("UNHIDE", color = OrbitAccent) }
                                }
                            }
                        }
                    }
                } else if (mediaUri != null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            player.pause()
                            mediaUri = null
                            selectedName = null
                        }) { Text("‹  BACK TO LIBRARY", color = OrbitText, fontSize = 11.sp, letterSpacing = 1.sp) }
                        Spacer(Modifier.weight(1f))
                        Text("NOW PLAYING", color = OrbitAccent, fontSize = 10.sp, letterSpacing = 1.sp)
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f).background(Color.Black, RoundedCornerShape(3.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = { viewContext ->
                                PlayerView(viewContext).apply {
                                    this.player = player
                                    useController = true
                                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                                }
                            },
                            update = {
                                it.player = player
                                it.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else if (currentTab == "Video") {
                    Column(
                        Modifier.fillMaxWidth().weight(1f).background(Color(0xFF050505), RoundedCornerShape(3.dp)).padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("VIDEOS ON DEVICE", color = OrbitText, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                                Text(if (hasPermission) "${videos.size} videos found" else "Allow access to scan your videos", color = OrbitMuted, fontSize = 11.sp)
                            }
                            if (hasPermission) TextButton(onClick = { refreshVideos() }) { Text("REFRESH", color = OrbitAccent, fontSize = 10.sp) }
                        }
                        Spacer(Modifier.height(8.dp))
                        when {
                            !hasPermission -> {
                                Spacer(Modifier.height(20.dp))
                                OrbitDotMark()
                                Spacer(Modifier.height(12.dp))
                                Text("LET ORBIT FIND YOUR VIDEOS", color = OrbitText, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Spacer(Modifier.height(8.dp))
                                Text("Allow video access. Your device videos will appear here; you won't need to browse folders each time.", color = OrbitMuted, fontSize = 12.sp)
                                Spacer(Modifier.height(16.dp))
                                OutlinedButton(
                                    onClick = { requestMediaPermission() },
                                    shape = RoundedCornerShape(3.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, OrbitLine),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OrbitText)
                                ) { Text("ALLOW STORAGE ACCESS", letterSpacing = 1.sp, fontSize = 11.sp) }
                            }
                            videos.isEmpty() -> {
                                Spacer(Modifier.height(28.dp))
                                Text("NO VIDEOS FOUND", color = OrbitText, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                                Spacer(Modifier.height(6.dp))
                                Text("Try Refresh, or open a video stored in another location.", color = OrbitMuted, fontSize = 12.sp)
                                Spacer(Modifier.height(12.dp))
                                OutlinedButton(onClick = { picker.launch(arrayOf("video/*")) }) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("OPEN FILE")
                                }
                            }
                            else -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    items(videos.filter { it.uri.toString() !in hiddenUris && it.name.contains(searchQuery, ignoreCase = true) }, key = { it.uri.toString() }) { video ->
                                        val thumbnail by produceState<Bitmap?>(initialValue = null, video.uri) {
                                            value = withContext(Dispatchers.IO) {
                                                if (Build.VERSION.SDK_INT >= 29) runCatching {
                                                    context.contentResolver.loadThumbnail(video.uri, Size(160, 100), null)
                                                }.getOrNull() else null
                                            }
                                        }
                                        Row(
                                            Modifier.fillMaxWidth().background(OrbitPanel, RoundedCornerShape(8.dp)).animateContentSize()
                                                .combinedClickable(onClick = { mediaUri = video.uri; selectedName = video.name }, onLongClick = { pendingHide = video; pinEntry = ""; pinError = ""; showPinDialog = true })
                                                .padding(horizontal = 12.dp, vertical = 13.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(Modifier.size(width = 88.dp, height = 58.dp).background(Color(0xFF242424), RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {
                                                if (thumbnail != null) {
                                                    Image(
                                                        bitmap = thumbnail!!.asImageBitmap(),
                                                        contentDescription = "Video thumbnail",
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                    )
                                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                                                } else {
                                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = OrbitText)
                                                }
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(video.name, color = OrbitText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                Spacer(Modifier.height(4.dp))
                                                Text("${formatDuration(video.durationMs)}  •  ${formatSize(video.size)}", color = OrbitMuted, fontSize = 10.sp)
                                            }
                                            Text("PLAY", color = OrbitAccent, fontSize = 10.sp, letterSpacing = 1.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }



                if (mediaUri == null && currentTab == "Video") Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("● ● ●", color = OrbitAccent, fontSize = 10.sp, letterSpacing = 2.sp)
                    Spacer(Modifier.weight(1f))
                    Text("DESIGNED FOR YOUR MEDIA", color = OrbitMuted, fontSize = 9.sp, letterSpacing = 1.5.sp)
                }
            }
        }
    }
    if (showCreatePlaylist) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylist = false },
            title = { Text("Create playlist") },
            text = { OutlinedTextField(value = newPlaylistName, onValueChange = { newPlaylistName = it.take(48) }, label = { Text("Playlist name") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val name = newPlaylistName.trim()
                    if (name.isNotEmpty() && name !in playlists) {
                        playlists = (playlists + name).sorted()
                        context.getSharedPreferences("orbit_player", Context.MODE_PRIVATE).edit().putStringSet("playlists", playlists.toSet()).apply()
                    }
                    showCreatePlaylist = false
                }) { Text("CREATE") }
            },
            dismissButton = { TextButton(onClick = { showCreatePlaylist = false }) { Text("CANCEL") } }
        )
    }
    if (showPinDialog) {
        val privacyPrefs = context.getSharedPreferences("orbit_privacy", Context.MODE_PRIVATE)
        val storedPin = privacyPrefs.getString("pin", null)
        AlertDialog(
            onDismissRequest = { showPinDialog = false; pendingHide = null },
            title = { Text(if (storedPin == null) "Set privacy PIN" else "Enter privacy PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (pendingHide != null) "Enter your PIN to hide this video." else "Create or enter a 4–8 digit PIN.")
                    OutlinedTextField(value = pinEntry, onValueChange = { if (it.length <= 8 && it.all { ch -> ch.isDigit() }) pinEntry = it }, label = { Text("PIN") }, singleLine = true)
                    if (pinError.isNotBlank()) Text(pinError, color = OrbitAccent)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        pinEntry.length !in 4..8 -> pinError = "PIN must be 4–8 digits"
                        storedPin == null -> {
                            privacyPrefs.edit().putString("pin", pinEntry).apply()
                            if (pendingHide != null) {
                                hiddenUris = hiddenUris + pendingHide!!.uri.toString()
                                privacyPrefs.edit().putStringSet("hidden_uris", hiddenUris).apply()
                            }
                            privacyUnlocked = true
                            pendingHide = null
                            showPinDialog = false
                        }
                        pinEntry == storedPin -> {
                            if (pendingHide != null) {
                                hiddenUris = hiddenUris + pendingHide!!.uri.toString()
                                privacyPrefs.edit().putStringSet("hidden_uris", hiddenUris).apply()
                                pendingHide = null
                            }
                            privacyUnlocked = true
                            showPinDialog = false
                        }
                        else -> pinError = "Incorrect PIN"
                    }
                }) { Text("CONFIRM") }
            },
            dismissButton = { TextButton(onClick = { showPinDialog = false; pendingHide = null }) { Text("CANCEL") } }
        )
    }
}

@Composable
private fun OrbitDotMark() {
    Canvas(Modifier.size(width = 88.dp, height = 64.dp)) {
        val radius = 2.dp.toPx()
        val gap = 9.dp.toPx()
        val cx = size.width / 2
        val cy = size.height / 2
        for (row in -3..3) for (col in -4..4) {
            val distance = kotlin.math.sqrt((row * row + col * col).toFloat())
            if (distance in 1.2f..4.9f) drawCircle(
                color = if ((row + col) % 5 == 0) OrbitAccent else OrbitText,
                radius = radius,
                center = Offset(cx + col * gap, cy + row * gap)
            )
        }
    }
}

private fun hasVideoPermission(context: Context): Boolean {
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

private fun queryVideos(context: Context): List<LocalVideo> {
    val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.SIZE,
        MediaStore.Video.Media.RELATIVE_PATH
    )
    val result = mutableListOf<LocalVideo>()
    val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"
    context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            result += LocalVideo(
                ContentUris.withAppendedId(collection, id),
                cursor.getString(nameCol) ?: "Video",
                cursor.getLong(durationCol),
                cursor.getLong(sizeCol),
                if (Build.VERSION.SDK_INT >= 29) cursor.getString(cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)) ?: "" else ""
            )
        }
    }
    return result
}


private fun queryAudios(context: Context): List<LocalAudio> {
    val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.SIZE
    )
    val result = mutableListOf<LocalAudio>()
    context.contentResolver.query(collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.DATE_ADDED} DESC")?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
        while (cursor.moveToNext()) {
            result += LocalAudio(
                ContentUris.withAppendedId(collection, cursor.getLong(idCol)),
                cursor.getString(nameCol) ?: "Audio",
                cursor.getLong(durationCol),
                cursor.getLong(sizeCol)
            )
        }
    }
    return result
}

private fun formatDuration(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

private fun formatSize(bytes: Long): String {
    return if (bytes >= 1024L * 1024L) "${bytes / (1024L * 1024L)} MB" else "${bytes / 1024L} KB"
}
