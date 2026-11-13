package com.chatlearn.domain

import kotlinx.serialization.Serializable

@Serializable
enum class Lang(val code: String) {
    KO("ko"), EN("en"), ES("es");

    companion object {
        fun fromCode(c: String): Lang? = entries.firstOrNull { it.code == c }
    }
}

@Serializable enum class Direction { RECEIVED, SENT }
@Serializable enum class TextOrigin { ORIGINAL, AI, USER }
@Serializable enum class Verdict { CORRECT, CLOSE, WRONG }
@Serializable enum class Rating { AGAIN, HARD, GOOD, EASY }

@Serializable enum class ExerciseKind { TRANSLATE, CLOZE, DICTATION }

/** 문장 카드인지, 번역 결과에서 골라 저장한 단어 카드인지. */
@Serializable enum class EntryKind { SENTENCE, WORD }

/** 태그 종류: 주제(자유), 함께 대화한 사람, 그 사람의 나라. 사람·나라는 저장할 때 한 번에 고르는 칩으로 보인다. */
@Serializable enum class TagKind { TOPIC, PERSON, COUNTRY }

/** 문법 기록의 출처: 스스로 써 보기의 시도, 복습 오답, 보낼 말 요청(내가 자주 필요로 하는 문법). */
@Serializable enum class GrammarSource { ATTEMPT, REVIEW, REQUEST }

/** 영어↔스페인어. 한국어는 "다른 외국어"가 없다. */
fun Lang.otherForeign(): Lang = when (this) {
    Lang.EN -> Lang.ES
    Lang.ES -> Lang.EN
    Lang.KO -> throw IllegalArgumentException("Korean has no counterpart foreign language")
}
