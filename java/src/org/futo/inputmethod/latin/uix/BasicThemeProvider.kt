package org.futo.inputmethod.latin.uix

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.media.ExifInterface
import android.net.Uri
import android.view.Gravity
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.util.Log
import android.util.LruCache
import android.util.TypedValue
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import androidx.annotation.ColorInt
import org.futo.inputmethod.keyboard.Key
import org.futo.inputmethod.latin.uix.theme.presets.IPlusMatteTheme
import org.futo.inputmethod.latin.uix.theme.presets.IPlusMatteThemeHelper
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.google.android.material.color.DynamicColors
import org.futo.inputmethod.keyboard.internal.KeyboardIconsSet
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.actions.AllActions
import org.futo.inputmethod.latin.uix.actions.AllActionsMap
import org.futo.inputmethod.latin.uix.theme.CustomPhotoLightColorScheme
import org.futo.inputmethod.latin.uix.theme.CustomPhotoTheme
import org.futo.inputmethod.latin.uix.theme.DarkColorScheme
import kotlin.math.roundToInt

internal fun Float.coerceFiniteIn(default: Float, min: Float, max: Float): Float =
    if (isFinite()) coerceIn(min, max) else default

internal fun truncateThemeNameSafely(name: String, maxChars: Int = 32): String {
    if (maxChars <= 0) return ""
    var end = minOf(name.length, maxChars)
    while (end > 0 && Character.isHighSurrogate(name[end - 1])) {
        end--
    }
    return name.substring(0, end)
}

internal fun extractPresetNameFromRecord(record: String, decoder: (String) -> String): String? {
    val parts = record.split("|")
    if (parts.size != 7) return null
    return decoder(parts[0]).trim()
}

internal fun hasThemeNameCollision(
    savedRecords: Collection<String>,
    candidateName: String,
    excludingRecord: String? = null,
    decoder: (String) -> String = { it }
): Boolean {
    val normalizedCandidate = candidateName.trim().lowercase()
    if (normalizedCandidate.isEmpty()) return false
    return savedRecords.any { record ->
        record != excludingRecord &&
            extractPresetNameFromRecord(record, decoder)?.lowercase() == normalizedCandidate
    }
}

val KeyBordersSetting = SettingsKey(booleanPreferencesKey("keyBorders"), true)
val KeyOpacitySetting = SettingsKey(floatPreferencesKey("key_opacity"), 1.0f)
val KeyShapeSetting = SettingsKey(stringPreferencesKey("key_shape"), "classic")
val HiddenKeysSetting = SettingsKey(booleanPreferencesKey("hiddenKeys"), false)
val KeyHintsSetting   = SettingsKey(booleanPreferencesKey("keyHints"), true)
val KeyboardFontSetting = SettingsKey(stringPreferencesKey("keyboard_font"), "nothing")
val KeyHintColorSetting = SettingsKey(stringPreferencesKey("key_hint_color"), "subtle")
val SpacebarColorSetting = SettingsKey(stringPreferencesKey("spacebar_color"), "default")
val CustomKeyboardBackgroundUriSetting = SettingsKey(stringPreferencesKey("custom_keyboard_background_uri"), "")
val CustomKeyboardBackgroundDimSetting = SettingsKey(floatPreferencesKey("custom_keyboard_background_dim"), 0.33f)
val CustomKeyboardBackgroundBlurSetting = SettingsKey(floatPreferencesKey("custom_keyboard_background_blur"), 0.0f)
val CustomKeyboardBackgroundZoomSetting = SettingsKey(floatPreferencesKey("custom_keyboard_background_zoom"), 1.0f)
val CustomKeyboardBackgroundXSetting = SettingsKey(floatPreferencesKey("custom_keyboard_background_x"), 0.0f)
val CustomKeyboardBackgroundYSetting = SettingsKey(floatPreferencesKey("custom_keyboard_background_y"), 0.0f)
val CustomPhotoTextContrastSetting = SettingsKey(stringPreferencesKey("custom_photo_text_contrast"), "dark_bg")
val SavedCustomPhotoThemesSetting = SettingsKey(stringSetPreferencesKey("saved_custom_photo_themes"), setOf())

fun keyShapeToCornerRadiusDp(keyShape: String): Dp = when (keyShape) {
    "sharp" -> 2.dp
    "rounded" -> 14.dp
    else -> 8.dp
}

const val MIN_KEYBOARD_HEIGHT_MULTIPLIER = 0.75f
const val DEFAULT_KEYBOARD_HEIGHT_MULTIPLIER = 1.00f
const val MAX_KEYBOARD_HEIGHT_MULTIPLIER = 1.30f
const val KEYBOARD_HEIGHT_STEP = 0.05f

fun clampKeyboardHeightMultiplier(value: Float): Float {
    if (!value.isFinite()) return DEFAULT_KEYBOARD_HEIGHT_MULTIPLIER
    return value.coerceIn(MIN_KEYBOARD_HEIGHT_MULTIPLIER, MAX_KEYBOARD_HEIGHT_MULTIPLIER)
}

