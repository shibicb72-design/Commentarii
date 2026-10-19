package com.shibi.commentarii.ui.week

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.shibi.commentarii.AppContainer
import com.shibi.commentarii.domain.weekStart
import com.shibi.commentarii.ui.month.MonthViewModel
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Edits made before the page reports `loaded` are rejected; after it they are accepted. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LoadGateTest {
    private fun awaitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out waiting for $what" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    private fun container() = AppContainer(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun weekEditsAreRejectedBeforeLoadAndAcceptedAfter() {
        val vm = WeekViewModel(container(), weekStart(LocalDate.now()))
        val actions = vm.buildActions({}, {})
        assertFalse(vm.state.value.loaded)

        actions.todo.onText(0, "too early")
        actions.onNotes("too early")
        assertTrue(vm.state.value.todo.none { it.text == "too early" })
        assertEquals("", vm.state.value.notes)

        awaitUntil("week page to load") { vm.state.value.loaded }
        actions.todo.onText(0, "after load")
        actions.onNotes("after load")
        assertEquals("after load", vm.state.value.todo[0].text)
        assertEquals("after load", vm.state.value.notes)
    }

    @Test
    fun monthEditsAreRejectedBeforeLoadAndAcceptedAfter() {
        val vm = MonthViewModel(container(), YearMonth.now())
        val actions = vm.buildActions({}, {})
        assertFalse(vm.state.value.loaded)

        actions.todo.onText(0, "too early")
        actions.onReview("too early")
        assertTrue(vm.state.value.todo.none { it.text == "too early" })
        assertEquals("", vm.state.value.review)

        awaitUntil("month page to load") { vm.state.value.loaded }
        actions.todo.onText(0, "after load")
        actions.onReview("after load")
        assertEquals("after load", vm.state.value.todo[0].text)
        assertEquals("after load", vm.state.value.review)
    }
}
