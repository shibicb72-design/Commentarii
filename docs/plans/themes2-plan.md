# Themes v2 REDO - plan (Opus, branch `opus/themes`)

v1 verdict: too plain, weak animations, cheap fonts/headers. v2 keeps the v1 engine, ids, picker and
persistence and redoes only the LOOK of the 7 non-default themes: dense sticker planner + cozy cream journal.

## Hard rules (repeat in every Sonnet brief)
1. Layout, sections, order, strings and content of all pages stay identical; only colours, fonts, label/card
   styling, glyphs, doodles, textures, animation change. No Grateful/Self Care. Fonts keep the base
   `lineHeight`; decorations draw outside bounds (drawBehind/drawWithContent), never through layout.
2. No global animations switch. Only cloud_drift/petal_fall/floating_hearts animate, gated by per-theme
   Animated/Off, `ValueAnimator.areAnimatorsEnabled()` and lifecycle >= STARTED.
3. `rose_cream` stays default and pixel-identical: new code starts `val art = currentArt() ?: <original>`.
4. Vector/Canvas art only; fonts only from `res/font`; no deps, no network; FLAG_SECURE/lock/privacy untouched.
5. Paths built once (unit-space `by lazy` or `drawWithCache`); one `withFrameNanos` loop per screen writing a
   float state read ONLY in draw; <= 40 particles per screen; no per-frame allocation.
6. Doodles never under text above alpha 0.18; `ThemeContrastTest` stays green. 7. Ids never change.

## Shared look vocabulary
- **Doodles**: 100x100 unit box, Bezier `cubicTo` only. Layers: (a) shadow = silhouette offset (2,3) ink
  @0.10; (b) optional sticker die-cut = silhouette stroked 7u white; (c) fill; (d) shade `lerp(fill,ink,.14)`
  clipped lower right; (e) highlight white @0.65 upper left; (f) 2.2u round-cap line `lerp(fill,ink,.55)`;
  (g) face: 3.4u oval eyes + 1.1u white catchlight, 2u smile arc, blush `F7A1B5` @0.55.
- **Section labels** keep titleLarge `lineHeight` 28sp and the 16/4dp padding; they show
  `title.uppercase()` with `clearAndSetSemantics { text = AnnotatedString(title) }` (semantics/tests see the
  original string); non-ROSE labels draw an 11dp trailing glyph 8dp inside the end padding in accent-deep.
  - `PILL`: solid accent capsule, 1dp `lerp(accent, ink, .25)` border, 14sp ink, letterSpacing 0.14em;
    `zIndex(1f)` + `offset(12.dp, 7.dp)` overlaps the card top border like a tab sticker; card 18dp corners.
  - `SWASH`: no fill; highlighter brush stroke behind the text (Bezier blob, tapered ragged ends, -2deg,
    accent @0.80 + a 3dp-narrower pass @0.45 for marker grain, 6dp past the text ends); 15sp, 0.12em; card 16dp.
  - `STAMP`: rice-paper capsule, 1.5dp dashed (6/4dp) accent-deep border ("stitched tag"), PILL offsets; card 14dp.
  - `ROSE`: the v1 code path, unchanged.
- **accent-deep** = `lerp(accent, ink, .45)` for non-text glyphs (>= 3:1 vs card).
- **Card**: border 1.5dp `lerp(accent_i, ink, .18)` (Sunny: beige D9C9A8); soft shadow = rounded rects
  offset +1/+2/+3dp, ink @0.05/0.035/0.02. Corner sticker 40dp centred at (w-26dp, -12dp), ±8deg by index:
  it sits in the empty tab row and 8dp top padding, never over rows. Washi tape 52x16dp at the bottom-right
  corner, -32deg, half outside, on cards with `index % 3 == 1` (tape themes). Index =
  `abs(title.hashCode() + cardSalt) % n`; salt chosen so Daily's 6 titles get 6 distinct stickers.
- **Paper** (one `drawPoints` / line batch from precomputed arrays; alpha light 0.35, dark 0.18):
  `DOT_GRID` 1.2dp dots, 16dp pitch; `LINED` 0.8dp lines, 28dp pitch + 1dp margin line at x=24dp in
  accent 2; `SPECKLE` 220 seeded 0.8-1.8dp specks; `STARS` 90 tiny 4-point stars + specks.
