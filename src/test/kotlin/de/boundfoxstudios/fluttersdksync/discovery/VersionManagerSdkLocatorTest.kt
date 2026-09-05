package de.boundfoxstudios.fluttersdksync.discovery

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VersionManagerSdkLocatorTest {

  private val locator = VersionManagerSdkLocator()
  private lateinit var temporaryDirectory: Path
  private lateinit var projectRoot: Path

  @BeforeTest
  fun setUp() {
    temporaryDirectory = Files.createTempDirectory("flutter-sdk-sync")
    projectRoot = temporaryDirectory.resolve("project").createDirectories()
  }

  @AfterTest
  fun tearDown() {
    temporaryDirectory.toFile().deleteRecursively()
  }

  @Test
  fun locate_projectWithSdkSymlink_returnsUnresolvedSymlinkPath() {
    val sdkHome = createFlutterSdkHome("3.35.0")
    val symlink = projectRoot.resolve(".fvm/flutter_sdk")
    symlink.parent.createDirectories()
    Files.createSymbolicLink(symlink, sdkHome)

    assertEquals(symlink, locator.locate(projectRoot))
  }

  @Test
  fun locate_symlinkPointingToIncompleteSdk_returnsNull() {
    val incompleteSdk = temporaryDirectory.resolve("incomplete").createDirectories()
    val symlink = projectRoot.resolve(".fvm/flutter_sdk")
    symlink.parent.createDirectories()
    Files.createSymbolicLink(symlink, incompleteSdk)

    assertNull(locator.locate(projectRoot))
  }

  @Test
  fun locate_projectWithoutSdkSymlink_fallsBackToVersionSymlink() {
    val sdkHome = createFlutterSdkHome("3.35.0")
    val versionSymlink = projectRoot.resolve(".fvm/versions/3.35.0")
    versionSymlink.parent.createDirectories()
    Files.createSymbolicLink(versionSymlink, sdkHome)

    assertEquals(versionSymlink, locator.locate(projectRoot))
  }

  @Test
  fun locate_projectWithoutFvmDirectory_returnsNull() {
    assertNull(locator.locate(projectRoot))
  }

  private fun createFlutterSdkHome(version: String): Path {
    val sdkHome = temporaryDirectory.resolve("cache/versions/$version")
    sdkHome.resolve("packages/flutter").createDirectories().resolve("pubspec.yaml").createFile()
    sdkHome.resolve("bin").createDirectories().resolve("flutter").createFile()
    sdkHome.resolve("bin/cache/dart-sdk/lib").createDirectories()
    return sdkHome
  }
}
