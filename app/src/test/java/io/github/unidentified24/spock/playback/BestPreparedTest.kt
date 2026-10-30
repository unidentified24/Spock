package io.github.unidentified24.spock.playback
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class BestPreparedTest {
 @Test fun fastCompressedCandidateIsPublishedBeforeLosslessCompletes()=runBlocking {
  val lossless=CompletableDeferred<Int>();val early=CompletableDeferred<Int>();val offered=mutableListOf<Int>()
  val result=async{bestPrepared(listOf(StreamLookup{100},StreamLookup{lossless.await()}),{it}){offered+=it;early.complete(it)}}
  assertEquals(100,withTimeout(2000){early.await()});assertFalse(result.isCompleted)
  lossless.complete(400);assertEquals(400,result.await());assertEquals(listOf(100,400),offered)
 }
 @Test fun lowerQualityDoesNotReplaceBufferedLossless()=runBlocking {
  val offered=mutableListOf<Int>()
  assertEquals(400,bestPrepared(listOf(StreamLookup{400},StreamLookup{yield();100}),{it}){offered+=it})
  assertEquals(listOf(400),offered)
 }
 @Test fun missesAndExceptionsDoNotEraseUsableCandidate()=runBlocking {
  assertEquals(100,bestPrepared(listOf(StreamLookup<Int>{null},StreamLookup{error("miss")},StreamLookup{100}),{it}){})
 }
 @Test fun timeoutKeepsAlreadyPreparedValueAndClosesHungSource()=runBlocking {
  var closed=false
  assertEquals(100,bestPrepared(listOf(StreamLookup{100},StreamLookup<Int>(cancel={closed=true}){awaitCancellation()}),{it},25){})
  assertTrue(closed)
 }
 @Test fun queueChangeCancellationClosesAllResources()=runBlocking {
  val entered=CompletableDeferred<Unit>();var closed=false
  val task=async{bestPrepared(listOf(StreamLookup<Int>(cancel={closed=true}){entered.complete(Unit);awaitCancellation()}),{it}){}}
  entered.await();task.cancelAndJoin();assertTrue(closed)
 }
 @Test fun noSourcesHasNoCandidate()=runBlocking {assertNull(bestPrepared<Int>(emptyList(),{it}){})}
}
