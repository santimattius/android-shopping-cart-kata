# Spec Phase: No Delta Spec Required

**Change**: `behavioral-testing-expansion`

## Verdict

No delta spec was written against `cart-list`, `coupon-validation`, or `purchase-summary`. This
change adds test infrastructure and a container/presentational seam with no user-observable
behavior difference and no scenario to add, modify, remove, or rename.

## Verification

The proposal's "Capabilities" section already declares both New and Modified Capabilities as
`None`. This phase independently verified that claim against the current on-disk code
(`app/src/main/java/com/pedidosya/kata/ui/cart/CartScreen.kt`,
`.../ui/summary/SummaryScreen.kt`) before accepting it:

1. **`CartScreen` is already structured as a dispatcher plus pure render functions.** The
   ViewModel-bound `CartScreen(viewModel, onNavigateToSummary)` does exactly three things: collect
   `state`, run a `LaunchedEffect` that forwards `NavigateToSummary` events, and `when (state)`
   dispatch to `LoadingContent()` / `ErrorContent(onRetry)` / `CartContent(state, onRefresh,
   onCouponInputChanged, onApplyCoupon, onConfirmPurchase)`. The proposed split extracts exactly
   that `when` dispatch into a new `internal` stateless `CartScreen(state, callbacks...)` overload;
   the existing public `CartScreen(viewModel, ...)` keeps the collection + `LaunchedEffect` and
   delegates its `viewModel::retry` / `viewModel::onRefresh` / etc. references unchanged into the
   new overload. No branch, callback wiring, or private composable (`LoadingContent`,
   `ErrorContent`, `CartContent`, `CouponSection`, `CouponStatusMessage`) is added, removed, or
   edited — they are moved, not rewritten.

2. **`SummaryScreen` follows the identical pattern.** `SummaryScreen(viewModel)` collects `state`
   and dispatches `when (state)` to `LoadingContent()` / `SummaryContent(current)`. The split
   extracts that dispatch into an `internal` stateless `SummaryScreen(state: SummaryUiState)`
   overload; the wrapper keeps only the `collectAsStateWithLifecycle()` call and delegates.

3. **All user-visible literals and gating logic are untouched by the extraction.** The strings a
   test would assert against — `"No pudimos cargar tu carrito."`, `"Reintentar"`, `"Aplicar"`,
   `"Confirmar Compra"`, `"Tu carrito está vacío."`, the five `CouponStatusMessage` branches
   (`NotApplied`/`Valid`/`Invalid`/`Inactive`/`ServiceError`), `"Resumen de compra"`, `"Descuento
   aplicado: {pct}%"` vs. `"Sin cupón aplicado"`, `"Total a pagar: {total}"` — and the two
   enablement predicates (`enabled = !state.isValidating` on Apply, `enabled = state.canConfirm`
   on Confirm) and `PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = onRefresh)` are
   copied verbatim into the new internal overloads. A container/presentational split that changed
   any of these would itself be a behavior change requiring a delta spec; this one does not.

4. **The new Robolectric tests exercise existing spec requirements, not new ones.** They assert
   `cart-list`'s "Offline-first load and error handling", "Retry action", "Pull-to-refresh
   (optional)", and "Cart UI state modeling" requirements; `coupon-validation`'s "Confirm Purchase
   gating" and "Coupon validation result modeling" requirements (via the five status branches);
   and `purchase-summary`'s "Nominal percentage and final total display" requirement — all already
   defined in `openspec/specs/`. Writing Given/When/Then scenarios for them here would either
   duplicate the main specs verbatim (no new WHAT) or smuggle test-coverage detail (HOW) into a
   spec artifact, which `sdd-spec`'s own convention prohibits ("specs describe WHAT, not HOW").

5. **The dispatcher-scheduler fix and Robolectric infra changes are build/test-only.** They touch
   `MainDispatcherRule`, existing ViewModel test files, `gradle/libs.versions.toml`, and
   `app/build.gradle.kts` — no production code path a spec scenario could observe.

## Conclusion

This change has no spec-level surface. Proceed directly to `sdd-design` (or `sdd-tasks` if design
is already covered by the proposal's Approach/Affected Areas table) without a delta spec artifact.
