package com.luma.lite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/** 轻量持久化：壁纸文件 + 五个 Dock 槽位 + 九个矩阵槽位。 */
object Prefs {
    private const val NAME = "luma"
    private const val KEY_DOCK = "dock_pkgs"
    private const val KEY_MATRIX = "matrix_pkgs"
    private const val KEY_WEATHER_CITY = "weather_city"

    const val DOCK_SLOTS = 5
    const val MATRIX_SLOTS = 9

    private fun sp(c: Context) = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun wallpaperFile(c: Context) = File(c.filesDir, "wallpaper_custom.jpg")

    fun saveWallpaperFromUri(c: Context, uri: Uri): Boolean {
        return try {
            val input = c.contentResolver.openInputStream(uri) ?: return false
            val decoded = input.use { BitmapFactory.decodeStream(it) } ?: return false
            val maxSide = 2400
            val longSide = maxOf(decoded.width, decoded.height)
            val bmp = if (longSide > maxSide) {
                val s = maxSide.toFloat() / longSide
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * s).toInt().coerceAtLeast(1),
                    (decoded.height * s).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                decoded
            }
            wallpaperFile(c).outputStream().use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            true
        } catch (t: Throwable) {
            false
        }
    }

    fun clearWallpaper(c: Context) {
        runCatching { wallpaperFile(c).delete() }
    }

    fun loadCustomWallpaper(c: Context): ImageBitmap? {
        return try {
            val f = wallpaperFile(c)
            if (!f.exists()) return null
            BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
        } catch (t: Throwable) {
            null
        }
    }

    private fun readList(c: Context, key: String, size: Int): List<String> {
        val raw = sp(c).getString(key, "") ?: ""
        val parts = if (raw.isEmpty()) emptyList() else raw.split("|")
        return List(size) { i -> parts.getOrElse(i) { "" } }
    }

    private fun writeList(c: Context, key: String, list: List<String>) {
        sp(c).edit().putString(key, list.joinToString("|") { it.replace("|", "") }).apply()
    }

    fun dockSlots(c: Context): List<String> = readList(c, KEY_DOCK, DOCK_SLOTS)

    /** 首次启动按用户点名的 5 个应用填 Dock；装不上的槽位会回落到默认。 */
    fun seedDockDefaults(c: Context) {
        val prefs = sp(c)
        if (prefs.getBoolean("dock_seeded", false)) return
        prefs.edit().putBoolean("dock_seeded", true).apply()
        val wanted = listOf(
            "com.tencent.mm",
            "com.tencent.mobileqq",
            "com.tencent.qqmusic",
            "com.autonavi.minimap",
            "com.android.settings"
        )
        wanted.forEachIndexed { index, pkg -> setDockSlot(c, index, pkg) }
    }

    fun setDockSlot(c: Context, index: Int, pkg: String?) {
        val list = readList(c, KEY_DOCK, DOCK_SLOTS).toMutableList()
        if (index in list.indices) list[index] = pkg.orEmpty()
        writeList(c, KEY_DOCK, list)
    }

    fun matrixSlots(c: Context): List<String> = readList(c, KEY_MATRIX, MATRIX_SLOTS)

    /** 空字符串 = 自动定位；否则是手动选的城市名。 */
    fun weatherCity(c: Context): String = sp(c).getString(KEY_WEATHER_CITY, "").orEmpty()

    fun setWeatherCity(c: Context, city: String) {
        sp(c).edit().putString(KEY_WEATHER_CITY, city).apply()
    }

    fun setMatrixSlot(c: Context, index: Int, pkg: String?) {
        val list = readList(c, KEY_MATRIX, MATRIX_SLOTS).toMutableList()
        if (index in list.indices) list[index] = pkg.orEmpty()
        writeList(c, KEY_MATRIX, list)
    }
}
