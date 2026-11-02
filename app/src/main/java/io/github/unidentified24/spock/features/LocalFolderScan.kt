package io.github.unidentified24.spock.features

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.unidentified24.spock.data.local.LocalImport
import io.github.unidentified24.spock.data.preferences.refreshLocalTracks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object LocalFolderScan {
    private const val WORK="spock-local-folder-scan"
    fun schedule(context:Context){
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK,ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<LocalFolderScanWorker>(24,TimeUnit.HOURS).build(),
        )
    }
}

class LocalFolderScanWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters){
    override suspend fun doWork():Result=withContext(Dispatchers.IO){
        if(!FeatureSettings.value.local.autoScan)return@withContext Result.success()
        try{
            val options=FeatureSettings.value.local
            val tracks=options.folders.flatMap { tree->LocalImport.importFolder(applicationContext,android.net.Uri.parse(tree),options.excluded) }
            refreshLocalTracks(applicationContext,tracks)
            Result.success()
        }catch(_:SecurityException){Result.success()}
        catch(_:Exception){Result.retry()}
    }
}
