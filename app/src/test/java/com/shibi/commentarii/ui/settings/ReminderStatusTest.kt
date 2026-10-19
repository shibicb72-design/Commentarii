package com.shibi.commentarii.ui.settings

import com.shibi.commentarii.R
import com.shibi.commentarii.debug.DebugLog
import com.shibi.commentarii.domain.AlarmKind
import java.nio.file.Files
import java.time.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderStatusTest {
    @Test
    fun batteryStatusMapsBothStates() {
        assertEquals(R.string.settings_battery_unrestricted, batteryStatusRes(true))
        assertEquals(R.string.settings_battery_optimised, batteryStatusRes(false))
    }

    @Test
    fun outcomeMessagesOnlyForPast() {
        assertEquals(R.string.outcome_past, outcomeMessageRes(AlarmKind.PAST))
        assertNull(outcomeMessageRes(AlarmKind.SCHEDULED))
        assertNull(outcomeMessageRes(AlarmKind.BLANK))
    }

    @Test
    fun debugLogViewModelCopiesExactlyReadAllAndClears() {
        val dir = Files.createTempDirectory("dbg").toFile()
        val log = DebugLog(dir, Clock.systemUTC())
        log.log("A", "one")
        log.log("B", "two")
        val vm = DebugLogViewModel(log)
        assertEquals(log.readAll(), vm.copyText())
        assertEquals(2, vm.lines().size)
        val before = vm.version.value
        vm.clear()
        assertTrue(vm.lines().isEmpty())
        assertEquals("", vm.copyText())
        assertTrue(vm.version.value > before)
        dir.deleteRecursively()
    }
}
