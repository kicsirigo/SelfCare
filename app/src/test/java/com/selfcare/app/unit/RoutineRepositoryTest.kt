package com.selfcare.app.unit

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.selfcare.app.data.local.SelfCareDatabase
import com.selfcare.app.data.local.entity.ProductEntity
import com.selfcare.app.data.repository.RoutineRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@RunWith(RobolectricTestRunner::class)
class RoutineRepositoryTest {

    private lateinit var database: SelfCareDatabase
    private lateinit var clock: MutableClock
    private lateinit var repository: RoutineRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SelfCareDatabase::class.java
        ).allowMainThreadQueries().build()

        clock = MutableClock(Instant.parse("2026-05-10T08:00:00Z"), ZoneId.of("UTC"))
        repository = RoutineRepository(
            routineDao = database.routineDao(),
            productDao = database.productDao(),
            clock = clock
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun oneTapMorningAndEveningCompletionIncludesMatchingDefaults() = runTest {
        val now = clock.millis()
        val cleanserId = database.productDao().insert(
            ProductEntity(
                name = "Gentle Cleanser",
                routineSlot = RoutineRepository.SLOT_BOTH,
                isDefaultInRoutine = true,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now
            )
        )
        val spfId = database.productDao().insert(
            ProductEntity(
                name = "Sunscreen SPF 50",
                routineSlot = RoutineRepository.PERIOD_MORNING,
                isDefaultInRoutine = true,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now
            )
        )
        val retinoidId = database.productDao().insert(
            ProductEntity(
                name = "Retinol Serum",
                routineSlot = RoutineRepository.PERIOD_EVENING,
                isDefaultInRoutine = true,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now
            )
        )

        val morningLog = repository.completeRoutine(RoutineRepository.PERIOD_MORNING)
        val eveningLog = repository.completeRoutine(RoutineRepository.PERIOD_EVENING)

        assertEquals(listOf(cleanserId, spfId), repository.decodeProductIds(morningLog.productIdsJson))
        assertEquals(listOf(cleanserId, retinoidId), repository.decodeProductIds(eveningLog.productIdsJson))
        assertTrue(morningLog.completedAtEpochMillis > 0L)
        assertTrue(eveningLog.completedAtEpochMillis > 0L)
    }

    @Test
    fun individualProductSkipAndOverrideTogglesCleanly() = runTest {
        val now = clock.millis()
        val cleanserId = database.productDao().insert(
            ProductEntity(
                name = "Cleanser",
                routineSlot = RoutineRepository.PERIOD_MORNING,
                isDefaultInRoutine = true,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now
            )
        )
        val spfId = database.productDao().insert(
            ProductEntity(
                name = "SPF",
                routineSlot = RoutineRepository.PERIOD_MORNING,
                isDefaultInRoutine = true,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now
            )
        )

        // Skip SPF before completing
        val afterSkip = repository.toggleProductOverride(RoutineRepository.PERIOD_MORNING, spfId)
        assertEquals(listOf(cleanserId), repository.decodeProductIds(afterSkip.productIdsJson))
        assertEquals(listOf(spfId), repository.decodeProductIds(afterSkip.skippedProductIdsJson))
        assertEquals(0L, afterSkip.completedAtEpochMillis)

        // Complete routine preserves skip
        val completed = repository.completeRoutine(RoutineRepository.PERIOD_MORNING)
        assertEquals(listOf(cleanserId), repository.decodeProductIds(completed.productIdsJson))
        assertEquals(listOf(spfId), repository.decodeProductIds(completed.skippedProductIdsJson))
        assertTrue(completed.completedAtEpochMillis > 0L)

        // Un-skip SPF restores it
        val restored = repository.toggleProductOverride(RoutineRepository.PERIOD_MORNING, spfId)
        assertEquals(setOf(cleanserId, spfId), repository.decodeProductIds(restored.productIdsJson).toSet())
        assertTrue(repository.decodeProductIds(restored.skippedProductIdsJson).isEmpty())
    }

    @Test
    fun midnightDateRolloverSeparatesDailyLogs() = runTest {
        val day1 = repository.currentEpochDay()
        repository.completeRoutine(RoutineRepository.PERIOD_MORNING)
        assertEquals(1, repository.getLogsForDate(day1).size)

        // Advance past midnight
        clock.advanceBy(1, ChronoUnit.DAYS)
        val day2 = repository.currentEpochDay()
        assertEquals(day1 + 1, day2)
        assertTrue(repository.getLogsForDate(day2).isEmpty())

        repository.completeRoutine(RoutineRepository.PERIOD_MORNING)
        assertEquals(1, repository.getLogsForDate(day1).size)
        assertEquals(1, repository.getLogsForDate(day2).size)
        assertEquals(2, database.routineDao().getAllOnce().size)
    }

    @Test
    fun duplicateLogPreventionPreservesRowIdOnUpsert() = runTest {
        val first = repository.completeRoutine(RoutineRepository.PERIOD_EVENING, notes = "first")
        clock.advanceBy(5, ChronoUnit.MINUTES)
        val second = repository.completeRoutine(RoutineRepository.PERIOD_EVENING, notes = "second")

        assertEquals(first.id, second.id)
        assertFalse(second.completedAtEpochMillis < first.completedAtEpochMillis)
        assertEquals("second", second.notes)
        assertEquals(1, database.routineDao().getAllOnce().size)
    }

    private class MutableClock(
        private var currentInstant: Instant,
        private val zoneId: ZoneId
    ) : Clock() {
        override fun getZone(): ZoneId = zoneId
        override fun withZone(zone: ZoneId): Clock = MutableClock(currentInstant, zone)
        override fun instant(): Instant = currentInstant

        fun advanceBy(amount: Long, unit: ChronoUnit) {
            currentInstant = currentInstant.plus(amount, unit)
        }
    }
}
