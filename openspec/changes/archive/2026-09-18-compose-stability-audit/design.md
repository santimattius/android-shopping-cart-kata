# Design: Compose Stability Audit

## Technical Approach

Two edits, evidence-first: (1) wire a property-gated `composeCompiler` reports block and capture a baseline report; (2) narrow `items` in both `Success` states to `kotlinx.collections.immutable.ImmutableList<CartItem>`. Cart converts on the repository-flow side before `combine`, so local `inputs` emissions do not reconvert an unchanged Room list; Summary converts at its state-construction boundary. Re-read the same report after the change.

No delta spec exists (pure non-functional change), so this document defines "done": a compiler-generated `classes.txt` flipping both `Success` classes from `unstable` to `stable`, with the existing suite green and behaviorally unmodified.

Built on `refactor/viewmodel-state-and-concurrency` (change 1), where `CartViewModel` already produces state via `combine(observeCart(), inputs, ::reduce).stateIn(...)`. Change 1 did not touch either UI-state file.

## Architecture Decisions

| # | Decision | Alternatives rejected | Rationale |
|---|---|---|---|
| 1 | `ImmutableList<CartItem>` on both `Success.items` | `@Immutable` on `Success` | Type-enforced. `@Immutable` over a `kotlin.collections.List` is a promise the compiler cannot check: a later `MutableList` silently yields stale UI. Measured blast radius is 6 production call sites — the annotation's only edge (zero churn) does not buy an unenforceable promise. |
| 2 | Convert in the ViewModels, not in `CartRepositoryImpl`; Cart maps before `combine` | Change `observeCart(): Flow<ImmutableList<CartItem>>` | Keeps a Compose concern out of the domain contract. The repository, `CalculateTotals(items: List<CartItem>, …)`, mappers, DAO, and DTOs stay on plain `List`; mapping before `combine` avoids conversion on local `inputs` emissions. |
| 3 | `.toImmutableList()` | `.toPersistentList()` | Narrowest truthful type; `persistentListOf` semantics (structural sharing / `add`) are never used here. |
| 4 | Reports gated on `-PcomposeReports=true` | Always-on `composeCompiler` block | Report generation is a compiler-plugin option and a task input; leaving it on invalidates every incremental Kotlin compile. |
| 5 | `classes.txt` is the binding evidence; `composables.txt` corroborates | Rely on `skippable` in `composables.txt` | **Refines the proposal's criterion 3**: with strong skipping on (default at Kotlin 2.0.21), restartable composables are reported skippable regardless of parameter stability. The load-bearing line is the per-parameter `stable state: Success` and the `classes.txt` verdict, not the `skippable` keyword. |

**Generic stability caveat (Decision 1)**: the Compose compiler treats `kotlinx.collections.immutable` interfaces as stable *parameterized by the element type* — `ImmutableList<T>` is stable only when `T` is. `CartItem` is a `data class` of 6 immutable primitives/`String?` with no collections, so it is inferred stable, and the baseline report must confirm this before the migration is trusted.

## Data Flow

```
Room ──Flow<List<CartItem>>──→ map { it.toImmutableList() } ──→ combine(items, inputs) ──→ reduce
                                      ↑ repository-flow boundary; local inputs do not reconvert items
                                     ▼
                          CartUiState.Success(items: ImmutableList<CartItem>, totals, …)
                                     │        totals = calculateTotals(items, coupon)  ← still takes List
                                     ▼
                       StateFlow conflates equal CartUiState values before Compose observes them

Room ──Flow<List<CartItem>>──→ SummaryViewModel.map { … }  ← ONLY conversion point (summary)
```

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `gradle/libs.versions.toml` | Modify | `kotlinxCollectionsImmutable = "0.3.8"` + `kotlinx-collections-immutable` library entry (confirm the version resolves offline-free at apply time) |
| `app/build.gradle.kts` | Modify | `implementation(libs.kotlinx.collections.immutable)` + gated `composeCompiler` block |
| `ui/cart/CartUiState.kt` | Modify | `items: ImmutableList<CartItem>` |
| `ui/summary/SummaryUiState.kt` | Modify | `items: ImmutableList<CartItem>` |
| `ui/cart/CartViewModel.kt` | Modify | Map repository items with `.toImmutableList()` before `combine`; `reduce` accepts `ImmutableList<CartItem>` |
| `ui/summary/SummaryViewModel.kt` | Modify | One `.toImmutableList()` in the `map { }` at line 50 |
| `test/.../CartViewModelTest.kt` | Modify | ~8 `CartUiState.Success(listOf(...) / emptyList(), …)` fixtures → `persistentListOf(...)` / `persistentListOf()`. Assertions unchanged |
| `test/.../SummaryViewModelTest.kt` | Unchanged | Never constructs `Success`; `assertEquals(items, success.items)` stays green — `ImmutableList` honours the `List` equals contract |
| `CartScreen.kt`, `SummaryScreen.kt` | Unchanged | `items(items = state.items, key = …)` binds the `LazyListScope.items(List<T>)` overload |

