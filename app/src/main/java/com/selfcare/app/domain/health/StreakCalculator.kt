package com.selfcare.app.domain.health

/**
 * 100% offline consecutive-day streak calculator.
 * Counts consecutive active days ending on [todayEpochDay], or on [todayEpochDay] - 1
 * so an active streak is preserved during the current day until midnight.
 */
object StreakCalculator {
    fun computeCurrentStreak(
        activeEpochDays: Set<Long>,
        todayEpochDay: Long
    ): Int {
        if (activeEpochDays.isEmpty()) return 0
        var cursor = when {
            todayEpochDay in activeEpochDays -> todayEpochDay
            (todayEpochDay - 1L) in activeEpochDays -> todayEpochDay - 1L
            else -> return 0
        }
        var streak = 0
        while (cursor in activeEpochDays) {
            streak++
            cursor--
        }
        return streak
    }
}
