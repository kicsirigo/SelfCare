package com.selfcare.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.selfcare.app.data.repository.RoutineRepository

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val period = intent?.getStringExtra(EXTRA_PERIOD)
            ?.takeIf { it.isNotBlank() }
            ?: RoutineRepository.PERIOD_MORNING

        NotificationHelper(context).showRoutineReminder(period)

        val hour = intent?.getIntExtra(EXTRA_HOUR, -1) ?: -1
        val minute = intent?.getIntExtra(EXTRA_MINUTE, -1) ?: -1
        if (hour in 0..23 && minute in 0..59) {
            ReminderScheduler(context).scheduleReminder(
                period = period,
                hour = hour,
                minute = minute,
                forceTomorrow = true
            )
        }
    }

    companion object {
        const val EXTRA_PERIOD = "extra_period"
        const val EXTRA_HOUR = "extra_hour"
        const val EXTRA_MINUTE = "extra_minute"
    }
}
