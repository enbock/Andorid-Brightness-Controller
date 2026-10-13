# F4 — Response Tuning

| Field | Value |
|---|---|
| **Epic** | [EPIC: Ambient Brightness Controller](./EPIC-ambient-brightness-controller.md) |
| **Status** | Draft — awaiting review |
| **Depends on** | F1 (Ambient Light Sensing), F2 (Automatic Brightness Control) |
| **Depended on by** | F7 (Status Dashboard) |

## Overview

This feature controls the *timing* of brightness changes: how fast the screen follows a change in
ambient light, how large the individual steps are, and how brief light changes are filtered out. It is
what separates a brightness controller that feels snappy and calm from one that feels sluggish or
twitchy.

## User Story 1 — Choose how fast the screen reacts

As a user, I want to control how quickly the brightness follows changes in ambient light, so that the
screen feels neither sluggish nor jittery.

#### Acceptance Criteria

- Given the response speed is set towards fast, when the ambient light changes significantly, then the
  brightness reaches the new target within the short reaction window used for the fast setting.
- Given the response speed is set towards slow, when the ambient light changes significantly, then the
  brightness approaches the new target gradually over the long reaction window used for the slow
  setting.
- Given any response speed setting, when the ambient light is steady, then the brightness settles and
  does not oscillate around the target.
- Given the response speed has been changed, when the controller is already running, then the new
  setting takes effect for the next adjustment without requiring a restart.

## User Story 2 — No visible stepping

As a user, I want large brightness changes to be smooth, so that I do not see the screen jump or flash
when I walk from a dark room into daylight.

#### Acceptance Criteria

- Given a large difference between the current and the newly computed brightness, when the change is
  applied, then it is applied as a ramp through intermediate values rather than as a single jump.
- Given the ramp is in progress, when no individual step exceeds the configured maximum step size,
  then the transition is not perceived as a flash.
- Given the target value changes again while a ramp is in progress, when the new target is received,
  then the ramp retargets from the current value and does not overshoot the new target.

## User Story 3 — Filtering out brief light changes

As a user, I want brief changes in ambient light to be ignored, so that passing shadows, a hand
movement, or a quick cloud do not make my brightness wobble.

#### Acceptance Criteria

- Given a change in ambient light lasts less than the debounce window, when the light returns to its
  previous level, then no brightness change is applied at all.
- Given a change in ambient light is sustained for longer than the debounce window, when the window
  elapses, then the adjustment begins.
- Given a sequence of small, alternating readings around a stable average, when they are processed,
  then they are treated as no change rather than as repeated small adjustments.

## User Story 4 — The setting survives restarts

As a user, I want my response speed to persist, so that I do not have to reconfigure the feel of the
app every time it restarts.

#### Acceptance Criteria

- Given I have changed the response speed, when the app process is restarted or the device is
  rebooted, then the setting is still in effect when the controller resumes.
- Given I have never changed the response speed, when the controller runs for the first time, then a
  sensible middle-of-the-range default is used.

## Out of Scope

- The manual offset on the brightness value (that is F3).
- The shape of the lux-to-brightness curve (that is F2).
- Per-app or per-scenario response profiles.
- Adaptive/learned response behaviour based on user behaviour history.

## Resolved Decisions

- A **single** "response speed" control is used, with a sensible middle-of-the-range default; there are
  no separate controls for reaction window and smoothing.
- The control lives on the app's dedicated settings screen, together with the offset.
- The concrete default value and the fast/slow window durations are fixed in the technical design.

## Technical Design

### Architecture & Components

