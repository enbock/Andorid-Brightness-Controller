# F5 — Background Service

| Field | Value |
|---|---|
| **Epic** | [EPIC: Ambient Brightness Controller](./EPIC-ambient-brightness-controller.md) |
| **Status** | Draft — awaiting review |
| **Depends on** | F1 (Ambient Light Sensing), F2 (Automatic Brightness Control) |
| **Depended on by** | F6 (Permissions & Setup Flow), F7 (Status Dashboard) |

## Overview

This feature is what makes the app actually useful: it keeps the sensing and brightness-control loop
alive while the user is doing something else, and defends it against Android's and OEMs' process
reclamation. It owns the foreground service, the persistent notification, the battery-optimization
exemption, and the start/stop/restart lifecycle of the controller.

## User Story 1 — Keep running while I use my phone normally

As a user, I want the controller to keep running while I use other apps or the screen is on, so that
brightness keeps adapting without me having to open the app.

#### Acceptance Criteria

- Given I have started the controller, when it is running, then an Android foreground service is
  active and shows a persistent notification for the whole time.
- Given the controller is running, when I swipe the app away from the recents list, then the service
  keeps running and brightness control continues uninterrupted.
- Given the controller is running, when the system is under memory pressure, then the foreground
  service is not selected for termination ahead of ordinary cached processes.
- Given the persistent notification is tapped, when it is tapped, then the app opens on the status
  dashboard.

## User Story 2 — Survive system and manufacturer termination

As a user, I want the controller to come back if the system kills it, so that brightness control does
not silently stop working without me noticing.

#### Acceptance Criteria

- Given the service has been terminated by the system, when the service is restarted, then the
  controller resumes with the settings (offset, response speed) that were in effect before.
- Given the device's battery optimization is active for this app, when I open the app, then it detects
  this and guides me to grant the battery-optimization exemption.
- Given the controller was running when the device was shut down or restarted, when the device has
  finished booting, then the controller starts again automatically and resumes with the stored
  settings.
- Given the controller was stopped when the device was shut down or restarted, when the device has
  finished booting, then the controller stays stopped and does not change the screen brightness.

## User Story 3 — A clean, reversible lifecycle

As a user, I want stopping the app to really stop it, so that I stay in control of my screen.

#### Acceptance Criteria

- Given the controller is running, when I stop it, then the foreground service stops, the notification
  is removed, and the ambient light sensor listener is unregistered.
- Given the controller is stopped, when the stop completes, then the screen brightness is restored to
  the value that was in effect before the controller was started.
- Given the controller is stopped, when I use my phone afterwards, then the app does not change
  brightness in any way.
- Given the `WRITE_SETTINGS` permission is revoked at runtime, when the next write fails, then the
  service stops attempting writes, reports the failure visibly, and does not crash.

## User Story 4 — A notification that tells me what is going on

As a user, I want the ongoing notification to show the current state and let me stop the controller,
so that I do not have to open the app just to check or turn it off.

#### Acceptance Criteria

- Given the controller is running, when the notification is shown, then it displays the current
  controller state and at least the currently applied brightness.
- Given the notification is shown, when I use its action, then I can stop the controller directly from
  the notification.
- Given the foreground service is active, when the user tries to dismiss the notification, then it
  cannot be swiped away while the service runs.

## Out of Scope

- Requesting or explaining the permissions in the UI (that is F6).
- The dashboard UI itself (that is F7).
- Running without any notification (not possible for a foreground service on the target Android
  version).
- Being started by a system broadcast other than boot, e.g. by a third-party automation app.
- Guaranteed survival: no app can be fully immune to a user-initiated force-stop.

## Resolved Decisions

- The controller restarts automatically after a device reboot **if it was running before the reboot**;
  if it was stopped, it stays stopped. There is no separate opt-in switch.
- Stopping the controller restores the brightness value that was in effect before it was started; there
  is no "keep the last value" option.
- The ongoing notification shows the state and offers a stop action only; the offset control lives on
  the app's settings screen.

## Technical Design

### Architecture & Components

Same three modules as F1/F2 (`:core` pure Kotlin, `:infrastructure` Android adapters, `:app` composition root; package root `com.ambientbrightness`). F5 owns process lifetime and consumes F1/F2 **only** through `AmbientLightService` and `AutomaticBrightnessController`.

Components to add (all `:core` unless noted):

- `BrightnessBackgroundService` (boundary) — single public entry point for F5: `start()`, `stop()`, `state: StateFlow<BackgroundServiceState>`. F6/F7 call only this; no UI here.
- `BackgroundServiceUseCase` — orchestration: foreground start → F2 `start()`; on stop: F2 `stop()` → restore snapshot → foreground stop → clear the persisted flag.
- `BackgroundServiceState` (sealed: `Stopped`, `Starting`, `Running(appliedBrightness)`, `StoppedBySystem`, `Failed(reason)`) — the surface F6/F7 read and the notification renders.
- `StartReason` (`UserRequest`, `SystemRestart`, `Boot`) and `StartOutcome` (`Started`/`Blocked(reason)`) — explicit reasons instead of a bare boolean.
- `ForegroundServiceHost` (interface) — `startForeground(content)`, `updateNotification(content)`,
  `stopForeground()`; keeps `:core` Android-free and lets tests assert lifecycle calls.
