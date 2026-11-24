package org.futo.inputmethod.latin.themestore.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KeybirdThemeManifestV1(
    val schema: Int = 1,
    @SerialName("theme_id") val themeId: String,
    @SerialName("app_id") val appId: String = "keybird",
    val title: String,
    val version: Int = 1,
    @SerialName("theme_type") val themeType: String = "color",
    @SerialName("is_light") val isLight: Boolean = false,
    val colors: ManifestColorsV1,
    val keyboard: ManifestKeyboardV1 = ManifestKeyboardV1(),
    @SerialName("art_pack") val artPack: ManifestArtPackV1? = null,
    @SerialName("font_pack") val fontPack: ManifestFontPackV1? = null,
    @SerialName("sticker_pack") val stickerPack: ManifestStickerPackV1? = null
)

@Serializable
data class ManifestFontPackV1(
    @SerialName("latin_asset") val latinAsset: String = "assets/fonts/latin.ttf",
    @SerialName("bangla_asset") val banglaAsset: String = "assets/fonts/bangla.ttf",
    @SerialName("number_asset") val numberAsset: String = "assets/fonts/number.ttf",
    @SerialName("latin_label") val latinLabel: String = "",
    @SerialName("bangla_label") val banglaLabel: String = "",
    @SerialName("number_label") val numberLabel: String = ""
)

@Serializable
data class ManifestStickerItemV1(
    val id: String,
    val label: String = "",
    val asset: String
)

@Serializable
data class ManifestStickerPackV1(
    @SerialName("pack_icon_asset") val packIconAsset: String = "",
    val stickers: List<ManifestStickerItemV1> = emptyList()
)

@Serializable
data class PreviewUrlsDto(
    val en: String = "",
    val bn: String = "",
    val num: String = ""
)

@Serializable
data class ManifestColorsV1(
    val primary: String = "#00E676",
    @SerialName("on_primary") val onPrimary: String = "#000000",
    @SerialName("primary_container") val primaryContainer: String = "#1B1B1F",
    @SerialName("on_primary_container") val onPrimaryContainer: String = "#FFFFFF",
    val secondary: String = "#00B0FF",
    @SerialName("on_secondary") val onSecondary: String = "#000000",
    @SerialName("secondary_container") val secondaryContainer: String = "#1B1B1F",
    @SerialName("on_secondary_container") val onSecondaryContainer: String = "#FFFFFF",
    val background: String = "#000000",
    @SerialName("on_background") val onBackground: String = "#FFFFFF",
    val surface: String = "#0E0E11",
    @SerialName("on_surface") val onSurface: String = "#FFFFFF",
    @SerialName("surface_variant") val surfaceVariant: String = "#1B1B1F",
    @SerialName("on_surface_variant") val onSurfaceVariant: String = "#9CA3AF",
    val outline: String = "#26262B"
)

@Serializable
data class ManifestKeyColorMapV1(
    val palette: List<String> = emptyList(),
    @SerialName("patternMode") val patternMode: String = "none",
    val keys: Map<String, String> = emptyMap()
)

@Serializable
data class ManifestMotionV1(
    @SerialName("bgAnimatedAsset") val bgAnimatedAsset: String = "",
    @SerialName("pressEffect") val pressEffect: String = "",
    @SerialName("pressColor") val pressColor: String = "",
    @SerialName("parallaxDp") val parallaxDp: Float = 0f,
    val particles: Boolean = false,
    @SerialName("particleDensity") val particleDensity: Int = 0,
    @SerialName("particleColor") val particleColor: String = ""
)

@Serializable
data class ManifestKeyboardV1(
    @SerialName("bg_type") val bgType: String = "solid",
    @SerialName("bg_colors") val bgColors: List<String> = listOf("#000000"),
    @SerialName("key_bg") val keyBg: String = "#1B1B1F",
    @SerialName("key_pressed_bg") val keyPressedBg: String = "#2C2C34",
    @SerialName("functional_key_bg") val functionalKeyBg: String = "#141418",
    @SerialName("enter_key_bg") val enterKeyBg: String = "#00E676",
    @SerialName("enter_key_fg") val enterKeyFg: String = "",
    @SerialName("spacebar_bg") val spacebarBg: String = "",
    @SerialName("spacebar_stroke") val spacebarStroke: String = "",
    @SerialName("key_stroke") val keyStroke: String = "#2B2D35",
    @SerialName("key_stroke_width_dp") val keyStrokeWidthDp: Float = 1.0f,
    @SerialName("key_corner_radius_dp") val keyCornerRadiusDp: Float = 8.0f,
    @SerialName("key_text") val keyText: String = "#FFFFFF",
    @SerialName("keyPressedText") val keyPressedText: String = "",
    @SerialName("key_hint") val keyHint: String = "#9CA3AF",
    @SerialName("popup_bg") val popupBg: String = "#1B1B1F",
    @SerialName("popup_text") val popupText: String = "#FFFFFF",
    @SerialName("gesture_trail") val gestureTrail: String = "",
    @SerialName("font_style") val fontStyle: String = "default",
    @SerialName("scrim_alpha") val scrimAlpha: Float = 0.0f,
    @SerialName("glass_rim_glow") val glassRimGlow: Boolean = false,
    /** Phase 2 effect preset: glass / motion / live / 3d_live_motion / touch / neon / ... */
    @SerialName("effect_mode") val effectMode: String = "glass",
    /** Phase 4 key geometry/material mode: classic / borderless / outline / glass / ceramic_3d / pill */
    @SerialName("key_style") val keyStyle: String = "classic",
    @SerialName("key_alpha") val keyAlpha: Float = 1.0f,
    @SerialName("rainbow_text") val rainbowText: Boolean = false,
    @SerialName("bg_animated_asset") val bgAnimatedAsset: String = "",
    @SerialName("key_color_map") val keyColorMap: ManifestKeyColorMapV1? = null,
    val motion: ManifestMotionV1? = null
)

