# F6 — Permissions & Setup Flow

| Field | Value |
|---|---|
| **Epic** | [EPIC: Ambient Brightness Controller](./EPIC-ambient-brightness-controller.md) |
| **Status** | Draft — awaiting review |
| **Depends on** | F5 (Background Service) |
| **Depended on by** | F7 (Status Dashboard) |

## Overview

This feature gets the user from "installed" to "working" with the minimum of confusion. Because the
app needs a *special* permission that cannot be granted by a normal dialog, plus a runtime
notification permission and a battery-optimization exemption, the setup flow has to explain each step,
open the right system screen, detect the outcome, and clearly report what is still missing.

## User Story 1 — Understand what the app needs and why

As a first-time user, I want a short explanation of what the app does and which permissions it needs
before it asks for anything, so that I can decide whether to trust it.

#### Acceptance Criteria

- Given the app is opened for the first time after installation, when the first screen is shown, then
  it briefly explains what the app does and lists the permissions it will ask for together with the
  reason for each one.
- Given the setup flow lists a permission, when it is described, then the description is written in
  plain, non-technical language and does not rely on Android jargon.
- Given the user is in the setup flow, when they complete or skip it, then the app does not ask for
  the same thing again on every subsequent start.

## User Story 2 — Grant the required permissions

As a user, I want each permission request to take me straight to the right system screen, so that I do
not have to figure out where the setting lives.

#### Acceptance Criteria

- Given the `WRITE_SETTINGS` permission has not been granted, when I activate the corresponding setup
  step, then the system screen for "Modify system settings" for this app is opened directly.
- Given the notification permission has not been granted, when I activate the corresponding setup
  step, then the system runtime permission dialog for notifications is shown.
- Given battery optimization is active for this app, when I activate the corresponding setup step,
  then the system screen to exempt the app from battery optimization is offered.
- Given I return to the app after visiting a system screen, when the app resumes, then it re-checks
  the permission state and updates the UI immediately without requiring a restart.

## User Story 3 — Always know what is blocking

As a user, I want to see exactly which requirement is still missing, so that I can tell the difference
between "not set up yet" and "broken".

#### Acceptance Criteria

- Given at least one required permission is missing, when I look at the app, then each missing
  requirement is listed with its own state, and the Start action is disabled.
- Given I try to start the controller while a requirement is missing, when I attempt it, then the app
  explains exactly which requirement blocks the start instead of failing silently.
- Given all requirements are satisfied, when I look at the app, then the setup state is clearly shown
  as complete and the Start action is enabled.

## User Story 4 — Handle uncovered and degraded situations

As a user, I want the app to be honest when my device or configuration cannot support it, so that I do
not keep troubleshooting something that cannot work.

#### Acceptance Criteria

- Given the device has no ambient light sensor, when the app starts, then it reports the device as
  unsupported, explains why, and does not offer to start the controller.
- Given the `WRITE_SETTINGS` permission is revoked while the controller is running, when the next
  brightness write fails, then the app detects this, stops attempting writes, and tells the user which
  permission needs to be re-granted.
- Given the notification permission is not granted, when the controller is started, then the app warns
  that the ongoing-service notification may not be visible to the user.

## Out of Scope

- Requesting permissions that the app does not need.
- Root, ADB or device-owner based permission grants.
- Automated permission granting without user interaction.
- Onboarding videos, account creation, or any sign-in flow.
- Explaining Android internals beyond what is needed to complete the setup.

## Resolved Decisions

- The setup flow is a **single checklist screen** that also serves as the permanent "what is still
  missing" view; there is no multi-step wizard.
- The checklist is **skippable**, so the user can look around the app before granting anything.
- Granting the battery-optimization exemption is **advisory**: it is recommended and explained, but it
  never blocks Start. Only the light sensor and `WRITE_SETTINGS` gate Start.

## Technical Design

### Architecture & Components

