package com.selfcare.app.e2e

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.selfcare.app.SelfCareApp
import com.selfcare.app.data.local.SelfCareDatabase
import com.selfcare.app.data.local.SettingsRepository
import com.selfcare.app.data.remote.OpenBeautyFactsClient
import com.selfcare.app.data.repository.FaceCheckInRepository
import com.selfcare.app.di.AppContainer
import com.selfcare.app.domain.ml.BarcodeScanner
import com.selfcare.app.domain.ml.FaceAnalysisResult
import com.selfcare.app.domain.ml.FaceAnalyzer
import com.selfcare.app.domain.ml.OcrScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import org.junit.rules.ExternalResource
import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class MutableClock(
    initialInstant: Instant = Instant.parse("2026-11-22T09:00:00Z"),
    private val zoneId: ZoneId = ZoneId.of("UTC")
) : Clock() {
    @Volatile
    private var currentInstant: Instant = initialInstant

    override fun getZone(): ZoneId = zoneId

    override fun withZone(zone: ZoneId): Clock = MutableClock(currentInstant, zone)

    override fun instant(): Instant = currentInstant

    fun setInstant(instant: Instant) {
        currentInstant = instant
    }

    fun advanceBy(duration: Duration) {
        currentInstant = currentInstant.plus(duration)
    }

    fun advanceDays(days: Long) {
        currentInstant = currentInstant.plus(Duration.ofDays(days))
    }
}

class FakeFaceAnalyzer(
    @Volatile var nextResult: FaceAnalysisResult = defaultResult()
) : FaceAnalyzer {
    var callCount: Int = 0
        private set

    override suspend fun analyze(bitmap: Bitmap): FaceAnalysisResult {
        callCount += 1
        return nextResult
    }

    companion object {
        fun defaultResult(
            underEyeDeltaLab: Float = 12.0f,
            skinRednessIndex: Float = 0.25f,
            skinTextureVariance: Float = 15.0f,
            lipDrynessScore: Float = 0.30f,
            hairToFaceRatio: Float = 0.42f,
            lightingQualityScore: Float = 0.90f,
            isLightingAcceptable: Boolean = true,
            lightingWarningReason: String? = null
        ) = FaceAnalysisResult(
            underEyeDeltaLab = underEyeDeltaLab,
            skinRednessIndex = skinRednessIndex,
            skinTextureVariance = skinTextureVariance,
            lipDrynessScore = lipDrynessScore,
            hairToFaceRatio = hairToFaceRatio,
            lightingQualityScore = lightingQualityScore,
            isLightingAcceptable = isLightingAcceptable,
            lightingWarningReason = lightingWarningReason
        )
    }
}

class FakeOcrScanner(
    @Volatile var nextText: String = ""
) : OcrScanner {
    var callCount: Int = 0
        private set

    override suspend fun recognizeText(bitmap: Bitmap): String {
        callCount += 1
        return nextText
    }
}

class FakeBarcodeScanner(
    @Volatile var nextBarcode: String? = null
) : BarcodeScanner {
    var callCount: Int = 0
        private set

    override suspend fun scanBarcode(bitmap: Bitmap): String? {
        callCount += 1
        return nextBarcode
    }
}

class TestAppContainer(
    context: Context,
    override val database: SelfCareDatabase,
    override val settingsRepository: SettingsRepository,
    override val clock: MutableClock,
    override val faceAnalyzer: FakeFaceAnalyzer,
    override val ocrScanner: FakeOcrScanner,
    override val barcodeScanner: FakeBarcodeScanner,
    private val obfBaseUrl: String
) : AppContainer(
    context = context,
    database = database,
    settingsRepository = settingsRepository,
    clock = clock
) {
    override val openBeautyFactsClient: OpenBeautyFactsClient by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .writeTimeout(2, TimeUnit.SECONDS)
            .build()
        OpenBeautyFactsClient(okHttpClient = client, baseUrl = obfBaseUrl)
    }
}

class SelfCareE2ERule : ExternalResource() {

    lateinit var app: SelfCareApp
        private set
    lateinit var database: SelfCareDatabase
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var clock: MutableClock
        private set
    lateinit var fakeFaceAnalyzer: FakeFaceAnalyzer
        private set
    lateinit var fakeOcrScanner: FakeOcrScanner
        private set
    lateinit var fakeBarcodeScanner: FakeBarcodeScanner
        private set
    lateinit var mockWebServer: MockWebServer
        private set
    lateinit var container: TestAppContainer
        private set

    private lateinit var tempDir: File
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>

    override fun before() {
        app = ApplicationProvider.getApplicationContext()
        File(app.filesDir, FaceCheckInRepository.CHECKINS_DIR_NAME).deleteRecursively()

        tempDir = Files.createTempDirectory("selfcare_e2e_").toFile()
        mockWebServer = MockWebServer()
        mockWebServer.start()

        database = Room.inMemoryDatabaseBuilder(app, SelfCareDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { File(tempDir, "e2e_settings.preferences_pb") }
        )
        settingsRepository = SettingsRepository(dataStore)

        clock = MutableClock()
        fakeFaceAnalyzer = FakeFaceAnalyzer()
        fakeOcrScanner = FakeOcrScanner()
        fakeBarcodeScanner = FakeBarcodeScanner()

        container = TestAppContainer(
            context = app,
            database = database,
            settingsRepository = settingsRepository,
            clock = clock,
            faceAnalyzer = fakeFaceAnalyzer,
            ocrScanner = fakeOcrScanner,
            barcodeScanner = fakeBarcodeScanner,
            obfBaseUrl = mockWebServer.url("/").toString()
        )
        app.setTestContainer(container)
    }

    override fun after() {
        try {
            app.resetContainer()
            database.close()
            dataStoreScope.cancel()
            mockWebServer.shutdown()
        } finally {
            File(app.filesDir, FaceCheckInRepository.CHECKINS_DIR_NAME).deleteRecursively()
            tempDir.deleteRecursively()
        }
    }
}

fun createTestBitmap(
    width: Int = 64,
    height: Int = 64,
    color: Int = Color.rgb(180, 150, 130)
): Bitmap {
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        eraseColor(color)
    }
}

fun ComposeTestRule.waitUntilTagExists(tag: String, timeoutMillis: Long = 5_000L) {
    waitUntil(timeoutMillis = timeoutMillis) {
        onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
}

fun ComposeTestRule.waitUntilTagDoesNotExist(tag: String, timeoutMillis: Long = 5_000L) {
    waitUntil(timeoutMillis = timeoutMillis) {
        onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
    }
}
