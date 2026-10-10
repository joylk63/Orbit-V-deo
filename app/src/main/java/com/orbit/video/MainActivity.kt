package com.orbit.video

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.View
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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.IconButton
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder

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
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var mediaUri by remember { mutableStateOf<Uri?>(null) }
    var selectedName by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var showTrackInfo by remember { mutableStateOf(false) }
    var videos by remember { mutableStateOf<List<LocalVideo>>(emptyList()) }
    var audios by remember { mutableStateOf<List<LocalAudio>>(emptyList()) }
    var hasPermission by remember { mutableStateOf(hasVideoPermission(context)) }
    var searchQuery by remember { mutableStateOf("") }
    val prefs = remember { context.getSharedPreferences("orbit_player", Context.MODE_PRIVATE) }
    var themeName by remember { mutableStateOf(prefs.getString("theme_name", "Orbit Red") ?: "Orbit Red") }
    val palette = when (themeName) {
        "Matrix Green" -> listOf(Color(0xFF050A07), Color(0xFF101B14), Color(0xFF00C878), Color(0xFFF0FFF5), Color(0xFF8DA99A), Color(0xFF294638))
        "Ice Blue" -> listOf(Color(0xFF06090D), Color(0xFF111923), Color(0xFF42B8FF), Color(0xFFF2F8FF), Color(0xFF91A5B8), Color(0xFF2A3C4D))
        "Amber" -> listOf(Color(0xFF0B0905), Color(0xFF1D1710), Color(0xFFFFB547), Color(0xFFFFF7E8), Color(0xFFB5A48A), Color(0xFF413222))
        else -> listOf(Color(0xFF080808), Color(0xFF141414), Color(0xFFE53935), Color(0xFFF4F4F0), Color(0xFF92928D), Color(0xFF303030))
    }
    val OrbitBlack by animateColorAsState(palette[0], animationSpec = tween(350), label = "theme-background")
    val OrbitPanel by animateColorAsState(palette[1], animationSpec = tween(350), label = "theme-panel")
    val OrbitAccent by animateColorAsState(palette[2], animationSpec = tween(350), label = "theme-accent")
    val OrbitText by animateColorAsState(palette[3], animationSpec = tween(350), label = "theme-text")
    val OrbitMuted by animateColorAsState(palette[4], animationSpec = tween(350), label = "theme-muted")
    val OrbitLine by animateColorAsState(palette[5], animationSpec = tween(350), label = "theme-line")
    var gridView by remember { mutableStateOf(prefs.getBoolean("video_grid_view", false)) }
    var currentTab by remember { mutableStateOf("Video") }
    var themeSectionExpanded by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var controlsLocked by remember { mutableStateOf(false) }
    var fillVideo by remember { mutableStateOf(false) }
    var speedMenuExpanded by remember { mutableStateOf(false) }
    var playerMenuExpanded by remember { mutableStateOf(false) }
    var landscapeMode by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinEntry by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf("") }
    var pendingHide by remember { mutableStateOf<LocalVideo?>(null) }
    var privacyUnlocked by remember { mutableStateOf(false) }
    var hiddenUris by remember { mutableStateOf(context.getSharedPreferences("orbit_privacy", Context.MODE_PRIVATE).getStringSet("hidden_uris", emptySet())?.toSet() ?: emptySet()) }
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
        controlsLocked = false
        (context as? ComponentActivity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        landscapeMode = false
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
    DisposableEffect(mediaUri, isLandscape) {
        val activity = context as? ComponentActivity
        val decor = activity?.window?.decorView
        val previousVisibility = decor?.systemUiVisibility ?: 0
        if (mediaUri != null && isLandscape) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
            decor?.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        } else {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
            decor?.systemUiVisibility = previousVisibility
        }
        onDispose {
            decor?.systemUiVisibility = previousVisibility
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = OrbitText, secondary = OrbitAccent, background = OrbitBlack,
        surface = OrbitPanel, onPrimary = OrbitBlack, onBackground = OrbitText, onSurface = OrbitText
    )) {
        Scaffold(
            containerColor = OrbitBlack,
            topBar = {
                if (mediaUri == null) {
                    Row(
                        Modifier.fillMaxWidth().background(OrbitBlack)
                            .statusBarsPadding()
                            .padding(start = 22.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("ORBIT", color = OrbitText, fontWeight = FontWeight.Black, letterSpacing = 4.sp, fontSize = 24.sp)
                            Text("V!DEO PLAYER  /  002", fontSize = 10.sp, color = OrbitMuted, letterSpacing = 2.sp)
                        }
                        Image(
                            painter = painterResource(id = R.drawable.orbit_logo),
                            contentDescription = "Orbit V!deo logo",
                            modifier = Modifier.size(42.dp)
                        )
                        IconButton(onClick = { if (!hasPermission) requestMediaPermission() else refreshVideos() }) {
                            Icon(Icons.Default.VideoLibrary, contentDescription = "Refresh video library", tint = OrbitText)
                        }
                    }
                }
            },
            bottomBar = {
                if (mediaUri == null) {
                    val navItems = listOf(
                        Triple("Video", Icons.Default.VideoLibrary, "Video"),
                        Triple("Music", Icons.Default.MusicNote, "Music"),
                        Triple("Folder", Icons.Default.Folder, "Folder"),
                        Triple("Me", Icons.Default.Person, "Me")
                    )
                    val selectedIndex = navItems.indexOfFirst { it.first == currentTab }.coerceAtLeast(0)
                    Surface(color = OrbitPanel, tonalElevation = 0.dp) {
                        BoxWithConstraints(
                            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            val itemWidth = maxWidth / navItems.size
                            val indicatorX by androidx.compose.animation.core.animateDpAsState(
                                targetValue = itemWidth * selectedIndex,
                                animationSpec = androidx.compose.animation.core.tween(
                                    durationMillis = 420,
                                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                                ),
                                label = "nav-pill-position"
                            )
                            Box(
                                Modifier.offset(x = indicatorX).width(itemWidth).height(42.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(OrbitText)
                            )
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                navItems.forEach { item ->
                                    val selected = currentTab == item.first
                                    val iconTint by animateColorAsState(
                                        targetValue = if (selected) OrbitBlack else OrbitMuted,
                                        animationSpec = tween(220),
                                        label = "nav-icon-tint-\${item.first}"
                                    )
                                    val labelTint by animateColorAsState(
                                        targetValue = if (selected) OrbitText else OrbitMuted,
                                        animationSpec = tween(220),
                                        label = "nav-label-tint-\${item.first}"
                                    )
                                    Column(
                                        Modifier.weight(1f).height(54.dp)
                                            .clip(RoundedCornerShape(50))
                                            .clickable { currentTab = item.first; privacyUnlocked = false },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(item.second, contentDescription = item.third, tint = iconTint, modifier = Modifier.size(21.dp))
                                        Text(item.third, color = labelTint, fontSize = 10.sp, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) { padding ->
            AnimatedContent(
                targetState = if (mediaUri != null) "Player" else currentTab,
                modifier = Modifier.fillMaxSize().padding(padding),
                transitionSpec = {
                    (fadeIn(animationSpec = tween(150)) + slideInHorizontally(animationSpec = tween(150)) { it / 40 }) togetherWith
                        (fadeOut(animationSpec = tween(110)) + slideOutHorizontally(animationSpec = tween(110)) { -it / 50 })
                },
                label = "orbit-screen-transition"
            ) { tab ->
            Column(
                modifier = Modifier.fillMaxSize()
                    .padding(horizontal = if (mediaUri != null) 0.dp else 18.dp, vertical = if (mediaUri != null) 0.dp else 8.dp)
                    .animateContentSize(animationSpec = tween(150)),
                verticalArrangement = Arrangement.spacedBy(if (mediaUri != null) 0.dp else 12.dp)
            ) {
                if (mediaUri == null && (tab == "Video" || tab == "Music")) {
                    OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, modifier = Modifier.fillMaxWidth().animateContentSize(), placeholder = { Text("Search videos", color = OrbitMuted) }, leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = OrbitMuted) }, singleLine = true, shape = RoundedCornerShape(10.dp), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = OrbitText, unfocusedTextColor = OrbitText, focusedBorderColor = OrbitAccent, unfocusedBorderColor = OrbitLine, cursorColor = OrbitAccent))
                }
                if (mediaUri == null && (tab == "Video" || tab == "Music")) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (mediaUri == null) "YOUR LIBRARY" else "NOW / PLAYING", color = OrbitMuted, fontSize = 10.sp, letterSpacing = 2.sp)
                    Spacer(Modifier.weight(1f))
                    Text("●  LOCAL MEDIA", color = OrbitAccent, fontSize = 10.sp, letterSpacing = 1.sp)
                }

                if (tab == "Music" && mediaUri == null) {
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
                } else if (tab == "Folder" && mediaUri == null) {
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
                }

                if (tab == "Me" && mediaUri == null) {
                    Column(Modifier.fillMaxWidth().weight(1f).background(OrbitPanel, RoundedCornerShape(12.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .clickable { themeSectionExpanded = !themeSectionExpanded }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = OrbitAccent)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("APPEARANCE", color = OrbitText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("Theme: $themeName", color = OrbitMuted, fontSize = 12.sp)
                            }
                            Text(if (themeSectionExpanded) "−" else "+", color = OrbitAccent, fontSize = 22.sp)
                        }
                        AnimatedContent(targetState = themeSectionExpanded, transitionSpec = {
                            fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                        }, label = "theme-options") { expanded ->
                            if (expanded) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    listOf("Orbit Red", "Matrix Green", "Ice Blue", "Amber").forEach { option ->
                                        FilterChip(
                                            selected = themeName == option,
                                            onClick = { themeName = option; prefs.edit().putString("theme_name", option).apply() },
                                            label = { Text(option) },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
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
                        HorizontalDivider(color = OrbitLine)
                        Text("ABOUT ORBIT", color = OrbitText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Developer: Joy", color = OrbitMuted, fontSize = 14.sp)
                        OutlinedButton(onClick = { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com/IMALONEJOY"))) }, modifier = Modifier.fillMaxWidth()) { Text("DEVELOPER FACEBOOK", color = OrbitText) }
                    }
                } else if (mediaUri != null) {
                    if (!isLandscape) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                player.pause()
                                mediaUri?.let { prefs.edit().putLong("position:" + it.toString(), player.currentPosition.coerceAtLeast(0L)).apply() }
                                mediaUri = null
                                selectedName = null
                                controlsLocked = false
                                (context as? ComponentActivity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                landscapeMode = false
                            }) { Text("‹", color = OrbitText, fontSize = 24.sp) }
                            Text(selectedName ?: "NOW PLAYING", color = OrbitMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            TextButton(onClick = { runCatching { TrackSelectionDialogBuilder(context, "HDR / Audio tracks", player, C.TRACK_TYPE_AUDIO).build().show() } }) { Text("♫", color = OrbitText, fontSize = 18.sp) }
                            TextButton(onClick = { runCatching { TrackSelectionDialogBuilder(context, "Subtitles / CC", player, C.TRACK_TYPE_TEXT).build().show() } }) { Text("CC", color = OrbitText, fontSize = 10.sp) }
                            Box {
                                TextButton(onClick = { playerMenuExpanded = true }) { Text("⋮", color = OrbitText, fontSize = 22.sp) }
                                DropdownMenu(expanded = playerMenuExpanded, onDismissRequest = { playerMenuExpanded = false }, containerColor = OrbitPanel) {
                                    DropdownMenuItem(text = { Text("Playback speed", color = OrbitText) }, onClick = { playerMenuExpanded = false; speedMenuExpanded = true })
                                    DropdownMenuItem(text = { Text(if (fillVideo) "Fit video" else "Fill screen", color = OrbitText) }, onClick = { fillVideo = !fillVideo; playerMenuExpanded = false })
                                    DropdownMenuItem(text = { Text(if (isLandscape) "Portrait orientation" else "Landscape orientation", color = OrbitText) }, onClick = {
                                        landscapeMode = !isLandscape
                                        (context as? ComponentActivity)?.requestedOrientation = if (!isLandscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        playerMenuExpanded = false
                                    })
                                }
                            }
                            DropdownMenu(expanded = speedMenuExpanded, onDismissRequest = { speedMenuExpanded = false }, containerColor = OrbitPanel) {
                                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { speed ->
                                    DropdownMenuItem(text = { Text("${speed}×", color = if (playbackSpeed == speed) OrbitAccent else OrbitText) }, onClick = { playbackSpeed = speed; player.setPlaybackSpeed(speed); speedMenuExpanded = false })
                                }
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black), contentAlignment = Alignment.Center) {
                        AndroidView(
                            factory = { viewContext -> PlayerView(viewContext).apply {
                                this.player = player
                                useController = true
                                controllerAutoShow = true
                                resizeMode = if (fillVideo) androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM else androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                                setShutterBackgroundColor(android.graphics.Color.BLACK)
                            } },
                            update = { it.player = player; it.resizeMode = if (fillVideo) androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM else androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT; it.useController = !controlsLocked },
                            modifier = Modifier.fillMaxSize()
                        )
                        if (!controlsLocked) {
                            Column(Modifier.align(Alignment.CenterStart).padding(start = 6.dp).background(Color(0x77000000), RoundedCornerShape(10.dp)), horizontalAlignment = Alignment.CenterHorizontally) {
                                TextButton(onClick = { player.volume = if (isMuted) 1f else 0f; isMuted = !isMuted }) { Text(if (isMuted) "🔇" else "🔊", color = Color.White, fontSize = 20.sp) }
                                TextButton(onClick = { controlsLocked = true }) { Text("🔒", color = Color.White, fontSize = 20.sp) }
                            }
                            Column(Modifier.align(Alignment.CenterEnd).padding(end = 6.dp).background(Color(0x77000000), RoundedCornerShape(10.dp)), horizontalAlignment = Alignment.CenterHorizontally) {
                                TextButton(onClick = { fillVideo = !fillVideo }) { Text("✂", color = Color.White, fontSize = 22.sp) }
                                TextButton(onClick = {
                                    val nextLandscape = !isLandscape
                                    landscapeMode = nextLandscape
                                    (context as? ComponentActivity)?.requestedOrientation = if (nextLandscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                }) { Text("▣", color = Color.White, fontSize = 22.sp) }
                            }
                            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = if (isLandscape) 2.dp else 6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(formatDuration(player.currentPosition), color = Color.White, fontSize = 10.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Slider(
                                        value = if (player.duration > 0L) player.currentPosition.coerceIn(0L, player.duration).toFloat() / player.duration.toFloat() else 0f,
                                        onValueChange = { fraction -> if (player.duration > 0L) player.seekTo((fraction * player.duration).toLong()) },
                                        modifier = Modifier.weight(1f).height(22.dp),
                                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color(0xFF27C786), inactiveTrackColor = OrbitMuted)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(formatDuration(player.duration.coerceAtLeast(0L)), color = Color.White, fontSize = 10.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                                    TextButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) { Text(if (isPlaying) "Ⅱ" else "▶", color = Color.White, fontSize = 22.sp) }
                                    TextButton(onClick = { player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L)) }) { Text("◀|", color = Color.White, fontSize = 16.sp) }
                                    TextButton(onClick = { player.seekTo(player.currentPosition + 10_000L) }) { Text("|▶", color = Color.White, fontSize = 16.sp) }
                                    TextButton(onClick = { speedMenuExpanded = true }) { Text("Speed ${playbackSpeed}×", color = OrbitText, fontSize = 12.sp) }
                                    TextButton(onClick = { runCatching { TrackSelectionDialogBuilder(context, "Audio track", player, C.TRACK_TYPE_AUDIO).build().show() } }) { Text("♫", color = Color.White, fontSize = 20.sp) }
                                    TextButton(onClick = { runCatching { TrackSelectionDialogBuilder(context, "Subtitles", player, C.TRACK_TYPE_TEXT).build().show() } }) { Text("CC", color = Color.White, fontSize = 12.sp) }
                                    TextButton(onClick = {
                                        val nextLandscape = !isLandscape
                                        landscapeMode = nextLandscape
                                        (context as? ComponentActivity)?.requestedOrientation = if (nextLandscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                    }) { Text("⛶", color = Color.White, fontSize = 20.sp) }
                                }
                            }
                        } else {
                            Button(onClick = { controlsLocked = false }, modifier = Modifier.align(Alignment.Center), colors = ButtonDefaults.buttonColors(containerColor = Color(0xAA202020))) { Text("🔒 UNLOCK", color = Color.White) }
                        }
                    }
                } else if (tab == "Video") {
                    Column(
                        Modifier.fillMaxWidth().weight(1f).background(Color(0xFF050505), RoundedCornerShape(3.dp)).padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("VIDEOS ON DEVICE", color = OrbitText, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                                Text(if (hasPermission) "${videos.size} videos found" else "Allow access to scan your videos", color = OrbitMuted, fontSize = 11.sp)
                            }
                            if (hasPermission) {
                                IconButton(onClick = {
                                    gridView = !gridView
                                    prefs.edit().putBoolean("video_grid_view", gridView).apply()
                                }) {
                                    Icon(
                                        imageVector = if (gridView) Icons.Default.ViewList else Icons.Default.GridView,
                                        contentDescription = if (gridView) "Switch to list view" else "Switch to grid view",
                                        tint = OrbitAccent
                                    )
                                }
                                TextButton(onClick = { refreshVideos() }) { Text("REFRESH", color = OrbitAccent, fontSize = 10.sp) }
                            }
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
                                val filteredVideos = videos.filter { it.uri.toString() !in hiddenUris && it.name.contains(searchQuery, ignoreCase = true) }
                                if (gridView) {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(2),
                                        modifier = Modifier.fillMaxWidth().weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(9.dp),
                                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                                    ) {
                                        items(filteredVideos.size, key = { filteredVideos[it].uri.toString() }) { index ->
                                            val video = filteredVideos[index]
                                            val thumbnail by produceState<Bitmap?>(initialValue = null, video.uri) {
                                                value = withContext(Dispatchers.IO) {
                                                    if (Build.VERSION.SDK_INT >= 29) runCatching {
                                                        context.contentResolver.loadThumbnail(video.uri, Size(240, 160), null)
                                                    }.getOrNull() else null
                                                }
                                            }
                                            Column(
                                                Modifier.fillMaxWidth().background(OrbitPanel, RoundedCornerShape(8.dp)).animateContentSize()
                                                    .combinedClickable(onClick = { mediaUri = video.uri; selectedName = video.name }, onLongClick = { pendingHide = video; pinEntry = ""; pinError = ""; showPinDialog = true })
                                                    .padding(8.dp)
                                            ) {
                                                Box(Modifier.fillMaxWidth().aspectRatio(1.24f).background(Color(0xFF242424), RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                                                    if (thumbnail != null) Image(
                                                        bitmap = thumbnail!!.asImageBitmap(),
                                                        contentDescription = "Video thumbnail",
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                    )
                                                    Box(Modifier.align(Alignment.Center).background(Color(0x77000000), RoundedCornerShape(50)).padding(4.dp)) {
                                                        Icon(Icons.Default.PlayArrow, contentDescription = "Play video", tint = Color.White)
                                                    }
                                                }
                                                Spacer(Modifier.height(8.dp))
                                                Text(video.name, color = OrbitText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, minLines = 2)
                                                Spacer(Modifier.height(4.dp))
                                                Text("${formatDuration(video.durationMs)} • ${formatSize(video.size)}", color = OrbitMuted, fontSize = 9.sp, maxLines = 1)
                                            }
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxWidth().weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(7.dp)
                                    ) {
                                        items(filteredVideos, key = { it.uri.toString() }) { video ->
                                            val thumbnail by produceState<Bitmap?>(initialValue = null, video.uri) {
                                                value = withContext(Dispatchers.IO) {
                                                    if (Build.VERSION.SDK_INT >= 29) runCatching {
                                                        context.contentResolver.loadThumbnail(video.uri, Size(240, 160), null)
                                                    }.getOrNull() else null
                                                }
                                            }
                                            Row(
                                                Modifier.fillMaxWidth().background(OrbitPanel, RoundedCornerShape(8.dp)).animateContentSize()
                                                    .combinedClickable(onClick = { mediaUri = video.uri; selectedName = video.name }, onLongClick = { pendingHide = video; pinEntry = ""; pinError = ""; showPinDialog = true })
                                                    .padding(horizontal = 12.dp, vertical = 13.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(Modifier.size(width = 136.dp, height = 90.dp).background(Color(0xFF242424), RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {
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
                }



                if (mediaUri == null && tab == "Video") Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("● ● ●", color = OrbitAccent, fontSize = 10.sp, letterSpacing = 2.sp)
                    Spacer(Modifier.weight(1f))
                    Text("DESIGNED FOR YOUR MEDIA", color = OrbitMuted, fontSize = 9.sp, letterSpacing = 1.5.sp)
                }
            }
            }
        }
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