fun snapKeyboardHeightMultiplier(value: Float): Float {
    val clamped = clampKeyboardHeightMultiplier(value)
    val steps = Math.round((clamped - MIN_KEYBOARD_HEIGHT_MULTIPLIER) / KEYBOARD_HEIGHT_STEP)
    return (MIN_KEYBOARD_HEIGHT_MULTIPLIER + steps * KEYBOARD_HEIGHT_STEP)
        .coerceIn(MIN_KEYBOARD_HEIGHT_MULTIPLIER, MAX_KEYBOARD_HEIGHT_MULTIPLIER)
}

val KeyboardHeightMultiplierSetting = SettingsKey(floatPreferencesKey("keyboardHeightMultiplier"), DEFAULT_KEYBOARD_HEIGHT_MULTIPLIER)
val KeyboardBottomOffsetSetting = SettingsKey(floatPreferencesKey("keyboardOffset"), 0.0f)
val OneHandedModeSetting = SettingsKey(stringPreferencesKey("one_handed_mode"), "off")

fun adjustColorBrightnessForContrast(bgColor: Int, fgColor: Int, desiredContrast: Float, adjustSaturation: Boolean = false): Int {
    // Convert RGB colors to HSL
    val bgHSL = FloatArray(3)
    ColorUtils.colorToHSL(bgColor, bgHSL)
    val fgHSL = FloatArray(3)
    ColorUtils.colorToHSL(fgColor, fgHSL)

    // Estimate the adjustment needed in lightness to achieve the desired contrast
    // This is a simplified approach and may not be perfectly accurate
    val lightnessAdjustment = (desiredContrast - 1) / 10.0f // Simplified and heuristic-based adjustment

    // Adjust the background color's lightness
    bgHSL[2] = bgHSL[2] + lightnessAdjustment
    bgHSL[2] = bgHSL[2].coerceIn(0f, 1f) // Ensure the lightness stays within valid range

    if(adjustSaturation) {
        bgHSL[1] = (bgHSL[1] + lightnessAdjustment).coerceIn(0f, 1f)
    }

    // Convert back to RGB and return the adjusted color
    return ColorUtils.HSLToColor(bgHSL)
}

fun<T> Preferences.get(key: SettingsKey<T>): T {
    return this[key.key] ?: key.default
}

private const val MAX_CUSTOM_PHOTO_BYTES = 24 * 1024 * 1024

private val customPhotoBitmapCache = object : LruCache<String, Bitmap>(2) {}

private fun readBoundedStreamBytes(context: Context, uri: Uri): ByteArray? {
    return context.contentResolver.openInputStream(uri)?.use { input ->
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_CUSTOM_PHOTO_BYTES) return null
            out.write(buf, 0, n)
        }
        out.toByteArray()
    }
}

private fun loadCustomPhotoBackground(context: Context, uriValue: String, dimAmount: Float, blurAmount: Float, zoom: Float, offsetX: Float, offsetY: Float): Drawable? {
    if (uriValue.isBlank()) return null

    return runCatching {
        val targetWidth = context.resources.displayMetrics.widthPixels.coerceAtLeast(720)
        val targetHeight = (targetWidth * 0.7f).toInt()
        val safeZoom = zoom.coerceFiniteIn(1.0f, 1.0f, 2.5f)
        val safeX = offsetX.coerceFiniteIn(0.0f, -1f, 1f)
        val safeY = offsetY.coerceFiniteIn(0.0f, -1f, 1f)
        val safeBlur = blurAmount.coerceFiniteIn(0.0f, 0.0f, 20.0f)
        val safeDim = dimAmount.coerceFiniteIn(0.35f, 0f, 0.85f)

        val cacheKey = "$uriValue|$targetWidth|$safeZoom|$safeX|$safeY|$safeBlur"
        val cachedBitmap = synchronized(customPhotoBitmapCache) {
            customPhotoBitmapCache.get(cacheKey)?.takeUnless { it.isRecycled }
        }

        val displayBitmap = cachedBitmap ?: run {
            val uri = Uri.parse(uriValue)
            // Single stream read replaces 3 repeated ContentResolver IPC opens on the UI thread.
            val rawBytes = readBoundedStreamBytes(context, uri) ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sampleSize = 1
            while (bounds.outWidth / sampleSize > targetWidth * 2 ||
                bounds.outHeight / sampleSize > targetHeight * 2
            ) {
                sampleSize *= 2
            }

            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, options) ?: return null

            val orientation = runCatching {
                ExifInterface(ByteArrayInputStream(rawBytes))
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val rotationDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            val bitmap = if (rotationDegrees != 0f) {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            } else {
                decoded
            }

            val scale = maxOf(targetWidth / bitmap.width.toFloat(), targetHeight / bitmap.height.toFloat()) * safeZoom
            val drawnWidth = bitmap.width * scale
            val drawnHeight = bitmap.height * scale
            val left = (targetWidth - drawnWidth) / 2f + safeX * (drawnWidth - targetWidth) / 2f
            val top = (targetHeight - drawnHeight) / 2f + safeY * (drawnHeight - targetHeight) / 2f
            val cropped = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            Canvas(cropped).drawBitmap(bitmap, null, RectF(left, top, left + drawnWidth, top + drawnHeight), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            bitmap.recycle()

            val result = if (safeBlur > 0.1f) {
                val reduction = (1.0f + safeBlur * 0.35f).coerceFiniteIn(1.0f, 1.0f, 8.0f)
                val small = Bitmap.createScaledBitmap(
                    cropped,
                    (targetWidth / reduction).toInt().coerceAtLeast(1),
                    (targetHeight / reduction).toInt().coerceAtLeast(1),
                    true
                )
                val blurred = Bitmap.createScaledBitmap(small, targetWidth, targetHeight, true)
                small.recycle()
                cropped.recycle()
                blurred
            } else {
                cropped
            }
            synchronized(customPhotoBitmapCache) {
                customPhotoBitmapCache.put(cacheKey, result)
            }
            result
        }

        val photo = BitmapDrawable(context.resources, displayBitmap).apply {
            gravity = Gravity.FILL
        }
        android.graphics.drawable.LayerDrawable(
            arrayOf(
                photo,
                android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.argb((safeDim * 255).roundToInt(), 0, 0, 0))
                }
            )
        )
    }.getOrNull()
}

