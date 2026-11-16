package org.futo.inputmethod.latin.themestore.engine

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.themestore.model.KeybirdThemeManifestV1
import org.futo.inputmethod.latin.themestore.security.ThemeLicenseGuard
import org.futo.inputmethod.latin.themestore.security.ThemeNativeGuard
import org.futo.inputmethod.latin.themestore.storage.ThemeStoreDbHelper
import org.futo.inputmethod.latin.uix.isDirectBootUnlocked
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.ThemeOptions

/**
 * Unifies built-in ThemeOptions with verified, locally installed Store Themes ("store:<themeId>").
 * Runs 100% offline inside both SettingsActivity and LatinIME.
 */
object ThemeRegistry {
    const val STORE_KEY_PREFIX = "store:"

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private val lock = Any()
    private val cachedManifests = HashMap<String, KeybirdThemeManifestV1>()

    fun storeKeyFor(themeId: String): String = "$STORE_KEY_PREFIX$themeId"

    fun extractStoreThemeId(themeKey: String?): String? {
        if (themeKey.isNullOrBlank()) return null
        return if (themeKey.startsWith(STORE_KEY_PREFIX)) {
            themeKey.removePrefix(STORE_KEY_PREFIX).takeIf { it.isNotBlank() }
        } else {
            null
        }
    }

    fun notifyChanged() {
        synchronized(lock) {
            cachedManifests.clear()
        }
        _revision.value += 1
    }

    fun resolveThemeOption(context: Context, normalizedKey: String): ThemeOption? {
        ThemeOptions[normalizedKey]?.let { return it }
        val themeId = extractStoreThemeId(normalizedKey) ?: return null
        val manifest = loadVerifiedManifest(context, themeId) ?: return null
        return manifestToThemeOption(manifest)
    }

    fun resolveStoreRenderProfile(context: Context, themeKey: String?): StoreThemeRenderProfile? {
        val themeId = extractStoreThemeId(themeKey) ?: return null
        val manifest = loadVerifiedManifest(context, themeId) ?: return null
        return ThemeManifestEngine.toRenderProfile(manifest)
    }

    fun getInstalledStoreThemeOptions(context: Context): List<ThemeOption> {
        if (!context.isDirectBootUnlocked) return emptyList()
        val db = ThemeStoreDbHelper.getInstance(context)
        return db.getInstalledThemes().mapNotNull { record ->
            val manifest = loadVerifiedManifest(context, record.themeId) ?: return@mapNotNull null
            manifestToThemeOption(manifest)
        }
    }

    private fun manifestToThemeOption(manifest: KeybirdThemeManifestV1): ThemeOption =
        ThemeOption(
            dynamic = false,
            key = storeKeyFor(manifest.themeId),
            name = R.string.store_theme_fallback_name,
            displayName = manifest.title,
            available = { true },
            obtainColors = { ThemeManifestEngine.toColorScheme(manifest) }
        )

    private fun loadVerifiedManifest(context: Context, themeId: String): KeybirdThemeManifestV1? {
        if (!context.isDirectBootUnlocked) return null

        val db = ThemeStoreDbHelper.getInstance(context)
        val record = db.getInstalledTheme(themeId) ?: return null
        val deviceHash = ThemeLicenseGuard.computeDeviceHash(context)

        // Enforce paid license + Ed25519 receipt + revocation kill-switch even completely offline
        if (record.isPaid) {
            if (!ThemeNativeGuard.isApkIntegrityValid(context)) return null
            val revoked = db.getRevokedHashes()
            if (revoked.contains(deviceHash)) return null
            val hasValidEntitlement = db.getEntitlements().any { ent ->
                val payload = ThemeLicenseGuard.verifyReceipt(
                    receipt = ent.receipt,
                    expectedDeviceHash = deviceHash,
                    revokedTargetHashes = revoked
                )
                payload != null && ThemeLicenseGuard.verifyCoversTheme(payload, themeId)
            }
            if (!hasValidEntitlement) return null
        }

        synchronized(lock) {
            cachedManifests[themeId]?.let { return it }
        }

        val manifestStr = db.readInstalledManifestJson(record, deviceHash) ?: return null
        val parsed = ThemeManifestEngine.parseManifest(manifestStr) ?: return null
        synchronized(lock) {
            cachedManifests[themeId] = parsed
        }
        return parsed
    }
}
