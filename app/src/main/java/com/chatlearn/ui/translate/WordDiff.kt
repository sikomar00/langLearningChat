package com.chatlearn.ui.translate

/** 고친 문장의 단어 하나. [changed]면 원문에 없던(바뀐) 단어. */
data class DiffWord(val text: String, val changed: Boolean)

/**
 * 첨삭 결과에서 바뀐 단어를 표시하려고, 고친 문장의 단어마다 원문과 비교한다(최장 공통 부분열).
 * 앞뒤 문장부호만 다른 단어는 같은 단어로 본다(끝에 마침표만 붙은 것까지 강조하면 시끄럽다). 대소문자와 단어 안 글자는 구분한다.
 */
fun wordDiff(original: String, corrected: String): List<DiffWord> {
    val a = original.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val b = corrected.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    fun key(w: String) = w.trim { !it.isLetterOrDigit() && it != '\'' }
    // lcs[i][j] = a[i..], b[j..]의 최장 공통 부분열 길이
    val lcs = Array(a.size + 1) { IntArray(b.size + 1) }
    for (i in a.indices.reversed()) for (j in b.indices.reversed()) {
        lcs[i][j] = if (key(a[i]) == key(b[j])) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
    }
    val out = ArrayList<DiffWord>(b.size)
    var i = 0; var j = 0
    while (j < b.size) {
        when {
            i < a.size && key(a[i]) == key(b[j]) -> { out += DiffWord(b[j], false); i++; j++ }
            i < a.size && lcs[i + 1][j] >= lcs[i][j + 1] -> i++
            else -> { out += DiffWord(b[j], true); j++ }
        }
    }
    return out
}
