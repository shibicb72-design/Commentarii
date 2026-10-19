package com.shibi.commentarii.domain

import com.shibi.commentarii.data.TaskItemEntity
import com.shibi.commentarii.data.TaskSection
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RolloverSessionTest {
    private val today = LocalDate.of(2026, 10, 18)
    private val offer = RolloverOffer(
        today.minusDays(1),
        listOf(TaskItemEntity(id = 7, pageId = 1, section = TaskSection.TODO, position = 0, text = "a")),
    )
    private val handled = mutableListOf<LocalDate>()
    private val applied = mutableListOf<Set<Long>>()
    private val session = RolloverSession(today, { handled += it }, { _, ids -> applied += ids })

    @Test
    fun noOfferMarksHandled() = runBlocking {
        session.onChecked(null)
        assertNull(session.offer.value)
        assertEquals(listOf(today), handled)
    }

    @Test
    fun dismissClearsOfferWithoutMarkingHandled() = runBlocking {
        session.onChecked(offer)
        assertNotNull(session.offer.value)
        session.dismiss()
        assertNull(session.offer.value)
        assertEquals(emptyList<LocalDate>(), handled)
        assertEquals(emptyList<Set<Long>>(), applied)
    }

    @Test
    fun dismissedOfferIsOfferedAgainByNextSession() = runBlocking {
        session.onChecked(offer)
        session.dismiss()
        val next = RolloverSession(today, { handled += it }, { _, ids -> applied += ids })
        next.onChecked(offer)
        assertEquals(offer, next.offer.value)
    }

    @Test
    fun skipMarksHandledWithoutApplying() = runBlocking {
        session.onChecked(offer)
        session.resolve(emptySet())
        assertNull(session.offer.value)
        assertEquals(listOf(today), handled)
        assertEquals(emptyList<Set<Long>>(), applied)
    }

    @Test
    fun moveAllAppliesAndMarksHandled() = runBlocking {
        session.onChecked(offer)
        session.resolve(setOf(7L))
        assertNull(session.offer.value)
        assertEquals(listOf(setOf(7L)), applied)
        assertEquals(listOf(today), handled)
    }

    @Test
    fun resolveWithoutOfferDoesNothing() = runBlocking {
        session.resolve(setOf(7L))
        assertEquals(emptyList<LocalDate>(), handled)
        assertEquals(emptyList<Set<Long>>(), applied)
    }
}
