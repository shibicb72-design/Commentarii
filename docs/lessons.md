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
- [alarms] a custom time less than 10 min away got no alarm and no feedback (triggerAt returned null) -> entry-10min if still ahead, else fire in 5 s if the entry itself is ahead (remember the immediate in `alarm_state` prefs so boot/resume do not repeat it), else tell the user it is past.
- [alarms] alarms set before the exact-alarm grant stayed inexact and the state-change broadcast is unreliable -> reschedule everything on every app start (AppContainer.onAppStart) as well as in BootReceiver.
- [notify] the channel was IMPORTANCE_DEFAULT and importance cannot be raised after creation -> new channel id (schedule_reminders_v2, HIGH), delete the old one.
- [alarms] request code was an xor-hash of the id and the PendingIntent had no per-entry identity -> action + `commentarii://alarm/<id>` data and requestCode = id.toInt(); cancel with an identical FLAG_NO_CREATE intent.
- [notify] POST_NOTIFICATIONS was asked once only, with no guidance and no fire-time check -> ask whenever notifications are not enabled, expose notificationsBlocked for a settings shortcut, and log enabled/channel importance when posting.
- [debug] reminders failed silently with nothing to inspect -> local ring log (DebugLog, 500 lines, no note text) for schedule, cancel, fire and reschedule events, plus a 10 s test reminder.
- [alarms] superseded (fix3): the 10-min lead and the now+5 s immediate path are gone; alarms fire at the exact entry minute, past times are skipped with a snackbar "That time has passed - no reminder set".
- [ui] checkbox/bullet text sat above the 48 dp control centre because of font padding plus ad-hoc vertical padding -> one `FirstLineRow` + `checklistTextStyle` (no font padding, centred line height) for every checklist-style row.
- [nav] `settings/debug` was dropped by NavRestore.isValid so unlock landed on Settings -> validate against `Routes` constants.
