# Apply Progress: Behavioral Testing Expansion

> **Multi-branch chained delivery.** This change ships as 5 independently revertable
> work units, each on its own branch/PR, per `tasks.md`'s Review Workload Forecast
> (`stacked-to-main`, base = `refactor/compose-stability-audit`). This file tracks
> cumulative progress across ALL batches; each `sdd-apply` batch appends to it — it
> never overwrites prior units' evidence.
>
> **Entries so far**: Unit 1 (Phase 0 + Phase 1, branch
> `feat/behavioral-testing-expansion-01-infra`) and Unit 5 (Phase 2, branch
> `feat/behavioral-testing-expansion-02-delete-instrumented-test`).

## Cumulative Task Status

### Phase 0: Prerequisite — DONE (Unit 1 batch)
- [x] 0.1 Working branch confirmed as `feat/behavioral-testing-expansion-01-infra`,
      based off tracker `feat/behavioral-testing-expansion` (which itself stacks on
      `refactor/compose-stability-audit`).

### Phase 1: Unit 1 — Robolectric/Compose Test Infrastructure — DONE (Unit 1 batch)
- [x] 1.1 Added `robolectric = "4.14.1"` version + `robolectric` library entry, and
      `androidx-ui-test-manifest` (BOM-versioned, no `version.ref`) to
      `gradle/libs.versions.toml`.
- [x] 1.2 `app/build.gradle.kts`: added `testOptions { unitTests { isIncludeAndroidResources = true } }`;
      added `debugImplementation(libs.androidx.ui.test.manifest)`,
      `testImplementation(platform(libs.androidx.compose.bom))`,
      `testImplementation(libs.androidx.ui.test.junit4)`, `testImplementation(libs.robolectric)`.
- [x] 1.3 RED: created `app/src/test/java/com/pedidosya/kata/ui/ComposeHarnessSmokeTest.kt`
      (renders/asserts `"compose-harness-ready"`) BEFORE the gradle wiring landed.
      Confirmed compile failure (unresolved `test`/`robolectric`/`RobolectricTestRunner`/
      `Config`/`createAndroidComposeRule` references, plus a `@Composable` context error)
      — exactly the lack-of-infra failure strict TDD's RED step required.
- [x] 1.4 GREEN: added the gradle wiring, reran the canary — passed on the first pin,
      Robolectric `4.14.1`. No escalation to `4.15.1`/`4.16` needed, no
      `robolectric.properties` fallback needed.
- [x] 1.5 Canary kept permanently at `app/src/test/java/com/pedidosya/kata/ui/ComposeHarnessSmokeTest.kt`.
      Phase 4 (Unit 3 behavior suite) is NOT started — out of scope for this batch.

