package org.futo.inputmethod.latin.uix.theme.selector

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.uix.CustomKeyboardBackgroundUriSetting
import org.futo.inputmethod.latin.uix.CustomKeyboardBackgroundDimSetting
import org.futo.inputmethod.latin.uix.CustomKeyboardBackgroundBlurSetting
import org.futo.inputmethod.latin.uix.CustomPhotoTextContrastSetting
import org.futo.inputmethod.latin.uix.SavedCustomPhotoThemesSetting
import org.futo.inputmethod.latin.uix.CustomKeyboardBackgroundZoomSetting
import org.futo.inputmethod.latin.uix.CustomKeyboardBackgroundXSetting
import org.futo.inputmethod.latin.uix.CustomKeyboardBackgroundYSetting
import org.futo.inputmethod.latin.uix.setSettingBlocking
import org.futo.inputmethod.latin.uix.KeyBordersSetting
import org.futo.inputmethod.latin.uix.KeyOpacitySetting
import org.futo.inputmethod.latin.uix.KeyShapeSetting
import org.futo.inputmethod.latin.uix.SpacebarColorSetting
import org.futo.inputmethod.latin.uix.settings.SettingRadio
import org.futo.inputmethod.latin.uix.KeyHintsSetting
import org.futo.inputmethod.latin.uix.KeyboardBottomOffsetSetting
import org.futo.inputmethod.latin.uix.KeyboardHeightMultiplierSetting
import org.futo.inputmethod.latin.uix.THEME_KEY
import org.futo.inputmethod.latin.uix.normalizeThemeKey
import org.futo.inputmethod.latin.uix.settings.SettingSlider
import org.futo.inputmethod.latin.uix.settings.SettingToggleDataStore
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.theme.CustomPhotoLightColorScheme
import org.futo.inputmethod.latin.uix.theme.CustomPhotoTheme
import org.futo.inputmethod.latin.uix.theme.DarkColorScheme
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.ThemeOptionKeys
import org.futo.inputmethod.latin.uix.theme.ThemeOptions
import org.futo.inputmethod.latin.uix.theme.Typography
import org.futo.inputmethod.latin.uix.theme.UixThemeWrapper
import org.futo.inputmethod.latin.uix.theme.presets.AMOLEDDarkPurple
import org.futo.inputmethod.latin.uix.theme.presets.AMOLEDNothing
import org.futo.inputmethod.latin.uix.theme.presets.IPlusMattePreviewKeyboard
import org.futo.inputmethod.latin.uix.theme.presets.IPlusMatteTheme
import org.futo.inputmethod.latin.uix.theme.presets.NothingLight
import org.futo.inputmethod.latin.uix.theme.presets.NothingDark
import org.futo.inputmethod.latin.uix.theme.presets.ClassicMaterialDark
import org.futo.inputmethod.latin.uix.theme.presets.DynamicDarkTheme
import org.futo.inputmethod.latin.uix.theme.presets.DynamicLightTheme
import org.futo.inputmethod.latin.uix.theme.presets.DynamicNothing
import org.futo.inputmethod.latin.uix.theme.presets.DynamicSystemTheme
import org.futo.inputmethod.latin.uix.theme.presets.VoiceInputTheme
import kotlin.math.roundToInt

private fun loadCustomPhotoThumbnail(context: Context, uriValue: String): ImageBitmap? {
    if (uriValue.isBlank()) return null
    return runCatching {
        val uri = Uri.parse(uriValue)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val targetSize = 360
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > targetSize * 2 ||
            bounds.outHeight / sampleSize > targetSize * 2
        ) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: return null

        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val rotationDegrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }

        val finalBitmap = if (rotationDegrees != 0f) {
            val matrix = Matrix().apply { postRotate(rotationDegrees) }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } else {
            decoded
        }

        finalBitmap.asImageBitmap()
    }.getOrNull()
}

