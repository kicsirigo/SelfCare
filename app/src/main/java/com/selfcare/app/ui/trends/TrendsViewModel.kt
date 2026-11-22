package com.selfcare.app.ui.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.selfcare.app.data.local.entity.FaceCheckInEntity
import com.selfcare.app.data.repository.AdherenceCorrelation
import com.selfcare.app.data.repository.FaceCheckInRepository
import com.selfcare.app.data.repository.MetricBaselineTrend
import com.selfcare.app.data.repository.PersonalTrendsSummary
import com.selfcare.app.data.repository.TrendDirection
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TrendsUiState(
    val isLoaded: Boolean = false,
    val summary: PersonalTrendsSummary = emptyTrendsSummary(),
    val recentCheckIns: List<FaceCheckInEntity> = emptyList()
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
    private val faceCheckInRepository: FaceCheckInRepository
) : ViewModel() {

    val uiState: StateFlow<TrendsUiState> = combine(
        faceCheckInRepository.observePersonalTrends(),
        faceCheckInRepository.observeRecentCheckIns(limit = 30)
    ) { summary, recent ->
        TrendsUiState(
            isLoaded = true,
            summary = summary,
            recentCheckIns = recent
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

    companion object {
        fun factory(
            faceCheckInRepository: FaceCheckInRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TrendsViewModel(faceCheckInRepository) as T
            }
        }
    }
}
