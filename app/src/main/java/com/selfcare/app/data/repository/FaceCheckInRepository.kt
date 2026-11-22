package com.selfcare.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.selfcare.app.data.local.SettingsRepository
import com.selfcare.app.data.local.dao.FaceCheckInDao
import com.selfcare.app.data.local.dao.HygieneDao
import com.selfcare.app.data.local.dao.RoutineDao
import com.selfcare.app.data.local.entity.FaceCheckInEntity
import com.selfcare.app.data.local.entity.HygieneLogEntity
import com.selfcare.app.data.local.entity.RoutineLogEntity
import com.selfcare.app.domain.cv.HaircutEvaluation
import com.selfcare.app.domain.cv.HaircutTracker
import com.selfcare.app.domain.ml.FaceAnalysisResult
import com.selfcare.app.domain.ml.FaceAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs

enum class TrendDirection {
    BETTER,
    STEADY,
    WORSE,
    INSUFFICIENT_DATA
}

data class MetricBaselineTrend(
    val latestValue: Float?,
    val rolling7DayAvg: Float?,
    val rolling30DayAvg: Float?,
    val deltaFrom30DayAvg: Float?,
    val direction: TrendDirection
)

data class AdherenceCorrelation(
    val highAdherenceAvgRedness: Float?,
    val lowAdherenceAvgRedness: Float?,
    val highAdherenceAvgUnderEye: Float?,
    val lowAdherenceAvgUnderEye: Float?,
    val highAdherenceAvgLipDryness: Float?,
    val lowAdherenceAvgLipDryness: Float?,
    val routineAdherenceRate30d: Float,
    val hygieneAdherenceRate30d: Float,
    val pairedDaysCount: Int,
    val insightCode: String
)

data class PersonalTrendsSummary(
    val totalCheckIns: Int,
    val checkInsLast7Days: Int,
    val checkInsLast30Days: Int,
    val underEyeTrend: MetricBaselineTrend,
    val rednessTrend: MetricBaselineTrend,
    val textureTrend: MetricBaselineTrend,
    val lipDrynessTrend: MetricBaselineTrend,
    val hairToFaceTrend: MetricBaselineTrend,
    val adherenceCorrelation: AdherenceCorrelation
)

data class CheckInSubmissionResult(
    val checkIn: FaceCheckInEntity,
    val analysis: FaceAnalysisResult,
    val haircutEvaluation: HaircutEvaluation
)

/**
 * Coordinates private on-device check-in photo storage, face metric extraction,
 * Room persistence, haircut baseline evaluation, and personal rolling trend summaries.
 */