/**
 * Reserved contract for Phase 2 Art-Pack themes (e.g., per-key character artwork + full banner).
 * Kept in schema v1 so simple themes and complex art packs share one storage and verification path.
 */
@Serializable
data class ManifestArtPackV1(
    @SerialName("bg_full_asset") val bgFullAsset: String? = null,
    @SerialName("spacebar_asset") val spacebarAsset: String? = null,
    val keymap: Map<String, String> = emptyMap(),
    @SerialName("group_fallbacks") val groupFallbacks: Map<String, String> = emptyMap(),
    @SerialName("scrim_mode") val scrimMode: String = "auto_yiq"
)

@Serializable
data class CatalogThemeItem(
    @SerialName("theme_id") val themeId: String,
    @SerialName("app_id") val appId: String = "keybird",
    @SerialName("short_code") val shortCode: String = "",
    val title: String,
    val description: String = "",
    val category: String = "general",
    @SerialName("theme_type") val themeType: String = "color",
    @SerialName("is_paid") val isPaid: Int = 0,
    @SerialName("price_bdt_paisa") val priceBdtPaisa: Int = 0,
    @SerialName("original_price_bdt_paisa") val originalPriceBdtPaisa: Int = 0,
    @SerialName("discount_percent") val discountPercent: Int = 0,
    @SerialName("discount_label") val discountLabel: String = "",
    @SerialName("current_version") val currentVersion: Int = 1,
    @SerialName("preview_url") val previewUrl: String = "",
    @SerialName("preview_urls") val previewUrls: PreviewUrlsDto = PreviewUrlsDto(),
    @SerialName("accent_hex") val accentHex: String = "#00E676",
    @SerialName("bundle_sha256") val bundleSha256: String? = null,
    @SerialName("size_bytes") val sizeBytes: Int? = null
)

@Serializable
data class PaymentAccountsDto(
    val bkash: String = "",
    val nagad: String = ""
)

@Serializable
data class CatalogResponse(
    val ok: Boolean = false,
    @SerialName("app_id") val appId: String = "keybird",
    @SerialName("support_telegram") val supportTelegram: String = "@boname3",
    @SerialName("payment_accounts") val paymentAccounts: PaymentAccountsDto = PaymentAccountsDto(),
    val themes: List<CatalogThemeItem> = emptyList()
)

@Serializable
data class FreeThemeResponse(
    val ok: Boolean = false,
    @SerialName("theme_id") val themeId: String = "",
    @SerialName("bundle_sha256") val bundleSha256: String = "",
    val manifest: KeybirdThemeManifestV1? = null
)

@Serializable
data class CreateOrderRequest(
    @SerialName("app_id") val appId: String = "keybird",
    @SerialName("device_hash") val deviceHash: String,
    @SerialName("theme_ids") val themeIds: List<String>,
    @SerialName("buyer_name") val buyerName: String = "",
    @SerialName("buyer_contact") val buyerContact: String,
    @SerialName("payment_method") val paymentMethod: String = "bkash"
)

@Serializable
data class CreateOrderResponse(
    val ok: Boolean = false,
    @SerialName("order_id") val orderId: String = "",
    @SerialName("expected_amount_paisa") val expectedAmountPaisa: Int = 0,
    @SerialName("expected_amount_bdt") val expectedAmountBdt: String = "0.00",
    @SerialName("payment_method") val paymentMethod: String = "bkash",
    @SerialName("pay_to") val payTo: String = ""
)

@Serializable
data class SubmitTrxRequest(
    @SerialName("order_id") val orderId: String,
    @SerialName("device_hash") val deviceHash: String,
    @SerialName("trx_id") val trxId: String
)

@Serializable
data class SubmitTrxResponse(
    val ok: Boolean = false,
    @SerialName("order_id") val orderId: String = "",
    val status: String = ""
)

@Serializable
data class EntitlementReceiptPayload(
    val v: Int = 1,
    @SerialName("app_id") val appId: String,
    val mode: String,
    @SerialName("theme_ids") val themeIds: List<String> = emptyList(),
    @SerialName("device_hash") val deviceHash: String,
    @SerialName("key_id") val keyId: String,
    @SerialName("issued_at") val issuedAt: Long,
    @SerialName("ttl_days") val ttlDays: Int
)

