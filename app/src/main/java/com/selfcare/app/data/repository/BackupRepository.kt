package com.selfcare.app.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.selfcare.app.data.local.AppLanguage
import com.selfcare.app.data.local.SelfCareDatabase
import com.selfcare.app.data.local.SettingsRepository
import com.selfcare.app.data.local.UserSettings
import com.selfcare.app.data.local.entity.FaceCheckInEntity
import com.selfcare.app.data.local.entity.HaircutBaselineEntity
import com.selfcare.app.data.local.entity.HygieneLogEntity
import com.selfcare.app.data.local.entity.ProductEntity
import com.selfcare.app.data.local.entity.RoutineLogEntity
import com.selfcare.app.ui.theme.ThemeMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.time.Clock

@Serializable
data class BackupSettings(
    val themeMode: String = ThemeMode.DARK.name,
    val appLanguage: String = AppLanguage.AUTO.name,
    val morningReminderEnabled: Boolean = false,
    val morningReminderHour: Int = 8,
    val morningReminderMinute: Int = 0,
    val eveningReminderEnabled: Boolean = false,
    val eveningReminderHour: Int = 21,
    val eveningReminderMinute: Int = 0,
    val autoDeleteCheckInPhotos: Boolean = false,
    val haircutThresholdPercent: Int = UserSettings.DEFAULT_HAIRCUT_THRESHOLD_PERCENT
)

@Serializable
data class BackupRoutineLog(
    val id: Long = 0L,
    val dateEpochDay: Long,
    val period: String,
    val productIdsJson: String = "[]",
    val skippedProductIdsJson: String = "[]",
    val completedAtEpochMillis: Long,
    val notes: String = ""
)

