package io.github.unidentified24.spock.data.backup

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class SpockDataCodecTest {
    @Test fun roundTripPreservesPortableTypesAndExcludesCredentials(){
        val original=mapOf("appearance" to mapOf("theme" to JsonPrimitive("OCEAN")),"LikedSongs" to mapOf("123" to JsonPrimitive("123")),"settings_prefs" to mapOf("preload_enabled" to JsonPrimitive(false),"crossfade_duration_ms" to JsonPrimitive(6000),"youtube_cookie" to JsonPrimitive("private-cookie")),"SpotifyAuth" to mapOf("token" to JsonPrimitive("secret")))
        val text=SpockDataCodec.encode(original)
        assertFalse(text.contains("private-cookie"));assertFalse(text.contains("secret"));assertFalse(text.contains("youtube_cookie"))
        val restored=SpockDataCodec.decode(text)
        assertEquals(false,restored.getValue("settings_prefs").getValue("preload_enabled").jsonPrimitive.boolean)
        assertEquals(6000,restored.getValue("settings_prefs").getValue("crossfade_duration_ms").jsonPrimitive.int)
        assertEquals("123",restored.getValue("LikedSongs").getValue("123").jsonPrimitive.content)
    }
    @Test fun rejectsUnsupportedVersionAndCredentialInjectionBeforeRestore(){
        listOf("""{"format":"spockdata","version":2,"data":{"appearance":{}}}""","""{"format":"spockdata","version":1,"data":{"settings_prefs":{"youtube_cookie":"injected"}}}""","""{"format":"spockdata","version":1,"data":{"SpotifyAuth":{"token":"injected"}}}""").forEach{assertThrows(IllegalArgumentException::class.java){SpockDataCodec.decode(it)}}
    }
    @Test fun rejectsMalformedTypesAndInvalidListeningDates(){
        listOf("""{"settings_prefs":{"preload_enabled":"false"}}""","""{"settings_prefs":{"crossfade_duration_ms":-1}}""","""{"LibraryCache":{"entries":"not json"}}""","""{"listening_meter":{"ledger":"{\"bad\":{\"date\":\"not-a-date\",\"ms\":10,\"songId\":1}}"}}""").forEach{data->assertThrows(IllegalArgumentException::class.java){SpockDataCodec.decode("""{"format":"spockdata","version":1,"data":$data}""")}}
    }
    @Test fun rejectsOversizedInputAndUnknownSections(){
        assertThrows(IllegalArgumentException::class.java){SpockDataCodec.decode(" ".repeat(SpockDataCodec.MAX_BYTES+1))}
        assertFalse(SpockDataCodec.allowed("downloads","/data/private/track"))
        assertFalse(SpockDataCodec.allowed("appearance","../token"))
    }
}
