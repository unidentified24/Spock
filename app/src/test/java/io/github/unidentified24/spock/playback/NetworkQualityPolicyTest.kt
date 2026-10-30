package io.github.unidentified24.spock.playback

import org.junit.Assert.*
import org.junit.Test

class NetworkQualityPolicyTest {
    @Test fun explicitWifiActionWorksOnMeteredWifiAndNeverChangesMobileData(){
        assertFalse(NetworkQualityPolicy.useWifi(true,true,false))
        assertTrue(NetworkQualityPolicy.useWifi(true,true,true))
        assertTrue(NetworkQualityPolicy.useWifi(true,false,false))
        assertFalse(NetworkQualityPolicy.useWifi(false,true,true))
        assertFalse(NetworkQualityPolicy.useWifi(false,false,true))
    }
}
