package com.selfcare.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.selfcare.app.data.local.UserSettings
import com.selfcare.app.di.AppContainer
import com.selfcare.app.ui.checkin.CheckInScreen
import com.selfcare.app.ui.checkin.CheckInViewModel
import com.selfcare.app.ui.coach.AiCoachChatSheet
import com.selfcare.app.ui.coach.AiCoachViewModel
import com.selfcare.app.ui.icons.AppIcons
import com.selfcare.app.ui.localization.LocaleHelper
import com.selfcare.app.ui.nutrition.NutritionScreen
import com.selfcare.app.ui.nutrition.NutritionViewModel
import com.selfcare.app.ui.routine.RoutineScreen
import com.selfcare.app.ui.routine.RoutineViewModel
import com.selfcare.app.ui.settings.PatchNotesDialog
import com.selfcare.app.ui.settings.SettingsScreen
import com.selfcare.app.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch
import com.selfcare.app.ui.shelf.ShelfScreen
import com.selfcare.app.ui.shelf.ShelfViewModel
import com.selfcare.app.ui.theme.SelfCareTheme
import com.selfcare.app.ui.trends.TrendsScreen
import com.selfcare.app.ui.trends.TrendsViewModel

enum class AppDestination(
    val testTag: String,
    val labelRes: Int,
    val icon: ImageVector
) {
    ROUTINE("nav_routine", R.string.nav_routine, AppIcons.Sun),
    NUTRITION("nav_nutrition", R.string.nav_nutrition, AppIcons.Habit),
    SHELF("nav_shelf", R.string.nav_shelf, AppIcons.Bottle),
    CHECKIN("nav_checkin", R.string.nav_checkin, AppIcons.Face),
    TRENDS("nav_trends", R.string.nav_trends, AppIcons.Chart),
    SETTINGS("nav_settings", R.string.nav_settings, AppIcons.Settings)
}

