package com.orbit.video

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

private val OrbitBlack = Color(0xFF090B0D)
private val OrbitPanel = Color(0xFF171A1D)
private val OrbitGreen = Color(0xFFB8FF3B)
private val OrbitText = Color(0xFFF2F4F5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
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
        primary = OrbitGreen, background = OrbitBlack, surface = OrbitPanel,
        onPrimary = OrbitBlack, onBackground = OrbitText, onSurface = OrbitText
    )) {
        Scaffold(
            containerColor = OrbitBlack,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("ORBIT V!DEO", fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                            Text("LOCAL MEDIA PLAYER", fontSize = 10.sp, color = OrbitGreen, letterSpacing = 2.sp)
                        }
                    },
                    actions = {
                        IconButton(onClick = { picker.launch(arrayOf("video/*", "audio/*")) }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Open media", tint = OrbitGreen)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = OrbitBlack, titleContentColor = OrbitText)
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f).background(Color.Black, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (mediaUri != null) {
                        AndroidView(
                            factory = { viewContext ->
                                androidx.media3.ui.PlayerView(viewContext).apply {
                                    this.player = player
                                    useController = true
                                    setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                                }
                            },
                            update = { it.player = player },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("◉", color = OrbitGreen, fontSize = 64.sp, fontWeight = FontWeight.Light)
                            Spacer(Modifier.height(12.dp))
                            Text("Your media. Your orbit.", color = OrbitText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            Text("Open a local video or audio file to begin", color = Color.Gray, fontSize = 12.sp)
                            Spacer(Modifier.height(20.dp))
                            Button(onClick = { picker.launch(arrayOf("video/*", "audio/*")) },
                                colors = ButtonDefaults.buttonColors(containerColor = OrbitGreen, contentColor = OrbitBlack)) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("OPEN MEDIA", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = OrbitPanel), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("NOW PLAYING", color = OrbitGreen, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                                Text(mediaUri?.lastPathSegment ?: "Nothing queued", color = OrbitText, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                            Surface(color = Color(0xFF252A2E), shape = RoundedCornerShape(8.dp)) {
                                Text("${playbackSpeed}×", modifier = Modifier.padding(8.dp), color = OrbitGreen, fontSize = 12.sp)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            FilledIconButton(
                                onClick = { if (player.isPlaying) player.pause() else player.play() },
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = OrbitGreen, contentColor = OrbitBlack),
                                enabled = mediaUri != null
                            ) {
                                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play or pause")
                            }
                            TextButton(onClick = {
                                playbackSpeed = when (playbackSpeed) { 1f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 2f; else -> 1f }
                                player.setPlaybackSpeed(playbackSpeed)
                            }) { Text("SPEED", color = OrbitGreen, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
                Text("ORBIT / BUILD 001", modifier = Modifier.fillMaxWidth(), color = Color.Gray, fontSize = 10.sp, letterSpacing = 2.sp)
            }
        }
    }
}