## Interfaces / Contracts

```kotlin
// app/build.gradle.kts — top level, after android { }
if (providers.gradleProperty("composeReports").orNull == "true") {
    composeCompiler {
        reportsDestination = layout.buildDirectory.dir("compose_compiler")
        metricsDestination = layout.buildDirectory.dir("compose_compiler")
    }
}
```

`composeCompiler {}` comes from the already-applied `org.jetbrains.kotlin.plugin.compose` 2.0.21 plugin; `DirectoryProperty` `=` assignment is supported by Gradle 8.11.1 (wrapper). Public API delta: each public `Success` constructor parameter `items` intentionally narrows from `List<CartItem>` to `ImmutableList<CartItem>` inside this single application module. Existing read sites still compile because `ImmutableList` is a `List`; constructor callers must now supply `ImmutableList`. No compatibility factory or stability annotation is added.

## Testing / Verification Strategy

| Layer | What | Approach |
|-------|------|----------|
| Compiler evidence | `Success` classes stable | `./gradlew :app:compileReleaseKotlin -PcomposeReports=true --rerun-tasks`, then read `app/build/compose_compiler/app_release-classes.txt` |
| Unit | 17 existing ViewModel behaviors | `./gradlew :app:testDebugUnitTest` — assertions byte-identical, only fixture constructors change |
| Build | Whole-app compilation of the narrowed type | `./gradlew :app:assembleDebug` |

Acceptance, checked against the same command before and after:

- Baseline: `unstable class Success { unstable val items: List<CartItem> … }` for both.
- After: `stable class Success { stable val items: ImmutableList<CartItem> … }` for both, and `stable class CartItem`.
- After, in `app_release-composables.txt`: `stable state: CartUiState.Success` on `CartContent`/`CouponSection` and `stable state: SummaryUiState.Success` on `SummaryContent`.
- Zero stability annotations added anywhere.
- Compiler reports classify stability; they do not measure runtime recomposition. Under Strong Skipping, stable parameters use `equals` and unstable parameters use identity when Compose can skip, but equal `StateFlow` emissions are conflated before Compose observes them.

**Strict-TDD exception for PR17-FIX-1.** This correction adds no behavior, so no behavioral RED test is meaningful and none is manufactured. Historical baseline/post-fix compiler reports remain stability evidence for the original migration, while the focused compiler report, unit regression suite, and build are the PR17-FIX-1 evidence cycle.

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or process-integration boundary.

## Migration / Rollout

No data migration, no schema/DI/navigation change, no feature flag. Two work-unit commits, revertable independently and in any order:

1. `build(compose): add gated compose compiler stability reports` — catalog + `build.gradle.kts` block.
2. `refactor(ui): hold cart items as ImmutableList in UI state` — both state classes, both ViewModels, `CartViewModelTest` fixtures.

**Rollback**: `git revert <sha>`. Reverting (2) restores the prior `List` constructor contract and Cart conversion placement; it does not establish a measured recomposition change. Reverting (1) only removes an opt-in report and leaves the migration intact. Reverting both also requires dropping the `libs.versions.toml` entry if (2) is reverted alone leaves the dependency unused (harmless, but remove it).

## Open Questions

- [ ] None blocking. `kotlinx-collections-immutable` `0.3.8` is the assumed Kotlin-2.0-compatible version; `sdd-apply` must confirm resolution and bump if Gradle fails to resolve it.
