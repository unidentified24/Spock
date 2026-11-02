package io.github.unidentified24.spock.features

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.unidentified24.spock.features.downloads.DownloadScheduler

class OfflineTileService:TileService(){
    override fun onStartListening(){super.onStartListening();sync()}
    override fun onClick(){
        super.onClick()
        val next=!FeatureSettings.value.downloads.offlineOnly
        FeatureSettings.update{it.copy(downloads=it.downloads.copy(offlineOnly=next))}
        if(!next)DownloadScheduler.kick(applicationContext,replace=true)
        sync()
    }
    private fun sync(){
        val tile=qsTile ?: return
        val offline=FeatureSettings.value.downloads.offlineOnly
        tile.state=if(offline)Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label="Offline music"
        if(android.os.Build.VERSION.SDK_INT>=29)tile.subtitle=if(offline)"On · saved music only" else "Off · online sources available"
        tile.updateTile()
    }
}