Same three modules as F1/F2/F5 (`:core` pure Kotlin, `:infrastructure` Android adapters, `:app` composition root; package root `com.ambientbrightness`). F6 owns the **aggregated requirement-state model** and the platform navigation glue; **F7 owns the presentation** (Compose checklist screen, copy, Start button). F6 does not re-detect permissions: it composes existing checkers.

`:core` (Android-free):
- `AppSetupService` (boundary interface) — the single surface **F7 consumes**; exposes aggregated `SetupStatus` and the Start gate `isReadyToStart`, plus `refresh()` / `acknowledgeSetup()`.
- `SetupRequirementAggregator` — the aggregation use-case/service implementing `AppSetupService`. Breaks down into per-requirement `Task/` classes: `SensorRequirementTask`, `WriteSettingsRequirementTask`, `NotificationRequirementTask`, `BatteryRequirementTask` (each maps one source into a `SetupRequirement`).
- Model VOs/enums: `SetupStatus`, `SetupRequirement`, `SetupRequirementId` (`AmbientLightSensor`, `WriteSettings`, `Notifications`, `BatteryOptimization`), `RequirementSeverity` (`Blocking`, `Advisory`), `RequirementState` (sealed: `Satisfied`, `Missing(reason)`, `Unsupported(reason)`, `Unknown`), `SetupAction` (sealed: `RequestNotificationPermission`, `OpenWriteSettings`, `OpenBatteryOptimizationSettings`, `None`).
- `NotificationPermissionChecker` (new `:core` interface) — `val isGranted: Flow<Boolean>`.
- `SetupStateStore` (new `:core` interface) — persistence of `setupAcknowledged` + `lastShownAtMillis` only.

`:infrastructure`: `AndroidNotificationPermissionChecker`, `AndroidSetupStateStore`, and their `Dummy*` variants. **Reuse as-is** (do not re-implement): F1 `AmbientLightService`, F2 `WriteSettingsPermissionChecker` + `AndroidWriteSettingsPermissionChecker`, F5 `BatteryOptimizationClient` + `AndroidBatteryOptimizationClient`.

`:app`: `SetupFlowCollector` (thin pass-through of `AppSetupService`; no logic), `SetupFlowNavigator` (turns a `SetupAction` into an `Intent`/permission request; Activity-scoped), `SetupLifecycleObserver` (ON_RESUME → `refresh()`), manual DI wiring, and the `POST_NOTIFICATIONS` manifest entry. The Compose checklist screen itself is F7.

### Data Flow

Reads: F1 `AmbientLightService.availability` → sensor requirement; F2 `WriteSettingsPermissionChecker` flow → `WriteSettings`; F5 `BatteryOptimizationClient.isExempt` → `BatteryOptimization`; `NotificationPermissionChecker.isGranted` → `Notifications`. `SetupRequirementAggregator` subscribes to all four, maps each through its `Task` class, and emits `SetupStatus`; `isReadyToStart` is true only when every `Blocking` requirement is `Satisfied`.

Actions: F7 renders the list and calls `SetupFlowNavigator.perform(action)` from the tapped row (so the request originates from the Compose screen in `:app`, which has the Activity). `SetupFlowNavigator` fires the correct platform intent / permission launcher. When the user returns, `SetupLifecycleObserver` (ON_RESUME) calls `AppSetupService.refresh()`; the sources re-emit and `SetupStatus` updates without a restart (US2 AC4, US3). Start is invoked on F5 `BrightnessBackgroundService` only when `isReadyToStart`; when blocked, F7 shows `SetupStatus.blockers` reasons (US3 AC2).

### Key Interfaces & Classes

