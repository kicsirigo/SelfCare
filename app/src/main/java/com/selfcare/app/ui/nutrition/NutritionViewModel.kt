package com.selfcare.app.ui.nutrition

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.selfcare.app.data.local.SettingsRepository
import com.selfcare.app.data.local.UserSettings
import com.selfcare.app.data.local.entity.NutritionLogEntity
import com.selfcare.app.data.local.entity.WaterLogEntity
import com.selfcare.app.data.remote.EstimatedMealInfo
import com.selfcare.app.data.remote.FoodLookupResult
import com.selfcare.app.data.remote.FoodNutritionItem
import com.selfcare.app.data.remote.FoodSearchResult
import com.selfcare.app.data.remote.MealVisionResult
import com.selfcare.app.data.remote.ProductVisionClient
import com.selfcare.app.data.repository.NutritionRepository
import com.selfcare.app.domain.health.ActivityLevel
import com.selfcare.app.domain.health.FoodPreset
import com.selfcare.app.domain.health.FoodPresetCatalog
import com.selfcare.app.domain.health.MacroCalculator
import com.selfcare.app.domain.health.MacroGoalType
import com.selfcare.app.domain.health.VitaminCalculator
import com.selfcare.app.domain.ml.BarcodeScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class PendingMealConfirmation(
    val name: String,
    val brand: String = "",
    val barcode: String? = null,
    val grams: Float,
    val caloriesKcal: Int,
    val proteinGrams: Float,
    val carbsGrams: Float,
    val fatGrams: Float,
    val fiberGrams: Float = 0f,
    val sugarGrams: Float = 0f,
    val saltGrams: Float = 0f,
    val source: String,
    val modelUsed: String = "",
    val isPer100gScalable: Boolean = false,
    val per100gKcal: Float = 0f,
    val per100gProtein: Float = 0f,
    val per100gCarbs: Float = 0f,
    val per100gFat: Float = 0f,
    val per100gFiber: Float = 0f,
    val per100gSugar: Float = 0f,
    val per100gSalt: Float = 0f
)

data class NutritionUiState(
    val todayMeals: List<NutritionLogEntity> = emptyList(),
    val recentUniqueMeals: List<NutritionLogEntity> = emptyList(),
    val todayWaterLogs: List<WaterLogEntity> = emptyList(),
    val totalCalories: Int = 0,
    val totalProtein: Float = 0f,
    val totalCarbs: Float = 0f,
    val totalFat: Float = 0f,
    val totalFiber: Float = 0f,
    val totalSugar: Float = 0f,
    val totalSaltGrams: Float = 0f,
    val totalWaterMl: Int = 0,
    val calorieTarget: Int = UserSettings.DEFAULT_CALORIE_TARGET,
    val proteinTarget: Int = UserSettings.DEFAULT_PROTEIN_TARGET,
    val carbsTarget: Int = UserSettings.DEFAULT_CARBS_TARGET,
    val fatTarget: Int = UserSettings.DEFAULT_FAT_TARGET,
    val baseWaterTargetMl: Int = UserSettings.DEFAULT_WATER_TARGET_ML,
    val saltWaterBonusMl: Int = 0,
    val waterTargetMl: Int = UserSettings.DEFAULT_WATER_TARGET_ML,
    val autoAgeYears: Int? = null,
    val bodyWeightKg: Float? = null,
    val targetWeightKg: Float? = null,
    val bodyHeightCm: Int? = null,
    val bodyIsMale: Boolean = true,
    val bodyActivityLevel: String = "MODERATE",
    val bodyMacroGoal: String = "MAINTAIN",
    val selectedMealType: String = NutritionLogEntity.MEAL_LUNCH,
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val searchQuery: String = "",
    val presetMatches: List<FoodPreset> = FoodPresetCatalog.items,
    val remoteMatches: List<FoodNutritionItem> = emptyList(),
    val pendingConfirmation: PendingMealConfirmation? = null,
    val isGroqConfigured: Boolean = false,
    val celebrationTrigger: Int = 0
)

