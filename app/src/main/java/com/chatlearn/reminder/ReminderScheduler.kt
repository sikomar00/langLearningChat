package com.chatlearn.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.ZoneId

const val ACTION_REMINDER = "com.chatlearn.action.REMINDER"

/** 하루 한 번 [hour]시에 울리는 알림을 예약한다. 울릴 때마다 다음 날 것을 다시 예약한다. */
class ReminderScheduler(private val context: Context, private val clock: () -> Long = System::currentTimeMillis) {
    fun apply(enabled: Boolean, hour: Int) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent()
        if (!enabled) {
            am.cancel(pi)
            return
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextReminderAt(clock(), hour, ZoneId.systemDefault()), pi)
    }

    private fun pendingIntent() = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMINDER),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
