package org.futo.inputmethod.latin.uix.actions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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

@Composable
fun LanguageDropdown(
    languageList: Map<String, String>,
    mExpanded: MutableState<Boolean>,
    mSelectedText: MutableState<String>,
    sourceLang: Boolean,
    prefKey: String,
    onLanguageSelected: () -> Unit = {},
) {
    val updateSharedPref = useUpdateSharedPrefString()
    Row(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceBright,
            shape = RoundedCornerShape(24.dp),
            onClick = { mExpanded.value = true },
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .padding(2.dp)
                .weight(1.0f)
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    TranslationHelper.getLanguageName(mSelectedText.value),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = Typography.bodyMedium
                )
            }
        }
        DropdownMenu(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            expanded = mExpanded.value,
            onDismissRequest = { mExpanded.value = false },
        ) {
            languageList.toSortedMap().forEach { (langCode, label) ->
                val isTargetAuto = !(!sourceLang && langCode == "auto")
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        mExpanded.value = false
                        mSelectedText.value = langCode
                        updateSharedPref(prefKey, langCode)
                        onLanguageSelected()
                    },
                    enabled = isTargetAuto,
                    trailingIcon = {
                        if (langCode == "auto") {
                            IconWithColor(
                                iconId = R.drawable.auto_detect,
                                iconColor = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun LanguageSelector(
    modifier: Modifier,
    isLoading: Boolean,
    mSelectedLanguage1: MutableState<String>,
    mSelectedLanguage2: MutableState<String>,
    onLanguagesChanged: () -> Unit = {},
) {
    val mExpanded1 = remember { mutableStateOf(false) }
    val mExpanded2 = remember { mutableStateOf(false) }
    val mLanguages = TranslationHelper.getLanguages()
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
            LanguageDropdown(
                mLanguages,
                mExpanded1,
                mSelectedLanguage1,
                true,
                Settings.PREF_KEY_LAST_SOURCE_LANG,
                onLanguageSelected = onLanguagesChanged
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
            LanguageDropdown(
                mLanguages,
                mExpanded2,
                mSelectedLanguage2,
                false,
                Settings.PREF_KEY_LAST_TARGET_LANG,
                onLanguageSelected = onLanguagesChanged
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

                setHeight(height.toInt())

                val editorInfo = EditorInfo().apply {
                    this.inputType = inputType
                }
                onCreateInputConnection(editorInfo)

                manager.overrideInputConnection(inputConnection!!, editorInfo)

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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 2.dp)
    ) {
        LanguageSelector(
            modifier = Modifier.fillMaxWidth(),
            isLoading = isLoading,
            mSelectedLanguage1 = languageFrom,
            mSelectedLanguage2 = languageTo,
            onLanguagesChanged = onLanguagesChanged
        )
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
}

val TranslateAction = Action(
    icon = R.drawable.nth_translate,
    name = R.string.translate_action_title,
    simplePressImpl = null,
    persistentState = null,
    canShowKeyboard = true,
    windowImpl = { manager, persistentState ->
        object : ActionWindow {
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

                val languageFrom = remember { mutableStateOf(sourcePref) }
                val languageTo = remember { mutableStateOf(targetPref) }
                val searchText = remember { mutableStateOf("") }
                val translatedText = remember { mutableStateOf("") }
                val isLoading = remember { mutableStateOf(false) }
                val error = remember { mutableStateOf<String?>(null) }

                val debounceJob = remember { mutableStateOf<Job?>(null) }

                val coroutineScope = rememberCoroutineScope()

                val launchTranslation: (String, Long) -> Unit = { updatedString, debounceMs ->
                    debounceJob.value?.cancel()
                    if (updatedString.isEmpty()) {
                        isLoading.value = false
                        error.value = null
                        translatedText.value = ""
                    } else {
                        debounceJob.value = coroutineScope.launch {
                            isLoading.value = true
                            error.value = null
                            if (debounceMs > 0L) delay(debounceMs)
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
            }
        }
    }
)
