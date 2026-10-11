# F1 — Ambient Light Sensing

| Field | Value |
|---|---|
| **Epic** | [EPIC: Ambient Brightness Controller](./EPIC-ambient-brightness-controller.md) |
| **Status** | Draft — awaiting review |
| **Depends on** | — |
| **Depended on by** | F2 (Automatic Brightness Control), F4 (Response Tuning), F7 (Status Dashboard) |

## Overview

This feature provides the app's input: a continuous, throttled and stabilised stream of ambient
illuminance (lux) values read from the device's ambient light sensor. It is the foundation every
other feature builds on, and it is what makes the difference between brightness that reacts to the
real environment and brightness that reacts to sensor noise.

## User Story 1 — Continuous sensor reading

As a user, I want the app to read the ambient light sensor continuously while it is running, so that
brightness decisions always reflect the light that is actually around me right now.

#### Acceptance Criteria

- Given the controller is running and the device has an ambient light sensor, when the ambient light
  changes, then a new lux value is produced within the configured sampling interval and no later
  than 1 s after the change.
- Given the controller is running, when the sensor would otherwise report faster than the configured
  sampling interval, then sensor events are throttled/aggregated to at most one processed value per
  interval, so the loop does not run more often than intended.
- Given the controller is stopped, when the app is idle, then the sensor listener is unregistered and
  no sensor events are processed at all.
- Given the screen is off, when the controller is running, then no brightness changes are applied
  (the sensing loop may idle in parallel with the screen state).

## User Story 2 — Clear detection of sensor availability

As a user whose device has no usable light sensor, I want to be told clearly and early, so that I do
not mistake a missing sensor for a bug in the app.

#### Acceptance Criteria

- Given the device has no ambient light sensor, when the app is started, then all sensor-dependent
  features are disabled and a clear, non-technical explanation is shown instead of a failure or a
  silent no-op.
- Given the device has an ambient light sensor, when the app is started, then the sensor is reported
  as available along with its name and reported maximum range, for diagnostics.
- Given the sensor is reported as present but returns no value within the startup timeout, when the
  timeout elapses, then the app surfaces the same "sensor not usable" state as for a missing sensor.

## User Story 3 — Stabilised, noise-free readings

As a user, I want the measured brightness value to be stable, so that a single spurious sensor
reading cannot cause a visible jump in screen brightness.

#### Acceptance Criteria

- Given a single reading that lies far outside the recent trend, when it is received, then it is
  discarded and does not by itself change the value handed on to the brightness calculation.
- Given steady ambient light, when the sensor reports values within its normal noise band, then the
  value handed on to the brightness calculation stays within a stable band and does not jitter.
- Given the sensor reports 0 lux (total darkness), when the value is processed, then it is treated as
  a valid, very-low-light reading and not as a missing or invalid value.
- Given the sensor reports a value outside its documented range (negative, or above the maximum
  range), when it is processed, then it is clamped to the valid range before being used.

## Out of Scope

- Converting lux into a screen brightness value (that is F2).
- Deciding how quickly changes are followed, i.e. smoothing and damping behaviour (that is F4).
- Any sensor other than the ambient light sensor — no proximity, camera-based, or colour sensors.
- Sensor calibration curves per device model.
- Historical logging or charting of sensor values.

## Technical Design

### Architecture & Components

Three Gradle modules (Android 16 / API 36, minSdk = targetSdk = 36, Kotlin):

- `:core` — pure Kotlin, no Android APIs. Domain model, boundary interface, use-cases, classifier.
  `:core` gains `implementation(libs.kotlinx.coroutines.core)` from the version catalog (for
  `Flow`/`StateFlow`) and remains Android-free.
- `:infrastructure` — Android adapters implementing the `:core` client interfaces.
- `:app` — composition root (manual DI), lifecycle owner of the collection loop.

`com.ambientbrightness` is the package root; `:core` carries `core.*`, adapters `infrastructure.*`.

Added/changed components:

- `AmbientLightService` (boundary, `:core`): the single public entry point for sensor
  availability and a stabilised lux stream. Everything else in the app depends on this.
- `LuxSample` (`:core`, value object): `lux: Float`, `timestampMillis: Long`.
- `SensorAvailability` (`:core`, sealed):
  ```kotlin
  sealed interface SensorAvailability {
      data class Available(val profile: SensorProfile) : SensorAvailability
      data class Unavailable(
          val reason: SensorUnavailableReason,
          val userMessage: String = reason.defaultUserMessage,
      ) : SensorAvailability
  }
  enum class SensorUnavailableReason(val defaultUserMessage: String) { NoSensor, NotUsable }
  ```
  `reason` is the machine-readable key that F7 maps to localized string resources; `userMessage` is
  a non-technical English fallback for logs, tests and non-UI consumers.
