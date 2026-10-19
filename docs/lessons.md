# Lessons

Carried over from Theatrum (local_music_player) where they apply:
- [signing] fresh ~/.android/debug.keystore per CI runner made every APK signature differ -> commit a debug-only keystore per repo.
- [headless] the PowerShell tool denies nested `powershell -File` in headless runs -> run gradle-brief.ps1 through the Bash tool.
- [contrast] text took colour from the window, not its card -> every surface passes its own contentColor.
- [verify] agents reported BUILD OK without running Gradle -> the planner re-runs the build itself.

## Commentarii
- [alarms] AlarmScheduler and PageRepository would depend on each other -> PageRepository takes a small `ScheduleListener`; the scheduler implements it and gets entries from callers.
- [lock] hiding content behind a lock overlay still leaves it readable to accessibility -> do not compose AppNav while locked; the back-stack routes are saved separately (see [nav]).
- [lock] BIOMETRIC_STRONG|DEVICE_CREDENTIAL is unsupported before API 30 -> use setDeviceCredentialAllowed there; with no screen lock the app stays locked and explains (this supersedes the old "unlock rather than lock out" rule).
- [rollover] computeRollover offered items already carried forward -> filter on carriedTo == null.
- [lock] unlocking when no screen lock exists let anyone past the lock -> never unlock without auth; LockScreen and Settings explain and open security settings (ACTION_BIOMETRIC_ENROLL, fallback ACTION_SECURITY_SETTINGS); availability is re-checked on resume.
- [lock] turning the lock OFF needed no auth -> requestLockChange requires authenticate in both directions; cancel/fail leaves the setting unchanged.
- [nav] AppNav is not composed while locked so nav state was lost -> record concrete back-stack routes (NavRestore, rememberSaveable string) and replay them after unlock.
- [rollover] dialog outside-tap was ignored and could not be re-offered -> dismiss only clears the in-memory offer (RolloverSession.dismiss); only Move/Skip set lastOpenedDate.
- [backup] hasUserData was pageCount() > 1 and missed single-day data -> count non-blank tasks, schedule text, notes text, titled notes, moods and self-care checks; blank auto-created pages do not count.
- [test] goAsync() is null when a receiver is called directly -> use PendingResult? and keep the launched Job on the receiver so tests can join it.
