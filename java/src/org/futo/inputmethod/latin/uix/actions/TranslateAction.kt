package org.futo.inputmethod.latin.uix.actions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.futo.inputmethod.keyboard.internal.CloudTranslationHandler
import org.futo.inputmethod.keyboard.internal.TranslationHelper
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.Suggest.TAG
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionEditText
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.LocalManager
import org.futo.inputmethod.latin.uix.settings.useSharedPrefsString
import org.futo.inputmethod.latin.uix.settings.useUpdateSharedPrefString
import org.futo.inputmethod.latin.uix.theme.Typography

internal fun swapLanguagePair(currentSource: String, currentTarget: String): Pair<String, String>? {
    if (currentSource == "auto") return null
    return currentTarget to currentSource
}

internal fun toggleBnEnPair(currentSource: String, currentTarget: String): Pair<String, String> {
    return if (currentSource == "bn" && currentTarget == "en") {
        "en" to "bn"
    } else {
        "bn" to "en"
    }
}

internal fun sortLanguagesForPicker(
    languageList: Map<String, String>,
    allowAuto: Boolean
): List<Pair<String, String>> {
    val pinnedOrder = listOfNotNull(
        if (allowAuto) "auto" else null,
        "bn",
        "en"
    )
    val pinned = pinnedOrder.mapNotNull { code ->
        languageList[code]?.let { label -> code to label }
    }
    val remaining = languageList.entries
        .filter { (code, _) -> (allowAuto || code != "auto") && code !in pinnedOrder }
        .sortedBy { it.value.lowercase() }
        .map { it.key to it.value }
    return pinned + remaining
}

private enum class TranslationPickerSide {
    SOURCE,
    TARGET
}

@Composable
private fun LanguagePickerButton(
    selectedCode: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceBright
        },
        shape = RoundedCornerShape(24.dp),
        onClick = onClick,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .padding(2.dp)
            .fillMaxWidth()
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                TranslationHelper.getLanguageName(selectedCode),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = Typography.bodyMedium
            )
        }
    }
}

@Composable
private fun LanguageSelector(
    modifier: Modifier,
    activePicker: TranslationPickerSide?,
    onTogglePicker: (TranslationPickerSide) -> Unit,
    mSelectedLanguage1: MutableState<String>,
    mSelectedLanguage2: MutableState<String>,
    onLanguagesChanged: () -> Unit = {},
) {
    val updateSharedPref = useUpdateSharedPrefString()
    val canSwap = mSelectedLanguage1.value != "auto"
    val isBnEnActive =
        (mSelectedLanguage1.value == "bn" && mSelectedLanguage2.value == "en") ||
            (mSelectedLanguage1.value == "en" && mSelectedLanguage2.value == "bn")

    Row(
        modifier = modifier.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            LanguagePickerButton(
                selectedCode = mSelectedLanguage1.value,
                isActive = activePicker == TranslationPickerSide.SOURCE,
                onClick = { onTogglePicker(TranslationPickerSide.SOURCE) }
            )
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceBright,
            shape = RoundedCornerShape(18.dp),
            onClick = {
                val swapped = swapLanguagePair(mSelectedLanguage1.value, mSelectedLanguage2.value)
                    ?: return@Surface
                mSelectedLanguage1.value = swapped.first
                mSelectedLanguage2.value = swapped.second
                updateSharedPref(Settings.PREF_KEY_LAST_SOURCE_LANG, swapped.first)
                updateSharedPref(Settings.PREF_KEY_LAST_TARGET_LANG, swapped.second)
                onLanguagesChanged()
            },
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .alpha(if (canSwap) 1f else 0.4f)
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⇄",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            LanguagePickerButton(
                selectedCode = mSelectedLanguage2.value,
                isActive = activePicker == TranslationPickerSide.TARGET,
                onClick = { onTogglePicker(TranslationPickerSide.TARGET) }
            )
        }

        Spacer(modifier = Modifier.width(2.dp))

        Surface(
            color = if (isBnEnActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceBright
            },
            contentColor = if (isBnEnActive) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onBackground
            },
            shape = RoundedCornerShape(18.dp),
            onClick = {
                val next = toggleBnEnPair(mSelectedLanguage1.value, mSelectedLanguage2.value)
                mSelectedLanguage1.value = next.first
                mSelectedLanguage2.value = next.second
                updateSharedPref(Settings.PREF_KEY_LAST_SOURCE_LANG, next.first)
                updateSharedPref(Settings.PREF_KEY_LAST_TARGET_LANG, next.second)
                onLanguagesChanged()
            },
            modifier = Modifier.padding(2.dp)
        ) {
            Text(
                text = if (mSelectedLanguage1.value == "bn" && mSelectedLanguage2.value == "en") {
                    "Bn→En"
                } else if (mSelectedLanguage1.value == "en" && mSelectedLanguage2.value == "bn") {
                    "En→Bn"
                } else {
                    "Bn⇄En"
                },
                style = Typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)
            )
        }
    }
}

