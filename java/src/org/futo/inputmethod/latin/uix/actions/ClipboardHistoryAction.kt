package org.futo.inputmethod.latin.uix.actions

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionTextEditor
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.DialogRequestItem
import org.futo.inputmethod.latin.uix.PersistentActionState
import org.futo.inputmethod.latin.uix.PersistentStateInitialization
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.getSettingBlocking
import org.futo.inputmethod.latin.uix.isDirectBootUnlocked
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.pages.ParagraphText
import org.futo.inputmethod.latin.uix.settings.pages.PaymentSurface
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.theme.Typography
import java.io.File
import java.io.FileOutputStream

val ClipboardHistoryEnabled = SettingsKey(
    booleanPreferencesKey("enableClipboardHistory"),
    false
)

val ClipboardMaxItems = SettingsKey(
    intPreferencesKey("clipboard_max_items"),
    50
)

val ClipboardMaxDays = SettingsKey(
    intPreferencesKey("clipboard_max_days"),
    7
)

private val CLIPBOARD_MAX_ITEM_OPTIONS = listOf(25, 50, 100)
private val CLIPBOARD_MAX_DAY_OPTIONS = listOf(3, 7, 30)
private const val MAX_CLIPBOARD_MEDIA_BYTES = 15L * 1024L * 1024L
private const val THUMBNAIL_TARGET_PX = 320

object UriSerializer : KSerializer<Uri> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Uri", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Uri) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Uri {
        return Uri.parse(decoder.decodeString())
    }
}

@Serializable
data class ClipboardEntry(
    val timestamp: Long,
    val pinned: Boolean,

    val text: String?,

    @Serializable(with = UriSerializer::class)
    val uri: Uri?,
    val mimeTypes: List<String>,
    val localMediaPath: String? = null
)

internal fun resolveImageExtension(mimeTypes: List<String>): String {
    val imageMime = mimeTypes.firstOrNull { it.startsWith("image/", ignoreCase = true) }
        ?: return "img"
    val fromMap = MimeTypeMap.getSingleton().getExtensionFromMimeType(imageMime.lowercase())
    return when {
        !fromMap.isNullOrBlank() -> fromMap
        imageMime.equals("image/jpeg", ignoreCase = true) ||
            imageMime.equals("image/jpg", ignoreCase = true) -> "jpg"
        imageMime.equals("image/png", ignoreCase = true) -> "png"
        imageMime.equals("image/webp", ignoreCase = true) -> "webp"
        imageMime.equals("image/gif", ignoreCase = true) -> "gif"
        else -> "img"
    }
}

internal fun filterClipboardEntries(entries: List<ClipboardEntry>, query: String): List<ClipboardEntry> {
    val trimmed = query.trim()
    val reversed = entries.asReversed()
    if (trimmed.isEmpty()) return reversed
    return reversed.filter { entry ->
        entry.text?.contains(trimmed, ignoreCase = true) == true
    }
}

internal fun clipboardEntryKey(entry: ClipboardEntry, fallbackIndex: Int): Any {
    val contentKey = entry.text?.let {
        if (it.length > 512) {
            // Compose really doesn't like extremely long keys, so
            // to avoid crashing we just provide a hash
            "h:${it.toFNV1aHash()}"
        } else {
            "t:$it"
        }
    } ?: entry.uri?.let {
        "u:${it.toString().toFNV1aHash()}"
    } ?: "i:$fallbackIndex"
    return "$contentKey:${entry.timestamp}"
}

private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
    var inSampleSize = 1
    if (height > maxDim || width > maxDim) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while ((halfHeight / inSampleSize) >= maxDim && (halfWidth / inSampleSize) >= maxDim) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

