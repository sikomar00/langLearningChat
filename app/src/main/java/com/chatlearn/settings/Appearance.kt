package com.chatlearn.settings

import com.chatlearn.domain.Lang

/** 밝게/어둡게: 휴대폰 설정을 따르거나 앱에서 고정한다. */
enum class ThemeMode(val label: String) {
    SYSTEM("휴대폰 설정"), LIGHT("밝게"), DARK("어둡게");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromName(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

enum class EnAccent(val label: String, val tag: String) { US("미국식", "en-US"), UK("영국식", "en-GB") }
enum class EsAccent(val label: String, val tag: String) { ES("스페인", "es-ES"), LATAM("중남미", "es-US") }

const val MIN_SPEECH_RATE = 0.5f
const val MAX_SPEECH_RATE = 1.5f

/** 듣기에 쓸 억양과 말 빠르기. 음성 인식과 답 입력 키보드 언어도 같은 억양을 따른다. */
data class VoicePrefs(val en: EnAccent = EnAccent.US, val es: EsAccent = EsAccent.ES, val rate: Float = 1f) {
    fun tag(lang: Lang): String = when (lang) {
        Lang.EN -> en.tag
        Lang.ES -> es.tag
        Lang.KO -> "ko-KR"
    }
}

fun voicePrefsOf(enAccent: String, esAccent: String, rate: Float) = VoicePrefs(
    EnAccent.entries.firstOrNull { it.name == enAccent } ?: EnAccent.US,
    EsAccent.entries.firstOrNull { it.name == esAccent } ?: EsAccent.ES,
    rate,
)

fun AppSettings.voice(): VoicePrefs = voicePrefsOf(enAccent, esAccent, speechRate)

/** 설정에서 억양을 골라 들어볼 때 읽어 줄 문장. */
fun voiceSample(lang: Lang): String = when (lang) {
    Lang.EN -> "Hey! How's it going? Let's catch up this weekend."
    Lang.ES -> "¡Hola! ¿Qué tal? Nos vemos este fin de semana."
    Lang.KO -> "안녕하세요"
}
