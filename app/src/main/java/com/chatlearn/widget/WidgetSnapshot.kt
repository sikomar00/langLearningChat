package com.chatlearn.widget

import com.chatlearn.data.EntryRepository
import com.chatlearn.review.ReviewSession
import com.chatlearn.stats.StatsCalculator
import java.time.ZoneId

/** 홈 화면 위젯에 보여 줄 오늘 상태. [due]는 복습 탭의 "오늘 복습할 카드"와 같은 수다. */
data class WidgetSnapshot(val cards: Int, val due: Int, val goalDone: Int, val goal: Int, val streak: Int)

suspend fun loadWidgetSnapshot(session: ReviewSession, repo: EntryRepository, now: Long, zone: ZoneId): WidgetSnapshot {
    val due = session.loadQueue().size
    val goal = session.todayProgress()
    // 전체 통계 대신 필요한 숫자만 읽는다(위젯은 자주 다시 그려진다).
    val streak = StatsCalculator.streak(repo.reviewTimes(), now, zone)
    return WidgetSnapshot(repo.countCards(), due, goal.done, goal.goal, streak)
}

fun widgetHeadline(s: WidgetSnapshot): String = when {
    s.cards == 0 -> "첫 문장을 저장해 보세요"
    s.due > 0 -> "복습할 카드 ${s.due}장"
    else -> "오늘 복습 끝!"
}

fun widgetDetail(s: WidgetSnapshot): String {
    val parts = buildList {
        if (s.goal > 0) add(if (s.goalDone >= s.goal) "목표 달성" else "목표 ${s.goalDone}/${s.goal}")
        if (s.streak > 0) add("${s.streak}일 연속")
    }
    return parts.joinToString(" · ").ifEmpty { if (s.due > 0) "지금 시작해 볼까요?" else "친구와 나눈 말을 저장해 보세요" }
}
