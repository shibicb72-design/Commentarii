package com.shibi.commentarii.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PageEntity::class,
        SectionBlockEntity::class,
        TaskItemEntity::class,
        ScheduleEntryEntity::class,
        MoodEntity::class,
        SelfCareItemEntity::class,
        SelfCareCheckEntity::class,
        FolderEntity::class,
        TagEntity::class,
        PageTagCrossRef::class,
        PageSearchFts::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class CommentariiDatabase : RoomDatabase() {
    abstract fun pageDao(): PageDao
    abstract fun selfCareDao(): SelfCareDao

    companion object {
        const val FILE_NAME = "commentarii.db"

        /** Weekly/monthly pages share a date with a daily page, so the unique index moves from (date) to (type, date). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS `index_pages_date`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_pages_type_date` ON `pages` (`type`, `date`)")
            }
        }

        fun build(context: Context): CommentariiDatabase =
            Room.databaseBuilder(context.applicationContext, CommentariiDatabase::class.java, FILE_NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
