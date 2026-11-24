package com.selfcare.app.ui.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.selfcare.app.data.local.SettingsRepository
import com.selfcare.app.data.local.UserSettings
import com.selfcare.app.data.local.entity.ProductEntity
import com.selfcare.app.data.local.entity.RoutineLogEntity
import com.selfcare.app.data.repository.HygieneRepository
import com.selfcare.app.data.repository.NutritionRepository
import com.selfcare.app.data.repository.ProductRepository
import com.selfcare.app.data.repository.RoutineRepository
import com.selfcare.app.domain.health.MealTimingAnalyzer
import com.selfcare.app.domain.health.StreakCalculator
import com.selfcare.app.domain.inci.DetectedConflict
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RoutineCustomHabitItem(
    val habitKey: String,
    val label: String,
    val completedToday: Boolean
)

data class RoutineUiState(
    val selectedPeriod: String = RoutineRepository.PERIOD_MORNING,
    val todayEpochDay: Long = 0L,
    val defaultProducts: List<ProductEntity> = emptyList(),
    val allProducts: List<ProductEntity> = emptyList(),
    val currentLog: RoutineLogEntity? = null,
    val isCompletedToday: Boolean = false,
    val completedAtHm: String = "",
    val morningCompletedToday: Boolean = false,
    val eveningCompletedToday: Boolean = false,
    val hairCompletedToday: Boolean = false,
    val includedProductIds: Set<Long> = emptySet(),
    val skippedProductIds: Set<Long> = emptySet(),
    val streakDays: Int = 0,
    val periodConflicts: List<DetectedConflict> = emptyList(),
    val todayCaloriesKcal: Int = 0,
    val calorieTargetKcal: Int = UserSettings.DEFAULT_CALORIE_TARGET,
    val todayWaterMl: Int = 0,
    val waterTargetMl: Int = UserSettings.DEFAULT_WATER_TARGET_ML,
    val shaveDaysUntilDue: Int? = null,
    val customHabits: List<RoutineCustomHabitItem> = emptyList()
)