- **Dark mode**: `accentDark_i = lerp(accent_i, bgDark, .60)`; label ink = v1 dark `onTab`; title colours =
  v1 dark `title` stops, else `lerp(c, White, .45)`. Doodle alpha x0.6, texture x0.5. The contrast test
  decides; Sonnet may tweak lerp factors.
- **Device sizing**: positions are fractions of layer size; element size = `minDim*k` clamped 16..110dp;
  `widthScale = clamp(w/400dp, 1, 1.25)` (S24U 384dp, Fold cover 373dp, Fold inner 673dp), so 40 caps hold.

## Themes (light hex; ink is also onTab/onBg)
| id | bg / card | paper | 5 accents | ink |
|---|---|---|---|---|
| washi_planner | FFFFFF / FFFFFF | DOT_GRID E4DEEA + dashed mint page border 9ED9C3 | F9C9D6 DCCFF5 C6EBDA FCEBA8 C7E3F8 | 2E2633 |
| sunny_journal | FDF6E7 / FFFDF7 | SPECKLE C9B89A | FFD3B0 FFF0A0 CDEFC0 BFD9FF DCCBFF (+FFC9DE) | 33291F |
| cloud_drift | F2F8FE (top gradient DCEEFC) / FFFFFF | none (sky) | CFE5F8 E3DAF6 FDE1E6 D8F1E4 FFF1C7 | 1F2A36 |
| petal_fall | FFF6F7 / FFFFFF | SPECKLE F2D6DC | F9CFD8 FBE3E8 E9D5C9 E5D9F2 D9EBDD | 3A2328 |
| floating_hearts | FBF6FF / FFFFFF | DOT_GRID EADFF5 (20dp) | E6D6F7 F8D3E6 D3E9F7 FDE7C8 D8F2E2 | 2D2438 |
| matcha_tea | F6F7EE / FFFFFA | LINED DDE3CC | D4E4B8 EDE3C8 C9DCC9 F2D6C9 E8E0F0 | 26301F |
| lavender_moon | F4F0FB / FFFFFF | STARS C9BEE8 | DCD3F2 C9D6F2 F2D3E4 D6EEF0 F6E7C1 | 272235 |

Material `Palette`: bg, onBg = ink, card, tabs = accents, onTab = ink, `title = emptyList()` (no brush;
ThemedTitle colours instead). primary/onPrimary and the dark palettes come from v1.

| id | title font + colour rule (+ trailing glyph) | label | glyph | bullet / check | tape |
|---|---|---|---|---|---|
| washi_planner | Sniglet ExtraBold, PER_WORD 2E2633, E06C66, 2E9C7A; sparkle | PILL | HEART | CIRCLE/HEART/STAR cycle; HEART | stripes |
| sunny_journal | Sniglet ExtraBold, PER_LETTER_OUTLINED fills F59AB5 F7CF4A 8FD48A 8EB8F5 B79CF2, 1.6dp outline 33291F; heart | SWASH | HEART | HEART/STAR/FLOWER multicolour; FLOWER | dots |
| cloud_drift | Pacifico, PER_WORD 3F6E9E, 7A5BB0; sparkle | PILL | SPARKLE | CLOUD; CIRCLE sky fill | no |
| petal_fall | Pacifico, PER_WORD B04A63, A0603F; flower | SWASH | FLOWER | FLOWER; FLOWER | no |
| floating_hearts | Pacifico, PER_LETTER alt 8656B0, C2477A; heart | PILL | HEART | HEART; HEART | no |
| matcha_tea | Sniglet ExtraBold, PER_WORD 4F6B2E, 8A5A3A; leaf | STAMP | LEAF | LEAF; CIRCLE matcha fill | gingham |
| lavender_moon | Pacifico, PER_LETTER_GRADIENT 6A5AA8 to 3F5A9E; star | STAMP dotted | STAR | STAR; STAR | no |

