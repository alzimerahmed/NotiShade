# NotiShade — Rules for AI Agents

## Project

NotiShade = on-device Android notification category manager (repo: https://github.com/alzimerahmed/NotiShade, owner: alzimerahmed). Control notifications by **what they're about**, across all apps at once: group every app's notification channels into categories (Promotions, Social, Messages, Security, …), block/silence/allow a whole category in one tap, keep a 7-day notification history (Logs) with blocked ones recorded, keyword rules, log exclusions, undo. No root, no account, **no internet permission** — everything stays on the device. Distribution: signed APKs via GitHub Releases (draft, with SHA256SUMS).

**License:** GPL-3.0, copyright © 2026 Alzimer Ahmed. Bundles the Inter typeface (SIL OFL 1.1, `docs/licenses/Inter-OFL.txt`).

**Scope:** Native Android app only. No backend, no network layer, no analytics — the app must never gain the INTERNET permission.

## How It Works (domain constraints — read before touching the engine)

- Android gives regular apps no API to change other apps' notification settings. NotiShade uses **Notification Listener access** + a **companion device pairing** (unlocks channel write access, same trick smartwatch companions use). Both are set up in `SetupScreen` / `backend/Access.kt`.
- **Blocking = set channel to IMPORTANCE_MIN** (so Android still delivers notifications to us), then the listener cancels each one on arrival and logs it. IMPORTANCE_NONE is unusable — Android drops those before we see them; `adoptBlocked` converts them to our block.
- **Categories are inferred** (`data/Classifier.kt`: channel id/name/description keywords, notification-category hints learned at runtime, conversation flag, weak app priors, tie-break toward categories riskier to silence). Users can override per channel.
- History retention: 7 days, max 5000 entries, batched writes (`data/HistoryStore.kt`). Settings in `data/Store.kt` (single JSON file, atomic writes, migration inline in `load()`).

## Tech Stack & Conventions (do not fight it)

### Language & Tooling
- **Language**: Kotlin (JVM target 17), coroutines + Flow + StateFlow.
- **UI**: Jetpack Compose (BOM 2026.09.00), Material 3, edge-to-edge. No XML layouts. Navigation = minimal own back stack (`Nav` in `MainActivity.kt`), no Navigation Compose.
- **DI**: none — manual wiring in `App.kt` (`App.of(context)` service locator). Do not add Hilt/Koin.
- **Persistence**: kotlinx-serialization JSON files via `AtomicFile` (`Store`, `HistoryStore`). No Room/DataStore.
- **Networking**: none, by design. Never add Retrofit/OkHttp or the INTERNET permission.
- **Build**: Gradle Kotlin DSL, single `:app` module, AGP with compileSdk/targetSdk 37, minSdk 33 (Android 13+). Dependencies are declared directly in `app/build.gradle.kts` (no version catalog). R8 minify + resource shrink on release. Debug applicationId `app.notishade.debug`.
- **Tests**: JUnit 4, plain JVM unit tests (`testDebugUnitTest`). Android-framework-dependent classes are kept thin; pure logic lives in testable objects (Classifier, RuleMatcher, HistoryLogic, BackupCodec).

### Architecture & Conventions
- **Packages**: `app.sift` — `backend/` (system-API wrappers: `Access`, `Channels`), `data/` (models, Classifier, Store, HistoryStore, Repository), `engine/` (`BulkEngine`: batched channel changes + undo batches), `service/` (`NotifListener`), `ui/` (Compose screens + `MainViewModel`).
- **State down / events up**: `MainViewModel` exposes StateFlows; screens call VM methods; user feedback through `vm.say(...)` snackbars (single SnackbarHost in `AppRoot`, undo window 6s).
- **All channel mutations go through `BulkEngine`** — it verifies changes actually applied (system silently ignores some), records undoable `Batch`es, and syncs `logBlocked`. Never call `Channels.update` from UI or the listener directly.
- **The listener is the hot path** — keep `NotifListener.onNotificationPosted` cheap; persist via batched stores; wrap engine calls in `runCatching`.
- **User-facing strings live in `strings.xml`** (extracted in Phase 6) — composables use `stringResource(R.string.x)`, non-composable paths use `Context.getString`/`getQuantityString`; es/de/ar locales exist (ar = RTL test). Persisted data keys (e.g. `Category.label`, batch titles) stay English by design — see `docs/research.md` ADR-007.

### Build Inputs (secrets — never commit)
- Release signing via CI secrets: `NOTISHADE_KEYSTORE_BASE64`, `NOTISHADE_KEYSTORE_PASSWORD`, `NOTISHADE_KEY_ALIAS`, `NOTISHADE_KEY_PASSWORD` (see `.github/workflows/release.yml`). Never commit keystore material (`.gitignore` covers `*.jks`, `*.keystore`, `keystore.properties`).

## Build / Verify

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug   # CI gate (ci.yml, push/PR to main)
./gradlew assembleRelease                             # signed release (release.yml, tag push)
```

**Remote-first verification (mandatory):** we do NOT build or test locally — all builds/tests run in GitHub Actions. Local work is edit-only: IDE typecheck / targeted static review while iterating. Push a branch and let CI verify; a CI green check counts as the gate. Local builds only when debugging the build system itself (requires JDK 17 + Android SDK).

**Release flow:** bump `versionCode`/`versionName` in `app/build.gradle.kts` → tag with the same version (`vX.Y.Z`) → CI builds a **draft** release with checksums → review and publish. `versionName` must match the tag.

## Gotchas

- **IMPORTANCE_NONE channels are invisible to us** — Android drops their notifications before any listener runs. Always convert to our own MIN-based block (`adoptBlocked`).
- **The system silently ignores some importance changes** (e.g. non-blockable channels) — `BulkEngine` re-reads channels after writing and counts mismatches as `locked`. Preserve that verification.
- **Blocked categories must stay IMPORTANCE_MIN, not NONE** — MIN is what keeps them visible to the listener for logging.
- **Release build is minified** (R8 + shrinkResources) — debug green does NOT guarantee release green; validate with `assembleRelease` before tagging.
- **Companion pairing is emulator-hostile** — grant permissions via adb instead (`cmd notification allow_listener`, `cmd companiondevice associate`; commands in README).
- **Don't break the no-network guarantee** — any dependency or code path that pulls in INTERNET access is a blocker.
- **Group summaries** are never logged individually (`FLAG_GROUP_SUMMARY` filter) — preserve.

## Agent Guidelines & Constraints

### Do's
- **Keep pure logic pure** — classification, rule matching, history merge/trim, backup codec stay framework-free objects with unit tests; Android types stay at the edges.
- **Write tests** for new engine/data behavior (behavior, not implementation).
- **Conventional Commits**: `type(scope): description`.
- **Respect module simplicity** — single-module app; don't propose feature modules without a decision record.
- **Plan discipline** — `docs/plan.md` must contain a Quality Gate (security/perf/a11y/anti-vibe audits) and a Release phase, not just feature phases. Every `idea.md` Feature Gap entry maps to a phase or is explicitly deferred — no silent drops. When a phase answers an open question in `docs/research.md`, close it there in the same change.

### Don'ts
- **NO local builds or test runs** — verification is CI-only (user directive).
- **NO INTERNET permission, no network dependencies, no analytics/telemetry** — core product promise.
- **NO new heavy dependencies** without a decision record in `docs/research.md` (license must stay GPL-3.0-compatible).
- **NO secrets in code** — signing material via GitHub secrets only.
- **NO deleting resources** without grepping all reference types (manifest, `R.*`, `@drawable/...`) — Android R-reference rule (`.devin/prompt/phase.md` §19).

## Agent Guidelines & Workflow (this repo's .devin system)

### Resource Discipline (mandatory, non-trivial tasks)
Before any non-trivial task:
1. Read `docs/toolset.md` intent-map (task type → resources)
2. Invoke every skill + sub-agent in that row
3. Read every rule for that task type (`.devin/rules/`)
4. At task end: `code-reviewer` sub-agent on final diff (non-negotiable)
5. Append learnings via `/ce-compound` if durable lesson

Phase implementations (task completes a docs/plan.md row): follow `.devin/prompt/phase.md`.

Skip all this for single-line edits, pure Q&A, reading files.

### Project-Type Filter (Android notification manager)
Per `docs/toolset.md` intent-map:
- **Skip web-only/network:** pwa-engineer, seo-specialist, css-architect, playwright-design-clone, payment-integrator, email-engineer, monorepo-manager, realtime-engineer, backend-architect, web-scraper, search-optimization.
- **Keep universal:** code-reviewer, debugger, test-engineer, security-auditor, performance-engineer, git-master, migration-specialist, docs-writer, build-optimizer, caveman-compressor, vibe-coding-auditor, type-safety-engineer, state-manager (Flow/MVI), database-engineer (JSON store durability), i18n-specialist (RTL/locales), media-optimizer, animation-engineer, frontend-designer (Compose taste), content-writer, accessibility.
- **Quality gates:** CI-only — `./gradlew testDebugUnitTest lintDebug assembleDebug` on GitHub Actions. No local builds.

## Communication Style

Default **caveman-lite** (lightly compressed, readable, technically accurate). `/caveman` skill for full/ultra/wenyan modes.

## Quick Task Flow

Quick tasks: `.devin/prompt/quick.md` (commandments) + `.devin/prompt/rules.md` (scoping, verification, escalation). Phased work: `.devin/prompt/phase.md`.

## Key References

> Note: `docs/*.md` files below are the private knowledge layer (gitignored) — they exist only in the local workspace, not for external cloners.

- `docs/toolset.md` — intent map (task type → skills, sub-agents, rules)
- `docs/plan.md` — phased plan + status
- `docs/project.md` — project state/structure
- `docs/tools-log.md` — .devin resources invoked per session
- `docs/CONCEPTS.md` — project vocabulary
- `docs/research.md` — research, ADRs, gotchas, open questions
- `docs/idea.md` — competitive analysis + Feature Gap List
- `docs/design/design-system.md` — UI tokens + rules
