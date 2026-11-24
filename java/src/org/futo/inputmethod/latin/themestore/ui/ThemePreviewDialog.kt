package org.futo.inputmethod.latin.themestore.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.futo.inputmethod.latin.themestore.engine.StoreFontEngine
import org.futo.inputmethod.latin.themestore.engine.StoreFontSet
import org.futo.inputmethod.latin.themestore.engine.ThemeManifestEngine
import org.futo.inputmethod.latin.themestore.model.StoreCatalogEntry
import org.futo.inputmethod.latin.themestore.model.StoreThemeState
import org.futo.inputmethod.latin.themestore.network.ThemeStoreApiClient
import java.util.concurrent.TimeUnit

internal enum class PreviewTab(val label: String) {
    EN("English"),
    BN("বাংলা"),
    NUM("123")
}

internal fun openTelegramSupport(context: Context, handle: String = "@boname3") {
    val username = handle.trim().removePrefix("@").ifBlank { "boname3" }
    val tgIntent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=$username")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(tgIntent)
        return
    } catch (_: ActivityNotFoundException) {
        // Fallback to browser / Telegram web link
    } catch (_: Throwable) {
    }

    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$username")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(webIntent)
    } catch (_: Throwable) {
        copyToClipboard(context, "Telegram", "@$username")
        Toast.makeText(context, "Open Telegram and message @$username", Toast.LENGTH_SHORT).show()
    }
}

internal object StorePreviewImageLoader {
    private val cache = object : LruCache<String, ImageBitmap>(48) {}

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun resolveUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            return trimmed
        }
        val base = ThemeStoreApiClient.DEFAULT_BASE_URL.trimEnd('/')
        return if (trimmed.startsWith("/")) "$base$trimmed" else "$base/$trimmed"
    }

    suspend fun load(rawUrl: String): ImageBitmap? {
        val url = resolveUrl(rawUrl)
        if (url.isEmpty()) return null
        synchronized(cache) {
            cache.get(url)?.let { return it }
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url(url).get().build()
                client.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) return@use null
                    val bytes = res.body?.bytes() ?: return@use null
                    if (bytes.isEmpty()) return@use null
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@use null
                    bmp.asImageBitmap().also { img ->
                        synchronized(cache) { cache.put(url, img) }
                    }
                }
            }.getOrNull()
        }
    }
}

