package com.chatlearn.widget

import com.chatlearn.domain.langLabel
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.components.FilledButton
import androidx.glance.appwidget.components.OutlineButton
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.chatlearn.ChatLearnApp
import com.chatlearn.R
import com.chatlearn.domain.Lang
import com.chatlearn.domain.Rating
import com.chatlearn.ui.theme.DarkScheme
import com.chatlearn.ui.theme.LightScheme
import kotlinx.coroutines.flow.first

// 위젯에 보이는 카드는 위젯 상태에 적어 두고 그린다(버튼을 누를 때마다 DB를 다시 읽지 않게).
private val CARD_ID = longPreferencesKey("card_id")
private val PROMPT = stringPreferencesKey("prompt")
private val ANSWER = stringPreferencesKey("answer")
private val ANSWER_LANG = stringPreferencesKey("answer_lang")
private val LEFT = intPreferencesKey("left")
private val REVEALED = booleanPreferencesKey("revealed")
private val MESSAGE = stringPreferencesKey("message")
private val RATING = ActionParameters.Key<String>("rating")
// 누른 버튼이 어느 카드의 것인지. 빠르게 두 번 누르면 두 번째는 이미 넘어간 카드라 평가하지 않는다.
private val RATED_CARD = ActionParameters.Key<Long>("card")
private const val NO_CARD = -1L

/** 홈 화면에서 한 장씩 복습하는 위젯: 한국어 뜻을 보고 떠올린 뒤 정답을 열어 스스로 평가한다. */
class FlashcardWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        loadNext(context, id)
        val dynamic = Build.VERSION.SDK_INT >= 31 && runCatching { container(context).settings.settings.first().dynamicColor }.getOrDefault(false)
        provideContent {
            if (dynamic) GlanceTheme { FlashcardContent(context) }
            else GlanceTheme(colors = ColorProviders(light = LightScheme, dark = DarkScheme)) { FlashcardContent(context) }
        }
    }
}

class FlashcardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FlashcardWidget()
}

private fun container(context: Context) = (context.applicationContext as ChatLearnApp).container

/** 오늘 큐의 첫 카드를 위젯 상태에 적는다. 같은 카드면 정답을 열어 둔 상태를 그대로 둔다. 못 읽으면 그렇다고 적는다. */
private suspend fun loadNext(context: Context, id: GlanceId) {
    val card = runCatching { nextWidgetCard(container(context).reviewSession()) }
    updateAppWidgetState(context, id) { p ->
        card.fold({ p.write(it); p.remove(MESSAGE) }, { p[MESSAGE] = "카드를 읽지 못했어요. 앱을 열어 확인해 주세요." })
    }
}

/**
 * 한 장 복습 위젯을 모두 DB에서 다시 읽어 그린다(그리는 중인 위젯은 update만으로는 저장해 둔 카드를 다시 그릴 뿐이다).
 * shortcut: "다시"로 10분 뒤에 돌아오는 카드는 앱을 나갈 때나 1시간마다 다시 읽을 때 나타난다. 바로 보여야 하면 그 시각에 다시 읽게 예약한다.
 */
internal suspend fun refreshFlashcards(context: Context) {
    GlanceAppWidgetManager(context).getGlanceIds(FlashcardWidget::class.java).forEach { id ->
        loadNext(context, id)
        FlashcardWidget().update(context, id)
    }
}

private fun MutablePreferences.write(card: WidgetCard?) {
    if (card == null) { this[CARD_ID] = NO_CARD; this[LEFT] = 0; return }
    if (this[CARD_ID] != card.cardId) this[REVEALED] = false
    this[CARD_ID] = card.cardId
    this[PROMPT] = card.prompt
    this[ANSWER] = card.answer
    this[ANSWER_LANG] = card.answerLang.code
    this[LEFT] = card.left
}

class RevealCardAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { it[REVEALED] = true }
        FlashcardWidget().update(context, glanceId)
    }
}

class RateCardAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val rating = parameters[RATING]?.let(Rating::valueOf) ?: return
        val cardId = parameters[RATED_CARD] ?: return
        // 그사이 앱에서 복습했으면(false) 평가하지 않고 다음 카드만 보여 준다. 저장에 실패하면 알린다.
        val failed = runCatching { rateWidgetCard(container(context).reviewSession(), cardId, rating) }.isFailure
        refreshReviewWidget(context)
        if (failed) {
            updateAppWidgetState(context, glanceId) { it[MESSAGE] = "평가를 저장하지 못했어요. 앱에서 다시 해 주세요." }
            FlashcardWidget().update(context, glanceId)
        }
    }
}

@Composable
private fun FlashcardContent(context: Context) {
    val p = currentState<Preferences>()
    val bg = if (Build.VERSION.SDK_INT >= 31) GlanceModifier.background(GlanceTheme.colors.widgetBackground).cornerRadius(20.dp)
    else GlanceModifier.background(ImageProvider(R.drawable.widget_background))
    val open = actionStartActivity(openIntent(context, "review"))
    Column(GlanceModifier.fillMaxSize().appWidgetBackground().then(bg).padding(14.dp)) {
        val cardId = p[CARD_ID]
        val left = p[LEFT] ?: 0
        val message = p[MESSAGE]
        Text(
            if (cardId != null && cardId != NO_CARD) "ChatLearn · 남은 ${left}장" else "ChatLearn",
            GlanceModifier.clickable(open), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium),
        )
        Spacer(GlanceModifier.height(6.dp))
        if (message != null) Text(message, style = TextStyle(color = GlanceTheme.colors.error, fontSize = 12.sp), maxLines = 2)
        if (cardId == null || cardId == NO_CARD) {
            if (message != null) return@Column
            Text("오늘 복습 끝!", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold))
            Text("새 문장을 저장하면 여기서 바로 복습할 수 있어요.", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 2)
            return@Column
        }
        val lang = Lang.fromCode(p[ANSWER_LANG].orEmpty()) ?: Lang.EN
        Text(p[PROMPT].orEmpty(), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold), maxLines = 2)
        Text("${langLabel(lang)}로 떠올려 보세요", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
        if (p[REVEALED] == true) {
            Spacer(GlanceModifier.height(6.dp))
            Text(p[ANSWER].orEmpty(), style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 16.sp, fontWeight = FontWeight.Medium), maxLines = 2)
        }
        Spacer(GlanceModifier.defaultWeight())
        if (p[REVEALED] == true) {
            Row(GlanceModifier.fillMaxWidth()) {
                OutlineButton(
                    "다시", contentColor = GlanceTheme.colors.primary, modifier = GlanceModifier.defaultWeight(), maxLines = 1,
                    onClick = actionRunCallback<RateCardAction>(actionParametersOf(RATING to Rating.AGAIN.name, RATED_CARD to cardId)),
                )
                Spacer(GlanceModifier.width(8.dp))
                FilledButton(
                    "알았어요", modifier = GlanceModifier.defaultWeight(), maxLines = 1,
                    onClick = actionRunCallback<RateCardAction>(actionParametersOf(RATING to Rating.GOOD.name, RATED_CARD to cardId)),
                )
            }
        } else {
            FilledButton("정답 보기", onClick = actionRunCallback<RevealCardAction>(), modifier = GlanceModifier.fillMaxWidth(), maxLines = 1)
        }
    }
}
