package com.chatlearn.domain

/** 저장할 때 나라 칩으로 먼저 보여 줄, 영어·스페인어를 쓰는 나라. 내가 쓴 나라가 앞에 온다. */
val COUNTRY_CHOICES = listOf("미국", "영국", "캐나다", "호주", "스페인", "멕시코", "콜롬비아", "아르헨티나", "칠레", "페루")
private val SPANISH_SPEAKING = setOf("스페인", "멕시코", "콜롬비아", "아르헨티나", "칠레", "페루")

/** 문장 언어를 쓰는 나라를 앞에(스페인어 문장이면 스페인·멕시코…가 먼저). 언어를 모르면 기본 순서. */
fun countryChoicesFor(lang: Lang?): List<String> = when (lang) {
    Lang.ES -> COUNTRY_CHOICES.sortedBy { it !in SPANISH_SPEAKING }
    Lang.EN -> COUNTRY_CHOICES.sortedBy { it in SPANISH_SPEAKING }
    else -> COUNTRY_CHOICES
}

// 카카오톡·왓츠앱에서 나를 가리키는 이름. 친구 후보에서 뺀다.
private val SELF_NAMES = setOf("나", "본인", "me", "you", "yo")

/** 붙여넣은 대화에서 말한 사람들(나를 가리키는 이름은 빼고, 나온 순서대로). */
fun speakersOf(lines: List<ChatLine>): List<String> =
    lines.mapNotNull { it.speaker }.filter { it.lowercase() !in SELF_NAMES }.distinct()

/** 한글이 영문자보다 많으면 한국어 글로 본다(공유받은 글이 보낼 말인지 받은 말인지 가를 때). */
fun looksKorean(text: String): Boolean {
    val hangul = text.count { it in '가'..'힣' || it in 'ㄱ'..'ㆎ' }
    val latin = text.count { it.isLetter() && it.code < 0x250 }
    return hangul > latin
}
