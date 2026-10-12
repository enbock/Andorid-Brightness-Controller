# Architecture Structure Alignment

Cross-cutting refactoring plan that brings the existing source layout of `:core`, `:infrastructure`
and `:app` in line with the refined clean-architecture instructions
(`.copilot/instructions/clean-architecture-principles.instructions.md`, sections *Keep files
together*, *Structure of UseCase methods*, *Place of Entity, Dto, Enum, Storages or Clients*,
*Dependency rules*).

This document is structure-only: **no acceptance criterion of any feature changes** and no
behaviour is altered. It exists because new features (F3–F7) keep adding types to the current layout
and would otherwise cement the deviation.

Repository state at the time of writing: branch `main` @ `eb15641`.

---

## Design

### Components to add/change and their responsibilities

No new production behaviour. The change set is a re-grouping of existing types plus three small
extractions:

1. **A responsibility level under each layer.** Both domains already exist implicitly in the code
   (`AmbientLightSensing` = story F1, `AutomaticBrightnessControl` = story F2) but have no folder.
   Every layer gets `.../core/<Responsibility>/...`, `.../infrastructure/<Responsibility>/...`,
   `.../app/<Responsibility>/...`.
2. **Data classes into the responsibility folder, not into a folder of their own.** `BrightnessValue`,
   `BrightnessRange`, `BrightnessContext`, `BrightnessStatus`, `BrightnessWriteResult`,
   `BrightnessSnapshot`, `BuiltInAdaptiveBrightnessState`, `BrightnessTransform`, `LuxSample`,
   `SensorProfile`, `SensorAvailability`, `SensorUnavailableReason`, `LightLevel` and the
   four client interfaces are *not* moved into separate `Entity/`/`Enum/` folders — they sit directly
   beside the use case/service they serve. The cross-cutting `Clock` port is the single exception and
   lives in `Core/Shared/` (see *Open questions*, decision 1).
3. **Interface + Default implementation in one folder.** `BrightnessMappingCurve`,
   `HysteresisGate` and the `ReadingStabiliser`/`OutlierClampingStabiliser` pair get merged so the
   contract and its default live in the same directory — matching the already-correct
   `AmbientLightService/` folder.
4. **Technical documentation is corrected by the task that moves a file.** Each task T3–T9 also
   updates the `docs/*.md` references to the paths it invalidates, so no commit ever leaves the
   documentation describing the old layout; T10 is only the exhaustive final cross-check.
4. **Infrastructure grouped by client, then by variant.** `<Client>/Android/<Impl>.kt` and
   `<Client>/Dummy/<Impl>.kt` replace the flat `AndroidXClient/` / `DummyXClient/` sibling folders,
   which gives the existing real/dummy pairs a common parent.
5. **`Task/` breakdown for the two oversized classes.** `DefaultAmbientLightService` (104 lines,
   `start()` owning mutex, session-job bookkeeping, first-sample watchdog and availability publishing
   in one block) and `AmbientLightUseCase` (private `LatestReading` class in the same file) are split
   into `Task/` classes. Public signatures stay identical.
6. **`Application` layer separated into composition root, collector and view.** `MainActivity` keeps
   the entry-point role; the inline `@Composable` moves into a `View/` file, the manual DI container
   and `Application` implementation move into `Composition/`.

### Data flow and interfaces

Unchanged. The only signatures touched are package names:

