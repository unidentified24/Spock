package io.github.unidentified24.spock.playback

object NetworkQualityPolicy {
    fun useWifi(wifi:Boolean,metered:Boolean,explicitWifiFlac:Boolean)=wifi&&(!metered||explicitWifiFlac)
}
