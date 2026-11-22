package com.selfcare.app.unit

import android.content.Context
import android.graphics.Bitmap
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.selfcare.app.data.local.SelfCareDatabase
import com.selfcare.app.data.local.SettingsRepository
import com.selfcare.app.data.local.entity.FaceCheckInEntity
import com.selfcare.app.data.local.entity.HygieneLogEntity
import com.selfcare.app.data.local.entity.RoutineLogEntity
import com.selfcare.app.data.repository.FaceCheckInRepository
import com.selfcare.app.data.repository.HygieneRepository
import com.selfcare.app.data.repository.RoutineRepository
import com.selfcare.app.data.repository.TrendDirection
import com.selfcare.app.domain.cv.HaircutTracker
import com.selfcare.app.domain.ml.FaceAnalysisResult
import com.selfcare.app.domain.ml.FaceAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class FaceCheckInRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var database: SelfCareDatabase
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var haircutTracker: HaircutTracker
    private lateinit var clock: Clock
    private lateinit var fakeAnalyzer: FakeFaceAnalyzer
    private lateinit var repository: FaceCheckInRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, FaceCheckInRepository.CHECKINS_DIR_NAME).deleteRecursively()

        database = Room.inMemoryDatabaseBuilder(
            context,
            SelfCareDatabase::class.java
        ).allowMainThreadQueries().build()

        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { tempFolder.newFile("checkin_settings.preferences_pb") }
        )
        settingsRepository = SettingsRepository(dataStore)
        clock = Clock.fixed(Instant.parse("2026-06-15T10:00:00Z"), ZoneId.of("UTC"))
        haircutTracker = HaircutTracker(
            haircutBaselineDao = database.haircutBaselineDao(),
            clock = clock
        )
        fakeAnalyzer = FakeFaceAnalyzer()

        repository = FaceCheckInRepository(
            context = context,
            faceCheckInDao = database.faceCheckInDao(),
            routineDao = database.routineDao(),
            hygieneDao = database.hygieneDao(),
            settingsRepository = settingsRepository,
            faceAnalyzer = fakeAnalyzer,
            haircutTracker = haircutTracker,
            clock = clock
        )
    }

    @After
    fun tearDown() {
        database.close()
        dataStoreScope.cancel()
        File(context.filesDir, FaceCheckInRepository.CHECKINS_DIR_NAME).deleteRecursively()
    }

    @Test
    fun savesPhotoStrictlyInPrivateCheckinsDirWithNomediaAndRespectsAutoDelete() = runTest {
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)

        // Keep photo when auto-delete is disabled
        settingsRepository.setAutoDeleteCheckInPhotos(false)
        val keptResult = repository.performCheckIn(bitmap, notes = "Morning")
        val checkInsDir = repository.getCheckInsDirectory()
        assertTrue(File(checkInsDir, FaceCheckInRepository.NOMEDIA_FILE_NAME).exists())
        assertNotNull(keptResult.checkIn.photoPath)
        val keptFile = File(keptResult.checkIn.photoPath!!)
        assertTrue(keptFile.exists())
        assertEquals(checkInsDir.canonicalPath, keptFile.parentFile?.canonicalPath)

        // Delete photo immediately after extraction when auto-delete is enabled
        settingsRepository.setAutoDeleteCheckInPhotos(true)
        val deletedResult = repository.performCheckIn(bitmap, notes = "Private")
        assertNull(deletedResult.checkIn.photoPath)
        val remainingJpgs = checkInsDir.listFiles { file -> file.extension == "jpg" }.orEmpty()
        assertEquals(1, remainingJpgs.size)
        assertTrue(keptFile.exists())
        assertEquals(keptFile.canonicalPath, remainingJpgs.single().canonicalPath)
    }

    @Test
    fun computesRolling7dAnd30dBaselinesAndAdherenceCorrelation() = runTest {
        val today = LocalDate.now(clock).toEpochDay()
        val checkInDao = database.faceCheckInDao()
        val routineDao = database.routineDao()
        val hygieneDao = database.hygieneDao()

        // Older 30-day window (days -20, -15): low adherence + higher redness/underEye/lipDryness
        for (offset in listOf(20L, 15L)) {
            val day = today - offset
            checkInDao.insert(
                FaceCheckInEntity(
                    dateEpochDay = day,
                    timestampEpochMillis = day * 86_400_000L,
                    underEyeDeltaLab = 18f,
                    skinRednessIndex = 0.45f,
                    skinTextureVariance = 10f,
                    lipDrynessScore = 0.50f,
                    hairToFaceRatio = 0.35f,
                    lightingQualityScore = 0.90f
                )
            )
        }

        // Recent 7-day window (days -2, 0): high routine + hygiene adherence + improved metrics
        for (offset in listOf(2L, 0L)) {
            val day = today - offset
            routineDao.upsert(
                RoutineLogEntity(
                    dateEpochDay = day,
                    period = RoutineRepository.PERIOD_MORNING,
                    completedAtEpochMillis = 1_000L
                )
            )
            routineDao.upsert(
                RoutineLogEntity(
                    dateEpochDay = day,
                    period = RoutineRepository.PERIOD_EVENING,
                    completedAtEpochMillis = 2_000L
                )
            )
            for (habitKey in HygieneRepository.BUILT_IN_HABIT_KEYS) {
                hygieneDao.upsert(
                    HygieneLogEntity(
                        dateEpochDay = day,
                        habitKey = habitKey,
                        label = habitKey,
                        completed = true,
                        completedAtEpochMillis = 3_000L
                    )
                )
            }
            checkInDao.insert(
                FaceCheckInEntity(
                    dateEpochDay = day,
                    timestampEpochMillis = day * 86_400_000L + 1_000L,
                    underEyeDeltaLab = 8f,
                    skinRednessIndex = 0.15f,
                    skinTextureVariance = 4f,
                    lipDrynessScore = 0.15f,
                    hairToFaceRatio = 0.40f,
                    lightingQualityScore = 0.92f
                )
            )
        }

        val summary = repository.computePersonalTrends()

        assertEquals(4, summary.totalCheckIns)
        assertEquals(2, summary.checkInsLast7Days)
        assertEquals(4, summary.checkInsLast30Days)

        assertEquals(0.15f, summary.rednessTrend.rolling7DayAvg ?: -1f, 0.001f)
        assertEquals(0.30f, summary.rednessTrend.rolling30DayAvg ?: -1f, 0.001f)
        assertEquals(TrendDirection.BETTER, summary.rednessTrend.direction)
        assertEquals(TrendDirection.BETTER, summary.underEyeTrend.direction)
        assertEquals(TrendDirection.BETTER, summary.lipDrynessTrend.direction)
        assertEquals(TrendDirection.BETTER, summary.textureTrend.direction)

        assertEquals(
            FaceCheckInRepository.INSIGHT_BETTER_ON_ADHERENT_DAYS,
            summary.adherenceCorrelation.insightCode
        )
        assertEquals(0.15f, summary.adherenceCorrelation.highAdherenceAvgRedness ?: -1f, 0.001f)
        assertEquals(0.45f, summary.adherenceCorrelation.lowAdherenceAvgRedness ?: -1f, 0.001f)
        assertEquals(4, summary.adherenceCorrelation.pairedDaysCount)
    }

    private class FakeFaceAnalyzer : FaceAnalyzer {
        override suspend fun analyze(bitmap: Bitmap): FaceAnalysisResult = FaceAnalysisResult(
            underEyeDeltaLab = 10f,
            skinRednessIndex = 0.20f,
            skinTextureVariance = 3.5f,
            lipDrynessScore = 0.18f,
            hairToFaceRatio = 0.42f,
            lightingQualityScore = 0.90f,
            isLightingAcceptable = true,
            lightingWarningReason = null
        )
    }
}
