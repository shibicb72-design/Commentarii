# Themes2 sweep (QA grep pass)

Scope: `app/src/main` plus the newest `themes2-gradle-*.log`. Read-only; no code changed.

## Security / network
- INTERNET permission removed: `AndroidManifest.xml:6` (`tools:node="remove"`).
- No `Socket`, `URL(`, or outbound `http` use in app code. The only `http` hits are XML namespaces (`AndroidManifest.xml:2`, vector/adaptive-icon files) and the OFL license URL in `assets/licenses/DancingScript-OFL.txt:5`.
- `FLAG_SECURE` set/cleared conditionally at `MainActivity.kt:88` and `:90`.

## Animation / lifecycle
- `withFrameNanos` loop at `ui/theme/ThemeBackground.kt:67,69`, gated by `Lifecycle.State.STARTED` at `:62`.
- `areAnimatorsEnabled()` honored at `ui/theme/Theme.kt:239`.

## Removed sections
- `Grateful`: 0 hits.
- `SelfCare`: remnants remain in `backup/ExportImport.kt:13-14,151-152,205,208`, `data/Daos.kt:299-353`, `data/Entities.kt:154,162`, `data/CommentariiDatabase.kt:18-19,31`. Consistent with commit 9cf017a (legacy tables kept for backup), but the export/import path still reads and writes them. Confirm that is intended.

## Fonts
- `R.font.` refs: `patrick_hand`, `pacifico`, `sniglet_extrabold` (`ui/theme/ThemeArt.kt:66-68`); `dancing_script` (`ui/theme/Type.kt:9`).
- Resource existence not checked in this pass.

## Build log
- Newest by mtime: `C:\Users\shibi\cc_commentarii\themes2-gradle-S1-0.log` (Glob order).
- `^w: ` matches: 0. Log is a Gradle task listing (first 30 lines read).
