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
    data class UpdateInfo(val version: String, val downloadUrl: String, val fingerprint: String, val sha256:String?=null,val notes:String="",val pageUrl:String="",val publishedAt:String="",val prerelease:Boolean=false)
    @Volatile var lastMessage:String="No check completed yet."
        private set
    suspend fun check(context: Context, manual:Boolean=false): UpdateInfo? = withContext(Dispatchers.IO) {
        val settings=io.github.unidentified24.spock.features.FeatureSettings.value.updates
        if(!manual&&!settings.automaticChecks){lastMessage="Automatic update checks are off.";return@withContext null}
        val info = fetch(settings.channel) ?: return@withContext null
        if (!ReleaseVersion.isNewer(info.version, BuildConfig.VERSION_NAME)) {lastMessage="Spock ${BuildConfig.VERSION_NAME} is up to date.";return@withContext null}
        if (!manual && context.getSharedPreferences("updates", 0).getString("skip", null) == info.fingerprint) null else info
    }
    fun skipRelease(context: Context, info: UpdateInfo) {
        context.getSharedPreferences("updates", 0).edit().putString("skip", info.fingerprint).apply()
    }
    fun fetch(channel:String="STABLE"): UpdateInfo? = fetchHistory(channel).firstOrNull()

    fun fetchHistory(channel:String="STABLE"):List<UpdateInfo> = runCatching {
        val connection = URL("https://api.github.com/repos/$REPOSITORY/releases?per_page=30").openConnection() as HttpURLConnection
        connection.connectTimeout = 8000; connection.readTimeout = 8000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "Spock/${BuildConfig.VERSION_NAME}")
        try {
            when(connection.responseCode){
                404 -> {lastMessage="No public signed release is available. Private repositories cannot serve anonymous app updates.";return emptyList()}
                403,429 -> error("GitHub temporarily refused the update check. Try again later.")
                200 -> Unit
                else -> error("Update service returned HTTP ${connection.responseCode}.")
            }
            val releases=org.json.JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
            val result=(0 until releases.length()).mapNotNull{index->
                val release=releases.optJSONObject(index) ?: return@mapNotNull null
                val pre=release.optBoolean("prerelease")
                if(release.optBoolean("draft") || (channel!="BETA"&&pre))return@mapNotNull null
                val version=release.optString("tag_name").removePrefix("v").removePrefix("V")
                if(!ReleaseAssetPolicy.version(version))return@mapNotNull null
                val assets=release.optJSONArray("assets") ?: return@mapNotNull null
                val asset=(0 until assets.length()).map{assets.getJSONObject(it)}.firstOrNull{it.optString("name")=="Spock-$version.apk"} ?: return@mapNotNull null
                val url=asset.optString("browser_download_url")
                if(!ReleaseAssetPolicy.allows(version,url))return@mapNotNull null
                UpdateInfo(version,url,"${release.optLong("id")}:$version",asset.optString("digest").takeIf{it.startsWith("sha256:")}?.removePrefix("sha256:"),release.optString("body").take(2000),release.optString("html_url"),release.optString("published_at"),pre)
            }
            if(result.isEmpty())lastMessage=if(channel=="BETA")"No signed beta or stable APK is published." else "No signed stable APK is published yet."
            else lastMessage="Spock ${result.first().version} ${if(result.first().prerelease)"beta " else ""}is available."
            result
        } finally { connection.disconnect() }
    }.onFailure{lastMessage=it.message?.take(180) ?: "Update check failed. Check your connection."}.getOrDefault(emptyList())
}
