package org.futo.inputmethod.latin.themestore.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.Color as AndroidColor
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import kotlinx.serialization.json.Json
import org.futo.inputmethod.keyboard.Key
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.themestore.model.KeybirdThemeManifestV1
import org.futo.inputmethod.latin.themestore.model.ManifestArtPackV1
import org.futo.inputmethod.latin.themestore.model.ManifestMotionV1
import org.futo.inputmethod.latin.themestore.security.ThemeLicenseGuard
import org.futo.inputmethod.latin.themestore.storage.ThemeStoreDbHelper
import org.futo.inputmethod.latin.uix.isDirectBootUnlocked
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class CropBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun width(): Float = right - left
    fun height(): Float = bottom - top
    fun toRectF(): RectF = RectF(left, top, right, bottom)
}

data class StoreThemeRenderProfile(
    val themeId: String,
    val isLight: Boolean,
    @ColorInt val primaryBgColor: Int,
    @ColorInt val bgGradientColors: IntArray,
    @ColorInt val keyBgColor: Int,
    @ColorInt val keyPressedBgColor: Int,
    @ColorInt val functionalKeyBgColor: Int,
    @ColorInt val enterKeyBgColor: Int,
    @ColorInt val enterKeyFgColor: Int,
    @ColorInt val spacebarBgColor: Int,
    @ColorInt val spacebarStrokeColor: Int,
    @ColorInt val keyStrokeColor: Int,
    val keyStrokeWidthDp: Float,
    val keyCornerRadiusDp: Float,
    @ColorInt val keyTextColor: Int,
    @ColorInt val keyPressedTextColor: Int = keyTextColor,
    @ColorInt val keyHintColor: Int,
    @ColorInt val popupBgColor: Int,
    @ColorInt val popupTextColor: Int,
    @ColorInt val gestureTrailColor: Int,
    val fontStyle: String,
    /** Phase 2 effect preset (glass / motion / live / 3d_live_motion / touch / ...). */
    val effectMode: String = "glass",
    /** Phase 4 key geometry/material mode: classic / borderless / outline / glass / ceramic_3d / pill. */
    val keyStyle: String = "classic",
    val keyAlpha: Float = 1.0f,
    val rainbowText: Boolean = false,
    /** Phase 2: bottom readability scrim alpha (0..1) for art-pack capsules. */
    val scrimAlpha: Float = 0f,
    /** Phase 2: draw the frosted rim + outer glow on art-pack capsules. */
    val glassRimGlow: Boolean = false,
    /** Phase 2: per-key character artwork + backdrop/spacebar banner bundle. */
    val artPack: ManifestArtPackV1? = null,
    /** Phase 3: animated WebP background asset path (`assets/bg/live.webp`). */
    val bgAnimatedAsset: String = "",
    /** Phase 3: per-key multi-color palette (Matte Bauhaus / Hybrid Matte / Gaming WASD). */
    val keyColorPalette: IntArray = IntArray(0),
    val keyColorPatternMode: String = "none",
    val keyColorExplicitMap: Map<String, Int> = emptyMap(),
    val motion: ManifestMotionV1? = null
)

/**
 * Room 3: Manifest JSON parser, Material3 ColorScheme builder, YIQ contrast & scrim math,
 * and BasicThemeProvider visual delegate for downloaded Keybird themes.
 */
object ThemeManifestEngine {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parseManifest(manifestJson: String): KeybirdThemeManifestV1? =
        runCatching { json.decodeFromString<KeybirdThemeManifestV1>(manifestJson) }.getOrNull()

    fun parseHexColorOrNull(hex: String?): Int? {
        if (hex.isNullOrBlank()) return null
        val clean = hex.trim()
        if (!clean.startsWith("#")) return null
        val body = clean.substring(1)
        return runCatching {
            when (body.length) {
                6 -> (0xFF000000L or body.toLong(16)).toInt()
                8 -> body.toLong(16).toInt()
                3 -> {
                    val r = body[0].toString().repeat(2)
                    val g = body[1].toString().repeat(2)
                    val b = body[2].toString().repeat(2)
                    (0xFF000000L or "$r$g$b".toLong(16)).toInt()
                }
                else -> return null
            }
        }.getOrNull()
    }

    fun parseHexColor(hex: String?, @ColorInt fallback: Int): Int =
        parseHexColorOrNull(hex) ?: fallback

    /** Normalizes manifest `effect_mode` to one of the supported static/touch visual presets. */
    fun normalizeEffectMode(raw: String?): String {
        val clean = raw?.trim()?.lowercase().orEmpty()
        return when (clean) {
            "glass", "motion", "live", "3d_live_motion", "touch",
            "neon", "aurora", "holographic", "parallax", "ripple" -> clean
            else -> "glass"
        }
    }

    /** Normalizes manifest `key_style` across classic / borderless / outline / glass / ceramic_3d / pill. */
    fun normalizeKeyStyle(raw: String?): String {
        val clean = raw?.trim()?.lowercase().orEmpty()
        return when (clean) {
            "classic", "borderless", "outline", "glass", "ceramic_3d", "pill" -> clean
            else -> "classic"
        }
    }

    private val DARK_RAINBOW_GLYPHS = intArrayOf(
        0xFF00F5D4.toInt(), // Neon cyan
        0xFF38BDF8.toInt(), // Sky electric
        0xFF4ADE80.toInt(), // Neon green
        0xFFFACC15.toInt(), // Neon gold
        0xFFFB923C.toInt(), // Coral orange
        0xFFF472B6.toInt(), // Hot pink
        0xFFC084FC.toInt()  // Ultraviolet
    )
    private val LIGHT_RAINBOW_GLYPHS = intArrayOf(
        0xFF0284C7.toInt(),
        0xFF059669.toInt(),
        0xFFD97706.toInt(),
        0xFFDB2777.toInt(),
        0xFF7C3AED.toInt(),
        0xFFDC2626.toInt(),
        0xFF0F766E.toInt()
    )

    /** Computes crisp white/dark foreground color using YIQ perceived luminance. */
    @ColorInt
    fun yiqContrastTextColor(@ColorInt bgColor: Int): Int {
        val r = (bgColor ushr 16) and 0xFF
        val g = (bgColor ushr 8) and 0xFF
        val b = bgColor and 0xFF
        val yiq = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        return if (yiq >= 0.55) 0xFF121212.toInt() else 0xFFFFFFFF.toInt()
    }

