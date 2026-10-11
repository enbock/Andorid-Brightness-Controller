# F2 — Automatic Brightness Control

| Field | Value |
|---|---|
| **Epic** | [EPIC: Ambient Brightness Controller](./EPIC-ambient-brightness-controller.md) |
| **Status** | Draft — awaiting review |
| **Depends on** | F1 (Ambient Light Sensing) |
| **Depended on by** | F3 (Manual Brightness Offset), F4 (Response Tuning), F5 (Background Service) |

## Overview

This feature turns the measured ambient light into an actual screen brightness value and applies it
to the system. It is the core of the app: the part that the user experiences as "the screen is
suddenly readable". It owns the response curve, the safety bounds, and the write to
`Settings.System.SCREEN_BRIGHTNESS`.

## User Story 1 — Automatic adjustment without user interaction

As a user, I want the screen brightness to be adjusted automatically to match the ambient light, so
that the screen stays readable without me touching the brightness slider.

#### Acceptance Criteria

- Given the controller is running and the `WRITE_SETTINGS` permission is granted, when the measured
  ambient light changes, then a new brightness value is computed and applied without any user
  interaction.
- Given a newly computed brightness value differs from the currently applied value by less than the
  configured minimum step, when it is computed, then no system write is performed, so the app does
  not perform needless writes.
- Given the app applies a new brightness, when the write succeeds, then the applied value is
  reflected as the current value in the app's own status, so the UI never reports a stale value.
- Given the app is running, when brightness is applied, then only `Settings.System.SCREEN_BRIGHTNESS`
  is written, and no other system setting is modified.

## User Story 2 — Safe and valid brightness values

As a user, I want the app to never leave my screen unreadably dark or invalidly bright, so that I can
always trust it to keep the display usable.

#### Acceptance Criteria

- Given a computed brightness would be below the minimum allowed value, when it is applied, then it
  is clamped to that minimum and never to 0, so the screen can never be driven to full darkness.
- Given a computed brightness would exceed the device's maximum brightness, when it is applied, then
  it is clamped to the maximum supported by the device.
- Given the device reports its own brightness range, when values are applied, then they are mapped
  into that range rather than assuming a fixed 0–255 scale.
- Given the brightness write fails (for example because the permission was revoked while running),
  when the failure occurs, then the app surfaces an error state, stops attempting writes, and does
  not crash.

## User Story 3 — Predictable lux-to-brightness behaviour

As a user, I want the relationship between light and brightness to be predictable and free of
flickering, so that the screen does not jump between two brightness levels when the light sits at a
boundary.

#### Acceptance Criteria

- Given two ambient light values where one is higher than the other, when both are mapped, then the
  brightness for the higher value is never lower than the brightness for the lower value (the curve
  is monotonically non-decreasing).
- Given the ambient light hovers at a value where the curve changes slope, when the light oscillates
  slightly around that value, then hysteresis prevents the applied brightness from rapidly alternating
  between two levels.
- Given the ambient light is very low, when it is mapped, then the result is a low but still
  readable brightness rather than the minimum, so the screen does not become unnecessarily dark.

## User Story 4 — Coexistence with the device's built-in adaptive brightness

As a user, I want to know when the app conflicts with the phone's own automatic brightness, so that
I understand why the screen does not behave as expected and can resolve it.

#### Acceptance Criteria

- Given the device's built-in adaptive brightness is enabled, when the controller is started, then the
  app informs the user that both systems will compete and offers to switch the built-in adaptive
  brightness off.
- Given the user declines to switch it off, when the controller runs, then it still functions but the
  UI keeps a persistent hint that the built-in adaptive brightness may override the app's value.
- Given the built-in adaptive brightness is disabled, when the controller runs, then the app does not
  show the conflict hint.

## Out of Scope

- The user-facing offset on top of the computed value (that is F3).
- Smoothing, damping and ramp timing (that is F4).
- Per-app or per-scenario brightness profiles.
- Colour temperature, blue-light filtering, or contrast adjustment.
- Screen dimming overlays or any mechanism other than the system brightness setting.

## Resolved Decisions

- Stopping the controller restores the brightness value that was in effect before it was started (see
  F5).
- The app never turns the device's built-in adaptive brightness off by itself; it only explains the
  conflict and links to the system setting.

## Technical Design

### Architecture & Components

Same three modules as F1 (`:core` pure Kotlin, `:infrastructure` Android adapters, `:app`
composition root; package root `com.ambientbrightness`). F2 adds the brightness pipeline and
consumes F1 **only** through `AmbientLightService` (`availability`, `luxStream()`/`start()`,
`stop()`). `AmbientLightSensorClient` is never referenced here.

