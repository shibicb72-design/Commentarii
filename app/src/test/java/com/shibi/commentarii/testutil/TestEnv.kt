package com.shibi.commentarii.testutil

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.shibi.commentarii.backup.ExportImport
import com.shibi.commentarii.data.CommentariiDatabase
import com.shibi.commentarii.data.PageRepository
import com.shibi.commentarii.data.SettingsRepository
import com.shibi.commentarii.debug.DebugLog
import com.shibi.commentarii.notify.AlarmScheduler
import java.io.File
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.rules.TemporaryFolder

/**
 * The data layer without an AppContainer: in-memory Room, a DataStore on its own temp file, and the repository, scheduler
 * and export/import built directly on [clock]. Its settings cannot leak, unlike the real container's. Its scheduler does share
 * the app's one AlarmManager, and alarms are keyed by row id, so saving a schedule through [pages] can cancel or replace a real
 * container's alarm. Don't do that next to a real container. Call [close] in @After.
 */
class TestEnv(tmp: TemporaryFolder, val clock: Clock) {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val db: CommentariiDatabase =
        Room.inMemoryDatabaseBuilder(context, CommentariiDatabase::class.java).allowMainThreadQueries().build()
    val settings = SettingsRepository(
        PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "${UUID.randomUUID()}.preferences_pb") },
    )
    val log = DebugLog(tmp.newFolder(), clock)
    val scheduler = AlarmScheduler(context, clock, log)
    val pages = PageRepository(db, clock, clock.zone, scheduler)
    val io = ExportImport(db, settings, pages)

    fun close() {
        db.close()
        scope.cancel()
    }
}
