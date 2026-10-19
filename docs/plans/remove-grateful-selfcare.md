# Plan: remove-grateful-selfcare (branch `opus/remove-grateful-selfcare`)
Goal: no "Today I Am Grateful For" / "Self Care" UI anywhere (Daily, Week, Month, Settings): no streaks, habit grid, habit summary, editor.
Keep untouched: DB version, `Entities.kt` (incl. `SectionKind`/`TaskSection` constants and `*_KINDS` seeding lists), `Daos.kt` (`SelfCareDao`),
`CommentariiDatabase.kt`, `backup/ExportImport.kt` (still round-trips `selfCareItems`/`selfCareChecks`), `PageRepository.kt:36`
(`hasUserData` still counts legacy checks). Line numbers are from `main` @ 6fa61f0; edit bottom-up within a file so ranges stay valid.
After every file, Grep it for now-unused imports and remove them. Paths are under `app/src/main/java/com/shibi/commentarii/` unless stated.

## STEP A: Daily + Settings + app wiring
1. `ui/daily/DailyPageContent.kt`: delete 99-106 (private `SelfCare` wrapper), 72-73 (compact items), 57-58 (two-column left column).
2. `ui/daily/DailySections.kt`
   - Delete 321-339 `StreakBadge`, 279-319 `SelfCareRow`, 241-277 `SelfCareCard`, 141-164 `GratefulCard` (with blank separators).
   - Likely unused imports (verify): `painterResource`, `semantics`/`contentDescription` (45-46), `FirstLineRow`/`checklistTextStyle`
     (51-52), `Icons.Filled.Check/Edit/Delete`. `CircleShape`, `AppColors`, arrow icons stay (still used).
   - Delete `app/src/main/res/drawable/ic_heart.xml` (only reference was line 151).
3. `ui/daily/DailyUiState.kt`
   - Delete 89-92 (`onToggleSelfCare`..`onRemoveSelfCare`) and 85 (`grateful: TaskListActions`).
   - `tasksOf` 50-55 / `withTasks` 57-62: `DONT_FORGET` becomes the `else` branch (same pattern as `WeekUiState` 36-46).
   - Delete 42-43 (`grateful`, `selfCare`) and 23 (`SelfCareUi`).
4. `ui/daily/DailyViewModel.kt`
   - Companion: delete 413 `STREAK_WINDOW_DAYS`, 411 `MIN_GRATEFUL`.
   - `buildActions`: delete 279-286 (self-care callbacks) and 271 (`grateful = taskActions(...)`).
   - `load()`: delete 171-186 (self-care `combine` collector) and 146 (`grateful = pad(...)`).
   - Delete 55 (`private val selfCare`) and import 19 `streaksFor`. Keep `combine` import (used at 76).
5. `ui/daily/DailySample.kt`: delete 32-44 (`grateful = ...`, `selfCare = ...`).
6. `ui/settings/SettingsScreen.kt`: delete 402-423 `NameDialog` (only the editor used it), 349-400 `SelfCareEditor`,
   302-304 (Self Care `SectionCard`). Remove imports 30-31 (`KeyboardArrowUp/Down`) and, if Grep shows no other use,
   `AlertDialog`, `OutlinedTextField`, `Icons.Filled.Edit/Delete`, `SmallIconButton`, `AddRowButton`. Keep `rememberSaveable` (~427).
7. `ui/settings/SettingsState.kt`: delete 46-52 `movedIds` (self-care reorder only), 37-41 (self-care callbacks), 21 `selfCare`, 7 `SelfCareEditUi`.
8. `ui/settings/SettingsViewModel.kt`: delete 98-105 (self-care callbacks); in `base` 31-46 drop `selfCare.observeItems()` (36),
   the `items` lambda param (37) and line 43; delete 27 (`private val selfCare`).
9. `CommentariiApp.kt`: delete 47 (`val selfCare = SelfCareRepository(db)`) and import 17.
10. Delete `data/SelfCareRepository.kt` and `domain/Streaks.kt` (dead after A4/A8/A9).
11. Tests (needed for Step A to compile), under `app/src/test/java/com/shibi/commentarii/`:
    - Delete `ui/settings/SettingsStateTest.kt` (only `movedIds`), `domain/StreaksTest.kt`, `ui/week/HabitSyncTest.kt`.
    - `data/PageRepositoryTest.kt` 143-150: rename to `hasUserDataTrueForLegacySelfCareCheck`; replace the repository with
      `val id = db.selfCareDao().insertItem(SelfCareItemEntity(name = "Walk", position = 0))`, `assertFalse`,
      `db.selfCareDao().insertCheck(SelfCareCheckEntity(id, day))`, `assertTrue` (same package, no imports).
    - `ui/ScreenshotTest.kt`: delete 128-131 (`SelfCareCard(...)`) and imports 18-19.