Same three modules as F1/F2 (`:core` pure Kotlin, `:infrastructure` Android adapters, `:app`
composition root; package root `com.ambientbrightness`). F4 consumes F1 only via the injected
`ReadingStabiliser` (F1's designated extension point) and F2 only via `BrightnessTransform` — no new
pipeline, no F1/F2 signature change. Components to add (all `:core` unless noted):

- `SettingsStore` (interface) + `SettingsKey<T>`/`SettingsKeys` (value objects) — typed, reactive,
  persistent key/value store; the **shared** store F3 (offset) and F7 adopt. `SettingsKeys` holds
  `RESPONSE_SPEED_PERCENT` and is where F3 adds its offset key. `:core` holds the contract only; both
  implementations live in `:infrastructure` — `InMemorySettingsStore` (Dummy) and
  `AndroidDataStoreSettingsStore` (persistent).
- `ResponseSpeed` (value object) — `percent: Int` in `0..100`; `0` = fastest, `100` = slowest,
  `DEFAULT = 50`, out-of-range input clamped.
- `ResponseProfile` (value object) + `ResponseProfileResolver` — derived timings
  (`reactionWindowMillis`, `debounceWindowMillis`, `maxStepFraction`) and the pure
  `ResponseSpeed → ResponseProfile` mapping.
- `ResponseTuningService` (boundary) — the reactive surface F7 observes and writes;
  `responseSpeed: StateFlow<ResponseSpeed>` + `suspend fun setResponseSpeed(speed)`.
- `UserConfigurableStabiliser` (implements F1 `ReadingStabiliser`) — **replaces** F1's
  `OutlierClampingStabiliser` as the injected instance by *decorating* it (composition, not a copy).
- `ResponsiveBrightnessRamp` (implements F2 `BrightnessTransform`) — the ramp of applied brightness;
  sole owner of step size and retargeting.

Fixed constants (owned here): `ResponseSpeed.DEFAULT = 50` (middle of the range);
`reactionWindowMillis` linear from `300 ms` (fast) to `3000 ms` (slow), default `1650 ms`;
`debounceWindowMillis` from `150 ms` to `600 ms` off the same control, default `375 ms`;
`maxStepFraction = 0.15` of the brightness span (anti-flash cap per invocation).

Split of responsibility (explicit): F1's `OutlierClampingStabiliser` keeps its fixed, per-sample, stateless duties (range
clamp, single-outlier rejection, `0 lux` validity) and is **not** made configurable; F4 adds time-domain behaviour on top
(debounce, slew smoothing). Outlier rejection is instantaneous, response tuning is time-based — they never overlap.

### Data Flow

1. `AmbientLightUseCase` (F1) throttles a raw event, then calls the injected
   `UserConfigurableStabiliser.stabilise(sample)`, which delegates to F1's `OutlierClampingStabiliser`
   first (unchanged), then applies the debounce gate (hold a change until sustained for
   `debounceWindowMillis`) plus slew/EMA smoothing from the *current* `ResponseProfile`.
2. F2 `BrightnessControllerUseCase`: `DefaultBrightnessMappingCurve` → `BrightnessTransform` hooks.
3. `ResponsiveBrightnessRamp.apply(fraction, context)` steps from the currently *applied* fraction
   toward the new target: `allowedStep = clamp(rate × elapsedMillis, 1 span-unit, maxStepFraction ×
   span)` with `rate = span / reactionWindowMillis`, `elapsedMillis` measured on the injected `Clock`
   since the previous invocation. One intermediate value per pipeline invocation — no extra ticker.
4. F2 unchanged afterwards: scale by `BrightnessRange`, clamp to `[max(1, min), max]`,
   `HysteresisGate`, `MinimumStepDeadBand`, `SystemBrightnessClient.write`.

Ramp position relative to F2's dead-band and clamping (concrete): the ramp runs **before** F2's clamp and dead-band, so
every intermediate value is independently clamped and still passes the dead-band. The dead-band minimum step must be
**strictly smaller** than one ramp step (configure ≤ 1 device unit) so it cannot swallow ramp progress.

### Key Interfaces & Classes

```kotlin
interface SettingsStore {                              // :core — shared (F3/F4/F7)
    fun <T> observe(key: SettingsKey<T>): Flow<T>
    suspend fun <T> set(key: SettingsKey<T>, value: T)
}
interface ResponseTuningService {                      // :core — F4 boundary observed by F7
    val responseSpeed: StateFlow<ResponseSpeed>
    suspend fun setResponseSpeed(speed: ResponseSpeed)
}
// Reused as-is (not redefined): ReadingStabiliser.stabilise(sample: LuxSample): LuxSample?  (F1)
// and BrightnessTransform.apply(fraction: Float, context: BrightnessContext): Float         (F2)
```

- `UserConfigurableStabiliser` re-reads the `ResponseProfile` from a `StateFlow<ResponseSpeed>` on
  every call, so a changed setting applies to the next adjustment without re-injection or restart
  (AC1.4). `ResponsiveBrightnessRamp` holds state (`currentFraction`, `targetFraction`,
  `lastCallMillis`) and is the **only** component that knows about ramping; F2's dead-band stays
  size-independent of F4. One injected `Clock` (`nowMillis()`) serves both — never
  `System.currentTimeMillis()` directly.

### State & Persistence

Mechanism: **AndroidX DataStore (Preferences)** over a single app-private file. Chosen over `SharedPreferences` (no
reactive `Flow`, main-thread I/O, no transactional writes) and over database/Room (overkill for scalar settings). Being
app-private storage it survives process death and device reboot by construction, satisfying AC4.1 without any receiver.

- One store instance, file `settings_store.preferences_pb`, app-scoped in `:app`; key
  `response_speed_percent: Int`. F3's offset uses the **same** `SettingsStore`/file with its own key —
  F4 defines the component so F3 and F7 can adopt it unchanged.
