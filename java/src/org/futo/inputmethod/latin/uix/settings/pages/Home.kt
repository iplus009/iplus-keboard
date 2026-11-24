package org.futo.inputmethod.latin.uix.settings.pages

import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import org.futo.inputmethod.announcements.Announcement
import org.futo.inputmethod.announcements.AnnouncementManager
import org.futo.inputmethod.announcements.AnnouncementSheet
import org.futo.inputmethod.latin.ActiveSubtype
import org.futo.inputmethod.latin.BuildConfig
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.Subtypes
import org.futo.inputmethod.latin.SubtypesSetting
import org.futo.inputmethod.latin.uix.TextEditPopupActivity
import org.futo.inputmethod.latin.uix.getSettingBlocking
import org.futo.inputmethod.latin.uix.setSettingBlocking
import org.futo.inputmethod.latin.uix.settings.DeveloperDetailsCard
import org.futo.inputmethod.latin.uix.settings.KeybirdUiTokens
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.NeuCard
import org.futo.inputmethod.latin.uix.settings.SectionHeader
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.settings.useDataStoreValue
import org.futo.inputmethod.latin.uix.settings.useSharedPrefsBool
import org.futo.inputmethod.latin.uix.theme.Doto
import org.futo.inputmethod.latin.uix.theme.Inter
import org.futo.inputmethod.updates.AppUpdate
import org.futo.inputmethod.updates.UpdateChecker
import org.futo.inputmethod.updates.UpdateSheet

@Composable
fun BengaliLayoutToggleRow(
    title: String,
    subtitle: String,
    active: Boolean,
    canToggle: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val accent = KeybirdUiTokens.accent()
    val textPrimary = KeybirdUiTokens.textPrimary()
    val textSecondary = KeybirdUiTokens.textSecondary()
    val raised = KeybirdUiTokens.raisedSurface()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canToggle) { onToggle(!active) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = textPrimary
            )
            Text(
                text = subtitle,
                fontFamily = Inter,
                fontSize = 12.sp,
                color = textSecondary
            )
        }
        Switch(
            checked = active,
            enabled = canToggle,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = KeybirdUiTokens.onAccent(),
                checkedTrackColor = accent,
                uncheckedThumbColor = textSecondary,
                uncheckedTrackColor = raised
            )
        )
    }
}

