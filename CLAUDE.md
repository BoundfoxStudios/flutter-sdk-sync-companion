# Flutter SDK Sync Companion

An IntelliJ Platform plugin in Kotlin that keeps a project's Flutter SDK selection in sync with the
SDK a version manager (currently FVM) provides. It is a *companion*: it runs alongside the official
Flutter plugin (`io.flutter`) and only sets and propagates the SDK path.

It exists because `io.flutter` does not reliably find the SDK of an FVM project. Publishing a fork
instead was rejected: the fork bundles JxBrowser jars whose TeamDev licence is not transferable, the
plugin id `io.flutter` is taken, and the vendor is Google.

## Build

```
JAVA_HOME=<jdk21> ./gradlew build buildPlugin verifyPluginProjectConfiguration
```

`JAVA_HOME` is not optional when the default JDK is newer than 21 — the toolchain will not resolve.

Target platform is IntelliJ 2025.3 (build 253). `sinceBuild = 253` is forced by the Dart plugin, not
by Flutter. `untilBuild` is deliberately unset: the IntelliJ Platform Gradle Plugin has had no
default since 2.6.0 and warns when one is set alongside `sinceBuild >= 243`.

## Architecture

`FlutterSdkSyncActivity` (a `postStartupActivity`) does nothing but obtain `FlutterSdkSyncService`
and await one synchronization. Creating the service is what registers the file listener, so a
project without a `.fvm` directory still notices a later `fvm use`.

`FlutterSdkSyncService` (project-level service, injected `CoroutineScope`) owns everything: the
`AsyncFileListener`, the debounced trigger, and the decision of what to do. It holds two pieces of
state under a `Mutex` — the last *observed* resolved target, and whether a propagation is still owed.
They answer two different questions: the first detects a switch, the second survives a failed
propagation so it is retried. Collapsing them into one nullable field makes a failed first
propagation leave the watcher permanently deaf.

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
  instead — off the EDT, with a completion hook.
- A forced `pub get` is required after a switch: `.dart_tool/package_config.json` holds absolute SDK
  paths while `PubRoot.hasUpToDatePackages` only compares pubspec timestamps.
- `DeviceService.restart()`, not a refresh: `DeviceDaemon.Command` compares equal across the switch
  because the configured path string is unchanged, so a refresh is a no-op. Note that
  `DeviceService.getInstance(project)` starts the daemon if the service does not exist yet.
- `FlutterSdk.getFlutterSdk` stats three files and reads a version file — never on the EDT.
- `PubRoot.getModule(project)` needs a read action.
- VFS events are application-wide even when subscribed on a project message bus. Any path filter must
  be anchored on the project base path.
- `AsyncFileListener` over `BulkFileListener` (the latter's own javadoc says so). Register with
  `VirtualFileManager.getInstance().addAsyncFileListener(coroutineScope, listener)` — that overload
  carries no deprecation and no `@ApiStatus.Experimental`, and the scope's completion unregisters it.
  `prepareChange` runs for every VFS batch in the whole IDE, so it must stay pure and cheap; side
  effects belong in `ChangeApplier.afterVfsChange()`, which runs on the EDT under the write lock and
  therefore only tolerates a non-blocking signal.
- A symlink re-point surfaces as one `VFilePropertyChangeEvent(PROP_SYMLINK_TARGET)`, but a type flip
  (broken target, or a file instead of a directory) surfaces as delete + create. Match on paths, not
  on event types, to cover both.
- `Flow.debounce` is `@FlowPreview` in the coroutines fork bundled with the platform, hence the
  explicit `@OptIn`. The `Alarm` classes it replaces are `@Obsolete` in 253 and their own KDoc points
  at a debounced flow. `kotlinx-coroutines-test` is not in the platform distribution — do not add it.
- `io.flutter` declares no SDK-related extension points, carries no `@ApiStatus` annotations, and a
  minimum version for a plugin dependency cannot be declared (IDEABKL-7906). If a version gate ever
  becomes necessary, do it at runtime via `PluginManagerCore.getPlugin(...).getVersion()`, the way
  `io.flutter` itself gates against the Dart plugin.

## FVM specifics

- Discovery is deliberately file-based, not `fvm`-binary-based: it works without fvm installed and
  assumes no CLI output format. Revisit only if Windows without Developer Mode (no symlinks) becomes
  a requirement — it is explicitly out of scope.
- `.fvmrc` is flat JSON, all fields optional, no published schema — tolerate unknown keys. The read
  key `flutterSdkVersion` takes precedence over `flutter`.
- The legacy `.fvm/fvm_config.json` is rewritten on every `fvm use`, so its presence proves nothing.
- `fvm api context` prints the entire process environment in clear text, tokens included. Never log
  its output unfiltered.

## State of verification

Confirmed in Android Studio on macOS: an `fvm use` on an open project is picked up, the SDK is
switched, the pub get console opens, and the Dart SDK version shown in the settings follows. So
`.fvm` does sit inside a recursive watch root — no `addRootToWatch` is needed — the event shape the
platform produces there is covered by the path-based matcher, and refreshing the four version files
is enough to make the analysis server pick up the new version on its own.

Everything else — the threading rules and the rest of the propagation chain — comes from the shipped
bytecode and sources rather than from an observed run. Unverified on Windows and Linux.

Accepted breakage on a switch, until the project is reopened: the DevTools server process of the old
SDK and any already-open DevTools tool windows.
