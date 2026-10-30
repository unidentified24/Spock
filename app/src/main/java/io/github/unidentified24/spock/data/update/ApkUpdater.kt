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
    private class InstallPermissionNeeded:IllegalStateException("Allow updates from Spock, then return and tap Download and install again.")
    @Suppress("DEPRECATION")
    suspend fun install(context: Context, info: UpdateChecker.UpdateInfo) = withContext(Dispatchers.IO) {
        require(ReleaseAssetPolicy.version(info.version)){"Invalid release version"}
        require(ReleaseAssetPolicy.allows(info.version,info.downloadUrl))
        val directory = File(context.getExternalFilesDir(null) ?: context.filesDir, "updates").apply { mkdirs() }
        val file = File(directory, "Spock-${info.version}.apk")
        val cached=file.isFile && file.length()>0L
        val manager = context.getSystemService(DownloadManager::class.java)
        val id = if(cached)-1L else manager.enqueue(DownloadManager.Request(Uri.parse(info.downloadUrl))
            .setTitle("Spock ${info.version}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(file)))
        try {
            var complete = cached
            val deadline=android.os.SystemClock.elapsedRealtime()+300000L
            while(!complete && android.os.SystemClock.elapsedRealtime()<deadline) {
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
            info.sha256?.let {expected ->
                check(expected.matches(Regex("[a-fA-F0-9]{64}"))){"Invalid release checksum"}
                val digest=java.security.MessageDigest.getInstance("SHA-256")
                file.inputStream().use{input->val buffer=ByteArray(32768);while(true){val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n)}}
                check(digest.digest().joinToString(""){"%02x".format(it)}.equals(expected,true)){"Update checksum mismatch. Download rejected."}
            }
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
                    context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                throw InstallPermissionNeeded()
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
            withContext(Dispatchers.Main) {
                context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        } catch (error: Exception) {
            if(error !is InstallPermissionNeeded){if(id>=0)manager.remove(id);file.delete()}
            throw error
        }
    }
}
