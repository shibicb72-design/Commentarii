package com.shibi.commentarii.ui.lock

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL

/** Opens the system screen where a fingerprint or screen lock can be set up. Shared by Settings and the lock screen. */
object SecuritySettings {
    /** API 30+: enrolment screen for biometric or device credential; below: the general security settings. */
    fun primaryIntent(sdk: Int = Build.VERSION.SDK_INT): Intent =
        if (sdk >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_BIOMETRIC_ENROLL).putExtra(
                Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                BIOMETRIC_WEAK or DEVICE_CREDENTIAL,
            )
        } else {
            fallbackIntent()
        }

    fun fallbackIntent(): Intent = Intent(Settings.ACTION_SECURITY_SETTINGS)

    fun open(context: Context) {
        try {
            context.startActivity(primaryIntent().newTask(context))
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(fallbackIntent().newTask(context))
            } catch (_: ActivityNotFoundException) {
                // Nothing can show security settings on this device; the explanation stays visible.
            }
        }
    }

    private fun Intent.newTask(context: Context): Intent =
        apply { if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
}
