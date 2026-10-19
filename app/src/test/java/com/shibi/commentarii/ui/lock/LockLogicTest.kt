package com.shibi.commentarii.ui.lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockLogicTest {
    @Test fun lockDisabledNeedsNothing() = assertEquals(UnlockAction.NONE, unlockAction(false, true))
    @Test fun lockDisabledAndUnavailableNeedsNothing() = assertEquals(UnlockAction.NONE, unlockAction(false, false))
    @Test fun enabledAndAvailablePrompts() = assertEquals(UnlockAction.PROMPT, unlockAction(true, true))
    @Test fun enabledAndUnavailableExplainsNeverUnlocks() =
        assertEquals(UnlockAction.SHOW_EXPLANATION, unlockAction(true, false))

    private class Calls {
        var applied: Boolean? = null
        var unavailable = false
    }

    private fun run(enable: Boolean, available: Boolean, authResult: Boolean?): Calls {
        val c = Calls()
        requestLockChange(
            enable = enable,
            available = available,
            authenticate = { done -> if (authResult != null) done(authResult) },
            apply = { c.applied = it },
            onUnavailable = { c.unavailable = true },
        )
        return c
    }

    @Test fun turnOffWithSuccessDisables() = assertEquals(false, run(false, true, true).applied)
    @Test fun turnOffWithFailureKeepsEnabled() = assertEquals(null, run(false, true, false).applied)
    @Test fun turnOffCancelledKeepsEnabled() = assertEquals(null, run(false, true, null).applied)
    @Test fun turnOnWithSuccessEnables() = assertEquals(true, run(true, true, true).applied)
    @Test fun turnOnWithFailureStaysOff() = assertEquals(null, run(true, true, false).applied)

    @Test
    fun unavailableShowsHelpAndChangesNothing() {
        for (enable in listOf(true, false)) {
            val c = run(enable, false, true)
            assertTrue(c.unavailable)
            assertEquals(null, c.applied)
        }
        assertFalse(run(true, true, true).unavailable)
    }
}