private data class TodayPulseSnapshot(
    val todayCaloriesKcal: Int = 0,
    val calorieTargetKcal: Int = UserSettings.DEFAULT_CALORIE_TARGET,
    val todayWaterMl: Int = 0,
    val waterTargetMl: Int = UserSettings.DEFAULT_WATER_TARGET_ML,
    val shaveDaysUntilDue: Int? = null,
    val customHabits: List<RoutineCustomHabitItem> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class RoutineViewModel(
    private val routineRepository: RoutineRepository,
    private val nutritionRepository: NutritionRepository? = null,
    private val hygieneRepository: HygieneRepository? = null,
    private val settingsRepository: SettingsRepository? = null,
    private val productRepository: ProductRepository? = null
) : ViewModel() {

    private val selectedPeriod = MutableStateFlow(RoutineRepository.PERIOD_MORNING)
    private val currentEpochDay = MutableStateFlow(routineRepository.currentEpochDay())

    private val logsForToday = currentEpochDay.flatMapLatest { epochDay ->
        routineRepository.observeLogsForDate(epochDay)
    }

    private val todayMealsFlow = currentEpochDay.flatMapLatest { epochDay ->
        nutritionRepository?.observeMealsForDay(epochDay) ?: flowOf(emptyList())
    }

    private val todayWaterFlow = currentEpochDay.flatMapLatest { epochDay ->
        nutritionRepository?.observeWaterForDay(epochDay) ?: flowOf(emptyList())
    }

    private val allHygieneLogsFlow = hygieneRepository?.observeAllLogs() ?: flowOf(emptyList())
    private val settingsFlow = settingsRepository?.settings ?: flowOf(UserSettings())

    private val pulseFlow = combine(
        currentEpochDay,
        todayMealsFlow,
        todayWaterFlow,
        allHygieneLogsFlow,
        settingsFlow
    ) { epochDay, meals, waterLogs, hygieneLogs, settings ->
        val todayKcal = meals.sumOf { it.caloriesKcal }
        val todayWater = waterLogs.sumOf { it.amountMl }
        val shaveDaysUntilDue = settings.lastShaveEpochDay?.let { lastShave ->
            (settings.shaveIntervalDays - (epochDay - lastShave).toInt())
        }

        // Collect all distinct custom habit definitions and check today's completion state
        val customDefs = linkedMapOf<String, String>()
        hygieneLogs.asSequence()
            .filter { it.isCustom }
            .forEach { log ->
                customDefs[log.habitKey] = log.label
            }
        val todayCompletedKeys = hygieneLogs.asSequence()
            .filter { it.dateEpochDay == epochDay && it.completed }
            .map { it.habitKey }
            .toSet()
        val habits = customDefs.entries.map { (key, label) ->
            RoutineCustomHabitItem(
                habitKey = key,
                label = label,
                completedToday = key in todayCompletedKeys
            )
        }

        TodayPulseSnapshot(
            todayCaloriesKcal = todayKcal,
            calorieTargetKcal = settings.dailyCalorieTarget,
            todayWaterMl = todayWater,
            waterTargetMl = settings.dailyWaterTargetMl,
            shaveDaysUntilDue = shaveDaysUntilDue,
            customHabits = habits
        )
    }

    val uiState: StateFlow<RoutineUiState> = combine(
        selectedPeriod,
        currentEpochDay,
        logsForToday,
        routineRepository.observeAllProducts(),
        routineRepository.observeAllLogs(),
        pulseFlow
    ) { values ->
        val period = values[0] as String
        val epochDay = values[1] as Long
        @Suppress("UNCHECKED_CAST")
        val logs = values[2] as List<RoutineLogEntity>
        @Suppress("UNCHECKED_CAST")
        val allProducts = values[3] as List<ProductEntity>
        @Suppress("UNCHECKED_CAST")
        val allLogs = values[4] as List<RoutineLogEntity>
        val pulse = values[5] as TodayPulseSnapshot

        val defaultProducts = allProducts.filter { product ->
            product.isDefaultInRoutine && when (product.routineSlot.uppercase()) {
                RoutineRepository.PERIOD_MORNING -> period == RoutineRepository.PERIOD_MORNING
                RoutineRepository.PERIOD_EVENING -> period == RoutineRepository.PERIOD_EVENING
                RoutineRepository.SLOT_BOTH ->
                    period == RoutineRepository.PERIOD_MORNING || period == RoutineRepository.PERIOD_EVENING
                RoutineRepository.PERIOD_HAIR -> period == RoutineRepository.PERIOD_HAIR
                else -> period == RoutineRepository.PERIOD_HAIR && RoutineRepository.isHairCategory(product.category)
            }
        }.sortedBy { applicationStepPriority(it) }

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
        val activeDays = allLogs
            .asSequence()
            .filter { it.completedAtEpochMillis > 0L }
            .map { it.dateEpochDay }
            .toSet()

        val activeProductsForConflicts = defaultProducts.filter { it.id !in skippedIds }
        val conflicts = if (productRepository != null && activeProductsForConflicts.size >= 2) {
            productRepository.computeShelfConflicts(activeProductsForConflicts)
        } else {
            emptyList()
        }

        val morningDone = logs.any { it.period == RoutineRepository.PERIOD_MORNING && it.completedAtEpochMillis > 0L }
        val eveningDone = logs.any { it.period == RoutineRepository.PERIOD_EVENING && it.completedAtEpochMillis > 0L }
        val hairDone = logs.any { it.period == RoutineRepository.PERIOD_HAIR && it.completedAtEpochMillis > 0L }
        val completedHm = currentLog?.completedAtEpochMillis
            ?.takeIf { it > 0L }
            ?.let { MealTimingAnalyzer.formatTimeHm(it) }
            .orEmpty()

        RoutineUiState(
            selectedPeriod = period,
            todayEpochDay = epochDay,
            defaultProducts = defaultProducts,
            allProducts = allProducts,
            currentLog = currentLog,
            isCompletedToday = currentLog != null && currentLog.completedAtEpochMillis > 0L,
            completedAtHm = completedHm,
            morningCompletedToday = morningDone,
            eveningCompletedToday = eveningDone,
            hairCompletedToday = hairDone,
            includedProductIds = includedIds,
            skippedProductIds = skippedIds,
            streakDays = StreakCalculator.computeCurrentStreak(activeDays, epochDay),
            periodConflicts = conflicts,
            todayCaloriesKcal = pulse.todayCaloriesKcal,
            calorieTargetKcal = pulse.calorieTargetKcal,
            todayWaterMl = pulse.todayWaterMl,
            waterTargetMl = pulse.waterTargetMl,
            shaveDaysUntilDue = pulse.shaveDaysUntilDue,
            customHabits = pulse.customHabits
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

    fun renameProduct(productId: Long, newName: String) {
        viewModelScope.launch {
            routineRepository.renameProduct(productId, newName)
        }
    }

    fun quickAddWater250() {
        val repo = nutritionRepository ?: return
        viewModelScope.launch {
            repo.logWater(250, dateEpochDay = currentEpochDay.value)
        }
    }

    fun logShaveToday() {
        val repo = settingsRepository ?: return
        viewModelScope.launch {
            repo.setLastShaveEpochDay(currentEpochDay.value)
        }
    }

    fun toggleCustomHabit(habitKey: String, label: String) {
        val repo = hygieneRepository ?: return
        viewModelScope.launch {
            repo.toggleHabit(habitKey = habitKey, label = label, isCustom = true)
        }
    }

    fun addCustomHabit(label: String) {
        val repo = hygieneRepository ?: return
        viewModelScope.launch {
            repo.addCustomHabit(label = label, initialCompleted = false)
        }
    }

    fun setProductRoutineDefault(productId: Long, isMorning: Boolean, isEvening: Boolean) {
        viewModelScope.launch {
            routineRepository.setProductRoutineDefault(productId, isMorning, isEvening)
        }
    }

    companion object {
        /**
         * Sorts skincare/hair/vitamin steps into realistic application order:
         * 1. Cleanser/Wash/Shampoo -> 2. Toner/Exfoliant/Serum/Rx -> 3. Moisturizer/Cream/Ointment/Lip -> 4. SPF -> 5. Vitamins/Medications
         */
        fun applicationStepPriority(product: ProductEntity): Int {
            val cat = product.category.uppercase()
            val text = "${product.name} ${product.brand}".lowercase()
            return when {
                cat == "SHAMPOO" || text.contains("cleanser") || text.contains("lemos") || text.contains("wash") || text.contains("foam") || text.contains("sampon") -> 1
                cat == "RX_TREATMENT" || text.contains("toner") || text.contains("serum") || text.contains("szérum") || text.contains("retinol") || text.contains("acid") || text.contains("bha") || text.contains("aha") -> 2
                text.contains("spf") || text.contains("sun") || text.contains("fényvéd") -> 4
                cat == "VITAMIN" || cat == "MEDICATION" || text.contains("vitamin") || text.contains("magnézium") || text.contains("omega") || text.contains("kreatin") || text.contains("cink") -> 5
                else -> 3
            }
        }

        fun factory(
            routineRepository: RoutineRepository,
            nutritionRepository: NutritionRepository? = null,
            hygieneRepository: HygieneRepository? = null,
            settingsRepository: SettingsRepository? = null,
            productRepository: ProductRepository? = null
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return RoutineViewModel(
                        routineRepository = routineRepository,
                        nutritionRepository = nutritionRepository,
                        hygieneRepository = hygieneRepository,
                        settingsRepository = settingsRepository,
                        productRepository = productRepository
                    ) as T
                }
            }
    }
}
