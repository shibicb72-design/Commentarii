package com.shibi.commentarii.data

import androidx.room.withTransaction
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

class SelfCareRepository(private val db: CommentariiDatabase) {
    private val dao = db.selfCareDao()

    fun observeItems(): Flow<List<SelfCareItemEntity>> = dao.observeActiveItems()

    suspend fun getItems(): List<SelfCareItemEntity> = dao.getActiveItems()

    suspend fun addItem(name: String): SelfCareItemEntity = db.withTransaction {
        val clean = name.trim()
        val pos = dao.maxPosition() + 1
        SelfCareItemEntity(id = dao.insertItem(SelfCareItemEntity(name = clean, position = pos)), name = clean, position = pos)
    }

    suspend fun renameItem(id: Long, name: String) = dao.rename(id, name.trim())

    /** Soft delete: history of checks is kept. */
    suspend fun removeItem(id: Long) = dao.setActive(id, false)

    /** Positions are assigned from the order of [orderedIds]. */
    suspend fun reorder(orderedIds: List<Long>) = db.withTransaction {
        orderedIds.forEachIndexed { i, id -> dao.setPosition(id, i) }
    }

    /** Flips the check for the day; returns true if now checked. */
    suspend fun toggleCheck(itemId: Long, date: LocalDate): Boolean = db.withTransaction {
        if (dao.checkCount(itemId, date) > 0) {
            dao.deleteCheck(itemId, date)
            false
        } else {
            dao.insertCheck(SelfCareCheckEntity(itemId, date))
            true
        }
    }

    suspend fun getChecks(from: LocalDate, to: LocalDate): List<SelfCareCheckEntity> = dao.getChecks(from, to)

    fun observeChecks(from: LocalDate, to: LocalDate): Flow<List<SelfCareCheckEntity>> = dao.observeChecks(from, to)
}
