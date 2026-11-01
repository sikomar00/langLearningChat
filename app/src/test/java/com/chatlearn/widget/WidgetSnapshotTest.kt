package com.chatlearn.widget

import com.chatlearn.ai.FakeAiService
import com.chatlearn.data.EntryDraft
import com.chatlearn.data.EntryRepository
import com.chatlearn.data.inMemoryDb
import com.chatlearn.domain.Direction
import com.chatlearn.domain.Lang
import com.chatlearn.domain.Rating
import com.chatlearn.review.GradeOutcome
import com.chatlearn.review.ReviewSession
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneId
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class WidgetSnapshotTest {
    private val now = 1_790_000_000_000L

    @Test fun headlineSaysHowManyCardsAreWaiting() {
        assertEquals("복습할 카드 3장", widgetHeadline(WidgetSnapshot(cards = 9, due = 3, goalDone = 0, goal = 10, streak = 0)))
        assertEquals("오늘 복습 끝!", widgetHeadline(WidgetSnapshot(cards = 9, due = 0, goalDone = 4, goal = 10, streak = 2)))
        assertEquals("첫 문장을 저장해 보세요", widgetHeadline(WidgetSnapshot(cards = 0, due = 0, goalDone = 0, goal = 10, streak = 0)))
    }

    @Test fun detailShowsGoalAndStreak() {
        assertEquals("목표 4/10 · 2일 연속", widgetDetail(WidgetSnapshot(9, 0, 4, 10, 2)))
        assertEquals("목표 달성 · 2일 연속", widgetDetail(WidgetSnapshot(9, 0, 12, 10, 2)))
        assertEquals("2일 연속", widgetDetail(WidgetSnapshot(9, 0, 4, 0, 2))) // 목표를 끈 경우
        assertEquals("지금 시작해 볼까요?", widgetDetail(WidgetSnapshot(9, 3, 0, 0, 0)))
    }

    @Test fun snapshotMatchesTodaysReviewQueueGoalAndStreak() = runBlocking {
        val db = inMemoryDb()
        val repo = EntryRepository(db)
        val session = ReviewSession(repo, FakeAiService(), { now }, { 10 }, dailyGoal = { 10 })
        repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, "Hi", "안녕", "", null, emptyList()), now - 1)
        val zone = ZoneId.systemDefault()

        assertEquals(WidgetSnapshot(cards = 1, due = 1, goalDone = 0, goal = 10, streak = 0), loadWidgetSnapshot(session, repo, now, zone))

        val item = session.prepare(session.loadQueue().single())
        session.rate(item, Rating.GOOD, GradeOutcome.SelfGrade(item.reference, null), "Hi", 1_000)
        assertEquals(WidgetSnapshot(cards = 1, due = 0, goalDone = 1, goal = 10, streak = 1), loadWidgetSnapshot(session, repo, now, zone))
        db.close()
    }
}
