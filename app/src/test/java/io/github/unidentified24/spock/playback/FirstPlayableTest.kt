package io.github.unidentified24.spock.playback

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.*
import org.junit.Test

class FirstPlayableTest {
    @Test fun startsAllProvidersAndCancelsLosersBeforeReturning() = runBlocking {
        withTimeout(2000) {
            val started = Channel<Int>(3)
            val gates = List(3) { CompletableDeferred<String>() }
            val stopped = mutableSetOf<Int>()
            val winner = async {
                firstPlayable(gates.mapIndexed { index, gate ->
                    StreamLookup {
                        started.send(index)
                        try { gate.await() } finally { stopped.add(index) }
                    }
                })
            }
            val allStarted = (0..2).map { started.receive() }.toSet()
            assertEquals(setOf(0, 1, 2), allStarted)
            gates[2].complete("YouTube")
            assertEquals("YouTube", winner.await())
            assertEquals(setOf(0, 1, 2), stopped)
            assertTrue(gates[0].isActive) // cancelling the resolver did not complete its external gate
        }
    }

    @Test fun missAndExceptionDoNotBeatAUsableSource() = runBlocking {
        assertEquals("FLAC", firstPlayable(listOf(
            StreamLookup<String> { null },
            StreamLookup<String> { error("Deezer unavailable") },
            StreamLookup { yield(); "FLAC" },
        )))
    }

    @Test fun fasterDeezerIsNotHeldForAQualityUpgrade() = runBlocking {
        var flacStopped = false
        val result = firstPlayable(listOf(
            StreamLookup { yield(); "Deezer MP3" },
            StreamLookup<String> { try { awaitCancellation() } finally { flacStopped = true } },
        ))
        assertEquals("Deezer MP3", result)
        assertTrue(flacStopped)
    }

    @Test fun allMissesAndNoEnabledSourcesReturnNull() = runBlocking {
        assertNull(firstPlayable<String>(emptyList()))
        assertNull(firstPlayable(listOf(StreamLookup<String> { null }, StreamLookup<String> { null })))
    }

    @Test fun deadlineCancelsHungSourcesAndClosesTheirResources() = runBlocking {
        var closed = false
        var stopped = false
        assertNull(firstPlayable(listOf(StreamLookup<String>(cancel = { closed = true }) {
            try { awaitCancellation() } finally { stopped = true }
        }), timeoutMs = 25))
        assertTrue(closed)
        assertTrue(stopped)
    }

    @Test fun skippingOrStoppingCancelsTheWholeRace() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var closed = false
        var stopped = false
        val request = async {
            firstPlayable(listOf(StreamLookup<String>(cancel = { closed = true }) {
                started.complete(Unit)
                try { awaitCancellation() } finally { stopped = true }
            }))
        }
        started.await()
        request.cancelAndJoin()
        assertTrue(closed)
        assertTrue(stopped)
        assertTrue(request.isCancelled)
    }
}
