<!--
Materialized from Engram (project: kata-mobile-android-empty, topic: sdd/shopping-cart/proposal,
observation #1414) on 2026-09-18. This is a copy; Engram remains the live source of truth.
-->

# Proposal: Shopping Cart with Category-Scoped Coupon Validation

## Intent

The `:app` scaffold is empty: no navigation, networking, persistence, DI, or unit-test infrastructure. Deliver the kata's graded feature — an offline-first cart list, remote coupon validation, and a purchase summary — on an architecture a reviewer can grade against the official Android app architecture guide. Success: cart renders from cache and refreshes from network; a typed coupon is validated remotely and discounts only its `applicable_category` subtotal; Summary shows nominal % and final total; all logic covered by unit tests written first (Strict TDD).

## Scope

### In Scope
- Cart screen: offline-first list (Room cache + Retrofit refresh), Loading/Error/Success states, item image/title/quantity/price, subtotal.
- Coupon field with a separate **Apply** button: remote-only validation, live preview (code, nominal %, discounted total) or typed error (invalid / inactive / service failure).
- **Confirm Purchase** button: empty field → navigate with 0% and no coupon call; entered code → reuse validated state (validate if not yet applied); invalid/error blocks navigation.
- Summary screen: item recap, nominal coupon %, final total.
- Discount rule: % applies only to the subtotal of items matching `applicable_category` (`all` matches every item); other items pay full price.
- Foundation: Navigation-Compose, Retrofit+OkHttp+JSON, Room, `ui/theme` package, manual constructor DI wired from `App.kt`, unit-test stack (JUnit, coroutines-test, MockK, Turbine), `git init`.
- Optional (non-blocking): pull-to-refresh on the cart list.

### Out of Scope
- Coupon caching or offline coupon validation; local coupon mock or fallback.
- Effective/blended discount percentage in Summary.
- Cart mutation (add/remove/quantity edit), checkout/payment, auth, multi-coupon stacking, multi-module split, DI frameworks (Hilt/Koin), MVI reducers.

## Capabilities

### New Capabilities
- `cart-list`: offline-first retrieval, caching, and display of cart items with totals.
- `coupon-validation`: remote-only coupon lookup, activation/category rules, and category-scoped discount calculation.
- `purchase-summary`: navigation contract and presentation of final total and nominal discount.

### Modified Capabilities
- None.

## Approach

Official Android architecture, single module, package-by-layer under `com.pedidosya.kata`:

- **data**: `CartRemoteDataSource` (Retrofit, `CartResponseDto{cart{id,currency,items}}`, float-safe price), `CartLocalDataSource` (Room `CartItemEntity` + DAO exposing `Flow`), `CouponRemoteDataSource` (fetches the full coupon list; code match happens client-side), repositories mapping DTO/entity → domain.
- **domain**: immutable `CartItem`, `Coupon`, `CartTotals`; use cases `ObserveCart`, `RefreshCart`, `ValidateCoupon`, `CalculateTotals` (pure, category-scoped math — the primary TDD target).
- **ui**: Compose screens + ViewModels exposing `StateFlow<UiState>`; intents as a sealed `Event` type. Navigation-Compose `NavHost` in `MainActivity`, route `cart → summary` passing final total and nominal % (no domain objects across the back stack).

Modeling: `sealed interface CartUiState { Loading; Error(reason); Success(items, totals, couponState) }` and `sealed interface CouponValidationResult { NotApplied; Valid(coupon, discountedTotal); Invalid; Inactive; ServiceError }`. Repositories return a `Result`-style wrapper, never throw across layers.

Offline-first: Room `Flow` is the single source of truth; the network refresh writes to Room and never renders directly.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `gradle/libs.versions.toml`, `app/build.gradle.kts` | Modified | Navigation, Retrofit/OkHttp/JSON, Room + KSP, test stack |
| `app/src/main/java/com/pedidosya/kata/App.kt` | Modified | Manual DI container / service graph entry point |
| `app/src/main/java/com/pedidosya/kata/MainActivity.kt` | Modified | Hosts `NavHost`; real `ui/theme` package |
| `.../data/`, `.../domain/`, `.../ui/` | New | Layer packages listed in Approach |
| `app/src/test/` | New | Unit tests (currently only an instrumented placeholder) |
| repo root | New | `git init` before any PR slicing |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Mock JSON captured via WebFetch, not raw HTTP | Med | Verify both URLs with `curl` before freezing DTOs in spec/design |
| Category-scoped discount contradicts the requirement's flat "% aplicado" wording | Med | Show nominal % per confirmed decision; document the rule in spec and a README note |
| dummyjson mock URLs expire or change shape | Low | Repository behind an interface; fake data source in tests keeps suite offline |
| Foundation setup (deps, Room, nav, test infra) crowds out feature work | Med | Sequence foundation as its own work unit before feature TDD |
| No git → no PR slicing or rollback | High | `git init` + baseline commit as the first task |
| Float arithmetic rounding in totals | Med | Single rounding policy at the presentation boundary, asserted in unit tests |

## Rollback Plan

Feature is additive on an empty scaffold. Per work unit: `git revert` the slice. Full rollback: reset to the baseline commit created by `git init`; the only pre-existing files touched are `App.kt`, `MainActivity.kt`, `app/build.gradle.kts`, and `libs.versions.toml`, each restorable to scaffold state. No migrations or user data exist — dropping the Room DB is lossless.

## Dependencies

- Reachable mock endpoints: cart `https://dummyjson.com/c/9518-ea8d-4660-afd8`, coupons `https://dummyjson.com/c/8e56-250c-4cd8-9c5a`.
- Test infrastructure must land before production code (Strict TDD).
- `git init` before any PR-sliced delivery.

## Success Criteria

- [ ] Cart renders cached items instantly and refreshes from the network; airplane mode still shows the cache.
- [ ] Apply validates remotely and shows preview or the correct typed error; no network → explicit service error, never a silent 0%.
- [ ] Discount applies only to matching-category subtotal; `all` covers every item; inactive coupon rejected.
- [ ] Confirm with an empty field issues no coupon request and reaches Summary with 0%.
- [ ] Invalid/errored coupon blocks navigation until corrected or cleared.
- [ ] Summary shows the nominal % and the discounted final total.
- [ ] `CalculateTotals` and `ValidateCoupon` unit-tested test-first, including mixed-category and rounding cases.

> Checkboxes reproduced verbatim (unchecked) from the original proposal observation in Engram — this
> document does not update them. `tasks.md` reports 45/45 tasks complete and a passing 43-test suite, but
> `verify-report.md` found that 3 of the spec **scenarios** underlying these criteria are not yet proven by
> a passing covering test (3 CRITICAL findings, one of them a likely defect in the "empty cart" path).
> Verification's overall verdict is **FAIL** — archive is blocked until those are resolved.

---
Engram observation: #1414 · topic `sdd/shopping-cart/proposal` · project `kata-mobile-android-empty`
