<!--
Materialized from Engram (project: android-shopping-cart-kata, topic: sdd/navigation-3-migration/research,
observation #1437) on 2026-09-18. This is a copy; Engram remains the live source of truth.
-->

## Research: AGP / compileSdk 36 / Navigation 3 version compatibility

Gates the future `navigation-3-migration` change (4th of 4 sequenced changes under `architecture-modernization`). The official Nav3 migration guide hard-requires `compileSdk = 36`; project is currently AGP 8.6.1 / compileSdk 35 / Gradle 8.11.1 / Kotlin 2.0.21.

### 1. Does AGP 8.6.1 support compileSdk 36? Minimum AGP, Gradle bump?
No. AGP 8.9.0 caps at API 35. **AGP 8.10.0 is the first version supporting API 36** (verbatim from official release notes), minimum Gradle requirement 8.11.1 — identical to the project's current Gradle version, so **no Gradle bump needed** at the AGP 8.10.0 floor. AGP 8.11.0/8.12.0 (also capped at API 36) raise the minimum Gradle requirement to 8.13 — only relevant if a newer-than-minimal AGP is chosen.
Confidence: High. Sources: AGP 8.9.0/8.10.0/8.11.0/8.12.0 release notes (developer.android.com/build/releases/agp-8-{9,10,11,12}-0-release-notes).

### 2. Current stable `navigation3-runtime` / `navigation3-ui`
**1.1.7** confirmed via Google Maven metadata (1.2.0 series is still `-rc01`, not stable). The migration guide's sample version (`nav3Core = "1.1.7"`) is real, not a placeholder.
Confidence: High. Source: dl.google.com/android/maven2/androidx/navigation3/navigation3-runtime/maven-metadata.xml.

### 3. Current stable `androidx.lifecycle:lifecycle-viewmodel-navigation3`
**2.11.0** confirmed via Google Maven metadata (2.12.0 series is alpha-only). Matches the guide exactly.
Confidence: High. Source: dl.google.com Maven metadata for lifecycle-viewmodel-navigation3.

### 4. Breaking changes AGP 8.6.1 → 8.10.0 (or 8.11/8.12)
AGP 8.10.0 documents one breaking change: `finalizeDsl` now requires parameterized types — affects custom Gradle plugin authors, unlikely to affect this app module's `build.gradle.kts`. AGP 8.7.0/8.8.0 notes not individually fetched (gap); nothing surfaced suggesting a breaking change in that range affecting Compose/Room/Retrofit/Material3.
Confidence: Medium (8.9–8.12 verified directly; 8.7/8.8 not fetched).

### 5. Kotlin 2.0.21 compatibility with the required AGP/compileSdk
**Unresolved ambiguity — flagged, not asserted safe.** `developer.android.com/build/kotlin-support`'s compatibility table states "Kotlin 2.0 → Required AGP version 7.4.2-8.3", which read literally conflicts with the project's own current working combination of Kotlin 2.0.21 + AGP 8.6.1 (already > 8.3, already works). No authoritative source resolves this discrepancy.
Confidence: Low/unresolved — recommend an empirical build spike (bump AGP to 8.10.0 + compileSdk 36 on a throwaway branch, run `./gradlew assembleDebug`) as an early task in the design/tasks phase of `navigation-3-migration`, rather than assuming safety.

### Sources
| Title | URL |
|---|---|
| Migrate from Navigation 2 to Navigation 3 | https://developer.android.com/guide/navigation/navigation-3/migration-guide |
| AGP 8.9.0 release notes | https://developer.android.com/build/releases/agp-8-9-0-release-notes |
| AGP 8.10.0 release notes | https://developer.android.com/build/releases/agp-8-10-0-release-notes |
| AGP 8.11.0 release notes | https://developer.android.com/build/releases/agp-8-11-0-release-notes |
| AGP 8.12.0 release notes | https://developer.android.com/build/releases/agp-8-12-0-release-notes |
| Google Maven metadata — navigation3-runtime | https://dl.google.com/android/maven2/androidx/navigation3/navigation3-runtime/maven-metadata.xml |
| Google Maven metadata — lifecycle-viewmodel-navigation3 | https://dl.google.com/android/maven2/androidx/lifecycle/lifecycle-viewmodel-navigation3/maven-metadata.xml |
| AGP, D8, and R8 versions required for Kotlin versions | https://developer.android.com/build/kotlin-support |

### Go/No-Go for sdd-propose
**Conditional GO.** compileSdk 36 is satisfiable with a contained, low-risk bump to AGP 8.10.0 (no Gradle bump). Nav3 dependency versions are verified-stable. Open item the design phase must account for: the Kotlin/AGP compatibility ambiguity (§5) — not blocking, but resolve via an early build-spike task, not by assumption.
