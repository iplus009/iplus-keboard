package org.futo.inputmethod.latin.themestore.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.themestore.engine.InstalledStickerItem
import org.futo.inputmethod.latin.themestore.engine.InstalledStickerPack
import org.futo.inputmethod.latin.themestore.engine.StickerPackRegistry
import org.futo.inputmethod.latin.themestore.engine.ThemeRegistry
import org.futo.inputmethod.latin.uix.KeyboardManagerForAction
import org.futo.inputmethod.latin.uix.settings.SettingsActivity

@Composable
fun StickerPickerPanel(
    manager: KeyboardManagerForAction,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val revision by ThemeRegistry.revision.collectAsState()

    val packs by produceState<List<InstalledStickerPack>>(initialValue = emptyList(), revision) {
        value = withContext(Dispatchers.IO) {
            StickerPackRegistry.getInstalledStickerPacks(context) +
                StickerPackRegistry.getCustomStickerPacks(context)
        }
    }

    var selectedPackIdx by remember { mutableIntStateOf(0) }
    val safeIdx = selectedPackIdx.coerceIn(0, (packs.size - 1).coerceAtLeast(0))
    val activePack = packs.getOrNull(safeIdx)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        if (packs.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "No Sticker Packs Unlocked Yet",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Unlock One Piece & Bangla Reaction sticker packs in Keybird Store using payment or API Key.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = {
                            val intent = Intent(context, SettingsActivity::class.java).apply {
                                putExtra("navDest", "themestore")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            runCatching { context.startActivity(intent) }
                            onExit()
                        }
                    ) {
                        Text("Open Keybird Store")
                    }
                }
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                itemsIndexed(packs) { index, pack ->
                    val selected = index == safeIdx
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { selectedPackIdx = index }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = pack.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (activePack != null) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(activePack.stickers, key = { it.id }) { stk ->
                        StickerCell(
                            item = stk,
                            onClick = {
                                val uri = StickerPackRegistry.getContentUriForSticker(context, stk.file)
                                if (uri == null) {
                                    Toast.makeText(context, "Unable to read sticker file", Toast.LENGTH_SHORT).show()
                                    return@StickerCell
                                }
                                val mime = listOf(StickerPackRegistry.mimeTypeForStickerFileName(stk.file.name))
                                val sent = runCatching { manager.typeUri(uri, mime) }.getOrDefault(false)
                                if (sent) {
                                    manager.performHapticAndAudioFeedback(Constants.CODE_EMOJI, view)
                                } else {
                                    Toast.makeText(
                                        context,
                                        "This text box does not accept stickers",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "ABC",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onExit)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
            Text(
                text = "Get More Stickers",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        val intent = Intent(context, SettingsActivity::class.java).apply {
                            putExtra("navDest", "themestore")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { context.startActivity(intent) }
                        onExit()
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

private sealed class DecodedStickerPreview {
    data class StaticBitmap(val bitmap: ImageBitmap) : DecodedStickerPreview()
    data class AnimatedDrawable(val drawable: Drawable) : DecodedStickerPreview()
}

@Composable
private fun StickerCell(
    item: InstalledStickerItem,
    onClick: () -> Unit
) {
    val preview by produceState<DecodedStickerPreview?>(initialValue = null, item.file.absolutePath) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                    item.file.name.endsWith(".webp", ignoreCase = true)
                ) {
                    val d = ImageDecoder.decodeDrawable(ImageDecoder.createSource(item.file))
                    if (d is Animatable) {
                        return@runCatching DecodedStickerPreview.AnimatedDrawable(d)
                    }
                }
                BitmapFactory.decodeFile(item.file.absolutePath)?.asImageBitmap()
                    ?.let { DecodedStickerPreview.StaticBitmap(it) }
            }.getOrNull()
        }
    }

    DisposableEffect(preview) {
        val anim = (preview as? DecodedStickerPreview.AnimatedDrawable)?.drawable as? Animatable
        anim?.start()
        onDispose {
            anim?.stop()
        }
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        when (val p = preview) {
            is DecodedStickerPreview.StaticBitmap -> {
                Image(
                    bitmap = p.bitmap,
                    contentDescription = item.label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is DecodedStickerPreview.AnimatedDrawable -> {
                AndroidView(
                    factory = { ctx ->
                        ImageView(ctx).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            contentDescription = item.label
                        }
                    },
                    update = { iv -> iv.setImageDrawable(p.drawable) },
                    modifier = Modifier.fillMaxSize()
                )
            }
            null -> {
                Box(Modifier.size(32.dp))
            }
        }
    }
}
