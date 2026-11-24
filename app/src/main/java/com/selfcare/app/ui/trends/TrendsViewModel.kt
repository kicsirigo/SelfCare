package com.selfcare.app.ui.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.selfcare.app.data.local.SettingsRepository
import com.selfcare.app.data.local.UserSettings
import com.selfcare.app.data.local.entity.FaceCheckInEntity
import com.selfcare.app.data.local.entity.NutritionLogEntity
import com.selfcare.app.data.local.entity.WeightLogEntity
import com.selfcare.app.data.repository.AdherenceCorrelation
import com.selfcare.app.data.repository.FaceCheckInRepository
import com.selfcare.app.data.repository.MetricBaselineTrend
import com.selfcare.app.data.repository.NutritionRepository
import com.selfcare.app.data.repository.PersonalTrendsSummary
import com.selfcare.app.data.repository.TrendDirection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class DailyCalorieBar(
    val dateEpochDay: Long,
    val caloriesKcal: Int,
    val proteinGrams: Int
)

data class TrendsUiState(
    val isLoaded: Boolean = false,
    val summary: PersonalTrendsSummary = emptyTrendsSummary(),
    val recentCheckIns: List<FaceCheckInEntity> = emptyList(),
    val weightHistory: List<WeightLogEntity> = emptyList(),
    val targetWeightKg: Float? = null,
    val dailyCalorieTarget: Int = UserSettings.DEFAULT_CALORIE_TARGET,
    val selectedCalorieRangeDays: Int = 14,
    val calorieChartBars: List<DailyCalorieBar> = emptyList(),
    val avgDailyCalories7d: Int? = null,
    val avgDailyProtein7d: Int? = null,
    val nutritionDaysLogged7d: Int = 0
)

private fun emptyMetricTrend() = MetricBaselineTrend(
    latestValue = null,
    rolling7DayAvg = null,
    rolling30DayAvg = null,
    deltaFrom30DayAvg = null,
    direction = TrendDirection.INSUFFICIENT_DATA
)

private fun emptyTrendsSummary() = PersonalTrendsSummary(
    totalCheckIns = 0,
    checkInsLast7Days = 0,
    checkInsLast30Days = 0,
    underEyeTrend = emptyMetricTrend(),
    rednessTrend = emptyMetricTrend(),
    textureTrend = emptyMetricTrend(),
    lipDrynessTrend = emptyMetricTrend(),
    hairToFaceTrend = emptyMetricTrend(),
    adherenceCorrelation = AdherenceCorrelation(
        highAdherenceAvgRedness = null,
        lowAdherenceAvgRedness = null,
        highAdherenceAvgUnderEye = null,
        lowAdherenceAvgUnderEye = null,
        highAdherenceAvgLipDryness = null,
        lowAdherenceAvgLipDryness = null,
        routineAdherenceRate30d = 0f,
        hygieneAdherenceRate30d = 0f,
        pairedDaysCount = 0,
        insightCode = FaceCheckInRepository.INSIGHT_NO_DATA
    )
)

