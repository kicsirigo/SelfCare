package com.selfcare.app.ui.nutrition

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selfcare.app.R
import com.selfcare.app.data.local.entity.ActivityLogEntity
import com.selfcare.app.data.local.entity.CustomRecipeEntity
import com.selfcare.app.data.local.entity.NutritionLogEntity
import com.selfcare.app.domain.health.ActivityCalorieCalculator
import com.selfcare.app.domain.health.ActivityLevel
import com.selfcare.app.domain.health.FoodPresetCatalog
import com.selfcare.app.domain.health.MacroCalculator
import com.selfcare.app.domain.health.MacroGoalType
import com.selfcare.app.ui.components.AnimatedWaterGlass
import com.selfcare.app.ui.components.CelebrationOverlay
import com.selfcare.app.ui.icons.AppIcons
import com.selfcare.app.ui.shelf.ScanPhotoHelper
import com.selfcare.app.ui.theme.NeonGreen
import com.selfcare.app.ui.theme.NeonWarning
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NutritionScreen(
    viewModel: NutritionViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val isHungarian = remember(config) {
        config.locales.get(0)?.language?.equals("hu", ignoreCase = true) == true
    }

    var pendingPhotoFile by remember { mutableStateOf<File?>(null) }
    var pendingCaptureMode by remember { mutableStateOf("MEAL") }
    var showCustomWaterDialog by remember { mutableStateOf(false) }
    var showManualMealDialog by remember { mutableStateOf(false) }
    var showGoalsDialog by remember { mutableStateOf(false) }
    var showWeightDialog by remember { mutableStateOf(false) }
    var showActivityDialog by remember { mutableStateOf(false) }
    var showRecipeBuilderDialog by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingPhotoFile
        pendingPhotoFile = null
        if (!success) {
            ScanPhotoHelper.cleanupTempFile(file)
            return@rememberLauncherForActivityResult
        }
        val bitmap = ScanPhotoHelper.decodeAndDeleteTempPhoto(file)
        when (pendingCaptureMode) {
            "BARCODE" -> viewModel.onBarcodePhotoCaptured(bitmap)
            "LABEL" -> viewModel.onNutritionLabelPhotoCaptured(bitmap)
            else -> viewModel.onMealPhotoCaptured(bitmap)
        }
    }

    fun launchCamera(mode: String) {
        pendingCaptureMode = mode
        try {
            val capture = ScanPhotoHelper.createTempScanCapture(context)
            pendingPhotoFile = capture.file
            cameraLauncher.launch(capture.uri)
        } catch (_: ActivityNotFoundException) {
            ScanPhotoHelper.cleanupTempFile(pendingPhotoFile)
            pendingPhotoFile = null
            showManualMealDialog = true
        } catch (_: Exception) {
            ScanPhotoHelper.cleanupTempFile(pendingPhotoFile)
            pendingPhotoFile = null
            showManualMealDialog = true
        }
    }

    if (showCustomWaterDialog) {
        CustomWaterDialog(
            onDismiss = { showCustomWaterDialog = false },
            onConfirm = { ml ->
                viewModel.addWater(ml)
                showCustomWaterDialog = false
            }
        )
    }

    if (showManualMealDialog) {
        ManualMealDialog(
            defaultMealType = state.selectedMealType,
            onDismiss = { showManualMealDialog = false },
            onSave = { name, grams, kcal, protein, carbs, fat, mealType ->
                viewModel.saveConfirmedMeal(
                    name = name,
                    brand = "",
                    barcode = null,
                    grams = grams,
                    caloriesKcal = kcal,
                    proteinGrams = protein,
                    carbsGrams = carbs,
                    fatGrams = fat,
                    fiberGrams = 0f,
                    sugarGrams = 0f,
                    mealType = mealType,
                    source = "MANUAL"
                )
                showManualMealDialog = false
            }
        )
    }

    if (showGoalsDialog) {
        NutritionGoalsDialog(
            state = state,
            onDismiss = { showGoalsDialog = false },
            onSaveManual = { kcal, p, c, f, w ->
                viewModel.updateManualTargets(kcal, p, c, f, w)
                showGoalsDialog = false
            },
            onCalculateAuto = { weight, targetWeight, height, age, isMale, activity, goal ->
                viewModel.calculateAndSaveTargets(weight, targetWeight, height, age, isMale, activity, goal)
                showGoalsDialog = false
            }
        )
    }

    if (showWeightDialog) {
        WeightLogDialog(
            initialWeightKg = state.latestLoggedWeightKg ?: state.bodyWeightKg,
            onDismiss = { showWeightDialog = false },
            onSave = { kg ->
                viewModel.logWeightEntry(kg)
                showWeightDialog = false
            }
        )
    }

    if (showActivityDialog) {
        ActivityLogDialog(
            weightKg = state.latestLoggedWeightKg ?: state.bodyWeightKg ?: 75f,
            isHungarian = isHungarian,
            onDismiss = { showActivityDialog = false },
            onSave = { name, mins, burned ->
                viewModel.logActivity(name, mins, burned)
                showActivityDialog = false
            }
        )
    }

    if (showRecipeBuilderDialog) {
        RecipeBuilderDialog(
            isHungarian = isHungarian,
            onDismiss = { showRecipeBuilderDialog = false },
            onSave = { name, servings, totalG, totalKcal, totalP, totalC, totalF, totalFib, totalSug, totalSalt, summary ->
                viewModel.saveCustomRecipe(
                    name = name,
                    servingsCount = servings,
                    totalGrams = totalG,
                    totalKcal = totalKcal,
                    totalProtein = totalP,
                    totalCarbs = totalC,
                    totalFat = totalF,
                    totalFiber = totalFib,
                    totalSugar = totalSug,
                    totalSalt = totalSalt,
                    ingredientsSummary = summary
                )
                showRecipeBuilderDialog = false
            }
        )
    }

    state.pendingConfirmation?.let { pending ->
        MealConfirmationDialog(
            pending = pending,
            defaultMealType = state.selectedMealType,
            onDismiss = viewModel::dismissPendingConfirmation,
            onConfirm = { name, grams, kcal, p, c, f, fiber, sugar, salt, mealType ->
                viewModel.saveConfirmedMeal(
                    name = name,
                    brand = pending.brand,
                    barcode = pending.barcode,
                    grams = grams,
                    caloriesKcal = kcal,
                    proteinGrams = p,
                    carbsGrams = c,
                    fatGrams = f,
                    fiberGrams = fiber,
                    sugarGrams = sugar,
                    saltGrams = salt,
                    mealType = mealType,
                    source = pending.source
                )
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("nutrition_screen"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 0. Day Switcher (< Yesterday | Today >)
            item {
                NutritionDateSwitcherBar(
                    selectedEpochDay = state.selectedEpochDay,
                    isToday = state.isToday,
                    onPreviousDay = viewModel::goToPreviousDay,
                    onNextDay = viewModel::goToNextDay,
                    onJumpToToday = viewModel::goToToday
                )
            }

            // 1. Calorie Ring + Protein/Carbs/Fat Dashboard Card
            item {
                CalorieMacroSummaryCard(
                    state = state,
                    onEditGoals = { showGoalsDialog = true },
                    onLogWeight = { showWeightDialog = true }
                )
            }

            // 2. Animated Water Glass Hydration Card (+250ml, +500ml, Custom + Salt Hydration Bonus)
            item {
                HydrationGlassCard(
                    currentMl = state.totalWaterMl,
                    targetMl = state.waterTargetMl,
                    totalSaltGrams = state.totalSaltGrams,
                    saltBonusMl = state.saltWaterBonusMl,
                    canUndo = state.todayWaterLogs.isNotEmpty(),
                    onAddWater = viewModel::addWater,
                    onCustomWaterClick = { showCustomWaterDialog = true },
                    onUndoLast = viewModel::undoLastWater
                )
            }

            // 3. Quick Capture Bar (Meal Photo AI + Nutrition Label + Barcode + Manual)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { launchCamera("MEAL") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("nutrition_meal_photo_btn")
                        ) {
                            Icon(
                                imageVector = AppIcons.Camera,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.nutrition_meal_photo_ai),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(
                            onClick = { launchCamera("LABEL") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("nutrition_label_scan_btn")
                        ) {
                            Icon(
                                imageVector = AppIcons.Scan,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.nutrition_label_scan_btn),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { launchCamera("BARCODE") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("nutrition_barcode_btn")
                        ) {
                            Icon(
                                imageVector = AppIcons.Barcode,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.nutrition_barcode_btn),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(
                            onClick = { showManualMealDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("nutrition_manual_add_btn")
                        ) {
                            Icon(
                                imageVector = AppIcons.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.nutrition_add_manual),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    AnimatedVisibility(visible = state.isBusy) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    state.statusMessage?.let { rawMsg ->
                        val displayMsg = when {
                            rawMsg == "NO_GROQ_KEY" -> stringResource(R.string.nutrition_err_no_groq_key)
                            rawMsg == "BARCODE_NOT_FOUND" -> stringResource(R.string.nutrition_err_no_barcode)
                            rawMsg == "NOT_FOUND" -> stringResource(R.string.nutrition_err_not_found)
                            rawMsg.startsWith("FOOD_NOT_FOUND:") ->
                                stringResource(R.string.nutrition_err_food_not_found, rawMsg.substringAfter(':'))
                            else -> rawMsg
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = displayMsg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = viewModel::clearStatusMessage,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = AppIcons.Close,
                                        contentDescription = stringResource(R.string.action_close),
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Meal Type Filter + Offline Food Presets & OpenFoodFacts Search
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                NutritionLogEntity.MEAL_BREAKFAST to R.string.nutrition_meal_breakfast,
                                NutritionLogEntity.MEAL_LUNCH to R.string.nutrition_meal_lunch,
                                NutritionLogEntity.MEAL_DINNER to R.string.nutrition_meal_dinner,
                                NutritionLogEntity.MEAL_SNACK to R.string.nutrition_meal_snack
                            ).forEach { (type, labelRes) ->
                                FilterChip(
                                    selected = state.selectedMealType == type,
                                    onClick = { viewModel.selectMealType(type) },
                                    label = { Text(text = stringResource(labelRes)) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = state.searchQuery,
                                onValueChange = viewModel::onSearchQueryChange,
                                singleLine = true,
                                placeholder = { Text(stringResource(R.string.nutrition_search_hint)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = AppIcons.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = viewModel::estimateMealByText,
                                enabled = state.searchQuery.isNotBlank() && !state.isBusy,
                                modifier = Modifier
                                    .weight(1.2f)
                                    .testTag("nutrition_ai_text_estimate_btn")
                            ) {
                                Icon(
                                    imageVector = AppIcons.Sparkle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.nutrition_ai_estimate_btn),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            OutlinedButton(
                                onClick = viewModel::searchOpenFoodFacts,
                                enabled = state.searchQuery.isNotBlank() && !state.isBusy,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = stringResource(R.string.shelf_lookup),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Text(
                            text = stringResource(R.string.privacy_no_ai_training_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (state.recentUniqueMeals.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.nutrition_recent_meals),
                                style = MaterialTheme.typography.labelMedium,
                                color = NeonGreen
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(state.recentUniqueMeals, key = { "recent_${it.id}" }) { recent ->
                                    AssistChip(
                                        onClick = { viewModel.openRecentMealConfirmation(recent) },
                                        label = {
                                            Text(
                                                text = "${recent.name} (${recent.caloriesKcal} kcal)",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        if (state.remoteMatches.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.nutrition_remote_results),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF38BDF8)
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(state.remoteMatches) { remote ->
                                    AssistChip(
                                        onClick = { viewModel.openRemoteFoodConfirmation(remote, source = "OFF_SEARCH") },
                                        label = {
                                            Text(
                                                text = "${remote.name} (${remote.per100gKcal.roundToInt()} kcal)",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        Text(
                            text = stringResource(R.string.nutrition_quick_presets),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.presetMatches.take(14), key = { it.id }) { preset ->
                                val label = if (isHungarian) preset.nameHu else preset.nameEn
                                AssistChip(
                                    onClick = { viewModel.openPresetConfirmation(preset, isHungarian) },
                                    label = {
                                        Text(
                                            text = "$label · ${preset.per100gProtein.roundToInt()}g P",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 4b. Custom Recipes (1-tap)
            item {
                CustomRecipesCard(
                    recipes = state.customRecipes,
                    onAddRecipe = { showRecipeBuilderDialog = true },
                    onSelectRecipe = viewModel::openRecipeConfirmation,
                    onDeleteRecipe = viewModel::deleteCustomRecipe
                )
            }

            // 4c. Exercise & Burned Calories
            item {
                ExerciseActivityCard(
                    activities = state.todayActivities,
                    totalBurnedKcal = state.totalBurnedKcal,
                    onAddActivity = { showActivityDialog = true },
                    onDeleteActivity = viewModel::deleteActivity
                )
            }

            // 5. Today's Logged Meals
            item {
                Text(
                    text = stringResource(R.string.nutrition_today_meals),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (state.todayMeals.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            text = stringResource(R.string.nutrition_empty_meals),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(state.todayMeals, key = { it.id }) { meal ->
                    MealLogCard(
                        meal = meal,
                        onDelete = { viewModel.deleteMeal(meal.id) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }

        // Non-blocking Confetti & Twinkling Stars Celebration Overlay
        CelebrationOverlay(triggerKey = state.celebrationTrigger)
    }
}

@Composable
private fun CalorieMacroSummaryCard(
    state: NutritionUiState,
    onEditGoals: () -> Unit,
    onLogWeight: () -> Unit
) {
    val targetKcal = state.effectiveCalorieTarget.coerceAtLeast(1)
    val kcalProgress by animateFloatAsState(
        targetValue = (state.totalCalories.toFloat() / targetKcal).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessLow),
        label = "kcal_ring"
    )
    val remaining = (targetKcal - state.totalCalories).coerceAtLeast(0)
    val isOver = state.totalCalories > (targetKcal * 1.08f)
    val ringColor = when {
        isOver -> MaterialTheme.colorScheme.error
        state.totalCalories >= (targetKcal * 0.90f) -> NeonGreen
        else -> Color(0xFF38BDF8)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("nutrition_summary_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.nutrition_daily_overview),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = onLogWeight,
                        modifier = Modifier.testTag("nutrition_log_weight_btn")
                    ) {
                        Text(
                            text = stringResource(R.string.nutrition_log_weight_btn),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    TextButton(
                        onClick = onEditGoals,
                        modifier = Modifier.testTag("nutrition_edit_goals_btn")
                    ) {
                        Text(
                            text = stringResource(R.string.nutrition_goals_btn),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier.size(116.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val stroke = 11.dp.toPx()
                        drawArc(
                            color = Color.White.copy(alpha = 0.10f),
                            startAngle = 135f,
                            sweepAngle = 270f,
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = ringColor,
                            startAngle = 135f,
                            sweepAngle = 270f * kcalProgress,
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${state.totalCalories}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "/ $targetKcal kcal",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.nutrition_remaining_kcal, remaining),
                            style = MaterialTheme.typography.labelSmall,
                            color = ringColor
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MacroBarRow(
                        label = stringResource(R.string.nutrition_protein),
                        current = state.totalProtein,
                        target = state.proteinTarget,
                        color = NeonGreen
                    )
                    MacroBarRow(
                        label = stringResource(R.string.nutrition_carbs),
                        current = state.totalCarbs,
                        target = state.carbsTarget,
                        color = Color(0xFF38BDF8)
                    )
                    MacroBarRow(
                        label = stringResource(R.string.nutrition_fat),
                        current = state.totalFat,
                        target = state.fatTarget,
                        color = NeonWarning
                    )
                }
            }

            if (state.totalBurnedKcal > 0) {
                Text(
                    text = stringResource(R.string.nutrition_burned_bonus, state.totalBurnedKcal),
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonGreen
                )
            }

            if (state.bodyWeightKg != null) {
                val goalText = when (state.bodyMacroGoal) {
                    MacroGoalType.CUT.name -> stringResource(R.string.nutrition_goal_cut)
                    MacroGoalType.BULK.name -> stringResource(R.string.nutrition_goal_bulk)
                    else -> stringResource(R.string.nutrition_goal_maintain)
                }
                val weightSummary = if (state.targetWeightKg != null) {
                    "${String.format(Locale.US, "%.1f", state.bodyWeightKg)} kg → ${String.format(Locale.US, "%.1f", state.targetWeightKg)} kg · $goalText"
                } else {
                    "${String.format(Locale.US, "%.1f", state.bodyWeightKg)} kg · $goalText"
                }
                Text(
                    text = weightSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF38BDF8)
                )
            }

            if (state.totalFiber > 0f || state.totalSugar > 0f || state.totalSaltGrams > 0f) {
                Text(
                    text = stringResource(
                        R.string.nutrition_fiber_sugar_salt_summary,
                        String.format(Locale.US, "%.1f", state.totalFiber),
                        String.format(Locale.US, "%.1f", state.totalSugar),
                        String.format(Locale.US, "%.1f", state.totalSaltGrams)
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MacroBarRow(
    label: String,
    current: Float,
    target: Int,
    color: Color
) {
    val progress by animateFloatAsState(
        targetValue = (current / target.coerceAtLeast(1)).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessLow),
        label = "macro_bar_$label"
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${current.roundToInt()} / ${target} g",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.10f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun HydrationGlassCard(
    currentMl: Int,
    targetMl: Int,
    totalSaltGrams: Float,
    saltBonusMl: Int,
    canUndo: Boolean,
    onAddWater: (Int) -> Unit,
    onCustomWaterClick: () -> Unit,
    onUndoLast: () -> Unit
) {
    val pct = ((currentMl.toFloat() / targetMl.coerceAtLeast(1)) * 100f).roundToInt().coerceAtMost(999)
    val reached = currentMl >= targetMl

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("nutrition_water_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (reached) BorderStroke(1.dp, NeonGreen.copy(alpha = 0.65f)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AnimatedWaterGlass(
                currentMl = currentMl,
                targetMl = targetMl,
                modifier = Modifier.testTag("nutrition_animated_water_glass")
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.nutrition_water_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "$currentMl / $targetMl ml ($pct%)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (reached) NeonGreen else Color(0xFF38BDF8)
                        )
                    }
                    if (canUndo) {
                        TextButton(onClick = onUndoLast) {
                            Text(
                                text = stringResource(R.string.nutrition_water_undo),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                if (totalSaltGrams > 0f) {
                    val saltText = if (saltBonusMl > 0) {
                        stringResource(
                            R.string.nutrition_salt_water_bonus,
                            String.format(Locale.US, "%.1f", totalSaltGrams),
                            saltBonusMl
                        )
                    } else {
                        stringResource(
                            R.string.nutrition_salt_daily_ok,
                            String.format(Locale.US, "%.1f", totalSaltGrams)
                        )
                    }
                    Text(
                        text = saltText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (saltBonusMl > 0) NeonWarning else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (reached) {
                    Text(
                        text = stringResource(R.string.nutrition_water_goal_reached),
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = { onAddWater(250) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("nutrition_water_250_btn")
                    ) {
                        Text(text = "+250 ml", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { onAddWater(500) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("nutrition_water_500_btn")
                    ) {
                        Text(text = "+500 ml", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onCustomWaterClick,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("nutrition_water_custom_btn")
                    ) {
                        Text(
                            text = stringResource(R.string.nutrition_water_custom),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MealLogCard(
    meal: NutritionLogEntity,
    onDelete: () -> Unit
) {
    val mealTypeLabel = when (meal.mealType) {
        NutritionLogEntity.MEAL_BREAKFAST -> stringResource(R.string.nutrition_meal_breakfast)
        NutritionLogEntity.MEAL_DINNER -> stringResource(R.string.nutrition_meal_dinner)
        NutritionLogEntity.MEAL_SNACK -> stringResource(R.string.nutrition_meal_snack)
        else -> stringResource(R.string.nutrition_meal_lunch)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = meal.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$mealTypeLabel · ${meal.grams.roundToInt()} g · ${meal.caloriesKcal} kcal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val saltSuffix = if (meal.saltGrams > 0.05f) {
                    "  ·  ${stringResource(R.string.nutrition_salt_short)}: ${String.format(Locale.US, "%.1f", meal.saltGrams)}g"
                } else {
                    ""
                }
                Text(
                    text = "P: ${meal.proteinGrams.roundToInt()}g  ·  CH: ${meal.carbsGrams.roundToInt()}g  ·  F: ${meal.fatGrams.roundToInt()}g$saltSuffix",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonGreen
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = AppIcons.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun CustomWaterDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var input by remember { mutableStateOf("350") }
    val parsed = input.trim().toIntOrNull()?.takeIf { it in 10..3000 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nutrition_water_custom_title)) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.filter { ch -> ch.isDigit() }.take(4) },
                label = { Text("ml (10–3000)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { parsed?.let(onConfirm) },
                enabled = parsed != null
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun MealConfirmationDialog(
    pending: PendingMealConfirmation,
    defaultMealType: String,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        grams: Float,
        kcal: Int,
        protein: Float,
        carbs: Float,
        fat: Float,
        fiber: Float,
        sugar: Float,
        salt: Float,
        mealType: String
    ) -> Unit
) {
    var name by remember(pending) { mutableStateOf(pending.name) }
    var gramsText by remember(pending) { mutableStateOf(pending.grams.roundToInt().toString()) }
    var kcalText by remember(pending) { mutableStateOf(pending.caloriesKcal.toString()) }
    var proteinText by remember(pending) { mutableStateOf(String.format(Locale.US, "%.1f", pending.proteinGrams)) }
    var carbsText by remember(pending) { mutableStateOf(String.format(Locale.US, "%.1f", pending.carbsGrams)) }
    var fatText by remember(pending) { mutableStateOf(String.format(Locale.US, "%.1f", pending.fatGrams)) }
    var saltText by remember(pending) { mutableStateOf(String.format(Locale.US, "%.1f", pending.saltGrams)) }
    var mealType by remember(pending) { mutableStateOf(defaultMealType) }

    fun applyMultiplier(factor: Float) {
        val g = (pending.grams * factor).coerceAtLeast(10f)
        gramsText = g.roundToInt().toString()
        kcalText = (pending.caloriesKcal * factor).roundToInt().toString()
        proteinText = String.format(Locale.US, "%.1f", pending.proteinGrams * factor)
        carbsText = String.format(Locale.US, "%.1f", pending.carbsGrams * factor)
        fatText = String.format(Locale.US, "%.1f", pending.fatGrams * factor)
        saltText = String.format(Locale.US, "%.1f", pending.saltGrams * factor)
    }

    fun applyGramsIfScalable(newGramsStr: String) {
        gramsText = newGramsStr
        val g = newGramsStr.toFloatOrNull() ?: return
        if (pending.isPer100gScalable && g > 0f) {
            val scaled = MacroCalculator.scalePer100g(
                grams = g,
                per100gKcal = pending.per100gKcal,
                per100gProtein = pending.per100gProtein,
                per100gCarbs = pending.per100gCarbs,
                per100gFat = pending.per100gFat,
                per100gFiber = pending.per100gFiber,
                per100gSugar = pending.per100gSugar,
                per100gSalt = pending.per100gSalt
            )
            kcalText = scaled.caloriesKcal.toString()
            proteinText = String.format(Locale.US, "%.1f", scaled.proteinGrams)
            carbsText = String.format(Locale.US, "%.1f", scaled.carbsGrams)
            fatText = String.format(Locale.US, "%.1f", scaled.fatGrams)
            saltText = String.format(Locale.US, "%.1f", scaled.saltGrams)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = stringResource(R.string.nutrition_confirm_meal_title))
                if (pending.modelUsed.isNotBlank()) {
                    Text(
                        text = "AI: ${pending.modelUsed}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF38BDF8)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.nutrition_meal_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!pending.isPer100gScalable) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0.5f to "0.5×", 1.0f to "1×", 1.5f to "1.5×").forEach { (mult, lbl) ->
                            AssistChip(
                                onClick = { applyMultiplier(mult) },
                                label = { Text(lbl) }
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = gramsText,
                        onValueChange = { applyGramsIfScalable(it.filter { ch -> ch.isDigit() || ch == '.' }) },
                        label = { Text("g") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = kcalText,
                        onValueChange = { kcalText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("kcal") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = proteinText,
                        onValueChange = { proteinText = it },
                        label = { Text("P (g)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = carbsText,
                        onValueChange = { carbsText = it },
                        label = { Text("CH (g)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = fatText,
                        onValueChange = { fatText = it },
                        label = { Text("F (g)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = saltText,
                    onValueChange = { saltText = it },
                    label = { Text("${stringResource(R.string.nutrition_salt_short)} (g)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val g = gramsText.toFloatOrNull() ?: pending.grams
                    val k = kcalText.toIntOrNull() ?: pending.caloriesKcal
                    val p = proteinText.replace(',', '.').toFloatOrNull() ?: pending.proteinGrams
                    val c = carbsText.replace(',', '.').toFloatOrNull() ?: pending.carbsGrams
                    val f = fatText.replace(',', '.').toFloatOrNull() ?: pending.fatGrams
                    val s = saltText.replace(',', '.').toFloatOrNull() ?: pending.saltGrams
                    onConfirm(name.ifBlank { pending.name }, g, k, p, c, f, pending.fiberGrams, pending.sugarGrams, s, mealType)
                }
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun ManualMealDialog(
    defaultMealType: String,
    onDismiss: () -> Unit,
    onSave: (String, Float, Int, Float, Float, Float, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var grams by remember { mutableStateOf("150") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nutrition_add_manual)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.nutrition_meal_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = grams,
                        onValueChange = { grams = it.filter { ch -> ch.isDigit() } },
                        label = { Text("g") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = kcal,
                        onValueChange = { kcal = it.filter { ch -> ch.isDigit() } },
                        label = { Text("kcal") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = protein,
                        onValueChange = { protein = it },
                        label = { Text("P (g)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = carbs,
                        onValueChange = { carbs = it },
                        label = { Text("CH (g)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = fat,
                        onValueChange = { fat = it },
                        label = { Text("F (g)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        name.trim(),
                        grams.toFloatOrNull() ?: 100f,
                        kcal.toIntOrNull() ?: 0,
                        protein.replace(',', '.').toFloatOrNull() ?: 0f,
                        carbs.replace(',', '.').toFloatOrNull() ?: 0f,
                        fat.replace(',', '.').toFloatOrNull() ?: 0f,
                        defaultMealType
                    )
                },
                enabled = name.isNotBlank() && (kcal.toIntOrNull() ?: 0) > 0
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NutritionGoalsDialog(
    state: NutritionUiState,
    onDismiss: () -> Unit,
    onSaveManual: (Int, Int, Int, Int, Int) -> Unit,
    onCalculateAuto: (Float, Float?, Float, Int, Boolean, ActivityLevel, MacroGoalType) -> Unit
) {
    var useAutoCalc by remember { mutableStateOf(true) }
    var kcalStr by remember { mutableStateOf(state.calorieTarget.toString()) }
    var proteinStr by remember { mutableStateOf(state.proteinTarget.toString()) }
    var carbsStr by remember { mutableStateOf(state.carbsTarget.toString()) }
    var fatStr by remember { mutableStateOf(state.fatTarget.toString()) }
    var waterStr by remember { mutableStateOf(state.baseWaterTargetMl.toString()) }

    var weightStr by remember {
        mutableStateOf(state.bodyWeightKg?.let { String.format(Locale.US, "%.1f", it) } ?: "75")
    }
    var targetWeightStr by remember {
        mutableStateOf(state.targetWeightKg?.let { String.format(Locale.US, "%.1f", it) } ?: "")
    }
    var heightStr by remember { mutableStateOf((state.bodyHeightCm ?: 178).toString()) }
    var ageStr by remember { mutableStateOf((state.autoAgeYears ?: 26).toString()) }
    var isMale by remember { mutableStateOf(state.bodyIsMale) }
    var activity by remember {
        mutableStateOf(
            ActivityLevel.entries.firstOrNull { it.name == state.bodyActivityLevel } ?: ActivityLevel.MODERATE
        )
    }
    var goal by remember {
        mutableStateOf(
            MacroGoalType.entries.firstOrNull { it.name == state.bodyMacroGoal } ?: MacroGoalType.MAINTAIN
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nutrition_goals_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = useAutoCalc,
                        onClick = { useAutoCalc = true },
                        label = { Text(stringResource(R.string.nutrition_goals_auto)) }
                    )
                    FilterChip(
                        selected = !useAutoCalc,
                        onClick = { useAutoCalc = false },
                        label = { Text(stringResource(R.string.nutrition_goals_manual)) }
                    )
                }

                if (!useAutoCalc) {
                    OutlinedTextField(
                        value = kcalStr,
                        onValueChange = { kcalStr = it.filter { ch -> ch.isDigit() } },
                        label = { Text("kcal") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = proteinStr,
                            onValueChange = { proteinStr = it.filter { ch -> ch.isDigit() } },
                            label = { Text("P (g)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = carbsStr,
                            onValueChange = { carbsStr = it.filter { ch -> ch.isDigit() } },
                            label = { Text("CH (g)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = fatStr,
                            onValueChange = { fatStr = it.filter { ch -> ch.isDigit() } },
                            label = { Text("F (g)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = waterStr,
                        onValueChange = { waterStr = it.filter { ch -> ch.isDigit() } },
                        label = { Text("${stringResource(R.string.nutrition_water_title)} (ml)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    if (state.autoAgeYears != null) {
                        Text(
                            text = stringResource(R.string.nutrition_auto_age_hint, state.autoAgeYears),
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonGreen
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = weightStr,
                            onValueChange = { weightStr = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                            label = { Text(stringResource(R.string.nutrition_current_weight_label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = targetWeightStr,
                            onValueChange = { targetWeightStr = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                            label = { Text(stringResource(R.string.nutrition_target_weight_label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = heightStr,
                            onValueChange = { heightStr = it.filter { ch -> ch.isDigit() } },
                            label = { Text("cm") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = ageStr,
                            onValueChange = { ageStr = it.filter { ch -> ch.isDigit() } },
                            label = { Text(stringResource(R.string.nutrition_age_label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = isMale,
                            onClick = { isMale = true },
                            label = { Text(stringResource(R.string.nutrition_gender_male)) }
                        )
                        FilterChip(
                            selected = !isMale,
                            onClick = { isMale = false },
                            label = { Text(stringResource(R.string.nutrition_gender_female)) }
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            ActivityLevel.SEDENTARY to R.string.nutrition_act_sedentary,
                            ActivityLevel.LIGHT to R.string.nutrition_act_light,
                            ActivityLevel.MODERATE to R.string.nutrition_act_moderate,
                            ActivityLevel.ACTIVE to R.string.nutrition_act_active
                        ).forEach { (lvl, res) ->
                            FilterChip(
                                selected = activity == lvl,
                                onClick = { activity = lvl },
                                label = { Text(stringResource(res)) }
                            )
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = goal == MacroGoalType.CUT,
                            onClick = { goal = MacroGoalType.CUT },
                            label = { Text(stringResource(R.string.nutrition_goal_cut)) }
                        )
                        FilterChip(
                            selected = goal == MacroGoalType.MAINTAIN,
                            onClick = { goal = MacroGoalType.MAINTAIN },
                            label = { Text(stringResource(R.string.nutrition_goal_maintain)) }
                        )
                        FilterChip(
                            selected = goal == MacroGoalType.BULK,
                            onClick = { goal = MacroGoalType.BULK },
                            label = { Text(stringResource(R.string.nutrition_goal_bulk)) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!useAutoCalc) {
                        onSaveManual(
                            kcalStr.toIntOrNull() ?: state.calorieTarget,
                            proteinStr.toIntOrNull() ?: state.proteinTarget,
                            carbsStr.toIntOrNull() ?: state.carbsTarget,
                            fatStr.toIntOrNull() ?: state.fatTarget,
                            waterStr.toIntOrNull() ?: state.baseWaterTargetMl
                        )
                    } else {
                        onCalculateAuto(
                            weightStr.replace(',', '.').toFloatOrNull() ?: (state.bodyWeightKg ?: 75f),
                            targetWeightStr.replace(',', '.').toFloatOrNull(),
                            heightStr.toFloatOrNull() ?: (state.bodyHeightCm?.toFloat() ?: 178f),
                            ageStr.toIntOrNull() ?: (state.autoAgeYears ?: 26),
                            isMale,
                            activity,
                            goal
                        )
                    }
                }
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun NutritionDateSwitcherBar(
    selectedEpochDay: Long,
    isToday: Boolean,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onJumpToToday: () -> Unit
) {
    val todayEpoch = remember { LocalDate.now().toEpochDay() }
    val selectedDate = remember(selectedEpochDay) { LocalDate.ofEpochDay(selectedEpochDay) }
    val dateStr = remember(selectedDate) {
        selectedDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US))
    }
    val label = when {
        isToday -> "${stringResource(R.string.nutrition_day_today)} ($dateStr)"
        selectedEpochDay == todayEpoch - 1L -> "${stringResource(R.string.nutrition_day_yesterday)} ($dateStr)"
        else -> dateStr
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("nutrition_date_switcher"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onPreviousDay,
                modifier = Modifier.testTag("nutrition_prev_day_btn")
            ) {
                Icon(
                    imageVector = AppIcons.Back,
                    contentDescription = stringResource(R.string.nutrition_day_prev),
                    modifier = Modifier.size(18.dp)
                )
            }
            TextButton(onClick = onJumpToToday) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isToday) MaterialTheme.colorScheme.onSurface else Color(0xFF38BDF8)
                )
            }
            TextButton(
                onClick = onNextDay,
                enabled = !isToday,
                modifier = Modifier.testTag("nutrition_next_day_btn")
            ) {
                Text(
                    text = ">",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ExerciseActivityCard(
    activities: List<ActivityLogEntity>,
    totalBurnedKcal: Int,
    onAddActivity: () -> Unit,
    onDeleteActivity: (Long) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("nutrition_activity_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.nutrition_activity_section),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (totalBurnedKcal > 0) {
                        Text(
                            text = stringResource(R.string.nutrition_burned_bonus, totalBurnedKcal),
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonGreen
                        )
                    }
                }
                OutlinedButton(
                    onClick = onAddActivity,
                    modifier = Modifier.testTag("nutrition_add_activity_btn")
                ) {
                    Text(
                        text = stringResource(R.string.nutrition_activity_add_btn),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            if (activities.isEmpty()) {
                Text(
                    text = stringResource(R.string.nutrition_activity_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                activities.forEach { act ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = act.activityName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${act.durationMinutes} min · +${act.caloriesBurned} kcal",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonGreen
                            )
                        }
                        IconButton(onClick = { onDeleteActivity(act.id) }) {
                            Icon(
                                imageVector = AppIcons.Delete,
                                contentDescription = stringResource(R.string.action_delete),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomRecipesCard(
    recipes: List<CustomRecipeEntity>,
    onAddRecipe: () -> Unit,
    onSelectRecipe: (CustomRecipeEntity) -> Unit,
    onDeleteRecipe: (Long) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("nutrition_recipes_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.nutrition_recipes_section),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = onAddRecipe,
                    modifier = Modifier.testTag("nutrition_add_recipe_btn")
                ) {
                    Text(
                        text = stringResource(R.string.nutrition_recipes_add_btn),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            if (recipes.isEmpty()) {
                Text(
                    text = stringResource(R.string.nutrition_recipes_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recipes, key = { "recipe_${it.id}" }) { recipe ->
                        AssistChip(
                            onClick = { onSelectRecipe(recipe) },
                            label = {
                                Text(
                                    text = "${recipe.name} (${recipe.perServingKcal} kcal)",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { onDeleteRecipe(recipe.id) },
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Icon(
                                        imageVector = AppIcons.Close,
                                        contentDescription = stringResource(R.string.action_delete),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeightLogDialog(
    initialWeightKg: Float?,
    onDismiss: () -> Unit,
    onSave: (Float) -> Unit
) {
    var weightStr by remember {
        mutableStateOf(
            initialWeightKg?.let { String.format(Locale.US, "%.1f", it) } ?: "75.0"
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nutrition_weight_dialog_title)) },
        text = {
            OutlinedTextField(
                value = weightStr,
                onValueChange = { weightStr = it },
                label = { Text(stringResource(R.string.nutrition_weight_input_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val kg = weightStr.replace(',', '.').toFloatOrNull()
                    if (kg != null && kg in 30f..250f) {
                        onSave(kg)
                    }
                }
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivityLogDialog(
    weightKg: Float,
    isHungarian: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Int, Int) -> Unit
) {
    var selectedPreset by remember { mutableStateOf(ActivityCalorieCalculator.presets.first()) }
    var nameStr by remember { mutableStateOf(if (isHungarian) selectedPreset.nameHu else selectedPreset.nameEn) }
    var durationStr by remember { mutableStateOf("30") }
    var burnedStr by remember {
        mutableStateOf(
            ActivityCalorieCalculator.estimateBurnedKcal(selectedPreset.metValue, weightKg, 30).toString()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nutrition_activity_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ActivityCalorieCalculator.presets.forEach { preset ->
                        FilterChip(
                            selected = selectedPreset.id == preset.id,
                            onClick = {
                                selectedPreset = preset
                                nameStr = if (isHungarian) preset.nameHu else preset.nameEn
                                val mins = durationStr.toIntOrNull() ?: 30
                                burnedStr = ActivityCalorieCalculator.estimateBurnedKcal(
                                    preset.metValue,
                                    weightKg,
                                    mins
                                ).toString()
                            },
                            label = { Text(if (isHungarian) preset.nameHu else preset.nameEn) }
                        )
                    }
                }
                OutlinedTextField(
                    value = nameStr,
                    onValueChange = { nameStr = it },
                    label = { Text(stringResource(R.string.nutrition_activity_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = durationStr,
                        onValueChange = { raw ->
                            durationStr = raw.filter { it.isDigit() }
                            val mins = durationStr.toIntOrNull() ?: 0
                            if (mins > 0) {
                                burnedStr = ActivityCalorieCalculator.estimateBurnedKcal(
                                    selectedPreset.metValue,
                                    weightKg,
                                    mins
                                ).toString()
                            }
                        },
                        label = { Text(stringResource(R.string.nutrition_activity_duration_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = burnedStr,
                        onValueChange = { burnedStr = it.filter { ch -> ch.isDigit() } },
                        label = { Text(stringResource(R.string.nutrition_activity_burned_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val mins = durationStr.toIntOrNull()?.coerceAtLeast(1) ?: 30
                    val kcal = burnedStr.toIntOrNull()?.coerceAtLeast(1) ?: 100
                    if (nameStr.isNotBlank()) {
                        onSave(nameStr.trim(), mins, kcal)
                    }
                }
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

private data class RecipeIngredientDraft(
    val name: String,
    val grams: Float,
    val kcal: Int,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val fiber: Float,
    val sugar: Float,
    val salt: Float
)

@Composable
private fun RecipeBuilderDialog(
    isHungarian: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Int, Float, Int, Float, Float, Float, Float, Float, Float, String) -> Unit
) {
    var recipeName by remember { mutableStateOf("") }
    var servingsStr by remember { mutableStateOf("4") }
    var ingredientSearch by remember { mutableStateOf("") }
    var ingredientGramsStr by remember { mutableStateOf("100") }
    var ingredients by remember { mutableStateOf<List<RecipeIngredientDraft>>(emptyList()) }

    val matchingPresets = remember(ingredientSearch) {
        FoodPresetCatalog.search(ingredientSearch).take(6)
    }
    val servings = servingsStr.toIntOrNull()?.coerceIn(1, 50) ?: 1
    val totalKcal = ingredients.sumOf { it.kcal }
    val perServingKcal = (totalKcal.toFloat() / servings).roundToInt()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nutrition_recipe_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = recipeName,
                    onValueChange = { recipeName = it },
                    label = { Text(stringResource(R.string.nutrition_recipe_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = servingsStr,
                    onValueChange = { servingsStr = it.filter { ch -> ch.isDigit() } },
                    label = { Text(stringResource(R.string.nutrition_recipe_servings_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ingredientSearch,
                        onValueChange = { ingredientSearch = it },
                        label = { Text(stringResource(R.string.nutrition_recipe_add_ingredient)) },
                        singleLine = true,
                        modifier = Modifier.weight(1.4f)
                    )
                    OutlinedTextField(
                        value = ingredientGramsStr,
                        onValueChange = { ingredientGramsStr = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("g") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(0.7f)
                    )
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(matchingPresets, key = { "ing_${it.id}" }) { preset ->
                        val label = if (isHungarian) preset.nameHu else preset.nameEn
                        AssistChip(
                            onClick = {
                                val g = ingredientGramsStr.toFloatOrNull()?.coerceAtLeast(1f) ?: preset.defaultGrams
                                val scaled = MacroCalculator.scalePer100g(
                                    grams = g,
                                    per100gKcal = preset.per100gKcal,
                                    per100gProtein = preset.per100gProtein,
                                    per100gCarbs = preset.per100gCarbs,
                                    per100gFat = preset.per100gFat,
                                    per100gFiber = preset.per100gFiber,
                                    per100gSugar = preset.per100gSugar,
                                    per100gSalt = preset.per100gSalt
                                )
                                ingredients = ingredients + RecipeIngredientDraft(
                                    name = label,
                                    grams = g,
                                    kcal = scaled.caloriesKcal,
                                    protein = scaled.proteinGrams,
                                    carbs = scaled.carbsGrams,
                                    fat = scaled.fatGrams,
                                    fiber = scaled.fiberGrams,
                                    sugar = scaled.sugarGrams,
                                    salt = scaled.saltGrams
                                )
                                ingredientSearch = ""
                            },
                            label = { Text("+ $label", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
                if (ingredients.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.nutrition_recipe_ingredients_count,
                            ingredients.size,
                            perServingKcal
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = NeonGreen
                    )
                    ingredients.forEachIndexed { idx, ing ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${ing.name} (${ing.grams.roundToInt()}g · ${ing.kcal} kcal)",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    ingredients = ingredients.filterIndexed { i, _ -> i != idx }
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = AppIcons.Close,
                                    contentDescription = stringResource(R.string.action_delete),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = recipeName.isNotBlank() && ingredients.isNotEmpty(),
                onClick = {
                    onSave(
                        recipeName.trim(),
                        servings,
                        ingredients.sumOf { it.grams.toDouble() }.toFloat(),
                        totalKcal,
                        ingredients.sumOf { it.protein.toDouble() }.toFloat(),
                        ingredients.sumOf { it.carbs.toDouble() }.toFloat(),
                        ingredients.sumOf { it.fat.toDouble() }.toFloat(),
                        ingredients.sumOf { it.fiber.toDouble() }.toFloat(),
                        ingredients.sumOf { it.sugar.toDouble() }.toFloat(),
                        ingredients.sumOf { it.salt.toDouble() }.toFloat(),
                        ingredients.joinToString(", ") { "${it.name} ${it.grams.roundToInt()}g" }
                    )
                }
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
