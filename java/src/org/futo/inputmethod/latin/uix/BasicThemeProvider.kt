package org.futo.inputmethod.latin.uix

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
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
import android.util.TypedValue
import androidx.annotation.ColorInt
import org.futo.inputmethod.keyboard.Key
import org.futo.inputmethod.latin.uix.theme.presets.OrbitThemeHelper
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
import org.futo.inputmethod.latin.uix.theme.DarkColorScheme
import kotlin.math.roundToInt

val KeyBordersSetting = SettingsKey(booleanPreferencesKey("keyBorders"), true)
val KeyOpacitySetting = SettingsKey(floatPreferencesKey("key_opacity"), 1.0f)
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
val SavedCustomPhotoThemesSetting = SettingsKey(stringSetPreferencesKey("saved_custom_photo_themes"), setOf())

val KeyboardHeightMultiplierSetting = SettingsKey(floatPreferencesKey("keyboardHeightMultiplier"), 1.0f)
val KeyboardBottomOffsetSetting = SettingsKey(floatPreferencesKey("keyboardOffset"), 0.0f)

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

private fun loadCustomPhotoBackground(context: Context, uriValue: String, dimAmount: Float, blurAmount: Float, zoom: Float, offsetX: Float, offsetY: Float): Drawable? {
    if (uriValue.isBlank()) return null

    return runCatching {
        val uri = Uri.parse(uriValue)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = context.contentResolver.openInputStream(uri) ?: return null
        boundsStream.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val targetWidth = context.resources.displayMetrics.widthPixels.coerceAtLeast(720)
        val targetHeight = (targetWidth * 0.7f).toInt()
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > targetWidth * 2 ||
            bounds.outHeight / sampleSize > targetHeight * 2
        ) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: return null

        val scale = maxOf(targetWidth / bitmap.width.toFloat(), targetHeight / bitmap.height.toFloat()) * zoom.coerceIn(1.0f, 2.5f)
        val drawnWidth = bitmap.width * scale
        val drawnHeight = bitmap.height * scale
        val left = (targetWidth - drawnWidth) / 2f + offsetX.coerceIn(-1f, 1f) * (drawnWidth - targetWidth) / 2f
        val top = (targetHeight - drawnHeight) / 2f + offsetY.coerceIn(-1f, 1f) * (drawnHeight - targetHeight) / 2f
        val cropped = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        Canvas(cropped).drawBitmap(bitmap, null, RectF(left, top, left + drawnWidth, top + drawnHeight), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        bitmap.recycle()
        val displayBitmap = if (blurAmount > 0.1f) {
            val reduction = (1.0f + blurAmount.coerceIn(0.0f, 20.0f) * 0.35f).coerceIn(1.0f, 8.0f)
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
        val photo = BitmapDrawable(context.resources, displayBitmap).apply {
            gravity = Gravity.FILL
        }
        android.graphics.drawable.LayerDrawable(
            arrayOf(
                photo,
                android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.argb((dimAmount.coerceIn(0f, 0.85f) * 255).roundToInt(), 0, 0, 0))
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
    val currentThemeKey: String = themeKey ?: context.getSettingBlocking(THEME_KEY)
    val isOrbitTheme: Boolean = currentThemeKey == "OrbitTheme"
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
    override val keyboardTypeface: Typeface?

    fun hasUpdated(np: Preferences): Boolean {
        return np.get(HiddenKeysSetting) != expertMode
                || np.get(KeyBordersSetting) != keyBorders
                || np.get(KeyOpacitySetting) != keyOpacity
                || np.get(KeyHintsSetting) != showKeyHints
                || np.get(KeyHintColorSetting) != keyHintColor
                || np.get(KeyboardHeightMultiplierSetting) != keyboardHeight
                || np.get(KeyboardFontSetting) != keyboardFont
                || np.get(SpacebarColorSetting) != spacebarColorPref
                || np.get(CustomKeyboardBackgroundUriSetting) != customKeyboardBackgroundUri
                || np.get(CustomKeyboardBackgroundDimSetting) != customKeyboardBackgroundDim
                || np.get(CustomKeyboardBackgroundBlurSetting) != customKeyboardBackgroundBlur
                || np.get(CustomKeyboardBackgroundZoomSetting) != customKeyboardBackgroundZoom
                || np.get(CustomKeyboardBackgroundXSetting) != customKeyboardBackgroundX
                || np.get(CustomKeyboardBackgroundYSetting) != customKeyboardBackgroundY
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
        val colorScheme = if(overrideColorScheme != null) {
            overrideColorScheme
        }else if(!DynamicColors.isDynamicColorAvailable()) {
            DarkColorScheme
        } else {
            val dCtx = DynamicColors.wrapContextIfAvailable(context)

            dynamicLightColorScheme(dCtx)
        }

        expertMode = context.getSettingBlocking(HiddenKeysSetting)
        keyBorders = context.getSettingBlocking(KeyBordersSetting)
        keyOpacity = context.getSettingBlocking(KeyOpacitySetting).coerceIn(0.15f, 1.0f)
        showKeyHints = context.getSettingBlocking(KeyHintsSetting)
        keyHintColor = context.getSettingBlocking(KeyHintColorSetting)
        keyboardHeight = context.getSettingBlocking(KeyboardHeightMultiplierSetting)
        keyboardFont = context.getSettingBlocking(KeyboardFontSetting)
        spacebarColorPref = context.getSettingBlocking(SpacebarColorSetting)
        customKeyboardBackgroundUri = context.getSettingBlocking(CustomKeyboardBackgroundUriSetting)
        customKeyboardBackgroundDim = context.getSettingBlocking(CustomKeyboardBackgroundDimSetting)
        customKeyboardBackgroundBlur = context.getSettingBlocking(CustomKeyboardBackgroundBlurSetting).coerceIn(0.0f, 20.0f)
        customKeyboardBackgroundZoom = context.getSettingBlocking(CustomKeyboardBackgroundZoomSetting)
        customKeyboardBackgroundX = context.getSettingBlocking(CustomKeyboardBackgroundXSetting)
        customKeyboardBackgroundY = context.getSettingBlocking(CustomKeyboardBackgroundYSetting)
        val customSpaceColor = when (spacebarColorPref) {
            "red" -> OrbitThemeHelper.COLOR_RED
            "blue" -> OrbitThemeHelper.COLOR_BLUE
            "yellow" -> OrbitThemeHelper.COLOR_YELLOW
            "white" -> OrbitThemeHelper.COLOR_WHITE
            "dark" -> OrbitThemeHelper.COLOR_DARK
            else -> null
        }
        OrbitThemeHelper.customSpacebarColor = customSpaceColor
        keyboardTypeface = if (isOrbitTheme) {
            ResourcesCompat.getFont(context, R.font.ndot_family) ?: Typeface.DEFAULT
        } else if (keyboardFont == "normal") {
            Typeface.DEFAULT
        } else {
            ResourcesCompat.getFont(context, R.font.ndot_family)
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
        primaryKeyboardColor = if(keyBorders) {
            colorScheme.background.toArgb()
        } else {
            colorScheme.surface.toArgb()
        }

        val ratio = 1.5f
        val keyColor = if(keyBorders) {
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
        colors[R.styleable.MainKeyboardView_gestureTrailColor] = primary
        colors[R.styleable.MainKeyboardView_slidingKeyInputPreviewColor] = primary

        addIcon(KeyboardIconsSet.NAME_SHIFT_KEY, R.drawable.nth_shift, onBackground)
        addIcon(KeyboardIconsSet.NAME_SHIFT_KEY_SHIFTED, R.drawable.nth_shifted, onBackground)
        addIcon(KeyboardIconsSet.NAME_DELETE_KEY, R.drawable.nth_delete, onBackground)
        addIcon(KeyboardIconsSet.NAME_SETTINGS_KEY, R.drawable.settings, onBackground)
        addIcon(KeyboardIconsSet.NAME_SPACE_KEY, null, onBackground)
        addIcon(KeyboardIconsSet.NAME_ENTER_KEY, R.drawable.nth_enter, enterKeyForeground)
        addIcon(KeyboardIconsSet.NAME_GO_KEY, R.drawable.sym_keyboard_go_lxx_light, enterKeyForeground)
        addIcon(KeyboardIconsSet.NAME_SEARCH_KEY, R.drawable.nth_search, enterKeyForeground)
        addIcon(KeyboardIconsSet.NAME_SEND_KEY, R.drawable.sym_keyboard_send_lxx_light, enterKeyForeground)
        addIcon(KeyboardIconsSet.NAME_NEXT_KEY, R.drawable.sym_keyboard_next_lxx_light, enterKeyForeground)
        addIcon(KeyboardIconsSet.NAME_DONE_KEY, R.drawable.sym_keyboard_done_lxx_light, enterKeyForeground)
        addIcon(KeyboardIconsSet.NAME_PREVIOUS_KEY, R.drawable.sym_keyboard_previous_lxx_light, enterKeyForeground)
        addIcon(KeyboardIconsSet.NAME_TAB_KEY, R.drawable.sym_keyboard_tab_holo_dark, onBackground) // TODO: Correct tint
        addIcon(KeyboardIconsSet.NAME_ZWNJ_KEY, R.drawable.sym_keyboard_zwnj_lxx_dark, onBackground)
        addIcon(KeyboardIconsSet.NAME_ZWJ_KEY, R.drawable.sym_keyboard_zwj_lxx_dark, onPrimary)

        addIcon(KeyboardIconsSet.NAME_EMOJI_ACTION_KEY, R.drawable.nth_smile, onPrimary)
        addIcon(KeyboardIconsSet.NAME_EMOJI_NORMAL_KEY, R.drawable.nth_smile, onBackground)

        // Add by name (action_emoji)
        AllActionsMap.forEach { (i, it) ->
            addIcon("action_${i}", it.icon, onBackground)
        }

        // Add by id (action_0)
        AllActions.forEachIndexed { i, it ->
            addIcon("action_${i}", it.icon, onBackground)
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

        keyboardBackground = if (currentThemeKey == "CustomPhotoTheme") {
            loadCustomPhotoBackground(context, customKeyboardBackgroundUri, customKeyboardBackgroundDim, customKeyboardBackgroundBlur, customKeyboardBackgroundZoom, customKeyboardBackgroundX, customKeyboardBackgroundY)
                ?: coloredRectangle(primaryKeyboardColor)
        } else if (isOrbitTheme) {
            coloredRectangle(0xFF000000.toInt())
        } else {
            coloredRectangle(primaryKeyboardColor)
        }

        keyBackground = StateListDrawable().apply {
            addStateWithHighlightLayerOnPressed(highlight, intArrayOf(android.R.attr.state_active),
                coloredRoundedRectangle(enterKeyBackground, dp(128.dp)),
                cornerRadius = 128.dp
            )

            addStateWithHighlightLayerOnPressed(highlight, intArrayOf(android.R.attr.state_checkable, android.R.attr.state_checked),
                coloredRoundedRectangle(colorScheme.secondaryContainer.toArgb(), dp(8.dp))
            )

            addStateWithHighlightLayerOnPressed(highlight, intArrayOf(android.R.attr.state_checkable),
                if(keyBorders) {
                    coloredRoundedRectangle(keyColor, dp(8.dp))
                } else {
                    coloredRectangle(transparent)
                }
            )

            addStateWithHighlightLayerOnPressed(highlight, intArrayOf(android.R.attr.state_first),
                if(keyBorders) {
                    coloredRoundedRectangle(functionalKeyColor, dp(8.dp))
                } else {
                    coloredRectangle(transparent)
                }
            )

            addStateWithHighlightLayerOnPressed(highlight, intArrayOf(android.R.attr.state_empty),
                if(keyBorders) {
                    coloredRoundedRectangle(keyColor, dp(8.dp))
                } else {
                    coloredRectangle(transparent)
                }
            )

            addStateWithHighlightLayerOnPressed(highlight, intArrayOf(),
                if(keyBorders) {
                    coloredRoundedRectangle(keyColor, dp(8.dp))
                } else {
                    coloredRectangle(transparent)
                }
            )
        }

        val spaceCornerRadius = if(keyBorders) {
            8.dp
        } else {
            48.dp
        }

        val effectiveSpaceColor = customSpaceColor ?: (if(keyBorders) keyColor else highlight)
        val spaceDrawable = if(customSpaceColor != null) {
            coloredRoundedRectangle(effectiveSpaceColor, dp(spaceCornerRadius)).apply {
                setStroke(dp(1.5.dp).roundToInt(), if (spacebarColorPref == "yellow") OrbitThemeHelper.COLOR_DARK else yellowAccent)
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

        spaceBarBackground = StateListDrawable().apply {
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

        keyFeedback = ShapeDrawable().apply {
            paint.color = secondary
            shape = RoundRectShape(
                floatArrayOf(
                    dp(8.dp), dp(8.dp), dp(8.dp), dp(8.dp),
                    dp(8.dp), dp(8.dp), dp(8.dp), dp(8.dp),
                ), null, null
            )

            intrinsicWidth = dp(48.dp).roundToInt()
            intrinsicHeight = dp(24.dp).roundToInt()

            setPadding(0, 0, 0, dp(50.dp).roundToInt())
        }

        moreKeysTextColor = onPrimaryContainer
        moreKeysKeyboardBackground = coloredRoundedRectangle(primaryContainer, dp(8.dp))
        popupKey = StateListDrawable().apply {
            addStateWithHighlightLayerOnPressed(primary, intArrayOf(),
                coloredRoundedRectangle(primaryContainer, dp(8.dp))
            )
        }
        actionPopupKey = StateListDrawable().apply {
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
            cachedCornerRadius = dp(8.dp)
            cachedPreviewWidth = dp(48.dp).roundToInt()
            cachedPreviewHeight = dp(24.dp).roundToInt()
            cachedPreviewPadding = dp(50.dp).roundToInt()
        }
    }

    override fun getKeyBackground(key: Key, isPressed: Boolean): Drawable? {
        if (!isOrbitTheme) return null
        ensureDimensions()
        return OrbitThemeHelper.getKeyBackground(key, isPressed, cachedCornerRadius)
    }

    override fun getKeyTextColor(key: Key): Int? {
        if (!isOrbitTheme) return null
        return OrbitThemeHelper.getKeyTextColor(key)
    }

    override fun getKeyHintColor(key: Key): Int? {
        if (!isOrbitTheme) return null
        return OrbitThemeHelper.getKeyHintColor(key)
    }

    override fun getKeyPreviewBackground(key: Key): Drawable? {
        if (!isOrbitTheme) return null
        ensureDimensions()
        return OrbitThemeHelper.getKeyPreviewBackground(
            key,
            cachedCornerRadius,
            cachedPreviewWidth,
            cachedPreviewHeight,
            cachedPreviewPadding
        )
    }
}
