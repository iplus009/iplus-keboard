package org.futo.inputmethod.latin.uix.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val DefaultNeuCardShape = RoundedCornerShape(16.dp)

/**
 * Unslop + UI/UX Pro Max adaptive neutral ramp tokens for Keybird Settings & Store UI.
 * Keeps app surfaces on a calm graphite/warm-paper ramp while letting the active theme's
 * primary color act as the single high-contrast accent (no green-card/red-icon clashes).
 */
object KeybirdUiTokens {
    @Composable
    fun isLightTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() > 0.45f

    @Composable
    fun pageBackground(): Color = if (isLightTheme()) Color(0xFFF7F4EE) else Color(0xFF111215)

    @Composable
    fun cardSurface(): Color = if (isLightTheme()) Color(0xFFFFFFFF) else Color(0xFF18191D)

    @Composable
    fun raisedSurface(): Color = if (isLightTheme()) Color(0xFFEFE9DF) else Color(0xFF22242A)

    @Composable
    fun hairlineBorder(): Color = if (isLightTheme()) Color(0x18171717) else Color(0x18FFFFFF)

    @Composable
    fun divider(): Color = if (isLightTheme()) Color(0x12171717) else Color(0x14FFFFFF)

    @Composable
    fun textPrimary(): Color = if (isLightTheme()) Color(0xFF171717) else Color(0xFFEDEDED)

    @Composable
    fun textSecondary(): Color = if (isLightTheme()) Color(0xFF525252) else Color(0xFFA1A1A6)

    @Composable
    fun textMuted(): Color = if (isLightTheme()) Color(0xFF737373) else Color(0xFF85858B)

    @Composable
    fun accent(): Color {
        val raw = MaterialTheme.colorScheme.primary
        val light = isLightTheme()
        return when {
            light && raw.luminance() > 0.52f -> Color(0xFF2563EB)
            !light && raw.luminance() < 0.12f -> Color(0xFF38BDF8)
            else -> raw
        }
    }

    @Composable
    fun onAccent(): Color = if (accent().luminance() > 0.42f) Color(0xFF121212) else Color(0xFFFFFFFF)
}

/**
 * Tactile card component providing neutral graphite/paper surface, 1dp hairline border,
 * and subtle spring press feedback.
 */
@Composable
fun NeuCard(
    modifier: Modifier = Modifier,
    shape: Shape = DefaultNeuCardShape,
    contentPadding: Dp = 8.dp,
    borderColor: Color? = null,
    borderWidth: Dp = 1.dp,
    containerColor: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = if (onClick != null) remember { MutableInteractionSource() } else null
    val isPressed = interactionSource?.collectIsPressedAsState()?.value ?: false

    val scaleState = animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.85f),
        label = "neu_card_scale"
    )

    val effectiveBorderColor = borderColor ?: KeybirdUiTokens.hairlineBorder()
    val backgroundColor = containerColor ?: KeybirdUiTokens.cardSurface()

    Box(
        modifier = modifier
            .graphicsLayer {
                val s = scaleState.value
                if (s != 1.0f) {
                    scaleX = s
                    scaleY = s
                }
            }
            .clip(shape)
            .background(backgroundColor, shape)
            .border(borderWidth, effectiveBorderColor, shape)
            .then(
                if (onClick != null && interactionSource != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            )
            .padding(contentPadding)
    ) {
        Column(content = content)
    }
}
