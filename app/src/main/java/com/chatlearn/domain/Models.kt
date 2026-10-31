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