- `BackgroundNotificationContent` (VO) + `BackgroundNotificationBuilder` — pure, unit-testable rendering of `BackgroundServiceState`.
- `ControllerStateStore` (interface) — persists `desiredRunning`/start reason across process death and reboot; read by the boot receiver.
- `BatteryOptimizationClient` (interface) — `isExempt: Flow<Boolean>`, `requestExemption()`, `openSettings()`; exposed for F6/F7, no UI here.
- `StopBrightnessRestorer` — Task class: writes F2's `BrightnessSnapshot` back via F2's `SystemBrightnessClient` (no parallel mechanism).

`:infrastructure`: `ForegroundBrightnessService` (the only Android `Service`, implements
`ForegroundServiceHost`), `BootCompletedReceiver`, `AndroidControllerStateStore`,
`AndroidBatteryOptimizationClient`, `AndroidBrightnessNotificationFactory`, plus `Dummy*` variants.
`:app`: `BrightnessServiceCollector` (thin forwarder; no business logic) + manual DI wiring.

### Data Flow

Start (`UserRequest`): F7 → `BrightnessBackgroundService.start()` → `desiredRunning = true` → `ForegroundServiceHost.startForeground()` (inside the platform's foreground window) → `AutomaticBrightnessController.start()` (F2, driving F1) → `Starting → Running`; each F2 `BrightnessStatus` update refreshes the notification.

Stop: `stop()` → `AutomaticBrightnessController.stop()` (F2 stops writing; F1 unregisters the sensor listener) → `StopBrightnessRestorer` writes F2's `BrightnessSnapshot` via `SystemBrightnessClient` → `stopForeground()` → `desiredRunning = false` → `Stopped`.

Restart after system kill (`START_STICKY`) or boot: re-read `ControllerStateStore`, call `AutomaticBrightnessController.start()`, resume with persisted offset/response settings → `Running`; boot starts only when `desiredRunning` was `true`.

### Key Interfaces & Classes

```kotlin
interface BrightnessBackgroundService {
    fun start(reason: StartReason): StartOutcome
    fun stop()
    val state: StateFlow<BackgroundServiceState>
    val batteryOptimizationExempt: Flow<Boolean>   // surface for F6/F7
}
interface ForegroundServiceHost {
    fun startForeground(content: BackgroundNotificationContent)
    fun updateNotification(content: BackgroundNotificationContent)
    fun stopForeground()
}
interface ControllerStateStore { var desiredRunning: Boolean; var lastStartReason: StartReason? }
interface BatteryOptimizationClient { val isExempt: Flow<Boolean>; fun requestExemption(); fun openSettings() }
```

- **Notification content:** `BackgroundNotificationBuilder` maps `Running(brightness)` to "Running — brightness N" plus a single **Stop** action; `Stopped/Failed` get their own text. No offset control (Resolved Decisions); pure Kotlin, unit-testable.
- **State reuse:** `appliedBrightness` comes from F2's `BrightnessStatus`; the notification never re-reads the system setting, so it cannot disagree with the controller.
- **Restoration source:** F2's `BrightnessSnapshot` is the pre-controller value. If `AutomaticBrightnessController` lacks a read accessor, add one property there (small F2 follow-up) — do **not** build a second snapshot. *Verify accessor name with F2.*
- **Idempotency:** `start()` while `Running` is a no-op; `stop()` while `Stopped` is a no-op. All transitions run through `BackgroundServiceUseCase` on one serialising scope, so UI/notification/boot calls cannot interleave.

### State & Persistence

`ControllerStateStore` holds two facts: `desiredRunning` and `lastStartReason` (SharedPreferences/DataStore in `:infrastructure`). This makes "was running before the reboot" survivable without persisting controller state; offset/response settings stay owned by F3/F4. Transient state (`BackgroundServiceState`, applied brightness) is in-memory and rebuilt on restart. Force-stop clears the process and the platform blocks restarts until the app is opened again — `desiredRunning` stays `true` but nothing runs; F6/F7 may reconcile that on the next app open.

### Android Platform Specifics

- **Foreground service type (API 36):** declare
  `android:foregroundServiceType="specialUse"` on the service, plus the subtype property
  `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="ambient light based screen brightness control"/>`.
  `specialUse` is chosen because the loop is a continuous sensor-driven system-settings writer that
  matches no other type (`dataSync` is for transfers, `connectedDevice` needs an external device,
  `health`/`location` are unrelated and carry stricter runtime checks/preconditions). **Rejected:**
  `dataSync` (semantically wrong; on API 35+ it is capped at 6 hours per 24 hours and a boot receiver
  may not start it at all — see below), and a plain background `Service` (killed by the system).
  **Confirmed against the Android 16 docs:** the subtype property name
  `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` is correct and its value is free-form, the matching
  constant for `startForeground()` is `ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE`, and
  `specialUse` has **no runtime prerequisites**. Documented external step: the declared use cases are
  reviewed in the Google Play Console when the app is submitted, so the property value must be
  specific enough to show why no other type fits.
- **Manifest permissions:** `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` (confirmed: this is the documented type-specific permission for `specialUse`), `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`. Do **not** add `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` by default (see risks).
- **Service declaration:** `android:exported="false"`, started via `ContextCompat.startForegroundService(...)`, with `ServiceCompat.startForeground(..., type)` called promptly in `onStartCommand` (short platform window). `START_STICKY` restart contract; handle the `null` intent after a system restart by falling back to stored state.
- **`POST_NOTIFICATIONS` denied (confirmed for API 33+):** the app does **not** need `POST_NOTIFICATIONS`
  to launch the FGS, but it must still post a notification when it does. With the permission denied the
  FGS notice is **not** visible in the notification drawer — so the Stop action is unreachable there —
  while the system Task Manager still lists the running foreground service. F5 must not crash, must
  treat a Task Manager kill as a stop, and must expose state via `BackgroundServiceState` for F6/F7.
- **Non-dismissible notification:** `setOngoing(true)` + no `setAutoCancel`, which prevents
  swipe-away for an active foreground service. The user can still terminate the service from the
  system UI (foreground-service Task Manager) — that must be treated as a stop, not as a bug.
- **Boot restart (confirmed):** `BootCompletedReceiver` with an `ACTION_BOOT_COMPLETED` intent filter
  and `RECEIVE_BOOT_COMPLETED`; it must return quickly and call `startForegroundService(...)` directly.
  Boot is an allowed exemption from background-start restrictions, but for apps targeting API 35+ a
  boot receiver may **not** launch `dataSync`, `camera`, `mediaPlayback`, `phoneCall`,
  `mediaProjection` or `microphone` (they throw `ForegroundServiceStartNotAllowedException`);
  `specialUse` is not on that list, so this design is permitted. Use `ACTION_BOOT_COMPLETED` and **not**
  `ACTION_LOCKED_BOOT_COMPLETED`, because the receiver reads the DataStore-backed
  `ControllerStateStore`, which is unavailable before the first user unlock. Test with
  `adb shell am compat enable FGS_BOOT_COMPLETED_RESTRICTIONS <pkg>` and
  `adb shell am broadcast -a android.intent.action.BOOT_COMPLETED <pkg>`.
- **Battery optimization:** detect with `PowerManager.isIgnoringBatteryOptimizations(packageName)`.
  Prefer `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` (guides the user to the system screen); the
  direct `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` dialog requires the
  `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission and has Play Store policy implications (risk).
- **Notification channel:** one low-importance channel (no sound/vibration); updates reuse the same notification id. No wakelock is held (Epic constraint).

### Edge Cases & Failure Handling

- Stop while a write is in flight → serialise through the use-case scope; restore runs after F2 has
  fully stopped, so the snapshot is not overwritten.
- `WRITE_SETTINGS` revoked at runtime → F2 already returns `Failed`; F5 maps it to `Failed(reason)` and keeps the service alive so the user can stop it explicitly; no crash (AC3.4).
- Sensor unavailable at restart → the service still runs foreground and surfaces F2/F1 availability
  through state; it does not crash on restart.
- Double start/stop, stop-from-notification racing stop-from-F7 → idempotent transitions; a second `stop()` after `Stopped` performs no restore (guard flag), so a fresh user-set brightness is never overwritten.
- Boot with `desiredRunning = false` → receiver does nothing; no service, no brightness write (AC2.4).
- User force-stop → not survivable by design; documented, not worked around.

### Test Strategy

`:core` (JVM): `BackgroundServiceUseCase` is driven by fakes of `ForegroundServiceHost`, `ControllerStateStore`, `BatteryOptimizationClient` and F2's `AutomaticBrightnessController`, asserting start order (foreground → controller), stop order (controller → restore → foreground), idempotency, and that nothing happens on a redundant stop. `BackgroundNotificationBuilder` is unit-tested for every `BackgroundServiceState` value and the stop action. `:infrastructure`: instrumented test starting `ForegroundBrightnessService` (service is foreground, notification ongoing, stop action cancels it) and one `BootCompletedReceiver` test with a stubbed `ControllerStateStore`. `Dummy*` adapters keep `:app` runnable without a device. No Compose UI tests (F6/F7 own UI).

### Implementation Tasks

- [x] **T1 — Domain model, state and boundary interfaces**: add `BackgroundServiceState`, `StartReason`,
  `StartOutcome`, `BackgroundNotificationContent`, and the `BrightnessBackgroundService`,
  `ForegroundServiceHost`, `ControllerStateStore`, `BatteryOptimizationClient` interfaces in `:core`,
  Android-free. *Files: `:core/src/main/kotlin/com/ambientbrightness/core/**`.*
  *Verify: compiles without Android; `BackgroundServiceState` covers all four stories' states.*
  *Depends on: none.*
- [x] **T2 — Notification content and state mapping**: implement `BackgroundNotificationBuilder`
  (state → title/text, `appliedBrightness` from F2 `BrightnessStatus`, single Stop action, ongoing
  flag) as pure Kotlin. *Files: `:core/.../BackgroundNotificationBuilder/**`.*
  *Verify: unit tests per state; AC4.1 (state + applied brightness), AC4.2 (stop action).*
  *Depends on: T1.*
- [x] **T3 — Lifecycle orchestration and brightness restoration**: implement
  `BackgroundServiceUseCase` (start/stop ordering, idempotency, serialised transitions) and the
  `StopBrightnessRestorer` Task that writes F2's `BrightnessSnapshot` through `SystemBrightnessClient`.
  *Files: `:core/.../BackgroundServiceUseCase/**`, `:core/.../StopBrightnessRestorer/**`.*
  *Verify: fake-driven tests for AC3.1–3.3, double start/stop, restore ordering; AC3.4 maps F2
  `Failed` to `Failed(reason)` without throwing. Depends on F2 exposing the snapshot accessor.*
  *Depends on: T1.*
- [x] **T4 — Android foreground service, notification and manifest**: implement
  `ForegroundBrightnessService`, `AndroidBrightnessNotificationFactory` and the manifest entries
  (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`,
  `foregroundServiceType="specialUse"` + subtype `<property>`, notification channel). Handle a `null`
  restart intent and `POST_NOTIFICATIONS` denial without crashing.
  *Files: `:infrastructure/src/main/kotlin/com/ambientbrightness/infrastructure/**`, `:app/src/main/AndroidManifest.xml`.*
  *Verify: instrumented test — service is foreground, notification is ongoing, stop action works;
  AC1.1, AC4.3 (not swipe-dismissible).*
  *Depends on: T1, T2.*
- [x] **T5 — Persistence, battery-optimization client and boot restart**: implement
  `AndroidControllerStateStore`, `AndroidBatteryOptimizationClient` (+ `Dummy*`), `BootCompletedReceiver`,
  `RECEIVE_BOOT_COMPLETED` and the `ACTION_BOOT_COMPLETED` filter; receiver starts the service only
  when `desiredRunning` is set. *Files: `:infrastructure/.../ControllerStateStore/**`,
  `.../BatteryOptimizationClient/**`, `.../BootCompletedReceiver/**`, `:app/src/main/AndroidManifest.xml`.*
  *Verify: receiver test with `desiredRunning` true → start, false → no-op (AC2.3, AC2.4); exemption
  state reads correctly and is exposed (AC2.2).*
  *Depends on: T1, T4.*
- [x] **T6 — Composition root, state collector and restart-after-kill hardening**: wire everything in
  `:app` (manual DI, app-scoped singletons, `BrightnessServiceCollector` forwarding
  `BackgroundServiceState` and `batteryOptimizationExempt` to F6/F7), bind
  `AutomaticBrightnessController` start/stop, and verify `START_STICKY` restart resumes with stored
  settings. *Files: `:app/src/main/kotlin/com/ambientbrightness/app/**`.*
  *Verify: end-to-end fake/instrumented run — start, kill the process, restart resumes `Running` with
  persisted settings (AC2.1); swipe-from-recents keeps it running (AC1.2).*
  *Depends on: T3, T4, T5.*

## Suggested Implementation Order

`T1 → T2 → T3` are pure `:core` and can largely proceed in parallel after T1 fixes the contracts.
`T4` (Android service + manifest) and `T5` (persistence/boot) are `:infrastructure` and can run in
parallel with each other once T1/T2 exist. `T6` is last because it is the only place that connects
the F2 controller, the Android service, the receiver and the F6/F7 state surface. T3's snapshot
restore has one external dependency (F2's `BrightnessSnapshot` accessor) — unblock it first.

## Implementation Notes

### T1 — Domain model, state and boundary interfaces

*Done: implementation commit `43f53bcad972ae91ad206ad3fd06feaa3981cfcc`.*

- Built the frozen F5 contract in `:core`, split across two responsibility folders (folder ==
  package, like `AmbientLightSensing`):
  - `BackgroundService/` — `BackgroundServiceState` (sealed interface: `Stopped`, `Starting`,
    `Running(appliedBrightness: BrightnessValue? = null)`, `StoppedBySystem`, `Failed(reason)`; each
    variant carries the story it serves, US1–US4), `StartReason` (`UserRequest`, `SystemRestart`,
    `Boot`), `StartOutcome` (sealed: `Started`, `Blocked(reason)`), `BackgroundNotificationContent`
    (pure VO: `title`, `text`, `ongoing`, `showStopAction`), `ForegroundServiceHost`
    (`startForeground`/`updateNotification`/`stopForeground`), `ControllerStateStore`
    (`var desiredRunning: Boolean`, `var lastStartReason: StartReason?`) and
    `BatteryOptimizationClient` (`isExempt: Flow<Boolean>`, `requestExemption()`, `openSettings()`).
  - `BrightnessBackgroundService/` — `BrightnessBackgroundService` (the only boundary F6/F7 use:
    `start(reason): StartOutcome`, `stop()`, `state: StateFlow<BackgroundServiceState>`,
    `batteryOptimizationExempt: Flow<Boolean>`).
- `:core` gained **no** `android.`/`androidx.datastore` import (the `ArchitectureStructureTest`
  guard passes); the platform `Service`, the notification factory, the DataStore-backed store, the
  boot receiver and the battery-optimization adapter are T4/T5 `:infrastructure` work.
- `BackgroundServiceState.Running` reuses F2's `BrightnessValue` (`@JvmInline value class`,
  `MINIMUM = 1`); no F2 change was needed (`AutomaticBrightnessController.snapshot` already exists).
- Files added:
  `core/src/main/kotlin/com/ambientbrightness/core/BackgroundService/BackgroundServiceState.kt`,
  `.../core/BackgroundService/StartReason.kt`, `.../core/BackgroundService/StartOutcome.kt`,
  `.../core/BackgroundService/BackgroundNotificationContent.kt`,
  `.../core/BackgroundService/ForegroundServiceHost.kt`,
  `.../core/BackgroundService/ControllerStateStore.kt`,
  `.../core/BackgroundService/BatteryOptimizationClient.kt`,
  `core/src/main/kotlin/com/ambientbrightness/core/BrightnessBackgroundService/BrightnessBackgroundService.kt`,
  `core/src/test/kotlin/com/ambientbrightness/core/BackgroundService/BackgroundServiceStateTest.kt`.
- Verification (from the repo root):
  `$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'` then
  `.\gradlew.bat :core:test --no-daemon --console=plain`
  → **BUILD SUCCESSFUL**, 28 test classes / 181 tests, 0 failures, 0 errors, 0 skipped
  (`BackgroundServiceStateTest` adds 10 tests).

### T2 — Notification content and state mapping

*Done: implementation commit `c395b04`.*

- Added `BackgroundNotificationBuilder` — a stateless, Android-free, side-effect-free class with a
  no-arg constructor (so F7/T4 can construct, inject or substitute it) exposing
  `fun build(state: BackgroundServiceState): BackgroundNotificationContent`.
- Mapping is an exhaustive `when` over all five variants (US4, AC4.1–AC4.3):
  - `Starting` → title "Ambient brightness control is starting", text "Starting the brightness
    controller...", `ongoing = true`, `showStopAction = true`.
  - `Running(brightness)` → title "Ambient brightness control is running", text
    "Applied brightness: N" from the state's F2 `BrightnessValue` (or "Waiting for the first light
    reading" when null), `ongoing = true`, `showStopAction = true`.
  - `Stopped` → title "Ambient brightness control is stopped", text "The brightness controller is
    stopped.", `ongoing = false`, `showStopAction = false`.
  - `StoppedBySystem` → title "Ambient brightness control is stopped", text "Android stopped the
    brightness controller. Open the app to start it again." (so a system stop is not mistaken for a
    user stop), `ongoing = false`, `showStopAction = false`.
  - `Failed(reason)` → title "Ambient brightness control stopped", text
    "The brightness controller stopped: <reason>", `ongoing = false`, `showStopAction = false`.
- The applied-brightness text lives in one documented private helper (`brightnessText`) and comes
  only from `state` — the builder never re-reads the system brightness setting, so it cannot
  disagree with the controller. `ongoing` is documented as the flag that makes the notification
  non-dismissible (AC4.3); the platform `setOngoing(true)` call stays in T4.
- Files added:
  `core/src/main/kotlin/com/ambientbrightness/core/BackgroundNotificationBuilder/BackgroundNotificationBuilder.kt`,
  `core/src/test/kotlin/com/ambientbrightness/core/BackgroundNotificationBuilder/BackgroundNotificationBuilderTest.kt`.
- Verification (from the repo root):
  `$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'` then
  `.\gradlew.bat :core:test --no-daemon --console=plain`
  → **BUILD SUCCESSFUL**, 29 test classes / 190 tests, 0 failures, 0 errors, 0 skipped
  (`BackgroundNotificationBuilderTest` adds 9 tests covering every state, AC4.1, AC4.2 and the
  `ongoing` flag). `:core` gained **no** `android.`/`androidx.datastore` import.

### T3 — Lifecycle orchestration and brightness restoration

*Done: implementation commit `d8577879dfda48acfe0bd6952dd5fa5286374f72`.*

- Added `StopBrightnessRestorer` (pure `:core` Task taking F2's `SystemBrightnessClient`):
  `restore(snapshot: BrightnessSnapshot?)` returns `null` and writes nothing when the snapshot is
  `null`, otherwise writes `snapshot.brightness` and returns the `BrightnessWriteResult` without
  throwing on `Failed`. The `null` case is what makes a redundant stop safe (a second `stop()` must
  never clobber a brightness the user has set since), and the write goes through F2's own client, so
  there is no parallel brightness mechanism (AC3.2).
- Added `BackgroundServiceUseCase : BrightnessBackgroundService` with a `MutableStateFlow` state and
  `batteryOptimizationExempt` forwarded from `BatteryOptimizationClient.isExempt`. All transitions
  run under one `lifecycleLock` on the injected `CoroutineScope`, so calls from F7, the notification
  action and the boot receiver cannot interleave.
  - **Start (AC1.1):** `Starting` → persist `desiredRunning = true` and `lastStartReason` →
    `host.startForeground(Starting content)` → `controller.start()` → observe `controller.status` on
    the scope, mapping each update to `Running(status.appliedBrightness)` and calling
    `host.updateNotification(...)`. Start order is therefore foreground before controller, and the
    notification can never disagree with the controller because it never re-reads the system setting.
  - **Stop (AC3.1, AC3.2):** capture `controller.snapshot` → `controller.stop()` (F2 stops writing,
    F1 releases the sensor) → `restorer.restore(snapshot)` → `host.stopForeground()` →
    `desiredRunning = false` → `Stopped`; the status observation is cancelled, so nothing continues to
    write or update the notification afterwards (AC3.3).
  - **Idempotency:** a second `start()` while active and a second `stop()` while stopped are no-ops,
    and the redundant stop performs no restore (guard flag). A `stop()` that was never preceded by a
    `start()` also does nothing.
  - **AC3.4:** a failed F2 write (`BrightnessWriteResult.Failed`) maps to
    `BackgroundServiceState.Failed(reason)` on the status stream without throwing, leaving the service
    alive so the user can stop it explicitly.
- No F2 change was needed: `AutomaticBrightnessController.snapshot` already existed, and no T1/T2 or
  `BrightnessBackgroundService` signature was altered.
- Files added:
  `core/src/main/kotlin/com/ambientbrightness/core/StopBrightnessRestorer/StopBrightnessRestorer.kt`,
  `core/src/main/kotlin/com/ambientbrightness/core/BackgroundServiceUseCase/BackgroundServiceUseCase.kt`,
  `core/src/test/kotlin/com/ambientbrightness/core/StopBrightnessRestorer/StopBrightnessRestorerTest.kt`,
  `core/src/test/kotlin/com/ambientbrightness/core/BackgroundServiceUseCase/BackgroundServiceUseCaseTest.kt`,
  `core/src/test/kotlin/com/ambientbrightness/core/testdoubles/BackgroundServiceFakes.kt`.
- Tests (JUnit 5, `runTest`, `backgroundScope`): an ordered `CallLog` across the fakes proves
  `host.startForeground` precedes `controller.start`, and that on stop `controller.stop` precedes the
  snapshot restore which precedes `host.stopForeground`; AC3.2 asserts the restored value equals the
  snapshot; AC3.3 asserts no status is mirrored after stop; AC3.4 asserts the `Failed(reason)` mapping;
  double start/stop and stop-without-start idempotency; and the exemption flow forwarding.
- Verification (from the repo root):
  `$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'` then
  `.\gradlew.bat :core:test --no-daemon --console=plain`
  → **BUILD SUCCESSFUL**, 31 test classes / 201 tests, 0 failures, 0 errors, 0 skipped
  (`StopBrightnessRestorerTest` adds 3 tests and `BackgroundServiceUseCaseTest` adds 8). `:core` gained
  **no** `android.`/`androidx.datastore` import.

### T4 — Android foreground service, notification and manifest

*Done: implementation commit `704485d817d11890a5745be3f2622525b0350850`.*

- Added the only Android `Service` in the project,
  `ForegroundBrightnessService : Service(), ForegroundServiceHost`
  (`:infrastructure/.../BackgroundService/ForegroundServiceHost/`). It is the platform host for T3's
  `BackgroundServiceUseCase`, so `:core` keeps driving the notification through the
  `ForegroundServiceHost` interface and never touches Android:
  - `onCreate` creates one `NotificationChannel` (`AndroidBrightnessNotificationFactory.CHANNEL_ID`)
    at `IMPORTANCE_LOW` (no sound/vibration); updates reuse one stable id
    (`AndroidBrightnessNotificationFactory.NOTIFICATION_ID`).
  - `onStartCommand` promotes the service with
    `ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)`
    promptly — for a `null` restart intent (`START_STICKY` after a system kill) it falls back to T2's
    `BackgroundNotificationBuilder.build(BackgroundServiceState.Starting)` content instead of a
    duplicated string — and returns `START_STICKY`.
  - `startForeground(content)` / `updateNotification(content)` / `stopForeground()` are the interface
    methods the use case calls; `updateNotification` posts only when already in the foreground and
    tolerates a denied `POST_NOTIFICATIONS` (`NotificationManagerCompat.areNotificationsEnabled()`
    guard plus a `SecurityException` guard), so the FGS still runs and nothing crashes (API 36 spec:
    the service does not need the permission to start, only to show the notice).
  - `onBind` returns a `LocalBinder` exposing the instance, which is how the composition root (T6) and
    the instrumented test obtain the `ForegroundServiceHost` without constructing the framework-owned
    service.
- Added `AndroidBrightnessNotificationFactory` (`.../BackgroundService/AndroidBrightnessNotificationFactory/`):
  `BackgroundNotificationContent` → `NotificationCompat` with `setOngoing(content.ongoing)`,
  `setAutoCancel(false)`, a content intent that opens the app's launcher activity
  (`PackageManager.getLaunchIntentForPackage` → `MainActivity`, resolved without an `:app` import,
  which the dependency rule forbids), and exactly one **Stop** action when `content.showStopAction`.
- **Stop action path:** the Stop action is a `PendingIntent.getService` carrying
  `ForegroundBrightnessService.ACTION_STOP`. Tapping it re-enters `onStartCommand(ACTION_STOP)`,
  which calls `stopRequestListener` — a seam set by the composition root (T6) to
  `BrightnessBackgroundService.stop()` — so the command reaches `BackgroundServiceUseCase.stop()`
  (F2 stop → snapshot restore → `host.stopForeground()`, which removes the notification and calls
  `stopSelf()`). When the listener is not wired (dev), the service stops itself without restoring.
- Added `DummyForegroundServiceHost` (same `ForegroundServiceHost/` folder) so `:app` can compose the
  lifecycle without a device; it tracks the current content and foreground flag in memory.
- Manifest (`app/src/main/AndroidManifest.xml`): added `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_SPECIAL_USE` and `POST_NOTIFICATIONS`, and declared
  `ForegroundBrightnessService` with `android:exported="false"` and
  `android:foregroundServiceType="specialUse"` plus the
  `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` `<property>` child (value
  "ambient light based screen brightness control"), per *Android Platform Specifics*. Verified in the
  merged manifest of `:app:assembleDebug`.
- Added the instrumented test
  `infrastructure/src/androidTest/kotlin/com/ambientbrightness/infrastructure/ForegroundBrightnessServiceTest.kt`
  (JUnit 4 / `AndroidJUnit4`): starts the service, binds through the `LocalBinder`, asserts
  `isInForeground`, asserts the built notification is `FLAG_ONGOING_EVENT`, is not
  `FLAG_AUTO_CANCEL` and carries exactly one "Stop" action, and (when notifications are visible)
  asserts the same on the posted notification. Device-dependent assertions use `assumeTrue`, matching
  the module's existing smoke tests; here it only has to compile.
- `:infrastructure` gained `implementation(libs.androidx.core.ktx)` (for `NotificationCompat`,
  `ServiceCompat`, `ContextCompat`, `NotificationManagerCompat`); the instrumented test deliberately
  uses only platform APIs so it does not depend on the androidx test classpath. No `:core` change:
  T1's `BackgroundNotificationContent`/`ForegroundServiceHost` and T2's builder were consumed as-is.
- Verification (from the repo root):
  `$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'` then
  `.\gradlew.bat :infrastructure:test :infrastructure:assembleDebugAndroidTest :app:assembleDebug --no-daemon --console=plain`
  → **BUILD SUCCESSFUL** (92 actionable tasks); `:infrastructure` unit tests 8 classes / 32 tests,
  0 failures; `:infrastructure:assembleDebugAndroidTest` and `:app:assembleDebug` compile/link.

### T5 — Persistence, battery-optimization client and boot restart

*Done: implementation commit `fb10bc2ba0150c5b3094f142c37a0274ee11c02b`.*

- Added `AndroidControllerStateStore` (`:infrastructure/.../BackgroundService/ControllerStateStore/Android/`)
  as the persistent `ControllerStateStore`.
  - **Storage choice: `SharedPreferences`, not DataStore.** The T1 contract declares `desiredRunning`
    and `lastStartReason` as *synchronous* `var`s, and the boot receiver (AC2.3/AC2.4) must read
    `desiredRunning` inside `onReceive` without suspending — DataStore only exposes a
    suspending/flow API, so a DataStore store would have forced a `runBlocking` in the receiver. One
    `SharedPreferences` file (`background_service_state`) serves reads from the in-memory snapshot the
    framework loads on first access; writes use `Editor.apply()`.
  - `lastStartReason` is persisted by `StartReason.name`; an unknown or absent name reads back as
    `null` via the `internal fun startReasonOrNull(name)` helper (unit-testable, no Android), so a
    renamed/removed enum value can never crash boot.
- Added `DummyControllerStateStore` (`ControllerStateStore/Dummy/`, in-memory) so `:app` (T6) can be
  composed without persistence.
- Added `AndroidBatteryOptimizationClient` (`.../BatteryOptimizationClient/Android/`).
  - **Documented choice:** Android has no change callback for the exemption, so `isExempt` is a
    `MutableStateFlow` (exposed as `StateFlow<Boolean>`, a `Flow` subtype) that is re-read from
    `PowerManager.isIgnoringBatteryOptimizations(packageName)` in `refresh()` — at construction, before
    every `requestExemption()`/`openSettings()` launch, and on demand for F6/F7 when the user returns
    from the system screen. No coroutine scope is needed because the flow is never `stateIn`-backed.
  - Both entry points open the advisory `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` list
    (with `FLAG_ACTIVITY_NEW_TASK`); the app-specific `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
    dialog is deliberately **not** used and no `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission was
    added (Play policy implications, per *Android Platform Specifics*).
  - Construction is seam-based (`exemptionReader: () -> Boolean`, `settingsScreenOpener: (String) ->
    Unit`) with a `companion fun create(context)` for real use, so the client is unit-testable without a
    device or a `Context`.
- Added `DummyBatteryOptimizationClient` (`BatteryOptimizationClient/Dummy/`): in-memory exemption plus
  call counters for the two launch entry points.
- Added `BootCompletedReceiver` (`.../BootCompletedReceiver/`), registered for
  `Intent.ACTION_BOOT_COMPLETED` **only** (never `ACTION_LOCKED_BOOT_COMPLETED`, since the store lives
  on credential-protected storage that is unavailable before the first unlock).
  - **How it decides to start:** `onReceive` checks the action, then
    `handleBootCompleted(action, storeFactory(context), serviceStarter(context))` — when the action is
    `BOOT_COMPLETED` it reads the synchronous `store.desiredRunning`; only when it is `true` does it call
    `ContextCompat.startForegroundService(ForegroundBrightnessService)`. When `false` it does nothing at
    all: no service start and therefore no brightness write (AC2.4). It returns immediately (no
    suspension, no I/O beyond the in-memory SharedPreferences snapshot).
  - Kept independent of `:app`: it constructs the infrastructure store directly via an overridable
    `internal var storeFactory: (Context) -> ControllerStateStore` and an overridable
    `internal var serviceStarter: (Context) -> Unit`, and its tested logic lives in the `internal fun
    handleBootCompleted(action, store, startService)` seam (same pattern as T4/T5's lambda seams), so a
    stubbed store drives it without a `Context`.
- Manifest (`app/src/main/AndroidManifest.xml`): added `RECEIVE_BOOT_COMPLETED` (no duplicate of the
  T4 permissions) and
  `<receiver android:name="...BootCompletedReceiver.BootCompletedReceiver" android:enabled="true"
  android:exported="false">` with an `<intent-filter>` for `android.intent.action.BOOT_COMPLETED`.
  Verified in the merged manifest of `:app:assembleDebug`.
- Tests (JUnit 4, `:infrastructure/src/test/...`): `BootCompletedReceiverTest` (desiredRunning `true` →
  one start; `false` → none; a non-boot action → ignored and the store not even read — AC2.3, AC2.4);
  `AndroidBatteryOptimizationClientTest` (exemption read at construction and exposed, `refresh()`
  re-reads, both entry points open `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` and not
  `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — AC2.2); `DummyBackgroundServiceAdaptersTest` and
  `AndroidControllerStateStoreTest` (enum-name round-trip incl. unknown-name → null).
- Verification (from the repo root):
  `$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'` then
  `.\gradlew.bat :infrastructure:test :app:assembleDebug --no-daemon --console=plain`
  → **BUILD SUCCESSFUL** (61 actionable tasks); `:infrastructure` unit tests 12 classes / 45 tests,
  0 failures, 0 errors, 0 skipped (the four T5 test classes add 13 tests); `:app:assembleDebug`
  compiles/links. No `:core` change.

### T6 — Composition root, state collector and restart-after-kill hardening

*Done: implementation commit `bc2008bdc268ddb36a57e1a1220da3f424414d14`.*

- **F5 is now complete (T1–T6).** Wired the whole feature into the `:app` composition root; no
  business logic moved into `:app`, and neither `:core` nor `:infrastructure` changed for T6.
- Added `BrightnessServiceCollector` (`:app/.../BackgroundService/Collector/`, folder == package like
  the other collectors): a thin, logic-free forwarder of `BrightnessBackgroundService.state` and
  `batteryOptimizationExempt`, mirroring `AmbientLightCollector`/`BrightnessStatusCollector`.
- `AmbientBrightnessComponent` builds one app-scoped `BackgroundServiceUseCase` from the existing F2
  `brightnessController`, a `ForegroundServiceHost`, a `ControllerStateStore`, a
  `BatteryOptimizationClient`, `StopBrightnessRestorer(brightnessClient)`,
  `BackgroundNotificationBuilder()` and `controllerScope`, and exposes it as
  `val backgroundService: BrightnessBackgroundService` plus `backgroundServiceCollector`. The use case
  drives the same controller the activity's `statusCollector` observes, so there is exactly one
  control loop, and the restore path writes through the same `brightnessClient` (no parallel
  brightness mechanism, AC3.2). New constructor parameters default to `DummyForegroundServiceHost`,
  `DummyControllerStateStore` and `DummyBatteryOptimizationClient`, so JVM tests inject deterministic
  adapters exactly like the existing client parameters.
- **Platform host bridge:** `ForegroundServiceHostBridge` (`:app/Composition/`, the only place allowed
  to import `:infrastructure`). The Android `Service` is created by the platform, so the bridge (1)
  brings it up with `ContextCompat.startForegroundService` (respecting the foreground window before
  any binding), (2) binds it with `bindService`/`BIND_AUTO_CREATE` and obtains the instance through
  `ForegroundBrightnessService.LocalBinder`, and (3) delegates `startForeground`/`updateNotification`/
  `stopForeground` to that instance, queueing the latest `BackgroundNotificationContent` until
  `onServiceConnected` fires (binding is asynchronous, the lifecycle calls are synchronous). It
  forwards the service's `stopRequestListener` to a `stopRequestHandler`, which
  `AmbientBrightnessApplication` wires to `component.backgroundService::stop`, so the notification's
  Stop action travels the same path as a stop from F7 (F2 stop → snapshot restore → foreground stop).
  `AmbientBrightnessApplication` also passes `AndroidControllerStateStore` and
  `AndroidBatteryOptimizationClient.create`. `MainActivity` and the existing collectors are unchanged
  and still compile; starting/stopping from the UI stays F6/F7's job.
- Import rule preserved: `git grep -n "com\.ambientbrightness\.infrastructure\." -- app/src/main/kotlin`
  matches only `Composition/AmbientBrightnessComponent.kt`, `Composition/AmbientBrightnessApplication.kt`
  and `Composition/ForegroundServiceHostBridge.kt`.
- Tests (`:app`, JUnit 4 + `kotlinx-coroutines-test`, `runTest`/`backgroundScope`/`TestClock`, the same
  style as `AmbientBrightnessResponseTuningTest`), driving the real component with dummy adapters and a
  recording `SystemBrightnessClient`:
  - **AC1.1** — `start(UserRequest)` promotes the host, persists `desiredRunning`/`lastStartReason`,
    reaches `BackgroundServiceState.Running`, and the state carries the brightness F2 applied last.
  - **AC2.1** — a **simulated restart** (a fresh component over the same persisted settings store and
    controller state, `start(SystemRestart)`) resumes `Running` with the stored response speed and the
    same applied brightness as before the simulated kill.
  - **AC3.1 / AC3.2 / AC3.3** — `stop()` returns to `Stopped`, clears the foreground service and
    `desiredRunning`, and restores the pre-controller brightness through F2's client.
- **Compile-verified only (needs a device):** the real process-kill/`START_STICKY` restart and
  swipe-from-recents (AC1.2) cannot be reproduced on a plain JVM — they require the Android platform to
  reclaim and restart the process, and the recents swipe is an ActivityManager behaviour. The JVM test
  therefore simulates the restart (above) and the bridge's real delegation stays compile-verified; the
  on-device parts are covered by the `:infrastructure` instrumented `ForegroundBrightnessServiceTest`
  and need a device/emulator run.
- Verification (from the repo root):
  `$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'` then
  `.\gradlew.bat :core:test :infrastructure:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --console=plain`
  → **BUILD SUCCESSFUL** (98 actionable tasks); `:core` 31 classes / 201 tests, `:infrastructure`
  12 classes / 45 tests, `:app` 3 classes / 8 tests — 0 failures, 0 errors, 0 skipped
  (`AmbientBrightnessBackgroundServiceTest` adds 3 tests); `:app:assembleDebug` and
  `:app:assembleDebugAndroidTest` compile/link.
- **AC coverage map:** AC1.1 → `AmbientBrightnessBackgroundServiceTest: AC1_1…`; AC2.1 →
  `…AC2_1…` (AC2.2/AC2.3/AC2.4 by the T5 `:infrastructure` tests); AC3.1/AC3.2/AC3.3 →
  `…AC3_1 AC3_2 AC3_3…`; AC3.4 → T3 `BackgroundServiceUseCaseTest`; AC4.1–AC4.3 → T2
  `BackgroundNotificationBuilderTest` + T4 instrumented test; AC1.2 (swipe-from-recents) and a real
  process kill (AC2.1 on device) are compile-verified only (device required, see above).
