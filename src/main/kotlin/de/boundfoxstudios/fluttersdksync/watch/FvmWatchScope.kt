package de.boundfoxstudios.fluttersdksync.watch

class FvmWatchScope(projectBasePath: String) {

  private val projectRoot = projectBasePath.trimEnd('/')
  private val configurationFile = "$projectRoot/$CONFIGURATION_FILE"
  private val fvmDirectory = "$projectRoot/$FVM_DIRECTORY"
  private val versionsDirectory = "$fvmDirectory/$VERSIONS_DIRECTORY"

  fun matches(path: String): Boolean =
    path == configurationFile ||
      path == fvmDirectory ||
      isDirectChildOf(path, fvmDirectory) ||
      isDirectChildOf(path, versionsDirectory)

  fun matchesAny(paths: Sequence<String>): Boolean = paths.any(::matches)

  // Only direct children: "fvm install" unpacks a whole SDK below .fvm/versions/<name>, so a
  // prefix match would fire for every single unpacked file.
  private fun isDirectChildOf(path: String, directory: String): Boolean =
    path.startsWith("$directory/") && path.indexOf('/', directory.length + 1) < 0

  private companion object {
    const val FVM_DIRECTORY = ".fvm"
    const val VERSIONS_DIRECTORY = "versions"
    const val CONFIGURATION_FILE = ".fvmrc"
  }
}
