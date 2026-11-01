package com.chatlearn.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BackupData(
    val version: Int = 2,
    val entries: List<EntryEntity>,
    val texts: List<EntryTextEntity>,
    val cards: List<ReviewCardEntity>,
    val logs: List<ReviewLogEntity>,
    val tags: List<TagEntity>,
    val entryTags: List<EntryTagEntity>,
)

class BackupFormatException(msg: String) : Exception(msg)

object Backup {
    const val CURRENT_VERSION = 2
    // 기본값도 파일에 쓴다: 값이 기본값과 같다는 이유로 필드가 빠지면 나중에 기본값이 바뀔 때 의미가 달라진다.
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

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
