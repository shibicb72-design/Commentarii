package com.shibi.commentarii.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    fun enablingAnimationOnUnsetThemeIsNoOp() = runBlocking {
        val s = repo()
        s.setThemeAnimated("cloud_drift", true)
        assertEquals(emptySet<String>(), s.staticThemeIds.first())
    }
}
