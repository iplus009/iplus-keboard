package org.futo.inputmethod.latin.uix

import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.View
import androidx.annotation.ColorInt
import org.futo.inputmethod.keyboard.Key

interface DynamicThemeProvider {
    val primaryKeyboardColor: Int
    val keyColor: Int

    val keyboardBackground: Drawable
    val keyBackground: Drawable
    val spaceBarBackground: Drawable

    val keyFeedback: Drawable

    val moreKeysTextColor: Int
    val moreKeysKeyboardBackground: Drawable
    val popupKey: Drawable
    val actionPopupKey: Drawable

    val keyboardTypeface: Typeface?
        get() = null

    @ColorInt
    fun getColor(i: Int): Int?

    fun getDrawable(i: Int): Drawable?

    fun getIcon(iconName: String): Drawable?

    fun getKeyboardHeightMultiplier(): Float

    fun getKeyBackground(key: Key, isPressed: Boolean): Drawable? = null

    fun getKeyTextColor(key: Key): Int? = null

    fun getKeyHintColor(key: Key): Int? = null

    fun getKeyPreviewBackground(key: Key): Drawable? = null

    fun onKeyPressedVisualEffect(key: Key, centerX: Float, centerY: Float) {}

    fun drawOverlayVisualEffects(canvas: Canvas, view: View): Boolean = false

    companion object {
        @ColorInt
        fun getColorOrDefault(i: Int, @ColorInt default: Int, keyAttr: TypedArray, provider: DynamicThemeProvider?): Int {
            return (provider?.getColor(i)) ?: keyAttr.getColor(i, default)
        }

        fun getDrawableOrDefault(i: Int, keyAttr: TypedArray, provider: DynamicThemeProvider?): Drawable? {
            return (provider?.getDrawable(i)) ?: keyAttr.getDrawable(i)
        }
    }
}
