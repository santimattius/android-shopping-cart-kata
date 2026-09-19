# Tasks: ViewModel State Production and Structured Concurrency

> STRICT TDD MODE IS ACTIVE. Test runner: `./gradlew :app:testDebugUnitTest`. `sdd-apply` MUST follow `strict-tdd.md`: RED before GREEN for every numbered pair below, no batching multiple GREEN steps behind one RED.

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~230-300 (CartViewModel.kt ~150-180 add+del across nearly every method; CartViewModelTest.kt ~55-70 for 2 new tests + 2 re-sited assertions) |
| 400-line budget risk | Medium |
| Chained PRs recommended | No |
| Suggested split | Single PR, two internal commits (state production; event channel) per design's rollback plan |
| Delivery strategy | auto-chain |
| Chain strategy | pending |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | `LoadPhase`/`CartInputs`/`combine().stateIn()` reduction; fixes retry-from-Error-with-empty-cart | PR 1, commit 1 | `./gradlew :app:testDebugUnitTest --tests "*CartViewModelTest*"` | N/A — pure unit test (Turbine+MockK), no emulator | `git revert` commit 1; reinstates empty-cart stuck-Error defect; disjoint from commit 2 |
| 2 | `Channel<CartEvent>(BUFFERED)` + `receiveAsFlow()` event delivery; fixes collector-gap event loss | PR 1, commit 2 | `./gradlew :app:testDebugUnitTest --tests "*CartViewModelTest*"` | N/A — pure unit test, no emulator | `git revert` commit 2; reopens collector-gap window; disjoint from commit 1 |

## Phase 1: RED — empty-cart retry-from-Error regression

- [x] 1.1 Add failing test to `app/src/test/java/com/pedidosya/kata/ui/cart/CartViewModelTest.kt`: `a successful retry with an empty cart leaves Error for an empty Success` — `observeCart()` returns `MutableStateFlow(emptyList())`, `refresh()` `returnsMany [failure, success]`; assert `Error(NoCacheAvailable)`, call `retry()`, assert `Success(emptyList(), calculateTotals(emptyList(), null))`.
- [x] 1.2 Run `./gradlew :app:testDebugUnitTest --tests "*CartViewModelTest*"` and confirm the new test fails (RED): today's `refresh()` success branch requires `_state.value is Loading`, which is false once `Error` was set.

## Phase 2: GREEN — combine().stateIn() reduction

- [x] 2.1 In `app/src/main/java/com/pedidosya/kata/ui/cart/CartViewModel.kt`, add `private enum class LoadPhase { Loading, Loaded, Failed }` and `private data class CartInputs(loadPhase, couponInput, coupon, isValidating, isRefreshing)`.
- [x] 2.2 Replace `_state`/`init` collector with `private val inputs = MutableStateFlow(CartInputs())` and `val state: StateFlow<CartUiState> = combine(repository.observeCart(), inputs, ::reduce).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CartUiState.Loading)`.
- [x] 2.3 Add private `reduce(items, inputs): CartUiState` implementing the design's total reduction table: non-empty items → `Success` always; empty + `Loading` → `Loading`; empty + `Failed` → `Error(NoCacheAvailable)`; empty + `Loaded` → `Success(emptyList())`.
- [x] 2.4 Rewrite `refresh(manual: Boolean = false)`: set `isRefreshing` only when `manual`; set `loadPhase = Loaded` on success, `Failed` only when current `loadPhase == Loading`, else unchanged; remove all `_state.value` reads.
- [x] 2.5 Rewrite `onRefresh()`, `onCouponInputChanged()`, `onApplyCoupon()`, `onConfirmPurchase()`, `validateAndUpdate()` to read/write only via `inputs.update {}`/`inputs.value`; remove `successFor()`'s `_state` coupling and the `latestItems` field.
- [x] 2.6 Run the Phase 1 test alone and confirm GREEN.

## Phase 3: Test-mechanism fix for pre-existing `.value` reads