```kotlin
// :core — boundary consumed by F7
interface AppSetupService {
    val status: StateFlow<SetupStatus>
    val isReadyToStart: StateFlow<Boolean>
    fun refresh()                 // re-derive from live sources (called on ON_RESUME)
    fun acknowledgeSetup()        // persists "setup completed/skipped" (US1 AC3)
}
interface NotificationPermissionChecker { val isGranted: Flow<Boolean> }
interface SetupStateStore { var setupAcknowledged: Boolean; var lastShownAtMillis: Long? }

data class SetupStatus(
    val requirements: List<SetupRequirement>,
    val isReadyToStart: Boolean,
    val blockers: List<SetupRequirementId>,   // Blocking requirements that are not Satisfied
)
data class SetupRequirement(
    val id: SetupRequirementId,
    val severity: RequirementSeverity,
    val state: RequirementState,
    val action: SetupAction,                  // what the row should trigger
)
sealed interface RequirementState {
    data object Satisfied : RequirementState
    data class Missing(val reason: MissingReason) : RequirementState          // NotGranted, Revoked
    data class Unsupported(val reason: UnsupportedReason) : RequirementState  // NoAmbientLightSensor
    data object Unknown : RequirementState
}
// :app — platform navigation, Activity-scoped
interface SetupFlowNavigator { fun perform(action: SetupAction); }
```

Severity mapping: `AmbientLightSensor` → `Blocking` (`Unsupported` when no/!usable sensor, US4 AC1); `WriteSettings` → `Blocking`; `BatteryOptimization` → `Advisory` (recommended, but never gates Start); `Notifications` → `Advisory` (never blocks Start, US4 AC3).

### State & Persistence

Only two facts are persisted, via `SetupStateStore` (DataStore/SharedPreferences in `:infrastructure`): `setupAcknowledged` (US1 AC3 — "do not ask again") and `lastShownAtMillis` (analytics/debug only). All requirement states are **re-derived live** on every `refresh()` and are never cached — caching would contradict US2 AC4 (re-check on resume). `SetupStateStore` is a separate concern from F5's `ControllerStateStore`; **rejected**: merging them (different lifetimes and single-responsibility). In-memory only: the current `SetupStatus`.

### Android Platform Specifics

- **`WRITE_SETTINGS` (US2 AC1) — confirmed:** open `Settings.ACTION_MANAGE_WRITE_SETTINGS` with `Uri.parse("package:$packageName")`. The platform documents the `package:` data URI as the way to "directly invoke the management GUI specific to the package name", so the per-app screen is still the supported path, and `Settings.System.canWrite(context)` (API 23+) remains the documented way to read the grant. The same docs state that "in some cases, a matching Activity may not exist, so ensure you safeguard against this" — hence the required `try/catch (ActivityNotFoundException)` and the fallback chain to `ACTION_MANAGE_WRITE_SETTINGS` without a URI, then `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`. Always use the `Settings` constant, never a literal: its value is `android.settings.action.MANAGE_WRITE_SETTINGS` (not `android.settings.MANAGE_WRITE_SETTINGS`).
- **`POST_NOTIFICATIONS` (US2 AC2) — confirmed:** request from the Compose screen in `:app` via `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())` (needs an Activity). Denial is **advisory** because the platform states that apps do not need `POST_NOTIFICATIONS` to launch a foreground service, and that with the permission denied the foreground-service notice is visible in the system Task Manager but not in the notification drawer. The controller therefore keeps running and only the stop affordance disappears (F5 behaviour). Missing permission does **not** block Start (US4 AC3).
- **Battery optimization (US2 AC3):** detect via F5's `BatteryOptimizationClient.isExempt` (`PowerManager.isIgnoringBatteryOptimizations`). Guide with `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`. **Play Store policy risk:** `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` requires the `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission and is restricted by Play to apps whose core function genuinely needs exemption; the safer alternative is the guiding settings intent (no special permission). Prefer the guide screen.
- **Lifecycle re-check (US2 AC4):** `SetupLifecycleObserver` as a `DefaultLifecycleObserver`/`LifecycleEventObserver` on ON_RESUME calls `refresh()`; F7 may also use `LifecycleResumeEffect`. No polling.
- **Sensor (US4 AC1):** read `AmbientLightService.availability`; `Unavailable(NoSensor|NotUsable)` → `Unsupported`, and F7 must not offer Start. No runtime permission is involved.

