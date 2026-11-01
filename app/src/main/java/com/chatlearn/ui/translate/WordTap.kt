package com.chatlearn.ui.translate

/** 눌러서 뜻을 찾을 수 있도록 문장을 공백 단위로 나눈다. */
fun textChunks(text: String): List<String> = text.split(Regex("\\s+")).filter { it.isNotEmpty() }

/** 덩어리에서 앞뒤 문장부호를 떼고 단어만 남긴다. 글자가 하나도 없으면 null. */
fun cleanWord(chunk: String): String? {
    val first = chunk.indexOfFirst { it.isLetter() }
    if (first < 0) return null
    val last = chunk.indexOfLast { it.isLetter() }
    return chunk.substring(first, last + 1)
}
