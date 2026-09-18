<!--
Materialized from Engram (project: kata-mobile-android-empty, topic: sdd/shopping-cart/design,
observation #1416) on 2026-09-18. This is a copy; Engram remains the live source of truth.
-->

# Design: Shopping Cart with Category-Scoped Coupon Validation

## Technical Approach

Single `:app` module, package-by-layer under `com.pedidosya.kata`, following the official Android app-architecture guide (UI → domain → data, dependencies point inward). Room is the single source of truth for cart items; Retrofit refreshes it. Coupons are fetched remotely on every Apply and never persisted. Wiring is manual constructor DI from `App.kt`.

```
com.pedidosya.kata/
  App.kt                      # owns AppContainer
  MainActivity.kt             # hosts KataNavHost
  di/AppContainer.kt          # manual graph (interface + Default impl)
  data/remote/{CartApi,CouponApi}.kt, dto/*Dto.kt
  data/local/{KataDatabase,CartItemDao,CartItemEntity}.kt
  data/mapper/{CartMappers,CouponMappers}.kt
  data/repository/{CartRepositoryImpl,CouponRepositoryImpl}.kt
  domain/model/{CartItem,Coupon,CartTotals}.kt
  domain/repository/{CartRepository,CouponRepository}.kt
  domain/usecase/{ObserveCart,RefreshCart,ValidateCoupon,CalculateTotals}.kt
  ui/theme/{Color,Type,Theme}.kt
  ui/navigation/{Routes,KataNavHost}.kt
  ui/cart/{CartScreen,CartViewModel,CartUiState,CartEvent}.kt
  ui/summary/{SummaryScreen,SummaryViewModel,SummaryUiState}.kt
```

## Architecture Decisions

| Decision | Choice | Rejected | Rationale |
|---|---|---|---|
| DI | Manual `AppContainer` in `App`, `viewModelFactory { initializer { } }` | Hilt, Koin | User decision #1407: zero new deps; graph is ~10 objects |
| Cache scope | Room for cart items only; coupons never cached | Caching coupons | Decisions #1408/#1409: validation must be remote-only |
| SSoT | DAO `Flow` is the only render source; network writes to Room | Render network response directly | Guarantees cache-first rendering and airplane-mode behavior |
| Coupon lookup | Fetch full list, match code client-side (trim + ignoreCase) | Per-code endpoint | Mock has no lookup-by-code endpoint |
| Discount | Applies only to the subtotal of items matching `applicable_category` (`all` = every item) | Flat discount on cart total | Decision #1410 |
| Summary data | Nav passes primitives (`code`, `pct`, `category`); Summary re-derives totals from Room + `CalculateTotals` | Pass `CartTotals`/parcelable | No parcelable domain objects on the back stack; no second network call |
| Money | `Double` prices, `round2` (BigDecimal HALF_UP) applied once inside `CalculateTotals` | Round in Composables; `BigDecimal` everywhere | Deterministic, unit-testable totals; UI only formats |
| Presentation | MVVM + sealed `CartEvent` intents | Full MVI reducer | Decision #1413: reducer ceremony is over-engineering here |

## Data Flow

```
CartApi ──DTO──> CartRepositoryImpl ──Entity──> Room ──Flow<Entity>──┐
                        ▲ refresh()                                  │ map
                        │                                            ▼
CartViewModel ──────────┴── combine(items, couponState) ──> StateFlow<CartUiState> ──> CartScreen
       │                                                                                  │ Apply
       └── ValidateCoupon ──> CouponRepositoryImpl ──> CouponApi (always network)  <───────┘
                                                                                     Confirm
CartScreen ──nav("summary?code&pct&category")──> SummaryViewModel ──Room+CalculateTotals──> SummaryScreen
```

Offline-first contract in `CartViewModel`: cache non-empty → `Success` immediately, refresh in background; refresh failure with non-empty cache → `Success(staleData = true)` (snackbar); refresh failure with empty cache → `Error`. Never blocks render on the network.

## Interfaces / Contracts

```kotlin
// domain
data class CartItem(val id: String, val title: String, val category: String,
                    val quantity: Int, val price: Double, val imageUrl: String?)
data class Coupon(val code: String, val discountPercentage: Double,
                  val applicableCategory: String, val isActive: Boolean)
data class CartTotals(val subtotal: Double, val discount: Double, val total: Double,
                      val nominalPercentage: Double)

interface CartRepository {
    fun observeCart(): Flow<List<CartItem>>       // Room-backed, never throws
    suspend fun refresh(): Result<Unit>
}
interface CouponRepository { suspend fun validate(code: String): CouponValidationResult }

sealed interface CouponValidationResult {
    data object NotApplied : CouponValidationResult
    data class Valid(val coupon: Coupon) : CouponValidationResult
    data object Invalid : CouponValidationResult      // code not in list
    data object Inactive : CouponValidationResult     // is_active == false
    data object ServiceError : CouponValidationResult // IO/HTTP/parse failure
}

sealed interface CartUiState {
    data object Loading : CartUiState
    data class Error(val reason: CartErrorReason) : CartUiState
    data class Success(val items: List<CartItem>, val totals: CartTotals,
                       val couponInput: String, val coupon: CouponValidationResult,
                       val isValidating: Boolean, val isRefreshing: Boolean,
                       val staleData: Boolean) : CartUiState {
        val canConfirm: Boolean get() =
            (couponInput.isBlank() && coupon !is CouponValidationResult.Valid) ||
            coupon is CouponValidationResult.Valid
    }
}
```

`CalculateTotals(items, coupon?)`: `matching = items.filter { c.applicableCategory == "all" || it.category == c.applicableCategory }`; `discount = round2(matching.sumOf { price * quantity } * pct / 100)`; `total = round2(subtotal) - discount`.

ViewModel exposure (single pattern for both screens):

```kotlin
val state: StateFlow<CartUiState> =
    combine(observeCart(), couponState, refreshState) { items, coupon, refresh ->reduce(...) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CartUiState.Loading)
```

Mapping: `CartItemDto → CartItemEntity` (in repository refresh, `@Transaction replaceAll` = deleteAll + insertAll), `CartItemEntity → CartItem` (DAO Flow map). `CouponDto → Coupon` directly; no entity. `image_url`/`discount_percentage`/`applicable_category`/`is_active` use `@SerialName`.

Manual graph: `App.onCreate` builds `DefaultAppContainer(context)` with `by lazy` for `OkHttpClient → Retrofit → CartApi/CouponApi`, `Room.databaseBuilder → CartItemDao`, repositories (each taking an injected `CoroutineDispatcher = Dispatchers.IO`), and use cases. Each ViewModel owns a `companion object Factory: ViewModelProvider.Factory` using `viewModelFactory { initializer { (this[APPLICATION_KEY] as App).container… } }`; Composables call `viewModel(factory = CartViewModel.Factory)`. No Android types below `ui`/`di` except the Room database builder.

Navigation: `NavHost(startDestination = Routes.CART)` in `MainActivity`; route `summary?code={code}&pct={pct}&category={category}` with `String`/`Float`/`String` args and defaults (`""`, `0f`, `"all"`) so the empty-coupon path needs no coupon call.

## File Changes

| File | Action | Description |
|---|---|---|
| `gradle/libs.versions.toml` | Modify | Add navigation-compose, lifecycle-viewmodel-compose/runtime-compose, retrofit + kotlinx-serialization converter, okhttp logging-interceptor, kotlinx-serialization-json (+ plugin, version.ref kotlin), room runtime/ktx/compiler, ksp plugin, coil-compose, junit4, kotlinx-coroutines-test, mockk, turbine |
| `app/build.gradle.kts` | Modify | Apply serialization + ksp plugins, add the deps above, `testOptions`/`ksp` room schema arg |
| `app/src/main/java/com/pedidosya/kata/App.kt` | Modify | Normalize indentation; hold `container: AppContainer` |
| `.../MainActivity.kt` | Modify | Replace placeholder `Text` with `KataTheme { KataNavHost() }` |
| `.../di/AppContainer.kt` | Create | Manual dependency graph |
| `.../data/**`, `.../domain/**`, `.../ui/**` | Create | Files listed in the tree above |
| `app/src/test/java/com/pedidosya/kata/**` | Create | Unit tests (test-first per Strict TDD) |
| repo root | Create | `git init` + baseline commit before any slice |

## Testing Strategy

| Layer | What | Approach |
|---|---|---|
| Unit — `CalculateTotals` | `all` category, single-category match, mixed cart, no match (discount 0), inactive never reaches it, empty cart, rounding (24.5×2, 15%) | Pure JUnit4, no mocks — primary TDD target |
| Unit — mappers | DTO→Entity→domain round trip, null `image_url`, float prices | Pure JUnit4 |
| Unit — `CouponRepositoryImpl` | code found/not found/inactive, case+whitespace insensitivity, `IOException`/HTTP 500 → `ServiceError`, no cache read | MockK on `CouponApi` + `runTest` |
| Unit — `CartRepositoryImpl` | refresh writes to Room and `observeCart` re-emits; refresh failure returns `Result.failure` and leaves cache intact | MockK api + in-memory fake DAO, `runTest` |
| Unit — `CartViewModel` | Loading→Success, cache-hit-then-refresh, empty cache + failure → Error, non-empty + failure → `staleData`, Apply emits `isValidating` then typed result, `canConfirm` gating | Turbine + `kotlinx-coroutines-test` (`StandardTestDispatcher`, `MainDispatcherRule`), fake repos |
| Unit — `SummaryViewModel` | derives totals from nav primitives + cached items; 0% path | Turbine + fakes |
| Instrumented | Existing placeholder only | Not expanded; Compose UI tests out of scope |

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or process-integration boundary. All network I/O is read-only HTTPS GET to two fixed mock URLs.

## Migration / Rollout

No migration. Room DB is new (`version = 1`, `fallbackToDestructiveMigration()` is acceptable — cache only, lossless to drop). Additive on an empty scaffold; rollback = `git revert` the slice or reset to the baseline commit.

## Open Questions

- [ ] Confirm the two mock URLs with a raw `curl` before freezing DTOs (exploration captured them via WebFetch). — **Resolved**: confirmed in `tasks.md` §3.1 (decision #1420); real `id` is a String, 3/4 sample items omit `title`/`image_url`.
- [ ] Pull-to-refresh (`PullToRefreshBox`, material3 1.3.2) stays an optional final slice — include only if budget allows. — **Resolved**: implemented in Phase 8 (`tasks.md`), code-complete and tested.

> Note: the `staleData: Boolean` field sketched above on `CartUiState.Success` was never actually
> implemented in any task/phase (see `tasks.md` and `apply-progress`) and is flagged as a documented,
> non-blocking design/implementation gap in `verify-report.md`.

---
Engram observation: #1416 · topic `sdd/shopping-cart/design` · project `kata-mobile-android-empty`
