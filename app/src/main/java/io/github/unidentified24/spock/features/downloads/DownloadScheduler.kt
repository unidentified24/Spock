package io.github.unidentified24.spock.features.downloads

import android.content.*
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.work.*
import io.github.unidentified24.spock.features.FeatureSettings
import io.github.unidentified24.spock.features.SessionIdentity
import java.util.concurrent.TimeUnit

object DownloadScheduler {
    const val QUEUE="spock-download-queue"
    private const val WAKE="spock-download-wakeup"
    fun networkConstraints(wifiOnly:Boolean,chargingOnly:Boolean,network:Boolean=true):Constraints {
        val builder=Constraints.Builder().setRequiresCharging(chargingOnly)
        if(network){
            if(Build.VERSION.SDK_INT>=28){
                val request=NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                if(wifiOnly)request.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                builder.setRequiredNetworkRequest(request.build(),if(wifiOnly)NetworkType.UNMETERED else NetworkType.CONNECTED)
            }else builder.setRequiredNetworkType(if(wifiOnly)NetworkType.UNMETERED else NetworkType.CONNECTED)
        }
        return builder.build()
    }
    @Synchronized fun kick(context:Context,replace:Boolean=false) {
        DownloadLedger.initialize(context)
        val now=System.currentTimeMillis();val pending=DownloadLedger.snapshot().filter{it.pending&&(!FeatureSettings.value.downloads.offlineOnly||!it.remote)}
        if(pending.isEmpty())return
        val due=pending.filter{it.runAfter<=now}
        if(due.isEmpty()){wakeAt(context,pending.minOf{it.runAfter});return}
        val rules=FeatureSettings.value.downloads
        val network=due.all{it.remote}
        val constraints=networkConstraints(rules.wifiOnly,rules.chargingOnly,network)
        val preferences=context.getSharedPreferences("download_scheduler",0)
        val signature="${SessionIdentity.id}:$network:${rules.wifiOnly}:${rules.chargingOnly}"
        val changed=preferences.getString("constraints",null)!=signature
        preferences.edit().putString("constraints",signature).apply()
        val request=OneTimeWorkRequestBuilder<DownloadQueueWorker>().setConstraints(constraints)
            .setInputData(workDataOf("profile" to SessionIdentity.id)).setBackoffCriteria(BackoffPolicy.LINEAR,10,TimeUnit.SECONDS)
            .addTag(QUEUE).build()
        WorkManager.getInstance(context).enqueueUniqueWork(QUEUE,if(replace||changed)ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.APPEND_OR_REPLACE,request)
    }
    fun wakeAt(context:Context,time:Long) {
        val request=OneTimeWorkRequestBuilder<DownloadWakeWorker>().setInitialDelay((time-System.currentTimeMillis()).coerceAtLeast(1000),TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("profile" to SessionIdentity.id)).build()
        WorkManager.getInstance(context).enqueueUniqueWork(WAKE,ExistingWorkPolicy.REPLACE,request)
    }
    fun stop(context:Context){WorkManager.getInstance(context).cancelUniqueWork(QUEUE);WorkManager.getInstance(context).cancelUniqueWork(WAKE)}
}

class DownloadWakeWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters) {
    override suspend fun doWork():Result {
        if(inputData.getString("profile")==SessionIdentity.id)DownloadScheduler.kick(applicationContext)
        return Result.success()
    }
}