**Corner stickers (cycled by card index) and background scene**
- **washi_planner** - stickers: CROWN, BOBA, TULIP_JAR, CALENDAR, BAG_HEART, CACTUS, SPEECH_HEART, PEN.
  Scene: dashed mint page border inset 6dp (7/5dp, 1.5dp); BEAR_PEEK over STICKY_NOTE bottom-right;
  PLANT_POT bottom-left; strips pink top-left (-18deg), mint mid-right (24deg), butter lower-left;
  scatter 6 hearts, 6 stars, 4 daisies, SPEECH_HEART, 6 sparkles.
- **sunny_journal** - stickers: SUN_FACE, MUG_HEART, CANDLE_JAR, CAMERA, NOTE_CARD, LEAF_SPRIG, DAISY,
  PLANT_POT. Scene: SUN_FACE + CLOUD_PUFF top-right; taped NOTE_CARD right gutter; 3 LEAF_SPRIGs left edge;
  CANDLE_JAR, CAMERA, MUG_HEART bottom-left; PLANT_POT bottom-right; 10 multicolour hearts/stars/flowers,
  8 sparkles; peach + lilac strips.
- **cloud_drift** - stickers: CLOUD_FACE, SUN_FACE, RAINBOW, RAINDROP, KITE, UMBRELLA; static: sky, RAINBOW @0.30.
- **petal_fall** - stickers: BLOSSOM, BRANCH, BUD, PETAL, DAISY, BOW. Static layer: speckle, BRANCH top-left
  (sways when animated), 3 BLOSSOMs bottom-right.
- **floating_hearts** - stickers: HEART_FACE, BALLOON, ENVELOPE, BOW, SPARKLE_CLUSTER, BEAR_PEEK holding a
  heart. Static layer: dots, 2 BALLOONs in the right gutter, ENVELOPE bottom-left.
- **matcha_tea** - stickers: TEACUP_FACE, TEA_LEAF, WHISK, DANGO, TEAPOT, BAMBOO. Scene: TEACUP_FACE +
  WHISK bottom-right with static STEAM; TEAPOT bottom-left; BAMBOO right edge; 8 TEA_LEAFs, DANGO; 2 sage
  gingham strips.
- **lavender_moon** - stickers: MOON_FACE, STAR_FACE, PLANET, LAVENDER, CANDLE_JAR, CLOUD_FACE (sleepy).
  Scene: big sleeping MOON_FACE top-right with halo @0.25; 3 dotted constellations; LAVENDER sprigs
  bottom-left; PLANET; 12 rounded stars; CLOUD_PUFF.

**Animated scenes (S7)** - t in seconds, fixed seeds; counts are base (max at widthScale 1.25):
- **cloud_drift** (19 base, ~24 max; staticTimeSec 7.5): parallax clouds wrap horizontally with ±3dp
  `sin(t*.6+seed)` bob - far 4 CLOUD_PUFF (0.5x, @0.45, 6dp/s), mid 3 (0.8x, @0.65, 11dp/s), near 2 sleepy
  CLOUD_FACE (1.2x, @0.85, 18dp/s, blink every 5s). SUN_FACE at (0.80w, 0.09h): rays rotate 12deg/s, bob
  4dp, cheeks pulse. 8 twinkling sparkles; 2 tiny birds glide a sine path every 14s.
- **petal_fall** (24 base, ~30 max; staticTimeSec 4.2): 14 PETALs fall 26-44dp/s, sway
  `sin(t*.9+seed)*22dp`, spin 40-90deg/s, 3D flip scaleX = `cos(t*1.3+seed)` (colour shifts to shade),
  fade in over the top 6% and out over the bottom 12%; 4 BLOSSOMs at 0.6x speed, spin 25deg/s;
  6 sparkles; BRANCH sways ±2.5deg (`sin(t*.5)`).
- **floating_hearts** (28 base, ~35 max; staticTimeSec 3.0): 12 hearts rise 18-34dp/s, wobble
  `sin(t*1.4+seed)*14dp`, tilt ±10deg in phase, pulse `1+.06*sin(t*3+seed)`, alpha 0 to 1 over the bottom
  10% and 1 to 0 over the top 25%, with highlight; 12 sparkles; 4 radial-gradient bokeh orbs @0.18;
  BALLOONs sway ±3deg.

