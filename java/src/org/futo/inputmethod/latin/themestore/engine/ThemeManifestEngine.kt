package org.futo.inputmethod.latin.themestore.engine

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.util.TypedValue
import androidx.annotation.ColorInt
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.core.content.res.ResourcesCompat
import kotlinx.serialization.json.Json
import org.futo.inputmethod.keyboard.Key
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.themestore.model.KeybirdThemeManifestV1
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
    val fontStyle: String
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
            fontStyle = k.fontStyle
        )
    }

    /**
     * Slot/group fallback resolver ready for Phase 2 Art-Packs across English & Bangla layouts.
     */
    fun resolveArtAssetKey(
        keymap: Map<String, String>,
        groupFallbacks: Map<String, String>,
        key: Key
    ): String? {
        val label = key.label?.lowercase()
        val exactId = when (key.code) {
            Constants.CODE_SPACE -> "k_space"
            Constants.CODE_ENTER -> "k_enter"
            Constants.CODE_SHIFT -> "k_shift"
            Constants.CODE_DELETE -> "k_delete"
            Constants.CODE_SWITCH_ALPHA_SYMBOL -> "k_sym"
            Constants.CODE_EMOJI, Constants.CODE_SHORTCUT -> "k_emoji"
            else -> if (!label.isNullOrBlank()) "k_$label" else null
        }
        if (exactId != null && keymap.containsKey(exactId)) {
            return keymap[exactId]
        }

        if (key.height > 0 && key.width > 0) {
            val row = (key.y / key.height).coerceAtLeast(0)
            val col = (key.x / key.width).coerceAtLeast(0)
            val slotKey = "slot_r${row}_c${col}"
            if (keymap.containsKey(slotKey)) {
                return keymap[slotKey]
            }
        }

        val group = when (key.code) {
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

class StoreKeyVisualDelegate(
    private val context: Context,
    val profile: StoreThemeRenderProfile
) {
    private val strokePx = dp(profile.keyStrokeWidthDp).roundToInt().coerceAtLeast(0)
    private val cornerPx = dp(profile.keyCornerRadiusDp)

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
        return if (profile.bgGradientColors.size >= 2) {
            GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, profile.bgGradientColors)
        } else {
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(profile.primaryBgColor)
            }
        }
    }

    fun createKeyBackgroundStateList(keyBorders: Boolean): StateListDrawable {
        val alphaFill = { color: Int -> if (keyBorders) color else AndroidColor.TRANSPARENT }
        val strokeW = if (keyBorders) strokePx else 0
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
        val normal = roundedRect(
            fill = profile.spacebarBgColor,
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
}
