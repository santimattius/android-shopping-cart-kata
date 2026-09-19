# Design: ViewModel State Production and Structured Concurrency

## Technical Approach

Reconcile `CartViewModel` with the pattern its own archived design already mandated for both screens (`openspec/changes/archive/2026-09-18-shopping-cart/design.md`, "ViewModel exposure (single pattern for both screens)": `combine(observeCart(), couponState, refreshState) { ... }.stateIn(viewModelScope, WhileSubscribed(5_000), Loading)`). `SummaryViewModel` implements it; `CartViewModel` drifted into a hand-rolled `MutableStateFlow` mutated from six sites. This change removes the drift; it does not revise the baseline.

Two flows are combined: `repository.observeCart()` (Room, source of truth) and one private `MutableStateFlow<CartInputs>` holding every locally-owned input. `CartUiState`, `CartEvent`, and `CartErrorReason` shapes stay frozen (changes 2-4 depend on them). `DispatcherProvider` stays out of scope per the proposal — this change adds zero I/O call sites.

## Root Cause and the Deterministic Fix

Today, an empty cart can only reach `Success` through one guard, `result.isSuccess && _state.value is CartUiState.Loading`, inside the `refresh()` coroutine — the `observeCart()` collector refuses to promote an empty list (`items.isNotEmpty() || current is Success`). Whenever that guard is false when the refresh result lands, the empty cart has no remaining promotion path. Reproducible today: from `Error`, a successful `retry()` with an empty cart stays `Error` forever (state is `Error`, so neither branch fires). The user-visible symptom class recorded in the verify report (obs #1424) is the same defect: emptiness is a conditional side effect instead of a state.

The reduction makes it total and order-independent:

| `items` | `loadPhase` | reduced state |
|---|---|---|
| non-empty | any | `Success(items, …)` — cache always wins |
| empty | `Loading` | `Loading` |
| empty | `Failed` | `Error(NoCacheAvailable)` |
| empty | `Loaded` | `Success(emptyList(), …)` |

`combine` emits nothing until both sources have emitted, so the pre-Room window still renders the `stateIn` initial value `Loading`. Interleaving of the Room emission and the refresh completion now changes only which intermediate tuple is seen, never the terminal tuple — so the terminal state is a pure function of `(items, inputs)`.

## Architecture Decisions

| # | Decision | Alternatives rejected | Rationale |
|---|---|---|---|
| 1 | `state = combine(observeCart(), inputs).stateIn(viewModelScope, WhileSubscribed(5_000), Loading)` | Keep `MutableStateFlow` and only patch the guard | Patching keeps six mutation sites racing; the archived design already mandates this pattern and `SummaryViewModel` proves it in-repo |
| 2 | Handlers read/write `inputs` only; no handler reads `state.value` | Handlers keep reading `state.value` | Under `WhileSubscribed`, `state.value` is the initial `Loading` until someone subscribes — a handler reading it would break with no collector attached. `inputs` is always live |
| 3 | Explicit `loadPhase: Loading \| Loaded \| Failed` in `CartInputs` | Infer load status from `items.isEmpty()` | Empty-and-unknown vs. empty-and-confirmed are different states; a sentinel cannot distinguish them |
| 4 | `Failed` only reachable from `Loading` | Any failed refresh sets `Failed` | Preserves "cache stays visible when background refresh fails" and stops a manual pull-to-refresh failure from demoting a loaded empty cart to `Error` |
| 5 | `Channel<CartEvent>(Channel.BUFFERED)` + `receiveAsFlow()`, emitted with `trySend` | `MutableSharedFlow(replay = 0, extraBufferCapacity = 1)` | replay-0 drops a `NavigateToSummary` emitted while `LaunchedEffect` is resubscribing after a configuration change. `receiveAsFlow()` (not `consumeAsFlow()`) permits sequential re-collection, which is exactly that resubscription. `trySend` removes the `viewModelScope.launch` wrapper, making emission synchronous with the handler |
| 6 | `SharingStarted.WhileSubscribed(5_000)` | `Eagerly` | Matches `SummaryViewModel` and the archived design; Decision 2 removes the only reason `.value` had to be hot |

**Single-consumer constraint (Decision 5).** A `Channel` is fan-out, not broadcast: each event reaches exactly one collector. Two collectors would *split* the stream, not duplicate it — a behavioral difference from `SharedFlow`. `CartScreen` has exactly one collector today; any future second observer must use durable state or a deliberate `SharedFlow`, not this property.

## Data Flow

```
Room ──Flow<List<CartItem>>─────────────────────┐
  ▲                                             │
  │ repository.refresh()                        ├─ combine ─→ reduce(items, inputs)
  │                                             │       │
viewModelScope.launch                           │       ▼
  │  update { loadPhase, isRefreshing }         │  stateIn(viewModelScope,
  ▼                                             │     WhileSubscribed(5_000), Loading)
MutableStateFlow<CartInputs> ───────────────────┘       │
  ▲   update { couponInput, coupon, isValidating }      ▼
  │                                          StateFlow<CartUiState> ──→ collectAsStateWithLifecycle
  │                                                                          │
CartScreen intents ───────────────────────────────────────────────────────────┘
  │ onConfirmPurchase
  ▼
trySend ─→ Channel<CartEvent>(BUFFERED) ─ receiveAsFlow() ─→ LaunchedEffect(viewModel) → onNavigateToSummary
```

Confirm sequence (typed-but-unapplied code): `onConfirmPurchase` reads `inputs.value.couponInput` → `launch { update{isValidating=true}; validateCoupon(code); update{coupon=result, isValidating=false} }` → on `Valid`, `trySend(NavigateToSummary)`. The suspending call is captured **outside** every `update {}` lambda, because `update` may retry its lambda.

## Interfaces / Contracts

```kotlin
private enum class LoadPhase { Loading, Loaded, Failed }

private data class CartInputs(
    val loadPhase: LoadPhase = LoadPhase.Loading,
    val couponInput: String = "",
    val coupon: CouponValidationResult = CouponValidationResult.NotApplied,
    val isValidating: Boolean = false,
    val isRefreshing: Boolean = false,
)

// refresh: `manual` marks the pull-to-refresh gesture only (Decision 4)
private fun refresh(manual: Boolean = false) = viewModelScope.launch {
    if (manual) inputs.update { it.copy(isRefreshing = true) }
    val result = repository.refresh()
    inputs.update {
        it.copy(
            loadPhase = when {
                result.isSuccess -> LoadPhase.Loaded
                it.loadPhase == LoadPhase.Loading -> LoadPhase.Failed
                else -> it.loadPhase
            },
            isRefreshing = if (manual) false else it.isRefreshing,
        )
    }
}

val events: Flow<CartEvent> = _events.receiveAsFlow()   // was SharedFlow<CartEvent>
```

Public API deltas: `events` narrows from `SharedFlow<CartEvent>` to `Flow<CartEvent>`. `state` keeps type `StateFlow<CartUiState>`. `latestItems`, `successFor(...)`, `_state`, and `_events.emit` disappear.

Handler guards move from render-phase checks (`current !is Success → return`) to input preconditions (blank code, in-flight validation). The render phase is now a derived projection, so it is not a valid precondition. `Loading`/`Error` render no coupon field, Apply, or Confirm affordance, so the relaxation is UI-unreachable; it is recorded here rather than silently dropped.

## File Changes

| File | Action | Description |
|---|---|---|
| `app/src/main/java/com/pedidosya/kata/ui/cart/CartViewModel.kt` | Modify | Reduction, `CartInputs`/`LoadPhase` holder, `Channel` events |
| `app/src/main/java/com/pedidosya/kata/ui/cart/CartScreen.kt` | Modify (KDoc only) | **Verified**: line 49 `viewModel.events.collect { … }` is type-inferred and there is no `SharedFlow` declaration or import in this file, so no source edit is required. Only the KDoc at lines 36-37 is touched, if at all |
| `app/src/test/java/com/pedidosya/kata/ui/cart/CartViewModelTest.kt` | Modify | Two RED tests added; two existing assertions re-sited (see below) |
| `app/src/main/java/com/pedidosya/kata/ui/cart/CartUiState.kt`, `CartEvent.kt`, `ui/summary/**` | Unchanged | Frozen shapes / reference pattern |

## Testing Strategy

Strict TDD is on. `CartViewModelTest` (14 tests, Turbine + MockK) is the regression gate.

**RED first — smallest test that proves the empty-cart fix** (fails today by Turbine timeout, no interleaving dependence):

```kotlin
@Test
fun `a successful retry with an empty cart leaves Error for an empty Success`() =
    runTest(UnconfinedTestDispatcher()) {
        every { repository.observeCart() } returns MutableStateFlow(emptyList())
        coEvery { repository.refresh() } returnsMany
            listOf(Result.failure(IOException("offline")), Result.success(Unit))
        val viewModel = newViewModel()
        viewModel.state.test {
            assertEquals(CartUiState.Error(CartErrorReason.NoCacheAvailable), awaitItem())
            viewModel.retry()
            assertEquals(
                CartUiState.Success(emptyList(), calculateTotals(emptyList(), null)),
                awaitItem(),
            )
        }
    }
```

RED #2 — event survives a collector gap: `onConfirmPurchase()` with a blank code **before** any collector, then `events.test { assertEquals(NavigateToSummary("", 0.0, "all"), awaitItem()) }`. Fails today (replay-0 `SharedFlow` drops it), passes with the buffered `Channel`.

| Layer | What to test | Approach |
|---|---|---|
| Unit | The 14 existing behaviors, unchanged assertions | Turbine + MockK, `runTest(UnconfinedTestDispatcher())` |
| Unit | Empty-cart retry recovery; event across a collector gap | The two RED tests above |
| Build | Compilation of the narrowed `events` type at the `CartScreen` call site | `./gradlew :app:assembleDebug` |

**Two existing tests need their assertion technique re-sited (not their asserted behavior).** `confirming with a typed but never-applied code…` (line 302) and `confirming with an invalid or errored coupon blocks navigation` (line 327) read `viewModel.state.value` with **no collector ever attached**. Under `WhileSubscribed`, `.value` is the initial `Loading` until first subscription, so both casts to `Success` would fail. Fix: attach a collector — wrap the assertion in `viewModel.state.test { … }`, or `backgroundScope.launch { viewModel.state.collect() }` before it. This is a test-mechanism consequence of Decision 6, not an observable behavior change, and it is the one place the proposal's "14 tests pass unchanged" needs qualification.

**Design-time observation, not a mandate (change 3's territory).** Every test uses `runTest(UnconfinedTestDispatcher())` *and* a `MainDispatcherRule` whose default is a **separate** `UnconfinedTestDispatcher` with its own scheduler. Two schedulers means `advanceUntilIdle`/`runCurrent` would not coordinate the two, and eager unconfined execution is the only reason ordering currently looks deterministic. Per the official `kotlinx-coroutines-test` guidance, `StandardTestDispatcher` is the default that exposes ordering bugs; `UnconfinedTestDispatcher` can mask them — notably here, it hides the exact `init`-collector vs. `refresh()` interleaving this change removes. Do not change the dispatcher strategy in this change; `behavioral-testing-expansion` owns it. The fix in this design is deliberately dispatcher-independent: after it lands, the terminal state is a pure reduction, so it holds under either dispatcher.

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or process-integration boundary. This change is confined to in-process state production inside one ViewModel.

## Migration / Rollout

No migration. No schema, navigation-argument, DI, or persisted-state change; the UI is visually identical apart from the empty-cart fix.

**Rollback plan.** Two work-unit commits, revertable independently and in any order:

1. `refactor(cart): derive CartUiState from a single combine().stateIn() reduction` — `CartViewModel` state production + the two re-sited assertions + RED #1.
2. `refactor(cart): deliver CartEvent through a buffered Channel` — `Channel`/`receiveAsFlow()`/`trySend` + RED #2 + the `CartScreen` KDoc.

Revert steps: `git revert <sha>` for the offending commit; no feature flag, because the change is UI-invisible and internal. Commit 1's revert restores the six mutation sites and reinstates the empty-cart defect. Commit 2's revert restores `SharedFlow` and reopens the collector-gap window. Neither revert can leave partial state: no data is written, and `CartUiState`/`CartEvent` public shapes are byte-identical before and after. If only one commit reverts, the other still compiles — they touch disjoint members of `CartViewModel`.

## Open Questions

- [ ] None blocking. The one judgement call escalated to `sdd-tasks`: re-siting two existing test assertions (documented above) is required by Decision 6 and qualifies the proposal's "14 tests pass unchanged" risk row.
