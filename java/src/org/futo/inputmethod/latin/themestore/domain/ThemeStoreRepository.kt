package org.futo.inputmethod.latin.themestore.domain

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.futo.inputmethod.latin.themestore.engine.ThemeRegistry
import org.futo.inputmethod.latin.themestore.model.CatalogThemeItem
import org.futo.inputmethod.latin.themestore.model.CreateOrderRequest
import org.futo.inputmethod.latin.themestore.model.CreateOrderResponse
import org.futo.inputmethod.latin.themestore.model.DeactivateRequest
import org.futo.inputmethod.latin.themestore.model.KeybirdThemeManifestV1
import org.futo.inputmethod.latin.themestore.model.OrderHistoryEntry
import org.futo.inputmethod.latin.themestore.model.PaymentAccountsDto
import org.futo.inputmethod.latin.themestore.model.RecoverRequest
import org.futo.inputmethod.latin.themestore.model.RedeemKeyRequest
import org.futo.inputmethod.latin.themestore.model.SignedEntitlementReceipt
import org.futo.inputmethod.latin.themestore.model.StoreCatalogEntry
import org.futo.inputmethod.latin.themestore.model.StoreThemeState
import org.futo.inputmethod.latin.themestore.model.SubmitTrxRequest
import org.futo.inputmethod.latin.themestore.model.ThemeFilter
import org.futo.inputmethod.latin.themestore.network.ThemeStoreApiClient
import org.futo.inputmethod.latin.themestore.security.ThemeLicenseGuard
import org.futo.inputmethod.latin.themestore.security.ThemeNativeGuard
import org.futo.inputmethod.latin.themestore.storage.InstalledThemeRecord
import org.futo.inputmethod.latin.themestore.storage.ThemeStoreDbHelper
import org.futo.inputmethod.latin.uix.THEME_KEY
import org.futo.inputmethod.latin.uix.setSetting

/**
 * Pure domain filter logic separated for unit testing and reuse.
 */
object StoreCatalogFilter {
    fun buildEntries(
        catalog: List<CatalogThemeItem>,
        installed: Map<String, InstalledThemeRecord>,
        ownedThemeIds: Set<String>,
        hasFullAccess: Boolean
    ): List<StoreCatalogEntry> = catalog.map { item ->
        val isPaid = item.isPaid == 1
        val isOwned = !isPaid || hasFullAccess || ownedThemeIds.contains(item.themeId)
        val rec = installed[item.themeId]
        val isDownloaded = rec != null
        val hasUpdate = rec != null && item.currentVersion > rec.installedVersion
        val state = when {
            isDownloaded -> StoreThemeState.DOWNLOADED
            !isPaid -> StoreThemeState.FREE
            isOwned -> StoreThemeState.OWNED
            else -> StoreThemeState.PAID
        }
        StoreCatalogEntry(
            themeId = item.themeId,
            title = item.title,
            description = item.description,
            category = item.category,
            themeType = item.themeType,
            isPaid = isPaid,
            priceBdtPaisa = item.priceBdtPaisa,
            remoteVersion = item.currentVersion,
            installedVersion = rec?.installedVersion,
            accentHex = item.accentHex,
            previewUrl = item.previewUrl,
            isOwned = isOwned,
            isDownloaded = isDownloaded,
            hasUpdate = hasUpdate,
            state = state
        )
    }

    fun applyFilter(
        entries: List<StoreCatalogEntry>,
        filter: ThemeFilter,
        query: String
    ): List<StoreCatalogEntry> {
        val q = query.trim().lowercase()
        return entries.filter { entry ->
            val matchesFilter = when (filter) {
                ThemeFilter.ALL -> true
                ThemeFilter.FREE -> !entry.isPaid
                ThemeFilter.PAID -> entry.isPaid
                ThemeFilter.MY_THEMES -> entry.isOwned || entry.isDownloaded
            }
            val matchesQuery = q.isEmpty() ||
                entry.title.lowercase().contains(q) ||
                entry.category.lowercase().contains(q) ||
                entry.themeId.lowercase().contains(q)
            matchesFilter && matchesQuery
        }
    }
}

/**
 * Coordinates catalog sync, free downloads, TrxID orders, 1-time key redemption,
 * recovery, revocation sync, and applying themes to Keybird.
 */
