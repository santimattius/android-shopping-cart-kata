# Verify Report: viewmodel-state-and-concurrency

**Change**: viewmodel-state-and-concurrency
**Mode**: Full artifact set (proposal, specs, design, tasks, apply-progress)
**Verdict**: **PASS**

## Completeness

| Artifact | Status |
|---|---|
| Proposal | Present, read |
| Spec deltas (cart-list, coupon-validation) | Present, read; 2 requirements / 8 scenarios total |
| Design | Present, read |
| Tasks | 18/18 checked in `tasks.md` |
| Apply progress | 18/18 tasks reported complete, consistent with code state |

Task completion cross-checked against code, not merely trusted:
- Phase 2 (`LoadPhase`/`CartInputs`/`combine().stateIn()`): present in `CartViewModel.kt` lines 49-96, 194-202.
- Phase 3 (re-sited `.value` reads): confirmed — the two design-named tests (`confirming with a typed but never-applied code…`, `confirming with an invalid or errored coupon blocks navigation`) now attach a `state.test {}` / collector before asserting; no bare uncollected `.value` reads remain in the test file.
- Phase 5 (`Channel(BUFFERED)` + `receiveAsFlow()` + `trySend`): present at lines 55, 64, 173; no `viewModelScope.launch` wrapper around emission.
- Phase 6.3 grep check (`_state`, handler `.value`, `successFor`, `latestItems`): re-run independently — zero matches in `CartViewModel.kt`.

## Build / Test Evidence (re-run independently, not reused from apply)

- `./gradlew :app:testDebugUnitTest --rerun-tasks` → **BUILD SUCCESSFUL**. 48/48 tests passed, 0 failures/errors/skipped across all 9 suites (verified via `TEST-*.xml` `tests=`/`failures=`/`errors=` attributes, not console text alone). `CartViewModelTest`: 16/16. `SummaryViewModelTest`: 3/3 (reference pattern, unchanged).
- `./gradlew :app:assembleDebug` → **BUILD SUCCESSFUL**. Confirms the narrowed `events: Flow<CartEvent>` type compiles cleanly at `CartScreen.kt`'s untouched collection site.

## Spec Compliance Matrix

### Domain: cart-list — Requirement "Cart UI state modeling"

| Scenario | Status | Covering test |
|---|---|---|
| Exhaustive state handling | PASS | `CartScreen.kt:57-64` — `when` over sealed `CartUiState` with no `else`, all 3 cases (`Loading`/`Error`/`Success`) explicit; compiler-enforced. |
| Single-reduction state derivation | PASS | `CartViewModel.kt:51-53` — exactly one `combine(repository.observeCart(), inputs, ::reduce).stateIn(...)`; no other assignment site; all handlers write only `inputs.update{}`. |
| Empty cart resolves deterministically on first load (NEW) | PASS | `first load without cache and a successful empty refresh transitions Loading to Success` (line 66) — uses `CompletableDeferred` to control `refresh()` completion independent of the `observeCart()` collector; asserts terminal `Success(emptyList())` regardless of interleaving. Also `a successful retry with an empty cart leaves Error for an empty Success` (line 180) covers the retry-from-Error path named in the design's root-cause analysis. |

### Domain: coupon-validation — Requirement "Confirm Purchase gating"

| Scenario | Status | Covering test |
|---|---|---|
| Empty field bypasses validation | PASS | `confirming with an empty coupon field does not call the coupon service and emits 0 percent` (line 272) |
| Already-applied valid coupon | PASS | `confirming with an already-applied valid coupon reuses it without a new remote call` (line 302) |
| Typed but not yet applied | PASS | `confirming with a typed but never-applied code validates remotely then emits navigate` (line 327) |
| Invalid, inactive, or errored coupon blocks navigation | PASS | `confirming with an invalid or errored coupon blocks navigation` (line 356) + per-outcome tests at lines 231, 251 |
| Navigation event survives a consumer resubscription gap (NEW) | PASS | `a navigate event survives a gap before any collector attaches` (line 288) — calls `onConfirmPurchase()` before any `events` collector attaches, then opens one and asserts delivery; fails under replay-0 `SharedFlow`, passes under buffered `Channel`. |

All 8 scenarios across both domains have a passing runtime-covering test. 0 CRITICAL `UNTESTED`/`FAILING` findings.

## Design Coherence

