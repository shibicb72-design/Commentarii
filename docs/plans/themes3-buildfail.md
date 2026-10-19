# themes3 build failure (log: C:\Users\shibi\cc_commentarii\themes3-gradle-matcha.log)

Build: `:app:testDebugUnitTest` FAILED in 51s. 192 tests completed, 1 failed. No `e:` compile errors.

- `app/src/test/java/com/shibi/commentarii/ui/theme/ThemeCardTest.kt:19` — `ThemeCardTest > dailyTitlesGetDistinctStickersPerTheme` — `java.lang.AssertionError`. Assertion `assertEquals("$id: $doodles", 6, doodles.toSet().size)`: the six daily titles do not get six distinct doodles for one theme. The log does not show the failing theme id or the doodle list; the message is only in the test report at `app/build/reports/tests/testDebugUnitTest/index.html`.

Likely cause to check: `cardDecor(...)` doodle selection in `app/src/main/java/com/shibi/commentarii/ui/theme/ThemeCard.kt` (or `DoodleKit.kt`) returns repeats for a theme whose `cornerDoodles(id)` pool is smaller than 6 (`lavender_moon` and `matcha_tea` were the most recent additions, so check those first).