| Type | Public signature today | After refactor |
| --- | --- | --- |
| `AmbientLightService.start()` | `Flow<LuxSample>` | unchanged, `com.ambientbrightness.core.AmbientLightSensing.AmbientLightService` |
| `AmbientLightSensorClient.register/unregister` | `(Float) -> Unit` / `Unit` | unchanged, new package |
| `DefaultAmbientLightService(sensorClient, useCase, startupTimeoutMillis)` | constructor | unchanged (Task classes are internal collaborators) |
| `AmbientLightUseCase(sensorClient, stabiliser, clock, samplingIntervalMillis)` | constructor | unchanged |
| `AutomaticBrightnessController`, `SystemBrightnessClient`, `DeviceBrightnessRangeClient`, `WriteSettingsPermissionChecker`, `ScreenStateClient` | interfaces | unchanged, new package |
| `OutlierClampingStabiliser(maxRangeLux, …)`, `DefaultHysteresisGate()`, `BrightnessRangeResolver`, `BrightnessRangeScaler`, `MinimumStepDeadBand` | classes/objects | unchanged, folder-consistent package |

Data flow (sensor → adapter → use case → stabiliser → service → collector → view) and the dependency
directions (`Application → Core`, `Infrastructure → Core`, composition root → both) are untouched.

### Key decisions + rejected alternatives

| Decision | Rationale | Rejected alternative |
| --- | --- | --- |
| Align **folder and package** (e.g. `com.ambientbrightness.core.AmbientLightSensing.AmbientLightUseCase`) instead of moving folders only | The instructions require DDD-style directory *and* class naming; today 20 of 25 core folders declare `package com.ambientbrightness.core`, so folder and namespace already contradict each other. A folder-only move would leave the same contradiction in place. | Folder-only move: near-zero diff, but keeps package ≠ folder and delivers no rule compliance. |
| Keep **one `Task/` level** directly below the owning class folder | Matches the instruction example (`Core/ExampleResponsibility/ExampleUseCase/Task/Executor1`) and keeps the class directory as the single import anchor. | A shared `core/tasks/` folder: violates "keep files together". |
| Keep the **`Flow`-returning use-case signature** and record a documented deviation from the request/response rule | `AmbientLightService.start(): Flow<LuxSample>` is the pinned F2–F7 boundary (see `docs/feature-ambient-light-sensing.md`, *Public boundary signatures*). Wrapping a streaming result in a `Response` object would either buffer the stream or force a breaking API change across five unwritten features. `AmbientLightUseCase.samples()` needs no input, so per the rule the request is legitimately omitted. | Introduce `SamplesRequest`/`SamplesResponse` now: breaks the documented boundary and the cold-stream semantics. |
| `Default*` naming for core implementations, `Android*`/`Dummy*` for adapters | Already the project convention; the instructions only require the implementation to live under the client. | Renaming to `HttpBrightnessClient`-style names: churn without benefit, since there is exactly one platform. |
| Structure guard as a **dependency-free JUnit test** that walks `src/main/kotlin` and asserts folder == package | Enforces the rule continuously without a new library; `:core` is a plain JVM module so the test needs no Android runtime. | Konsist/ArchUnit: extra dependency plus Kotlin-version coupling for a rule a 30-line test already covers. |

---

## Feasibility

- **Verdict: feasible with caveats.**
- The refactor is mechanical (move + rename + import rewrite) and verifiable by the existing test
  suites: 19 core unit-test files, 6 infrastructure unit-test files, 2 app instrumented tests.
- Acceptance criteria: **not affected**. No AC is cheap/expensive/contradictory here; the only
  documented decision that the refactor supersedes is the F1 note *"one directory per type"*
  (`docs/feature-ambient-light-sensing.md`), which contradicts the refined *Keep files together*
  rule and must be corrected.

