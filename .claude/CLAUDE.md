# Flutter SDK Sync Companion

An IntelliJ Platform plugin in Kotlin that keeps a project's Flutter SDK selection in sync with the
SDK a version manager (currently FVM) provides. It is a *companion*: it runs alongside the official
Flutter plugin (`io.flutter`) and only sets and propagates the SDK path.

It exists because `io.flutter` does not reliably find the SDK of an FVM project. Publishing a fork
instead was rejected: the fork bundles JxBrowser jars whose TeamDev licence is not transferable, the
plugin id `io.flutter` is taken, and the vendor is Google.

The general conventions that apply to every change in this repository:

@general-conventions.md

## Build

```
JAVA_HOME=<jdk21> ./gradlew build buildPlugin verifyPluginProjectConfiguration
```

`JAVA_HOME` is not optional when the default JDK is newer than 21: the toolchain will not resolve.

Target platform is IntelliJ 2025.3 (build 253). `sinceBuild = 253` is forced by the Dart plugin, not
by Flutter. `untilBuild` is deliberately unset: the IntelliJ Platform Gradle Plugin has had no
default since 2.6.0 and warns when one is set alongside `sinceBuild >= 243`.

## Release

Releases are cut by release-please from the Conventional Commit history. A push to `main` opens or
updates a release pull request; merging it writes `CHANGELOG.md`, tags the release, signs the built
plugin zip, attaches it to the GitHub release and uploads the same signed zip to the JetBrains
Marketplace via `publishPlugin`, which reads the repository secret
`JETBRAINS_MARKETPLACE_UPLOAD_TOKEN`. `publishPlugin` fails when the version was already published,
so a re-run of a release never silently overwrites anything.

Signing reads the repository secrets `JETBRAINS_MARKETPLACE_CERTIFICATE_CHAIN` and
`JETBRAINS_MARKETPLACE_PRIVATE_KEY` (each the PEM file as single-line Base64; a value with line
breaks fails to decode and is used verbatim) plus `JETBRAINS_MARKETPLACE_PRIVATE_KEY_PASSWORD`.
`publishPlugin.archiveFile` is wired to `signPlugin.signedArchiveFile` on purpose: the default picks
the signed zip via `signPlugin.didWork`, which the configuration cache freezes to false, so the
default published the unsigned zip (verified 2026-09-09 with IPGP 2.18.1). Open risk until the first
signed release: in the sandbox `publishPlugin` could not parse any signed zip (`The plugin archive
file cannot be extracted`), not even the JetBrains-signed Marketplace download of this plugin, so the
cause is the bundled `intellij-plugin-structure`, not the key. Remove this note once a release has
been published signed.

One self-signed certificate serves every Boundfox plugin: nothing in the signature names a plugin,
and verification matches the last certificate of the chain against a trust store. Expiry is checked
nowhere in the chain (signer, verifier, IDE), so an expired certificate keeps working and rotation
means reissuing the certificate for the same key and replacing the two secrets.

The version lives in exactly one place, `version = ...` in `build.gradle.kts`, annotated with
`// x-release-please-version`. Never bump it by hand: `patchPluginXml` derives the plugin version and
the zip name from it, and a manual bump would collide with the one release-please writes.

Marketplace change notes are the release-please changelog: `pluginConfiguration.changeNotes` renders
the `CHANGELOG.md` section of the current version to HTML through the Gradle Changelog Plugin
(`org.jetbrains.changelog`) and `patchPluginXml` writes it as `<change-notes>`. The Marketplace
shows nothing else (`publishPlugin` uploads no notes, and notes cannot be edited after upload), so a
version without a changelog section ships without notes. The wiring is explicit on purpose: the
convention IPGP applies as soon as the changelog plugin is present falls back to `getUnreleased()`,
release-please never writes an Unreleased section, and the build then fails while storing the
configuration cache whenever the version has no section, as it was before the first release and is
again after any manual version bump. Branches off main carry the notes of the last release because
release-please bumps the version and writes the section in the same pull request.
`getOrNull(version)?.let { }` leaves the provider empty instead, which yields no element rather than
an empty one. `patchChangelog` is disabled because IPGP makes `publishPlugin` depend on it and it
rewrites `CHANGELOG.md` into a shape release-please does not produce (an `## Unreleased` header, the
compare link dropped, `*` bullets turned into `-`). The commit and issue links release-please
appends to every entry stay in the notes: `withLinks(false)` only affects reference-style links, and
dropping the inline ones would mean rebuilding the `Changelog.Item` with a regex.

`actionlint` reports `client-id` on `actions/create-github-app-token` as an unknown input and
demands the deprecated `app-id`. Its bundled action database is stale: verified against the
`action.yml` at tag v3.2.0, `client-id` is the current input and `app-id` carries a deprecation
message. Do not "fix" that warning.

