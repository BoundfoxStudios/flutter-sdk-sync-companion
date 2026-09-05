package com.boundfoxstudios.fluttersdksync.apply

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.jetbrains.lang.dart.sdk.DartSdk
import io.flutter.sdk.FlutterSdkUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

class FlutterSdkApplier {

  // The symlink is stored unresolved on purpose: fvm re-points it on every "fvm use", so the
  // configured path keeps tracking the active version. Resolving it would pin the project to
  // one cached version and make "fvm doctor" report a broken setup.
  suspend fun applyIfChanged(project: Project, sdkPath: Path): Boolean {
    val targetPath = FileUtil.toSystemIndependentName(sdkPath.toString())
    // DartSdk.getDartSdk recomputes a cached value that stats and reads files from disk.
    val configuredPath = withContext(Dispatchers.IO) { configuredSdkPath(project) }
    if (configuredPath == targetPath) {
      return false
    }
    // Configuring the Dart SDK roots only works once the path is known to the VFS.
    withContext(Dispatchers.IO) {
      LocalFileSystem.getInstance().refreshAndFindFileByNioFile(sdkPath)
    } ?: run {
      LOG.warn("Flutter SDK path is not visible in the VFS: $targetPath")
      return false
    }
    // setFlutterSdkPath takes a write action itself, and 253 rejects those off the EDT.
    withContext(Dispatchers.EDT) { FlutterSdkUtil.setFlutterSdkPath(project, targetPath) }
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
    val LOG: Logger = Logger.getInstance(FlutterSdkApplier::class.java)
  }
}
