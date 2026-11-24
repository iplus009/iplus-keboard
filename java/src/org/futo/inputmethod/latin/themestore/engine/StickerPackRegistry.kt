package org.futo.inputmethod.latin.themestore.engine

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import org.futo.inputmethod.latin.BuildConfig
import org.futo.inputmethod.latin.themestore.security.ThemeLicenseGuard
import org.futo.inputmethod.latin.themestore.security.ThemeNativeGuard
import org.futo.inputmethod.latin.themestore.storage.ThemeStoreDbHelper
import org.futo.inputmethod.latin.uix.isDirectBootUnlocked
import java.io.File

data class InstalledStickerItem(
    val id: String,
    val label: String,
    val file: File
)

data class InstalledStickerPack(
    val packId: String,
    val title: String,
    val stickers: List<InstalledStickerItem>
)

/**
 * Resolves verified unlocked Sticker Packs for the in-keyboard Sticker Picker and
 * provides FileProvider content URIs for InputConnection.commitContent().
 */
object StickerPackRegistry {
    fun getInstalledStickerPacks(context: Context): List<InstalledStickerPack> {
        if (!context.isDirectBootUnlocked) return emptyList()
        val db = ThemeStoreDbHelper.getInstance(context)
        val deviceHash = ThemeLicenseGuard.computeDeviceHash(context)
        val revoked = db.getRevokedHashes()
        if (revoked.contains(deviceHash)) return emptyList()
        val ents = db.getEntitlements()

        val packs = mutableListOf<InstalledStickerPack>()
        for (record in db.getInstalledThemes()) {
            if (record.isPaid) {
                if (!ThemeNativeGuard.isApkIntegrityValid(context)) continue
                val ok = ents.any { ent ->
                    val payload = ThemeLicenseGuard.verifyReceipt(
                        receipt = ent.receipt,
                        expectedDeviceHash = deviceHash,
                        revokedTargetHashes = revoked
                    )
                    payload != null && ThemeLicenseGuard.verifyCoversTheme(payload, record.themeId)
                }
                if (!ok) continue
            }

            val manifestJson = db.readInstalledManifestJson(record, deviceHash) ?: continue
            val manifest = ThemeManifestEngine.parseManifest(manifestJson) ?: continue
            if (manifest.themeType != "sticker") continue
            val sp = manifest.stickerPack ?: continue

            val dir = db.stickerShareDir(manifest.themeId)
            var zipBytesCache: ByteArray? = null
            val items = mutableListOf<InstalledStickerItem>()
            for (stk in sp.stickers) {
                val ext = if (stk.asset.endsWith(".png", ignoreCase = true)) "png" else "webp"
                val file = File(dir, "${stk.id}.$ext")
                if (!file.exists() || file.length() < 64L) {
                    if (zipBytesCache == null) {
                        zipBytesCache = db.readArtBundle(manifest.themeId, record.isPaid, deviceHash)
                    }
                    val bytes = zipBytesCache?.let { ArtBundleCodec.extractAsset(it, stk.asset) }
                    if (bytes != null) {
                        file.writeBytes(bytes)
                    }
                }
                if (file.exists() && file.length() >= 64L) {
                    items.add(InstalledStickerItem(id = stk.id, label = stk.label, file = file))
                }
            }
            if (items.isNotEmpty()) {
                packs.add(InstalledStickerPack(packId = manifest.themeId, title = manifest.title, stickers = items))
            }
        }
        return packs
    }

    fun getContentUriForSticker(context: Context, file: File): Uri? = runCatching {
        FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
    }.getOrNull()

    /** Returns the exact single MIME type matching a sticker filename's extension. */
    fun mimeTypeForStickerFileName(fileName: String): String {
        val lower = fileName.trim().lowercase()
        return when {
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            else -> "image/webp"
        }
    }

    // ---- User-created stickers (imported from the Settings ➔ My Stickers screen) ----

    /** Directory holding the user's own sticker images (copied from picked content URIs). */
    fun customStickerDir(context: Context): File =
        File(context.filesDir, "custom_stickers/my_pack").apply { mkdirs() }

    /** Copies picked images into the custom pack. Returns number imported. */
    fun importCustomStickers(context: Context, uris: List<Uri>): Int {
        val dir = customStickerDir(context)
        var count = 0
        for (uri in uris) {
            runCatching {
                val mime = context.contentResolver.getType(uri).orEmpty()
                val ext = when {
                    mime.contains("png") -> "png"
                    mime.contains("webp") -> "webp"
                    mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
                    else -> "webp"
                }
                val name = "stk_${System.currentTimeMillis()}_${count}.$ext"
                context.contentResolver.openInputStream(uri)?.use { input ->
                    File(dir, name).outputStream().use { out -> input.copyTo(out) }
                }
                count++
            }
        }
        if (count > 0) ThemeRegistry.notifyChanged()
        return count
    }

    /** Deletes a single user-imported sticker file and refreshes the picker. */
    fun deleteCustomSticker(file: File): Boolean {
        val deleted = runCatching { file.exists() && file.delete() }.getOrDefault(false)
        if (deleted) ThemeRegistry.notifyChanged()
        return deleted
    }

    /** Exposes user-created stickers as an extra pack inside the keyboard Sticker Picker. */
    fun getCustomStickerPacks(context: Context): List<InstalledStickerPack> {
        val items = customStickerDir(context).listFiles()
            ?.filter { it.isFile && it.length() >= 64L }
            ?.sortedBy { it.name }
            ?.mapIndexed { idx, f ->
                InstalledStickerItem(
                    id = "custom_${f.name.substringBefore('.')}",
                    label = "My sticker ${idx + 1}",
                    file = f
                )
            }.orEmpty()
        return if (items.isEmpty()) emptyList()
        else listOf(InstalledStickerPack(packId = "custom_my_pack", title = "My Stickers", stickers = items))
    }
}
