# Apply Progress: Behavioral Testing Expansion

> **Multi-branch chained delivery.** This change ships as 5 independently revertable
> work units, each on its own branch/PR, per `tasks.md`'s Review Workload Forecast
> (`stacked-to-main`, base = `refactor/compose-stability-audit`). This file tracks
> cumulative progress across ALL batches; each `sdd-apply` batch appends to it — it
> never overwrites prior units' evidence.
>
> **This entry: Unit 1 of 5 (Phase 0 + Phase 1).** Branch:
> `feat/behavioral-testing-expansion-01-infra`.

## Cumulative Task Status

### Phase 0: Prerequisite — DONE (this batch)
- [x] 0.1 Working branch confirmed as `feat/behavioral-testing-expansion-01-infra`,
      based off tracker `feat/behavioral-testing-expansion` (which itself stacks on
      `refactor/compose-stability-audit`).

### Phase 1: Unit 1 — Robolectric/Compose Test Infrastructure — DONE (this batch)
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

### Phase 2 (Unit 5 — delete ExampleInstrumentedTest): NOT STARTED — separate batch/PR2
### Phase 3 (Unit 2 — container/presentational split): NOT STARTED — separate batch/PR3
### Phase 4 (Unit 3 — 18-test Compose behavior suite): NOT STARTED — separate batch/PR4
### Phase 5 (Unit 4 — dispatcher/scheduler fix): NOT STARTED — separate batch/PR5
### Phase 6 (Final regression): NOT STARTED — runs after all units land

## TDD Cycle Evidence (Strict TDD Mode)

| Task | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 1.1-1.4 | `app/src/test/java/com/pedidosya/kata/ui/ComposeHarnessSmokeTest.kt` | Integration (Robolectric+Compose) | N/A (new file, no prior harness test) | ✅ Written first; confirmed failing/erroring against pre-wiring gradle config | ✅ Passed after gradle wiring (`./gradlew :app:testDebugUnitTest --tests "*ComposeHarnessSmokeTest*"`, 1/1, 0 failures) | ➖ Skipped — purely structural harness checkpoint, single possible output (design explicitly frames this as the "prove ONE Robolectric Compose test green" checkpoint, not a behavior suite) | ➖ None needed — 26-line test class, no duplication/complexity to extract |

### Test Summary
- **Total tests written**: 1 (canary)
- **Total tests passing**: 1/1 (canary) + 49/49 pre-existing = 50/50 total suite
- **Layers used**: Integration/Robolectric-Compose (1)
- **Approval tests** (refactoring): None — no refactoring tasks in this batch
- **Pure functions created**: 0 (infra/config work only)

## Work Unit Evidence

| Evidence | Value |
|---|---|
| Focused test command and exact result | `./gradlew :app:testDebugUnitTest --tests "*ComposeHarnessSmokeTest*"` → BUILD SUCCESSFUL, 1 test, 0 failures/errors/skipped |
| Runtime harness command/scenario and exact result | `./gradlew :app:testDebugUnitTest` (full suite) → BUILD SUCCESSFUL, 50 tests total (1 new canary + 49 pre-existing), 0 failures/errors/skipped |
| Rollback boundary | Revert `app/build.gradle.kts`, `gradle/libs.versions.toml`, and delete `app/src/test/java/com/pedidosya/kata/ui/ComposeHarnessSmokeTest.kt`. Independent of all other units — no other unit's files were touched in this batch. |

## Files Changed (this batch)

| File | Action | What Was Done |
|------|--------|----------------|
| `gradle/libs.versions.toml` | Modified | Added `robolectric = "4.14.1"` version + library entry; added `androidx-ui-test-manifest` library entry (BOM-versioned) |
| `app/build.gradle.kts` | Modified | Added `testOptions { unitTests { isIncludeAndroidResources = true } }`; added `debugImplementation(libs.androidx.ui.test.manifest)`, `testImplementation(platform(libs.androidx.compose.bom))`, `testImplementation(libs.androidx.ui.test.junit4)`, `testImplementation(libs.robolectric)`. `androidTestImplementation` block left untouched (belongs to Unit 5 / Phase 2). |
| `app/src/test/java/com/pedidosya/kata/ui/ComposeHarnessSmokeTest.kt` | Created | Permanent Robolectric/Compose harness canary — renders `Text("compose-harness-ready")` via `createAndroidComposeRule<ComponentActivity>()`, asserts it is displayed |
| `openspec/changes/behavioral-testing-expansion/tasks.md` | Modified | Marked Phase 0 + Phase 1 tasks `[x]`; Phase 2-6 left unchecked |

## Deviations from Design
None — implementation matches design. Robolectric `4.14.1` (the starting pin) resolved
and passed on the first attempt; no escalation to `4.15.1`/`4.16` and no
`app/src/test/resources/robolectric.properties` `sdk=34` fallback was required.

## Issues Found
None.

## Next Batch
Unit 2 (Phase 2, `ExampleInstrumentedTest` deletion) or Unit 3 (Phase 3, container/
presentational split) on a new stacked branch per the chain strategy. This batch does
not commit — changes are left in the working tree for the orchestrator to commit/PR.
