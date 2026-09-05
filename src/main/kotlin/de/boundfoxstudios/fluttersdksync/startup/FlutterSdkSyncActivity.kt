package de.boundfoxstudios.fluttersdksync.startup

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import de.boundfoxstudios.fluttersdksync.sync.FlutterSdkSyncService

class FlutterSdkSyncActivity : ProjectActivity {

  // Creating the service also registers the file listener, so a project that has no .fvm yet
  // still notices a later "fvm use".
  override suspend fun execute(project: Project) {
    FlutterSdkSyncService.getInstance(project).synchronize()
  }
}
