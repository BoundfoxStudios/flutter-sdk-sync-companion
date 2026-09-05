package de.boundfoxstudios.fluttersdksync.discovery

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class VersionManagerSdkLocator {

  fun locate(projectRoot: Path): Path? =
    symlinkCandidates(projectRoot).firstOrNull { isFlutterSdkHome(it) }

  fun resolveTarget(sdkPath: Path): Path? =
    try {
      sdkPath.toRealPath()
    } catch (_: IOException) {
      null
    }

  private fun symlinkCandidates(projectRoot: Path): Sequence<Path> = sequence {
    val fvmDirectory = projectRoot.resolve(FVM_DIRECTORY)
    yield(fvmDirectory.resolve(SDK_SYMLINK))
    yieldAll(versionSymlinks(fvmDirectory.resolve(VERSIONS_DIRECTORY)))
  }

  private fun versionSymlinks(versionsDirectory: Path): List<Path> {
    if (!Files.isDirectory(versionsDirectory)) {
      return emptyList()
    }
    return Files.newDirectoryStream(versionsDirectory).use { entries ->
      entries.sortedBy { it.fileName.toString() }
    }
  }

  private fun isFlutterSdkHome(candidate: Path): Boolean =
    Files.isRegularFile(candidate.resolve("packages/flutter/pubspec.yaml")) &&
      Files.isRegularFile(candidate.resolve("bin/flutter")) &&
      Files.isDirectory(candidate.resolve("bin/cache/dart-sdk/lib"))

  private companion object {
    const val FVM_DIRECTORY = ".fvm"
    const val SDK_SYMLINK = "flutter_sdk"
    const val VERSIONS_DIRECTORY = "versions"
  }
}
