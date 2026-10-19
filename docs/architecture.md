# Architecture

Single module `:app`, package `com.shibi.commentarii`. All data local (Room + DataStore); no network.

## Data model (Room, `data/Entities.kt`)
- `pages` (id, type, title, date?, folderId?, pinned, createdAt, updatedAt, lastOpenedAt?). `date` is unique, so one DAILY page per day. `type` is `PageType` (DAILY, NOTE).
- `section_blocks` (pageId -> pages, CASCADE; kind, position, payloadJson): the ordered sections of a page.
- `task_items` (pageId, section, position, text, done, carriedTo?): list-style sections (priorities, to-do, don't forget, grateful). `carriedTo` marks the original of a rolled-over task.
- `schedule_entries` (pageId, date, minuteOfDay, text, isCustom): the day schedule (6:00-22:00 half-hour seed rows).
- `moods` (date PK, 1..5) and `self_care_items` / `self_care_checks` (itemId+date PK) are day-keyed, not page-keyed, so streaks work across days.
- `folders` (pages.folderId SET_NULL on delete), `tags` (unique name) and `page_tags` (pageId+tagId, CASCADE both ways).
- `page_search`: FTS4 (unicode61), rowid = page id.
- Converters: LocalDate <-> epoch day, Instant <-> epoch millis.

## Section blocks and JSON payloads
Every page has ordered `section_blocks` rows. Structured data (tasks, schedule, mood, self-care) lives in its own tables; free-form sections (NOTES, RICH_TEXT) store a `BlockDocument` (list of `DocBlock(type, text, ...)`) serialised in `payloadJson`. New free-form section kinds need no migration.

## Search (FTS)
No triggers. `PageRepository.rebuildFts(pageId)` rewrites the page row: title plus flattened text of tasks, schedule and document sections. It is called after each save that can change searchable text. `search()` turns user input into a prefix MATCH (`toFtsMatch`), sanitising operators, and returns pinned pages first. The FTS table is never exported; import rebuilds it.

## Repositories and DI
Manual DI through `AppContainer` (created in `CommentariiApp`). It owns the `CommentariiDatabase`, `SettingsRepository` (DataStore: lock, export reminder, last export, last opened date, reminder dismissal, dark mode), `PageRepository`, `SelfCareRepository` and `ExportImport`. DAOs: `PageDao` (pages, blocks, tasks, schedule, mood, FTS, folders, tags, backup bulk methods), `SelfCareDao`.

## Domain (pure functions, `domain/`)
No Android dependencies, unit tested: `Rollover` (carry unfinished tasks to the next day), `Streaks` (self-care streak counting), `AlarmTimes` (next reminder times).

## Export / import (`backup/ExportImport.kt`)
One JSON document, `formatVersion` = 1:
`folders, tags, pages, sectionBlocks, tasks, schedule, moods, selfCareItems, selfCareChecks, pageTags, settings`.
Dates are epoch days, instants epoch millis, ids preserved so relations survive. Settings include exportReminderEnabled, darkMode, lastOpenedDate, lastExportAt, reminderDismissedAt; `lockEnabled` is deliberately never exported or changed.
- `export()`: reads all tables in one transaction; deterministic ordering, so export -> import -> export is byte-identical.
- `import(json)`: parses and validates fully first (bad JSON or formatVersion != 1 throws `BackupFormatException` with no data touched), then in a single transaction deletes everything, inserts with original ids, rebuilds FTS for every page, and applies settings afterwards.
- Callers (UI, later) are responsible for setting `lastExportAt` after a successful export.

## Adding a new template type
Add a `PageType` constant, the section kinds it needs (`SectionKind`), seed its `section_blocks` in `PageRepository`, and write a renderer composable for each kind. No migration is needed for JSON-payload sections; only genuinely new relational data needs a table, a DAO, a Room version bump with a Migration, and an entry in `ExportImport` (bump `formatVersion` only if the layout changes incompatibly).