- `ResponseTuningService` seeds `responseSpeed` from `SettingsStore.observe(RESPONSE_SPEED_PERCENT)`
  (default `50` when absent → AC4.2), persists on `setResponseSpeed`, and exposes
  `responseSpeed: StateFlow<ResponseSpeed>` as F7's reactive state; F3 should mirror this for offset.

### Android Platform Specifics

- DataStore needs a `Context`, so only `:infrastructure` (`AndroidDataStoreSettingsStore`) and `:app`
  see it — `:core` stays Android-free. Writes are `suspend` on `Dispatchers.IO`; the UI-facing
  `StateFlow` is fed from the store's flow, so F7 never touches I/O.
- No new permission and no `BOOT_COMPLETED` receiver (persistence is storage-based);
  `Settings.System.SCREEN_BRIGHTNESS` writes remain entirely F2's — F4 writes no system setting.

### Edge Cases & Failure Handling

- Corrupt/absent/out-of-range stored value → clamped to `0..100`, falling back to `DEFAULT`; never
  crashes (AC4.2).
- Setting changed mid-ramp → retarget from the current value, new `reactionWindowMillis` effective
  immediately (AC2.3, AC1.4). Target reversal recomputes the step bound and cannot overshoot, because
  the ramp never moves past the target in one step.
- Long gap between invocations (screen off, throttling) → `elapsedMillis` grows but the per-invocation
  step is capped at `maxStepFraction`, so no visible jump on resume. A change shorter than
  `debounceWindowMillis` is suppressed entirely (AC3.1); small alternating readings around a stable
  average count as no change (AC3.3).
- Steady target → smoothing removes residual jitter while F2's `HysteresisGate` still guards curve
  breakpoints; both are idempotent at steady state, so no double damping (AC1.3). DataStore I/O
  failure → keep the in-memory `StateFlow` value, log, surface nothing fatal.

### Test Strategy

All `:core`, pure Kotlin, fake `Clock` + a `:core` test-double settings store (F4's
`InMemorySettingsStore` is a `:infrastructure` implementation, which `:core` cannot depend on), no
Android:
`ResponseProfileResolver` (default, both extremes, monotonicity); `ResponseTuningService` (default
when absent, persistence round-trip, live emission to F7); `UserConfigurableStabiliser` (suppression
below / start above the debounce window, steady-state no-oscillation, live profile change);
`ResponsiveBrightnessRamp` (no single jump, no overshoot on retarget, monotone approach, cap on long
gap). `:infrastructure` gets one instrumented test proving a value written by one
`AndroidDataStoreSettingsStore` is read back by a **fresh** instance (simulated restart, AC4.1);
`:app` gets one virtual-time integration test through fake sample → stabiliser → F2 pipeline → ramp →
fake `SystemBrightnessClient`, asserting intermediate write count and monotonicity.

### Implementation Tasks

- [x] **T1 — Shared persistent settings store**: add `SettingsStore` + `SettingsKey<T>`/`SettingsKeys`
  in `:core` (contract and value objects only); implement `InMemorySettingsStore` (Dummy) and
  `AndroidDataStoreSettingsStore` (`:infrastructure`, Preferences DataStore on `Dispatchers.IO`);
  register one app-scoped instance in `:app`'s composition root. *Files:
  `:core/.../core/SettingsStore/**` (contract only),
  `:infrastructure/.../infrastructure/SettingsStore/**` (both implementations),
  `:app/src/main/kotlin/com/ambientbrightness/app/**`.* *Verify: unit test for round-trip + reactive
  emission on `InMemorySettingsStore`; instrumented test that a value written by one store instance
  is read by a fresh instance.*
- [x] **T2 — Response speed model, profile mapping, boundary service**: add `ResponseSpeed` (clamp,
  `DEFAULT = 50`), `ResponseProfile`, `ResponseProfileResolver` (fixed constants above) and the
  `ResponseTuningService` boundary with its `DefaultResponseTuningService(store, scope)`
  implementation, seeded from `SettingsStore.observe(RESPONSE_SPEED_PERCENT)` and persisting on
  `setResponseSpeed`, exposing `responseSpeed: StateFlow<ResponseSpeed>` for F7. The constructor is
  `(store: SettingsStore, scope: CoroutineScope)` — no `Clock` (see the T2 design resolution below).
  *Files: `:core/src/main/kotlin/com/ambientbrightness/core/ResponseTuning/**`,
  `:core/src/test/kotlin/com/ambientbrightness/core/testdoubles/FakeSettingsStore.kt`.* *Verify: unit
  tests for default-when-absent (AC4.2), clamped out-of-range values, derivation at `0/50/100`,
  reactive update after `setResponseSpeed` (AC1.4).*