- `SensorProfile` (`:core`): sensor `name`, `maxRangeLux`, `minDelayMicros` for diagnostics.
- `AmbientLightSensorClient` (`:core` interface, adapter in `:infrastructure`):
  ```kotlin
  interface AmbientLightSensorClient {
      val availability: SensorAvailability
      fun register(onLux: (Float) -> Unit)
      fun unregister()   // idempotent; guarantees no further callbacks
  }
  ```
  Adapter: raw sensor registration/events.
- `ScreenStateClient` (`:core` interface, adapter in `:infrastructure`):
  ```kotlin
  interface ScreenStateClient { val isScreenOn: StateFlow<Boolean> }   // hot, conflated
  ```
- `LightLevelClassifier` (`:core`, Decision D10): `object LightLevelClassifier { fun classify(lux: Float): LightLevel }`
  — stateless and total. Boundaries are lower-inclusive / upper-exclusive: `DARK [0, 10)`,
  `DIM [10, 100)`, `INDOOR [100, 1000)`, `BRIGHT [1000, 10000)`, `FULL_SUN [10000, ∞)`. `UNKNOWN`
  is returned for non-finite lux (NaN, ±Infinity) and for negative lux (invalid input). AC3.4 clamps
  negatives before the classifier in the pipeline, so a real 0 lux reading is `DARK` per AC3.3.
- `AmbientLightUseCase` (`:core`): orchestrates client events → throttle → stabilise → emit.
- `ReadingStabiliser` (`:core` interface) + `OutlierClampingStabiliser` (`:core`):
  ```kotlin
  interface ReadingStabiliser { fun stabilise(sample: LuxSample): LuxSample? }
  ```
  Synchronous and non-suspending; `null` means the value is rejected/not emitted this round.
  Implementations are stateful across calls (they retain the last accepted value for outlier
  rejection). `OutlierClampingStabiliser` is constructed with `maxRangeLux`. This matches F4's
  already-written `stabilise(sample): LuxSample?`; F4's "stateless" wording refers to the fixed
  per-sample duties, not the retained state. Also does range clamping and 0-lux validity.
- `AmbientLightCollector` (`:app`, thin): consumes the boundary interface and forwards samples;
  no business logic.

### Data Flow

`SensorManager` events → `AmbientLightSensorClient` (adapter) → `AmbientLightUseCase`:
raw event is throttled to at most one processed value per sampling interval, then handed to
`ReadingStabiliser.stabilise(sample: LuxSample)`: a `null` return rejects the sample (nothing is
emitted this round); otherwise the accepted sample is clamped to `[0, maxRangeLux]` and single
samples far outside the recent trend are rejected. Because negatives are clamped here before
classification, `LightLevelClassifier.classify(lux)` only ever receives non-negative finite lux
(AC3.4 → AC3.3). The accepted sample becomes `LuxSample`, is classified via `LightLevelClassifier`,
and is emitted on `AmbientLightService.luxStream()`. `ScreenStateClient` gates emission only for
consumers that request screen-on samples; the sensing loop itself may idle while screen-off
(AC1.4) — no brightness action is taken in F1.

Interfaces (`:core`):

```kotlin
interface AmbientLightService {
    val availability: StateFlow<SensorAvailability>
    fun start(): Flow<LuxSample>       // cold; collection registers the listener
    fun stop()                         // unregisters the listener deterministically
}
```

**Extension point (F2/F4):** F2 must consume `AmbientLightService.luxStream()`/`availability`
only — it must never touch `AmbientLightSensorClient`. F4 replaces the injected
`ReadingStabiliser` (constructor parameter of `AmbientLightUseCase`) with a user-setting-driven
implementation; F1 ships only the fixed default and does not expose any tuning setting.

### State & Persistence

No persistence, database or file storage. `availability` and the last stabilised sample are
in-memory only; collection state is tied to the `start()`/`stop()` lifecycle. `AmbientLightService`
is app-scoped so the stream survives view recomposition but not process death.

### Android Platform Specifics

- `Sensor.TYPE_LIGHT` via `SensorManager.getDefaultSensor`; register/unregister on start/stop,
  never in a lifecycle-less singleton.
