package com.shibi.commentarii

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.shibi.commentarii.backup.ExportImport
import com.shibi.commentarii.data.CommentariiDatabase
import com.shibi.commentarii.data.PageRepository
import com.shibi.commentarii.data.SettingsRepository
import com.shibi.commentarii.debug.DebugLog
import com.shibi.commentarii.notify.AlarmScheduler
import com.shibi.commentarii.notify.Notifications
import com.shibi.commentarii.ui.lock.AppLock
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Manual DI container. */
class AppContainer(context: Context) {
    private val appContext: Context = context.applicationContext
    val clock: Clock = Clock.systemDefaultZone()
    val zone: ZoneId = clock.zone
    val debugLog = DebugLog(appContext.filesDir, clock)
    val db: CommentariiDatabase = CommentariiDatabase.build(context)
    val alarms = AlarmScheduler(appContext, clock, debugLog)
    val pages = PageRepository(db, clock, zone, alarms)
    val settings = SettingsRepository(context.applicationContext.settingsDataStore)
    val exportImport = ExportImport(db, settings, pages)
    val lock = AppLock()

    /** Replaceable in tests. */
    internal var scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _notificationPermissionRequest = MutableStateFlow(false)

    /** True when the UI should ask for POST_NOTIFICATIONS now; the UI calls [notificationPermissionHandled]. */
    val notificationPermissionRequest: StateFlow<Boolean> = _notificationPermissionRequest.asStateFlow()

    private val _notificationsBlocked = MutableStateFlow(!areNotificationsEnabled())

    /** True when the system blocks this app's notifications (permission denied or switched off in settings). */
    val notificationsBlocked: StateFlow<Boolean> = _notificationsBlocked.asStateFlow()

    private val _notificationsDenied = MutableStateFlow(false)

    /** True on API 33+ once the permission was requested and it is still not granted. */
    val notificationsDenied: StateFlow<Boolean> = _notificationsDenied.asStateFlow()

    @Volatile private var permissionRequested = false

    private fun areNotificationsEnabled() = NotificationManagerCompat.from(appContext).areNotificationsEnabled()

    private fun permissionGranted() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    /** Re-reads the notification state; the UI calls this on resume. */
    fun refreshNotificationState() {
        _notificationsBlocked.value = !areNotificationsEnabled()
        _notificationsDenied.value =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && permissionRequested && !permissionGranted()
    }

    fun notificationPermissionHandled() {
        _notificationPermissionRequest.value = false
        refreshNotificationState()
    }

    /**
     * Call when a timed entry has text. On API 33+ asks for POST_NOTIFICATIONS whenever notifications are not
     * enabled (not only the first time); if the system no longer shows the dialog the UI offers the settings page.
     */
    suspend fun onTimedEntryHasText() {
        refreshNotificationState()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (areNotificationsEnabled()) return
        permissionRequested = true
        settings.setNotificationsAsked(true)
        _notificationPermissionRequest.value = true
    }

    /** Re-registers alarms for all future entries. */
    suspend fun rescheduleAllAlarms(reason: String) {
        try {
            alarms.rescheduleAll(pages.getUpcomingSchedule(LocalDate.now(clock).minusDays(1)), reason)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            debugLog.log("ERROR", "rescheduleAllAlarms($reason) failed: $t")
        }
    }

    /** Process came to the foreground: re-arm the lock and re-register alarms (e.g. after granting exact alarms). */
    fun onAppStart(): Job {
        lock.onForeground()
        refreshNotificationState()
        return scope.launch { rescheduleAllAlarms("app resume") }
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
                override fun onStart(owner: LifecycleOwner) {
                    container.onAppStart()
                }

                override fun onStop(owner: LifecycleOwner) = container.lock.onBackground()
            },
        )
    }
}
