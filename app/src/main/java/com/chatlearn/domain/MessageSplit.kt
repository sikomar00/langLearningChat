package com.chatlearn.domain

private const val MAX_LINES = 20
private const val CONTEXT_EACH_SIDE = 2

/** 붙여넣은 대화의 한 줄. [speaker]는 대화 형식에서 이름을 알아낼 수 있을 때만. */
data class ChatLine(val text: String, val speaker: String? = null)

/** [truncated]면 [MAX_LINES]줄을 넘어 뒤를 잘랐다. */
data class ParsedChat(val lines: List<ChatLine>, val truncated: Boolean)

// 카카오톡 PC에서 복사: [이름] [오후 3:15] 메시지
private val KAKAO_PC = Regex("""^\[([^\]]{1,30})\]\s*\[(?:오전|오후)\s*\d{1,2}:\d{2}\]\s*(.*)$""")
// 카카오톡 대화 내보내기: 2026. 11. 13. 오후 3:15, 이름 : 메시지
private val KAKAO_EXPORT = Regex("""^\d{4}\.\s*\d{1,2}\.\s*\d{1,2}\.\s*(?:오전|오후)\s*\d{1,2}:\d{2},\s*(.{1,30}?)\s*:\s*(.*)$""")
// 안드로이드 카카오톡 내보내기: 2026년 11월 13일 오후 3:15, 이름 : 메시지
private val KAKAO_EXPORT_KO = Regex("""^\d{4}년\s*\d{1,2}월\s*\d{1,2}일\s*(?:오전|오후)\s*\d{1,2}:\d{2},\s*(.{1,30}?)\s*:\s*(.*)$""")
// 카카오톡 날짜만 있는 줄(2026년 11월 13일 금요일, 앞뒤 ---- 포함)과 시각만 있는 줄
private val KAKAO_DATE = Regex("""^[-\s]*\d{4}년\s*\d{1,2}월\s*\d{1,2}일(?:\s*[월화수목금토일]요일)?[-\s]*$""")
private val KOREAN_TIME = Regex("""^(?:오전|오후)\s*\d{1,2}:\d{2}$""")

// 줄 맨 앞의 시간 표시. 왓츠앱 등에서 복사하면 붙어 나온다.
private val BRACKETED = Regex("""^\[[^\]]{1,30}\]\s*""")
private val DATE_TIME = Regex("""^\d{1,2}[/.\-]\d{1,2}[/.\-]\d{2,4},?\s+\d{1,2}:\d{2}(:\d{2})?\s*([APap][Mm])?\s*[-–]\s*""")
private val TIME = Regex("""^\d{1,2}:\d{2}(:\d{2})?\s*([APap][Mm])?\s*[-–]?\s*""")
private val ONLY_NUMBERS = Regex("""^[\d/.:\-\s]+$""")
// 시간 표시를 떼어 낸 뒤 "이름: 메시지". 시간 표시가 있었을 때만 이름으로 본다("Note: bring food"는 그대로).
private val NAME_PREFIX = Regex("""^([^:\s]+(?: [^:\s]+){0,2}):\s+(.+)$""")

/** 붙여넣은 대화를 메시지 단위(줄)로 나눈다. 시간 표시·날짜 줄·빈 줄은 없애고, 말한 사람을 알아내면 따로 둔다. */
fun parseChat(text: String): ParsedChat {
    val all = text.lines().map { it.trim() }.mapNotNull(::parseLine)
    return ParsedChat(all.take(MAX_LINES), all.size > MAX_LINES)
}

/** 메시지 글자만(말한 사람 이름 없이). */
fun splitMessages(text: String): List<String> = parseChat(text).lines.map { it.text }

private fun parseLine(line: String): ChatLine? {
    if (line.isEmpty() || KAKAO_DATE.matches(line) || KOREAN_TIME.matches(line)) return null
    KAKAO_PC.matchEntire(line)?.let { return chatLine(it.groupValues[2], it.groupValues[1]) }
    KAKAO_EXPORT.matchEntire(line)?.let { return chatLine(it.groupValues[2], it.groupValues[1]) }
    KAKAO_EXPORT_KO.matchEntire(line)?.let { return chatLine(it.groupValues[2], it.groupValues[1]) }
    // 이름을 떼는 건 왓츠앱처럼 [시각]이나 날짜·시각이 붙은 대화 형식일 때만("10:30 Reminder: ..."는 그대로).
    val chatStamp = BRACKETED.containsMatchIn(line) || DATE_TIME.containsMatchIn(line)
    val stripped = listOf(BRACKETED, DATE_TIME, TIME).fold(line) { acc, re -> acc.replaceFirst(re, "") }.trim()
    if (stripped.isEmpty() || ONLY_NUMBERS.matches(stripped)) return null
    if (chatStamp) NAME_PREFIX.matchEntire(stripped)?.let { return chatLine(it.groupValues[2], it.groupValues[1]) }
    return ChatLine(stripped)
}

private fun chatLine(text: String, speaker: String): ChatLine? = text.trim().takeIf { it.isNotEmpty() }?.let { ChatLine(it, speaker.trim()) }

/** [index] 줄의 앞뒤 대화(각 2줄). 저장한 문장과 함께 두어 나중에 어떤 맥락이었는지 본다. 혼자면 null. */
fun contextAround(lines: List<ChatLine>, index: Int): String? {
    if (lines.size < 2) return null
    val from = (index - CONTEXT_EACH_SIDE).coerceAtLeast(0)
    val to = (index + CONTEXT_EACH_SIDE).coerceAtMost(lines.lastIndex)
    return (from..to).joinToString("\n") { i ->
        val l = lines[i]
        (if (i == index) "> " else "") + (l.speaker?.let { "$it: " } ?: "") + l.text
    }
}

/** [contextAround]가 만든 글을 줄로 되돌린다. 저장한 줄("> "로 시작)은 saved=true. 표시가 없는 맥락(답장한 말 등)은 모두 false. */
fun contextLines(context: String): List<Pair<String, Boolean>> =
    context.lines().filter { it.isNotBlank() }.map { if (it.startsWith("> ")) it.removePrefix("> ") to true else it to false }

