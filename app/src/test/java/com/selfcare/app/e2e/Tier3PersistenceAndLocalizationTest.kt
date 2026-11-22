package com.selfcare.app.e2e

import android.net.Uri
import com.selfcare.app.data.local.AppLanguage
import com.selfcare.app.data.local.entity.FaceCheckInEntity
import com.selfcare.app.data.local.entity.HaircutBaselineEntity
import com.selfcare.app.data.local.entity.HygieneLogEntity
import com.selfcare.app.data.local.entity.ProductEntity
import com.selfcare.app.data.local.entity.RoutineLogEntity
import com.selfcare.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Tier3PersistenceAndLocalizationTest {

    @get:Rule
    val e2eRule = SelfCareE2ERule()

    private fun resolveProjectRoot(): File {
        val cwd = File(System.getProperty("user.dir") ?: ".").canonicalFile
        return when {
            File(cwd, "app/src/main/AndroidManifest.xml").exists() -> cwd
            File(cwd.parentFile, "app/src/main/AndroidManifest.xml").exists() -> cwd.parentFile
            else -> cwd
        }
    }

    @Test
    fun roomDb_roundtripAcrossAllFiveEntities() {
        runBlocking {
        val now = e2eRule.clock.millis()
        val epochDay = 20779L

        val productId = e2eRule.database.productDao().insert(
            ProductEntity(
                name = "Hydrating Cleanser",
                brand = "SimpleCare",
                barcode = "1234567890123",
                routineSlot = "MORNING",
                isDefaultInRoutine = true,
                rawIngredientsText = "Aqua, Glycerin",
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now
            )
        )
        val routineId = e2eRule.database.routineDao().upsert(
            RoutineLogEntity(
                dateEpochDay = epochDay,
                period = "MORNING",
                productIdsJson = "[$productId]",
                skippedProductIdsJson = "[]",
                completedAtEpochMillis = now
            )
        )
        val checkInId = e2eRule.database.faceCheckInDao().insert(
            FaceCheckInEntity(
                dateEpochDay = epochDay,
                timestampEpochMillis = now,
                underEyeDeltaLab = 11.5f,
                skinRednessIndex = 0.22f,
                skinTextureVariance = 14.0f,
                lipDrynessScore = 0.28f,
                hairToFaceRatio = 0.41f,
                lightingQualityScore = 0.92f
            )
        )
        val hygieneId = e2eRule.database.hygieneDao().upsert(
            HygieneLogEntity(
                dateEpochDay = epochDay,
                habitKey = "SHOWER",
                label = "Shower",
                isCustom = false,
                completed = true,
                completedAtEpochMillis = now
            )
        )
        val baselineId = e2eRule.database.haircutBaselineDao().insert(
            HaircutBaselineEntity(
                baselineRatio = 0.41f,
                growthThresholdPercent = 15f,
                recordedAtEpochMillis = now,
                isActive = true
            )
        )

        assertTrue(productId > 0L)
        assertTrue(routineId > 0L)
        assertTrue(checkInId > 0L)
        assertTrue(hygieneId > 0L)
        assertTrue(baselineId > 0L)

        assertEquals(1, e2eRule.database.productDao().observeAll().first().size)
        assertEquals(1, e2eRule.database.routineDao().observeByDate(epochDay).first().size)
        assertEquals(1, e2eRule.database.faceCheckInDao().observeRecent(10).first().size)
        assertEquals(1, e2eRule.database.hygieneDao().observeByDate(epochDay).first().size)
        assertNotNull(e2eRule.database.haircutBaselineDao().getActiveBaseline())
        }
    }

    @Test
    fun backupRepository_safUriExportAndImportRoundtrip() {
        runBlocking {
        val now = e2eRule.clock.millis()
        val epochDay = 20779L

        e2eRule.settingsRepository.setThemeMode(ThemeMode.AMOLED)
        e2eRule.settingsRepository.setAppLanguage(AppLanguage.HU)
        e2eRule.settingsRepository.setAutoDeleteCheckInPhotos(true)

        e2eRule.database.productDao().insert(
            ProductEntity(
                name = "Night Retinol",
                brand = "DermLab",
                routineSlot = "EVENING",
                isDefaultInRoutine = true,
                rawIngredientsText = "Aqua, Retinol",
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now
            )
        )
        e2eRule.database.routineDao().upsert(
            RoutineLogEntity(
                dateEpochDay = epochDay,
                period = "EVENING",
                productIdsJson = "[1]",
                completedAtEpochMillis = now
            )
        )
        e2eRule.database.hygieneDao().upsert(
            HygieneLogEntity(
                dateEpochDay = epochDay,
                habitKey = "TEETH_PM",
                label = "Evening Teeth",
                completedAtEpochMillis = now
            )
        )
        e2eRule.database.faceCheckInDao().insert(
            FaceCheckInEntity(
                dateEpochDay = epochDay,
                timestampEpochMillis = now,
                underEyeDeltaLab = 12f,
                skinRednessIndex = 0.25f,
                skinTextureVariance = 15f,
                lipDrynessScore = 0.30f,
                hairToFaceRatio = 0.42f,
                lightingQualityScore = 0.90f
            )
        )
        e2eRule.database.haircutBaselineDao().insert(
            HaircutBaselineEntity(
                baselineRatio = 0.42f,
                growthThresholdPercent = 15f,
                recordedAtEpochMillis = now,
                isActive = true
            )
        )

        val backupFile = File(e2eRule.app.cacheDir, "e2e_backup_roundtrip.json")
        val backupUri = Uri.fromFile(backupFile)
        val exportResult = e2eRule.container.backupRepository.exportToUri(e2eRule.app, backupUri)
        assertTrue(exportResult.isSuccess)
        assertTrue(backupFile.exists() && backupFile.length() > 0L)

        // Wipe DB and reset settings
        e2eRule.database.clearAllTables()
        e2eRule.settingsRepository.setThemeMode(ThemeMode.LIGHT)
        e2eRule.settingsRepository.setAppLanguage(AppLanguage.EN)
        e2eRule.settingsRepository.setAutoDeleteCheckInPhotos(false)

        val importResult = e2eRule.container.backupRepository.importFromUri(e2eRule.app, backupUri)
        assertTrue(importResult.isSuccess)
        val summary = importResult.getOrThrow()
        assertEquals(1, summary.productsCount)
        assertEquals(1, summary.routineLogsCount)
        assertEquals(1, summary.hygieneLogsCount)
        assertEquals(1, summary.faceCheckInsCount)
        assertEquals(1, summary.haircutBaselinesCount)

        val restoredSettings = e2eRule.settingsRepository.settings.first()
        assertEquals(ThemeMode.AMOLED, restoredSettings.themeMode)
        assertEquals(AppLanguage.HU, restoredSettings.appLanguage)
        assertTrue(restoredSettings.autoDeleteCheckInPhotos)

        backupFile.delete()
        }
    }

    @Test
    fun privacyAndConfigChecks_manifestGitignoreAndZeroTelemetry() {
        val root = resolveProjectRoot()

        val gitignore = File(root, ".gitignore").readText()
        val gitignoreLines = gitignore.lines().map { it.trim() }
        assertTrue(gitignoreLines.contains(".env"))
        assertTrue(gitignoreLines.contains(".env.*"))
        assertTrue(gitignoreLines.contains("!.env.example"))

        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
        assertTrue(manifest.contains("android.permission.CAMERA"))
        assertTrue(manifest.contains("android.permission.POST_NOTIFICATIONS"))
        assertTrue(manifest.contains("android.permission.INTERNET"))
        assertFalse(manifest.contains("ACCESS_FINE_LOCATION"))
        assertFalse(manifest.contains("READ_CONTACTS"))

        val buildGradle = File(root, "app/build.gradle.kts").readText().lowercase()
        val versionsToml = File(root, "gradle/libs.versions.toml").readText().lowercase()
        val forbiddenSdks = listOf("firebase", "crashlytics", "analytics", "mixpanel", "amplitude", "admob", "ads")
        forbiddenSdks.forEach { sdk ->
            assertFalse("Forbidden SDK '$sdk' in app/build.gradle.kts", buildGradle.contains(sdk))
            assertFalse("Forbidden SDK '$sdk' in libs.versions.toml", versionsToml.contains(sdk))
        }
    }

    @Test
    fun localizationParity_enAndHuKeysMatchAndZeroUnicodeEmojisInMain() {
        val root = resolveProjectRoot()
        val enXml = File(root, "app/src/main/res/values/strings.xml")
        val huXml = File(root, "app/src/main/res/values-hu/strings.xml")

        assertTrue(enXml.exists())
        assertTrue(huXml.exists())

        val enKeys = parseStringKeys(enXml)
        val huKeys = parseStringKeys(huXml)
        assertTrue(enKeys.isNotEmpty())
        assertEquals("EN and HU strings.xml keys must have 1:1 parity", enKeys, huKeys)

        val mainDir = File(root, "app/src/main")
        val emojiFiles = mutableListOf<String>()
        mainDir.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }
            .forEach { file ->
                val content = file.readText()
                if (containsUnicodeEmoji(content)) {
                    emojiFiles.add(file.relativeTo(root).path)
                }
            }
        assertTrue("Found Unicode emojis in app/src/main: $emojiFiles", emojiFiles.isEmpty())
    }

    private fun parseStringKeys(xmlFile: File): Set<String> {
        val factory = DocumentBuilderFactory.newInstance()
        val doc = factory.newDocumentBuilder().parse(xmlFile)
        val nodes = doc.getElementsByTagName("string")
        val keys = linkedSetOf<String>()
        for (i in 0 until nodes.length) {
            val element = nodes.item(i) as? Element ?: continue
            val name = element.getAttribute("name")
            if (name.isNotBlank()) {
                keys.add(name)
            }
        }
        return keys
    }

    private fun containsUnicodeEmoji(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val isEmojiRange =
                (cp in 0x1F300..0x1FAFF) ||
                    (cp in 0x2600..0x27BF) ||
                    cp == 0xFE0F
            if (isEmojiRange) return true
            i += Character.charCount(cp)
        }
        return false
    }
}