class ThemeStoreRepository(
    context: Context,
    private val api: ThemeStoreApiClient = ThemeStoreApiClient()
) {
    private val appContext = context.applicationContext
    private val db = ThemeStoreDbHelper.getInstance(appContext)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val deviceHash: String
        get() = ThemeLicenseGuard.computeDeviceHash(appContext)

    var lastPaymentAccounts: PaymentAccountsDto = PaymentAccountsDto()
        private set

    fun getCachedEntries(): List<StoreCatalogEntry> {
        val catalog = db.getCatalog()
        val installed = db.getInstalledThemes().associateBy { it.themeId }
        val (ownedIds, fullAccess) = computeVerifiedOwnership()
        return StoreCatalogFilter.buildEntries(catalog, installed, ownedIds, fullAccess)
    }

    fun getOrderHistory(): List<OrderHistoryEntry> = db.getOrderHistory()

    fun getActiveRecoveryHint(): String? =
        db.getEntitlements().firstOrNull()?.recoveryTokenHint?.takeIf { it.isNotBlank() }

    suspend fun syncCatalogAndRevocations(): Result<List<StoreCatalogEntry>> {
        val revRes = api.fetchRevocations()
        revRes.getOrNull()?.let { signedRev ->
            val verifiedEntries = ThemeLicenseGuard.verifyRevocationList(signedRev)
            if (verifiedEntries != null) {
                db.replaceRevocations(verifiedEntries)
                ThemeRegistry.notifyChanged()
            }
        }

        val catRes = api.fetchCatalog()
        return catRes.map { resp ->
            lastPaymentAccounts = resp.paymentAccounts
            db.replaceCatalog(resp.themes)
            getCachedEntries()
        }
    }

    suspend fun downloadAndApplyTheme(entry: StoreCatalogEntry): Result<String> {
        return runCatching {
            val manifestResp = if (!entry.isPaid) {
                api.fetchFreeTheme(entry.themeId).getOrThrow()
            } else {
                val receipt = findReceiptForTheme(entry.themeId)
                    ?: error("No valid license found for ${entry.title}. Redeem a 1-time API key first.")
                api.downloadPaidTheme(entry.themeId, deviceHash, receipt).getOrThrow()
            }

            val manifest = manifestResp.manifest
                ?: error("Theme bundle manifest missing")
            saveManifestLocally(
                manifest = manifest,
                isPaid = entry.isPaid,
                bundleSha256 = manifestResp.bundleSha256
            )
            applyInstalledTheme(entry.themeId)
            entry.themeId
        }
    }

    suspend fun applyInstalledTheme(themeId: String) {
        appContext.setSetting(THEME_KEY, ThemeRegistry.storeKeyFor(themeId))
        ThemeRegistry.notifyChanged()
    }

    suspend fun createOrder(
        themeIds: List<String>,
        buyerName: String,
        buyerContact: String,
        paymentMethod: String
    ): Result<CreateOrderResponse> {
        val res = api.createOrder(
            CreateOrderRequest(
                deviceHash = deviceHash,
                themeIds = themeIds,
                buyerName = buyerName,
                buyerContact = buyerContact,
                paymentMethod = paymentMethod
            )
        )
        res.onSuccess { created ->
            db.saveOrderHistory(
                OrderHistoryEntry(
                    orderId = created.orderId,
                    paymentMethod = created.paymentMethod,
                    expectedAmountBdt = created.expectedAmountBdt,
                    trxId = "",
                    themeIds = themeIds,
                    status = "awaiting_payment",
                    createdAt = System.currentTimeMillis() / 1000L
                )
            )
        }
        return res
    }

    suspend fun submitTrx(
        orderId: String,
        trxId: String,
        orderMeta: CreateOrderResponse,
        themeIds: List<String>
    ): Result<String> {
        val cleanTrx = trxId.trim().uppercase()
        val res = api.submitTrx(
            SubmitTrxRequest(
                orderId = orderId,
                deviceHash = deviceHash,
                trxId = cleanTrx
            )
        )
        return res.map {
            db.saveOrderHistory(
                OrderHistoryEntry(
                    orderId = orderId,
                    paymentMethod = orderMeta.paymentMethod,
                    expectedAmountBdt = orderMeta.expectedAmountBdt,
                    trxId = cleanTrx,
                    themeIds = themeIds,
                    status = it.status,
                    createdAt = System.currentTimeMillis() / 1000L
                )
            )
            it.status
        }
    }

    suspend fun redeemApiKey(apiKey: String): Result<RedeemResult> {
        if (!ThemeNativeGuard.isApkIntegrityValid(appContext)) {
            return Result.failure(SecurityException("APK signature verification failed"))
        }
        val res = api.redeemKey(
            RedeemKeyRequest(
                apiKey = apiKey.trim(),
                deviceHash = deviceHash
            )
        )
        return res.mapCatching { body ->
            val receipt = body.receipt ?: error("Server did not return a signed receipt")
            val payload = ThemeLicenseGuard.verifyReceipt(
                receipt = receipt,
                expectedDeviceHash = deviceHash,
                revokedTargetHashes = db.getRevokedHashes()
            ) ?: error("Entitlement receipt signature verification failed")

            val hint = body.recoveryToken.takeIf { it.length >= 8 }
                ?.let { "${it.take(8)}...${it.takeLast(4)}" }.orEmpty()
            db.saveEntitlementReceipt(receipt, hint)

            // Automatically download unlocked themes whose IDs are explicitly listed
            val catalogById = db.getCatalog().associateBy { it.themeId }
            val targets = if (payload.themeIds.contains("*")) {
                catalogById.values.filter { it.isPaid == 1 }.map { it.themeId }
            } else {
                payload.themeIds
            }
            for (tid in targets) {
                val dl = api.downloadPaidTheme(tid, deviceHash, receipt).getOrNull()
                val manifest = dl?.manifest ?: continue
                saveManifestLocally(manifest, isPaid = true, bundleSha256 = dl.bundleSha256)
            }
            ThemeRegistry.notifyChanged()
            RedeemResult(
                grantedThemeIds = targets,
                recoveryToken = body.recoveryToken
            )
        }
    }

    suspend fun deactivateDevice(): Result<Unit> {
        val res = api.deactivateDevice(DeactivateRequest(deviceHash = deviceHash))
        res.onSuccess {
            db.clearAllEntitlementsAndVaultFiles()
            ThemeRegistry.notifyChanged()
        }
        return res
    }

    suspend fun recoverPurchases(
        recoveryToken: String?,
        trxId: String?,
        buyerContact: String?
    ): Result<RedeemResult> {
        if (!ThemeNativeGuard.isApkIntegrityValid(appContext)) {
            return Result.failure(SecurityException("APK signature verification failed"))
        }
        val res = api.recoverEntitlements(
            RecoverRequest(
                newDeviceHash = deviceHash,
                recoveryToken = recoveryToken?.trim()?.takeIf { it.isNotEmpty() },
                trxId = trxId?.trim()?.uppercase()?.takeIf { it.isNotEmpty() },
                buyerContact = buyerContact?.trim()?.takeIf { it.isNotEmpty() }
            )
        )
        return res.mapCatching { body ->
            val receipt = body.receipt ?: error("Missing recovery receipt")
            val payload = ThemeLicenseGuard.verifyReceipt(
                receipt = receipt,
                expectedDeviceHash = deviceHash,
                revokedTargetHashes = db.getRevokedHashes()
            ) ?: error("Recovered receipt verification failed")

            val hint = body.recoveryToken.takeIf { it.length >= 8 }
                ?.let { "${it.take(8)}...${it.takeLast(4)}" }.orEmpty()
            db.saveEntitlementReceipt(receipt, hint)

            val catalogById = db.getCatalog().associateBy { it.themeId }
            val targets = if (payload.themeIds.contains("*")) {
                catalogById.values.filter { it.isPaid == 1 }.map { it.themeId }
            } else {
                payload.themeIds
            }
            for (tid in targets) {
                val dl = api.downloadPaidTheme(tid, deviceHash, receipt).getOrNull()
                val manifest = dl?.manifest ?: continue
                saveManifestLocally(manifest, isPaid = true, bundleSha256 = dl.bundleSha256)
            }
            ThemeRegistry.notifyChanged()
            RedeemResult(grantedThemeIds = targets, recoveryToken = body.recoveryToken)
        }
    }

    private fun saveManifestLocally(
        manifest: KeybirdThemeManifestV1,
        isPaid: Boolean,
        bundleSha256: String
    ) {
        val manifestText = json.encodeToString(manifest)
        val file = if (isPaid) {
            db.writeEncryptedPaidThemeManifest(manifest.themeId, manifestText, deviceHash)
        } else {
            db.writeFreeThemeManifest(manifest.themeId, manifestText)
        }
        db.saveInstalledTheme(
            InstalledThemeRecord(
                themeId = manifest.themeId,
                installedVersion = manifest.version,
                isPaid = isPaid,
                storageFilePath = file.absolutePath,
                bundleSha256 = bundleSha256,
                isEncrypted = isPaid,
                installedAt = System.currentTimeMillis() / 1000L
            )
        )
    }

    private fun computeVerifiedOwnership(): Pair<Set<String>, Boolean> {
        val revoked = db.getRevokedHashes()
        if (revoked.contains(deviceHash)) return emptySet<String>() to false
        val owned = mutableSetOf<String>()
        var fullAccess = false
        for (ent in db.getEntitlements()) {
            val verified = ThemeLicenseGuard.verifyReceipt(
                receipt = ent.receipt,
                expectedDeviceHash = deviceHash,
                revokedTargetHashes = revoked
            ) ?: continue
            if (verified.themeIds.contains("*")) {
                fullAccess = true
            } else {
                owned.addAll(verified.themeIds)
            }
        }
        return owned to fullAccess
    }

    private fun findReceiptForTheme(themeId: String): SignedEntitlementReceipt? {
        val revoked = db.getRevokedHashes()
        for (ent in db.getEntitlements()) {
            val verified = ThemeLicenseGuard.verifyReceipt(
                receipt = ent.receipt,
                expectedDeviceHash = deviceHash,
                revokedTargetHashes = revoked
            ) ?: continue
            if (ThemeLicenseGuard.verifyCoversTheme(verified, themeId)) {
                return ent.receipt
            }
        }
        return null
    }
}

data class RedeemResult(
    val grantedThemeIds: List<String>,
    val recoveryToken: String
)