- [x] **T3 — User-configurable stabiliser on F1's extension point**: implement
  `UserConfigurableStabiliser : ReadingStabiliser`: delegate first to F1's `OutlierClampingStabiliser`
  (unchanged), then apply the debounce gate and time-based smoothing from the live `ResponseProfile`
  via the injected `Clock`; inject it as F1's `AmbientLightUseCase` stabiliser in `:app`. Do not
  modify F1's outlier logic or interfaces. *Files:
  `:core/.../core/UserConfigurableStabiliser/**`, `:app/.../app/**`.* *Verify: fake-clock unit tests
  for suppression below the debounce window (AC3.1), start above it (AC3.2), no reaction to small
  alternating readings (AC3.3), steady-state settling (AC1.3), live profile change without restart
  (AC1.4).*
- [ ] **T4 — Responsive brightness ramp on F2's transform hook**: implement
  `ResponsiveBrightnessRamp : BrightnessTransform` with `currentFraction`/`targetFraction`/
  `lastCallMillis` state, the time-based step bound (`rate × elapsed`, capped at `maxStepFraction`)
  and mid-ramp retargeting; register it in F2's transform hook list in `:app`, with F2's
  `MinimumStepDeadBand` ≤ 1 device unit so it cannot suppress ramp progress. *Files:
  `:core/.../core/ResponsiveBrightnessRamp/**`, `:app/.../app/**`.* *Verify: fake-clock unit tests
  that a full-span change becomes several bounded steps not one jump (AC2.1, AC2.2) and that a
  mid-ramp retarget never overshoots and stays monotone (AC2.3).*
- [ ] **T5 — Composition, wiring, end-to-end verification**: wire `SettingsStore`,
  `ResponseTuningService`, `UserConfigurableStabiliser` (into F1) and `ResponsiveBrightnessRamp`
  (into F2) in `:app`; expose `ResponseTuningService` for F7; add the virtual-time integration test.
  *Files: `:app/src/main/kotlin/com/ambientbrightness/app/**`,
  `:app/src/test/kotlin/com/ambientbrightness/app/**`.* *Verify: integration test asserting write
  count, monotone intermediate values and correct use of a persisted speed after a simulated restart
  (AC1.1, AC2.2, AC3.2, AC4.1).*

## Suggested Implementation Order

`T1 → T2 → T3 → T4 → T5`. T1 first: the persistence mechanism is shared with F3/F7, so the key
contract must be frozen before anything depends on it. T2 needs only T1's interfaces (its `:core`
tests use a `:core` test-double store, because `:core` cannot depend on `:infrastructure`, where both
store implementations live). T3 and T4 are mutually independent — T3 plugs into F1, T4 into F2 — and can
run in parallel once T2's `ResponseProfile` exists. T5 is last: the only task touching both pipelines.

## Implementation Notes

### T1 — Shared persistent settings store

*Done: implementation commit `399d0286fd68b7b85995061e5a69985dce6791a6`.*

- Built the shared key contract in `:core`: `SettingsStore` (`observe`/`set` exactly as designed),
  `SettingsKey<T>` as a sealed hierarchy whose subtype is the Android-free type tag (`IntKey`,
  `LongKey`, `FloatKey`, `BooleanKey`, `StringKey`) and `SettingsKeys` as the single registry, holding
  `RESPONSE_SPEED_PERCENT` with the stored name `response_speed_percent` and the default `50`. A key
  carries its own absent-case value, so `observe` can stay non-null (`T`), which is what F3's
  "default when absent" needs. `:core` imports no `android.`/`androidx.datastore` type.
- Built both implementations in `:infrastructure`. `InMemorySettingsStore` is reactive and
  thread-safe (`MutableStateFlow<Map<String, Any?>>`, writes through `update`), serves the Dummy
  composition and tests. `AndroidDataStoreSettingsStore` maps each sealed subtype onto the matching
  `Preferences` accessor, suspends writes onto the injected `Dispatchers.IO`, and emits empty
  preferences (i.e. the key defaults) on an `IOException`; reads observe `DataStore.data`, so a
  collector sees a `set` from any instance immediately. It takes its `DataStore<Preferences>` and
  dispatcher through the constructor — no singleton — and offers
  `create(context, scope, fileName = "settings_store.preferences_pb", ioDispatcher)` for the
  composition root.
- `:app` registers exactly one app-scoped store over `settings_store.preferences_pb`
  (`AndroidDataStoreSettingsStore.create(context = this, scope = appScope)`), and the component's new
  `settingsStore: SettingsStore` parameter defaults to the in-memory store. F3/F4/F7 consume the
  contract unchanged; `SettingsKeys` is where F3 adds `BRIGHTNESS_OFFSET_PERCENT`.
