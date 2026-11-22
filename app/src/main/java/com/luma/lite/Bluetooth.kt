package com.luma.lite

import android.Manifest
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.telephony.TelephonyManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

data class BtDevice(
    val name: String,
    val kind: String,
    val battery: Int?,
    val address: String = "",
    val raw: BluetoothDevice? = null
)

/** 反射调 BluetoothDevice.isConnected()：这个方法在各版本 SDK 可见性不一致，编译期不可靠。 */
private fun deviceConnected(device: BluetoothDevice): Boolean = runCatching {
    val method = device.javaClass.getMethod("isConnected")
    (method.invoke(device) as? Boolean) == true
}.getOrDefault(false)

private fun deviceBattery(device: BluetoothDevice): Int? = runCatching {
    val method = device.javaClass.getMethod("getBatteryLevel")
    (method.invoke(device) as? Int)?.takeIf { it in 0..100 }
}.getOrNull()

private val BATTERY_SERVICE: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
private val BATTERY_LEVEL: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
private const val GATT_TTL_MS = 5 * 60 * 1000L
private val gattCache = mutableMapOf<String, Pair<Int, Long>>()

private fun cachedBattery(address: String): Int? {
    val entry = gattCache[address] ?: return null
    if (SystemClock.elapsedRealtime() - entry.second > GATT_TTL_MS) return null
    return entry.first.takeIf { it in 0..100 }
}

private fun needsBatteryProbe(address: String): Boolean {
    val entry = gattCache[address] ?: return true
    return SystemClock.elapsedRealtime() - entry.second > GATT_TTL_MS
}

/**
 * 读标准 Battery Service（0x180F / 0x2A19）。
 * 蓝牙电量没有公开的系统 API（BluetoothDevice.getBatteryLevel 是 @SystemApi，反射被拦），
 * 只能自己开 GATT 去读；设备没暴露这个 service 就返回 null。
 */
suspend fun readGattBattery(context: Context, device: BluetoothDevice): Int? =
    withTimeoutOrNull(9000L) {
        suspendCancellableCoroutine { cont ->
            val done = AtomicBoolean(false)
            var gatt: BluetoothGatt? = null

            fun finish(value: Int?) {
                if (done.compareAndSet(false, true)) {
                    runCatching { gatt?.disconnect() }
                    runCatching { gatt?.close() }
                    runCatching { cont.resume(value) }
                }
            }

            try {
                gatt = device.connectGatt(
                    context,
                    false,
                    object : BluetoothGattCallback() {
                        override fun onConnectionStateChange(
                            g: BluetoothGatt,
                            status: Int,
                            newState: Int
                        ) {
                            if (newState == BluetoothProfile.STATE_CONNECTED &&
                                status == BluetoothGatt.GATT_SUCCESS
                            ) {
                                if (!g.discoverServices()) finish(null)
                            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                                finish(null)
                            }
                        }

                        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                            val ch = g.getService(BATTERY_SERVICE)?.getCharacteristic(BATTERY_LEVEL)
                            if (ch == null || !g.readCharacteristic(ch)) finish(null)
                        }

                        @Deprecated("Deprecated in API 33")
                        override fun onCharacteristicRead(
                            g: BluetoothGatt,
                            characteristic: BluetoothGattCharacteristic,
                            status: Int
                        ) {
                            val v = if (status == BluetoothGatt.GATT_SUCCESS) {
                                characteristic.value?.getOrNull(0)?.toInt()
                            } else {
                                null
                            }
                            finish(v)
                        }

                        override fun onCharacteristicRead(
                            g: BluetoothGatt,
                            characteristic: BluetoothGattCharacteristic,
                            value: ByteArray,
                            status: Int
                        ) {
                            val v = if (status == BluetoothGatt.GATT_SUCCESS) {
                                value.getOrNull(0)?.toInt()
                            } else {
                                null
                            }
                            finish(v)
                        }
                    },
                    BluetoothDevice.TRANSPORT_LE
                )
            } catch (t: Throwable) {
                finish(null)
            }
            cont.invokeOnCancellation { finish(null) }
        }
    }

