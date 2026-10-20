S1: Theme.kt core types (AppTheme, ThemeMode, DecorationFrame, Palette, paletteMode, themeTypography, LocalAppTheme/LocalThemeAnimate, AppThemes with Rose & Cream default only) + CommentariiTheme(dark, theme, animate); CommentariiColors.tabs.
S1: SettingsRepository themeId/staticThemeIds + setters (DEFAULT_THEME_ID); MainActivity collects them, loading gate extended; 16 theme strings; SettingsRepositoryThemeTest.
S1: not built or tested (Gradle runs outside Claude); default theme path uses the original schemes/AppTypography unchanged.
S2: SettingsUiState themeId/staticThemeIds + SettingsCallbacks onThemeSelect/onThemeAnimated; SettingsViewModel combines theme flows (4-way) and wires setThemeId/setThemeAnimated.
S2: SettingsScreen Theme card gets divider + ThemeRow list (radio, swatch, name/desc); animated themes only get an Animated/Off segmented toggle (+ system-animations-off hint); no global switch.
S2: 4 strings added; not built or tested (Gradle runs outside Claude). settings_theme_choose is unused (no new heading, per plan).
S3: new ui/theme/ThemeBackground.kt: ThemedPage (original Surface; adds doodle layer only when theme.decoration != null) and ThemeBackground (one infinite transition, run = user animate && theme.animated && system animators on && lifecycle >= STARTED, phase read only in drawWithCache onDrawBehind).
S3: DailyComponents.SectionCard uses theme tabShape/cardShape and tabs[abs(title.hashCode()) % size]; WASHI/HIGHLIGHTER drawn behind the title text, RIBBON via GenericShape notch; default theme path yields identical values. DailyPageContent root Surface -> ThemedPage.
S3: not built or tested (Gradle runs outside Claude); per-theme Daily screenshots left to S6 (only Rose & Cream exists yet).
S4: new ui/theme/ThemesStatic.kt: WashiPlanner, SunnyJournal, MatchaTea, LavenderMoon via paletteMode (plan palettes/shapes/header styles) with Compose-Canvas-only painters (hearts, stars, bear, plant, sun, candle, mug, leaves, steam, crescent, constellations) sized relative to the screen and scaled for wide screens.
S4: Theme.kt AppThemes.all now has 5 themes; WeekPageContent root Surface -> ThemedPage; new test ui/theme/ThemeContrastTest (contrast pairs, title stops, unique ids, byId fallback, <=3 animated).
S4: not built or tested (Gradle runs outside Claude); Matcha uses Serif but not italic because themeTypography only sets fontFamily (Theme.kt untouched there).
S5: new ui/theme/ThemesAnimated.kt: CloudDrift (60s, drifting clouds + bobbing sun), PetalFall (24s), FloatingHearts (20s) via paletteMode; vector-only painters, paths built once per size, phase read only in draw, whole-cycle motion for seamless loops, fixed deterministic seeds.
S5: Theme.kt AppThemes.all now 8 themes (3 animated); MonthPageContent root Surface -> ThemedPage (unused Surface/MaterialTheme imports removed).
S5: not built or tested (Gradle runs outside Claude); ThemeContrastTest already covers the new themes via AppThemes.all.
S6: NotesListScreen and SearchScreen root Surface -> ThemedPage (only that call + import changed).
S6: ScreenshotTest gets themedDaily(id) (light, compact, animate=false) and 8 @Test fns writing daily_theme_<id>.png.
S6: not built or tested (Gradle runs outside Claude); operator must run recordRoborazziDebug and check rose_cream matches daily_light_compact.
