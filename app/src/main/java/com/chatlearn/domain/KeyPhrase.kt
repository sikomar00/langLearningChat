package com.chatlearn.domain

private const val TRAILING = ".,;:!?\"'“”‘’…"

/**
 * AI나 사용자가 고른 핵심 표현이 [sentence] 안에 실제로 있으면 문장에 쓰인 그대로의 글자(대소문자 포함)를 돌려준다.
 * 비었거나, 문장에 없거나, 문장 전체와 같으면(빈칸으로 쓸 수 없다) null.
 */
fun normalizeKeyPhrase(sentence: String, phrase: String?): String? {
    val wanted = phrase?.trim()?.trimEnd { it in TRAILING }?.trim().orEmpty()
    if (wanted.isEmpty()) return null
    val at = sentence.indexOf(wanted, ignoreCase = true)
    if (at < 0) return null
    val found = sentence.substring(at, at + wanted.length)
    return found.takeIf { it.length < sentence.trim().length }
}