    private fun relativeLuminance(@ColorInt color: Int): Double {
        fun ch(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * ch((color ushr 16) and 0xFF) +
            0.7152 * ch((color ushr 8) and 0xFF) +
            0.0722 * ch(color and 0xFF)
    }

    private fun wcagContrast(@ColorInt fg: Int, @ColorInt bg: Int): Double {
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }

    /** Ensures language label on spacebar stays legible over [StoreThemeRenderProfile.spacebarBgColor]. */
    @ColorInt
    fun resolveSpacebarLabelColor(profile: StoreThemeRenderProfile): Int {
        val bg = profile.spacebarBgColor
        if (wcagContrast(profile.spacebarStrokeColor, bg) >= 3.0) return profile.spacebarStrokeColor
        if (wcagContrast(profile.keyTextColor, bg) >= 3.0) return profile.keyTextColor
        return yiqContrastTextColor(bg)
    }

    fun toColorScheme(manifest: KeybirdThemeManifestV1): ColorScheme {
        val c = manifest.colors
        val primary = Color(parseHexColor(c.primary, 0xFF00E676.toInt()))
        val onPrimary = Color(parseHexColor(c.onPrimary, 0xFF000000.toInt()))
        val primaryContainer = Color(parseHexColor(c.primaryContainer, 0xFF1B1B1F.toInt()))
        val onPrimaryContainer = Color(parseHexColor(c.onPrimaryContainer, 0xFFFFFFFF.toInt()))
        val secondary = Color(parseHexColor(c.secondary, 0xFF00B0FF.toInt()))
        val onSecondary = Color(parseHexColor(c.onSecondary, 0xFF000000.toInt()))
        val secondaryContainer = Color(parseHexColor(c.secondaryContainer, 0xFF1B1B1F.toInt()))
        val onSecondaryContainer = Color(parseHexColor(c.onSecondaryContainer, 0xFFFFFFFF.toInt()))
        val background = Color(parseHexColor(c.background, 0xFF000000.toInt()))
        val onBackground = Color(parseHexColor(c.onBackground, 0xFFFFFFFF.toInt()))
        val surface = Color(parseHexColor(c.surface, 0xFF0E0E11.toInt()))
        val onSurface = Color(parseHexColor(c.onSurface, 0xFFFFFFFF.toInt()))
        val surfaceVariant = Color(parseHexColor(c.surfaceVariant, 0xFF1B1B1F.toInt()))
        val onSurfaceVariant = Color(parseHexColor(c.onSurfaceVariant, 0xFF9CA3AF.toInt()))
        val outline = Color(parseHexColor(c.outline, 0xFF26262B.toInt()))

        return if (manifest.isLight) {
            lightColorScheme(
                primary = primary,
                onPrimary = onPrimary,
                primaryContainer = primaryContainer,
                onPrimaryContainer = onPrimaryContainer,
                secondary = secondary,
                onSecondary = onSecondary,
                secondaryContainer = secondaryContainer,
                onSecondaryContainer = onSecondaryContainer,
                tertiary = primary,
                onTertiary = onPrimary,
                background = background,
                onBackground = onBackground,
                surface = surface,
                onSurface = onSurface,
                surfaceVariant = surfaceVariant,
                onSurfaceVariant = onSurfaceVariant,
                outline = outline
            )
        } else {
            darkColorScheme(
                primary = primary,
                onPrimary = onPrimary,
                primaryContainer = primaryContainer,
                onPrimaryContainer = onPrimaryContainer,
                secondary = secondary,
                onSecondary = onSecondary,
                secondaryContainer = secondaryContainer,
                onSecondaryContainer = onSecondaryContainer,
                tertiary = primary,
                onTertiary = onPrimary,
                background = background,
                onBackground = onBackground,
                surface = surface,
                onSurface = onSurface,
                surfaceVariant = surfaceVariant,
                onSurfaceVariant = onSurfaceVariant,
                outline = outline
            )
        }
    }

    fun toRenderProfile(manifest: KeybirdThemeManifestV1): StoreThemeRenderProfile {
        val k = manifest.keyboard
        val bgColors = k.bgColors.mapNotNull { hex ->
            parseHexColorOrNull(hex)
        }.ifEmpty {
            listOf(parseHexColor(manifest.colors.background, 0xFF000000.toInt()))
        }
        val keyBg = parseHexColor(k.keyBg, 0xFF1B1B1F.toInt())
        val keyPressedBg = parseHexColor(k.keyPressedBg, 0xFF2E2E38.toInt())
        val enterBg = parseHexColor(k.enterKeyBg, parseHexColor(manifest.colors.primary, 0xFF00E676.toInt()))
        val keyText = parseHexColor(k.keyText, yiqContrastTextColor(keyBg))
        val keyPressedText = parseHexColorOrNull(k.keyPressedText) ?: yiqContrastTextColor(keyPressedBg)
        // Heal themes cached locally under v4.1.0 where encodeDefaults baked #000000/#1B1B1F/#00E676 into non-green manifests.
        val hasBakedV410Defaults = enterBg != 0xFF00E676.toInt() &&
            k.enterKeyFg.equals("#000000", ignoreCase = true) &&
            k.spacebarBg.equals("#1B1B1F", ignoreCase = true) &&
            k.spacebarStroke.equals("#00E676", ignoreCase = true)
        val rawEnterFg = if (hasBakedV410Defaults) {
            yiqContrastTextColor(enterBg)
        } else {
            parseHexColor(k.enterKeyFg, yiqContrastTextColor(enterBg))
        }
        val safeEnterFg = if (wcagContrast(rawEnterFg, enterBg) < 3.0) yiqContrastTextColor(enterBg) else rawEnterFg
        val resolvedSpaceBg = if (hasBakedV410Defaults) keyBg else parseHexColor(k.spacebarBg, keyBg)
        val resolvedSpaceStroke = if (hasBakedV410Defaults) enterBg else parseHexColor(k.spacebarStroke, enterBg)
        val resolvedTrail = if (hasBakedV410Defaults && k.gestureTrail.equals("#00E676", ignoreCase = true)) {
            enterBg
        } else {
            parseHexColor(k.gestureTrail, enterBg)
        }
        val rawMap = k.keyColorMap
        val palette = rawMap?.palette?.mapNotNull { parseHexColorOrNull(it) }?.toIntArray() ?: IntArray(0)
        val patternMode = rawMap?.patternMode?.trim()?.lowercase().orEmpty().ifBlank { "none" }
        val explicitKeys = rawMap?.keys?.entries?.mapNotNull { (rawKey, rawHex) ->
            val color = parseHexColorOrNull(rawHex) ?: return@mapNotNull null
            rawKey.trim().lowercase() to color
        }?.toMap().orEmpty()
        val liveAsset = k.bgAnimatedAsset.trim().ifBlank {
            k.motion?.bgAnimatedAsset?.trim().orEmpty()
        }
        return StoreThemeRenderProfile(
            themeId = manifest.themeId,
            isLight = manifest.isLight,
            primaryBgColor = bgColors.first(),
            bgGradientColors = bgColors.toIntArray(),
            keyBgColor = keyBg,
            keyPressedBgColor = keyPressedBg,
            functionalKeyBgColor = parseHexColor(k.functionalKeyBg, 0xFF141418.toInt()),
            enterKeyBgColor = enterBg,
            enterKeyFgColor = safeEnterFg,
            spacebarBgColor = resolvedSpaceBg,
            spacebarStrokeColor = resolvedSpaceStroke,
            keyStrokeColor = parseHexColor(k.keyStroke, 0xFF2B2D35.toInt()),
            keyStrokeWidthDp = k.keyStrokeWidthDp.coerceIn(0f, 3f),
            keyCornerRadiusDp = k.keyCornerRadiusDp.coerceIn(0f, 24f),
            keyTextColor = keyText,
            keyPressedTextColor = keyPressedText,
            keyHintColor = parseHexColor(k.keyHint, 0xFF9CA3AF.toInt()),
            popupBgColor = parseHexColor(k.popupBg, 0xFF1B1B1F.toInt()),
            popupTextColor = parseHexColor(k.popupText, 0xFFFFFFFF.toInt()),
            gestureTrailColor = resolvedTrail,
            fontStyle = k.fontStyle,
            effectMode = k.effectMode.ifBlank { "glass" },
            keyStyle = normalizeKeyStyle(k.keyStyle),
            keyAlpha = if (k.keyAlpha.isFinite()) k.keyAlpha.coerceIn(0f, 1f) else 1f,
            rainbowText = k.rainbowText,
            scrimAlpha = k.scrimAlpha.coerceIn(0f, 1f),
            glassRimGlow = k.glassRimGlow,
            artPack = manifest.artPack,
            bgAnimatedAsset = liveAsset,
            keyColorPalette = palette,
            keyColorPatternMode = patternMode,
            keyColorExplicitMap = explicitKeys,
            motion = k.motion
        )
    }

    fun resolveKeyFillColor(
        profile: StoreThemeRenderProfile,
        key: Key
    ): Int? = resolveKeyFillColor(
        profile = profile,
        code = key.code,
        label = key.label,
        x = key.x,
        y = key.y,
        width = key.width,
        height = key.height
    )

    /**
     * Resolves per-key custom fill color for Matte Bauhaus, Hybrid Matte, Gaming WASD, and
     * explicit `key_color_map.keys` across English and Bangla layouts.
     */
    fun resolveKeyFillColor(
        profile: StoreThemeRenderProfile,
        code: Int,
        label: String?,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ): Int? {
        val lowerLabel = label?.trim()?.lowercase()
        val exactId = when (code) {
            Constants.CODE_SPACE -> "k_space"
            Constants.CODE_ENTER -> "k_enter"
            Constants.CODE_SHIFT -> "k_shift"
            Constants.CODE_DELETE -> "k_delete"
            Constants.CODE_SWITCH_ALPHA_SYMBOL -> "k_sym"
            Constants.CODE_EMOJI, Constants.CODE_SHORTCUT -> "k_emoji"
            else -> if (!lowerLabel.isNullOrBlank()) "k_$lowerLabel" else null
        }
        val explicit = profile.keyColorExplicitMap
        if (exactId != null && explicit.containsKey(exactId)) {
            return explicit[exactId]
        }
        if (!lowerLabel.isNullOrBlank() && explicit.containsKey(lowerLabel)) {
            return explicit[lowerLabel]
        }
        val row = if (height > 0) (y / height).coerceAtLeast(0) else 0
        val col = if (width > 0) (x / width).coerceAtLeast(0) else 0
        if (height > 0 && width > 0) {
            val slotKey = "slot_r${row}_c${col}"
            if (explicit.containsKey(slotKey)) {
                return explicit[slotKey]
            }
        }

        val isFunctional = code == Constants.CODE_SPACE ||
            code == Constants.CODE_ENTER ||
            code == Constants.CODE_SHIFT ||
            code == Constants.CODE_DELETE ||
            code == Constants.CODE_SWITCH_ALPHA_SYMBOL ||
            code == Constants.CODE_EMOJI ||
            code == Constants.CODE_SHORTCUT
        if (isFunctional) return null

        val palette = profile.keyColorPalette
        return when (profile.keyColorPatternMode) {
            "wasd", "wasd_accent" -> {
                val isWasdLabel = lowerLabel == "w" || lowerLabel == "a" || lowerLabel == "s" || lowerLabel == "d"
                val isWasdSlot = height > 0 && width > 0 && ((row == 0 && col == 1) || (row == 1 && col in 0..2))
                if (isWasdLabel || isWasdSlot) {
                    palette.getOrNull(1) ?: palette.firstOrNull() ?: profile.enterKeyBgColor
                } else {
                    null
                }
            }
            "bauhaus_cycle", "hybrid_matte", "cycle", "multi" -> {
                if (palette.isEmpty()) null else {
                    val idx = if (height > 0 && width > 0) {
                        (row * 3 + col).mod(palette.size)
                    } else {
                        ((lowerLabel?.hashCode() ?: code) and 0x7FFFFFFF) % palette.size
                    }
                    palette[idx]
                }
            }
            "row_cycle", "row_gradient" -> {
                if (palette.isEmpty()) null else palette[row.mod(palette.size)]
            }
            else -> null
        }
    }

    @ColorInt
    fun resolveKeyTextColor(
        profile: StoreThemeRenderProfile,
        code: Int,
        label: String?,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ): Int {
        if (code == Constants.CODE_ENTER) return profile.enterKeyFgColor
        val customFill = resolveKeyFillColor(profile, code, label, x, y, width, height)
        if (customFill != null) return yiqContrastTextColor(customFill)
        val isFunctional = code == Constants.CODE_SPACE ||
            code == Constants.CODE_SHIFT ||
            code == Constants.CODE_DELETE ||
            code == Constants.CODE_SWITCH_ALPHA_SYMBOL ||
            code == Constants.CODE_EMOJI ||
            code == Constants.CODE_SHORTCUT
        if (profile.rainbowText && !isFunctional) {
            val palette = if (profile.isLight) LIGHT_RAINBOW_GLYPHS else DARK_RAINBOW_GLYPHS
            val col = if (width > 0) (x / width).coerceAtLeast(0) else ((label?.hashCode() ?: code) and 0x7FFFFFFF)
            val row = if (height > 0) (y / height).coerceAtLeast(0) else 0
            return palette[(col + row).mod(palette.size)]
        }
        return profile.keyTextColor
    }

    @ColorInt
    fun resolveKeyHintColor(
        profile: StoreThemeRenderProfile,
        code: Int,
        label: String?,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ): Int {
        val customFill = resolveKeyFillColor(profile, code, label, x, y, width, height)
        if (customFill != null) {
            return ColorUtils.setAlphaComponent(yiqContrastTextColor(customFill), 178)
        }
        return profile.keyHintColor
    }

    fun computeCenterCropRect(
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Int,
        dstHeight: Int,
        offsetLeft: Float = 0f,
        offsetTop: Float = 0f
    ): CropBounds {
        if (srcWidth <= 0 || srcHeight <= 0 || dstWidth <= 0 || dstHeight <= 0) {
            return CropBounds(offsetLeft, offsetTop, offsetLeft + dstWidth.coerceAtLeast(0), offsetTop + dstHeight.coerceAtLeast(0))
        }
        val scale = max(dstWidth.toFloat() / srcWidth.toFloat(), dstHeight.toFloat() / srcHeight.toFloat())
        val dw = srcWidth * scale
        val dh = srcHeight * scale
        val left = offsetLeft + (dstWidth - dw) / 2f
        val top = offsetTop + (dstHeight - dh) / 2f
        return CropBounds(left, top, left + dw, top + dh)
    }

    fun shouldAnimateLiveBackground(
        hasLiveContent: Boolean,
        isVisible: Boolean,
        isPowerSaveMode: Boolean,
        animatorDurationScale: Float
    ): Boolean = hasLiveContent && isVisible && !isPowerSaveMode && animatorDurationScale > 0f

    /**
     * Slot/group fallback resolver ready for Phase 2 Art-Packs across English & Bangla layouts.
     */
    fun resolveArtAssetKey(
        keymap: Map<String, String>,
        groupFallbacks: Map<String, String>,
        key: Key
    ): String? = resolveArtAssetKey(
        keymap = keymap,
        groupFallbacks = groupFallbacks,
        code = key.code,
        label = key.label,
        x = key.x,
        y = key.y,
        width = key.width,
        height = key.height
    )

    /**
     * Primitive overload kept JVM-unit-testable (no Android [Key] construction needed).
     * Resolution order: exact `k_<label>`/code key, then Bangla `slot_r<row>_c<col>`,
     * then `group_fallbacks`.
     */
    fun resolveArtAssetKey(
        keymap: Map<String, String>,
        groupFallbacks: Map<String, String>,
        code: Int,
        label: String?,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ): String? {
        val lowerLabel = label?.lowercase()
        val exactId = when (code) {
            Constants.CODE_SPACE -> "k_space"
            Constants.CODE_ENTER -> "k_enter"
            Constants.CODE_SHIFT -> "k_shift"
            Constants.CODE_DELETE -> "k_delete"
            Constants.CODE_SWITCH_ALPHA_SYMBOL -> "k_sym"
            Constants.CODE_EMOJI, Constants.CODE_SHORTCUT -> "k_emoji"
            else -> if (!lowerLabel.isNullOrBlank()) "k_$lowerLabel" else null
        }
        if (exactId != null && keymap.containsKey(exactId)) {
            return keymap[exactId]
        }

        if (height > 0 && width > 0) {
            val row = (y / height).coerceAtLeast(0)
            val col = (x / width).coerceAtLeast(0)
            val slotKey = "slot_r${row}_c${col}"
            if (keymap.containsKey(slotKey)) {
                return keymap[slotKey]
            }
        }

        val group = when (code) {
            Constants.CODE_ENTER -> "accent"
            Constants.CODE_SHIFT, Constants.CODE_DELETE,
            Constants.CODE_SWITCH_ALPHA_SYMBOL, Constants.CODE_EMOJI,
            Constants.CODE_SHORTCUT -> "functional"
            Constants.CODE_SPACE -> "space"
            else -> "letter"
        }
        return groupFallbacks[group] ?: groupFallbacks["default"]
    }
}

/** User-facing customization sliders blended on top of Store Themes when not locked. */
data class KeyStyleOverrides(
    val keyBorders: Boolean = true,
    val keyOpacity: Float = 1f,
    val keyShape: String = "classic"
)

/**
 * Reads an art-pack asset's raw bytes for a theme. Implementations must be safe against
 * missing/encrypted-bundle failures (return null to fall back to code-drawn rendering).
 */
interface ArtAssetReader {
    fun readAsset(themeId: String, isPaid: Boolean, assetPath: String): ByteArray?
}

/**
 * Reads device-bound encrypted `.iplustheme` bundles from [ThemeStoreDbHelper], decrypting
 * once per theme into a small ZIP cache before extracting individual assets.
 */
class DbArtAssetReader(private val context: Context) : ArtAssetReader {
    private val zipCache = object : LruCache<String, ByteArray>(4 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = (value.size / 1024).coerceAtLeast(1)
    }

