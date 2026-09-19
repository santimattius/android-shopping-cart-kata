# Design: Compose Stability Audit

## Technical Approach

Two edits, evidence-first: (1) wire a property-gated `composeCompiler` reports block and capture a **baseline** report; (2) narrow `items` in both `Success` states to `kotlinx.collections.immutable.ImmutableList<CartItem>`, converting exactly once at the ViewModel state-construction boundary, then re-read the same report.

No delta spec exists (pure non-functional change), so this document defines "done": a compiler-generated `classes.txt` flipping both `Success` classes from `unstable` to `stable`, with the existing suite green and behaviorally unmodified.

Built on `refactor/viewmodel-state-and-concurrency` (change 1), where `CartViewModel` already produces state via `combine(observeCart(), inputs, ::reduce).stateIn(...)`. Change 1 did not touch either UI-state file.

## Architecture Decisions

| # | Decision | Alternatives rejected | Rationale |
|---|---|---|---|
| 1 | `ImmutableList<CartItem>` on both `Success.items` | `@Immutable` on `Success` | Type-enforced. `@Immutable` over a `kotlin.collections.List` is a promise the compiler cannot check: a later `MutableList` silently yields stale UI. Measured blast radius is 6 production call sites — the annotation's only edge (zero churn) does not buy an unenforceable promise. |
| 2 | Convert in the ViewModels, not in `CartRepositoryImpl` | Change `observeCart(): Flow<ImmutableList<CartItem>>` | Keeps a Compose concern out of the domain contract. `CalculateTotals(items: List<CartItem>, …)`, mappers, DAO and DTOs stay on plain `List` and compile unchanged, because `ImmutableList` **is-a** `List`. |
| 3 | `.toImmutableList()` | `.toPersistentList()` | Narrowest truthful type; `persistentListOf` semantics (structural sharing / `add`) are never used here. |
| 4 | Reports gated on `-PcomposeReports=true` | Always-on `composeCompiler` block | Report generation is a compiler-plugin option and a task input; leaving it on invalidates every incremental Kotlin compile. |
| 5 | `classes.txt` is the binding evidence; `composables.txt` corroborates | Rely on `skippable` in `composables.txt` | **Refines the proposal's criterion 3**: with strong skipping on (default at Kotlin 2.0.21), restartable composables are reported skippable regardless of parameter stability. The load-bearing line is the per-parameter `stable state: Success` and the `classes.txt` verdict, not the `skippable` keyword. |

**Generic stability caveat (Decision 1)**: the Compose compiler treats `kotlinx.collections.immutable` interfaces as stable *parameterized by the element type* — `ImmutableList<T>` is stable only when `T` is. `CartItem` is a `data class` of 6 immutable primitives/`String?` with no collections, so it is inferred stable, and the baseline report must confirm this before the migration is trusted.

## Data Flow

```
Room ──Flow<List<CartItem>>──→ CartViewModel.reduce(items, inputs)
                                     │  items.toImmutableList()   ← ONLY conversion point (cart)
                                     ▼
                          CartUiState.Success(items: ImmutableList<CartItem>, totals, …)
                                     │        totals = calculateTotals(items, coupon)  ← still takes List
                                     ▼
                     collectAsStateWithLifecycle → CartContent / CouponSection
                          compares by equals(), not identity → skips on unchanged refresh

Room ──Flow<List<CartItem>>──→ SummaryViewModel.map { … }  ← ONLY conversion point (summary)
```

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `gradle/libs.versions.toml` | Modify | `kotlinxCollectionsImmutable = "0.3.8"` + `kotlinx-collections-immutable` library entry (confirm the version resolves offline-free at apply time) |
| `app/build.gradle.kts` | Modify | `implementation(libs.kotlinx.collections.immutable)` + gated `composeCompiler` block |
| `ui/cart/CartUiState.kt` | Modify | `items: ImmutableList<CartItem>` |
| `ui/summary/SummaryUiState.kt` | Modify | `items: ImmutableList<CartItem>` |
| `ui/cart/CartViewModel.kt` | Modify | One `.toImmutableList()` inside the `reduce(...)` that builds `Success` |
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

`composeCompiler {}` comes from the already-applied `org.jetbrains.kotlin.plugin.compose` 2.0.21 plugin; `DirectoryProperty` `=` assignment is supported by Gradle 8.11.1 (wrapper). Public API delta: `Success.items` narrows `List` → `ImmutableList`; nothing widens, so every existing read site still compiles.

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

**Note for `sdd-tasks` — what RED means here.** Strict TDD is on project-wide, but this change adds no behavior, so there is no behavioral test that can fail first and pass after. Do **not** invent one. The RED artifact is the **baseline compiler report** captured before the type change and committed/quoted as task evidence: it is the failing observation, and the post-fix report is the GREEN. The existing suite is a regression gate, not the proof. The only legitimate test edits are fixture constructor types.

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or process-integration boundary.

## Migration / Rollout

No data migration, no schema/DI/navigation change, no feature flag. Two work-unit commits, revertable independently and in any order:

1. `build(compose): add gated compose compiler stability reports` — catalog + `build.gradle.kts` block.
2. `refactor(ui): hold cart items as ImmutableList in UI state` — both state classes, both ViewModels, `CartViewModelTest` fixtures.

**Rollback**: `git revert <sha>`. Reverting (2) restores identity comparison and today's recomposition behavior — nothing is persisted and no public ViewModel or navigation contract changes, so it cannot leave partial state. Reverting (1) only removes an opt-in report and leaves the migration intact. Reverting both also requires dropping the `libs.versions.toml` entry if (2) is reverted alone leaves the dependency unused (harmless, but remove it).

## Open Questions

- [ ] None blocking. `kotlinx-collections-immutable` `0.3.8` is the assumed Kotlin-2.0-compatible version; `sdd-apply` must confirm resolution and bump if Gradle fails to resolve it.
