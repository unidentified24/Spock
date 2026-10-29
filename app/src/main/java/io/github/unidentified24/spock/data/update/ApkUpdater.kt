package io.github.unidentified24.spock.data.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

object ApkUpdater {
    @Suppress("DEPRECATION")
    suspend fun install(context: Context, info: UpdateChecker.UpdateInfo) = withContext(Dispatchers.IO) {
        require(info.downloadUrl.startsWith("https://github.com/${UpdateChecker.REPOSITORY}/releases/download/"))
        val directory = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
        val file = File(directory, "Spock-${info.version}.apk")
        if (file.exists()) file.delete()
        val manager = context.getSystemService(DownloadManager::class.java)
        val id = manager.enqueue(DownloadManager.Request(Uri.parse(info.downloadUrl))
            .setTitle("Spock ${info.version}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(file)))
        try {
            var complete = false
            repeat(300) {
                if (!complete) {
                    delay(1000)
                    manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                        check(cursor.moveToFirst()) { "Download was removed. Try again." }
                        when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                            DownloadManager.STATUS_FAILED -> error("Download failed. Check your connection and retry.")
                            DownloadManager.STATUS_SUCCESSFUL -> complete = true
                        }
                    }
                }
            }
            check(complete) { "Download timed out. Try again." }
            val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
            val candidate = context.packageManager.getPackageArchiveInfo(file.path, flags) ?: error("Invalid APK")
            val installed = context.packageManager.getPackageInfo(context.packageName, flags)
            fun signatures(p: android.content.pm.PackageInfo): Set<String> {
                val values = if (Build.VERSION.SDK_INT >= 28) p.signingInfo?.apkContentsSigners else p.signatures
                return values?.map { it.toCharsString() }?.toSet().orEmpty()
            }
            check(candidate.packageName == context.packageName) { "This update belongs to a different app." }
            check(PackageInfoCompat.getLongVersionCode(candidate) > PackageInfoCompat.getLongVersionCode(installed)) { "Update build number must be newer." }
            check(signatures(installed).isNotEmpty() && signatures(candidate) == signatures(installed)) { "Update signing certificate does not match." }
            if (!context.packageManager.canRequestPackageInstalls()) {
                withContext(Dispatchers.Main) {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                }
                error("Allow updates from Spock, then tap Download and install again.")
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
            withContext(Dispatchers.Main) {
                context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        } catch (error: Exception) {
            manager.remove(id)
            throw error
        }
    }
}