- Reported units are SI lux; `getMaximumRange()` bounds the accepted range, `getMinDelay()`
  informs the sampling interval. Sensors on older/lower-end devices may ignore the requested rate.
- Screen state via `PowerManager.isInteractive` + a display listener — not a polling loop.
- No runtime permission is required for `TYPE_LIGHT`.
- Compose is not used in this feature.

### Edge Cases & Failure Handling

- No sensor (`getDefaultSensor` returns null) → `Unavailable(NoSensor)`.
- Sensor present but silent past the startup timeout → `Unavailable(NotUsable)` (same surfaced
  state as no sensor, AC2.3).
- Negative or above-range value → clamped before use (AC3.4).
- `0 lux` → valid very-low-light sample (AC3.3), never treated as missing.
- Spurious outlier → discarded, does not move the emitted value on its own (AC3.1).
- Screen off → no brightness action; loop may idle (AC1.4).
- `stop()` must be idempotent and guarantee unregistration (AC1.3).

### Test Strategy

`:core` use-case, classifier and stabiliser are pure Kotlin and unit-tested with an injected
`Clock` and fake clients (`FakeAmbientLightSensorClient`, `FakeScreenStateClient`), covering
throttling, outlier rejection, clamping, 0 lux and the startup timeout. Adapters get a
`Dummy`/fake implementation for development builds per the architecture, so `:app` can run
without hardware. Android framework interaction (`SensorManager`, display listener) is verified
by one instrumented smoke test; no Compose tests.

### Build Toolchain (verified on this machine)

- **JDK 21** — Eclipse Temurin `21.0.12.101`, at `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot`.
  `JAVA_HOME` is set machine-wide, so new processes pick it up automatically. AGP requires JDK 17+.
- **Android SDK** — at `C:\Users\endre\AppData\Local\Android\Sdk`, containing:
  `platform-tools` 37.0.1, `platforms/android-36` (Android 16), `build-tools/36.0.0`, and an
  accepted `android-sdk-license`. Installed with the Android CLI (`android sdk install`).
- **Android CLI** — `Google.AndroidCLI` 1.0.16500706 (`android.exe`, installed via winget). Provides
  `android sdk …` (SDK packages), `android create …` (project templates), `android run …` and
  `android docs …` (official documentation search). It is *not* required to build the project.
- **`ANDROID_HOME` and `ANDROID_SDK_ROOT` are not set** — the SDK location must come from
  `local.properties` (`sdk.dir=…`), which is machine-local and must not be committed.
- **Gradle wrapper is part of the repository** (T0): run `.\gradlew.bat …` from the project root.
  Gradle, AGP, Kotlin and Compose versions are pinned in `gradle/libs.versions.toml`, which is the
  single source of truth — the catalogue set came from Google's own `empty-activity` template
  (`android create empty-activity`), so it is validated for `compileSdk = 36`. Do not hand-guess
  AGP/Kotlin/Gradle compatibility; add new dependencies through the catalogue and keep the build green.
- Shell note: every command runs in a **fresh process**, so tools installed during a session are not
  on `PATH` for later commands — use absolute paths.

### Implementation Tasks

