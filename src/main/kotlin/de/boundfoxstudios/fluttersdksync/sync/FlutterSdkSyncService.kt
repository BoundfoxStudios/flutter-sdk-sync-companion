package de.boundfoxstudios.fluttersdksync.sync

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFileManager
import de.boundfoxstudios.fluttersdksync.apply.FlutterSdkApplier
import de.boundfoxstudios.fluttersdksync.discovery.VersionManagerSdkLocator
import de.boundfoxstudios.fluttersdksync.watch.DebouncedTrigger
import de.boundfoxstudios.fluttersdksync.watch.FvmWatchScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Path
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

  suspend fun synchronize() {
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
      if (configurationChanged || targetSwitched) {
        propagationOwed = true
      }
      if (propagationOwed) {
        LOG.info("Propagating Flutter SDK $resolvedTarget")
        propagator.propagate(project, sdkPath)
        // Cleared only after a successful propagation, so a failure is retried on the next signal.
        propagationOwed = false
      }
    }
  }

  companion object {
    fun getInstance(project: Project): FlutterSdkSyncService = project.service()

    private val QUIET_PERIOD = 500.milliseconds
    private val LOG: Logger = Logger.getInstance(FlutterSdkSyncService::class.java)
  }
}
