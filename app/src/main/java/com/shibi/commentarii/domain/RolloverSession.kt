package com.shibi.commentarii.domain

import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory state of the "carry over unfinished items" offer for [date].
 * - [dismiss] (tap outside / back) only clears the offer; today is NOT marked as handled, so it is offered again the next time
 *   the page is opened.
 * - [resolve] (Move all / Pick / Skip) applies the chosen items and marks today as handled.
 */
class RolloverSession(
    private val date: LocalDate,
    private val markHandled: suspend (LocalDate) -> Unit,
    private val apply: suspend (RolloverOffer, Set<Long>) -> Unit,
) {
    private val _offer = MutableStateFlow<RolloverOffer?>(null)
    val offer: StateFlow<RolloverOffer?> = _offer.asStateFlow()

    /** [computed] is the result of [computeRollover]; no offer means today is simply marked as opened. */
    suspend fun onChecked(computed: RolloverOffer?) {
        if (computed == null) markHandled(date) else _offer.value = computed
    }

    fun dismiss() {
        _offer.value = null
    }

    /** Empty [selectedIds] = skip. */
    suspend fun resolve(selectedIds: Set<Long>) {
        val current = _offer.value ?: return
        _offer.value = null
        if (selectedIds.isNotEmpty()) apply(current, selectedIds)
        markHandled(date)
    }
}
