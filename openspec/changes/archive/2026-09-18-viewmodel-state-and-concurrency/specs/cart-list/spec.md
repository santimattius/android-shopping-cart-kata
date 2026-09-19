# Delta for cart-list

Reconciles `CartViewModel.state` production to the `combine(...).stateIn(...)` pattern `SummaryViewModel` already follows, and turns the verify-report's flagged empty-cart hazard (Engram obs #1424) into a covering scenario.

## MODIFIED Requirements

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
