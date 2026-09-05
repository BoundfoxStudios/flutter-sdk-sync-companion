package com.boundfoxstudios.fluttersdksync.sync

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFileManager
import com.boundfoxstudios.fluttersdksync.apply.FlutterSdkApplier
import com.boundfoxstudios.fluttersdksync.discovery.VersionManagerSdkLocator
import com.boundfoxstudios.fluttersdksync.settings.FlutterSdkSyncSettings
import com.boundfoxstudios.fluttersdksync.watch.DebouncedTrigger
import com.boundfoxstudios.fluttersdksync.watch.FvmWatchScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

@Service(Service.Level.PROJECT)
class FlutterSdkSyncService(
  private val project: Project,
  coroutineScope: CoroutineScope,
) {

  private val locator = VersionManagerSdkLocator()
  private val applier = FlutterSdkApplier()
  private val propagator = FlutterSdkPropagator()
  private val trigger = DebouncedTrigger(coroutineScope, QUIET_PERIOD, ::synchronize)
  private val stateMutex = Mutex()
  private var lastObservedTarget: Path? = null
  private var propagationOwed = false
  // Set from the EDT and consumed on the service scope, hence atomic instead of the Mutex.
  private val propagationForced = AtomicBoolean(false)

  private val settings: FlutterSdkSyncSettings
    get() = FlutterSdkSyncSettings.getInstance(project)

  init {
    val projectBasePath = project.basePath
    if (projectBasePath != null) {
      val watchScope = FvmWatchScope(projectBasePath)
      val changeApplier = object : AsyncFileListener.ChangeApplier {
        override fun afterVfsChange() {
          trigger.signal()
        }
      }
      // VFS events are application-wide and prepareChange runs for every batch in the whole IDE,
      // hence the lazy filter anchored on the project base path.
      VirtualFileManager.getInstance().addAsyncFileListener(coroutineScope) { events ->
        val paths = events.asSequence().map { event -> event.path }
        if (watchScope.matchesAny(paths)) changeApplier else null
      }
    }
  }

  // A service created while synchronization was off has observed no target at all, and the
  // configured path string is the same either way, so turning it on has to propagate blind.
  fun requestForcedPropagation() {
    propagationForced.set(true)
    trigger.signal()
  }

  suspend fun synchronize() {
    // Checked before any state is touched: lastObservedTarget has to keep the target that was last
    // propagated, so an "fvm use" made while synchronization was off still counts as a switch.
    if (!settings.synchronizationEnabled) return

    val projectRoot = project.basePath?.let(Path::of) ?: return
    val sdkPath = withContext(Dispatchers.IO) { locator.locate(projectRoot) } ?: return
    val resolvedTarget = withContext(Dispatchers.IO) { locator.resolveTarget(sdkPath) } ?: return

    stateMutex.withLock {
      val configurationChanged = applier.applyIfChanged(project, sdkPath)
      if (configurationChanged) {
        LOG.info("Flutter SDK set to $sdkPath")
      }
      val previousTarget = lastObservedTarget
      // The first observation is not a switch: a project that is already configured correctly
      // must not run a pub get just because it was opened.
      val targetSwitched = previousTarget != null && previousTarget != resolvedTarget
      lastObservedTarget = resolvedTarget
      // Consumed outside the condition: behind a short-circuiting || the request would survive
      // into the next run and propagate a second time for nothing.
      val forcedPropagation = propagationForced.getAndSet(false)
      if (configurationChanged || targetSwitched || forcedPropagation) {
        propagationOwed = true
      }
      if (propagationOwed) {
        val runPubGet = settings.runPubGet
        LOG.info("Propagating Flutter SDK $resolvedTarget (pub get: $runPubGet)")
        // Stays owed when the propagation throws or when io.flutter resolves no SDK at that
        // path, so the next signal retries it.
        propagationOwed = !propagator.propagate(project, sdkPath, runPubGet)
      }
    }
  }

  companion object {
    fun getInstance(project: Project): FlutterSdkSyncService = project.service()

    private val QUIET_PERIOD = 500.milliseconds
    private val LOG: Logger = Logger.getInstance(FlutterSdkSyncService::class.java)
  }
}
