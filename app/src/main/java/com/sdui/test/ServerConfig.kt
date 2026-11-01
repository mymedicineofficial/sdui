package com.sdui.test

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ServerConfig {
    // TODO: apna GitHub username aur repo name yahan daalo.
    // Reppo me "server/" folder ke andar home.json, profile.json hone chahiye.
    private const val GITHUB_USER = "YOUR_GITHUB_USERNAME"
    private const val GITHUB_REPO = "YOUR_REPO_NAME"
    private const val BRANCH = "main"

    private const val BASE =
        "https://raw.githubusercontent.com/$GITHUB_USER/$GITHUB_REPO/$BRANCH/server"

    /** screen = "home" -> server/home.json */
    fun fetchScreen(screen: String): JSONObject {
        // ?t= GitHub raw cache ko bypass karne ke liye
        val url = URL("$BASE/$screen.json?t=${System.currentTimeMillis()}")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
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
}