class FaceCheckInRepository(
    private val context: Context,
    private val faceCheckInDao: FaceCheckInDao,
    private val routineDao: RoutineDao,
    private val hygieneDao: HygieneDao,
    private val settingsRepository: SettingsRepository,
    private val faceAnalyzer: FaceAnalyzer,
    private val haircutTracker: HaircutTracker,
    private val clock: Clock = Clock.systemDefaultZone()
) {

    fun currentEpochDay(): Long = LocalDate.now(clock).toEpochDay()

    fun getCheckInsDirectory(): File = ensureCheckInsDir()

    fun observeAllCheckIns(): Flow<List<FaceCheckInEntity>> = faceCheckInDao.observeAll()

    fun observeRecentCheckIns(limit: Int = 30): Flow<List<FaceCheckInEntity>> =
        faceCheckInDao.observeRecent(limit)

    suspend fun getAllCheckInsOnce(): List<FaceCheckInEntity> = faceCheckInDao.getAllOnce()

    suspend fun getCheckInById(id: Long): FaceCheckInEntity? = faceCheckInDao.getById(id)

    fun observePersonalTrends(): Flow<PersonalTrendsSummary> = combine(
        faceCheckInDao.observeAll(),
        routineDao.observeAll(),
        hygieneDao.observeAll()
    ) { checkIns, routineLogs, hygieneLogs ->
        computePersonalTrendsSummary(
            checkIns = checkIns,
            routineLogs = routineLogs,
            hygieneLogs = hygieneLogs,
            todayEpochDay = currentEpochDay()
        )
    }

    suspend fun computePersonalTrends(): PersonalTrendsSummary {
        return computePersonalTrendsSummary(
            checkIns = faceCheckInDao.getAllOnce(),
            routineLogs = routineDao.getAllOnce(),
            hygieneLogs = hygieneDao.getAllOnce(),
            todayEpochDay = currentEpochDay()
        )
    }

    /**
     * Saves the raw bitmap inside private filesDir/checkins (.nomedia protected),
     * extracts metrics offline via FaceAnalyzer, immediately deletes the raw photo when
     * autoDeleteCheckInPhotos is enabled, persists FaceCheckInEntity, and evaluates HaircutTracker.
     */
    suspend fun performCheckIn(
        bitmap: Bitmap,
        notes: String = ""
    ): CheckInSubmissionResult {
        val analysis = faceAnalyzer.analyze(bitmap)
        return saveAnalyzedCheckIn(
            bitmap = bitmap,
            analysis = analysis,
            notes = notes
        )
    }

    /**
     * Persists a pre-analyzed check-in while enforcing private photo storage and
     * auto-delete privacy settings.
     */
    suspend fun saveAnalyzedCheckIn(
        bitmap: Bitmap,
        analysis: FaceAnalysisResult,
        notes: String = ""
    ): CheckInSubmissionResult {
        val settings = settingsRepository.getSettingsOnce()
        val nowMillis = clock.millis()
        val epochDay = currentEpochDay()
        val photoFile = saveCheckInPhoto(bitmap, nowMillis)

        val savedPhotoPath = if (settings.autoDeleteCheckInPhotos) {
            deleteFileQuietly(photoFile)
            null
        } else {
            photoFile.absolutePath
        }

        val entity = FaceCheckInEntity(
            dateEpochDay = epochDay,
            timestampEpochMillis = nowMillis,
            underEyeDeltaLab = analysis.underEyeDeltaLab,
            skinRednessIndex = analysis.skinRednessIndex,
            skinTextureVariance = analysis.skinTextureVariance,
            lipDrynessScore = analysis.lipDrynessScore,
            hairToFaceRatio = analysis.hairToFaceRatio,
            lightingQualityScore = analysis.lightingQualityScore,
            photoPath = savedPhotoPath,
            notes = notes.trim()
        )
        val insertedId = faceCheckInDao.insert(entity)
        val persisted = faceCheckInDao.getById(insertedId) ?: entity.copy(id = insertedId)

        val haircutEval = haircutTracker.evaluate(
            currentHairToFaceRatio = analysis.hairToFaceRatio,
            triggerNotification = true,
            overrideThresholdPercent = settings.haircutThresholdPercent.toFloat()
        )

        return CheckInSubmissionResult(
            checkIn = persisted,
            analysis = analysis,
            haircutEvaluation = haircutEval
        )
    }

    suspend fun deleteCheckIn(id: Long) {
        val existing = faceCheckInDao.getById(id)
        if (existing?.photoPath != null) {
            deletePhotoIfOwned(existing.photoPath)
        }
        faceCheckInDao.deleteById(id)
    }

    suspend fun saveCheckInPhoto(
        bitmap: Bitmap,
        timestampMillis: Long = clock.millis()
    ): File = withContext(Dispatchers.IO) {
        val dir = ensureCheckInsDir()
        val uniqueSuffix = UUID.randomUUID().toString().take(8)
        val target = File(dir, "checkin_${timestampMillis}_${uniqueSuffix}.jpg")
        FileOutputStream(target).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.flush()
        }
        target
    }

    internal fun computePersonalTrendsSummary(
        checkIns: List<FaceCheckInEntity>,
        routineLogs: List<RoutineLogEntity>,
        hygieneLogs: List<HygieneLogEntity>,
        todayEpochDay: Long
    ): PersonalTrendsSummary {
        val sortedDesc = checkIns.sortedByDescending { it.timestampEpochMillis }
        val start7d = todayEpochDay - 6L
        val start30d = todayEpochDay - 29L

        val last7d = sortedDesc.filter { it.dateEpochDay in start7d..todayEpochDay }
        val last30d = sortedDesc.filter { it.dateEpochDay in start30d..todayEpochDay }

        val underEyeTrend = buildMetricTrend(
            sortedDesc = sortedDesc,
            last7d = last7d,
            last30d = last30d,
            selector = { it.underEyeDeltaLab },
            deadband = 0.5f,
            lowerIsBetter = true
        )
        val rednessTrend = buildMetricTrend(
            sortedDesc = sortedDesc,
            last7d = last7d,
            last30d = last30d,
            selector = { it.skinRednessIndex },
            deadband = 0.02f,
            lowerIsBetter = true
        )
        val textureTrend = buildMetricTrend(
            sortedDesc = sortedDesc,
            last7d = last7d,
            last30d = last30d,
            selector = { it.skinTextureVariance },
            deadband = 0.5f,
            lowerIsBetter = true
        )
        val lipDrynessTrend = buildMetricTrend(
            sortedDesc = sortedDesc,
            last7d = last7d,
            last30d = last30d,
            selector = { it.lipDrynessScore },
            deadband = 0.02f,
            lowerIsBetter = true
        )
        val hairToFaceTrend = buildMetricTrend(
            sortedDesc = sortedDesc,
            last7d = last7d,
            last30d = last30d,
            selector = { it.hairToFaceRatio },
            deadband = 0.02f,
            lowerIsBetter = false
        )

        val adherenceCorrelation = computeAdherenceCorrelation(
            last30dCheckIns = last30d,
            routineLogs = routineLogs,
            hygieneLogs = hygieneLogs,
            start30d = start30d,
            todayEpochDay = todayEpochDay
        )

        return PersonalTrendsSummary(
            totalCheckIns = sortedDesc.size,
            checkInsLast7Days = last7d.size,
            checkInsLast30Days = last30d.size,
            underEyeTrend = underEyeTrend,
            rednessTrend = rednessTrend,
            textureTrend = textureTrend,
            lipDrynessTrend = lipDrynessTrend,
            hairToFaceTrend = hairToFaceTrend,
            adherenceCorrelation = adherenceCorrelation
        )
    }

    private fun buildMetricTrend(
        sortedDesc: List<FaceCheckInEntity>,
        last7d: List<FaceCheckInEntity>,
        last30d: List<FaceCheckInEntity>,
        selector: (FaceCheckInEntity) -> Float,
        deadband: Float,
        lowerIsBetter: Boolean
    ): MetricBaselineTrend {
        val latest = sortedDesc.firstOrNull()?.let(selector)
        val avg7d = last7d.takeIf { it.isNotEmpty() }?.map(selector)?.average()?.toFloat()
        val avg30d = last30d.takeIf { it.isNotEmpty() }?.map(selector)?.average()?.toFloat()
        val comparisonValue = avg7d ?: latest
        val delta = if (comparisonValue != null && avg30d != null) {
            comparisonValue - avg30d
        } else {
            null
        }

        val direction = when {
            last30d.size < 2 || delta == null || avg30d == null -> TrendDirection.INSUFFICIENT_DATA
            !lowerIsBetter -> TrendDirection.STEADY
            else -> {
                val threshold = maxOf(deadband, abs(avg30d) * 0.05f)
                when {
                    delta < -threshold -> TrendDirection.BETTER
                    delta > threshold -> TrendDirection.WORSE
                    else -> TrendDirection.STEADY
                }
            }
        }

        return MetricBaselineTrend(
            latestValue = latest,
            rolling7DayAvg = avg7d,
            rolling30DayAvg = avg30d,
            deltaFrom30DayAvg = delta,
            direction = direction
        )
    }

    private fun computeAdherenceCorrelation(
        last30dCheckIns: List<FaceCheckInEntity>,
        routineLogs: List<RoutineLogEntity>,
        hygieneLogs: List<HygieneLogEntity>,
        start30d: Long,
        todayEpochDay: Long
    ): AdherenceCorrelation {
        val routines30d = routineLogs.filter {
            it.dateEpochDay in start30d..todayEpochDay && it.completedAtEpochMillis > 0L
        }
        val hygiene30d = hygieneLogs.filter {
            it.dateEpochDay in start30d..todayEpochDay && it.completed
        }

        val routineRate30d = (routines30d.size.toFloat() / (30f * 2f)).coerceIn(0f, 1f)
        val builtInHabitCount = HygieneRepository.BUILT_IN_HABIT_KEYS.size.toFloat()
        val builtInHygieneCompleted = hygiene30d.count {
            it.habitKey in HygieneRepository.BUILT_IN_HABIT_KEYS
        }
        val hygieneRate30d = (builtInHygieneCompleted.toFloat() / (30f * builtInHabitCount))
            .coerceIn(0f, 1f)

        if (last30dCheckIns.isEmpty()) {
            return AdherenceCorrelation(
                highAdherenceAvgRedness = null,
                lowAdherenceAvgRedness = null,
                highAdherenceAvgUnderEye = null,
                lowAdherenceAvgUnderEye = null,
                highAdherenceAvgLipDryness = null,
                lowAdherenceAvgLipDryness = null,
                routineAdherenceRate30d = routineRate30d,
                hygieneAdherenceRate30d = hygieneRate30d,
                pairedDaysCount = 0,
                insightCode = INSIGHT_NO_DATA
            )
        }

        val routineByDay = routines30d.groupBy { it.dateEpochDay }
        val hygieneByDay = hygiene30d.groupBy { it.dateEpochDay }

        val highBucket = mutableListOf<FaceCheckInEntity>()
        val lowBucket = mutableListOf<FaceCheckInEntity>()
        val distinctDays = HashSet<Long>()

        for (checkIn in last30dCheckIns) {
            val day = checkIn.dateEpochDay
            distinctDays.add(day)
            val routinePeriods = routineByDay[day]
                ?.map { it.period }
                ?.distinct()
                ?.size ?: 0
            val routineScore = (routinePeriods / 2f).coerceIn(0f, 1f)

            val hygieneCompleted = hygieneByDay[day]?.size ?: 0
            val hygieneScore = (hygieneCompleted / builtInHabitCount).coerceIn(0f, 1f)

            val combinedScore = 0.5f * routineScore + 0.5f * hygieneScore
            if (combinedScore >= HIGH_ADHERENCE_THRESHOLD) {
                highBucket.add(checkIn)
            } else {
                lowBucket.add(checkIn)
            }
        }

        val highRedness = highBucket.takeIf { it.isNotEmpty() }?.map { it.skinRednessIndex }?.average()?.toFloat()
        val lowRedness = lowBucket.takeIf { it.isNotEmpty() }?.map { it.skinRednessIndex }?.average()?.toFloat()
        val highUnderEye = highBucket.takeIf { it.isNotEmpty() }?.map { it.underEyeDeltaLab }?.average()?.toFloat()
        val lowUnderEye = lowBucket.takeIf { it.isNotEmpty() }?.map { it.underEyeDeltaLab }?.average()?.toFloat()
        val highLip = highBucket.takeIf { it.isNotEmpty() }?.map { it.lipDrynessScore }?.average()?.toFloat()
        val lowLip = lowBucket.takeIf { it.isNotEmpty() }?.map { it.lipDrynessScore }?.average()?.toFloat()

        val insightCode = when {
            highBucket.isEmpty() || lowBucket.isEmpty() -> INSIGHT_NEED_MORE_VARIED_DAYS
            isBetterOnHighAdherence(highRedness, lowRedness, highUnderEye, lowUnderEye, highLip, lowLip) ->
                INSIGHT_BETTER_ON_ADHERENT_DAYS
            else -> INSIGHT_STEADY_ACROSS_ADHERENCE
        }

        return AdherenceCorrelation(
            highAdherenceAvgRedness = highRedness,
            lowAdherenceAvgRedness = lowRedness,
            highAdherenceAvgUnderEye = highUnderEye,
            lowAdherenceAvgUnderEye = lowUnderEye,
            highAdherenceAvgLipDryness = highLip,
            lowAdherenceAvgLipDryness = lowLip,
            routineAdherenceRate30d = routineRate30d,
            hygieneAdherenceRate30d = hygieneRate30d,
            pairedDaysCount = distinctDays.size,
            insightCode = insightCode
        )
    }

    private fun isBetterOnHighAdherence(
        highRedness: Float?,
        lowRedness: Float?,
        highUnderEye: Float?,
        lowUnderEye: Float?,
        highLip: Float?,
        lowLip: Float?
    ): Boolean {
        var improvements = 0
        if (highRedness != null && lowRedness != null && highRedness < lowRedness - 0.01f) improvements++
        if (highUnderEye != null && lowUnderEye != null && highUnderEye < lowUnderEye - 0.3f) improvements++
        if (highLip != null && lowLip != null && highLip < lowLip - 0.01f) improvements++
        return improvements >= 2 || (highRedness != null && lowRedness != null && highRedness < lowRedness - 0.03f)
    }

    private fun ensureCheckInsDir(): File {
        val dir = File(context.filesDir, CHECKINS_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val nomedia = File(dir, NOMEDIA_FILE_NAME)
        if (!nomedia.exists()) {
            runCatching { nomedia.createNewFile() }
        }
        return dir
    }

    private fun deletePhotoIfOwned(photoPath: String) {
        val dirCanonical = runCatching { ensureCheckInsDir().canonicalPath }.getOrNull() ?: return
        val file = File(photoPath)
        val fileCanonical = runCatching { file.canonicalPath }.getOrNull() ?: return
        if (fileCanonical.startsWith(dirCanonical + File.separator)) {
            deleteFileQuietly(file)
        }
    }

    private fun deleteFileQuietly(file: File) {
        runCatching {
            if (file.exists()) {
                file.delete()
            }
        }
    }

    companion object {
        const val CHECKINS_DIR_NAME = "checkins"
        const val NOMEDIA_FILE_NAME = ".nomedia"
        private const val JPEG_QUALITY = 92
        private const val HIGH_ADHERENCE_THRESHOLD = 0.5f

        const val INSIGHT_NO_DATA = "NO_DATA"
        const val INSIGHT_NEED_MORE_VARIED_DAYS = "NEED_MORE_VARIED_DAYS"
        const val INSIGHT_BETTER_ON_ADHERENT_DAYS = "BETTER_ON_ADHERENT_DAYS"
        const val INSIGHT_STEADY_ACROSS_ADHERENCE = "STEADY_ACROSS_ADHERENCE"
    }
}
