package com.luma.lite

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.location.Geocoder
import android.location.LocationManager
import android.provider.CalendarContract
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class NowPlaying(
    val title: String,
    val artist: String,
    val packageName: String,
    val isPlaying: Boolean
)

data class NotifItem(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val time: Long
)

data class CalEvent(
    val title: String,
    val begin: Long,
    val allDay: Boolean
)

data class WeatherInfo(
    val city: String,
    val temp: Int,
    val desc: String
)

/** 进程内共享状态：通知监听和 UI 之间靠它通信。 */
object Hub {
    val nowPlaying = mutableStateOf<NowPlaying?>(null)
    val notifications = mutableStateOf<List<NotifItem>>(emptyList())
    val weather = mutableStateOf<WeatherInfo?>(null)
    val weatherFailed = mutableStateOf(false)
    val listenerGranted = mutableStateOf(false)

    /** 媒体通知里的大图，很多播放器只把封面放在这里。 */
    val mediaArt = mutableStateOf<ImageBitmap?>(null)

    /** 通知监听服务实例，用来撤销单条通知。 */
    var listener: LumaNotifService? = null

    fun dismiss(key: String) {
        runCatching { listener?.cancelNotification(key) }
        notifications.value = notifications.value.filterNot { it.key == key }
    }
}

class LumaNotifService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        Hub.listener = this
        runCatching { Hub.listenerGranted.value = true }
        snapshot()
    }

    override fun onDestroy() {
        if (Hub.listener === this) Hub.listener = null
        super.onDestroy()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        runCatching { Hub.listenerGranted.value = false }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        snapshot()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        snapshot()
    }

    private fun snapshot() {
        val active: Array<StatusBarNotification> = runCatching {
            activeNotifications ?: return
        }.getOrNull() ?: return

        val pm = packageManager
        val mine = packageName

        val items = active
            .asSequence()
            .filter { it.packageName != mine }
            .filter { it.isClearable }
            .sortedByDescending { it.postTime }
            .map { sbn ->
                val extras = sbn.notification.extras
                NotifItem(
                    key = sbn.key,
                    packageName = sbn.packageName,
                    appLabel = runCatching {
                        pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
                    }.getOrDefault(sbn.packageName),
                    title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                    text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
                    time = sbn.postTime
                )
            }
            .toList()

        Hub.notifications.value = items

        val media = active.firstOrNull { isMedia(it) }
        if (media != null) {
            @Suppress("DEPRECATION")
            val art: Any? = media.notification.extras.getParcelable(Notification.EXTRA_LARGE_ICON)
                ?: media.notification.extras.getParcelable(Notification.EXTRA_LARGE_ICON_BIG)
            if (art is Bitmap) {
                Hub.mediaArt.value = art.asImageBitmap()
            }
        }
        Hub.nowPlaying.value = media?.let { sbn ->
            val extras = sbn.notification.extras
            NowPlaying(
                title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                    ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
                artist = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
                    ?: extras.getString(Notification.EXTRA_TEXT).orEmpty(),
                packageName = sbn.packageName,
                isPlaying = sbn.notification.actions?.any { it.title?.contains("暂停") == true } == true
            )
        }
    }

    private fun isMedia(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        val template = n.extras.getString(Notification.EXTRA_TEMPLATE)
        return n.category == Notification.CATEGORY_TRANSPORT ||
            n.extras.containsKey("android.mediaSession") ||
            (template != null && template.contains("MediaStyle"))
    }
}

data class City(val name: String, val lat: Double, val lon: Double)

val CITIES = listOf(
    City("北京", 39.9042, 116.4074),
    City("上海", 31.2304, 121.4737),
    City("广州", 23.1291, 113.2644),
    City("深圳", 22.5431, 114.0579),
    City("成都", 30.5728, 104.0668),
    City("杭州", 30.2741, 120.1551),
    City("重庆", 29.5630, 106.5516),
    City("西安", 34.3416, 108.9398),
    City("武汉", 30.5928, 114.3055),
    City("南京", 32.0603, 118.7969),
    City("苏州", 31.2989, 120.5853),
    City("天津", 39.3434, 117.3616),
    City("长沙", 28.2282, 112.9388),
    City("郑州", 34.7466, 113.6254),
    City("青岛", 36.0671, 120.3826),
    City("厦门", 24.4798, 118.0894)
)

fun cityByName(name: String): City? = CITIES.firstOrNull { it.name == name }