Components to add (all `:core` unless noted):

- `AutomaticBrightnessController` — boundary/service and single public entry point for F2
  (`start()`, `stop()`, `status: StateFlow<BrightnessStatus>`).
- `BrightnessControllerUseCase` — orchestrates sample → map → transform hooks → clamp → dead-band →
  write, and owns the running flag.
- `BrightnessMappingCurve` (interface) + `DefaultBrightnessMappingCurve` — monotonic lux→fraction.
- `BrightnessRange` (value object: `min`, `max`) + `BrightnessRangeResolver` — resolves the real
  device range.
- `BrightnessValue` (value object, integer in device units, invariant `>= 1`).
- `MinimumStepDeadBand` — suppresses writes when |new − applied| < min step.
- `HysteresisGate` — directional hysteresis against curve oscillation (US3).
- `SystemBrightnessClient` (interface) — read/write `Settings.System.SCREEN_BRIGHTNESS`.
- `DeviceBrightnessRangeClient` (interface) — reads `SCREEN_BRIGHTNESS_MAX` / power profile.
- `WriteSettingsPermissionChecker` (interface) — reports `WRITE_SETTINGS` grant state as a flow.
- `BuiltInAdaptiveBrightnessState` (value object) + `AdaptiveBrightnessConflictDetector` —
  observable conflict state for F6/F7 (D4); no UI here.
- `BrightnessTransform` (interface) — **extension point**: post-mapping, pre-clamp hooks so F3
  (offset) and F4 (smoothing/ramp) plug into the pipeline without touching it.
- `BrightnessSnapshot` — pre-controller brightness snapshot F5 restores on stop (D3).
- `BrightnessWriteResult` (sealed) — `Applied` / `Suppressed` / `Failed(reason)`.

`:infrastructure`: `AndroidSystemBrightnessClient`, `AndroidDeviceBrightnessRangeClient`,
`AndroidWriteSettingsPermissionChecker`, plus `Dummy*` variants for tests/development.
`:app`: `BrightnessStatusCollector` (thin, forwards status; no business logic).

### Data Flow

`AmbientLightService.luxStream()` → `BrightnessControllerUseCase`: on each `LuxSample`, if not
running or permission missing, skip. Otherwise `DefaultBrightnessMappingCurve` maps lux → fraction
`[0,1]`; `BrightnessTransform` hooks (empty in F2) may modify the fraction; `BrightnessRangeResolver`
+ `BrightnessRange` scale it to device units and clamp to `[max(1,min), max]`; `HysteresisGate`
smooths breakpoint oscillation; `MinimumStepDeadBand` compares against the last applied value and
drops writes below the step. A surviving value is written via `SystemBrightnessClient`; the result
updates `status` (`appliedBrightness`, `lastLux`, `writeState`). `AdaptiveBrightnessConflictDetector`
runs in parallel off the permission/system state and publishes `BuiltInAdaptiveBrightnessState`.

Interfaces (`:core`):

```kotlin
interface AutomaticBrightnessController {
    fun start()
    fun stop()
    val status: StateFlow<BrightnessStatus>
    val adaptiveBrightnessConflict: StateFlow<BuiltInAdaptiveBrightnessState>
}
interface BrightnessMappingCurve { fun toFraction(lux: Float): Float }
interface SystemBrightnessClient {
    fun readCurrent(): Int?
    fun write(value: BrightnessValue): BrightnessWriteResult
}
interface BrightnessTransform { fun apply(fraction: Float, context: BrightnessContext): Float }
```

### Key Interfaces & Classes

- **Curve (US3):** piecewise-linear in log10(lux) space (~1–100 000 lux) through fixed anchor
  points, computed to be monotonically non-decreasing; low-light endpoint returns a low-but-readable
  value (> 1), never the device minimum (AC3.3). Chosen over a lookup table (device-specific, hard to
  keep monotonic) and over a power function (harder to satisfy the "very low light" floor and to
  reason about at breakpoints).
- **Hysteresis (US3/AC3.2):** `HysteresisGate` keeps a dead-band in lux around the current anchor;
  the value only re-maps once the sample leaves the band by a configured margin, so oscillation at a
  slope change cannot alternate the applied value.
- **Range discovery (US2/AC2.3):** `BrightnessRangeResolver` prefers `Settings.System.SCREEN_BRIGHTNESS_MAX`,
  falls back to `PowerManager`/power-profile brightness, and to a 255 default only if both are
  unreadable; minimum is a configured floor (never 0).
