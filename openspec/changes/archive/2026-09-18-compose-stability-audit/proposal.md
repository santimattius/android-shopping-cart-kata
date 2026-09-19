# Proposal: Compose Stability Audit

## Intent

Both `Success` UI-state classes hold raw `kotlin.collections.List`, which the Compose compiler infers unstable. Under strong skipping (default at Kotlin 2.0.21) unstable params compare by **identity**, not `equals`. Both ViewModels rebuild `Success` on every Room emission, so a refresh writing structurally-identical items fails identity and forces `CartContent`, `CouponSection`, `SummaryContent` to recompose. No compiler report is wired, so this goal currently rests on a manual code read.

## Scope

### In Scope
- Migrate `items` in `CartUiState.Success` / `SummaryUiState.Success` to `kotlinx.collections.immutable.ImmutableList`, converting once at the ViewModel state-construction boundary.
- Add `kotlinx-collections-immutable` to the version catalog and `app` dependencies.
- Wire `composeCompiler { reportsDestination / metricsDestination }`, gated on a `composeReports` Gradle property.
- Capture before/after `classes.txt` + `composables.txt` as the change's evidence.
- Update affected unit tests to the new type.

### Out of Scope
- `CartItem`, `Coupon`, `CartTotals`, `CouponValidationResult` — verified collection-free, all-`val`, already inferred stable. No annotation added.
- Navigation, new Compose UI tests (change 3), further state-production changes (change 1).
- `stabilityConfigurationFiles`, baseline profiles, runtime tracing.

## Capabilities

### New Capabilities
None — no user-observable behavior changes.

### Modified Capabilities
None — `cart-list`, `coupon-validation`, `purchase-summary` requirements are unaffected.

## Approach

Evidence first: wire the report, capture a baseline, apply the fix, re-read the same report.

| # | Decision | Rejected | Rationale |
|---|---|---|---|
| 1 | `ImmutableList` on the UI-state boundary | `@Immutable` annotation | Type-enforced vs. an unenforced promise a contributor can silently break into stale UI |
| 2 | Convert in the ViewModels; `CartRepository` keeps `Flow<List<CartItem>>` | Convert in `CartRepositoryImpl` | Keeps a UI concern out of the domain contract; `CalculateTotals` is unchanged (`ImmutableList` is-a `List`) |
| 3 | Reports in-scope, property-gated | Defer to a follow-up | A stability audit without compiler evidence is one more manual read; the gate keeps normal builds untouched |
| 4 | Verify strong skipping from the report | Assert it from version numbers | On by default at Kotlin 2.0.21 with no opt-out in this build, but the report is the proof |

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `ui/cart/CartUiState.kt`, `ui/summary/SummaryUiState.kt` | Modified | `items` type |
| `ui/cart/CartViewModel.kt`, `ui/summary/SummaryViewModel.kt` | Modified | One conversion at each `Success` construction |
| `app/build.gradle.kts`, `gradle/libs.versions.toml` | Modified | Dependency + `composeCompiler` block |
| `test/.../CartViewModelTest.kt`, `SummaryViewModelTest.kt` | Modified | Fixture types only; no assertion changes |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Local checkout predates change 1 (`CartViewModel` still hand-rolls `MutableStateFlow`; no change-1 archive folder) | High | Pull merged `main` before apply; `Success` shapes are frozen either way |
| Report contradicts the premise | Low-Med | Baseline is captured first; if unstable classes differ, stop and re-propose |
| `toImmutableList()` copies per emission | Low | Kata-sized cart; cheaper than the subtree recomposition removed |

## Rollback Plan

Two independently revertable commits: (1) build wiring, (2) `ImmutableList` migration + test updates. `git revert <sha>`. Nothing is persisted and no public navigation or ViewModel contract changes, so either reverts alone: reverting (2) restores today's identity comparison; reverting (1) only removes the opt-in report.

## Dependencies

- `viewmodel-state-and-concurrency` present in the working checkout.
- New: `org.jetbrains.kotlinx:kotlinx-collections-immutable`.
- Compose Compiler Gradle plugin — already applied.

## Success Criteria

- [ ] Baseline `classes.txt` lists both `Success` classes unstable.
- [ ] Post-fix `classes.txt` lists both stable.
- [ ] Post-fix `composables.txt` marks `CartContent`, `CouponSection`, `SummaryContent` skippable.
- [ ] `./gradlew :app:testDebugUnitTest` and `:app:assembleDebug` pass, no behavior assertion changed.
- [ ] No stability annotation added to a type that does not truthfully hold the promise.
