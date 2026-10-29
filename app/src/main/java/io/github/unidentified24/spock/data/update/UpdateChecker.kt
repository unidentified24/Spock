package io.github.unidentified24.spock.data.update

import android.content.Context
import io.github.unidentified24.spock.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {
    const val REPOSITORY = "unidentified24/Spock"
    data class UpdateInfo(val version: String, val downloadUrl: String, val fingerprint: String)
    suspend fun check(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val info = fetch() ?: return@withContext null
        if (!ReleaseVersion.isNewer(info.version, BuildConfig.VERSION_NAME)) return@withContext null
        if (context.getSharedPreferences("updates", 0).getString("skip", null) == info.fingerprint) null else info
    }
    fun skipRelease(context: Context, info: UpdateInfo) {
        context.getSharedPreferences("updates", 0).edit().putString("skip", info.fingerprint).apply()
    }
    fun fetch(): UpdateInfo? = runCatching {
        val connection = URL("https://api.github.com/repos/$REPOSITORY/releases/latest").openConnection() as HttpURLConnection
        connection.connectTimeout = 8000; connection.readTimeout = 8000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "Spock/${BuildConfig.VERSION_NAME}")
        try {
            if (connection.responseCode != 200) return null
            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (release.optBoolean("prerelease") || release.optBoolean("draft")) return null
            val version = release.optString("tag_name").removePrefix("v").removePrefix("V")
            val assets = release.optJSONArray("assets") ?: return null
            val asset = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name") == "Spock-$version.apk" } ?: return null
            val url = asset.optString("browser_download_url")
            if (!url.startsWith("https://github.com/$REPOSITORY/releases/download/")) return null
            UpdateInfo(version, url, "${release.optLong("id")}:$version")
        } finally { connection.disconnect() }
    }.getOrNull()
}
