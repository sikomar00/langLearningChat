package com.chatlearn.review.exercise

import kotlin.random.Random

data class Cloze(val display: String, val answers: List<String>)

object ClozeBuilder {
    const val BLANK = "_____"
    private const val MIN_LENGTH = 4
    private const val TWO_BLANKS_FROM_WORDS = 8
    private val WORD = Regex("""\p{L}[\p{L}'’-]*""")

    // 4글자 이상이지만 학습 가치가 낮은 기능어
    private val STOP = setOf(
        "this", "that", "with", "from", "have", "been", "were", "what", "when", "where", "which", "about",
        "would", "there", "their", "they", "them", "then", "than", "your", "just", "like", "will", "into",
        "over", "some", "very", "much", "more", "also", "could", "should", "because", "while", "these",
        "those", "other", "after", "before",
        "para", "pero", "como", "cómo", "esto", "esta", "este", "estos", "estas", "estoy", "están", "tengo",
        "tiene", "tienen", "porque", "cuando", "donde", "desde", "hasta", "sobre", "entre", "también", "muy",
        "más", "menos", "todo", "todos", "todas", "unos", "unas", "algo", "pues", "cada", "otro", "otra",
        "sido", "solo", "sólo", "aunque", "mientras",
    )

    fun build(sentence: String, seed: Long, keyPhrase: String? = null): Cloze? {
        keyPhrase?.trim()?.takeIf { it.isNotEmpty() }?.let { phrase ->
            val at = sentence.indexOf(phrase, ignoreCase = true)
            if (at >= 0) {
                val end = at + phrase.length
                return Cloze(sentence.substring(0, at) + BLANK + sentence.substring(end), listOf(sentence.substring(at, end)))
            }
        }
        val words = WORD.findAll(sentence).toList()
        val candidates = words.filter { it.value.length >= MIN_LENGTH && it.value.lowercase() !in STOP }
        if (candidates.isEmpty()) return null
        val want = if (words.size >= TWO_BLANKS_FROM_WORDS && candidates.size >= 2) 2 else 1
        val chosen = candidates.shuffled(Random(seed)).take(want).sortedBy { it.range.first }
        val sb = StringBuilder()
        var last = 0
        chosen.forEach { m ->
            sb.append(sentence, last, m.range.first).append(BLANK)
            last = m.range.last + 1
        }
        sb.append(sentence, last, sentence.length)
        return Cloze(sb.toString(), chosen.map { it.value })
    }
}
