# Commentarii v1 plan (approved by Shibi, Oct 18 2026)

Already in the repo: skeleton (`MainActivity`, Room/KSP, DataStore, biometric, appcompat, window-size-class in the catalog),
manifest with allowBackup=false + data_extraction_rules excluding everything + INTERNET removed,
`res/font/dancing_script.ttf` (OFL, licence in `assets/licenses/DancingScript-OFL.txt`). Do not add network libraries.
Manual DI: `CommentariiApp : Application` holding an `AppContainer` (db, repositories, settings, alarm scheduler, clock).
Inject a `java.time.Clock` / `ZoneId` everywhere time matters so logic is testable.

## Phase A - data layer (pure + Room), with unit tests
Package `data/` and `domain/`.
- `PageEntity(id, type: String ["DAILY","NOTE", future "WEEKLY_PAYMENTS","TRAVEL","JOURNAL"], title, date: LocalDate? (unique for DAILY),
  folderId?, pinned, createdAt, updatedAt, lastOpenedAt)`.
- `SectionBlockEntity(id, pageId, kind: String, position, payloadJson)` - each template is a list of section blocks; new templates add new kinds,
  no schema rewrite. Daily kinds: PRIORITIES, TODO, SCHEDULE, DONT_FORGET, GRATEFUL, SELF_CARE, MOOD, NOTES. Note kind: RICH_TEXT.
  Use structured child tables where we query: `TaskItemEntity(id, pageId, section [PRIORITY|TODO|DONT_FORGET|GRATEFUL], position, text, done)`,
  `ScheduleEntryEntity(id, pageId, date, minuteOfDay, text, isCustom)` (hour rows 6..22 are minuteOfDay = h*60, isCustom=false),
  `MoodEntity(date PK, mood 1..5)`, `SelfCareItemEntity(id, name, position, active)`, `SelfCareCheckEntity(itemId, date, PK both)`.
  NOTES / RICH_TEXT payload: a small block document JSON: list of blocks {type: PARAGRAPH|H1|H2|BULLET|CHECKBOX, text, checked, spans:[{start,end,bold}]}.
- `FolderEntity(id,name)`, `TagEntity(id,name)`, `PageTagCrossRef(pageId,tagId)`.
- FTS: `PageSearchFts` (Room `@Fts4`) with columns pageId-ish rowid mapping, title, body; repository keeps body = flattened text of all sections
  and is rebuilt on every save. `search(query)` returns pages of both types, pinned first.
- DataStore `SettingsRepository`: lockEnabled, exportReminderEnabled (default true), lastExportAt, lastOpenedDate (for rollover), reminderDismissedAt,
  darkMode (system/light/dark).
- `domain/Rollover.kt`: pure function: given today, last page date (most recent DAILY before today) and its unfinished PRIORITY+TODO items,
  returns `RolloverOffer(fromDate, items)` or null. Only once per new day (lastOpenedDate < today). Never moves automatically;
  `applyRollover(selectedIds)` copies selected items to today's TODO (priorities go to today's priorities if slots free, else TODO) and marks originals as carried (keep text, done=false, add `carriedTo` date field).
- `domain/AlarmTimes.kt`: pure: `triggerAt(date, minuteOfDay, zone, now): Instant? = the entry's local datetime in zone` (exact minute), null if <= now (past times are skipped, the UI shows a snackbar); handle DST gaps
  (use ZonedDateTime.of which shifts forward) and overlaps (earlier offset). `upcomingAlarms(entries, now, zone)` for entries with non-blank text.
- `domain/Streaks.kt`: pure: current streak per self-care item = consecutive days ending today (or yesterday if today not yet checked) with a check.
- `backup/ExportImport.kt`: export everything (pages, blocks, tasks, schedule, moods, self-care items+checks, folders, tags, refs, settings except lock) to one JSON
  (org.json or a hand-written serializer; formatVersion=1). Import replaces all data in one transaction after validating formatVersion. Round-trip test with Robolectric in-memory Room.
- Tests: `RolloverTest`, `AlarmTimesTest` (exact minute, custom 7:35 -> 7:35, past skipped, DST spring-forward 2:30 America/New_York on 2026-03-08, fall-back 1:30 on 2026-11-01, blank text skipped, 00:05 entry -> 00:05 same day),
  `StreaksTest`, `ExportImportRoundTripTest`, `PageRepositoryTest` (daily page unique per date, FTS finds both types, pin ordering, folders/tags).
- Write `docs/architecture.md` (data model, how a new template type plugs in: add type + section kinds + a renderer, no migration needed for JSON-payload sections).

## Phase B - UI
- Theme `ui/theme`: light: background #FBF6F3, cards #FFFFFF-ish cream #FFFDFB, tabs #E8B4B8 / #F4D6D6, sage #D8D3C4, text #2B2220. Dark: background #1E1A19, cards #2A2423,
  tabs #8E5A5F with text #FFF5F3, sage #6E6A5E, text #F3ECE9. EVERY text colour comes from its own container (contentColorFor / explicit). No dark-on-dark, no light-on-light.
  Typography: Dancing Script for display/title (page title, section labels optional), default sans for body.
