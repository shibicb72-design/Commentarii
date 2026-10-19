package com.shibi.commentarii.ui.lock

/** What the lock screen should do right now. */
enum class UnlockAction { NONE, PROMPT, SHOW_EXPLANATION }

/**
 * Pure unlock decision. With the lock enabled and nothing to authenticate with (no biometric, no screen lock) the app
 * stays locked and explains how to set one up; it never unlocks without a successful authentication.
 */
fun unlockAction(lockEnabled: Boolean, authAvailable: Boolean): UnlockAction = when {
    !lockEnabled -> UnlockAction.NONE
    authAvailable -> UnlockAction.PROMPT
    else -> UnlockAction.SHOW_EXPLANATION
}

/**
 * Settings lock toggle. Turning the lock on OR off needs a successful authentication; cancel/failure leaves the setting
 * unchanged. [authenticate] reports its result through the callback (true = success). If authentication is not
 * [available], [onUnavailable] is called and nothing changes.
 */
fun requestLockChange(
    enable: Boolean,
    available: Boolean,
    authenticate: ((Boolean) -> Unit) -> Unit,
    apply: (Boolean) -> Unit,
    onUnavailable: () -> Unit,
) {
    if (!available) {
        onUnavailable()
        return
    }
    authenticate { ok -> if (ok) apply(enable) }
}
