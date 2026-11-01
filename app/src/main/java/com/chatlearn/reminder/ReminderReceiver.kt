package com.chatlearn.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.chatlearn.ChatLearnApp
import com.chatlearn.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val CHANNEL_ID = "review_reminder"

/** 알림 시각에 울려서 복습할 카드 수를 알리고, 부팅·앱 업데이트 뒤에는 예약만 다시 한다. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val container = (context.applicationContext as ChatLearnApp).container
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val s = container.settings.settings.first()
                if (!s.reminderEnabled) return@launch
                if (intent.action == ACTION_REMINDER) {
                    val dayStart = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    // 오늘 이미 복습했다면 알리지 않는다.
                    // 쉬는 요일에도 울리지 않는다(다음 날 예약은 아래에서 그대로 이어진다).
                    if (isReminderDay(s.reminderDays, java.time.LocalDate.now().dayOfWeek) && !container.repo.reviewedSince(dayStart)) {
                        val queue = container.reviewSession().loadQueue()
                        reminderText(queue.size, queue.count { it.card.lastReviewedAt == null }, ReminderTone.fromName(s.reminderTone))?.let { show(context, it) }
                    }
                }
                ReminderScheduler(context).apply(true, s.reminderHour)
            } finally {
                pending.finish()
            }
        }
    }

    private fun show(context: Context, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "복습 알림", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        nm.notify(
            1,
            Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("ChatLearn")
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }
}
