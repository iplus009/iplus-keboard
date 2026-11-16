package org.futo.inputmethod.latin.themestore.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.futo.inputmethod.latin.themestore.model.CatalogThemeItem
import org.futo.inputmethod.latin.themestore.model.OrderHistoryEntry
import org.futo.inputmethod.latin.themestore.model.RevocationEntry
import org.futo.inputmethod.latin.themestore.model.SignedEntitlementReceipt
import org.futo.inputmethod.latin.uix.isDirectBootUnlocked
import java.io.File

data class InstalledThemeRecord(
    val themeId: String,
    val installedVersion: Int,
    val isPaid: Boolean,
    val storageFilePath: String,
    val bundleSha256: String,
    val isEncrypted: Boolean,
    val installedAt: Long
)

data class StoredEntitlementRecord(
    val entitlementKey: String,
    val appId: String,
    val themeId: String,
    val keyId: String,
    val receipt: SignedEntitlementReceipt,
    val active: Boolean,
    val recoveryTokenHint: String
)

/**
 * Room 2: Local SQLite catalog cache, installed themes index, entitlement receipts,
 * signed revocation list, and encrypted vault file management.
 */
class ThemeStoreDbHelper(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DB_NAME,
    null,
    DB_VERSION
) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS catalog_cache (
                theme_id TEXT PRIMARY KEY,
                app_id TEXT NOT NULL,
                title TEXT NOT NULL,
                description TEXT NOT NULL,
                category TEXT NOT NULL,
                theme_type TEXT NOT NULL,
                is_paid INTEGER NOT NULL,
                price_bdt_paisa INTEGER NOT NULL,
                remote_version INTEGER NOT NULL,
                preview_url TEXT NOT NULL,
                accent_hex TEXT NOT NULL,
                bundle_sha256 TEXT NOT NULL,
                size_bytes INTEGER NOT NULL,
                sort_order INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS installed_themes (
                theme_id TEXT PRIMARY KEY,
                installed_version INTEGER NOT NULL,
                is_paid INTEGER NOT NULL,
                storage_file_path TEXT NOT NULL,
                bundle_sha256 TEXT NOT NULL,
                is_encrypted INTEGER NOT NULL,
                installed_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS entitlements (
                entitlement_key TEXT PRIMARY KEY,
                app_id TEXT NOT NULL,
                theme_id TEXT NOT NULL,
                key_id TEXT NOT NULL,
                receipt_json TEXT NOT NULL,
                active INTEGER NOT NULL,
                recovery_token_hint TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS revocations_cache (
                target_hash TEXT PRIMARY KEY,
                target_type TEXT NOT NULL,
                revoked_at INTEGER NOT NULL,
                synced_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS orders_history (
                order_id TEXT PRIMARY KEY,
                payment_method TEXT NOT NULL,
                expected_amount_bdt TEXT NOT NULL,
                trx_id TEXT NOT NULL,
                theme_ids_json TEXT NOT NULL,
                status TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future migrations handled explicitly without dropping verified entitlements
    }

    fun replaceCatalog(items: List<CatalogThemeItem>) {
        if (!appContext.isDirectBootUnlocked) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("catalog_cache", null, null)
            val now = System.currentTimeMillis() / 1000L
            items.forEachIndexed { idx, item ->
                val cv = ContentValues().apply {
                    put("theme_id", item.themeId)
                    put("app_id", item.appId)
                    put("title", item.title)
                    put("description", item.description)
                    put("category", item.category)
                    put("theme_type", item.themeType)
                    put("is_paid", item.isPaid)
                    put("price_bdt_paisa", item.priceBdtPaisa)
                    put("remote_version", item.currentVersion)
                    put("preview_url", item.previewUrl)
                    put("accent_hex", item.accentHex)
                    put("bundle_sha256", item.bundleSha256 ?: "")
                    put("size_bytes", item.sizeBytes ?: 0)
                    put("sort_order", idx)
                    put("updated_at", now)
                }
                db.insertWithOnConflict("catalog_cache", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getCatalog(): List<CatalogThemeItem> {
        if (!appContext.isDirectBootUnlocked) return emptyList()
        val list = mutableListOf<CatalogThemeItem>()
        readableDatabase.rawQuery(
            "SELECT * FROM catalog_cache ORDER BY sort_order ASC",
            null
        ).use { c ->
            while (c.moveToNext()) {
                list.add(
                    CatalogThemeItem(
                        themeId = c.getString(c.getColumnIndexOrThrow("theme_id")),
                        appId = c.getString(c.getColumnIndexOrThrow("app_id")),
                        title = c.getString(c.getColumnIndexOrThrow("title")),
                        description = c.getString(c.getColumnIndexOrThrow("description")),
                        category = c.getString(c.getColumnIndexOrThrow("category")),
                        themeType = c.getString(c.getColumnIndexOrThrow("theme_type")),
                        isPaid = c.getInt(c.getColumnIndexOrThrow("is_paid")),
                        priceBdtPaisa = c.getInt(c.getColumnIndexOrThrow("price_bdt_paisa")),
                        currentVersion = c.getInt(c.getColumnIndexOrThrow("remote_version")),
                        previewUrl = c.getString(c.getColumnIndexOrThrow("preview_url")),
                        accentHex = c.getString(c.getColumnIndexOrThrow("accent_hex")),
                        bundleSha256 = c.getString(c.getColumnIndexOrThrow("bundle_sha256")),
                        sizeBytes = c.getInt(c.getColumnIndexOrThrow("size_bytes"))
                    )
                )
            }
        }
        return list
    }

    fun saveInstalledTheme(record: InstalledThemeRecord) {
        if (!appContext.isDirectBootUnlocked) return
        val cv = ContentValues().apply {
            put("theme_id", record.themeId)
            put("installed_version", record.installedVersion)
            put("is_paid", if (record.isPaid) 1 else 0)
            put("storage_file_path", record.storageFilePath)
            put("bundle_sha256", record.bundleSha256)
            put("is_encrypted", if (record.isEncrypted) 1 else 0)
            put("installed_at", record.installedAt)
        }
        writableDatabase.insertWithOnConflict("installed_themes", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getInstalledThemes(): List<InstalledThemeRecord> {
        if (!appContext.isDirectBootUnlocked) return emptyList()
        val list = mutableListOf<InstalledThemeRecord>()
        readableDatabase.rawQuery("SELECT * FROM installed_themes", null).use { c ->
            while (c.moveToNext()) {
                list.add(
                    InstalledThemeRecord(
                        themeId = c.getString(c.getColumnIndexOrThrow("theme_id")),
                        installedVersion = c.getInt(c.getColumnIndexOrThrow("installed_version")),
                        isPaid = c.getInt(c.getColumnIndexOrThrow("is_paid")) == 1,
                        storageFilePath = c.getString(c.getColumnIndexOrThrow("storage_file_path")),
                        bundleSha256 = c.getString(c.getColumnIndexOrThrow("bundle_sha256")),
                        isEncrypted = c.getInt(c.getColumnIndexOrThrow("is_encrypted")) == 1,
                        installedAt = c.getLong(c.getColumnIndexOrThrow("installed_at"))
                    )
                )
            }
        }
        return list
    }

    fun getInstalledTheme(themeId: String): InstalledThemeRecord? =
        getInstalledThemes().firstOrNull { it.themeId == themeId }

    fun saveEntitlementReceipt(
        receipt: SignedEntitlementReceipt,
        recoveryTokenHint: String
    ) {
        if (!appContext.isDirectBootUnlocked) return
        val db = writableDatabase
        val receiptJson = json.encodeToString(receipt)
        val now = System.currentTimeMillis() / 1000L
        db.beginTransaction()
        try {
            for (themeId in receipt.payload.themeIds) {
                val entKey = "${receipt.payload.appId}:$themeId"
                val cv = ContentValues().apply {
                    put("entitlement_key", entKey)
                    put("app_id", receipt.payload.appId)
                    put("theme_id", themeId)
                    put("key_id", receipt.payload.keyId)
                    put("receipt_json", receiptJson)
                    put("active", 1)
                    put("recovery_token_hint", recoveryTokenHint)
                    put("created_at", now)
                }
                db.insertWithOnConflict("entitlements", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getEntitlements(): List<StoredEntitlementRecord> {
        if (!appContext.isDirectBootUnlocked) return emptyList()
        val list = mutableListOf<StoredEntitlementRecord>()
        readableDatabase.rawQuery("SELECT * FROM entitlements WHERE active = 1", null).use { c ->
            while (c.moveToNext()) {
                val receiptStr = c.getString(c.getColumnIndexOrThrow("receipt_json"))
                val parsedReceipt = runCatching {
                    json.decodeFromString<SignedEntitlementReceipt>(receiptStr)
                }.getOrNull() ?: continue
                list.add(
                    StoredEntitlementRecord(
                        entitlementKey = c.getString(c.getColumnIndexOrThrow("entitlement_key")),
                        appId = c.getString(c.getColumnIndexOrThrow("app_id")),
                        themeId = c.getString(c.getColumnIndexOrThrow("theme_id")),
                        keyId = c.getString(c.getColumnIndexOrThrow("key_id")),
                        receipt = parsedReceipt,
                        active = c.getInt(c.getColumnIndexOrThrow("active")) == 1,
                        recoveryTokenHint = c.getString(c.getColumnIndexOrThrow("recovery_token_hint"))
                    )
                )
            }
        }
        return list
    }

    fun replaceRevocations(entries: List<RevocationEntry>) {
        if (!appContext.isDirectBootUnlocked) return
        val db = writableDatabase
        val now = System.currentTimeMillis() / 1000L
        db.beginTransaction()
        try {
            db.delete("revocations_cache", null, null)
            for (e in entries) {
                val cv = ContentValues().apply {
                    put("target_hash", e.targetHash)
                    put("target_type", e.targetType)
                    put("revoked_at", e.revokedAt)
                    put("synced_at", now)
                }
                db.insertWithOnConflict("revocations_cache", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getRevokedHashes(): Set<String> {
        if (!appContext.isDirectBootUnlocked) return emptySet()
        val set = mutableSetOf<String>()
        readableDatabase.rawQuery("SELECT target_hash FROM revocations_cache", null).use { c ->
            while (c.moveToNext()) {
                set.add(c.getString(0))
            }
        }
        return set
    }

    fun saveOrderHistory(order: OrderHistoryEntry) {
        if (!appContext.isDirectBootUnlocked) return
        val cv = ContentValues().apply {
            put("order_id", order.orderId)
            put("payment_method", order.paymentMethod)
            put("expected_amount_bdt", order.expectedAmountBdt)
            put("trx_id", order.trxId)
            put("theme_ids_json", json.encodeToString(order.themeIds))
            put("status", order.status)
            put("created_at", order.createdAt)
        }
        writableDatabase.insertWithOnConflict("orders_history", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getOrderHistory(): List<OrderHistoryEntry> {
        if (!appContext.isDirectBootUnlocked) return emptyList()
        val list = mutableListOf<OrderHistoryEntry>()
        readableDatabase.rawQuery("SELECT * FROM orders_history ORDER BY created_at DESC", null).use { c ->
            while (c.moveToNext()) {
                val themeIds = runCatching {
                    json.decodeFromString<List<String>>(c.getString(c.getColumnIndexOrThrow("theme_ids_json")))
                }.getOrDefault(emptyList())
                list.add(
                    OrderHistoryEntry(
                        orderId = c.getString(c.getColumnIndexOrThrow("order_id")),
                        paymentMethod = c.getString(c.getColumnIndexOrThrow("payment_method")),
                        expectedAmountBdt = c.getString(c.getColumnIndexOrThrow("expected_amount_bdt")),
                        trxId = c.getString(c.getColumnIndexOrThrow("trx_id")),
                        themeIds = themeIds,
                        status = c.getString(c.getColumnIndexOrThrow("status")),
                        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"))
                    )
                )
            }
        }
        return list
    }

    fun clearAllEntitlementsAndVaultFiles() {
        if (!appContext.isDirectBootUnlocked) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("entitlements", null, null)
            db.delete("installed_themes", "is_paid = 1", null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        vaultDir(appContext).deleteRecursively()
    }

    fun writeFreeThemeManifest(themeId: String, manifestJson: String): File {
        val dir = File(appContext.filesDir, "theme_store/free").apply { mkdirs() }
        return File(dir, "$themeId.json").apply { writeText(manifestJson, Charsets.UTF_8) }
    }

    fun writeEncryptedPaidThemeManifest(
        themeId: String,
        manifestJson: String,
        deviceHash: String
    ): File {
        val dir = vaultDir(appContext).apply { mkdirs() }
        val aad = ThemeVaultCrypto.buildAad("keybird", themeId, deviceHash)
        val encrypted = ThemeVaultCrypto.encrypt(manifestJson.toByteArray(Charsets.UTF_8), aad)
        return File(dir, "$themeId.enc").apply { writeBytes(encrypted) }
    }

    fun readInstalledManifestJson(record: InstalledThemeRecord, deviceHash: String): String? {
        if (!appContext.isDirectBootUnlocked) return null
        val file = File(record.storageFilePath)
        if (!file.exists()) return null
        return if (record.isEncrypted) {
            val aad = ThemeVaultCrypto.buildAad("keybird", record.themeId, deviceHash)
            ThemeVaultCrypto.decrypt(file.readBytes(), aad)?.toString(Charsets.UTF_8)
        } else {
            file.readText(Charsets.UTF_8)
        }
    }

    companion object {
        private const val DB_NAME = "keybird_themestore.db"
        private const val DB_VERSION = 1

        private fun vaultDir(context: Context): File =
            File(context.filesDir, "theme_store/vault")

        @Volatile
        private var instance: ThemeStoreDbHelper? = null

        fun getInstance(context: Context): ThemeStoreDbHelper =
            instance ?: synchronized(this) {
                instance ?: ThemeStoreDbHelper(context.applicationContext).also { instance = it }
            }
    }
}
