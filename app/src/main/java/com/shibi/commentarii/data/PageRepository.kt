package com.shibi.commentarii.data

import androidx.room.withTransaction
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

class PageRepository(
    private val db: CommentariiDatabase,
    private val clock: Clock,
    val zone: ZoneId,
    private val scheduleListener: ScheduleListener? = null,
) {
    private val dao = db.pageDao()

    private fun now(): Instant = clock.instant()

    // ---- Pages ----

    suspend fun getPage(id: Long): PageEntity? = dao.getPage(id)

    /**
     * True when the user has entered anything worth backing up: text in a daily-notes or note document, a titled
     * note, a non-blank task or schedule entry, a mood, or a self-care check. Blank auto-created daily pages do not count.
     */
    suspend fun hasUserData(): Boolean =
        dao.nonBlankTaskCount() > 0 ||
            dao.nonBlankScheduleCount() > 0 ||
            dao.moodCount() > 0 ||
            db.selfCareDao().totalCheckCount() > 0 ||
            dao.titledNoteCount() > 0 ||
            dao.getDocumentPayloads().any { BlockDocument.fromJson(it).flattenText().isNotEmpty() }

    fun observePage(id: Long): Flow<PageEntity?> = dao.observePage(id)

    fun observeNotes(): Flow<List<PageEntity>> = dao.observeNotes()

    /** The single DAILY page for [date]; created (with 8 section blocks and 17 hour rows) on first call. */
    suspend fun getOrCreateDaily(date: LocalDate): PageEntity = db.withTransaction {
        dao.getDaily(date) ?: run {
            val t = now()
            val id = dao.insertPage(
                PageEntity(type = PageType.DAILY, title = date.toString(), date = date, createdAt = t, updatedAt = t),
            )
            dao.insertBlocks(
                SectionKind.DAILY_KINDS.mapIndexed { i, kind ->
                    SectionBlockEntity(
                        pageId = id,
                        kind = kind,
                        position = i,
                        payloadJson = if (kind == SectionKind.NOTES) BlockDocument.EMPTY.toJson() else "{}",
                    )
                },
            )
            dao.insertSchedule(
                (FIRST_HOUR..LAST_HOUR).map { h ->
                    ScheduleEntryEntity(pageId = id, date = date, minuteOfDay = h * 60, text = "", isCustom = false)
                },
            )
            rebuildFts(id)
            checkNotNull(dao.getPage(id))
        }
    }

    suspend fun getLatestDailyBefore(date: LocalDate): PageEntity? = dao.getLatestDailyBefore(date)

    suspend fun createNote(title: String): PageEntity = db.withTransaction {
        val t = now()
        val id = dao.insertPage(PageEntity(type = PageType.NOTE, title = title, createdAt = t, updatedAt = t))
        dao.insertBlocks(
            listOf(
                SectionBlockEntity(
                    pageId = id,
                    kind = SectionKind.RICH_TEXT,
                    position = 0,
                    payloadJson = BlockDocument.EMPTY.toJson(),
                ),
            ),
        )
        rebuildFts(id)
        checkNotNull(dao.getPage(id))
    }

    suspend fun setTitle(pageId: Long, title: String) = db.withTransaction {
        dao.setTitle(pageId, title, now())
        rebuildFts(pageId)
    }

    suspend fun deletePage(pageId: Long) {
        val ids = db.withTransaction {
            val scheduleIds = dao.getSchedule(pageId).map { it.id }
            dao.deleteFts(pageId)
            dao.deletePage(pageId)
            scheduleIds
        }
        if (ids.isNotEmpty()) scheduleListener?.onScheduleChanged(ids, emptyList())
    }

    suspend fun setPinned(pageId: Long, pinned: Boolean) = dao.setPinned(pageId, pinned)

    suspend fun moveToFolder(pageId: Long, folderId: Long?) = dao.setFolder(pageId, folderId)

    suspend fun markOpened(pageId: Long) = dao.setLastOpened(pageId, now())

    // ---- Section content ----

    suspend fun getTasks(pageId: Long): List<TaskItemEntity> = dao.getTasks(pageId)

    suspend fun getTasks(pageId: Long, section: String): List<TaskItemEntity> = dao.getTasks(pageId, section)

    fun observeTasks(pageId: Long, section: String): Flow<List<TaskItemEntity>> = dao.observeTasks(pageId, section)

    /**
     * Replaces the items of one [section] with [items] (positions are re-assigned from list order).
     * Items with id 0 are inserted; others updated; missing ones deleted. Returns the saved rows with ids.
     */
    suspend fun saveTasks(pageId: Long, section: String, items: List<TaskItemEntity>): List<TaskItemEntity> =
        db.withTransaction {
            val rows = items.mapIndexed { i, it -> it.copy(pageId = pageId, section = section, position = i) }
            val ids = dao.upsertTasks(rows)
            val saved = rows.mapIndexed { i, r -> r.copy(id = ids[i]) }
            dao.deleteTasksNotIn(pageId, section, ids)
            dao.touch(pageId, now())
            rebuildFts(pageId)
            saved
        }

    suspend fun getSchedule(pageId: Long): List<ScheduleEntryEntity> = dao.getSchedule(pageId)

    fun observeSchedule(pageId: Long): Flow<List<ScheduleEntryEntity>> = dao.observeSchedule(pageId)

    /** Entries with the page's date. Existing ids are kept (stable for alarms); missing ones are deleted. */
    suspend fun saveSchedule(pageId: Long, entries: List<ScheduleEntryEntity>): List<ScheduleEntryEntity> {
        var touched: Set<Long> = emptySet()
        val result = db.withTransaction {
            val date = checkNotNull(dao.getPage(pageId)?.date) { "Schedule needs a dated page" }
            val before = dao.getSchedule(pageId).map { it.id }
            val rows = entries.map { it.copy(pageId = pageId, date = date) }
            val ids = dao.upsertSchedule(rows)
            val saved = rows.mapIndexed { i, r -> r.copy(id = ids[i]) }
            dao.deleteScheduleNotIn(pageId, ids)
            dao.touch(pageId, now())
            rebuildFts(pageId)
            touched = before.toSet() + ids
            saved
        }
        scheduleListener?.onScheduleChanged(touched, result)
        return result
    }

    /** Non-empty schedule entries dated [from] or later (for rescheduling alarms). */
    suspend fun getUpcomingSchedule(from: LocalDate): List<ScheduleEntryEntity> = dao.getScheduleFrom(from)

    suspend fun getMood(date: LocalDate): MoodEntity? = dao.getMood(date)

    fun observeMood(date: LocalDate): Flow<MoodEntity?> = dao.observeMood(date)

    /** [mood] 1..5, or null to clear. */
    suspend fun saveMood(date: LocalDate, mood: Int?) {
        if (mood == null) {
            dao.deleteMood(date)
        } else {
            require(mood in 1..5) { "mood must be 1..5" }
            dao.upsertMood(MoodEntity(date, mood))
        }
    }

    suspend fun getBlockDocument(pageId: Long, kind: String): BlockDocument =
        BlockDocument.fromJson(dao.getBlocks(pageId).firstOrNull { it.kind == kind }?.payloadJson)

    /** Saves the NOTES (daily) or RICH_TEXT (note) document. */
    suspend fun saveBlockDocument(pageId: Long, kind: String, doc: BlockDocument) = db.withTransaction {
        dao.updateBlockPayload(pageId, kind, doc.toJson())
        dao.touch(pageId, now())
        rebuildFts(pageId)
    }

    // ---- Folders ----

    fun observeFolders(): Flow<List<FolderEntity>> = dao.observeFolders()

    suspend fun getFolders(): List<FolderEntity> = dao.getFolders()

    suspend fun createFolder(name: String): FolderEntity {
        val clean = name.trim()
        return FolderEntity(dao.insertFolder(FolderEntity(name = clean)), clean)
    }

    suspend fun renameFolder(id: Long, name: String) = dao.renameFolder(id, name.trim())

    /** Pages in the folder become unfiled. */
    suspend fun deleteFolder(id: Long) = dao.deleteFolder(id)

    suspend fun getPagesInFolder(folderId: Long): List<PageEntity> = dao.getPagesInFolder(folderId)

    // ---- Tags ----

    fun observeTags(): Flow<List<TagEntity>> = dao.observeTags()

    suspend fun getTags(): List<TagEntity> = dao.getTags()

    /** Returns the existing tag with this name (case-insensitive) or creates it. */
    suspend fun getOrCreateTag(name: String): TagEntity = db.withTransaction {
        val clean = name.trim()
        dao.getTagByName(clean) ?: run {
            val id = dao.insertTag(TagEntity(name = clean))
            TagEntity(id, clean)
        }
    }

    suspend fun renameTag(id: Long, name: String) = dao.renameTag(id, name.trim())

    suspend fun deleteTag(id: Long) = dao.deleteTag(id)

    suspend fun addTag(pageId: Long, tagId: Long) = dao.insertCrossRef(PageTagCrossRef(pageId, tagId))

    suspend fun removeTag(pageId: Long, tagId: Long) = dao.deleteCrossRef(pageId, tagId)

    suspend fun getTagsForPage(pageId: Long): List<TagEntity> = dao.getTagsForPage(pageId)

    fun observeTagsForPage(pageId: Long): Flow<List<TagEntity>> = dao.observeTagsForPage(pageId)

    suspend fun getPagesWithTag(tagId: Long): List<PageEntity> = dao.getPagesWithTag(tagId)

    // ---- Search ----

    /** Rebuilds the FTS row: body = flattened text of tasks, schedule and document sections. */
    suspend fun rebuildFts(pageId: Long) = db.withTransaction {
        val page = dao.getPage(pageId) ?: return@withTransaction
        val parts = mutableListOf<String>()
        dao.getTasks(pageId).forEach { parts += it.text }
        dao.getSchedule(pageId).forEach { parts += it.text }
        dao.getBlocks(pageId)
            .filter { it.kind == SectionKind.NOTES || it.kind == SectionKind.RICH_TEXT }
            .forEach { parts += BlockDocument.fromJson(it.payloadJson).flattenText() }
        val body = parts.filter { it.isNotBlank() }.joinToString("\n")
        dao.deleteFts(pageId)
        dao.insertFts(PageSearchFts(pageId, page.title, body))
    }

    /** Prefix search over title and body, pinned first. Blank/unusable queries return an empty list. */
    suspend fun search(query: String): List<PageEntity> {
        val match = toFtsMatch(query) ?: return emptyList()
        return dao.search(match)
    }

    companion object {
        const val FIRST_HOUR = 6
        const val LAST_HOUR = 22

        /** Terms of letters/digits only (no FTS operators or quotes), each as a prefix term, ANDed. */
        fun toFtsMatch(query: String): String? {
            val terms = query.split(Regex("[^\\p{L}\\p{N}]+"))
                .filter { it.isNotEmpty() }
                .map { it.lowercase() }
            if (terms.isEmpty()) return null
            return terms.joinToString(" ") { "$it*" }
        }
    }
}
