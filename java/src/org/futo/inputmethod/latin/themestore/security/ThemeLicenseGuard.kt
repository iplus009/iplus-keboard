package org.futo.inputmethod.latin.themestore.security

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import kotlinx.serialization.json.Json
import org.futo.inputmethod.latin.themestore.model.EntitlementReceiptPayload
import org.futo.inputmethod.latin.themestore.model.RevocationEntry
import org.futo.inputmethod.latin.themestore.model.RevocationPayloadOnly
import org.futo.inputmethod.latin.themestore.model.SignedEntitlementReceipt
import org.futo.inputmethod.latin.themestore.model.SignedRevocationList
import java.security.MessageDigest

/**
 * Verifies Ed25519 receipts, enforces strict app_id + device_hash isolation,
 * and checks offline revocations before any paid theme is unlocked or rendered.
 */
object ThemeLicenseGuard {
    const val APP_ID = "keybird"

    private val json = Json { ignoreUnknownKeys = true }

    @SuppressLint("HardwareIds")
    fun computeDeviceHash(context: Context): String {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty()
        val raw = "$APP_ID:${context.packageName}:$androidId"
        return sha256Hex(raw)
    }

    fun sha256Hex(input: String): String =
        sha256Hex(input.toByteArray(Charsets.UTF_8))

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun verifyReceipt(
        receipt: SignedEntitlementReceipt,
        expectedDeviceHash: String,
        revokedTargetHashes: Set<String> = emptySet(),
        publicKey32: ByteArray = ThemeNativeGuard.trustedEd25519PublicKeyBytes(),
        nowEpochSec: Long = System.currentTimeMillis() / 1000L
    ): EntitlementReceiptPayload? {
        val msgBytes = decodeBase64Url(receipt.payloadB64) ?: return null
        val sigBytes = decodeBase64Url(receipt.signatureB64) ?: return null
        if (!Ed25519Verifier.verify(publicKey32, msgBytes, sigBytes)) {
            return null
        }

        val payload = runCatching {
            json.decodeFromString<EntitlementReceiptPayload>(msgBytes.toString(Charsets.UTF_8))
        }.getOrNull() ?: return null

        // Prevent cross-app receipt reuse (LemonSqueezy-class vulnerability)
        if (payload.appId != APP_ID && payload.appId != "*") return null
        if (payload.deviceHash != expectedDeviceHash) return null
        if (nowEpochSec > payload.issuedAt + payload.ttlDays.toLong() * 86400L) return null

        val keyHash = sha256Hex(payload.keyId)
        if (revokedTargetHashes.contains(keyHash) || revokedTargetHashes.contains(expectedDeviceHash)) {
            return null
        }

        return payload
    }

    fun verifyCoversTheme(payload: EntitlementReceiptPayload, themeId: String): Boolean {
        return payload.themeIds.contains("*") || payload.themeIds.contains(themeId)
    }

    fun verifyRevocationList(
        list: SignedRevocationList,
        publicKey32: ByteArray = ThemeNativeGuard.trustedEd25519PublicKeyBytes()
    ): List<RevocationEntry>? {
        val msgBytes = decodeBase64Url(list.payloadB64) ?: return null
        val sigBytes = decodeBase64Url(list.signatureB64) ?: return null
        if (!Ed25519Verifier.verify(publicKey32, msgBytes, sigBytes)) {
            return null
        }
        val parsed = runCatching {
            json.decodeFromString<RevocationPayloadOnly>(msgBytes.toString(Charsets.UTF_8))
        }.getOrNull() ?: return null
        return parsed.entries
    }

    /** Pure Kotlin Base64URL decoder compatible with Android API 24+ and JVM unit tests. */
    fun decodeBase64Url(input: String): ByteArray? {
        if (input.isEmpty()) return ByteArray(0)
        val clean = input.trim()
        val out = ByteArray((clean.length * 3) / 4 + 3)
        var outIdx = 0
        var acc = 0
        var bits = 0
        for (ch in clean) {
            if (ch == '=') break
            val v = when (ch) {
                in 'A'..'Z' -> ch.code - 'A'.code
                in 'a'..'z' -> ch.code - 'a'.code + 26
                in '0'..'9' -> ch.code - '0'.code + 52
                '-', '+' -> 62
                '_', '/' -> 63
                else -> return null
            }
            acc = (acc shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out[outIdx++] = ((acc ushr bits) and 0xFF).toByte()
            }
        }
        return out.copyOf(outIdx)
    }
}
