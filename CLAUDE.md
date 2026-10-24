# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Two native Kotlin Android apps (a pair) for monitoring and remote-controlling a child's tablet ("Leon"), mainly YouTube Kids screen time. `README.md` (Indonesian) has the feature list and the end-user setup walkthrough. Code comments, log messages and UI strings are also in Indonesian; match that when editing nearby code.

- `child-app` — runs on the tablet. Foreground service that reports usage stats, executes remote commands, and hosts a BLE GATT server.
- `parent-app` — runs on the parent's phone. Dashboard plus remote control, sends commands over Firebase or BLE.

## Commands

Gradle wrapper, two modules (`:child-app`, `:parent-app`), compileSdk/targetSdk 34, minSdk 26, JVM 17, Kotlin 2.0.20, AGP 8.4.1. Dependency versions live in `gradle/libs.versions.toml`.

```bash
./gradlew :child-app:assembleDebug        # build tablet app
./gradlew :parent-app:assembleDebug       # build phone app
./gradlew :child-app:installDebug         # install to a connected device/emulator
./gradlew :parent-app:installDebug
```

```bash
./gradlew :child-app:testDebugUnitTest    # JVM unit tests (LimitEnforcer, category classifier, PinHasher)
./gradlew :parent-app:testDebugUnitTest   # JVM unit tests (history analysis, schedule formatting, PIN/alert/app-lock logic)
./gradlew :child-app:testDebugUnitTest --tests "com.parentalcontrol.child.manager.LimitEnforcerTest"  # one class
```

Tests cover only pure-Kotlin logic (no Android classes); there are no instrumented tests and no lint config. If Gradle reports no Java runtime, point `JAVA_HOME` at Android Studio's bundled JBR (`/Applications/Android Studio.app/Contents/jbr/Contents/Home` on macOS).

**Build prerequisite:** both modules apply the `google-services` plugin, so each needs its own `google-services.json` (`child-app/` for package `com.parentalcontrol.child`, `parent-app/` for `com.parentalcontrol.parent`). The files are gitignored; the build fails without them. `local.properties` (SDK path) is also gitignored.

BLE can't be tested on an emulator; verifying the offline path needs two physical devices.

## Architecture

There is **no shared module**. The two apps communicate only through a Firebase Realtime Database node and a BLE characteristic, and the contract between them is duplicated in code on both sides. Changing one side without the other breaks things silently (no compile error).

### Duplicated contract (keep both sides in sync)

| Contract | Child side | Parent side |
|---|---|---|
| Device ID `"leon_tablet"` (hardcoded) | `ChildMonitoringService.onCreate`, `ChildFcmService.onNewToken` | `ParentApp.TARGET_DEVICE_ID` |
| BLE service / characteristic UUIDs (`0000fff0…` / `0000fff1…`) | `ChildApp` companion | `ParentApp` companion |
| BLE command codes 1–6 (`CMD_*`) | `BleServerManager` companion | `BleClientManager` companion |
| Firebase command action strings | `ChildMonitoringService.listenForCommands`, `ChildFcmService.onMessageReceived` | `MainActivity.setupControls` call sites |
| Usage summary schema | `DeviceUsageSummary` / `AppUsageInfo` (written with `setValue`) | `FirebaseSyncManager.parseSummary` (parsed **field by field** by name), `DeviceSummary` / `AppUsageItem` |
| History entry `history/{yyyy-MM-dd}` | `ChildMonitoringService.writeDailyHistory` | `FirebaseSyncManager.parseHistory`, `DayHistory` |
| Limits config (one Gson JSON string at `config`) | `model/LimitsConfig.kt` | `model/LimitsConfig.kt` — identical data classes except defaults (parent defaults to the three standard `appLimits` rules, all "no limit") |
| Category keys (`edukasi`/`hiburan`/`game`/`lainnya`) | `AppCategory` | `CategoryKeys` |
| Device settings (one Gson JSON string at `settings`) | `model/DeviceSettings.kt` | `model/DeviceSettings.kt` |
| PIN (`pin/{salt,hash}`, SHA-256 of `"salt:pin"`) | `manager/PinHasher.kt` | `analysis/SecurityLogic.kt` (`PinHasher`) — both tests assert the same known digest |
| Alert (`lastAlert/{id,type,message,timestamp}`) and `AlertTypes` | `AlertWriter`, `model/DeviceSettings.kt` | `FirebaseSyncManager`, `model/DeviceSettings.kt` |
| Location (`location/{lat,lng,accuracy,provider,timestamp}`) | `LocationReporter` | `FirebaseSyncManager` (`DeviceLocation`) |

