package com.luma.lite

import android.content.ComponentName
import android.content.Context
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class Track(
    val title: String,
    val artist: String,
    val album: String,
    val art: ImageBitmap?,
    val durationMs: Long,
    val positionMs: Long,
    val playing: Boolean,
    val packageName: String,
    val fetchedAt: Long
)

private fun activeController(context: Context): MediaController? {
    val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        ?: return null
    val cn = ComponentName(context, LumaNotifService::class.java)
    val sessions = try {
        msm.getActiveSessions(cn)
    } catch (t: Throwable) {
        return null
    }
    if (sessions.isNullOrEmpty()) return null

    // QQ音乐 / 网易云 / 酷狗 / Apple Music / Spotify 都会注册 MediaSession。
    // 多会话并存时：优先正在播放的，其次最近更新过状态的。
    return sessions
        .filter { it.metadata != null }
        .sortedWith(
            compareByDescending<MediaController> { scoreOf(it) }
        )
        .firstOrNull()
}

private fun scoreOf(controller: MediaController): Int {
    val state = controller.playbackState?.state ?: return 0
    val base = when (state) {
        PlaybackState.STATE_PLAYING -> 400
        PlaybackState.STATE_BUFFERING,
        PlaybackState.STATE_CONNECTING -> 300
        PlaybackState.STATE_PAUSED -> 200
        PlaybackState.STATE_STOPPED,
        PlaybackState.STATE_NONE -> 100
        else -> 50
    }
    val age = SystemClock.elapsedRealtime() - (controller.playbackState?.lastPositionUpdateTime ?: 0L)
    val freshness = when {
        age < 60_000L -> 40
        age < 600_000L -> 20
        else -> 0
    }
    return base + freshness
}

private val artCache = mutableMapOf<String, ImageBitmap>()

private fun loadUriBitmap(context: Context, uri: String?): ImageBitmap? {
    if (uri.isNullOrBlank()) return null
    artCache[uri]?.let { return it }
    return try {
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
            BitmapFactory.decodeStream(input)?.asImageBitmap()?.also { artCache[uri] = it }
        }
    } catch (t: Throwable) {
        null
    }
}

fun readTrack(context: Context): Track? {
    val controller = activeController(context) ?: return null
    val md: MediaMetadata = controller.metadata ?: return null
    val state = controller.playbackState
    val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)
        ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        ?: return null
    val art: ImageBitmap? = try {
        md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)?.asImageBitmap()
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)?.asImageBitmap()
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)?.asImageBitmap()
            ?: loadUriBitmap(context, md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI))
            ?: loadUriBitmap(context, md.getString(MediaMetadata.METADATA_KEY_ART_URI))
            ?: loadUriBitmap(context, md.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI))
            ?: Hub.mediaArt.value
    } catch (t: Throwable) {
        null
    }
    return Track(
        title = title,
        artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty(),
        album = md.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
        art = art,
        durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0L),
        positionMs = (state?.position ?: 0L).coerceAtLeast(0L),
        playing = state?.state == PlaybackState.STATE_PLAYING,
        packageName = controller.packageName ?: "",
        fetchedAt = SystemClock.elapsedRealtime()
    )
}

fun dispatchMediaKey(context: Context, keyCode: Int) {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    runCatching {
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }
}

@Composable
fun rememberNowPlaying(): Track? {
    val context = LocalContext.current
    var track by remember { mutableStateOf<Track?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            val next = withContext(Dispatchers.IO) { readTrack(context) }
            track = next
            delay(900)
        }
    }
    return track
}

/** 播放位置：用上次读到的 position 加上本地流逝时间，避免每帧去问系统。 */
@Composable
fun rememberPositionMs(track: Track?): Long {
    var tick by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(track?.title, track?.playing) {
        while (true) {
            tick = SystemClock.elapsedRealtime()
            delay(250)
        }
    }
    val t = track ?: return 0L
    val raw = if (t.playing) t.positionMs + (tick - t.fetchedAt) else t.positionMs
    val cap = if (t.durationMs > 0) t.durationMs else Long.MAX_VALUE
    return raw.coerceIn(0L, cap)
}

fun formatMs(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}