## SLICES
Each slice is a fresh `claude -p --model sonnet --max-budget-usd 1.50` session: <= 30 turns, <= 3
main-source files (tests extra), Read by range, no Gradle (operator builds). New files go in `ui/theme/`.

### S1 - Fonts, art spec, glyphs, ThemedTitle, ThemedSectionLabel (ThemeArt.kt new, DailyComponents.kt, DailyHeader.kt)
```kotlin
object ArtFonts { val PatrickHand = FontFamily(Font(R.font.patrick_hand)); val Pacifico = FontFamily(Font(R.font.pacifico))
    val Sniglet = FontFamily(Font(R.font.sniglet_extrabold, FontWeight.ExtraBold)); val Dancing = ScriptFont }
enum class LabelStyle { ROSE, PILL, SWASH, STAMP }
enum class TitleColoring { PER_WORD, PER_LETTER, PER_LETTER_GRADIENT, PER_LETTER_OUTLINED }
enum class Glyph { DOT, CIRCLE, HEART, STAR, SPARKLE, FLOWER, LEAF, CLOUD, MOON, DIAMOND }
enum class Paper { NONE, DOT_GRID, LINED, SPECKLE, STARS }
@Immutable class ArtColors(val accents: List<Color>, val ink: Color, val title: List<Color>, val outline: Color?,
    val paper: Color, val decoAlpha: Float)
@Immutable class ThemeArt(val titleFont: FontFamily, val titleColoring: TitleColoring, val titleTrail: Glyph?,
    val labelStyle: LabelStyle, val labelFont: FontFamily, val labelTrail: Glyph?, val bullets: List<Glyph>,
    val check: Glyph, val paper: Paper, val tape: Boolean, val cardSalt: Int, val light: ArtColors, val dark: ArtColors)
fun themeArt(id: String): ThemeArt?                       // null for rose_cream; values from the tables
@Composable fun currentArt(): Pair<ThemeArt, ArtColors>?  // LocalAppTheme + background luminance
fun DrawScope.drawGlyph(g: Glyph, center: Offset, size: Float, color: Color, filled: Boolean = true)
@Composable fun ThemedTitle(text: String, style: TextStyle, modifier: Modifier = Modifier)
@Composable fun ThemedSectionLabel(title: String, accent: Color, modifier: Modifier = Modifier)
fun accentDeep(accent: Color, ink: Color): Color = lerp(accent, ink, .45f)
```
- Glyphs: Bezier unit paths `by lazy`, white @0.55 highlight when filled. ThemedTitle: `buildAnnotatedString`,
  one SpanStyle colour per word/letter (whitespace/punctuation don't advance), `style.copy(fontFamily =
  art.titleFont, brush = null, lineHeight = style.lineHeight)`; OUTLINED = `Box { Text(fill); Text(same,
  drawStyle = Stroke(1.6dp, join = Round), color = outline, Modifier.clearAndSetSemantics {}) }`; trailing
  glyph in `drawWithContent` after the last line's right edge (`onTextLayout` into a draw-read state),
  skipped if it would overflow. ThemedSectionLabel: PILL/SWASH/STAMP per vocabulary, swash in `drawWithCache`.
- SectionCard: non-ROSE uses ThemedSectionLabel + card shape/border; Rose path byte-identical.
  DailyHeader.kt:69 `Text(title, displaySmall, ...)` becomes `ThemedTitle(title, displaySmall, Modifier.weight(1f))`.
- Test: extend `ThemeContrastTest`: ink vs each accent >= 4.5 (light+dark); title colours vs bg >= 3.0
  (outline >= 4.5 when OUTLINED); `themeArt("rose_cream") == null`, the 7 others non-null.
- Accept: build OK (operator); rose_cream and non-theme Roborazzi images unchanged; text-based UI tests pass.

### S2 - Doodle library part 1, stickers (DoodleKit.kt, StickersPlanner.kt, StickersCozy.kt; all new)
```kotlin
@Immutable class DoodleInk(val fill: Color, val second: Color, val ink: Color, val blush: Color = Color(0xFFF7A1B5), val alpha: Float = 1f)
enum class DoodleId { BEAR_PEEK, STICKY_NOTE, PLANT_POT, CROWN, FLAG, BUBBLE_EXCLAIM, CALENDAR, CART, BAG_HEART,
  FORK_SPOON, BOBA, DROP_FACE, SMILEY, TULIP_JAR, CACTUS, PEN, SPEECH_HEART, MUG_HEART, CANDLE_JAR, CAMERA,
  NOTE_CARD, ENVELOPE, BOW, /* part 2 */ SUN_FACE, CLOUD_FACE, CLOUD_PUFF, RAINBOW, RAINDROP, KITE, UMBRELLA,
  MOON_FACE, STAR_FACE, PLANET, SPARKLE_CLUSTER, HEART_FACE, BALLOON, BLOSSOM, BRANCH, PETAL, BUD, DAISY,
  LEAF_SPRIG, TEA_LEAF, TEACUP_FACE, WHISK, TEAPOT, DANGO, BAMBOO, LAVENDER, STEAM }
