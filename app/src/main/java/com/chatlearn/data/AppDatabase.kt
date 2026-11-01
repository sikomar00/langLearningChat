package com.chatlearn.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        EntryEntity::class, EntryTextEntity::class, ReviewCardEntity::class,
        ReviewLogEntity::class, TagEntity::class, EntryTagEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao
}
