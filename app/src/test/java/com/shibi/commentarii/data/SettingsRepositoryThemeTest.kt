package com.shibi.commentarii.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryThemeTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun repo() = SettingsRepository(
        PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "prefs.preferences_pb") },
    )

    /**
     * In-memory store for tests that need several writes: the file store's atomic rename fails on a second write on the
     * Windows host. Counts updateData calls so a test can assert that an operation is a single write.
     */
    private class MemoryStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        var writes = 0
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            writes++
            return transform(state.value).also { state.value = it }
        }
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun defaultsToRoseCreamAndAnimated() = runBlocking {
        val s = repo()
        assertEquals("rose_cream", s.themeId.first())
        assertEquals(emptySet<String>(), s.staticThemeIds.first())
    }

    @Test
    fun themeIdRoundTrips() = runBlocking {
        val s = repo()
        s.setThemeId("petal_fall")
        assertEquals("petal_fall", s.themeId.first())
    }

    @Test
    fun animatedFlagIsPerTheme() = runBlocking {
        // One write per store: repeated DataStore writes hit an IOException (atomic rename over an
        // existing file, FileStorage.kt:114) on the Windows host that runs these unit tests.
        val s = repo()
        s.setThemeAnimated("petal_fall", false)
        assertEquals(setOf("petal_fall"), s.staticThemeIds.first())
    }

    @Test
    fun overridesAreEmptyWhenAbsent() = runBlocking {
        assertEquals(emptyMap<String, String>(), repo().themeOverrides.first())
    }

    @Test
    fun overrideIsStoredPerThemeAsRawJson() = runBlocking {
        val s = repo()
        s.setThemeOverride("petal_fall", """{"v":1}""")
        assertEquals(mapOf("petal_fall" to """{"v":1}"""), s.themeOverrides.first())
        assertEquals("rose_cream", s.themeId.first())
    }

    @Test
    fun blankOrNullOverrideRemovesTheKeyAndLeavesOthers() = runBlocking {
        val s = SettingsRepository(MemoryStore())
        s.setThemeOverride("petal_fall", """{"v":1}""")
        s.setThemeOverride("matcha_tea", """{"v":1,"tape":"true"}""")
        s.setThemeOverride("petal_fall", "  ")
        assertEquals(mapOf("matcha_tea" to """{"v":1,"tape":"true"}"""), s.themeOverrides.first())
        s.setThemeOverride("matcha_tea", null)
        assertEquals(emptyMap<String, String>(), s.themeOverrides.first())
    }

    @Test
    fun resetCustomizationIsOneWriteThatRemovesOverrideAndRestoresAnimated() = runBlocking {
        val store = MemoryStore()
        val s = SettingsRepository(store)
        s.setThemeOverride("petal_fall", """{"v":1}""")
        s.setThemeOverride("matcha_tea", """{"v":1}""")
        s.setThemeAnimated("petal_fall", false)
        s.setThemeAnimated("matcha_tea", false)
        val before = store.writes
        s.resetThemeCustomization("petal_fall")
        assertEquals(1, store.writes - before)
        assertEquals(mapOf("matcha_tea" to """{"v":1}"""), s.themeOverrides.first())
        assertEquals(setOf("matcha_tea"), s.staticThemeIds.first())
    }

    @Test
    fun clearThemeOverridesKeepsOtherSettings() = runBlocking {
        val s = SettingsRepository(MemoryStore())
        s.setThemeId("petal_fall")
        s.setThemeOverride("petal_fall", """{"v":1}""")
        s.clearThemeOverrides()
        assertEquals(emptyMap<String, String>(), s.themeOverrides.first())
        assertEquals("petal_fall", s.themeId.first())
    }

    @Test
    fun garbageOverrideTextIsReturnedRawAndDoesNotCrashTheFlow() = runBlocking {
        val s = SettingsRepository(MemoryStore())
        s.setThemeOverride("petal_fall", "{not json")
        assertEquals(mapOf("petal_fall" to "{not json"), s.themeOverrides.first())
    }

    @Test
    fun enablingAnimationOnUnsetThemeIsNoOp() = runBlocking {
        val s = repo()
        s.setThemeAnimated("cloud_drift", true)
        assertEquals(emptySet<String>(), s.staticThemeIds.first())
    }
}
