package com.chatlearn.reminder

import java.time.Instant
import java.time.ZoneId

/** [now] 이후 처음 오는 [hour]시 정각. 지금이 정각이면 내일로 잡아서 같은 알림이 두 번 울리지 않게 한다. */
fun nextReminderAt(now: Long, hour: Int, zone: ZoneId): Long {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atTime(hour, 0).atZone(zone)
    val target = if (today.toInstant().toEpochMilli() > now) today else today.plusDays(1)
    return target.toInstant().toEpochMilli()
}

/** 알림 문구. 복습할 카드가 없으면 알림을 보내지 않는다(null). */
fun reminderText(due: Int, newCards: Int): String? = when {
    due <= 0 -> null
    newCards > 0 -> "복습할 카드 ${due}장이 기다려요. (새 카드 ${newCards}장 포함)"
    else -> "복습할 카드 ${due}장이 기다려요."
}
