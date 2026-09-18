<!--
Materialized from Engram (project: kata-mobile-android-empty, topic: sdd/shopping-cart/explore,
observation #1406) on 2026-09-18. This is a copy; Engram remains the live source of truth.
-->

## Exploration: shopping-cart kata (cart list + coupon validation + summary)

### Current State
Scaffold is a truly empty single-module `:app` Android/Compose project, no theme package generated:
- Package `com.pedidosya.kata`, `namespace`/`applicationId` = `com.pedidosya.kata`.
- AGP 8.6.1, Kotlin 2.0.21, Gradle 8.11.1, compileSdk/targetSdk 35, minSdk 24, Java 17.
- `App.kt` is a bare `Application` subclass (no DI annotation). `MainActivity.kt` extends `AppCompatActivity`, calls `setContent { MaterialTheme { Text(...) } }` directly — there is NO generated `ui/theme/{Color,Type,Theme}.kt`, unusual even for the default "Empty Activity" template.
- `gradle/libs.versions.toml` only declares: activity-compose, compose-bom (2025.05.00), compose ui/ui-graphics/ui-tooling-preview, compose-material3 1.3.2, google-material, appcompat, androidx test runner/rules, ui-test-junit4. NOTHING for: navigation, networking (Retrofit/Ktor/OkHttp), JSON (kotlinx.serialization/Moshi/Gson), DI (Hilt/Koin), Room/DataStore, coroutines-test, MockK, Turbine, JUnit (unit).
- No `androidx.navigation` dependency exists at all — screen-to-screen navigation (List → Summary) has zero scaffolding today and must be added from scratch (Navigation-Compose or manual state-based nav).
- No git repo initialized (at exploration time).

### Mock API — verified real response shapes (fetched twice, consistent both times, via WebFetch)
**GET https://dummyjson.com/c/9518-ea8d-4660-afd8** (cart):
```json
{"cart":{"id":"cart_987654321","currency":"USD","items":[
  {"id":"prod_001","title":"iPhone 15 Pro","category":"technology","quantity":1,"price":999,"image_url":"..."},
  {"id":"prod_002","title":"Funda de Silicona MagSafe","category":"technology","quantity":2,"price":49,"image_url":"..."},
  {"id":"prod_003","title":"Café en Grano Premium 1kg","category":"grocery","quantity":2,"price":24.5,"image_url":"..."},
  {"id":"prod_004","title":"Aceite de Oliva Extra Virgen 500ml","category":"grocery","quantity":1,"price":12,"image_url":"..."}
]}}
```
Notable: root wrapper key is `cart` (not the items array directly) → DTO needs `CartResponseDto{ cart: CartDto{ id, currency, items: List<CartItemDto> } }`. Prices are non-integer floats for some items (24.5) — needs a numeric type, not Int. `image_url` present per item (list UI should render it or gracefully placeholder if load fails — not explicitly required but present in payload).

**GET https://dummyjson.com/c/8e56-250c-4cd8-9c5a** (coupons):
```json
{"coupons":[
  {"code":"BIENVENIDA10","discount_percentage":10,"applicable_category":"all","is_active":true},
  {"code":"TECHREBATE25","discount_percentage":25,"applicable_category":"technology","is_active":true},
  {"code":"FRESHFOOD15","discount_percentage":15,"applicable_category":"grocery","is_active":true},
  {"code":"CUPONVENCIDO","discount_percentage":50,"applicable_category":"all","is_active":false}
]}
```
Notable: this is a LIST endpoint (no single-code lookup by query param) — the client must fetch the whole coupon list once and match the user-typed code client-side. Each coupon carries `applicable_category` (all/technology/grocery) and `is_active` — meaning coupons can be category-scoped and can be expired/disabled, which the cart's mixed-category items make directly relevant to discount-calculation logic.

**Verification caveat**: response captured through `WebFetch` (an AI-mediated fetch, not a raw HTTP client) — confirmed identical across two independently-worded prompts (high confidence), but a direct `curl`/Postman check before freezing DTOs in `sdd-propose`/`sdd-spec` is still recommended since no Bash/network tool was available in this exploration sandbox. (Resolved later in `sdd/shopping-cart/tasks` §3.1: raw `curl` confirmed the shapes, with a note that the real response's `id` is a String and 3/4 sample items omit `title`/`image_url`.)

### Affected Areas (all net-new — nothing exists yet)
- `app/build.gradle.kts`, `gradle/libs.versions.toml` — need networking, JSON, DI, Room, navigation, and unit-test deps added.
- `app/src/main/java/com/pedidosya/kata/App.kt` — becomes DI entry point (Hilt `@HiltAndroidApp` or Koin `startKoin`).
- `app/src/main/java/com/pedidosya/kata/MainActivity.kt` — becomes NavHost host; needs a proper `ui/theme` package created (currently missing).
- New packages needed from scratch: `domain/`, `data/` (remote DTOs + Room entities + repositories), `presentation/{cartlist,summary}/` (ViewModels + Composables), DI module wiring.
- No existing unit tests to preserve/extend (only the instrumented placeholder); Strict TDD mode means test infra (JUnit5/4, kotlinx-coroutines-test, MockK or Turbine) must be added before any production code.

### Approaches — Architecture Pattern
1. **MVVM + Clean Architecture (domain/data/presentation layers), sealed `UiState` per screen** — ViewModel exposes `StateFlow<UiState>`, use cases in `domain`, repository pattern in `data`.
   - Pros: matches the requirement's own wording ("MVVM, MVI, Clean Architecture"); clean layer boundaries are easy for a reviewer to grade; testable use cases in isolation; natural fit for Strict TDD (test use cases and ViewModels independently).
   - Cons: more files/boilerplate for only 3 screens; slightly more ceremony than the kata strictly needs.
   - Effort: Medium.

2. **MVI (Intent → Reducer → State, single `StateFlow` per screen)** — explicit unidirectional dataflow with sealed `Intent`/`Effect` classes and a reducer function.
   - Pros: showcases advanced reactive-state discipline explicitly (an evaluation criterion); single source of truth per screen is very testable.
   - Cons: heavier ceremony (Intent/Reducer/Effect sealed hierarchies) for simple screens; less universally recognized than MVVM by reviewers; higher risk of over-engineering a small kata.
   - Effort: Medium-High.

3. **Lightweight MVVM+Clean hybrid**: Clean layering (domain/data) + MVVM presentation, but ViewModel intents modeled as a small sealed `Event`/`Action` type (MVI flavor) without a full reducer abstraction.
   - Pros: gets Clean layering credit AND explicit reactive-intent modeling credit without MVI's full ceremony; scales cleanly to exactly 3 screens.
   - Cons: a "hybrid" needs one clear paragraph of justification so it doesn't read as inconsistent.
   - Effort: Medium.

**Recommendation**: Option 3 (MVVM presentation + Clean domain/data layering + sealed Event/State), lowest risk of over/under-engineering for a graded kata and reuses the requirement's own vocabulary. (This is the option the accepted proposal/design ultimately followed.)

### Approaches — Networking
1. **Retrofit + OkHttp** — Pros: most conventional/expected in Android interviews, mature, simple with `kotlinx-serialization-converter` or Moshi, small setup. Cons: not multiplatform (irrelevant here — no KMP requirement). Effort: Low.
2. **Ktor Client** — Pros: coroutine-native, KMP-ready. Cons: no KMP need in this kata; more manual setup (logging/serialization plugins); less familiar to typical Android-only reviewers. Effort: Low-Medium.

**Recommendation**: Retrofit + OkHttp + kotlinx.serialization converter — lower risk, most conventional for an Android-only evaluated kata.

### Approaches — Offline-first cache for cart list
1. **Room** — Pros: purpose-built for structured list caching, natural `Flow<List<CartItemEntity>>` observation matching the offline-first "show cache immediately, refresh in background" requirement; supports querying/updating individual rows. Cons: schema/DAO/migration boilerplate. Effort: Medium.
2. **DataStore (Proto or Preferences)** — Pros: simpler for a single blob. Cons: not designed for list querying; would require serializing the whole cart as one JSON blob, losing per-item granularity and any future incremental update capability. Effort: Low but architecturally worse fit.

**Recommendation**: Room for the cart items cache (Flow-driven, matches offline-first spec directly). DataStore only if a tiny extra preference (e.g. last-successful-fetch timestamp) is needed — optional, not required by the spec.

### Approaches — Dependency Injection
1. **Hilt** — Pros: Google-recommended standard, compile-time safety, `@HiltViewModel` integrates directly with Compose Navigation, most expected in an Android architecture-graded kata. Cons: KSP/kapt setup overhead, slightly heavier build. Effort: Medium.
2. **Koin** — Pros: lightweight Kotlin DSL, zero annotation processing, faster to wire for a small kata, easy manual module definition. Cons: runtime resolution (no compile-time safety), slightly less "textbook" for interview grading of DI usage. Effort: Low.
3. **Manual/ServiceLocator** — Pros: zero dependency. Cons: for a kata explicitly graded on "architecture and patterns," skipping a recognized DI framework reads as weaker; wiring gets messy fast even at 3 screens. Effort: Low but higher long-term risk.

**Recommendation**: Hilt is the safer default for grading expectations; Koin is a legitimate faster-to-wire alternative if setup time is a concern — this is the one real open trade-off to confirm with the user before `sdd-propose`. (Resolved later — decision #1407 in the design — as **manual `AppContainer` DI**, rejecting both Hilt and Koin: zero new deps, graph is ~10 objects.)

### Risks / Ambiguities to resolve before `sdd-propose`
1. **Coupon service failure/invalid-code behavior is unspecified** — requirement doesn't say whether an invalid/expired/unreachable coupon blocks "Confirmar Compra" or simply proceeds with 0% discount. Needs a product decision.
2. **Scope of "local mock or remote URL" for coupons is ambiguous** — could mean (a) always call the remote URL, (b) a fallback chain (remote first, hardcoded local mock on failure), or (c) developer's free choice of either. This materially changes the coupon repository design and must be decided, not assumed.
3. **Offline-first caching scope is ambiguous** — requirement explicitly describes offline-first only for the cart list; it's unclear whether the coupon list should also be cached for offline validation, or whether coupon validation always requires connectivity.
4. **Category-scoped coupon vs. flat-total discount is a real data/requirement mismatch** — coupons carry `applicable_category` (all/technology/grocery) but the cart mixes categories, while the requirement's Summary screen only asks for "total final" + "% descuento aplicado" (singular, flat). Needs a decision: apply the % to the whole cart total regardless of category (simpler, likely sufficient for the stated Summary requirement) vs. correctly restrict the discount to matching-category items (more correct given the data model, more complex, not explicitly requested).
5. **No navigation dependency exists yet** — Navigation-Compose (or an equivalent manual approach) must be added; not a blocker, but a concrete scope item to size correctly in `sdd-tasks`.
6. **JSON/data-shape fidelity was captured via an AI-mediated WebFetch, not a raw HTTP call** — confirmed consistent across two independent fetches (high confidence) but recommend one direct verification (curl) before DTOs are frozen in `sdd-spec`/`sdd-design`.
7. **Strict TDD Mode is active** with zero unit-test infra currently declared (no JUnit unit deps, no MockK, no coroutines-test, no Turbine) — this must be added as the very first implementation task, before any production code, or TDD cannot start.

### Ready for Proposal
Yes, with two explicit open questions the orchestrator should surface to the user before/at `sdd-propose`: (1) Hilt vs Koin for DI, and (2) the coupon-failure/category-scope ambiguities above (items 1–4) — these are product decisions, not implementation details, and should not be silently assumed by the proposer.

---
Engram observation: #1406 · topic `sdd/shopping-cart/explore` · project `kata-mobile-android-empty`