- [x] **T0 — Gradle project skeleton**: create the three-module build with Kotlin DSL and a version
  catalog. Root files: `settings.gradle.kts` (`include(":app", ":core", ":infrastructure")`,
  `pluginManagement`/`dependencyResolutionManagement` with `google()` + `mavenCentral()`),
  `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, the Gradle wrapper
  (`gradlew`, `gradlew.bat`, `gradle/wrapper/**`), `.gitignore` (build output, `.gradle`,
  `local.properties`) and a machine-local `local.properties`. Per module: `:core` uses the plain
  Kotlin JVM plugin with JUnit 5 and **no Android plugin**; `:infrastructure` (`com.android.library`)
  and `:app` (`com.android.application`) declare `namespace`, `compileSdk = 36`, `minSdk = 36`,
  `targetSdk = 36` and a Java/Kotlin toolchain of 21. `:app` additionally gets a minimal
  `AndroidManifest.xml` and an empty Compose `MainActivity` so the build is verifiable end-to-end.
  No feature code, permissions or services yet. Source layout stays `:core/src/main/kotlin/…`
  (not `src/core/…`) per the recorded decision.
  *Files: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`,
  `gradle/wrapper/**`, `gradlew`, `gradlew.bat`, `.gitignore`, `local.properties`,
  `:core/build.gradle.kts`, `:infrastructure/build.gradle.kts`, `:app/build.gradle.kts`,
  `:app/src/main/AndroidManifest.xml`, `:app/src/main/kotlin/com/ambientbrightness/app/MainActivity.kt`.*
  *Verify: `./gradlew :core:test :app:assembleDebug` succeeds; a deliberately failing `:core` test
  is reported as a failure (proves the test task actually runs).*
  *Done: versions ported from the Google-validated `android create empty-activity` template —
  Gradle 9.1.0 (wrapper `-bin`, sha256 pinned), AGP 9.0.1 (`com.android.application` /
  `com.android.library`), Kotlin 2.3.20 (`org.jetbrains.kotlin.jvm` for `:core`,
  `org.jetbrains.kotlin.plugin.compose` for `:app`; AGP 9 has built-in Kotlin support, so no
  `kotlin.android` plugin), Compose BOM 2026.03.01, JUnit Jupiter 5.12.2.
  Verified with `.\gradlew.bat :core:test :app:assembleDebug --no-daemon` → BUILD SUCCESSFUL;
  `:infrastructure:assembleDebug` also BUILD SUCCESSFUL. A temporary failing
  `SkeletonFailingTest` in `:core/src/test/kotlin` made `:core:test` FAIL as expected and was
  removed afterwards (test task proven to execute). APK: `app\build\outputs\apk\debug\app-debug.apk`.
  Commit: `24e583d` (`feat(build): add Gradle skeleton for :app, :core and :infrastructure`).*
- [ ] **T1 — Domain model, boundary interface and classifier**: add `LuxSample`, `SensorProfile`,
  `SensorAvailability`, the `LightLevel` enum + `LightLevelClassifier`, the `AmbientLightSensorClient`
  and `ScreenStateClient` interfaces, and the `AmbientLightService` boundary interface in `:core`.
  Keep them Android-free. *Files: `:core/src/main/kotlin/com/ambientbrightness/core/**`.*
  *Verify: compiles with no Android dependency; unit tests for classifier boundaries.*
- [ ] **T2 — Infrastructure adapters (real + Dummy)**: implement `AndroidAmbientLightSensorClient`
  (registration, events, `SensorProfile` from `getMaximumRange`/`getMinDelay`) and
  `AndroidScreenStateClient`; add `DummyAmbientLightSensorClient` for tests/development.
  *Files: `:infrastructure/src/main/kotlin/com/ambientbrightness/infrastructure/**`.*
  *Verify: instrumented smoke test that a light event is received; Dummy path runs on emulator.*
- [ ] **T3 — Stabiliser + throttled collection use-case**: implement `ReadingStabiliser`,
  `OutlierClampingStabiliser` (clamp, outlier rejection, 0-lux validity) and `AmbientLightUseCase`
  with injectable `Clock` and injected stabiliser, throttling to one value per interval.
  *Files: `:core/src/main/kotlin/com/ambientbrightness/core/AmbientLightUseCase/**`.*
  *Verify: unit tests for AC1.2, AC3.1–AC3.4 with fake clients and a fixed clock.*
- [ ] **T4 — Availability, startup timeout and status surfacing**: resolve availability at start,
  apply the startup timeout → `Unavailable(NotUsable)`, and expose both states through
  `AmbientLightService.availability`; add the collector that carries the non-technical message
  to the caller (no UI). *Files: `:core/.../AmbientLightService/**`,
  `:app/src/main/kotlin/com/ambientbrightness/app/AmbientLightCollector/**`.*
  *Verify: unit tests for AC2.1–AC2.3.*
- [ ] **T5 — Composition root and lifecycle wiring**: bind clients, use-case and service in
  `:app`, wire `start()`/`stop()` so `stop()` unregisters the listener idempotently and the
  screen-off gate is honoured. *Files: `:app/src/main/kotlin/com/ambientbrightness/app/**`.*
  *Verify: instrumented test asserting no sensor events after `stop()` (AC1.3) and idle while
  screen off (AC1.4).*

## Suggested Implementation Order

`T0 → T1 → T2 → T3 → T4 → T5`. T0 has no dependencies and unblocks every other feature, so it must
land first. T1 fixes the boundary contract that F2/F4 depend on, so it must land
first and be reviewed. T2 and T3 can proceed in parallel once the interfaces exist (T3 tests only
need fakes). T4 depends on T2's availability data; T5 depends on everything else because it is the
only module that touches real Android lifecycles. Ship T1+T3 as a vertical slice testable with
fakes before wiring hardware in T2/T5.
