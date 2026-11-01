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

    /** screen = "home" -> server/home.json
     *  bust = true sirf manual Refresh par, warna CDN cache se tez aata hai */
    fun fetchScreen(screen: String, bust: Boolean = false): JSONObject {
        val suffix = if (bust) "?t=${System.currentTimeMillis()}" else ""
        val url = URL("$BASE/$screen.json$suffix")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        try {
            if (conn.responseCode != 200) {
                throw IllegalStateException("HTTP ${conn.responseCode} for $screen.json")
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    fun loadCached(context: Context, screen: String): JSONObject? {
        val text = context.getSharedPreferences("sdui_cache", Context.MODE_PRIVATE)
            .getString(screen, null) ?: return null
        return runCatching { JSONObject(text) }.getOrNull()
    }

    fun saveCache(context: Context, screen: String, json: JSONObject) {
        context.getSharedPreferences("sdui_cache", Context.MODE_PRIVATE)
            .edit()
            .putString(screen, json.toString())
            .apply()
    }
}