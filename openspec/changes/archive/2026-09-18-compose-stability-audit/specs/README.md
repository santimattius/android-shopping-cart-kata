# Spec Phase: No Delta Spec Required

**Change**: `compose-stability-audit`

## Verdict

No delta spec was written against `cart-list`, `coupon-validation`, or `purchase-summary`. This
change is a type-level Compose stability fix with no user-observable behavior difference and no
scenario to add, modify, remove, or rename.

## Verification

The proposal's "Capabilities" section already declares both New and Modified Capabilities as
`None`. This phase independently verified that claim against the current code before accepting it:

1. **`CartUiState.Success.items` and `SummaryUiState.Success.items`** currently type as
   `kotlin.collections.List<CartItem>`. The change migrates both to
   `kotlinx.collections.immutable.ImmutableList<CartItem>`, converted once at each ViewModel's
   `Success` construction site (`CartViewModel.successFor`, `SummaryViewModel.state`). The
   underlying `CartRepository.observeCart(): Flow<List<CartItem>>` contract is untouched.

2. **`ImmutableList<T>` is a subtype of `List<T>`**. Every consumer of `.items` —
   `CartContent`/`CartItemRow` and `SummaryContent`/`SummaryItemRow` in `CartScreen.kt` and
   `SummaryScreen.kt` — passes it straight into `androidx.compose.foundation.lazy.items(items:
   List<T>, key, ...)`, whose signature only requires `List<T>`. No consumer pattern-matches on
   the concrete `List` type, casts to a mutable list type, or calls a mutating method (`add`,
   `removeAt`, etc.) on `items`. Compilation and rendering are unaffected.

3. **Existing tests assert structural equality, not concrete type.** `SummaryViewModelTest` and
   `CartViewModelTest` use `assertEquals(items, success.items)` / `MutableStateFlow(items)` against
   plain `listOf(...)` fixtures. Kotlin's `List.equals()` contract is structural (same size, same
   elements, same order) regardless of the concrete implementing class, so these assertions hold
   unchanged after the migration; the proposal's own task list already accounts for updating
   fixture *types* (not assertions) for this reason.

4. **No existing spec requirement references a concrete collection type.** The `cart-list`
   spec's "Cart UI state modeling" requirement models `Success(items, totals, couponState)` as a
   closed/sealed shape — it does not constrain `items` to `List` specifically, and no Given/When/Then
   scenario in `cart-list`, `coupon-validation`, or `purchase-summary` depends on the concrete
   collection type, mutability, or identity semantics of `items`.

5. **The `composeCompiler` reports/metrics wiring** (`app/build.gradle.kts`) is a build-tooling
   addition, property-gated (`composeReports`), and produces no runtime behavior difference —
   nothing a Given/When/Then scenario could observe.

## Conclusion

This change has no spec-level surface. Proceed directly to `sdd-design` (or `sdd-tasks` if design
is already covered by the proposal's Approach/Decisions table) without a delta spec artifact.
