package com.shibi.commentarii.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

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
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class CommentariiDatabase : RoomDatabase() {
    abstract fun pageDao(): PageDao
    abstract fun selfCareDao(): SelfCareDao

    companion object {
        const val FILE_NAME = "commentarii.db"

        fun build(context: Context): CommentariiDatabase =
            Room.databaseBuilder(context.applicationContext, CommentariiDatabase::class.java, FILE_NAME).build()
    }
}
