package com.selfcare.app.ui.checkin

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.selfcare.app.data.local.SettingsRepository
import com.selfcare.app.data.local.UserSettings
import com.selfcare.app.data.local.entity.FaceCheckInEntity
import com.selfcare.app.data.local.entity.HaircutBaselineEntity
import com.selfcare.app.data.repository.CheckInSubmissionResult
import com.selfcare.app.data.repository.FaceCheckInRepository
import com.selfcare.app.data.repository.HygieneRepository
import com.selfcare.app.data.repository.ProductRepository
import com.selfcare.app.data.repository.RoutineRepository
import com.selfcare.app.domain.cv.CheckInDailyContext
import com.selfcare.app.domain.cv.HaircutTracker
import com.selfcare.app.domain.cv.LightingAssessment
import com.selfcare.app.domain.cv.LightingQualityChecker
import com.selfcare.app.domain.cv.ShelfProductSummary
import com.selfcare.app.domain.ml.FaceAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

data class CheckInUiState(
    val hasCameraPermission: Boolean = false,
    val cameraUnavailable: Boolean = false,
    val lightingAssessment: LightingAssessment? = null,
    val isFaceDetected: Boolean? = null,
    val liveBrightnessPercent: Int? = null,
    val screenFlashEnabled: Boolean = false,
    val isFlashFiring: Boolean = false,
    val cooldownSecondsRemaining: Int = 0,
    val noFaceError: Boolean = false,
    val isCapturing: Boolean = false,
    val autoDeletePhotos: Boolean = false,
    val haircutThresholdPercent: Int = UserSettings.DEFAULT_HAIRCUT_THRESHOLD_PERCENT,
    val activeBaseline: HaircutBaselineEntity? = null,
    val latestCheckIn: FaceCheckInEntity? = null,
    val currentHairGrowthPercent: Float = 0f,
    val isHaircutReminderTriggered: Boolean = false,
    val lastResult: CheckInSubmissionResult? = null,
    val baselineResetAtMillis: Long? = null,
    val errorMessage: String? = null,
    val shaveIntervalDays: Int = UserSettings.DEFAULT_SHAVE_INTERVAL_DAYS,
    val daysSinceLastShave: Int? = null,
    val isShaveReminderDue: Boolean = false,
    val shelfProducts: List<ShelfProductSummary> = emptyList(),
    val dailyContext: CheckInDailyContext = CheckInDailyContext()
)

