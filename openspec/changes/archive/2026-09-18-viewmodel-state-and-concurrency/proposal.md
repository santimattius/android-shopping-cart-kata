<!--
Materialized from Engram (project: android-shopping-cart-kata, topic: sdd/viewmodel-state-and-concurrency/proposal,
observation #1438) on 2026-09-18. This is a copy; Engram remains the live source of truth.
-->

# Proposal: ViewModel State Production and Structured Concurrency

Change 1 of 4 under the `architecture-modernization` initiative (goals 4 + 5 only).

## Intent

`CartViewModel` never implemented the `combine(...).stateIn(...)` production its own design (`sdd/shopping-cart/design`) mandated for both screens. It hand-rolls a `MutableStateFlow` mutated from six sites through non-atomic `.value` read-modify-write. `SummaryViewModel` does follow the design. This drift underlies the verify-report's empty-cart first-load hazard (obs #1424), where `Loading→Success` depends on coroutine interleaving. Reconcile before three downstream changes build on this state shape.

## Scope

### In Scope
- `CartViewModel.state` becomes one `combine(observeCart(), inputs).stateIn(viewModelScope, WhileSubscribed(5_000), Loading)` reduction.
- Private `MutableStateFlow<CartInputs>` (couponInput, coupon, isValidating, isRefreshing, loadPhase) mutated only via `update {}`; handlers stop reading `state.value`.
- Explicit `loadPhase` makes `Loading→Success|Error` a pure reduction, not a coroutine race.
- `CartEvent` delivery moves to `Channel(BUFFERED)` + `receiveAsFlow()`.
- `SummaryViewModel`/`SummaryUiState` unchanged — the reference pattern.

### Out of Scope
- A `DispatcherProvider` abstraction (deferred, see Approach).
- Navigation, `@Immutable`/`ImmutableList`, and test additions (changes 2-4).

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `cart-list`: "Cart UI state modeling" — state MUST derive from a single reduction of the Room source plus one input holder; empty-cart first load MUST resolve deterministically.
- `coupon-validation`: "Confirm Purchase gating" — the navigation handoff MUST be delivered exactly once and MUST survive a collector gap.

## Approach

Observable behavior is preserved; only the production mechanism changes. Handlers become input mutations, so no handler needs a synchronous `state.value` read, closing every lost-update window.

`Channel` over `SharedFlow`: per `kotlin-concurrency-and-flow`, an exactly-once handoff to one consumer that must survive a collector gap is a buffered `Channel`. `CartScreen` has exactly one collector, and today's `LaunchedEffect` resubscription window after a configuration change can drop a `NavigateToSummary`.

`DispatcherProvider` deferred: repositories already inject `CoroutineDispatcher` (default `Dispatchers.IO`), so the testability seam exists, and this change adds zero I/O call sites. Introduce it when a third repository or a non-IO dispatcher need appears.

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `ui/cart/CartViewModel.kt` | Modified | Reducer, input holder, `Channel` events |
| `ui/cart/CartScreen.kt` | Modified | Declared `events` type only (`SharedFlow`→`Flow`) |
| `ui/summary/SummaryViewModel.kt` | Unchanged | Reference pattern |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Loading/Error transition regression | Med | Strict TDD is on; the 14 existing `CartViewModelTest` tests MUST pass unchanged |
| `receiveAsFlow()` is single-consumer | Low | One collector today; the spec delta states the constraint |
| Scope creep into changes 2-4 | Med | `CartUiState` and `CartEvent` shapes stay frozen |

## Rollback Plan

State production and event delivery land as separate work-unit commits. `git revert` of either restores prior behavior: no schema, navigation-argument, DI, or public state-type change is involved, so revert needs no migration and cannot leave partial state.

## Dependencies

None. Changes 2-4 depend on this one.

## Success Criteria

- [ ] `state` comes from exactly one `stateIn`; no `_state` assignment remains.
- [ ] No handler reads `state.value`; input mutation goes through `update {}`.
- [ ] `CartEvent` is delivered by a buffered `Channel` as `receiveAsFlow()`.
- [ ] Existing unit tests pass unchanged; `:app:testDebugUnitTest` and `:app:assembleDebug` are green.