    override fun readAsset(themeId: String, isPaid: Boolean, assetPath: String): ByteArray? {
        if (!context.isDirectBootUnlocked) return null
        val deviceHash = ThemeLicenseGuard.computeDeviceHash(context)
        val zip = zipCache.get(themeId) ?: run {
            val bytes = ThemeStoreDbHelper.getInstance(context).readArtBundle(themeId, isPaid, deviceHash)
            if (bytes != null) zipCache.put(themeId, bytes)
            bytes
        } ?: return null
        return ArtBundleCodec.extractAsset(zip, assetPath)
    }
}

/** Applies [opacity] (0..1) to an existing color's alpha channel. */
private fun blendAlpha(@ColorInt color: Int, opacity: Float): Int {
    val o = if (opacity.isFinite()) opacity.coerceIn(0f, 1f) else 1f
    return ColorUtils.setAlphaComponent(color, (AndroidColor.alpha(color) * o).roundToInt())
}

class StoreKeyVisualDelegate(
    private val context: Context,
    val profile: StoreThemeRenderProfile,
    private val style: KeyStyleOverrides = KeyStyleOverrides(),
    artAssetReader: ArtAssetReader? = null
) {
    private val artAssetReader: ArtAssetReader = artAssetReader ?: DbArtAssetReader(context)

    /** Whether the theme's art bundle is stored encrypted (paid) or plaintext-dir (free). */
    private val isPaid: Boolean = runCatching {
        ThemeStoreDbHelper.getInstance(context).getInstalledTheme(profile.themeId)?.isPaid
    }.getOrNull() ?: false

    private val strokePx = dp(profile.keyStrokeWidthDp).roundToInt().coerceAtLeast(0)

    private val normalizedEffectMode = ThemeManifestEngine.normalizeEffectMode(profile.effectMode)

    /** "touch" / "ripple" / "3d_live_motion" presets get an amplified pressed-state response. */
    private val touchBoost =
        normalizedEffectMode == "touch" ||
            normalizedEffectMode == "ripple" ||
            normalizedEffectMode == "3d_live_motion"

    /** User's key-shape slider blended on top of the manifest's own corner radius. */
    private fun effectiveRadiusDp(): Float = when (style.keyShape) {
        "sharp" -> 2f
        "rounded" -> 14f
        else -> profile.keyCornerRadiusDp
    }

    private val cornerPx = if (profile.keyStyle == "pill") dp(128f) else dp(effectiveRadiusDp())

    private val touchFxController = StoreTouchFxController(context, profile, normalizedEffectMode)

    fun onKeyPressedVisualEffect(key: Key, centerX: Float, centerY: Float) {
        touchFxController.onKeyPressed(key, centerX, centerY)
    }

    fun drawOverlayVisualEffects(canvas: Canvas, view: View): Boolean =
        touchFxController.draw(canvas, view)

    /** Decoded art bitmaps (keyed by bundle asset path), sized in KB. */
    private val bitmapCache = object : LruCache<String, Bitmap>(8 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }

    private fun loadBitmap(assetPath: String): Bitmap? {
        bitmapCache.get(assetPath)?.let { return it }
        val bytes = artAssetReader.readAsset(profile.themeId, isPaid, assetPath) ?: return null
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        bitmapCache.put(assetPath, bmp)
        return bmp
    }

    private fun dp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics)

