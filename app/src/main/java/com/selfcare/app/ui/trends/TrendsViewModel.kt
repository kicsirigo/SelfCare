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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class TrendsUiState(
    val isLoaded: Boolean = false,
    val summary: PersonalTrendsSummary = emptyTrendsSummary(),
    val recentCheckIns: List<FaceCheckInEntity> = emptyList(),
    val weightHistory: List<WeightLogEntity> = emptyList(),
    val targetWeightKg: Float? = null,
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

    private val weightsFlow = nutritionRepository?.observeAllWeights() ?: flowOf(emptyList())
    private val mealsFlow = nutritionRepository?.observeRecentMeals(200) ?: flowOf(emptyList())
    private val settingsFlow = settingsRepository?.settings ?: flowOf(UserSettings())

    val uiState: StateFlow<TrendsUiState> = combine(
        faceCheckInRepository.observePersonalTrends(),
        faceCheckInRepository.observeRecentCheckIns(limit = 30),
        weightsFlow,
        mealsFlow,
        settingsFlow
    ) { summary, recent, weights, recentMeals, settings ->
        val todayDay = nutritionRepository?.todayEpochDay() ?: java.time.LocalDate.now().toEpochDay()
        val cutoffDay = todayDay - 6L
        val mealsLast7d = recentMeals.filter { it.dateEpochDay in cutoffDay..todayDay }
        val byDay: Map<Long, List<NutritionLogEntity>> = mealsLast7d.groupBy { it.dateEpochDay }
        val daysLogged = byDay.size
        val avgKcal = if (daysLogged > 0) {
            (byDay.values.sumOf { dayMeals -> dayMeals.sumOf { it.caloriesKcal } }.toFloat() / daysLogged).roundToInt()
        } else null
        val avgProtein = if (daysLogged > 0) {
            (byDay.values.sumOf { dayMeals -> dayMeals.sumOf { it.proteinGrams.toDouble() } }.toFloat() / daysLogged).roundToInt()
        } else null

        TrendsUiState(
            isLoaded = true,
            summary = summary,
            recentCheckIns = recent,
            weightHistory = weights.sortedBy { it.dateEpochDay }.takeLast(30),
            targetWeightKg = settings.targetWeightKg,
            avgDailyCalories7d = avgKcal,
            avgDailyProtein7d = avgProtein,
            nutritionDaysLogged7d = daysLogged
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = TrendsUiState()
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
