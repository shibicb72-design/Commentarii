# Fix 3 - exact-time reminders, checklist alignment, debug route restore
Branch `opus/v1-core`. Keep SCHEDULE_EXACT_ALARM, the 10 s test reminder, the high-importance channel, resume/boot reschedule and the debug log. No network permission.

## Goal
1. Reminders fire at the exact minute of the entry: hour rows at hh:00, custom entries at their time. If the time has passed when saved: no alarm, snackbar "That time has passed - no reminder set". Remove the 10-min lead and the now+5 s path.
2. Checklist and daily-page row text is vertically centred on the checkbox/bullet (first line for multi-line).
3. `settings/debug` survives navigation restore after unlock.

## Root-cause notes
- `domain/AlarmTimes.kt:11-66`: `ALARM_LEAD_MINUTES`, `IMMEDIATE_DELAY_SECONDS`, `AlarmKind.IMMEDIATE`, `Alarm.immediate`; `classifyEntry`/`triggerAt` subtract the lead and fall back to now+5 s.
- `notify/AlarmScheduler.kt:46-105,181,206`: IMMEDIATE outcome, `KEY_FIRED` prefs de-dup (only needed for the now+5 s path). KDoc `:28` says "10 minutes before".
- `notify/Notifications.kt:44,73` uses `notif_in_10_min`; receiver has no entry time (extras only id/date/text, `AlarmScheduler.kt:192-194`, `AlarmReceiver.kt:11-13`).
- Alignment: `NoteEditorScreen.kt:304` Row uses `Alignment.Top` with `BasicTextField` `.padding(vertical = 6.dp)`; `DailyComponents.kt:59-71` LineField centres the Row but the text box carries font padding and a 10 dp vertical padding, so glyphs sit above the 48 dp checkbox centre. `DailySections.kt:283` SelfCareRow uses a plain `Text`.
- `ui/lock/NavRestore.kt:25-30` `isValid` allows notes/search/settings/daily/note only; `AppNav.DEBUG = "settings/debug"` is dropped.

## Files and changes
- `domain/AlarmTimes.kt`: delete `ALARM_LEAD_MINUTES`, `IMMEDIATE_DELAY_SECONDS`, `AlarmKind.IMMEDIATE`, `Alarm.immediate`. `classifyEntry`: BLANK if text blank; SCHEDULED if `entryInstant > now` (strict); else PAST. `triggerAt` returns `entryInstant` or null. `upcomingAlarms` keeps SCHEDULED only. `entryInstant` DST rules unchanged (gap shifts forward, overlap takes earlier offset).
- `domain/AlarmSelection.kt`: KDoc (`:8`) - no lead/immediate wording; logic unchanged otherwise.
- `notify/AlarmScheduler.kt`: drop immediate branch and `KEY_FIRED`/`immediateKey` (one-time `prefs.edit().remove(KEY_FIRED_LEGACY)` on init is fine); outcomes emit PAST only; debug log line drops `immediate=`; KDoc `:25,:28,:39,:59` updated. Add `EXTRA_MINUTE` (minuteOfDay) to the PendingIntent extras; test reminder passes -1.
- `notify/Notifications.kt`: add `EXTRA_MINUTE`; `showReminder(..., minuteOfDay: Int)`; text = `notif_now_at` with time formatted like the schedule time label (`DateTimeFormatter.ofLocalizedTime(SHORT)`), or `notif_now` when minute < 0. KDoc `:44`.
- `notify/AlarmReceiver.kt`: read `EXTRA_MINUTE` (default -1) and pass through.
- `ui/settings/ReminderStatus.kt`: only PAST -> `R.string.outcome_past`; remove IMMEDIATE mapping. `ui/daily/DailyPageScreen.kt:52-57`: remove `immediateText` branch.
- `res/values/strings.xml`: delete `notif_in_10_min`, `outcome_immediate`; add `notif_now` "Now: %1$s", `notif_now_at` "Now (%1$s): %2$s"; `notif_channel_desc` "Reminders at the time of a schedule entry."; `outcome_past` "That time has passed - no reminder set". Keep `settings_msg_test_scheduled`. Grep strings for any other "10 min"/"ten minutes" reminder wording (Settings reminders section) and fix.
- New `ui/components/AlignedRow.kt`: `@Composable fun FirstLineRow(textStyle, leading, modifier, trailing, content)` - Row(`Alignment.Top`); leading and trailing each in `Box(Modifier.size(48.dp), Center)`; content gets `padding(top = max(0, (48.dp - lineHeightDp)/2))` where `lineHeightDp` comes from `textStyle.lineHeight` via `LocalDensity`; if lineHeight > 48 dp, pad the leading box instead. Export `checklistTextStyle(base)` = base + `LineHeightStyle(Center, Trim.None)` + `PlatformTextStyle(includeFontPadding = false)`. Single-line result equals CenterVertically; multi-line aligns to line 1.
- `ui/notes/NoteEditorScreen.kt:304-326` (BlockRow, CHECKBOX and bullet types): use `FirstLineRow`; BasicTextField uses `checklistTextStyle`, no vertical padding (row spacing via bottom padding 4 dp); no decoration padding.
- `ui/daily/DailyComponents.kt:42-85` (LineField): use `FirstLineRow` + `checklistTextStyle`; replace `.padding(vertical = 10.dp)` with the computed top pad + matching bottom pad; placeholder uses the same style. Covers priorities (number + CircleCheck in leading), to-do, don't-forget bullet, grateful heart (`DailySections.kt:55,73,104,124`).
- `ui/daily/DailySections.kt:283` SelfCareRow display mode: `FirstLineRow` with `Text(style = checklistTextStyle(bodyLarge))`. ScheduleCard (`:153`) untouched.
- `ui/lock/NavRestore.kt:25-30`: allow `AppNav.DEBUG` ("settings/debug"); use AppNav constants, not literals.
- Docs: `docs/plan-v1.md:27,32,69` and `docs/architecture.md:25` -> exact-minute trigger, past skipped, "Now (hh:mm): text". `docs/lessons.md:20`: append "superseded: lead dropped (fix3), past times are skipped with a snackbar".