@Serializable
data class SignedEntitlementReceipt(
    val payload: EntitlementReceiptPayload,
    @SerialName("payload_b64") val payloadB64: String,
    @SerialName("signature_b64") val signatureB64: String
)

@Serializable
data class RedeemKeyRequest(
    @SerialName("app_id") val appId: String = "keybird",
    @SerialName("api_key") val apiKey: String,
    @SerialName("device_hash") val deviceHash: String
)

@Serializable
data class RedeemKeyResponse(
    val ok: Boolean = false,
    @SerialName("key_kind") val keyKind: String = "standard",
    val receipt: SignedEntitlementReceipt? = null,
    @SerialName("recovery_token") val recoveryToken: String = "",
    @SerialName("granted_theme_ids") val grantedThemeIds: List<String> = emptyList(),
    @SerialName("claim_number") val claimNumber: Int? = null,
    @SerialName("max_claims") val maxClaims: Int? = null,
    @SerialName("account_linked") val accountLinked: Boolean = false
)

@Serializable
data class PaidDownloadRequest(
    @SerialName("app_id") val appId: String = "keybird",
    @SerialName("theme_id") val themeId: String,
    @SerialName("device_hash") val deviceHash: String,
    val receipt: SignedEntitlementReceipt
)

@Serializable
data class DeactivateRequest(
    @SerialName("app_id") val appId: String = "keybird",
    @SerialName("device_hash") val deviceHash: String
)

@Serializable
data class RecoverRequest(
    @SerialName("app_id") val appId: String = "keybird",
    @SerialName("new_device_hash") val newDeviceHash: String,
    @SerialName("recovery_token") val recoveryToken: String? = null,
    @SerialName("trx_id") val trxId: String? = null,
    @SerialName("buyer_contact") val buyerContact: String? = null
)

@Serializable
data class RecoverResponse(
    val ok: Boolean = false,
    val receipt: SignedEntitlementReceipt? = null,
    @SerialName("recovery_token") val recoveryToken: String = "",
    @SerialName("recovered_theme_ids") val recoveredThemeIds: List<String> = emptyList(),
    @SerialName("account_linked") val accountLinked: Boolean = false
)

@Serializable
data class AccountUserDto(
    val id: String = "",
    val email: String = ""
)

@Serializable
data class AccountEntitlementsResponse(
    val ok: Boolean = false,
    val user: AccountUserDto? = null,
    @SerialName("owned_theme_ids") val ownedThemeIds: List<String> = emptyList(),
    val receipts: List<SignedEntitlementReceipt> = emptyList()
)

@Serializable
data class RevocationEntry(
    @SerialName("revocation_id") val revocationId: String,
    @SerialName("app_id") val appId: String,
    @SerialName("target_type") val targetType: String,
    @SerialName("target_hash") val targetHash: String,
    @SerialName("revoked_at") val revokedAt: Long
)

@Serializable
data class SignedRevocationList(
    val ok: Boolean = false,
    val v: Int = 1,
    @SerialName("generated_at") val generatedAt: Long = 0L,
    val entries: List<RevocationEntry> = emptyList(),
    @SerialName("payload_b64") val payloadB64: String = "",
    @SerialName("signature_b64") val signatureB64: String = ""
)

@Serializable
data class RevocationPayloadOnly(
    val v: Int = 1,
    @SerialName("generated_at") val generatedAt: Long,
    val entries: List<RevocationEntry>
)

@Serializable
data class ApiErrorWrapper(
    val ok: Boolean = false,
    val error: ApiErrorBody? = null
)

@Serializable
data class ApiErrorBody(
    val code: String = "",
    val message: String = ""
)

enum class StoreThemeState {
    FREE,
    PAID,
    OWNED,
    DOWNLOADED
}

enum class ThemeFilter {
    ALL,
    FREE,
    PAID,
    MY_THEMES
}

enum class ProductTabFilter {
    ALL,
    THEMES,
    FONTS,
    STICKERS
}

data class StoreCatalogEntry(
    val themeId: String,
    val shortCode: String = "",
    val title: String,
    val description: String,
    val category: String,
    val themeType: String,
    val isPaid: Boolean,
    val priceBdtPaisa: Int,
    val originalPriceBdtPaisa: Int = 0,
    val discountPercent: Int = 0,
    val discountLabel: String = "",
    val remoteVersion: Int,
    val installedVersion: Int?,
    val accentHex: String,
    val previewUrl: String,
    val previewUrls: PreviewUrlsDto = PreviewUrlsDto(),
    val bundleSha256: String? = null,
    val isOwned: Boolean,
    val isDownloaded: Boolean,
    val hasUpdate: Boolean,
    val state: StoreThemeState
) {
    val hasDiscount: Boolean
        get() = isPaid && discountPercent > 0 && originalPriceBdtPaisa > priceBdtPaisa
}

data class OrderHistoryEntry(
    val orderId: String,
    val paymentMethod: String,
    val expectedAmountBdt: String,
    val trxId: String,
    val themeIds: List<String>,
    val status: String,
    val createdAt: Long
)
