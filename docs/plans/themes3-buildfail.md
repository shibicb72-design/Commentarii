# Build failure: themes3 (from themes3-gradle-shared.log)

Source: `C:\Users\shibi\cc_commentarii\themes3-gradle-shared.log`
Task: `:app:compileDebugKotlin` FAILED (log line 42). Build ended `BUILD FAILED in 2s` (line 60).

## Errors

- `app/src/main/java/com/shibi/commentarii/ui/theme/ThemeCard.kt:139:41`: `Unresolved reference 'addOutline'.`

## Notes

- Only one Kotlin compile error (`e: ` lines); no test or other task failures in the log.
- "What went wrong" block (log line 49) is the standard Gradle task-failure wrapper for the same error.
- Not verified against source; the line number is taken from the compiler output.
