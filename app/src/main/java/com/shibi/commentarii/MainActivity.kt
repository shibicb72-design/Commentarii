package com.shibi.commentarii

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shibi.commentarii.data.DarkMode
import com.shibi.commentarii.notify.Notifications
import com.shibi.commentarii.ui.AppNav
import com.shibi.commentarii.ui.Routes
import com.shibi.commentarii.ui.lock.BiometricAuth
import com.shibi.commentarii.ui.lock.LockScreen
import com.shibi.commentarii.ui.lock.NavRestore
import com.shibi.commentarii.ui.lock.SecuritySettings
import com.shibi.commentarii.ui.lock.UnlockAction
import com.shibi.commentarii.ui.lock.unlockAction
import com.shibi.commentarii.ui.theme.AppThemes
import com.shibi.commentarii.ui.theme.CommentariiTheme
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : AppCompatActivity() {
    /** Date requested by a reminder notification; consumed by AppNav. */
    private var openDate by mutableStateOf<LocalDate?>(null)

    /** False when no biometric or screen lock is enrolled; re-checked on resume (e.g. after visiting system settings). */
    private var authAvailable by mutableStateOf(true)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            (application as CommentariiApp).container.refreshNotificationState()
        }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as CommentariiApp).container
        readDate(intent)
        authAvailable = BiometricAuth.isAvailable(this)
        // Read before the first frame, so the saved look never flashes the default and nothing shows before we know whether to lock.
        val savedDarkMode = runBlocking { container.settings.darkMode.first() }
        val savedLock = runBlocking { container.settings.lockEnabled.first() }
        val savedTheme = runBlocking { container.settings.themeId.first() }
        val savedStatic = runBlocking { container.settings.staticThemeIds.first() }
        setContent {
            val darkMode by container.settings.darkMode.collectAsStateWithLifecycle(savedDarkMode)
            val dark = when (darkMode) {
                DarkMode.SYSTEM -> isSystemInDarkTheme()
                DarkMode.LIGHT -> false
                DarkMode.DARK -> true
            }
            val lockEnabled by container.settings.lockEnabled.collectAsStateWithLifecycle(savedLock)
            val themeId by container.settings.themeId.collectAsStateWithLifecycle(savedTheme)
            val staticThemeIds by container.settings.staticThemeIds.collectAsStateWithLifecycle(savedStatic)
            val appTheme = AppThemes.byId(themeId)
            val locked by container.lock.locked.collectAsStateWithLifecycle()
            val askNotifications by container.notificationPermissionRequest.collectAsStateWithLifecycle()
            val showLock = lockEnabled && locked
            // Back stack of the unlocked app, kept as route strings only (no screen is composed while locked).
            var savedRoutes by rememberSaveable { mutableStateOf("") }
            // What the Week and Month tabs last treated as current, which must outlive a lock for AppNav to spot a rollover.
            val weekHome = rememberSaveable { mutableStateOf(Routes.week(LocalDate.now(container.clock))) }
            val monthHome = rememberSaveable { mutableStateOf(Routes.month(YearMonth.now(container.clock))) }

            LaunchedEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            LaunchedEffect(lockEnabled) {
                if (lockEnabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
            LaunchedEffect(showLock, authAvailable) {
                if (unlockAction(showLock, authAvailable) == UnlockAction.PROMPT) promptUnlock(container)
            }
            LaunchedEffect(askNotifications) {
                if (askNotifications) {
                    container.notificationPermissionHandled()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
            CommentariiTheme(
                dark = dark,
                theme = appTheme,
                animate = appTheme.animated && appTheme.id !in staticThemeIds,
            ) {
                when {
                    showLock -> LockScreen(
                        onUnlock = { promptUnlock(container) },
                        authAvailable = authAvailable,
                        onOpenSecuritySettings = { SecuritySettings.open(this@MainActivity) },
                    )
                    else -> AppNav(
                        container,
                        calculateWindowSizeClass(this).widthSizeClass,
                        openDate = openDate,
                        onOpenDateHandled = { openDate = null },
                        restoreRoutes = remember { NavRestore.decode(savedRoutes) },
                        onRoutesChanged = { savedRoutes = NavRestore.encode(it) },
                        weekHome = weekHome,
                        monthHome = monthHome,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readDate(intent)
    }

    private fun readDate(intent: Intent?) {
        intent?.getStringExtra(Notifications.EXTRA_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.let { openDate = it }
    }

    override fun onResume() {
        super.onResume()
        (application as CommentariiApp).container.refreshNotificationState()
        authAvailable = BiometricAuth.isAvailable(this)
    }

    /** Never unlocks without a successful authentication; with nothing to authenticate with the lock screen explains. */
    private fun promptUnlock(container: AppContainer) {
        authAvailable = BiometricAuth.isAvailable(this)
        if (unlockAction(true, authAvailable) != UnlockAction.PROMPT) return
        BiometricAuth.authenticate(this, onSuccess = { container.lock.unlock() })
    }
}