private suspend fun loadDownsampledThumbnail(context: Context, entry: ClipboardEntry): ImageBitmap? =
    withContext(Dispatchers.IO) {
        try {
            val localFile = entry.localMediaPath?.let { File(it) }?.takeIf { it.exists() }
                ?: entry.uri?.takeIf { it.scheme == "file" }?.path?.let { File(it) }?.takeIf { it.exists() }

            if (localFile != null) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(localFile.absolutePath, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, THUMBNAIL_TARGET_PX)
                }
                return@withContext BitmapFactory.decodeFile(localFile.absolutePath, decodeOpts)?.asImageBitmap()
            }

            val uri = entry.uri ?: return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            } ?: return@withContext null

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, THUMBNAIL_TARGET_PX)
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)?.asImageBitmap()
            }
        } catch (_: Exception) {
            null
        }
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClipboardEntryView(modifier: Modifier, clipboardEntry: ClipboardEntry, onPaste: (ClipboardEntry) -> Unit, onRemove: (ClipboardEntry) -> Unit, onPin: (ClipboardEntry) -> Unit) {
    val context = LocalContext.current
    val hasImage = clipboardEntry.uri != null && clipboardEntry.text.isNullOrEmpty()
    val thumbnail by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = clipboardEntry.localMediaPath,
        key2 = clipboardEntry.uri
    ) {
        value = if (hasImage) loadDownsampledThumbnail(context, clipboardEntry) else null
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .padding(2.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = androidx.compose.material.ripple.rememberRipple(),
                enabled = true,
                onClick = { onPaste(clipboardEntry) },
                onLongClick = { onPin(clipboardEntry) }
            ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column {
            Row(modifier = Modifier.padding(0.dp)) {
                IconButton(onClick = {
                    onPin(clipboardEntry)
                }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        painterResource(id = R.drawable.push_pin),
                        contentDescription = "Pin",
                        tint = if(clipboardEntry.pinned) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                        },
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1.0f))

                IconButton(onClick = {
                    onRemove(clipboardEntry)
                }, modifier = Modifier.size(32.dp), enabled = !clipboardEntry.pinned) {
                    Icon(
                        painterResource(id = R.drawable.close),
                        contentDescription = "Close",
                        tint = if(clipboardEntry.pinned) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (hasImage) {
                val bmp = thumbnail
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = "Copied image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .padding(horizontal = 8.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                } else {
                    Text(
                        text = clipboardEntry.mimeTypes.firstOrNull() ?: "Image",
                        modifier = Modifier.padding(8.dp, 2.dp),
                        style = Typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            } else {
                val text = (clipboardEntry.text ?: "").let {
                    if(it.length > 256) {
                        it.substring(0, 256) + "..."
                    } else {
                        it
                    }
                }

                Text(text, modifier = Modifier.padding(8.dp, 2.dp), style = Typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}



@Preview(showBackground = true)
@Composable
fun ClipboardEntryViewPreview() {
    val sampleText = listOf("This is an entry", "Copying text a lot", "hunter2", "https://www.example.com/forum/viewpost/1234573193.html?parameter=1234")
    LazyVerticalStaggeredGrid(
        modifier = Modifier.fillMaxWidth(),
        columns = StaggeredGridCells.Adaptive(160.dp),
        verticalItemSpacing = 4.dp,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(sampleText.size) {
            ClipboardEntryView(modifier = Modifier, clipboardEntry = ClipboardEntry(0L, it % 2 == 0, sampleText[it], null, listOf()), onPin = {}, onPaste = {}, onRemove = {})
        }
    }
}

class ClipboardHistoryManager(val context: Context, val coroutineScope: LifecycleCoroutineScope) : PersistentActionState {
    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    val clipboardHistory = mutableStateListOf(ClipboardEntry(
        timestamp = 0L,
        pinned = true,
        text = "Clipboard entries will appear here",
        uri = null,
        mimeTypes = listOf()
    ))
    
    override suspend fun onDeviceUnlocked() {
        loadClipboard()
    }

    init {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                loadClipboard()
            }

            withContext(Dispatchers.Main) {
                clipboardManager.addPrimaryClipChangedListener {
                    if(!context.getSettingBlocking(ClipboardHistoryEnabled)) return@addPrimaryClipChangedListener

                    val clip = clipboardManager.primaryClip ?: return@addPrimaryClipChangedListener
                    if (clip.itemCount <= 0) return@addPrimaryClipChangedListener

                    val clipItem = clip.getItemAt(0)
                    val uri = clipItem?.uri

                    val timestamp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        clip.description?.timestamp
                    } else {
                        null
                    } ?: System.currentTimeMillis()

                    val mimeTypes = List(clip.description?.mimeTypeCount ?: 0) {
                        clip.description?.getMimeType(it)
                    }.filterNotNull()

                    val isSensitive = clip.description?.extras?.getBoolean(
                        ClipDescription.EXTRA_IS_SENSITIVE, false) ?: false
                    if (isSensitive) return@addPrimaryClipChangedListener

                    val isImage = uri != null && mimeTypes.any { it.startsWith("image/", ignoreCase = true) }
                    if (isImage && uri != null) {
                        if (!context.isDirectBootUnlocked) return@addPrimaryClipChangedListener
                        val itemText = clipItem.text?.toString()?.takeIf { it.isNotBlank() }
                        coroutineScope.launch(Dispatchers.IO) {
                            val copiedFile = copyImageUriToMediaDir(uri, timestamp, mimeTypes)
                            if (copiedFile == null) {
                                // Fallback cleanly to text if unreadable image had accompanying text
                                if (itemText != null) {
                                    withContext(Dispatchers.Main) {
                                        addOrUpdateEntry(
                                            ClipboardEntry(
                                                timestamp = timestamp,
                                                pinned = false,
                                                text = itemText,
                                                uri = null,
                                                mimeTypes = mimeTypes
                                            )
                                        )
                                    }
                                }
                                return@launch
                            }

                            val storedUri = resolveShareableOrFallbackUri(copiedFile, uri)
                            withContext(Dispatchers.Main) {
                                addOrUpdateEntry(
                                    ClipboardEntry(
                                        timestamp = timestamp,
                                        pinned = false,
                                        text = itemText,
                                        uri = storedUri,
                                        mimeTypes = mimeTypes,
                                        localMediaPath = copiedFile.absolutePath
                                    )
                                )
                            }
                        }
                    } else {
                        val text = clipItem?.coerceToText(context)?.toString()
                        if (text != null && uri == null) {
                            addOrUpdateEntry(
                                ClipboardEntry(
                                    timestamp = timestamp,
                                    pinned = false,
                                    text = text,
                                    uri = null,
                                    mimeTypes = mimeTypes
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun copyImageUriToMediaDir(uri: Uri, timestamp: Long, mimeTypes: List<String>): File? {
        return try {
            val mediaDir = File(context.filesDir, "clipboard_media").apply { mkdirs() }
            val ext = resolveImageExtension(mimeTypes)
            val tempFile = File(mediaDir, "${timestamp}_tmp.${ext}")

            var hash = -3750763034362895579L
            val fnvPrime = 1099511628211L
            var totalBytes = 0L
            var exceededLimit = false

            val opened = context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        totalBytes += read
                        if (totalBytes > MAX_CLIPBOARD_MEDIA_BYTES) {
                            exceededLimit = true
                            break
                        }
                        output.write(buffer, 0, read)
                        for (idx in 0 until read) {
                            hash = (hash xor (buffer[idx].toLong() and 0xffL)) * fnvPrime
                        }
                    }
                }
                true
            } ?: false

            if (!opened || exceededLimit || totalBytes <= 0L) {
                tempFile.delete()
                return null
            }

            val hashHex = hash.toULong().toString(16)
            val destFile = File(mediaDir, "${timestamp}_${hashHex}.${ext}")
            if (tempFile.renameTo(destFile)) {
                destFile
            } else {
                tempFile
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveShareableOrFallbackUri(localFile: File, originalUri: Uri): Uri {
        val authority = "${context.packageName}.fileprovider"
        val hasFileProvider = try {
            context.packageManager.resolveContentProvider(authority, 0) != null
        } catch (_: Exception) {
            false
        }
        if (hasFileProvider) {
            try {
                return FileProvider.getUriForFile(context, authority, localFile)
            } catch (_: Exception) {
            }
        }
        return if (originalUri.scheme == "content") originalUri else Uri.fromFile(localFile)
    }

    private fun addOrUpdateEntry(entry: ClipboardEntry) {
        val matching = clipboardHistory.filter {
            (entry.text != null && it.text == entry.text) ||
                (entry.uri != null && it.uri == entry.uri)
        }
        val isAlreadyPinned = matching.any { it.pinned }
        clipboardHistory.removeAll(matching)

        val newEntry = entry.copy(pinned = isAlreadyPinned || entry.pinned)
        clipboardHistory.add(newEntry)

        val obsoletePaths = matching.mapNotNull { it.localMediaPath }
            .filter { path -> path != newEntry.localMediaPath }
        deleteUnusedMediaPaths(obsoletePaths)

        saveClipboard()
    }

    private fun deleteUnusedMediaPaths(candidatePaths: List<String>) {
        if (candidatePaths.isEmpty()) return
        val activePaths = clipboardHistory.mapNotNull { it.localMediaPath }.toSet()
        val toDelete = candidatePaths.filter { it !in activePaths }.distinct()
        if (toDelete.isEmpty()) return

        coroutineScope.launch(Dispatchers.IO) {
            toDelete.forEach { path ->
                runCatching { File(path).delete() }
            }
        }
    }

    suspend fun pruneOldItems() = withContext(Dispatchers.Main) {
        val maxDays = context.getSetting(ClipboardMaxDays).toLong()
        val minimumTimestamp = System.currentTimeMillis() - (maxDays * 24L * 60L * 60L * 1000L)
        val removedEntries = mutableListOf<ClipboardEntry>()

        val expired = clipboardHistory.filter {
            (!it.pinned) && (it.timestamp < minimumTimestamp)
        }
        if (expired.isNotEmpty()) {
            clipboardHistory.removeAll(expired)
            removedEntries.addAll(expired)
        }

        val maxItems = context.getSetting(ClipboardMaxItems)
        val numUnpinnedItems = clipboardHistory.count { !it.pinned }

        val numItemsToRemove = numUnpinnedItems - maxItems
        if(numItemsToRemove > 0) {
            for(i in 0 until numItemsToRemove) {
                val idx = clipboardHistory.indexOfFirst { !it.pinned }
                if(idx == -1) break
                removedEntries.add(clipboardHistory.removeAt(idx))
            }
        }

        val candidatePaths = removedEntries.mapNotNull { it.localMediaPath }
        deleteUnusedMediaPaths(candidatePaths)
    }

    private val clipboardIoMutex = Mutex()

    private fun saveClipboard() {
        if(!context.isDirectBootUnlocked) return

        coroutineScope.launch {
            clipboardIoMutex.withLock {
                withContext(Dispatchers.IO) {
                    pruneOldItems()

                    val snapshot = withContext(Dispatchers.Main) {
                        clipboardHistory.toList()
                    }
                    val json = Json.encodeToString(snapshot)

                    val file = File(context.filesDir, "clipboard.json")
                    val tmpFile = File(context.filesDir, "clipboard.json.tmp")
                    tmpFile.writeText(json)
                    if (!tmpFile.renameTo(file)) {
                        file.writeText(json)
                        tmpFile.delete()
                    }
                }
            }
        }
    }

    private suspend fun loadClipboard() = clipboardIoMutex.withLock {
        if(!context.isDirectBootUnlocked) return@withLock

        try {
            val file = File(context.filesDir, "clipboard.json")

            if(!context.getSettingBlocking(ClipboardHistoryEnabled)) {
                file.delete()
                File(context.filesDir, "clipboard_media").deleteRecursively()
            }else if (file.exists()) {
                val reader = file.bufferedReader()
                val inputString = reader.use { it.readText() }

                val data = Json.decodeFromString<List<ClipboardEntry>>(inputString)

                withContext(Dispatchers.Main) {
                    clipboardHistory.clear()
                    clipboardHistory.addAll(data)
                }
                pruneOldItems()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun onPaste(item: ClipboardEntry) {
        clipboardHistory.remove(item)

        clipboardHistory.add(
            ClipboardEntry(
                timestamp = System.currentTimeMillis(),
                pinned = item.pinned,
                text = item.text,
                uri = item.uri,
                mimeTypes = item.mimeTypes,
                localMediaPath = item.localMediaPath
            )
        )

        saveClipboard()
    }

    fun onPin(item: ClipboardEntry) {
        clipboardHistory.remove(item)

        clipboardHistory.add(
            ClipboardEntry(
                timestamp = System.currentTimeMillis(),
                pinned = !item.pinned,
                text = item.text,
                uri = item.uri,
                mimeTypes = item.mimeTypes,
                localMediaPath = item.localMediaPath
            )
        )

        saveClipboard()
    }

    fun onRemove(item: ClipboardEntry) {
        // Clear the clipboard if the item being removed is the current one
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val primaryItem = clipboardManager.primaryClip?.getItemAt(0)
            val matchesText = (item.text != null) && item.text == primaryItem?.coerceToText(context)?.toString()
            val matchesUri = (item.uri != null) && item.uri == primaryItem?.uri
            if(matchesText || matchesUri) {
                clipboardManager.clearPrimaryClip()
            }
        }
        clipboardHistory.remove(item)
        item.localMediaPath?.let { deleteUnusedMediaPaths(listOf(it)) }
        saveClipboard()
    }

    override suspend fun cleanUp() {
        saveClipboard()
    }

}

fun String.toFNV1aHash(): Long {
    val fnvPrime: Long = 1099511628211L
    var hash: Long = -3750763034362895579L

    for (byte in this.toByteArray()) {
        hash = hash xor byte.toLong()
        hash *= fnvPrime
    }

    return hash
}

@Composable
private fun ClipboardLimitPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = Typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
val ClipboardHistoryAction = Action(
    icon = R.drawable.clipboard_manager,
    name = R.string.clipboard_manager_action_title,
    simplePressImpl = null,
    canShowKeyboard = true,
    persistentState = { manager ->
        ClipboardHistoryManager(manager.getContext(), manager.getLifecycleScope())
    },
    persistentStateInitialization = PersistentStateInitialization.OnKeyboardLoad,
    windowImpl = { manager, persistent ->
        val unlocked = !manager.isDeviceLocked()
        val clipboardHistoryManager = persistent as ClipboardHistoryManager

        manager.getLifecycleScope().launch { clipboardHistoryManager.pruneOldItems() }
        object : ActionWindow {
            private val searchText = mutableStateOf("")
            private val searching = mutableStateOf(false)

            @Composable
            override fun windowName(): String {
                return stringResource(R.string.clipboard_manager_action_title)
            }

            @Composable
            override fun WindowTitleBar(rowScope: RowScope) {
                val clipboardHistory = useDataStore(ClipboardHistoryEnabled, blocking = true)
                if (!unlocked || !clipboardHistory.value) {
                    super.WindowTitleBar(rowScope)
                    return
                }

                if (searching.value) {
                    with(rowScope) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceBright,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .padding(2.dp)
                                .weight(1.0f)
                        ) {
                            Box(
                                modifier = Modifier.padding(8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                ActionTextEditor(text = searchText)
                            }
                        }
                    }
                    IconButton(onClick = {
                        searchText.value = ""
                        searching.value = false
                    }) {
                        Icon(
                            painterResource(id = R.drawable.close),
                            contentDescription = "Close search"
                        )
                    }
                } else {
                    super.WindowTitleBar(rowScope)

                    IconButton(onClick = {
                        searching.value = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search clipboard"
                        )
                    }

                    IconButton(onClick = {
                        val numUnpinnedItems =
                            clipboardHistoryManager.clipboardHistory.count { !it.pinned }
                        if (clipboardHistoryManager.clipboardHistory.size == 0) {
                            manager.requestDialog(
                                "There are no items to clear. Disable clipboard history?",
                                listOf(
                                    DialogRequestItem("Cancel") {},
                                    DialogRequestItem("Disable") {
                                        clipboardHistory.setValue(false)
                                    },
                                ),
                                {}
                            )
                        } else if (numUnpinnedItems == 0) {
                            manager.requestDialog(
                                "There are no unpinned items to clear. Unpin all items?",
                                listOf(
                                    DialogRequestItem("Cancel") {},
                                    DialogRequestItem("Unpin") {
                                        clipboardHistoryManager.clipboardHistory.toList().forEach {
                                            if (it.pinned) {
                                                clipboardHistoryManager.onPin(it)
                                            }
                                        }
                                    },
                                ),
                                {}
                            )
                        } else {
                            manager.requestDialog(
                                "Clear all unpinned items?",
                                listOf(
                                    DialogRequestItem("Cancel") {},
                                    DialogRequestItem("Clear") {
                                        clipboardHistoryManager.clipboardHistory.toList().forEach {
                                            if (!it.pinned) {
                                                clipboardHistoryManager.onRemove(it)
                                            }
                                        }
                                    },
                                ),
                                {}
                            )
                        }
                    }) {
                        Icon(
                            painterResource(id = R.drawable.close),
                            contentDescription = "Clear clipboard"
                        )
                    }
                }
            }

            @Composable
            override fun WindowContents(keyboardShown: Boolean) {
                val view = LocalView.current
                val clipboardHistory = useDataStore(ClipboardHistoryEnabled, blocking = true)
                val maxItemsPref = useDataStore(ClipboardMaxItems)
                val maxDaysPref = useDataStore(ClipboardMaxDays)

                if(!unlocked) {
                    ScrollableList {
                        PaymentSurface(isPrimary = true, title = "Device Locked") {
                            ParagraphText("Please unlock your device to access clipboard history")
                        }
                    }
                } else if(!clipboardHistory.value) {
                    ScrollableList {
                        PaymentSurface(isPrimary = true, title = "Clipboard History Inactive") {
                            ParagraphText("Clipboard history is not enabled. To save clipboard items, you can enable clipboard history. This will keep up to ${maxItemsPref.value} items for ${maxDaysPref.value} days unless pinned. Passwords and other items marked sensitive are excluded from history.")
                            Button(onClick = {
                                clipboardHistory.setValue(true)
                            }, modifier = Modifier
                                .padding(8.dp)
                                .fillMaxWidth()) {
                                Text("Enable Clipboard History")
                            }
                        }
                    }
                } else {
                    LaunchedEffect(maxItemsPref.value, maxDaysPref.value) {
                        clipboardHistoryManager.pruneOldItems()
                    }

                    val filteredEntries = filterClipboardEntries(
                        clipboardHistoryManager.clipboardHistory,
                        searchText.value
                    )

                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Max:",
                                style = Typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            CLIPBOARD_MAX_ITEM_OPTIONS.forEach { option ->
                                ClipboardLimitPill(
                                    label = option.toString(),
                                    selected = maxItemsPref.value == option,
                                    onClick = { maxItemsPref.setValue(option) }
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = "Days:",
                                style = Typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            CLIPBOARD_MAX_DAY_OPTIONS.forEach { option ->
                                ClipboardLimitPill(
                                    label = "${option}d",
                                    selected = maxDaysPref.value == option,
                                    onClick = { maxDaysPref.setValue(option) }
                                )
                            }
                        }

                        if (filteredEntries.isEmpty() && searchText.value.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No matching clipboard items",
                                    style = Typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyVerticalStaggeredGrid(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                columns = StaggeredGridCells.Adaptive(180.dp),
                                verticalItemSpacing = 4.dp,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                items(filteredEntries.size, key = { idx ->
                                    clipboardEntryKey(filteredEntries[idx], idx)
                                }) { idx ->
                                    val entry = filteredEntries[idx]
                                    ClipboardEntryView(
                                        modifier = Modifier.animateItemPlacement(),
                                        clipboardEntry = entry,
                                        onPaste = {
                                            if (it.uri != null) {
                                                if (searching.value) {
                                                    searching.value = false
                                                    manager.unsetInputConnection()
                                                }
                                                val pasted = try {
                                                    it.uri.scheme == "content" && manager.typeUri(it.uri, it.mimeTypes)
                                                } catch (_: Exception) {
                                                    false
                                                }
                                                if (!pasted) {
                                                    Toast.makeText(
                                                        manager.getContext(),
                                                        "App does not support image insertion",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            } else if (it.text != null) {
                                                manager.typeText(it.text)
                                            }
                                            clipboardHistoryManager.onPaste(it)
                                            manager.performHapticAndAudioFeedback(Constants.CODE_OUTPUT_TEXT, view)
                                        },
                                        onRemove = {
                                            clipboardHistoryManager.onRemove(it)
                                            manager.performHapticAndAudioFeedback(Constants.CODE_TAB, view)
                                        },
                                        onPin = {
                                            clipboardHistoryManager.onPin(it)
                                            manager.performHapticAndAudioFeedback(Constants.CODE_TAB, view)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            override fun close() {
                searchText.value = ""
                searching.value = false
            }
        }
    }
)