package com.selfcare.app.ui.hygiene

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.selfcare.app.data.repository.HygieneRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HygieneCustomItem(
    val habitKey: String,
    val label: String,
    val completed: Boolean
)

data class HygieneUiState(
    val todayEpochDay: Long = 0L,
    val completedByKey: Map<String, Boolean> = emptyMap(),
    val customHabits: List<HygieneCustomItem> = emptyList(),
    val customInputText: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class)
class HygieneViewModel(
    private val hygieneRepository: HygieneRepository
) : ViewModel() {

    private val currentEpochDay = MutableStateFlow(hygieneRepository.currentEpochDay())
    private val customInputText = MutableStateFlow("")

    private val todayLogs = currentEpochDay.flatMapLatest { epochDay ->
        hygieneRepository.observeLogsForDate(epochDay)
    }

    val uiState: StateFlow<HygieneUiState> = combine(
        currentEpochDay,
        todayLogs,
        hygieneRepository.observeAllLogs(),
        customInputText
    ) { epochDay, logsToday, allLogs, input ->
        val completedByKey = logsToday.associate { it.habitKey to it.completed }
        val customDefinitions = allLogs
            .filter { it.isCustom }
            .distinctBy { it.habitKey }
            .map { def ->
                HygieneCustomItem(
                    habitKey = def.habitKey,
                    label = def.label,
                    completed = completedByKey[def.habitKey] == true
                )
            }
        HygieneUiState(
            todayEpochDay = epochDay,
            completedByKey = completedByKey,
            customHabits = customDefinitions,
            customInputText = input
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = HygieneUiState(todayEpochDay = hygieneRepository.currentEpochDay())
    )

    fun refreshDate() {
        val latest = hygieneRepository.currentEpochDay()
        if (currentEpochDay.value != latest) {
            currentEpochDay.value = latest
        }
    }

    fun toggleHabit(habitKey: String, label: String, isCustom: Boolean = false) {
        viewModelScope.launch {
            refreshDate()
            hygieneRepository.toggleHabit(habitKey = habitKey, label = label, isCustom = isCustom)
        }
    }

    fun updateCustomInput(text: String) {
        customInputText.value = text
    }

    fun addCustomHabit() {
        val label = customInputText.value.trim()
        if (label.isEmpty()) return
        viewModelScope.launch {
            refreshDate()
            val added = hygieneRepository.addCustomHabit(label = label, initialCompleted = false)
            if (added != null) {
                customInputText.value = ""
            }
        }
    }

    fun deleteCustomHabit(habitKey: String) {
        viewModelScope.launch {
            hygieneRepository.deleteCustomHabit(habitKey)
        }
    }

    companion object {
        fun factory(hygieneRepository: HygieneRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HygieneViewModel(hygieneRepository) as T
                }
            }
    }
}
