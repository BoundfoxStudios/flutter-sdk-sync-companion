package de.boundfoxstudios.fluttersdksync.settings

import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "FlutterSdkSyncSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class FlutterSdkSyncSettings :
  SerializablePersistentStateComponent<FlutterSdkSyncSettings.SettingsState>(SettingsState()) {

  // The XML serializer binds mutable properties only: a "val", even with @JvmField, is dropped
  // silently. The names become the option keys in workspace.xml and cannot change after a release.
  data class SettingsState(
    var synchronizationEnabled: Boolean = true,
    var runPubGet: Boolean = true,
  )

  var synchronizationEnabled: Boolean
    get() = state.synchronizationEnabled
    set(value) {
      updateState { it.copy(synchronizationEnabled = value) }
    }

  var runPubGet: Boolean
    get() = state.runPubGet
    set(value) {
      updateState { it.copy(runPubGet = value) }
    }

  companion object {
    fun getInstance(project: Project): FlutterSdkSyncSettings = project.service()
  }
}
