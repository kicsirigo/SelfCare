package com.selfcare.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.selfcare.app.data.local.UserSettings
import com.selfcare.app.di.AppContainer
import com.selfcare.app.ui.checkin.CheckInScreen
import com.selfcare.app.ui.checkin.CheckInViewModel
import com.selfcare.app.ui.hygiene.HygieneScreen
import com.selfcare.app.ui.hygiene.HygieneViewModel
import com.selfcare.app.ui.icons.AppIcons
import com.selfcare.app.ui.localization.LocaleHelper
import com.selfcare.app.ui.routine.RoutineScreen
import com.selfcare.app.ui.routine.RoutineViewModel
import com.selfcare.app.ui.settings.SettingsScreen
import com.selfcare.app.ui.settings.SettingsViewModel
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
    HYGIENE("nav_hygiene", R.string.nav_hygiene, AppIcons.Habit),
    SHELF("nav_shelf", R.string.nav_shelf, AppIcons.Bottle),
    CHECKIN("nav_checkin", R.string.nav_checkin, AppIcons.Face),
    TRENDS("nav_trends", R.string.nav_trends, AppIcons.Chart),
    SETTINGS("nav_settings", R.string.nav_settings, AppIcons.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as SelfCareApp).container
        setContent {
            SelfCareRoot(container = container)
        }
    }
}

@Composable
fun SelfCareRoot(container: AppContainer) {
    val settings by container.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = UserSettings())
    val baseContext = LocalContext.current

    val localizedContext = remember(baseContext, settings.appLanguage) {
        LocaleHelper.createLocalizedContext(baseContext, settings.appLanguage)
    }

    LaunchedEffect(settings.appLanguage) {
        LocaleHelper.syncPlatformLocale(baseContext, settings.appLanguage)
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

    CompositionLocalProvider(*providers) {
        SelfCareTheme(themeMode = settings.themeMode) {
            SelfCareScaffold(container = container)
        }
    }
}

@Composable
private fun SelfCareScaffold(container: AppContainer) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestination.ROUTINE) }

    val routineViewModel: RoutineViewModel = viewModel(
        factory = RoutineViewModel.factory(container.routineRepository)
    )
    val hygieneViewModel: HygieneViewModel = viewModel(
        factory = HygieneViewModel.factory(container.hygieneRepository)
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(
            settingsRepository = container.settingsRepository,
            backupRepository = container.backupRepository,
            reminderScheduler = container.reminderScheduler
        )
    )
    val shelfViewModel: ShelfViewModel = viewModel(
        factory = ShelfViewModel.factory(
            productRepository = container.productRepository,
            barcodeScanner = container.barcodeScanner,
            ocrScanner = container.ocrScanner
        )
    )
    val checkInViewModel: CheckInViewModel = viewModel(
        factory = CheckInViewModel.factory(
            faceCheckInRepository = container.faceCheckInRepository,
            haircutTracker = container.haircutTracker,
            settingsRepository = container.settingsRepository
        )
    )
    val trendsViewModel: TrendsViewModel = viewModel(
        factory = TrendsViewModel.factory(
            faceCheckInRepository = container.faceCheckInRepository
        )
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("app_root"),
        bottomBar = {
            NavigationBar {
                AppDestination.entries.forEach { destination ->
                    val label = stringResource(destination.labelRes)
                    NavigationBarItem(
                        selected = currentDestination == destination,
                        onClick = { currentDestination = destination },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = label,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.testTag(destination.testTag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentDestination) {
                AppDestination.ROUTINE -> RoutineScreen(viewModel = routineViewModel)
                AppDestination.HYGIENE -> HygieneScreen(viewModel = hygieneViewModel)
                AppDestination.SHELF -> ShelfScreen(viewModel = shelfViewModel)
                AppDestination.CHECKIN -> CheckInScreen(viewModel = checkInViewModel)
                AppDestination.TRENDS -> TrendsScreen(viewModel = trendsViewModel)
                AppDestination.SETTINGS -> SettingsScreen(viewModel = settingsViewModel)
            }
        }
    }
}
