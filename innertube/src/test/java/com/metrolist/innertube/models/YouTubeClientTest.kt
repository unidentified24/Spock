package com.metrolist.innertube.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeClientTest {
    @Test fun directAudioClientKeepsItsOwnIdentityAtTheCdn(){
        val client=YouTubeClient.IOS_DIRECT
        val restored=YouTubeClient.forStreamUrl("https://r.example.googlevideo.com/videoplayback?c=IOS&cver=20.10.4")
        assertEquals(client,restored)
        assertEquals(client.userAgent,restored.mediaHeaders()["User-Agent"])
        assertFalse(client.useWebPoTokens)
    }
    @Test fun missingVersionHintDoesNotFallBackToAnIncompatibleUserAgent(){
        val client=YouTubeClient.IOS_DIRECT
        val original="https://r.example.googlevideo.com/videoplayback?c=IOS&sig=unchanged"
        val tagged=client.streamUrlWithIdentity(original)
        assertTrue(tagged.startsWith(original))
        assertEquals(client,YouTubeClient.forStreamUrl(tagged))
        assertEquals(1,Regex("cver=").findAll(client.streamUrlWithIdentity(tagged)).count())
    }
    @Test
    fun nativeMusicClientUsesYouTubeEndpointWithoutBrowserHeaders() {
        val client = YouTubeClient.ANDROID_MUSIC

        assertEquals("https://www.youtube.com/youtubei/v1/player", client.playerApiUrl)
        assertEquals(client.userAgent, client.mediaHeaders()["User-Agent"])
        assertNull(client.mediaHeaders()["Origin"])
        assertNull(client.mediaHeaders()["Referer"])
        assertFalse(client.loginSupported)
    }

    @Test
    fun webRemixUsesMusicEndpointAndMatchingMediaHeaders() {
        val client = YouTubeClient.WEB_REMIX

        assertEquals("https://music.youtube.com/youtubei/v1/player", client.playerApiUrl)
        assertEquals("https://music.youtube.com", client.mediaHeaders()["Origin"])
        assertEquals("https://music.youtube.com/", client.mediaHeaders()["Referer"])
        assertTrue(client.loginSupported)
    }

    @Test
    fun streamUrlRestoresTheMintingIdentity() {
        val nativeUrl = "https://r.example.googlevideo.com/videoplayback?c=ANDROID_MUSIC&cver=8.39.42"
        val webUrl = "https://r.example.googlevideo.com/videoplayback?c=WEB_REMIX&cver=1.20260707.12.00"
        val legacyVrUrl = "https://r.example.googlevideo.com/videoplayback?c=ANDROID_VR&cver=1.43.32"

        assertEquals(YouTubeClient.ANDROID_MUSIC, YouTubeClient.forStreamUrl(nativeUrl))
        assertEquals(YouTubeClient.WEB_REMIX, YouTubeClient.forStreamUrl(webUrl))
        assertEquals(YouTubeClient.ANDROID_VR_1_43_32, YouTubeClient.forStreamUrl(legacyVrUrl))
    }
}
