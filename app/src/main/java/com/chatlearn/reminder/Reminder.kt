package com.chatlearn.reminder

import java.time.Instant
import java.time.ZoneId

/** [now] 이후 처음 오는 [hour]시 정각. 지금이 정각이면 내일로 잡아서 같은 알림이 두 번 울리지 않게 한다. */
fun nextReminderAt(now: Long, hour: Int, zone: ZoneId): Long {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atTime(hour, 0).atZone(zone)
    val target = if (today.toInstant().toEpochMilli() > now) today else today.plusDays(1)
    return target.toInstant().toEpochMilli()
}

enum class ReminderTone(val label: String) {
    PLAIN("담백하게"), GENTLE("부드럽게"), CHEER("응원하며");

    companion object {
        fun fromName(name: String?): ReminderTone = entries.firstOrNull { it.name == name } ?: PLAIN
    }
}

/** 알림을 받을 요일 마스크. 월요일이 0번 비트, 일요일이 6번 비트. */
const val ALL_DAYS = 0b1111111

fun isReminderDay(mask: Int, day: java.time.DayOfWeek): Boolean = mask and (1 shl (day.value - 1)) != 0

/** 알림 문구. 복습할 카드가 없으면 알림을 보내지 않는다(null). */
fun reminderText(due: Int, newCards: Int, tone: ReminderTone = ReminderTone.PLAIN): String? {
    if (due <= 0) return null
    val extra = if (newCards > 0) " (새 카드 ${newCards}장 포함)" else ""
    return when (tone) {
        ReminderTone.PLAIN -> "복습할 카드 ${due}장이 기다려요.$extra"
        ReminderTone.GENTLE -> "시간 날 때 가볍게 ${due}장만 볼까요?$extra"
        ReminderTone.CHEER -> "오늘도 한 걸음! ${due}장만 하면 끝나요.$extra"
    }
}
