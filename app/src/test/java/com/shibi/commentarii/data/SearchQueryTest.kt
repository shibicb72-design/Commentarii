package com.shibi.commentarii.data

import com.shibi.commentarii.testutil.MutableClock
import com.shibi.commentarii.testutil.TestEnv
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Checklist #222-#224, #227, #228, #231, #232, #234-#236: FTS search through the repository on real FTS4 under Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SearchQueryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val clock = MutableClock(LocalDateTime.of(2026, 10, 18, 9, 0))
    private lateinit var env: TestEnv
    private val repo get() = env.pages

    @Before
    fun setUp() {
        env = TestEnv(tmp, clock)
    }

    @After
    fun tearDown() = env.close()

    private suspend fun note(title: String, body: String = ""): PageEntity {
        val page = repo.createNote(title)
        if (body.isNotEmpty()) {
            repo.saveBlockDocument(page.id, SectionKind.RICH_TEXT, BlockDocument(listOf(DocBlock(BlockType.PARAGRAPH, body))))
        }
        return page
    }

    private suspend fun ids(query: String) = repo.search(query).map { it.id }

    @Test
    fun item222_prefixFindsTheWordButNotTheMiddleOfIt() = runBlocking {
        val hit = note("Monday", "see the dentist at noon")
        note("Tuesday", "buy bread")
        assertEquals(listOf(hit.id), ids("den"))
        assertEquals(listOf(hit.id), ids("dentist"))
        assertEquals(emptyList<Long>(), ids("entist"))
    }

    @Test
    fun item223_termsAreAnded() = runBlocking {
        val both = note("One", "call mum tonight")
        val onlyCall = note("Two", "call the plumber")
        assertEquals(setOf(both.id, onlyCall.id), ids("call").toSet())
        assertEquals(listOf(both.id), ids("call mum"))
        assertEquals(listOf(both.id), ids("mum call"))
        assertEquals(emptyList<Long>(), ids("call zebra"))
    }

    @Test
    fun item224_caseDoesNotMatter() = runBlocking {
        val lower = note("One", "dentist")
        val upper = note("Two", "DENTIST VISIT")
        val expected = setOf(lower.id, upper.id)
        assertEquals(expected, ids("dentist").toSet())
        assertEquals(expected, ids("DENTIST").toSet())
        assertEquals(expected, ids("DeNtIsT").toSet())
        val accented = note("Three", "École")
        assertEquals(listOf(accented.id), ids("ÉCOLE"))
    }

    @Test
    fun item227_wordOnlyInTheTitleIsFoundAndFollowsARename() = runBlocking {
        val page = note("Quarterly", "body words only")
        note("Other", "unrelated")
        assertEquals(listOf(page.id), ids("quarterly"))
        repo.setTitle(page.id, "Annual")
        assertEquals(listOf(page.id), ids("annual"))
        assertEquals(emptyList<Long>(), ids("quarterly"))
    }

    @Test
    fun item228_pinnedFirstThenMostRecentlyUpdated() = runBlocking {
        val oldest = note("alpha oldest")
        clock.set(LocalDateTime.of(2026, 10, 18, 10, 0))
        val middle = note("alpha middle")
        clock.set(LocalDateTime.of(2026, 10, 18, 11, 0))
        val newest = note("alpha newest")
        assertEquals(listOf(newest.id, middle.id, oldest.id), ids("alpha"))

        repo.setPinned(oldest.id, true)
        assertEquals(listOf(oldest.id, newest.id, middle.id), ids("alpha"))
        assertEquals(listOf(true, false, false), repo.search("alpha").map { it.pinned })

        clock.set(LocalDateTime.of(2026, 10, 18, 12, 0))
        repo.setTitle(middle.id, "alpha middle edited")
        assertEquals(listOf(oldest.id, middle.id, newest.id), ids("alpha"))
    }

    @Test
    fun item231_punctuationOrEmojiOnlyFindsNothingWithoutCrashing() = runBlocking {
        val page = note("Party", "bring cake 🎉 !!!")
        assertEquals(listOf(page.id), ids("cake"))
        for (query in listOf("!!!", "🎉", "🎉🎉 !?", "@#$%^&*()", "   ", "")) {
            assertNull("match for '$query'", PageRepository.toFtsMatch(query))
            assertEquals("results for '$query'", emptyList<Long>(), ids(query))
        }
    }

    @Test
    fun item232_operatorsAndQuotesAreSeparators() = runBlocking {
        val foo = note("One", "foo alone")
        val fooBar = note("Two", "foo and bar")
        val fooOrBar = note("Three", "foo or bar")
        val everyFoo = setOf(foo.id, fooBar.id, fooOrBar.id)

        for (query in listOf("\"foo", "foo\"", "-foo", "foo*", "(foo)", "^foo", "foo:", "*foo*", "'foo'")) {
            assertEquals("results for '$query'", everyFoo, ids(query).toSet())
        }
        // "or" is an ordinary term here, so it only matches the page that really contains the word.
        assertEquals("foo* or* bar*", PageRepository.toFtsMatch("foo OR bar"))
        assertEquals(listOf(fooOrBar.id), ids("foo OR bar"))
        assertEquals(listOf(fooBar.id), ids("foo AND bar"))
        // None of these may reach SQLite as an operator; a syntax error would throw here.
        for (query in listOf("NOT foo", "foo NEAR bar", "NEAR/2 foo", "title:foo", "\"* OR", "foo\" OR \"bar", "' OR 1=1 --")) {
            ids(query)
        }
    }

    @Test
    fun item234_tagsAndFoldersAreNotIndexed() = runBlocking {
        val page = note("Errand", "pick up the parcel")
        repo.addTag(page.id, repo.getOrCreateTag("urgent").id)
        repo.moveToFolder(page.id, repo.createFolder("Workshop").id)
        repo.saveBlockDocument(page.id, SectionKind.RICH_TEXT, BlockDocument(listOf(DocBlock(BlockType.PARAGRAPH, "pick up the parcel"))))
        assertEquals(listOf(page.id), ids("parcel"))
        assertEquals(emptyList<Long>(), ids("urgent"))
        assertEquals(emptyList<Long>(), ids("workshop"))

        val positive = note("Marked", "this one is urgent in its text")
        assertEquals(listOf(positive.id), ids("urgent"))
    }

    @Test
    fun item235_yearMatchesDailyPagesByIsoTitleEvenWhenBlank() = runBlocking {
        val blank2026 = repo.getOrCreateDaily(LocalDate.of(2026, 3, 9))
        val blank2025 = repo.getOrCreateDaily(LocalDate.of(2025, 3, 9))
        val other = note("No year here", "just words")
        assertEquals("2026-03-09", blank2026.title)
        assertTrue(repo.getTasks(blank2026.id).isEmpty())

        assertEquals(listOf(blank2026.id), ids("2026"))
        assertEquals(listOf(blank2025.id), ids("2025"))
        assertEquals(listOf(blank2026.id), ids("2026-03"))
        assertTrue(other.id !in ids("2026"))
    }

    @Test
    fun item236_pageLeavesResultsOnceTheWordIsEditedOut() = runBlocking {
        val edited = note("One", "remember the walrus")
        val kept = note("Two", "the walrus again")
        assertEquals(setOf(edited.id, kept.id), ids("walrus").toSet())
        repo.saveBlockDocument(edited.id, SectionKind.RICH_TEXT, BlockDocument(listOf(DocBlock(BlockType.PARAGRAPH, "remember the seal"))))
        assertEquals(listOf(kept.id), ids("walrus"))
        assertEquals(listOf(edited.id), ids("seal"))

        val day = repo.getOrCreateDaily(LocalDate.of(2026, 10, 18))
        val saved = repo.saveTasks(day.id, TaskSection.TODO, listOf(TaskItemEntity(pageId = day.id, section = "", position = 0, text = "buy mangoes")))
        assertEquals(listOf(day.id), ids("mango"))
        repo.saveTasks(day.id, TaskSection.TODO, saved.map { it.copy(text = "buy pears") })
        assertEquals(emptyList<Long>(), ids("mango"))

        val rows = repo.getSchedule(day.id)
        repo.saveSchedule(day.id, rows.map { if (it.minuteOfDay == 15 * 60) it.copy(text = "gym session") else it })
        assertEquals(listOf(day.id), ids("gym"))
        repo.saveSchedule(day.id, rows)
        assertEquals(emptyList<Long>(), ids("gym"))
    }
}