class NutritionViewModel(
    private val nutritionRepository: NutritionRepository,
    private val settingsRepository: SettingsRepository,
    private val barcodeScanner: BarcodeScanner? = null,
    private val visionClient: ProductVisionClient? = null
) : ViewModel() {

    private data class TransientState(
        val selectedMealType: String = NutritionLogEntity.MEAL_LUNCH,
        val isBusy: Boolean = false,
        val statusMessage: String? = null,
        val searchQuery: String = "",
        val remoteMatches: List<FoodNutritionItem> = emptyList(),
        val pendingConfirmation: PendingMealConfirmation? = null,
        val celebrationTrigger: Int = 0
    )

    private val transientFlow = MutableStateFlow(TransientState())

    val uiState: StateFlow<NutritionUiState> = combine(
        nutritionRepository.observeMealsForToday(),
        nutritionRepository.observeRecentMeals(40),
        nutritionRepository.observeWaterForToday(),
        settingsRepository.settings,
        transientFlow
    ) { meals, recentMeals, waterLogs, settings, tr ->
        val totalKcal = meals.sumOf { it.caloriesKcal }
        val totalProtein = meals.sumOf { it.proteinGrams.toDouble() }.toFloat()
        val totalCarbs = meals.sumOf { it.carbsGrams.toDouble() }.toFloat()
        val totalFat = meals.sumOf { it.fatGrams.toDouble() }.toFloat()
        val totalFiber = meals.sumOf { it.fiberGrams.toDouble() }.toFloat()
        val totalSugar = meals.sumOf { it.sugarGrams.toDouble() }.toFloat()
        val totalSalt = meals.sumOf { it.saltGrams.toDouble() }.toFloat()
        val totalWater = waterLogs.sumOf { it.amountMl }
        val saltBonusMl = MacroCalculator.computeSaltHydrationBonusMl(totalSalt)
        val effectiveWaterTarget = settings.dailyWaterTargetMl + saltBonusMl
        val uniqueRecent = recentMeals
            .distinctBy { it.name.trim().lowercase() }
            .take(8)

        NutritionUiState(
            todayMeals = meals,
            recentUniqueMeals = uniqueRecent,
            todayWaterLogs = waterLogs,
            totalCalories = totalKcal,
            totalProtein = totalProtein,
            totalCarbs = totalCarbs,
            totalFat = totalFat,
            totalFiber = totalFiber,
            totalSugar = totalSugar,
            totalSaltGrams = totalSalt,
            totalWaterMl = totalWater,
            calorieTarget = settings.dailyCalorieTarget,
            proteinTarget = settings.dailyProteinTargetGrams,
            carbsTarget = settings.dailyCarbsTargetGrams,
            fatTarget = settings.dailyFatTargetGrams,
            baseWaterTargetMl = settings.dailyWaterTargetMl,
            saltWaterBonusMl = saltBonusMl,
            waterTargetMl = effectiveWaterTarget,
            autoAgeYears = VitaminCalculator.computeAgeYears(settings.birthDateIso),
            bodyWeightKg = settings.bodyWeightKg,
            targetWeightKg = settings.targetWeightKg,
            bodyHeightCm = settings.bodyHeightCm,
            bodyIsMale = settings.bodyIsMale,
            bodyActivityLevel = settings.bodyActivityLevel,
            bodyMacroGoal = settings.bodyMacroGoal,
            selectedMealType = tr.selectedMealType,
            isBusy = tr.isBusy,
            statusMessage = tr.statusMessage,
            searchQuery = tr.searchQuery,
            presetMatches = FoodPresetCatalog.search(tr.searchQuery),
            remoteMatches = tr.remoteMatches,
            pendingConfirmation = tr.pendingConfirmation,
            isGroqConfigured = settings.geminiApiKey.isNotBlank(),
            celebrationTrigger = tr.celebrationTrigger
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NutritionUiState()
    )

    fun selectMealType(mealType: String) {
        transientFlow.update { it.copy(selectedMealType = mealType) }
    }

    fun onSearchQueryChange(query: String) {
        transientFlow.update { it.copy(searchQuery = query, statusMessage = null) }
    }

    fun clearStatusMessage() {
        transientFlow.update { it.copy(statusMessage = null) }
    }

    fun addWater(amountMl: Int) {
        if (amountMl <= 0) return
        val currentState = uiState.value
        val wasBelowTarget = currentState.totalWaterMl < currentState.waterTargetMl
        val willReachTarget = (currentState.totalWaterMl + amountMl) >= currentState.waterTargetMl
        viewModelScope.launch {
            nutritionRepository.logWater(amountMl)
            if (wasBelowTarget && willReachTarget) {
                triggerCelebration()
            }
        }
    }

    fun undoLastWater() {
        val last = uiState.value.todayWaterLogs.firstOrNull() ?: return
        viewModelScope.launch {
            nutritionRepository.deleteWater(last.id)
        }
    }

    fun openPresetConfirmation(preset: FoodPreset, isHungarian: Boolean) {
        val scaled = MacroCalculator.scalePer100g(
            grams = preset.defaultGrams,
            per100gKcal = preset.per100gKcal,
            per100gProtein = preset.per100gProtein,
            per100gCarbs = preset.per100gCarbs,
            per100gFat = preset.per100gFat,
            per100gFiber = preset.per100gFiber,
            per100gSugar = preset.per100gSugar,
            per100gSalt = preset.per100gSalt
        )
        transientFlow.update {
            it.copy(
                pendingConfirmation = PendingMealConfirmation(
                    name = if (isHungarian) preset.nameHu else preset.nameEn,
                    grams = preset.defaultGrams,
                    caloriesKcal = scaled.caloriesKcal,
                    proteinGrams = scaled.proteinGrams,
                    carbsGrams = scaled.carbsGrams,
                    fatGrams = scaled.fatGrams,
                    fiberGrams = scaled.fiberGrams,
                    sugarGrams = scaled.sugarGrams,
                    saltGrams = scaled.saltGrams,
                    source = "PRESET",
                    isPer100gScalable = true,
                    per100gKcal = preset.per100gKcal,
                    per100gProtein = preset.per100gProtein,
                    per100gCarbs = preset.per100gCarbs,
                    per100gFat = preset.per100gFat,
                    per100gFiber = preset.per100gFiber,
                    per100gSugar = preset.per100gSugar,
                    per100gSalt = preset.per100gSalt
                )
            )
        }
    }

    fun openRecentMealConfirmation(meal: NutritionLogEntity) {
        transientFlow.update {
            it.copy(
                pendingConfirmation = PendingMealConfirmation(
                    name = meal.name,
                    brand = meal.brand,
                    barcode = meal.barcode,
                    grams = meal.grams,
                    caloriesKcal = meal.caloriesKcal,
                    proteinGrams = meal.proteinGrams,
                    carbsGrams = meal.carbsGrams,
                    fatGrams = meal.fatGrams,
                    fiberGrams = meal.fiberGrams,
                    sugarGrams = meal.sugarGrams,
                    saltGrams = meal.saltGrams,
                    source = meal.source,
                    isPer100gScalable = false
                )
            )
        }
    }

    fun openRemoteFoodConfirmation(item: FoodNutritionItem, source: String = "BARCODE") {
        val scaled = MacroCalculator.scalePer100g(
            grams = item.defaultGrams,
            per100gKcal = item.per100gKcal,
            per100gProtein = item.per100gProtein,
            per100gCarbs = item.per100gCarbs,
            per100gFat = item.per100gFat,
            per100gFiber = item.per100gFiber,
            per100gSugar = item.per100gSugar,
            per100gSalt = item.per100gSalt
        )
        transientFlow.update {
            it.copy(
                isBusy = false,
                pendingConfirmation = PendingMealConfirmation(
                    name = item.name,
                    brand = item.brand,
                    barcode = item.barcode,
                    grams = item.defaultGrams,
                    caloriesKcal = scaled.caloriesKcal,
                    proteinGrams = scaled.proteinGrams,
                    carbsGrams = scaled.carbsGrams,
                    fatGrams = scaled.fatGrams,
                    fiberGrams = scaled.fiberGrams,
                    sugarGrams = scaled.sugarGrams,
                    saltGrams = scaled.saltGrams,
                    source = source,
                    isPer100gScalable = true,
                    per100gKcal = item.per100gKcal,
                    per100gProtein = item.per100gProtein,
                    per100gCarbs = item.per100gCarbs,
                    per100gFat = item.per100gFat,
                    per100gFiber = item.per100gFiber,
                    per100gSugar = item.per100gSugar,
                    per100gSalt = item.per100gSalt
                )
            )
        }
    }

    fun estimateMealByText() {
        val q = transientFlow.value.searchQuery.trim()
        if (q.isBlank() || visionClient == null) return
        viewModelScope.launch {
            val apiKey = settingsRepository.settings.first().geminiApiKey.trim()
            if (apiKey.isEmpty()) {
                transientFlow.update { it.copy(statusMessage = "NO_GROQ_KEY") }
                return@launch
            }
            transientFlow.update { it.copy(isBusy = true, statusMessage = null) }
            when (val res = visionClient.estimateMealFromText(q, apiKey)) {
                is MealVisionResult.Success -> {
                    val m: EstimatedMealInfo = res.meal
                    transientFlow.update {
                        it.copy(
                            isBusy = false,
                            pendingConfirmation = PendingMealConfirmation(
                                name = m.name,
                                grams = m.estimatedGrams,
                                caloriesKcal = m.caloriesKcal,
                                proteinGrams = m.proteinGrams,
                                carbsGrams = m.carbsGrams,
                                fatGrams = m.fatGrams,
                                fiberGrams = m.fiberGrams,
                                sugarGrams = m.sugarGrams,
                                saltGrams = m.saltGrams,
                                source = "GROQ_TEXT",
                                modelUsed = res.modelUsed,
                                isPer100gScalable = false
                            )
                        )
                    }
                }
                is MealVisionResult.Error -> {
                    transientFlow.update { it.copy(isBusy = false, statusMessage = res.reason) }
                }
            }
        }
    }

    fun searchOpenFoodFacts() {
        val q = transientFlow.value.searchQuery.trim()
        if (q.isBlank()) return
        viewModelScope.launch {
            transientFlow.update { it.copy(isBusy = true, statusMessage = null) }
            when (val res = nutritionRepository.searchFoodRemote(q)) {
                is FoodSearchResult.Success -> {
                    transientFlow.update {
                        it.copy(
                            isBusy = false,
                            remoteMatches = res.items,
                            statusMessage = if (res.items.isEmpty()) "NOT_FOUND" else null
                        )
                    }
                }
                is FoodSearchResult.Error -> {
                    transientFlow.update { it.copy(isBusy = false, statusMessage = res.reason) }
                }
            }
        }
    }

    fun onBarcodePhotoCaptured(bitmap: Bitmap?) {
        if (bitmap == null || barcodeScanner == null) return
        viewModelScope.launch {
            transientFlow.update { it.copy(isBusy = true, statusMessage = null) }
            val code = barcodeScanner.scanBarcode(bitmap)
            if (code.isNullOrBlank()) {
                transientFlow.update { it.copy(isBusy = false, statusMessage = "BARCODE_NOT_FOUND") }
                return@launch
            }
            when (val res = nutritionRepository.lookupBarcode(code)) {
                is FoodLookupResult.Found -> openRemoteFoodConfirmation(res.item, source = "BARCODE")
                is FoodLookupResult.NotFound -> transientFlow.update {
                    it.copy(isBusy = false, statusMessage = "FOOD_NOT_FOUND:$code")
                }
                is FoodLookupResult.Error -> transientFlow.update {
                    it.copy(isBusy = false, statusMessage = res.reason)
                }
            }
        }
    }

    fun onMealPhotoCaptured(bitmap: Bitmap?) {
        if (bitmap == null || visionClient == null) return
        viewModelScope.launch {
            val apiKey = settingsRepository.settings.first().geminiApiKey.trim()
            if (apiKey.isEmpty()) {
                transientFlow.update { it.copy(statusMessage = "NO_GROQ_KEY") }
                return@launch
            }
            transientFlow.update { it.copy(isBusy = true, statusMessage = null) }
            when (val res = visionClient.estimateMealFromPhoto(bitmap, apiKey)) {
                is MealVisionResult.Success -> {
                    val m: EstimatedMealInfo = res.meal
                    transientFlow.update {
                        it.copy(
                            isBusy = false,
                            pendingConfirmation = PendingMealConfirmation(
                                name = m.name,
                                grams = m.estimatedGrams,
                                caloriesKcal = m.caloriesKcal,
                                proteinGrams = m.proteinGrams,
                                carbsGrams = m.carbsGrams,
                                fatGrams = m.fatGrams,
                                fiberGrams = m.fiberGrams,
                                sugarGrams = m.sugarGrams,
                                saltGrams = m.saltGrams,
                                source = "GROQ_VISION",
                                modelUsed = res.modelUsed,
                                isPer100gScalable = false
                            )
                        )
                    }
                }
                is MealVisionResult.Error -> {
                    transientFlow.update { it.copy(isBusy = false, statusMessage = res.reason) }
                }
            }
        }
    }

    fun dismissPendingConfirmation() {
        transientFlow.update { it.copy(pendingConfirmation = null) }
    }

    fun saveConfirmedMeal(
        name: String,
        brand: String,
        barcode: String?,
        grams: Float,
        caloriesKcal: Int,
        proteinGrams: Float,
        carbsGrams: Float,
        fatGrams: Float,
        fiberGrams: Float,
        sugarGrams: Float,
        saltGrams: Float = 0f,
        mealType: String,
        source: String
    ) {
        val currentState = uiState.value
        val wasProteinBelow = currentState.totalProtein < currentState.proteinTarget
        val willHitProtein = (currentState.totalProtein + proteinGrams) >= currentState.proteinTarget
        val wasKcalBelow = currentState.totalCalories < (currentState.calorieTarget * 0.90f)
        val willHitKcalSweetSpot = (currentState.totalCalories + caloriesKcal) in
            (currentState.calorieTarget * 0.90f).roundToInt()..(currentState.calorieTarget * 1.08f).roundToInt()

        viewModelScope.launch {
            nutritionRepository.logMeal(
                mealType = mealType,
                name = name,
                brand = brand,
                barcode = barcode,
                grams = grams,
                caloriesKcal = caloriesKcal,
                proteinGrams = proteinGrams,
                carbsGrams = carbsGrams,
                fatGrams = fatGrams,
                fiberGrams = fiberGrams,
                sugarGrams = sugarGrams,
                saltGrams = saltGrams,
                source = source
            )
            transientFlow.update { it.copy(pendingConfirmation = null) }
            if ((wasProteinBelow && willHitProtein) || (wasKcalBelow && willHitKcalSweetSpot)) {
                triggerCelebration()
            }
        }
    }

    fun deleteMeal(id: Long) {
        viewModelScope.launch {
            nutritionRepository.deleteMeal(id)
        }
    }

    fun updateManualTargets(
        caloriesKcal: Int,
        proteinGrams: Int,
        carbsGrams: Int,
        fatGrams: Int,
        waterMl: Int
    ) {
        viewModelScope.launch {
            settingsRepository.updateNutritionTargets(
                caloriesKcal = caloriesKcal,
                proteinGrams = proteinGrams,
                carbsGrams = carbsGrams,
                fatGrams = fatGrams,
                waterMl = waterMl
            )
        }
    }

    fun calculateAndSaveTargets(
        weightKg: Float,
        targetWeightKg: Float?,
        heightCm: Float,
        ageYears: Int,
        isMale: Boolean,
        activityLevel: ActivityLevel,
        goal: MacroGoalType
    ) {
        val targets = MacroCalculator.calculateTargets(
            weightKg = weightKg,
            heightCm = heightCm,
            ageYears = ageYears,
            isMale = isMale,
            activityLevel = activityLevel,
            goal = goal
        )
        viewModelScope.launch {
            settingsRepository.saveBodyProfile(
                weightKg = weightKg,
                targetWeightKg = targetWeightKg,
                heightCm = heightCm.roundToInt(),
                isMale = isMale,
                activityLevel = activityLevel.name,
                macroGoal = goal.name
            )
            settingsRepository.updateNutritionTargets(
                caloriesKcal = targets.caloriesKcal,
                proteinGrams = targets.proteinGrams,
                carbsGrams = targets.carbsGrams,
                fatGrams = targets.fatGrams,
                waterMl = targets.waterMl
            )
        }
    }

    private fun triggerCelebration() {
        transientFlow.update { it.copy(celebrationTrigger = it.celebrationTrigger + 1) }
    }

    companion object {
        fun factory(
            nutritionRepository: NutritionRepository,
            settingsRepository: SettingsRepository,
            barcodeScanner: BarcodeScanner?,
            visionClient: ProductVisionClient?
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return NutritionViewModel(
                    nutritionRepository = nutritionRepository,
                    settingsRepository = settingsRepository,
                    barcodeScanner = barcodeScanner,
                    visionClient = visionClient
                ) as T
            }
        }
    }
}
