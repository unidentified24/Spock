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
    data class UpdateInfo(val version: String, val downloadUrl: String, val fingerprint: String, val sha256:String?=null)
    @Volatile var lastMessage:String="No check completed yet."
        private set
    suspend fun check(context: Context, manual:Boolean=false): UpdateInfo? = withContext(Dispatchers.IO) {
        val info = fetch() ?: return@withContext null
        if (!ReleaseVersion.isNewer(info.version, BuildConfig.VERSION_NAME)) {lastMessage="Spock ${BuildConfig.VERSION_NAME} is up to date.";return@withContext null}
        if (!manual && context.getSharedPreferences("updates", 0).getString("skip", null) == info.fingerprint) null else info
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
            when(connection.responseCode){
                404 -> {lastMessage="No public signed release is available. Private repositories cannot serve anonymous app updates.";return null}
                403,429 -> error("GitHub temporarily refused the update check. Try again later.")
                200 -> Unit
                else -> error("Update service returned HTTP ${connection.responseCode}.")
            }
            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (release.optBoolean("prerelease") || release.optBoolean("draft")) {lastMessage="No stable release is published yet.";return null}
            val version = release.optString("tag_name").removePrefix("v").removePrefix("V")
            require(ReleaseAssetPolicy.version(version)){"Release version is invalid."}
            val assets = release.optJSONArray("assets") ?: error("Release has no APK assets.")
            val asset = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name") == "Spock-$version.apk" } ?: error("Release is missing Spock-$version.apk.")
            val url = asset.optString("browser_download_url")
            require(ReleaseAssetPolicy.allows(version,url)){"Unexpected update download location."}
            lastMessage="Spock $version is available."
            UpdateInfo(version, url, "${release.optLong("id")}:$version",asset.optString("digest").takeIf{it.startsWith("sha256:")}?.removePrefix("sha256:"))
        } finally { connection.disconnect() }
    }.onFailure{lastMessage=it.message?.take(180) ?: "Update check failed. Check your connection."}.getOrNull()
}
