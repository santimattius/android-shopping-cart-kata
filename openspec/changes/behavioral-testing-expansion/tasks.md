# Tasks: Behavioral Testing Expansion

## Review Workload Forecast

| Field | Value |
| ------- | ------- |
| Estimated changed lines | ~380-460 (2 prod files, 3 new/modified test files, 2 gradle files, 1 deletion) |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR1 → PR2 → PR3 → PR4 → PR5 (5 independently revertable work units) |
| Delivery strategy | auto-chain |
| Chain strategy | stacked-to-main (base = `refactor/compose-stability-audit`, this change's actual trunk) |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | PR | Focused test command | Runtime harness | Rollback boundary |
| --- | --- | --- | --- | --- | --- |
| 1 | Robolectric/Compose infra + canary | PR1 | `./gradlew :app:testDebugUnitTest --tests "*ComposeHarnessSmokeTest"` | Robolectric JVM unit test, no device | Revertable alone; independent of all other units |
| 2 | Delete `ExampleInstrumentedTest` | PR2 | `./gradlew :app:assembleDebug` (androidTest no longer compiles into the build) | N/A — deletion only, no runtime harness | Revertable alone; independent |
| 3 | Container/presentational split | PR3 | `./gradlew :app:testDebugUnitTest --tests "*CartScreenTest"` | Robolectric JVM unit test | Revert requires reverting PR4 first (PR4 depends on the split) |
| 4 | 18-test Compose behavior suite | PR4 | `./gradlew :app:testDebugUnitTest --tests "*CartScreenTest" --tests "*SummaryScreenTest"` | Robolectric JVM unit test | Revertable alone; leaves PR3's infra unused (harmless) |
| 5 | Shared `TestCoroutineScheduler` | PR5 | `./gradlew :app:testDebugUnitTest --tests "*CartViewModelTest" --tests "*SummaryViewModelTest"` | JUnit4 + Turbine, no Robolectric | Revertable alone; independent |

## Phase 0: Prerequisite

- [x] 0.1 Confirm the working branch stacks on `refactor/compose-stability-audit` (not `main`); create/checkout from it if not already current.

## Phase 1: Unit 1 — Robolectric/Compose Test Infrastructure

- [x] 1.1 Add `robolectric = "4.14.1"` version + `robolectric` library entry, and `androidx-ui-test-manifest` (BOM-versioned, no `version.ref`) to `gradle/libs.versions.toml`.
- [x] 1.2 In `app/build.gradle.kts`: add `testOptions { unitTests { isIncludeAndroidResources = true } }`; add `debugImplementation(libs.androidx.ui.test.manifest)`, `testImplementation(platform(libs.androidx.compose.bom))`, `testImplementation(libs.androidx.ui.test.junit4)`, `testImplementation(libs.robolectric)`.
- [x] 1.3 RED: create `app/src/test/java/com/pedidosya/kata/ui/ComposeHarnessSmokeTest.kt` (renders/asserts `"compose-harness-ready"`); confirm it fails/errors before 1.1/1.2 land.
- [x] 1.4 GREEN: run the canary until it passes under `./gradlew :app:testDebugUnitTest`. If it fails, escalate Robolectric `4.14.1 → 4.15.1 → 4.16` and/or add `app/src/test/resources/robolectric.properties` (`sdk=34`) per design's bounded procedure; a failure outside that range is a blocker to report, not to keep guessing at.
- [x] 1.5 Do not start Phase 4 until 1.4 is green. Keep the canary permanently (harness/UI failure separator).

## Phase 2: Unit 5 — Delete `ExampleInstrumentedTest`

- [x] 2.1 Independently confirm `ExampleInstrumentedTest.kt` fails to compile today: it imports `androidx.test.ext.junit.runners.AndroidJUnit4`, not exported by the declared `androidx.test:runner`/`:rules` dependencies. **REFUTED — see apply-progress**: `./gradlew :app:compileDebugAndroidTestKotlin` was BUILD SUCCESSFUL before deletion; `androidx.test.ext:junit:1.1.5` was present transitively via `androidx.compose.ui:ui-test-junit4` (declared `androidTestImplementation(libs.androidx.ui.test.junit4)`), not via `runner`/`rules`. The file still asserted a wrong value (`"com.pedidosya.kata.fruit_store"` instead of applicationId `"com.pedidosya.kata"`) and was never executed by `testDebugUnitTest`, so deletion proceeded on that basis.
- [x] 2.2 Delete `app/src/androidTest/java/com/pedidosya/kata/fruit_store/ExampleInstrumentedTest.kt`; remove now-empty `androidTest` directories.
- [x] 2.3 Remove the 4 `androidTestImplementation(...)` lines from `app/build.gradle.kts`; remove the `androidx-test-runner`/`androidx-test-rules` library entries and their versions from `gradle/libs.versions.toml`.

## Phase 3: Unit 2 — Container/Presentational Split

- [x] 3.1 RED: in new `app/src/test/java/com/pedidosya/kata/ui/cart/CartScreenTest.kt`, wrote the Loading test (design's Test List item 1) calling `internal fun CartScreen(state, ...)`; confirmed compile failure before the overload existed.
- [x] 3.2 GREEN: added the `internal` stateless `CartScreen(state: CartUiState, onRetry, onRefresh, onCouponInputChanged, onApplyCoupon, onConfirmPurchase)` overload to `app/src/main/java/com/pedidosya/kata/ui/cart/CartScreen.kt`, moving the existing `when (state)` dispatch (Loading/Error/Success); public wrapper retains `collectAsStateWithLifecycle()`/`LaunchedEffect` and delegates.
- [x] 3.3 Added the `internal` stateless `SummaryScreen(state: SummaryUiState)` overload to `app/src/main/java/com/pedidosya/kata/ui/summary/SummaryScreen.kt`, moving its `when (state)` dispatch; wrapper retains `collectAsStateWithLifecycle()` and delegates.
- [x] 3.4 Confirmed the Loading test passes; ran `./gradlew :app:assembleDebug` and the 20 existing ViewModel tests unchanged.
- [x] 3.5 Confirmed `git diff` on both files shows only movement/delegation — no changed literal, predicate, modifier, or branch.

## Phase 4: Unit 3 — 18-Test Compose Behavior Suite

- [x] 4.1 Write `CartScreenTest.kt` items 2-14 per design.md's Test List (Error rendering + retry, Success list content, empty-cart rendering, Apply/Confirm enablement from `isValidating`/`canConfirm`, coupon-input callback, all 5 `CouponStatusMessage` branches, `isRefreshing` smoke).
- [x] 4.2 Write item 14 (`performTouchInput { swipeDown() }` triggers `onRefresh`) last. Bounded fallback: one debugging attempt if non-deterministic; if still flaky, delete it, keep item 13, and record "pull-to-refresh gesture assertion deferred to instrumented testing" in the verify report — do not add a production `testTag`. **Completed:** the initial item-text target did not trigger refresh; the one permitted debugging attempt retargeted the gesture to `onNode(hasScrollAction())` (the `LazyColumn` semantics node), which passed. No deferral or production `testTag` was needed.
- [x] 4.3 Write `app/src/test/java/com/pedidosya/kata/ui/summary/SummaryScreenTest.kt` items 15-18 per design.md (Loading, Success + item rows/total, discount row at 15.0%, "Sin cupón aplicado" at 0.0%).
- [x] 4.4 RED-by-inversion on every new test in 4.1-4.3: invert the assertion once, confirm it fails, restore the correct assertion, confirm it passes.
- [x] 4.5 Run `./gradlew :app:testDebugUnitTest --tests "*CartScreenTest" --tests "*SummaryScreenTest"`; all green.

## Phase 5: Unit 4 — Dispatcher/Scheduler Fix

- [ ] 5.1 In `app/src/test/java/com/pedidosya/kata/core/MainDispatcherRule.kt`, change `private val testDispatcher` to `val testDispatcher`.
- [ ] 5.2 Update all 17 `runTest(...)` call sites in `app/src/test/java/com/pedidosya/kata/ui/cart/CartViewModelTest.kt` to `runTest(mainDispatcherRule.testDispatcher)`; remove the now-unused `UnconfinedTestDispatcher` import.
- [ ] 5.3 Update all 3 `runTest(...)` call sites in `app/src/test/java/com/pedidosya/kata/ui/summary/SummaryViewModelTest.kt` the same way; remove the now-unused import.
- [ ] 5.4 Run both updated test files; assertions must stay byte-identical to before this change. A newly red test is a finding to report, never an assertion to adjust.

## Phase 6: Final Regression

- [ ] 6.1 Run `./gradlew :app:testDebugUnitTest --rerun-tasks`; the canary, all 18 Compose tests, and the 20 ViewModel tests are green.
- [ ] 6.2 Run `./gradlew :app:assembleDebug`; green.
- [ ] 6.3 Confirm `app/src/androidTest/` no longer exists and no `androidTestImplementation` declaration remains anywhere in `app/build.gradle.kts`.
