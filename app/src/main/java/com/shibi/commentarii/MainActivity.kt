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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shibi.commentarii.data.DarkMode
import com.shibi.commentarii.notify.Notifications
import com.shibi.commentarii.ui.AppNav
import com.shibi.commentarii.ui.lock.BiometricAuth
import com.shibi.commentarii.ui.lock.LockScreen
import com.shibi.commentarii.ui.theme.CommentariiTheme
import java.time.LocalDate

class MainActivity : AppCompatActivity() {
    /** Date requested by a reminder notification; consumed by AppNav. */
    private var openDate by mutableStateOf<LocalDate?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as CommentariiApp).container
        readDate(intent)
        setContent {
            val darkMode by container.settings.darkMode.collectAsStateWithLifecycle(DarkMode.SYSTEM)
            val dark = when (darkMode) {
                DarkMode.SYSTEM -> isSystemInDarkTheme()
                DarkMode.LIGHT -> false
                DarkMode.DARK -> true
            }
            // null until the setting has loaded, so nothing is shown before we know whether to lock.
            val lockEnabled by container.settings.lockEnabled.collectAsStateWithLifecycle<Boolean?>(null)
            val locked by container.lock.locked.collectAsStateWithLifecycle()
            val askNotifications by container.notificationPermissionRequest.collectAsStateWithLifecycle()
            val showLock = lockEnabled == true && locked

            LaunchedEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            LaunchedEffect(lockEnabled) {
                if (lockEnabled == true) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else if (lockEnabled == false) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
            LaunchedEffect(showLock) {
                if (showLock) promptUnlock(container)
            }
            LaunchedEffect(askNotifications) {
                if (askNotifications) {
                    container.notificationPermissionHandled()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
            CommentariiTheme(dark = dark) {
                when {
                    lockEnabled == null -> Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {}
                    showLock -> LockScreen(onUnlock = { promptUnlock(container) })
                    else -> AppNav(
                        container,
                        calculateWindowSizeClass(this).widthSizeClass,
                        openDate = openDate,
                        onOpenDateHandled = { openDate = null },
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

    private fun promptUnlock(container: AppContainer) {
        if (!BiometricAuth.isAvailable(this)) {
            // No biometric and no screen lock enrolled: there is nothing to authenticate with, so do not lock out.
            container.lock.unlock()
            return
        }
        BiometricAuth.authenticate(this, onSuccess = { container.lock.unlock() })
    }
}

