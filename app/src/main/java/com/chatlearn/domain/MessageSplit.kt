package com.chatlearn.domain

private const val MAX_LINES = 20

// 줄 맨 앞의 시간 표시. 왓츠앱 등에서 복사하면 붙어 나온다. 이름은 건드리지 않는다.
private val BRACKETED = Regex("""^\[[^\]]{1,30}\]\s*""")
private val DATE_TIME = Regex("""^\d{1,2}[/.\-]\d{1,2}[/.\-]\d{2,4},?\s+\d{1,2}:\d{2}(:\d{2})?\s*([APap][Mm])?\s*[-\u2013]\s*""")
private val TIME = Regex("""^\d{1,2}:\d{2}(:\d{2})?\s*([APap][Mm])?\s*[-\u2013]?\s*""")
private val ONLY_NUMBERS = Regex("""^[\d/.:\-\s]+$""")

/** 붙여넣은 대화를 메시지 단위(줄)로 나눈다. 시간 표시와 빈 줄은 없애고, 최대 20줄까지 돌려준다. */
fun splitMessages(text: String): List<String> =
    text.lines().map { it.trim() }
        .map { line -> listOf(BRACKETED, DATE_TIME, TIME).fold(line) { acc, re -> acc.replaceFirst(re, "") }.trim() }
        .filter { it.isNotEmpty() && !ONLY_NUMBERS.matches(it) }
        .take(MAX_LINES)