- **Dead-band (US1/AC1.2):** `MinimumStepDeadBand` is the sole reason a computed value is not
  written; it is size-independent of F4's ramp timing.
- **Write failures (US2/AC2.4):** `SystemBrightnessClient.write` returns `Failed` (permission
  revoked, `SecurityException`, `IllegalArgumentException`); the use-case sets an error state, stops
  attempting writes, and never crashes.

### State & Persistence

In-memory only: `status`, `adaptiveBrightnessConflict`, last-applied value and the
`BrightnessSnapshot` are held by `AutomaticBrightnessController` (app-scoped). No database or files;
F2 does not persist the offset (F3) or response settings (F4). The snapshot exists solely so F5 can
restore the pre-controller value on stop (D3); F2 exposes it and does not act on stop itself.

### Android Platform Specifics

- Write path: `Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, v)` —
  the only setting modified (AC1.4); no overlay.
- `WRITE_SETTINGS` is a special permission: check with
  `Settings.System.canWrite(context)`; observe changes via a lifecycle-aware reload (also after
  returning from the system Settings screen).
- `SCREEN_BRIGHTNESS_MAX` is API-36-available; guard with a fallback chain as above.
- `Settings.System.SCREEN_BRIGHTNESS_MODE` is **read only** (D4) — reading it feeds the conflict
  state, never a write.
- Screen-off is already gated upstream in F1; F2 still ignores samples while stopped.

### Edge Cases & Failure Handling

- Permission revoked mid-run → first failed write flips to `Failed`, writes stop, state surfaced.
- Computed value < minimum → clamp to floor `>= 1`, never 0 (AC2.1); above max → clamp (AC2.2).
- Device range unreadable → fallback range, logged, no crash.
- `LuxSample` invalid/unavailable → skip; do not write.
- Rapid oscillation at a breakpoint → absorbed by `HysteresisGate` + dead-band.
- Built-in adaptive brightness on → conflict state `Enabled`, controller still runs (AC4.2); hint
  is a downstream concern (F6/F7).
- `stop()` → controller stops writing; restoration is deferred to F5 via `BrightnessSnapshot`.
- `start()`/`stop()` idempotent.

### Test Strategy

`:core` is pure Kotlin: `DefaultBrightnessMappingCurve` gets a property/monotonicity test (random
lux pairs never decrease) plus low-light floor and breakpoint cases; `HysteresisGate` and
`MinimumStepDeadBand` are unit-tested with a fixed `Clock`; `BrightnessControllerUseCase` is driven
by fake `AmbientLightService`, `SystemBrightnessClient`, `DeviceBrightnessRangeClient` and
`WriteSettingsPermissionChecker`, asserting clamp, dead-band suppression and failure handling
(AC1.2, AC2.1–2.4, AC3.1). `AdaptiveBrightnessConflictDetector` is unit-tested for the three AC4
states. `:infrastructure` adapters are covered by one instrumented test that writes and reads back a
value on a device/emulator with the permission granted; `Dummy*` adapters let `:app` run without
hardware. No Compose tests.

### Implementation Tasks

- [x] **T1 — Domain model & boundary interfaces**: add `BrightnessValue`, `BrightnessRange`,
  `BrightnessWriteResult`, `BrightnessStatus`, `BuiltInAdaptiveBrightnessState`, `BrightnessContext`
  and `BrightnessSnapshot`, plus the `AutomaticBrightnessController`, `BrightnessMappingCurve`,
  `SystemBrightnessClient`, `DeviceBrightnessRangeClient`, `WriteSettingsPermissionChecker`,
  `HysteresisGate` and `BrightnessTransform` interfaces in `:core`, Android-free.
  *Files: `:core/src/main/kotlin/com/ambientbrightness/core/**`.*
  *Verify: compiles with no Android dependency; VOs enforce `>= 1`; contract tests on fakes.*
- [ ] **T2 — Lux→brightness mapping curve and hysteresis**: implement `DefaultBrightnessMappingCurve`
  (log-lux piecewise anchors, monotonic, low-light floor) and `HysteresisGate`.
  *Files: `:core/src/main/kotlin/com/ambientbrightness/core/BrightnessMappingCurve/**`,
  `.../HysteresisGate/**`.*
  *Verify: monotonicity property test, low-light floor (AC3.3) and breakpoint oscillation (AC3.2).*