- Files added:
  `core/src/main/kotlin/com/ambientbrightness/core/SettingsStore/SettingsStore.kt`,
  `.../core/SettingsStore/SettingsKey.kt`,
  `.../core/SettingsStore/SettingsKeys.kt`,
  `core/src/test/kotlin/com/ambientbrightness/core/SettingsStore/SettingsKeysTest.kt`,
  `infrastructure/src/main/kotlin/com/ambientbrightness/infrastructure/SettingsStore/SettingsStore/InMemory/InMemorySettingsStore.kt`,
  `.../infrastructure/SettingsStore/SettingsStore/Android/AndroidDataStoreSettingsStore.kt`,
  `infrastructure/src/test/kotlin/com/ambientbrightness/infrastructure/InMemorySettingsStoreTest.kt`,
  `infrastructure/src/androidTest/kotlin/com/ambientbrightness/infrastructure/AndroidDataStoreSettingsStoreTest.kt`.
  Files changed: `gradle/libs.versions.toml` (`androidxDatastore = "1.2.1"` +
  `androidx-datastore-preferences`), `infrastructure/build.gradle.kts`
  (`implementation(libs.androidx.datastore.preferences)`, `testImplementation(libs.kotlinx.coroutines.test)`),
  `app/.../app/Composition/AmbientBrightnessComponent.kt` (`settingsStore` parameter) and
  `.../app/Composition/AmbientBrightnessApplication.kt` (the one real store).
- Deviation 1 (corrected after the first review): `InMemorySettingsStore` lives in
  `:infrastructure` (`SettingsStore/SettingsStore/InMemory/`), **not** in `:core`. Core holds
  entities, value objects and interfaces only; every implementation of an interface — including the
  Dummy/in-memory one — belongs to Infrastructure. Its test therefore lives in `:infrastructure`'s
  JVM test source set (JUnit 4 + `kotlinx-coroutines-test`, added as `testImplementation`), because
  `:core` cannot depend on `:infrastructure`. The original task text and this document said
  "(`:core`, tests/dummy)"; both were wrong and are corrected here and in
  `feature-manual-brightness-offset.md`. A `:core` test that needs a store uses a `:core`
  test-source double, like the existing `FakeClock` — T1 has no such `:core` test, so none was added
  yet; the first store-dependent `:core` test (F3 T4 / F4 T2) adds it.
- Deviation 2: `SettingsKey` is a sealed class hierarchy rather than an enum-tagged holder, and not a
  `data class`. The sealed subtype is the type tag that carries `T` into the store, which keeps
  `:core` free of storage types; identity (not value) equality prevents two features' keys from
  collapsing into one entry.
- Deviation 3: the instrumented test lives in `:infrastructure`'s `androidTest`, not `:app`'s —
  the adapter under test is `:infrastructure`, the module already resolves DataStore, and
  `InstrumentationRegistry.targetContext` supplies the `Context`. It writes to a unique file name per
  run and cancels the first store's scope before opening the fresh instance, since DataStore refuses
  two active instances on one file; it asserts absent ⇒ default, then a write read back by a fresh
  instance (simulated restart).
- Deviation 4: the store tolerates a corrupt file by reading defaults
  (`ReplaceFileCorruptionHandler { emptyPreferences() }`) instead of failing; a missing/corrupt key
  yields the key's own default, which is AC4.2 and F3's AC4.3.
- Verification (from the repo root, all with `--no-daemon --console=plain`):
  `.\gradlew.bat :core:test` → BUILD SUCCESSFUL, 19 test classes / 104 tests, 0 failures (102 before
  T1: +2 for `SettingsKeysTest`; the in-memory tests moved to `:infrastructure`);
  `.\gradlew.bat :infrastructure:test` → BUILD SUCCESSFUL, 8 test classes / 32 tests, 0 failures
  (28 before T1: +4 for `InMemorySettingsStoreTest`); `.\gradlew.bat :app:assembleDebug` → BUILD
  SUCCESSFUL; `.\gradlew.bat :infrastructure:assembleDebugAndroidTest` → BUILD SUCCESSFUL. The
  folder/package guards in both modules stay green.
- Not verified on device: `AndroidDataStoreSettingsStoreTest` (the DataStore round-trip across a
  fresh instance, AC4.1) is compiled only — no emulator or device is attached in this environment.

### T2 — Response speed model, profile mapping, boundary service

*Done: implementation commit `c286a396c9eca953e0edbfac53b831cbc3fe2b4b`.*

