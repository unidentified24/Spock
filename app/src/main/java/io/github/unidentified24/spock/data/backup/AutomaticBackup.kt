package io.github.unidentified24.spock.data.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.unidentified24.spock.features.FeatureSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.TimeUnit

object AutomaticBackup {
    private const val WORK="spock-automatic-backup"
    fun schedule(context:Context){
        val options=FeatureSettings.value.backups;val manager=WorkManager.getInstance(context)
        if(!options.automatic||options.folder.isBlank()){manager.cancelUniqueWork(WORK);return}
        val request=PeriodicWorkRequestBuilder<AutomaticBackupWorker>(options.intervalDays.coerceIn(1,30).toLong(),TimeUnit.DAYS).build()
        manager.enqueueUniquePeriodicWork(WORK,ExistingPeriodicWorkPolicy.UPDATE,request)
    }
}

class AutomaticBackupWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters){
    override suspend fun doWork():Result=withContext(Dispatchers.IO){
        val options=FeatureSettings.value.backups
        if(!options.automatic||options.folder.isBlank())return@withContext Result.success()
        val root=DocumentFile.fromTreeUri(applicationContext,Uri.parse(options.folder)) ?: return@withContext Result.retry()
        try{
            val name="Spock-${LocalDate.now()}.spockdata"
            root.findFile(name)?.delete()
            val file=root.createFile("application/octet-stream",name) ?: return@withContext Result.retry()
            SpockDataBackup.export(applicationContext,file.uri)
            root.listFiles().filter{it.name.orEmpty().startsWith("Spock-")&&it.name.orEmpty().endsWith(".spockdata")}
                .sortedByDescending{it.lastModified()}.drop(options.historyCount.coerceIn(1,50)).forEach{it.delete()}
            applicationContext.getSharedPreferences("auto_backup_status",0).edit().putLong("last",System.currentTimeMillis()).putString("error","").apply()
            Result.success()
        }catch(_:SecurityException){Result.retry()}
        catch(_:Exception){applicationContext.getSharedPreferences("auto_backup_status",0).edit().putString("error","Automatic backup couldn't write to the selected folder.").apply();Result.retry()}
    }
}
