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
| A package name equals the fully-qualified name of a classifier declared in its parent package | High — `:core:compileKotlin` aborts with `Package conflicts with classifier …`; the condition existed since F2 but stayed masked by incremental compilation and surfaced at the first full re-analysis | Merge interface + default implementation into one folder **in the same commit that first introduces the responsibility level** (folded into T3 for `BrightnessMappingCurve`/`HysteresisGate`); never leave a same-named interface in the layer root |

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
   else in `:app` (view, collector) depends on `:core` plus the component facade. Consequence: no file in
   `:app` outside `Composition/` may reference an adapter type, and the instrumented adapter test stays in
   `:app` androidTest (see the *Correction* paragraph below). The application class move in the same task
   requires `app/src/main/AndroidManifest.xml` to name `.Composition.AmbientBrightnessApplication` — a
   source edit, not a build-file change (see T9).
   *Correction (T9, commit `00012a6`):* T9 first relocated
   `AndroidBrightnessAdaptersTest.kt` into `:infrastructure` androidTest and had to revert that relocation
   in `00012a6`. `infrastructure/src/main/AndroidManifest.xml` is an empty manifest (no `WRITE_SETTINGS`,
   which `app/src/main/AndroidManifest.xml` does declare) and the `:infrastructure` androidTest
   instrumentation target is `com.ambientbrightness.infrastructure.test`, so `Settings.System.canWrite()`
   is `false` there: the write/read-back test would silently skip via `assumeTrue`, the
   permission-observation test would fail its hard `assertTrue`, and the private helper's
   `appops set com.ambientbrightness.app …` would target a package the test process is not running as —
   none of it visible to any compile task. `docs/feature-automatic-brightness-control.md` records the
   on-device evidence: those tests passed 5/5 only after `adb shell appops set com.ambientbrightness.app
   WRITE_SETTINGS allow` while the test ran on the app target.
   *Enforcement (clarified in T9):* the rule is enforced **structurally** — `:app` is reorganized so only `Composition/**` imports `com.ambientbrightness.infrastructure.*` — and verified by grep in T10. An automated `:app` guard mirroring the T2/T8 tests was considered and explicitly declined by the Project Owner, because `:app` has no JVM test source set: it would require adding `app/src/test/**` plus `testImplementation(libs.junit)`. Limitation: the composition-root isolation is therefore not machine-checked; a future task may add such a guard.
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

- [x] **T1 — Freeze the verified baseline (S)** — depends on: none
      - Run `./gradlew :core:test :infrastructure:test :app:assembleDebug` and record the green
        baseline commit SHA and the test counts in this file.
      - The target tree and all design decisions are already fixed (see `## Design` and
        *Open questions — all resolved*); do not re-open them.
      - Verify: baseline commands exit 0; SHA recorded.

