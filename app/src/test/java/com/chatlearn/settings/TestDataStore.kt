package com.chatlearn.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File

/** 테스트용 DataStore. 안드로이드용 파일 저장은 Windows에서 덮어쓰기를 못 해 두 번째 저장부터 실패하므로 Okio 저장을 쓴다. */
fun testDataStore(dir: File): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { dir.resolve("s.preferences_pb").toOkioPath() })
