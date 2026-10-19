package com.shibi.commentarii.ui.lock

import android.os.Build
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.shibi.commentarii.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lock state: locked on cold start and after more than [timeoutMs] in the background. Whether the lock applies at
 * all (the setting) is decided by the caller; this only tracks "needs unlocking".
 */
class AppLock(
    private val timeoutMs: Long = BACKGROUND_TIMEOUT_MS,
    private val nowMs: () -> Long = { SystemClock.elapsedRealtime() },
) {
    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()
    private var backgroundedAt: Long? = null

    fun unlock() {
        _locked.value = false
    }

    fun onBackground() {
        backgroundedAt = nowMs()
    }

    fun onForeground() {
        if (shouldRelock(backgroundedAt, nowMs(), timeoutMs)) _locked.value = true
        backgroundedAt = null
    }

    companion object {
        const val BACKGROUND_TIMEOUT_MS = 60_000L

        /** True when the app was backgrounded for strictly more than [timeoutMs]. */
        fun shouldRelock(backgroundedAt: Long?, now: Long, timeoutMs: Long): Boolean =
            backgroundedAt != null && now - backgroundedAt > timeoutMs
    }
}

/** BiometricPrompt wrapper: biometric (weak or strong) or device credential. */
object BiometricAuth {
    private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** False when the device has neither an enrolled biometric nor a screen lock. */
    fun isAvailable(activity: FragmentActivity): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
        } else {
            // API 29: the combined authenticator mask is unsupported; BIOMETRIC_WEAK check plus device credential.
            val bm = BiometricManager.from(activity)
            bm.canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS ||
                bm.canAuthenticate(DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
        }

    fun authenticate(activity: FragmentActivity, onSuccess: () -> Unit, onFailure: () -> Unit = {}) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFailure()
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.lock_prompt_title))
            .setSubtitle(activity.getString(R.string.lock_prompt_subtitle))
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setAllowedAuthenticators(AUTHENTICATORS)
                } else {
                    @Suppress("DEPRECATION")
                    setDeviceCredentialAllowed(true)
                }
            }
            .build()
        prompt.authenticate(info)
    }
}