### Risks

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Import churn across modules | Medium — moves touch `:app`, `:infrastructure` and all test sources | One module-scoped commit per task, full compile + unit tests after each |
| Package moves of `internal` helpers | Medium — `brightnessRangeFromMaximum`, `readBrightnessSetting`, `writeBrightnessSetting` are `internal` and referenced by infrastructure tests in the same module; sub-packages require explicit imports | Include the affected test files in the same task and run `:infrastructure:test` before committing |
| Instrumented tests need a device/emulator | Low — `:app` and `:infrastructure` androidTest cannot run in every environment | Structural verification via `:core:test`, `:infrastructure:test`, `:app:assembleDebug`; run `connectedDebugAndroidTest` where an API 36 emulator is available (it was, per F1 T1/T5) |
| Docs drift | Medium — 6 feature docs and the EPIC reference explicit source paths | Every moving task corrects the paths it invalidates in the same commit; T10 re-greps every documented path (must exist on disk) |
| Silent behaviour change during `Task/` extraction | Medium — `DefaultAmbientLightService` contains concurrency bookkeeping | Extraction without logic edits; `DefaultAmbientLightServiceTest` + `AmbientLightServiceContractTest` must stay green unmodified |

### Open questions — all resolved before implementation (binding)

1. **Where does `Clock` live?** → **Decision: `core/.../core/Shared/Clock.kt`.**
   `Clock` is consumed by three responsibilities that are *not* the same domain: F1
   (`AmbientLightUseCase`, shipped), F2 (`BrightnessControllerUseCase` plus the fixed-clock tests of
   `MinimumStepDeadBand`, see `docs/feature-automatic-brightness-control.md`) and F4
   (`docs/feature-response-tuning.md`: "One injected `Clock` (`nowMillis()`) serves both"). It is a
   cross-cutting port, so owning it under one domain would force cross-domain imports between
   responsibility packages and would misstate ownership.
   *Rejected:* `AmbientLightSensing/Clock.kt` (first consumer owns it) — cheap now, wrong once F2/F4
   land; duplicating a `Clock` per responsibility — three identical ports.
   `Core/Shared/` is the place for further cross-cutting ports only, never for domain types.
2. **Composition-root isolation?** → **Decision: yes, enforced.**
   Only `app/.../app/Composition/**` may import `com.ambientbrightness.infrastructure.*`. Everything
   else in `:app` (view, collector) depends on `:core` plus the component facade. Consequence: the
   adapter test that currently sits in `:app` androidTest moves to `:infrastructure` (T9).
3. **Does the refined rule need a streaming exception?** → **Decision: yes, and it is documented in
   the project, not silently ignored.** A cold-`Flow` use case legitimately omits the
   `Request`/`Response` pair: the result is a stream, not a value object, and `AmbientLightService.start()`
   is a pinned boundary for F2–F7. The exception is recorded in
   `docs/feature-ambient-light-sensing.md` (T6/T10). The user-level instruction file
   (`~/.copilot/instructions/clean-architecture-principles.instructions.md`) is outside this
   repository and must be updated by the user with the wording proposed in T10.
4. **`Task/` breakdown of `OutlierClampingStabiliser` / `MinimumStepDeadBand`?** → **Decision: not
   mandated.** They are 46/40 lines and cohesive; splitting is left to the developer's judgement
   during T7 and must stay behaviour-preserving.

Every task below is therefore fully specified: do not re-open these decisions.

---

## Task Breakdown

- [ ] **T1 — Freeze the verified baseline (S)** — depends on: none
      - Run `./gradlew :core:test :infrastructure:test :app:assembleDebug` and record the green
        baseline commit SHA and the test counts in this file.
      - The target tree and all design decisions are already fixed (see `## Design` and
        *Open questions — all resolved*); do not re-open them.
      - Verify: baseline commands exit 0; SHA recorded.

