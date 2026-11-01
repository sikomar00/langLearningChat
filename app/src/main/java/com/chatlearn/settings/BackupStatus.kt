package com.chatlearn.settings

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private const val DAY_MS = 86_400_000L

/** 이만큼 지나면 백업을 권한다. 앱 데이터는 기기 백업에 들어가지 않아 폰을 바꾸면 사라진다. */
const val BACKUP_NUDGE_DAYS = 14

/** 문장이 이보다 적으면 잃을 게 적으니 재촉하지 않는다. */
const val BACKUP_NUDGE_MIN_ENTRIES = 10

fun backupStatus(lastBackupAt: Long, now: Long, zone: ZoneId): String {
    if (lastBackupAt <= 0) return "아직 백업한 적이 없어요"
    fun date(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(date(lastBackupAt), date(now))
    return "마지막 백업: " + when {
        days <= 0 -> "오늘"
        days == 1L -> "어제"
        else -> "${days}일 전"
    }
}

fun backupOverdue(lastBackupAt: Long, now: Long, entries: Int): Boolean =
    entries >= BACKUP_NUDGE_MIN_ENTRIES && (lastBackupAt <= 0 || now - lastBackupAt > BACKUP_NUDGE_DAYS * DAY_MS)
