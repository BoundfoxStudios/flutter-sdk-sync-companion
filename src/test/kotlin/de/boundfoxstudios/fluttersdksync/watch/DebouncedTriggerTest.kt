package de.boundfoxstudios.fluttersdksync.watch

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class DebouncedTriggerTest {

  private val collectorExecutor = Executors.newSingleThreadExecutor()
  private val scope = CoroutineScope(collectorExecutor.asCoroutineDispatcher())

  @AfterTest
  fun tearDown() {
    scope.cancel()
    collectorExecutor.shutdownNow()
  }

  @Test
  fun signal_issuedBeforeTheCollectorSubscribes_stillFires() {
    val fired = CompletableDeferred<Unit>()
    val collectorMayStart = CountDownLatch(1)
    // Occupying the only collector thread orders the signal before the subscription instead of
    // leaving that ordering to chance.
    collectorExecutor.execute { collectorMayStart.await() }
    val trigger = DebouncedTrigger(scope, QUIET_PERIOD) { fired.complete(Unit) }

    trigger.signal()
    collectorMayStart.countDown()

    runBlocking { withTimeout(TIMEOUT) { fired.await() } }
  }

  @Test
  fun signal_callbackFailed_stillFiresOnTheNextSignal() {
    val firstCallStarted = CompletableDeferred<Unit>()
    val firedAgain = CompletableDeferred<Unit>()
    val trigger = DebouncedTrigger(scope, QUIET_PERIOD) {
      if (firstCallStarted.complete(Unit)) {
        throw IllegalStateException("propagation failed")
      }
      firedAgain.complete(Unit)
    }

    trigger.signal()

    runBlocking {
      withTimeout(TIMEOUT) {
        firstCallStarted.await()
        trigger.signal()
        firedAgain.await()
      }
    }
  }

  private companion object {
    val QUIET_PERIOD = 50.milliseconds
    val TIMEOUT = 2.seconds
  }
}
