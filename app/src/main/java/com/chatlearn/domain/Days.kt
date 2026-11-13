package com.chatlearn.domain

import java.time.Instant
import java.time.ZoneId

/** [epochMs]가 속한 날(기기 시간대)의 0시. [plusDays]만큼 뒤 날의 0시도 구한다(서머타임에도 정확). */
fun startOfDay(epochMs: Long, plusDays: Long = 0, zone: ZoneId = ZoneId.systemDefault()): Long =
    Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate().plusDays(plusDays).atStartOfDay(zone).toInstant().toEpochMilli()