### Edge Cases & Failure Handling

- No ambient-light sensor → `AmbientLightSensor = Unsupported(NoAmbientLightSensor)`, `Blocking`, Start hidden/disabled with the reason shown (US4 AC1).
- `WRITE_SETTINGS` revoked at runtime → F2's `AutomaticBrightnessController` reports failure → F5 `BackgroundServiceState.Failed`; on the next ON_RESUME the checker flow reports `Missing(Revoked)` and F6 surfaces it (US4 AC2). F6 only reports which requirement is missing; F2/F5 own stopping the writes.
- `POST_NOTIFICATIONS` denied → `Notifications = Missing(NotGranted)`, `Advisory`; Start stays enabled and F6 marks the warning so F7 can explain the invisible notification (US4 AC3).
- Setup skipped → `acknowledgeSetup()` persists `setupAcknowledged = true`; the checklist remains the permanent "what is missing" view (Resolved Decisions), still reachable.
- Source emits nothing yet → `Unknown` (treated as not-ready only for `Blocking` requirements until first real value); avoid a false "ready".
- `ActivityNotFoundException` from any launch → fall back chain above; never crash.
- F5 force-stop left `desiredRunning = true` with nothing running → F6 does not act on it (out of scope); the checklist reconciles the visible state on open.

### Test Strategy

`:core` (JVM): `SetupRequirementAggregator` unit-tested with fakes of F1 `AmbientLightService`, F2 `WriteSettingsPermissionChecker`, F5 `BatteryOptimizationClient` and `NotificationPermissionChecker`, plus a `DummySetupStateStore`. Cover the full matrix (satisfied / missing / unsupported / unknown), `isReadyToStart` gating, `blockers` contents, and that `Notifications` and `BatteryOptimization` never block. `:infrastructure`: instrumented tests for `AndroidNotificationPermissionChecker` and `AndroidSetupStateStore` (persistence + `Dummy*` keep `:app` runnable without a device). `:app`: `SetupFlowNavigator` intent/fallback tests (Robolectric or instrumented) incl. `ActivityNotFoundException`. Do not duplicate F1/F2/F5 tests; no Compose tests here (F7 owns UI tests).

### Implementation Tasks

