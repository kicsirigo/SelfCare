package com.selfcare.app.di

import android.content.Context
import com.selfcare.app.data.local.SelfCareDatabase
import com.selfcare.app.data.local.SettingsRepository
import com.selfcare.app.data.ml.MlKitBarcodeScanner
import com.selfcare.app.data.ml.MlKitFaceAnalyzer
import com.selfcare.app.data.ml.MlKitOcrScanner
import com.selfcare.app.data.ml.OnnxAcneDetector
import com.selfcare.app.data.ml.OnnxSkinSignalsAnalyzer
import com.selfcare.app.data.remote.AppUpdateManager
import com.selfcare.app.data.remote.GroqVisionProductClient
import com.selfcare.app.data.remote.OpenBeautyFactsClient
import com.selfcare.app.data.remote.OpenFoodFactsNutritionClient
import com.selfcare.app.data.remote.ProductVisionClient
import com.selfcare.app.data.repository.BackupRepository
import com.selfcare.app.data.repository.FaceCheckInRepository
import com.selfcare.app.data.repository.HygieneRepository
import com.selfcare.app.data.repository.NutritionRepository
import com.selfcare.app.data.repository.ProductRepository
import com.selfcare.app.data.repository.RoutineRepository
import com.selfcare.app.domain.cv.HaircutTracker
import com.selfcare.app.domain.inci.InciDatabaseLoader
import com.selfcare.app.domain.inci.InciEngine
import com.selfcare.app.domain.ml.BarcodeScanner
import com.selfcare.app.domain.ml.FaceAnalyzer
import com.selfcare.app.domain.ml.OcrScanner
import com.selfcare.app.notifications.NotificationHelper
import com.selfcare.app.notifications.ReminderScheduler
import kotlinx.coroutines.launch
import java.time.Clock

open class AppContainer(
    val context: Context,
    open val database: SelfCareDatabase = SelfCareDatabase.getInstance(context),
    open val settingsRepository: SettingsRepository = SettingsRepository.create(context),
    open val clock: Clock = Clock.systemDefaultZone()
) {
    open val routineRepository: RoutineRepository by lazy {
        RoutineRepository(
            routineDao = database.routineDao(),
            productDao = database.productDao(),
            clock = clock
        )
    }

    open val hygieneRepository: HygieneRepository by lazy {
        HygieneRepository(
            hygieneDao = database.hygieneDao(),
            clock = clock
        )
    }

    open val backupRepository: BackupRepository by lazy {
        BackupRepository(
            database = database,
            settingsRepository = settingsRepository,
            clock = clock,
            checkInsDir = java.io.File(context.filesDir, "checkins")
        )
    }

    open val notificationHelper: NotificationHelper by lazy {
        NotificationHelper(context)
    }

    open val reminderScheduler: ReminderScheduler by lazy {
        ReminderScheduler(context, clock)
    }

    open val faceAnalyzer: FaceAnalyzer by lazy {
        MlKitFaceAnalyzer(
            acneDetector = OnnxAcneDetector(context),
            skinSignalsAnalyzer = OnnxSkinSignalsAnalyzer(context)
        )
    }

    open val haircutTracker: HaircutTracker by lazy {
        HaircutTracker(
            haircutBaselineDao = database.haircutBaselineDao(),
            notificationHelper = notificationHelper,
            clock = clock
        )
    }

    open val faceCheckInRepository: FaceCheckInRepository by lazy {
        FaceCheckInRepository(
            context = context,
            faceCheckInDao = database.faceCheckInDao(),
            routineDao = database.routineDao(),
            hygieneDao = database.hygieneDao(),
            settingsRepository = settingsRepository,
            faceAnalyzer = faceAnalyzer,
            haircutTracker = haircutTracker,
            clock = clock
        )
    }

    open val inciEngine: InciEngine by lazy {
        InciEngine(InciDatabaseLoader.loadFromContext(context))
    }

    open val openBeautyFactsClient: OpenBeautyFactsClient by lazy {
        OpenBeautyFactsClient()
    }

    private val quotaScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )

    open val productVisionClient: ProductVisionClient by lazy {
        GroqVisionProductClient(
            onUsageRecorded = { event ->
                quotaScope.launch {
                    runCatching { settingsRepository.recordGroqUsage(event) }
                }
            }
        )
    }

    open val openFoodFactsNutritionClient: OpenFoodFactsNutritionClient by lazy {
        OpenFoodFactsNutritionClient()
    }

    open val nutritionRepository: NutritionRepository by lazy {
        NutritionRepository(
            nutritionDao = database.nutritionDao(),
            openFoodFactsClient = openFoodFactsNutritionClient,
            clock = clock
        )
    }

    open val appUpdateManager: AppUpdateManager by lazy {
        AppUpdateManager(context = context)
    }

    open val productRepository: ProductRepository by lazy {
        ProductRepository(
            productDao = database.productDao(),
            openBeautyFactsClient = openBeautyFactsClient,
            inciEngine = inciEngine,
            clock = clock,
            productsPhotoDir = java.io.File(context.filesDir, "products")
        )
    }

    open val ocrScanner: OcrScanner by lazy {
        MlKitOcrScanner()
    }

    open val barcodeScanner: BarcodeScanner by lazy {
        MlKitBarcodeScanner()
    }
}