- [x] 3.1 In `CartViewModelTest.kt` (`confirming with a typed but never-applied code…`, currently line 302), replace the uncollected `viewModel.state.value` read with an attached collector (`state.test {}` or a `backgroundScope` collector) before asserting `canConfirm` — same asserted behavior, mechanism only.
- [x] 3.2 In `CartViewModelTest.kt` (`confirming with an invalid or errored coupon blocks navigation`, currently line 327), apply the same collector-attachment fix before asserting `finalState.canConfirm` is false.
- [x] 3.3 Run the full `CartViewModelTest` suite and confirm Phase 1's test and both re-sited tests pass together. **Deviation**: a third pre-existing test (`retry after an error calls refresh again and succeeds once the cache is populated`) also needed the same collector-attachment-style mechanism fix — see Deviations note below.

## Phase 4: RED — navigation event survives a collector gap

- [x] 4.1 Add failing test to `CartViewModelTest.kt`: call `onConfirmPurchase()` with a blank coupon field BEFORE attaching any `events` collector, then open `events.test { assertEquals(CartEvent.NavigateToSummary("", 0.0, "all"), awaitItem()) }`; confirm it fails today (replay-0 `SharedFlow` drops the pre-collector emission).

## Phase 5: GREEN — buffered Channel event delivery

- [x] 5.1 In `CartViewModel.kt`, replace `_events: MutableSharedFlow<CartEvent>` / `events: SharedFlow<CartEvent>` with `private val _events = Channel<CartEvent>(Channel.BUFFERED)` and `val events: Flow<CartEvent> = _events.receiveAsFlow()`.
- [x] 5.2 Replace both `emitNavigate(...)` overloads' `viewModelScope.launch { _events.emit(...) }` bodies with a direct `_events.trySend(CartEvent.NavigateToSummary(...))` call (no `launch` wrapper).
- [x] 5.3 Run the Phase 4 test alone and confirm GREEN.

## Phase 6: Full regression and build verification

- [x] 6.1 Run `./gradlew :app:testDebugUnitTest` and confirm all `CartViewModelTest` tests (14 original + 2 new = 16) and all `app/src/test/java/com/pedidosya/kata/ui/summary/SummaryViewModelTest.kt` (read-only reference, unchanged) tests pass.
- [x] 6.2 Run `./gradlew :app:assembleDebug` and confirm the narrowed `events: Flow<CartEvent>` type compiles cleanly at `app/src/main/java/com/pedidosya/kata/ui/cart/CartScreen.kt`'s collection site — no source edit expected there (design-verified).
- [x] 6.3 Grep `CartViewModel.kt` to confirm no `_state`, no handler `.value` read, and no `successFor`/`latestItems` remnants remain (proposal Success Criteria). `successFor` was inlined into `reduce()` during REFACTOR to satisfy this exact grep, per the design's "Public API deltas" list.
- [x] 6.4 **Follow-up (closes verify WARNING, obs #1443 / `verify-report.md`)**: add a dedicated test to `CartViewModelTest.kt` naming the exact edge case "a manual pull-to-refresh fails while the cart is already `Loaded`-empty" — `a failed manual refresh on an already-loaded empty cart keeps Success instead of demoting to Error`. Proves Decision 4's guard (`Failed` reachable only from `Loading`) by construction: cart reaches `Success(emptyList())` via init's successful load, `onRefresh()` fails on the second `repository.refresh()` call, and the assertion casts to `CartUiState.Success` (would throw `ClassCastException` and fail if the guard were absent and the state demoted to `Error`). Confirmed a genuine RED during authoring (first draft without a real suspension gate between the two `inputs.update` calls under `UnconfinedTestDispatcher` conflated the intermediate `isRefreshing=true` StateFlow emission and timed out on the third `awaitItem()`); fixed by mirroring the existing gated-second-call pattern from `manual refresh sets isRefreshing while in flight...`. `./gradlew :app:testDebugUnitTest --tests "*CartViewModelTest*" --rerun-tasks` → 17/17 (was 16/16). Full `./gradlew :app:testDebugUnitTest --rerun-tasks` → 49/49, 0 failures across all 9 suites.
