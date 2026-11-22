package com.luma.lite

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

data class BatteryInfo(val level: Int, val charging: Boolean)

fun readBattery(context: Context): BatteryInfo {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
        status == BatteryManager.BATTERY_STATUS_FULL ||
        plugged != 0
    val pct = if (scale > 0 && level >= 0) level * 100 / scale else 0
    return BatteryInfo(pct, charging)
}

@Composable
fun rememberBattery(): BatteryInfo {
    val context = LocalContext.current
    val state = remember { mutableStateOf(readBattery(context)) }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                state.value = readBattery(context)
            }
        }
        val ok = runCatching {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }.isSuccess
        onDispose {
            if (ok) runCatching { context.unregisterReceiver(receiver) }
        }
    }
    return state.value
}
