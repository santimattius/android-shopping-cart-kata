<!--
Materialized from Engram (project: kata-mobile-android-empty, topic: sdd/shopping-cart/spec,
observation #1415) on 2026-09-18. The Engram artifact concatenates all three domains (cart-list,
coupon-validation, purchase-summary) into one spec document with per-domain headers; this file
extracts only the `cart-list` domain section, per OpenSpec's one-spec-per-domain convention. This
is a copy; Engram remains the live source of truth (single combined document there).
-->

# cart-list Specification

## Purpose

Offline-first cart list, per `sdd/shopping-cart/proposal`. New capability: `cart-list`.

## ADDED Requirements

### Requirement: Offline-first load and error handling

The system MUST render cached items immediately when a cache exists and MUST refresh from network in the background without blocking the list. The system MUST show Loading only on first load with no cache and no response yet. The system MUST show Error+Retry when no cache exists and the fetch fails. A failed background refresh over an existing cache MUST NOT surface a blocking error. An empty cart MUST render an empty-state message with zero subtotal.

#### Scenario: Cache present, refresh succeeds silently

- GIVEN a cached cart exists
- WHEN the screen opens
- THEN cached items render immediately and refresh replaces them silently, no spinner

#### Scenario: First load, no cache, network available

- GIVEN no cache exists
- WHEN the screen opens and the fetch succeeds
- THEN Loading renders, then items render

#### Scenario: First load, no cache, no network

- GIVEN no cache exists and there is no connectivity
- WHEN the screen opens
- THEN Error with a Retry action renders

#### Scenario: Cache present, background refresh fails

- GIVEN a cached cart exists
- WHEN the background refresh fails
- THEN cached items remain visible and no error is shown

#### Scenario: Empty cart

- GIVEN the cart has zero items
- WHEN the screen renders
- THEN an empty-state message renders with a zero subtotal

### Requirement: Retry action

The system MUST re-attempt the fetch when Retry is tapped on the Error state.

#### Scenario: Retry succeeds

- GIVEN Error is visible
- WHEN the user taps Retry and network is available
- THEN items load and render

### Requirement: Pull-to-refresh (optional)

The system MAY support pull-to-refresh, triggering the same refresh as background auto-refresh. Its absence MUST NOT block delivery.

#### Scenario: Manual refresh gesture

- GIVEN the list is visible
- WHEN the user pulls to refresh
- THEN the same silent-refresh behavior applies

### Requirement: Cart UI state modeling

The system MUST model cart-screen state as a closed type (e.g. sealed): Loading, Error(reason), Success(items, totals, couponState). The system MUST derive `state` from exactly one `combine` reduction of the Room-backed cart source (`observeCart()`) and a single input holder (coupon input text, coupon validation result, validating flag, refreshing flag), shared as `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loading)`. No code path outside that reduction MUST assign cart-screen state directly. Handlers MUST update inputs only through the input holder's `update {}`; handlers MUST NOT read a synchronous `.value` of the produced `state` to decide the next state. The Loading→Success promotion for a first load with zero cached and zero fetched items MUST be deterministic and MUST NOT depend on the relative interleaving of the cart-observation collector and the refresh call.
(Previously: only required the closed-type modeling; the state production mechanism and empty-cart first-load determinism were unspecified, and the shipped implementation used a hand-rolled `MutableStateFlow` mutated from six call sites, which left the empty-cart case able to get stuck on Loading depending on coroutine interleaving.)

#### Scenario: Exhaustive state handling

- GIVEN the state type is sealed
- WHEN the UI renders any state
- THEN Loading/Error/Success are handled by a compiler-enforced exhaustive branch

#### Scenario: Single-reduction state derivation

- GIVEN the cart source, coupon input, coupon validation result, and refresh/validation flags can each change independently
- WHEN any one of them changes
- THEN `state` recomputes through the single `combine(...).stateIn(...)` reduction
- AND no other code path assigns cart-screen state directly

#### Scenario: Empty cart resolves deterministically on first load

- GIVEN no cache exists and the first fetch response is a genuinely empty cart
- WHEN the fetch completes
- THEN `state` transitions from Loading to Success with zero items and a zero subtotal
- AND this transition occurs regardless of whether the cart-observation collector or the refresh call completes first
