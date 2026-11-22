package com.luma.lite

import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Paper = Color(0xFFF8FAFD)

/** 三张卡上下滑动切换：音乐 / 日历 / 通知。 */
@Composable
fun SmartStack(shape: Dp, compact: Boolean, modifier: Modifier = Modifier) {
    val pager = rememberPagerState(
        initialPage = StartPage.stack.coerceIn(0, 2),
        pageCount = { 3 }
    )
    val context = LocalContext.current
    var events by remember { mutableStateOf<List<CalEvent>>(emptyList()) }
    var calGranted by remember { mutableStateOf(false) }

    LaunchedEffect(pager.currentPage) {
        events = refreshCalendar(context)
        calGranted = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    Box(modifier) {
        VerticalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 0.dp
        ) { page ->
            Box(
                Modifier
                    .fillMaxSize()
                    .glass(shape, fill = 0.15f)
                    .padding(
                        start = if (compact) 14.dp else 18.dp,
                        end = if (compact) 24.dp else 30.dp,
                        top = if (compact) 14.dp else 18.dp,
                        bottom = if (compact) 14.dp else 18.dp
                    )
            ) {
                when (page) {
                    0 -> MusicCard(compact)
                    1 -> CalendarCard(compact, events, calGranted)
                    else -> NotifCard(compact)
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = if (compact) 8.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            repeat(3) { i ->
                val on = pager.currentPage == i
                Box(
                    Modifier
                        .size(if (on) 6.dp else 4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Paper.copy(alpha = if (on) 0.90f else 0.28f))
                )
            }
        }
    }
}

@Composable
private fun MusicCard(compact: Boolean) {
    val np = rememberNowPlaying()
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(if (compact) 44.dp else 52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(listOf(Color(0xFFE9A66B), Color(0xFF39465F)))
                    )
            ) {
                val art = np?.art
                if (art != null) {
                    Image(
                        bitmap = art,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Column(Modifier.padding(start = 10.dp)) {
                Text(
                    text = np?.title?.takeIf { it.isNotBlank() } ?: "暂无播放",
                    color = Paper,
                    fontSize = if (compact) 13.sp else 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = np?.artist?.takeIf { it.isNotBlank() } ?: "打开 QQ音乐 后这里会亮",
                    color = Paper.copy(alpha = 0.66f),
                    fontSize = if (compact) 10.sp else 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.16f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.42f)
                    .fillMaxHeight()
                    .background(Paper.copy(alpha = 0.80f))
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                MediaGlyph("prev", 12.dp, Paper.copy(alpha = 0.85f))
                MediaGlyph(if (np?.playing == true) "pause" else "play", 12.dp, Paper)
                MediaGlyph("next", 12.dp, Paper.copy(alpha = 0.85f))
            }
            Text(
                text = np?.packageName?.let { shortPkg(it) } ?: "音乐",
                color = Paper.copy(alpha = 0.50f),
                fontSize = 10.sp
            )
        }
    }
}

private fun shortPkg(pkg: String): String = when {
    pkg.contains("qqmusic") -> "QQ音乐"
    pkg.contains("netease") -> "网易云"
    pkg.contains("kugou") -> "酷狗"
    pkg.contains("kuwo") -> "酷我"
    pkg.contains("spotify") -> "Spotify"
    pkg.contains("apple") -> "Apple Music"
    pkg.contains("migu") -> "咪咕"
    pkg.contains("qianqian") -> "千千"
    else -> pkg.substringAfterLast('.')
}

@Composable
private fun CalendarCard(compact: Boolean, events: List<CalEvent>, granted: Boolean) {
    val today = remember { SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(Date()) }
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(if (compact) 34.dp else 40.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color.White.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = SimpleDateFormat("dd", Locale.CHINA).format(Date()),
                    color = Paper,
                    fontSize = if (compact) 14.sp else 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Column(Modifier.padding(start = 9.dp)) {
                Text("今天", color = Paper, fontSize = if (compact) 12.sp else 14.sp, fontWeight = FontWeight.Medium)
                Text(today, color = Paper.copy(alpha = 0.58f), fontSize = 10.sp)
            }
        }

        when {
            !granted -> Text(
                "还没给日历权限，授权后这里显示今天日程",
                color = Paper.copy(alpha = 0.60f),
                fontSize = if (compact) 11.sp else 12.sp
            )

            events.isEmpty() -> Text(
                "今日无日程，享受悠闲时光吧",
                color = Paper.copy(alpha = 0.72f),
                fontSize = if (compact) 11.sp else 12.sp
            )

            else -> Column(verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 9.dp)) {
                events.take(3).forEach { e ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Paper.copy(alpha = 0.65f))
                        )
                        Text(
                            text = "  ${eventTimeText(e)}  ",
                            color = Paper.copy(alpha = 0.58f),
                            fontSize = 10.sp
                        )
                        Text(
                            text = e.title,
                            color = Paper,
                            fontSize = if (compact) 11.sp else 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotifCard(compact: Boolean) {
    val items = Hub.notifications.value
    var dismissed by remember { mutableStateOf<Set<String>>(emptySet()) }
    val visible = items.filterNot { it.key in dismissed }

    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "通知",
                color = Paper,
                fontSize = if (compact) 12.sp else 14.sp,
                fontWeight = FontWeight.Medium
            )
            if (visible.isNotEmpty()) {
                Text(
                    "  ·  ${visible.size} 条",
                    color = Paper.copy(alpha = 0.52f),
                    fontSize = 10.sp
                )
            }
        }

        if (visible.isEmpty()) {
            Text(
                "没有通知，或者还没授权通知使用权",
                color = Paper.copy(alpha = 0.60f),
                fontSize = if (compact) 11.sp else 12.sp
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
                visible.take(3).forEach { n ->
                    NotifRow(n, compact) { dismissed = dismissed + n.key }
                }
            }
        }
    }
}

/** 单条通知：右滑清除，滑过 72dp 就清掉。 */
@Composable
private fun NotifRow(n: NotifItem, compact: Boolean, onCleared: () -> Unit) {
    val density = LocalDensity.current
    val clearPx = with(density) { 72.dp.toPx() }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var gone by remember { mutableStateOf(false) }

    val offsetX by animateFloatAsState(
        targetValue = if (gone) 2600f else if (dragging) dragX else 0f,
        animationSpec = if (dragging) snap() else spring(dampingRatio = 0.85f, stiffness = 700f),
        label = "notifSwipe",
        finishedListener = { v ->
            if (gone && v > 2500f) {
                Hub.dismiss(n.key)
                onCleared()
            }
        }
    )

    Column(
        Modifier
            .fillMaxWidth()
            .offset { IntOffset(offsetX.roundToInt(), 0) }
            .clip(RoundedCornerShape(8.dp))
            .background(Paper.copy(alpha = 0.06f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .pointerInput(n.key) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragX = 0f
                        dragging = true
                    },
                    onDragEnd = {
                        dragging = false
                        if (dragX > clearPx) gone = true else dragX = 0f
                    },
                    onDragCancel = {
                        dragging = false
                        dragX = 0f
                    },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        dragX = (dragX + amount).coerceIn(0f, 2400f)
                    }
                )
            }
    ) {
        Text(
            text = n.appLabel,
            color = Paper.copy(alpha = 0.55f),
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = n.title.ifBlank { n.text },
            color = Paper,
            fontSize = if (compact) 11.sp else 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