- `ResponseSpeed` is a data class with a private constructor and the `of(percent)` factory: the
  factory clamps into `FASTEST_PERCENT..SLOWEST_PERCENT` (`0..100`, both bounds public because the
  resolver interpolates over the range), and `DEFAULT = 50` stays the absent-case value. The private
  constructor keeps an unclamped `percent` unrepresentable; the class carries
  `@ConsistentCopyVisibility` so the generated `copy()` remains private (without it the compiler
  warns about the Kotlin 2.5 data-class visibility change).
- `ResponseProfile` is a plain data holder with exactly the three public properties
  (`reactionWindowMillis`, `debounceWindowMillis`, `maxStepFraction`) and no behaviour, as designed.
- `ResponseProfileResolver` is one concrete class in its own folder — no interface/`Default…` pair,
  because the mapping is pure, stateless and side-effect free, so there is nothing to substitute. It
  interpolates both windows linearly off `percent` and exposes the fixed constants publicly
  (`FASTEST_REACTION_WINDOW_MILLIS = 300`, `SLOWEST_REACTION_WINDOW_MILLIS = 3000`,
  `FASTEST_DEBOUNCE_WINDOW_MILLIS = 150`, `SLOWEST_DEBOUNCE_WINDOW_MILLIS = 600`,
  `MAX_STEP_FRACTION = 0.15f`) so T3/T4 reuse them instead of re-deriving them.
- `ResponseTuningService` (boundary) and `DefaultResponseTuningService` both live in `:core`,
  following the in-module precedent for platform-independent business logic (`AmbientLightService` +
  `DefaultAmbientLightService`, `HysteresisGate` + `DefaultHysteresisGate`): the implementation only
  depends on the injected `SettingsStore`, so it needs no Android type and `:infrastructure` gets
  nothing. The boundary stays exactly `responseSpeed: StateFlow<ResponseSpeed>` +
  `suspend fun setResponseSpeed(speed)`.
- Deviation (also recorded in the design resolution below): the implementation is
  `DefaultResponseTuningService(store: SettingsStore, scope: CoroutineScope)` and takes **no
  `Clock`**. A `StateFlow` needs a producer and `:app` must stay the only scope owner, so the scope
  is constructor-injected, mirroring the existing `AndroidDataStoreSettingsStore.create(context,
  scope)` and `BrightnessControllerUseCase(..., scope)`; the `Clock` was dropped because no T2 logic
  reads the wall clock (T3/T4 inject their own). Seeding is
  `store.observe(RESPONSE_SPEED_PERCENT).map { ResponseSpeed.of(it) }.stateIn(scope,
  SharingStarted.Eagerly, ResponseSpeed.of(DEFAULT))`, and `setResponseSpeed` writes to the store
  only — the store's emission updates the `StateFlow`, so there is no second (optimistic) source of
  truth.
- Deviation: `:core`'s first store-dependent tests add the test-source double `FakeSettingsStore`
  next to `FakeClock`, as T1 announced. It seeds through its constructor, stays reactive like both
  real stores and records `writes`, so a test can assert the persisted value and the value read back
  through `observe`. No production in-memory store was added to `:core`.
