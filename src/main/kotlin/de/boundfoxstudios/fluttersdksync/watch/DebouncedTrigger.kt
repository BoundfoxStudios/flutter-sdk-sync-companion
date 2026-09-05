package de.boundfoxstudios.fluttersdksync.watch

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.time.Duration

// Flow.debounce is still @FlowPreview in the coroutines fork bundled with the platform, and the
// Alarm classes it replaces are @Obsolete in 253.
@OptIn(FlowPreview::class)
class DebouncedTrigger(
  scope: CoroutineScope,
  quietPeriod: Duration,
  onQuiet: suspend () -> Unit,
) {

  // replay = 1 keeps a signal that arrives before the collector has subscribed.
  private val signals = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    scope.launch {
      signals.debounce(quietPeriod).collect {
        // A failure must not end the collection: the trigger would stay silent for the rest
        // of the session. It is logged as a warning because the causes are environmental.
        try {
          onQuiet()
        } catch (cancellation: CancellationException) {
          throw cancellation
        } catch (failure: Exception) {
          LOG.warn("Flutter SDK synchronization failed", failure)
        }
      }
    }
  }

  fun signal() {
    signals.tryEmit(Unit)
  }

  private companion object {
    val LOG: Logger = Logger.getInstance(DebouncedTrigger::class.java)
  }
}