@Composable
internal fun StoreRemoteThumbnail(
    rawUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallback: @Composable () -> Unit
) {
    val resolvedUrl = remember(rawUrl) { StorePreviewImageLoader.resolveUrl(rawUrl) }
    var bitmap by remember(resolvedUrl) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(resolvedUrl) {
        bitmap = if (resolvedUrl.isNotEmpty()) StorePreviewImageLoader.load(resolvedUrl) else null
    }

    val loaded = bitmap
    if (loaded != null) {
        Image(
            bitmap = loaded,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    } else {
        fallback()
    }
}

@Composable
fun ThemePreviewDialog(
    entry: StoreCatalogEntry,
    isActive: Boolean,
    isInCart: Boolean,
    isBusy: Boolean,
    supportTelegram: String,
    onDismiss: () -> Unit,
    onPrimaryAction: () -> Unit,
    onInstantBuy: () -> Unit
) {
    val context = LocalContext.current
    var activeTab by remember(entry.themeId) { mutableStateOf(PreviewTab.EN) }
    var reloadNonce by remember(entry.themeId) { mutableIntStateOf(0) }

    val selectedUrl = remember(entry, activeTab) {
        when (activeTab) {
            PreviewTab.EN -> entry.previewUrls.en.ifBlank { entry.previewUrl }
            PreviewTab.BN -> entry.previewUrls.bn
            PreviewTab.NUM -> entry.previewUrls.num
        }.let { StorePreviewImageLoader.resolveUrl(it) }
    }

    var loadedBitmap by remember(selectedUrl, reloadNonce) { mutableStateOf<ImageBitmap?>(null) }
    var isLoadingImage by remember(selectedUrl, reloadNonce) { mutableStateOf(selectedUrl.isNotEmpty()) }
    var loadFailed by remember(selectedUrl, reloadNonce) { mutableStateOf(false) }

    LaunchedEffect(selectedUrl, reloadNonce) {
        if (selectedUrl.isEmpty()) {
            loadedBitmap = null
            isLoadingImage = false
            loadFailed = false
        } else {
            isLoadingImage = true
            loadFailed = false
            val bmp = StorePreviewImageLoader.load(selectedUrl)
            loadedBitmap = bmp
            isLoadingImage = false
            loadFailed = (bmp == null)
        }
    }

    var localFontSet by remember(entry.themeId, entry.isDownloaded) { mutableStateOf<StoreFontSet?>(null) }
    LaunchedEffect(entry.themeId, entry.isDownloaded) {
        if (entry.themeType == "font" && entry.isDownloaded) {
            localFontSet = withContext(Dispatchers.IO) {
                StoreFontEngine.loadVerifiedFontSet(context, entry.themeId)
            }
        } else {
            localFontSet = null
        }
    }

    val typeBadge = when (entry.themeType) {
        "art_pack" -> "ART PACK"
        "font" -> "FONT PACK"
        "sticker" -> "STICKER PACK"
        else -> "THEME"
    }

    Dialog(onDismissRequest = { if (!isBusy) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 650.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header: #short_code + Type badge + Price
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (entry.shortCode.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "#${entry.shortCode}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = typeBadge,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    if (entry.isPaid) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (entry.hasDiscount) {
                                Text(
                                    text = formatBdtFromPaisa(entry.originalPriceBdtPaisa),
                                    style = MaterialTheme.typography.labelMedium,
                                    textDecoration = TextDecoration.LineThrough,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = formatBdtFromPaisa(entry.priceBdtPaisa),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFD97706)
                            )
                        }
                    } else {
                        Text(
                            text = "FREE",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (entry.description.isNotBlank()) {
                        Text(
                            text = entry.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 3-Tab Layout Switcher: English | বাংলা | 123
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PreviewTab.values().forEach { tab ->
                        val selected = activeTab == tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else Color.Transparent
                                )
                                .clickable { activeTab = tab }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Preview Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 195.dp, max = 250.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        selectedUrl.isNotEmpty() && isLoadingImage -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp)
                                Text(
                                    text = "Loading ${activeTab.label} preview...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        selectedUrl.isNotEmpty() && loadedBitmap != null -> {
                            Image(
                                bitmap = loadedBitmap!!,
                                contentDescription = "${entry.title} ${activeTab.label} preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 195.dp, max = 250.dp)
                                    .padding(4.dp)
                            )
                        }
                        selectedUrl.isNotEmpty() && loadFailed -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "Preview could not load",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                OutlinedButton(onClick = { reloadNonce++ }) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Retry")
                                }
                            }
                        }
                        else -> {
                            InteractiveLayoutPreview(
                                entry = entry,
                                tab = activeTab,
                                fontSet = localFontSet
                            )
                        }
                    }
                }

                // Helpful context hint by product type
                val helperNote = when (entry.themeType) {
                    "font" -> "Applies Bangla, English & Number typography independently on top of any active theme."
                    "sticker" -> "After downloading, open Keybird Emoji drawer and switch to the Stickers tab to send in any app."
                    "art_pack" -> "Full custom keycap artwork pack with distinct letter-matched characters and themed spacebar."
                    else -> "Full Keybird color & surface theme with instant offline switching."
                }
                Text(
                    text = helperNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Primary Actions
                when (entry.state) {
                    StoreThemeState.PAID -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onPrimaryAction,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    if (isInCart) Icons.Default.Check else Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(if (isInCart) "In Cart" else "Cart", maxLines = 1)
                            }
                            Button(
                                onClick = {
                                    onDismiss()
                                    onInstantBuy()
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                            ) {
                                Text("Buy ${formatBdtFromPaisa(entry.priceBdtPaisa)}", maxLines = 1)
                            }
                        }
                    }
                    else -> {
                        val ctaText = when {
                            isBusy -> "Working..."
                            entry.hasUpdate -> "Update Now"
                            entry.themeType == "sticker" && entry.state == StoreThemeState.DOWNLOADED -> "Ready in Emoji -> Stickers"
                            entry.themeType == "sticker" -> "Download Sticker Pack"
                            entry.themeType == "font" && isActive -> "Font Active"
                            entry.themeType == "font" && entry.state == StoreThemeState.DOWNLOADED -> "Apply Font"
                            entry.themeType == "font" -> "Download & Apply Font"
                            entry.state == StoreThemeState.DOWNLOADED && isActive -> "Applied"
                            entry.state == StoreThemeState.DOWNLOADED -> "Apply Theme"
                            entry.state == StoreThemeState.OWNED -> "Download & Apply"
                            else -> "Get & Apply"
                        }
                        val disablePrimary = isBusy ||
                            (!entry.hasUpdate && entry.state == StoreThemeState.DOWNLOADED && (isActive || entry.themeType == "sticker"))
                        Button(
                            onClick = onPrimaryAction,
                            enabled = !disablePrimary,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(ctaText)
                        }
                    }
                }

                // Footer: Telegram Support + Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { openTelegramSupport(context, supportTelegram) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Admin $supportTelegram", style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = onDismiss, enabled = !isBusy) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun InteractiveLayoutPreview(
    entry: StoreCatalogEntry,
    tab: PreviewTab,
    fontSet: StoreFontSet?
) {
    val accentColor = remember(entry.accentHex) {
        Color(ThemeManifestEngine.parseHexColor(entry.accentHex, 0xFF00E676.toInt()))
    }
    val isLight = remember(entry.themeId, entry.description) {
        entry.themeId.contains("light", ignoreCase = true) ||
            entry.themeId.contains("paper", ignoreCase = true) ||
            Regex("\\blight\\b", RegexOption.IGNORE_CASE).containsMatchIn(entry.description)
    }
    val boardTop = if (isLight) Color(0xFFF3F4F6) else Color(0xFF101318)
    val keyBg = if (isLight) Color(0xFFFFFFFF) else Color(0xFF1E222B)
    val funcKeyBg = if (isLight) Color(0xFFE5E7EB) else Color(0xFF161922)
    val keyFg = if (isLight) Color(0xFF111827) else Color(0xFFF9FAFB)

    val composeFontFamily = remember(fontSet, tab) {
        val tf = when (tab) {
            PreviewTab.EN -> fontSet?.latin
            PreviewTab.BN -> fontSet?.bangla
            PreviewTab.NUM -> fontSet?.number ?: fontSet?.latin
        }
        if (tf != null) FontFamily(tf) else FontFamily.Default
    }

    val rows: List<List<String>> = remember(tab) {
        when (tab) {
            PreviewTab.EN -> listOf(
                listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
                listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
                listOf("⇧", "Z", "X", "C", "V", "B", "N", "M", "⌫")
            )
            PreviewTab.BN -> listOf(
                listOf("ঙ", "য", "ড", "প", "ট", "চ", "জ", "হ", "গ", "ড়"),
                listOf("ৃ", "ু", "ি", "া", "্", "ব", "ক", "ত", "দ"),
                listOf("⇧", "য়", "শ", "ষ", "স", "ম", "ন", "ল", "⌫")
            )
            PreviewTab.NUM -> listOf(
                listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
                listOf("@", "#", "৳", "_", "&", "-", "+", "(", ")", "/"),
                listOf("=\\<", "*", "\"", "'", ":", ";", "!", "?", "⌫")
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(boardTop, accentColor.copy(alpha = 0.20f))))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (entry.themeType == "font") {
            val specimen = when (tab) {
                PreviewTab.EN -> "Abc Qwerty • Keybird Font"
                PreviewTab.BN -> "অ আ ক খ • আমার সোনার বাংলা"
                PreviewTab.NUM -> "0123456789 • ০১২৩৪৫৬৭৮৯ ৳"
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(keyBg.copy(alpha = 0.85f))
                    .border(1.dp, accentColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = specimen,
                    fontFamily = composeFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = keyFg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }

        rows.forEachIndexed { rowIdx, rowKeys ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (rowIdx == 1) 10.dp else 0.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                rowKeys.forEach { label ->
                    val isFunc = label == "⇧" || label == "⌫" || label == "=\\<"
                    Box(
                        modifier = Modifier
                            .weight(if (isFunc) 1.35f else 1f)
                            .height(30.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isFunc) funcKeyBg else keyBg)
                            .border(0.75.dp, accentColor.copy(alpha = 0.28f), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontFamily = composeFontFamily,
                            fontSize = if (label.length > 2) 9.sp else 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = keyFg,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Bottom spacebar row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1.4f)
                    .height(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(funcKeyBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (tab) {
                        PreviewTab.EN -> "?123"
                        PreviewTab.BN -> "১২৩"
                        PreviewTab.NUM -> "ABC"
                    },
                    fontFamily = composeFontFamily,
                    fontSize = 11.sp,
                    color = keyFg
                )
            }
            Box(
                modifier = Modifier
                    .weight(4.2f)
                    .height(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(keyBg)
                    .border(1.dp, accentColor, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (tab) {
                        PreviewTab.EN -> "Keybird"
                        PreviewTab.BN -> "বাংলা"
                        PreviewTab.NUM -> "123 • ১২৩"
                    },
                    fontFamily = composeFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = keyFg
                )
            }
            Box(
                modifier = Modifier
                    .weight(1.5f)
                    .height(30.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(accentColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "↵",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(ThemeManifestEngine.yiqContrastTextColor(ThemeManifestEngine.parseHexColor(entry.accentHex, 0xFF00E676.toInt())))
                )
            }
        }
    }
}
