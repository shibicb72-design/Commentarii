# Themes sweep (QA grep pass, 2026-10-19)

1. **Network/FLAG_SECURE:** `MainActivity.kt:88,90` FLAG_SECURE set/clear (as expected). Manifest `AndroidManifest.xml:6` removes INTERNET (`tools:node="remove"`). No Socket or `URL(` hits. The `http` hits are XML namespaces, a font licence URL and a comment; no network code.
2. **Animation pause and system checks:** `ThemeBackground.kt:59-63` gates on `systemAnimationsEnabled()` plus `state.isAtLeast(Lifecycle.State.STARTED)` before `rememberInfiniteTransition`. `Theme.kt:237` wraps `ValueAnimator.areAnimatorsEnabled()`. `SettingsScreen.kt:390` checks it too. Lifecycle hooks (`LifecycleEventEffect`) are in the Day/Week/Month/Note/Notes screens, flushing on ON_STOP.
3. **Grateful / Self Care in UI:** no UI usage. Remaining hits are data-layer only: `backup/ExportImport.kt`, `data/Daos.kt`, `data/Entities.kt`, `data/CommentariiDatabase.kt` (legacy tables kept for backup, per commit 9cf017a).
4. **Screenshots:** the specified globs (`app/src/test/snapshots/**`, `app/src/**/roborazzi/**`) match nothing. Output is in `app/build/outputs/roborazzi/`. Daily theme PNGs present for all 8 ids: rose_cream, washi_planner, sunny_journal, cloud_drift, petal_fall, floating_hearts, matcha_tea, lavender_moon. Missing from plan list: none. File sizes not recorded (Glob returns paths only).
5. **Gradle warnings:** the glob returned 8 logs (S1, S1-fix, S2, S2-fix, S3-S6). Newest by mtime is assumed to be `themes-gradle-S1.log`; `^w: ` matches zero lines in it. Other logs not scanned.

**Verdict**
- Network: clean (no INTERNET, no socket/URL code); FLAG_SECURE unchanged.
- Animation: pause (lifecycle STARTED) and system-animator checks present.
- Grateful/Self Care: no UI references; data-layer legacy kept intentionally.
- Screenshots: all 8 Daily theme PNGs exist, but under `app/build/outputs/roborazzi/`, not the paths specified; sizes not captured.
- Gradle: no `w:` warnings in the S1 log; the other 7 logs are unchecked.
