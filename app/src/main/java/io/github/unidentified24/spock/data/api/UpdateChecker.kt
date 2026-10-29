package io.github.unidentified24.spock.data.api

import android.content.Context
import io.github.unidentified24.spock.data.update.ReleaseVersion

/** Home badge uses the same release source and version comparison as the installer. */
object UpdateChecker {
    data class Update(val version: String, val url: String)
    fun check(context: Context, current: String, force: Boolean = false): Update? {
        val p = context.getSharedPreferences("release_badge", 0)
        if (force || System.currentTimeMillis() - p.getLong("checked", 0) > 21600000L) {
            io.github.unidentified24.spock.data.update.UpdateChecker.fetch()?.let {
                p.edit().putString("version", it.version).putString("url", it.downloadUrl)
                    .putLong("checked", System.currentTimeMillis()).apply()
            }
        }
        val version = p.getString("version", null) ?: return null
        return if (isNewer(version, current)) Update(version, p.getString("url", "")!!) else null
    }
    internal fun isNewer(latest: String, current: String) = ReleaseVersion.isNewer(latest, current)
}
