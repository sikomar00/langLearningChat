package com.chatlearn.review

import android.content.Context
import android.speech.tts.TextToSpeech
import com.chatlearn.domain.Lang
import java.util.Locale

/** 폰에 내장된 음성(TTS). 엔진 초기화가 끝나기 전에는 쓸 수 없는 것으로 본다. */
class AndroidTts(context: Context) : Tts {
    @Volatile private var ready = false
    private val engine = TextToSpeech(context.applicationContext) { status -> ready = status == TextToSpeech.SUCCESS }

    private fun locale(l: Lang): Locale = when (l) {
        Lang.EN -> Locale.US
        Lang.ES -> Locale.forLanguageTag("es-ES")
        Lang.KO -> Locale.KOREAN
    }

    override fun isAvailable(lang: Lang) =
        ready && engine.isLanguageAvailable(locale(lang)) >= TextToSpeech.LANG_AVAILABLE

    override fun speak(text: String, lang: Lang, slow: Boolean) {
        if (!isAvailable(lang)) return
        engine.language = locale(lang)
        engine.setSpeechRate(if (slow) 0.7f else 1.0f)
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "chatlearn-utterance")
    }

    override fun stop() { engine.stop() }
}
