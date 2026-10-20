# Build failure S1 (themes)

Source: `C:\Users\shibi\cc_commentarii\themes-gradle-S1.log`

Compile errors (`^e: `): none. `compileDebugUnitTestKotlin` ran without errors.

Failing tests: 1 of 152.

- `SettingsRepositoryThemeTest > animatedFlagIsPerTheme` FAILED (log:59)
  - `java.io.IOException at FileStorage.kt:114`
  - Caused by: `java.io.IOException at FileStorage.kt:114`
  - Test source line not in log. Test file: `app/src/test/java/com/shibi/commentarii/data/SettingsRepositoryThemeTest.kt`

Gradle: `:app:testDebugUnitTest FAILED` (log:65), `BUILD FAILED in 53s` (log:77).
Full report: `app/build/reports/tests/testDebugUnitTest/index.html`
