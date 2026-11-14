package com.chatlearn.domain

private val SPANISH_CHARS = setOf('ñ', 'á', 'é', 'í', 'ó', 'ú', 'ü', '¿', '¡')
private val SPANISH_WORDS = setOf("el", "la", "los", "las", "de", "que", "y", "en", "un", "una", "es", "por", "para", "con", "no", "se", "lo", "pero", "yo", "tu", "muy", "estoy", "hola", "gracias")
private val ENGLISH_WORDS = setOf("the", "a", "an", "and", "is", "are", "i", "you", "to", "of", "in", "it", "that", "for", "with", "not", "do", "my", "was", "have", "hi", "thanks")

/** 내가 쓴 외국어 초안이 영어인지 스페인어인지 어림한다. 단서가 비기면 null(지난번 고른 언어를 쓴다). */
fun guessDraftLang(text: String): Lang? {
    val lower = text.lowercase()
    val words = lower.split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
    val es = words.count { it in SPANISH_WORDS } + 2 * lower.count { it in SPANISH_CHARS }
    val en = words.count { it in ENGLISH_WORDS }
    return when {
        es > en -> Lang.ES
        en > es -> Lang.EN
        else -> null
    }
}
