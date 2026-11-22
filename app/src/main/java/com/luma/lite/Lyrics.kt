package com.luma.lite

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class LrcLine(val timeMs: Long, val text: String)

private val TAG_RE = Regex("""\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

fun parseLrc(raw: String): List<LrcLine> {
    val out = mutableListOf<LrcLine>()
    raw.lineSequence().forEach { line ->
        val matches = TAG_RE.findAll(line).toList()
        if (matches.isEmpty()) return@forEach
        val text = line.substring(matches.last().range.last + 1).trim()
        if (text.isEmpty()) return@forEach
        matches.forEach { m ->
            val min = m.groupValues[1].toIntOrNull() ?: 0
            val sec = m.groupValues[2].toIntOrNull() ?: 0
            val frac = m.groupValues[3]
            val ms = when (frac.length) {
                0 -> 0
                1 -> (frac.toIntOrNull() ?: 0) * 100
                2 -> (frac.toIntOrNull() ?: 0) * 10
                else -> frac.take(3).toIntOrNull() ?: 0
            }
            out += LrcLine(min * 60_000L + sec * 1_000L + ms, text)
        }
    }
    return out.sortedBy { it.timeMs }
}

private fun httpGet(url: String): String? {
    return try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 6000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            setRequestProperty("Referer", "https://music.163.com/")
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        body
    } catch (t: Throwable) {
        null
    }
}

/** 按「歌名 + 歌手」去网易云找一条匹配，取 LRC。失败返回 null。 */
private fun fetchLyricsBlocking(title: String, artist: String): List<LrcLine>? {
    return try {
        val q = URLEncoder.encode("$title $artist".trim(), "UTF-8")
        val search = httpGet("https://music.163.com/api/search/get?s=$q&type=1&offset=0&limit=1")
            ?: return null
        val songs = JSONObject(search).optJSONObject("result")?.optJSONArray("songs") ?: return null
        if (songs.length() == 0) return null
        val id = songs.getJSONObject(0).optLong("id", 0L)
        if (id <= 0L) return null
        val lyricJson = httpGet("https://music.163.com/api/song/lyric?lv=1&kv=1&tv=-1&id=$id")
            ?: return null
        val lrc = JSONObject(lyricJson).optJSONObject("lrc")?.optString("lyric").orEmpty()
        if (lrc.isBlank()) return null
        parseLrc(lrc).ifEmpty { null }
    } catch (t: Throwable) {
        null
    }
}

object LyricsRepo {
    private val cache = mutableMapOf<String, List<LrcLine>?>()

    suspend fun get(context: Context, title: String, artist: String): List<LrcLine>? {
        val key = "$title|$artist"
        if (cache.containsKey(key)) return cache[key]
        val lines = withContext(Dispatchers.IO) { fetchLyricsBlocking(title, artist) }
        cache[key] = lines
        return lines
    }
}
