package com.chatlearn.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.chatlearn.domain.*
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "folder", indices = [Index("name", unique = true)])
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = 0,
)

@Serializable
@Entity(
    tableName = "entry",
    foreignKeys = [ForeignKey(FolderEntity::class, ["id"], ["folderId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("folderId")],
)
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 이 표현을 알게 된(저장한) 날. 번역 화면에서 날짜를 골라 저장하면 그 날짜가 들어간다. */
    val createdAt: Long,
    val direction: Direction,
    val chatLang: Lang,
    val note: String,
    val tone: String?,
    /** 들어 있는 폴더. null이면 폴더에 넣지 않은 문장. 폴더가 지워지면 null로 돌아간다. */
    val folderId: Long? = null,
)

@Serializable
@Entity(
    tableName = "entry_text",
    primaryKeys = ["entryId", "lang"],
    foreignKeys = [ForeignKey(EntryEntity::class, ["id"], ["entryId"], onDelete = ForeignKey.CASCADE)],
)
data class EntryTextEntity(
    val entryId: Long,
    val lang: Lang,
    val text: String,
    val origin: TextOrigin,
    /** 이 문장에서 배울 핵심 표현. null이면 아직 모름, ""이면 없음으로 확인됨. */
    val keyPhrase: String? = null,
)

@Serializable
@Entity(
    tableName = "review_card",
    foreignKeys = [ForeignKey(EntryEntity::class, ["id"], ["entryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["entryId", "answerLang"], unique = true), Index("dueAt")],
)
data class ReviewCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val answerLang: Lang,
    val dueAt: Long,
    val intervalDays: Double,
    val ease: Double,
    val reps: Int,
    val lapses: Int,
    val lastReviewedAt: Long?,
) {
    fun toState() = CardState(intervalDays, ease, reps, lapses, dueAt, lastReviewedAt)
    fun withState(s: CardState) = copy(
        intervalDays = s.intervalDays, ease = s.ease, reps = s.reps,
        lapses = s.lapses, dueAt = s.dueAt, lastReviewedAt = s.lastReviewedAt,
    )
}

@Serializable
@Entity(
    tableName = "review_log",
    foreignKeys = [ForeignKey(ReviewCardEntity::class, ["id"], ["cardId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("cardId")],
)
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,
    val reviewedAt: Long,
    val promptLang: Lang,
    val userAnswer: String,
    val aiVerdict: Verdict?,
    val aiFeedback: String?,
    val rating: Rating,
    val responseMs: Long? = null,
    @ColumnInfo(defaultValue = "'TRANSLATE'") val exercise: ExerciseKind = ExerciseKind.TRANSLATE,
)

@Serializable
@Entity(tableName = "tag", indices = [Index("name", unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(defaultValue = "0") val lastUsedAt: Long = 0,
)

@Serializable
@Entity(
    tableName = "entry_tag",
    primaryKeys = ["entryId", "tagId"],
    foreignKeys = [
        ForeignKey(EntryEntity::class, ["id"], ["entryId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TagEntity::class, ["id"], ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tagId")],
)
data class EntryTagEntity(val entryId: Long, val tagId: Long)