class MainActivity : ComponentActivity() {
    private val sharedProductFlow = MutableStateFlow<Pair<String?, String?>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingShareIntent(intent)
        val container = (application as SelfCareApp).container
        setContent {
            SelfCareRoot(
                container = container,
                sharedProductFlow = sharedProductFlow,
                onConsumeSharedProduct = { sharedProductFlow.value = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun handleIncomingShareIntent(incoming: Intent?) {
        if (incoming?.action == Intent.ACTION_SEND && incoming.type == "text/plain") {
            val subject = incoming.getStringExtra(Intent.EXTRA_SUBJECT)
            val text = incoming.getStringExtra(Intent.EXTRA_TEXT)
            if (!subject.isNullOrBlank() || !text.isNullOrBlank()) {
                sharedProductFlow.value = subject to text
            }
        }
    }
}

@Composable
fun SelfCareRoot(
    container: AppContainer,
    sharedProductFlow: StateFlow<Pair<String?, String?>?>? = null,
    onConsumeSharedProduct: () -> Unit = {}
) {
    val loadedSettings by container.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = null)
    val settings = loadedSettings ?: UserSettings()
    val baseContext = LocalContext.current

    val localizedContext = remember(baseContext, settings.appLanguage) {
        LocaleHelper.createLocalizedContext(baseContext, settings.appLanguage)
    }

    LaunchedEffect(loadedSettings?.appLanguage) {
        val persistedLanguage = loadedSettings?.appLanguage ?: return@LaunchedEffect
        LocaleHelper.syncPlatformLocale(baseContext, persistedLanguage)
    }

    LaunchedEffect(settings.blockScreenshots) {
        val activity = baseContext as? ComponentActivity ?: return@LaunchedEffect
        if (settings.blockScreenshots) {
            activity.window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    val registryOwner = LocalActivityResultRegistryOwner.current
        ?: (baseContext as? ActivityResultRegistryOwner)

    val providers = buildList {
        add(LocalContext provides localizedContext)
        add(LocalConfiguration provides localizedContext.resources.configuration)
        if (registryOwner != null) {
            add(LocalActivityResultRegistryOwner provides registryOwner)
        }
    }.toTypedArray()

    val scope = rememberCoroutineScope()
    var patchNotesDismissedInSession by rememberSaveable { mutableStateOf(false) }
    val showAutoPatchNotes = loadedSettings != null &&
        !patchNotesDismissedInSession &&
        !Build.FINGERPRINT.equals("robolectric", ignoreCase = true) &&
        loadedSettings!!.lastSeenVersionCode < BuildConfig.VERSION_CODE

    CompositionLocalProvider(*providers) {
        SelfCareTheme(themeMode = settings.themeMode) {
            SelfCareScaffold(
                container = container,
                sharedProductFlow = sharedProductFlow,
                onConsumeSharedProduct = onConsumeSharedProduct
            )
            if (showAutoPatchNotes) {
                PatchNotesDialog(
                    isNewUpdate = true,
                    onDismiss = {
                        patchNotesDismissedInSession = true
                        scope.launch {
                            container.settingsRepository.setLastSeenVersionCode(BuildConfig.VERSION_CODE)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SelfCareScaffold(
    container: AppContainer,
    sharedProductFlow: StateFlow<Pair<String?, String?>?>? = null,
    onConsumeSharedProduct: () -> Unit = {}
) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestination.ROUTINE) }
    var previousDestination by rememberSaveable { mutableStateOf(AppDestination.ROUTINE) }

    val bottomDestinations = remember {
        listOf(
            AppDestination.ROUTINE,
            AppDestination.NUTRITION,
            AppDestination.CHECKIN,
            AppDestination.SHELF,
            AppDestination.TRENDS
        )
    }

    val routineViewModel: RoutineViewModel = viewModel(
        factory = RoutineViewModel.factory(container.routineRepository)
    )
    val nutritionViewModel: NutritionViewModel = viewModel(
        factory = NutritionViewModel.factory(
            nutritionRepository = container.nutritionRepository,
            settingsRepository = container.settingsRepository,
            barcodeScanner = container.barcodeScanner,
            visionClient = container.productVisionClient
        )
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(
            settingsRepository = container.settingsRepository,
            backupRepository = container.backupRepository,
            reminderScheduler = container.reminderScheduler,
            productVisionClient = container.productVisionClient,
            appUpdateManager = container.appUpdateManager
        )
    )
    val shelfViewModel: ShelfViewModel = viewModel(
        factory = ShelfViewModel.factory(
            productRepository = container.productRepository,
            barcodeScanner = container.barcodeScanner,
            ocrScanner = container.ocrScanner,
            settingsRepository = container.settingsRepository,
            productVisionClient = container.productVisionClient
        )
    )
    val checkInViewModel: CheckInViewModel = viewModel(
        factory = CheckInViewModel.factory(
            faceCheckInRepository = container.faceCheckInRepository,
            haircutTracker = container.haircutTracker,
            settingsRepository = container.settingsRepository,
            faceAnalyzer = container.faceAnalyzer,
            productRepository = container.productRepository,
            routineRepository = container.routineRepository,
            hygieneRepository = container.hygieneRepository
        )
    )
    val trendsViewModel: TrendsViewModel = viewModel(
        factory = TrendsViewModel.factory(
            faceCheckInRepository = container.faceCheckInRepository,
            nutritionRepository = container.nutritionRepository,
            settingsRepository = container.settingsRepository
        )
    )
    val aiCoachViewModel: AiCoachViewModel = viewModel(
        factory = AiCoachViewModel.factory(
            nutritionRepository = container.nutritionRepository,
            productRepository = container.productRepository,
            faceCheckInRepository = container.faceCheckInRepository,
            settingsRepository = container.settingsRepository,
            visionClient = container.productVisionClient
        )
    )
    var showAiCoachSheet by rememberSaveable { mutableStateOf(false) }

    val incomingShare = sharedProductFlow?.collectAsStateWithLifecycle(initialValue = null)?.value
    LaunchedEffect(incomingShare) {
        if (incomingShare != null) {
            currentDestination = AppDestination.SHELF
            shelfViewModel.onSharedProductText(incomingShare.first, incomingShare.second)
            onConsumeSharedProduct()
        }
    }

    if (showAiCoachSheet) {
        AiCoachChatSheet(
            viewModel = aiCoachViewModel,
            onDismiss = { showAiCoachSheet = false }
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("app_root"),
        floatingActionButtonPosition = FabPosition.Start,
        floatingActionButton = {
            if (currentDestination != AppDestination.SETTINGS) {
                SmallFloatingActionButton(
                    onClick = { showAiCoachSheet = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("ai_coach_fab")
                ) {
                    Icon(
                        imageVector = AppIcons.Sparkle,
                        contentDescription = stringResource(R.string.ai_coach_fab_desc),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (currentDestination == AppDestination.SETTINGS) {
                                IconButton(
                                    onClick = { currentDestination = previousDestination },
                                    modifier = Modifier.testTag("nav_settings_back")
                                ) {
                                    Icon(
                                        imageVector = AppIcons.Back,
                                        contentDescription = stringResource(R.string.action_back),
                                        tint = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Text(
                                text = stringResource(currentDestination.labelRes),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        Surface(
                            onClick = {
                                if (currentDestination == AppDestination.SETTINGS) {
                                    currentDestination = previousDestination
                                } else {
                                    previousDestination = currentDestination
                                    currentDestination = AppDestination.SETTINGS
                                }
                            },
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = if (currentDestination == AppDestination.SETTINGS) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (currentDestination == AppDestination.SETTINGS) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                                }
                            ),
                            modifier = Modifier.testTag(AppDestination.SETTINGS.testTag)
                        ) {
                            Box(
                                modifier = Modifier.padding(9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = AppIcons.Settings,
                                    contentDescription = stringResource(R.string.nav_settings),
                                    tint = if (currentDestination == AppDestination.SETTINGS) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
                    ),
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        bottomDestinations.forEach { destination ->
                            val label = stringResource(destination.labelRes)
                            val selected = currentDestination == destination
                            val isCenterCheckIn = destination == AppDestination.CHECKIN
                            Surface(
                                onClick = { currentDestination = destination },
                                shape = if (isCenterCheckIn) {
                                    androidx.compose.foundation.shape.CircleShape
                                } else {
                                    androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
                                },
                                color = when {
                                    selected -> MaterialTheme.colorScheme.primaryContainer
                                    isCenterCheckIn -> MaterialTheme.colorScheme.surfaceVariant
                                    else -> androidx.compose.ui.graphics.Color.Transparent
                                },
                                border = if (isCenterCheckIn) {
                                    androidx.compose.foundation.BorderStroke(
                                        1.25.dp,
                                        if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                        }
                                    )
                                } else {
                                    null
                                },
                                modifier = Modifier.testTag(destination.testTag)
                            ) {
                                Box(
                                    modifier = Modifier.padding(
                                        horizontal = if (isCenterCheckIn) 16.dp else 14.dp,
                                        vertical = 10.dp
                                    ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = label,
                                        tint = when {
                                            selected -> MaterialTheme.colorScheme.primary
                                            isCenterCheckIn -> MaterialTheme.colorScheme.onSurface
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(if (isCenterCheckIn) 24.dp else 22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentDestination,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220)) +
                        slideInVertically(animationSpec = tween(220)) { it / 28 }) togetherWith
                        fadeOut(animationSpec = tween(150))
                },
                label = "nav_tab_transition"
            ) { targetDestination ->
                when (targetDestination) {
                    AppDestination.ROUTINE -> RoutineScreen(viewModel = routineViewModel)
                    AppDestination.NUTRITION -> NutritionScreen(viewModel = nutritionViewModel)
                    AppDestination.SHELF -> ShelfScreen(viewModel = shelfViewModel)
                    AppDestination.CHECKIN -> CheckInScreen(viewModel = checkInViewModel)
                    AppDestination.TRENDS -> TrendsScreen(viewModel = trendsViewModel)
                    AppDestination.SETTINGS -> SettingsScreen(viewModel = settingsViewModel)
                }
            }
        }
    }
}
