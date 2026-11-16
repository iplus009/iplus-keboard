package org.futo.inputmethod.latin.themestore.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import org.futo.inputmethod.latin.BuildConfig
import org.futo.inputmethod.latin.utils.JniUtils
import java.security.MessageDigest

/**
 * Bridges native libjni_latinime.so verification primitives and release signing certificate checks.
 */
object ThemeNativeGuard {
    // Public key is safe to embed; private signing key lives strictly on Cloudflare Worker secrets.
    private const val FALLBACK_ED25519_PUB_HEX =
        "3978e58cf9df854aeb68bb5576d4de2dbc6b159d395a2be898d857295aeff7cd"

    // Set to production release signing certificate SHA-256 hex when distributing release builds.
    @Volatile
    var pinnedReleaseCertSha256: String? = null

    private val nativeLoaded: Boolean by lazy {
        runCatching {
            JniUtils.loadNativeLibrary()
            true
        }.getOrDefault(false)
    }

    @JvmStatic
    private external fun nativeGetTrustedEd25519PubKeyHex(): String

    @JvmStatic
    private external fun nativeConstantTimeEquals(a: ByteArray, b: ByteArray): Boolean

    fun trustedEd25519PublicKeyBytes(): ByteArray {
        val hex = if (nativeLoaded) {
            runCatching { nativeGetTrustedEd25519PubKeyHex() }.getOrDefault(FALLBACK_ED25519_PUB_HEX)
        } else {
            FALLBACK_ED25519_PUB_HEX
        }
        return Ed25519Verifier.hexToBytes(hex)
    }

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (nativeLoaded) {
            val nativeRes = runCatching { nativeConstantTimeEquals(a, b) }.getOrNull()
            if (nativeRes != null) return nativeRes
        }
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    fun isApkIntegrityValid(context: Context): Boolean {
        val expectedSha256 = pinnedReleaseCertSha256?.trim()?.lowercase()
        if (BuildConfig.DEBUG || expectedSha256.isNullOrEmpty()) return true
        val actual = getSigningCertSha256(context) ?: return false
        return constantTimeEquals(
            actual.toByteArray(Charsets.UTF_8),
            expectedSha256.toByteArray(Charsets.UTF_8)
        )
    }

    @Suppress("DEPRECATION")
    fun getSigningCertSha256(context: Context): String? = runCatching {
        val pm = context.packageManager
        val pkg = context.packageName
        val certBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
            info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        } else {
            val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
            info.signatures?.firstOrNull()?.toByteArray()
        } ?: return@runCatching null

        val digest = MessageDigest.getInstance("SHA-256").digest(certBytes)
        digest.joinToString("") { "%02x".format(it) }
    }.getOrNull()
}
