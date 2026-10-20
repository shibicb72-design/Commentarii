ITERATE
# Themes2 visual review (Opus art director, 2026-10-19)
Source: 8 daily_theme_*.png (compact 1236x9600) + daily_theme_washi_planner_fold_inner.png. Code not read; file names come from git status, so fixes name a file and what to draw.
Layout, section order and strings match on every theme. No Grateful/Self Care. Text reads well on every theme except where cloud_drift's clouds sit on chips.
Problems on every theme (fix these first):
- G1 Background art gathers in the empty bottom ~35% of the 3200dp shot, so on a real phone most of it sits below the content. ThemeBackground.kt / ThemeArt.kt: spread motifs along the margins beside the cards (y spread over the content height) and keep at most 2 hero stickers near the footer.
- G2 The tall Schedule card is a long empty white block on all themes. ThemeCard.kt: give the card body a paper texture (dot-grid/lined/speckle, alpha <= 0.06) and a second sticker partway down the right edge for cards over ~600dp.
- G3 Schedule rows have no themed glyph. Add a small pastel clock or theme bullet before each time (DailySections.kt row look only, no string or layout change).
- G4 Page-title display text has no doodle under it. Add a script subtitle feel with a sparkle or underline swash under the title (PeriodHeader.kt, decoration only, no new text).

## rose_cream — 8 (default, required unchanged)
Looks like v1: script title and section tabs, plain cards. This is correct under rule 3. Keep it as is; no changes.

## washi_planner — 7 (readable)
Strongest theme. Multicolour Sniglet title, dashed mint border, pastel tab pills, crown/boba/jar stickers, numbered circles and heart bullets all match image 1. Fold inner looks great.
1. StickersPlanner.kt: the bear-on-note, potted plant and sticky note sit in the empty footer. Move the bear to peek over the Priorities card's top-right edge and the plant beside the Notes card.
2. ThemeCard.kt: tab pill fill and card border colours repeat (pink, yellow, pink). Cycle pink → yellow → lavender → mint → blue per section, as in the reference.
3. Add washi strips taped across 2–3 card corners (they only appear at the page edge now). Add a mint footer pill with ◆ separators around "You've got this!" (the look only).
4. Mood row: put a pastel circle behind each face glyph, tinted to match the cycling colours.

## sunny_journal — 7 (readable)
Rainbow bubbly title, highlighter swashes with heart, sun and leaf sprigs: closest to image 2.
1. Sun is jammed against the calendar icon and overlaps it at the title. Move it to the right margin of the header row with 8dp clearance and add a cloud partly in front.
2. Swashes are short and light. Make them wider (text width +24dp) with a rough brush edge and 2-tone overlap (DoodleKit.kt).
3. Candle, camera, mug and plant sit in the footer. Spread them beside the cards (G1) and add a taped note card behind Notes.
4. Bullets: alternate heart/star/flower per row in different colours instead of one flower glyph.

## matcha_tea — 6 (readable)
Dashed tab pills and lined paper are nice, but the theme is muted and sparse. The teapot and bowl with whisk are cute but sit in the footer.
1. Checkboxes are plain circles. Draw a leaf or tea-bead check glyph.
2. DoodlesGarden.kt: give each card corner a sticker with a face (mochi, dango, onigiri, teacup). Some corners are tiny line icons now.
3. Add bamboo/leaf sprigs layered across the card edges, plus a soft drop shadow under the cards.
4. Make the title more two-tone: dark matcha with cream inline highlight.

## lavender_moon — 5 (readable)
Script title with a star is nice, but cards are plain, the page is mostly empty, and the stickers are tiny (moon, star, planet, lavender).
1. DoodlesSky.kt: draw the corner stickers about 1.6x larger with faces, layered fills and highlights. Add a crescent moon with a sleeping face near the header.
2. Background: dotted constellation lines plus twinkling 4-point sparkles spread through the content height (G1). Today it is only a few dots.
3. Dashed tab pills: fill them with solid pastel (lilac, mint, peach) rather than the pale fill and outline.
4. Footer pill: add stars on either side and a sparkle burst.

## floating_hearts — 6 (readable)
Script title with a heart, pastel tabs, envelope/bow/teddy stickers. Hearts are too few and too pale.
1. ThemeParticles.kt: make rising hearts bigger (12–22dp), use 3 pinks/lilacs, add a highlight dot, and keep 12–18 visible in the static frame.
2. Draw the big envelope beside Notes and spill a heart from it. Add a heart-shaped washi strip on 2 cards.
3. Bullets: filled hearts in alternating pink/lilac/blue rather than small grey hearts.
4. Card border: double-line pink with a scalloped top edge on the tab.

## petal_fall — 6 (readable)
Sakura clusters are the best-looking art (layered petals, centres). Swash tabs look good.
1. Cards are plain: add a blossom-branch sticker curling over each card's top-right corner (alternate left/right).
2. ThemeParticles.kt: the static frame shows few petals. Show 15–20 tumbling petals at mixed sizes and rotations across the content height.
3. Checkbox glyph: 5-petal flower outline, then filled pink when checked (currently a generic flower).
4. Title: add a petal resting on the "O" and a pink brush underline.

## cloud_drift — 5 (readability issue)
Smiling sun, rainbow and cloud bullets are on-theme, but clouds sit on top of the date chips and the "Personal" chip, and clip card edges.
1. ThemeBackground.kt: keep parallax clouds out of the header/date-strip/chip band, or draw them at alpha <= 0.25 behind the chips. Never draw them over chip text.
2. Clouds look like flat grey lumps. Draw them as Bezier puffs with a white top, soft blue underside, faces on 1–2, and 3 parallax depths.
3. Give the sun rotating rays that are larger and warmer. Add a rainbow arc behind the Schedule tab and a kite string.
4. Bullets: cloud plus raindrop alternating, tinted per section.
