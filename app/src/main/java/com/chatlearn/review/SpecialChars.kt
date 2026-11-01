package com.chatlearn.review

import com.chatlearn.domain.Lang

/** 한국어 키보드에서 치기 어려운 글자. 복습 입력창 위에 버튼으로 보여 준다. */
fun specialChars(lang: Lang): List<String> = when (lang) {
    Lang.ES -> listOf("á", "é", "í", "ó", "ú", "ñ", "ü", "¿", "¡")
    Lang.EN, Lang.KO -> emptyList()
}