### Two command channels

`MainActivity.dispatchCommand(action, param, bleCode)` in parent-app picks the transport: BLE if currently connected, otherwise Firebase.

- **Online (Firebase RTDB):** parent writes `devices/leon_tablet/pendingCommand = {action, value, timestamp}`. `ChildMonitoringService` has a `ValueEventListener` on it, runs the action, then `removeValue()`s the node so it isn't replayed. Actions: `LOCK_NOW`, `SHOW_OVERLAY`, `HIDE_OVERLAY`, `SET_VOLUME`, `SET_BRIGHTNESS`, `SET_WIFI`, `SYNC_STATS`.
- **Offline (BLE):** child advertises a GATT service (`BleServerManager`); parent scans, connects and writes a 2-byte payload `[cmdCode, param]` to the command characteristic (`BleClientManager`). `param` is one signed byte, so values are limited to 0–100 in practice. BLE has no `SYNC_STATS` or disable-Wi-Fi code; `CMD_ENABLE_WIFI` only enables.
- `ChildFcmService` is a third, partly redundant receiver of the same action strings (FCM data messages). The child stores its token at `devices/leon_tablet/fcmToken`, but parent-app has no FCM sender, so this path is currently unused.

Command handling is therefore implemented in three places in child-app (service Firebase listener, service BLE callback, FCM service), all delegating to `DeviceControlManager`. A new command must be added to each channel that should support it, plus the parent UI. `CommandType`/`RemoteCommand` in `child-app/.../model/RemoteCommand.kt` is unused; dispatch uses raw strings and ints.

### State flow (child → parent)

