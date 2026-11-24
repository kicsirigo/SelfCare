package com.selfcare.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.selfcare.app.MainActivity
import com.selfcare.app.R
import com.selfcare.app.data.repository.RoutineRepository
import kotlin.math.roundToInt

class NotificationHelper(
    private val context: Context
) {

    fun createNotificationChannels() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val routineChannel = NotificationChannel(
            CHANNEL_ROUTINE_REMINDERS,
            context.getString(R.string.notification_channel_routine_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_routine_desc)
        }

        val haircutChannel = NotificationChannel(
            CHANNEL_HAIRCUT_REMINDERS,
            context.getString(R.string.notification_channel_haircut_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_haircut_desc)
        }

        manager.createNotificationChannel(routineChannel)
        manager.createNotificationChannel(haircutChannel)
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showRoutineReminder(period: String): Boolean {
        if (!hasNotificationPermission()) return false
        createNotificationChannels()

        val isMorning = period.equals(RoutineRepository.PERIOD_MORNING, ignoreCase = true)
        val isWater = period.equals(ReminderScheduler.PERIOD_WATER, ignoreCase = true)
        val title = when {
            isMorning -> context.getString(R.string.notification_morning_title)
            isWater -> context.getString(R.string.notification_water_title)
            else -> context.getString(R.string.notification_evening_title)
        }
        val body = when {
            isMorning -> context.getString(R.string.notification_morning_body)
            isWater -> context.getString(R.string.notification_water_body)
            else -> context.getString(R.string.notification_evening_body)
        }
        val notificationId = when {
            isMorning -> NOTIFICATION_ID_MORNING
            isWater -> NOTIFICATION_ID_WATER
            else -> NOTIFICATION_ID_EVENING
        }

        return postNotification(
            channelId = CHANNEL_ROUTINE_REMINDERS,
            notificationId = notificationId,
            title = title,
            body = body
        )
    }

    fun showHaircutReminder(growthPercent: Float): Boolean {
        if (!hasNotificationPermission()) return false
        createNotificationChannels()

        val roundedPercent = growthPercent.roundToInt().coerceAtLeast(1)
        val title = context.getString(R.string.notification_haircut_title)
        val body = context.getString(R.string.notification_haircut_body, roundedPercent)

        return postNotification(
            channelId = CHANNEL_HAIRCUT_REMINDERS,
            notificationId = NOTIFICATION_ID_HAIRCUT,
            title = title,
            body = body
        )
    }

    private fun postNotification(
        channelId: String,
        notificationId: Int,
        title: String,
        body: String
    ): Boolean {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    companion object {
        const val CHANNEL_ROUTINE_REMINDERS = "routine_reminders"
        const val CHANNEL_HAIRCUT_REMINDERS = "haircut_reminders"
        const val NOTIFICATION_ID_MORNING = 1001
        const val NOTIFICATION_ID_EVENING = 1002
        const val NOTIFICATION_ID_HAIRCUT = 1003
        const val NOTIFICATION_ID_WATER = 1004
    }
}
