# Theme picker - plan (Opus, branch `opus/themes`)

## Overview
Eight themes that change ONLY the look (colours, title typography, card/tab shapes, header styling,
background doodles, optional animation). No page layout, section, string or order changes anywhere.
Grateful/Self Care stay removed. "Rose & Cream" (current light+dark) is the default and must render
pixel-identical to today (existing Roborazzi images unchanged).

| id | Name | One-liner | Animated |
|---|---|---|---|
| `rose_cream` | Rose & Cream | The original dusty-pink tabs on warm cream (default). | no |
| `washi_planner` | Washi Planner | Pastel tab labels in five sweet colours, washi-tape strips, hearts, stars, a little bear and potted plant. | no |
| `sunny_journal` | Sunny Journal | Cream paper with rainbow highlighter swashes, a smiling sun, leaf sprigs, candles and mugs. | no |
| `cloud_drift` | Cloud Drift | Baby-blue sky where soft clouds drift past and a sleepy sun gently bobs. | yes |
| `petal_fall` | Petal Fall | Blush cherry-blossom pages with petals slowly swaying down. | yes |
| `floating_hearts` | Floating Hearts | Lilac and pink with tiny hearts rising and sparkles twinkling. | yes |
| `matcha_tea` | Matcha Tea | Calm matcha greens, tea leaves and steam curls, serif-italic titles. | no |
| `lavender_moon` | Lavender Moon | Lavender dusk with a crescent moon, tiny stars and dotted constellations. | no |

Decisions:
- Decorations draw on Daily, Week, Month, Notes list and Search. Settings, Note editor, Lock screen and
  Debug log get theme colours/shapes but no doodles (picker legibility; editor focus; lock unchanged).
- Picker lives INSIDE the existing Settings "Theme" section, below the existing Light/Dark/System radios.
  No new section, no global animations switch.
- Per-theme Animated/Off persisted as a set of ids that are OFF (`theme_static_ids`); absent = Animated.
- Effective animate = theme.animated AND id not in static set AND `ValueAnimator.areAnimatorsEnabled()`
  AND lifecycle >= STARTED. Non-animated themes never animate and show no toggle.
- Fonts: no new font files/deps. Titles use existing `ScriptFont` (Dancing Script) or `FontFamily.Serif`.
- Mood day-square colours stay the default ones in every theme (semantic, already contrast-checked).
- HARD contrast rule: every text colour is paired with its own container; S4 adds a unit test (>= 4.5:1
  for body pairs, >= 3:1 for display-size title gradient stops vs background).

## File list
New: `ui/theme/ThemeBackground.kt` (engine), `ui/theme/ThemesStatic.kt` (washi, sunny, matcha, lavender),
`ui/theme/ThemesAnimated.kt` (cloud, petal, hearts), tests `data/SettingsRepositoryThemeTest.kt`,
`ui/theme/ThemeContrastTest.kt`.
Edited: `ui/theme/Theme.kt`, `data/SettingsRepository.kt`, `MainActivity.kt`, `ui/settings/SettingsState.kt`,
`SettingsViewModel.kt`, `SettingsScreen.kt`, `ui/daily/DailyComponents.kt` (SectionCard look only),
`ui/daily/DailyPageContent.kt`, `ui/week/WeekPageContent.kt`, `ui/month/MonthPageContent.kt`,
`ui/notes/NotesListScreen.kt`, `ui/search/SearchScreen.kt` (root Surface -> `ThemedPage`, 1 call each),
`res/values/strings.xml`, `test/.../ui/ScreenshotTest.kt`. Not touched: PeriodHeader.kt, PeriodCards.kt,
Type.kt (title gradient arrives through Typography, so PeriodHeader needs no edit).
Per slice: <= 3 Kotlin main-source files, plus strings.xml and test files where listed.