- Vector drawables you draw yourself: `ic_flower.xml` (simple 5-petal), `ic_heart.xml`. Launcher icon: adaptive icon with cream bg + pink flower.
- Navigation (navigation-compose): Today (daily page) / Notes / Search / Settings; routes `daily/{date}`, `note/{id}`, `notes`, `search`, `settings`.
  Compact: bottom bar. Medium/Expanded: navigation rail.
- Daily page screen: date header in script font with a horizontal date strip (+/- 7 days, tap to flip, calendar button opens DatePicker), pin, folder/tag chips.
  Sections as rounded cards with a pink label tab: Today's Top Priorities (3 numbered lines), To-Do List (circle checkbox + Done tick, add item, reorder with up/down
  or drag handle), Schedule (6 AM..10 PM rows, text field per hour, "+ custom time" with TimePicker, custom entries sorted in), Don't Forget (bullets),
  Today I Am Grateful For (heart bullets, 5 lines, "add line"), Self Care (checklist with streak badge "🔥3" or "3d", inline add/rename/remove), Today's Mood
  (5 faces very happy..very sad, tap selects, stored per day), Notes (free text), footer "You've got this!" in script font. NO quote box.
  Layout by WindowSizeClass: Compact width -> single LazyColumn of all sections; Medium/Expanded -> two columns
  (left: priorities, to-do, don't forget, grateful, self care, mood; right: schedule, notes), footer spanning.
  Autosave (debounced ~500 ms) via ViewModel.
- Notes list: pinned first, folder filter chips, tag chips, FAB new note. Note editor: title + block editor with a toolbar (Bold, H1, H2, bullet, checkbox);
  checkboxes inline tappable. Keep it robust over fancy: per-block TextFields with type; bold via selection spans in the block.
- Search: FTS query field, results of both types with type icon, tap opens.
- Settings: Fingerprint lock toggle, export reminder toggle, Export (SAF CreateDocument "commentarii-backup-YYYY-MM-DD.json"), Import (SAF OpenDocument, confirm dialog
  "replaces all data"), edit Self Care items, theme (system/light/dark), Licences (shows OFL text).
- Export reminder: dismissable banner on Today if reminder enabled and lastExportAt null-or-older than 14 days (and data exists); dismiss hides for 14 days.
- Rollover dialog on first open of a new day: "Carry over N unfinished items from <date>?" buttons Move all / Pick… (checkbox list) / Skip.
- Roborazzi tests (`@GraphicsMode(NATIVE)`, Robolectric sdk 35) recording: daily_light_compact (w411dp-h891dp... use S24-ish `w412dp-h915dp-xxhdpi`),
  daily_dark_compact, daily_light_expanded (`w884dp-h1104dp-xhdpi` unfolded Fold), daily_dark_expanded, notes_list_light, note_editor_light, lock_screen_light,
  lock_screen_dark. Use sample data with filled sections. Render screen composables with fake state (no real DB needed).

## Phase C - platform
- Lock: `AppLock` with BiometricPrompt (BIOMETRIC_STRONG or BIOMETRIC_WEAK | DEVICE_CREDENTIAL; on API 29 use setDeviceCredentialAllowed fallback).
  Lock on cold start and when returning after >60 s in background (ProcessLifecycleOwner). Lock screen composable (script title, flower, "Unlock" button).
  When lock enabled: window FLAG_SECURE set; cleared when disabled. Toggling on requires a successful auth first.
- Notifications: channel "Schedule reminders". `AlarmScheduler` using AlarmManager: manifest declares USE_EXACT_ALARM? NO - USE_EXACT_ALARM is only for alarm/calendar apps and
  Play-policy restricted; use SCHEDULE_EXACT_ALARM (maxSdkVersion not set) + `canScheduleExactAlarms()`; if not granted, use setAndAllowWhileIdle (inexact) and show
  a one-time explainer in Settings with a button to ACTION_REQUEST_SCHEDULE_EXACT_ALARM. Exact: setExactAndAllowWhileIdle.
  PendingIntent per entry id (requestCode = entry id hash), extras date+text. `AlarmReceiver` posts notification "Now (hh:mm): <text>" with content intent opening
  MainActivity with `daily/{date}` deep link. `BootReceiver` for BOOT_COMPLETED, TIMEZONE_CHANGED, TIME_SET, MY_PACKAGE_REPLACED -> reschedule all future entries.
  Schedule/cancel on entry edit/delete. Never schedule past times (use AlarmTimes). Request POST_NOTIFICATIONS (API 33+) only the first time a timed entry gets text.
  Also handle ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> reschedule.
- Unit test for the scheduler's selection (which entries get alarms) via pure functions; Robolectric test that the boot receiver reschedules (ShadowAlarmManager) if cheap.

## Done = `gradle-brief.ps1 assembleDebug testDebugUnitTest recordRoborazziDebug` green, zero new `w:` lines if practical.