- [x] **T1 — Core requirement model & boundary interfaces**: add `SetupStatus`, `SetupRequirement`, `SetupRequirementId`, `RequirementSeverity`, `RequirementState` (+ `MissingReason`/`UnsupportedReason`), `SetupAction`, the `AppSetupService` boundary interface, and the `NotificationPermissionChecker` and `SetupStateStore` interfaces. Keep them Android-free. *Files: `:core/src/main/kotlin/com/ambientbrightness/core/AppSetupService/**`.* *Verify: compiles without Android; the model expresses every US1–US4 state.* (depends on: none)
- [x] **T2 — Aggregation service and per-requirement tasks**: implement `SetupRequirementAggregator` (implements `AppSetupService`) subscribing to F1 `AmbientLightService.availability`, F2 `WriteSettingsPermissionChecker`, F5 `BatteryOptimizationClient.isExempt` and `NotificationPermissionChecker.isGranted`; add the `Task/` classes that map each source to a `SetupRequirement`, compute `isReadyToStart` and `blockers` (only `Blocking` requirements gate), and make `Notifications` and `BatteryOptimization` `Advisory`. *Files: `:core/.../SetupRequirementAggregator/**`.* *Verify: unit tests against the state matrix and the Start gate with fakes.* (depends on: T1)
- [x] **T3 — Infrastructure adapters (real + Dummy)**: implement `AndroidNotificationPermissionChecker` (`NotificationManagerCompat.areNotificationsEnabled`/`checkSelfPermission`) and `AndroidSetupStateStore`, plus `DummyNotificationPermissionChecker` and `DummySetupStateStore`. *Files: `:infrastructure/src/main/kotlin/com/ambientbrightness/infrastructure/**`.* *Verify: instrumented persistence test; `:app` runs on the Dummies.* (depends on: T1)
- [x] **T4 — App navigation, lifecycle and wiring**: implement `SetupFlowNavigator` (`perform(SetupAction)` → `ACTION_MANAGE_WRITE_SETTINGS` with `package:` URI, battery-optimization settings intent, notification permission launcher; `ActivityNotFoundException` fallback chain), `SetupFlowCollector` (thin pass-through) and `SetupLifecycleObserver` (ON_RESUME → `refresh()`); wire DI in the composition root and add `POST_NOTIFICATIONS` to the manifest. *Files: `:app/src/main/kotlin/com/ambientbrightness/app/SetupFlow*/**`, `:app/src/main/AndroidManifest.xml`.* *Verify: intent/fallback tests; ON_RESUME triggers `refresh()`.* (depends on: T2, T3)
- [ ] **T5 — First-run acknowledgement and degraded-path integration**: persist `setupAcknowledged`/`lastShownAtMillis` on complete **and** skip (US1 AC3), expose the persisted flag so F7 shows the checklist once and thereafter keeps it as the permanent view, and integrate the degraded paths (no sensor → unsupported/Start not offered; runtime-revoked `WRITE_SETTINGS`; advisory notification warning) end to end with F5's state. *Files: `:core/.../SetupRequirementAggregator/**`, `:app/.../SetupFlow*/**`.* *Verify: US1 AC3, US3 AC1–AC3, US4 AC1–AC3 covered by tests.* (depends on: T4)

### Suggested Implementation Order

`T1 → (T2 ∥ T3) → T4 → T5`. T1 fixes the `AppSetupService` contract F7 consumes and gates Start on, so it lands first and is reviewed with F7. T2 and T3 are independent once T1 exists (T2 tests use fakes, T3 touches only infrastructure). T4 depends on both because navigation needs the model and the notification checker. T5 closes the loop for the first-run and degraded paths and must run against real adapters. Ship T1–T2 as a vertical model slice testable with fakes before any UI wiring.

## Implementation Notes

### T1 — Core requirement model & boundary interfaces

*Done: implementation commit `474d557`.*

- Added the Android-free setup model in `:core` under `com.ambientbrightness.core.AppSetupService`, one type per file: `SetupStatus`, `SetupRequirement`, `SetupRequirementId`, `RequirementSeverity`, `RequirementState`, `MissingReason`, `UnsupportedReason`, `SetupAction`.
- Added the boundaries F7 and `:infrastructure` consume: `AppSetupService` (`status`/`isReadyToStart` as `StateFlow`, `refresh()`, `acknowledgeSetup()`), `NotificationPermissionChecker` (`isGranted: Flow<Boolean>`) and `SetupStateStore` (`setupAcknowledged`, `lastShownAtMillis`).
- Documented the fixed severity mapping in KDoc (`AmbientLightSensor`/`WriteSettings` = `Blocking`, `Notifications`/`BatteryOptimization` = `Advisory`) and that only `Blocking` requirements gate Start; each KDoc names the user story/AC it serves.
- No aggregator, no persistence and no navigation: behaviour lands in T2–T5. The aggregator will own the id→severity mapping at runtime.
- Test `core/src/test/kotlin/com/ambientbrightness/core/AppSetupService/AppSetupModelTest.kt` (JUnit 5 Jupiter, backticked names) constructs every `RequirementState` variant and `SetupAction`, branches over both hierarchies with an exhaustive `when` and no `else`, asserts the severity rule for all four ids, and covers `SetupStatus` equality/`copy` plus `blockers` (only blocking, not-`Satisfied` ids).
- Verification: `.\gradlew.bat :core:test --no-daemon --console=plain` → BUILD SUCCESSFUL, 212 tests / 0 failures / 0 errors / 0 skipped (32 suites; 11 of them in `AppSetupModelTest`). No `android.*` or `androidx.*` import in the new files.

