package io.github.unidentified24.spock.ui.notification

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import io.github.unidentified24.spock.MainActivity
import io.github.unidentified24.spock.R
import io.github.unidentified24.spock.data.preferences.loadLastPlayback
import java.util.concurrent.TimeUnit

class PlayerWidgetProvider:AppWidgetProvider(){
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray)=ids.forEach{update(context,manager,it)}
    override fun onReceive(context:Context,intent:Intent){
        super.onReceive(context,intent)
        when(intent.action){PLAY,PREVIOUS,NEXT->control(context,intent.action!!)}
    }

    companion object {
        private const val PLAY="io.github.unidentified24.spock.widget.PLAY"
        private const val PREVIOUS="io.github.unidentified24.spock.widget.PREVIOUS"
        private const val NEXT="io.github.unidentified24.spock.widget.NEXT"
        fun refresh(context:Context){
            val manager=AppWidgetManager.getInstance(context);val ids=manager.getAppWidgetIds(ComponentName(context,PlayerWidgetProvider::class.java))
            if(ids.isNotEmpty())ids.forEach{update(context,manager,it)}
        }
        private fun update(context:Context,manager:AppWidgetManager,id:Int){
            val views=RemoteViews(context.packageName,R.layout.widget_player)
            val data=loadLastPlayback(context)?.first
            views.setTextViewText(R.id.widget_title,data?.title ?: "Your music")
            views.setTextViewText(R.id.widget_artist,data?.singer ?: "Open Spock to start listening")
            views.setImageViewResource(R.id.widget_play,if(io.github.unidentified24.spock.di.SongPlayer.isPlaybackWanted())android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            val open=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java).putExtra("spock_page","player").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            views.setOnClickPendingIntent(R.id.widget_title,open);views.setOnClickPendingIntent(R.id.widget_art,open)
            views.setOnClickPendingIntent(R.id.widget_play,action(context,id,PLAY))
            views.setOnClickPendingIntent(R.id.widget_previous,action(context,id,PREVIOUS))
            views.setOnClickPendingIntent(R.id.widget_next,action(context,id,NEXT))
            manager.updateAppWidget(id,views)
        }
        private fun action(context:Context,id:Int,name:String)=PendingIntent.getBroadcast(context,id+name.hashCode(),Intent(context,PlayerWidgetProvider::class.java).setAction(name),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        private fun control(context:Context,action:String){
            val token=SessionToken(context,ComponentName(context,PlaybackService::class.java))
            val future=MediaController.Builder(context,token).buildAsync()
            future.addListener({runCatching{
                val controller=future.get(8,TimeUnit.SECONDS)
                when(action){
                    PLAY->{if(controller.isPlaying)controller.pause()else controller.play()}
                    PREVIOUS->controller.seekToPrevious()
                    NEXT->controller.seekToNext()
                }
                MediaController.releaseFuture(future)
                refresh(context)
            }.onFailure{runCatching{MediaController.releaseFuture(future)}}},MoreExecutors.directExecutor())
        }
    }
}
