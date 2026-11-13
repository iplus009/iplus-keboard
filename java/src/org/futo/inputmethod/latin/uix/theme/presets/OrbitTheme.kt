package org.futo.inputmethod.latin.uix.theme.presets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.theme.ThemeOption

// Matte Bauhaus iPlus Theme Palette
val IPlusMatteYellow = Color(0xFFE2B438)
val IPlusMatteBlue = Color(0xFF2A5288)
val IPlusMatteRed = Color(0xFFC23636)
val IPlusMatteWhite = Color(0xFFEAE6E1)
val IPlusMatteDark = Color(0xFF1C1D20)
val IPlusMatteBlack = Color(0xFF000000)

private val iPlusMatteColorScheme = darkColorScheme(
    primary = IPlusMatteRed,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = IPlusMatteDark,
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = IPlusMatteBlue,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = IPlusMatteDark,
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = IPlusMatteYellow,
    onTertiary = Color(0xFF121212),
    background = IPlusMatteBlack,
    onBackground = Color(0xFFFFFFFF),
    surface = IPlusMatteBlack,
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = IPlusMatteDark,
    onSurfaceVariant = Color(0xFFFFFFFF),
    outline = IPlusMatteYellow,
    outlineVariant = Color(0xFF2B2D31)
)

val IPlusMatteTheme = ThemeOption(
    dynamic = false,
    key = "IPlusMatteTheme",
    name = R.string.theme_orbit,
    available = { true }
) {
    iPlusMatteColorScheme
}

@Composable
fun IPlusMattePreviewKeyboard() {
    val yellow = IPlusMatteYellow
    val blue = IPlusMatteBlue
    val dark = IPlusMatteDark
    val white = IPlusMatteWhite
    val red = IPlusMatteRed

    val row1 = listOf(yellow, blue, dark, white, red, blue, white, red, yellow, dark)
    val row2 = listOf(white, red, blue, dark, yellow, white, red, blue, dark)
    val row3 = listOf(yellow, dark, yellow, white, red, blue, dark, yellow, white)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Row 1
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(1.5.dp, Alignment.CenterHorizontally)
        ) {
            row1.forEach { color ->
                Surface(
                    modifier = Modifier.weight(1f).height(10.dp),
                    color = color,
                    shape = RoundedCornerShape(2.dp)
                ) {}
            }
        }
        // Row 2
        Row(
            modifier = Modifier.fillMaxWidth(0.92f),
            horizontalArrangement = Arrangement.spacedBy(1.5.dp, Alignment.CenterHorizontally)
        ) {
            row2.forEach { color ->
                Surface(
                    modifier = Modifier.weight(1f).height(10.dp),
                    color = color,
                    shape = RoundedCornerShape(2.dp)
                ) {}
            }
        }
        // Row 3
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(1.5.dp, Alignment.CenterHorizontally)
        ) {
            row3.forEach { color ->
                Surface(
                    modifier = Modifier.weight(1f).height(10.dp),
                    color = color,
                    shape = RoundedCornerShape(2.dp)
                ) {}
            }
        }
        // Row 4
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(1.5.dp, Alignment.CenterHorizontally)
        ) {
            Surface(modifier = Modifier.weight(1.2f).height(10.dp), color = red, shape = RoundedCornerShape(2.dp)) {}
            Surface(modifier = Modifier.weight(0.9f).height(10.dp), color = blue, shape = RoundedCornerShape(2.dp)) {}
            Surface(modifier = Modifier.weight(0.9f).height(10.dp), color = dark, shape = RoundedCornerShape(2.dp)) {}
            Surface(modifier = Modifier.weight(3.5f).height(10.dp), color = dark, shape = RoundedCornerShape(2.dp)) {}
            Surface(modifier = Modifier.weight(0.9f).height(10.dp), color = white, shape = RoundedCornerShape(2.dp)) {}
            Surface(modifier = Modifier.weight(1.2f).height(10.dp), color = red, shape = RoundedCornerShape(2.dp)) {}
        }
    }
}
