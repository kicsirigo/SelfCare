package com.selfcare.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.selfcare.app.data.local.UserSettings
import com.selfcare.app.data.repository.RoutineRepository
import java.time.Clock
import java.time.ZonedDateTime

class ReminderScheduler(
    private val context: Context,
    private val clock: Clock = Clock.systemDefaultZone()
) {

    private val alarmManager: AlarmManager?
        get() = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    fun syncReminders(settings: UserSettings) {
        if (settings.morningReminderEnabled) {
            scheduleReminder(
                period = RoutineRepository.PERIOD_MORNING,
                hour = settings.morningReminderHour,
                minute = settings.morningReminderMinute
            )
        } else {
            cancelReminder(RoutineRepository.PERIOD_MORNING)
        }

        if (settings.eveningReminderEnabled) {
            scheduleReminder(
                period = RoutineRepository.PERIOD_EVENING,
                hour = settings.eveningReminderHour,
                minute = settings.eveningReminderMinute
            )
        } else {
            cancelReminder(RoutineRepository.PERIOD_EVENING)
        }
    }

    fun scheduleReminder(
        period: String,
        hour: Int,
        minute: Int,
        forceTomorrow: Boolean = false
    ) {
        val manager = alarmManager ?: return
        val normalizedPeriod = if (period.equals(RoutineRepository.PERIOD_MORNING, ignoreCase = true)) {
            RoutineRepository.PERIOD_MORNING
        } else {
            RoutineRepository.PERIOD_EVENING
        }
        val triggerAtMillis = computeNextTriggerMillis(
            hour = hour.coerceIn(0, 23),
            minute = minute.coerceIn(0, 59),
            forceTomorrow = forceTomorrow
        )
        val pendingIntent = buildPendingIntent(normalizedPeriod, hour, minute)
        manager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
    }

    fun cancelReminder(period: String) {
        val manager = alarmManager ?: return
        val normalizedPeriod = if (period.equals(RoutineRepository.PERIOD_MORNING, ignoreCase = true)) {
            RoutineRepository.PERIOD_MORNING
        } else {
            RoutineRepository.PERIOD_EVENING
        }
        val pendingIntent = buildPendingIntent(normalizedPeriod, 0, 0)
        manager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    fun computeNextTriggerMillis(
        hour: Int,
        minute: Int,
        forceTomorrow: Boolean = false
    ): Long {
        val now = ZonedDateTime.now(clock)
        var target = now.withHour(hour.coerceIn(0, 23))
            .withMinute(minute.coerceIn(0, 59))
            .withSecond(0)
            .withNano(0)
        if (forceTomorrow || !target.isAfter(now)) {
            target = target.plusDays(1)
        }
        return target.toInstant().toEpochMilli()
    }

    private fun buildPendingIntent(
        period: String,
        hour: Int,
        minute: Int
    ): PendingIntent {
        val requestCode = if (period == RoutineRepository.PERIOD_MORNING) {
            REQUEST_CODE_MORNING
        } else {
            REQUEST_CODE_EVENING
        }
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_PERIOD, period)
            putExtra(ReminderReceiver.EXTRA_HOUR, hour)
            putExtra(ReminderReceiver.EXTRA_MINUTE, minute)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val REQUEST_CODE_MORNING = 2001
        private const val REQUEST_CODE_EVENING = 2002
    }
}
