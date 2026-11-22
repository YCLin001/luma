package com.luma.lite

import android.view.KeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

private val CenterInk = Color(0xFFF8FAFD)

/** 中间区域：九宫格 / 播放器 / 歌词，三页竖向滑动，卡片外框固定不动。 */
@Composable
fun CenterPanel(
    shape: Dp,
    compact: Boolean,
    apps: List<AppEntry>,
    slots: List<String>,
    onLongPress: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val pager = rememberPagerState(
        initialPage = StartPage.center.coerceIn(0, 2),
        pageCount = { 3 }
    )
    val track = rememberNowPlaying()

    Box(modifier) {
        VerticalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 0.dp
        ) { page ->
            Box(
                Modifier
                    .fillMaxSize()
                    .glass(shape)
                    .padding(
                        start = if (compact) 14.dp else 18.dp,
                        top = if (compact) 14.dp else 18.dp,
                        bottom = if (compact) 14.dp else 18.dp,
                        end = if (compact) 22.dp else 26.dp
                    )
            ) {
                when (page) {
                    0 -> MatrixContent(compact, apps, slots, onLongPress)
                    1 -> PlayerCard(compact, track)
                    else -> LyricsCard(compact, track)
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = if (compact) 7.dp else 9.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            repeat(3) { i ->
                val on = pager.currentPage == i
                Box(
                    Modifier
                        .size(if (on) 6.dp else 4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(CenterInk.copy(alpha = if (on) 0.90f else 0.28f))
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MatrixContent(
    compact: Boolean,
    apps: List<AppEntry>,
    slots: List<String>,
    onLongPress: (Int) -> Unit
) {
    val context = LocalContext.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = minOf(maxWidth / 3, maxHeight / 3)
        val iconSide = side * 0.62f
        Column(Modifier.fillMaxSize()) {
            for (r in 0 until 3) {
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (c in 0 until 3) {
                        val index = r * 3 + c
                        val app = resolveApp(apps, slots.getOrNull(index), index + 5)
                        Box(
                            modifier = Modifier
                                .size(side)
                                .combinedClickable(
                                    onClick = { app?.let { launchApp(context, it.packageName) } },
                                    onLongClick = { onLongPress(index) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            val icon = app?.icon
                            if (icon != null) {
                                Image(
                                    bitmap = icon,
                                    contentDescription = app.label,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(iconSide)
                                        .clip(RoundedCornerShape(if (compact) 16.dp else 20.dp))
                                )
                            } else {
                                Box(
                                    Modifier
                                        .size(iconSide)
                                        .clip(RoundedCornerShape(if (compact) 16.dp else 20.dp))
                                        .background(CenterInk.copy(alpha = 0.08f))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerCard(compact: Boolean, track: Track?) {
    val context = LocalContext.current
    val pos = rememberPositionMs(track)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cover = minOf(maxWidth * 0.70f, maxHeight * 0.42f)
        val duration = track?.durationMs ?: 0L
        val frac = if (duration > 0) (pos.toFloat() / duration).coerceIn(0f, 1f) else 0f

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                Modifier
                    .size(cover)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(listOf(Color(0xFFE9A66B), Color(0xFF39465F)))
                    )
            ) {
                val art = track?.art
                if (art != null) {
                    Image(
                        bitmap = art,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = track?.title ?: "未在播放",
                    color = CenterInk,
                    fontSize = if (compact) 13.sp else 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track?.artist?.takeIf { it.isNotBlank() } ?: "打开音乐 App 试试",
                    color = CenterInk.copy(alpha = 0.60f),
                    fontSize = if (compact) 10.sp else 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.16f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(frac)
                            .fillMaxHeight()
                            .background(CenterInk.copy(alpha = 0.85f))
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatMs(pos), color = CenterInk.copy(alpha = 0.50f), fontSize = 9.sp)
                    Text(formatMs(duration), color = CenterInk.copy(alpha = 0.50f), fontSize = 9.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                KeyButton("prev", compact) {
                    dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                }
                Box(
                    modifier = Modifier
                        .size(if (compact) 40.dp else 46.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.16f))
                        .clickable { dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) },
                    contentAlignment = Alignment.Center
                ) {
                    MediaGlyph(
                        kind = if (track?.playing == true) "pause" else "play",
                        side = if (compact) 16.dp else 18.dp,
                        tint = CenterInk
                    )
                }
                KeyButton("next", compact) {
                    dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
                }
            }
        }
    }
}

@Composable
private fun KeyButton(kind: String, compact: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(if (compact) 34.dp else 38.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        MediaGlyph(
            kind = kind,
            side = if (compact) 15.dp else 17.dp,
            tint = CenterInk.copy(alpha = 0.85f)
        )
    }
}

@Composable
internal fun MediaGlyph(kind: String, side: Dp, tint: Color) {
    Canvas(Modifier.size(side)) {
        val w = size.width
        val h = size.height
        when (kind) {
            "play" -> {
                val p = Path().apply {
                    moveTo(w * 0.24f, h * 0.12f)
                    lineTo(w * 0.86f, h * 0.50f)
                    lineTo(w * 0.24f, h * 0.88f)
                    close()
                }
                drawPath(p, tint)
            }

            "pause" -> {
                val bar = Size(w * 0.18f, h * 0.74f)
                val r = CornerRadius(w * 0.06f, w * 0.06f)
                drawRoundRect(tint, Offset(w * 0.24f, h * 0.13f), bar, r)
                drawRoundRect(tint, Offset(w * 0.58f, h * 0.13f), bar, r)
            }

            "prev" -> {
                drawRect(tint, Offset(w * 0.14f, h * 0.18f), Size(w * 0.10f, h * 0.64f))
                val p = Path().apply {
                    moveTo(w * 0.86f, h * 0.16f)
                    lineTo(w * 0.34f, h * 0.50f)
                    lineTo(w * 0.86f, h * 0.84f)
                    close()
                }
                drawPath(p, tint)
            }

            else -> {
                drawRect(tint, Offset(w * 0.76f, h * 0.18f), Size(w * 0.10f, h * 0.64f))
                val p = Path().apply {
                    moveTo(w * 0.14f, h * 0.16f)
                    lineTo(w * 0.66f, h * 0.50f)
                    lineTo(w * 0.14f, h * 0.84f)
                    close()
                }
                drawPath(p, tint)
            }
        }
    }
}

@Composable
private fun LyricsCard(compact: Boolean, track: Track?) {
    val context = LocalContext.current
    var lines by remember { mutableStateOf<List<LrcLine>>(emptyList()) }
    var status by remember { mutableStateOf("") }

    LaunchedEffect(track?.title, track?.artist) {
        val t = track
        if (t == null) {
            lines = emptyList()
            status = "未在播放"
            return@LaunchedEffect
        }
        status = "匹配歌词中…"
        val found = LyricsRepo.get(context, t.title, t.artist)
        lines = found ?: emptyList()
        status = if (found == null) "这首歌没找到歌词" else ""
    }

    val pos = rememberPositionMs(track)
    val current = if (lines.isEmpty()) 0 else lines.indexOfLast { it.timeMs <= pos }.coerceAtLeast(0)

    if (lines.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = status.ifBlank { "未在播放" },
                color = CenterInk.copy(alpha = 0.55f),
                fontSize = if (compact) 11.sp else 12.sp
            )
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            for (d in -2..2) {
                val i = current + d
                val line = lines.getOrNull(i)
                val active = d == 0
                Text(
                    text = line?.text ?: "",
                    color = CenterInk.copy(
                        alpha = if (active) 0.96f else (0.40f - 0.09f * abs(d)).coerceAtLeast(0.10f)
                    ),
                    fontSize = if (active) {
                        if (compact) 14.sp else 16.sp
                    } else {
                        if (compact) 10.sp else 12.sp
                    },
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.padding(vertical = if (active) 5.dp else 2.dp)
                )
            }
        }
    }
}