- Files added: `core/src/main/kotlin/com/ambientbrightness/core/ResponseTuning/ResponseSpeed.kt`,
  `.../core/ResponseTuning/ResponseProfile.kt`,
  `.../core/ResponseTuning/ResponseProfileResolver/ResponseProfileResolver.kt`,
  `.../core/ResponseTuning/ResponseTuningService/ResponseTuningService.kt`,
  `.../core/ResponseTuning/ResponseTuningService/DefaultResponseTuningService.kt`,
  `core/src/test/kotlin/com/ambientbrightness/core/testdoubles/FakeSettingsStore.kt`,
  `core/src/test/kotlin/com/ambientbrightness/core/ResponseTuning/ResponseSpeedTest.kt`,
  `.../core/ResponseTuning/ResponseProfileResolver/ResponseProfileResolverTest.kt`,
  `.../core/ResponseTuning/ResponseTuningService/DefaultResponseTuningServiceTest.kt`.
  Files changed: none — F1, F2, `SettingsStore`, the key names and the key defaults are untouched
  (`SettingsKeys.RESPONSE_SPEED_PERCENT` keeps T1's literal `50`), and neither `:infrastructure` nor
  `:app` is affected.
- Tests cover default when the key is absent (AC4.2), a stored value clamped into the range, in-range
  pass-through, the derivation at `0/50/100` (300/1650/3000 ms and 150/375/600 ms, `maxStepFraction`
  0.15 at every speed), monotonicity over all 101 percents, the live emission to a running collector
  after `setResponseSpeed` (AC1.4), a value persisted before construction seeding the flow and
  reaching a late collector, and the clamped write round-tripping through the store.
- Verification (from the repo root): `.\gradlew.bat :core:test --no-daemon --console=plain` →
  BUILD SUCCESSFUL, 22 test classes / 118 tests, 0 failures, 0 errors, no compiler warnings
  (19 classes / 104 tests before T2: +3 classes, +14 tests). `ArchitectureStructureTest`
  (folder/package + Android-import guard) is green, so the new files declare
  `com.ambientbrightness.core.ResponseTuning…` and `:core` imports no Android type. No other module
  was built because no other module changed.

### T2 — Design resolution (design only)

*Design only — the two under-specified points in the T2 text were resolved before implementation; no
acceptance criterion changes and the boundary surface does not grow.*

- **Gap 1 — who owns the scope feeding `responseSpeed`.** Decision: **option (a)**, a constructor
  scope. The boundary keeps exactly the two designed members:

  ```kotlin
  interface ResponseTuningService {
      val responseSpeed: StateFlow<ResponseSpeed>
      suspend fun setResponseSpeed(speed: ResponseSpeed)
  }
  ```

  The concrete implementation is
  `class DefaultResponseTuningService(store: SettingsStore, scope: CoroutineScope) :
  ResponseTuningService` — collection starts at construction, and the class creates no scope of its
  own, so `:app` (T5) remains the only scope owner and passes the existing `appScope`. This mirrors
  code already in the repo: `AndroidDataStoreSettingsStore.create(context, scope)` in
  `:infrastructure` and `BrightnessControllerUseCase(..., private val scope: CoroutineScope)` in
  `:core`. Seeding is a single declaration
  `store.observe(SettingsKeys.RESPONSE_SPEED_PERCENT).map(ResponseSpeed::of)
  .stateIn(scope, SharingStarted.Eagerly, ResponseSpeed.DEFAULT)`, so a value persisted before
  construction is picked up (both store implementations emit immediately), `responseSpeed` is always
  a valid `StateFlow`, and a steady default is exposed when the key is absent (AC4.2).
  `setResponseSpeed` does only `store.set(SettingsKeys.RESPONSE_SPEED_PERCENT, speed.percent)`; the
  new value reaches F7 through the store's own emission, so there is no second (optimistic) source of
  truth and the live update of AC1.4 falls out of the store contract.
  - *Rejected (b) `start(scope)`/`stop()`* (the `AmbientLightService` / `AutomaticBrightnessController`
    precedent): those own device resources that must be released deterministically, whereas this
    service only mirrors an app-scoped store; a lifecycle hook would force F7 to know when to
    `start`, keep the surface larger than designed, and expose a stuck default until it is called.
  - *Rejected (c) a cold `Flow`/`SharingStarted` boundary*: F7 is specified to receive a
    `StateFlow`, which `:app` must not have to build itself.
- **Gap 2 — the `Clock`.** Decision: **omit it**; `DefaultResponseTuningService` takes no `Clock`.
  Reading and writing a percent is not time-based, so a `Clock` parameter would be dead surface; T3
  and T4 still inject the `Clock` they need themselves. The design's "one injected `Clock` serves
  both — never `System.currentTimeMillis()` directly" invariant is about the two time-based pipeline
  components (`UserConfigurableStabiliser`, `ResponsiveBrightnessRamp`), which take the same `Clock`
  instance from `:app`; the service is not one of them. Recorded as a deliberate deviation from the
  earlier T2 wording.

Consequences for the developer: the service is one small class, so no `Task/` split is warranted. The
T2 `:core` unit test is the first store-dependent `:core` test (noted in T1) and therefore adds the
`:core` test-source double `FakeSettingsStore` under `core/src/test/.../core/testdoubles/`; because
the `stateIn` upstream never completes, the test must collect under `backgroundScope` (advancing with
`runCurrent()`/`advanceUntilIdle()`) rather than let `runTest` await it.

### T3 — User-configurable stabiliser on F1's extension point

*Done: implementation commit `587a727573ef1b55a9421e8433ece7c05ad704ab`.*

- Built `UserConfigurableStabiliser` as a pure decorator over the injected `ReadingStabiliser`
  (`OutlierClampingStabiliser` in production, taken as a constructor dependency): it calls the
  delegate first and returns `null` unchanged whenever the delegate rejects a sample, so F1's range
  clamp, single-outlier rejection and `0 lux` validity are untouched, non-configurable and never
  bypassed. F1's outlier logic, its interface and its tests are unmodified.
- Added exactly the two time-domain rules on top. **Debounce gate (AC3.1, AC3.2):** a reading more
  than the noise band away from the level in effect becomes the *candidate* and starts its window at
  the injected `Clock`'s time; only once `now - candidateSinceMillis >= debounceWindowMillis` (the
  window of the live profile) does the value move, and any reading back within the noise band of the
  level in effect clears the candidate — so a transient lights up nothing. **Smoothing (AC1.1,
  AC1.3):** the move is an EMA step of `(target - current) × elapsedMillis / reactionWindowMillis`
  (fraction clamped to `0..1`), so a fast setting reaches the target in one step once the window has
  elapsed (elapsed ≥ window ⇒ fraction 1) while a slow setting approaches it gradually; the value
  snaps exactly onto the target once the remaining distance is below the noise band, which is what
  makes a steady level settle without oscillating.