## Core types (S1, in Theme.kt)
```kotlin
enum class HeaderStyle { PLAIN, WASHI, HIGHLIGHTER, RIBBON }
fun interface DecorationFrame { fun DrawScope.draw(phase: Float) }       // phase in [0,1)
fun interface ThemeDecoration { fun build(size: Size, density: Density, dark: Boolean): DecorationFrame }
@Immutable class ThemeMode(val scheme: ColorScheme, val extras: CommentariiColors, val titleBrush: Brush?)
@Immutable class AppTheme(
    val id: String, @StringRes val nameRes: Int, @StringRes val descriptionRes: Int,
    val light: ThemeMode, val dark: ThemeMode,
    val titleFont: FontFamily = ScriptFont,
    val tabShape: Shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    val cardShape: Shape = RoundedCornerShape(0.dp, 16.dp, 16.dp, 16.dp),
    val headerStyle: HeaderStyle = HeaderStyle.PLAIN,
    val decoration: ThemeDecoration? = null,
    val animated: Boolean = false, val periodMillis: Int = 0, val staticPhase: Float = 0f,
)
val LocalAppTheme = staticCompositionLocalOf { AppThemes.Default }
val LocalThemeAnimate = staticCompositionLocalOf { false }     // user-level intent only
object AppThemes { const val DEFAULT_ID = "rose_cream"; val Default: AppTheme; val all: List<AppTheme>
    fun byId(id: String?): AppTheme = all.firstOrNull { it.id == id } ?: Default }
fun systemAnimationsEnabled(): Boolean = ValueAnimator.areAnimatorsEnabled()
fun themeTypography(font: FontFamily, titleBrush: Brush?): Typography  // as AppTypography; brush on display* only
fun paletteMode(p: Palette, dark: Boolean): ThemeMode   // builds full ColorScheme from a Palette (below)
data class Palette(val bg: Long, val onBg: Long, val primary: Long, val onPrimary: Long, val card: Long,
    val outlineVariant: Long, val tabs: List<Long>, val onTab: Long, val title: List<Long>)
```
`CommentariiColors` gains `val tabs: List<Color> = listOf(tab)` (default keeps Rose & Cream identical).
`paletteMode`: primaryContainer = tabs[0], secondaryContainer/sage = tabs[1 % n], tertiaryContainer = tabs[2 % n],
all on*Container = onTab; background = surface = surfaceBright = bg; surfaceContainerLowest = card;
Low/Container/High/Highest = lerp(bg, onBg, .03/.05/.08/.11); surfaceVariant = lerp(.07);
onSurfaceVariant = lerp(onBg, bg, .25); outline = lerp(onBg, bg, .45); error* = Rose & Cream values;
mood/onMood = Rose & Cream Light/DarkExtras values. Rose & Cream keeps its hand-written schemes,
`AppTypography` instance and null decoration.

## Palettes (hex; light / dark)
| id | bg | onBg | primary/onPrimary | card | outlineVar | tabs (onTab) | title gradient |
|---|---|---|---|---|---|---|---|
| washi L | FFF9F4 | 2E2633 | A0527A/FFFFFF | FFFFFF | E6D9E6 | F8C8D8 D9CCF2 C8EBD9 FBEBB0 C6E2F7 (2E2633) | C2477A 7E5BB5 2E8A66 9A6A0A 3B7BB8 |
| washi D | 221E26 | F1EAF2 | F2B6CF/3A1A2A | 2C2731 | 4A4250 | 7A4A5E 5A4E7A 3F6B58 7A6A32 3E5E7A (FFF6FA) | F7B9D0 CDBDF5 AEE3C8 F6E3A0 B5D8F5 |
| sunny L | FDF8EC | 33291F | B5582E/FFFFFF | FFFDF6 | EADFC8 | FFD6A5 FDFFB6 CAFFBF A0C4FF BDB2FF FFC6FF (33291F) | C4513A 9C6F00 3E8E4F 3A6FB8 7B5AB8 |
| sunny D | 24201A | F3EBDD | F4B98E/3D2410 | 2E2921 | 4B4336 | 8A5A3A 6E6C30 4A7A44 3E5A86 5E5290 7E4A7E (FFF8EE) | FFC9A0 F3E58A B6E8A8 A8C8F5 CBBEF5 |
| cloud L | F3F8FD | 1F2A36 | 3F6E9E/FFFFFF | FFFFFF | D5E2EF | CFE4F7 E3DAF5 FDE2E4 (1F2A36) | 3F6E9E 7A5BB0 |
| cloud D | 182230 | E7EEF6 | A9CBEE/12304E | 202C3B | 3A4A5C | 3A5A7A 4E4670 6E4A56 (F4F8FC) | A9CBEE D2C2F2 |
| petal L | FFF7F8 | 3A2328 | B04A63/FFFFFF | FFFFFF | F0D8DD | F9CFD8 FBE3E8 E9D5C9 (3A2328) | B04A63 9A5A3A |
| petal D | 231A1C | F6E8EB | F3B3C2/4A1A28 | 2D2225 | 4E3C40 | 85505E 6E4E55 6A5446 (FFF4F6) | F3B3C2 F0CDB8 |
| hearts L | FBF7FF | 2D2438 | 8656B0/FFFFFF | FFFFFF | E4DAF0 | E6D6F7 F8D3E6 D3E9F7 (2D2438) | 8656B0 C2477A |
| hearts D | 1F1A27 | EFE8F7 | CDB0EE/34204E | 29222F | 463C52 | 5C4A7A 7A4A64 3E5E78 (FAF5FF) | CDB0EE F7B9D0 |
| matcha L | F6F7EF | 26301F | 5C7A3A/FFFFFF | FFFFFA | DCE2CC | D4E4B8 EDE3C8 C9DCC9 (26301F) | none (onBg) |
| matcha D | 1B2017 | E8EEDD | B7D38F/24361A | 242B1F | 3F4A36 | 4E6436 6A5E40 3E5A44 (F5FAEE) | none |
| lavender L | F5F2FA | 272235 | 6A5AA8/FFFFFF | FFFFFF | DDD6EC | DCD3F2 C9D6F2 F2D3E4 (272235) | 6A5AA8 3F5A9E |
| lavender D | 16141F | ECE8F6 | C3B6F2/2A2058 | 201D2C | 3D3850 | 4A3F78 34486E 6A4060 (F7F4FF) | C3B6F2 B5C8F5 |