### T2 — Aggregation service and per-requirement tasks

*Done: implementation commit `7548d74`.*

- Added `SetupRequirementAggregator` (`core/src/main/kotlin/com/ambientbrightness/core/SetupRequirementAggregator/SetupRequirementAggregator.kt`, package `com.ambientbrightness.core.SetupRequirementAggregator`), implementing `AppSetupService`. It composes F1 `AmbientLightService.availability`, F2 `WriteSettingsPermissionChecker.hasWriteSettingsPermission`, F5 `BatteryOptimizationClient.isExempt` and F6 `NotificationPermissionChecker.isGranted`, plus the `SetupStateStore`, a `CoroutineScope` and an injected `Shared.Clock`.
- Added the `Task/` mappers (all `internal`, one source value → one `SetupRequirement`): `SensorRequirementTask`, `WriteSettingsRequirementTask`, `NotificationRequirementTask`, `BatteryRequirementTask`.
- **Mapping rules.** Sensor (`Blocking`): `Available` → `Satisfied`/`None`, `Unavailable` (both `NoSensor` and `NotUsable`) → `Unsupported(NoAmbientLightSensor)`/`None`; write settings (`Blocking`): `true` → `Satisfied`/`None`, `false` → `Missing(...)`/`OpenWriteSettings`; notifications (`Advisory`): `true` → `Satisfied`/`None`, `false` → `Missing(NotGranted)`/`RequestNotificationPermission`; battery (`Advisory`): `true` → `Satisfied`/`None`, `false` → `Missing(NotGranted)`/`OpenBatteryOptimizationSettings`. A `null` input (the source has not reported yet) maps to `Unknown`/`None` for every requirement.
- **`Missing(Revoked)` decision.** F2's checker exposes only a boolean, so `WriteSettingsRequirementTask` tracks whether it ever observed `true`: a `false` **after** a satisfied observation is reported as `Missing(Revoked)`, a `false` without one stays `Missing(NotGranted)` (US4 AC2). The memory is monotonic, so repeated derivations keep the same reason. Notifications and battery intentionally stay `NotGranted` (the task contract fixes them, and F5/F6 expose no revocation signal).
- **Derivation.** Each source is collected eagerly in the injected scope into a private "latest value" `StateFlow`; every emission re-derives `SetupStatus` (requirement order `AmbientLightSensor`, `WriteSettings`, `Notifications`, `BatteryOptimization`), so `status`/`isReadyToStart` are never cached. `isReadyToStart` is `blockers.isEmpty()` where `blockers` = the non-`Satisfied` `Blocking` ids only — `Notifications`/`BatteryOptimization` can never appear (US3, US2 AC4). `refresh()` re-derives and re-publishes from the latest observed values; because the sources are observed continuously it is normally idempotent, and it exists as the seam the ON_RESUME hook calls. `acknowledgeSetup()` sets `setupAcknowledged = true` and stores `clock.nowMillis()` in `lastShownAtMillis` (US1 AC3).
- Test doubles: new `core/src/test/kotlin/com/ambientbrightness/core/testdoubles/SetupFlowFakes.kt` with `FakeNotificationPermissionChecker` (null initial value withholds the first emission) and `FakeSetupStateStore`. Reused `FakeAmbientLightService`, `FakeWriteSettingsPermissionChecker`, `FakeBatteryOptimizationClient` and `FakeClock`; added `FakeAmbientLightService.setAvailability(...)` and made `FakeBatteryOptimizationClient` accept a nullable `exempt` (default unchanged) so it can withhold its first emission.
- Test `core/src/test/kotlin/com/ambientbrightness/core/SetupRequirementAggregator/SetupRequirementAggregatorTest.kt` (JUnit 5 Jupiter, 14 tests, `runTest`/`runCurrent`): the full state matrix per requirement (satisfied / missing / unsupported / unknown), the all-`Unknown` seed before any source reports, the `isReadyToStart` gate, `blockers` contents (never notifications/battery), a live update without reconstruction (sensor, write settings and notifications republished in turn), `refresh()` re-derivation/idempotence, and `acknowledgeSetup()` persistence.
- Verification: `.\gradlew.bat :core:test --no-daemon --console=plain` → BUILD SUCCESSFUL, 226 tests / 0 failures / 0 errors / 0 skipped (33 suites; 14 of them in `SetupRequirementAggregatorTest`). No `android.*` or `androidx.*` import in any new or changed file.

