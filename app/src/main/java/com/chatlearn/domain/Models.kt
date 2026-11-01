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

/** 영어↔스페인어. 한국어는 "다른 외국어"가 없다. */
fun Lang.otherForeign(): Lang = when (this) {
    Lang.EN -> Lang.ES
    Lang.ES -> Lang.EN
    Lang.KO -> throw IllegalArgumentException("Korean has no counterpart foreign language")
}