Shapes/styles: washi tab RoundedCornerShape(12,12,0,0), card (0,20,20,20), WASHI; sunny tab (6dp all),
card (0,12,12,12), HIGHLIGHTER; cloud/petal/hearts tab (18,18,0,0), card (0,22,22,22), PLAIN;
matcha tab (4,4,0,0), card (0,8,8,8), RIBBON, titleFont Serif italic; lavender default shapes, RIBBON.
Section tab colour = `tabs[abs(title.hashCode()) % tabs.size]` (stable per section; size 1 for Rose & Cream).

## SLICES

### S1 - Core spec, CompositionLocal, persistence (Theme.kt, SettingsRepository.kt, MainActivity.kt)
- Theme.kt: add the core types above; `AppThemes.all = listOf(Default)` for now;
  `fun CommentariiTheme(dark: Boolean, theme: AppTheme = AppThemes.Default, animate: Boolean = false, content)`
  provides LocalAppTheme, LocalThemeAnimate, LocalCommentariiColors = mode.extras, MaterialTheme(mode.scheme,
  if (theme === Default) AppTypography else themeTypography(...)). DarkMode overload forwards unchanged.
- SettingsRepository: keys `theme_id` (string), `theme_static_ids` (stringSet);
  `val themeId: Flow<String>` (default "rose_cream"), `val staticThemeIds: Flow<Set<String>>` (default empty),
  `suspend fun setThemeId(id: String)`, `suspend fun setThemeAnimated(id: String, animated: Boolean)`
  (removes id from the set when true, adds when false).
- MainActivity: collect `themeId` as `String?` (initial null) and `staticThemeIds`; extend the existing
  loading gate to `lockEnabled == null || themeId == null` (no default-theme flash); call
  `CommentariiTheme(dark, theme = AppThemes.byId(themeId), animate = theme.animated && theme.id !in static)`.
  FLAG_SECURE, lock, edge-to-edge code untouched.
- strings.xml: `theme_<id>_name` / `theme_<id>_desc` for all 8 ids (text from the table above).
- Test `SettingsRepositoryThemeTest` (pattern of ExportImportRoundTripTest): default id, set/get id, toggle
  animated off/on per id independently.
- Accept: build OK; existing Roborazzi images identical (operator runs compare); unit test green.

### S2 - Settings picker with per-animated-theme Animated/Off (SettingsState, SettingsViewModel, SettingsScreen)
- State: `themeId: String = AppThemes.DEFAULT_ID`, `staticThemeIds: Set<String> = emptySet()`.
  Callbacks: `onThemeSelect: (String) -> Unit = {}`, `onThemeAnimated: (String, Boolean) -> Unit = {}`.