@Serializable
data class BackupProduct(
    val id: Long = 0L,
    val name: String,
    val brand: String = "",
    val barcode: String? = null,
    val category: String = "OTHER",
    val routineSlot: String = "NONE",
    val isDefaultInRoutine: Boolean = false,
    val rawIngredientsText: String = "",
    val matchedInciJson: String = "[]",
    val source: String = "MANUAL",
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

@Serializable
data class BackupFaceCheckIn(
    val id: Long = 0L,
    val dateEpochDay: Long,
    val timestampEpochMillis: Long,
    val underEyeDeltaLab: Float,
    val skinRednessIndex: Float,
    val skinTextureVariance: Float,
    val lipDrynessScore: Float,
    val hairToFaceRatio: Float,
    val lightingQualityScore: Float,
    val photoPath: String? = null,
    val notes: String = "",
    val blemishScore: Float = 0f,
    val blemishCount: Int = 0,
    val stubbleScore: Float = 0f
)

@Serializable
data class BackupHygieneLog(
    val id: Long = 0L,
    val dateEpochDay: Long,
    val habitKey: String,
    val label: String,
    val isCustom: Boolean = false,
    val completed: Boolean = true,
    val completedAtEpochMillis: Long
)

@Serializable
data class BackupHaircutBaseline(
    val id: Long = 0L,
    val baselineRatio: Float,
    val growthThresholdPercent: Float = 15f,
    val recordedAtEpochMillis: Long,
    val isActive: Boolean = true
)

@Serializable
data class SelfCareBackupPayload(
    val schemaVersion: Int = BackupRepository.CURRENT_SCHEMA_VERSION,
    val exportedAtEpochMillis: Long,
    val settings: BackupSettings = BackupSettings(),
    val routineLogs: List<BackupRoutineLog> = emptyList(),
    val products: List<BackupProduct> = emptyList(),
    val faceCheckIns: List<BackupFaceCheckIn> = emptyList(),
    val hygieneLogs: List<BackupHygieneLog> = emptyList(),
    val haircutBaselines: List<BackupHaircutBaseline> = emptyList()
)

data class ImportSummary(
    val schemaVersion: Int,
    val routineLogsCount: Int,
    val productsCount: Int,
    val faceCheckInsCount: Int,
    val hygieneLogsCount: Int,
    val haircutBaselinesCount: Int
)

class BackupRepository(
    private val database: SelfCareDatabase,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val checkInsDir: File? = null
) {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    suspend fun exportToJson(): String {
        val currentSettings = settingsRepository.getSettingsOnce()
        val routineLogs = database.routineDao().getAllOnce().map { it.toBackup() }
        val products = database.productDao().getAllOnce().map { it.toBackup() }
        val faceCheckIns = database.faceCheckInDao().getAllOnce().map { it.toBackup() }
        val hygieneLogs = database.hygieneDao().getAllOnce().map { it.toBackup() }
        val haircutBaselines = database.haircutBaselineDao().getAllOnce().map { it.toBackup() }

        val payload = SelfCareBackupPayload(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            exportedAtEpochMillis = clock.millis(),
            settings = currentSettings.toBackup(),
            routineLogs = routineLogs,
            products = products,
            faceCheckIns = faceCheckIns,
            hygieneLogs = hygieneLogs,
            haircutBaselines = haircutBaselines
        )
        return json.encodeToString(payload)
    }

    suspend fun importFromJson(rawJson: String): Result<ImportSummary> {
        if (rawJson.isBlank()) {
            return Result.failure(IllegalArgumentException("Backup JSON is empty"))
        }
        if (rawJson.toByteArray(Charsets.UTF_8).size > MAX_BACKUP_BYTES) {
            return Result.failure(IllegalArgumentException("Backup JSON exceeds maximum allowed size"))
        }

        val payload = runCatching {
            json.decodeFromString<SelfCareBackupPayload>(rawJson)
        }.getOrElse { error ->
            return Result.failure(IllegalArgumentException("Malformed backup JSON", error))
        }

        if (payload.schemaVersion != CURRENT_SCHEMA_VERSION) {
            return Result.failure(
                IllegalArgumentException("Unsupported backup schemaVersion: ${payload.schemaVersion}")
            )
        }

        return runCatching {
            database.withTransaction {
                database.routineDao().deleteAll()
                database.productDao().deleteAll()
                database.faceCheckInDao().deleteAll()
                database.hygieneDao().deleteAll()
                database.haircutBaselineDao().deleteAll()

                payload.routineLogs.forEach { database.routineDao().upsert(it.toEntity()) }
                payload.products.forEach { database.productDao().insert(it.toEntity()) }
                payload.faceCheckIns.forEach {
                    database.faceCheckInDao().insert(it.toEntity(sanitizePhotoPath(it.photoPath, checkInsDir)))
                }
                payload.hygieneLogs.forEach { database.hygieneDao().upsert(it.toEntity()) }
                payload.haircutBaselines.forEach {
                    database.haircutBaselineDao().insert(it.toEntity())
                }
            }

            settingsRepository.replaceAll(payload.settings.toUserSettings())

            ImportSummary(
                schemaVersion = payload.schemaVersion,
                routineLogsCount = payload.routineLogs.size,
                productsCount = payload.products.size,
                faceCheckInsCount = payload.faceCheckIns.size,
                hygieneLogsCount = payload.hygieneLogs.size,
                haircutBaselinesCount = payload.haircutBaselines.size
            )
        }
    }

    suspend fun exportToUri(context: Context, uri: Uri): Result<Unit> =
        exportToUri(context.contentResolver, uri)

    suspend fun exportToUri(contentResolver: ContentResolver, uri: Uri): Result<Unit> =
        runCatching {
            val serialized = exportToJson()
            val outputStream = contentResolver.openOutputStream(uri)
                ?: throw IOException("Unable to open output stream for $uri")
            outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(serialized)
            }
        }

    suspend fun importFromUri(context: Context, uri: Uri): Result<ImportSummary> =
        importFromUri(context.contentResolver, uri)

    suspend fun importFromUri(contentResolver: ContentResolver, uri: Uri): Result<ImportSummary> {
        val rawJson = runCatching {
            val inputStream = contentResolver.openInputStream(uri)
                ?: throw IOException("Unable to open input stream for $uri")
            inputStream.use { input ->
                val buffer = ByteArray(8 * 1024)
                val out = ByteArrayOutputStream()
                var totalRead = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    totalRead += read
                    if (totalRead > MAX_BACKUP_BYTES) {
                        throw IOException("Backup file exceeds maximum allowed size ($MAX_BACKUP_BYTES bytes)")
                    }
                    out.write(buffer, 0, read)
                }
                out.toString(Charsets.UTF_8.name())
            }
        }.getOrElse { error ->
            return Result.failure(error)
        }
        return importFromJson(rawJson)
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val MAX_BACKUP_BYTES = 10 * 1024 * 1024

        fun sanitizePhotoPath(rawPath: String?, allowedCheckInsDir: File?): String? {
            if (rawPath.isNullOrBlank() || allowedCheckInsDir == null) return null
            if (rawPath.contains('\u0000')) return null
            return runCatching {
                val baseCanonical = allowedCheckInsDir.canonicalFile
                val targetCanonical = File(rawPath).canonicalFile
                val allowedPrefix = baseCanonical.path + File.separator
                if (targetCanonical.path.startsWith(allowedPrefix) && targetCanonical.parentFile == baseCanonical) {
                    targetCanonical.absolutePath
                } else {
                    null
                }
            }.getOrNull()
        }
    }
}

private fun UserSettings.toBackup(): BackupSettings = BackupSettings(
    themeMode = themeMode.name,
    appLanguage = appLanguage.name,
    morningReminderEnabled = morningReminderEnabled,
    morningReminderHour = morningReminderHour,
    morningReminderMinute = morningReminderMinute,
    eveningReminderEnabled = eveningReminderEnabled,
    eveningReminderHour = eveningReminderHour,
    eveningReminderMinute = eveningReminderMinute,
    autoDeleteCheckInPhotos = autoDeleteCheckInPhotos,
    haircutThresholdPercent = haircutThresholdPercent
)

