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

The system MUST model cart-screen state as a closed type (e.g. sealed): Loading, Error(reason), Success(items, totals, couponState).

#### Scenario: Exhaustive state handling

- GIVEN the state type is sealed
- WHEN the UI renders any state
- THEN Loading/Error/Success are handled by a compiler-enforced exhaustive branch

---
> **Verification status** (from `verify-report.md`, Engram obs #1424 — verdict FAIL): 3 of this domain's
> scenarios are CRITICAL findings — UNTESTED, not proven by a passing covering test:
>
> - "First load, no cache, network available" — UNTESTED
> - "Cache present, background refresh fails" — UNTESTED
> - "Empty cart" — UNTESTED, and code inspection indicates a **likely defect**: `CartViewModel`'s `init`
>   block only promotes `Loading -> Success` when `items.isNotEmpty() || current is CartUiState.Success`
>   (`CartViewModel.kt:52`); a genuinely empty first-fetch response may never re-emit through Room and the
>   screen could be stuck on `Loading` forever instead of showing the empty-state message.
>
> "Cache present, refresh succeeds silently" and "Retry succeeds" and the sealed-type scenario are
> COMPLIANT. Pull-to-refresh's "Manual refresh gesture" scenario is COMPLIANT.

Engram observation: #1415 (combined spec) · topic `sdd/shopping-cart/spec` · project `kata-mobile-android-empty` · domain extracted: `cart-list`
