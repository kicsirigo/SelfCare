package com.selfcare.app.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.selfcare.app.data.local.SettingsRepository
import com.selfcare.app.data.remote.AiCoachChatMessage
import com.selfcare.app.data.remote.AiCoachChatResult
import com.selfcare.app.data.remote.ProductVisionClient
import com.selfcare.app.data.repository.FaceCheckInRepository
import com.selfcare.app.data.repository.NutritionRepository
import com.selfcare.app.data.repository.ProductRepository
import com.selfcare.app.domain.ai.AiChatContextScope
import com.selfcare.app.domain.ai.AppDataContextBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiCoachUiState(
    val messages: List<AiCoachChatMessage> = emptyList(),
    val scope: AiChatContextScope = AiChatContextScope(),
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
    val contextPreview: String? = null,
    val lastModelUsed: String = ""
)

class AiCoachViewModel(
    private val nutritionRepository: NutritionRepository,
    private val productRepository: ProductRepository,
    private val faceCheckInRepository: FaceCheckInRepository,
    private val settingsRepository: SettingsRepository,
    private val visionClient: ProductVisionClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiCoachUiState())
    val uiState: StateFlow<AiCoachUiState> = _uiState.asStateFlow()

    fun toggleNutritionScope() {
        _uiState.update { it.copy(scope = it.scope.copy(includeNutrition = !it.scope.includeNutrition)) }
    }

    fun toggleFitnessScope() {
        _uiState.update { it.copy(scope = it.scope.copy(includeFitnessWeight = !it.scope.includeFitnessWeight)) }
    }

    fun toggleShelfScope() {
        _uiState.update { it.copy(scope = it.scope.copy(includeRoutineShelf = !it.scope.includeRoutineShelf)) }
    }

    fun toggleFaceScope() {
        _uiState.update { it.copy(scope = it.scope.copy(includeFaceTrends = !it.scope.includeFaceTrends)) }
    }

    fun clearConversation() {
        _uiState.update { it.copy(messages = emptyList(), errorMessage = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun openContextPreview() {
        viewModelScope.launch {
            val ctx = buildCurrentContext(_uiState.value.scope)
            _uiState.update { it.copy(contextPreview = ctx) }
        }
    }

    fun dismissContextPreview() {
        _uiState.update { it.copy(contextPreview = null) }
    }

    fun sendMessage(rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty() || _uiState.value.isBusy) return

        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val apiKey = settings.geminiApiKey.trim()
            if (apiKey.isEmpty()) {
                _uiState.update { it.copy(errorMessage = "NO_GROQ_KEY") }
                return@launch
            }

            val historyBefore = _uiState.value.messages
            val scope = _uiState.value.scope
            _uiState.update {
                it.copy(
                    messages = it.messages + AiCoachChatMessage("user", text),
                    isBusy = true,
                    errorMessage = null
                )
            }

            val contextText = buildCurrentContext(scope)
            when (val res = visionClient.chatWithCoach(contextText, historyBefore, text, apiKey)) {
                is AiCoachChatResult.Success -> {
                    _uiState.update {
                        it.copy(
                            messages = it.messages + AiCoachChatMessage("assistant", res.reply),
                            isBusy = false,
                            lastModelUsed = res.modelUsed
                        )
                    }
                }
                is AiCoachChatResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            errorMessage = res.reason
                        )
                    }
                }
            }
        }
    }

    private suspend fun buildCurrentContext(scope: AiChatContextScope): String {
        val settings = settingsRepository.settings.first()
        val today = nutritionRepository.todayEpochDay()
        val todayMeals = if (scope.includeNutrition) {
            nutritionRepository.observeMealsForDay(today).first()
        } else emptyList()
        val recentMeals = if (scope.includeNutrition) {
            nutritionRepository.observeRecentMeals(120).first()
        } else emptyList()
        val todayWater = if (scope.includeNutrition) {
            nutritionRepository.observeWaterForDay(today).first()
        } else emptyList()
        val todayActivities = if (scope.includeFitnessWeight) {
            nutritionRepository.observeActivitiesForDay(today).first()
        } else emptyList()
        val weights = if (scope.includeFitnessWeight) {
            nutritionRepository.observeAllWeights().first()
        } else emptyList()
        val products = if (scope.includeRoutineShelf) {
            productRepository.observeAllProducts().first()
        } else emptyList()
        val checkIns = if (scope.includeFaceTrends) {
            faceCheckInRepository.observeRecentCheckIns(limit = 5).first()
        } else emptyList()

        return AppDataContextBuilder.buildContext(
            scope = scope,
            settings = settings,
            todayEpochDay = today,
            todayMeals = todayMeals,
            recentMeals7d = recentMeals,
            todayWater = todayWater,
            todayActivities = todayActivities,
            weights = weights,
            shelfProducts = products,
            recentCheckIns = checkIns
        )
    }

    companion object {
        fun factory(
            nutritionRepository: NutritionRepository,
            productRepository: ProductRepository,
            faceCheckInRepository: FaceCheckInRepository,
            settingsRepository: SettingsRepository,
            visionClient: ProductVisionClient
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AiCoachViewModel(
                    nutritionRepository = nutritionRepository,
                    productRepository = productRepository,
                    faceCheckInRepository = faceCheckInRepository,
                    settingsRepository = settingsRepository,
                    visionClient = visionClient
                ) as T
            }
        }
    }
}
