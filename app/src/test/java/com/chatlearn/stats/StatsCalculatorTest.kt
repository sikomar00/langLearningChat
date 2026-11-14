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
import kotlin.test.assertTrue

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

    private fun logEx(cardId: Long, rating: Rating, ex: ExerciseKind, ms: Long?) =
        ReviewLogEntity(nextLog++, cardId, now, Lang.KO, "x", null, null, rating, ms, ex)

    @Test fun retentionIsSplitByExerciseKind() {
        val logs = listOf(
            logEx(1, Rating.GOOD, ExerciseKind.CLOZE, 1000), logEx(1, Rating.AGAIN, ExerciseKind.CLOZE, 3000),
            logEx(1, Rating.EASY, ExerciseKind.DICTATION, null),
        )
        val s = compute(logs)
        assertEquals(Retention(2, 1), s.byExercise[ExerciseKind.CLOZE])
        assertEquals(Retention(1, 1), s.byExercise[ExerciseKind.DICTATION])
        assertNull(s.byExercise[ExerciseKind.TRANSLATE])
    }

    @Test fun averageResponseTimeIgnoresLogsWithoutIt() {
        assertEquals(2000L, compute(listOf(logEx(1, Rating.GOOD, ExerciseKind.CLOZE, 1000), logEx(1, Rating.GOOD, ExerciseKind.CLOZE, 3000), logEx(1, Rating.GOOD, ExerciseKind.TRANSLATE, null))).avgResponseMs)
        assertNull(compute(listOf(logEx(1, Rating.GOOD, ExerciseKind.TRANSLATE, null))).avgResponseMs)
    }

    @Test fun weakestCountsSentencesNotCardsInTheSentencesOwnLanguage() {
        val enEntry = LibraryItem(EntryEntity(1, 0, Direction.RECEIVED, Lang.EN, "", null),
            mapOf(Lang.EN to "How are you?", Lang.ES to "¿Cómo estás?", Lang.KO to "한"), emptyList())
        val weak = compute(emptyList(), listOf(card(1, 1, Lang.ES, lapses = 2), card(2, 1, Lang.EN, lapses = 1)), listOf(enEntry)).weakest
        assertEquals(WeakItem(1, "How are you?", 3, Lang.EN), weak.single())
    }

    @Test fun emptyDataGivesZerosAndNullRate() {
        val s = compute(emptyList())
        assertEquals(0, s.growth.weekReviews)
        assertNull(s.growth.week.rate)
        assertEquals(0, s.streak)
        assertEquals(7, s.last7.size)
        assertEquals(0, s.growth.mastered)
    }

    @Test fun todayCountsOnlyTodayInTheGivenZoneAndAgainIsNotRemembered() {
        val logs = listOf(
            log(1, ms(today, 0, 0), Rating.GOOD),            // 오늘 00:00 포함
            log(1, ms(today, 9), Rating.AGAIN),
            log(1, ms(today, 11), Rating.HARD),
            log(1, ms(today.minusDays(1), 23, 59), Rating.GOOD), // 어제 23:59 제외
        )
        val s = compute(logs)
        assertEquals(3, s.last7.last().reviews)
        assertEquals(1, s.last7[5].reviews)
        assertEquals(Retention(4, 3), s.growth.week)
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

    @Test fun growthCountsSentenceStagesAtTwentyOneDays() {
        val cards = listOf(
            card(1, 1, last = null, interval = 0.0),
            card(2, 2, interval = 20.9),
            card(3, 3, interval = 21.0),
            card(4, 4, interval = 90.0),
        )
        val g = compute(emptyList(), cards, (1L..4L).map { item(it, "s$it") }).growth
        assertEquals(listOf("익힘" to 2, "거의" to 1, "익히는 중" to 0, "새 문장" to 1), g.stages)
        assertEquals(2, g.mastered)
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
        assertEquals(1, s.last7.last().reviews)
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

    @Test fun responseTimeIgnoresOneVeryLongIdleOutlier() {
        val logs = listOf(1000L, 2000L, 3000L, 1_800_000L).map { logEx(1, Rating.GOOD, ExerciseKind.CLOZE, it) }
        assertEquals(2500L, compute(logs).avgResponseMs)
    }

    @Test fun heatmapCoversTwelveWeeksFromAMondayToToday() {
        // 2026-11-01은 일요일: 이번 주가 꽉 차서 12주 × 7일
        val s = compute(listOf(
            log(1, ms(today, 9), Rating.GOOD),
            log(1, ms(today.minusDays(3), 9), Rating.GOOD), log(1, ms(today.minusDays(3), 10), Rating.AGAIN),
            log(1, ms(today.minusDays(200), 9), Rating.GOOD), // 12주보다 오래된 기록은 빠진다
        ))
        assertEquals(java.time.DayOfWeek.MONDAY, s.heatmap.first().date.dayOfWeek)
        assertEquals(today, s.heatmap.last().date)
        assertEquals(12 * 7, s.heatmap.size)
        assertEquals(1, s.heatmap.last().reviews)
        assertEquals(2, s.heatmap.single { it.date == today.minusDays(3) }.reviews)
        assertEquals(3, s.heatmap.sumOf { it.reviews })
    }

    @Test fun heatmapStopsAtTodayInTheMiddleOfAWeek() {
        val wednesday = LocalDate.of(2026, 11, 4)
        val s = StatsCalculator.compute(emptyList(), emptyList(), emptyList(), ms(wednesday, 12), zone)
        assertEquals(11 * 7 + 3, s.heatmap.size)
        assertEquals(wednesday, s.heatmap.last().date)
    }

    @Test fun heatLevelGrowsWithTheNumberOfReviews() {
        assertEquals(listOf(0, 1, 1, 2, 3, 4), listOf(0, 1, 4, 5, 10, 20).map(::heatLevel))
    }

    @Test fun forecastCountsReviewedCardsDuePerDayWithOverdueToday() {
        val due = listOf(
            ms(today.minusDays(3), 9, 0), // 밀린 카드는 오늘로
            ms(today, 23, 0),
            ms(today.plusDays(1), 8, 0), ms(today.plusDays(1), 20, 0),
            ms(today.plusDays(6), 10, 0),
            ms(today.plusDays(7), 10, 0), // 7일 밖
        )
        val f = StatsCalculator.forecast(due, now, zone, days = 7)
        assertEquals(7, f.size)
        assertEquals(today, f.first().date)
        assertEquals(listOf(2, 2, 0, 0, 0, 0, 1), f.map { it.reviews })
    }

    @Test fun growthComparesThisWeekWithLastWeekAndCountsNewlyMastered() {
        val cards = listOf(card(1, 1, interval = 30.0), card(2, 2, interval = 40.0), card(3, 3))
        val logs = listOf(
            // 1번: 10일 간격으로 복습해 이번 주에 익힘이 됨
            log(1, ms(today.minusDays(12), 9), Rating.GOOD), log(1, ms(today.minusDays(2), 9), Rating.GOOD),
            // 2번: 30일 만의 복습이라 원래 익힘이던 문장
            log(2, ms(today.minusDays(33), 9), Rating.GOOD), log(2, ms(today.minusDays(3), 9), Rating.GOOD),
            log(3, ms(today, 9), Rating.AGAIN), log(3, ms(today.minusDays(8), 9), Rating.GOOD),
        )
        val P = com.chatlearn.domain.TagKind.PERSON
        val items = listOf(item(1, "a").copy(tags = listOf("Lucia"), tagKinds = mapOf("Lucia" to P)), item(2, "b", Lang.ES), item(3, "c"))
        val g = compute(logs, cards, items).growth
        assertEquals(2, g.mastered)
        assertEquals(3, g.weekReviews)
        assertEquals(2, g.prevWeekReviews)
        assertEquals(2, g.week.remembered)
        assertEquals(1, g.monthDays) // 오늘이 11월 1일이라 이번 달 복습한 날은 오늘 하루
    }

    @Test fun factLineStatesOnlyRecordedFacts() {
        val P = com.chatlearn.domain.TagKind.PERSON
        assertEquals("아직 익힌 문장이 없어요.", GrowthCalculator.factLine(emptyList()))
        val es = (1L..3L).map { item(it, "s$it", Lang.ES).copy(tags = if (it < 3) listOf("Lucia") else emptyList(), tagKinds = mapOf("Lucia" to P)) }
        assertEquals("스페인어 문장 3개, 영어 문장 1개를 익혔어요. 가장 많이 나온 친구는 Lucia, 2개예요.", GrowthCalculator.factLine(es + item(4, "e")))
        assertEquals("영어 문장 1개를 익혔어요.", GrowthCalculator.factLine(listOf(item(4, "e"))))
    }

    @Test fun friendsAreRankedBySentencesWithTheirMasteredCount() {
        val P = com.chatlearn.domain.TagKind.PERSON
        fun withFriend(id: Long, name: String) = item(id, "s$id").copy(tags = listOf(name), tagKinds = mapOf(name to P))
        val cards = listOf(card(1, 1, interval = 30.0), card(2, 2), card(3, 3), card(4, 4, interval = 25.0))
        val items = listOf(withFriend(1, "Lucia"), withFriend(2, "Lucia"), withFriend(3, "Tom"), item(4, "s4"))
        assertEquals(listOf(FriendGrowth("Lucia", 2, 1), FriendGrowth("Tom", 1, 0)), compute(emptyList(), cards, items).growth.friends)
    }

    @Test fun outlookPutsOverdueOnTodayAndSpreadsNewCardsByTheDailyLimit() {
        val cards = listOf(
            card(1, 1, due = now - 5 * 86_400_000L), card(2, 2, due = ms(today.plusDays(3), 9)),
            card(3, 3, last = null), card(4, 4, last = null), card(5, 5, last = null),
        )
        val o = GrowthCalculator.outlook(cards, emptyList(), now, zone, newPerDay = 2)
        assertEquals(30, o.size)
        assertEquals(OutlookDay(today, 1, 2), o[0])
        assertEquals(OutlookDay(today.plusDays(1), 0, 1), o[1])
        assertEquals(1, o[3].reviews)
        assertEquals(3, o.sumOf { it.new })
    }

    @Test fun excludedEntriesLeaveEveryStatAndKnownEntriesCountAsMastered() {
        val cards = listOf(card(1, 1, lapses = 2), card(2, 2, lapses = 3), card(3, 3, last = null))
        val logs = listOf(log(1, ms(today, 9), Rating.AGAIN), log(2, ms(today, 10), Rating.GOOD))
        val items = listOf(
            item(1, "a").let { it.copy(entry = it.entry.copy(excludeFromStats = true)) },
            item(2, "b").let { it.copy(entry = it.entry.copy(known = true)) },
            item(3, "c"),
        )
        val s = compute(logs, cards, items)
        assertEquals(Retention(1, 1), s.byLang[Lang.EN]) // 1번(통계 제외)의 "다시"는 빠진다
        assertEquals(Retention(1, 1), s.growth.week)
        // 복습한 날·복습 수는 모든 기록으로 센다(연속·달력이 화면마다 다르지 않게).
        assertEquals(2, s.last7.last().reviews)
        assertEquals(2, s.growth.weekReviews)
        assertEquals(1, s.growth.mastered) // 2번은 알아요라 익힘
        assertEquals(listOf("익힘" to 1, "거의" to 0, "익히는 중" to 0, "새 문장" to 1), s.growth.stages)
        assertTrue(s.weakest.isEmpty()) // 1번은 통계 제외, 2번은 알아요
        val o = GrowthCalculator.outlook(cards, items, now, zone, newPerDay = 5)
        assertEquals(1, o.sumOf { it.reviews }) // 알아요인 2번 카드는 예보에서 빠진다
    }
}