`ChildMonitoringService.syncUsageStatsToFirebase()` writes `devices/leon_tablet/{summary, history/<today>, lastHeartbeat, isOnline}` every 60 s, and again ~300 ms after every executed command (so the parent's volume/brightness sliders reflect the applied value). `isOnline` is set true with `onDisconnect().setValue(false)`. History is one entry per day (overwritten on each sync); the entry from 35 days ago is deleted on each sync. `summary.limitUsedMinutes` excludes the child app's own foreground time, matching what enforcement counts.

`FirebaseSyncManager` keeps **separate listeners** for `summary`, `isOnline`, `history` (last 30 keys) and `config` — don't go back to one listener on the whole device node, it would re-download the growing history on every summary change. While BLE is connected the parent shows "Bluetooth" status and ignores the Firebase online/offline text, but still renders summary data from Firebase.

### Limits (parent → child, enforced on the tablet)

Parent edits a `LimitsConfig` (daily limit + per-day bonus minutes, per-app/category rules, bedtime window, study window) and writes it whole to `devices/leon_tablet/config` as a JSON string. The child caches it in SharedPreferences (`LimitsStore`), so enforcement keeps working offline and after reboot. `ChildMonitoringService.enforceLimits()` runs every 15 s and on every config change: it builds a `UsageSnapshot` and asks the pure `LimitEnforcer.evaluate()` for a `Violation`; if there is one it shows `LockOverlayActivity` with the reason, and it auto-dismisses the overlay when the violation clears (only an overlay it showed itself — `enforcementLockActive`).

- Priority: bedtime → daily limit (+ today's bonus) → study window (blocks only game/hiburan in foreground) → per-rule limits (only while a matching app is in the foreground).
- A manual unlock (parent `HIDE_OVERLAY` via any channel, or the PIN dialog) snoozes enforcement for `LimitsStore.SNOOZE_MINUTES`; `hideLockOverlay(snooze = false)` is the internal path that must not snooze.
- Everything defaults to disabled, so nothing locks until the parent turns it on.
- Windows crossing midnight belong to the day they start on (`LimitEnforcer.isWindowActive`).

### Settings, PIN, alerts, location (parent → child, and alerts back)

Besides `config`, the parent writes two more nodes that the child caches in SharedPreferences (`SettingsStore`) so they apply offline: `settings` (`locationEnabled`, `uninstallProtection`) and `pin` (salt + hash). Removing `pin` returns the child to the default PIN. The parent verifies the *old* PIN locally against the same hash before writing a new one (`PinChange.validate`). A 4-digit PIN is brute-forceable by anyone who can read the database; hashing only keeps it from sitting in plaintext.

- **Alerts:** the child writes a single `lastAlert` node (overwritten, so it never grows) when enforcement starts a lock (`LIMIT_REACHED`) and when Device Admin is disabled while `uninstallProtection` is on (`ADMIN_DISABLED`); `AlertWriter` suppresses the same type for 5 min. The parent turns it into a local notification (`AlertNotifier`) only if notifications are enabled, and only for alerts newer than the last one seen (`AlertTracker`; the first alert ever observed is just a baseline). There is no push server, so notifications only appear while the parent process is alive.
- **"Proteksi Uninstall" does not block uninstalling.** Real blocking needs Device Owner (provisioned on a factory-reset device). Today it only warns the parent when the admin is disabled, and the child reports `deviceAdminActive` in the summary so the Settings tab can show it.
- **Location:** `LocationReporter` (plain `LocationManager`, no Play Services) writes `location` about every 10 min while `locationEnabled`; `ChildMonitoringService` re-checks it on every sync so it starts once permission/location services become available. It needs `ACCESS_FINE_LOCATION` plus `ACCESS_BACKGROUND_LOCATION` (optional row 6 in the child's permission checklist; it is *not* required to start protection). The service's foreground type stays `connectedDevice` — don't add `location` to the manifest type without choosing the type at runtime, because `startForeground` throws when a declared type's permission isn't granted (the service catches that and stops itself).
- The summary also carries `deviceModel`, `androidVersion`, `batteryPercent`, `deviceAdminActive` for the parent's profile card.

### Child-app specifics

- `ChildMonitoringService` (START_STICKY foreground service) owns the BLE server, Firebase listener, and the periodic sync loop. `BootReceiver` restarts it on boot only if Bluetooth permissions are granted. `child-app/.../ui/MainActivity` is a permission checklist that starts the service.
- Usage stats come from `UsageStatsManager.queryEvents` only (`AppUsageManager`), turned into per-app time by the pure `UsageSessionCalculator`: daily window from 00:00; only one app is foreground at a time (another app's `RESUMED` closes the previous session); screen-off / keyguard / shutdown events close sessions; a session still open at the end counts only if `PowerManager.isInteractive`. Do **not** reintroduce `queryAndAggregateUsageStats` or trust `RESUMED`/`PAUSED` pairing: a missing `PAUSED` made sessions "hang" and grow on every sync (a 1-hour app showed 8 h, a day showed 36 h). Minutes are `ceil` of the summed milliseconds (`minutesCeil`), never summed per app. Only launchable apps with ≥1 s usage are included, and the child app itself is excluded. The calculator also sets `foregroundPackage`, used by enforcement.
- The child manifest must keep its `<queries>` launcher-intent block: with Android 11+ package visibility, `getLaunchIntentForPackage`/`getApplicationInfo` fail for other apps without it, and `AppUsageManager` silently drops every app it can't see (YouTube Kids would vanish from `appList`, totals and enforcement).
- Firebase's Kotlin serializer drops the `is` prefix: `DeviceUsageSummary.isLocked` is stored as `locked` (and `AppUsageInfo.isYoutubeKids` as `youtubeKids`). Read the stored name when parsing on the parent.
- Each app gets a category from `AppCategoryClassifier` (package-name hints first, then `ApplicationInfo.category`); it is sent in `appList[].category` and aggregated into `summary.categories`.
- YouTube detection differs by side: child's `AppUsageInfo.isYoutubeKids` matches any package containing "youtube" (so regular YouTube counts toward `youtubeKidsMinutes`), while parent's `AppUsageItem.isYoutubeKids` matches only the Kids package.
- Lock behaviors: `LOCK_NOW` = `DevicePolicyManager.lockNow()` (needs Device Admin via `AdminReceiver`). The "rest" overlay is `LockOverlayActivity` (show-when-locked activity, back disabled, dismissed by an in-app broadcast `ACTION_DISMISS_OVERLAY` or the PIN dialog). The PIN is verified by `SettingsStore.verifyPin`: against the salt+hash the parent wrote to `pin` (cached locally), falling back to the default `1234` when the parent never set one.
- Brightness needs `WRITE_SETTINGS`; it clamps to ≥5% and forces manual mode. Wi-Fi toggling only works below Android 10 (`setWifiEnabled` returns false on API 29+).

### Parent-app specifics

`MainActivity` is only a shell: bottom navigation over four fragments (Dashboard, Aktivitas, Batas Waktu, Pengaturan), all using ViewBinding. Firebase and BLE are owned by one activity-scoped `ParentViewModel` (so tab switches/rotation don't drop the BLE connection); fragments observe its LiveData (`device`, `history`, `config`, `bleConnected`) and call `dispatchCommand` / `saveConfig` / `grantBonus`. `BleClientManager` has a 12 s scan timeout and connects to the first device advertising the service UUID. Pure logic for the history/insights screens lives in `analysis/` (unit-tested); `BarChartView` is a small custom Canvas view, no chart library. Local-only preferences (dark mode, notification opt-in, biometric lock, last seen alert) live in the `parent_prefs` SharedPreferences (`MainActivity.KEY_*`). The optional biometric app lock is a `lockCover` view in `activity_main.xml` driven by `MainActivity.checkAppLock` and `AppLockPolicy`; its locked/unlocked state lives in `ParentViewModel` so rotation can't bypass it.

The UI follows a Google Stitch redesign ("Parental Control App Redesign"); colors were estimated from screenshots, not exported design tokens.

## Firebase security

Access is meant to be limited to one shared **Firebase Auth email/password "family account"** that both apps sign in with; `database.rules.json` (placeholder `GANTI_DENGAN_UID_AKUN_KELUARGA`, replaced with the account's UID and published manually in the console — nothing deploys it) only allows `devices/leon_tablet` for that UID. The repo can't enforce this: until the owner publishes the rules, the database keeps whatever rules it has (historically open read/write).

- Parent: `MainActivity` redirects to `LoginActivity` when `FirebaseAuth.currentUser == null` *before touching `ParentViewModel`* (that is what starts the Firebase listeners), and an `AuthStateListener` sends it back to login if the session ends. Sign-out lives in the Settings tab.
- Child: the settings checklist has a required "Akun Keluarga" sign-in card (deliberately no sign-out button, the child can open this app). `ChildMonitoringService` registers an `AuthStateListener` and only attaches its Firebase listeners in `startFirebaseSession()` once signed in; a listener cancelled with `PERMISSION_DENIED` (e.g. rules just changed) restarts the session after 30 s, because the SDK does not re-attach cancelled listeners. All child writes (`syncUsageStatsToFirebase`, `AlertWriter`, FCM token) are skipped when signed out. Enforcement (`LimitEnforcer`) deliberately does not depend on auth: it runs from the locally cached config.
- Both modules depend on `firebase-auth-ktx`; the Email/Password provider must be enabled in the Firebase console.
- Residual risk: both apps share one account, so anyone who can read the child's app data (root/ADB) holds a valid session, and a 4-digit PIN hash is brute-forceable by any account holder.