fun DrawScope.drawDoodle(id: DoodleId, center: Offset, size: Float, ink: DoodleInk, t: Float = 0f,
    rotation: Float = 0f, sticker: Boolean = false)  // translate+rotate+scale(size/100), dispatch; part 2: `else -> Unit`
fun DrawScope.drawWashiTape(center: Offset, length: Float, thickness: Float, angle: Float, color: Color,
    pattern: Int /*0 stripes,1 dots,2 gingham*/, ink: Color)  // torn zigzag ends, fill @0.82, pattern @0.14
internal fun DrawScope.drawFace(cx: Float, cy: Float, s: Float, ink: DoodleInk, sleepy: Boolean = false)
internal fun DrawScope.drawLayered(silhouette: Path, ink: DoodleInk, sticker: Boolean)  // layers a-f
```
- StickersPlanner.kt = BEAR_PEEK..SPEECH_HEART; StickersCozy.kt = MUG_HEART..BOW; each is `internal fun
  DrawScope.drawX(ink: DoodleInk)` in the 0..100 box, paths in a private object `by lazy`. Bear: ears with
  inner ears, muzzle, face, paws over a note edge; boba: lid dome, straw, pearls, face; tulip jar: glass
  with highlight stripe and 3 tulips.
- Test `DoodleKitTest` (JVM): each part-1 id draws without throwing on a `CanvasDrawScope` over
  `ImageBitmap(64,64)`.
- Accept: compiles; no `Path()`/allocation in draw bodies.

### S3 - Doodle library part 2, sky + garden (DoodlesSky.kt new, DoodlesGarden.kt new, DoodleKit.kt)
- `DoodlesSky.kt`: `drawSun(ink, rayAngle)` (12 rounded teardrop rays, face, blush); `drawCloudFace(ink,
  blink)`; `drawCloudPuff(ink)` (5-lobe Bezier, lower shade band); rainbow (4 pastel arcs + cloud feet);
  raindrop with face; kite with bow tail; umbrella; `drawMoonFace(ink, sleepy)` (crescent, closed eyes,
  nightcap tip); star with face; ringed planet; sparkle cluster; heart with face; heart balloon + curly string.
- `DoodlesGarden.kt`: 5-petal notched blossom with pink centre dots; branch (Bezier twig, 3 blossoms,
  2 buds); notched-teardrop petal; bud; daisy; leaf sprig (stem plus 5 leaves with midribs); tea leaf;
  teacup with face and matcha ellipse; chasen whisk; teapot; dango (3 pastel balls with faces); bamboo;
  lavender; `drawSteam(ink, t)` (3 S-curves, phase from t).
- DoodleKit.kt: replace `else -> Unit` with the part-2 cases. `t` drives SUN_FACE rays, CLOUD_FACE blink
  and STEAM.
- Test: extend `DoodleKitTest` to all ids. Accept: as S2. (S2/S3 are pure new code: no visual change yet.)

### S4 - Themed card, sticker, tape, bullets, checkbox (ThemeCard.kt new, DailyComponents.kt, DailySections.kt)
```kotlin
@Immutable class CardDecor(val doodle: DoodleId, val accent: Color, val ink: DoodleInk, val tape: Boolean,
    val tapePattern: Int, val sticker: Boolean, val shadowInk: Color)
