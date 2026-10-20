# Architecture

Single module `:app`, package `com.shibi.commentarii`. All data local (Room + DataStore); no network.

## Data model (Room, `data/Entities.kt`)
- `pages` (id, type, title, date?, folderId?, pinned, createdAt, updatedAt, lastOpenedAt?). `(type, date)` is unique (index `index_pages_type_date`), so one page per type per date: a DAILY page, a WEEKLY page keyed by its Monday and a MONTHLY page keyed by the 1st can all share a date. `type` is `PageType` (DAILY, NOTE, WEEKLY, MONTHLY). Notes have a null date and may repeat.
- `section_blocks` (pageId -> pages, CASCADE; kind, position, payloadJson): the ordered sections of a page.
- `task_items` (pageId, section, position, text, done, carriedTo?): list-style sections (priorities, to-do, don't forget). `carriedTo` marks the original of a rolled-over task.
- `schedule_entries` (pageId, date, minuteOfDay, text, isCustom): the day schedule (6:00-22:00 half-hour seed rows).
- `moods` (date PK, 1..5) and `self_care_items` / `self_care_checks` (itemId+date PK) are day-keyed, not page-keyed. The self-care tables are legacy: kept only for the backup round-trip, no longer shown or edited. Old GRATEFUL tasks stay in the database, invisible.
- `folders` (pages.folderId SET_NULL on delete), `tags` (unique name) and `page_tags` (pageId+tagId, CASCADE both ways).
- `page_search`: FTS4 (unicode61), rowid = page id.
- Converters: LocalDate <-> epoch day, Instant <-> epoch millis.

## Database versions
- v1: initial schema, `index_pages_date` unique on `date`.
- v2: `MIGRATION_1_2` drops `index_pages_date` and creates the unique `index_pages_type_date(type, date)`. No new table; `MigrationTest` checks that data survives and that WEEKLY and DAILY can share a date. Registered in `CommentariiDatabase.build` via `addMigrations`; schemas are exported to `app/schemas` (`1.json`, `2.json`); the test builds the v1 database from `1.json` itself (Robolectric does not see schema files as test assets, so `MigrationTestHelper` is not used).

## Section blocks and JSON payloads
Every page has ordered `section_blocks` rows. Structured data (tasks, schedule, mood) lives in its own tables; free-form sections (NOTES, RICH_TEXT) store a `BlockDocument` (list of `DocBlock(type, text, ...)`) serialised in `payloadJson`. New free-form section kinds need no migration.
- WEEKLY_KINDS: GOALS, TODO, DONT_FORGET, SELF_CARE (legacy, not shown), MOOD (row of the day-keyed moods), NOTES, REVIEW_WELL, REVIEW_IMPROVE.
- MONTHLY_KINDS: GOALS, EVENTS, TODO, SELF_CARE (legacy, not shown), NOTES, REVIEW.
- Document kinds (NOTES, RICH_TEXT, REVIEW_WELL, REVIEW_IMPROVE, REVIEW) hold a `BlockDocument`. GOALS, TODO and DONT_FORGET are `task_items` rows of the period page (section GOALS/TODO/DONT_FORGET); the weekly page allows at most 3 goals (UI and view model), the monthly page any number.
- EVENTS holds an `EventList(items = [EventItem(epochDay, text)])` in `data/EventList.kt`, kept sorted by date. Events have no reminders and no alarm code.
- Weekly and monthly pages are created by `getOrCreateWeekly(monday)` / `getOrCreateMonthly(firstOfMonth)` (idempotent, seeded blocks, fixed English titles such as "Week of Oct 12 – Oct 18 (W42)" and "October 2026" for search; the screen formats titles from `strings.xml`). `observeWeekly` / `observeMonthly` never create a page; the daily "This week & month" card uses them.
- Visiting a week or month page never creates DAILY pages; tapping a day opens `daily/{date}`.
- Rollover looks at DAILY pages only (`getLatestDailyBefore`), so weekly and monthly tasks are never carried over.

## Search (FTS)
No triggers. `PageRepository.rebuildFts(pageId)` rewrites the page row: title plus flattened text of tasks, schedule, document sections (including the review kinds) and the EVENTS list. It is called after each save that can change searchable text. `search()` turns user input into a prefix MATCH (`toFtsMatch`), sanitising operators, and returns pinned pages first. The FTS table is never exported; import rebuilds it.

## Repositories and DI
Manual DI through `AppContainer` (created in `CommentariiApp`). It owns the `CommentariiDatabase`, `SettingsRepository` (DataStore: lock, export reminder, last export, last opened date, reminder dismissal, dark mode), `PageRepository` and `ExportImport`. DAOs: `PageDao` (pages, blocks, tasks, schedule, mood, FTS, folders, tags, backup bulk methods), `SelfCareDao`.

## Domain (pure functions, `domain/`)
No Android dependencies, unit tested: `Rollover` (carry unfinished tasks to the next day), `Periods` (Monday-first weeks, ISO week, month grid padded to whole weeks, week/month route parsing that snaps or falls back to the current period), `AlarmTimes` (exact-minute reminder times; past entries are skipped).

## Calendar and day content
The month calendar dots a day that has a mood, a non-blank task or schedule text, or a non-empty NOTES document (`PageDao.observeContentDates`), and tints it by mood with a theme container colour plus its on-colour. Week and month pages read daily data through read-only DAO queries (`observeDailyTasks`, `observeMoods`, `observeContentDates`).

## Export / import (`backup/ExportImport.kt`)
One JSON document, `formatVersion` = 1:
`folders, tags, pages, sectionBlocks, tasks, schedule, moods, selfCareItems, selfCareChecks, pageTags, settings`.
Page types are plain strings and there is no type whitelist, so weekly and monthly pages round-trip unchanged and `formatVersion` stays 1 (v1 files import as before). Dates are epoch days, instants epoch millis, ids preserved so relations survive. Settings include exportReminderEnabled, darkMode, lastOpenedDate, lastExportAt, reminderDismissedAt; `lockEnabled` is deliberately never exported or changed.
- `export()`: reads all tables in one transaction; deterministic ordering, so export -> import -> export is byte-identical.
- `import(json)`: parses and validates fully first (bad JSON or formatVersion != 1 throws `BackupFormatException` with no data touched), then in a single transaction deletes everything, inserts with original ids, rebuilds FTS for every page, and applies settings afterwards.
- Callers (UI, later) are responsible for setting `lastExportAt` after a successful export.

## Adding a new template type
Add a `PageType` constant, the section kinds it needs (`SectionKind`), seed its `section_blocks` in `PageRepository`, and write a renderer composable for each kind. No migration is needed for JSON-payload sections; only genuinely new relational data needs a table, a DAO, a Room version bump with a Migration, and an entry in `ExportImport` (bump `formatVersion` only if the layout changes incompatibly).
