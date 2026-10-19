package com.shibi.commentarii

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.shibi.commentarii.data.CommentariiDatabase
import com.shibi.commentarii.data.PageRepository
import com.shibi.commentarii.data.SelfCareRepository
import com.shibi.commentarii.data.SettingsRepository
import java.time.Clock
import java.time.ZoneId

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Manual DI container. */
class AppContainer(context: Context) {
    val clock: Clock = Clock.systemDefaultZone()
    val zone: ZoneId = clock.zone
    val db: CommentariiDatabase = CommentariiDatabase.build(context)
    val pages = PageRepository(db, clock, zone)
    val selfCare = SelfCareRepository(db)
    val settings = SettingsRepository(context.applicationContext.settingsDataStore)
}

class CommentariiApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
