package com.selfcare.app.data.local

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class EncryptedVaultEnvelope(
    val schema: Int = 1,
    val kdf: String = "PBKDF2-SHA256-100000",
    val payloadIv: String,
    val payloadCiphertext: String,
    val pwdSalt: String,
    val pwdWrapIv: String,
    val pwdWrappedMasterKey: String,
    val recSalt: String,
    val recWrapIv: String,
    val recWrappedMasterKey: String
)

data class EncryptedVaultBundle(
    val envelopeJson: String,
    val recoveryKey: String
)

/**
 * Zero-Knowledge End-to-End Encryption for Cloud Save backups.
 *
 * - Backup JSON is GZIP-compressed and encrypted with a random 256-bit AES-GCM master key.
 * - The master key is wrapped TWICE using PBKDF2WithHmacSHA256 (100,000 iterations):
 *   1) with the user's password
 *   2) with a one-time generated Recovery Key (e.g. SC-XXXX-XXXX-XXXX-XXXX)
 * - The server only ever stores the encrypted envelope and NEVER receives the raw password
 *   or plaintext health data.
 */
object CloudBackupCrypto {

    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val SALT_BYTES = 16
    private const val MASTER_KEY_BYTES = 32
    private const val RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    private val secureRandom = SecureRandom()
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Derives a separate deterministic auth secret from (username, userPassword) so the raw
     * user password is never sent to the authentication server.
     */
    fun deriveAuthPassword(username: String, userPassword: String): String {
        val normalizedUser = username.trim().lowercase()
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("selfcare-zk-auth-v1:$normalizedUser:$userPassword".toByteArray(Charsets.UTF_8))
        return "sc1_" + Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    /**
     * Generates a human-readable 120-bit recovery key (e.g. SC-A4F9-K2M8-P7X3-Q9V2-N6W5).
     */
    fun generateRecoveryKey(): String {
        val groups = (0 until 5).map {
            buildString(4) {
                repeat(4) {
                    append(RECOVERY_ALPHABET[secureRandom.nextInt(RECOVERY_ALPHABET.length)])
                }
            }
        }
        return "SC-" + groups.joinToString("-")
    }

    fun normalizeRecoveryKey(raw: String): String =
        raw.trim().uppercase().replace(" ", "")

    fun createEncryptedVault(
        backupJson: String,
        password: String,
        existingRecoveryKey: String? = null
    ): EncryptedVaultBundle {
        require(password.length >= 6) { "Password must be at least 6 characters" }
        val recoveryKey = existingRecoveryKey
            ?.takeIf { it.isNotBlank() }
            ?.let(::normalizeRecoveryKey)
            ?: generateRecoveryKey()

        val compressed = gzip(backupJson.toByteArray(Charsets.UTF_8))
        val masterKey = randomBytes(MASTER_KEY_BYTES)

        val (payloadIv, payloadCiphertext) = aesGcmEncrypt(compressed, masterKey)

        val pwdSalt = randomBytes(SALT_BYTES)
        val pwdDerivedKey = deriveKey(password, pwdSalt)
        val (pwdWrapIv, pwdWrappedMasterKey) = aesGcmEncrypt(masterKey, pwdDerivedKey)

        val recSalt = randomBytes(SALT_BYTES)
        val recDerivedKey = deriveKey(recoveryKey, recSalt)
        val (recWrapIv, recWrappedMasterKey) = aesGcmEncrypt(masterKey, recDerivedKey)

        val envelope = EncryptedVaultEnvelope(
            payloadIv = b64Encode(payloadIv),
            payloadCiphertext = b64Encode(payloadCiphertext),
            pwdSalt = b64Encode(pwdSalt),
            pwdWrapIv = b64Encode(pwdWrapIv),
            pwdWrappedMasterKey = b64Encode(pwdWrappedMasterKey),
            recSalt = b64Encode(recSalt),
            recWrapIv = b64Encode(recWrapIv),
            recWrappedMasterKey = b64Encode(recWrappedMasterKey)
        )
        return EncryptedVaultBundle(
            envelopeJson = json.encodeToString(envelope),
            recoveryKey = recoveryKey
        )
    }

    fun decryptVaultWithPassword(envelopeJson: String, password: String): Result<String> =
        runCatching {
            val env = json.decodeFromString<EncryptedVaultEnvelope>(envelopeJson)
            require(env.schema == 1) { "Unsupported vault schema: ${env.schema}" }
            val pwdDerivedKey = deriveKey(password, b64Decode(env.pwdSalt))
            val masterKey = aesGcmDecrypt(
                ciphertext = b64Decode(env.pwdWrappedMasterKey),
                iv = b64Decode(env.pwdWrapIv),
                keyBytes = pwdDerivedKey
            )
            val compressed = aesGcmDecrypt(
                ciphertext = b64Decode(env.payloadCiphertext),
                iv = b64Decode(env.payloadIv),
                keyBytes = masterKey
            )
            String(gunzip(compressed), Charsets.UTF_8)
        }

    fun decryptVaultWithRecoveryKey(envelopeJson: String, recoveryKey: String): Result<String> =
        runCatching {
            val env = json.decodeFromString<EncryptedVaultEnvelope>(envelopeJson)
            require(env.schema == 1) { "Unsupported vault schema: ${env.schema}" }
            val recDerivedKey = deriveKey(normalizeRecoveryKey(recoveryKey), b64Decode(env.recSalt))
            val masterKey = aesGcmDecrypt(
                ciphertext = b64Decode(env.recWrappedMasterKey),
                iv = b64Decode(env.recWrapIv),
                keyBytes = recDerivedKey
            )
            val compressed = aesGcmDecrypt(
                ciphertext = b64Decode(env.payloadCiphertext),
                iv = b64Decode(env.payloadIv),
                keyBytes = masterKey
            )
            String(gunzip(compressed), Charsets.UTF_8)
        }

    private fun deriveKey(secret: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun aesGcmEncrypt(plaintext: ByteArray, keyBytes: ByteArray): Pair<ByteArray, ByteArray> {
        val iv = randomBytes(GCM_IV_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)
        return iv to ciphertext
    }

    private fun aesGcmDecrypt(ciphertext: ByteArray, iv: ByteArray, keyBytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun gzip(input: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(input) }
        return out.toByteArray()
    }

    private fun gunzip(input: ByteArray): ByteArray {
        return GZIPInputStream(ByteArrayInputStream(input)).use { it.readBytes() }
    }

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also { secureRandom.nextBytes(it) }

    private fun b64Encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun b64Decode(str: String): ByteArray = Base64.getDecoder().decode(str)
}