class CheckInViewModel(
    private val faceCheckInRepository: FaceCheckInRepository,
    private val haircutTracker: HaircutTracker,
    private val settingsRepository: SettingsRepository,
    private val faceAnalyzer: FaceAnalyzer? = null,
    private val productRepository: ProductRepository? = null,
    private val routineRepository: RoutineRepository? = null,
    private val hygieneRepository: HygieneRepository? = null
) : ViewModel() {

    private data class TransientState(
        val hasCameraPermission: Boolean = false,
        val cameraUnavailable: Boolean = false,
        val lightingAssessment: LightingAssessment? = null,
        val isFaceDetected: Boolean? = null,
        val liveBrightnessPercent: Int? = null,
        val screenFlashEnabled: Boolean = false,
        val isFlashFiring: Boolean = false,
        val cooldownSecondsRemaining: Int = 0,
        val noFaceError: Boolean = false,
        val isCapturing: Boolean = false,
        val lastResult: CheckInSubmissionResult? = null,
        val baselineResetAtMillis: Long? = null,
        val errorMessage: String? = null
    )

    private val transientState = MutableStateFlow(TransientState())
    private var liveFaceCheckJob: Job? = null
    private var cooldownJob: Job? = null

    private val dailyContextFlow = combine(
        routineRepository?.observeAllLogs() ?: flowOf(emptyList()),
        hygieneRepository?.observeAllLogs() ?: flowOf(emptyList())
    ) { routineLogs, hygieneLogs ->
        val today = faceCheckInRepository.currentEpochDay()
        val todayRoutines = routineLogs.filter { it.dateEpochDay == today }
        val morningDone = todayRoutines.any { it.period.equals("MORNING", ignoreCase = true) }
        val eveningDone = todayRoutines.any { it.period.equals("EVENING", ignoreCase = true) }
        val lipBalmDone = hygieneLogs.any {
            it.dateEpochDay == today &&
                it.completed &&
                (it.habitKey.contains("lip", ignoreCase = true) ||
                    it.label.contains("lip", ignoreCase = true) ||
                    it.label.contains("ajak", ignoreCase = true))
        }
        CheckInDailyContext(
            morningRoutineDone = morningDone,
            eveningRoutineDone = eveningDone,
            lipBalmLoggedToday = lipBalmDone
        )
    }

    val uiState: StateFlow<CheckInUiState> = combine(
        transientState,
        settingsRepository.settings,
        haircutTracker.observeActiveBaseline(),
        faceCheckInRepository.observeRecentCheckIns(1),
        productRepository?.observeAllProducts() ?: flowOf(emptyList<com.selfcare.app.data.local.entity.ProductEntity>()),
        dailyContextFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val transient = values[0] as TransientState
        val settings = values[1] as UserSettings
        val baseline = values[2] as HaircutBaselineEntity?
        @Suppress("UNCHECKED_CAST")
        val recentCheckIns = values[3] as List<FaceCheckInEntity>
        @Suppress("UNCHECKED_CAST")
        val products = values[4] as List<com.selfcare.app.data.local.entity.ProductEntity>
        val dailyCtx = values[5] as CheckInDailyContext

        val latest = recentCheckIns.firstOrNull()
        val latestRatio = transient.lastResult?.checkIn?.hairToFaceRatio ?: latest?.hairToFaceRatio
        val growthPercent = if (baseline != null && latestRatio != null) {
            haircutTracker.calculateGrowthPercent(
                baselineRatio = baseline.baselineRatio,
                currentRatio = latestRatio
            )
        } else {
            0f
        }
        val threshold = settings.haircutThresholdPercent
        val reminderTriggered = baseline != null &&
            (growthPercent >= threshold ||
                transient.lastResult?.haircutEvaluation?.isThresholdCrossed == true)

        val todayEpochDay = faceCheckInRepository.currentEpochDay()
        val daysSinceShave = settings.lastShaveEpochDay?.let { (todayEpochDay - it).toInt().coerceAtLeast(0) }
        val latestStubble = transient.lastResult?.checkIn?.stubbleScore ?: latest?.stubbleScore ?: 0f
        val shaveDue = (daysSinceShave != null && daysSinceShave >= settings.shaveIntervalDays) ||
            latestStubble >= 0.52f

        val shelfSummaries = products.map { p ->
            ShelfProductSummary(
                name = p.name,
                category = p.category,
                ingredientsText = p.rawIngredientsText
            )
        }

        CheckInUiState(
            hasCameraPermission = transient.hasCameraPermission,
            cameraUnavailable = transient.cameraUnavailable,
            lightingAssessment = transient.lightingAssessment,
            isFaceDetected = transient.isFaceDetected,
            liveBrightnessPercent = transient.liveBrightnessPercent,
            screenFlashEnabled = transient.screenFlashEnabled,
            isFlashFiring = transient.isFlashFiring,
            cooldownSecondsRemaining = transient.cooldownSecondsRemaining,
            noFaceError = transient.noFaceError,
            isCapturing = transient.isCapturing,
            autoDeletePhotos = settings.autoDeleteCheckInPhotos,
            haircutThresholdPercent = threshold,
            activeBaseline = baseline,
            latestCheckIn = latest,
            currentHairGrowthPercent = growthPercent,
            isHaircutReminderTriggered = reminderTriggered,
            lastResult = transient.lastResult,
            baselineResetAtMillis = transient.baselineResetAtMillis,
            errorMessage = transient.errorMessage,
            shaveIntervalDays = settings.shaveIntervalDays,
            daysSinceLastShave = daysSinceShave,
            isShaveReminderDue = shaveDue,
            shelfProducts = shelfSummaries,
            dailyContext = dailyCtx
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = CheckInUiState()
    )

    fun updateCameraPermission(granted: Boolean) {
        transientState.value = transientState.value.copy(hasCameraPermission = granted)
    }

    fun setCameraUnavailable(unavailable: Boolean) {
        transientState.value = transientState.value.copy(cameraUnavailable = unavailable)
    }

    fun onLightingAssessed(assessment: LightingAssessment) {
        val brightnessPct = (assessment.meanLuminance * 100f).roundToInt().coerceIn(0, 100)
        transientState.value = transientState.value.copy(
            lightingAssessment = assessment,
            liveBrightnessPercent = brightnessPct
        )
    }

    fun onLiveFrameSampled(frameBitmap: Bitmap, assessment: LightingAssessment) {
        val brightnessPct = (assessment.meanLuminance * 100f).roundToInt().coerceIn(0, 100)
        transientState.value = transientState.value.copy(
            lightingAssessment = assessment,
            liveBrightnessPercent = brightnessPct
        )

        val analyzer = faceAnalyzer ?: run {
            frameBitmap.recycle()
            return
        }
        if (liveFaceCheckJob?.isActive == true) {
            frameBitmap.recycle()
            return
        }
        liveFaceCheckJob = viewModelScope.launch {
            val present = try {
                withContext(Dispatchers.Default) {
                    analyzer.isFacePresent(frameBitmap)
                }
            } catch (_: Throwable) {
                false
            } finally {
                frameBitmap.recycle()
            }
            transientState.value = transientState.value.copy(
                isFaceDetected = present,
                noFaceError = false
            )
        }
    }

    fun toggleScreenFlash(enabled: Boolean) {
        transientState.value = transientState.value.copy(screenFlashEnabled = enabled)
    }

    fun setFlashFiring(firing: Boolean) {
        transientState.value = transientState.value.copy(isFlashFiring = firing)
    }

    fun assessLightingFromBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            val assessment = withContext(Dispatchers.Default) {
                LightingQualityChecker.assess(bitmap)
            }
            val brightnessPct = (assessment.meanLuminance * 100f).roundToInt().coerceIn(0, 100)
            transientState.value = transientState.value.copy(
                lightingAssessment = assessment,
                liveBrightnessPercent = brightnessPct
            )
        }
    }

    fun submitBitmapCheckIn(bitmap: Bitmap, notes: String = "") {
        if (transientState.value.isCapturing) return
        viewModelScope.launch {
            transientState.value = transientState.value.copy(
                isCapturing = true,
                errorMessage = null,
                noFaceError = false
            )
            try {
                val result = faceCheckInRepository.performCheckIn(bitmap = bitmap, notes = notes)
                transientState.value = transientState.value.copy(
                    isCapturing = false,
                    lastResult = result,
                    errorMessage = null,
                    noFaceError = false
                )
            } catch (t: Throwable) {
                transientState.value = transientState.value.copy(
                    isCapturing = false,
                    errorMessage = t.message ?: "Check-in failed"
                )
            }
        }
    }

    private fun startCooldown(seconds: Int = CAPTURE_COOLDOWN_SECONDS) {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            for (remaining in seconds downTo 1) {
                transientState.value = transientState.value.copy(cooldownSecondsRemaining = remaining)
                delay(1_000L)
            }
            transientState.value = transientState.value.copy(cooldownSecondsRemaining = 0)
        }
    }

    fun setAutoDeleteCheckInPhotos(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoDeleteCheckInPhotos(enabled)
        }
    }

    fun markShavedToday() {
        viewModelScope.launch {
            settingsRepository.setLastShaveEpochDay(faceCheckInRepository.currentEpochDay())
        }
    }

    fun resetHaircutBaseline() {
        viewModelScope.launch {
            val current = uiState.value
            val referenceRatio = current.lastResult?.checkIn?.hairToFaceRatio
                ?: current.latestCheckIn?.hairToFaceRatio
                ?: DEFAULT_INITIAL_HAIR_RATIO
            val saved = haircutTracker.resetBaseline(
                currentOrInitialRatio = referenceRatio,
                thresholdPercent = current.haircutThresholdPercent.toFloat()
            )
            val currentTransient = transientState.value
            transientState.value = currentTransient.copy(
                baselineResetAtMillis = saved.recordedAtEpochMillis,
                lastResult = currentTransient.lastResult?.let { prev ->
                    prev.copy(
                        haircutEvaluation = prev.haircutEvaluation.copy(
                            baselineRatio = saved.baselineRatio,
                            growthPercent = 0f,
                            isThresholdCrossed = false
                        )
                    )
                },
                errorMessage = null
            )
        }
    }

    fun clearError() {
        transientState.value = transientState.value.copy(errorMessage = null)
    }

    companion object {
        private const val DEFAULT_INITIAL_HAIR_RATIO = 0.25f
        const val CAPTURE_COOLDOWN_SECONDS = 5

        fun factory(
            faceCheckInRepository: FaceCheckInRepository,
            haircutTracker: HaircutTracker,
            settingsRepository: SettingsRepository,
            faceAnalyzer: FaceAnalyzer? = null,
            productRepository: ProductRepository? = null,
            routineRepository: RoutineRepository? = null,
            hygieneRepository: HygieneRepository? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return CheckInViewModel(
                    faceCheckInRepository = faceCheckInRepository,
                    haircutTracker = haircutTracker,
                    settingsRepository = settingsRepository,
                    faceAnalyzer = faceAnalyzer,
                    productRepository = productRepository,
                    routineRepository = routineRepository,
                    hygieneRepository = hygieneRepository
                ) as T
            }
        }
    }
}