## Tests
- `domain/AlarmTimesTest.kt`: replace lead tests with `hourRowFiresAtTopOfHour` (09:00 -> 09:00:00), `customTimeFiresAtExactMinute` (23:10 -> 23:10), `pastIsSkipped` (now 09:00:30, entry 09:00 -> PAST/null), `oneMinuteAheadIsScheduled`, `blankSkipped`; DST: spring-forward 02:30 in America/New_York 2026-03-08 -> 03:30 EDT, fall-back 01:30 on 2026-11-01 -> earlier offset (05:30Z), and at 05:31Z it is PAST. Remove `withinLead...`, `exactlyTenMinutes...`, `upcomingAlarmsFlagsImmediate`, `00:05 -> previous day 23:55`.
- `domain/AlarmSelectionTest.kt`: `triggersAtEntryTime`; "too soon" 9:05 case becomes scheduled at 9:05; dedupe tests keep.
- `notify/ScheduleFlowTest.kt:107-111,154`: saving a past entry emits one PAST outcome and registers nothing; saving a future entry registers at the exact instant; log line without `immediate=`. Remove immediate re-fire test.
- `notify/BootReceiverTest.kt:67`: drop `.minusMinutes(10)`. `notify/NotificationsTest.kt`: text is "Now (…): Dentist"; minute -1 -> "Now: Test reminder". `ui/settings/ReminderStatusTest.kt:22`: remove IMMEDIATE assert.
- NavRestore test (extend or add `ui/lock/NavRestoreTest.kt`): `["daily/2026-10-18","settings","settings/debug"]` all kept.
- `ui/ScreenshotTest.kt`: `checklist_alignment_light` at phone qualifiers, light theme: a Column with `NoteEditorContent` built from `sampleNoteEditorUiState()` copied with one short and one 3-line checkbox block (checked + unchecked), plus `TodoCard`/`SelfCareCard` with a short and a long item. Do not change the existing sample files (keeps other baselines stable).

## Decisions
- Strict `>` now: an entry for the current minute already started -> PAST. No grace window.
- `AlarmKind.IMMEDIATE` removed rather than left unused; no DB/schema change. Channel id unchanged (description change only).
- One alignment helper for all checklist-style rows instead of per-screen magic paddings.

## Acceptance criteria
- `gradle-brief.ps1 assembleDebug testDebugUnitTest` prints BUILD OK; `recordRoborazziDebug` produces `checklist_alignment_light.png`; screenshot-reviewer confirms checkbox centre = first-line centre (single and multi-line) and no contrast violations.
- `Grep -i "10 min|ten minutes|IMMEDIATE|notifying now"` in `app/src` and `docs/` (except lessons history and the 10 s test reminder) returns nothing.
- Manifest still has SCHEDULE_EXACT_ALARM and no INTERNET. Unlock from Settings > Debug log returns to the debug log.