### T3 — Infrastructure adapters (real + Dummy)

*Done: implementation commit `c0035e1`.*

- Added the `:infrastructure` adapters under `com.ambientbrightness.infrastructure.AppSetupService`, one responsibility folder per contract with the `<Client>/<Android|Dummy>/<Impl>.kt` convention: `NotificationPermissionChecker/Android/AndroidNotificationPermissionChecker.kt`, `NotificationPermissionChecker/Dummy/DummyNotificationPermissionChecker.kt`, `SetupStateStore/Android/AndroidSetupStateStore.kt`, `SetupStateStore/Dummy/DummySetupStateStore.kt`.
- `AndroidNotificationPermissionChecker` combines `NotificationManagerCompat.areNotificationsEnabled()` with `ContextCompat.checkSelfPermission(...POST_NOTIFICATIONS)` (a granted permission can still be switched off in the system settings, so only the combination matches what the user sees). Android offers no change callback for either read, so the state is a re-readable `StateFlow` refreshed in a documented `refresh()`, mirroring F5's `AndroidBatteryOptimizationClient`; the two platform reads are constructor seams with a `companion create(context)`.
- `AndroidSetupStateStore` is a `SharedPreferences`-backed store (`setup_state` file), the same mechanism and reasoning F5 T5 used for `AndroidControllerStateStore`: the contract is a synchronous `var`, which DataStore cannot serve. `setupAcknowledged` defaults to `false`, and `lastShownAtMillis` is written/removed explicitly so an absent value reads back as `null` instead of a misleading `0L`.
- The Dummy variants are in-memory only (settable `MutableStateFlow` grant, constructor-injected store facts), so `:app` keeps running without a device. No F1/F2/F5 checker was re-implemented and neither `:core` nor `:app` was touched.
- Tests: `:infrastructure` JUnit 4 unit test `infrastructure/src/test/kotlin/com/ambientbrightness/infrastructure/DummyAppSetupAdaptersTest.kt` (3 tests: default/false and toggled grant, initially-granted grant, absent store defaults plus both facts kept in memory). Instrumented test `infrastructure/src/androidTest/kotlin/com/ambientbrightness/infrastructure/AndroidAppSetupAdaptersTest.kt` (`@RunWith(AndroidJUnit4::class)`, platform APIs only, 4 tests: absent values, persistence round-trip across instances, clearing the shown time, and the checker reading the live state twice) — compiled here, no device attached.
- Verification: `.\gradlew.bat :infrastructure:test :infrastructure:assembleDebugAndroidTest --no-daemon --console=plain` → BUILD SUCCESSFUL; `:infrastructure:test` 48 tests / 0 failures / 0 errors / 0 skipped (13 suites; 3 of them in `DummyAppSetupAdaptersTest`), and `:infrastructure:assembleDebugAndroidTest` compiled the instrumented sources.

### T4 — App navigation, lifecycle and wiring

*Done: implementation commit `46325a2692e4956a18c17291a2119b83fcecd2fe`.*

