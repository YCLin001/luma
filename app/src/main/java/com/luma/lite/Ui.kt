package com.luma.lite

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ink = Color(0xFFF8FAFD)
private val GlassTint = Color(0xFF0C121C)
private val ChargeGreen = Color(0xFF3DDC84)

internal fun Modifier.glass(
    shape: Dp,
    fill: Float = 0.14f,
    stroke: Float = 0.34f
): Modifier = this
    .clip(RoundedCornerShape(shape))
    .background(GlassTint.copy(alpha = 0.20f))
    .background(Color.White.copy(alpha = fill))
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = stroke),
                Color.White.copy(alpha = stroke * 0.12f),
                Color.White.copy(alpha = stroke * 0.45f)
            )
        ),
        shape = RoundedCornerShape(shape)
    )

private data class EditTarget(val kind: String, val index: Int)

internal fun resolveApp(apps: List<AppEntry>, custom: String?, fallback: Int): AppEntry? {
    if (!custom.isNullOrBlank()) {
        apps.firstOrNull { it.packageName == custom }?.let { return it }
    }
    return apps.getOrNull(fallback)
}

@Composable
fun NatureBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color(0xFF101A2E),
                0.30f to Color(0xFF2C3C58),
                0.52f to Color(0xFF7E6B72),
                0.62f to Color(0xFFE0A377),
                0.72f to Color(0xFFF3C79A),
                0.84f to Color(0xFF2A3448),
                1f to Color(0xFF121A2A)
            )
        )
        drawCircle(
            color = Color(0xFFFFE0B0).copy(alpha = 0.55f),
            radius = h * 0.055f,
            center = Offset(w * 0.63f, h * 0.605f)
        )
        val far = Path().apply {
            moveTo(0f, h * 0.66f)
            lineTo(w * 0.10f, h * 0.55f)
            lineTo(w * 0.22f, h * 0.63f)
            lineTo(w * 0.36f, h * 0.47f)
            lineTo(w * 0.47f, h * 0.58f)
            lineTo(w * 0.58f, h * 0.44f)
            lineTo(w * 0.70f, h * 0.57f)
            lineTo(w * 0.82f, h * 0.50f)
            lineTo(w * 0.93f, h * 0.60f)
            lineTo(w, h * 0.55f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(far, Color(0xFF1B2536).copy(alpha = 0.92f))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LumaHome() {
    val apps = rememberLauncherApps()
    val context = LocalContext.current
    val battery = rememberBattery()
    val btDevices = rememberBtDevices()

    var wallpaper by remember { mutableStateOf<ImageBitmap?>(null) }
    var hasCustom by remember { mutableStateOf(Prefs.wallpaperFile(context).exists()) }
    var dockSlots by remember { mutableStateOf(run {
        Prefs.seedDockDefaults(context)
        Prefs.dockSlots(context)
    }) }
    var matrixSlots by remember { mutableStateOf(Prefs.matrixSlots(context)) }
    var edit by remember { mutableStateOf<EditTarget?>(null) }
    var retained by remember { mutableStateOf<EditTarget?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var cityOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var scrimReady by remember { mutableStateOf(false) }
    val overlayVisible = edit != null
    val blurRadius by animateDpAsState(
        targetValue = if (overlayVisible) 18.dp else 0.dp,
        animationSpec = tween(240),
        label = "backdropBlur"
    )

    LaunchedEffect(edit) {
        if (edit != null) {
            retained = edit
            // 等手指抬起来再允许点遮罩关闭，否则长按松手会立刻把面板关掉
            delay(350)
            scrimReady = true
        } else {
            scrimReady = false
        }
    }

    BackHandler(enabled = overlayVisible) { edit = null }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null && Prefs.saveWallpaperFromUri(context, uri)) {
            hasCustom = true
            wallpaper = Prefs.loadCustomWallpaper(context)
        }
    }

    val btPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch {
                refreshWeather(context, Prefs.weatherCity(context).ifBlank { null })
            }
        }
    }

    LaunchedEffect(Unit) {
        val locGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!locGranted) {
            permLauncher.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
            delay(1000)
        }
        val btGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.BLUETOOTH_CONNECT
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!btGranted) {
            btPerm.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    LaunchedEffect(Unit) {
        wallpaper = withContext(Dispatchers.IO) {
            Prefs.loadCustomWallpaper(context) ?: loadSystemWallpaper(context)
        }
        refreshWeather(context, Prefs.weatherCity(context).ifBlank { null })
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier)
        ) {
            val ratio = maxWidth.value / maxHeight.value
            val compact = ratio >= 1.85f
            val pad = if (compact) 14.dp else 22.dp
            val gap = if (compact) 10.dp else 14.dp
            val shape = if (compact) 22.dp else 26.dp

            val wp = wallpaper
            if (wp != null) {
                Image(
                    bitmap = wp,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.36f)))
            } else {
                NatureBackdrop(Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f)))
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(pad),
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                Column(
                    modifier = Modifier
                        .weight(if (compact) 0.34f else 0.37f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(gap)
                ) {
                    ClockPanel(shape, compact, battery, Modifier.weight(0.42f).fillMaxWidth())
                    SmartStack(shape, compact, Modifier.weight(0.58f).fillMaxWidth())
                }

                Column(
                    modifier = Modifier
                        .weight(if (compact) 0.36f else 0.37f)
                        .fillMaxHeight()
                ) {
                    CenterPanel(
                        shape = shape,
                        compact = compact,
                        apps = apps,
                        slots = matrixSlots,
                        onLongPress = { edit = EditTarget("matrix", it) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(if (compact) 0.30f else 0.26f)
                        .fillMaxHeight()
                ) {
                    val showBt = btDevices.isNotEmpty() && maxWidth > 178.dp
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(
                            if (compact) 8.dp else 10.dp,
                            Alignment.End
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showBt) {
                            BtCard(
                                devices = btDevices.take(3),
                                compact = compact,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                        DockPanel(
                            shape = shape,
                            compact = compact,
                            apps = apps,
                            slots = dockSlots,
                            onLongPress = { edit = EditTarget("dock", it) },
                            onMore = { menuOpen = true }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = overlayVisible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f))
                    .clickable(enabled = scrimReady) { edit = null }
            )
        }

        AnimatedVisibility(
            visible = overlayVisible,
            enter = fadeIn(tween(200)) + scaleIn(
                initialScale = 0.88f,
                animationSpec = tween(260, easing = FastOutSlowInEasing)
            ),
            exit = fadeOut(tween(160)) + scaleOut(
                targetScale = 0.92f,
                animationSpec = tween(180)
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            val t = retained
            if (t != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AppPickerPanel(
                        apps = apps,
                        title = if (t.kind == "dock") {
                            "换 Dock 第 ${t.index + 1} 个图标"
                        } else {
                            "换第 ${t.index + 1} 个图标"
                        },
                        onPick = { pkg ->
                            if (t.kind == "dock") {
                                Prefs.setDockSlot(context, t.index, pkg)
                                dockSlots = Prefs.dockSlots(context)
                            } else {
                                Prefs.setMatrixSlot(context, t.index, pkg)
                                matrixSlots = Prefs.matrixSlots(context)
                            }
                            edit = null
                        },
                        onReset = {
                            if (t.kind == "dock") {
                                Prefs.setDockSlot(context, t.index, null)
                                dockSlots = Prefs.dockSlots(context)
                            } else {
                                Prefs.setMatrixSlot(context, t.index, null)
                                matrixSlots = Prefs.matrixSlots(context)
                            }
                            edit = null
                        },
                        onDismiss = { edit = null }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = menuOpen,
            enter = fadeIn(tween(160)) + scaleIn(
                initialScale = 0.94f,
                animationSpec = tween(200, easing = FastOutSlowInEasing)
            ),
            exit = fadeOut(tween(140)) + scaleOut(
                targetScale = 0.96f,
                animationSpec = tween(150)
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.44f))
                    .clickable { menuOpen = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    Modifier
                        .width(300.dp)
                        .glass(20.dp, fill = 0.18f, stroke = 0.42f)
                        .padding(vertical = 6.dp)
                ) {
                    MenuRow("换壁纸（从相册选）") {
                        menuOpen = false
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                    MenuDivider()
                    MenuRow("用系统壁纸") {
                        menuOpen = false
                        Prefs.clearWallpaper(context)
                        hasCustom = false
                        scope.launch {
                            wallpaper = withContext(Dispatchers.IO) { loadSystemWallpaper(context) }
                        }
                    }
                    MenuDivider()
                    MenuRow("更换城市") {
                        menuOpen = false
                        cityOpen = true
                    }
                    MenuDivider()
                    MenuRow("关闭") { menuOpen = false }
                }
            }
        }

        AnimatedVisibility(
            visible = cityOpen,
            enter = fadeIn(tween(160)) + scaleIn(
                initialScale = 0.94f,
                animationSpec = tween(200, easing = FastOutSlowInEasing)
            ),
            exit = fadeOut(tween(140)) + scaleOut(
                targetScale = 0.96f,
                animationSpec = tween(150)
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.44f))
                        .clickable { cityOpen = false }
                )
                CityPickerPanel(
                    current = Prefs.weatherCity(context),
                    onPick = { picked ->
                        Prefs.setWeatherCity(context, picked)
                        cityOpen = false
                        scope.launch {
                            refreshWeather(context, picked.ifBlank { null })
                        }
                    },
                    onDismiss = { cityOpen = false }
                )
            }
        }
    }
}