| Design element | Verified in code |
|---|---|
| Decision 1: `combine(observeCart(), inputs).stateIn(WhileSubscribed(5_000), Loading)` | Match — `CartViewModel.kt:51-53` |
| Decision 2: handlers read/write only `inputs`, never `state.value` | Match — grep confirms zero handler `.value` reads |
| Decision 3: explicit `loadPhase: Loading\|Loaded\|Failed` | Match — `LoadPhase` enum, `CartInputs.loadPhase` |
| **Decision 4: `Failed` reachable only from `Loading`** | **Match** — `refresh()` (line 177-192): `loadPhase = Loaded` on success; `Failed` set only when `it.loadPhase == LoadPhase.Loading`, else unchanged. A failed pull-to-refresh on an already-`Loaded` empty cart cannot reach `Failed`/`Error`; it stays `Loaded` → `reduce` returns `Success(emptyList())`. This is enforced structurally by the guard, not just by convention. No dedicated test names this exact edge case (empty-and-Loaded + failed manual refresh), but the reduction table and the guard make it unambiguous by inspection — noted as a WARNING (coverage gap), not a spec violation, since no spec scenario names this exact sub-case. |
| Decision 5: `Channel<CartEvent>(BUFFERED)` + `receiveAsFlow()` + `trySend` | Match — lines 55, 64, 173 |
| Decision 6: `WhileSubscribed(5_000)` (not `Eagerly`) | Match — line 53 |
| Reduction table (non-empty→Success always; empty+Loading→Loading; empty+Failed→Error; empty+Loaded→Success(empty)) | Match — `reduce()` lines 78-96 implements exactly this table |
| `DispatcherProvider` stays out of scope | Confirmed absent — `grep -rn "DispatcherProvider" app/src` returns no matches |
| `CartScreen.kt` needs zero source edit | Confirmed — `git diff HEAD -- .../CartScreen.kt` is empty |
| `CartUiState`/`CartEvent` shapes frozen | Confirmed — both files unchanged; `CartUiState` still `Loading`/`Error(reason)`/`Success(...)` |

## Deviations from Design — Assessed

1. **Third re-sited test** (`retry after an error calls refresh again and succeeds once the cache is populated`, line 148) needed an extra `awaitItem()` for an intermediate `Success(emptyList())` emission beyond the 2 design-named tests. **Assessment: consistent, not scope creep.** This is a direct, predictable consequence of the design's own documented rationale ("interleaving now changes only which intermediate tuple is seen, never the terminal tuple" — design.md, root-cause section) applied to a test the design authors didn't individually trace through. The terminal assertion is unchanged; only mechanism (one extra intermediate-state await) changed. No spec or behavior impact.
2. **`successFor(...)` fully removed** rather than "refactored". **Assessment: consistent, explicitly authorized.** Design's own "Public API deltas" section states `successFor(...)` "disappears", and task 6.3 explicitly greps for its absence. Apply's phrasing gap (tasks 2.3-2.5 didn't say "remove") is a tasks-authoring omission, not an apply deviation from design intent.
3. **Early draft briefly bundled Channel migration into Phase 2 GREEN**, caught and split back before any test ran. **Assessment: non-issue.** Never reached a test run in that bundled state; final code and commit boundaries respect the design's two-work-unit split (state reduction vs. event channel), each independently revertable per the rollback plan.

None of the three deviations represents unauthorized scope creep or a spec violation. All are either directly predicted by the design's own text or explicitly authorized by it.

## Issues

**CRITICAL**: None.

**WARNING**:
- No dedicated test names the specific edge case "a manual pull-to-refresh fails while the cart is already `Loaded`-empty" (i.e., `Success(emptyList())` before the failed refresh, `Success(emptyList())` still after). The code's guard structurally prevents regression (Decision 4), and this exact sub-case is not named by any spec scenario, so this is a coverage-completeness note, not a spec or design violation. Recommend adding one test for full documentation-by-test of the reduction table's `empty | Loaded` row under a failed manual refresh, since this is the specific correctness property the proposal calls out as "not just a style preference."

**SUGGESTION**:
- None beyond the above.

## Final Verdict

**PASS.** All 18 tasks are complete and match the actual code state (spot-checked and grep-verified, not merely trusted from checkboxes). All 8 spec scenarios across both domains (cart-list, coupon-validation) — including both NEW scenarios (empty-cart deterministic resolution / retry-from-Error, and navigation-event-survives-a-collector-gap) — are satisfied by passing runtime tests, independently re-run (`48/48`, 0 failures). Both required commands from `openspec/config.yaml`'s verify block are green when re-run fresh (`--rerun-tasks` test run, `assembleDebug`). `DispatcherProvider` was not introduced. `CartScreen.kt` has zero diff. The `combine(...).stateIn(...)` reduction matches the design's `LoadPhase` state machine, and Decision 4 (`Failed` reachable only from `Loading`) is structurally enforced in `refresh()`. All 3 reported deviations from design are consistent with the design's own stated intent and are not scope creep. One WARNING (a missing explicit test for one edge case of Decision 4) does not block PASS.