class TrendsViewModel(
    private val faceCheckInRepository: FaceCheckInRepository,
    private val nutritionRepository: NutritionRepository? = null,
    private val settingsRepository: SettingsRepository? = null
) : ViewModel() {

    private val selectedRangeFlow = MutableStateFlow(14)
    private val weightsFlow = nutritionRepository?.observeAllWeights() ?: flowOf(emptyList())
    private val yearStartDay: Long = (nutritionRepository?.todayEpochDay() ?: java.time.LocalDate.now().toEpochDay()) - 364L
    private val mealsYearFlow = nutritionRepository?.observeMealsSince(yearStartDay) ?: flowOf(emptyList())
    private val settingsFlow = settingsRepository?.settings ?: flowOf(UserSettings())

    private val nutritionMetaFlow = combine(
        weightsFlow,
        mealsYearFlow,
        settingsFlow,
        selectedRangeFlow
    ) { weights, mealsYear, settings, rangeDays ->
        NutritionTrendsMeta(weights, mealsYear, settings, rangeDays)
    }

    val uiState: StateFlow<TrendsUiState> = combine(
        faceCheckInRepository.observePersonalTrends(),
        faceCheckInRepository.observeRecentCheckIns(limit = 30),
        nutritionMetaFlow
    ) { summary, recent, meta ->
        val todayDay = nutritionRepository?.todayEpochDay() ?: java.time.LocalDate.now().toEpochDay()
        val cutoffDay = todayDay - (meta.rangeDays - 1L)
        val mealsInRange = meta.mealsYear.filter { it.dateEpochDay in cutoffDay..todayDay }
        val byDay: Map<Long, List<NutritionLogEntity>> = mealsInRange.groupBy { it.dateEpochDay }
        val daysLogged = byDay.size
        val avgKcal = if (daysLogged > 0) {
            (byDay.values.sumOf { dayMeals -> dayMeals.sumOf { it.caloriesKcal } }.toFloat() / daysLogged).roundToInt()
        } else null
        val avgProtein = if (daysLogged > 0) {
            (byDay.values.sumOf { dayMeals -> dayMeals.sumOf { it.proteinGrams.toDouble() } }.toFloat() / daysLogged).roundToInt()
        } else null

        val chartBars = buildCalorieChartBars(
            mealsInRange = mealsInRange,
            todayEpochDay = todayDay,
            rangeDays = meta.rangeDays
        )

        TrendsUiState(
            isLoaded = true,
            summary = summary,
            recentCheckIns = recent,
            weightHistory = meta.weights.sortedBy { it.dateEpochDay }.takeLast(30),
            targetWeightKg = meta.settings.targetWeightKg,
            dailyCalorieTarget = meta.settings.dailyCalorieTarget,
            selectedCalorieRangeDays = meta.rangeDays,
            calorieChartBars = chartBars,
            avgDailyCalories7d = avgKcal,
            avgDailyProtein7d = avgProtein,
            nutritionDaysLogged7d = daysLogged
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = TrendsUiState()
    )

    fun selectCalorieRangeDays(days: Int) {
        if (days in listOf(14, 30, 90, 365)) {
            selectedRangeFlow.value = days
        }
    }

    private data class NutritionTrendsMeta(
        val weights: List<WeightLogEntity>,
        val mealsYear: List<NutritionLogEntity>,
        val settings: UserSettings,
        val rangeDays: Int
    )

    fun deleteCheckIn(id: Long) {
        viewModelScope.launch {
            faceCheckInRepository.deleteCheckIn(id)
        }
    }

    fun deleteCheckIns(ids: Set<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { id ->
                faceCheckInRepository.deleteCheckIn(id)
            }
        }
    }

    companion object {
        fun buildCalorieChartBars(
            mealsInRange: List<NutritionLogEntity>,
            todayEpochDay: Long,
            rangeDays: Int
        ): List<DailyCalorieBar> {
            val startDay = todayEpochDay - (rangeDays - 1L)
            val byDay = mealsInRange.groupBy { it.dateEpochDay }
            val bucketSize = when (rangeDays) {
                365 -> 7
                90 -> 3
                else -> 1
            }
            val result = mutableListOf<DailyCalorieBar>()
            var bucketEnd = todayEpochDay
            while (bucketEnd >= startDay) {
                val bucketStart = (bucketEnd - (bucketSize - 1L)).coerceAtLeast(startDay)
                val loggedDaysInBucket = (bucketStart..bucketEnd).mapNotNull { d ->
                    val list = byDay[d]
                    if (list.isNullOrEmpty()) null else list
                }
                val avgKcal = if (loggedDaysInBucket.isEmpty()) 0 else {
                    (loggedDaysInBucket.sumOf { dayList -> dayList.sumOf { it.caloriesKcal } }.toFloat() /
                        loggedDaysInBucket.size).roundToInt()
                }
                val avgProtein = if (loggedDaysInBucket.isEmpty()) 0 else {
                    (loggedDaysInBucket.sumOf { dayList -> dayList.sumOf { it.proteinGrams.toDouble() } }.toFloat() /
                        loggedDaysInBucket.size).roundToInt()
                }
                result.add(
                    DailyCalorieBar(
                        dateEpochDay = bucketEnd,
                        caloriesKcal = avgKcal,
                        proteinGrams = avgProtein
                    )
                )
                bucketEnd = bucketStart - 1L
            }
            return result.asReversed()
        }

        fun factory(
            faceCheckInRepository: FaceCheckInRepository,
            nutritionRepository: NutritionRepository? = null,
            settingsRepository: SettingsRepository? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TrendsViewModel(
                    faceCheckInRepository = faceCheckInRepository,
                    nutritionRepository = nutritionRepository,
                    settingsRepository = settingsRepository
                ) as T
            }
        }
    }
}
