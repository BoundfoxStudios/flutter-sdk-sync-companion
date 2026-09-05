package de.boundfoxstudios.fluttersdksync.watch

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FvmWatchScopeTest {

  private val watchScope = FvmWatchScope(PROJECT_ROOT)

  @Test
  fun matches_sdkSymlink_returnsTrue() {
    assertTrue(watchScope.matches("$PROJECT_ROOT/.fvm/flutter_sdk"))
  }

  @Test
  fun matches_fvmDirectoryItself_returnsTrue() {
    assertTrue(watchScope.matches("$PROJECT_ROOT/.fvm"))
  }

  @Test
  fun matches_fileInsideCachedSdk_returnsFalse() {
    assertFalse(watchScope.matches("$PROJECT_ROOT/.fvm/versions/3.35.0/bin/flutter"))
  }

  @Test
  fun matches_directChildOfVersionsDirectory_returnsTrue() {
    assertTrue(watchScope.matches("$PROJECT_ROOT/.fvm/versions/3.35.0"))
  }

  @Test
  fun matches_siblingProjectSharingThePathPrefix_returnsFalse() {
    assertFalse(watchScope.matches("${PROJECT_ROOT}-other/.fvm/flutter_sdk"))
  }

  @Test
  fun matches_sourceFile_returnsFalse() {
    assertFalse(watchScope.matches("$PROJECT_ROOT/lib/main.dart"))
  }

  @Test
  fun matchesAny_batchContainingConfigurationFile_returnsTrue() {
    val paths = sequenceOf("$PROJECT_ROOT/lib/main.dart", "$PROJECT_ROOT/.fvmrc")

    assertTrue(watchScope.matchesAny(paths))
  }

  private companion object {
    const val PROJECT_ROOT = "/home/manu/projects/app"
  }
}
