package org.futo.inputmethod.latin.uix.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.futo.inputmethod.latin.uix.theme.Inter

@Composable
fun DeveloperDetailsCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val accent = KeybirdUiTokens.accent()
    val raised = KeybirdUiTokens.raisedSurface()
    val hairline = KeybirdUiTokens.hairlineBorder()

    NeuCard(modifier = modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "Keybird • iPlus Apps Store",
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = KeybirdUiTokens.textPrimary()
                )
                Text(
                    text = "Official Keybird updates, themes, and community support",
                    fontFamily = Inter,
                    fontSize = 12.sp,
                    color = KeybirdUiTokens.textSecondary()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(raised)
                        .border(1.dp, hairline, RoundedCornerShape(8.dp))
                        .clickable {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://t.me/ismailq01appsstore")
                                )
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Telegram Channel",
                        fontFamily = Inter,
                        color = accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(raised)
                        .border(1.dp, hairline, RoundedCornerShape(8.dp))
                        .clickable {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://github.com/iplus009/iplus-keboard")
                                )
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "GitHub Repo",
                        fontFamily = Inter,
                        color = KeybirdUiTokens.textPrimary(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
