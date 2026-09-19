# Proposal: Behavioral Testing Expansion

## Intent

UI behavior is completely unverified. 20 ViewModel tests cover state production; zero tests cover the two composables that render it. `androidx.compose.ui:ui-test-junit4` is declared but unused, and the only `androidTest` file asserts `packageName == "com.pedidosya.kata.fruit_store"` — an applicationId that does not exist, so it fails if ever run. Changes 1-2 froze `CartUiState`/`SummaryUiState`; lock the rendering contract against them now, before change 4 rewrites navigation.

## Scope

### In Scope

- **Robolectric-backed Compose tests in `src/test`** (so `./gradlew :app:testDebugUnitTest` — the project's verify command — actually runs them): Cart Loading / Error+Retry / Success / empty-cart rendering; Apply and Confirm enablement from `isValidating` and `canConfirm`; `PullToRefreshBox` gesture and `isRefreshing`; all five `CouponStatusMessage` branches; Summary Loading / Success / discount-vs-"Sin cupón" row.
- **Container-presentational split**: `internal` stateless `CartScreen(state, callbacks)` and `SummaryScreen(state)` overloads; today's ViewModel-bound composables become thin delegating wrappers. No behavior change.
- **Test infra**: Robolectric; `ui-test-junit4` moved to `testImplementation`; `ui-test-manifest` as `debugImplementation`; `testOptions.unitTests.isIncludeAndroidResources = true`.
- **Delete `ExampleInstrumentedTest`** and the `androidTest`-only dependency declarations it leaves unused.
- **One shared scheduler**: `MainDispatcherRule` exposes its `TestDispatcher`; the 20 existing tests call `runTest(mainDispatcherRule.testDispatcher)` instead of constructing a second, independent `UnconfinedTestDispatcher`.

### Out of Scope

- **Screenshot testing** — deferred. Every named gap is semantic, not visual; a golden-image toolchain plus baselines on top of newly-introduced Robolectric doubles the new-infra surface in one PR.
- **Full `StandardTestDispatcher` migration** — deferred. After change 1, `CartViewModel.state` is a pure order-independent reduction, so Unconfined masks nothing behavioral here; the real defect was two uncoordinated schedulers, fixed above at a fraction of the blast radius.
- **Navigation route-argument test** — deferred to change 4, which replaces the string-route mechanism with `NavKey` types; testing the doomed encoder is throwaway work.
- Instrumented/E2E tests, ViewModel production changes, navigation changes.

## Capabilities

### New Capabilities

- None — no new behavior; tests plus a UI seam refactor only.

### Modified Capabilities

- None — `cart-list`, `coupon-validation`, and `purchase-summary` requirements are unchanged. This change verifies them as written.

## Approach

Test the smallest UI contract that proves the behavior: render controlled `Success`/`Loading`/`Error` values through `createAndroidComposeRule<ComponentActivity>()` and assert user-visible semantics (`onNodeWithText`, `assertIsEnabled`/`assertIsNotEnabled`, `assertDoesNotExist`) plus captured callback values. No ViewModel, repository, or DI graph is constructed for a layout contract — that path is already covered by the 20 existing Turbine tests and would only reintroduce their async surface as flakiness.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `gradle/libs.versions.toml`, `app/build.gradle.kts` | Modified | Robolectric, sourceset moves, `isIncludeAndroidResources` |
| `ui/cart/CartScreen.kt`, `ui/summary/SummaryScreen.kt` | Modified | Stateless `internal` overload + wrapper |
| `app/src/test/.../ui/cart/`, `.../ui/summary/` | New | Compose behavior tests |
| `app/src/androidTest/.../fruit_store/ExampleInstrumentedTest.kt` | Removed | Stale, failing, zero value |
| `test/.../core/MainDispatcherRule.kt`, `CartViewModelTest.kt`, `SummaryViewModelTest.kt` | Modified | Shared `TestCoroutineScheduler` |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Robolectric vs. Compose BOM 2025.05.00 / AGP 8.6.1 incompatibility | Med | `sdd-design` pins the version; `sdd-apply` proves one Compose test green under `:app:testDebugUnitTest` before writing the suite. Fallback: `androidTest` sourceset (deps already present), accepting it runs only on a device |
| Hardcoded Spanish UI literals make `onNodeWithText` brittle | Low | Assert the same literals the composables declare; string-resource extraction is separate work |
| Presentational split touches both screens | Low | Wrappers are pure delegation; `:app:assembleDebug` and the existing suite are the gate |
| Shared-scheduler edit touches all 20 existing tests | Low | Mechanical, assertions unchanged. A test that turns red exposes a previously masked ordering dependency — a finding, not a regression |

## Rollback Plan

Four independently revertable work-unit commits, in dependency order:

1. `build(test): add Robolectric and Compose UI test infrastructure`
2. `refactor(ui): split cart and summary screens into stateless overloads`
3. `test(ui): cover cart and summary rendering with Compose behavior tests`
4. `test(core): share one TestCoroutineScheduler across dispatcher and runTest`

`git revert <sha>` on any of them. Nothing is persisted, no schema, DI, navigation, or public ViewModel contract changes, so no revert can leave partial state. Reverting (3) alone leaves unused infra (harmless); reverting (2) requires reverting (3) first. (1) and (4) are independent of everything else. No feature flag — production behavior is identical before and after.

## Dependencies

- Stacks on `refactor/compose-stability-audit` (change 2), itself stacked on change 1.
- Robolectric is a new dependency; version must resolve against the offline-free catalog at apply time.

## Success Criteria

- [ ] `./gradlew :app:testDebugUnitTest --rerun-tasks` green, including the new Compose tests.
- [ ] Every exploration-named gap covered by an assertion or explicitly deferred above: Loading/Error/Success, empty cart, `canConfirm`, `isValidating`, `PullToRefreshBox`, all five coupon status branches, Summary discount row.
- [ ] `./gradlew :app:assembleDebug` green; no `androidTest` source remains.
- [ ] The 20 pre-existing ViewModel tests still pass with byte-identical assertions, under one shared `TestCoroutineScheduler`.
- [ ] Deferred items (screenshots, `StandardTestDispatcher`, navigation-argument test) recorded with rationale; the route-contract test is handed to change 4 as an inherited acceptance criterion.