@Composable
fun ThemePreview(
    theme: ThemeOption,
    isSelected: Boolean = false,
    overrideName: String? = null,
    modifier: Modifier = Modifier,
    customPhotoUri: String = "",
    customPhotoDim: Float = 0.33f,
    customPhotoContrast: String = "dark_bg",
    onClick: () -> Unit = { }
) {
    if(theme == DynamicSystemTheme) return DynamicThemePreview(isSelected, onClick)

    val context = LocalContext.current
    val isInspecting = LocalInspectionMode.current
    val colors = remember(theme.key, customPhotoContrast) {
        if (theme.key == CustomPhotoTheme.key) {
            if (customPhotoContrast == "light_bg") CustomPhotoLightColorScheme else DarkColorScheme
        } else {
            theme.obtainColors(context)
        }
    }

    val photoThumbnail by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = customPhotoUri,
        key2 = theme.key
    ) {
        value = if (theme.key == CustomPhotoTheme.key && customPhotoUri.isNotBlank() && !isInspecting) {
            withContext(Dispatchers.IO) {
                loadCustomPhotoThumbnail(context, customPhotoUri)
            }
        } else {
            null
        }
    }

    val currColors = MaterialTheme.colorScheme

    val borderWidth = if (isSelected) {
        4.dp
    } else {
        Dp.Hairline
    }

    val borderColor = if (isSelected) {
        currColors.inversePrimary
    } else {
        currColors.outline
    }

    val textColor = colors.onBackground

    // TODO: These have to be manually kept same as those in BasicThemeProvider
    val spacebarColor = colors.outline.copy(alpha = 0.33f)
    val actionColor = colors.primary

    val keyboardShape = RoundedCornerShape(8.dp)

    val previewModifier = if(isInspecting) {
        modifier.width(172.dp)
    } else {
        modifier
    }

    Surface(
        modifier = previewModifier
            .padding(12.dp)
            .height(128.dp)
            .border(borderWidth, borderColor, keyboardShape)
            .clickable { onClick() },
        color = colors.surface,
        shape = keyboardShape
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (photoThumbnail != null) {
                Image(
                    bitmap = photoThumbnail!!,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = customPhotoDim.coerceIn(0.0f, 0.85f)))
                )
            }

            // Theme name and action bar
            Text(
                text = overrideName ?: stringResource(theme.name),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .background(colors.background)
                    .fillMaxWidth()
                    .padding(4.dp),
                color = textColor,
                style = Typography.labelSmall
            )

            // Keyboard contents
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                if (theme.key == IPlusMatteTheme.key) {
                    IPlusMattePreviewKeyboard()
                } else {
                    // Spacebar
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(18.dp)
                            .align(Alignment.BottomCenter),
                        color = spacebarColor,
                        shape = RoundedCornerShape(12.dp)
                    ) { }

                    // Enter key
                    Surface(
                        modifier = Modifier
                            .width(24.dp)
                            .height(18.dp)
                            .align(Alignment.BottomEnd)
                            .padding(0.dp, 1.dp),
                        color = actionColor,
                        shape = RoundedCornerShape(4.dp)
                    ) { }
                }
            }
        }
    }
}

// Special case to demonstrate the light and dark mode
@Preview
@Composable
fun DynamicThemePreview(isSelected: Boolean = false, onClick: () -> Unit = { }) {
    Box {
        ThemePreview(
            DynamicLightTheme,
            isSelected = isSelected,
            onClick = onClick,
            overrideName = stringResource(DynamicSystemTheme.name),
            modifier = Modifier.clip(GenericShape { size, _ ->
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width * 0.66f, 0f)
                    lineTo(size.width * 0.33f, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                addPath(path)
            })
        )
        ThemePreview(
            DynamicDarkTheme,
            isSelected = isSelected,
            onClick = onClick,
            overrideName = stringResource(DynamicSystemTheme.name),
            modifier = Modifier.clip(GenericShape { size, _ ->
                val path = Path().apply {
                    moveTo(size.width * 0.66f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, size.height)
                    lineTo(size.width * 0.33f, size.height)
                    close()
                }
                addPath(path)
            })
        )
    }
}

