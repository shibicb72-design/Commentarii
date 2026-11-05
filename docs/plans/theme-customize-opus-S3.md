# theme-customize S3 + S4a - Opus team lead plan

Authority: `theme-customize-plan.md` (3.2, 3.5, 3.6, section 4 rows S3/S4a), `theme-customize-S3.md`. Team: lead + `opus-peer`. Only the lead commits.

## S3: what finishes it

WIP gaps found on review of the uncommitted resolver:

1. `MainActivity` uses `customized` / `ThemeOverrideCodec` without imports: does not compile.
2. `onTertiary`: an `onPrimary`-only edit on Rose moved it though tertiary stays stock, and a primary edit could leave it stale. Palette themes always pair it with `onPrimary`; Rose follows only once tertiary moved.
3. A readable colour drifted one channel step through `readableOn`'s Oklab lerp at fraction 0: now returned as picked, and an unedited primary is kept unless it fails 4.5.
4. `outlineVariant` and `sky` keys were ignored: now applied. `sky` added to the codec's colour keys so bad values drop like every other colour.
5. Outlined titles (Sunny): fills were forced to 3:1, repainting stock pastels. They now keep their values, as `ThemeContrastTest` allows; legibility comes from outline/bg 4.5. **Deviation from the literal 3.5 table, recorded in the report.**
6. Dead title-brush branch removed (`titleBrush` is null in all 8 themes).

### Lead (code)
`T/ThemeCustomize.kt`, `T/ThemeOverride.kt` (key + stale comments), `MainActivity.kt`, `T/Theme.kt`.

### Peer (tests + Gradle)
- New `ThemeCustomizeTest`: same instance for null/empty on all 8; per-role isolation with documented dependents; equals `paletteMode(merged)`; seeded random overrides, 8 themes x 2 modes, every 3.5 pair plus the `ThemeContrastTest` extras; polarity; mood + derived onMood; stored onMood ignored; bad JSON = stock.
- Extend `ThemeStartupFlashTest`: saved override on the first frame.
- Runs targeted tests, reports failures with a diagnosis. Real bugs get a failing test first.

### Acceptance
Targeted, then full `testDebugUnitTest --rerun-tasks` with 0 failures; `assembleDebug` OK; `ThemeStockGoldenTest` and `ThemeContrastTest` unedited and green. Commit `feat(theme): S3 ThemeCustomize resolver + startup wiring`, push the feature branch only.

## S4a: usable customize UI (only after S3 is green)

Scope is the plan's S4a row: Customize button, route, screen, preview, mode selector, reset, Animated/Off. **No colour editing** (S4b), no fonts/glyphs (S5). No new motion, no new dependency.

| Slice | Files | Owner | Acceptance |
|---|---|---|---|
| A. Route | `ui/AppNav.kt` (`Routes.THEME_CUSTOMIZE = "settings/theme/{id}"`, `tabOf`, `concreteRoute`), `ui/lock/NavRestore.kt` (`isValid`) | Lead | Route maps to the Settings tab, survives lock restore (trap 6), unknown id pops back |
| B. Entry | `ui/settings/SettingsScreen.kt` `ThemeRow`: sibling `TextButton` at `padding(start = 48.dp)` below Animated/Off, labelled with the theme name for TalkBack; "Customized" tag; `ThemeSwatch` reads the effective theme | Lead | Merged row semantics unchanged, so `ThemeFlowTestBase.themeRow` and existing flow tests stay green unedited |
| C. Screen + VM | New `ui/settings/ThemeCustomizeScreen.kt`, `ThemeCustomizeViewModel.kt`: back, theme name, Light/Dark selector (defaults to current mode, does not change the app's mode), live preview, Animated/Off on animated themes, reset with confirm dialog naming what changes | Lead | Compact: preview above controls. Medium/Expanded: two columns. Strings in `strings.xml`, targets at least 48dp, every surface sets its content colour |
| D. Preview | Nested `CommentariiTheme(dark, draftTheme, animate = false) { ThemedPage { DailyPageContent(sample...) } }` in a bounded box, input blocked, semantics cleared | Lead | Uses existing samples; no new animation |
| E. Tests | New `ThemeCustomizeFlowTest`; NavRestore test addition | Peer | Open from Settings, edited override shown, reset restores stock and Animated, non-active theme's edit leaves app unchanged, route survives lock restore |

Acceptance for S4a: full `testDebugUnitTest --rerun-tasks` 0 failures, `assembleDebug` OK, existing theme flow tests unedited. Commit `feat(theme): S4a customize screen, route, preview, reset`, push the feature branch only.

## Out of scope
Scene/sticker colours, motion, merging, main, Fold/adb, S4b and later. The other untracked `docs/plans/*.md` stay uncommitted; this file is committed with S3.
