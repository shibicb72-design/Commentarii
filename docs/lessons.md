# Lessons

Carried over from Theatrum (local_music_player) where they apply:
- [signing] fresh ~/.android/debug.keystore per CI runner made every APK signature differ -> commit a debug-only keystore per repo.
- [headless] the PowerShell tool denies nested `powershell -File` in headless runs -> run gradle-brief.ps1 through the Bash tool.
- [contrast] text took colour from the window, not its card -> every surface passes its own contentColor.
- [verify] agents reported BUILD OK without running Gradle -> the planner re-runs the build itself.

## Commentarii