@Composable
fun AddCustomThemeButton(onClick: () -> Unit = { }) {
    val context = LocalContext.current
    val currColors = MaterialTheme.colorScheme

    val keyboardShape = RoundedCornerShape(8.dp)

    Surface(
        modifier = Modifier
            .padding(12.dp)
            .width(172.dp)
            .height(128.dp)
            .clickable { onClick() },
        color = currColors.surfaceVariant,
        shape = keyboardShape
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Icon(
                Icons.Default.Add, contentDescription = "Add custom photo theme", modifier = Modifier
                    .size(48.dp)
                    .align(
                        Alignment.Center
                    )
            )
        }
    }
}

@Composable
fun ThemePicker(onSelected: (ThemeOption) -> Unit) {
    val context = LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val permissionPersisted = runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }.isSuccess

            if (!permissionPersisted) {
                Toast.makeText(
                    context,
                    "Couldn't keep access to this photo. Please choose another image.",
                    Toast.LENGTH_LONG
                ).show()
                return@rememberLauncherForActivityResult
            }

            context.setSettingBlocking(CustomKeyboardBackgroundUriSetting.key, uri.toString())
            onSelected(CustomPhotoTheme)
        }
    }

    val currentTheme = normalizeThemeKey(useDataStore(THEME_KEY.key, "").value)
    val savedThemes = useDataStore(SavedCustomPhotoThemesSetting.key, SavedCustomPhotoThemesSetting.default)
    val photoUri = useDataStore(CustomKeyboardBackgroundUriSetting.key, CustomKeyboardBackgroundUriSetting.default)
    val photoDim = useDataStore(CustomKeyboardBackgroundDimSetting.key, CustomKeyboardBackgroundDimSetting.default)
    val photoBlur = useDataStore(CustomKeyboardBackgroundBlurSetting.key, CustomKeyboardBackgroundBlurSetting.default)
    val photoZoom = useDataStore(CustomKeyboardBackgroundZoomSetting.key, CustomKeyboardBackgroundZoomSetting.default)
    val photoX = useDataStore(CustomKeyboardBackgroundXSetting.key, CustomKeyboardBackgroundXSetting.default)
    val photoY = useDataStore(CustomKeyboardBackgroundYSetting.key, CustomKeyboardBackgroundYSetting.default)
    val photoContrast = useDataStore(CustomPhotoTextContrastSetting.key, CustomPhotoTextContrastSetting.default)
    val keyShapeSetting = useDataStore(KeyShapeSetting.key, KeyShapeSetting.default)
    val newThemeName = remember { mutableStateOf("") }
    val renameTarget = remember { mutableStateOf<String?>(null) }
    val renameName = remember { mutableStateOf("") }

    fun applyPhotoTheme(record: String) {
        val p = record.split("|")
        if (p.size != 7) return
        context.setSettingBlocking(CustomKeyboardBackgroundUriSetting.key, android.net.Uri.decode(p[1]))
        context.setSettingBlocking(CustomKeyboardBackgroundDimSetting.key, p[2].toFloatOrNull() ?: 0.33f)
        context.setSettingBlocking(CustomKeyboardBackgroundBlurSetting.key, p[3].toFloatOrNull() ?: 0.0f)
        context.setSettingBlocking(CustomKeyboardBackgroundZoomSetting.key, p[4].toFloatOrNull() ?: 1.0f)
        context.setSettingBlocking(CustomKeyboardBackgroundXSetting.key, p[5].toFloatOrNull() ?: 0.0f)
        context.setSettingBlocking(CustomKeyboardBackgroundYSetting.key, p[6].toFloatOrNull() ?: 0.0f)
        onSelected(CustomPhotoTheme)
    }

    val isInspecting = LocalInspectionMode.current
    val availableThemeOptions = remember {
        ThemeOptionKeys.mapNotNull { key ->
            ThemeOptions[key]?.let { Pair(key, it) }
        }.filter {
            it.second.available(context)
        }.filter {
            when (isInspecting) {
                true -> !it.second.dynamic
                else -> true
            }
        }
    }


    val originalDirection = LocalLayoutDirection.current

    Column {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            LazyVerticalGrid(
                modifier = Modifier.fillMaxWidth(),
                columns = GridCells.Adaptive(minSize = 172.dp),
                horizontalArrangement = if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
                    Arrangement.End
                } else {
                    Arrangement.Start
                }
            ) {
                items(availableThemeOptions.count()) {
                    val themeOption = availableThemeOptions[it].second

                    ThemePreview(
                        theme = themeOption,
                        isSelected = themeOption.key == currentTheme,
                        customPhotoUri = if (themeOption.key == CustomPhotoTheme.key) photoUri.value else "",
                        customPhotoDim = if (themeOption.key == CustomPhotoTheme.key) photoDim.value else 0.33f,
                        customPhotoContrast = if (themeOption.key == CustomPhotoTheme.key) photoContrast.value else "dark_bg"
                    ) {
                        onSelected(themeOption)
                    }
                }

                item {
                    AddCustomThemeButton {
                        photoPicker.launch(arrayOf("image/*"))
                    }
                }

                item(span = { GridItemSpan(maxCurrentLineSpan) }) { }

                if (currentTheme == CustomPhotoTheme.key) {
                    item(span = { GridItemSpan(maxCurrentLineSpan) }) {
                        CompositionLocalProvider(LocalLayoutDirection provides originalDirection) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                SettingSlider(
                                    title = "Photo background dim",
                                    setting = CustomKeyboardBackgroundDimSetting,
                                    range = 0.0f..0.85f,
                                    transform = { it.coerceIn(0.0f, 0.85f) },
                                    indicator = { "${(it * 100).roundToInt()}%" },
                                    subtitle = "Adjust image darkness so key labels stay readable"
                                )

                                SettingSlider(
                                    title = "Photo background blur",
                                    setting = CustomKeyboardBackgroundBlurSetting,
                                    range = 0.0f..20.0f,
                                    transform = { it.coerceIn(0.0f, 20.0f) },
                                    indicator = { it.roundToInt().toString() },
                                    subtitle = "Soften the photo behind the keys"
                                )

                                SettingSlider(
                                    title = "Photo zoom",
                                    setting = CustomKeyboardBackgroundZoomSetting,
                                    range = 1.0f..2.5f,
                                    transform = { it.coerceIn(1.0f, 2.5f) },
                                    indicator = { "${"%.1f".format(it)}x" },
                                    subtitle = "Zoom in to frame the photo"
                                )

                                SettingSlider(
                                    title = "Photo horizontal position",
                                    setting = CustomKeyboardBackgroundXSetting,
                                    range = -1.0f..1.0f,
                                    transform = { it.coerceIn(-1.0f, 1.0f) },
                                    indicator = { "${(it * 100).roundToInt()}%" },
                                    subtitle = "Move the photo left or right"
                                )

                                SettingSlider(
                                    title = "Photo vertical position",
                                    setting = CustomKeyboardBackgroundYSetting,
                                    range = -1.0f..1.0f,
                                    transform = { it.coerceIn(-1.0f, 1.0f) },
                                    indicator = { "${(it * 100).roundToInt()}%" },
                                    subtitle = "Move the photo up or down"
                                )

                                SettingRadio(
                                    title = "Photo text contrast",
                                    options = listOf("dark_bg", "light_bg"),
                                    optionNames = listOf(
                                        "Light text (for dark photos)",
                                        "Dark text (for bright photos)"
                                    ),
                                    setting = photoContrast
                                )

                                Text(
                                    text = "Reset photo adjustments",
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clickable {
                                            context.setSettingBlocking(CustomKeyboardBackgroundDimSetting.key, CustomKeyboardBackgroundDimSetting.default)
                                            context.setSettingBlocking(CustomKeyboardBackgroundBlurSetting.key, CustomKeyboardBackgroundBlurSetting.default)
                                            context.setSettingBlocking(CustomKeyboardBackgroundZoomSetting.key, CustomKeyboardBackgroundZoomSetting.default)
                                            context.setSettingBlocking(CustomKeyboardBackgroundXSetting.key, CustomKeyboardBackgroundXSetting.default)
                                            context.setSettingBlocking(CustomKeyboardBackgroundYSetting.key, CustomKeyboardBackgroundYSetting.default)
                                        }
                                        .padding(12.dp)
                                )

                                TextButton(
                                    onClick = {
                                        val currentUri = photoUri.value
                                        if (currentUri.isNotBlank()) {
                                            runCatching {
                                                context.contentResolver.releasePersistableUriPermission(
                                                    Uri.parse(currentUri),
                                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                )
                                            }
                                        }
                                        context.setSettingBlocking(
                                            CustomKeyboardBackgroundUriSetting.key,
                                            CustomKeyboardBackgroundUriSetting.default
                                        )
                                        onSelected(IPlusMatteTheme)
                                    },
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                ) {
                                    Text(
                                        text = "Remove current photo",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                item(span = { GridItemSpan(maxCurrentLineSpan) }) {
                    CompositionLocalProvider(LocalLayoutDirection provides originalDirection) {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                            Text("Save current photo theme", style = Typography.titleMedium)
                            OutlinedTextField(
                                value = newThemeName.value,
                                onValueChange = { newThemeName.value = it.take(32) },
                                label = { Text("Theme name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    val name = newThemeName.value.trim()
                                    if (name.isNotEmpty() && photoUri.value.isNotBlank()) {
                                        val record = listOf(
                                            android.net.Uri.encode(name),
                                            android.net.Uri.encode(photoUri.value),
                                            photoDim.value.toString(),
                                            photoBlur.value.toString(),
                                            photoZoom.value.toString(),
                                            photoX.value.toString(),
                                            photoY.value.toString()
                                        ).joinToString("|")
                                        context.setSettingBlocking(SavedCustomPhotoThemesSetting.key, savedThemes.value + record)
                                        newThemeName.value = ""
                                    }
                                },
                                enabled = newThemeName.value.trim().isNotEmpty() && photoUri.value.isNotBlank(),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Save theme") }

                            savedThemes.value.sorted().forEach { record ->
                                val parts = record.split("|")
                                if (parts.size == 7) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Text(android.net.Uri.decode(parts[0]))
                                        androidx.compose.foundation.layout.Row {
                                            TextButton(onClick = { applyPhotoTheme(record) }) { Text("Apply") }
                                            TextButton(onClick = {
                                                renameTarget.value = record
                                                renameName.value = android.net.Uri.decode(parts[0])
                                            }) { Text("Rename") }
                                            TextButton(onClick = {
                                                context.setSettingBlocking(SavedCustomPhotoThemesSetting.key, savedThemes.value - record)
                                            }) { Text("Delete") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item(span = { GridItemSpan(maxCurrentLineSpan) }) {
                    CompositionLocalProvider(LocalLayoutDirection provides originalDirection) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SettingToggleDataStore(
                                title = "Key borders",
                                setting = KeyBordersSetting
                            )
                            KeyShapeSelector(
                                selectedShape = keyShapeSetting.value,
                                onSelect = { keyShapeSetting.setValue(it) }
                            )
                            SettingSlider(
                                title = "Key background opacity",
                                setting = KeyOpacitySetting,
                                range = 0.15f..1.0f,
                                transform = { it.coerceIn(0.15f, 1.0f) },
                                indicator = { "${(it * 100).roundToInt()}%" },
                                subtitle = "Adjust key transparency over your keyboard background"
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }

                item(span = { GridItemSpan(maxCurrentLineSpan) }) {
                    CompositionLocalProvider(LocalLayoutDirection provides originalDirection) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SettingRadio(
                                title = "Spacebar Color",
                                options = listOf("default", "red", "blue", "yellow", "white", "dark"),
                                optionNames = listOf("Default", "Matte Red", "Matte Blue", "Matte Yellow", "Matte White", "Matte Dark"),
                                setting = useDataStore(
                                    key = SpacebarColorSetting.key,
                                    default = SpacebarColorSetting.default
                                )
                            )
                        }
                    }
                }
            }
        }

        if (renameTarget.value != null) {
            AlertDialog(
                onDismissRequest = { renameTarget.value = null },
                title = { Text("Rename photo theme") },
                text = {
                    OutlinedTextField(
                        value = renameName.value,
                        onValueChange = { renameName.value = it.take(32) },
                        singleLine = true,
                        label = { Text("Theme name") }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val old = renameTarget.value
                        val parts = old?.split("|")
                        val name = renameName.value.trim()
                        if (old != null && parts != null && parts.size == 7 && name.isNotEmpty()) {
                            val renamed = (listOf(android.net.Uri.encode(name)) + parts.drop(1)).joinToString("|")
                            context.setSettingBlocking(SavedCustomPhotoThemesSetting.key, (savedThemes.value - old) + renamed)
                        }
                        renameTarget.value = null
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { renameTarget.value = null }) { Text("Cancel") }
                }
            )
        }
    }
}


@Composable
private fun KeyShapeSelector(
    selectedShape: String,
    onSelect: (String) -> Unit
) {
    val options = listOf(
        Triple("sharp", "Sharp", 2.dp),
        Triple("classic", "Classic", 8.dp),
        Triple("rounded", "Rounded", 14.dp)
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Key shape",
            style = Typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (key, label, radius) ->
                val selected = selectedShape == key
                val shape = RoundedCornerShape(radius)
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = shape
                        )
                        .clip(shape)
                        .clickable { onSelect(key) },
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = shape
                ) {
                    Text(
                        text = label,
                        textAlign = TextAlign.Center,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = Typography.labelLarge,
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp)
                    )
                }
            }
        }
    }
}


@Preview
@Composable
private fun ThemePickerPreview() {
    Column {
        UixThemeWrapper(VoiceInputTheme.obtainColors(LocalContext.current)) {
            Surface(
                color = MaterialTheme.colorScheme.background
            ) {
                ThemePicker {}
            }
        }
        UixThemeWrapper(ClassicMaterialDark.obtainColors(LocalContext.current)) {
            Surface(
                color = MaterialTheme.colorScheme.background
            ) {
                ThemePicker {}
            }
        }
        UixThemeWrapper(AMOLEDDarkPurple.obtainColors(LocalContext.current)) {
            Surface(
                color = MaterialTheme.colorScheme.background
            ) {
                ThemePicker {}
            }
        }
        UixThemeWrapper(AMOLEDNothing.obtainColors(LocalContext.current)) {
            Surface(
                color = MaterialTheme.colorScheme.background
            ) {
                ThemePicker {}
            }
        }
        UixThemeWrapper(DynamicNothing.obtainColors(LocalContext.current)) {
            Surface(
                color = MaterialTheme.colorScheme.background
            ) {
                ThemePicker {}
            }
        }
        UixThemeWrapper(NothingLight.obtainColors(LocalContext.current)) {
            Surface(
                color = MaterialTheme.colorScheme.background
            ) {
                ThemePicker {}
            }
        }
        UixThemeWrapper(NothingDark.obtainColors(LocalContext.current)) {
            Surface(
                color = MaterialTheme.colorScheme.background
            ) {
                ThemePicker {}
            }
        }
    }
}