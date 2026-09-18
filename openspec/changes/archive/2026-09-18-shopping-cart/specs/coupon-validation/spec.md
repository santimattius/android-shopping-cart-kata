<!--
Materialized from Engram (project: kata-mobile-android-empty, topic: sdd/shopping-cart/spec,
observation #1415) on 2026-09-18. The Engram artifact concatenates all three domains (cart-list,
coupon-validation, purchase-summary) into one spec document with per-domain headers; this file
extracts only the `coupon-validation` domain section, per OpenSpec's one-spec-per-domain convention.
This is a copy; Engram remains the live source of truth (single combined document there).
-->

# coupon-validation Specification

## Purpose
Remote-only category-scoped coupon validation, per `sdd/shopping-cart/proposal`. New capability: `coupon-validation`.

## ADDED Requirements

### Requirement: Apply triggers remote-only validation
The system MUST validate the typed code exclusively against the remote coupon service on "Aplicar." The system MUST NOT use cached or local-mock coupon data, with or without connectivity.

#### Scenario: Valid, active, category-matching coupon
- GIVEN a code matching an active coupon
- WHEN the user taps Aplicar
- THEN a preview shows the nominal % and the new total discounted on the matching-category subtotal

#### Scenario: Unknown code
- GIVEN the code matches no remote coupon
- WHEN the user taps Aplicar
- THEN an invalid-coupon message renders, no discount previewed

#### Scenario: Inactive coupon
- GIVEN the code matches a coupon with `is_active = false`
- WHEN the user taps Aplicar
- THEN an inactive-coupon message renders, no discount previewed

#### Scenario: Service failure or no network
- GIVEN the remote request fails or connectivity is absent
- WHEN the user taps Aplicar
- THEN a service-error message renders, no discount and no fallback validation

### Requirement: Coupon validation result modeling
The system MUST model the validation outcome as a closed type (e.g. sealed): NotApplied, Valid(coupon, discountedTotal), Invalid, Inactive, ServiceError.

#### Scenario: Exhaustive result handling
- GIVEN the result type is sealed
- WHEN logic branches on it
- THEN every case is handled by a compiler-enforced exhaustive branch

### Requirement: Confirm Purchase gating
The system MUST gate navigation to Summary on the current coupon state without an unnecessary remote call.

#### Scenario: Empty field bypasses validation
- GIVEN the coupon field is empty
- WHEN the user taps Confirmar Compra
- THEN no coupon call is made and navigation proceeds with 0% discount

#### Scenario: Already-applied valid coupon
- GIVEN Aplicar already returned Valid for the typed code
- WHEN the user taps Confirmar Compra
- THEN navigation proceeds using that result, without a new remote call

#### Scenario: Typed but not yet applied
- GIVEN a code is typed and Aplicar was not pressed
- WHEN the user taps Confirmar Compra
- THEN the system validates remotely first, then navigates or blocks per the outcome

#### Scenario: Invalid, inactive, or errored coupon blocks navigation
- GIVEN the coupon state is Invalid, Inactive, or ServiceError
- WHEN the user taps Confirmar Compra
- THEN navigation MUST NOT occur and the error message stays visible

### Requirement: Category-scoped discount calculation
The system MUST apply `discount_percentage` only to the subtotal of items whose `category` matches `applicable_category`, or to all items when `applicable_category` is `all`. Non-matching items MUST pay full price. The final total MUST equal the sum of all category subtotals with the discount applied only to the matching portion.

#### Scenario: Mixed-category cart, category-scoped coupon
- GIVEN technology and grocery items and a coupon scoped to `technology`
- WHEN the coupon is applied
- THEN only the technology subtotal is discounted; grocery stays full price; total is the sum of both

#### Scenario: Coupon scoped to "all"
- GIVEN a mixed-category cart and a coupon with `applicable_category = all`
- WHEN the coupon is applied
- THEN every item's subtotal is discounted by the coupon %

---
> **Verification status** (from `verify-report.md`, Engram obs #1424 — verdict FAIL): this domain has no
> CRITICAL findings, but 2 scenarios are PARTIAL — implemented and indirectly covered, but not proven by a
> dedicated test at the `CartViewModel`/Confirm level:
> - "Service failure or no network" (Apply) — `ServiceError` is well covered at `CouponRepositoryImplTest`
>   and `ValidateCouponTest`, but has no dedicated `CartViewModel`-level Apply test.
> - "Invalid, inactive, or errored coupon blocks navigation" (Confirm) — only `Invalid` is independently
>   exercised; `Inactive`/`ServiceError` share the identical `when`-arm in `onConfirmPurchase()`
>   (`CartViewModel.kt:125-128`) but are not independently exercised at Confirm time.
>
> All other scenarios in this domain (Valid/Unknown-code Apply, exhaustive result modeling, empty-field
> bypass, already-applied reuse, typed-but-not-applied, mixed-category discount, "all"-scoped discount)
> are COMPLIANT.

Engram observation: #1415 (combined spec) · topic `sdd/shopping-cart/spec` · project `kata-mobile-android-empty` · domain extracted: `coupon-validation`
