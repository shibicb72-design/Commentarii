package com.shibi.commentarii.testutil

import android.app.AlarmManager
import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.shibi.commentarii.CommentariiApp
import com.shibi.commentarii.R
import com.shibi.commentarii.data.TaskItemEntity
import com.shibi.commentarii.ui.AppNav
import com.shibi.commentarii.ui.daily.DailyPageScreen
import com.shibi.commentarii.ui.theme.AppColors
import com.shibi.commentarii.ui.theme.CommentariiColors
import com.shibi.commentarii.ui.theme.CommentariiTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowDialog

/**
 * Hosts the real DailyPageScreen on the real container (real view model, Room, settings, alarms), so tests tap what a user
 * taps and read back what was saved. Settings leak across tests, so they are reset on both sides of every test.
 * Times are forced to the 12-hour format, otherwise they would follow the sandbox's locale default.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w412dp-h915dp-xxhdpi")
abstract class DailyPageTestBase {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    protected val container get() = (rule.activity.application as CommentariiApp).container
    protected val today: LocalDate get() = LocalDate.now(container.clock)
    protected lateinit var colors: ColorScheme
    protected lateinit var appColors: CommentariiColors

    private var shownDate by mutableStateOf<LocalDate?>(null)
    private var generation by mutableIntStateOf(0)

    @Before
    fun resetState() {
        container.settings.resetToDefaults()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        Settings.System.putString(rule.activity.contentResolver, Settings.System.TIME_12_24, "12")
        assertFalse("each test needs a fresh database", runBlocking { container.pages.hasUserData() })
    }

    /** runWithLooper, because a view model's settings edit may still be in flight and runBlocking here could deadlock on it. */
    @After
    fun restoreState() {
        val settings = container.settings
        runWithLooper { settings.resetToDefaults() }
    }

    protected fun str(res: Int, vararg args: Any): String = rule.activity.getString(res, *args)

    /**
     * Composes the page for [date]. [onSelectDate] moves the screen to the picked day, as AppNav does. Each [reopen] gets a new
     * view model store, which is what leaving the page and coming back later looks like to the view model.
     */
    protected fun open(
        date: LocalDate,
        width: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
        dark: Boolean = false,
    ) {
        shownDate = date
        rule.setContent {
            CommentariiTheme(dark = dark) {
                colors = MaterialTheme.colorScheme
                appColors = AppColors.current
                key(generation) {
                    val owner = remember {
                        object : ViewModelStoreOwner {
                            override val viewModelStore = ViewModelStore()
                        }
                    }
                    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
                    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                        DailyPageScreen(container, shownDate!!, { shownDate = it }, width)
                    }
                }
            }
        }
        awaitLoaded()
    }

    protected fun reopen() {
        generation++
        rule.waitForIdle()
        awaitLoaded()
    }

    /** Priority rows only appear after load() has read the page. The Notes field is no sign of that: it is always there. */
    protected fun awaitLoaded() {
        rule.waitUntilWithLooper { exists(hasText("1.")) }
        rule.waitForIdle()
    }

    /** Waits on the UI, Room or settings, draining the main looper and then letting compose catch up. */
    protected fun await(condition: () -> Boolean) {
        rule.waitUntilWithLooper(condition = condition)
        rule.waitForIdle()
    }

    protected fun exists(matcher: SemanticsMatcher) = rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    protected fun count(matcher: SemanticsMatcher) = rule.onAllNodes(matcher).fetchSemanticsNodes().size

    /** The page's own vertical list: the date strip is a lazy list too, but it scrolls sideways. */
    protected val pageList = SemanticsMatcher("the page's vertical list") {
        SemanticsActions.ScrollToIndex in it.config && SemanticsProperties.VerticalScrollAxisRange in it.config
    }

    protected fun scrollTo(matcher: SemanticsMatcher) {
        rule.onNode(pageList).performScrollToNode(matcher)
        rule.waitForIdle()
    }

    /** Shown text with Android's non-breaking spaces made plain; newer locale data puts one before AM/PM. */
    protected fun plain(text: String) = text.replace(' ', ' ').replace(' ', ' ')

    protected fun hasTime(expected: String) = SemanticsMatcher("shows time \"$expected\"") { node ->
        node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { plain(it.text) == expected }
    }

    /** The field holding [text] (or showing it as a placeholder). */
    protected fun field(text: String): SemanticsNodeInteraction {
        val matcher = hasText(text) and hasSetTextAction()
        scrollTo(matcher)
        return rule.onNode(matcher)
    }

    protected fun tapText(text: String) {
        scrollTo(hasText(text))
        rule.onNode(hasText(text)).performClick()
        rule.waitForIdle()
    }

    /** Tap on a button in a dialog or menu, which is outside the page's list. */
    protected fun tapInDialog(text: String) {
        rule.onNode(hasText(text)).performClick()
        rule.waitForIdle()
    }

    /** Goes to [date] the way a user far from it would, by typing it into the date picker's text mode (en-US format). */
    protected fun goToDateWithPicker(date: LocalDate) {
        tapDescription(str(R.string.daily_pick_date))
        tapDescription("Switch to text input mode")
        val input = rule.onNode(hasSetTextAction() and hasAnyAncestor(isDialog()))
        input.performTextClearance()
        input.performTextInput(date.format(DateTimeFormatter.ofPattern("MMddyyyy", Locale.US)))
        rule.waitForIdle()
        tapInDialog(str(R.string.action_ok))
    }

    protected fun tapDescription(description: String) {
        rule.onNode(hasContentDescription(description)).performClick()
        rule.waitForIdle()
    }

    /** The round check on the same row as [fieldNode]: rows are found by position because they share no semantic parent. */
    protected fun checkOf(fieldNode: SemanticsNodeInteraction): SemanticsNodeInteraction {
        val row = centerY(fieldNode.fetchSemanticsNode())
        val nodes = rule.onAllNodes(isToggleable()).fetchSemanticsNodes()
        return rule.onAllNodes(isToggleable())[nodes.indices.minBy { abs(centerY(nodes[it]) - row) }]
    }

    /** Middle of the node even when scrolled out of view, where clipped bounds would put every row at the same edge. */
    protected fun centerY(node: SemanticsNode) = node.positionInRoot.y + node.size.height / 2f

    /** The style the text was really laid out with; there is no semantics property for strike-through, bold or font. */
    protected fun layoutOf(node: SemanticsNode): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        checkNotNull(node.config[SemanticsActions.GetTextLayoutResult].action) { "no text layout" }.invoke(results)
        return results.single()
    }

    protected fun bounds(interaction: SemanticsNodeInteraction): Rect = interaction.fetchSemanticsNode().boundsInRoot

    /** Where the node really is, including any part scrolled out of view, which [bounds] clips away. */
    protected fun unclippedBounds(interaction: SemanticsNodeInteraction): Rect {
        val node = interaction.fetchSemanticsNode()
        return Rect(node.positionInRoot, node.size.toSize())
    }

    protected fun dp(value: Int) = value * rule.activity.resources.displayMetrics.density

    /** Colour of the pixel [xDp], [yDp] from the node's top-left corner, from a software draw of the activity's window. */
    protected fun pixel(interaction: SemanticsNodeInteraction, xDp: Float, yDp: Float): Color {
        val density = rule.activity.resources.displayMetrics.density
        val origin = interaction.fetchSemanticsNode().boundsInWindow
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bitmap)) }
        return Color(bitmap.getPixel((origin.left + xDp * density).toInt(), (origin.top + yDp * density).toInt()))
    }

    protected fun assertSameColor(what: String, expected: Color, actual: Color) {
        val close = abs(expected.red - actual.red) < 0.02f && abs(expected.green - actual.green) < 0.02f &&
            abs(expected.blue - actual.blue) < 0.02f
        assertTrue("$what: expected $expected but was $actual", close)
    }

    protected fun alarms() = shadowOf(rule.activity.getSystemService(AlarmManager::class.java)).scheduledAlarms

    protected fun triggerMs(date: LocalDate, minuteOfDay: Int) =
        date.atStartOfDay(container.zone).plusMinutes(minuteOfDay.toLong()).toInstant().toEpochMilli()

    // ---- Seeding and reading back through the container ----

    protected fun pageId(date: LocalDate): Long = runBlocking { container.pages.getOrCreateDaily(date).id }

    protected fun seedTasks(date: LocalDate, section: String, vararg texts: String, done: Boolean = false) {
        val id = pageId(date)
        runBlocking {
            container.pages.saveTasks(
                id,
                section,
                texts.mapIndexed { i, text ->
                    TaskItemEntity(pageId = id, section = section, position = i, text = text, done = done)
                },
            )
        }
    }

    protected fun tasks(date: LocalDate, section: String): List<TaskItemEntity> =
        runBlocking { container.pages.getTasks(pageId(date), section) }

    protected fun texts(date: LocalDate, section: String) = tasks(date, section).map { it.text }.filter { it.isNotBlank() }

    // ---- Dialog windows: Compose tests cannot touch outside a dialog or press Back inside its window ----

    /** Back, delivered to the showing dialog's own window as the system would. */
    protected fun pressBackInDialog() {
        val dialog: Dialog = ShadowDialog.getLatestDialog()
        @Suppress("DEPRECATION")
        rule.runOnUiThread { dialog.onBackPressed() }
        rule.waitForIdle()
    }

    /** A tap beyond the dialog window's top-left corner, which no dialog's content reaches. */
    protected fun tapOutsideDialog() {
        val dialog: Dialog = ShadowDialog.getLatestDialog()
        rule.runOnUiThread {
            val at = SystemClock.uptimeMillis()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(at, at, action, -200f, -200f, 0)
                dialog.dispatchTouchEvent(event)
                event.recycle()
            }
        }
        rule.waitForIdle()
    }

    protected val rootWidth: Float get() = rule.onRoot().fetchSemanticsNode().boundsInRoot.width

    // ---- The whole app shell, for items that cross tabs ----

    /** Back stack routes as AppNav reports them, e.g. `week/2026-11-02`. */
    protected var routes = emptyList<String>()

    protected fun openApp(width: WindowWidthSizeClass = WindowWidthSizeClass.Compact) {
        rule.setContent {
            CommentariiTheme(dark = false) {
                colors = MaterialTheme.colorScheme
                appColors = AppColors.current
                AppNav(container, width, onRoutesChanged = { routes = it })
            }
        }
        awaitLoaded()
    }

    protected fun tab(label: Int) =
        hasText(str(label)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    protected fun tapTab(label: Int) {
        rule.onNode(tab(label)).performClick()
        rule.waitForIdle()
    }

    protected fun pressBackInApp() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    /** Lets ripples and other press animations finish, so a pixel shows the resting colour. */
    protected fun settleAnimations() {
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
    }

    /** Runs the looper and compose for [millis] of real time, for checks that something does not arrive. */
    protected fun settleFor(millis: Long) {
        val end = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            rule.waitForIdle()
            Thread.sleep(10)
        }
    }

    /**
     * A folder on the page's chip, for checks that the page offers nothing. load() starts observing the page only after its
     * rollover check, so once the chip shows this name that check is over and a dialog could no longer be on its way.
     */
    protected fun seedLoadAnchor(date: LocalDate, name: String = "Anchor") {
        val id = pageId(date)
        runBlocking { container.pages.moveToFolder(id, container.pages.createFolder(name).id) }
    }

    protected fun awaitLoadAnchor(name: String = "Anchor") = await { exists(hasText(name)) }
}
