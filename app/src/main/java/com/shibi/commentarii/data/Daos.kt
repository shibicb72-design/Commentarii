package com.shibi.commentarii.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface PageDao {
    // Pages
    @Insert
    suspend fun insertPage(page: PageEntity): Long

    @Update
    suspend fun updatePage(page: PageEntity)

    @Query("SELECT * FROM pages WHERE id = :id")
    suspend fun getPage(id: Long): PageEntity?

    @Query("SELECT * FROM pages WHERE id = :id")
    fun observePage(id: Long): Flow<PageEntity?>

    @Query("SELECT * FROM pages WHERE type = 'DAILY' AND date = :date LIMIT 1")
    suspend fun getDaily(date: LocalDate): PageEntity?

    @Query("SELECT * FROM pages WHERE type = 'DAILY' AND date < :before ORDER BY date DESC LIMIT 1")
    suspend fun getLatestDailyBefore(before: LocalDate): PageEntity?

    @Query("SELECT * FROM pages WHERE type = 'NOTE' ORDER BY pinned DESC, updatedAt DESC, id DESC")
    fun observeNotes(): Flow<List<PageEntity>>

    @Query("SELECT COUNT(*) FROM pages")
    suspend fun pageCount(): Int

    @Query("UPDATE pages SET updatedAt = :at WHERE id = :id")
    suspend fun touch(id: Long, at: Instant)

    @Query("UPDATE pages SET title = :title, updatedAt = :at WHERE id = :id")
    suspend fun setTitle(id: Long, title: String, at: Instant)

    @Query("UPDATE pages SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE pages SET folderId = :folderId WHERE id = :id")
    suspend fun setFolder(id: Long, folderId: Long?)

    @Query("UPDATE pages SET lastOpenedAt = :at WHERE id = :id")
    suspend fun setLastOpened(id: Long, at: Instant)

    @Query("DELETE FROM pages WHERE id = :id")
    suspend fun deletePage(id: Long)

    // Section blocks
    @Insert
    suspend fun insertBlocks(blocks: List<SectionBlockEntity>)

    @Query("SELECT * FROM section_blocks WHERE pageId = :pageId ORDER BY position")
    suspend fun getBlocks(pageId: Long): List<SectionBlockEntity>

    @Query("UPDATE section_blocks SET payloadJson = :payload WHERE pageId = :pageId AND kind = :kind")
    suspend fun updateBlockPayload(pageId: Long, kind: String, payload: String)

    // Tasks
    @Query("SELECT * FROM task_items WHERE pageId = :pageId ORDER BY section, position, id")
    suspend fun getTasks(pageId: Long): List<TaskItemEntity>

    @Query("SELECT * FROM task_items WHERE pageId = :pageId AND section = :section ORDER BY position, id")
    suspend fun getTasks(pageId: Long, section: String): List<TaskItemEntity>

    @Query("SELECT * FROM task_items WHERE pageId = :pageId AND section = :section ORDER BY position, id")
    fun observeTasks(pageId: Long, section: String): Flow<List<TaskItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTasks(tasks: List<TaskItemEntity>): List<Long>

    @Query("DELETE FROM task_items WHERE pageId = :pageId AND section = :section AND id NOT IN (:keepIds)")
    suspend fun deleteTasksNotIn(pageId: Long, section: String, keepIds: List<Long>)

    // Schedule
    @Query("SELECT * FROM schedule_entries WHERE pageId = :pageId ORDER BY minuteOfDay, id")
    suspend fun getSchedule(pageId: Long): List<ScheduleEntryEntity>

    @Query("SELECT * FROM schedule_entries WHERE pageId = :pageId ORDER BY minuteOfDay, id")
    fun observeSchedule(pageId: Long): Flow<List<ScheduleEntryEntity>>

    @Query("SELECT * FROM schedule_entries WHERE date >= :from AND text != '' ORDER BY date, minuteOfDay")
    suspend fun getScheduleFrom(from: LocalDate): List<ScheduleEntryEntity>

    @Insert
    suspend fun insertSchedule(entries: List<ScheduleEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSchedule(entries: List<ScheduleEntryEntity>): List<Long>

    @Query("DELETE FROM schedule_entries WHERE pageId = :pageId AND id NOT IN (:keepIds)")
    suspend fun deleteScheduleNotIn(pageId: Long, keepIds: List<Long>)

    // Mood
    @Query("SELECT * FROM moods WHERE date = :date")
    suspend fun getMood(date: LocalDate): MoodEntity?

    @Query("SELECT * FROM moods WHERE date = :date")
    fun observeMood(date: LocalDate): Flow<MoodEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMood(mood: MoodEntity)

    @Query("DELETE FROM moods WHERE date = :date")
    suspend fun deleteMood(date: LocalDate)

    // FTS
    @Query("DELETE FROM page_search WHERE rowid = :pageId")
    suspend fun deleteFts(pageId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFts(row: PageSearchFts)

    @Query(
        "SELECT * FROM pages WHERE id IN (SELECT rowid FROM page_search WHERE page_search MATCH :match) " +
            "ORDER BY pinned DESC, updatedAt DESC, id DESC",
    )
    suspend fun search(match: String): List<PageEntity>

    // Folders
    @Insert
    suspend fun insertFolder(folder: FolderEntity): Long

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun renameFolder(id: Long, name: String)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolder(id: Long)

    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE")
    fun observeFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE")
    suspend fun getFolders(): List<FolderEntity>

    @Query("SELECT * FROM pages WHERE folderId = :folderId ORDER BY pinned DESC, updatedAt DESC, id DESC")
    suspend fun getPagesInFolder(folderId: Long): List<PageEntity>

    // Tags
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getTagByName(name: String): TagEntity?

    @Query("UPDATE tags SET name = :name WHERE id = :id")
    suspend fun renameTag(id: Long, name: String)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteTag(id: Long)

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    fun observeTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    suspend fun getTags(): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(ref: PageTagCrossRef)

    @Query("DELETE FROM page_tags WHERE pageId = :pageId AND tagId = :tagId")
    suspend fun deleteCrossRef(pageId: Long, tagId: Long)

    @Query("SELECT t.* FROM tags t JOIN page_tags r ON r.tagId = t.id WHERE r.pageId = :pageId ORDER BY t.name COLLATE NOCASE")
    suspend fun getTagsForPage(pageId: Long): List<TagEntity>

    @Query("SELECT t.* FROM tags t JOIN page_tags r ON r.tagId = t.id WHERE r.pageId = :pageId ORDER BY t.name COLLATE NOCASE")
    fun observeTagsForPage(pageId: Long): Flow<List<TagEntity>>

    @Query(
        "SELECT p.* FROM pages p JOIN page_tags r ON r.pageId = p.id WHERE r.tagId = :tagId " +
            "ORDER BY p.pinned DESC, p.updatedAt DESC, p.id DESC",
    )
    suspend fun getPagesWithTag(tagId: Long): List<PageEntity>

    // ---- Backup (export/import) ----
    @Query("SELECT * FROM pages ORDER BY id")
    suspend fun getAllPages(): List<PageEntity>

    @Query("SELECT * FROM section_blocks ORDER BY id")
    suspend fun getAllBlocks(): List<SectionBlockEntity>

    @Query("SELECT * FROM task_items ORDER BY id")
    suspend fun getAllTasks(): List<TaskItemEntity>

    @Query("SELECT * FROM schedule_entries ORDER BY id")
    suspend fun getAllSchedule(): List<ScheduleEntryEntity>

    @Query("SELECT * FROM moods ORDER BY date")
    suspend fun getAllMoods(): List<MoodEntity>

    @Query("SELECT * FROM folders ORDER BY id")
    suspend fun getAllFolders(): List<FolderEntity>

    @Query("SELECT * FROM tags ORDER BY id")
    suspend fun getAllTags(): List<TagEntity>

    @Query("SELECT * FROM page_tags ORDER BY pageId, tagId")
    suspend fun getAllPageTags(): List<PageTagCrossRef>

    @Insert
    suspend fun insertPages(pages: List<PageEntity>)

    @Insert
    suspend fun insertAllTasks(tasks: List<TaskItemEntity>)

    @Insert
    suspend fun insertMoods(moods: List<MoodEntity>)

    @Insert
    suspend fun insertFolders(folders: List<FolderEntity>)

    @Insert
    suspend fun insertTags(tags: List<TagEntity>)

    @Insert
    suspend fun insertPageTags(refs: List<PageTagCrossRef>)

    @Query("DELETE FROM pages")
    suspend fun deleteAllPages()

    @Query("DELETE FROM section_blocks")
    suspend fun deleteAllBlocks()

    @Query("DELETE FROM task_items")
    suspend fun deleteAllTasks()

    @Query("DELETE FROM schedule_entries")
    suspend fun deleteAllSchedule()

    @Query("DELETE FROM moods")
    suspend fun deleteAllMoods()

    @Query("DELETE FROM folders")
    suspend fun deleteAllFolders()

    @Query("DELETE FROM tags")
    suspend fun deleteAllTags()

    @Query("DELETE FROM page_tags")
    suspend fun deleteAllPageTags()

    @Query("DELETE FROM page_search")
    suspend fun deleteAllFts()
}

@Dao
interface SelfCareDao {
    @Query("SELECT * FROM self_care_items WHERE active = 1 ORDER BY position, id")
    fun observeActiveItems(): Flow<List<SelfCareItemEntity>>

    @Query("SELECT * FROM self_care_items WHERE active = 1 ORDER BY position, id")
    suspend fun getActiveItems(): List<SelfCareItemEntity>

    @Query("SELECT * FROM self_care_items WHERE id = :id")
    suspend fun getItem(id: Long): SelfCareItemEntity?

    @Query("SELECT COALESCE(MAX(position), -1) FROM self_care_items")
    suspend fun maxPosition(): Int

    @Insert
    suspend fun insertItem(item: SelfCareItemEntity): Long

    @Query("UPDATE self_care_items SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE self_care_items SET active = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)

    @Query("UPDATE self_care_items SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int)

    @Query("SELECT COUNT(*) FROM self_care_checks WHERE itemId = :itemId AND date = :date")
    suspend fun checkCount(itemId: Long, date: LocalDate): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCheck(check: SelfCareCheckEntity)

    @Query("DELETE FROM self_care_checks WHERE itemId = :itemId AND date = :date")
    suspend fun deleteCheck(itemId: Long, date: LocalDate)

    @Query("SELECT * FROM self_care_checks WHERE date >= :from AND date <= :to")
    suspend fun getChecks(from: LocalDate, to: LocalDate): List<SelfCareCheckEntity>

    @Query("SELECT * FROM self_care_checks WHERE date >= :from AND date <= :to")
    fun observeChecks(from: LocalDate, to: LocalDate): Flow<List<SelfCareCheckEntity>>

    // ---- Backup (export/import) ----
    @Query("SELECT * FROM self_care_items ORDER BY id")
    suspend fun getAllItems(): List<SelfCareItemEntity>

    @Query("SELECT * FROM self_care_checks ORDER BY itemId, date")
    suspend fun getAllChecks(): List<SelfCareCheckEntity>

    @Insert
    suspend fun insertItems(items: List<SelfCareItemEntity>)

    @Insert
    suspend fun insertChecks(checks: List<SelfCareCheckEntity>)

    @Query("DELETE FROM self_care_checks")
    suspend fun deleteAllChecks()

    @Query("DELETE FROM self_care_items")
    suspend fun deleteAllItems()
}