fun cornerDoodles(themeId: String): List<DoodleId>                 // lists above
fun cardDecor(themeId: String, art: ThemeArt, colors: ArtColors, title: String): CardDecor
fun Modifier.themedCard(decor: CardDecor, shape: Shape): Modifier   // drawWithCache: shadow paths cached;
    // onDrawWithContent { shadow; drawContent(); drawDoodle(...); tape }
@Composable fun ThemedBullet(index: Int, modifier: Modifier = Modifier)   // keeps the 6dp footprint, draws a 12dp glyph
@Composable fun ThemedCheckGlyph(checked: Boolean, modifier: Modifier = Modifier) // 22dp outline, or filled accent + white tick
fun Modifier.themedBadge(index: Int, colors: ArtColors): Modifier  // 24dp pastel circle behind "1."; text colour ink
```
- SectionCard: `themedCard` goes on the card Surface modifier (before Surface's clip, so the sticker
  overhangs). Sticker die-cut only for washi/sunny.
- CircleCheck: keep the 40dp toggleable box and `Role.Checkbox`; swap only the inner 22dp Box.
- DailySections.kt:117: the 6dp dot becomes `ThemedBullet(i)`. :61: the "1." Text gets
  `.themedBadge(i, colors)`; width stays 26dp.
- Test `ThemeCardTest` (JVM): the 6 Daily titles ("Today's Top Priorities", "To-Do List", "Schedule",
  "Don't Forget", "Today's Mood", "Notes") map to 6 distinct doodles per theme (tune `cardSalt` in
  ThemeArt.kt only if needed); `cardDecor` is deterministic.
- Accept: rose_cream unchanged; no sticker or tape over a text row or icon (Haiku screenshot-reviewer).

### S5 - Paper textures + static scenes (ThemePaper.kt new, ThemesStatic.kt, ui/common/PeriodHeader.kt)
```kotlin
class PaperCache(size: Size, density: Density, paper: Paper, seed: Int)  // precomputed FloatArray / List<Offset>
fun DrawScope.drawPaper(cache: PaperCache, color: Color, alpha: Float)
fun DrawScope.drawPageBorder(color: Color, density: Density)            // dashed rounded rect inset 6dp
fun DrawScope.drawScatter(seed: Int, count: Int, glyphs: List<Glyph>, colors: List<Color>, area: Rect,
    avoid: Rect?, alpha: Float, sizeDp: ClosedFloatingPointRange<Float>, density: Density)  // seeded, ±25deg
```
- ThemesStatic.kt rewrites WashiPlanner, SunnyJournal, MatchaTea, LavenderMoon: new Palette, `titleFont =
  art.titleFont`, `headerStyle = PLAIN`, `decoration` = paper + scene + strips. Anchors (64-110dp, bear
  96dp) in the bottom 22% and side gutters (12% w); scatter avoids `Rect(.10w,.06h,.90w,.80h)` unless alpha <= 0.18.
- PeriodHeader.kt:78 `Text(title, displaySmall)` becomes `ThemedTitle(title, displaySmall)` (Week/Month;
  moved here from S1 for the 3-file cap).
- Accept: contrast test green; the 4 `daily_theme_*` images show texture, washi page border and >= 12
  motifs; nothing above alpha 0.18 under body text.

### S6 - Animation engine rewrite (ThemeParticles.kt new, ThemeBackground.kt, Theme.kt)
```kotlin
fun interface SceneFrame { fun DrawScope.draw(timeSec: Float) }
fun interface ThemeScene { fun build(size: Size, density: Density, dark: Boolean): SceneFrame }
@Immutable class ParticleSystem(val count: Int, val seed: Int, val speedDp: ClosedFloatingPointRange<Float>,
    val direction: Int /*+1 down,-1 up,0 horizontal wrap*/, val swayDp: Float, val swayHz: Float,
    val spinDegPerSec: ClosedFloatingPointRange<Float>, val flip: Boolean, val fadeInFrac: Float,
    val fadeOutFrac: Float, val sizeDp: ClosedFloatingPointRange<Float>, val alpha: Float)
