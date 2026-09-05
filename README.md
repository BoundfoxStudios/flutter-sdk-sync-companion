# Flutter SDK Sync Companion

[![Build](https://img.shields.io/github/actions/workflow/status/BoundfoxStudios/flutter-sdk-sync-companion/build.yml?branch=main&label=build)](https://github.com/BoundfoxStudios/flutter-sdk-sync-companion/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/BoundfoxStudios/flutter-sdk-sync-companion?label=release)](https://github.com/BoundfoxStudios/flutter-sdk-sync-companion/releases/latest)
[![License](https://img.shields.io/github/license/BoundfoxStudios/flutter-sdk-sync-companion)](LICENSE)

An IntelliJ Platform plugin that keeps a project's Flutter SDK selection in sync with the SDK that
[FVM](https://fvm.app) provides.

Run `fvm use 3.35.0` while the IDE is open and the switch is picked up on its own: the SDK is
refreshed, `pub get` runs for every pub root, and the device daemon is restarted. No IDE restart,
no reopening the project, no pointing the settings at a new path by hand.

## Why

The official Flutter plugin does not reliably find the SDK of an FVM project, and it cannot notice a
version switch even when the path is set correctly. The reason is the path itself: FVM points the
project at the symlink `.fvm/flutter_sdk` and re-points that symlink on every `fvm use`, so the
configured path string never changes and nothing downstream is invalidated.

This plugin watches the resolved target of that symlink instead and propagates the change explicitly.
It is a *companion*: it runs alongside the official plugin and only sets and propagates the SDK path.

## Requirements

- IntelliJ IDEA 2025.3 or newer, or a matching Android Studio
- The official Flutter and Dart plugins
- A project set up with FVM, meaning a `.fvm/flutter_sdk` symlink

FVM does not have to be on the IDE's PATH. The SDK is discovered from the project's `.fvm` directory,
so no `fvm` binary is invoked. Other version managers are not supported.

## Installation

Download the zip from the [latest release](https://github.com/BoundfoxStudios/flutter-sdk-sync-companion/releases/latest)
and install it in the IDE with **Settings → Plugins → ⚙ → Install Plugin from Disk**.

## Settings

**Settings → Languages & Frameworks → Flutter SDK Sync**, per project:

- **Keep the Flutter SDK in sync with FVM.** The master switch. While it is off, nothing is
  propagated, and a switch made in the meantime is picked up when you turn it back on.
- **Run `pub get` after a version switch.** On by default. Turning it off keeps
  `.dart_tool/package_config.json` pointing at the previous SDK until you run `pub get` yourself.

## Contributing

Build and verify with:

```
./gradlew build buildPlugin verifyPluginProjectConfiguration
```

`JAVA_HOME` has to point at a JDK 21 when your default JDK is newer, otherwise the toolchain will
not resolve.

## Licence

[MIT](LICENSE). This plugin is not affiliated with or endorsed by Google LLC.