- [x] **T2 — Folder/package guard test for `:core` (S)** — depends on: T4
      - Add `core/src/test/kotlin/com/ambientbrightness/core/ArchitectureStructureTest.kt`: walk
        `File("src/main/kotlin")` (Gradle's default test working directory is the module directory),
        for every `*.kt` read the `package` line, derive the expected package from the directory path
        relative to the source root and assert equality; additionally assert that no `:core` source
        imports `android.`.
      - The guard must be green the moment it is added — add it after T4, never `@Disabled`.
      - Verify: `:core:test` green; a scratch file with a mismatching `package` makes it fail; delete
        the scratch file afterwards.

- [x] **T3 — Create the `AmbientLightSensing` responsibility in `:core` (M)** — depends on: T1
      - Move to `core/src/main/kotlin/com/ambientbrightness/core/AmbientLightSensing/`:
        `AmbientLightService/{AmbientLightService,DefaultAmbientLightService}.kt`,
        `AmbientLightUseCase/AmbientLightUseCase.kt`, `ReadingStabiliser/ReadingStabiliser.kt`,
        `LightLevel/{LightLevel,LightLevelClassifier}.kt`, and the data/port types
        `LuxSample.kt`, `SensorProfile.kt`, `SensorAvailability.kt`, `SensorUnavailableReason.kt`,
        `AmbientLightSensorClient.kt`, `ScreenStateClient.kt` directly under `AmbientLightSensing/`.
      - Move `Clock/Clock.kt` to `core/.../core/Shared/Clock.kt` (decision 1 in *Open questions*).
      - **Blocker fix — must be part of this commit, otherwise `:core:compileKotlin` fails:**
        move the two interface files out of the layer root into their existing folders. A package must
        never share its fully-qualified name with a classifier declared in its *parent* package:
        `com.ambientbrightness.core.BrightnessMappingCurve` (package, declared by
        `BrightnessMappingCurve/DefaultBrightnessMappingCurve.kt`) collided with the
        `BrightnessMappingCurve` interface that still sat in `com.ambientbrightness.core`, and the same
        holds for `HysteresisGate`. The compiler aborts with
        `error: Package conflicts with classifier com.ambientbrightness.core.BrightnessMappingCurve`.
        → `core/.../core/BrightnessMappingCurve.kt` moves to
        `core/.../core/BrightnessMappingCurve/BrightnessMappingCurve.kt` and
        `core/.../core/HysteresisGate.kt` to `core/.../core/HysteresisGate/HysteresisGate.kt`, each with
        the package of its new folder (`com.ambientbrightness.core.BrightnessMappingCurve` resp.
        `com.ambientbrightness.core.HysteresisGate`, see the same-shaped `AmbientLightService/`).
        This pulls the `BrightnessMappingCurve`/`HysteresisGate` part of T5 forward into T3; update every
        import of the two interfaces (`core/src/test/**`, `infrastructure/**`, `app/**`).
      - Set every moved file's `package` to `com.ambientbrightness.core.AmbientLightSensing[.<TypeFolder>]`
        resp. `com.ambientbrightness.core.Shared` and rewrite imports in `core/src/test/**`,
        `infrastructure/**`, `app/**`.
      - Correct the `:core` paths for these types in `docs/feature-ambient-light-sensing.md`
        (including the `{LuxSample,SensorProfile,…}` list and the `Clock/Clock` file list) and in
        `docs/feature-automatic-brightness-control.md` / `docs/feature-response-tuning.md` where
        `Clock` is referenced by path.
      - Verify: `.\gradlew.bat :core:test :infrastructure:test :app:assembleDebug` exits 0; no stale
        path left by
        `rg 'core/Clock|core/LuxSample|core/SensorProfile|core/LightLevel|core/ReadingStabiliser|core/AmbientLight|core/BrightnessMappingCurve.kt|core/HysteresisGate.kt' docs`
        and no `import com.ambientbrightness.core.BrightnessMappingCurve` /
        `...core.HysteresisGate` left in any source file.

- [x] **T4 — Create the `AutomaticBrightnessControl` responsibility in `:core` (M)** — depends on: T3
      - Move the brightness contract types and the already-foldered services:
        `AutomaticBrightnessController.kt`, `BrightnessValue.kt`, `BrightnessRange.kt`,
        `BrightnessContext.kt`, `BrightnessStatus.kt`, `BrightnessWriteResult.kt`,
        `BrightnessSnapshot.kt`, `BuiltInAdaptiveBrightnessState.kt`, `BrightnessTransform.kt`,
        `SystemBrightnessClient.kt`, `DeviceBrightnessRangeClient.kt`,
        `WriteSettingsPermissionChecker.kt`, plus the folders `BrightnessMappingCurve/`,
        `HysteresisGate/`, `BrightnessRangeResolver/`, `BrightnessRangeScaler/`,
        `MinimumStepDeadBand/` under `core/.../core/AutomaticBrightnessControl/`.
      - `BrightnessMappingCurve/` and `HysteresisGate/` already contain their interface after T3 (blocker
        fix); this task only renames their packages, no further file moves inside them.
      - Apply `package com.ambientbrightness.core.AutomaticBrightnessControl[.<TypeFolder>]` and
        rewrite imports in `:infrastructure`, `:app` and the core/infrastructure tests
        (`AndroidDeviceBrightnessRangeClientTest`, `AndroidSystemBrightnessClientTest`,
        `DummyBrightnessAdaptersTest`, `BrightnessContractsTest`, `testdoubles/BrightnessContractFakes.kt`).
      - Correct the `:core` paths in `docs/feature-automatic-brightness-control.md`,
        `docs/feature-background-service.md`, `docs/feature-manual-brightness-offset.md`,
        `docs/feature-status-dashboard.md` and `docs/EPIC-ambient-brightness-controller.md` wherever a
        moved type is referenced by path or by `core/.../` example.
      - Verify: `:core:test` and `:infrastructure:test` green; `:app:assembleDebug` green; no stale
        `com.ambientbrightness.core.Brightness*` import path in the moved sources.

- [x] **T5 — Merge interface and default implementation folders (S)** — depends on: T3, T4
      - The `BrightnessMappingCurve` and `HysteresisGate` merges are **already part of T3** (blocker
        fix, see T3) because the folder-consistent package name collided with the interfaces that
        still lived in the layer root. Only one merge is left here:
      - `ReadingStabiliser/{ReadingStabiliser,OutlierClampingStabiliser}.kt` (remove the separate
        `OutlierClampingStabiliser/` folder; the class name stays `OutlierClampingStabiliser`, it only
        takes over `ReadingStabiliser`'s package).
      - Update the affected paths in `docs/feature-automatic-brightness-control.md`
        (`ReadingStabiliser/`, `OutlierClampingStabiliser/`).
      - Verify: no file remains in a folder whose name differs from the type it defines; `:core:test`
        green; no package name in `:core` equals the fully-qualified name of a classifier.

- [x] **T6 — Give the use case its folder-local collaborators (S)** — depends on: T3
      - Extract the private `LatestReading` class out of `AmbientLightUseCase.kt` into
        `AmbientLightUseCase/Task/LatestReading.kt` (`internal`, behaviour identical).
      - Record the streaming exception (decision 3 in *Open questions*) in
        `docs/feature-ambient-light-sensing.md` next to the `AmbientLightUseCase` / boundary task text,
        with the rationale that `samples(): Flow<LuxSample>` intentionally omits the
        `Request`/`Response` pair.
      - Verify: `AmbientLightUseCaseTest` green without modification.

- [x] **T7 — Break `DefaultAmbientLightService` into `Task/` classes (M)** — depends on: T3, T6
      - Move to `AmbientLightService/Task/`:
        - `CollectionSessionRegistry.kt` — owns `collectionMutex`, `activeJob`, `activeJobLock`,
          `publishIfActive`, `stop()`'s cancellation lookup.
        - `StartupAvailabilityGate.kt` — owns `startupTimeoutMillis` validation, the
          `CompletableDeferred` first-sample watchdog and the timeout outcome.
      - `DefaultAmbientLightService` keeps the constructor signature and delegates; no logic edits.
      - Verify: `DefaultAmbientLightServiceTest` and `AmbientLightServiceContractTest` green without
        modification.

- [x] **T8 — Regroup `:infrastructure` by responsibility → client → variant (M)** — depends on: T3, T4
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

- [x] **T9 — Structure the `:app` application layer (M)** — depends on: T3, T8
      - `app/.../app/Composition/{AmbientBrightnessApplication,AmbientBrightnessComponent}.kt`
        (package `com.ambientbrightness.app.Composition`) — the only place that may import
        `infrastructure.*` (decision 2 in *Open questions*).
      - `app/.../app/AmbientLightSensing/Collector/AmbientLightCollector.kt`.
      - `app/.../app/AmbientLightSensing/View/AmbientBrightnessView.kt`: move the composable out of
        `MainActivity.kt` with its body, signature and name **unchanged except its visibility** — it must
        change from `private` to `internal`. A `private` top-level function is file-private, so
        `MainActivity.kt` could not call it after the move; `internal` keeps exactly the current module
        scope, so `setContent { AmbientBrightnessApp() }` in `MainActivity.kt` stays byte-identical and
        `:app:assembleDebug` stays green. `MainActivity` stays the entry point in the package root and
        calls it. (Without this visibility change the task's own verify requirement is unreachable.)
      - Update imports in `app/src/androidTest/**` (one file): `AmbientBrightnessComponentTest.kt` keeps
        `package com.ambientbrightness.app` but uses `AmbientBrightnessComponent` unqualified, so it needs
        `import com.ambientbrightness.app.Composition.AmbientBrightnessComponent` after the component moves
        into `Composition/`. `AndroidBrightnessAdaptersTest.kt` is the only other `:app` androidTest file.
      - **`AndroidBrightnessAdaptersTest.kt` stays in `app/src/androidTest/kotlin/com/ambientbrightness/app/`
        untouched** — no relocation, no `package`-line change, no assertion change. The first version of
        this plan moved it to `:infrastructure` androidTest; that was wrong and was reverted by commit
        `00012a6` (evidence in the *Correction* paragraph under decision 2: no `WRITE_SETTINGS` in the
        library manifest, instrumentation target `com.ambientbrightness.infrastructure.test`, and the 5/5
        on-device run that only passes while the test runs on the app target). `:app` therefore keeps its
        test-scope use of `implementation(project(":infrastructure"))`, which the composition root needs
        anyway.
      - **Update `app/src/main/AndroidManifest.xml`**: `<application android:name=".AmbientBrightnessApplication" …>`
        becomes `android:name=".Composition.AmbientBrightnessApplication"`. The relative name resolves
        against the `namespace`/`applicationId` `com.ambientbrightness.app`, and the class moves to
        `com.ambientbrightness.app.Composition`, so the manifest entry — not in the original T9 file
        list — must follow the move. Without it `:app:assembleDebug` still builds but the process crashes
        at start-up (`ClassNotFoundException`), a silent behaviour break the compiler cannot catch.
        This is a source-file edit only: `app/build.gradle.kts` and `infrastructure/build.gradle.kts` stay
        untouched (T9 has no build-file change), and the `.MainActivity` entry is unaffected because
        `MainActivity` stays in the package root.
      - Correct the `:app` paths in `docs/feature-ambient-light-sensing.md` (T5/collector/component
        file lists) and in `docs/feature-background-service.md` where `app/.../app/**` is referenced.
      - Out of scope: no new UI, no ViewModel behaviour, no new callbacks — the view stays the current
        empty `Scaffold`.
      - Verify: `:app:assembleDebug` green; `:app:compileDebugAndroidTestKotlin` and
        `:infrastructure:compileDebugAndroidTestKotlin` green; `:core:test` 81/0 and `:infrastructure:test`
        27/0 unchanged; `connectedDebugAndroidTest` on the API 36 emulator where available. The manifest
        rename is **not** covered by any compile task, so additionally install and launch the debug APK
        once on the API 36 emulator where available to confirm `AmbientBrightnessApplication` is
        instantiated. No emulator was attached during this run, so both device-level checks stay open and
        rest on the documented 5/5 run in `docs/feature-automatic-brightness-control.md`.

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

---

## Implementation Notes

### T1 — Verified baseline

- Baseline commit (rollback anchor, unchanged by this task): `678a4ac`
  (`docs(architecture): plan clean-architecture structure alignment (T1-T10)`), branch `main`.
- Verification command: `.\gradlew.bat :core:test :infrastructure:test :app:assembleDebug --console=plain`
  → `BUILD SUCCESSFUL in 17s` (exit 0, 63 actionable tasks).
- Observed test counts at the baseline: `:core:test` **80 tests / 0 failures / 0 skipped**
  (15 test classes); `:infrastructure:test` **26 tests / 0 failures / 0 skipped** (6 test classes);
  `:app:assembleDebug` green (`app-debug.apk` produced).
- No source change in this task; only this note was added.

### T3 — Create the `AmbientLightSensing` responsibility in `:core`

- Done in commit `ed38782`; this includes the
  `BrightnessMappingCurve`/`HysteresisGate` part that was pulled forward from T5 by the blocker fix.
- Moved with `git mv`; every moved file declares the package of its new folder, and the emptied old
  directories were deleted: `AmbientLightSensing/{LuxSample,SensorProfile,SensorAvailability,
  SensorUnavailableReason,AmbientLightSensorClient,ScreenStateClient}.kt`,
  `AmbientLightSensing/AmbientLightService/{AmbientLightService,DefaultAmbientLightService}.kt`,
  `AmbientLightSensing/AmbientLightUseCase/AmbientLightUseCase.kt`,
  `AmbientLightSensing/ReadingStabiliser/ReadingStabiliser.kt`,
  `AmbientLightSensing/OutlierClampingStabiliser/OutlierClampingStabiliser.kt`,
  `AmbientLightSensing/LightLevel/{LightLevel,LightLevelClassifier}.kt`, and `Clock/Clock.kt` →
  `Shared/Clock.kt` (decision 1). The layer root now holds only the T4 scope.
- Blocker fix as planned: `core/.../core/BrightnessMappingCurve.kt` →
  `core/.../core/BrightnessMappingCurve/BrightnessMappingCurve.kt` and `core/.../core/HysteresisGate.kt`
  → `core/.../core/HysteresisGate/HysteresisGate.kt`, each with the package of its new folder; the now
  redundant self-imports in `DefaultBrightnessMappingCurve`/`DefaultHysteresisGate` were removed and
  `core/src/test/.../testdoubles/BrightnessContractFakes.kt` imports both interfaces from their new
  packages. `:core:compileKotlin` is green again — the collision existed since the F2 commits and was
  only masked by incremental compilation.
- Outside `:core` only imports changed: `infrastructure/src/main/**`, `infrastructure/src/test/**`,
  `infrastructure/src/androidTest/**`, `app/src/main/**`, `app/src/androidTest/**`. No restructuring of
  `:infrastructure`/`:app` (T8/T9) and no test-package regroup inside `core/src/test/**`.
- Deviation found while fixing imports: `com.ambientbrightness.core.AmbientLightSensing.LightLevel` now
  names a *package*, so `LightLevelTest` and `LightLevelClassifierTest` had to import
  `...AmbientLightSensing.LightLevel.LightLevel`.
- Docs corrected in this commit: `docs/feature-ambient-light-sensing.md` (the `:core` file/`Files:`
  lists, including the `Clock` → `Shared/Clock.kt` entry) and `docs/feature-automatic-brightness-control.md`
  (the two interface paths changed by the blocker fix). `docs/feature-response-tuning.md` contains no
  `Clock` path reference, so it needed no change.
- Verification: `.\gradlew.bat --console=plain :core:test :infrastructure:test :app:assembleDebug`
  → `BUILD SUCCESSFUL`; `:core:test` **80 tests / 0 failures / 0 skipped** (15 classes),
  `:infrastructure:test` **26 tests / 0 failures / 0 skipped** (6 classes), `:app:assembleDebug` green
  (`app-debug.apk`). `:infrastructure:compileDebugAndroidTestKotlin` also green.

### T4 — Create the `AutomaticBrightnessControl` responsibility in `:core`

- Done in the single T4 commit `refactor(core): introduce the AutomaticBrightnessControl responsibility`
  (SHA is recorded by the T10 sweep, because a commit cannot contain its own hash).
- Moved with `git mv` into `core/src/main/kotlin/com/ambientbrightness/core/AutomaticBrightnessControl/`:
  the twelve contract types flat under it (`AutomaticBrightnessController.kt`, `BrightnessContext.kt`,
  `BrightnessRange.kt`, `BrightnessSnapshot.kt`, `BrightnessStatus.kt`, `BrightnessTransform.kt`,
  `BrightnessValue.kt`, `BrightnessWriteResult.kt`, `BuiltInAdaptiveBrightnessState.kt`,
  `DeviceBrightnessRangeClient.kt`, `SystemBrightnessClient.kt`, `WriteSettingsPermissionChecker.kt`)
  plus the five folders `BrightnessMappingCurve/`, `HysteresisGate/`, `BrightnessRangeResolver/`,
  `BrightnessRangeScaler/`, `MinimumStepDeadBand/` as whole directories (no file moves inside them).
  The emptied layer root directories were deleted, so only `AutomaticBrightnessControl/`,
  `AmbientLightSensing/` and `Shared/` remain there.
- Packages: the flat files declare `com.ambientbrightness.core.AutomaticBrightnessControl`, the
  foldered files `com.ambientbrightness.core.AutomaticBrightnessControl.<TypeFolder>`
  (`BrightnessMappingCurve`, `HysteresisGate`, `BrightnessRangeResolver`, `BrightnessRangeScaler`,
  `MinimumStepDeadBand`). No package name equals the fully-qualified name of a classifier declared in
  its parent package: the interfaces live inside their own folders, and no folder is named like a
  sibling folder's file.
- Imports rewritten (imports only, no test regroup): inside `:core` the foldered implementations
  (`BrightnessRangeResolver`, `BrightnessRangeScaler`, `MinimumStepDeadBand`) and
  `core/src/test/**` → `BrightnessContractsTest`, `testdoubles/BrightnessContractFakes.kt`; outside
  `:core` → `infrastructure/src/main/**` (six Android/Dummy adapters),
  `infrastructure/src/test/**` (`AndroidSystemBrightnessClientTest`,
  `AndroidDeviceBrightnessRangeClientTest`, `DummyBrightnessAdaptersTest`) and
  `app/src/androidTest/**` (`AndroidBrightnessAdaptersTest`). `infrastructure/src/androidTest/**`
  contains no reference to a moved type, so it needed no change.
- Necessity, not a package choice: because the test sources keep their old packages (no regroup),
  the unqualified references to the moved types had to become explicit imports —
  `BrightnessContractsTest` (7 `BrightnessValue`/`BrightnessRange`/`BrightnessSnapshot`/
  `BrightnessStatus`/`BrightnessWriteResult`/`BrightnessContext`/`BuiltInAdaptiveBrightnessState`
  imports) and the one-class imports of `DefaultBrightnessMappingCurveTest`,
  `DefaultHysteresisGateTest`, `BrightnessRangeResolverTest`, `BrightnessRangeScalerTest`,
  `MinimumStepDeadBandTest`. Same shape of deviation as T3's `LightLevel` note, no behaviour change.
- Docs corrected in this commit: `docs/feature-automatic-brightness-control.md` (the T2/T3 `Files:`
  hints and the T1/T2/T3 `Implementation Notes` file lists for the moved main files; the `core/src/test/**`
  entries were left untouched because the test layout did not change) and
  `docs/feature-manual-brightness-offset.md` (the T3 `:core/.../BrightnessContext/**` example).
  `docs/feature-background-service.md`, `docs/feature-status-dashboard.md` and
  `docs/EPIC-ambient-brightness-controller.md` reference the moved types only as type names in prose,
  never by path, and `docs/feature-ambient-light-sensing.md` no longer carries a
  `BrightnessMappingCurve`/`HysteresisGate` path after T3, so they needed no change (verified with
  `rg 'core/(AutomaticBrightnessController|BrightnessContext|BrightnessRange|BrightnessSnapshot|BrightnessStatus|BrightnessTransform|BrightnessValue|BrightnessWriteResult|BuiltInAdaptiveBrightnessState|DeviceBrightnessRangeClient|SystemBrightnessClient|WriteSettingsPermissionChecker|BrightnessMappingCurve|HysteresisGate|BrightnessRangeResolver|BrightnessRangeScaler|MinimumStepDeadBand)' docs`).
- Verification: `.\gradlew.bat --console=plain :core:test :infrastructure:test :app:assembleDebug
  :infrastructure:compileDebugAndroidTestKotlin` → `BUILD SUCCESSFUL`;
  `:core:test` **80 tests / 0 failures / 0 skipped** (15 classes), `:infrastructure:test`
  **26 tests / 0 failures / 0 skipped** (6 classes), `app-debug.apk` produced.
  The full-module re-analysis proof was run as
  `.\gradlew.bat --console=plain --no-build-cache --rerun-tasks :core:clean :core:test`
  → `BUILD SUCCESSFUL` with `:core:compileKotlin`, `:core:compileTestKotlin` and `:core:test` all
  executed (no cache hit), so no incremental state hides a package/classifier error.
- No other deviation; the refactor is move/rename/package/import only — no signature, logic or
  formatting change.

### T2 — Folder/package guard test for `:core`

- Added `core/src/test/kotlin/com/ambientbrightness/core/ArchitectureStructureTest.kt`, package
  `com.ambientbrightness.core`, so the guard's own folder/package pair satisfies the rule it checks.
  Test-only change: no production file, no other file touched, no build/config change, no new
  dependency, no fixture.
- One `@Test` collects all violations of both rules and asserts once, so a single run reports **every**
  offender instead of the first. The class name is
  `ArchitectureStructureTest` with the test
  `every source file lives in the folder its package names and imports no Android type`:
  1. **Directory ↔ package consistency.** Walks `File("src/main/kotlin")` (JUnit's working directory is
     the module directory `core/`) over all `*.kt` files, reads each with `Charsets.UTF_8`, skips
     leading blank lines and `//` / `/* … */` / KDoc comments, takes the first `package` declaration
     and compares it with the package derived from the file's own folder relative to
     `src/main/kotlin/com/ambientbrightness/`. A file inside that subtree without a `package` line and
     a file outside the subtree are violations too. Every message names the offending path plus both
     package strings. `assertTrue(sourceRoot.isDirectory, …)` (message names the working directory)
     makes a wrong working directory fail instead of passing vacuously.
  2. **Layer purity.** Import lines only, via `Regex("^import\s+android\.")` over the same
     comment-aware line scan — no whole-file `contains("android.")`, so prose and commented-out imports
     cannot fire it.
- Verification (positive): `.\gradlew.bat --console=plain :core:test :infrastructure:test
  :app:assembleDebug` → `BUILD SUCCESSFUL`; `:core:test` **81 tests / 0 failures / 0 skipped**
  (16 classes = 15 baseline + the guard; 80 baseline tests + 1 guard), `:infrastructure:test`
  **26 tests / 0 failures / 0 skipped** (6 classes), `app-debug.apk` produced. An executed, uncached
  run `.\gradlew.bat --console=plain --no-build-cache :core:clean :core:test` ran
  `:core:compileKotlin`, `:core:compileTestKotlin` and `:core:test` and reported the same 81/0/0.
- The guard is not accidentally skipped: `.\gradlew.bat --console=plain :core:test --tests
  "com.ambientbrightness.core.ArchitectureStructureTest"` executes exactly that class
  (1 test, 0 failures, 0.084 s), and the per-class report
  `core/build/test-results/test/TEST-com.ambientbrightness.core.ArchitectureStructureTest.xml`
  is written on every run.
- Negative check: a temporary `core/src/main/kotlin/com/ambientbrightness/core/GuardScratch.kt` with
  `package com.ambientbrightness.core.wrong` (plus `GuardScratchNoPackage.kt` without any `package`
  line) made `:core:test` fail — `81 tests completed, 1 failed` — with
  `src/main/kotlin/com/ambientbrightness/core/GuardScratch.kt declares package
  'com.ambientbrightness.core.wrong' but its folder requires 'com.ambientbrightness.core'` and
  `src/main/kotlin/com/ambientbrightness/core/GuardScratchNoPackage.kt has no package declaration; its
  folder requires 'com.ambientbrightness.core'` in the **same** assertion. Both scratch files were
  deleted, `:core:test` is green again (81/0/0) and `git status --short` is clean. A third scratch file
  whose *comments* carried a wrong `package` line and an `import android.os.Bundle` line kept the guard
  green, proving the comment-aware scan does not false-positive on prose.
- The guard reported **no violation** on the T3/T4 layout and no `android.` import exists anywhere in
  `core/src/main/**`, so nothing had to be excluded, weakened or marked `@Disabled`; the rule is green
  the moment it is added.
- Docs: no feature-doc path change in this task — the guard adds no production file, so it invalidates
  no documented `:core` path. The T2 checklist entry already names the one added file, so it needed no
  file list addition.

### T5 — Merge interface and default implementation folders

- The `BrightnessMappingCurve`/`HysteresisGate` merges named in this task were already delivered by T3
  (blocker fix, commit `ed38782`), so only the `ReadingStabiliser` merge was left and executed here.
- Move (`git mv`, history preserved): `core/src/main/kotlin/com/ambientbrightness/core/AmbientLightSensing/OutlierClampingStabiliser/OutlierClampingStabiliser.kt`
  → `core/src/main/kotlin/com/ambientbrightness/core/AmbientLightSensing/ReadingStabiliser/OutlierClampingStabiliser.kt`;
  the emptied `OutlierClampingStabiliser/` directory was deleted. `ReadingStabiliser/` now contains
  `ReadingStabiliser.kt` + `OutlierClampingStabiliser.kt` (interface and its default implementation
  share the folder, same pattern as `BrightnessMappingCurve/`/`HysteresisGate/`).
- New package: `com.ambientbrightness.core.AmbientLightSensing.OutlierClampingStabiliser` →
  `com.ambientbrightness.core.AmbientLightSensing.ReadingStabiliser` (it takes over the interface's
  package). The class name stays `OutlierClampingStabiliser`; `ReadingStabiliser.kt` was not touched.
  The import of `ReadingStabiliser` inside the moved file became a same-package self-import and was
  dropped (same shape as T3's `DefaultBrightnessMappingCurve`/`DefaultHysteresisGate` cleanup) — the
  only other change in that file is the `package` line.
- Import sites rewritten (import lines only): `app/src/main/kotlin/com/ambientbrightness/app/AmbientBrightnessComponent/AmbientBrightnessComponent.kt`
  and `core/src/test/kotlin/com/ambientbrightness/core/OutlierClampingStabiliser/OutlierClampingStabiliserTest.kt`
  (the test keeps its own folder/package `com.ambientbrightness.core.OutlierClampingStabiliser`; only
  its import of the production class changed). No file in `infrastructure/src/main/**`,
  `infrastructure/src/test/**`, `infrastructure/src/androidTest/**` or `app/src/androidTest/**`
  referenced the type, verified with `git grep -n "AmbientLightSensing.OutlierClampingStabiliser"`.
- Docs (paths only): `docs/feature-ambient-light-sensing.md` — the T3 `Files added:` list entry
  `AmbientLightSensing/{OutlierClampingStabiliser/OutlierClampingStabiliser,…}` became
  `AmbientLightSensing/{ReadingStabiliser/OutlierClampingStabiliser,…}`; its `:core/src/test/.../`
  entry needed no change because the test layout is untouched. `docs/feature-automatic-brightness-control.md`
  (the file the checklist names) carries no `ReadingStabiliser/` or `OutlierClampingStabiliser/` path
  after T3 — verified with `git grep -n "ReadingStabiliser/\|OutlierClampingStabiliser/" -- docs` — so
  it needed no edit; `docs/feature-response-tuning.md` mentions the type by name only. The historical
  path list in the T3 entry above is left as the T3 record (this task touches no other task section),
  so T10's path sweep must treat it as history, not as a live path.
- Verification (all green): `.\gradlew.bat --console=plain :core:test :infrastructure:test :app:assembleDebug :infrastructure:compileDebugAndroidTestKotlin`
  → `BUILD SUCCESSFUL in 3s`, `:core:test` **81 tests / 0 failures / 0 skipped** (16 classes),
  `:infrastructure:test` **26 tests / 0 failures / 0 skipped** (6 classes), `app-debug.apk` produced.
  Cache-busting run `.\gradlew.bat --console=plain --no-build-cache :core:clean :core:test` →
  `BUILD SUCCESSFUL in 4s` with `:core:compileKotlin`, `:core:compileTestKotlin` and `:core:test`
  all executed (81/0/0), so no incremental state hides a package/classifier error.
- Structural checks performed (script over every `core/src/main/kotlin/com/ambientbrightness/core/**/*.kt`):
  (1) every file's `package` line equals the package derived from its own folder — zero mismatches, so
  no folder name differs from the type it defines except the intended shared folders
  (`ReadingStabiliser/`, `BrightnessMappingCurve/`, `HysteresisGate/`, plus the pre-existing
  `AmbientLightService/`, `LightLevel/` and the flat T4 contract files) — and the T2
  `ArchitectureStructureTest` stays green unmodified; (2) for every package the simple name of its last
  segment was compared with the classifiers declared in its parent package: **no collision**, i.e. no
  package's FQ name equals the FQ name of a classifier in its parent package and nothing named
  `ReadingStabiliser` exists in `com.ambientbrightness.core.AmbientLightSensing`.
- No signature, visibility, KDoc, behaviour or formatting change; one commit for the move, the two
  import fixes and the doc path. Working tree clean afterwards.

### T6 — Give the use case its folder-local collaborators

- Done in one commit, `refactor(core): extract LatestReading into the use case Task folder` (the SHA
  cannot appear in its own commit; the T10 sweep records it).
- Extracted type: `LatestReading`, the `private class` that sat at the bottom of
  `core/src/main/kotlin/com/ambientbrightness/core/AmbientLightSensing/AmbientLightUseCase/AmbientLightUseCase.kt`,
  now in `core/src/main/kotlin/com/ambientbrightness/core/AmbientLightSensing/AmbientLightUseCase/Task/LatestReading.kt`
  with package `com.ambientbrightness.core.AmbientLightSensing.AmbientLightUseCase.Task` (folder and
  package agree, so the T2 `ArchitectureStructureTest` stays green) and visibility `internal` —
  `...AmbientLightSensing.AmbientLightUseCase` and `...AmbientLightUseCase.Task` are sibling packages
  inside the same `:core` module, and the holder must not leak to `:app`/`:infrastructure`. The body
  is identical (both `@Volatile` fields, `consumedVersion`, `record`, `takeIfNew`) and its KDoc moved
  with the class; the only addition is the `...AmbientLightSensing.LuxSample` import.
- `AmbientLightUseCase.kt` no longer declares the class and gained
  `import com.ambientbrightness.core.AmbientLightSensing.AmbientLightUseCase.Task.LatestReading`.
  `samples()` still calls `LatestReading()` / `record` / `takeIfNew` unchanged. The `LuxSample`
  import stayed: the checklist phrased it as "its now-unused import", but the signature
  `samples(): Flow<LuxSample>` still needs it, so removing it would not compile.
  `DEFAULT_SAMPLING_INTERVAL_MILLIS` stayed in place (public API used by `:app` and the tests) and
  `AmbientLightService` was untouched — T7 owns the remainder.
- No `Request`/`Response` types, no signature or behaviour change, and `AmbientLightUseCaseTest` is
  unmodified (confirmed with `git status --short`: no `:core/src/test/**` path is listed).
- Streaming exception (decision 3) recorded once, in the architecture prose of
  `docs/feature-ambient-light-sensing.md`, as a continuation of the `AmbientLightUseCase` bullet in
  `### Architecture & Components`: `AmbientLightUseCase.samples()` and the pinned
  `AmbientLightService.start(): Flow<LuxSample>` intentionally omit the `Request`/`Response` pair,
  because the outcome is a stream, not a value object — the `Flow` *is* the return channel and
  `start()` is the fixed contract F2–F7 build on. In the same file the explicit T3 `Files added:`
  list got an appended note naming the new `AmbientLightUseCase/Task/LatestReading.kt` path (history
  kept, nothing rewritten); the wildcard `*Files: .../AmbientLightUseCase/**.*` entry of that task
  already covers the new file, so it needed no edit.
- Verification (all green): `.\gradlew.bat --console=plain :core:test :infrastructure:test
  :app:assembleDebug :infrastructure:compileDebugAndroidTestKotlin` → `BUILD SUCCESSFUL in 2s`;
  `:core:test` **81 tests / 0 failures / 0 skipped** (16 classes, including the T2 guard),
  `:infrastructure:test` **26 tests / 0 failures / 0 skipped** (6 classes), `app-debug.apk` produced.
  Cache-busting run `.\gradlew.bat --console=plain --no-build-cache :core:clean :core:test` →
  `BUILD SUCCESSFUL in 4s` with `:core:compileKotlin`, `:core:compileTestKotlin` and `:core:test` all
  executed (81 / 0 / 0), so no incremental state hides a folder/package mismatch. Working tree clean
  afterwards, no untracked leftovers.

### T7 — Break `DefaultAmbientLightService` into `Task/` classes

- Done in one commit, `refactor(core): split DefaultAmbientLightService into Task collaborators` (the
  SHA cannot appear in its own commit; the T10 sweep records it).
- Two files added, both `internal`, package
  `com.ambientbrightness.core.AmbientLightSensing.AmbientLightService.Task` (folder and package agree,
  so the T2 `ArchitectureStructureTest` stays green):
  - `core/src/main/kotlin/com/ambientbrightness/core/AmbientLightSensing/AmbientLightService/Task/CollectionSessionRegistry.kt`
    — owns `collectionMutex` (as `lockCollection()`/`unlockCollection()`, kept explicit; the
    `channelFlow` body is *not* wrapped in a `withLock` helper), `activeJobLock`, `activeJob`,
    `registerSession`, `releaseSession`, `publishIfActive` (guarded by the availability
    `MutableStateFlow` injected as `availabilitySink`) and `cancelActiveSession()` for `stop()`'s
    cancellation lookup.
  - `core/src/main/kotlin/com/ambientbrightness/core/AmbientLightSensing/AmbientLightService/Task/StartupAvailabilityGate.kt`
    — owns the `require(startupTimeoutMillis > 0L) { "startupTimeoutMillis must be positive" }`
    validation and, via `newWatchdog()`, the `CompletableDeferred`-based `FirstSampleWatchdog` with
    `markFirstSample()` (idempotent `complete(Unit)`, called on every sample) and
    `awaitFirstSample()` (`withTimeoutOrNull(startupTimeoutMillis) { firstSample.await(); true } ?: false`).
    `FirstSampleWatchdog` lives in the same file, so only these two files were added.
- `DefaultAmbientLightService` keeps its KDoc, its constructor (`sensorClient`, `useCase`,
  `startupTimeoutMillis = DEFAULT_SENSOR_STARTUP_TIMEOUT_MILLIS` — now a plain parameter instead of a
  `private val`, because the gate owns the value; the parameter name and default are unchanged, so
  positional and named callers are unaffected), `availability` (`asStateFlow()` view), the
  `initialAvailability` derivation and `start()`/`stop()`. It gained two service properties,
  `startupGate = StartupAvailabilityGate(startupTimeoutMillis)` (declared first, where the `init`
  block sat, so the `IllegalArgumentException` still fires during service construction and before
  `initialAvailability` is derived) and `sessions = CollectionSessionRegistry(mutableAvailability)`,
  and every former inline member is now a delegation: `lockCollection`/`unlockCollection`/`registerSession`
  around the `try`, `publishIfActive` at the four publish sites, `releaseSession` + `unlockCollection`
  in the outer `finally` in that exact order, `watchdog.markFirstSample()` in the collector and
  `sessions.cancelActiveSession()` in `stop()`. Unchanged: `launch(start = CoroutineStart.UNDISPATCHED)`,
  both `sampleJob.cancelAndJoin()` calls (inner `finally` and timeout branch) in place, the whole
  `try`/`finally` nesting with the `return@channelFlow` statements, and `stop()`'s unregister-before-cancel
  order that skips both calls when there is no active session. No reformatting, logic change or extra
  abstraction; `OutlierClampingStabiliser`/`MinimumStepDeadBand` were left untouched (decision 4).
- Docs corrected in the same commit: `docs/feature-ambient-light-sensing.md` — appended (history kept,
  nothing rewritten) a T7 note after the T4 entry's `Files changed:`/test list, naming the two new
  `AmbientLightService/Task/**` paths; the wildcard `AmbientLightSensing/AmbientLightService/**` entry of
  T4 already covers them.
- Verification (all green): `.\gradlew.bat --console=plain :core:test :infrastructure:test
  :app:assembleDebug :infrastructure:compileDebugAndroidTestKotlin` → `BUILD SUCCESSFUL in 2s`;
  `:core:test` **81 tests / 0 failures / 0 skipped** (16 classes, including the T2 guard),
  `:infrastructure:test` **26 tests / 0 failures / 0 skipped** (6 classes), `app-debug.apk` produced.
  Focused run `.\gradlew.bat --console=plain :core:test --tests "*AmbientLightService*"
  --tests "*DefaultAmbientLightServiceTest*" --rerun-tasks` → `BUILD SUCCESSFUL in 3s`, 9 tests /
  0 failures: `DefaultAmbientLightServiceTest` 5/0 (no sensor, first-reading/profile, silent-sensor
  timeout, zero lux, cancellation/stop) plus `AmbientLightServiceContractTest` 4/0. Cache-busting run
  `.\gradlew.bat --console=plain --no-build-cache :core:clean :core:test` → `BUILD SUCCESSFUL in 4s`
  with `:core:compileKotlin`, `:core:compileTestKotlin` and `:core:test` all executed (81 / 0 / 0), so
  no incremental state hides a folder/package mismatch. `git status --short` lists only the modified
  service plus the new `Task/` directory and the two docs — no path under `core/src/test/**`, so both
  service tests are green unmodified.

### T8 — Regroup `:infrastructure` by responsibility → client → variant

- Done in one commit, `refactor(infrastructure): regroup adapters by responsibility, client and variant`
  (the SHA cannot appear in its own commit; the T10 sweep records it).
- Eleven main files moved with `git mv` inside a fresh responsibility → client → variant tree; the
  emptied flat `Android*/`/`Dummy*/` directories were deleted, so
  `infrastructure/src/main/kotlin/com/ambientbrightness/infrastructure/` now contains only
  `AmbientLightSensing/` and `AutomaticBrightnessControl/`. Old → new mapping (path **and** package):

| Old path (package `com.ambientbrightness.infrastructure`) | New path | New package suffix |
| --- | --- | --- |
| `AndroidAmbientLightSensorClient/AndroidAmbientLightSensorClient.kt` | `AmbientLightSensing/AmbientLightSensorClient/Android/AndroidAmbientLightSensorClient.kt` | `.AmbientLightSensing.AmbientLightSensorClient.Android` |
| `DummyAmbientLightSensorClient/DummyAmbientLightSensorClient.kt` | `AmbientLightSensing/AmbientLightSensorClient/Dummy/DummyAmbientLightSensorClient.kt` | `.AmbientLightSensing.AmbientLightSensorClient.Dummy` |
| `AndroidScreenStateClient/AndroidScreenStateClient.kt` | `AmbientLightSensing/ScreenStateClient/Android/AndroidScreenStateClient.kt` | `.AmbientLightSensing.ScreenStateClient.Android` |
| `DummyScreenStateClient/DummyScreenStateClient.kt` | `AmbientLightSensing/ScreenStateClient/Dummy/DummyScreenStateClient.kt` | `.AmbientLightSensing.ScreenStateClient.Dummy` |
| `SensorProfileFactory/SensorProfileFactory.kt` | `AmbientLightSensing/SensorProfileFactory/SensorProfileFactory.kt` | `.AmbientLightSensing.SensorProfileFactory` |
| `AndroidSystemBrightnessClient/AndroidSystemBrightnessClient.kt` | `AutomaticBrightnessControl/SystemBrightnessClient/Android/AndroidSystemBrightnessClient.kt` | `.AutomaticBrightnessControl.SystemBrightnessClient.Android` |
| `DummySystemBrightnessClient/DummySystemBrightnessClient.kt` | `AutomaticBrightnessControl/SystemBrightnessClient/Dummy/DummySystemBrightnessClient.kt` | `.AutomaticBrightnessControl.SystemBrightnessClient.Dummy` |
| `AndroidDeviceBrightnessRangeClient/AndroidDeviceBrightnessRangeClient.kt` | `AutomaticBrightnessControl/DeviceBrightnessRangeClient/Android/AndroidDeviceBrightnessRangeClient.kt` | `.AutomaticBrightnessControl.DeviceBrightnessRangeClient.Android` |
| `DummyDeviceBrightnessRangeClient/DummyDeviceBrightnessRangeClient.kt` | `AutomaticBrightnessControl/DeviceBrightnessRangeClient/Dummy/DummyDeviceBrightnessRangeClient.kt` | `.AutomaticBrightnessControl.DeviceBrightnessRangeClient.Dummy` |
| `AndroidWriteSettingsPermissionChecker/AndroidWriteSettingsPermissionChecker.kt` | `AutomaticBrightnessControl/WriteSettingsPermissionChecker/Android/AndroidWriteSettingsPermissionChecker.kt` | `.AutomaticBrightnessControl.WriteSettingsPermissionChecker.Android` |
| `DummyWriteSettingsPermissionChecker/DummyWriteSettingsPermissionChecker.kt` | `AutomaticBrightnessControl/WriteSettingsPermissionChecker/Dummy/DummyWriteSettingsPermissionChecker.kt` | `.AutomaticBrightnessControl.WriteSettingsPermissionChecker.Dummy` |

- Ten of the eleven files changed only their `package` line. `AndroidAmbientLightSensorClient.kt` gained
  one import (`...AmbientLightSensing.SensorProfileFactory.SensorProfileFactory`) because it called the
  factory unqualified from the former shared package. No class, function or file was renamed, no
  visibility changed — `brightnessRangeFromMaximum`, `readBrightnessSetting` and `writeBrightnessSetting`
  stay `internal` top-level functions in their new `.../Android/` packages — and no logic, KDoc or
  formatting was touched.
- Package/classifier trap avoided as designed: every adapter sits in `Android/` or `Dummy/` under its
  `<Client>/` folder, and only `SensorProfileFactory.kt` sits one level below `AmbientLightSensing/`
  without a variant level — the same shape T3/T4 used for the `:core` port folders. No package's FQ name
  equals the FQ name of a classifier declared in its parent package.
- Import consequences (imports only, no test regroup, no assertion touched):
  - `app/src/main/kotlin/com/ambientbrightness/app/AmbientBrightnessApplication.kt` (2 imports) and
    `app/src/androidTest/kotlin/com/ambientbrightness/app/AndroidBrightnessAdaptersTest.kt` (3 imports);
    that instrumented test permanently stays in `:app` — the original plan had T9 relocating it into
    `:infrastructure`, which T9 attempted and reverted in `00012a6` (see T9).
  - The six test files keep their folder and package `com.ambientbrightness.infrastructure` at the
    module root, so they lost the same-package visibility of both the adapters and the `internal`
    helpers and gained explicit imports: `AndroidDeviceBrightnessRangeClientTest`
    (`brightnessRangeFromMaximum`), `AndroidSystemBrightnessClientTest` (`readBrightnessSetting`,
    `writeBrightnessSetting`), `DummyAmbientLightSensorClientTest`, `DummyScreenStateClientTest`,
    `SensorProfileFactoryTest` and `DummyBrightnessAdaptersTest` (three dummy adapters).
    `infrastructure/src/androidTest/.../AndroidAmbientLightSensorClientTest.kt` imports
    `AndroidAmbientLightSensorClient` for the same reason.
- Guard added: `infrastructure/src/test/kotlin/com/ambientbrightness/infrastructure/ArchitectureStructureTest.kt`,
  package `com.ambientbrightness.infrastructure`, so its own folder/package pair satisfies the rule it
  checks. It mirrors the T2 `:core` guard's folder↔package logic exactly — `File("src/main/kotlin")`
  walk, base directory `src/main/kotlin/com/ambientbrightness`, expected package = `com.ambientbrightness`
  + folder segments, comment-aware `package` parsing, a file outside the base tree or without a
  `package` line counted as a violation, all violations collected and asserted once, and the non-vacuous
  `sourceRoot.isDirectory` assertion whose message names the working directory. Two deliberate
  differences: the `import android.` purity check is **omitted** (`:infrastructure` *is* the Android
  adapter layer), and the JUnit 4 flavour (`org.junit.Test` / `org.junit.Assert.assertTrue`, message
  first) is used because `:infrastructure` depends on `libs.junit` only — the `:core` guard's
  `org.junit.jupiter.api` API does not exist in this module. No other assertion was added.
- Negative proof (the guard cannot pass vacuously): a temporary
  `infrastructure/src/main/kotlin/com/ambientbrightness/infrastructure/GuardScratch.kt` declaring
  `package com.ambientbrightness.infrastructure.wrong` plus a `GuardScratchNoPackage.kt` without any
  `package` line made the guard fail — `1 test completed, 1 failed` — with both offenders in the
  **same** assertion:
  `src/main/kotlin/com/ambientbrightness/infrastructure/GuardScratch.kt declares package 'com.ambientbrightness.infrastructure.wrong' but its folder requires 'com.ambientbrightness.infrastructure'`
  and `src/main/kotlin/com/ambientbrightness/infrastructure/GuardScratchNoPackage.kt has no package
  declaration; its folder requires 'com.ambientbrightness.infrastructure'`. A third scratch file whose
  comments and KDoc carried a wrong `package` line and an `import android.os.Bundle` line stayed green,
  proving the comment-aware scan does not false-positive on prose. All three scratch files were deleted
  and the guard is green again. The guard is non-vacuous positively as well: it reports **no violation**
  on the T8 layout, so nothing had to be excluded or weakened.
  Focused run `.\gradlew.bat --console=plain :infrastructure:testDebugUnitTest --tests
  "com.ambientbrightness.infrastructure.ArchitectureStructureTest" --rerun-tasks` → 1 test / 0 failures
  (`:infrastructure:test` is a lifecycle task that rejects `--tests`, hence the variant-level task).
- Docs corrected in the same commit (paths fixed in place, marker appended, narrative untouched):
  `docs/feature-ambient-light-sensing.md` (the T2 `Files added:` list around the `commit d650dc4` block:
  the five main paths now point into `AmbientLightSensing/.../{Android,Dummy}/` + `(regrouped by T8)`; the
  `:infrastructure/src/test/**` and `:infrastructure/src/androidTest/**` entries stayed valid because the
  tests do not move) and `docs/feature-automatic-brightness-control.md` (the six `infrastructure/src/main/**`
  entries of the T2 `Files changed:` list now point into `AutomaticBrightnessControl/.../{Android,Dummy}/`
  + `(regrouped by T8)`; the three `infrastructure/src/test/**` entries and the `app/src/androidTest/**`
  / `app/src/main/AndroidManifest.xml` entries are unchanged and still valid). Wildcard paths such as
  `:infrastructure/src/main/kotlin/com/ambientbrightness/infrastructure/**` in
  `docs/feature-background-service.md`, `docs/feature-response-tuning.md` and
  `docs/feature-permissions-setup-flow.md` remain valid and were deliberately left untouched. A grep for
  the old flat usage (`git grep -n -E "com\.ambientbrightness\.infrastructure\.[A-Z]" -- app/src
  infrastructure/src core/src`) returns only new-style package/import lines — no stale
  `com.ambientbrightness.infrastructure.<Adapter>` reference remains.
- Verification (all green): `.\gradlew.bat --console=plain :core:test :infrastructure:test
  :app:assembleDebug :infrastructure:compileDebugAndroidTestKotlin` → `BUILD SUCCESSFUL in 3s`;
  `:core:test` **81 tests / 0 failures / 0 skipped** (16 classes, unchanged), `:infrastructure:test`
  **27 tests / 0 failures / 0 skipped** (7 classes = 6 baseline + the guard), `app-debug.apk` produced,
  `:infrastructure:compileDebugAndroidTestKotlin` green (instrumented tests compiled only, no emulator
  and no `connectedAndroidTest`). Cache-busting runs:
  `.\gradlew.bat --console=plain --no-build-cache :infrastructure:clean :infrastructure:test` →
  `BUILD SUCCESSFUL in 3s` with `:infrastructure:compileDebugKotlin`,
  `:infrastructure:compileDebugUnitTestKotlin` and `:infrastructure:testDebugUnitTest` all executed
  (27 / 0 / 0), and `.\gradlew.bat --console=plain --no-build-cache :core:clean :core:test` →
  `BUILD SUCCESSFUL in 3s` (81 / 0 / 0), so no incremental state hides a folder/package mismatch.
  A combined `--no-build-cache` invocation of all four configuration-cache tasks failed with
  *"Unable to make progress running work"* (a `clean` task racing its own consumer in one task graph), so
  the two clean/test pairs were run as separate commands as the checklist phrases them.
- No deviation from the T8 design; the refactor is move/package/import only, the only test edits are
  added imports plus the new guard, and every existing test count is unchanged.

### T9 — Structure the `:app` application layer

- Done in **two** commits: `d7e660c` `refactor(app): separate the composition root, collector and view`
  and, after the correction described below, `00012a6` `fix(app): keep the android brightness adapter test
  on the app instrumentation target` (a commit cannot cite its own SHA; the T10 sweep records both).
- Three files moved with `git mv` and one composable extracted into a new file; the emptied
  `AmbientBrightnessComponent/` and `AmbientLightCollector/` directories were deleted, so
  `app/src/main/kotlin/com/ambientbrightness/app/` now contains only `MainActivity.kt` plus the
  `Composition/` and `AmbientLightSensing/` trees. Old → new mapping (path **and** package):

| Old path (package `com.ambientbrightness.app`) | New path | New package |
| --- | --- | --- |
| `AmbientBrightnessApplication.kt` | `Composition/AmbientBrightnessApplication.kt` | `com.ambientbrightness.app.Composition` |
| `AmbientBrightnessComponent/AmbientBrightnessComponent.kt` | `Composition/AmbientBrightnessComponent.kt` | `com.ambientbrightness.app.Composition` |
| `AmbientLightCollector/AmbientLightCollector.kt` | `AmbientLightSensing/Collector/AmbientLightCollector.kt` | `com.ambientbrightness.app.AmbientLightSensing.Collector` |
| `MainActivity.kt` (root, plus the `private @Composable AmbientBrightnessApp()` it declared) | `MainActivity.kt` (root, package unchanged) + new `AmbientLightSensing/View/AmbientBrightnessView.kt` | `com.ambientbrightness.app.AmbientLightSensing.View` |

- View extraction: the composable body moved byte-identically
  (`MaterialTheme { Scaffold(modifier = Modifier.fillMaxSize(), containerColor =
  MaterialTheme.colorScheme.background) { } }`) into the new `AmbientLightSensing/View/AmbientBrightnessView.kt`;
  besides the file/package move the only change is the visibility, `private` → `internal` (a top-level
  `private` function is file-private and could not be called from `MainActivity.kt` in another package).
  The function name `AmbientBrightnessApp` is unchanged, and the file gained one KDoc line describing it
  as the top-level view composable. `MainActivity` keeps its lifecycle logic byte-identically; it now
  imports `...AmbientLightSensing.View.AmbientBrightnessApp` and `...Composition.AmbientBrightnessApplication`
  and lost the five Compose imports (`fillMaxSize`, `MaterialTheme`, `Scaffold`, `Composable`, `Modifier`)
  that only the extracted composable used. `AmbientBrightnessComponent` gained
  `import com.ambientbrightness.app.AmbientLightSensing.Collector.AmbientLightCollector`; the collector and
  the application file changed only their `package` line (the application keeps its two `infrastructure.*`
  imports). No new UI, no ViewModel, no new callback, no behaviour change — the view stays the empty
  `Scaffold`.
- Composition-root isolation enforced structurally (decision 2, whose *Enforcement* clarification is
  appended there). Grep proof: `git grep -n "com\.ambientbrightness\.infrastructure\." -- app/src` returns
  exactly two hits, both under `Composition/` —
  `app/src/main/kotlin/com/ambientbrightness/app/Composition/AmbientBrightnessApplication.kt:7`
  (`...AmbientLightSensing.AmbientLightSensorClient.Android.AndroidAmbientLightSensorClient`) and `:8`
  (`...AmbientLightSensing.ScreenStateClient.Android.AndroidScreenStateClient`). No file under
  `AmbientLightSensing/**` and not `MainActivity.kt` references `infrastructure.*`; they depend on `:core`
  plus the component facade. No automated `:app` guard was added (declined by the Project Owner, see
  decision 2), so the isolation stays grep-verified here and in T10.
- Package/classifier trap avoided as designed: every file lands in `Composition/`,
  `AmbientLightSensing/Collector/` or `AmbientLightSensing/View/`, and no file sits directly in
  `.../app/AmbientLightSensing/`, so no package name equals the FQ name of a classifier declared in its
  parent package.
- Adapter test: the plan's relocation was **attempted and reverted**. `d7e660c` moved
  `app/src/androidTest/kotlin/com/ambientbrightness/app/AndroidBrightnessAdaptersTest.kt` to
  `infrastructure/src/androidTest/kotlin/com/ambientbrightness/infrastructure/AndroidBrightnessAdaptersTest.kt`
  (package line only; imports, class name, `@RunWith`, the three tests, the assertions, the `assumeTrue`
  guards and the two private helpers byte-identical, with `:infrastructure:compileDebugAndroidTestKotlin`
  green), and `00012a6` moved it back to the `:app` path with `package com.ambientbrightness.app`, so
  `git diff cea6bfc HEAD -- app/src/androidTest/kotlin/com/ambientbrightness/app/AndroidBrightnessAdaptersTest.kt`
  is **empty**. Reason: the relocation is a runtime regression that no compile task can detect — the
  `:infrastructure` library manifest is empty (no `WRITE_SETTINGS`), its androidTest instrumentation target
  is `com.ambientbrightness.infrastructure.test`, so `assumeTrue(Settings.System.canWrite(…))` silently skips
  one test, the hard `assertTrue("The test requires adb-granted WRITE_SETTINGS.", …)` fails, and
  `setWriteSettingsAppOpMode` grants the AppOps mode to `com.ambientbrightness.app`, a package the test
  process is not running as. `docs/feature-automatic-brightness-control.md` records that these tests were
  moved **into** `:app` for exactly this reason and passed 5/5 on the API 36 emulator afterwards. `:app`
  therefore keeps its instrumented adapter test on the app instrumentation target.
- `AmbientBrightnessComponentTest.kt` stays in `app/src/androidTest/kotlin/com/ambientbrightness/app/` with
  one added import, `com.ambientbrightness.app.Composition.AmbientBrightnessComponent`; its assertions are
  untouched. No build file changed: `app/build.gradle.kts` keeps `implementation(project(":infrastructure"))`
  (the composition root constructs the Android adapters) and `infrastructure/build.gradle.kts` already
  declared `androidTestImplementation(libs.junit)`, `libs.androidx.test.runner` and
  `libs.androidx.test.ext.junit`; `:app` nevertheless keeps the instrumented adapter test, because it needs
  the **app** instrumentation target rather than the library's (see the correction above).
- `app/src/main/AndroidManifest.xml` follows the move: `android:name=".AmbientBrightnessApplication"` →
  `android:name=".Composition.AmbientBrightnessApplication"` (source edit only, no build-file change; the
  `.MainActivity` entry is unaffected because `MainActivity` stays in the package root). No compile task
  covers this rename.
- Docs corrected in the same commit (paths fixed in place, marker `(moved by T9)` appended, surrounding
  narrative untouched):
  - `docs/feature-ambient-light-sensing.md`: the T4 `Files:` entry
    `:app/.../app/AmbientLightCollector/**` → `:app/.../app/AmbientLightSensing/Collector/**`; the T4
    `Done` file list entry for `AmbientLightCollector.kt`; and the T5 `Files changed:` entries for
    `AmbientBrightnessApplication.kt`, `AmbientBrightnessComponent.kt` and `AmbientLightCollector.kt`
    (now `Composition/` and `AmbientLightSensing/Collector/`). `MainActivity.kt`,
    `app/src/main/AndroidManifest.xml`, `app/build.gradle.kts`, the remaining androidTest path and the
    `:app/src/main/kotlin/com/ambientbrightness/app/**` wildcard stayed valid and were not touched.
  - `docs/feature-automatic-brightness-control.md`: `d7e660c` had repointed the
    `AndroidBrightnessAdaptersTest.kt` entry of the F2 `Files changed:` list and the *"The actual brightness
    tests are in …"* sentence at `infrastructure/src/androidTest/...`; `00012a6` restored both to the `:app`
    path verbatim and added one sentence recording why the test has to stay on the app instrumentation
    target.
  - `docs/feature-background-service.md`, `feature-response-tuning.md`, `feature-manual-brightness-offset.md`,
    `feature-permissions-setup-flow.md`, `feature-status-dashboard.md`: inspected, no change needed — their
    `:app/src/main/kotlin/com/ambientbrightness/app/**` entries are wildcards that still match and every
    concrete path they name (`AndroidManifest.xml`, `MainActivity.kt`, `res/values/…`) still exists.
- Verification (all green, exact commands):
  - `.\gradlew.bat --console=plain :core:test :infrastructure:test :app:assembleDebug
    :infrastructure:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin` →
    `BUILD SUCCESSFUL in 4s` (76 actionable tasks: 14 executed, 5 from cache, 57 up-to-date) with
    `:app:packageDebug` producing `app-debug.apk`.
  - `:core:test` **81 tests / 0 failures / 0 skipped** (16 classes, unchanged);
    `:infrastructure:test` **27 tests / 0 failures / 0 skipped** (7 classes, unchanged; re-run with
    `.\gradlew.bat --console=plain --no-build-cache :infrastructure:test --rerun-tasks` →
    `BUILD SUCCESSFUL in 3s`, 17 tasks executed).
  - Cache-busting after the moves, as two **separate** invocations (one graph containing both `clean`
    tasks aborts with Gradle's *"Unable to make progress running work"*):
    `.\gradlew.bat --console=plain --no-build-cache :app:clean :app:assembleDebug` →
    `BUILD SUCCESSFUL in 6s` (37 executed) with `:app:compileDebugKotlin`, `:app:packageDebug` and
    `:app:assembleDebug` executed, and
    `.\gradlew.bat --console=plain --no-build-cache :core:clean :core:test` →
    `BUILD SUCCESSFUL in 3s` with `:core:compileKotlin`, `:core:compileTestKotlin` and `:core:test`
    executed (81 / 0 / 0).
  - Only the instrumented tests were compiled — no emulator was provisioned and `connectedAndroidTest` was
    not run, per the checklist. Nothing greyed out: no `@Disabled`, no `@Ignore`, no assertion edited and no
    test count changed.
- Deviations: none from the clarified T9 design, plus **one correction to the plan itself** — the adapter
  test relocation was wrong and was reverted in `00012a6` (see that paragraph). Two ambiguities were
  escalated to the Technical Architect before implementation and resolved in this document: (1) a test that
  declares the module-root `com.ambientbrightness.infrastructure` package must keep its three adapter
  imports (post-T8 those adapters live in sub-packages, so dropping them cannot compile) and (2) the
  `AndroidManifest.xml` entry must follow the `AmbientBrightnessApplication` move. Residual risk: the
  manifest rename is invisible to every compile task and no emulator was attached during this run, so
  neither the manifest rename nor the instrumented adapter test could be exercised on a device — both are
  documented in `docs/feature-automatic-brightness-control.md` and must be re-checked by the next
  `connectedDebugAndroidTest` run against an API 36 emulator.