## Architecture

`FlutterSdkSyncActivity` (a `postStartupActivity`) does nothing but obtain `FlutterSdkSyncService`
and await one synchronization. Creating the service is what registers the file listener, so a
project without a `.fvm` directory still notices a later `fvm use`.

`FlutterSdkSyncService` (project-level service, injected `CoroutineScope`) owns everything: the
`AsyncFileListener`, the debounced trigger, and the decision of what to do. Two of its three pieces
of state live under a `Mutex`: the last *observed* resolved target, and whether a propagation is
still owed. They answer two different questions: the first detects a switch, the second survives a
failed propagation so it is retried. Collapsing them into one nullable field makes a failed first
propagation leave the watcher permanently deaf. The third, the forced-propagation request, is an
`AtomicBoolean` outside the `Mutex` on purpose: the settings page sets it from the EDT, where a
suspending lock cannot be taken, and the service scope consumes it.

`FlutterSdkSyncSettings` keeps the two working-mode switches (keep in sync, run pub get) in the
workspace file rather than in a file of its own: they are personal switches, not a team contract, and
`$WORKSPACE_FILE$` is the only project storage the platform itself marks VCS-ignored. The master
switch is an early return at the top of `synchronize()`, never a condition on the listener
registration: the startup activity is a second entry point, and the scope-bound
`addAsyncFileListener` has no unregister handle. While the switch is off, `synchronize()` must not
touch `lastObservedTarget` or `propagationOwed`: the first is what still recognizes a switch made
while sync was off, the second is an obligation that turning sync off does not discharge. A service
created while the switch was off has observed no target at all, and the configured path string never
changes, so the settings page forces one propagation on the off to on transition, at the price of a
redundant pub get when nothing actually changed. That propagation is dispatched while the settings
dialog is still modal, and `Dispatchers.EDT` carries no modality state, so it only reaches the EDT
once the dialog is closed: applying and leaving the dialog open defers the work, it does not lose it.

`VersionManagerSdkLocator` stays free of IntelliJ imports so it is unit-testable without an IDE
fixture. `FvmWatchScope` and `DebouncedTrigger` are pure for the same reason. Everything else is
platform surface and is verified by running the IDE, not by tests.

## The central design decision

**The SDK path is stored as the unresolved symlink `<project>/.fvm/flutter_sdk`, never as the
resolved cache path.** FVM re-points that symlink on every `fvm use`. Writing the resolved path
instead pins the project to one version and makes `fvm doctor` report a broken setup.

The consequence is not obvious and drives the whole propagation design: because the stored path
string never changes across an `fvm use`, **`FlutterSdkUtil.setFlutterSdkPath` is a complete no-op on
a version switch**. `DartSdkLibUtil.configureDartSdkAndReturnUndoingDisposable` early-returns past
`setupDartSdkRoots` when the passed home path equals the configured one, and
`FlutterSdkManager.checkForFlutterSdkChange()` only compares the boolean `isFlutterSdkSetAndNeeded()`,
which does not flip. A switch must therefore be detected on the **resolved** target
(`Path.toRealPath()`) and propagated explicitly.

Known limitation a companion cannot fix: two projects whose `.fvm/flutter_sdk` resolve to the same
version share one `FlutterSdk.projectSdkCache` entry (keyed on `getCanonicalPath()`), and the cached
instance keeps the `home` of whichever project was opened first. Harmless while both are open.

## Platform pitfalls (verified against the shipped 2025.3 / io.flutter 96.0.0 / Dart 509.0.0 jars)

- `FlutterSdkUtil.setFlutterSdkPath` takes a write action internally. In 253 a write action off the
  EDT throws `IllegalStateException`. Call it inside `withContext(Dispatchers.EDT)`, and do *not*
  wrap it in `writeAction {}` / `edtWriteAction {}`.
- `LocalFileSystem.refreshAndFindFileByNioFile` does **not** refresh an entry already in the VFS
  snapshot, so a re-pointed symlink keeps its old `getCanonicalPath()` and the switch goes unnoticed.
  Force it with `VfsUtil.markDirtyAndRefresh(async = false, recursive = false, reloadChildren = false, …)`.
  A synchronous refresh must not run on the EDT and must not hold a read lock.
- Refreshing the symlink alone is not enough. `FlutterSdkVersion` reads
  `bin/cache/flutter.version.json` (falling back to `version`) through the VFS, and the Dart plugin's
  `DartSdk.getDartSdk(project)` is a `CachedValue` whose dependencies are `ProjectRootManager` plus
  the VirtualFiles `<dartSdkHome>/version` and `<dartSdkHome>/lib/core/core.dart`. Refresh those four
  files and both plugins see the new version.
