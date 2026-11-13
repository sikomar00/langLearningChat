package com.chatlearn.domain

data class CardState(
    val intervalDays: Double,
    val ease: Double,
    val reps: Int,
    val lapses: Int,
    val dueAt: Long,
    val lastReviewedAt: Long?,
)

object Scheduler {
    const val MIN_EASE = 1.3
    /** 간격이 이 일수 이상이면 "익숙한" 카드로 본다(복습 방식 선택과 통계가 함께 쓴다). */
    const val MATURE_DAYS = 21.0
    private const val DAY_MS = 86_400_000L
    private const val RELEARN_MS = 10 * 60_000L

    fun initial(now: Long) = CardState(0.0, 2.5, 0, 0, now, null)

    fun next(card: CardState, rating: Rating, now: Long, jitter: Double = 0.0): CardState {
        require(jitter in -0.05..0.05) { "jitter must be within ±5%" }
        val base = when (card.reps) { 0 -> 1.0; 1 -> 3.0; else -> card.intervalDays }
        // 기한 전에 미리 복습하면 실제로 버틴 시간만큼만 인정한다(선택 복습을 반복해도 간격이 부풀지 않게).
        val elapsed = card.lastReviewedAt?.let { (now - it).toDouble() / DAY_MS }
        // 지터(±5%)로 기한이 조금 당겨진 카드는 제때 복습한 것으로 본다. 기한 전에만 "미리 복습"이다.
        if (card.reps >= 2 && elapsed != null && now < card.dueAt && rating != Rating.AGAIN) {
            val grown = when (rating) {
                Rating.HARD -> card.intervalDays
                Rating.EASY -> elapsed * card.ease * 1.3
                else -> elapsed * card.ease
            }
            return schedule(card, maxOf(card.intervalDays, grown), card.ease, now, jitter)
        }
        return when (rating) {
            Rating.AGAIN -> card.copy(
                intervalDays = 1.0,
                ease = (card.ease - 0.2).coerceAtLeast(MIN_EASE),
                reps = 0,
                lapses = card.lapses + 1,
                dueAt = now + RELEARN_MS,
                lastReviewedAt = now,
            )
            Rating.HARD -> schedule(card, maxOf(1.0, card.intervalDays * 1.2), card.ease - 0.15, now, jitter)
            Rating.GOOD -> schedule(card, if (card.reps < 2) base else card.intervalDays * card.ease, card.ease, now, jitter)
            Rating.EASY -> schedule(card, base * card.ease * 1.3, card.ease + 0.15, now, jitter)
        }
    }

    private fun schedule(card: CardState, interval: Double, ease: Double, now: Long, jitter: Double) =
        card.copy(
            intervalDays = interval,
            ease = ease.coerceAtLeast(MIN_EASE),
            reps = card.reps + 1,
            dueAt = now + (interval * (1 + jitter) * DAY_MS).toLong(),
            lastReviewedAt = now,
        )
}