12. `app/src/main/res/values/strings.xml`: delete 167 `settings_add_self_care`; 59-61 `daily_self_care_hint`, `daily_streak_badge`,
    `daily_streak_description`; 47 `daily_grateful_hint`; 26-27 `section_grateful`, `section_self_care`.
    Keep `daily_name_hint` (DailyHeader:212), `daily_add_item`, `daily_confirm`, `daily_rename` (still used).

## STEP B: Week + Month + tests/screenshots + docs
1. `ui/week/WeekPageContent.kt`: delete 77 (compact `WeekHabitCard`) and 61 (two-column).
2. `ui/week/WeekSections.kt`: delete 92-124 (`WeekHabitCard` + KDoc); drop imports left unused (e.g. `CircleCheck`, `Box`).
   Line 155 (`WeekMoodCard`): `R.string.habit_summary_none` -> `R.string.mood_none_short` (see B12).
3. `ui/week/WeekUiState.kt`: delete 56 `onToggleHabit`, 27 `habits`, 16-17 `WeekHabitUi`.
4. `ui/week/WeekViewModel.kt`
   - Delete 146 (`onToggleHabit`).
   - `summarise` 105-129: drop params 108-109, line 123 (`weekGrid`), line 127 (`habits = ...`).
   - `Summary` 97-103: drop `habits` (100) and from `applyTo` (102).
   - `load()` 84-89: `combine(pages.observeDailyTasks(monday, last), pages.observeMoods(monday, last)) { daily, moods -> summarise(daily, moods) }`.
   - Delete 37 (`selfCare`), imports 12-13 (`SelfCare*Entity`), 17 (`weekGrid`).
5. `ui/week/WeekSample.kt`: delete 30-34 (`habits = ...`); line 37 -> "Finished the report early and kept up the morning walks."
6. `ui/month/MonthPageContent.kt`: delete 72 and 56 (`HabitSummaryCard`); KDoc line 30 "left: calendar and habits" -> "left: calendar and review".
7. `ui/month/MonthSections.kt`: delete 182-200 (`HabitSummaryCard`); drop imports left unused.
8. `ui/month/MonthUiState.kt`: delete 31 `habits`, 20-21 `MonthHabitUi`.
9. `ui/month/MonthViewModel.kt`: delete 98-108 (self-care collector), 37 `selfCare`, import 16 `monthSummary`. Keep `combine` (91).
10. `ui/month/MonthSample.kt`: delete 35-39 (`habits = ...`).
11. Delete `domain/Habits.kt` and `app/src/test/java/com/shibi/commentarii/domain/HabitsTest.kt`.
12. `strings.xml`: delete 89 `habit_summary_empty`, 87 `habit_summary_fmt`; rename 88 `habit_summary_none` -> `mood_none_short` (value "-").
13. Sweep: `git grep -niE "gratef|selfCare|self_care|habit|streak" app/src` may only hit Entities.kt, Daos.kt, CommentariiDatabase.kt,
    ExportImport.kt, PageRepository.kt:36, ExportImportRoundTripTest.kt, PageRepositoryTest.kt (legacy test).
14. `gradle-runner`: `assembleDebug testDebugUnitTest`, then `recordRoborazziDebug`. `screenshot-reviewer`: daily/week/month (compact +
    two-column, light + dark) and `checklist_alignment_light` show no Grateful/Self Care cards and no gaps or contrast problems.
15. `docs/architecture.md`: 8 drop "grateful" (legacy GRATEFUL rows stay, not shown); 10 self-care tables = legacy, kept only for backup
    round-trip; 20 drop "self-care"; 21-22 `SELF_CARE (...)` -> "SELF_CARE (legacy, not shown)"; 33 remove `SelfCareRepository`;
    36 remove `Streaks` and `Habits`; delete 38-39 (habit denominator rule); 42 drop the habit-toggle clause; 46 unchanged.

Risks: A3 `else` change must not send other sections to a visible list (Daily only edits PRIORITY/TODO/DONT_FORGET now).
Two-column Daily left column gets shorter than the right one; acceptable. No migration: old GRATEFUL tasks and self-care rows stay
in the DB and in backups, invisible.
