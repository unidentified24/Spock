package io.github.unidentified24.spock.playback

import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test

class BoundedInputTest {
    @Test fun handlesPartialHeaderReadsAndStopsAtTheLimit(){
        val expected=ByteArray(100){it.toByte()}
        val stream=object:ByteArrayInputStream(expected){override fun read(b:ByteArray,off:Int,len:Int)=super.read(b,off,minOf(len,3))}
        assertArrayEquals(expected.copyOf(42),stream.readPrefix(42))
        assertEquals(42,stream.read())
    }
    @Test fun returnsOnlyAvailableBytesOnEarlyEof(){assertArrayEquals(byteArrayOf(1,2),ByteArrayInputStream(byteArrayOf(1,2)).readPrefix(42))}
}
