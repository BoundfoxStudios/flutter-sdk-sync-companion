package de.boundfoxstudios.fluttersdksync.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import de.boundfoxstudios.fluttersdksync.sync.FlutterSdkSyncService

// BoundConfigurable insists on a display name, but the settings tree renders the displayName of
// the extension point, so renaming the page here alone changes nothing.
class FlutterSdkSyncConfigurable(private val project: Project) : BoundConfigurable("Flutter SDK Sync") {

  private val settings = FlutterSdkSyncSettings.getInstance(project)

  override fun createPanel(): DialogPanel = panel {
    lateinit var synchronizationEnabled: Cell<JBCheckBox>
    row {
      synchronizationEnabled = checkBox("Keep the Flutter SDK in sync with FVM")
        .bindSelected(settings::synchronizationEnabled)
        .comment("The SDK is taken from the project's .fvm directory. Other version managers are not supported.")
    }
    indent {
      row {
        checkBox("Run 'pub get' after a version switch")
          .bindSelected(settings::runPubGet)
          .comment(
            "When disabled, the project keeps resolving its packages against the previous SDK " +
              "until you run pub get manually.",
          )
      }
    }.enabledIf(synchronizationEnabled.selected)
  }

  override fun apply() {
    val wasEnabled = settings.synchronizationEnabled
    super.apply()
    // The project may have been switched to another version while synchronization was off.
    if (!wasEnabled && settings.synchronizationEnabled) {
      FlutterSdkSyncService.getInstance(project).requestForcedPropagation()
    }
  }
}
