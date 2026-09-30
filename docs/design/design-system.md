# Sift Design System

> Source of truth: `app/src/main/java/app/sift/ui/Theme.kt`. This doc states the rules; the code is the tokens.

## Principles
- **Ink on paper, near-monochrome.** Colour is reserved for meaning: indigo = selection/primary action, red = blocked/error. Category identity comes from icon shape + label, never hue — thirteen categories must read as one calm list.
- **Calm density.** Lists are the app's core surface; typography carries weight instead of size so dense rows stay legible without shouting.
- **Both themes are first-class.** Every screen must look deliberate in light and dark; never hardcode colors — use `MaterialTheme.colorScheme`. `materialYou` (wallpaper palettes) is a user toggle that swaps the whole scheme; all screens must survive it.

## Colour tokens (Material 3 roles)
| Role | Light | Dark | Notes |
|---|---|---|---|
| primary | `#3B4A9E` indigo | `#B4BDEA` indigo-light | Selection, primary action |
| error | `#B3261E` | `#F2B8B5` | Blocked states only |
| background/surface | `#FAFAF9` warm paper | `#0E0E10` | |
| surfaceContainer (L→H) | `#F1F1EE`→`#E6E6E2` | `#18181B`→`#2A2A2E` | Cards, sheets, search field |
| outlineVariant | `#E2E2DE` | `#2B2B2F` | Hairline borders |

Blocked rows: headline dims to `onSurfaceVariant`, supporting line uses `error`, app icon at 40% alpha with an 18dp red badge.

## Typography — Inter (subset: Latin, weights 300–700)
- Headings: SemiBold, tight tracking (display 52sp/−1.6 → headlineSmall 21sp/−0.4).
- Titles: Medium/SemiBold, 11–19sp.
- Body: Normal, 12.5–16sp.
- Labels carry weight (Medium) at 11–14sp, +0.2–0.9 tracking.
- `TabularFigures` (`tnum`) for any ticking count or timestamp.
- `OvertypeLabel` (11sp Medium, +0.9 tracking, uppercased by caller) for section labels.

## Shape
`Shapes`: 8 / 12 / 16 / 20 / 26dp — softer than hard-cornered, tighter than stock M3. Chips and search field use `CircleShape`. Sheets/cards 16–20dp.

## Components (in `ui/Components.kt`)
- `TabScaffold` / `DetailScaffold` — screen chrome; one `SnackbarHost` at `AppRoot` level only (per-screen hosts replay animations on tab change — do not regress).
- `QuietChip` (filter chips), `MenuChip` (dropdown pickers), `ActionTile` (channel actions), `Pill` (status), `SearchField` (borderless filled, circle), `EmptyState` (title + body + optional action — every list screen defines empty/loading/error), `AppIcon` (128px bitmap cache), `SectionLabel`, `BrandMark`/`Wordmark`.
- Feedback: `vm.say(...)` snackbars; undoable messages get a 6s window (`UNDO_MILLIS`), indefinite duration + timeout.

## Motion & a11y minimums
- Purposeful transitions only; respect reduced-motion; no decorative loops.
- Touch targets ≥48dp; every icon gets a content description (or `null` when decorative and the row is labelled); text scales with system font size (sp everywhere).
- RTL: use `padding(start=)` (RTL-aware), never `absolutePadding`; icons auto-mirrored where directional.

## Adding a new screen — checklist
1. Tokens only — no hardcoded colors/sizes outside `Theme.kt`.
2. Empty, loading, and error states defined.
3. Verified light + dark + materialYou.
4. Strings via `res/values/strings.xml` (i18n era — no new hardcoded UI text).
5. TalkBack order sane; icons described.
