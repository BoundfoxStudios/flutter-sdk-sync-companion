package de.boundfoxstudios.fluttersdksync.startup

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import de.boundfoxstudios.fluttersdksync.apply.FlutterSdkApplier
import de.boundfoxstudios.fluttersdksync.discovery.VersionManagerSdkLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

class FlutterSdkSyncActivity : ProjectActivity {

  private val locator = VersionManagerSdkLocator()
  private val applier = FlutterSdkApplier()

  override suspend fun execute(project: Project) {
    val projectRoot = project.basePath?.let(Path::of) ?: return
    val sdkPath = withContext(Dispatchers.IO) { locator.locate(projectRoot) } ?: return

    if (applier.applyIfChanged(project, sdkPath)) {
      LOG.info("Flutter SDK set to $sdkPath")
    }
  }

  private companion object {
    val LOG: Logger = Logger.getInstance(FlutterSdkSyncActivity::class.java)
  }
}
