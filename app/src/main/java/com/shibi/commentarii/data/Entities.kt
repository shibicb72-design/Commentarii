package com.shibi.commentarii.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate

object PageType {
    const val DAILY = "DAILY"
    const val NOTE = "NOTE"
    const val WEEKLY = "WEEKLY"
    const val MONTHLY = "MONTHLY"
    // Future: WEEKLY_PAYMENTS, TRAVEL, JOURNAL
}

object SectionKind {
    const val PRIORITIES = "PRIORITIES"
    const val TODO = "TODO"
    const val SCHEDULE = "SCHEDULE"
    const val DONT_FORGET = "DONT_FORGET"
    const val GRATEFUL = "GRATEFUL"
    const val SELF_CARE = "SELF_CARE"
    const val MOOD = "MOOD"
    const val NOTES = "NOTES"
    const val RICH_TEXT = "RICH_TEXT"
    const val GOALS = "GOALS"
    const val EVENTS = "EVENTS"
    const val REVIEW_WELL = "REVIEW_WELL"
    const val REVIEW_IMPROVE = "REVIEW_IMPROVE"
    const val REVIEW = "REVIEW"

    val DAILY_KINDS = listOf(PRIORITIES, TODO, SCHEDULE, DONT_FORGET, GRATEFUL, SELF_CARE, MOOD, NOTES)
    val WEEKLY_KINDS = listOf(GOALS, TODO, DONT_FORGET, SELF_CARE, MOOD, NOTES, REVIEW_WELL, REVIEW_IMPROVE)
    val MONTHLY_KINDS = listOf(GOALS, EVENTS, TODO, SELF_CARE, NOTES, REVIEW)

    /** Kinds whose payload is a [BlockDocument] (or, for EVENTS, an event list) and so is flattened into search. */
    val DOCUMENT_KINDS = setOf(NOTES, RICH_TEXT, REVIEW_WELL, REVIEW_IMPROVE, REVIEW)
}

object TaskSection {
    const val PRIORITY = "PRIORITY"
    const val TODO = "TODO"
    const val DONT_FORGET = "DONT_FORGET"
    const val GRATEFUL = "GRATEFUL"
    const val GOALS = "GOALS"
}

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["type", "date"], unique = true),
        Index(value = ["folderId"]),
        Index(value = ["type"]),
    ],
)
data class PageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val title: String,
    val date: LocalDate? = null,
    val folderId: Long? = null,
    val pinned: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
    val lastOpenedAt: Instant? = null,
)

@Entity(
    tableName = "section_blocks",
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["pageId", "position"])],
)
data class SectionBlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pageId: Long,
    val kind: String,
    val position: Int,
    val payloadJson: String = "{}",
)

@Entity(
    tableName = "task_items",
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["pageId", "section", "position"])],
)
data class TaskItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pageId: Long,
    val section: String,
    val position: Int,
    val text: String,
    val done: Boolean = false,
    /** Set on the original item when it was carried over to another day. */
    val carriedTo: LocalDate? = null,
)

@Entity(
    tableName = "schedule_entries",
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["pageId", "minuteOfDay"]), Index(value = ["date"])],
)
data class ScheduleEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pageId: Long,
    val date: LocalDate,
    val minuteOfDay: Int,
    val text: String = "",
    val isCustom: Boolean = false,
)

@Entity(tableName = "moods")
data class MoodEntity(
    @PrimaryKey val date: LocalDate,
    /** 1 (very sad) .. 5 (very happy). */
    val mood: Int,
)

@Entity(tableName = "self_care_items")
data class SelfCareItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int,
    val active: Boolean = true,
)

@Entity(tableName = "self_care_checks", primaryKeys = ["itemId", "date"])
data class SelfCareCheckEntity(
    val itemId: Long,
    val date: LocalDate,
)

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "page_tags",
    primaryKeys = ["pageId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tagId"])],
)
data class PageTagCrossRef(
    val pageId: Long,
    val tagId: Long,
)

/** Full-text index; rowid = PageEntity.id. */
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "page_search")
data class PageSearchFts(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    val title: String,
    val body: String,
)

class Converters {
    @TypeConverter
    fun localDateToLong(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun longToLocalDate(value: Long?): LocalDate? = value?.let { LocalDate.ofEpochDay(it) }

    @TypeConverter
    fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun longToInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }
}
