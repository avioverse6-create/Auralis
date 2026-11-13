package com.auralis.player

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executors

data class Song(val id: Long, val title: String, val artist: String, val uri: Uri, val duration: Long)

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        setContent { AuralisApp(this) }
    }
    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }
    fun play(song: Song, songs: List<Song>) {
        val future = controllerFuture ?: return
        if (!future.isDone) return
        val player = future.get()
        player.setMediaItems(songs.map { MediaItem.fromUri(it.uri) }, songs.indexOf(song), 0L)
        player.prepare()
        player.play()
    }
}

private val palettes = listOf(
    Color(0xFF6750A4), Color(0xFF006A60), Color(0xFF0061A4),
    Color(0xFF9A4057), Color(0xFF745B00), Color(0xFF465D91)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuralisApp(activity: MainActivity) {
    var songs by remember { mutableStateOf(emptyList<Song>()) }
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) }
    var selectedColor by remember { mutableIntStateOf(0) }
    var dark by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var selectedSong by remember { mutableStateOf<Song?>(null) }
    var fontScale by remember { mutableFloatStateOf(1f) }
    var bold by remember { mutableStateOf(false) }
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    fun scan() {
        val result = mutableListOf<Song>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION)
        activity.contentResolver.query(collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.TITLE} ASC")?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val duration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (c.moveToNext()) {
                val songId = c.getLong(id)
                result += Song(songId, c.getString(title) ?: "Unknown", c.getString(artist) ?: "Unknown artist",
                    Uri.withAppendedPath(collection, songId.toString()), c.getLong(duration))
            }
        }
        songs = result
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) scan() }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED) scan()
    }
    val scheme = if (dark) darkColorScheme(primary = palettes[selectedColor], secondary = palettes[selectedColor].copy(alpha=.8f), surface = Color(0xFF111318), background = Color(0xFF111318))
        else lightColorScheme(primary = palettes[selectedColor], secondary = palettes[selectedColor], surface = Color(0xFFF8F7FC), background = Color(0xFFF8F7FC))
    MaterialTheme(colorScheme = scheme) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    listOf("Library", "Playlists", "Settings").forEachIndexed { i, label ->
                        NavigationBarItem(selected = tab == i, onClick = { tab = i; if (i == 2) showSettings = true },
                            icon = { Icon(if (i == 0) Icons.Rounded.LibraryMusic else if (i == 1) Icons.Rounded.QueueMusic else Icons.Rounded.Settings, null) },
                            label = { Text(label) })
                    }
                }
            },
            topBar = {
                TopAppBar(title = {
                    Column {
                        Text("Auralis", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                        Text("YOUR MUSIC, YOUR MOMENT", fontSize = 10.sp, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }, actions = {
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Rounded.Tune, "Settings") }
                })
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp)) {
                if (tab == 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(query, { query = it }, Modifier.weight(1f), placeholder = { Text("Search your library") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true, shape = RoundedCornerShape(24.dp))
                        FilledTonalIconButton(onClick = { if (ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED) scan() else launcher.launch(permission) }) { Icon(Icons.Rounded.Refresh, "Scan") }
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Your library", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${songs.size} tracks", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(10.dp))
                    if (songs.isEmpty()) {
                        Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Rounded.LibraryMusic, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(12.dp))
                            Text("Your music lives here", style = MaterialTheme.typography.titleLarge)
                            Text("Allow access to scan audio on your phone", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { launcher.launch(permission) }) { Text("Find my music") }
                        }
                    } else {
                        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            itemsIndexed(songs.filter { it.title.contains(query, true) || it.artist.contains(query, true) }) { _, song ->
                                ListItem(
                                    headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium, fontSize = (16 * fontScale).sp) },
                                    supportingContent = { Text(song.artist, maxLines = 1) },
                                    leadingContent = { Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(palettes[selectedColor], palettes[selectedColor].copy(alpha=.35f)))), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.MusicNote, null, tint = Color.White) } },
                                    trailingContent = { Text(formatTime(song.duration), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable { selectedSong = song; activity.play(song, songs) }
                                )
                            }
                        }
                    }
                } else if (tab == 1) {
                    Text("Playlists", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    ListItem(headlineContent = { Text("All songs") }, supportingContent = { Text("${songs.size} tracks") }, leadingContent = { Icon(Icons.Rounded.QueueMusic, null) })
                    Text("Playlist creation and queue management are coming in the next iteration.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
                } else {
                    Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Personalize your listening experience", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (showSettings) {
            ModalBottomSheet(onDismissRequest = { showSettings = false }) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Appearance", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Dark theme", Modifier.weight(1f)); Switch(dark, { dark = it }) }
                    Text("Material You color", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        palettes.forEachIndexed { i, color -> Box(Modifier.size(38.dp).clip(CircleShape).background(color).clickable { selectedColor = i }, contentAlignment = Alignment.Center) { if (selectedColor == i) Icon(Icons.Rounded.Check, null, tint = Color.White) } }
                    }
                    Text("Text size", style = MaterialTheme.typography.titleMedium)
                    Slider(fontScale, { fontScale = it }, valueRange = .85f..1.2f)
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Bold song titles", Modifier.weight(1f)); Switch(bold, { bold = it }) }
                    Spacer(Modifier.height(12.dp))
                    Text("Auralis • Material Design 3", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        selectedSong?.let { song ->
            ModalBottomSheet(onDismissRequest = { selectedSong = null }) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(220.dp).clip(RoundedCornerShape(32.dp)).background(Brush.linearGradient(listOf(palettes[selectedColor], Color(0xFF17152B)))), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Album, null, Modifier.size(100.dp), tint = Color.White)
                    }
                    Spacer(Modifier.height(22.dp))
                    Text(song.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
                    Text(song.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(24.dp))
                    PlayerControls(activity)
                    Spacer(Modifier.height(28.dp))
                }
            }
        }
    }
}

@Composable
private fun PlayerControls(activity: MainActivity) {
    var playing by remember { mutableStateOf(true) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        IconButton(onClick = { activity.controllerFutureCompat()?.let { it.seekToPreviousMediaItem() } }) { Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(34.dp)) }
        FilledIconButton(onClick = { activity.controllerFutureCompat()?.let { if (it.isPlaying) it.pause() else it.play(); playing = it.isPlaying } }, modifier = Modifier.size(68.dp), shape = CircleShape) {
            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play pause", Modifier.size(38.dp))
        }
        IconButton(onClick = { activity.controllerFutureCompat()?.let { it.seekToNextMediaItem() } }) { Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(34.dp)) }
    }
}

private fun MainActivity.controllerFutureCompat(): MediaController? = try {
    val f = javaClass.getDeclaredField("controllerFuture").apply { isAccessible = true }.get(this) as? ListenableFuture<MediaController>
    if (f?.isDone == true) f.get() else null
} catch (_: Exception) { null }

private fun formatTime(ms: Long): String = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)
