package org.futo.inputmethod.latin.themestore.engine

import android.content.Context
import android.graphics.Typeface
import org.futo.inputmethod.latin.themestore.security.ThemeLicenseGuard
import org.futo.inputmethod.latin.themestore.security.ThemeNativeGuard
import org.futo.inputmethod.latin.themestore.storage.ThemeStoreDbHelper
import org.futo.inputmethod.latin.uix.isDirectBootUnlocked
import java.io.File

enum class LabelScript {
    BANGLA,
    NUMBER,
    LATIN
}

data class StoreFontSet(
    val fontId: String,
    val latin: Typeface?,
    val bangla: Typeface?,
    val number: Typeface?
)

/**
 * Loads decrypted `.iplusfont` TTF bundles for the active Store Font Pack and routes
 * Bangla, English, and Numeric keycap labels to their dedicated Typeface on Android 7+.
 */
object StoreFontEngine {
    private const val NO_FONT_SENTINEL = "__none__"
    private val lock = Any()

    @Volatile
    private var activeFontSet: StoreFontSet? = null

    @Volatile
    private var loadedFontId: String? = null

    fun invalidate() {
        synchronized(lock) {
            activeFontSet = null
            loadedFontId = null
        }
    }

    /** Pure script classifier so Bangla glyphs never fall back to broken tofu boxes on Latin-only display fonts. */
    @JvmStatic
    fun classifyLabelScript(label: String?): LabelScript {
        if (label.isNullOrBlank()) return LabelScript.LATIN
        var hasBanglaLetter = false
        var hasDigit = false
        var hasOther = false
        for (ch in label) {
            val code = ch.code
            when {
                code in 0x09E6..0x09EF -> hasDigit = true // Bengali digits ০-৯
                code in 0x0980..0x09FF -> hasBanglaLetter = true // Bengali script
                ch in '0'..'9' -> hasDigit = true
                !ch.isWhitespace() -> hasOther = true
            }
        }
        return when {
            hasBanglaLetter -> LabelScript.BANGLA
            hasDigit && !hasOther -> LabelScript.NUMBER
            else -> LabelScript.LATIN
        }
    }

    @JvmStatic
    fun selectTypefaceForLabel(label: String?, fallback: Typeface): Typeface {
        val set = activeFontSet ?: return fallback
        val chosen = when (classifyLabelScript(label)) {
            LabelScript.BANGLA -> set.bangla
            LabelScript.NUMBER -> set.number ?: set.latin
            LabelScript.LATIN -> set.latin
        }
        return chosen ?: fallback
    }

    @JvmStatic
    fun hasActiveStoreFont(): Boolean = activeFontSet != null

    fun activeFontLatinTypeface(): Typeface? = activeFontSet?.latin

    fun refreshFromActivePreference(context: Context): StoreFontSet? {
        if (!context.isDirectBootUnlocked) {
            invalidate()
            return null
        }
        synchronized(lock) {
            if (loadedFontId == NO_FONT_SENTINEL) return null
        }
        val db = ThemeStoreDbHelper.getInstance(context)
        val wantedId = db.getActiveFontId()
        if (wantedId.isNullOrBlank()) {
            synchronized(lock) {
                activeFontSet = null
                loadedFontId = NO_FONT_SENTINEL
            }
            return null
        }
        synchronized(lock) {
            if (loadedFontId == wantedId && activeFontSet != null) {
                return activeFontSet
            }
        }
        val loaded = loadVerifiedFontSet(context, wantedId)
        synchronized(lock) {
            activeFontSet = loaded
            loadedFontId = if (loaded != null) wantedId else NO_FONT_SENTINEL
        }
        return loaded
    }

    fun loadVerifiedFontSet(context: Context, fontId: String): StoreFontSet? {
        if (!context.isDirectBootUnlocked) return null
        val db = ThemeStoreDbHelper.getInstance(context)
        val record = db.getInstalledTheme(fontId) ?: return null
        val deviceHash = ThemeLicenseGuard.computeDeviceHash(context)

        if (record.isPaid) {
            if (!ThemeNativeGuard.isApkIntegrityValid(context)) return null
            val revoked = db.getRevokedHashes()
            if (revoked.contains(deviceHash)) return null
            val validEnt = db.getEntitlements().any { ent ->
                val payload = ThemeLicenseGuard.verifyReceipt(
                    receipt = ent.receipt,
                    expectedDeviceHash = deviceHash,
                    revokedTargetHashes = revoked
                )
                payload != null && ThemeLicenseGuard.verifyCoversTheme(payload, fontId)
            }
            if (!validEnt) return null
        }

        val manifestJson = db.readInstalledManifestJson(record, deviceHash) ?: return null
        val manifest = ThemeManifestEngine.parseManifest(manifestJson) ?: return null
        val fp = manifest.fontPack ?: return null

        val zipBytes = db.readArtBundle(fontId, record.isPaid, deviceHash) ?: return null
        val cacheDir = File(context.applicationContext.cacheDir, "active_store_fonts/$fontId").apply { mkdirs() }

        fun loadSlot(assetPath: String, outFileName: String): Typeface? {
            if (assetPath.isBlank()) return null
            val target = File(cacheDir, outFileName)
            if (!target.exists() || target.length() < 128L) {
                val bytes = ArtBundleCodec.extractAsset(zipBytes, assetPath) ?: return null
                target.writeBytes(bytes)
            }
            return runCatching { Typeface.createFromFile(target) }.getOrNull()
        }

        val latin = loadSlot(fp.latinAsset, "latin.ttf")
        val bangla = loadSlot(fp.banglaAsset, "bangla.ttf")
        val number = loadSlot(fp.numberAsset, "number.ttf")
        if (latin == null && bangla == null && number == null) return null
        return StoreFontSet(fontId = fontId, latin = latin, bangla = bangla, number = number)
    }
}
