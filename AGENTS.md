# AGENTS.md - Commentarii

Local-only notes and daily planner. Primary tool: Claude Code (config in `.claude/`). Lessons: `docs/lessons.md`. Data model: `docs/architecture.md`.

## Ground rules
- Never install on, uninstall from, or change data on Shibi's devices. Report the CI APK artifact instead.
- One branch per ticket (`opus/<topic>`); PR body starts "Do not merge without Shibi's OK." Never merge, never push to `main`, never force-push.
- Never kill `claude.exe` or `opencode.exe`. Never commit secrets. Never add a release key.
- `app/debug.keystore` is DEBUG-ONLY and NON-SECRET (android / androiddebugkey), generated for this repo. CI versionCode = `GITHUB_RUN_NUMBER`.
- Local only: no INTERNET permission, `allowBackup=false`, data extraction rules exclude everything. Never add a network library or cloud SDK.

## Project map
- Single module `:app`, package `com.shibi.commentarii` (minSdk 29, targetSdk 36). Kotlin, Compose Material 3, Room (+FTS), DataStore, BiometricPrompt, AlarmManager. Manual DI via `AppContainer`.
- Dependencies only through `gradle/libs.versions.toml`. User-facing text in `res/values/strings.xml`.
- Tests: `app/src/test/java/com/shibi/commentarii/` (JUnit 4; Robolectric for Room and Roborazzi). Screenshots go to `app/build/outputs/roborazzi/` via `recordRoborazziDebug`.

## Build and test (always via the wrapper)
```
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/gradle-brief.ps1 assembleDebug testDebugUnitTest
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/gradle-brief.ps1 recordRoborazziDebug
```
Prints `BUILD OK` or the first failure lines. Full log `%TEMP%\gradle-last.log` (Grep it, never read it whole).

## Design rules
- HARD: no dark-on-dark or light-on-light text. Every surface sets its content colour from its own container; check light and dark themes.
- Palette: cream `#FBF6F3` background, dusty pink `#E8B4B8`/`#F4D6D6` section tabs, sage `#D8D3C4` accents, brown-black text. Script font only for titles.
- Compact width (folded Fold 7, S24 Ultra): sections stacked in one scroll. Expanded/medium (unfolded): two columns.

## Delegation (token budget)
- Builds/tests -> `gradle-runner` (Haiku). Screenshot review -> `screenshot-reviewer` (Haiku). Search -> `Explore` (Haiku). Implementation -> `android-implementer` (Sonnet). Planning in main conversation (`opusplan`).
- The main conversation never reads PNGs, full Gradle logs or `build/` files.