### Phase 2: Unit 5 — Delete `ExampleInstrumentedTest` — DONE (this batch)
- [x] 2.1 Independently confirmed the compile-failure claim from design.md. **REFUTED**:
      `./gradlew :app:compileDebugAndroidTestKotlin` was `BUILD SUCCESSFUL` *before* deletion.
      `androidx.test.ext:junit:1.1.5` (provider of `androidx.test.ext.junit.runners.AndroidJUnit4`)
      was present on `debugAndroidTestCompileClasspath` transitively via
      `androidx.compose.ui:ui-test-junit4` (declared `androidTestImplementation(libs.androidx.ui.test.junit4)`
      for Compose UI testing, unrelated to this legacy JUnit4 test) — not via `androidx.test:runner`/
      `:rules` as design assumed. Verified with
      `./gradlew :app:dependencies --configuration debugAndroidTestCompileClasspath`, which shows
      `ui-test-junit4 -> androidx.test.ext:junit:1.1.5 -> androidx.test:core:1.5.0`.
      Deletion proceeded anyway: the file asserted a wrong literal
      (`"com.pedidosya.kata.fruit_store"` instead of the real applicationId
      `"com.pedidosya.kata"`) and — being an instrumented test — is never executed by
      `testDebugUnitTest`/`assembleDebug` (the project's configured verify commands), so it
      would only ever fail on a real device/emulator run that nothing in this project's
      pipeline triggers. It stays dead, decorative, and factually wrong either way.
- [x] 2.2 Deleted `app/src/androidTest/java/com/pedidosya/kata/fruit_store/ExampleInstrumentedTest.kt`.
      `app/src/androidTest/` had no other files, so the whole directory tree (including the
      now-empty `fruit_store` package dir) is gone — `app/src/` now contains only `main` and `test`.
- [x] 2.3 Removed all 4 `androidTestImplementation(...)` lines (compose-bom platform,
      `ui-test-junit4`, `androidx.test.rules`, `androidx.test.runner`) from
      `app/build.gradle.kts`. Removed the `androidx-test-runner`/`androidx-test-rules` library
      catalog entries and their `androidxTestRunner`/`androidxTestRules` version keys from
      `gradle/libs.versions.toml`, after grepping the whole repo to confirm neither was
      referenced anywhere else. `libs.androidx.ui.test.junit4` (the catalog alias) was kept —
      it is still consumed by `testImplementation(libs.androidx.ui.test.junit4)`, added by
      Unit 1 for Robolectric Compose tests; that is a separate `testImplementation` use, not
      the deleted `androidTestImplementation` one. `defaultConfig.testInstrumentationRunner`
      was left untouched per design (inert without an `androidTest` sourceset, removing it is
      churn out of this unit's scope).

### Phase 3 (Unit 2 — container/presentational split): NOT STARTED — separate batch/PR3
### Phase 4 (Unit 3 — 18-test Compose behavior suite): NOT STARTED — separate batch/PR4
### Phase 5 (Unit 4 — dispatcher/scheduler fix): NOT STARTED — separate batch/PR5
### Phase 6 (Final regression): NOT STARTED — runs after all units land

## TDD Cycle Evidence (Strict TDD Mode)

| Task | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 1.1-1.4 | `app/src/test/java/com/pedidosya/kata/ui/ComposeHarnessSmokeTest.kt` | Integration (Robolectric+Compose) | N/A (new file, no prior harness test) | ✅ Written first; confirmed failing/erroring against pre-wiring gradle config | ✅ Passed after gradle wiring (`./gradlew :app:testDebugUnitTest --tests "*ComposeHarnessSmokeTest*"`, 1/1, 0 failures) | ➖ Skipped — purely structural harness checkpoint, single possible output (design explicitly frames this as the "prove ONE Robolectric Compose test green" checkpoint, not a behavior suite) | ➖ None needed — 26-line test class, no duplication/complexity to extract |
| 2.1-2.3 | N/A — deletion-only unit, no production/test code added | N/A | N/A | ➖ N/A — this is a deletion + dependency-cleanup unit, not new behavior; strict TDD's RED/GREEN cycle does not apply per design's Strict TDD Mapping table (units 3/4 only list RED-by-inversion/no-RED; deletion units are outside that table entirely) | ✅ GREEN validated via the two full-suite commands below (unit and assemble) | ➖ N/A | ➖ N/A |

### Test Summary
- **Total tests written**: 0 this batch (deletion-only unit)
- **Total tests passing**: 50/50 (unchanged from Unit 1 — no test was added or removed by this
  deletion; `ExampleInstrumentedTest` was never part of the `testDebugUnitTest` suite)
- **Layers used**: N/A this batch
- **Approval tests** (refactoring): None
- **Pure functions created**: 0

## Work Unit Evidence

| Evidence | Value |
|---|---|
| Focused test command and exact result | `./gradlew :app:testDebugUnitTest` → BUILD SUCCESSFUL, 50 tests, 0 failures/errors/skipped (JUnit XML aggregate: `tests=50 skipped=0 failures=0 errors=0`) |
| Runtime harness command/scenario and exact result | `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL. N/A for a device/emulator harness — this unit only deletes an instrumented test and its now-unused `androidTestImplementation` dependencies; there is no runtime boundary left to exercise in `androidTest` after deletion, and design/tasks specify `assembleDebug` (androidTest no longer compiles into the build) as this unit's harness proxy. |
| Rollback boundary | `git checkout -- app/build.gradle.kts gradle/libs.versions.toml` and restore `app/src/androidTest/java/com/pedidosya/kata/fruit_store/ExampleInstrumentedTest.kt` from git history. Fully independent of Unit 1's files (`ComposeHarnessSmokeTest.kt`, the `testImplementation`/`debugImplementation` additions) — no overlap. |

## Files Changed (Unit 5 batch)

| File | Action | What Was Done |
|------|--------|----------------|
| `app/src/androidTest/java/com/pedidosya/kata/fruit_store/ExampleInstrumentedTest.kt` | Deleted | Stale scaffold test, wrong asserted package name, never executed by the verify command |
| `app/build.gradle.kts` | Modified | Removed all 4 `androidTestImplementation(...)` lines |
| `gradle/libs.versions.toml` | Modified | Removed `androidx-test-runner`/`androidx-test-rules` library entries and their `androidxTestRunner`/`androidxTestRules` version keys |
| `openspec/changes/behavioral-testing-expansion/tasks.md` | Modified | Marked Phase 2 tasks `[x]`, with the refuted-claim note inline on 2.1 |

## Deviations from Design
Design's Verification section and File Changes table asserted `ExampleInstrumentedTest`
"most likely does not compile"/"likely non-compiling" — **this claim is refuted by direct
build evidence** (see Phase 2 / 2.1 above). The androidTest sourceset compiled successfully
before deletion because `androidx.compose.ui:ui-test-junit4` transitively supplies
`androidx.test.ext:junit`. The deletion decision itself is unaffected: the file still asserted
an incorrect value and was dead code with respect to every command this project's verify
pipeline runs, so removing it (and its now provably-unused `androidTestImplementation`
dependency block) remains correct — just for a "dead and wrong," not "won't compile," reason.
No other deviation from design.

## Issues Found
Design's compile-failure claim for `ExampleInstrumentedTest` was not independently verified
before this batch (design.md explicitly says "no build was run") and turned out to be wrong;
recorded above for anyone tracing why 2.1's task note differs from the original design
rationale. No other issues found; Unit 1's regression baseline (50/50 tests) is preserved.

## Next Batch
Unit 2 (Phase 3, container/presentational split) or Unit 4 (Phase 5, dispatcher/scheduler
fix) on a new stacked branch per the chain strategy — both are independent of everything
delivered so far. This batch does not commit — changes are left in the working tree for the
orchestrator to commit/PR.
