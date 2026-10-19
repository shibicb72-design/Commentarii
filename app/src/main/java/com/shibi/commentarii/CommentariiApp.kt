package com.shibi.commentarii

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.shibi.commentarii.backup.ExportImport
import com.shibi.commentarii.data.CommentariiDatabase
import com.shibi.commentarii.data.PageRepository
import com.shibi.commentarii.data.SelfCareRepository
import com.shibi.commentarii.data.SettingsRepository
import com.shibi.commentarii.notify.AlarmScheduler
import com.shibi.commentarii.notify.Notifications
import com.shibi.commentarii.ui.lock.AppLock
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Manual DI container. */
class AppContainer(context: Context) {
    val clock: Clock = Clock.systemDefaultZone()
    val zone: ZoneId = clock.zone
    val db: CommentariiDatabase = CommentariiDatabase.build(context)
    val alarms = AlarmScheduler(context.applicationContext, clock)
    val pages = PageRepository(db, clock, zone, alarms)
    val selfCare = SelfCareRepository(db)
    val settings = SettingsRepository(context.applicationContext.settingsDataStore)
    val exportImport = ExportImport(db, settings, pages)
    val lock = AppLock()

    private val _notificationPermissionRequest = MutableStateFlow(false)

    /** True when the UI should ask for POST_NOTIFICATIONS now; the UI calls [notificationPermissionHandled]. */
    val notificationPermissionRequest: StateFlow<Boolean> = _notificationPermissionRequest.asStateFlow()

    fun notificationPermissionHandled() {
        _notificationPermissionRequest.value = false
    }

    /** Call when a timed entry has text; asks for POST_NOTIFICATIONS (API 33+) only the first time. */
    suspend fun onTimedEntryHasText() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (settings.notificationsAsked.first()) return
        settings.setNotificationsAsked(true)
        _notificationPermissionRequest.value = true
    }

    /** Re-registers alarms for all future entries. */
    suspend fun rescheduleAllAlarms() {
        alarms.rescheduleAll(pages.getUpcomingSchedule(LocalDate.now(clock).minusDays(1)))
    }
}

class CommentariiApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.ensureChannel(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = container.lock.onForeground()

                override fun onStop(owner: LifecycleOwner) = container.lock.onBackground()
            },
        )
    }
}
