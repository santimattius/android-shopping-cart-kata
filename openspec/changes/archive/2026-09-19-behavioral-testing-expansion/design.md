# Design: Behavioral Testing Expansion

## Technical Approach

Four independently revertable work units, in dependency order: (1) Robolectric + Compose test
infrastructure with a permanent harness canary, (2) container/presentational split of both screens,
(3) the Compose behavior suite, (4) one shared `TestCoroutineScheduler`. No delta spec exists
(`specs/README.md` verdict: no spec-level surface), so correctness is defined by the Verification
section below, not by new scenarios.

## Architecture Decisions

### Decision: Robolectric-backed Compose tests in `src/test`, not `androidTest`

| Option | Tradeoff | Decision |
|---|---|---|
| `androidTest` (deps already present) | Never executed by `./gradlew :app:testDebugUnitTest` — the project's configured `apply.test_command` and `verify.test_command`. Tests that the verify gate cannot run are decoration. Requires a device/emulator in CI. | Rejected (kept as fallback) |
| `src/test` + Robolectric | One new dependency; runs on every `testDebugUnitTest`; same Compose test APIs, same assertions. | **Chosen** |

Rationale: the binding constraint is the *verify command*, not test fidelity. Both sourcesets use
identical `ui-test-junit4` APIs, so the fallback (move the same files to `androidTest`) costs a
package move, not a rewrite.

### Decision: the testable seam is a stateless `CartScreen`/`SummaryScreen` overload, not `internal` content composables

`CartContent`, `CouponSection`, `CouponStatusMessage`, `LoadingContent`, `ErrorContent` stay
`private`. `CartContent` renders only `Success`; the `Loading`/`Error`/`Success` dispatch lives in
`CartScreen`. Exposing the `when` dispatch as one `internal` overload covers every branch through a
single seam and widens the production API by exactly two symbols. Rejected: making five composables
`internal` (five-symbol API growth to prove the same contract).

### Decision: pure extraction — no stability or memoization changes

The wrapper keeps passing bound references (`viewModel::retry`, `viewModel::onRefresh`, …) exactly
as today, one level higher. No `remember`, no `@Stable` annotation, no parameter reordering.
Rationale: change 2 (`refactor/compose-stability-audit`) owns stability; mixing it in here makes a
behavior-preserving refactor unreviewable and breaks the "revert (2) alone" rollback guarantee.

### Decision: version pins are knowledge-based and empirically gated

Web verification tools were unavailable to this phase. Pins below are stated as the starting point
and are **validated by the work-unit-1 canary test**, not by assertion. If dependency resolution or
the canary fails, `sdd-apply` may bump Robolectric within `4.14.1 → 4.15.1 → 4.16` and/or add
`app/src/test/resources/robolectric.properties` containing `sdk=34` — without redesigning. Any bump
outside that range is a blocker to report, not a judgment call.

## Data Flow

    CartScreen(viewModel, onNavigateToSummary)        ← public wrapper, unchanged behavior
      │ collectAsStateWithLifecycle()  +  LaunchedEffect(events → onNavigateToSummary)
      ▼
    CartScreen(state, onRetry, onRefresh, …)          ← internal, stateless   ◄── Robolectric tests
      │ when (state)
      ├─► LoadingContent()   ├─► ErrorContent(onRetry)   └─► CartContent(…) ─► CouponSection ─► CouponStatusMessage

Tests drive the internal overload with literal state values and capture callback invocations. No
ViewModel, repository, Room, Retrofit, or DI graph is constructed for a rendering contract.

## Interfaces / Contracts

```kotlin
// ui/cart/CartScreen.kt — new internal overload; bodies moved verbatim from the wrapper's `when`
@Composable
internal fun CartScreen(
    state: CartUiState,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onCouponInputChanged: (String) -> Unit,
    onApplyCoupon: () -> Unit,
    onConfirmPurchase: () -> Unit,
)

// ui/summary/SummaryScreen.kt
@Composable
internal fun SummaryScreen(state: SummaryUiState)

// core/MainDispatcherRule.kt — the only edit: `private val` → `val`
class MainDispatcherRule(val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher()
```

The public `CartScreen(viewModel, onNavigateToSummary)` retains its `collectAsStateWithLifecycle()`
and `LaunchedEffect(viewModel)` event collection and delegates; the public `SummaryScreen(viewModel)`
retains only the state collection and delegates. Signatures of both public composables are unchanged,
so `KataNavHost` needs no edit.

### Gradle wiring

`gradle/libs.versions.toml`: add `robolectric = "4.14.1"` and
`robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }`;
add `androidx-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }`
(BOM-versioned, no `version.ref`); remove the now-unused `androidx-test-runner` /
`androidx-test-rules` entries and their `androidxTestRunner` / `androidxTestRules` versions.

`app/build.gradle.kts`:

```kotlin
android {
    testOptions { unitTests { isIncludeAndroidResources = true } }   // required by Robolectric
}
dependencies {
    // delete all four androidTestImplementation(...) lines
    debugImplementation(libs.androidx.ui.test.manifest)              // ComponentActivity in merged manifest
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)                 // moved from androidTestImplementation
    testImplementation(libs.robolectric)
}
```

`defaultConfig.testInstrumentationRunner` stays: it is inert without an `androidTest` sourceset and
removing it is churn. `ui-test-junit4` brings `androidx.test:core` (`ActivityScenario`) transitively;
if apply hits an unresolved `ActivityScenario`, add `testImplementation("androidx.test:core")`.

### Test class template

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)   // bypass App/DefaultAppContainer
class CartScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
}
```

`@Config(application = …)` isolates UI tests from `App.onCreate`'s `DefaultAppContainer`
construction. That container is fully `by lazy` today, so this is insulation against future eager
initialization, not a current fix.

## Test List (work unit 3)

`app/src/test/java/com/pedidosya/kata/ui/cart/CartScreenTest.kt`:

1. `Loading` → progress node exists; `"Confirmar Compra"` `assertDoesNotExist()`.
2. `Error(NoCacheAvailable)` → `"No pudimos cargar tu carrito."` displayed.
3. `Error` + click `"Reintentar"` → `onRetry` captured once.
4. `Success` with items → row title and `"{qty} x {price}"` displayed.
5. `Success` with empty `persistentListOf()` → `"Tu carrito está vacío."` displayed, no rows.
6. `isValidating = true` → `"Aplicar"` `assertIsNotEnabled()`.
7. `isValidating = false` → `"Aplicar"` `assertIsEnabled()`; click → `onApplyCoupon` captured.
8. `coupon = Invalid` → `"Confirmar Compra"` `assertIsNotEnabled()` (`canConfirm == false`).
9. `isValidating = true` → `"Confirmar Compra"` `assertIsNotEnabled()`.
10. `coupon = NotApplied` → `"Confirmar Compra"` `assertIsEnabled()`; click → `onConfirmPurchase` captured.
11. `performTextInput` on the coupon field → `onCouponInputChanged` captured (field is controlled by
    `state.couponInput`, so assert the callback, never the rendered value).
12. `CouponStatusMessage`, five branches — `NotApplied` (all four messages absent),
    `Valid` (`"Cupón aplicado: {pct}% off. Nuevo total: {total}"`), `Invalid`
    (`"El código ingresado no es válido."`), `Inactive` (`"Este cupón ya no está activo."`),
    `ServiceError` (`"No pudimos validar el cupón. Intentá de nuevo."`).
13. `isRefreshing = true` → items still render (state-driven smoke).
14. `performTouchInput { swipeDown() }` on the list → `onRefresh` captured once.

`app/src/test/java/com/pedidosya/kata/ui/summary/SummaryScreenTest.kt`:

15. `Loading` → progress node exists; `"Resumen de compra"` absent.
16. `Success` → title, item rows, `"Total a pagar: {total}"` displayed.
17. `nominalPercentage = 15.0` → `"Descuento aplicado: 15.0%"`.
18. `nominalPercentage = 0.0` → `"Sin cupón aplicado"`.

Assert the exact Spanish literals the composables declare (they are hardcoded; resource extraction
is separate work). Test 14 is the only gesture-driven case: `PullToRefreshBox` depends on
nested-scroll plus animation, which is the least certain thing under Robolectric. Bounded fallback —
if it is non-deterministic after **one** debugging attempt, delete it, keep 13, and record
"pull-to-refresh gesture assertion deferred to instrumented testing" in the verify report. Do not
add a production `testTag` to rescue it.

## Strict TDD Mapping (for `sdd-tasks` / `sdd-apply`)

Strict TDD is enabled project-wide, but classic RED applies cleanly only to unit 1 and unit 2. Do
not manufacture failing tests for behavior-preserving work.

| Unit | What RED means here | GREEN |
|---|---|---|
| 1 — infra | `ComposeHarnessSmokeTest` (renders `Text("compose-harness-ready")`, asserts it) written **first**; it fails/errors because Robolectric, `ui-test-manifest`, and `isIncludeAndroidResources` are absent. | Same test passes under `./gradlew :app:testDebugUnitTest`. **This is the proposal's mandatory "prove ONE Robolectric Compose test green before writing the suite" checkpoint.** Do not start unit 3 until it is green. Keep the canary permanently — it separates harness failures from UI failures. |
| 2 — split | Write test 1 (`Loading`) **first**; it fails to compile — unresolved `CartScreen(state = …)`. Compile failure is legitimate RED. | Overload added; test 1 passes; `:app:assembleDebug` and the 20 existing tests unchanged. |
| 3 — suite | No true RED: these characterize behavior that already ships, so every test goes green on first run. Substitute **RED-by-inversion**: for each new test, invert the expectation once, confirm it fails, restore it, confirm it passes. An assertion that passes inverted is not wired to the UI. | All 18 tests green. |
| 4 — scheduler | No RED. Behavior-preserving mechanical fix. | The 20 existing tests pass with **byte-identical assertions**. If one turns red, that is a previously masked ordering dependency — report it as a finding; never adjust an assertion to restore green. |

## Testing Strategy

| Layer | What to Test | Approach |
|---|---|---|
| Harness | Robolectric + Compose wiring | 1 canary test (unit 1) |
| UI behavior | Loading/Error/Success, empty cart, `canConfirm`, `isValidating`, 5 coupon branches, refresh, summary discount row | 18 Robolectric Compose tests against the stateless overloads |
| ViewModel | Unchanged | 20 existing Turbine tests, now on one shared scheduler |
| Screenshot / instrumented / E2E | — | Out of scope per proposal |

## Verification ("done and correct")

1. `./gradlew :app:testDebugUnitTest --rerun-tasks` green, including all new Compose tests.
2. The 20 pre-existing ViewModel tests pass with unmodified assertions under one shared
   `TestCoroutineScheduler` (no `runTest(UnconfinedTestDispatcher())` call site remains; the
   `UnconfinedTestDispatcher` import is removed from both ViewModel test files).
3. `./gradlew :app:assembleDebug` green.
4. `app/src/androidTest/` no longer exists; no `androidTestImplementation` declaration remains; the
   `androidx-test-runner` / `androidx-test-rules` catalog entries are gone.
5. `git diff` on `CartScreen.kt` / `SummaryScreen.kt` shows only movement and delegation — no
   changed literal, predicate, modifier, or branch.

Note: `ExampleInstrumentedTest` imports `androidx.test.ext.junit.runners.AndroidJUnit4`, which no
declared dependency provides (`androidx.test:runner` and `:rules` do not export it), so the
`androidTest` sourceset most likely does not compile today. Deleting it removes a latent build
failure in addition to a false assertion.

## File Changes

| File | Action | Description |
|---|---|---|
| `gradle/libs.versions.toml` | Modify | +`robolectric`, +`androidx-ui-test-manifest`; −test-runner/-rules entries and versions |
| `app/build.gradle.kts` | Modify | `isIncludeAndroidResources`; test/debug dependency moves; delete `androidTestImplementation` block |
| `app/src/main/java/com/pedidosya/kata/ui/cart/CartScreen.kt` | Modify | Add `internal` stateless overload; wrapper delegates |
| `app/src/main/java/com/pedidosya/kata/ui/summary/SummaryScreen.kt` | Modify | Add `internal` stateless overload; wrapper delegates |
| `app/src/test/java/com/pedidosya/kata/ui/ComposeHarnessSmokeTest.kt` | Create | Unit-1 canary / checkpoint |
| `app/src/test/java/com/pedidosya/kata/ui/cart/CartScreenTest.kt` | Create | Tests 1–14 |
| `app/src/test/java/com/pedidosya/kata/ui/summary/SummaryScreenTest.kt` | Create | Tests 15–18 |
| `app/src/test/java/com/pedidosya/kata/core/MainDispatcherRule.kt` | Modify | `private val` → `val testDispatcher` |
| `app/src/test/java/com/pedidosya/kata/ui/cart/CartViewModelTest.kt` | Modify | 17 `runTest` sites → `runTest(mainDispatcherRule.testDispatcher)` |
| `app/src/test/java/com/pedidosya/kata/ui/summary/SummaryViewModelTest.kt` | Modify | 3 `runTest` sites → same |
| `app/src/androidTest/java/com/pedidosya/kata/fruit_store/ExampleInstrumentedTest.kt` | Delete | Stale, asserts a non-existent applicationId, likely non-compiling |
| `app/src/test/resources/robolectric.properties` | Create **only if needed** | `sdk=34`, if the canary fails on the default SDK 35 |

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or
process-integration boundary. Robolectric downloads `android-all` artifacts from Maven on first run
(network required once); the unit-1 canary surfaces that immediately.

## Migration / Rollout

No migration. No feature flag — production behavior is byte-identical before and after. Rollback is
four `git revert <sha>` targets in the proposal's commit order; unit 3 must be reverted before unit
2, and units 1 and 4 are independent of everything else. Reverting unit 3 alone leaves unused test
infrastructure, which is harmless.

## Open Questions

- [ ] Robolectric `4.14.1` vs. Compose BOM `2025.05.00` under AGP `8.6.1` is a knowledge-based pin
      (no web verification available to this phase). Resolved empirically by the unit-1 canary; the
      allowed bump range and `sdk=34` fallback are specified above.
- [ ] Whether `performTouchInput { swipeDown() }` reliably triggers `PullToRefreshBox.onRefresh`
      under Robolectric. Bounded fallback specified (test 14).
