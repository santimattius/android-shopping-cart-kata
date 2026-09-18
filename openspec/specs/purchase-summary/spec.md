<!--
Materialized from Engram (project: kata-mobile-android-empty, topic: sdd/shopping-cart/spec,
observation #1415) on 2026-09-18. The Engram artifact concatenates all three domains (cart-list,
coupon-validation, purchase-summary) into one spec document with per-domain headers; this file
extracts only the `purchase-summary` domain section, per OpenSpec's one-spec-per-domain convention.
This is a copy; Engram remains the live source of truth (single combined document there).
-->

# purchase-summary Specification

## Purpose

Navigation contract and presentation of final total and nominal discount, per `sdd/shopping-cart/proposal`. New capability: `purchase-summary`.

## ADDED Requirements

### Requirement: Nominal percentage and final total display

The system MUST show, on Summary, the item recap, the coupon's nominal discount percentage (never an effective/blended percentage), and the final total already reflecting the category-scoped discount.

#### Scenario: Summary with applied coupon

- GIVEN purchase confirmed with a Valid coupon
- WHEN Summary renders
- THEN it shows the nominal % (e.g. "15%") and the discounted final total

#### Scenario: Summary without a coupon

- GIVEN purchase confirmed with an empty coupon field
- WHEN Summary renders
- THEN it shows no discount (or 0%) and the full-price final total

---
> **Verification status** (from `verify-report.md`, Engram obs #1424 — verdict FAIL): both scenarios in
> this domain are COMPLIANT — "Summary with applied coupon" is covered by
> `SummaryViewModelTest > with a valid applied coupon it shows the discounted total and the nominal
> percentage`, and "Summary without a coupon" is covered by `SummaryViewModelTest > with no coupon code
> it shows the full total and zero percentage`. This domain contributed none of the change's 3 CRITICAL
> findings; the overall change verdict is still FAIL due to CRITICAL gaps in `cart-list`.

Engram observation: #1415 (combined spec) · topic `sdd/shopping-cart/spec` · project `kata-mobile-android-empty` · domain extracted: `purchase-summary`
