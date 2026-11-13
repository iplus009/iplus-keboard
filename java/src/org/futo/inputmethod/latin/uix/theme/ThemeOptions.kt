package org.futo.inputmethod.latin.uix.theme

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.futo.inputmethod.latin.uix.CustomPhotoTextContrastSetting
import org.futo.inputmethod.latin.uix.getSettingBlocking
import org.futo.inputmethod.latin.uix.theme.presets.AMOLEDDarkPurple
import org.futo.inputmethod.latin.uix.theme.presets.AMOLEDNothing
import org.futo.inputmethod.latin.uix.theme.presets.OrbitTheme
import org.futo.inputmethod.latin.uix.theme.presets.NothingLight
import org.futo.inputmethod.latin.uix.theme.presets.NothingDark
import org.futo.inputmethod.latin.uix.theme.presets.ClassicMaterialDark
import org.futo.inputmethod.latin.uix.theme.presets.ClassicMaterialLight
import org.futo.inputmethod.latin.uix.theme.presets.CottonCandy
import org.futo.inputmethod.latin.uix.theme.presets.DeepSeaDark
import org.futo.inputmethod.latin.uix.theme.presets.DeepSeaLight
import org.futo.inputmethod.latin.uix.theme.presets.DynamicDarkTheme
import org.futo.inputmethod.latin.uix.theme.presets.DynamicLightTheme
import org.futo.inputmethod.latin.uix.theme.presets.DynamicNothing
import org.futo.inputmethod.latin.uix.theme.presets.DynamicSystemTheme
import org.futo.inputmethod.latin.uix.theme.presets.Emerald
import org.futo.inputmethod.latin.uix.theme.presets.Snowfall
import org.futo.inputmethod.latin.uix.theme.presets.SteelGray
import org.futo.inputmethod.latin.uix.theme.presets.Sunflower
import org.futo.inputmethod.latin.uix.theme.presets.VoiceInputTheme
import org.futo.inputmethod.latin.R

data class ThemeOption(
    val dynamic: Boolean,
    val key: String,
    @StringRes val name: Int,
    val available: (Context) -> Boolean,
    val obtainColors: (Context) -> ColorScheme,
)

val CustomPhotoLightColorScheme = lightColorScheme(
    primary = NothingRed,
    onPrimary = NothingWhite,
    primaryContainer = Color(0xFFE4E7EC),
    onPrimaryContainer = Color(0xFF121212),
    secondary = Color(0xFF1C1D20),
    onSecondary = NothingWhite,
    secondaryContainer = Color(0xFFDDE1E6),
    onSecondaryContainer = Color(0xFF121212),
    tertiary = NothingRed,
    onTertiary = NothingWhite,
    tertiaryContainer = Color(0xFFE4E7EC),
    onTertiaryContainer = Color(0xFF121212),
    background = Color(0xFFECEFF1),
    onBackground = Color(0xFF121212),
    surface = Color(0xFFF5F7F9),
    onSurface = Color(0xFF121212),
    surfaceVariant = Color(0xFFE0E3E7),
    onSurfaceVariant = Color(0xFF3A3D42),
    outline = Color(0xFF6B7078)
)

val CustomPhotoTheme = ThemeOption(
    dynamic = false,
    key = "CustomPhotoTheme",
    name = R.string.custom_photo_theme,
    available = { true },
    obtainColors = { context ->
        if (context.getSettingBlocking(CustomPhotoTextContrastSetting) == "light_bg") {
            CustomPhotoLightColorScheme
        } else {
            DarkColorScheme
        }
    },
)

val ThemeOptions = mapOf(
    CustomPhotoTheme.key to CustomPhotoTheme,
    OrbitTheme.key to OrbitTheme,
    VoiceInputTheme.key to VoiceInputTheme,

    DynamicNothing.key to DynamicNothing,
    DynamicSystemTheme.key to DynamicSystemTheme,
    DynamicDarkTheme.key to DynamicDarkTheme,
    DynamicLightTheme.key to DynamicLightTheme,

    ClassicMaterialDark.key to ClassicMaterialDark,
    ClassicMaterialLight.key to ClassicMaterialLight,
    AMOLEDDarkPurple.key to AMOLEDDarkPurple,
    AMOLEDNothing.key to AMOLEDNothing,
    NothingLight.key to NothingLight,
    NothingDark.key to NothingDark,

    Sunflower.key to Sunflower,
    Snowfall.key to Snowfall,
    SteelGray.key to SteelGray,
    Emerald.key to Emerald,
    CottonCandy.key to CottonCandy,

    DeepSeaLight.key to DeepSeaLight,
    DeepSeaDark.key to DeepSeaDark,

)

val ThemeOptionKeys = ThemeOptions.keys
