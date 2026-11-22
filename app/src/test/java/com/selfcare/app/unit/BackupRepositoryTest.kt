package com.selfcare.app.unit

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.selfcare.app.data.local.AppLanguage
import com.selfcare.app.data.local.SelfCareDatabase
import com.selfcare.app.data.local.SettingsRepository
import com.selfcare.app.data.local.UserSettings
import com.selfcare.app.data.local.entity.FaceCheckInEntity
import com.selfcare.app.data.local.entity.HaircutBaselineEntity
import com.selfcare.app.data.local.entity.HygieneLogEntity
import com.selfcare.app.data.local.entity.ProductEntity
import com.selfcare.app.data.local.entity.RoutineLogEntity
import com.selfcare.app.data.repository.BackupRepository
import com.selfcare.app.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class BackupRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var database: SelfCareDatabase
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var backupRepository: BackupRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SelfCareDatabase::class.java
        ).allowMainThreadQueries().build()

        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { tempFolder.newFile("test_settings.preferences_pb") }
        )
        settingsRepository = SettingsRepository(dataStore)
        backupRepository = BackupRepository(
            database = database,
            settingsRepository = settingsRepository,
            clock = Clock.fixed(Instant.parse("2026-05-10T12:00:00Z"), ZoneId.of("UTC"))
        )
    }

    @After
    fun tearDown() {
        database.close()
        dataStoreScope.cancel()
    }

    @Test
    fun exportAndImportRoundtripRestoresAllFiveEntitiesAndSettings() = runTest {
        val expectedSettings = UserSettings(
            themeMode = ThemeMode.AMOLED,
            appLanguage = AppLanguage.HU,
            morningReminderEnabled = true,
            morningReminderHour = 7,
            morningReminderMinute = 30,
            eveningReminderEnabled = true,
            eveningReminderHour = 22,
            eveningReminderMinute = 15,
            autoDeleteCheckInPhotos = true,
            haircutThresholdPercent = 25
        )
        settingsRepository.replaceAll(expectedSettings)

        val product = ProductEntity(
            id = 10L,
            name = "Hydrating Serum",
            brand = "SkinLab",
            barcode = "1234567890123",
            category = "SERUM",
            routineSlot = "BOTH",
            isDefaultInRoutine = true,
            rawIngredientsText = "Aqua, Glycerin, Niacinamide",
            matchedInciJson = """["Niacinamide"]""",
            source = "MANUAL",
            createdAtEpochMillis = 1_000L,
            updatedAtEpochMillis = 2_000L
        )
        val routineLog = RoutineLogEntity(
            id = 20L,
            dateEpochDay = 20_583L,
            period = "MORNING",
            productIdsJson = "[10]",
            skippedProductIdsJson = "[]",
            completedAtEpochMillis = 3_000L,
            notes = "Felt great"
        )
        val faceCheckIn = FaceCheckInEntity(
            id = 30L,
            dateEpochDay = 20_583L,
            timestampEpochMillis = 4_000L,
            underEyeDeltaLab = 12.5f,
            skinRednessIndex = 0.18f,
            skinTextureVariance = 4.2f,
            lipDrynessScore = 0.25f,
            hairToFaceRatio = 0.42f,
            lightingQualityScore = 0.91f,
            photoPath = null,
            notes = "Good daylight"
        )
        val hygieneLog = HygieneLogEntity(
            id = 40L,
            dateEpochDay = 20_583L,
            habitKey = "SHOWER",
            label = "Shower or Bath",
            isCustom = false,
            completed = true,
            completedAtEpochMillis = 5_000L
        )
        val haircutBaseline = HaircutBaselineEntity(
            id = 50L,
            baselineRatio = 0.35f,
            growthThresholdPercent = 25f,
            recordedAtEpochMillis = 6_000L,
            isActive = true
        )

        database.productDao().insert(product)
        database.routineDao().upsert(routineLog)
        database.faceCheckInDao().insert(faceCheckIn)
        database.hygieneDao().upsert(hygieneLog)
        database.haircutBaselineDao().insert(haircutBaseline)

        val exportedJson = backupRepository.exportToJson()

        // Wipe database and reset settings
        database.routineDao().deleteAll()
        database.productDao().deleteAll()
        database.faceCheckInDao().deleteAll()
        database.hygieneDao().deleteAll()
        database.haircutBaselineDao().deleteAll()
        settingsRepository.replaceAll(UserSettings())

        val result = backupRepository.importFromJson(exportedJson)
        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()
        assertEquals(1, summary.routineLogsCount)
        assertEquals(1, summary.productsCount)
        assertEquals(1, summary.faceCheckInsCount)
        assertEquals(1, summary.hygieneLogsCount)
        assertEquals(1, summary.haircutBaselinesCount)

        assertEquals(listOf(product), database.productDao().getAllOnce())
        assertEquals(listOf(routineLog), database.routineDao().getAllOnce())
        assertEquals(listOf(faceCheckIn), database.faceCheckInDao().getAllOnce())
        assertEquals(listOf(hygieneLog), database.hygieneDao().getAllOnce())
        assertEquals(listOf(haircutBaseline), database.haircutBaselineDao().getAllOnce())
        assertEquals(expectedSettings, settingsRepository.getSettingsOnce())
    }

    @Test
    fun importFromInvalidJsonReturnsFailure() = runTest {
        assertTrue(backupRepository.importFromJson("").isFailure)
        assertTrue(backupRepository.importFromJson("{not-valid-json").isFailure)
    }
}
