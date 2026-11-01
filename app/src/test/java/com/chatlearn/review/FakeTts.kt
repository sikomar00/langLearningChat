package com.chatlearn.review

import com.chatlearn.domain.Lang

class FakeTts(var available: Boolean = true) : Tts {
    val spoken = mutableListOf<Triple<String, Lang, Boolean>>()
    override fun isAvailable(lang: Lang) = available
    override fun speak(text: String, lang: Lang, slow: Boolean) { spoken += Triple(text, lang, slow) }
    override fun stop() {}
}
