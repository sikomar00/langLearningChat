package com.chatlearn.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context

fun inMemoryDb(): AppDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