fun readBtDevices(context: Context): List<BtDevice> {
    val granted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.BLUETOOTH_CONNECT
    ) == PackageManager.PERMISSION_GRANTED
    if (!granted) return emptyList()

    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        ?: return emptyList()
    val adapter = manager.adapter ?: return emptyList()
    if (!adapter.isEnabled) return emptyList()

    val found = LinkedHashMap<String, BluetoothDevice>()

    // 一、系统层面：已配对设备里，当前处于任意连接状态的
    runCatching { adapter.bondedDevices }.getOrNull().orEmpty().forEach { device ->
        if (deviceConnected(device)) {
            runCatching { device.address }.getOrNull()?.let { found[it] = device }
        }
    }

    // 二、按 profile 再兜一层：音频走 A2DP/HEADSET，手环手表多走 GATT
    val profiles = intArrayOf(
        BluetoothProfile.HEADSET,
        BluetoothProfile.A2DP,
        BluetoothProfile.GATT,
        BluetoothProfile.GATT_SERVER
    )
    profiles.forEach { profile ->
        runCatching { manager.getConnectedDevices(profile) }.getOrNull().orEmpty().forEach { device ->
            runCatching { device.address }.getOrNull()?.let { found[it] = device }
        }
    }

    if (found.isEmpty()) return emptyList()

    return found.values.mapNotNull { device ->
        val rawName: String? = try {
            device.name
        } catch (t: Throwable) {
            null
        }
        if (rawName.isNullOrBlank()) return@mapNotNull null
        val major = runCatching { device.bluetoothClass?.majorDeviceClass }.getOrNull()
        val kind = when (major) {
            BluetoothClass.Device.Major.AUDIO_VIDEO -> "headset"
            BluetoothClass.Device.Major.WEARABLE -> "watch"
            BluetoothClass.Device.Major.COMPUTER -> "computer"
            else -> "bt"
        }
        val address = runCatching { device.address }.getOrDefault("")
        BtDevice(
            name = rawName,
            kind = kind,
            battery = deviceBattery(device) ?: cachedBattery(address),
            address = address,
            raw = device
        )
    }
}

@Composable
fun rememberBtDevices(): List<BtDevice> {
    val context = LocalContext.current
    var devices by remember { mutableStateOf<List<BtDevice>>(emptyList()) }
    LaunchedEffect(Unit) {
        while (true) {
            val list = withContext(Dispatchers.IO) { readBtDevices(context) }
            devices = list
            // 没有电量的，逐个开 GATT 试读一次（结果缓存 5 分钟，失败也缓存，避免死循环连）
            list.forEach { device ->
                val raw = device.raw
                if (device.battery == null && raw != null && device.address.isNotBlank() &&
                    needsBatteryProbe(device.address)
                ) {
                    val value = withContext(Dispatchers.IO) { readGattBattery(context, raw) }
                    gattCache[device.address] = (value ?: -1) to SystemClock.elapsedRealtime()
                    if (value != null) {
                        devices = devices.map {
                            if (it.address == device.address) it.copy(battery = value) else it
                        }
                    }
                }
            }
            delay(8000)
        }
    }
    return devices
}

data class NetState(val type: String, val carrier: String)

fun readNetwork(context: Context): NetState {
    return try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetState("离线", "")
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            ?: return NetState("离线", "")
        when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetState("Wi-Fi", "")
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                val carrier = runCatching { tm?.networkOperatorName }.getOrNull().orEmpty()
                val gen = runCatching {
                    when (tm?.dataNetworkType) {
                        TelephonyManager.NETWORK_TYPE_NR -> "5G"
                        TelephonyManager.NETWORK_TYPE_LTE -> "4G"
                        TelephonyManager.NETWORK_TYPE_UMTS,
                        TelephonyManager.NETWORK_TYPE_HSDPA,
                        TelephonyManager.NETWORK_TYPE_HSUPA -> "3G"
                        else -> ""
                    }
                }.getOrNull().orEmpty()
                NetState(gen.ifBlank { "移动网络" }, carrier)
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetState("有线网络", "")
            else -> NetState("已联网", "")
        }
    } catch (t: Throwable) {
        NetState("离线", "")
    }
}

@Composable
fun rememberNetwork(): NetState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(NetState("…", "")) }
    LaunchedEffect(Unit) {
        while (true) {
            state = withContext(Dispatchers.IO) { readNetwork(context) }
            delay(10_000)
        }
    }
    return state
}

