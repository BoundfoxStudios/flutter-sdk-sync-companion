import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.PublishPluginTask
import org.jetbrains.intellij.platform.gradle.tasks.SignPluginTask

plugins {
  id("org.jetbrains.kotlin.jvm") version "2.4.10"
  id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "com.boundfoxstudios"
version = "1.0.0" // x-release-please-version

repositories {
  mavenCentral()
  intellijPlatform {
    defaultRepositories()
  }
}

dependencies {
  intellijPlatform {
    intellijIdea(providers.gradleProperty("targetIdeVersion"))
    plugin("io.flutter", providers.gradleProperty("flutterPluginVersion").get())
    plugin("Dart", providers.gradleProperty("dartPluginVersion").get())
    testFramework(TestFrameworkType.Platform)
  }
  testImplementation(kotlin("test"))
}

intellijPlatform {
  publishing {
    token = providers.environmentVariable("JETBRAINS_MARKETPLACE_UPLOAD_TOKEN")
  }
  signing {
    certificateChain = providers.environmentVariable("JETBRAINS_MARKETPLACE_CERTIFICATE_CHAIN")
    privateKey = providers.environmentVariable("JETBRAINS_MARKETPLACE_PRIVATE_KEY")
    password = providers.environmentVariable("JETBRAINS_MARKETPLACE_PRIVATE_KEY_PASSWORD")
  }
  pluginConfiguration {
    ideaVersion {
      sinceBuild = providers.gradleProperty("sinceBuild")
    }
  }
  buildSearchableOptions = false
}

kotlin {
  jvmToolchain(providers.gradleProperty("javaVersion").get().toInt())
}

// The default wiring reads signPlugin.didWork, which the configuration cache freezes to false.
tasks.named<PublishPluginTask>("publishPlugin") {
  archiveFile = tasks.named<SignPluginTask>("signPlugin").flatMap { it.signedArchiveFile }
}
