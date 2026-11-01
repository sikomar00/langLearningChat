package com.chatlearn.stats

import com.chatlearn.data.*
import com.chatlearn.domain.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StatsCalculatorTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 1)
    private val now = ms(today, 12, 0)

    private fun ms(d: LocalDate, h: Int, m: Int = 0) = LocalDateTime.of(d, java.time.LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()

    private var nextLog = 1L
    private fun log(cardId: Long, at: Long, rating: Rating) =
        ReviewLogEntity(nextLog++, cardId, at, Lang.KO, "x", null, null, rating)

    private fun card(id: Long, entryId: Long, lang: Lang = Lang.EN, interval: Double = 1.0, lapses: Int = 0,
                     last: Long? = 1L, due: Long = 0L) =
        ReviewCardEntity(id, entryId, lang, due, interval, 2.5, 1, lapses, last)

    private fun item(id: Long, text: String, lang: Lang = Lang.EN, tags: List<String> = emptyList()) =
        LibraryItem(EntryEntity(id, 0, Direction.RECEIVED, lang, "", null), mapOf(lang to text, Lang.KO to "한"), tags)

    private fun compute(logs: List<ReviewLogEntity>, cards: List<ReviewCardEntity> = emptyList(), items: List<LibraryItem> = emptyList()) =
        StatsCalculator.compute(logs, cards, items, now, zone)

    @Test fun emptyDataGivesZerosAndNullRate() {
        val s = compute(emptyList())
        assertEquals(0, s.today.reviews)
        assertNull(s.today.rate)
        assertEquals(0, s.streak)
        assertEquals(7, s.last7.size)
        assertEquals(CardMix(0, 0, 0), s.cards)
    }

    @Test fun todayCountsOnlyTodayInTheGivenZoneAndAgainIsNotRemembered() {
        val logs = listOf(
            log(1, ms(today, 0, 0), Rating.GOOD),            // 오늘 00:00 포함
            log(1, ms(today, 9), Rating.AGAIN),
            log(1, ms(today, 11), Rating.HARD),
            log(1, ms(today.minusDays(1), 23, 59), Rating.GOOD), // 어제 23:59 제외
        )
        val s = compute(logs)
        assertEquals(Retention(3, 2), s.today)
        assertEquals(2.0 / 3, s.today.rate!!, 1e-9)
    }

    @Test fun last7IsOldestToNewestWithZeroDays() {
        val logs = listOf(log(1, ms(today, 8), Rating.GOOD), log(1, ms(today.minusDays(6), 8), Rating.GOOD),
            log(1, ms(today.minusDays(7), 8), Rating.GOOD)) // 7일 밖
        val s = compute(logs)
        assertEquals(today.minusDays(6), s.last7.first().date)
        assertEquals(today, s.last7.last().date)
        assertEquals(1, s.last7.first().reviews)
        assertEquals(1, s.last7.last().reviews)
        assertEquals(0, s.last7[3].reviews)
    }

    @Test fun streakCountsConsecutiveDaysEndingToday() {
        val logs = (0..2).map { log(1, ms(today.minusDays(it.toLong()), 10), Rating.GOOD) }
        assertEquals(3, compute(logs).streak)
    }

    @Test fun streakSurvivesUntilTodaysFirstReview() {
        val logs = (1..2).map { log(1, ms(today.minusDays(it.toLong()), 10), Rating.GOOD) } // 오늘은 아직 없음
        assertEquals(2, compute(logs).streak)
    }

    @Test fun streakBreaksOnAGap() {
        val logs = listOf(log(1, ms(today, 10), Rating.GOOD), log(1, ms(today.minusDays(2), 10), Rating.GOOD))
        assertEquals(1, compute(logs).streak)
    }

    @Test fun streakIsZeroWhenLastReviewWasTwoDaysAgo() {
        assertEquals(0, compute(listOf(log(1, ms(today.minusDays(2), 10), Rating.GOOD))).streak)
    }

    @Test fun cardMixSplitsNewLearningAndMatureAt21Days() {
        val cards = listOf(
            card(1, 1, last = null, interval = 0.0),
            card(2, 2, interval = 20.9),
            card(3, 3, interval = 21.0),
            card(4, 4, interval = 90.0),
        )
        assertEquals(CardMix(new = 1, learning = 1, mature = 2), compute(emptyList(), cards).cards)
    }

    @Test fun weakestListsMostLapsedFirstOnlyWithLapsesAndTopFive() {
        val cards = (1L..7L).map { card(it, it, lapses = if (it == 7L) 0 else it.toInt()) }
        val items = (1L..7L).map { item(it, "s$it") }
        val weak = compute(emptyList(), cards, items).weakest
        assertEquals(listOf(6L, 5L, 4L, 3L, 2L), weak.map { it.entryId })
        assertEquals("s6", weak.first().text)
        assertEquals(6, weak.first().lapses)
    }

    @Test fun retentionByLanguageUsesTheCardsAnswerLanguage() {
        val cards = listOf(card(1, 1, Lang.EN), card(2, 2, Lang.ES))
        val logs = listOf(log(1, now, Rating.GOOD), log(1, now, Rating.AGAIN), log(2, now, Rating.EASY))
        val s = compute(logs, cards)
        assertEquals(Retention(2, 1), s.byLang[Lang.EN])
        assertEquals(Retention(1, 1), s.byLang[Lang.ES])
    }

    @Test fun retentionByTagCountsEachTagOfTheEntry() {
        val cards = listOf(card(1, 1), card(2, 2))
        val items = listOf(item(1, "a", tags = listOf("Alex", "여행")), item(2, "b", tags = listOf("Alex")))
        val logs = listOf(log(1, now, Rating.GOOD), log(2, now, Rating.AGAIN))
        val s = compute(logs, cards, items)
        assertEquals(Retention(2, 1), s.byTag["Alex"])
        assertEquals(Retention(1, 1), s.byTag["여행"])
    }

    @Test fun logsWithoutACardAreIgnoredByLangAndTag() {
        val s = compute(listOf(log(99, now, Rating.GOOD)))
        assertEquals(1, s.today.reviews)
        assertEquals(emptyMap(), s.byLang)
    }

    @Test fun sentenceStatsSummarisesOneEntry() {
        val c = card(1, 5, lapses = 2, last = 777L, due = 999L)
        val logs = listOf(log(1, 1, Rating.GOOD), log(1, 2, Rating.AGAIN), log(1, 3, Rating.EASY), log(2, 4, Rating.GOOD))
        val st = StatsCalculator.sentenceStats(5, logs, listOf(c, card(2, 6)))
        assertEquals(SentenceStats(reviews = 3, remembered = 2, lapses = 2, lastReviewedAt = 777L, dueAt = 999L), st)
    }

    @Test fun sentenceStatsForAnUnreviewedEntryIsEmpty() {
        val st = StatsCalculator.sentenceStats(5, emptyList(), listOf(card(1, 5, last = null)))
        assertEquals(0, st.reviews)
        assertNull(st.lastReviewedAt)
    }
}