private fun wmoToText(code: Int): String = when (code) {
    0 -> "晴"
    1 -> "晴间多云"
    2 -> "多云"
    3 -> "阴"
    45, 48 -> "雾"
    51, 53, 55 -> "毛毛雨"
    56, 57 -> "冻雨"
    61, 63, 65 -> "雨"
    66, 67 -> "冻雨"
    71, 73, 75 -> "雪"
    77 -> "雪粒"
    80, 81, 82 -> "阵雨"
    85, 86 -> "阵雪"
    95 -> "雷雨"
    96, 99 -> "雷暴冰雹"
    else -> "多云"
}

fun fetchWeather(lat: Double, lon: Double, label: String): WeatherInfo? {
    val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
        "&current=temperature_2m,weather_code&timezone=Asia%2FShanghai"
    return runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            requestMethod = "GET"
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val current = JSONObject(body).getJSONObject("current")
        WeatherInfo(
            city = label,
            temp = current.getDouble("temperature_2m").toInt(),
            desc = wmoToText(current.getInt("weather_code"))
        )
    }.getOrNull()
}

/** 拿系统里最近一次定位（城市级够用，不主动请求持续定位）。 */
fun readLastKnownLocation(context: Context): android.location.Location? {
    val granted = ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    if (!granted) return null
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    var best: android.location.Location? = null
    listOf(
        LocationManager.NETWORK_PROVIDER,
        LocationManager.GPS_PROVIDER,
        LocationManager.PASSIVE_PROVIDER
    ).forEach { provider ->
        val loc = runCatching { lm.getLastKnownLocation(provider) }.getOrNull() ?: return@forEach
        val current = best
        if (current == null || loc.time > current.time) best = loc
    }
    return best
}

/** 反查城市名，失败返回 null（界面会显示「当前位置」）。 */
fun cityNameAt(context: Context, lat: Double, lon: Double): String? = runCatching {
    val geocoder = Geocoder(context, Locale.CHINA)
    val address = geocoder.getFromLocation(lat, lon, 1)?.firstOrNull() ?: return@runCatching null
    address.locality ?: address.subAdminArea ?: address.adminArea
}.getOrNull()

fun loadTodayEvents(context: Context): List<CalEvent> {
    val granted = ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.READ_CALENDAR
    ) == PackageManager.PERMISSION_GRANTED
    if (!granted) return emptyList()

    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val begin = cal.timeInMillis
    val end = begin + 24L * 60 * 60 * 1000

    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
        .appendPath(begin.toString())
        .appendPath(end.toString())
        .build()

    val projection = arrayOf(
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.ALL_DAY
    )

    return runCatching {
        val out = mutableListOf<CalEvent>()
        context.contentResolver.query(uri, projection, null, null, CalendarContract.Instances.BEGIN + " ASC")
            ?.use { c ->
                while (c.moveToNext() && out.size < 5) {
                    out += CalEvent(
                        title = c.getString(0) ?: "未命名",
                        begin = c.getLong(1),
                        allDay = c.getInt(2) == 1
                    )
                }
            }
        out
    }.getOrDefault(emptyList())
}

fun eventTimeText(e: CalEvent): String {
    if (e.allDay) return "全天"
    return SimpleDateFormat("HH:mm", Locale.CHINA).format(java.util.Date(e.begin))
}

fun loadSystemWallpaper(context: Context): ImageBitmap? {
    return try {
        val wm = android.app.WallpaperManager.getInstance(context)
        val d: Drawable? = wm.drawable
        if (d == null) {
            null
        } else {
            val bmp: Bitmap = d.toBitmap()
            bmp.copy(Bitmap.Config.ARGB_8888, false)?.asImageBitmap()
        }
    } catch (t: Throwable) {
        null
    }
}

/**
 * 刷新天气：manualCity 非空就按手动城市取，否则定位自动识别。
 * 定位或反查失败时置 weatherFailed，界面提示去菜单里选城市。
 */
suspend fun refreshWeather(context: Context, manualCity: String?): Boolean {
    val info = withContext(Dispatchers.IO) {
        val manual = manualCity?.let { cityByName(it) }
        if (manual != null) {
            fetchWeather(manual.lat, manual.lon, manual.name)
        } else {
            val loc = readLastKnownLocation(context)
            if (loc == null) {
                null
            } else {
                val name = cityNameAt(context, loc.latitude, loc.longitude) ?: "当前位置"
                fetchWeather(loc.latitude, loc.longitude, name)
            }
        }
    }
    if (info != null) {
        Hub.weather.value = info
        Hub.weatherFailed.value = false
        return true
    }
    if (Hub.weather.value == null) Hub.weatherFailed.value = true
    return false
}

suspend fun refreshCalendar(context: Context): List<CalEvent> =
    withContext(Dispatchers.IO) { loadTodayEvents(context) }