- Both the profile and the speed are re-read on **every** call (`resolver.resolve(responseSpeed.value)`
  on the injected `StateFlow<ResponseSpeed>`), which is what delivers AC1.4 with no re-injection and no
  restart; the class holds no coroutine scope and starts no collection of its own.
- Noise band: `max(MINIMUM_NOISE_BAND_LUX = 2 lx, NOISE_BAND_FRACTION = 0.05f × level in effect)`,
  owned by this class and the one place "small" is defined. Readings inside it are treated as no
  change at all (AC3.3). Robustness: a non-finite or negative lux from the delegate is dropped
  (`null`) instead of emitted, and the class never throws.
- `:app` wiring: `AmbientBrightnessComponent` now builds `DefaultResponseTuningService(store =
  settingsStore, scope = controllerScope)` and exposes it as `val responseTuningService:
  ResponseTuningService` (F7's boundary, unchanged surface), and passes
  `UserConfigurableStabiliser(delegate = OutlierClampingStabiliser(maxRangeLux = sensorRangeLux),
  responseSpeed = responseTuningService.responseSpeed, clock = clock)` into F1's
  `AmbientLightUseCase`.
- Files added: `core/src/main/kotlin/com/ambientbrightness/core/UserConfigurableStabiliser/UserConfigurableStabiliser.kt`,
  `core/src/test/kotlin/com/ambientbrightness/core/UserConfigurableStabiliser/UserConfigurableStabiliserTest.kt`.
  Files changed: `app/src/main/kotlin/com/ambientbrightness/app/Composition/AmbientBrightnessComponent.kt`.
- Deviation 1: the `:app` component gained a `clock: Clock = SystemClock` constructor parameter (it
  previously hardcoded `SystemClock`) and hands that same instance to `AmbientLightUseCase`, so the
  spec's "one injected `Clock` serves both — never `System.currentTimeMillis()` directly" now holds
  for the whole chain. Without it T5's virtual-time integration test cannot be deterministic. The
  default keeps every existing caller (Application, `AmbientBrightnessComponentTest`) source-
  compatible.
- Deviation 2: the design fixed the profile constants but not the smoothing *formula* (it says
  "slew/EMA smoothing"). EMA was chosen over a lux-domain slew because the lux span is not known to
  the stabiliser (`maxRangeLux` belongs to the delegate/profile factory), and because an EMA makes
  "reaches the target within the reaction window" (AC1.1) literally true: once `elapsedMillis ≥
  reactionWindowMillis` the fraction saturates at 1 and the value snaps to the target. The two noise
  band constants above are new and owned here.
- Usage note for T5/F7: because the folder `ResponseTuning/ResponseTuningService/` shares its name
  with the `ResponseTuningService` classifier, the boundary is imported in its `…ResponseTuningService.ResponseTuningService`
  form — the same shape F1's `…AmbientLightSensing.AmbientLightService.AmbientLightService` already
  uses (`import …ResponseTuning.ResponseTuningService.DefaultResponseTuningService` for the
  implementation is unaffected).
- Verification (from the repo root): `.\gradlew.bat :core:test --no-daemon --console=plain` →
  BUILD SUCCESSFUL, 23 test classes / 128 tests, 0 failures, 0 errors (22 / 118 before T3: +1 class,
  +10 tests, all in the new `UserConfigurableStabiliserTest`); `.\gradlew.bat :app:assembleDebug
  --no-daemon --console=plain` → BUILD SUCCESSFUL. `ArchitectureStructureTest` (folder/package +
  Android-import guard) green.
- Test mapping: AC1.1 → fast reaches in one step / slow only gradually; AC3.1 → change shorter than
  the debounce window is not applied at all; AC3.2 → sustained change starts when the window
  elapses (asserted at 160.6 lx, i.e. 500 ms of the 1650 ms window); AC3.3 → small alternating
  readings (100/102/98/101/99/103/97) emit the stable 100 throughout; AC1.3 → steady level settles on
  its target and does not oscillate; AC1.4 → changing the `StateFlow` mid-run applies on the next
  call; plus delegate-rejection, invalid-value and monotone-approach cases.
