# Apply Progress: Compose Stability Audit

**Change**: compose-stability-audit
**Mode**: Standard (no artificial behavioral test; RED/GREEN evidence is a compiler stability report per design)
**Batch**: 1 (single batch, all 22 tasks complete)
**Branch**: `refactor/compose-stability-audit`, based on `refactor/viewmodel-state-and-concurrency` (PR #16, unmerged)

## Status

22/22 tasks complete. Ready for verify.

## Work Unit Evidence

| Evidence | Value |
|---|---|
| Focused test command and exact result | `./gradlew :app:testDebugUnitTest` — BUILD SUCCESSFUL; 17/17 `CartViewModelTest` + 3/3 `SummaryViewModelTest` pass, 0 failures/errors |
| Runtime harness command/scenario and exact result | N/A — non-functional refactor, no user-observable scenario (per design); compiler-report evidence below substitutes |
| Rollback boundary | Two independently revertable, uncommitted-but-separable hunks: (1) `app/build.gradle.kts` compose-reports block only, (2) the `ImmutableList` migration (state classes, ViewModels, `libs.versions.toml`, `CartViewModelTest.kt`) |

## Non-Functional RED/GREEN Evidence (Compose Compiler Stability Report)

**BASELINE** (`app/build/compose_compiler/app_release-classes.txt`, before migration):
```
stable class CartItem { ... }                              # gate passed
unstable class Success {                                   # CartUiState.Success
  unstable val items: List<CartItem>
  ...
}
unstable class Success {                                   # SummaryUiState.Success
  unstable val items: List<CartItem>
  ...
}
```

**POST-FIX** (same file, after migration):
```
stable class Success {                                     # CartUiState.Success
  stable val items: ImmutableList<CartItem>
  ...
}
stable class Success {                                     # SummaryUiState.Success
  stable val items: ImmutableList<CartItem>
  ...
}
```

Corroboration in `app_release-composables.txt`: `SummaryContent` shows `stable state: Success`. `CartContent`/`CouponSection` show `state: Success` with no explicit stability prefix — a report-formatting nuance (both nested types share the simple name `Success`); per design Decision 5, `classes.txt` is the binding evidence and is unambiguous (both `Success` classes flip to `stable`). Non-binding, not a blocker.

Zero `@Immutable`/`@Stable` annotations added anywhere in the diff (confirmed via `git diff` grep).

## TDD / Evidence Notes

Strict TDD is active project-wide, but per design and tasks Notes, this change adds no behavior — no synthetic behavioral RED test was written. The RED→GREEN cycle is the baseline-vs-post-fix compiler report above; Phase 6's existing 20-test suite is the regression gate, not the proof of the change.

## Completed Tasks

- [x] 0.1 Branched `refactor/compose-stability-audit` from `refactor/viewmodel-state-and-concurrency` (verified change-1 `combine(...).stateIn(...)` present via `git log`)
- [x] 1.1 Gated `composeCompiler { reportsDestination / metricsDestination }` block in `app/build.gradle.kts`
- [x] 1.2 Phase 1 commit boundary noted (left uncommitted per orchestrator instruction)
- [x] 2.1 Ran baseline `:app:compileReleaseKotlin -PcomposeReports=true --rerun-tasks`
- [x] 2.2 Gate check: `CartItem` confirmed `stable`
- [x] 2.3 Baseline confirmed both `Success` classes `unstable`
- [x] 3.1 Added `kotlinxCollectionsImmutable = "0.3.8"` + library entry to `gradle/libs.versions.toml`
- [x] 3.2 Added `implementation(libs.kotlinx.collections.immutable)` to `app/build.gradle.kts`
- [x] 3.3 Resolved `0.3.8` cleanly against Kotlin 2.0.21 via `:app:dependencies --configuration releaseRuntimeClasspath` — no version bump needed
- [x] 4.1 `CartUiState.Success.items` → `ImmutableList<CartItem>`
- [x] 4.2 `SummaryUiState.Success.items` → `ImmutableList<CartItem>`
- [x] 4.3 One `.toImmutableList()` in `CartViewModel.reduce(...)`
- [x] 4.4 One `.toImmutableList()` in `SummaryViewModel`'s `map { }`
- [x] 4.5 `CartViewModelTest.kt`: 9 positional `Success(...)` fixtures converted to `persistentListOf(...)`/`persistentListOf()`; zero assertions changed
- [x] 4.6 Confirmed `SummaryViewModelTest.kt` needs zero edits (file read; verified)
- [x] 4.7 Phase 4 commit boundary noted (left uncommitted per orchestrator instruction)
- [x] 5.1 Ran post-fix `:app:compileReleaseKotlin -PcomposeReports=true --rerun-tasks`
- [x] 5.2 Confirmed both `Success` classes now `stable` with `stable val items: ImmutableList<CartItem>`
- [x] 5.3 Composables corroboration checked (partial — see note above, non-binding)
- [x] 5.4 Confirmed zero stability annotations added
- [x] 6.1 `:app:testDebugUnitTest` — BUILD SUCCESSFUL, 0 failures
- [x] 6.2 `:app:assembleDebug` — BUILD SUCCESSFUL; `CartScreen.kt`/`SummaryScreen.kt` untouched

## Files Changed

| File | Action | What Was Done |
|------|--------|---------------|
| `app/build.gradle.kts` | Modified | Gated `composeCompiler` block; added `kotlinx-collections-immutable` dependency |
| `gradle/libs.versions.toml` | Modified | Added `kotlinxCollectionsImmutable = "0.3.8"` version + library entry |
| `app/src/main/java/com/pedidosya/kata/ui/cart/CartUiState.kt` | Modified | `Success.items`: `List<CartItem>` → `ImmutableList<CartItem>` |
| `app/src/main/java/com/pedidosya/kata/ui/summary/SummaryUiState.kt` | Modified | `Success.items`: `List<CartItem>` → `ImmutableList<CartItem>` |
| `app/src/main/java/com/pedidosya/kata/ui/cart/CartViewModel.kt` | Modified | One `.toImmutableList()` at `Success(...)` construction |
| `app/src/main/java/com/pedidosya/kata/ui/summary/SummaryViewModel.kt` | Modified | One `.toImmutableList()` at `Success(...)` construction |
| `app/src/test/java/com/pedidosya/kata/ui/cart/CartViewModelTest.kt` | Modified | 9 fixture sites converted to `persistentListOf`; no assertion changes |
| `app/src/test/java/com/pedidosya/kata/ui/summary/SummaryViewModelTest.kt` | Unchanged | Confirmed zero edits needed |
| `app/src/main/java/com/pedidosya/kata/ui/cart/CartScreen.kt`, `.../summary/SummaryScreen.kt` | Unchanged | Confirmed no edits needed |

## Deviations from Design

None — implementation matches design. Only clarification: task 4.5 described "~8" fixture sites; the actual count was 9 (`CartUiState.Success(...)` constructor call sites), consistent with the design's estimate.

## Issues Found

None.

## Remaining Tasks

None — all 22 tasks complete.

## Workload / PR Boundary

- Mode: single PR (per Review Workload Forecast: Low risk, no chaining needed)
- Current work unit: both suggested units (1: compiler wiring + baseline; 2: ImmutableList migration + fixtures + post-fix evidence) — both completed in this single batch, left as two logically separable, uncommitted diffs for the orchestrator to commit/PR
- Boundary: starts at branch creation from `refactor/viewmodel-state-and-concurrency`, ends at Phase 6 regression pass
- Estimated review budget impact: within the ~120-180 line forecast; actual diff is ~109 lines changed across 7 files (excluding pre-existing unrelated working-tree changes to `MainActivity.kt`/`KataNavHost.kt`/`.atl/*`/`.gitignore` that predate this change and were not touched by this apply)
