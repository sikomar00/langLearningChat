package com.chatlearn.domain

import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToLong

data class CardState(
    val intervalDays: Double,
    /** 예전(SM-2) 일정의 쉬움 정도. FSRS는 쓰지 않고, 안정도가 없는 옛 카드의 난이도를 정할 때만 읽는다. */
    val ease: Double,
    val reps: Int,
    val lapses: Int,
    val dueAt: Long,
    val lastReviewedAt: Long?,
    val stability: Double? = null,
    val difficulty: Double? = null,
)

/**
 * FSRS(Free Spaced Repetition Scheduler) 일정. 카드마다 기억 안정도(S, 일)와 난이도(D, 1~10)를 두고,
 * 실제로 지난 시간으로 지금 기억할 확률(R)을 구해 다음 간격을 정한다. 목표 기억률이 90%면 간격 = 안정도.
 * 가중치는 FSRS-5 공개 기본값이고 내 기록으로 최적화하지 않는다. 같은 날 다시 볼 때는 FSRS-5의 단기 공식 대신
 * 긴 간격 공식을 그대로 써서(지난 시간이 짧으면 거의 늘지 않는다) 선택 복습을 반복해도 간격이 부풀지 않게 했다.
 */
object Scheduler {
    /** 간격이 이 일수 이상이면 "익숙한" 카드로 본다(복습 방식 선택과 통계가 함께 쓴다). */
    const val MATURE_DAYS = 21.0
    const val DEFAULT_RETENTION = 0.9
    /** 설정에서 고를 수 있는 기억 목표 범위. */
    val RETENTION_RANGE = 0.8..0.97
    /** 하루에 내는 복습 카드(새 카드 제외) 기본 최대 수. */
    const val DEFAULT_MAX_REVIEWS = 50
    private const val DAY_MS = 86_400_000L
    private const val RELEARN_MS = 10 * 60_000L
    private const val MAX_INTERVAL_DAYS = 36_500.0
    private const val DECAY = -0.5
    private const val FACTOR = 19.0 / 81.0
    private val W = doubleArrayOf(
        0.40255, 1.18385, 3.173, 15.69105, 7.1949, 0.5345, 1.4604, 0.0046, 1.54575, 0.1192,
        1.01925, 1.9395, 0.11, 0.29605, 2.2698, 0.2315, 2.9898, 0.51655, 0.6621,
    )

    fun initial(now: Long) = CardState(0.0, 2.5, 0, 0, now, null)

    /** 예전 일정의 ease(1.3~)를 FSRS 난이도로 옮긴다: 2.5(보통) → 5, 1.3 → 10. DB 마이그레이션과 같은 식. */
    fun difficultyFromEase(ease: Double): Double = (5.0 - (ease - 2.5) * 5.0).coerceIn(1.0, 10.0)

    /** [elapsedDays] 뒤에 기억하고 있을 확률. */
    fun retrievability(elapsedDays: Double, stability: Double): Double = (1 + FACTOR * elapsedDays / stability).pow(DECAY)

    fun next(card: CardState, rating: Rating, now: Long, jitter: Double = 0.0, retention: Double = DEFAULT_RETENTION): CardState {
        require(jitter in -0.05..0.05) { "jitter must be within ±5%" }
        require(retention in RETENTION_RANGE) { "retention must be within $RETENTION_RANGE" }
        val g = rating.ordinal + 1 // AGAIN=1 … EASY=4
        val last = card.lastReviewedAt
        // 옛 카드(안정도 없음)는 예전 간격·ease에서 이어받는다.
        val oldS = card.stability ?: card.intervalDays.takeIf { last != null && it > 0 }
        val (s, d) = if (oldS == null || last == null) {
            W[g - 1] to initialDifficulty(g)
        } else {
            val oldD = card.difficulty ?: difficultyFromEase(card.ease)
            val r = retrievability(((now - last).coerceAtLeast(0)).toDouble() / DAY_MS, oldS)
            val s = if (rating == Rating.AGAIN) forgetStability(oldD, oldS, r) else recallStability(oldD, oldS, r, rating)
            s to nextDifficulty(oldD, g)
        }
        val interval = (s / FACTOR * (retention.pow(1 / DECAY) - 1)).coerceIn(1.0, MAX_INTERVAL_DAYS).roundToLong().toDouble()
        return if (rating == Rating.AGAIN) {
            card.copy(
                // 이 앱에서 lapses는 "틀린 횟수"다(자주 틀리는 문장·도움말에 쓴다). 새 카드를 몰라도 센다.
                intervalDays = interval, reps = 0, lapses = card.lapses + 1,
                dueAt = now + RELEARN_MS, lastReviewedAt = now, stability = s, difficulty = d,
            )
        } else {
            card.copy(
                intervalDays = interval, reps = card.reps + 1,
                dueAt = now + (interval * (1 + jitter) * DAY_MS).toLong(), lastReviewedAt = now, stability = s, difficulty = d,
            )
        }
    }

    private fun initialDifficulty(g: Int) = (W[4] - exp(W[5] * (g - 1)) + 1).coerceIn(1.0, 10.0)

    private fun nextDifficulty(d: Double, g: Int): Double {
        val damped = d + (-W[6] * (g - 3)) * (10 - d) / 9
        // 쉬움(EASY) 기준값 쪽으로 조금씩 되돌려 난이도가 한쪽 끝에 갇히지 않게 한다.
        return (W[7] * initialDifficulty(4) + (1 - W[7]) * damped).coerceIn(1.0, 10.0)
    }

    private fun recallStability(d: Double, s: Double, r: Double, rating: Rating): Double {
        val hard = if (rating == Rating.HARD) W[15] else 1.0
        val easy = if (rating == Rating.EASY) W[16] else 1.0
        return s * (exp(W[8]) * (11 - d) * s.pow(-W[9]) * (exp(W[10] * (1 - r)) - 1) * hard * easy + 1)
    }

    private fun forgetStability(d: Double, s: Double, r: Double): Double =
        minOf(s, W[11] * d.pow(-W[12]) * ((s + 1).pow(W[13]) - 1) * exp(W[14] * (1 - r)))
}