- ViewModel: `private val theme = combine(settings.themeId, settings.staticThemeIds, ::Pair)`; fold into
  `state` combine (4 flows); callbacks call `setThemeId` / `setThemeAnimated`.
- Screen: inside the existing Theme SectionCard, after the radios: `HorizontalDivider()`, then
  `AppThemes.all.forEach { ThemeRow(it, selected, animatedOn, onSelect, onAnimated) }`.
  `@Composable private fun ThemeRow(theme: AppTheme, selected: Boolean, animatedOn: Boolean,
  onSelect: () -> Unit, onAnimated: (Boolean) -> Unit)`: selectable Row (Role.RadioButton): RadioButton,
  `ThemeSwatch(theme)` (24dp circle bg + 3 overlapping 12dp tab dots, light or dark mode per current
  `isSystemInDarkTheme`/state), name (bodyLarge) + description (bodySmall). If `theme.animated`: below it a
  `SingleChoiceSegmentedButtonRow` with "Animated" / "Off"; if `!systemAnimationsEnabled()` add
  bodySmall hint "System animations are off, so this stays still." Non-animated rows show no toggle.
- strings: `settings_theme_choose`, `settings_theme_animated`, `settings_theme_static`, `settings_theme_system_anim_off`.
- Accept: picker changes theme live; toggle persists across restart; no other Settings section changed.

### S3 - Decoration/animation engine (ThemeBackground.kt new, DailyComponents.kt, DailyPageContent.kt)
- `@Composable fun ThemedPage(modifier: Modifier = Modifier, content: @Composable () -> Unit)`: same
  Surface(fillMaxSize, background, onBackground) as today; inside: `ThemeBackground(Modifier.fillMaxSize())`
  then `content()`. When `decoration == null` it emits only the original Surface (Rose & Cream identical).
- `@Composable fun ThemeBackground(modifier: Modifier)`: one per screen; never inside lazy items.
  `val lifecycle = LocalLifecycleOwner.current.lifecycle; val state by lifecycle.currentStateAsState()`;
  `val systemOn = remember(state) { systemAnimationsEnabled() }` (re-read when lifecycle changes);
  `val run = LocalThemeAnimate.current && theme.animated && systemOn && state.isAtLeast(STARTED)`.
  If run: `val t = rememberInfiniteTransition("themeBg").animateFloat(0f, 1f,
  infiniteRepeatable(tween(theme.periodMillis, easing = LinearEasing)), "phase")` (State, not delegated);
  else `t = remember { mutableFloatStateOf(theme.staticPhase) }`.
  `Spacer(modifier.drawWithCache { val f = deco.build(size, this, dark); onDrawBehind { with(f) { draw(t.value) } } })`
  -> phase read only in draw: zero recomposition per frame; paths built once per size.
- Painter rules (for S4/S5): positions in fractions of size; element size = (minDimension * k)
  clamped 16..96dp; element count = base * clamp(width/412dp, 1, 2) so the Fold inner screen is not sparse;
  alpha light 0.35-0.55, dark 0.16-0.25; stroke/fill only with Path/Oval/Arc; no bitmaps, no text.
- DailyComponents.SectionCard: read `LocalAppTheme.current`; tab colour per hash rule; tab shape and card
  shape from spec; same paddings, sizes, border. Header styles drawn with `Modifier.drawBehind` on the tab
  (no size change): WASHI = 45deg stripes of onTab @0.12 + zigzag 2dp ends; HIGHLIGHTER = tab Surface
  colour Transparent, swash = rounded rect skewed -4deg in tab colour @0.85 inset 2dp; RIBBON = 6dp
  triangular notch cut via GenericShape at the tab's right end.
- DailyPageContent: root `Surface(...)` -> `ThemedPage(modifier.fillMaxSize()) {`.
- Accept: Rose & Cream screenshots identical; with a temp debug theme, Layout Inspector shows no
  recomposition count growth while animating; animation stops when app is backgrounded.

