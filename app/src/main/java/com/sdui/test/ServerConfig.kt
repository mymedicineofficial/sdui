package com.sdui.test

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ServerConfig {
    private const val GITHUB_USER = "mymedicineofficial"
    private const val GITHUB_REPO = "sdui"
    private const val BRANCH = "main"

    private const val BASE =
        "https://raw.githubusercontent.com/$GITHUB_USER/$GITHUB_REPO/$BRANCH/server"

    /** server/<path> ko text ki tarah laata hai. bust = true sirf manual Refresh par. */
    private fun http(path: String, bust: Boolean): String {
        val suffix = if (bust) "?t=${System.currentTimeMillis()}" else ""
        val conn = URL("$BASE/$path$suffix").openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        try {
            if (conn.responseCode != 200) {
                throw IllegalStateException("HTTP ${conn.responseCode} for $path")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** screen = "home" -> server/home.json */
    fun fetchScreen(screen: String, bust: Boolean = false): JSONObject =
        JSONObject(http("$screen.json", bust))

    /** path = "page.html" -> server/page.html (raw text) */
    fun fetchText(path: String, bust: Boolean = false): String = http(path, bust)

    // ---- offline cache ----
    fun loadCached(context: Context, screen: String): JSONObject? {
        val text = loadCachedText(context, screen) ?: return null
        return runCatching { JSONObject(text) }.getOrNull()
    }

    fun saveCache(context: Context, screen: String, json: JSONObject) =
        saveCachedText(context, screen, json.toString())

    fun loadCachedText(context: Context, key: String): String? =
        context.getSharedPreferences("sdui_cache", Context.MODE_PRIVATE).getString(key, null)

    fun saveCachedText(context: Context, key: String, text: String) {
        context.getSharedPreferences("sdui_cache", Context.MODE_PRIVATE)
            .edit().putString(key, text).apply()
    }
}
