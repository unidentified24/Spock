package io.github.unidentified24.spock.features

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import io.github.unidentified24.spock.MainActivity
import io.github.unidentified24.spock.R

object LauncherShortcuts {
    fun publish(context:Context){
        if(android.os.Build.VERSION.SDK_INT<25)return
        val manager=context.getSystemService(ShortcutManager::class.java) ?: return
        manager.dynamicShortcuts=listOf(
            shortcut(context,"downloads","Downloads","Open offline music","downloads",0),
            shortcut(context,"collection","Collection","Open your library","library",1),
            shortcut(context,"queue","Queue","Open what plays next","queue",2),
            shortcut(context,"mixes","Mixes","Find something new","mixes",3),
            shortcut(context,"sleep","Sleep timer","Set a listening timer","player",4),
        )
    }
    private fun shortcut(context:Context,id:String,label:String,details:String,page:String,rank:Int)=
        ShortcutInfo.Builder(context,id).setShortLabel(label).setLongLabel(details)
            .setIcon(Icon.createWithResource(context,R.mipmap.ic_launcher))
            .setIntent(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("spock_page",page))
            .setRank(rank).build()
}
