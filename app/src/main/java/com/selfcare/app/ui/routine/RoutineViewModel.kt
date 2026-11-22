package com.selfcare.app.ui.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.selfcare.app.data.local.entity.ProductEntity
import com.selfcare.app.data.local.entity.RoutineLogEntity
import com.selfcare.app.data.repository.RoutineRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RoutineUiState(
    val selectedPeriod: String = RoutineRepository.PERIOD_MORNING,
    val todayEpochDay: Long = 0L,
    val defaultProducts: List<ProductEntity> = emptyList(),
    val allProducts: List<ProductEntity> = emptyList(),
    val currentLog: RoutineLogEntity? = null,
    val isCompletedToday: Boolean = false,
    val includedProductIds: Set<Long> = emptySet(),
    val skippedProductIds: Set<Long> = emptySet()
)

@OptIn(ExperimentalCoroutinesApi::class)
class RoutineViewModel(
    private val routineRepository: RoutineRepository
) : ViewModel() {

    private val selectedPeriod = MutableStateFlow(RoutineRepository.PERIOD_MORNING)
    private val currentEpochDay = MutableStateFlow(routineRepository.currentEpochDay())

    private val logsForToday = currentEpochDay.flatMapLatest { epochDay ->
        routineRepository.observeLogsForDate(epochDay)
    }

    val uiState: StateFlow<RoutineUiState> = combine(
        selectedPeriod,
        currentEpochDay,
        logsForToday,
        routineRepository.observeAllProducts()
    ) { period, epochDay, logs, allProducts ->
        val defaultProducts = allProducts.filter { product ->
            product.isDefaultInRoutine && when (product.routineSlot.uppercase()) {
                RoutineRepository.PERIOD_MORNING -> period == RoutineRepository.PERIOD_MORNING
                RoutineRepository.PERIOD_EVENING -> period == RoutineRepository.PERIOD_EVENING
                RoutineRepository.SLOT_BOTH ->
                    period == RoutineRepository.PERIOD_MORNING || period == RoutineRepository.PERIOD_EVENING
                RoutineRepository.PERIOD_HAIR -> period == RoutineRepository.PERIOD_HAIR
                else -> period == RoutineRepository.PERIOD_HAIR && RoutineRepository.isHairCategory(product.category)
            }
        }
        val currentLog = logs.firstOrNull { it.period == period }
        val skippedIds = if (currentLog != null) {
            routineRepository.decodeProductIds(currentLog.skippedProductIdsJson).toSet()
        } else {
            emptySet()
        }
        val includedIds = if (currentLog != null) {
            routineRepository.decodeProductIds(currentLog.productIdsJson).toSet()
        } else {
            defaultProducts.map { it.id }.toSet()
        }

        RoutineUiState(
            selectedPeriod = period,
            todayEpochDay = epochDay,
            defaultProducts = defaultProducts,
            allProducts = allProducts,
            currentLog = currentLog,
            isCompletedToday = currentLog != null && currentLog.completedAtEpochMillis > 0L,
            includedProductIds = includedIds,
            skippedProductIds = skippedIds
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = RoutineUiState(todayEpochDay = routineRepository.currentEpochDay())
    )

    fun selectPeriod(period: String) {
        selectedPeriod.value = period
    }

    fun refreshDate() {
        val latest = routineRepository.currentEpochDay()
        if (currentEpochDay.value != latest) {
            currentEpochDay.value = latest
        }
    }

    fun completeCurrentRoutine() {
        val period = selectedPeriod.value
        viewModelScope.launch {
            refreshDate()
            routineRepository.completeRoutine(period)
        }
    }

    fun toggleProductOverride(productId: Long) {
        val period = selectedPeriod.value
        viewModelScope.launch {
            refreshDate()
            routineRepository.toggleProductOverride(period, productId)
        }
    }

    fun setProductRoutineDefault(productId: Long, isMorning: Boolean, isEvening: Boolean) {
        viewModelScope.launch {
            routineRepository.setProductRoutineDefault(productId, isMorning, isEvening)
        }
    }

    companion object {
        fun factory(routineRepository: RoutineRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return RoutineViewModel(routineRepository) as T
                }
            }
    }
}
