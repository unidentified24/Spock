package io.github.unidentified24.spock.features.downloads

import android.content.*
import android.net.*
import android.os.BatteryManager

data class ConnectionState(val online:Boolean,val wifi:Boolean,val metered:Boolean,val charging:Boolean)
object NetworkStatus {
    fun read(context:Context):ConnectionState {
        val manager=context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps=manager.activeNetwork?.let(manager::getNetworkCapabilities)
        val battery=context.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val charging=battery?.getIntExtra(BatteryManager.EXTRA_STATUS,-1) in setOf(BatteryManager.BATTERY_STATUS_CHARGING,BatteryManager.BATTERY_STATUS_FULL)
        return ConnectionState(caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)==true,caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true,manager.isActiveNetworkMetered,charging)
    }
}