### S4 - Static themes (ThemesStatic.kt new, Theme.kt, WeekPageContent.kt)
- `internal val WashiPlanner, SunnyJournal, MatchaTea, LavenderMoon: AppTheme` built with `paletteMode`.
  Painters (private `ThemeDecoration`s): washi = hearts, 5-point stars, cloud puffs, round-ear bear face in
  bottom-right corner, pot + 3-leaf plant bottom-left, 2 tilted washi strips top corners; sunny = sun
  (circle + 10 rays + arc smile + dot eyes) top-right, cloud, leaf sprigs left edge, candle (rect + teardrop
  flame), mug (rounded rect + handle arc + steam S-curves), hearts; matcha = tea-leaf ovals with midribs,
  cup outline, steam curls; lavender = crescent (two-circle difference path), 4-point stars, dotted
  constellation lines.
- Theme.kt: `AppThemes.all = listOf(Default, WashiPlanner, SunnyJournal, MatchaTea, LavenderMoon)`.
- WeekPageContent: root Surface -> ThemedPage.
- Test `ThemeContrastTest` (pure JVM, Compose `Color.luminance()`): for every theme x mode: onBg/bg,
  onCard/card, onTab/each tab, onPrimary/primary >= 4.5; each title gradient stop vs bg >= 3.0;
  ids unique; `byId("nope") == Default`; <= 3 animated themes.
- Accept: contrast test green; no dark-on-dark/light-on-light in Week light+dark (operator screenshots).

### S5 - Animated themes (ThemesAnimated.kt new, Theme.kt, MonthPageContent.kt)
- `CloudDrift` (period 60000ms, staticPhase .3f): 5 clouds (3-4 overlapping circles) on 3 depth rows,
  x = frac(x0 + phase * speedRow) * (w + cw) - cw (wraps); sun at (0.82w, 0.10h) bobbing
  y += sin(2pi*phase*3) * 4dp, rays rotate 0..15deg; dark = clouds white @0.10, sun @0.20.
- `PetalFall` (24000ms, .4f): 14 petals (teardrop path), each with seed (x0, delay, size); y = frac(phase
  + delay) * (h + s); x = x0*w + sin(2pi*(phase*2+delay)) * 18dp; rotate by 360*frac(phase+delay).
- `FloatingHearts` (20000ms, .25f): 10 hearts rising (reverse of petals, slower sway), 12 sparkles
  (4-point star) with alpha 0.2 + 0.3*(0.5+0.5*sin(2pi*(phase*4+seed))).
- All seeds are fixed constants (deterministic); `animated = true`.
- Theme.kt: append the 3 to `AppThemes.all` (8 total). MonthPageContent: root Surface -> ThemedPage.
- Accept: contrast test still green; on device with Animated each animates smoothly while scrolling
  Daily (GPU profile bars under 16ms on S24 Ultra), Off and system "Remove animations" both freeze it.

### S6 - Roborazzi + remaining pages (ScreenshotTest.kt, NotesListScreen.kt, SearchScreen.kt)
- NotesListScreen, SearchScreen: root Surface -> ThemedPage (only that call changes).
- ScreenshotTest: `private fun themedDaily(id: String) { RuntimeEnvironment.setQualifiers(compact);
  rule.setContent { CommentariiTheme(dark = false, theme = AppThemes.byId(id), animate = false) {
  DailyPageContent(sampleDailyUiState(), DailyActions(), WindowWidthSizeClass.Compact) } };
  rule.onRoot().captureRoboImage("build/outputs/roborazzi/daily_theme_$id.png") }`
  and one `@Test` per id (8): `dailyThemeRoseCream()` ... `dailyThemeLavenderMoon()`.
  animate = false => no infinite transition, staticPhase frames, deterministic.
- Accept: `recordRoborazziDebug` (operator) produces 8 new PNGs; `daily_theme_rose_cream` matches
  `daily_light_compact`; all prior images unchanged; screenshot-reviewer (Haiku) reports per theme:
  same sections and order as default, readable text, doodles only behind/between cards.

## Global acceptance
- No section/layout/string-content change on Today/Daily, Week, Month, Notes, Search, Settings
  (Settings only gains the picker rows inside the existing Theme section).
- No new dependencies, permissions, network, or backup fields. FLAG_SECURE and app lock code untouched.
- Opus review per slice on a pre-generated diff of the 1-3 key files; builds run by the operator.
