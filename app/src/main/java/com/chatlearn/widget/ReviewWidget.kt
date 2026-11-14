package com.chatlearn.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.components.FilledButton
import androidx.glance.appwidget.components.OutlineButton
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.TextStyle
import com.chatlearn.ACTION_OPEN
import com.chatlearn.ChatLearnApp
import com.chatlearn.EXTRA_TARGET
import com.chatlearn.MainActivity
import com.chatlearn.R
import com.chatlearn.ui.theme.DarkScheme
import com.chatlearn.ui.theme.LightScheme
import kotlinx.coroutines.flow.first
import java.time.ZoneId

private val SMALL = DpSize(110.dp, 110.dp)
private val WIDE = DpSize(220.dp, 110.dp)

/** 홈 화면 위젯: 오늘 복습할 카드 수·목표·연속 학습일, 복습과 붙여넣고 번역 버튼. */
class ReviewWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as ChatLearnApp).container
        val snapshot = runCatching {
            loadWidgetSnapshot(container.reviewSession(), container.repo, System.currentTimeMillis(), ZoneId.systemDefault())
        }.getOrNull()
        // 앱에서 배경화면 색을 켠 경우에만 위젯도 따른다. 아니면 앱과 같은 보라 테마.
        val dynamic = Build.VERSION.SDK_INT >= 31 && runCatching { container.settings.settings.first().dynamicColor }.getOrDefault(false)
        provideContent {
            if (dynamic) GlanceTheme { WidgetContent(context, snapshot) }
            else GlanceTheme(colors = ColorProviders(light = LightScheme, dark = DarkScheme)) { WidgetContent(context, snapshot) }
        }
    }
}

class ReviewWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ReviewWidget()
}

@Composable
private fun WidgetContent(context: Context, s: WidgetSnapshot?) {
    val wide = LocalSize.current.width >= WIDE.width
    val review = actionStartActivity(openIntent(context, "review"))
    // 12 이상은 런처 모서리에 맞춰 둥글게 깎고, 그 아래는 둥근 배경 그림을 쓴다.
    val bg = if (Build.VERSION.SDK_INT >= 31) GlanceModifier.background(GlanceTheme.colors.widgetBackground).cornerRadius(20.dp)
    else GlanceModifier.background(ImageProvider(R.drawable.widget_background))
    Column(GlanceModifier.fillMaxSize().appWidgetBackground().then(bg).padding(14.dp).clickable(review)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                ImageProvider(R.drawable.ic_stat_chatlearn), contentDescription = null,
                modifier = GlanceModifier.size(16.dp), colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
            )
            Spacer(GlanceModifier.width(6.dp))
            Text("ChatLearn", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium))
        }
        Spacer(GlanceModifier.height(6.dp))
        if (s != null && s.cards > 0 && s.due > 0) {
            // 할 카드가 있으면 숫자를 크게: 좁은 2×2에서도 줄이 꺾이지 않는다.
            Text("오늘 복습", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
            Row(GlanceModifier.semantics { contentDescription = widgetHeadline(s) }, verticalAlignment = Alignment.Bottom) {
                Text("${s.due}", style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 34.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Text("장", GlanceModifier.padding(start = 2.dp, bottom = 6.dp), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold))
            }
        } else {
            Text(
                s?.let(::widgetHeadline) ?: "복습할 카드를 확인해 보세요",
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold), maxLines = 2,
            )
        }
        if (s != null) Text(widgetDetail(s), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 2)
        Spacer(GlanceModifier.defaultWeight())
        Row(GlanceModifier.fillMaxWidth()) {
            FilledButton("복습", onClick = review, modifier = GlanceModifier.defaultWeight(), maxLines = 1)
            if (wide) {
                Spacer(GlanceModifier.width(8.dp))
                OutlineButton(
                    "붙여넣기", contentColor = GlanceTheme.colors.primary,
                    onClick = actionStartActivity(openIntent(context, "paste")), modifier = GlanceModifier.defaultWeight(), maxLines = 1,
                )
            }
        }
    }
}

internal fun openIntent(context: Context, target: String) = Intent(context, MainActivity::class.java)
    .setAction(ACTION_OPEN).putExtra(EXTRA_TARGET, target)
    // 엑스트라만 다른 인텐트는 같은 PendingIntent로 묶이므로 버튼마다 주소를 다르게 준다.
    .setData(Uri.parse("chatlearn://open/$target"))
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

/** 복습·저장 뒤 위젯(오늘의 복습·한 장 복습)을 다시 그린다. 위젯이 없으면 아무 일도 하지 않는다. */
suspend fun refreshReviewWidget(context: Context) {
    runCatching { ReviewWidget().updateAll(context) }
    runCatching { refreshFlashcards(context) }
}

/** 런처가 앱 안에서 위젯 추가를 지원하는가. */
fun canPinReviewWidget(context: Context): Boolean = AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

/** 홈 화면에 위젯을 추가해 달라고 런처에 요청한다(런처가 확인 창을 띄운다). */
fun requestPinReviewWidget(context: Context): Boolean =
    AppWidgetManager.getInstance(context).requestPinAppWidget(ComponentName(context, ReviewWidgetReceiver::class.java), null, null)
