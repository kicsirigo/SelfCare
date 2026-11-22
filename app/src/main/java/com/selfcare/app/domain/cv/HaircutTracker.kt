package com.selfcare.app.domain.cv

import com.selfcare.app.data.local.UserSettings
import com.selfcare.app.data.local.dao.HaircutBaselineDao
import com.selfcare.app.data.local.entity.HaircutBaselineEntity
import com.selfcare.app.notifications.NotificationHelper
import kotlinx.coroutines.flow.Flow
import java.time.Clock

data class HaircutEvaluation(
    val hasBaseline: Boolean,
    val baselineRatio: Float?,
    val currentRatio: Float,
    val growthPercent: Float,
    val thresholdPercent: Float,
    val isThresholdCrossed: Boolean,
    val notificationTriggered: Boolean
)

/**
 * Tracks hair-to-face ratio growth against the active HaircutBaselineEntity,
 * evaluates threshold crossings, resets baselines after a haircut, and dispatches
 * local reminders via NotificationHelper.
 */
class HaircutTracker(
    private val haircutBaselineDao: HaircutBaselineDao,
    private val notificationHelper: NotificationHelper? = null,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val onTriggerReminder: ((Float) -> Boolean)? = null
) {

    fun observeActiveBaseline(): Flow<HaircutBaselineEntity?> =
        haircutBaselineDao.observeActiveBaseline()

    suspend fun getActiveBaseline(): HaircutBaselineEntity? =
        haircutBaselineDao.getActiveBaseline()

    fun calculateGrowthPercent(baselineRatio: Float, currentRatio: Float): Float {
        if (baselineRatio <= MIN_VALID_BASELINE_RATIO) return 0f
        return (((currentRatio - baselineRatio) / baselineRatio) * 100f).coerceAtLeast(0f)
    }

    suspend fun evaluate(
        currentHairToFaceRatio: Float,
        triggerNotification: Boolean = true,
        overrideThresholdPercent: Float? = null
    ): HaircutEvaluation {
        val active = haircutBaselineDao.getActiveBaseline()
        if (active == null) {
            return HaircutEvaluation(
                hasBaseline = false,
                baselineRatio = null,
                currentRatio = currentHairToFaceRatio,
                growthPercent = 0f,
                thresholdPercent = normalizeThreshold(
                    overrideThresholdPercent ?: UserSettings.DEFAULT_HAIRCUT_THRESHOLD_PERCENT.toFloat()
                ),
                isThresholdCrossed = false,
                notificationTriggered = false
            )
        }

        val growthPercent = calculateGrowthPercent(
            baselineRatio = active.baselineRatio,
            currentRatio = currentHairToFaceRatio
        )
        val effectiveThreshold = normalizeThreshold(overrideThresholdPercent ?: active.growthThresholdPercent)
        val crossed = growthPercent >= effectiveThreshold

        val notificationTriggered = if (crossed && triggerNotification) {
            dispatchHaircutReminder(growthPercent)
        } else {
            false
        }

        return HaircutEvaluation(
            hasBaseline = true,
            baselineRatio = active.baselineRatio,
            currentRatio = currentHairToFaceRatio,
            growthPercent = growthPercent,
            thresholdPercent = effectiveThreshold,
            isThresholdCrossed = crossed,
            notificationTriggered = notificationTriggered
        )
    }

    /**
     * Deactivates any previous baselines and stores a fresh active baseline ("Just had a haircut").
     */
    suspend fun resetBaseline(
        currentOrInitialRatio: Float,
        thresholdPercent: Float = UserSettings.DEFAULT_HAIRCUT_THRESHOLD_PERCENT.toFloat()
    ): HaircutBaselineEntity {
        haircutBaselineDao.deactivateAll()
        val entity = HaircutBaselineEntity(
            baselineRatio = currentOrInitialRatio.coerceAtLeast(0f),
            growthThresholdPercent = normalizeThreshold(thresholdPercent),
            recordedAtEpochMillis = clock.millis(),
            isActive = true
        )
        val insertedId = haircutBaselineDao.insert(entity)
        return haircutBaselineDao.getActiveBaseline() ?: entity.copy(id = insertedId)
    }

    private fun dispatchHaircutReminder(growthPercent: Float): Boolean {
        return try {
            onTriggerReminder?.invoke(growthPercent)
                ?: notificationHelper?.showHaircutReminder(growthPercent)
                ?: false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun normalizeThreshold(value: Float): Float = value.coerceIn(
        UserSettings.MIN_HAIRCUT_THRESHOLD_PERCENT.toFloat(),
        UserSettings.MAX_HAIRCUT_THRESHOLD_PERCENT.toFloat()
    )

    companion object {
        private const val MIN_VALID_BASELINE_RATIO = 0.0001f
    }
}
