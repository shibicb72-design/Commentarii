# fix4 - wrapping rows + load gate (branch opus/v2-week-month)
1. ui/daily/DailyComponents.kt: LineField wraps (no singleLine); `singleLine` -> `allowNewlines` (newlines become spaces otherwise, IME Done); new `enabled` (readOnly); CircleCheck/AddRowButton get `enabled`.
2. ui/daily/DailySections.kt: TodoCard, DontForgetCard, NotesCard take `enabled`; Notes uses allowNewlines. Priorities/grateful/schedule wrap via the shared LineField.
3. ui/common/PeriodCards.kt: GoalsCard/ReviewCard take `enabled`; Review uses allowNewlines.
4. ui/month/MonthSections.kt: EventsCard `enabled` (field, date button, remove, add).
5. ui/week/WeekSections.kt: WeekHabitCard `enabled` (checks inert until loaded); habit summary logic unchanged.
6. ui/week/WeekUiState.kt, ui/month/MonthUiState.kt: `loaded` (default true for samples/screenshots).
7. ui/week/WeekViewModel.kt, ui/month/MonthViewModel.kt: start `loaded = false`, set true in load() just before autosaver.markReady().
8. ui/week/WeekPageContent.kt, ui/month/MonthPageContent.kt: pass `enabled = state.loaded` to every editable card.
9. test ui/week/LoadGateTest.kt: edits rejected before load, accepted after (week + month).
10. ScreenshotTest checklist_alignment_light already has long To-Do/Don't Forget rows; it now shows them wrapped. docs/lessons.md updated.
