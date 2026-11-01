package com.chatlearn.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        EntryEntity::class, EntryTextEntity::class, ReviewCardEntity::class,
        ReviewLogEntity::class, TagEntity::class, EntryTagEntity::class, FolderEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao
}