    private fun roundedRect(
        @ColorInt fill: Int,
        radiusPx: Float,
        @ColorInt strokeColor: Int = 0,
        strokeWidth: Int = 0
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radiusPx
        setColor(fill)
        if (strokeWidth > 0 && strokeColor != 0) {
            setStroke(strokeWidth, strokeColor)
        }
    }

    fun resolveTypefaceOrNull(): Typeface? = when (profile.fontStyle) {
        "doto", "pixel" -> ResourcesCompat.getFont(context, R.font.doto)
        "inter" -> ResourcesCompat.getFont(context, R.font.inter)
        "tech_mono" -> Typeface.create("monospace", Typeface.NORMAL)
        "elegant" -> Typeface.create("serif", Typeface.NORMAL)
        "bold" -> Typeface.create("sans-serif-black", Typeface.NORMAL)
        "clean" -> Typeface.create("sans-serif", Typeface.NORMAL)
        else -> null
    }

    fun createKeyboardBackground(): Drawable {
        // Phase 2/3: art-pack full backdrop renders center-cropped (never stretched by Gravity.FILL).
        val baseBackdrop: Drawable = profile.artPack?.bgFullAsset?.let { path ->
            loadBitmap(path)?.let { bmp -> CenterCropBitmapDrawable(bmp) }
        } ?: if (profile.bgGradientColors.size >= 2) {
            GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, profile.bgGradientColors)
        } else {
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(profile.primaryBgColor)
            }
        }

        val liveBytes = profile.bgAnimatedAsset.takeIf { it.isNotBlank() }?.let { path ->
            artAssetReader.readAsset(profile.themeId, isPaid, path)
        }
        val hasParticles = profile.motion?.particles == true && (profile.motion.particleDensity > 0)
        val hasLiveShader = normalizedEffectMode == "live" ||
            normalizedEffectMode == "motion" ||
            normalizedEffectMode == "3d_live_motion" ||
            normalizedEffectMode == "aurora"
        if (liveBytes != null || hasParticles || hasLiveShader) {
            return LiveKeyboardBackgroundDrawable(
                context = context,
                baseDrawable = baseBackdrop,
                animatedWebpBytes = liveBytes,
                profile = profile,
                effectMode = normalizedEffectMode
            )
        }
        return baseBackdrop
    }

    fun createKeyBackgroundStateList(): StateListDrawable {
        val baseOpacity = when (profile.keyStyle) {
            "borderless" -> 0f
            "outline" -> (style.keyOpacity * profile.keyAlpha).coerceAtMost(0.16f)
            "glass" -> (style.keyOpacity * profile.keyAlpha).coerceIn(0.30f, 0.68f)
            else -> style.keyOpacity * profile.keyAlpha
        }
        val alphaFill = { color: Int ->
            if (!style.keyBorders || profile.keyStyle == "borderless") AndroidColor.TRANSPARENT
            else blendAlpha(color, baseOpacity)
        }
        val strokeW = when {
            !style.keyBorders || profile.keyStyle == "borderless" -> 0
            profile.keyStyle == "outline" -> strokePx.coerceAtLeast(dp(1.2f).roundToInt())
            else -> strokePx
        }
        val normal = roundedRect(alphaFill(profile.keyBgColor), cornerPx, profile.keyStrokeColor, strokeW)
        val pressedFill = if (profile.keyStyle == "borderless") {
            ColorUtils.setAlphaComponent(profile.spacebarStrokeColor, 0x3A)
        } else {
            profile.keyPressedBgColor
        }
        val pressed = roundedRect(pressedFill, cornerPx, profile.spacebarStrokeColor, strokeW)
        val functional = roundedRect(alphaFill(profile.functionalKeyBgColor), cornerPx, profile.keyStrokeColor, strokeW)
        val enterFill = if (profile.keyStyle == "borderless") {
            ColorUtils.setAlphaComponent(profile.enterKeyBgColor, 0x33)
        } else {
            profile.enterKeyBgColor
        }
        val enter = roundedRect(enterFill, dp(128f))
        val enterPressed = LayerDrawable(arrayOf(enter, roundedRect(0x33FFFFFF, dp(128f))))

        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed, android.R.attr.state_active), enterPressed)
            addState(intArrayOf(android.R.attr.state_active), enter)
            addState(intArrayOf(android.R.attr.state_pressed, android.R.attr.state_first), pressed)
            addState(intArrayOf(android.R.attr.state_first), functional)
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

    fun createSpaceBarBackground(): StateListDrawable {
        // Phase 2: art-pack spacebar banner composes into a capsule with rim + scrim.
        profile.artPack?.spacebarAsset?.let { path ->
            val bmp = loadBitmap(path)
            if (bmp != null) {
                val normal = ArtPackKeyBackgroundDrawable(
                    bitmap = bmp,
                    radiusPx = cornerPx,
                    rimColor = profile.spacebarStrokeColor,
                    rimWidthPx = if (style.keyBorders) dp(profile.keyStrokeWidthDp).coerceAtLeast(dp(1.2f)) else 0f,
                    scrimAlpha = profile.scrimAlpha,
                    glow = profile.glassRimGlow,
                    pressed = false,
                    pressedOverlay = profile.keyPressedBgColor,
                    opacity = style.keyOpacity,
                    touchBoost = touchBoost,
                    effectMode = normalizedEffectMode
                )
                val pressed = ArtPackKeyBackgroundDrawable(
                    bitmap = bmp,
                    radiusPx = cornerPx,
                    rimColor = profile.spacebarStrokeColor,
                    rimWidthPx = if (style.keyBorders) dp(profile.keyStrokeWidthDp).coerceAtLeast(dp(1.5f)) else 0f,
                    scrimAlpha = profile.scrimAlpha,
                    glow = profile.glassRimGlow,
                    pressed = true,
                    pressedOverlay = profile.keyPressedBgColor,
                    opacity = style.keyOpacity,
                    touchBoost = touchBoost,
                    effectMode = normalizedEffectMode
                )
                return StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_pressed), pressed)
                    addState(intArrayOf(), normal)
                }
            }
        }

        if (profile.keyStyle in setOf("outline", "glass", "ceramic_3d", "borderless", "pill")) {
            val spaceFill = if (profile.keyStyle == "borderless") {
                ColorUtils.setAlphaComponent(profile.spacebarStrokeColor, 0x20)
            } else {
                profile.spacebarBgColor
            }
            val spaceAlpha = when (profile.keyStyle) {
                "borderless" -> 0.25f
                "outline" -> (style.keyOpacity * profile.keyAlpha).coerceAtMost(0.16f)
                "glass" -> (style.keyOpacity * profile.keyAlpha).coerceIn(0.30f, 0.68f)
                else -> style.keyOpacity * profile.keyAlpha
            }
            val rimW = if (profile.keyStyle == "borderless") dp(0.9f) else dp(profile.keyStrokeWidthDp).coerceAtLeast(dp(1.2f))
            val normal = StyledColorKeyCapsuleDrawable(
                fillColor = spaceFill,
                radiusPx = cornerPx,
                rimColor = profile.spacebarStrokeColor,
                rimWidthPx = rimW,
                glow = profile.glassRimGlow || profile.keyStyle == "outline",
                pressed = false,
                pressedOverlay = profile.keyPressedBgColor,
                opacity = spaceAlpha,
                touchBoost = touchBoost,
                effectMode = normalizedEffectMode,
                keyStyle = if (profile.keyStyle == "borderless") "glass" else profile.keyStyle
            )
            val pressed = StyledColorKeyCapsuleDrawable(
                fillColor = spaceFill,
                radiusPx = cornerPx,
                rimColor = profile.spacebarStrokeColor,
                rimWidthPx = dp(profile.keyStrokeWidthDp).coerceAtLeast(dp(1.5f)),
                glow = profile.glassRimGlow || profile.keyStyle == "outline",
                pressed = true,
                pressedOverlay = profile.keyPressedBgColor,
                opacity = spaceAlpha.coerceAtLeast(0.45f),
                touchBoost = true,
                effectMode = normalizedEffectMode,
                keyStyle = if (profile.keyStyle == "borderless") "glass" else profile.keyStyle
            )
            return StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), pressed)
                addState(intArrayOf(), normal)
            }
        }

        val normal = roundedRect(
            fill = blendAlpha(profile.spacebarBgColor, style.keyOpacity * profile.keyAlpha),
            radiusPx = cornerPx,
            strokeColor = profile.spacebarStrokeColor,
            strokeWidth = strokePx.coerceAtLeast(dp(1.2f).roundToInt())
        )
        val pressed = roundedRect(
            fill = profile.keyPressedBgColor,
            radiusPx = cornerPx,
            strokeColor = profile.spacebarStrokeColor,
            strokeWidth = strokePx.coerceAtLeast(dp(1.5f).roundToInt())
        )
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

    /**
     * Phase 2: resolves the key's art asset (`k_a`..`k_m`, `k_shift`, `slot_r*_c*`, group
     * fallbacks) and composes it inside a rounded capsule with the code-drawn frosted rim,
     * outer glow, bottom readability scrim, and pressed highlight. Returns null when the
     * theme has no art-pack or the asset is unavailable (falls back to code-drawn colors).
     */
    fun getArtPackKeyBackground(key: Key, isPressed: Boolean): Drawable? {
        val art = profile.artPack ?: return null
        // Spacebar is rendered separately via createSpaceBarBackground() (banner + its own rim).
        if (key.code == Constants.CODE_SPACE) return null
        val assetPath = ThemeManifestEngine.resolveArtAssetKey(art.keymap, art.groupFallbacks, key) ?: return null
        val bmp = loadBitmap(assetPath) ?: return null
        return ArtPackKeyBackgroundDrawable(
            bitmap = bmp,
            radiusPx = cornerPx,
            rimColor = profile.keyStrokeColor,
            rimWidthPx = if (style.keyBorders) dp(profile.keyStrokeWidthDp).coerceAtLeast(1f) else 0f,
            scrimAlpha = profile.scrimAlpha,
            glow = profile.glassRimGlow,
            pressed = isPressed,
            pressedOverlay = profile.keyPressedBgColor,
            opacity = style.keyOpacity,
            touchBoost = touchBoost,
            effectMode = normalizedEffectMode
        )
    }

    fun createKeyFeedback(widthPx: Int, heightPx: Int, bottomPaddingPx: Int): Drawable =
        ShapeDrawable().apply {
            paint.color = profile.popupBgColor
            shape = RoundRectShape(
                floatArrayOf(cornerPx, cornerPx, cornerPx, cornerPx, cornerPx, cornerPx, cornerPx, cornerPx),
                null,
                null
            )
            intrinsicWidth = widthPx.coerceAtLeast(1)
            intrinsicHeight = heightPx.coerceAtLeast(1)
            setPadding(0, 0, 0, bottomPaddingPx.coerceAtLeast(0))
        }

    fun createPopupKey(pill: Boolean): Drawable {
        val r = if (pill) dp(128f) else cornerPx
        val normal = roundedRect(profile.popupBgColor, r, profile.keyStrokeColor, strokePx)
        val pressed = roundedRect(profile.keyPressedBgColor, r, profile.spacebarStrokeColor, strokePx)
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

    /**
     * Phase 3 unified key background: returns an art-pack capsule when the key has a character/group
     * bitmap, or a code-drawn Hybrid/Glass/Bauhaus Matte capsule when the theme uses `key_color_map`,
     * `glass_rim_glow`, or rich `effect_mode` / `motion.pressEffect`.
     */
    fun getKeyBackground(key: Key, isPressed: Boolean): Drawable? {
        getArtPackKeyBackground(key, isPressed)?.let { return it }
        if (key.code == Constants.CODE_SPACE) return null
        if (!style.keyBorders && profile.keyStyle != "borderless") return null

        val customFill = ThemeManifestEngine.resolveKeyFillColor(profile, key)
        val motionPressEffect = profile.motion?.pressEffect?.takeIf { it.isNotBlank() }
            ?.let { ThemeManifestEngine.normalizeEffectMode(it) }
        val activeEffect = if (isPressed && motionPressEffect != null) motionPressEffect else normalizedEffectMode
        val styledMode = profile.keyStyle != "classic"
        val needsCustomCapsule = styledMode ||
            customFill != null ||
            profile.glassRimGlow ||
            activeEffect != "glass" ||
            !profile.motion?.pressColor.isNullOrBlank()
        if (!needsCustomCapsule) return null

        val isEnter = key.code == Constants.CODE_ENTER
        val isFunctional = key.code == Constants.CODE_SHIFT ||
            key.code == Constants.CODE_DELETE ||
            key.code == Constants.CODE_SWITCH_ALPHA_SYMBOL ||
            key.code == Constants.CODE_EMOJI ||
            key.code == Constants.CODE_SHORTCUT
        val baseFill = when {
            isEnter -> profile.enterKeyBgColor
            customFill != null -> customFill
            isFunctional -> profile.functionalKeyBgColor
            else -> profile.keyBgColor
        }
        val pressColor = ThemeManifestEngine.parseHexColorOrNull(profile.motion?.pressColor)
            ?: profile.keyPressedBgColor
        val rimColor = if (isEnter || customFill != null) profile.spacebarStrokeColor else profile.keyStrokeColor
        val effectiveOpacity = when (profile.keyStyle) {
            "borderless" -> 0f
            "outline" -> (style.keyOpacity * profile.keyAlpha).coerceAtMost(if (isEnter) 0.55f else 0.16f)
            "glass" -> (style.keyOpacity * profile.keyAlpha).coerceIn(0.28f, if (isEnter) 0.88f else 0.66f)
            else -> if (profile.glassRimGlow && customFill == null && !isEnter) {
                (style.keyOpacity * profile.keyAlpha * 0.80f).coerceIn(0.42f, 0.94f)
            } else {
                style.keyOpacity * profile.keyAlpha
            }
        }
        val minRim = if (profile.keyStyle == "outline" || profile.glassRimGlow) dp(1.1f) else 0f
        return StyledColorKeyCapsuleDrawable(
            fillColor = baseFill,
            radiusPx = if (isEnter || profile.keyStyle == "pill") dp(128f) else cornerPx,
            rimColor = rimColor,
            rimWidthPx = if (profile.keyStyle == "borderless") 0f else dp(profile.keyStrokeWidthDp).coerceAtLeast(minRim),
            glow = profile.glassRimGlow || profile.keyStyle == "outline",
            pressed = isPressed,
            pressedOverlay = pressColor,
            opacity = effectiveOpacity,
            touchBoost = touchBoost || activeEffect == "ripple" || activeEffect == "touch",
            effectMode = activeEffect,
            keyStyle = profile.keyStyle
        )
    }

    fun getKeyTextColor(key: Key): Int =
        ThemeManifestEngine.resolveKeyTextColor(
            profile = profile,
            code = key.code,
            label = key.label,
            x = key.x,
            y = key.y,
            width = key.width,
            height = key.height
        )

    fun getKeyHintColor(): Int = profile.keyHintColor

    fun getKeyHintColor(key: Key): Int =
        ThemeManifestEngine.resolveKeyHintColor(
            profile = profile,
            code = key.code,
            label = key.label,
            x = key.x,
            y = key.y,
            width = key.width,
            height = key.height
        )

    private class CenterCropBitmapDrawable(
        private val bitmap: Bitmap
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            val crop = ThemeManifestEngine.computeCenterCropRect(
                srcWidth = bitmap.width,
                srcHeight = bitmap.height,
                dstWidth = b.width(),
                dstHeight = b.height(),
                offsetLeft = b.left.toFloat(),
                offsetTop = b.top.toFloat()
            )
            val save = canvas.save()
            canvas.clipRect(b)
            canvas.drawBitmap(bitmap, null, crop.toRectF(), paint)
            canvas.restoreToCount(save)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }

    private class StyledColorKeyCapsuleDrawable(
        @ColorInt private val fillColor: Int,
        private val radiusPx: Float,
        @ColorInt private val rimColor: Int,
        private val rimWidthPx: Float,
        private val glow: Boolean,
        private val pressed: Boolean,
        @ColorInt private val pressedOverlay: Int,
        private val opacity: Float,
        private val touchBoost: Boolean,
        private val effectMode: String,
        private val keyStyle: String = "classic"
    ) : Drawable() {
        private val opacityClamped = if (opacity.isFinite()) opacity.coerceIn(0f, 1f) else 1f
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = blendAlpha(fillColor, opacityClamped)
        }
        private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = rimWidthPx
            color = rimColor
        }
        private val specPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val clipPath = Path()
        private val rect = RectF()

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            val r = radiusPx.coerceAtMost(min(w, h) / 2f)
            rect.set(b)

            if (keyStyle == "borderless") {
                if (pressed) {
                    specPaint.shader = null
                    specPaint.color = ColorUtils.setAlphaComponent(pressedOverlay, if (touchBoost) 0x66 else 0x44)
                    canvas.drawRoundRect(rect, r, r, specPaint)
                    specPaint.shader = RadialGradient(
                        rect.centerX(), rect.centerY(), max(w, h) * 0.6f,
                        ColorUtils.setAlphaComponent(rimColor, 0x66),
                        AndroidColor.TRANSPARENT,
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawRoundRect(rect, r, r, specPaint)
                }
                return
            }

            val isNeon = effectMode == "neon" || keyStyle == "outline"
            val isIridescent = effectMode == "aurora" || effectMode == "holographic"
            val is3d = effectMode == "3d_live_motion" || effectMode == "parallax" || keyStyle == "ceramic_3d"
            val isGlass = keyStyle == "glass"
            val isRipple = effectMode == "ripple"
            val isStreak = effectMode == "motion" || effectMode == "live"

            if ((glow || isNeon) && rimWidthPx > 0f) {
                val baseGlow = if (isNeon) 0.28f else 0.16f
                val haloFactor = opacityClamped.coerceAtLeast(0.75f)
                for (i in 1..2) {
                    rimPaint.color = blendAlpha(rimColor, (baseGlow / i) * haloFactor)
                    rimPaint.strokeWidth = rimWidthPx + i * rimWidthPx * 1.15f
                    canvas.drawRoundRect(rect, r, r, rimPaint)
                }
            }

            if (opacityClamped > 0.01f) {
                canvas.drawRoundRect(rect, r, r, fillPaint)
            }

            clipPath.reset()
            clipPath.addRoundRect(rect, r, r, Path.Direction.CW)
            val save = canvas.save()
            canvas.clipPath(clipPath)

            when {
                keyStyle == "ceramic_3d" -> {
                    // Cerakey-style radial glazed pigment pool + bright rim bevel + sculpted bottom skirt
                    specPaint.shader = RadialGradient(
                        rect.centerX(), rect.centerY() - h * 0.05f, max(w, h) * 0.56f,
                        ColorUtils.setAlphaComponent(AndroidColor.BLACK, 0x22),
                        ColorUtils.setAlphaComponent(AndroidColor.WHITE, 0x38),
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawRect(rect, specPaint)
                    specPaint.shader = LinearGradient(
                        0f, b.top.toFloat(), 0f, b.top.toFloat() + h * 0.24f,
                        ColorUtils.setAlphaComponent(AndroidColor.WHITE, 0x45),
                        AndroidColor.TRANSPARENT,
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawRect(rect, specPaint)
                    specPaint.shader = LinearGradient(
                        0f, b.bottom.toFloat() - h * 0.22f, 0f, b.bottom.toFloat(),
                        AndroidColor.TRANSPARENT,
                        ColorUtils.setAlphaComponent(AndroidColor.BLACK, 0x48),
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawRect(rect, specPaint)
                }
                isGlass -> {
                    specPaint.shader = LinearGradient(
                        b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(),
                        intArrayOf(
                            ColorUtils.setAlphaComponent(AndroidColor.WHITE, 0x38),
                            ColorUtils.setAlphaComponent(AndroidColor.WHITE, 0x10),
                            ColorUtils.setAlphaComponent(AndroidColor.BLACK, 0x16)
                        ),
                        floatArrayOf(0f, 0.45f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawRect(rect, specPaint)
                }
                isIridescent -> {
                    specPaint.shader = LinearGradient(
                        b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(),
                        intArrayOf(
                            blendAlpha(0xFF00F5D4.toInt(), 0.16f * opacityClamped.coerceAtLeast(0.5f)),
                            blendAlpha(0xFF8B5CF6.toInt(), 0.14f * opacityClamped.coerceAtLeast(0.5f)),
                            blendAlpha(0xFFF472B6.toInt(), 0.16f * opacityClamped.coerceAtLeast(0.5f))
                        ),
                        floatArrayOf(0f, 0.5f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawRect(rect, specPaint)
                }
                isStreak -> {
                    specPaint.shader = LinearGradient(
                        b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(),
                        intArrayOf(
                            blendAlpha(AndroidColor.WHITE, 0f),
                            blendAlpha(AndroidColor.WHITE, 0.14f * opacityClamped.coerceAtLeast(0.5f)),
                            blendAlpha(AndroidColor.WHITE, 0f)
                        ),
                        floatArrayOf(0.25f, 0.5f, 0.75f),
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawRect(rect, specPaint)
                }
                is3d -> {
                    specPaint.shader = LinearGradient(
                        0f, b.top.toFloat(), 0f, b.top.toFloat() + h * 0.22f,
                        blendAlpha(AndroidColor.WHITE, 0.20f * opacityClamped),
                        blendAlpha(AndroidColor.WHITE, 0f),
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawRect(rect, specPaint)
                    specPaint.shader = LinearGradient(
                        0f, b.bottom.toFloat() - h * 0.20f, 0f, b.bottom.toFloat(),
                        blendAlpha(AndroidColor.BLACK, 0f),
                        blendAlpha(AndroidColor.BLACK, 0.30f * opacityClamped),
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawRect(rect, specPaint)
                }
                else -> {
                    specPaint.shader = LinearGradient(
                        0f, b.top.toFloat(), 0f, b.top.toFloat() + h * 0.20f,
                        blendAlpha(AndroidColor.WHITE, if (glow) 0.16f * opacityClamped else 0.08f * opacityClamped),
                        blendAlpha(AndroidColor.WHITE, 0f),
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawRect(rect, specPaint)
                }
            }

            if (pressed) {
                val cap = if (touchBoost) 0x96 else 0x55
                val overlayAlpha = min(AndroidColor.alpha(pressedOverlay).coerceAtLeast(0x40), cap)
                specPaint.shader = null
                specPaint.color = ColorUtils.setAlphaComponent(pressedOverlay, overlayAlpha)
                canvas.drawRect(rect, specPaint)
                if (isRipple || touchBoost) {
                    specPaint.shader = RadialGradient(
                        rect.centerX(), rect.centerY(), max(w, h) * 0.65f,
                        ColorUtils.setAlphaComponent(rimColor, if (isRipple) 0x88 else 0x55),
                        AndroidColor.TRANSPARENT,
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawRect(rect, specPaint)
                }
            }

            canvas.restoreToCount(save)

            if (rimWidthPx > 0f) {
                rimPaint.color = blendAlpha(rimColor, if (glow || isNeon || keyStyle == "glass") 0.85f else 0.45f)
                rimPaint.strokeWidth = if (isNeon) rimWidthPx * 1.25f else rimWidthPx
                canvas.drawRoundRect(rect, r, r, rimPaint)
            }
        }

        override fun setAlpha(alpha: Int) {
            fillPaint.alpha = alpha
            rimPaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            fillPaint.colorFilter = colorFilter
            rimPaint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private class AgslAuroraShaderHelper(
        @ColorInt primaryBg: Int,
        @ColorInt accent1: Int,
        @ColorInt accent2: Int
    ) {
        private val shader = RuntimeShader(
            """
            uniform float2 iResolution;
            uniform float iTime;
            uniform half4 iBg;
            uniform half4 iC1;
            uniform half4 iC2;
            half4 main(float2 fragCoord) {
                float2 uv = fragCoord / max(iResolution, float2(1.0, 1.0));
                float w1 = 0.5 + 0.5 * sin(uv.x * 4.2 + iTime * 1.1 + cos(uv.y * 3.1 - iTime * 0.7));
                float w2 = 0.5 + 0.5 * cos(uv.y * 5.0 - iTime * 0.9 + sin(uv.x * 3.5 + iTime * 0.8));
                float ribbon = smoothstep(0.25, 0.95, w1 * 0.65 + w2 * 0.35);
                half3 tint = mix(iC1.rgb, iC2.rgb, clamp(uv.x + 0.25 * sin(iTime * 0.6), 0.0, 1.0));
                return half4(tint * ribbon * 0.32, ribbon * 0.28);
            }
            """.trimIndent()
        )
        private val paint = Paint().apply { shader = this@AgslAuroraShaderHelper.shader }

        init {
            shader.setFloatUniform(
                "iBg",
                AndroidColor.red(primaryBg) / 255f,
                AndroidColor.green(primaryBg) / 255f,
                AndroidColor.blue(primaryBg) / 255f,
                1f
            )
            shader.setFloatUniform(
                "iC1",
                AndroidColor.red(accent1) / 255f,
                AndroidColor.green(accent1) / 255f,
                AndroidColor.blue(accent1) / 255f,
                1f
            )
            shader.setFloatUniform(
                "iC2",
                AndroidColor.red(accent2) / 255f,
                AndroidColor.green(accent2) / 255f,
                AndroidColor.blue(accent2) / 255f,
                1f
            )
        }

        fun draw(canvas: Canvas, left: Float, top: Float, width: Float, height: Float, timeSec: Float) {
            shader.setFloatUniform("iResolution", width, height)
            shader.setFloatUniform("iTime", timeSec)
            canvas.save()
            canvas.translate(left, top)
            canvas.drawRect(0f, 0f, width, height, paint)
            canvas.restore()
        }
    }

    private class StoreTouchFxController(
        private val context: Context,
        private val profile: StoreThemeRenderProfile,
        private val effectMode: String
    ) {
        private val enabled = effectMode in setOf("ripple", "touch", "neon", "live", "aurora", "3d_live_motion", "motion") ||
            (profile.motion?.particles == true && profile.motion.particleDensity > 0)

        private val accentColor = ThemeManifestEngine.parseHexColorOrNull(profile.motion?.particleColor)
            ?: profile.enterKeyBgColor
        private val secondaryColor = profile.spacebarStrokeColor

        private val rippleX = FloatArray(MAX_RIPPLES)
        private val rippleY = FloatArray(MAX_RIPPLES)
        private val rippleStart = LongArray(MAX_RIPPLES)
        private var rippleCursor = 0

        private val partX = FloatArray(MAX_PARTICLES)
        private val partY = FloatArray(MAX_PARTICLES)
        private val partVx = FloatArray(MAX_PARTICLES)
        private val partVy = FloatArray(MAX_PARTICLES)
        private val partStart = LongArray(MAX_PARTICLES)
        private var partCursor = 0

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val hasSparkles = profile.motion?.particles == true ||
            effectMode in setOf("neon", "aurora", "3d_live_motion", "live")

        private fun canAnimate(): Boolean {
            val pm = runCatching { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }.getOrNull()
            if (pm?.isPowerSaveMode == true) return false
            val scale = runCatching {
                Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            }.getOrDefault(1f)
            return scale > 0f
        }

        fun onKeyPressed(key: Key, centerX: Float, centerY: Float) {
            if (!enabled || key.isSpacer || !canAnimate()) return
            val now = SystemClock.uptimeMillis()
            val idx = rippleCursor
            rippleX[idx] = centerX
            rippleY[idx] = centerY
            rippleStart[idx] = now
            rippleCursor = (rippleCursor + 1) % MAX_RIPPLES

            if (hasSparkles) {
                for (i in 0 until PARTICLES_PER_TAP) {
                    val pidx = partCursor
                    val angle = ((now + i * 73L) % 360L) * (Math.PI / 180.0)
                    val speed = 0.08f + (i % 3) * 0.045f
                    partX[pidx] = centerX
                    partY[pidx] = centerY
                    partVx[pidx] = (cos(angle) * speed).toFloat()
                    partVy[pidx] = (sin(angle) * speed - 0.10).toFloat()
                    partStart[pidx] = now
                    partCursor = (partCursor + 1) % MAX_PARTICLES
                }
            }
        }

        fun draw(canvas: Canvas, view: View): Boolean {
            if (!enabled) return false
            val now = SystemClock.uptimeMillis()
            var active = false

            paint.style = Paint.Style.STROKE
            val maxR = view.resources.displayMetrics.density * 46f
            for (i in 0 until MAX_RIPPLES) {
                val t0 = rippleStart[i]
                if (t0 <= 0L) continue
                val elapsed = now - t0
                if (elapsed >= RIPPLE_MS) {
                    rippleStart[i] = 0L
                    continue
                }
                active = true
                val p = elapsed.toFloat() / RIPPLE_MS
                val radius = maxR * (0.25f + 0.75f * p)
                val alpha = ((1f - p) * 140f).roundToInt().coerceIn(0, 255)
                paint.strokeWidth = view.resources.displayMetrics.density * (2.4f - 1.6f * p)
                paint.color = ColorUtils.setAlphaComponent(accentColor, alpha)
                canvas.drawCircle(rippleX[i], rippleY[i], radius, paint)
            }

            if (hasSparkles) {
                paint.style = Paint.Style.FILL
                val baseSize = view.resources.displayMetrics.density * 2.6f
                for (i in 0 until MAX_PARTICLES) {
                    val t0 = partStart[i]
                    if (t0 <= 0L) continue
                    val elapsed = now - t0
                    if (elapsed >= PARTICLE_MS) {
                        partStart[i] = 0L
                        continue
                    }
                    active = true
                    val p = elapsed.toFloat() / PARTICLE_MS
                    val px = partX[i] + partVx[i] * elapsed
                    val py = partY[i] + partVy[i] * elapsed + 0.00015f * elapsed * elapsed
                    val alpha = ((1f - p) * 185f).roundToInt().coerceIn(0, 255)
                    val col = if (i % 2 == 0) accentColor else secondaryColor
                    paint.color = ColorUtils.setAlphaComponent(col, alpha)
                    canvas.drawCircle(px, py, baseSize * (1f - 0.45f * p), paint)
                }
            }
            return active
        }

        companion object {
            private const val MAX_RIPPLES = 6
            private const val MAX_PARTICLES = 36
            private const val PARTICLES_PER_TAP = 5
            private const val RIPPLE_MS = 380L
            private const val PARTICLE_MS = 480L
        }
    }

    private class LiveKeyboardBackgroundDrawable(
        private val context: Context,
        private val baseDrawable: Drawable,
        animatedWebpBytes: ByteArray?,
        private val profile: StoreThemeRenderProfile,
        private val effectMode: String
    ) : Drawable(), Animatable, Drawable.Callback {
        private var running = false
        private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val waveRect = RectF()
        private val particleColor: Int = ThemeManifestEngine.parseHexColorOrNull(profile.motion?.particleColor)
            ?: profile.enterKeyBgColor
        private val particleCount: Int = when {
            profile.motion?.particles == true -> (profile.motion.particleDensity.coerceIn(1, 5) * 4)
            effectMode == "live" || effectMode == "aurora" || effectMode == "3d_live_motion" -> 8
            else -> 0
        }

        private val decodedAnimatedDrawable: Drawable? = if (
            animatedWebpBytes != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        ) {
            runCatching {
                val src = ImageDecoder.createSource(ByteBuffer.wrap(animatedWebpBytes))
                ImageDecoder.decodeDrawable(src).also { d -> d.callback = this }
            }.getOrNull()
        } else {
            null
        }

        private val agslAuroraHelper: Any? = if (
            decodedAnimatedDrawable == null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            effectMode in setOf("aurora", "live", "motion", "3d_live_motion", "neon")
        ) {
            runCatching {
                AgslAuroraShaderHelper(
                    primaryBg = profile.primaryBgColor,
                    accent1 = particleColor,
                    accent2 = profile.spacebarStrokeColor
                )
            }.getOrNull()
        } else {
            null
        }

        private val frameTicker = Runnable {
            if (running && canAnimateNow()) {
                invalidateSelf()
                scheduleSelf(this.frameTickerRef, SystemClock.uptimeMillis() + FRAME_DELAY_MS)
            } else {
                running = false
            }
        }
        private val frameTickerRef: Runnable get() = frameTicker

        private fun canAnimateNow(): Boolean {
            val pm = runCatching {
                context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            }.getOrNull()
            val powerSave = pm?.isPowerSaveMode == true
            val animScale = runCatching {
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1.0f
                )
            }.getOrDefault(1.0f)
            return ThemeManifestEngine.shouldAnimateLiveBackground(
                hasLiveContent = true,
                isVisible = isVisible,
                isPowerSaveMode = powerSave,
                animatorDurationScale = animScale
            )
        }

        override fun onBoundsChange(bounds: android.graphics.Rect) {
            super.onBoundsChange(bounds)
            baseDrawable.bounds = bounds
            decodedAnimatedDrawable?.bounds = bounds
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            baseDrawable.bounds = b
            baseDrawable.draw(canvas)

            val anim = decodedAnimatedDrawable
            if (anim != null) {
                val save = canvas.save()
                canvas.clipRect(b)
                val sw = anim.intrinsicWidth
                val sh = anim.intrinsicHeight
                if (sw > 0 && sh > 0) {
                    val crop = ThemeManifestEngine.computeCenterCropRect(sw, sh, b.width(), b.height(), b.left.toFloat(), b.top.toFloat())
                    anim.setBounds(
                        crop.left.roundToInt(),
                        crop.top.roundToInt(),
                        crop.right.roundToInt(),
                        crop.bottom.roundToInt()
                    )
                } else {
                    anim.bounds = b
                }
                anim.draw(canvas)
                canvas.restoreToCount(save)
            }

            // Procedural live aurora wave + drifting particles (~18 FPS when running).
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            val t = if (running) (SystemClock.uptimeMillis() % 12000L) / 12000f else 0.25f
            val angle = (t * Math.PI * 2.0).toFloat()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && agslAuroraHelper is AgslAuroraShaderHelper) {
                val sec = if (running) (SystemClock.uptimeMillis() % 60000L) / 1000f else 1.5f
                agslAuroraHelper.draw(canvas, b.left.toFloat(), b.top.toFloat(), w, h, sec)
            } else if (effectMode == "aurora" || effectMode == "live" || effectMode == "motion" || effectMode == "3d_live_motion") {
                val cx = b.left + w * (0.5f + 0.28f * sin(angle))
                val cy = b.top + h * (0.45f + 0.22f * cos(angle))
                wavePaint.shader = RadialGradient(
                    cx,
                    cy,
                    max(w, h) * 0.65f,
                    ColorUtils.setAlphaComponent(particleColor, if (profile.isLight) 0x22 else 0x33),
                    AndroidColor.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
                waveRect.set(b)
                canvas.drawRect(waveRect, wavePaint)
            }

            if (particleCount > 0) {
                for (i in 0 until particleCount) {
                    val seed = (i + 1) * 1.37f
                    val px = b.left + ((sin(angle + seed * 2.1f) * 0.45f + 0.5f) * w)
                    val py = b.top + (((i.toFloat() / particleCount) + t) % 1.0f) * h
                    val radius = (2.2f + (i % 3) * 1.4f) * (w / 540f).coerceAtLeast(0.75f)
                    val alpha = if (profile.isLight) 0x38 else 0x52
                    particlePaint.color = ColorUtils.setAlphaComponent(particleColor, alpha)
                    canvas.drawCircle(px, py, radius, particlePaint)
                }
            }
        }

        override fun start() {
            if (running) return
            if (!canAnimateNow()) return
            running = true
            (decodedAnimatedDrawable as? Animatable)?.start()
            scheduleSelf(frameTicker, SystemClock.uptimeMillis() + FRAME_DELAY_MS)
            invalidateSelf()
        }

        override fun stop() {
            running = false
            unscheduleSelf(frameTicker)
            (decodedAnimatedDrawable as? Animatable)?.stop()
        }

        override fun isRunning(): Boolean = running

        override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
            val changed = super.setVisible(visible, restart)
            decodedAnimatedDrawable?.setVisible(visible, restart)
            if (visible) {
                if (canAnimateNow()) start()
            } else {
                stop()
            }
            return changed
        }

        override fun invalidateDrawable(who: Drawable) {
            invalidateSelf()
        }

        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
            scheduleSelf(what, `when`)
        }

        override fun unscheduleDrawable(who: Drawable, what: Runnable) {
            unscheduleSelf(what)
        }

        override fun setAlpha(alpha: Int) {
            baseDrawable.alpha = alpha
            decodedAnimatedDrawable?.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            baseDrawable.colorFilter = colorFilter
            decodedAnimatedDrawable?.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT

        companion object {
            private const val FRAME_DELAY_MS = 55L // ~18 FPS smooth ambient motion, ultra-low CPU
        }
    }

    /**
     * Code-drawn art key: center-crop bitmap clipped to a rounded capsule, plus top
     * specular sheen, vertical dark bottom readability scrim, thin outer rim (and a
     * soft concentric glow when [glass_rim_glow] is set), and a translucent pressed overlay.
     */
    private class ArtPackKeyBackgroundDrawable(
        private val bitmap: Bitmap,
        private val radiusPx: Float,
        private val rimColor: Int,
        private val rimWidthPx: Float,
        private val scrimAlpha: Float,
        private val glow: Boolean,
        private val pressed: Boolean,
        @ColorInt private val pressedOverlay: Int,
        private val opacity: Float,
        private val touchBoost: Boolean = false,
        private val effectMode: String = "glass"
    ) : Drawable() {
        private val opacityClamped = if (opacity.isFinite()) opacity.coerceIn(0f, 1f) else 1f
        private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            alpha = (opacityClamped * 255).roundToInt()
        }
        private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = rimWidthPx
            color = rimColor
            alpha = (opacityClamped * 255).roundToInt()
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            val r = radiusPx.coerceAtMost(min(w, h) / 2f)
            val rect = RectF(b)

            val isNeon = effectMode == "neon"
            val isIridescent = effectMode == "aurora" || effectMode == "holographic"
            val is3d = effectMode == "3d_live_motion" || effectMode == "parallax"
            val isRipple = effectMode == "ripple"
            val isStreak = effectMode == "motion" || effectMode == "live"

            if ((glow || isNeon) && rimWidthPx > 0f) {
                val baseGlow = if (isNeon) 0.24f else 0.12f
                for (i in 1..3) {
                    rimPaint.color = blendAlpha(rimColor, (baseGlow / i) * opacityClamped)
                    rimPaint.strokeWidth = rimWidthPx + i * rimWidthPx * (if (isNeon) 1.35f else 1f)
                    canvas.drawRoundRect(rect, r, r, rimPaint)
                }
            }

            val path = Path().apply { addRoundRect(rect, r, r, Path.Direction.CW) }
            val save = canvas.save()
            canvas.clipPath(path)

            val scale = max(w / bitmap.width, h / bitmap.height)
            val dw = bitmap.width * scale
            val dh = bitmap.height * scale
            val left = b.left + (w - dw) / 2f
            val top = b.top + (h - dh) / 2f
            canvas.drawBitmap(bitmap, null, RectF(left, top, left + dw, top + dh), bitmapPaint)

            // Vertical dark bottom readability scrim so key text stays readable over art.
            if (scrimAlpha > 0f) {
                val scrim = Paint().apply {
                    shader = LinearGradient(
                        0f, b.top.toFloat() + h * 0.55f, 0f, b.bottom.toFloat(),
                        blendAlpha(AndroidColor.BLACK, 0f),
                        blendAlpha(AndroidColor.BLACK, scrimAlpha * opacityClamped),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(rect, scrim)
            }

            // Effect-specific surface treatment (lightweight static Canvas shaders, zero IME timer overhead).
            when {
                isIridescent -> {
                    val irid = Paint().apply {
                        shader = LinearGradient(
                            b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(),
                            intArrayOf(
                                blendAlpha(0xFF00F5D4.toInt(), 0.16f * opacityClamped),
                                blendAlpha(0xFF8B5CF6.toInt(), 0.14f * opacityClamped),
                                blendAlpha(0xFFF472B6.toInt(), 0.16f * opacityClamped)
                            ),
                            floatArrayOf(0f, 0.5f, 1f),
                            Shader.TileMode.CLAMP
                        )
                    }
                    canvas.drawRect(rect, irid)
                }
                isStreak -> {
                    val streak = Paint().apply {
                        shader = LinearGradient(
                            b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(),
                            intArrayOf(
                                blendAlpha(AndroidColor.WHITE, 0f),
                                blendAlpha(AndroidColor.WHITE, 0.15f * opacityClamped),
                                blendAlpha(AndroidColor.WHITE, 0f)
                            ),
                            floatArrayOf(0.25f, 0.5f, 0.75f),
                            Shader.TileMode.CLAMP
                        )
                    }
                    canvas.drawRect(rect, streak)
                }
                is3d -> {
                    val topBevel = Paint().apply {
                        shader = LinearGradient(
                            0f, b.top.toFloat(), 0f, b.top.toFloat() + h * 0.22f,
                            blendAlpha(AndroidColor.WHITE, 0.22f * opacityClamped),
                            blendAlpha(AndroidColor.WHITE, 0f),
                            Shader.TileMode.CLAMP
                        )
                    }
                    canvas.drawRect(rect, topBevel)
                    val bottomDepth = Paint().apply {
                        shader = LinearGradient(
                            0f, b.bottom.toFloat() - h * 0.20f, 0f, b.bottom.toFloat(),
                            blendAlpha(AndroidColor.BLACK, 0f),
                            blendAlpha(AndroidColor.BLACK, 0.35f * opacityClamped),
                            Shader.TileMode.CLAMP
                        )
                    }
                    canvas.drawRect(rect, bottomDepth)
                }
                else -> {
                    // Default glass / neon / touch / ripple top specular highlight sheen.
                    val sheen = Paint().apply {
                        shader = LinearGradient(
                            0f, b.top.toFloat(), 0f, b.top.toFloat() + h * 0.18f,
                            blendAlpha(AndroidColor.WHITE, 0.10f * opacityClamped),
                            blendAlpha(AndroidColor.WHITE, 0f),
                            Shader.TileMode.CLAMP
                        )
                    }
                    canvas.drawRect(rect, sheen)
                }
            }

            // Pressed-state highlight (capped so art stays visible).
            if (pressed) {
                val cap = if (touchBoost) 0xA0 else 0x59
                val overlayAlpha = min(AndroidColor.alpha(pressedOverlay).coerceAtLeast(0x40), cap)
                val pressedPaint = Paint().apply {
                    color = ColorUtils.setAlphaComponent(pressedOverlay, overlayAlpha)
                }
                canvas.drawRect(rect, pressedPaint)
                if (isRipple || touchBoost) {
                    val radial = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = RadialGradient(
                            rect.centerX(), rect.centerY(), max(w, h) * 0.65f,
                            ColorUtils.setAlphaComponent(rimColor, if (isRipple) 0x88 else 0x55),
                            AndroidColor.TRANSPARENT,
                            Shader.TileMode.CLAMP
                        )
                    }
                    canvas.drawRect(rect, radial)
                }
                if (touchBoost) {
                    val flash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = max(1f, rimWidthPx)
                        color = ColorUtils.setAlphaComponent(AndroidColor.WHITE, 0x66)
                    }
                    canvas.drawRoundRect(rect, r, r, flash)
                }
            }

            canvas.restoreToCount(save)

            // Thin outer rim stroke on top of the capsule.
            if (rimWidthPx > 0f) {
                rimPaint.color = rimColor
                rimPaint.strokeWidth = if (isNeon) rimWidthPx * 1.25f else rimWidthPx
                canvas.drawRoundRect(rect, r, r, rimPaint)
            }
        }

        override fun setAlpha(alpha: Int) {
            bitmapPaint.alpha = alpha
            rimPaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            bitmapPaint.colorFilter = colorFilter
            rimPaint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }
}