class ParticleField(system: ParticleSystem, size: Size, density: Density)  // per-particle FloatArrays
fun DrawScope.drawParticles(field: ParticleField, t: Float, draw: DrawScope.(i: Int, size: Float, alpha: Float) -> Unit)
    // pure f(t): wrap(y0 + dir*speed*t), sway, spin, flip scaleX = cos, fade; one withTransform per particle
fun twinkle(t: Float, seed: Float): Float = 0.3f + 0.7f * sin(t * 1.7f + seed).let { it * it }
const val MAX_PARTICLES = 40
```
- Theme.kt AppTheme: add `val scene: ThemeScene? = null, val staticTimeSec: Float = 0f`.
- ThemeBackground.kt rewrite: layer 1 = Spacer `drawWithCache` (paper + `decoration`, static); layer 2 only
  if `scene != null`: `onDrawBehind { with(frame) { draw(time.floatValue) } }`. Clock:
  `val time = remember(theme) { mutableFloatStateOf(theme.staticTimeSec) }`; `if (run) LaunchedEffect(theme,
  run) { val base = time.floatValue; val start = withFrameNanos { it }; while (isActive) withFrameNanos {
  time.floatValue = base + (it - start) / 1e9f } }`. `run` = LocalThemeAnimate && animated && systemOn
  (re-read on lifecycle change) && lifecycle >= STARTED; pause keeps the frame, resume continues. v1 phase
  decorations get `frac(time*1000/periodMillis)` until S7. Drop `rememberInfiniteTransition`.
- Test `ParticlesTest` (JVM): deterministic for the same t; alpha in [0,1]; wrap keeps y in [-size, h+size].
- Accept: v1 animated themes still move; Off/Remove-animations/background freeze; recomposition flat.

### S7 - Animated scenes + footer pill (ThemesAnimated.kt, ui/daily/DailyPageContent.kt, res/values/strings.xml)
- ThemesAnimated.kt: rewrite CloudDrift, PetalFall, FloatingHearts: Palette, `titleFont`, `decoration` =
  static layer, `scene` per "Animated scenes", `staticTimeSec`, `periodMillis = 0`; ParticleFields built
  once in `build()`; `internal fun particleCount(widthScale: Float): Int` (<= 40).
- DailyPageContent.kt:70-77 footer: when art != null, `.drawBehind` a pill (accent[2] @0.85, text width
  + 32dp via a draw-read `onTextLayout` state) with 6dp DIAMOND glyphs at both ends. String unchanged.
- strings.xml: refresh only the 7 `theme_<id>_desc` values. Test: `ParticlesTest` adds `particleCount(1.25f) <= 40`.
- Accept (S24U, Animated): clouds/sun, petals, hearts smooth at 60fps while scrolling Daily; Off freezes a pretty frame.

### S8 - Roborazzi (test/.../ui/ScreenshotTest.kt, test/.../ui/theme/ThemeContrastTest.kt)
- Keep every existing test and name; `themedDaily(id)` stays for all 8 ids (`animate = false` draws at
  `staticTimeSec`, deterministic). Add `s24u = "w384dp-h832dp-xxxhdpi"`, `foldCover = "w373dp-h873dp-xxhdpi"`,
  `foldInner = "w673dp-h747dp-xxhdpi"` and `private fun themedPage(id, page, q, dev)` (Compact; Medium on
  foldInner) capturing `${page}_theme_${id}_$dev.png`: 18 tests = {washi_planner, cloud_drift} x
  {daily, week, month} x {s24u, fold_cover, fold_inner}.
- ThemeContrastTest: dark-mode art pairs; `AppThemes.all.count { it.scene != null } == 3`.
- Accept: operator `recordRoborazziDebug` gives 8 + 18 PNGs; `daily_theme_rose_cream` == `daily_light_compact`;
  pre-theme images unchanged; Haiku reviewer: sticker on every card, readable text, no Fold-inner clipping.

Risks: title wrap on Fold cover (use 0.92x size); labels > 70% card width (13sp); 3200dp `compact` shots
look sparse (judge on S8 device shots); `drawStyle = Stroke` needs Compose UI >= 1.4.
