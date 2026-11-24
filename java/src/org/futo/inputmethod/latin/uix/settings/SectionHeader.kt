package org.futo.inputmethod.latin.uix.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.futo.inputmethod.latin.uix.theme.Inter

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    badge: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(14.dp)
                    .background(KeybirdUiTokens.accent(), RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = KeybirdUiTokens.textSecondary()
            )
        }
        if (!badge.isNullOrBlank()) {
            Text(
                text = badge,
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = KeybirdUiTokens.textMuted()
            )
        }
    }
}