- [ ] **T3 — Device range resolution, scaling, clamping and dead-band**: implement
  `BrightnessRangeResolver` (fallback chain) and the clamp + `MinimumStepDeadBand` logic inside
  `BrightnessControllerUseCase`'s pure part.
  *Files: `:core/src/main/kotlin/com/ambientbrightness/core/BrightnessRangeResolver/**`,
  `.../BrightnessControllerUseCase/**`.*
  *Verify: unit tests for AC2.2/AC2.3, never-0 (AC2.1) and suppression below min step (AC1.2).*
- [ ] **T4 — System brightness writer, permission checker and failure handling**: implement
  `AndroidSystemBrightnessClient`, `AndroidDeviceBrightnessRangeClient`,
  `AndroidWriteSettingsPermissionChecker` and the `Dummy*` variants in `:infrastructure`; wire the
  `Failed`/error path so writes stop and the app does not crash.
  *Files: `:infrastructure/src/main/kotlin/com/ambientbrightness/infrastructure/**`.*
  *Verify: instrumented write-then-read-back; unit-tested failure path (AC2.4) with a fake client.*
- [ ] **T5 — Controller orchestration, conflict state, snapshot and composition root**: implement
  `AutomaticBrightnessController` + `BrightnessControllerUseCase` wiring the F1 stream through map →
  transform-hooks → clamp → dead-band → write, publish `status`,
  `adaptiveBrightnessConflict` and `BrightnessSnapshot`, and bind everything in `:app` with
  `BrightnessStatusCollector`.
  *Files: `:core/.../AutomaticBrightnessController/**`,
  `:app/src/main/kotlin/com/ambientbrightness/app/**`.*
  *Verify: end-to-end fake-driven test (AC1.1, AC1.3, AC1.4, AC4.1–4.3); `stop()` halts writes.*

## Suggested Implementation Order

`T1 → T2 → T3 → T4 → T5`. T1 fixes the contract F3/F4/F5 extend, so it lands first. T2 and T3 are
pure and can proceed in parallel once T1 exists; T3 depends on T2's fraction output but only via the
interface, so the clamp/dead-band work can be built against a stub curve. T4 is independent of T2/T3
and can run alongside them (adapters only). T5 depends on all of them because it is the only place
that connects the F1 stream, the status surface and the composition root.

## Implementation Notes

### T1 — Domain model & boundary interfaces

*Done: implementation commit `8d137e13aab57afb3e28464d22953d81b7abe9f7`. Added these files:*

- `core/src/main/kotlin/com/ambientbrightness/core/AutomaticBrightnessController.kt`
- `core/src/main/kotlin/com/ambientbrightness/core/BrightnessContext.kt`
- `core/src/main/kotlin/com/ambientbrightness/core/BrightnessMappingCurve.kt`
- `core/src/main/kotlin/com/ambientbrightness/core/BrightnessRange.kt`
- `core/src/main/kotlin/com/ambientbrightness/core/BrightnessSnapshot.kt`
- `core/src/main/kotlin/com/ambientbrightness/core/BrightnessStatus.kt`
- `core/src/main/kotlin/com/ambientbrightness/core/BrightnessTransform.kt`
- `core/src/main/kotlin/com/ambientbrightness/core/BrightnessValue.kt`
- `core/src/main/kotlin/com/ambientbrightness/core/BrightnessWriteResult.kt`
- `core/src/main/kotlin/com/ambientbrightness/core/BuiltInAdaptiveBrightnessState.kt`
- `core/src/main/kotlin/com/ambientbrightness/core/DeviceBrightnessRangeClient.kt`
- `core/src/main/kotlin/com/ambientbrightness/core/HysteresisGate.kt`
- `core/src/main/kotlin/com/ambientbrightness/core/SystemBrightnessClient.kt`
- `core/src/main/kotlin/com/ambientbrightness/core/WriteSettingsPermissionChecker.kt`
- `core/src/test/kotlin/com/ambientbrightness/core/BrightnessContractsTest.kt`
- `core/src/test/kotlin/com/ambientbrightness/core/testdoubles/BrightnessContractFakes.kt`

  `BrightnessValue` rejects values below 1; `BrightnessRange` requires a positive minimum and
  `max >= min`. Status exposes the last applied value, last lux, write outcome and error. Range
  discovery is a separate client; permission grant is observable as a `StateFlow`. No deviations.
  Verification passed: `:core:test`; `:core:dependencies --configuration compileClasspath`
  (no Android artifacts); `:core:test :infrastructure:test :app:assembleDebug`.
