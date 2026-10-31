package com.chatlearn.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BackupData(
    val version: Int = 1,
    val entries: List<EntryEntity>,
    val texts: List<EntryTextEntity>,
    val cards: List<ReviewCardEntity>,
    val logs: List<ReviewLogEntity>,
    val tags: List<TagEntity>,
    val entryTags: List<EntryTagEntity>,
)

class BackupFormatException(msg: String) : Exception(msg)

object Backup {
    const val CURRENT_VERSION = 1
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    fun encode(d: BackupData): String = json.encodeToString(BackupData.serializer(), d)

    fun decode(s: String): BackupData {
        val d = try {
            json.decodeFromString(BackupData.serializer(), s)
        } catch (e: Exception) {
            throw BackupFormatException("백업 파일을 읽을 수 없어요.")
        }
        if (d.version > CURRENT_VERSION) throw BackupFormatException("더 새로운 버전의 백업 파일이에요.")
        return d
    }
}
