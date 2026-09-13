package com.nonitv.app

import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class Channel(val id: String, val name: String, val streamUrl: String, val category: String)

object M3UParser {
    private val client = OkHttpClient()
    suspend fun fetchChannels(m3uUrl: String): List<Channel> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Channel>()
        try {
            val request = Request.Builder().url(m3uUrl).build()
            val response = client.newCall(request).execute()
            val content = response.body?.string() ?: ""
            var currentName = ""
            var currentGroup = "عام"
            var id = 1
            content.lines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("#EXTINF:")) {
                    currentName = trimmed.substringAfterLast(",").ifEmpty { "قناة" }
                    if (trimmed.contains("group-title=\"")) {
                        currentGroup = trimmed.substringAfter("group-title=\"").substringBefore("\"")
                    }
                } else if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                    if (currentName.isNotEmpty()) {
                        list.add(Channel(id.toString(), currentName, trimmed, currentGroup))
                        id++
                        currentName = ""
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return@withContext list
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                NoniTvHomeScreen()
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(streamUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember(streamUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(streamUrl))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    player = exoPlayer
                    useController = true
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = modifier
        )
    ) { onDispose { exoPlayer.release() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoniTvHomeScreen() {
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var selectedChannel by remember { mutableStateOf<Channel?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // يمكنك تغيير رابط قائمة القنوات M3U هنا
    val playlistUrl = "https://iptv-org.github.io/iptv/index.m3u"

    LaunchedEffect(Unit) {
        val data = M3UParser.fetchChannels(playlistUrl)
        if (data.isNotEmpty()) {
            channels = data
            selectedChannel = data.first()
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Noni tv", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF121212))
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.Red)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding).background(Color.Black)) {
                selectedChannel?.let { channel ->
                    Box(modifier = Modifier.fillMaxWidth().height(230.dp).background(Color.DarkGray)) {
                        VideoPlayer(streamUrl = channel.streamUrl)
                    }
                }
                Text("القنوات المباشرة", style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.padding(16.dp))
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(channels) { channel ->
                        val isSelected = channel.id == selectedChannel?.id
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable { selectedChannel = channel },
                            colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFE50914) else Color(0xFF1E1E1E))
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = channel.name, color = Color.White)
                                Text(text = channel.category, color = Color.LightGray)
                            }
                        }
                    }
                }
            }
        }
    }
}
