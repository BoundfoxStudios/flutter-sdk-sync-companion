package de.boundfoxstudios.fluttersdksync.sync

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import io.flutter.pub.PubRoot
import io.flutter.pub.PubRoots
import io.flutter.run.daemon.DeviceService
import io.flutter.sdk.FlutterSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

class FlutterSdkPropagator {

  suspend fun propagate(project: Project, sdkPath: Path, runPubGet: Boolean): Boolean {
    // A synchronous VFS refresh must run neither on the EDT nor under a read lock, and
    // FlutterSdk.getFlutterSdk stats and reads SDK files, so both belong on a background thread.
    // The lookup also guards the device restart below, so it stays outside the pub get branch,
    // and finding no SDK means nothing was propagated and the caller has to retry.
    val flutterSdk = withContext(Dispatchers.IO) {
      refreshSdkFiles(sdkPath)
      FlutterSdk.getFlutterSdk(project)
    } ?: return false

    if (runPubGet) {
      val modulesByPubRoot = readAction { collectModulesByPubRoot(project) }

      // The pub get below is the only consumer, and this saves every unsaved document in the
      // whole IDE.
      withContext(Dispatchers.EDT) {
        FileDocumentManager.getInstance().saveAllDocuments()
      }

      // .dart_tool/package_config.json holds absolute SDK paths, and PubRoot.hasUpToDatePackages
      // only compares pubspec timestamps, so the pub get has to be forced.
      withContext(Dispatchers.IO) {
        modulesByPubRoot.forEach { (pubRoot, module) ->
          flutterSdk.flutterPackagesGet(pubRoot).startInModuleConsole(module, pubRoot::refresh, null)
        }
      }
    }

    // A plain refresh would be a no-op: the daemon command compares equal because the configured
    // SDK path string is unchanged across an "fvm use".
    DeviceService.getInstance(project).restart()
    return true
  }

  private fun refreshSdkFiles(sdkPath: Path) {
    val sdkHome = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(sdkPath) ?: return
    // refreshAndFindFileByNioFile does not re-read an entry that is already cached, so the
    // re-pointed symlink and the version files behind it have to be marked dirty explicitly.
    // The Flutter version files feed FlutterSdkVersion, the Dart ones invalidate the cached
    // DartSdk, which in turn makes the analysis server restart itself.
    val versionFiles = VERSION_FILES.mapNotNull(sdkHome::findFileByRelativePath)
    VfsUtil.markDirtyAndRefresh(false, false, false, sdkHome, *versionFiles.toTypedArray())
  }

  private fun collectModulesByPubRoot(project: Project): List<Pair<PubRoot, Module>> =
    PubRoots.forProject(project).mapNotNull { pubRoot ->
      pubRoot.getModule(project)?.let { module -> pubRoot to module }
    }

  private companion object {
    val VERSION_FILES = listOf(
      "bin/cache/flutter.version.json",
      "version",
      "bin/cache/dart-sdk/version",
      "bin/cache/dart-sdk/lib/core/core.dart",
    )
  }
}
