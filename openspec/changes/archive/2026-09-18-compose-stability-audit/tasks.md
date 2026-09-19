# Tasks: Compose Stability Audit

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~120-180 |
| 400-line budget risk | Low |
| Chained PRs recommended | No |
| Suggested split | Single PR (2 commits, see Migration/Rollout) |
| Delivery strategy | auto-chain |
| Chain strategy | pending |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Low

Rationale: 6 files touched (catalog, build script, 2 state classes, 2 ViewModels) plus
~8 fixture edits in one test file. No new UI, no new tests beyond fixture-type edits, no
generated goldens. Well under the 400-line budget as a single PR.

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | Compiler-report wiring + baseline evidence | PR 1 (single PR, commit 1) | `./gradlew :app:compileReleaseKotlin -PcomposeReports=true --rerun-tasks` | N/A — build-tooling only, no runtime scenario | Revert `app/build.gradle.kts` block; removes opt-in report only |
| 2 | `ImmutableList` migration + fixtures + post-fix evidence | PR 1 (single PR, commit 2) | `./gradlew :app:testDebugUnitTest` | N/A — non-functional refactor, no user-observable scenario to exercise | Revert commit 2; restores the prior `List` source contract without claiming a measured recomposition change |

## Phase 0: Prerequisite

- [x] 0.1 Branch from `refactor/viewmodel-state-and-concurrency` (PR #16, unmerged), NOT from `main`. Verify `git log` shows the change-1 `combine(...).stateIn(...)` refactor in `CartViewModel.kt` before proceeding.

## Phase 1: Compiler Report Wiring (evidence infrastructure)

- [x] 1.1 Add gated `composeCompiler { reportsDestination / metricsDestination }` block to `app/build.gradle.kts`, top level after `android { }`, wrapped in `if (providers.gradleProperty("composeReports").orNull == "true")` per design's Interfaces/Contracts snippet.
- [x] 1.2 Commit as `build(compose): add gated compose compiler stability reports` (independent, revertable commit per design Migration/Rollout).

## Phase 2: Baseline Evidence (RED — pre-fix compiler report)

- [x] 2.1 Run `./gradlew :app:compileReleaseKotlin -PcomposeReports=true --rerun-tasks` and read `app/build/compose_compiler/app_release-classes.txt`.
- [x] 2.2 Gate check: confirm `CartItem` reports `stable class CartItem`. If NOT stable, STOP — do not proceed to Phase 3; the `ImmutableList<CartItem>` migration cannot deliver a stable `Success` class if the element type itself is unstable, and the design's premise needs re-proposing.
- [x] 2.3 Confirm both `CartUiState.Success` and `SummaryUiState.Success` report `unstable class Success { unstable val items: List<CartItem> … }`. Record this baseline text as the RED evidence (quote in the PR description or apply-progress notes) — this is the failing observation the design substitutes for a behavioral RED test.

## Phase 3: Dependency Addition

- [x] 3.1 Add `kotlinxCollectionsImmutable = "0.3.8"` to `[versions]` and a `kotlinx-collections-immutable` entry to `[libraries]` in `gradle/libs.versions.toml` (group `org.jetbrains.kotlinx`, name `kotlinx-collections-immutable`).
- [x] 3.2 Add `implementation(libs.kotlinx.collections.immutable)` to `app/build.gradle.kts` dependencies block.
- [x] 3.3 Run a sync/resolve (e.g. `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`) to confirm `0.3.8` resolves against Kotlin 2.0.21. If resolution fails, bump to the nearest resolvable version and note the change from design's assumed value. RESULT: `0.3.8` resolved cleanly (`org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.8 -> kotlinx-collections-immutable-jvm:0.3.8`) — no bump needed.

## Phase 4: ImmutableList Migration (GREEN — the fix)

- [x] 4.1 Change `items` in `CartUiState.Success` (`app/src/main/java/com/pedidosya/kata/ui/cart/CartUiState.kt`) from `List<CartItem>` to `kotlinx.collections.immutable.ImmutableList<CartItem>`.
- [x] 4.2 Change `items` in `SummaryUiState.Success` (`app/src/main/java/com/pedidosya/kata/ui/summary/SummaryUiState.kt`) from `List<CartItem>` to `ImmutableList<CartItem>`.
- [x] 4.3 In `CartViewModel.kt`, map `observeCart()` items with exactly one `.toImmutableList()` call before `combine`, and make `reduce(...)` accept `ImmutableList<CartItem>`. Do not touch `CartRepository`, `CalculateTotals`, mappers, or the DAO.
- [x] 4.4 In `SummaryViewModel.kt`, add exactly one `.toImmutableList()` call in the `map { }` block that builds `Success` (around line 50 per design). Do not touch upstream sources.
- [x] 4.5 Update `app/src/test/java/com/pedidosya/kata/ui/cart/CartViewModelTest.kt`: convert the 9 positional `CartUiState.Success(...)` fixtures (variable- and literal-backed) to `persistentListOf(...)` / `persistentListOf()` (import `kotlinx.collections.immutable.persistentListOf`). Do not change any assertion.
- [x] 4.6 Confirm `app/src/test/java/com/pedidosya/kata/ui/summary/SummaryViewModelTest.kt` needs zero edits (it never constructs `Success` directly; `assertEquals(items, success.items)` holds under `List` structural equality). Read the file to verify this before skipping it. CONFIRMED — file read, zero edits made.
- [x] 4.7 Commit as `refactor(ui): hold cart items as ImmutableList in UI state`. (left uncommitted per orchestrator instruction — working tree ready for review)

## Phase 5: Post-Fix Evidence (GREEN — post-fix compiler report)

- [x] 5.1 Re-run `./gradlew :app:compileReleaseKotlin -PcomposeReports=true --rerun-tasks` and re-read `app/build/compose_compiler/app_release-classes.txt`.
- [x] 5.2 Confirm both `CartUiState.Success` and `SummaryUiState.Success` now report `stable class Success { stable val items: ImmutableList<CartItem> … }`. This is the GREEN evidence — the load-bearing check, per design Decision 5. Do not accept `skippable` in `composables.txt` alone as proof (strong skipping makes that keyword unreliable regardless of parameter stability). CONFIRMED both stable.
- [x] 5.3 Corroborate (non-binding) in `app_release-composables.txt`: `stable state: CartUiState.Success` on `CartContent`/`CouponSection`, `stable state: SummaryUiState.Success` on `SummaryContent`. PARTIAL: `SummaryContent` shows `stable state: Success`; `CartContent`/`CouponSection` show `state: Success` with no explicit stability prefix (likely a report-formatting quirk from two nested types sharing the simple name `Success`, or the composer's own group-key printing — classes.txt remains the binding evidence per design and is unambiguous for both).
- [x] 5.4 Confirm zero stability annotations (`@Immutable`, `@Stable`) were added anywhere in the diff. CONFIRMED — `git diff` grep for `@Immutable`/`@Stable` returns none.

## Phase 6: Regression Verification

- [x] 6.1 Run `./gradlew :app:testDebugUnitTest` — all existing ViewModel tests (17 behaviors) must pass unchanged; only fixture constructors changed, no assertion changed. RESULT: BUILD SUCCESSFUL, 17/17 CartViewModelTest + 3/3 SummaryViewModelTest pass, 0 failures/errors.
- [x] 6.2 Run `./gradlew :app:assembleDebug` — full app compiles with the narrowed `Success.items` type; confirm `CartScreen.kt` / `SummaryScreen.kt` require no edits (design: `items(items = state.items, ...)` binds the `List<T>` overload unchanged). RESULT: BUILD SUCCESSFUL; `git diff` on both files is empty.

## Notes for sdd-apply

- **No artificial behavioral RED test.** Strict TDD is enabled project-wide, but PR17-FIX-1 adds
  no behavior. Do not write a synthetic test asserting recomposition counts or `Success` stability.
  The original baseline/post-fix reports are historical stability evidence, not a manufactured RED
  for this correction; the focused report, existing suite, and build are the regression evidence.
- **Branch base is not `main`.** Phase 0 is a hard prerequisite: this change stacks on the
  unmerged `refactor/viewmodel-state-and-concurrency` (PR #16), where `CartViewModel` already
  produces state via `combine(...).stateIn(...)`. Starting from `main` will apply the migration
  against a stale `CartViewModel` shape.
- **Phase 2.2 is an early gate, not an afterthought.** If baseline evidence shows `CartItem`
  itself is unstable, stop before Phase 3 — `ImmutableList<CartItem>` cannot be stable if its
  element type isn't, and the whole plan needs re-proposing.
- **Post-review correction.** Stability classification is not a measured recomposition result.
  With Strong Skipping, stable parameters use `equals` and unstable parameters use identity when
  Compose evaluates a received update; equal `StateFlow` emissions are conflated before Compose
  observes them. The public `Success` constructor narrowing is intentional and source-level within
  this application module; no compatibility factories or stability annotations are added.
