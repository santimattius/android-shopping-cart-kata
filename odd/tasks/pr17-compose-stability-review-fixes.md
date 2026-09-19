# PR #17 Compose Stability Review Fixes

## Objective

Correct the evidence and implementation issues found while auditing PR #17, then publish an educational explanation backed by the exact Compose/Kotlin versions and fresh compiler/build evidence.

## Problem

The `ImmutableList` migration is compiler-valid, but archived SDD documentation overstates that structurally equal Room emissions necessarily force recomposition. `CartViewModel` also converts the same Room list again on local input changes because `toImmutableList()` is inside the `combine` reducer. The UI-state constructor narrowing is intentional for this application module but is not documented as a source-level contract change.

## Why

Reviewers should be able to distinguish compiler-proven stability from measured runtime performance. The implementation should also place immutable conversion at the data boundary it actually follows.

## Scope

- Move Cart's `List<CartItem>` → `ImmutableList<CartItem>` conversion to the repository-flow side of `combine`.
- Keep domain/repository contracts unchanged.
- Correct inaccurate recomposition claims in archived SDD evidence.
- Document the intentional constructor source narrowing.
- Update PR #17 with version-specific evidence and the learning argument.

## Non-goals

- No new product behavior.
- No `@Stable` or `@Immutable` annotations.
- No runtime recomposition benchmark claim.
- No changes to Summary conversion unless evidence requires them.
- No changes to unrelated `.atl`, `.gitignore`, navigation, or later testing-chain work.

## Constraints

- Worktree: `/Users/santiago/Documents/Development/Kata/kata-mobile-android-empty-pr17-audit`
- Branch: `refactor/compose-stability-audit`
- Kotlin/Compose compiler: 2.0.21
- Compose BOM: 2025.05.00; resolved runtime/foundation/UI: 1.8.1
- Strict TDD source: `openspec/config.yaml`
- Test runner: `./gradlew :app:testDebugUnitTest`
- Delivery strategy: update existing PR #17 as one focused correction work unit.
- Forecast: under 200 authored changed lines.

## Tasks

- [ ] **PR17-FIX-1 — Correct implementation and evidence**
  - Route: delegated writer (multi-file write trigger).
  - Move Cart immutable conversion before `combine`; preserve behavior and state equality.
  - Correct proposal/design/apply/verify/archive claims that structurally equal Room emissions necessarily force recomposition.
  - Document `List` → `ImmutableList` as intentional source-level constructor narrowing inside this application module.
  - Run compiler reports, focused/full unit tests, and assemble validation.
  - Commit the coherent code + evidence update with a Conventional Commit message.
  - Update PR #17 body and add an educational evidence comment.

## Acceptance Criteria

- Cart conversion occurs once per repository list emission, not on every local `inputs` emission.
- `CartUiState.Success` and `SummaryUiState.Success` remain compiler-inferred stable.
- Strong Skipping remains enabled.
- Documentation distinguishes compiler classification from runtime measurement.
- Documentation acknowledges the constructor source-level narrowing.
- Unit tests and `assembleDebug` pass.
- PR #17 contains exact versions, commands, results, and the reasoning behind the correction.
- Unrelated dirty paths remain untouched.

## Progress

- Audit complete at PR head `391fdcf`.
- Official android-cli documentation consulted for strong skipping and immutable collections.
- Baseline validation: 49/49 tests passed; `assembleDebug` passed; compiler reports confirm stable states.

## Verification Evidence

- Strict-TDD exception: no RED test was added because this is a non-behavioral optimization/evidence correction; existing assertions remain unchanged.
- `./gradlew :app:compileReleaseKotlin -PcomposeReports=true --rerun-tasks` — BUILD SUCCESSFUL. `app_release-module.json` reports `StrongSkipping: true`; `app_release-classes.txt` reports `CartItem` and both `Success` classes stable with `ImmutableList<CartItem>`.
- `./gradlew :app:testDebugUnitTest --rerun-tasks` — BUILD SUCCESSFUL.
- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL.
- Cart source review confirms repository items map to `ImmutableList` before `combine`, and `reduce` accepts `ImmutableList<CartItem>`; repository/domain contracts remain `List<CartItem>`.

## Next Step

Parent: review the bounded diff, then make the work-unit commit and decide whether to update PR #17. The main checkbox remains unchecked because commit, push, and PR publication are parent-owned.
