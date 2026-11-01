package com.chatlearn.review.exercise

import com.chatlearn.domain.Verdict
import java.text.Normalizer

data class WordMark(val word: String, val correct: Boolean)
data class DictationResult(val verdict: Verdict, val marks: List<WordMark>)

object AnswerGrader {
    const val DICTATION_CLOSE_MAX_ERROR_RATE = 0.15

    fun normalize(s: String): String =
        s.replace('’', '\'').lowercase()
            .replace(Regex("""[^\p{L}\p{N}\s'-]"""), " ")
            .replace(Regex("\\s+"), " ").trim()

    fun stripAccents(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    fun editDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            dp[i][j] = minOf(
                dp[i - 1][j] + 1, dp[i][j - 1] + 1,
                dp[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1,
            )
        }
        return dp[a.length][b.length]
    }

    fun gradeCloze(answers: List<String>, inputs: List<String>): Verdict {
        require(answers.size == inputs.size)
        val levels = answers.zip(inputs).map { (a, i) ->
            val na = normalize(a); val ni = normalize(i)
            when {
                na == ni -> 0
                stripAccents(na) == stripAccents(ni) || editDistance(na, ni) <= 1 -> 1
                else -> 2
            }
        }
        return when {
            levels.any { it == 2 } -> Verdict.WRONG
            levels.any { it == 1 } -> Verdict.CLOSE
            else -> Verdict.CORRECT
        }
    }

    fun gradeDictation(reference: String, input: String): DictationResult {
        val ref = normalize(reference).split(' ').filter { it.isNotEmpty() }
        val typed = normalize(input).split(' ').filter { it.isNotEmpty() }
        // 단어 단위 최장 공통 부분열로 맞은 단어를 찾는다.
        val lcs = Array(ref.size + 1) { IntArray(typed.size + 1) }
        for (i in ref.indices.reversed()) for (j in typed.indices.reversed()) {
            lcs[i][j] = if (ref[i] == typed[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
        }
        val correct = BooleanArray(ref.size)
        var i = 0; var j = 0
        while (i < ref.size && j < typed.size) {
            when {
                ref[i] == typed[j] -> { correct[i] = true; i++; j++ }
                lcs[i + 1][j] >= lcs[i][j + 1] -> i++
                else -> j++
            }
        }
        val marks = ref.mapIndexed { idx, w -> WordMark(w, correct[idx]) }
        val matched = correct.count { it }
        val errors = maxOf(ref.size - matched, typed.size - matched)
        val accentOnly = ref.size == typed.size &&
            ref.zip(typed).all { (r, t) -> stripAccents(r) == stripAccents(t) }
        val verdict = when {
            ref.isEmpty() || typed.isEmpty() -> Verdict.WRONG
            errors == 0 -> Verdict.CORRECT
            accentOnly -> Verdict.CLOSE
            errors.toDouble() / ref.size <= DICTATION_CLOSE_MAX_ERROR_RATE -> Verdict.CLOSE
            else -> Verdict.WRONG
        }
        return DictationResult(verdict, marks)
    }
}
