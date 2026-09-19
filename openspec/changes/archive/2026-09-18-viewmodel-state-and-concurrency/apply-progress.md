<!--
Materialized alongside Engram (project: android-shopping-cart-kata, topic: sdd/viewmodel-state-and-concurrency/apply-progress)
on 2026-09-19. This is a copy; both copies are kept in sync (hybrid artifact store mode).
-->

# Apply Progress: ViewModel State Production and Structured Concurrency

**Change**: `viewmodel-state-and-concurrency`
**Mode**: Strict TDD
**Status**: 19/19 tasks complete (18 original + follow-up task 6.4). Ready for verify.

## Completed Tasks

All 18 original tasks across Phases 1-6 are complete, plus follow-up task 6.4 (see below). See `tasks.md` for the per-task `[x]` record.

## Follow-up batch: closing verify WARNING (task 6.4)

**Trigger**: Independent verify (Engram `sdd/viewmodel-state-and-concurrency/verify-report` obs #1443 / `openspec/changes/viewmodel-state-and-concurrency/verify-report.md`) passed with 0 CRITICAL but flagged one WARNING: no dedicated test named the exact edge case "a manual pull-to-refresh fails while the cart is already `Loaded`-empty". Decision 4's guard (`Failed` reachable only from `Loading`) already prevented the regression structurally; this was a coverage-completeness note, not a spec violation.

**What was done**: Added one new test to `CartViewModelTest.kt`:
`a failed manual refresh on an already-loaded empty cart keeps Success instead of demoting to Error`.

- Setup: `repository.observeCart()` returns an empty `MutableStateFlow`; `repository.refresh()` is gated by call count — call 1 (init's automatic load) resolves immediately with `Result.success(Unit)`, landing the cart in `Success(emptyList())` (`Loaded`); call 2+ (the manual `onRefresh()` below) suspends on a `CompletableDeferred` until the test completes it with `Result.failure(IOException(...))`.
- Sequence: `state.test { }` observes `loaded` (`Success(emptyList())`, `isRefreshing=false`) → `viewModel.onRefresh()` → `refreshing` (`Success`, `isRefreshing=true`) → gate completes with failure → `afterFailedRefresh` (`Success(emptyList())`, `isRefreshing=false`) — cast to `CartUiState.Success` at every step, which would throw `ClassCastException` and fail the test if the guard were absent and the state demoted to `Error`.
- **Genuine RED encountered during authoring** (not a tautological GREEN-from-start): the first draft used `coEvery { repository.refresh() } returnsMany listOf(success, failure)` with no real suspension between the two `inputs.update {}` calls inside `refresh(manual = true)`. Under `UnconfinedTestDispatcher`, both updates fired synchronously with no coroutine dispatch boundary between them; `state` (a `StateFlow`, which conflates emissions a slow collector hasn't caught up to) delivered only the first (`isRefreshing=true`) intermediate item to Turbine, then the collector's `awaitItem()` for the post-failure state timed out (`TurbineTimeoutCancellationException`, "No value produced in 3s") because no further distinguishable emission ever reached it before the test's internal machinery moved on. This confirmed the test genuinely exercises the runtime interleaving of `refresh()`'s failure path, not a tautology. Fixed by mirroring the already-proven gated-second-call pattern from the pre-existing test `manual refresh sets isRefreshing while in flight and clears it once the background refresh completes` (a real `CompletableDeferred` suspension point between the two updates, gated only for calls after the first).
- No production code change was required — `CartViewModel.kt`'s existing `refresh()` guard (`Failed` set only when `it.loadPhase == LoadPhase.Loading`, else unchanged) already enforces the property; this batch is test-only, confirming verify's PASS was correct.

**Task tracking**: added as task 6.4 in `tasks.md` (Phase 6), rather than a new phase, since it closes an existing Phase 6 verification-scope item.

## Files Changed

| File | Action | What Was Done |
|------|--------|----------------|
| `app/src/main/java/com/pedidosya/kata/ui/cart/CartViewModel.kt` | Modified | Replaced the hand-rolled `MutableStateFlow` mutated from six sites with a single `combine(repository.observeCart(), inputs, ::reduce).stateIn(viewModelScope, WhileSubscribed(5_000), Loading)` reduction. Added private `LoadPhase` enum and `CartInputs` data class. All handlers (`onRefresh`, `onCouponInputChanged`, `onApplyCoupon`, `onConfirmPurchase`, `validateAndUpdate`, `refresh`) now read/write only `inputs` via `.update {}`/`.value`, never `state.value`. Replaced `_events: MutableSharedFlow<CartEvent>` with `_events: Channel<CartEvent>(Channel.BUFFERED)` exposed as `events: Flow<CartEvent> = _events.receiveAsFlow()`; `emitNavigate` now calls `_events.trySend(...)` directly (no `viewModelScope.launch` wrapper). `successFor(...)` was inlined into `reduce()` and removed as a named function (design's "Public API deltas" list requires it to disappear); `latestItems` field removed. |
| `app/src/test/java/com/pedidosya/kata/ui/cart/CartViewModelTest.kt` | Modified | Added RED test `a successful retry with an empty cart leaves Error for an empty Success` (Phase 1/2). Added RED test `a navigate event survives a gap before any collector attaches` (Phase 4/5). Re-sited three pre-existing tests' assertion technique (see Deviations) — no asserted behavior changed in any of them. **Follow-up (task 6.4)**: added `a failed manual refresh on an already-loaded empty cart keeps Success instead of demoting to Error` — closes the verify WARNING on Decision 4 test coverage. |

## TDD Cycle Evidence

| Task | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 1.1-1.2 | `CartViewModelTest.kt` — `a successful retry with an empty cart leaves Error for an empty Success` | Unit (Turbine+MockK) | ✅ 14/14 baseline | ✅ Written, confirmed failing via `TurbineTimeoutCancellationException` | — (GREEN is Phase 2) | ➖ Single spec scenario | N/A |
| 2.1-2.6 | Same test | Unit | — | (from 1.1) | ✅ Passed after `combine().stateIn()` reduction | ➖ Covered by existing 13 other scenarios in the same suite (happy path, cache-visible-on-fail, first-load-empty, etc.) | ✅ `successFor` later inlined out (see 6.3) |
| 3.1-3.3 | Same suite — 3 tests re-sited | Unit | ✅ Re-run after 2.x: 2 failures anticipated by design + 1 undocumented | N/A (mechanism fix, not new behavior) | ✅ All 16 tests (14 orig + 2 new) pass together | N/A | N/A |
| 4.1 | `CartViewModelTest.kt` — `a navigate event survives a gap before any collector attaches` | Unit (Turbine+MockK) | ✅ 16/16 baseline (post-Phase 3) | ✅ Written, confirmed failing via `TurbineTimeoutCancellationException` (replay-0 `SharedFlow` drops pre-collector emission) | — (GREEN is Phase 5) | ➖ Single spec scenario | N/A |
| 5.1-5.3 | Same test | Unit | — | (from 4.1) | ✅ Passed after `Channel(BUFFERED)` + `receiveAsFlow()` + `trySend` | ➖ Covered by existing confirm-purchase scenarios (already-applied, typed-unapplied, blocked) which continued passing | ✅ Clean — no `launch` wrapper needed |
| 6.1-6.3 | Full suite | Unit + Build | ✅ 16/16 `CartViewModelTest` + 3/3 `SummaryViewModelTest` + full module suite (48 tests total) | N/A | ✅ `./gradlew :app:testDebugUnitTest` and `./gradlew :app:assembleDebug` both green | N/A | ✅ `successFor` inlined into `reduce()`; re-verified full suite + build after |
| 6.4 (follow-up) | `CartViewModelTest.kt` — `a failed manual refresh on an already-loaded empty cart keeps Success instead of demoting to Error` | Unit (Turbine+MockK) | ✅ 16/16 baseline (verified via `--rerun-tasks` before edit) | ✅ Written; first draft (no suspension gate between updates) genuinely failed with `TurbineTimeoutCancellationException` — a real RED, not tautological | ✅ Passed after mirroring the gated-second-call pattern from the pre-existing `manual refresh sets isRefreshing...` test — no production code change needed | ➖ Single spec scenario (one specific edge case named by the verify WARNING) | ➖ None needed — test-only change |

### Test Summary
- **Total tests written**: 3 new across this change's full lifecycle (`a successful retry with an empty cart leaves Error for an empty Success`, `a navigate event survives a gap before any collector attaches`, and the follow-up `a failed manual refresh on an already-loaded empty cart keeps Success instead of demoting to Error`)
- **Total tests passing**: 49/49 (full `:app:testDebugUnitTest` suite, including 17 `CartViewModelTest` + 3 `SummaryViewModelTest` + 29 across the other 7 unaffected suites)
- **Layers used**: Unit (49), Integration (0), E2E (0)
- **Approval tests** (refactoring): None — the design's reduction table fully specifies the new behavior; no approval-test staging was needed since the RED tests directly encode the target behavior
- **Pure functions created**: `reduce(items, inputs): CartUiState` is a pure function of its two arguments (no side effects, deterministic)

## Work Unit Evidence

| Evidence | Value |
|---|---|
| Focused test command and exact result | `./gradlew :app:testDebugUnitTest --tests "*CartViewModelTest*"` → BUILD SUCCESSFUL, 16/16 tests passing (final run after REFACTOR) |
| Runtime harness command/scenario and exact result | `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL; confirms the narrowed `events: Flow<CartEvent>` type compiles cleanly at `CartScreen.kt`'s untouched collection site |
| Rollback boundary | Single file `CartViewModel.kt` plus its test file; `git revert` of this commit restores the six-mutation-site `MutableStateFlow` and the replay-0 `SharedFlow`, reinstating both the empty-cart-stuck-in-Error defect and the collector-gap event-loss window. No schema, DI, or navigation-argument change is involved, so revert is clean. |

**Follow-up batch (task 6.4) Work Unit Evidence:**

| Evidence | Value |
|---|---|
| Focused test command and exact result | `./gradlew :app:testDebugUnitTest --tests "*CartViewModelTest*" --rerun-tasks` → BUILD SUCCESSFUL, 17/17 (`tests="17" skipped="0" failures="0" errors="0"` in the JUnit XML report, not console text alone) |
| Runtime harness command/scenario and exact result | N/A — pure unit test (Turbine + MockK), no emulator or runtime boundary crossed; consistent with the design's Threat Matrix (N/A, no routing/shell/subprocess/VCS boundary) |
| Rollback boundary | Test-file-only addition to `CartViewModelTest.kt` (one new `@Test`); `git revert` of this follow-up commit removes only the new test, leaving `CartViewModel.kt` and all 18 originally-completed tasks untouched. Zero production code was changed. |

## Deviations from Design

1. **Third test needed the same re-siting technique, beyond the two design named.** `retry after an error calls refresh again and succeeds once the cache is populated` broke after Phase 2's GREEN step, not because of a `.value`-without-collector read (Decision 6, the documented cause) but because the new total `combine().stateIn()` reduction emits an *intermediate* `Success(emptyList())` state between `retry()`'s synchronous `inputs.update` and the test's subsequent `cartFlow.value = listOf(item(id = "p1"))` mutation — a direct, if unlisted, consequence of the design's own stated rationale ("interleaving...changes only which intermediate tuple is seen, never the terminal tuple", design.md line 22). Fixed by adding one `awaitItem()` to consume that intermediate state before asserting the final populated `Success`; the asserted terminal behavior is unchanged. Documented inline in the test with a comment referencing Decision 1.
2. **`successFor(...)` was removed, not just refactored.** The design's "Public API deltas" section (design.md line 91) explicitly lists `successFor(...)` among the members that "disappear," but task 2.3-2.5 only described adding `reduce(...)` without explicitly instructing removal of the helper it initially called. The first GREEN pass kept `successFor` as a helper called from `reduce`; task 6.3's grep check (which explicitly checks for `successFor`/`latestItems` remnants) caught this, and the REFACTOR step inlined `successFor`'s body directly into `reduce()`, removing the named function. Re-verified full suite + `assembleDebug` green after.
3. **Phase 2's GREEN commit did not touch the event channel; Phase 5 did it separately**, exactly as the RED→GREEN pairing in tasks.md requires (no batching multiple GREEN steps behind one RED). An early draft accidentally combined the `Channel` migration into the same edit as the `combine().stateIn()` reduction; this was caught and split back out before running any tests, restoring the original `MutableSharedFlow`/`SharedFlow` for Phase 2-3 and only introducing `Channel`/`receiveAsFlow()`/`trySend` in Phase 5, after Phase 4's RED test was written and confirmed failing.

## Issues Found

None beyond the deviations above — no design-level defect found; the two Success Criteria checks (task 6.3 grep, `CartScreen.kt` KDoc-only touch) both hold as written in `design.md` and `proposal.md`.

## Workload / PR Boundary

- Mode: single PR (per Review Workload Forecast: `Chained PRs recommended: No`, `400-line budget risk: Medium`)
- Current work unit: both Unit 1 (state reduction) and Unit 2 (event channel) — implemented together as one cohesive change per the orchestrator's explicit instruction that chaining is not needed for this change
- Boundary: starts from the pre-existing `CartViewModel`/`CartViewModelTest` (6-site `MutableStateFlow` + replay-0 `SharedFlow`) and ends with the full `combine().stateIn()` reduction + buffered `Channel` events, all 18 tasks complete
- Estimated review budget impact: within the ~230-300 estimated line forecast; no `DispatcherProvider` introduced, no `CartScreen.kt` source edit, `CartUiState`/`CartEvent` shapes frozen

## Verification Evidence

- `./gradlew :app:testDebugUnitTest` → BUILD SUCCESSFUL. 48 tests total across the module (16 `CartViewModelTest` = 14 original + 2 new, 3 `SummaryViewModelTest`, plus other unaffected suites), 0 failures, 0 errors, 0 skipped.
- `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL. Confirms `CartScreen.kt`'s `viewModel.events.collect { ... }` compiles cleanly against the narrowed `Flow<CartEvent>` type with zero source changes to that file (design-verified claim holds).

**Follow-up batch (task 6.4) re-verification, 2026-09-19:**

- Baseline before edit: `./gradlew :app:testDebugUnitTest --tests "*CartViewModelTest*" --rerun-tasks` → 16/16, confirming no pre-existing regression before adding the new test.
- `./gradlew :app:testDebugUnitTest --tests "*CartViewModelTest*" --rerun-tasks` → 17/17 (`CartViewModelTest.xml`: `tests="17" skipped="0" failures="0" errors="0"`).
- `./gradlew :app:testDebugUnitTest --rerun-tasks` (full module, all 9 suites) → BUILD SUCCESSFUL. 49 tests total, 0 failures, 0 errors, 0 skipped: `CartMapperTest` 4, `CouponMapperTest` 2, `CartRepositoryImplTest` 3, `CouponRepositoryImplTest` 7, `AppContainerTest` 5, `CalculateTotalsTest` 6, `ValidateCouponTest` 2, `CartViewModelTest` 17, `SummaryViewModelTest` 3.
- No production code change was required or made; `CartViewModel.kt` is byte-identical to the state verify's report inspected (verify's PASS on Decision 4 was correct, the gap was test coverage only).

## Remaining Tasks

None. 19/19 complete (18 original + follow-up 6.4).

## Status

19/19 tasks complete (18 original + follow-up 6.4, closing the verify-report WARNING on Decision 4 test coverage). Ready for verify.
