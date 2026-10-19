package com.shibi.commentarii.backup

import androidx.room.withTransaction
import com.shibi.commentarii.data.CommentariiDatabase
import com.shibi.commentarii.data.DarkMode
import com.shibi.commentarii.data.FolderEntity
import com.shibi.commentarii.data.MoodEntity
import com.shibi.commentarii.data.PageEntity
import com.shibi.commentarii.data.PageRepository
import com.shibi.commentarii.data.PageTagCrossRef
import com.shibi.commentarii.data.ScheduleEntryEntity
import com.shibi.commentarii.data.SectionBlockEntity
import com.shibi.commentarii.data.SelfCareCheckEntity
import com.shibi.commentarii.data.SelfCareItemEntity
import com.shibi.commentarii.data.SettingsRepository
import com.shibi.commentarii.data.TagEntity
import com.shibi.commentarii.data.TaskItemEntity
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Whole-database JSON export/import. The FTS index is not stored; it is rebuilt on import. lockEnabled is never exported or touched. */
class ExportImport(
    private val db: CommentariiDatabase,
    private val settings: SettingsRepository,
    private val pages: PageRepository = PageRepository(db, Clock.systemDefaultZone(), ZoneId.systemDefault()),
) {
    private val dao = db.pageDao()
    private val selfCare = db.selfCareDao()

    suspend fun export(): String {
        val root = JSONObject()
        db.withTransaction {
            root.put("formatVersion", FORMAT_VERSION)
            root.put("folders", arr(dao.getAllFolders()) { JSONObject().put("id", it.id).put("name", it.name) })
            root.put("tags", arr(dao.getAllTags()) { JSONObject().put("id", it.id).put("name", it.name) })
            root.put(
                "pages",
                arr(dao.getAllPages()) {
                    JSONObject()
                        .put("id", it.id)
                        .put("type", it.type)
                        .put("title", it.title)
                        .put("date", it.date?.toEpochDay() ?: JSONObject.NULL)
                        .put("folderId", it.folderId ?: JSONObject.NULL)
                        .put("pinned", it.pinned)
                        .put("createdAt", it.createdAt.toEpochMilli())
                        .put("updatedAt", it.updatedAt.toEpochMilli())
                        .put("lastOpenedAt", it.lastOpenedAt?.toEpochMilli() ?: JSONObject.NULL)
                },
            )
            root.put(
                "sectionBlocks",
                arr(dao.getAllBlocks()) {
                    JSONObject().put("id", it.id).put("pageId", it.pageId).put("kind", it.kind)
                        .put("position", it.position).put("payloadJson", it.payloadJson)
                },
            )
            root.put(
                "tasks",
                arr(dao.getAllTasks()) {
                    JSONObject().put("id", it.id).put("pageId", it.pageId).put("section", it.section)
                        .put("position", it.position).put("text", it.text).put("done", it.done)
                        .put("carriedTo", it.carriedTo?.toEpochDay() ?: JSONObject.NULL)
                },
            )
            root.put(
                "schedule",
                arr(dao.getAllSchedule()) {
                    JSONObject().put("id", it.id).put("pageId", it.pageId).put("date", it.date.toEpochDay())
                        .put("minuteOfDay", it.minuteOfDay).put("text", it.text).put("isCustom", it.isCustom)
                },
            )
            root.put("moods", arr(dao.getAllMoods()) { JSONObject().put("date", it.date.toEpochDay()).put("mood", it.mood) })
            root.put(
                "selfCareItems",
                arr(selfCare.getAllItems()) {
                    JSONObject().put("id", it.id).put("name", it.name).put("position", it.position).put("active", it.active)
                },
            )
            root.put(
                "selfCareChecks",
                arr(selfCare.getAllChecks()) { JSONObject().put("itemId", it.itemId).put("date", it.date.toEpochDay()) },
            )
            root.put("pageTags", arr(dao.getAllPageTags()) { JSONObject().put("pageId", it.pageId).put("tagId", it.tagId) })
        }
        val s = JSONObject()
        s.put("exportReminderEnabled", settings.exportReminderEnabled.first())
        s.put("darkMode", settings.darkMode.first().name)
        s.put("lastOpenedDate", settings.lastOpenedDate.first()?.toEpochDay() ?: JSONObject.NULL)
        s.put("lastExportAt", settings.lastExportAt.first()?.toEpochMilli() ?: JSONObject.NULL)
        s.put("reminderDismissedAt", settings.reminderDismissedAt.first()?.toEpochMilli() ?: JSONObject.NULL)
        root.put("settings", s)
        return root.toString()
    }

    /** Replaces all data with the backup. Throws [BackupFormatException] before touching anything if invalid. */
    suspend fun import(json: String) {
        val parsed = parse(json)
        db.withTransaction {
            dao.deleteAllFts()
            dao.deleteAllPageTags()
            dao.deleteAllTasks()
            dao.deleteAllSchedule()
            dao.deleteAllBlocks()
            dao.deleteAllPages()
            dao.deleteAllFolders()
            dao.deleteAllTags()
            dao.deleteAllMoods()
            selfCare.deleteAllChecks()
            selfCare.deleteAllItems()

            dao.insertFolders(parsed.folders)
            dao.insertTags(parsed.tags)
            dao.insertPages(parsed.pages)
            dao.insertBlocks(parsed.blocks)
            dao.insertAllTasks(parsed.tasks)
            dao.insertSchedule(parsed.schedule)
            dao.insertMoods(parsed.moods)
            selfCare.insertItems(parsed.items)
            selfCare.insertChecks(parsed.checks)
            dao.insertPageTags(parsed.pageTags)
            parsed.pages.forEach { pages.rebuildFts(it.id) }
        }
        parsed.settings?.let { st ->
            st.optBoolOrNull("exportReminderEnabled")?.let { settings.setExportReminderEnabled(it) }
            st.optString("darkMode", "").takeIf { it.isNotEmpty() }
                ?.let { runCatching { DarkMode.valueOf(it) }.getOrNull() }
                ?.let { settings.setDarkMode(it) }
            settings.setLastOpenedDate(st.optLongOrNull("lastOpenedDate")?.let { LocalDate.ofEpochDay(it) })
            settings.setLastExportAt(st.optLongOrNull("lastExportAt")?.let { Instant.ofEpochMilli(it) })
            settings.setReminderDismissedAt(st.optLongOrNull("reminderDismissedAt")?.let { Instant.ofEpochMilli(it) })
        }
    }

    private class Parsed(
        val folders: List<FolderEntity>,
        val tags: List<TagEntity>,
        val pages: List<PageEntity>,
        val blocks: List<SectionBlockEntity>,
        val tasks: List<TaskItemEntity>,
        val schedule: List<ScheduleEntryEntity>,
        val moods: List<MoodEntity>,
        val items: List<SelfCareItemEntity>,
        val checks: List<SelfCareCheckEntity>,
        val pageTags: List<PageTagCrossRef>,
        val settings: JSONObject?,
    )

    private fun parse(json: String): Parsed {
        try {
            val root = JSONObject(json)
            val version = root.optInt("formatVersion", -1)
            if (version != FORMAT_VERSION) {
                throw BackupFormatException("Unsupported backup formatVersion $version (expected $FORMAT_VERSION)")
            }
            return Parsed(
                folders = list(root, "folders") { FolderEntity(it.getLong("id"), it.getString("name")) },
                tags = list(root, "tags") { TagEntity(it.getLong("id"), it.getString("name")) },
                pages = list(root, "pages") {
                    PageEntity(
                        id = it.getLong("id"),
                        type = it.getString("type"),
                        title = it.getString("title"),
                        date = it.optLongOrNull("date")?.let { d -> LocalDate.ofEpochDay(d) },
                        folderId = it.optLongOrNull("folderId"),
                        pinned = it.getBoolean("pinned"),
                        createdAt = Instant.ofEpochMilli(it.getLong("createdAt")),
                        updatedAt = Instant.ofEpochMilli(it.getLong("updatedAt")),
                        lastOpenedAt = it.optLongOrNull("lastOpenedAt")?.let { t -> Instant.ofEpochMilli(t) },
                    )
                },
                blocks = list(root, "sectionBlocks") {
                    SectionBlockEntity(
                        it.getLong("id"), it.getLong("pageId"), it.getString("kind"),
                        it.getInt("position"), it.getString("payloadJson"),
                    )
                },
                tasks = list(root, "tasks") {
                    TaskItemEntity(
                        id = it.getLong("id"),
                        pageId = it.getLong("pageId"),
                        section = it.getString("section"),
                        position = it.getInt("position"),
                        text = it.getString("text"),
                        done = it.getBoolean("done"),
                        carriedTo = it.optLongOrNull("carriedTo")?.let { d -> LocalDate.ofEpochDay(d) },
                    )
                },
                schedule = list(root, "schedule") {
                    ScheduleEntryEntity(
                        it.getLong("id"), it.getLong("pageId"), LocalDate.ofEpochDay(it.getLong("date")),
                        it.getInt("minuteOfDay"), it.getString("text"), it.getBoolean("isCustom"),
                    )
                },
                moods = list(root, "moods") { MoodEntity(LocalDate.ofEpochDay(it.getLong("date")), it.getInt("mood")) },
                items = list(root, "selfCareItems") {
                    SelfCareItemEntity(it.getLong("id"), it.getString("name"), it.getInt("position"), it.getBoolean("active"))
                },
                checks = list(root, "selfCareChecks") {
                    SelfCareCheckEntity(it.getLong("itemId"), LocalDate.ofEpochDay(it.getLong("date")))
                },
                pageTags = list(root, "pageTags") { PageTagCrossRef(it.getLong("pageId"), it.getLong("tagId")) },
                settings = root.optJSONObject("settings"),
            )
        } catch (e: BackupFormatException) {
            throw e
        } catch (e: JSONException) {
            throw BackupFormatException("Backup file is not valid: ${e.message}", e)
        } catch (e: RuntimeException) {
            throw BackupFormatException("Backup file is not valid: ${e.message}", e)
        }
    }

    private fun <T> arr(items: List<T>, f: (T) -> JSONObject): JSONArray {
        val a = JSONArray()
        items.forEach { a.put(f(it)) }
        return a
    }

    private fun <T> list(root: JSONObject, key: String, f: (JSONObject) -> T): List<T> {
        val a = root.getJSONArray(key)
        return (0 until a.length()).map { f(a.getJSONObject(it)) }
    }

    private fun JSONObject.optLongOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)

    private fun JSONObject.optBoolOrNull(key: String): Boolean? = if (isNull(key)) null else getBoolean(key)

    companion object {
        const val FORMAT_VERSION = 1
    }
}
