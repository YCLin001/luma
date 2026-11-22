package com.luma.lite

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class AppEntry(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap?
)

fun loadLauncherApps(context: Context, iconSizePx: Int = 128): List<AppEntry> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolved = pm.queryIntentActivities(intent, 0)
    return resolved
        .filter { it.activityInfo.packageName != context.packageName }
        .distinctBy { it.activityInfo.packageName }
        .mapNotNull { ri ->
            runCatching {
                val d: Drawable = ri.loadIcon(pm)
                AppEntry(
                    label = ri.loadLabel(pm).toString(),
                    packageName = ri.activityInfo.packageName,
                    icon = d.toBitmap(iconSizePx, iconSizePx).asImageBitmap()
                )
            }.getOrNull()
        }
        .sortedBy { it.label }
}

/**
 * 试着用 freeform（悬浮小窗）启动。
 * 注意：ActivityOptions.setLaunchWindowingMode / WINDOWING_MODE_FREEFORM 都是 @SystemApi，
 * 公开 SDK 里没有，只能反射；而且设备必须真的支持 freeform，否则系统会降级成全屏。
 */
private fun tryFreeform(context: Context, intent: Intent): Boolean {
    return try {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)) {
            return false
        }
        val options = ActivityOptions.makeBasic()
        val method = ActivityOptions::class.java.getMethod(
            "setLaunchWindowingMode",
            Int::class.javaPrimitiveType
        )
        method.invoke(options, 5) // WINDOWING_MODE_FREEFORM
        context.startActivity(intent, options.toBundle())
        true
    } catch (t: Throwable) {
        false
    }
}

fun launchApp(context: Context, pkg: String) {
    val pm = context.packageManager
    val intent = pm.getLaunchIntentForPackage(pkg) ?: return
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    if (tryFreeform(context, intent)) return
    runCatching { context.startActivity(intent) }
}

@Composable
fun rememberLauncherApps(): List<AppEntry> {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadLauncherApps(context) }
    }
    return apps
}
