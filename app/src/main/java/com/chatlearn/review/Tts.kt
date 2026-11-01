package com.chatlearn.review

import com.chatlearn.domain.Lang

interface Tts {
    fun isAvailable(lang: Lang): Boolean
    fun speak(text: String, lang: Lang, slow: Boolean)
    fun stop()
}

/** 음성을 쓸 수 없을 때(또는 테스트에서) 쓰는 빈 구현. */
object NoTts : Tts {
    override fun isAvailable(lang: Lang) = false
    override fun speak(text: String, lang: Lang, slow: Boolean) {}
    override fun stop() {}
}