/** 蓝牙设备那张小玻璃卡：上半设备，下半网络 + 今日日程，同一块玻璃里。 */
@Composable
fun BtCard(devices: List<BtDevice>, compact: Boolean, modifier: Modifier = Modifier) {
    if (devices.isEmpty()) return
    val context = LocalContext.current
    val net = rememberNetwork()
    var events by remember { mutableStateOf<List<CalEvent>>(emptyList()) }

    LaunchedEffect(Unit) {
        while (true) {
            events = withContext(Dispatchers.IO) { loadTodayEvents(context) }
            delay(60_000)
        }
    }

    val shown = devices.take(3)
    Column(
        modifier = modifier
            .widthIn(max = if (compact) 142.dp else 158.dp)
            .glass(if (compact) 18.dp else 20.dp, fill = 0.14f, stroke = 0.38f)
            .padding(
                horizontal = if (compact) 11.dp else 13.dp,
                vertical = if (compact) 13.dp else 15.dp
            ),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Column(Modifier.weight(0.6f)) {
            shown.forEach { device ->
                Row(
                    modifier = Modifier.padding(bottom = if (compact) 14.dp else 16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    BtGlyph(
                        device.kind,
                        if (compact) 16.dp else 18.dp,
                        Color(0xFFF8FAFD).copy(alpha = 0.92f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = device.name,
                            color = Color(0xFFF8FAFD).copy(alpha = 0.96f),
                            fontSize = if (compact) 11.sp else 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = if (compact) 15.sp else 16.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (device.battery != null) {
                            Text(
                                text = "${device.battery}%",
                                color = Color(0xFFF8FAFD).copy(alpha = 0.70f),
                                fontSize = if (compact) 10.sp else 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = if (net.carrier.isBlank()) net.type else "${net.type} · ${net.carrier}",
                color = Color(0xFFF8FAFD).copy(alpha = 0.72f),
                fontSize = if (compact) 9.sp else 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(if (compact) 9.dp else 11.dp))
            if (events.isEmpty()) {
                Text(
                    text = "今日无日程",
                    color = Color(0xFFF8FAFD).copy(alpha = 0.48f),
                    fontSize = if (compact) 9.sp else 10.sp
                )
            } else {
                Text(
                    text = "今日 ${events.size} 项",
                    color = Color(0xFFF8FAFD).copy(alpha = 0.72f),
                    fontSize = if (compact) 9.sp else 10.sp,
                    fontWeight = FontWeight.Medium
                )
                events.take(2).forEach { event ->
                    Text(
                        text = "${eventTimeText(event)}  ${event.title}",
                        color = Color(0xFFF8FAFD).copy(alpha = 0.52f),
                        fontSize = if (compact) 9.sp else 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun BtGlyph(kind: String, side: Dp, tint: Color) {
    Canvas(Modifier.size(side)) {
        val w = size.width
        val h = size.height
        when (kind) {
            "headset" -> {
                drawArc(
                    color = tint,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(w * 0.12f, h * 0.20f),
                    size = Size(w * 0.76f, h * 0.66f),
                    style = Stroke(width = w * 0.10f)
                )
                val cupW = w * 0.16f
                val cupH = h * 0.30f
                val r = CornerRadius(w * 0.06f, w * 0.06f)
                drawRoundRect(tint, Offset(w * 0.06f, h * 0.50f), Size(cupW, cupH), r)
                drawRoundRect(tint, Offset(w * 0.78f, h * 0.50f), Size(cupW, cupH), r)
            }

            "watch" -> {
                val r = CornerRadius(w * 0.14f, w * 0.14f)
                drawRoundRect(tint, Offset(w * 0.20f, h * 0.28f), Size(w * 0.60f, h * 0.44f), r)
                drawRoundRect(
                    tint,
                    Offset(w * 0.34f, h * 0.06f),
                    Size(w * 0.32f, h * 0.18f),
                    CornerRadius(w * 0.04f)
                )
                drawRoundRect(
                    tint,
                    Offset(w * 0.34f, h * 0.76f),
                    Size(w * 0.32f, h * 0.18f),
                    CornerRadius(w * 0.04f)
                )
            }

            "computer" -> {
                drawRoundRect(
                    tint,
                    Offset(w * 0.08f, h * 0.18f),
                    Size(w * 0.84f, h * 0.50f),
                    CornerRadius(w * 0.06f),
                    style = Stroke(width = w * 0.10f)
                )
                drawLine(
                    tint,
                    Offset(w * 0.30f, h * 0.86f),
                    Offset(w * 0.70f, h * 0.86f),
                    strokeWidth = w * 0.10f
                )
                drawLine(
                    tint,
                    Offset(w * 0.50f, h * 0.68f),
                    Offset(w * 0.50f, h * 0.86f),
                    strokeWidth = w * 0.10f
                )
            }

            else -> {
                drawPath(
                    Path().apply {
                        moveTo(w * 0.50f, h * 0.06f)
                        lineTo(w * 0.50f, h * 0.94f)
                    },
                    tint,
                    style = Stroke(width = w * 0.09f)
                )
                drawPath(
                    Path().apply {
                        moveTo(w * 0.50f, h * 0.06f)
                        lineTo(w * 0.82f, h * 0.30f)
                        lineTo(w * 0.50f, h * 0.52f)
                    },
                    tint,
                    style = Stroke(width = w * 0.09f)
                )
                drawPath(
                    Path().apply {
                        moveTo(w * 0.50f, h * 0.94f)
                        lineTo(w * 0.82f, h * 0.70f)
                        lineTo(w * 0.50f, h * 0.48f)
                    },
                    tint,
                    style = Stroke(width = w * 0.09f)
                )
            }
        }
    }
}