@Composable
private fun QuickStudioBentoTile(
    title: String,
    subtitle: String,
    iconRes: Int,
    highlighted: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val accent = KeybirdUiTokens.accent()
    val border = if (highlighted) accent.copy(alpha = 0.45f) else KeybirdUiTokens.hairlineBorder()
    val iconBg = if (highlighted) accent.copy(alpha = 0.16f) else KeybirdUiTokens.raisedSurface()
    val iconTint = if (highlighted) accent else KeybirdUiTokens.textPrimary()

    NeuCard(
        modifier = modifier,
        contentPadding = 12.dp,
        borderColor = border,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBg)
                    .border(1.dp, KeybirdUiTokens.hairlineBorder(), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(19.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = KeybirdUiTokens.textPrimary(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontFamily = Inter,
                    fontSize = 11.sp,
                    color = KeybirdUiTokens.textSecondary(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun BengaliLayoutBentoCard(
    title: String,
    sample: String,
    active: Boolean,
    canToggle: Boolean,
    modifier: Modifier = Modifier,
    onToggle: (Boolean) -> Unit
) {
    val accent = KeybirdUiTokens.accent()
    val border = if (active) accent.copy(alpha = 0.65f) else KeybirdUiTokens.hairlineBorder()
    val badgeBg = if (active) accent.copy(alpha = 0.16f) else KeybirdUiTokens.raisedSurface()
    val badgeText = if (active) accent else KeybirdUiTokens.textMuted()

    NeuCard(
        modifier = modifier,
        contentPadding = 12.dp,
        borderColor = border,
        onClick = {
            if (canToggle) onToggle(!active)
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = KeybirdUiTokens.textPrimary(),
                    maxLines = 1
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (active) "ON" else "OFF",
                        fontFamily = Doto,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = badgeText
                    )
                }
            }
            Text(
                text = sample,
                fontFamily = Inter,
                fontSize = 12.sp,
                color = KeybirdUiTokens.textSecondary(),
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun HomeScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val isDeveloper = useDataStoreValue(IS_DEVELOPER)
    val isCheckingUpdate by UpdateChecker.isChecking.collectAsState()
    var showUpdateSheet by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<AppUpdate?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Announcement manager & state
    val (actionBarShown, setActionBarShown) = useDataStore(ActionBarDisplayedSetting)
    val (isNumberRowActive, setNumberRowActive) = useSharedPrefsBool(Settings.PREF_ENABLE_NUMBER_ROW, false)
    val (isPinSpacebarActive, setPinSpacebarActive) = useSharedPrefsBool(Settings.PREF_PIN_KEYBOARD_NAME_ON_SPACEBAR, true)

    val announcementManager = remember { AnnouncementManager.getInstance(context) }
    val availableAnnouncement by announcementManager.availableAnnouncement.collectAsState()
    val isCheckingAnnouncement by announcementManager.isChecking.collectAsState()
    var showAnnouncementSheet by remember { mutableStateOf(false) }

    val currentSubtypes = useDataStoreValue(SubtypesSetting)
    val akkhorSubtype = "bn_BD:KeyboardLayoutSet=bengali_akkhor:"
    val avroSubtype = "bn_BD:KeyboardLayoutSet=bengali_avro:"
    val jatiyoSubtype = "bn_BD:KeyboardLayoutSet=bengali_jatiyo:"
    val probhatSubtype = "bn_BD:KeyboardLayoutSet=bengali_probhat:"

    val isAkkhorActive = currentSubtypes.contains(akkhorSubtype)
    val isAvroActive = currentSubtypes.contains(avroSubtype)
    val isJatiyoActive = currentSubtypes.contains(jatiyoSubtype)
    val isProbhatActive = currentSubtypes.contains(probhatSubtype)

    fun toggleSubtype(subtypeStr: String, enabled: Boolean) {
        if (enabled && currentSubtypes.size >= Subtypes.MAX_ACTIVE_KEYBOARDS) {
            Toast.makeText(context, "Maximum 5 keyboards can be selected at a time", Toast.LENGTH_SHORT).show()
            return
        }
        val newSubtypes = if (enabled) {
            currentSubtypes + subtypeStr
        } else {
            if (currentSubtypes.size > 1) currentSubtypes - subtypeStr else currentSubtypes
        }
        context.setSettingBlocking(SubtypesSetting.key, newSubtypes)
        if (!enabled && context.getSettingBlocking(ActiveSubtype) == subtypeStr) {
            context.setSettingBlocking(ActiveSubtype.key, newSubtypes.firstOrNull() ?: "")
        }
    }

    LaunchedEffect(Unit) {
        // Keep initial setup local. Online update checks are available only
        // when the user explicitly requests them from the settings screen.
        Subtypes.addDefaultSubtypesIfNecessary(context)
    }

    val pageBg = KeybirdUiTokens.pageBackground()
    val accent = KeybirdUiTokens.accent()
    val dividerColor = KeybirdUiTokens.divider()
    val borderColor = KeybirdUiTokens.hairlineBorder()
    val textPrimary = KeybirdUiTokens.textPrimary()
    val textSecondary = KeybirdUiTokens.textSecondary()
    val raisedSurface = KeybirdUiTokens.raisedSurface()
    val activeBengaliCount = listOf(isAkkhorActive, isAvroActive, isJatiyoActive, isProbhatActive).count { it }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBg)
    ) {
        Column(
            modifier = Modifier
                .weight(1.0f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(bottom = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Hero Card: Keybird Brand Mark, Version Keycap & Bauhaus Tactile Strip
            NeuCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = 16.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF0E1B7A), Color(0xFF02041E))
                                    )
                                )
                                .border(1.dp, borderColor, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                contentDescription = "Keybird Logo",
                                modifier = Modifier.size(40.dp),
                                tint = Color.Unspecified
                            )
                        }
                        Column {
                            Text(
                                "Keybird",
                                fontFamily = Doto,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                letterSpacing = 0.5.sp,
                                color = textPrimary
                            )
                            Text(
                                "Bangla & English Keyboard",
                                fontFamily = Inter,
                                fontSize = 12.sp,
                                color = textSecondary
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(accent.copy(alpha = 0.14f))
                            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "v${BuildConfig.VERSION_NAME}",
                            fontFamily = Doto,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = accent
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Signature Bauhaus Matte keycap preview strip + active layout summary
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            Color(0xFFE2B438),
                            Color(0xFF2A5288),
                            Color(0xFFC23636),
                            Color(0xFFEAE6E1),
                            Color(0xFF22242A)
                        ).forEach { capColor ->
                            Box(
                                modifier = Modifier
                                    .size(width = 18.dp, height = 10.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(capColor)
                                    .border(0.5.dp, borderColor, RoundedCornerShape(3.dp))
                            )
                        }
                    }
                    Text(
                        text = "$activeBengaliCount Bangla layout(s) active",
                        fontFamily = Inter,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. STUDIO & APPEARANCE (2x2 Bento Quick Grid)
            SectionHeader(
                title = "Studio & Appearance",
                badge = "Themes • Stickers • Size",
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickStudioBentoTile(
                        title = "Theme Store",
                        subtitle = "Themes & Stickers",
                        iconRes = R.drawable.themes,
                        highlighted = true,
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigate("themestore") }
                    )
                    QuickStudioBentoTile(
                        title = "Themes",
                        subtitle = "Installed & Custom",
                        iconRes = R.drawable.themes,
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigate("themes") }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickStudioBentoTile(
                        title = "My Stickers",
                        subtitle = "Custom sticker pack",
                        iconRes = R.drawable.ic_stickers,
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigate("mystickers") }
                    )
                    QuickStudioBentoTile(
                        title = "Keyboard Size",
                        subtitle = "Height & Layout",
                        iconRes = R.drawable.maximize,
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigate("resize") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. BENGALI KEYBOARD LAYOUTS (2x2 Tactile Keycap Grid)
            SectionHeader(
                title = "Bengali Layouts",
                badge = "Tap to toggle",
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BengaliLayoutBentoCard(
                        title = "Avro (অভ্র)",
                        sample = "ami → আমি",
                        active = isAvroActive,
                        canToggle = !isAvroActive || currentSubtypes.size > 1,
                        modifier = Modifier.weight(1f),
                        onToggle = { enabled -> toggleSubtype(avroSubtype, enabled) }
                    )
                    BengaliLayoutBentoCard(
                        title = "Bangla (বাংলা)",
                        sample = "অ আ ক খ (5-row)",
                        active = isAkkhorActive,
                        canToggle = !isAkkhorActive || currentSubtypes.size > 1,
                        modifier = Modifier.weight(1f),
                        onToggle = { enabled -> toggleSubtype(akkhorSubtype, enabled) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BengaliLayoutBentoCard(
                        title = "Jatiyo (জাতীয়)",
                        sample = "ক খ গ (National)",
                        active = isJatiyoActive,
                        canToggle = !isJatiyoActive || currentSubtypes.size > 1,
                        modifier = Modifier.weight(1f),
                        onToggle = { enabled -> toggleSubtype(jatiyoSubtype, enabled) }
                    )
                    BengaliLayoutBentoCard(
                        title = "Probhat (প্রভাত)",
                        sample = "প্রভাত লেআউট",
                        active = isProbhatActive,
                        canToggle = !isProbhatActive || currentSubtypes.size > 1,
                        modifier = Modifier.weight(1f),
                        onToggle = { enabled -> toggleSubtype(probhatSubtype, enabled) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. INPUT & LANGUAGES
            SectionHeader(title = "Input & Typing", modifier = Modifier.padding(horizontal = 16.dp))
            NeuCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = 4.dp
            ) {
                NavigationItem(
                    title = "Languages & Dictionaries",
                    style = NavigationItemStyle.HomePrimary,
                    navigate = { navController.navigate("languages") },
                    icon = painterResource(id = R.drawable.globe)
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(dividerColor)
                )
                NavigationItem(
                    title = "Keyboard & Typing",
                    style = NavigationItemStyle.HomeSecondary,
                    navigate = { navController.navigate("typing") },
                    icon = painterResource(id = R.drawable.keyboard)
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(dividerColor)
                )
                NavigationItem(
                    title = "Text Prediction",
                    style = NavigationItemStyle.HomeTertiary,
                    navigate = { navController.navigate("predictiveText") },
                    icon = painterResource(id = R.drawable.text_prediction)
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(dividerColor)
                )
                NavigationItem(
                    title = "Voice Input",
                    style = NavigationItemStyle.HomePrimary,
                    navigate = { navController.navigate("voiceInput") },
                    icon = painterResource(id = R.drawable.mic)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. KEYBOARD ROWS & SPACEBAR
            SectionHeader(title = "Toolbar & Spacebar", modifier = Modifier.padding(horizontal = 16.dp))
            NeuCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = 4.dp
            ) {
                BengaliLayoutToggleRow(
                    title = "Suggestion Row",
                    subtitle = "Show suggestions and quick actions bar above keyboard",
                    active = actionBarShown,
                    canToggle = true,
                    onToggle = { enabled -> setActionBarShown(enabled) }
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(dividerColor)
                )
                BengaliLayoutToggleRow(
                    title = "Number Row",
                    subtitle = "Show dedicated row of numbers (0–9) on keyboard",
                    active = isNumberRowActive,
                    canToggle = true,
                    onToggle = { enabled -> setNumberRowActive(enabled) }
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(dividerColor)
                )
                BengaliLayoutToggleRow(
                    title = "Pin Name on Spacebar",
                    subtitle = if (isPinSpacebarActive) "Always show active layout name on spacebar" else "Show briefly when switching, then fade out",
                    active = isPinSpacebarActive,
                    canToggle = true,
                    onToggle = { enabled -> setPinSpacebarActive(enabled) }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. UPDATES & ANNOUNCEMENTS
            SectionHeader(title = "Updates & System", modifier = Modifier.padding(horizontal = 16.dp))
            NeuCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isCheckingUpdate) {
                            coroutineScope.launch {
                                val result = UpdateChecker.checkForUpdate()
                                result.fold(
                                    onSuccess = { update ->
                                        if (update.isUpdateAvailable) {
                                            availableUpdate = update
                                            showUpdateSheet = true
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Keybird is up to date (v${BuildConfig.VERSION_NAME})",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    onFailure = { error ->
                                        Toast.makeText(
                                            context,
                                            error.message?.let { "Update check failed: $it" }
                                                ?: "Couldn't check for updates. Check your internet connection.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                )
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(accent.copy(alpha = 0.14f))
                                .border(1.dp, borderColor, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.activity),
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                "Check for Updates",
                                fontFamily = Inter,
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                                color = textPrimary
                            )
                            Text(
                                if (isCheckingUpdate) "Checking releases..." else "Version ${BuildConfig.VERSION_NAME} • Tap to check",
                                fontFamily = Inter,
                                fontSize = 12.sp,
                                color = textSecondary
                            )
                        }
                    }

                    if (isCheckingUpdate) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = accent,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(raisedSurface)
                                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                "Check",
                                fontFamily = Inter,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = textPrimary
                            )
                        }
                    }
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(dividerColor)
                )
                NavigationItem(
                    title = "Announcements",
                    style = NavigationItemStyle.HomeSecondary,
                    navigate = {
                        announcementManager.checkForAnnouncements(coroutineScope, forceShow = true) { res ->
                            res.fold(
                                onSuccess = { ann ->
                                    if (ann != null) {
                                        showAnnouncementSheet = true
                                    } else {
                                        Toast.makeText(context, "No new announcements", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onFailure = {
                                    Toast.makeText(context, "No new announcements", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    },
                    icon = painterResource(id = R.drawable.ic_campaign)
                )
                if (isDeveloper || LocalInspectionMode.current) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(dividerColor)
                    )
                    NavigationItem(
                        title = "Developer Settings",
                        style = NavigationItemStyle.HomeTertiary,
                        navigate = { navController.navigate("developer") },
                        icon = painterResource(id = R.drawable.code)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 6. ABOUT DEVELOPER
            SectionHeader(title = "About Keybird", modifier = Modifier.padding(horizontal = 16.dp))
            DeveloperDetailsCard(modifier = Modifier.padding(horizontal = 16.dp))

            Spacer(modifier = Modifier.height(10.dp))

            NeuCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = 4.dp
            ) {
                NavigationItem(
                    title = "Help & Feedback",
                    style = NavigationItemStyle.HomeSecondary,
                    navigate = { navController.navigate("help") },
                    icon = painterResource(id = R.drawable.help_circle)
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(dividerColor)
                )
                NavigationItem(
                    title = "Credits & Licenses",
                    style = NavigationItemStyle.HomeTertiary,
                    navigate = { navController.navigate("credits") },
                    icon = painterResource(id = R.drawable.file_text)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Bottom "Try Typing" dock (compact in landscape so Bento rows stay unclipped)
        val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (isLandscape) 4.dp else 10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isLandscape) 40.dp else 50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(KeybirdUiTokens.cardSurface())
                    .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                    .clickable {
                        val intent = Intent().apply {
                            setClass(context, TextEditPopupActivity::class.java)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        context.startActivity(intent)
                    }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.keyboard),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        stringResource(R.string.try_typing),
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = textPrimary
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accent.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Test",
                        fontFamily = Doto,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = accent
                    )
                }
            }
        }
    }

    if (showUpdateSheet && availableUpdate != null) {
        UpdateSheet(
            update = availableUpdate!!,
            onDismiss = { showUpdateSheet = false }
        )
    }

    if (showAnnouncementSheet && availableAnnouncement != null) {
        AnnouncementSheet(
            announcement = availableAnnouncement!!,
            onDismiss = {
                showAnnouncementSheet = false
                availableAnnouncement?.let { announcementManager.dismissAnnouncement(it.id) }
            }
        )
    }
}
