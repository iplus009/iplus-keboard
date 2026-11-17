package org.futo.inputmethod.latin.themestore.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.Color as AndroidColor
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import androidx.annotation.ColorInt
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
import org.futo.inputmethod.latin.themestore.security.ThemeLicenseGuard
import org.futo.inputmethod.latin.themestore.storage.ThemeStoreDbHelper
import org.futo.inputmethod.latin.uix.isDirectBootUnlocked
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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
    @ColorInt val keyHintColor: Int,
    @ColorInt val popupBgColor: Int,
    @ColorInt val popupTextColor: Int,
    @ColorInt val gestureTrailColor: Int,
    val fontStyle: String,
    /** Phase 2: bottom readability scrim alpha (0..1) for art-pack capsules. */
    val scrimAlpha: Float = 0f,
    /** Phase 2: draw the frosted rim + outer glow on art-pack capsules. */
    val glassRimGlow: Boolean = false,
    /** Phase 2: per-key character artwork + backdrop/spacebar banner bundle. */
    val artPack: ManifestArtPackV1? = null
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

    /** Computes crisp white/dark foreground color using YIQ perceived luminance. */
    @ColorInt
    fun yiqContrastTextColor(@ColorInt bgColor: Int): Int {
        val r = (bgColor ushr 16) and 0xFF
        val g = (bgColor ushr 8) and 0xFF
        val b = bgColor and 0xFF
        val yiq = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        return if (yiq >= 0.55) 0xFF121212.toInt() else 0xFFFFFFFF.toInt()
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
        val enterBg = parseHexColor(k.enterKeyBg, parseHexColor(manifest.colors.primary, 0xFF00E676.toInt()))
        return StoreThemeRenderProfile(
            themeId = manifest.themeId,
            isLight = manifest.isLight,
            primaryBgColor = bgColors.first(),
            bgGradientColors = bgColors.toIntArray(),
            keyBgColor = keyBg,
            keyPressedBgColor = parseHexColor(k.keyPressedBg, 0xFF2E2E38.toInt()),
            functionalKeyBgColor = parseHexColor(k.functionalKeyBg, 0xFF141418.toInt()),
            enterKeyBgColor = enterBg,
            enterKeyFgColor = parseHexColor(k.enterKeyFg, yiqContrastTextColor(enterBg)),
            spacebarBgColor = parseHexColor(k.spacebarBg, keyBg),
            spacebarStrokeColor = parseHexColor(k.spacebarStroke, enterBg),
            keyStrokeColor = parseHexColor(k.keyStroke, 0xFF2B2D35.toInt()),
            keyStrokeWidthDp = k.keyStrokeWidthDp.coerceIn(0f, 3f),
            keyCornerRadiusDp = k.keyCornerRadiusDp.coerceIn(0f, 24f),
            keyTextColor = parseHexColor(k.keyText, yiqContrastTextColor(keyBg)),
            keyHintColor = parseHexColor(k.keyHint, 0xFF9CA3AF.toInt()),
            popupBgColor = parseHexColor(k.popupBg, 0xFF1B1B1F.toInt()),
            popupTextColor = parseHexColor(k.popupText, 0xFFFFFFFF.toInt()),
            gestureTrailColor = parseHexColor(k.gestureTrail, enterBg),
            fontStyle = k.fontStyle,
            scrimAlpha = k.scrimAlpha.coerceIn(0f, 1f),
            glassRimGlow = k.glassRimGlow,
            artPack = manifest.artPack
        )
    }

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

    /** User's key-shape slider blended on top of the manifest's own corner radius. */
    private fun effectiveRadiusDp(): Float = when (style.keyShape) {
        "sharp" -> 2f
        "rounded" -> 14f
        else -> profile.keyCornerRadiusDp
    }

    private val cornerPx = dp(effectiveRadiusDp())

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
        // Phase 2: art-pack full backdrop renders as the keyboard background when available.
        profile.artPack?.bgFullAsset?.let { path ->
            val bmp = loadBitmap(path)
            if (bmp != null) {
                return BitmapDrawable(context.resources, bmp).apply { gravity = Gravity.FILL }
            }
        }
        return if (profile.bgGradientColors.size >= 2) {
            GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, profile.bgGradientColors)
        } else {
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(profile.primaryBgColor)
            }
        }
    }

    fun createKeyBackgroundStateList(): StateListDrawable {
        val alphaFill = { color: Int ->
            if (style.keyBorders) blendAlpha(color, style.keyOpacity) else AndroidColor.TRANSPARENT
        }
        val strokeW = if (style.keyBorders) strokePx else 0
        val normal = roundedRect(alphaFill(profile.keyBgColor), cornerPx, profile.keyStrokeColor, strokeW)
        val pressed = roundedRect(profile.keyPressedBgColor, cornerPx, profile.spacebarStrokeColor, strokeW)
        val functional = roundedRect(alphaFill(profile.functionalKeyBgColor), cornerPx, profile.keyStrokeColor, strokeW)
        val enter = roundedRect(profile.enterKeyBgColor, dp(128f))
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
                    opacity = style.keyOpacity
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
                    opacity = style.keyOpacity
                )
                return StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_pressed), pressed)
                    addState(intArrayOf(), normal)
                }
            }
        }

        val normal = roundedRect(
            fill = blendAlpha(profile.spacebarBgColor, style.keyOpacity),
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
            opacity = style.keyOpacity
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

    fun getKeyTextColor(key: Key): Int =
        if (key.code == Constants.CODE_ENTER) profile.enterKeyFgColor else profile.keyTextColor

    fun getKeyHintColor(): Int = profile.keyHintColor

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
        private val opacity: Float
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

            if (glow && rimWidthPx > 0f) {
                for (i in 1..3) {
                    rimPaint.color = blendAlpha(rimColor, (0.12f / i) * opacityClamped)
                    rimPaint.strokeWidth = rimWidthPx + i * rimWidthPx
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

            // Top specular highlight sheen.
            val sheen = Paint().apply {
                shader = LinearGradient(
                    0f, b.top.toFloat(), 0f, b.top.toFloat() + h * 0.18f,
                    blendAlpha(AndroidColor.WHITE, 0.10f * opacityClamped),
                    blendAlpha(AndroidColor.WHITE, 0f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(rect, sheen)

            // Pressed-state highlight (capped so art stays visible).
            if (pressed) {
                val overlayAlpha = min(AndroidColor.alpha(pressedOverlay), 0x59)
                val pressedPaint = Paint().apply {
                    color = ColorUtils.setAlphaComponent(pressedOverlay, overlayAlpha)
                }
                canvas.drawRect(rect, pressedPaint)
            }

            canvas.restoreToCount(save)

            // Thin outer rim stroke on top of the capsule.
            if (rimWidthPx > 0f) {
                rimPaint.color = rimColor
                rimPaint.strokeWidth = rimWidthPx
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