class BasicThemeProvider(
    val context: Context,
    val overrideColorScheme: ColorScheme? = null,
    val themeKey: String? = null
) : DynamicThemeProvider {
    val currentThemeKey: String = normalizeThemeKey(themeKey ?: context.getSettingBlocking(THEME_KEY))
    val isIPlusMatteTheme: Boolean = currentThemeKey == IPlusMatteTheme.key
    val storeRevision: Int = org.futo.inputmethod.latin.themestore.engine.ThemeRegistry.revision.value
    private val storeProfile =
        org.futo.inputmethod.latin.themestore.engine.ThemeRegistry.resolveStoreRenderProfile(
            context,
            currentThemeKey
        )
    private val storeDelegate = storeProfile?.let {
        org.futo.inputmethod.latin.themestore.engine.StoreKeyVisualDelegate(
            context,
            it,
            // User sliders blend on top of Store Themes (blended when not locked)
            org.futo.inputmethod.latin.themestore.engine.KeyStyleOverrides(
                keyBorders = context.getSettingBlocking(KeyBordersSetting),
                keyOpacity = context.getSettingBlocking(KeyOpacitySetting).coerceIn(0.15f, 1.0f),
                keyShape = context.getSettingBlocking(KeyShapeSetting)
            )
        )
    }
    override val primaryKeyboardColor: Int
    override val keyColor: Int

    override val keyboardBackground: Drawable
    override val keyBackground: Drawable
    override val spaceBarBackground: Drawable

    override val keyFeedback: Drawable

    override val moreKeysTextColor: Int
    override val moreKeysKeyboardBackground: Drawable
    override val popupKey: Drawable
    override val actionPopupKey: Drawable

    private val colors: HashMap<Int, Int> = HashMap()
    override fun getColor(i: Int): Int? {
        return colors[i]
    }


    private val drawables: HashMap<Int, Drawable> = HashMap()
    override fun getDrawable(i: Int): Drawable? {
        return drawables[i]
    }

    val icons: HashMap<String, Drawable?> = hashMapOf()
    override fun getIcon(iconName: String): Drawable? {
        if(iconName == KeyboardIconsSet.ICON_UNDEFINED) return null

        if(!icons.containsKey(iconName)) {
            Log.e("BasicThemeProvider", "Unknown icon $iconName")
        }

        return icons[iconName]
    }

    override fun getKeyboardHeightMultiplier(): Float {
        return keyboardHeight
    }

    private fun dp(dp: Dp): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.value,
            context.resources.displayMetrics
        );
    }

    private fun coloredRectangle(@ColorInt color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
        }
    }

    private fun coloredRoundedRectangle(@ColorInt color: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
        }
    }

    private fun coloredOval(@ColorInt color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            cornerRadius = Float.MAX_VALUE
            setColor(color)
        }
    }

    private fun StateListDrawable.addStateWithHighlightLayerOnPressed(@ColorInt highlight: Int, stateSet: IntArray, drawable: Drawable, cornerRadius: Dp = 8.dp) {
        addState(intArrayOf(android.R.attr.state_pressed) + stateSet, LayerDrawable(
            arrayOf(
                drawable,
                coloredRoundedRectangle(highlight, dp(cornerRadius))
            )
        )
        )
        addState(stateSet, drawable)
    }

    val expertMode: Boolean
    val keyBorders: Boolean
    val keyOpacity: Float
    val keyShape: String
    val showKeyHints: Boolean
    val keyHintColor: String
    val keyboardHeight: Float
    val keyboardFont: String
    val spacebarColorPref: String
    val customKeyboardBackgroundUri: String
    val customKeyboardBackgroundDim: Float
    val customKeyboardBackgroundBlur: Float
    val customKeyboardBackgroundZoom: Float
    val customKeyboardBackgroundX: Float
    val customKeyboardBackgroundY: Float
    val customPhotoTextContrast: String
    val oneHandedMode: String
    override val keyboardTypeface: Typeface?

    fun hasUpdated(np: Preferences): Boolean {
        return np.get(HiddenKeysSetting) != expertMode
                || np.get(KeyBordersSetting) != keyBorders
                || np.get(KeyOpacitySetting) != keyOpacity
                || np.get(KeyShapeSetting) != keyShape
                || np.get(KeyHintsSetting) != showKeyHints
                || np.get(KeyHintColorSetting) != keyHintColor
                || clampKeyboardHeightMultiplier(np.get(KeyboardHeightMultiplierSetting)) != keyboardHeight
                || np.get(KeyboardFontSetting) != keyboardFont
                || np.get(SpacebarColorSetting) != spacebarColorPref
                || np.get(CustomKeyboardBackgroundUriSetting) != customKeyboardBackgroundUri
                || np.get(CustomKeyboardBackgroundDimSetting) != customKeyboardBackgroundDim
                || np.get(CustomKeyboardBackgroundBlurSetting) != customKeyboardBackgroundBlur
                || np.get(CustomKeyboardBackgroundZoomSetting) != customKeyboardBackgroundZoom
                || np.get(CustomKeyboardBackgroundXSetting) != customKeyboardBackgroundX
                || np.get(CustomKeyboardBackgroundYSetting) != customKeyboardBackgroundY
                || np.get(CustomPhotoTextContrastSetting) != customPhotoTextContrast
                || np.get(OneHandedModeSetting) != oneHandedMode
                || org.futo.inputmethod.latin.themestore.engine.ThemeRegistry.revision.value != storeRevision
    }


    private fun addIcon(iconName: String, drawableIntResId: Int, tint: Int) {
        addIcon(iconName, AppCompatResources.getDrawable(
            context,
            drawableIntResId
        ), tint)
    }

    private fun addIcon(iconName: String, drawable: Drawable?, tint: Int) {
        icons[iconName] = drawable?.apply {
            setTint(tint)
        }
    }

    init {
        expertMode = context.getSettingBlocking(HiddenKeysSetting)
        keyBorders = context.getSettingBlocking(KeyBordersSetting)
        keyOpacity = context.getSettingBlocking(KeyOpacitySetting).coerceIn(0.15f, 1.0f)
        keyShape = context.getSettingBlocking(KeyShapeSetting)
        showKeyHints = context.getSettingBlocking(KeyHintsSetting)
        keyHintColor = context.getSettingBlocking(KeyHintColorSetting)
        keyboardHeight = clampKeyboardHeightMultiplier(context.getSettingBlocking(KeyboardHeightMultiplierSetting))
        keyboardFont = context.getSettingBlocking(KeyboardFontSetting)
        spacebarColorPref = context.getSettingBlocking(SpacebarColorSetting)
        customKeyboardBackgroundUri = context.getSettingBlocking(CustomKeyboardBackgroundUriSetting)
        customKeyboardBackgroundDim = context.getSettingBlocking(CustomKeyboardBackgroundDimSetting)
        customKeyboardBackgroundBlur = context.getSettingBlocking(CustomKeyboardBackgroundBlurSetting).coerceIn(0.0f, 20.0f)
        customKeyboardBackgroundZoom = context.getSettingBlocking(CustomKeyboardBackgroundZoomSetting)
        customKeyboardBackgroundX = context.getSettingBlocking(CustomKeyboardBackgroundXSetting)
        customKeyboardBackgroundY = context.getSettingBlocking(CustomKeyboardBackgroundYSetting)
        customPhotoTextContrast = context.getSettingBlocking(CustomPhotoTextContrastSetting)
        oneHandedMode = context.getSettingBlocking(OneHandedModeSetting)

        val keyCornerRadius = keyShapeToCornerRadiusDp(keyShape)
        val keyCornerRadiusPx = dp(keyCornerRadius)

        val colorScheme = if (currentThemeKey == CustomPhotoTheme.key) {
            if (customPhotoTextContrast == "light_bg") CustomPhotoLightColorScheme else DarkColorScheme
        } else if (overrideColorScheme != null) {
            overrideColorScheme
        } else if (!DynamicColors.isDynamicColorAvailable()) {
            DarkColorScheme
        } else {
            val dCtx = DynamicColors.wrapContextIfAvailable(context)

            dynamicLightColorScheme(dCtx)
        }
        val customSpaceColor = when (spacebarColorPref) {
            "red" -> IPlusMatteThemeHelper.COLOR_RED
            "blue" -> IPlusMatteThemeHelper.COLOR_BLUE
            "yellow" -> IPlusMatteThemeHelper.COLOR_YELLOW
            "white" -> IPlusMatteThemeHelper.COLOR_WHITE
            "dark" -> IPlusMatteThemeHelper.COLOR_DARK
            else -> null
        }
        IPlusMatteThemeHelper.customSpacebarColor = customSpaceColor
        val activeStoreFont = org.futo.inputmethod.latin.themestore.engine.StoreFontEngine.refreshFromActivePreference(context)
        keyboardTypeface = activeStoreFont?.latin ?: storeDelegate?.resolveTypefaceOrNull() ?: when (keyboardFont) {
            "normal", "clean" -> Typeface.create("sans-serif", Typeface.NORMAL)
            "doto", "pixel" -> ResourcesCompat.getFont(context, R.font.doto) ?: Typeface.DEFAULT
            "inter", "rounded" -> if (keyboardFont == "rounded") Typeface.create("sans-serif-rounded", Typeface.NORMAL) else ResourcesCompat.getFont(context, R.font.inter) ?: Typeface.DEFAULT
            "tech_mono" -> Typeface.create("monospace", Typeface.NORMAL)
            "elegant" -> Typeface.create("serif", Typeface.NORMAL)
            "bold" -> Typeface.create("sans-serif-black", Typeface.NORMAL)
            "anime" -> Typeface.create("casual", Typeface.BOLD)
            "manga" -> Typeface.create("sans-serif-condensed", Typeface.BOLD)
            "cyberpunk" -> Typeface.create("sans-serif", Typeface.BOLD)
            "gothic" -> Typeface.create("serif", Typeface.BOLD)
            else -> ResourcesCompat.getFont(context, R.font.ndot_family) ?: Typeface.DEFAULT
        }

        val primary = colorScheme.primary.toArgb()
        val secondary = colorScheme.secondary.toArgb()
        val highlight = colorScheme.outline.copy(alpha = 0.33f).toArgb()

        val background = colorScheme.surface.toArgb()
        val surface = colorScheme.background.toArgb()
        val outline = colorScheme.outline.toArgb()

        val primaryContainer = colorScheme.primaryContainer.toArgb()
        val onPrimaryContainer = colorScheme.onPrimaryContainer.toArgb()

        val onPrimary = colorScheme.onPrimary.toArgb()
        val onPrimaryThird = colorScheme.onPrimary.copy(alpha = 0.33f).toArgb()
        val onSecondary = colorScheme.onSecondary.toArgb()
        val onBackground = colorScheme.onBackground.toArgb()
        val onBackgroundHalf = colorScheme.onBackground.copy(alpha = 0.5f).toArgb()
        val onBackgroundThird = colorScheme.onBackground.copy(alpha = 0.33f).toArgb()

        val transparent = Color.Transparent.toArgb()
        val yellowAccent = 0xFFFFCC00.toInt()
        val primaryKeyboardBase = storeProfile?.primaryBgColor ?: if(keyBorders) {
            colorScheme.background.toArgb()
        } else {
            colorScheme.surface.toArgb()
        }
        primaryKeyboardColor = primaryKeyboardBase

        val ratio = 1.5f
        val keyColor = storeProfile?.keyBgColor ?: if(keyBorders) {
            var c = adjustColorBrightnessForContrast(primaryKeyboardColor, primaryKeyboardColor, ratio)
            if(c == primaryKeyboardColor) {
                // May happen if the color is already 100% white
                c = adjustColorBrightnessForContrast(primaryKeyboardColor, primaryKeyboardColor, 1.0f / (ratio / 2.0f + 0.5f))
            }
            ColorUtils.setAlphaComponent(c, (keyOpacity * 255).roundToInt())
        } else {
            transparent
        }

        this.keyColor = keyColor

        val functionalKeyColor = if(keyBorders) {
            ColorUtils.setAlphaComponent(
                adjustColorBrightnessForContrast(primaryKeyboardColor, primaryKeyboardColor, ratio / 2.0f + 0.5f, adjustSaturation = true),
                (keyOpacity * 255).roundToInt()
            )
        } else {
            transparent
        }

        val enterKeyBackground = if(expertMode) { functionalKeyColor } else { primary }
        val enterKeyForeground = if(expertMode) { onBackgroundThird } else { onPrimary }

        val hintColor = if (keyHintColor == "yellow") yellowAccent else onBackgroundHalf
        colors[R.styleable.Keyboard_Key_keyTextColor] = onBackground
        colors[R.styleable.Keyboard_Key_keyTextInactivatedColor] = onBackgroundHalf
        colors[R.styleable.Keyboard_Key_keyPressedTextColor] = onPrimary
        colors[R.styleable.Keyboard_Key_keyTextShadowColor] = 0
        colors[R.styleable.Keyboard_Key_actionKeyTextColor] = enterKeyForeground
        colors[R.styleable.Keyboard_Key_functionalTextColor] = onBackground
        colors[R.styleable.Keyboard_Key_keyHintLetterColor] = hintColor
        colors[R.styleable.Keyboard_Key_keyHintLabelColor] = hintColor
        colors[R.styleable.Keyboard_Key_keyShiftedLetterHintInactivatedColor] = hintColor
        colors[R.styleable.Keyboard_Key_keyShiftedLetterHintActivatedColor] = hintColor
        colors[R.styleable.Keyboard_Key_keyPreviewTextColor] = onSecondary
        colors[R.styleable.MainKeyboardView_languageOnSpacebarTextColor] = yellowAccent
        colors[R.styleable.MainKeyboardView_gestureTrailColor] = storeProfile?.gestureTrailColor ?: primary
        colors[R.styleable.MainKeyboardView_slidingKeyInputPreviewColor] = storeProfile?.gestureTrailColor ?: primary

        if (storeProfile != null) {
            colors[R.styleable.Keyboard_Key_keyTextColor] = storeProfile.keyTextColor
            colors[R.styleable.Keyboard_Key_keyTextInactivatedColor] =
                ColorUtils.setAlphaComponent(storeProfile.keyTextColor, 128)
            colors[R.styleable.Keyboard_Key_keyPressedTextColor] = storeProfile.keyPressedTextColor
            colors[R.styleable.Keyboard_Key_actionKeyTextColor] = storeProfile.enterKeyFgColor
            colors[R.styleable.Keyboard_Key_functionalTextColor] = storeProfile.keyTextColor
            colors[R.styleable.Keyboard_Key_keyHintLetterColor] = storeProfile.keyHintColor
            colors[R.styleable.Keyboard_Key_keyHintLabelColor] = storeProfile.keyHintColor
            colors[R.styleable.Keyboard_Key_keyShiftedLetterHintInactivatedColor] = storeProfile.keyHintColor
            colors[R.styleable.Keyboard_Key_keyShiftedLetterHintActivatedColor] = storeProfile.keyHintColor
            colors[R.styleable.Keyboard_Key_keyPreviewTextColor] = storeProfile.popupTextColor
            colors[R.styleable.MainKeyboardView_languageOnSpacebarTextColor] =
                org.futo.inputmethod.latin.themestore.engine.ThemeManifestEngine.resolveSpacebarLabelColor(storeProfile)
        }

        val keyIconTint = storeProfile?.keyTextColor ?: onBackground
        val actionIconTint = if (expertMode) onBackgroundThird else (storeProfile?.enterKeyFgColor ?: enterKeyForeground)
        val primaryKeyIconTint = storeProfile?.keyTextColor ?: onPrimary

        addIcon(KeyboardIconsSet.NAME_SHIFT_KEY, R.drawable.nth_shift, keyIconTint)
        addIcon(KeyboardIconsSet.NAME_SHIFT_KEY_SHIFTED, R.drawable.nth_shifted, keyIconTint)
        addIcon(KeyboardIconsSet.NAME_DELETE_KEY, R.drawable.nth_delete, keyIconTint)
        addIcon(KeyboardIconsSet.NAME_SETTINGS_KEY, R.drawable.settings, keyIconTint)
        addIcon(KeyboardIconsSet.NAME_SPACE_KEY, null, keyIconTint)
        addIcon(KeyboardIconsSet.NAME_ENTER_KEY, R.drawable.nth_enter, actionIconTint)
        addIcon(KeyboardIconsSet.NAME_GO_KEY, R.drawable.sym_keyboard_go_lxx_light, actionIconTint)
        addIcon(KeyboardIconsSet.NAME_SEARCH_KEY, R.drawable.nth_search, actionIconTint)
        addIcon(KeyboardIconsSet.NAME_SEND_KEY, R.drawable.sym_keyboard_send_lxx_light, actionIconTint)
        addIcon(KeyboardIconsSet.NAME_NEXT_KEY, R.drawable.sym_keyboard_next_lxx_light, actionIconTint)
        addIcon(KeyboardIconsSet.NAME_DONE_KEY, R.drawable.sym_keyboard_done_lxx_light, actionIconTint)
        addIcon(KeyboardIconsSet.NAME_PREVIOUS_KEY, R.drawable.sym_keyboard_previous_lxx_light, actionIconTint)
        addIcon(KeyboardIconsSet.NAME_TAB_KEY, R.drawable.sym_keyboard_tab_holo_dark, keyIconTint) // TODO: Correct tint
        addIcon(KeyboardIconsSet.NAME_ZWNJ_KEY, R.drawable.sym_keyboard_zwnj_lxx_dark, keyIconTint)
        addIcon(KeyboardIconsSet.NAME_ZWJ_KEY, R.drawable.sym_keyboard_zwj_lxx_dark, primaryKeyIconTint)

        addIcon(KeyboardIconsSet.NAME_EMOJI_ACTION_KEY, R.drawable.nth_smile, primaryKeyIconTint)
        addIcon(KeyboardIconsSet.NAME_EMOJI_NORMAL_KEY, R.drawable.nth_smile, keyIconTint)

        // Add by name (action_emoji)
        AllActionsMap.forEach { (i, it) ->
            addIcon("action_${i}", it.icon, keyIconTint)
        }

        // Add by id (action_0)
        AllActions.forEachIndexed { i, it ->
            addIcon("action_${i}", it.icon, keyIconTint)
        }

        if(!showKeyHints) {
            colors[R.styleable.Keyboard_Key_keyHintLetterColor] = transparent
            colors[R.styleable.Keyboard_Key_keyHintLabelColor] = transparent
            colors[R.styleable.Keyboard_Key_keyShiftedLetterHintInactivatedColor] = transparent
            colors[R.styleable.Keyboard_Key_keyShiftedLetterHintActivatedColor] = transparent
        }

        if(expertMode) {
            colors[R.styleable.Keyboard_Key_keyTextColor] = transparent
            colors[R.styleable.Keyboard_Key_keyTextInactivatedColor] = transparent
            colors[R.styleable.Keyboard_Key_keyHintLetterColor] = transparent
            colors[R.styleable.Keyboard_Key_keyHintLabelColor] = transparent


            // Note: We don't fully hide some things, but fade them away as they may be important landmarks
            colors[R.styleable.Keyboard_Key_functionalTextColor] = onBackgroundThird
        }

        keyboardBackground = if (storeDelegate != null) {
            storeDelegate.createKeyboardBackground()
        } else if (currentThemeKey == "CustomPhotoTheme") {
            loadCustomPhotoBackground(context, customKeyboardBackgroundUri, customKeyboardBackgroundDim, customKeyboardBackgroundBlur, customKeyboardBackgroundZoom, customKeyboardBackgroundX, customKeyboardBackgroundY)
                ?: coloredRectangle(primaryKeyboardColor)
        } else if (isIPlusMatteTheme) {
            coloredRectangle(0xFF000000.toInt())
        } else {
            coloredRectangle(primaryKeyboardColor)
        }

        keyBackground = if (storeDelegate != null) {
            storeDelegate.createKeyBackgroundStateList()
        } else StateListDrawable().apply {
            addStateWithHighlightLayerOnPressed(highlight, intArrayOf(android.R.attr.state_active),
                coloredRoundedRectangle(enterKeyBackground, dp(128.dp)),
                cornerRadius = 128.dp
            )

            addStateWithHighlightLayerOnPressed(highlight, intArrayOf(android.R.attr.state_checkable, android.R.attr.state_checked),
                coloredRoundedRectangle(colorScheme.secondaryContainer.toArgb(), keyCornerRadiusPx),
                cornerRadius = keyCornerRadius
            )

            addStateWithHighlightLayerOnPressed(highlight, intArrayOf(android.R.attr.state_checkable),
                if(keyBorders) {
                    coloredRoundedRectangle(keyColor, keyCornerRadiusPx)
                } else {
                    coloredRectangle(transparent)
                },
                cornerRadius = keyCornerRadius
            )

            addStateWithHighlightLayerOnPressed(highlight, intArrayOf(android.R.attr.state_first),
                if(keyBorders) {
                    coloredRoundedRectangle(functionalKeyColor, keyCornerRadiusPx)
                } else {
                    coloredRectangle(transparent)
                },
                cornerRadius = keyCornerRadius
            )

            addStateWithHighlightLayerOnPressed(highlight, intArrayOf(android.R.attr.state_empty),
                if(keyBorders) {
                    coloredRoundedRectangle(keyColor, keyCornerRadiusPx)
                } else {
                    coloredRectangle(transparent)
                },
                cornerRadius = keyCornerRadius
            )

            addStateWithHighlightLayerOnPressed(highlight, intArrayOf(),
                if(keyBorders) {
                    coloredRoundedRectangle(keyColor, keyCornerRadiusPx)
                } else {
                    coloredRectangle(transparent)
                },
                cornerRadius = keyCornerRadius
            )
        }

        val spaceCornerRadius = if(keyBorders) {
            keyCornerRadius
        } else {
            48.dp
        }

        val effectiveSpaceColor = customSpaceColor ?: (if(keyBorders) keyColor else highlight)
        val spaceDrawable = if(customSpaceColor != null) {
            coloredRoundedRectangle(effectiveSpaceColor, dp(spaceCornerRadius)).apply {
                setStroke(dp(1.5.dp).roundToInt(), if (spacebarColorPref == "yellow") IPlusMatteThemeHelper.COLOR_DARK else yellowAccent)
            }
        } else if(keyBorders) {
            coloredRoundedRectangle(keyColor, dp(spaceCornerRadius)).apply {
                setStroke(dp(1.5.dp).roundToInt(), yellowAccent)
            }
        } else if(expertMode) {
            coloredRoundedRectangle(colorScheme.outline.copy(alpha = 0.1f).toArgb(), dp(spaceCornerRadius))
        } else {
            coloredRoundedRectangle(highlight, dp(spaceCornerRadius)).apply {
                setStroke(dp(1.5.dp).roundToInt(), yellowAccent)
            }
        }

        spaceBarBackground = if (storeDelegate != null) {
            storeDelegate.createSpaceBarBackground()
        } else StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed),
                LayerDrawable(
                    arrayOf(
                        spaceDrawable,
                        coloredRoundedRectangle(highlight, dp(spaceCornerRadius))
                    )
                )
            )
            addState(intArrayOf(),
                spaceDrawable
            )
        }

        keyFeedback = if (storeDelegate != null) {
            storeDelegate.createKeyFeedback(
                dp(48.dp).roundToInt(),
                dp(24.dp).roundToInt(),
                dp(50.dp).roundToInt()
            )
        } else ShapeDrawable().apply {
            paint.color = secondary
            shape = RoundRectShape(
                floatArrayOf(
                    keyCornerRadiusPx, keyCornerRadiusPx, keyCornerRadiusPx, keyCornerRadiusPx,
                    keyCornerRadiusPx, keyCornerRadiusPx, keyCornerRadiusPx, keyCornerRadiusPx,
                ), null, null
            )

            intrinsicWidth = dp(48.dp).roundToInt()
            intrinsicHeight = dp(24.dp).roundToInt()

            setPadding(0, 0, 0, dp(50.dp).roundToInt())
        }

        moreKeysTextColor = storeProfile?.popupTextColor ?: onPrimaryContainer
        moreKeysKeyboardBackground = if (storeProfile != null) {
            coloredRoundedRectangle(storeProfile.popupBgColor, dp(storeProfile.keyCornerRadiusDp.dp))
        } else {
            coloredRoundedRectangle(primaryContainer, keyCornerRadiusPx)
        }
        popupKey = if (storeDelegate != null) {
            storeDelegate.createPopupKey(pill = false)
        } else StateListDrawable().apply {
            addStateWithHighlightLayerOnPressed(primary, intArrayOf(),
                coloredRoundedRectangle(primaryContainer, keyCornerRadiusPx),
                cornerRadius = keyCornerRadius
            )
        }
        actionPopupKey = if (storeDelegate != null) {
            storeDelegate.createPopupKey(pill = true)
        } else StateListDrawable().apply {
            addStateWithHighlightLayerOnPressed(primary, intArrayOf(),
                coloredRoundedRectangle(primaryContainer, dp(128.dp)),
                128.dp
            )
        }

    }


    private var cachedCornerRadius = -1f
    private var cachedPreviewWidth = 0
    private var cachedPreviewHeight = 0
    private var cachedPreviewPadding = 0

    private fun ensureDimensions() {
        if (cachedCornerRadius < 0f) {
            cachedCornerRadius = dp(keyShapeToCornerRadiusDp(keyShape))
            cachedPreviewWidth = dp(48.dp).roundToInt()
            cachedPreviewHeight = dp(24.dp).roundToInt()
            cachedPreviewPadding = dp(50.dp).roundToInt()
        }
    }

    override fun getKeyBackground(key: Key, isPressed: Boolean): Drawable? {
        if (storeDelegate != null) {
            storeDelegate.getKeyBackground(key, isPressed)?.let { return it }
            return null
        }
        if (!isIPlusMatteTheme) return null
        ensureDimensions()
        return IPlusMatteThemeHelper.getKeyBackground(key, isPressed, cachedCornerRadius)
    }

    override fun getKeyTextColor(key: Key): Int? {
        if (storeDelegate != null && !expertMode) return storeDelegate.getKeyTextColor(key)
        if (!isIPlusMatteTheme) return null
        return IPlusMatteThemeHelper.getKeyTextColor(key)
    }

    override fun getKeyHintColor(key: Key): Int? {
        if (storeDelegate != null && showKeyHints && !expertMode) return storeDelegate.getKeyHintColor(key)
        if (!isIPlusMatteTheme) return null
        return IPlusMatteThemeHelper.getKeyHintColor(key)
    }

    override fun onKeyPressedVisualEffect(key: Key, centerX: Float, centerY: Float) {
        storeDelegate?.onKeyPressedVisualEffect(key, centerX, centerY)
    }

    override fun drawOverlayVisualEffects(canvas: android.graphics.Canvas, view: android.view.View): Boolean =
        storeDelegate?.drawOverlayVisualEffects(canvas, view) ?: false

    override fun getKeyPreviewBackground(key: Key): Drawable? {
        if (!isIPlusMatteTheme) return null
        ensureDimensions()
        return IPlusMatteThemeHelper.getKeyPreviewBackground(
            key,
            cachedCornerRadius,
            cachedPreviewWidth,
            cachedPreviewHeight,
            cachedPreviewPadding
        )
    }
}
