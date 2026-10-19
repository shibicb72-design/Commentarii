# Lessons

Carried over from Theatrum (local_music_player) where they apply:
- [signing] fresh ~/.android/debug.keystore per CI runner made every APK signature differ -> commit a debug-only keystore per repo.
- [headless] the PowerShell tool denies nested `powershell -File` in headless runs -> run gradle-brief.ps1 through the Bash tool.
- [contrast] text took colour from the window, not its card -> every surface passes its own contentColor.
- [verify] agents reported BUILD OK without running Gradle -> the planner re-runs the build itself.

## Commentarii
- [alarms] AlarmScheduler and PageRepository would depend on each other -> PageRepository takes a small `ScheduleListener`; the scheduler implements it and gets entries from callers.
- [lock] hiding content behind a lock overlay still leaves it readable to accessibility -> do not compose AppNav while locked; nav state resets on relock.
- [lock] BIOMETRIC_STRONG|DEVICE_CREDENTIAL is unsupported before API 30 -> use setDeviceCredentialAllowed there; if the device has no screen lock, unlock rather than lock the user out.
- [rollover] computeRollover offered items already carried forward -> filter on carriedTo == null.
