package com.chatlearn.review

import com.chatlearn.domain.Lang
import com.chatlearn.review.exercise.AnswerGrader
import com.chatlearn.review.exercise.DictationResult

/** 음성 인식에 넘길 언어 태그. */
fun speechLanguageTag(lang: Lang): String = when (lang) {
    Lang.EN -> "en-US"
    Lang.ES -> "es-ES"
    Lang.KO -> "ko-KR"
}

/** 음성 인식 결과 후보 중 첫 번째로 비어 있지 않은 것. */
fun spokenText(results: List<String>?): String? = results?.firstOrNull { it.isNotBlank() }?.trim()

/** 마이크로 받은 말을 입력칸에 넣는다: 비어 있으면 그대로, 이미 쓴 게 있으면 띄어 쓰고 이어 붙인다. */
fun appendSpoken(current: String, spoken: String): String =
    if (current.isBlank()) spoken else current.trimEnd() + " " + spoken

/** 따라 말하기: 말한 문장을 정답과 받아쓰기와 같은 기준(대소문자·문장부호 무시)으로 비교한다. */
fun shadowingResult(reference: String, spoken: String): DictationResult = AnswerGrader.gradeDictation(reference, spoken)
