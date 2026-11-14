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
    indices = [Index("folderId"), Index("createdAt")],
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
    @ColumnInfo(defaultValue = "'SENTENCE'") val kind: EntryKind = EntryKind.SENTENCE,
    /** 이 문장 앞뒤로 오간 대화(여러 줄을 붙여 넣었을 때). 단어 카드면 그 단어가 나온 문장. */
    val contextText: String? = null,
    /** 노션 데이터베이스로 보낸 페이지 id. 다시 보내면 새로 만들지 않고 이 페이지를 고친다. */
    val notionPageId: String? = null,
    /** 이미 알아요: 복습 차례(오늘 복습·위젯·예보)에 넣지 않는다. 직접 골라 복습하면 나온다. 익힌 정도에서는 익힘으로 센다. */
    @ColumnInfo(defaultValue = "0") val known: Boolean = false,
    /** 통계에서 빼기: 정답률·익힌 정도·친구별·자주 틀리는 문장에 넣지 않는다. 복습은 그대로 한다. */
    @ColumnInfo(defaultValue = "0") val excludeFromStats: Boolean = false,
)

@Serializable
@Entity(
    tableName = "entry_text",
    primaryKeys = ["entryId", "lang"],
    foreignKeys = [ForeignKey(EntryEntity::class, ["id"], ["entryId"], onDelete = ForeignKey.CASCADE)],
    // 저장할 때 같은 문장이 있는지 찾는 조회용.
    indices = [Index("lang", "text")],
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
    /** 예전(SM-2) 일정 값. FSRS로 바꾼 뒤로는 고치지 않고, 안정도가 없는 옛 카드의 난이도를 정할 때만 읽는다. */
    val ease: Double,
    val reps: Int,
    val lapses: Int,
    val lastReviewedAt: Long?,
    /** FSRS 기억 안정도(일). 아직 한 번도 복습하지 않았으면 null. */
    val stability: Double? = null,
    /** FSRS 난이도(1~10). 아직 한 번도 복습하지 않았으면 null. */
    val difficulty: Double? = null,
) {
    fun toState() = CardState(intervalDays, ease, reps, lapses, dueAt, lastReviewedAt, stability, difficulty)
    fun withState(s: CardState) = copy(
        intervalDays = s.intervalDays, ease = s.ease, reps = s.reps,
        lapses = s.lapses, dueAt = s.dueAt, lastReviewedAt = s.lastReviewedAt,
        stability = s.stability, difficulty = s.difficulty,
    )
}

@Serializable
@Entity(
    tableName = "review_log",
    foreignKeys = [ForeignKey(ReviewCardEntity::class, ["id"], ["cardId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("cardId"), Index("reviewedAt")],
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
    @ColumnInfo(defaultValue = "'TOPIC'") val kind: TagKind = TagKind.TOPIC,
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

/** 문법 항목 하나에 대한 기록 한 줄. 모아서 약한 문법을 찾는다. 문장이 지워져도 기록은 남는다. */
@Serializable
@Entity(
    tableName = "grammar_event",
    foreignKeys = [ForeignKey(EntryEntity::class, ["id"], ["entryId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("pointKey"), Index("entryId")],
)
data class GrammarEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lang: Lang,
    /** 정해진 문법 목록의 키(예: "es.ser_estar"). */
    val pointKey: String,
    val source: GrammarSource,
    val isError: Boolean,
    val entryId: Long?,
    val at: Long,
)

/** 저장한 문장에 쓰인 문법 항목. 그 문장을 복습에서 틀리면 이 항목들의 오답으로 센다. */
@Serializable
@Entity(
    tableName = "entry_grammar",
    primaryKeys = ["entryId", "pointKey"],
    foreignKeys = [ForeignKey(EntryEntity::class, ["id"], ["entryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("pointKey")],
)
data class EntryGrammarEntity(val entryId: Long, val pointKey: String)
