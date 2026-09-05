package de.boundfoxstudios.fluttersdksync.apply

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.jetbrains.lang.dart.sdk.DartSdk
import io.flutter.sdk.FlutterSdkUtil
import java.nio.file.Path

class FlutterSdkApplier {

  // The symlink is stored unresolved on purpose: fvm re-points it on every "fvm use", so the
  // configured path keeps tracking the active version. Resolving it would pin the project to
  // one cached version and make "fvm doctor" report a broken setup.
  fun applyIfChanged(project: Project, sdkPath: Path): Boolean {
    val targetPath = FileUtil.toSystemIndependentName(sdkPath.toString())
    if (configuredSdkPath(project) == targetPath) {
      return false
    }
    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(sdkPath) ?: return false
    FlutterSdkUtil.setFlutterSdkPath(project, targetPath)
    return true
  }

  private fun configuredSdkPath(project: Project): String? {
    val dartSdkHome = DartSdk.getDartSdk(project)?.homePath ?: return null
    val normalizedHome = FileUtil.toSystemIndependentName(dartSdkHome)
    val flutterHome = normalizedHome.removeSuffix(DART_SDK_SUFFIX)
    return flutterHome.takeIf { it != normalizedHome }
  }

  private companion object {
    const val DART_SDK_SUFFIX = "/bin/cache/dart-sdk"
  }
}