@Composable
private fun ClockPanel(shape: Dp, compact: Boolean, battery: BatteryInfo, modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(10_000)
        }
    }
    val time = remember(now) { SimpleDateFormat("HH:mm", Locale.CHINA).format(now) }
    val date = remember(now) { SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(now) }

    BoxWithConstraints(
        modifier.glass(shape).padding(if (compact) 16.dp else 20.dp)
    ) {
        val cw = maxWidth.value
        val ch = maxHeight.value
        val clockSp = minOf(ch * 0.40f, cw / 4.5f).coerceIn(26f, 96f)
        val subSp = (clockSp * 0.26f).coerceIn(10f, 22f)

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = time,
                    color = Ink,
                    fontSize = clockSp.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-1.5).sp,
                    maxLines = 1
                )
                val w = Hub.weather.value
                val failed = Hub.weatherFailed.value
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = w?.let { "${it.temp}°" } ?: "--°",
                        color = Ink,
                        fontSize = subSp.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "  " + (
                            w?.let { "${it.desc} · ${it.city}" }
                                ?: if (failed) "未定位 · 菜单里选城市" else "同步中"
                            ),
                        color = Ink.copy(alpha = 0.80f),
                        fontSize = subSp.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (battery.charging) {
                        Text("⚡", color = ChargeGreen, fontSize = (subSp * 1.05f).sp)
                        Text(
                            " 充电中 ",
                            color = ChargeGreen,
                            fontSize = (subSp * 0.86f).sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Text(
                            "电量 ",
                            color = Ink.copy(alpha = 0.72f),
                            fontSize = (subSp * 0.86f).sp
                        )
                    }
                    Text(
                        "${battery.level}%",
                        color = Ink,
                        fontSize = (subSp * 0.92f).sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = date,
                    color = Ink.copy(alpha = 0.70f),
                    fontSize = (subSp * 0.86f).sp
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppMatrixPanel(
    shape: Dp,
    compact: Boolean,
    apps: List<AppEntry>,
    slots: List<String>,
    onLongPress: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Box(
        modifier
            .glass(shape)
            .padding(if (compact) 14.dp else 18.dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val cols = 3
            val rows = 3
            val side = minOf(maxWidth / cols, maxHeight / rows)
            val iconSide = side * 0.62f

            Column(Modifier.fillMaxSize()) {
                for (r in 0 until rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (c in 0 until cols) {
                            val index = r * cols + c
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
                                if (app != null) {
                                    Image(
                                        bitmap = app.icon!!,
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
                                            .background(Color.White.copy(alpha = 0.08f))
                                    )
                                }
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
private fun DockPanel(
    shape: Dp,
    compact: Boolean,
    apps: List<AppEntry>,
    slots: List<String>,
    onLongPress: (Int) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    BoxWithConstraints(modifier) {
        val maxTile = if (compact) 44.dp else 50.dp
        val reserved = if (compact) 22.dp else 30.dp
        val tile = minOf(maxTile, (maxHeight - reserved) / 7.3f)
        val gap = tile * 0.16f
        Column(
            modifier = Modifier
                .width(if (compact) 68.dp else 78.dp)
                .glass(shape + 6.dp, fill = 0.16f, stroke = 0.44f)
                .padding(vertical = if (compact) 10.dp else 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
        for (i in 0 until Prefs.DOCK_SLOTS) {
            val app = resolveApp(apps, slots.getOrNull(i), i)
            Box(
                modifier = Modifier
                    .size(tile)
                    .combinedClickable(
                        onClick = { app?.let { launchApp(context, it.packageName) } },
                        onLongClick = { onLongPress(i) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (app != null) {
                    Image(
                        bitmap = app.icon!!,
                        contentDescription = app.label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(tile)
                            .clip(RoundedCornerShape(if (compact) 13.dp else 16.dp))
                    )
                } else {
                    Box(
                        Modifier
                            .size(tile)
                            .clip(RoundedCornerShape(if (compact) 13.dp else 16.dp))
                            .background(Color.White.copy(alpha = 0.10f))
                    )
                }
            }
        }
        Box(
            Modifier
                .width(if (compact) 26.dp else 34.dp)
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.22f))
        )
        Box(
            modifier = Modifier
                .size(tile)
                .glass(if (compact) 13.dp else 16.dp, fill = 0.18f, stroke = 0.40f)
                .clickable { onMore() },
            contentAlignment = Alignment.Center
        ) {
            Text("⋯", color = Ink, fontSize = if (compact) 15.sp else 17.sp)
        }
    }
    }
}

@Composable
private fun CityPickerPanel(
    current: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth(0.46f)
            .fillMaxHeight(0.86f)
            .glass(22.dp, fill = 0.18f, stroke = 0.44f)
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("选择城市", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                "关闭",
                color = Ink.copy(alpha = 0.85f),
                fontSize = 11.sp,
                modifier = Modifier.clickable { onDismiss() }
            )
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(top = 6.dp)) {
            item {
                CityRow(
                    label = "自动定位（当前位置）",
                    selected = current.isBlank(),
                    onClick = { onPick("") }
                )
            }
            items(CITIES) { city ->
                CityRow(
                    label = city.name,
                    selected = current == city.name,
                    onClick = { onPick(city.name) }
                )
            }
        }
    }
}

@Composable
private fun CityRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (selected) Ink else Ink.copy(alpha = 0.80f),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
        if (selected) {
            Spacer(Modifier.weight(1f))
            Text("当前", color = Ink.copy(alpha = 0.48f), fontSize = 9.sp)
        }
    }
}

@Composable
private fun MenuRow(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = Ink,
        fontSize = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    )
}

@Composable
private fun MenuDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(0.6.dp)
            .background(Color.White.copy(alpha = 0.10f))
    )
}

@Composable
private fun AppPickerPanel(
    apps: List<AppEntry>,
    title: String,
    onPick: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth(0.80f)
            .fillMaxHeight(0.90f)
            .glass(24.dp, fill = 0.18f, stroke = 0.44f)
            .padding(18.dp)
    ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        "恢复默认",
                        color = Ink.copy(alpha = 0.78f),
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { onReset() }
                    )
                    Text(
                        "关闭",
                        color = Ink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { onDismiss() }
                    )
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 76.dp),
                modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(apps) { app ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onPick(app.packageName) }
                    ) {
                        Image(
                            bitmap = app.icon!!,
                            contentDescription = app.label,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(13.dp))
                        )
                        Text(
                            text = app.label,
                            color = Ink.copy(alpha = 0.80f),
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
}