@Composable
private fun InlineLanguageListOverlay(
    side: TranslationPickerSide,
    selectedCode: String,
    languageList: Map<String, String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val entries = remember(side, languageList) {
        sortLanguagesForPicker(languageList, allowAuto = side == TranslationPickerSide.SOURCE)
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (side == TranslationPickerSide.SOURCE) "Source language" else "Target language",
                    style = Typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(entries, key = { it.first }) { (langCode, label) ->
                    val isSelected = langCode == selectedCode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(langCode) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = label,
                            style = Typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        if (langCode == "auto") {
                            IconWithColor(
                                iconId = R.drawable.auto_detect,
                                iconColor = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun TranslationInputBox(
    text: MutableState<String>,
    placeHolder: String = "",
    fontSize: Float = 15f,
    callbackTextChange: (String) -> Unit
) {
    val context = LocalContext.current
    val manager = LocalManager.current

    val height = with(LocalDensity.current) {
        48.dp.toPx()
    }

    val inputType = EditorInfo.TYPE_CLASS_TEXT

    val color = LocalContentColor.current
    val hintColor = MaterialTheme.colorScheme.onBackground

    AndroidView(
        factory = {
            ActionEditText(context).apply {
                this.inputType = inputType

                setTextChangeCallback {
                    text.value = it
                    callbackTextChange(it)
                }
                hint = placeHolder
                setHintTextColor(hintColor.toArgb())

                setText(text.value)
                textSize = fontSize

                setTextColor(color.toArgb())

                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )

                privateImeOptions = "org.futo.inputmethod.latin.NoSuggestions=1"

                setHeight(height.toInt())

                val editorInfo = EditorInfo().apply {
                    this.inputType = inputType
                }
                onCreateInputConnection(editorInfo)

                inputConnection?.let { manager.overrideInputConnection(it, editorInfo) }

                requestFocus()
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        onRelease = {
            manager.unsetInputConnection()
        }
    )
}


@Composable
fun SourceTextInput(modifier: Modifier, searchText: MutableState<String>, onTextChange: (String) -> Unit) {
    Row(modifier = modifier) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceBright,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .padding(2.dp)
                .weight(1.0f)
        ) {
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.CenterVertically),
                contentAlignment = Alignment.CenterStart,
            ) {
                TranslationInputBox(
                    text = searchText,
                    placeHolder = "Type text here to translate.",
                    fontSize = 18f,
                    callbackTextChange = onTextChange
                )
            }
        }
    }
}

@Composable
fun TranslateScreen(
    isLoading: Boolean,
    translatedText: String,
    errorText: String?,
    searchText: MutableState<String>,
    onTextChange: (String) -> Unit,
    onLanguagesChanged: () -> Unit,
    onCopyResult: (String) -> Unit,
    languageFrom: MutableState<String>,
    languageTo: MutableState<String>
) {
    val activePicker = remember { mutableStateOf<TranslationPickerSide?>(null) }
    val languages = remember { TranslationHelper.getLanguages() }
    val updateSharedPref = useUpdateSharedPrefString()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 2.dp)
    ) {
        LanguageSelector(
            modifier = Modifier.fillMaxWidth(),
            activePicker = activePicker.value,
            onTogglePicker = { side ->
                activePicker.value = if (activePicker.value == side) null else side
            },
            mSelectedLanguage1 = languageFrom,
            mSelectedLanguage2 = languageTo,
            onLanguagesChanged = {
                activePicker.value = null
                onLanguagesChanged()
            }
        )

        Box(
            modifier = Modifier
                .weight(1.0f)
                .fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                SourceTextInput(
                    modifier = Modifier
                        .weight(1.0f)
                        .fillMaxWidth(),
                    searchText = searchText,
                    onTextChange = onTextChange,
                )

                if (isLoading || errorText != null || translatedText.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            when {
                                isLoading -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Translating...",
                                            style = Typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                errorText != null -> {
                                    Text(
                                        text = errorText,
                                        style = Typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                else -> {
                                    Text(
                                        text = translatedText,
                                        style = Typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { onCopyResult(translatedText) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.copy),
                                            contentDescription = "Copy result",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Render language list inside IME bounds so PopupWindow never steals keyboard touches.
            activePicker.value?.let { side ->
                val isSource = side == TranslationPickerSide.SOURCE
                InlineLanguageListOverlay(
                    side = side,
                    selectedCode = if (isSource) languageFrom.value else languageTo.value,
                    languageList = languages,
                    onSelect = { code ->
                        if (isSource) {
                            languageFrom.value = code
                            updateSharedPref(Settings.PREF_KEY_LAST_SOURCE_LANG, code)
                        } else {
                            languageTo.value = code
                            updateSharedPref(Settings.PREF_KEY_LAST_TARGET_LANG, code)
                        }
                        activePicker.value = null
                        onLanguagesChanged()
                    },
                    onDismiss = { activePicker.value = null }
                )
            }
        }
    }
}

val TranslateAction = Action(
    icon = R.drawable.nth_translate,
    name = R.string.translate_action_title,
    simplePressImpl = null,
    persistentState = null,
    canShowKeyboard = true,
    windowImpl = { manager, _ ->
        object : ActionWindow {
            private val searchText = mutableStateOf("")
            private val translatedText = mutableStateOf("")
            private val isLoading = mutableStateOf(false)
            private val error = mutableStateOf<String?>(null)
            private var debounceJob: Job? = null
            @Volatile
            private var closed = false

            @Composable
            override fun windowName(): String {
                return stringResource(R.string.translate_action_title)
            }

            @Composable
            override fun WindowContents(keyboardShown: Boolean) {
                val context = LocalContext.current
                val view = LocalView.current
                val (sourcePref, _) = useSharedPrefsString(Settings.PREF_KEY_LAST_SOURCE_LANG, "auto")
                val (targetPref, _) = useSharedPrefsString(Settings.PREF_KEY_LAST_TARGET_LANG, "bn")

                val languageFrom = remember(sourcePref) { mutableStateOf(sourcePref) }
                val languageTo = remember(targetPref) { mutableStateOf(targetPref) }
                val coroutineScope = rememberCoroutineScope()

                val launchTranslation: (String, Long) -> Unit = { updatedString, debounceMs ->
                    debounceJob?.cancel()
                    if (updatedString.isEmpty()) {
                        isLoading.value = false
                        error.value = null
                        translatedText.value = ""
                    } else {
                        debounceJob = coroutineScope.launch {
                            isLoading.value = true
                            error.value = null
                            if (debounceMs > 0L) delay(debounceMs)
                            if (closed) return@launch
                            if (languageFrom.value == languageTo.value) {
                                translatedText.value = updatedString
                                manager.replaceAllText(updatedString)
                                isLoading.value = false
                            } else {
                                val result = CloudTranslationHandler.translate(
                                    languageFrom.value,
                                    languageTo.value,
                                    updatedString
                                )
                                if (closed) return@launch
                                result.fold(
                                    onSuccess = {
                                        Log.d(TAG, "Translation Success")
                                        translatedText.value = it
                                        manager.replaceAllText(it)
                                        isLoading.value = false
                                    },
                                    onFailure = {
                                        Log.d(TAG, "Translation fail")
                                        error.value = it.message ?: "Translation failed."
                                        isLoading.value = false
                                    }
                                )
                            }
                        }
                        Log.d(TAG, "Initiated Translation")
                    }
                }

                TranslateScreen(
                    isLoading = isLoading.value,
                    translatedText = translatedText.value,
                    errorText = error.value,
                    searchText = searchText,
                    onTextChange = { updatedString ->
                        launchTranslation(updatedString, 500L)
                    },
                    onLanguagesChanged = {
                        if (searchText.value.isNotEmpty()) {
                            launchTranslation(searchText.value, 0L)
                        }
                    },
                    onCopyResult = { resultText ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("Translated text", resultText))
                        manager.performHapticAndAudioFeedback(Constants.CODE_OUTPUT_TEXT, view)
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    },
                    languageFrom = languageFrom,
                    languageTo = languageTo,
                )
            }

            override fun close() {
                closed = true
                debounceJob?.cancel()
                debounceJob = null
                manager.unsetInputConnection()
            }
        }
    }
)
