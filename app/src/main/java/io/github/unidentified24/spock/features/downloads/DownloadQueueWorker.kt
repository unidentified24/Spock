package io.github.unidentified24.spock.features.downloads

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import io.github.unidentified24.spock.MainActivity
import io.github.unidentified24.spock.R
import io.github.unidentified24.spock.di.Downloads
import io.github.unidentified24.spock.features.FeatureSettings
import io.github.unidentified24.spock.features.SessionIdentity
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class DownloadQueueWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters) {
    companion object {private val exclusive=Mutex();const val NOTIFICATION=4202;const val CHANNEL="spock-downloads"}
    private val active=ConcurrentHashMap<Int,Job>()
    private fun notification():Notification {
        val manager=applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"Music downloads",NotificationManager.IMPORTANCE_LOW))
        val tasks=DownloadLedger.snapshot();val running=tasks.filter{it.state==DownloadState.RUNNING};val waiting=tasks.count{it.pending&&it.state!=DownloadState.RUNNING}
        val open=PendingIntent.getActivity(applicationContext,4202,Intent(applicationContext,MainActivity::class.java).putExtra("spock_page","downloads").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop=PendingIntent.getBroadcast(applicationContext,4203,Intent(applicationContext,DownloadControlReceiver::class.java).setAction("cancel").putExtra("profile",SessionIdentity.id),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(applicationContext,CHANNEL).setSmallIcon(R.drawable.spock_symbol).setContentTitle("Spock · saving your music")
            .setContentText(if(running.isEmpty())"$waiting tracks queued" else running.take(2).joinToString(" · "){it.song.title}.take(120))
            .setSubText("${running.size} saving · $waiting waiting").setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open)
            .setProgress(100,if(running.isEmpty())0 else running.map{it.percent}.average().toInt(),running.isEmpty())
            .addAction(0,"Cancel downloads",stop).build()
    }
    override suspend fun doWork():Result=exclusive.withLock {
        val profile=inputData.getString("profile") ?: SessionIdentity.id
        if(profile!=SessionIdentity.id)return@withLock Result.success()
        DownloadLedger.initialize(applicationContext);DownloadLedger.recover(profile)
        if(DownloadLedger.snapshot(profile).none{it.pending})return@withLock Result.success()
        try {
            val info=if(Build.VERSION.SDK_INT>=29)ForegroundInfo(NOTIFICATION,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)else ForegroundInfo(NOTIFICATION,notification())
            setForeground(info)
        }catch(_:Exception){return@withLock Result.retry()}
        val manager=applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            supervisorScope {
                val failure=java.util.concurrent.atomic.AtomicReference<Exception?>(null)
                val monitor=launch {
                    while(isActive){
                        val network=NetworkStatus.read(applicationContext)
                        active.forEach{(id,job)->val task=DownloadLedger.get(id,profile)
                            if(isStopped||profile!=SessionIdentity.id||task?.state!=DownloadState.RUNNING||(task!=null&&!DownloadPolicy.canRun(task,FeatureSettings.value.downloads,network.wifi,network.online,network.charging,System.currentTimeMillis()))){task?.let{Downloads.interrupt(it.song.url)};job.cancel()}
                        }
                        manager.notify(NOTIFICATION,notification());delay(800)
                    }
                }
                try {
                    while(isActive&&profile==SessionIdentity.id){
                        failure.get()?.let{throw it}
                        val rules=FeatureSettings.value.downloads
                        if(active.size>=rules.parallel){delay(200);continue}
                        val network=NetworkStatus.read(applicationContext);val now=System.currentTimeMillis()
                        val ready=DownloadPolicy.ordered(DownloadLedger.snapshot(profile),now).filter{DownloadPolicy.canRun(it,rules,network.wifi,network.online,network.charging,now)}
                        ready.take((rules.parallel-active.size).coerceAtLeast(0)).forEach{task->
                            val started=DownloadLedger.begin(task.song.id,profile) ?: return@forEach
                            val job=launch(start=CoroutineStart.LAZY) {
                                try {
                                    val outcome=Downloads.runPersisted(started,applicationContext)
                                    if(DownloadLedger.get(started.song.id,profile)?.state==DownloadState.RUNNING){
                                        if(outcome.success)DownloadLedger.change(started.song.id,profile){it.copy(state=DownloadState.COMPLETE,percent=100,error="")}
                                        else {
                                            val retry=rules.automaticRetry&&started.attempts<=rules.retryLimit
                                            DownloadLedger.change(started.song.id,profile){it.copy(state=if(retry)DownloadState.RETRY else DownloadState.FAILED,error=outcome.error,runAfter=if(retry)DownloadPolicy.retryAt(System.currentTimeMillis(),started.attempts)else 0)}
                                        }
                                    }
                                    if(outcome.success||DownloadLedger.get(started.song.id,profile)?.state==DownloadState.FAILED)Downloads.deliverCompletion(started.song.url,outcome.success)
                                }catch(cancelled:CancellationException){
                                    runCatching{DownloadLedger.change(started.song.id,profile){if(it.state==DownloadState.RUNNING)it.copy(state=DownloadState.QUEUED,attempts=(it.attempts-1).coerceAtLeast(0))else it}}
                                    throw cancelled
                                }catch(error:Exception){failure.compareAndSet(null,error)}
                                finally{active.remove(started.song.id)}
                            }
                            active[started.song.id]=job;job.start()
                        }
                        if(active.isEmpty()&&ready.isEmpty())break
                        delay(200)
                    }
                    active.values.toList().joinAll()
                    failure.get()?.let{throw it}
                }finally{monitor.cancel();active.forEach{(id,job)->DownloadLedger.get(id,profile)?.let{Downloads.interrupt(it.song.url)};job.cancel()}}
            }
        }catch(cancelled:CancellationException){Downloads.interruptAll();throw cancelled}
        catch(_:Exception){Downloads.interruptAll();DownloadLedger.recover(profile);return@withLock Result.retry()}
        val pending=DownloadLedger.snapshot(profile).filter{it.pending}
        if(pending.any{it.runAfter<=System.currentTimeMillis()}){DownloadScheduler.kick(applicationContext);return@withLock Result.success()}
        pending.minOfOrNull{it.runAfter}?.let{DownloadScheduler.wakeAt(applicationContext,it)}
        Result.success()
    }
}

class DownloadControlReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){if(intent.getStringExtra("profile")==SessionIdentity.id&&intent.action=="cancel")Downloads.cancelAll()}
}
