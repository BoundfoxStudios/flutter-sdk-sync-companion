import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
  id("org.jetbrains.kotlin.jvm") version "2.4.10"
  id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "com.boundfoxstudios"
version = "0.1.0" // x-release-please-version

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