private fun BackupSettings.toUserSettings(): UserSettings = UserSettings(
    themeMode = ThemeMode.entries.firstOrNull {
        it.name.equals(themeMode, ignoreCase = true)
    } ?: ThemeMode.DARK,
    appLanguage = AppLanguage.fromName(appLanguage),
    morningReminderEnabled = morningReminderEnabled,
    morningReminderHour = morningReminderHour.coerceIn(0, 23),
    morningReminderMinute = morningReminderMinute.coerceIn(0, 59),
    eveningReminderEnabled = eveningReminderEnabled,
    eveningReminderHour = eveningReminderHour.coerceIn(0, 23),
    eveningReminderMinute = eveningReminderMinute.coerceIn(0, 59),
    autoDeleteCheckInPhotos = autoDeleteCheckInPhotos,
    haircutThresholdPercent = haircutThresholdPercent.coerceIn(
        UserSettings.MIN_HAIRCUT_THRESHOLD_PERCENT,
        UserSettings.MAX_HAIRCUT_THRESHOLD_PERCENT
    )
)

private fun RoutineLogEntity.toBackup(): BackupRoutineLog = BackupRoutineLog(
    id = id,
    dateEpochDay = dateEpochDay,
    period = period,
    productIdsJson = productIdsJson,
    skippedProductIdsJson = skippedProductIdsJson,
    completedAtEpochMillis = completedAtEpochMillis,
    notes = notes
)

private fun BackupRoutineLog.toEntity(): RoutineLogEntity = RoutineLogEntity(
    id = id,
    dateEpochDay = dateEpochDay,
    period = period,
    productIdsJson = productIdsJson,
    skippedProductIdsJson = skippedProductIdsJson,
    completedAtEpochMillis = completedAtEpochMillis,
    notes = notes
)

private fun ProductEntity.toBackup(): BackupProduct = BackupProduct(
    id = id,
    name = name,
    brand = brand,
    barcode = barcode,
    category = category,
    routineSlot = routineSlot,
    isDefaultInRoutine = isDefaultInRoutine,
    rawIngredientsText = rawIngredientsText,
    matchedInciJson = matchedInciJson,
    source = source,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis
)

private fun BackupProduct.toEntity(): ProductEntity = ProductEntity(
    id = id,
    name = name,
    brand = brand,
    barcode = barcode,
    category = category,
    routineSlot = routineSlot,
    isDefaultInRoutine = isDefaultInRoutine,
    rawIngredientsText = rawIngredientsText,
    matchedInciJson = matchedInciJson,
    source = source,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis
)

private fun FaceCheckInEntity.toBackup(): BackupFaceCheckIn = BackupFaceCheckIn(
    id = id,
    dateEpochDay = dateEpochDay,
    timestampEpochMillis = timestampEpochMillis,
    underEyeDeltaLab = underEyeDeltaLab,
    skinRednessIndex = skinRednessIndex,
    skinTextureVariance = skinTextureVariance,
    lipDrynessScore = lipDrynessScore,
    hairToFaceRatio = hairToFaceRatio,
    lightingQualityScore = lightingQualityScore,
    photoPath = photoPath,
    notes = notes,
    blemishScore = blemishScore,
    blemishCount = blemishCount,
    stubbleScore = stubbleScore
)

private fun BackupFaceCheckIn.toEntity(sanitizedPhotoPath: String?): FaceCheckInEntity = FaceCheckInEntity(
    id = id,
    dateEpochDay = dateEpochDay,
    timestampEpochMillis = timestampEpochMillis,
    underEyeDeltaLab = underEyeDeltaLab,
    skinRednessIndex = skinRednessIndex,
    skinTextureVariance = skinTextureVariance,
    lipDrynessScore = lipDrynessScore,
    hairToFaceRatio = hairToFaceRatio,
    lightingQualityScore = lightingQualityScore,
    photoPath = sanitizedPhotoPath,
    notes = notes,
    blemishScore = blemishScore,
    blemishCount = blemishCount,
    stubbleScore = stubbleScore
)

private fun HygieneLogEntity.toBackup(): BackupHygieneLog = BackupHygieneLog(
    id = id,
    dateEpochDay = dateEpochDay,
    habitKey = habitKey,
    label = label,
    isCustom = isCustom,
    completed = completed,
    completedAtEpochMillis = completedAtEpochMillis
)

private fun BackupHygieneLog.toEntity(): HygieneLogEntity = HygieneLogEntity(
    id = id,
    dateEpochDay = dateEpochDay,
    habitKey = habitKey,
    label = label,
    isCustom = isCustom,
    completed = completed,
    completedAtEpochMillis = completedAtEpochMillis
)

private fun HaircutBaselineEntity.toBackup(): BackupHaircutBaseline = BackupHaircutBaseline(
    id = id,
    baselineRatio = baselineRatio,
    growthThresholdPercent = growthThresholdPercent,
    recordedAtEpochMillis = recordedAtEpochMillis,
    isActive = isActive
)

private fun BackupHaircutBaseline.toEntity(): HaircutBaselineEntity = HaircutBaselineEntity(
    id = id,
    baselineRatio = baselineRatio,
    growthThresholdPercent = growthThresholdPercent,
    recordedAtEpochMillis = recordedAtEpochMillis,
    isActive = isActive
)