- [ ] **T2 — Folder/package guard test for `:core` (S)** — depends on: T4
      - Add `core/src/test/kotlin/com/ambientbrightness/core/ArchitectureStructureTest.kt`: walk
        `File("src/main/kotlin")` (Gradle's default test working directory is the module directory),
        for every `*.kt` read the `package` line, derive the expected package from the directory path
        relative to the source root and assert equality; additionally assert that no `:core` source
        imports `android.`.
      - The guard must be green the moment it is added — add it after T4, never `@Disabled`.
      - Verify: `:core:test` green; a scratch file with a mismatching `package` makes it fail; delete
        the scratch file afterwards.

- [ ] **T3 — Create the `AmbientLightSensing` responsibility in `:core` (M)** — depends on: T1
      - Move to `core/src/main/kotlin/com/ambientbrightness/core/AmbientLightSensing/`:
        `AmbientLightService/{AmbientLightService,DefaultAmbientLightService}.kt`,
        `AmbientLightUseCase/AmbientLightUseCase.kt`, `ReadingStabiliser/ReadingStabiliser.kt`,
        `LightLevel/{LightLevel,LightLevelClassifier}.kt`, and the data/port types
        `LuxSample.kt`, `SensorProfile.kt`, `SensorAvailability.kt`, `SensorUnavailableReason.kt`,
        `AmbientLightSensorClient.kt`, `ScreenStateClient.kt` directly under `AmbientLightSensing/`.
      - Move `Clock/Clock.kt` to `core/.../core/Shared/Clock.kt` (decision 1 in *Open questions*).
      - Set every moved file's `package` to `com.ambientbrightness.core.AmbientLightSensing[.<TypeFolder>]`
        resp. `com.ambientbrightness.core.Shared` and rewrite imports in `core/src/test/**`,
        `infrastructure/**`, `app/**`.
      - Correct the `:core` paths for these types in `docs/feature-ambient-light-sensing.md`
        (including the `{LuxSample,SensorProfile,…}` list and the `Clock/Clock` file list) and in
        `docs/feature-automatic-brightness-control.md` / `docs/feature-response-tuning.md` where
        `Clock` is referenced by path.
      - Verify: `:core:test` green; `:infrastructure:compileDebugKotlin` and `:app:compileDebugKotlin`
        green; guard test (T2) green; no stale path left by `rg 'core/Clock|core/LuxSample|core/SensorProfile|core/LightLevel|core/ReadingStabiliser|core/AmbientLight' docs`.

- [ ] **T4 — Create the `AutomaticBrightnessControl` responsibility in `:core` (M)** — depends on: T3
      - Move the brightness contract types and the already-foldered services:
        `AutomaticBrightnessController.kt`, `BrightnessValue.kt`, `BrightnessRange.kt`,
        `BrightnessContext.kt`, `BrightnessStatus.kt`, `BrightnessWriteResult.kt`,
        `BrightnessSnapshot.kt`, `BuiltInAdaptiveBrightnessState.kt`, `BrightnessTransform.kt`,
        `SystemBrightnessClient.kt`, `DeviceBrightnessRangeClient.kt`,
        `WriteSettingsPermissionChecker.kt`, plus the folders `BrightnessMappingCurve/`,
        `HysteresisGate/`, `BrightnessRangeResolver/`, `BrightnessRangeScaler/`,
        `MinimumStepDeadBand/` under `core/.../core/AutomaticBrightnessControl/`.
      - Apply `package com.ambientbrightness.core.AutomaticBrightnessControl[.<TypeFolder>]` and
        rewrite imports in `:infrastructure`, `:app` and the core/infrastructure tests
        (`AndroidDeviceBrightnessRangeClientTest`, `AndroidSystemBrightnessClientTest`,
        `DummyBrightnessAdaptersTest`, `BrightnessContractsTest`, `testdoubles/BrightnessContractFakes.kt`).
      - Correct the `:core` paths in `docs/feature-automatic-brightness-control.md`,
        `docs/feature-background-service.md`, `docs/feature-manual-brightness-offset.md`,
        `docs/feature-status-dashboard.md` and `docs/EPIC-ambient-brightness-controller.md` wherever a
        moved type is referenced by path or by `core/.../` example.
      - Verify: `:core:test` and `:infrastructure:test` green; guard test green; no stale
        `com.ambientbrightness.core.Brightness*` import path in the moved sources.

- [ ] **T5 — Merge interface and default implementation folders (S)** — depends on: T3, T4
      - `BrightnessMappingCurve/{BrightnessMappingCurve,DefaultBrightnessMappingCurve}.kt` (drop the
        stray root-level `BrightnessMappingCurve.kt`).
      - `HysteresisGate/{HysteresisGate,DefaultHysteresisGate}.kt`.
      - `ReadingStabiliser/{ReadingStabiliser,OutlierClampingStabiliser}.kt` (remove the separate
        `OutlierClampingStabiliser/` folder).
      - Update the affected paths in `docs/feature-automatic-brightness-control.md`
        (`BrightnessMappingCurve/`, `HysteresisGate/`, `ReadingStabiliser/`).
      - Verify: no file remains in a folder whose name differs from the type it defines; `:core:test`
        green.

- [ ] **T6 — Give the use case its folder-local collaborators (S)** — depends on: T3
      - Extract the private `LatestReading` class out of `AmbientLightUseCase.kt` into
        `AmbientLightUseCase/Task/LatestReading.kt` (`internal`, behaviour identical).
      - Record the streaming exception (decision 3 in *Open questions*) in
        `docs/feature-ambient-light-sensing.md` next to the `AmbientLightUseCase` / boundary task text,
        with the rationale that `samples(): Flow<LuxSample>` intentionally omits the
        `Request`/`Response` pair.
      - Verify: `AmbientLightUseCaseTest` green without modification.

- [ ] **T7 — Break `DefaultAmbientLightService` into `Task/` classes (M)** — depends on: T3, T6
      - Move to `AmbientLightService/Task/`:
        - `CollectionSessionRegistry.kt` — owns `collectionMutex`, `activeJob`, `activeJobLock`,
          `publishIfActive`, `stop()`'s cancellation lookup.
        - `StartupAvailabilityGate.kt` — owns `startupTimeoutMillis` validation, the
          `CompletableDeferred` first-sample watchdog and the timeout outcome.
      - `DefaultAmbientLightService` keeps the constructor signature and delegates; no logic edits.
      - Verify: `DefaultAmbientLightServiceTest` and `AmbientLightServiceContractTest` green without
        modification.

- [ ] **T8 — Regroup `:infrastructure` by responsibility → client → variant (M)** — depends on: T3, T4
      - `infrastructure/.../infrastructure/AmbientLightSensing/AmbientLightSensorClient/Android/AndroidAmbientLightSensorClient.kt`
        and `.../AmbientLightSensorClient/Dummy/DummyAmbientLightSensorClient.kt`;
        `.../AmbientLightSensing/ScreenStateClient/{Android,Dummy}/…`;
        `.../AmbientLightSensing/SensorProfileFactory/SensorProfileFactory.kt`.
      - `infrastructure/.../infrastructure/AutomaticBrightnessControl/SystemBrightnessClient/{Android,Dummy}/…`,
        `.../DeviceBrightnessRangeClient/{Android,Dummy}/…`,
        `.../WriteSettingsPermissionChecker/{Android,Dummy}/…`.
      - Apply `package com.ambientbrightness.infrastructure.<Responsibility>.<Client>.<Variant>` and
        update imports in `app/**`, `infrastructure/src/test/**` (including the `internal` helpers)
        and `infrastructure/src/androidTest/**`.
      - Correct the adapter paths in `docs/feature-ambient-light-sensing.md` and
        `docs/feature-automatic-brightness-control.md` (the `Android*/Dummy*` file lists).
      - Add the mirrored `infrastructure/src/test/kotlin/.../ArchitectureStructureTest.kt` with the
        same folder/package logic for the `:infrastructure` source root (it can only be green once this
        task is done).
      - Verify: `:infrastructure:test` green; `:app:assembleDebug` green;
        `:infrastructure:compileDebugAndroidTestKotlin` green.

- [ ] **T9 — Structure the `:app` application layer (M)** — depends on: T3, T8
      - `app/.../app/Composition/{AmbientBrightnessApplication,AmbientBrightnessComponent}.kt`
        (package `com.ambientbrightness.app.Composition`) — the only place that may import
        `infrastructure.*` (decision 2 in *Open questions*).
      - `app/.../app/AmbientLightSensing/Collector/AmbientLightCollector.kt`.
      - `app/.../app/AmbientLightSensing/View/AmbientBrightnessView.kt`: move the private
        `AmbientBrightnessApp()` composable out of `MainActivity.kt` unchanged; `MainActivity` stays
        the entry point in the package root and calls it.
      - Update imports in `app/src/androidTest/**`.
      - Relocate `app/src/androidTest/.../AndroidBrightnessAdaptersTest.kt` to
        `infrastructure/src/androidTest/.../`: it exercises `AndroidDeviceBrightnessRangeClient`,
        `AndroidSystemBrightnessClient` and `AndroidWriteSettingsPermissionChecker`, so it is an
        infrastructure test that currently forces `:app` to depend on `infrastructure` in test scope
        (decision 2 in *Open questions*).
      - Correct the `:app` paths in `docs/feature-ambient-light-sensing.md` (T5/collector/component
        file lists) and in `docs/feature-background-service.md` where `app/.../app/**` is referenced.
      - Out of scope: no new UI, no ViewModel behaviour, no new callbacks — the view stays the current
        empty `Scaffold`.
      - Verify: `:app:assembleDebug` green; `connectedDebugAndroidTest` on the API 36 emulator where
        available.

- [ ] **T10 — Documentation sweep and final verification (S)** — depends on: T3–T9
      - Several docs were already corrected inside T3–T9; this task is the final, exhaustive check.
      - Re-grep `src/main/kotlin` / `src/test/kotlin` paths in
        `docs/EPIC-ambient-brightness-controller.md` and all seven `docs/feature-*.md`; fix anything
        still pointing at the old layout.
      - Fix the superseded *"one directory per type"* statement in
        `feature-ambient-light-sensing.md` and point future tasks at the responsibility folders;
        confirm the streaming exception recorded in T6 reads consistently with `AmbientLightService`'s
        pinned signature.
      - Hand the user the wording for the user-level instruction file (outside this repository, cannot
        be edited from here): *"An exception applies to streaming use cases: a use case whose result is
        a `Flow` (or equivalent observable stream) omits `Request`/`Response`; the request is omitted
        when no input is needed and the stream itself is the result."*
      - Verify: every documented path exists on disk; `./gradlew :core:test :infrastructure:test
        :app:assembleDebug` green; guard tests green; final commit SHA recorded in this file.

---

## Suggested Implementation Order

1. **T1** first — it freezes the green baseline SHA used as rollback anchor for every later task; the
   design decisions are already fixed and must not be re-opened.
2. **T3 → T4 → T2** — `:core` is the dependency-free module with the densest test coverage (19 test
   files), so it is the cheapest place to validate the new convention before the other modules depend
   on it. T2 (the folder/package guard) lands immediately after T4, i.e. at the first moment it can be
   green without being `@Disabled`.
3. **T5 → T6 → T7** — small, behaviour-preserving clean-ups of the folders T3/T4 just created, then the
   concurrency-sensitive `Task/` split of the service in its own reviewable commit.
4. **T8 → T9** — adapters first, then the application layer: both are import-heavy and both consume core
   packages. T8 also adds the mirrored infrastructure guard, T9 enforces the composition-root isolation
   decided in the *Open questions* section.
5. **T10 last** — the final documentation and verification sweep: it can only run once every path is
   final, and it makes the result self-checking (every documented path must exist on disk).

Note on documentation: the technical docs are corrected inside the task that invalidates a path
(T3–T9 each carry a doc bullet) and not deferred to T10; T10 is only the exhaustive cross-check, so a
partially finished run never leaves the docs describing a stale layout.
