package com.orbit.video

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8, 8, 8)
        window.navigationBarColor = android.graphics.Color.rgb(8, 8, 8)
        setContent { OrbitApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrbitApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var mediaUri by remember { mutableStateOf<Uri?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var showTrackInfo by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            mediaUri = uri
        }
    }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            })
        }
    }
    LaunchedEffect(mediaUri) {
        mediaUri?.let {
            player.setMediaItem(MediaItem.fromUri(it))
            player.prepare()
            player.playWhenReady = true
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
                Row(
                    Modifier.fillMaxWidth().background(OrbitBlack)
                        .padding(start = 22.dp, end = 12.dp, top = 20.dp, bottom = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("ORBIT", color = OrbitText, fontWeight = FontWeight.Black, letterSpacing = 4.sp, fontSize = 27.sp)
                        Text("V!DEO PLAYER  /  001", fontSize = 10.sp, color = OrbitMuted, letterSpacing = 2.sp)
                    }
                    Box(Modifier.size(34.dp).background(OrbitAccent, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                        Text("●", color = OrbitBlack, fontSize = 15.sp)
                    }
                    IconButton(onClick = { picker.launch(arrayOf("video/*", "audio/*")) }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Open media", tint = OrbitText)
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("NOW / PLAYING", color = OrbitMuted, fontSize = 10.sp, letterSpacing = 2.sp)
                    Spacer(Modifier.weight(1f))
                    Text("●  LOCAL MEDIA", color = OrbitAccent, fontSize = 10.sp, letterSpacing = 1.sp)
                }

                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f)
                        .background(Color(0xFF050505), RoundedCornerShape(3.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (mediaUri != null) {
                        AndroidView(
                            factory = { viewContext ->
                                PlayerView(viewContext).apply {
                                    this.player = player
                                    useController = true
                                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                                }
                            },
                            update = { it.player = player },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            OrbitDotMark()
                            Spacer(Modifier.height(22.dp))
                            Text("NOTHING TO PLAY", color = OrbitText, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Choose a file to enter your orbit.", color = OrbitMuted, fontSize = 12.sp)
                            Spacer(Modifier.height(24.dp))
                            OutlinedButton(
                                onClick = { picker.launch(arrayOf("video/*", "audio/*")) },
                                shape = RoundedCornerShape(3.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, OrbitLine),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = OrbitText)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("BROWSE FILES", letterSpacing = 1.5.sp, fontSize = 11.sp)
                            }
                        }
                    }
                }

                Column(
                    Modifier.fillMaxWidth().background(OrbitPanel, RoundedCornerShape(3.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("CURRENT FILE", color = OrbitMuted, fontSize = 10.sp, letterSpacing = 2.sp)
                            Spacer(Modifier.height(5.dp))
                            Text(mediaUri?.lastPathSegment ?: "No file selected", color = OrbitText, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                        Text("\${playbackSpeed}×", color = OrbitText, fontSize = 13.sp,
                            modifier = Modifier.background(OrbitBlack, RoundedCornerShape(3.dp)).padding(9.dp))
                    }
                    HorizontalDivider(color = OrbitLine, thickness = 1.dp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        FilledIconButton(
                            onClick = { if (player.isPlaying) player.pause() else player.play() },
                            enabled = mediaUri != null,
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = OrbitText, contentColor = OrbitBlack),
                            shape = RoundedCornerShape(3.dp)
                        ) {
                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play or pause")
                        }
                        OutlinedButton(
                            onClick = {
                                playbackSpeed = when (playbackSpeed) { 1f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 2f; else -> 1f }
                                player.setPlaybackSpeed(playbackSpeed)
                            },
                            shape = RoundedCornerShape(3.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, OrbitLine),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = OrbitText)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("SPEED", fontSize = 11.sp, letterSpacing = 1.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { showTrackInfo = !showTrackInfo }) {
                            Icon(Icons.Default.Subtitles, contentDescription = "Audio and subtitle tracks", tint = if (showTrackInfo) OrbitAccent else OrbitMuted)
                        }
                    }
                    if (showTrackInfo) {
                        Text("Audio and subtitle track selection will be added in the next build.", color = OrbitMuted, fontSize = 11.sp)
                    }
                }

                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("● ● ●", color = OrbitAccent, fontSize = 10.sp, letterSpacing = 2.sp)
                    Spacer(Modifier.weight(1f))
                    Text("DESIGNED FOR YOUR MEDIA", color = OrbitMuted, fontSize = 9.sp, letterSpacing = 1.5.sp)
                }
            }
        }
    }
}

@Composable
private fun OrbitDotMark() {
    Canvas(Modifier.size(width = 104.dp, height = 82.dp)) {
        val radius = 2.1.dp.toPx()
        val gap = 10.dp.toPx()
        val cx = size.width / 2
        val cy = size.height / 2
        for (row in -3..3) {
            for (col in -4..4) {
                val distance = kotlin.math.sqrt((row * row + col * col).toFloat())
                if (distance in 1.2f..4.9f) {
                    drawCircle(
                        color = if ((row + col) % 5 == 0) OrbitAccent else OrbitText,
                        radius = radius,
                        center = Offset(cx + col * gap, cy + row * gap)
                    )
                }
            }
        }
    }
}