- Do **not** restart the Dart analysis server. Once those two dart-sdk files are refreshed,
  `DartAnalysisServerService.serverReadyForRequest()` restarts it by itself on the next request.
  (`restartServer()` also asserts read access, so it would need a `readAction {}`.)
- `FlutterSdk.startPubGet` spawns the subprocess on the calling thread, and `io.flutter` calls it
  from the EDT. Use `flutterPackagesGet(root).startInModuleConsole(module, root::refresh, null)`
  instead, off the EDT, with a completion hook.
- A forced `pub get` is required after a switch: `.dart_tool/package_config.json` holds absolute SDK
  paths while `PubRoot.hasUpToDatePackages` only compares pubspec timestamps.
- `DeviceService.restart()`, not a refresh: `DeviceDaemon.Command` compares equal across the switch
  because the configured path string is unchanged, so a refresh is a no-op. Note that
  `DeviceService.getInstance(project)` starts the daemon if the service does not exist yet.
- `FlutterSdk.getFlutterSdk` stats three files and reads a version file, never on the EDT.
- `PubRoot.getModule(project)` needs a read action.
- VFS events are application-wide even when subscribed on a project message bus. Any path filter must
  be anchored on the project base path.
- `AsyncFileListener` over `BulkFileListener` (the latter's own javadoc says so). Register with
  `VirtualFileManager.getInstance().addAsyncFileListener(coroutineScope, listener)`: that overload
  carries no deprecation and no `@ApiStatus.Experimental`, and the scope's completion unregisters it.
  `prepareChange` runs for every VFS batch in the whole IDE, so it must stay pure and cheap; side
  effects belong in `ChangeApplier.afterVfsChange()`, which runs on the EDT under the write lock and
  therefore only tolerates a non-blocking signal.
- A symlink re-point surfaces as one `VFilePropertyChangeEvent(PROP_SYMLINK_TARGET)`, but a type flip
  (broken target, or a file instead of a directory) surfaces as delete + create. Match on paths, not
  on event types, to cover both.
- `Flow.debounce` is `@FlowPreview` in the coroutines fork bundled with the platform, hence the
  explicit `@OptIn`. The `Alarm` classes it replaces are `@Obsolete` in 253 and their own KDoc points
  at a debounced flow. `kotlinx-coroutines-test` is not in the platform distribution, do not add it.
- `io.flutter` declares no SDK-related extension points, carries no `@ApiStatus` annotations, and a
  minimum version for a plugin dependency cannot be declared (IDEABKL-7906). If a version gate ever
  becomes necessary, do it at runtime via `PluginManagerCore.getPlugin(...).getVersion()`, the way
  `io.flutter` itself gates against the Dart plugin.

## FVM specifics

- Discovery is deliberately file-based, not `fvm`-binary-based: it works without fvm installed and
  assumes no CLI output format. Revisit only if Windows without Developer Mode (no symlinks) becomes
  a requirement: it is explicitly out of scope.
- `.fvmrc` is flat JSON, all fields optional, no published schema: tolerate unknown keys. The read
  key `flutterSdkVersion` takes precedence over `flutter`.
- The legacy `.fvm/fvm_config.json` is rewritten on every `fvm use`, so its presence proves nothing.
- `fvm api context` prints the entire process environment in clear text, tokens included. Never log
  its output unfiltered.

## State of verification

Confirmed in Android Studio on macOS: an `fvm use` on an open project is picked up, the SDK is
switched, the pub get console opens, and the Dart SDK version shown in the settings follows. So
`.fvm` does sit inside a recursive watch root (no `addRootToWatch` is needed), the event shape the
platform produces there is covered by the path-based matcher, and refreshing the four version files
is enough to make the analysis server pick up the new version on its own.

Everything else (the threading rules and the rest of the propagation chain) comes from the shipped
bytecode and sources rather than from an observed run. Unverified on Windows and Linux.

The settings page itself is unverified. Nobody has opened it in a running IDE yet: neither the
placement under Languages & Frameworks, nor that the workspace file really gains a
`FlutterSdkSyncSettings` component in Android Studio, nor the forced propagation on the
off to on transition.

Accepted breakage on a switch, until the project is reopened: the DevTools server process of the old
SDK and any already-open DevTools tool windows.

With the pub get switched off, a version switch still refreshes the four version files and restarts
the device service, so the displayed Dart SDK version and the analysis server follow it, but
`.dart_tool/package_config.json` keeps the absolute paths of the previous SDK until a pub get is run
by hand. Saving all documents is part of that skip: nothing but the pub get reads them.