- Added `app/src/main/kotlin/com/ambientbrightness/app/SetupFlow/` with three thin `:app` pieces: `Collector/SetupFlowCollector.kt` (logic-free forwarder of `status` and `isReadyToStart`, mirroring `BrightnessServiceCollector`), `Navigator/SetupFlowNavigator.kt` and `SetupFlow/Lifecycle/SetupLifecycleObserver.kt`.
- **Navigator design (deviation in shape, not in behaviour).** The navigation *decision* is pure data: a new `Navigator/SetupScreen.kt` (`action` + optional `package:` `dataUri`) describes a system screen, and opening one is a single injected seam `(SetupScreen) -> Boolean` that reports whether the device handled it. The three `WRITE_SETTINGS` candidates form the documented fallback order (per-app `ACTION_MANAGE_WRITE_SETTINGS` with `package:`, the same action without the URI, then `ACTION_APPLICATION_DETAILS_SETTINGS`), and the battery row uses `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` — never the restricted `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. `RequestNotificationPermission` delegates to an injected launcher (F7 supplies `rememberLauncherForActivityResult(RequestPermission())`), and `None` does nothing.
  *Why the seam returns a boolean instead of catching `ActivityNotFoundException` in the navigator:* the exception can only be raised by the real `startActivity`, and `Intent`/`Uri`/`ActivityNotFoundException` are Android types that a JVM unit test cannot instantiate (verified: constructing them throws `RuntimeException("Stub!")` under the plain `android.jar`). Moving the `try/catch (ActivityNotFoundException)` into `SetupFlowNavigator.create(context, ...)` keeps the platform surface to one lambda, keeps `:app`'s behaviour identical (the chain is still walked on a missing Activity and never crashes), and makes the whole flow unit-testable off-device.
- `SetupLifecycleObserver` is a `DefaultLifecycleObserver` whose `onResume` calls `AppSetupService.refresh()`, so a grant made in a system screen is re-derived without a restart and without polling (US2 AC2.4).
- **Wiring.** `Composition.AmbientBrightnessComponent` gained Dummy-defaulted `notificationPermissionChecker` and `setupStateStore` parameters, builds one app-scoped `SetupRequirementAggregator` over the existing F1 `service`, F2 `writeSettingsPermissionChecker`, F5 `batteryOptimizationClient` and the injected `Clock`, and exposes `setupService: AppSetupService` and `setupFlowCollector`. `Composition.AmbientBrightnessApplication` passes `AndroidNotificationPermissionChecker.create(this)` and `AndroidSetupStateStore(this)`. `POST_NOTIFICATIONS` was already declared by F5 T4, so the manifest is unchanged. The composition-root isolation rule still holds: `git grep "com\.ambientbrightness\.infrastructure\." -- app/src/main/kotlin` matches only `Composition/**`.
- Files added: `:app .../SetupFlow/Collector/SetupFlowCollector.kt`, `.../SetupFlow/Navigator/SetupFlowNavigator.kt`, `.../SetupFlow/Navigator/SetupScreen.kt`, `.../SetupFlow/Lifecycle/SetupLifecycleObserver.kt`, `:app test SetupFlowNavigatorTest.kt` (7 tests) and `SetupLifecycleObserverTest.kt` (1 test).
- Tests assert the fallback chain end to end: the per-app screen is preferred; a missing per-app screen falls back to the plain action and then to the app details screen; a device that handles none is tolerated without crashing; the battery row uses the advisory action; the notification row requests the runtime permission and opens no screen; `None` starts nothing; and `onResume` calls `refresh()` exactly once.
- Verification (from the repo root): `.\gradlew.bat :core:test :infrastructure:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --console=plain` → BUILD SUCCESSFUL, 0 failures; `:core` 33 classes / 226 tests, `:infrastructure` 13 / 48, `:app` 5 / 16 (T4 adds `SetupFlowNavigatorTest` and `SetupLifecycleObserverTest`).
- Cleanup: an untracked scratch probe (`app/src/test/kotlin/com/ambientbrightness/app/ScratchAndroidBehaviourTest.kt`) left in the tree while establishing the Android-stub behaviour above was deleted and never committed.

