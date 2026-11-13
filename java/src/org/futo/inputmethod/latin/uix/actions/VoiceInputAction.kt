package org.futo.inputmethod.latin.uix.actions

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.permissions.PermissionsManager
import org.futo.inputmethod.latin.uix.AUDIO_FOCUS
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.DISALLOW_SYMBOLS
import org.futo.inputmethod.latin.uix.ENABLE_ENGLISH
import org.futo.inputmethod.latin.uix.ENABLE_LIVE_TRANSCRIPTION
import org.futo.inputmethod.latin.uix.VOICE_PUNCTUATION
import org.futo.inputmethod.latin.uix.ENABLE_SOUND
import org.futo.inputmethod.latin.uix.KeyboardManagerForAction
import org.futo.inputmethod.latin.uix.PREFER_BLUETOOTH
import org.futo.inputmethod.latin.uix.PersistentActionState
import org.futo.inputmethod.latin.uix.ResourceHelper
import org.futo.inputmethod.latin.uix.USE_SYSTEM_VOICE_INPUT
import org.futo.inputmethod.latin.uix.VERBOSE_PROGRESS
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.setSetting
import org.futo.inputmethod.latin.uix.settings.SettingsActivity
import org.futo.inputmethod.latin.uix.theme.Typography
import org.futo.inputmethod.latin.uix.voiceinput.downloader.DownloadActivity
import org.futo.inputmethod.latin.xlm.UserDictionaryObserver
import org.futo.inputmethod.updates.openURI
import org.futo.voiceinput.shared.ModelDoesNotExistException
import org.futo.voiceinput.shared.RecognizerView
import org.futo.voiceinput.shared.RecognizerViewListener
import org.futo.voiceinput.shared.RecognizerViewSettings
import org.futo.voiceinput.shared.RecordingSettings
import org.futo.voiceinput.shared.SoundPlayer
import org.futo.voiceinput.shared.types.Language
import org.futo.voiceinput.shared.types.ModelLoader
import org.futo.voiceinput.shared.types.getLanguageFromWhisperString
import org.futo.voiceinput.shared.ui.MicrophoneDeviceState
import org.futo.voiceinput.shared.whisper.DecodingConfiguration
import org.futo.voiceinput.shared.whisper.ModelManager
import org.futo.voiceinput.shared.whisper.MultiModelRunConfiguration
import java.util.Locale

val SystemVoiceInputAction = Action(
    icon = R.drawable.mic_fill,
    name = R.string.system_voice_input_action_title,
    simplePressImpl = { it, _ ->
        it.triggerSystemVoiceInput()
    },
    persistentState = null,
    windowImpl = null,
    shownInEditor = false
)


class VoiceInputPersistentState(val manager: KeyboardManagerForAction) : PersistentActionState {
    val modelManager = ModelManager(manager.getContext())
    val soundPlayer = SoundPlayer(manager.getContext())
    val userDictionaryObserver = UserDictionaryObserver(manager.getContext())

    override suspend fun cleanUp() {
        modelManager.cleanUp()
    }
}

private class VoiceInputActionWindow(
    val manager: KeyboardManagerForAction, val state: VoiceInputPersistentState,
    val model: ModelLoader, val locale: Locale
) : ActionWindow, RecognizerViewListener {
    val context = manager.getContext()

    private var shouldPlaySounds: Boolean = false
    private suspend fun loadSettings(): RecognizerViewSettings = coroutineScope {
        val enableSound = async { context.getSetting(ENABLE_SOUND) }
        val verboseFeedback = async { context.getSetting(VERBOSE_PROGRESS) }
        val disallowSymbols = async { context.getSetting(DISALLOW_SYMBOLS) }
        val enableEnglish = async { context.getSetting(ENABLE_ENGLISH) }
        val enableLiveTranscription = async { context.getSetting(ENABLE_LIVE_TRANSCRIPTION) }
        val voicePunctuation = async { context.getSetting(VOICE_PUNCTUATION) }
        val useBluetoothAudio = async { context.getSetting(PREFER_BLUETOOTH) }
        val requestAudioFocus = async { context.getSetting(AUDIO_FOCUS) }

        val primaryModel = model
        val languageSpecificModels = mutableMapOf<Language, ModelLoader>()
        val englishEnabled = enableEnglish.await()
        val allowedLanguages = buildSet {
            val detected = getLanguageFromWhisperString(locale.language)
            add(detected ?: Language.English)
            if (englishEnabled && detected != null && !locale.language.equals("en", ignoreCase = true)) {
                add(Language.English)
            }
        }

        shouldPlaySounds = enableSound.await()

        return@coroutineScope RecognizerViewSettings(
            shouldShowInlinePartialResult = enableLiveTranscription.await(),
            shouldShowVerboseFeedback = verboseFeedback.await(),
            modelRunConfiguration = MultiModelRunConfiguration(
                primaryModel = primaryModel,
                languageSpecificModels = languageSpecificModels
            ),
            decodingConfiguration = DecodingConfiguration(
                glossary = state.userDictionaryObserver.getWords().map { it.word },
                languages = allowedLanguages,
                suppressSymbols = disallowSymbols.await() && !voicePunctuation.await()
            ),
            recordingConfiguration = RecordingSettings(
                preferBluetoothMic = useBluetoothAudio.await(),
                requestAudioFocus = requestAudioFocus.await()
            )
        )
    }

    private var recognizerView: MutableState<RecognizerView?> = mutableStateOf(null)
    private var modelException: MutableState<ModelDoesNotExistException?> = mutableStateOf(null)

    private val initJob = manager.getLifecycleScope().launch {
        yield()
        val settings = withContext(Dispatchers.IO) {
            loadSettings()
        }

        yield()
        val recognizerView = try {
            RecognizerView(
                context = manager.getContext(),
                listener = this@VoiceInputActionWindow,
                settings = settings,
                lifecycleScope = manager.getLifecycleScope(),
                modelManager = state.modelManager
            )
        } catch(e: ModelDoesNotExistException) {
            modelException.value = e
            return@launch
        }

        this@VoiceInputActionWindow.recognizerView.value = recognizerView

        yield()
        recognizerView.reset()

        yield()
        recognizerView.start()
    }

    private var inputTransaction = manager.createInputTransaction(true)

    @Composable
    private fun ModelDownloader(modelException: ModelDoesNotExistException) {
        val context = LocalContext.current
        Box(modifier = Modifier.fillMaxSize().clickable {
            val intent = Intent(context, DownloadActivity::class.java)
            intent.putStringArrayListExtra("models", ArrayList(modelException.models.map { model -> model.getRequiredDownloadList(context) }.flatten()))

            if(context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
        }) {
            Text("Tap to complete setup", modifier = Modifier.align(Alignment.Center))
        }
    }

    @Composable
    override fun windowName(): String {
        return stringResource(R.string.voice_input_action_title)
    }

    @Composable
    override fun WindowContents(keyboardShown: Boolean) {
        Box(modifier = Modifier
            .fillMaxSize()
            .clickable(enabled = true,
                onClickLabel = null,
                onClick = { recognizerView.value?.finish() },
                role = null,
                indication = null,
                interactionSource = remember { MutableInteractionSource() })
            .semantics(mergeDescendants = true) {
                traversalIndex = -1.0f
            }) {
            Box(modifier = Modifier.align(Alignment.Center)) {
                when {
                    modelException.value != null -> ModelDownloader(modelException.value!!)
                    recognizerView.value != null -> recognizerView.value!!.Content()
                }
            }
        }
    }

    override fun close() {
        initJob.cancel()
        recognizerView.value?.cancel()
    }

    private var wasFinished = false
    private var cancelPlayed = false
    override fun cancelled() {
        if (!wasFinished) {
            if (shouldPlaySounds && !cancelPlayed) {
                state.soundPlayer.playCancelSound()
                cancelPlayed = true
            }
            inputTransaction.cancel()
        }
    }

    override fun recordingStarted(device: MicrophoneDeviceState) {
        if (shouldPlaySounds) {
            state.soundPlayer.playStartSound()
        }

        // Only set the setting if bluetooth is available, else it would reset the setting
        // every time it's used without a bluetooth device connected.
        if(device.bluetoothAvailable) {
            manager.getLifecycleScope().launch {
                context.setSetting(PREFER_BLUETOOTH, device.bluetoothActive)
            }
        }
    }

    override fun finished(result: String) {
        wasFinished = true

        inputTransaction.commit(result)
        manager.announce(result)
        manager.closeActionWindow()
    }

    override fun partialResult(result: String) {
        inputTransaction.updatePartial(result)
    }

    override fun requestPermission(onGranted: () -> Unit, onRejected: () -> Unit): Boolean {
        PermissionsManager.get(context).requestPermissions(
            { granted ->
                manager.getLifecycleScope().launch(Dispatchers.Main) {
                    if (granted) onGranted() else onRejected()
                }
            },
            null,
            Manifest.permission.RECORD_AUDIO
        )
        return true
    }
}

internal fun resolveSpeechLocaleTag(locale: Locale): String {
    if (locale.language.equals("bn", ignoreCase = true) && locale.country.isBlank()) {
        return "bn-BD"
    }
    if (locale.country.isBlank()) {
        return locale.language.ifBlank { "en-US" }
    }
    return locale.toLanguageTag().ifBlank { "en-US" }
}

private enum class DeviceSpeechState {
    STARTING,
    LISTENING,
    PARTIAL,
    NEED_PERMISSION,
    UNAVAILABLE,
    ERROR
}

private class DeviceSpeechInputWindow(
    val manager: KeyboardManagerForAction,
    val locale: Locale
) : ActionWindow {
    private val context = manager.getContext()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val inputTransaction = manager.createInputTransaction(true)
    private val uiState = mutableStateOf(DeviceSpeechState.STARTING)
    private val transcriptText = mutableStateOf("")
    private val statusMessage = mutableStateOf("Starting voice input (${locale.displayLanguage.ifBlank { locale.language }})...")
    private val rmsLevel = mutableFloatStateOf(0f)

    private var speechRecognizer: SpeechRecognizer? = null
    private var finishedOrCancelled = false
    private var closed = false

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            if (closed) return
            uiState.value = DeviceSpeechState.LISTENING
            statusMessage.value = "Listening (${locale.displayLanguage.ifBlank { locale.language }})... Tap Done when finished."
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {
            if (closed) return
            rmsLevel.floatValue = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            if (closed || finishedOrCancelled) return
            statusMessage.value = "Finishing transcription..."
        }

        override fun onError(error: Int) {
            if (closed || finishedOrCancelled) return
            uiState.value = DeviceSpeechState.ERROR
            rmsLevel.floatValue = 0f
            statusMessage.value = when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    uiState.value = DeviceSpeechState.NEED_PERMISSION
                    "Microphone permission is required for voice typing."
                }
                SpeechRecognizer.ERROR_AUDIO -> "Microphone audio error. Check if another app is using the mic."
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error during speech recognition. Try again."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service is busy. Tap Try Again."
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch any speech. Tap Try Again and speak clearly."
                else -> "Voice input error ($error). Tap Try Again."
            }
        }

        override fun onResults(results: Bundle?) {
            if (closed || finishedOrCancelled) return
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            if (text.isNotEmpty()) {
                finishedOrCancelled = true
                inputTransaction.commit(text)
                manager.announce(text)
                manager.closeActionWindow()
            } else {
                uiState.value = DeviceSpeechState.ERROR
                statusMessage.value = "Didn't catch any speech. Tap Try Again."
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (closed || finishedOrCancelled) return
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = matches?.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            if (partial.isNotEmpty()) {
                uiState.value = DeviceSpeechState.PARTIAL
                transcriptText.value = partial
                inputTransaction.updatePartial(partial)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    init {
        ensurePermissionAndStart()
    }

    private fun ensurePermissionAndStart() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            uiState.value = DeviceSpeechState.UNAVAILABLE
            statusMessage.value = "No speech recognition service is enabled on this device. Install/enable Google Speech Services or import an offline model in Voice Settings."
            return
        }

        val hasMic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMic) {
            uiState.value = DeviceSpeechState.NEED_PERMISSION
            statusMessage.value = "Microphone permission is needed for voice input."
            PermissionsManager.get(context).requestPermissions(
                { granted ->
                    mainHandler.post {
                        if (closed) return@post
                        if (granted) {
                            startListeningOnMain()
                        } else {
                            uiState.value = DeviceSpeechState.NEED_PERMISSION
                            statusMessage.value = "Microphone permission denied. Grant permission to use voice input."
                        }
                    }
                },
                null,
                Manifest.permission.RECORD_AUDIO
            )
        } else {
            mainHandler.post { if (!closed) startListeningOnMain() }
        }
    }

    private fun startListeningOnMain() {
        if (closed) return
        uiState.value = DeviceSpeechState.STARTING
        transcriptText.value = ""
        statusMessage.value = "Starting microphone (${locale.displayLanguage.ifBlank { locale.language }})..."

        runCatching {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(recognitionListener)
                val tag = resolveSpeechLocaleTag(locale)
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }
                startListening(intent)
            }
        }.onFailure {
            uiState.value = DeviceSpeechState.UNAVAILABLE
            statusMessage.value = "Could not start device voice input. Open Voice Settings to configure."
        }
    }

    private fun openVoiceSettings() {
        val intent = Intent(context, SettingsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navDest", "voiceInput")
        }
        context.startActivity(intent)
        manager.closeActionWindow()
    }

    @Composable
    override fun windowName(): String {
        return stringResource(R.string.system_voice_input_action_title)
    }

    @Composable
    override fun WindowContents(keyboardShown: Boolean) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (uiState.value) {
                    DeviceSpeechState.STARTING,
                    DeviceSpeechState.LISTENING,
                    DeviceSpeechState.PARTIAL -> {
                        Text(
                            text = if (transcriptText.value.isNotBlank()) transcriptText.value else statusMessage.value,
                            style = Typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        LinearProgressIndicator(
                            progress = rmsLevel.floatValue.coerceAtLeast(0.15f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                mainHandler.post { speechRecognizer?.stopListening() }
                            }) {
                                Text("Done")
                            }
                            OutlinedButton(onClick = { manager.closeActionWindow() }) {
                                Text("Cancel")
                            }
                        }
                    }

                    DeviceSpeechState.NEED_PERMISSION -> {
                        Text(
                            text = statusMessage.value,
                            style = Typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { ensurePermissionAndStart() }) {
                                Text("Grant Mic")
                            }
                            OutlinedButton(onClick = {
                                val appSettings = Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    android.net.Uri.parse("package:${context.packageName}")
                                ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                                context.startActivity(appSettings)
                            }) {
                                Text("App Info")
                            }
                        }
                    }

                    DeviceSpeechState.ERROR -> {
                        Text(
                            text = statusMessage.value,
                            style = Typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { ensurePermissionAndStart() }) {
                                Text("Try Again")
                            }
                            OutlinedButton(onClick = { manager.closeActionWindow() }) {
                                Text("Close")
                            }
                        }
                    }

                    DeviceSpeechState.UNAVAILABLE -> {
                        Text(
                            text = statusMessage.value,
                            style = Typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { openVoiceSettings() }) {
                                Text("Voice Settings")
                            }
                            OutlinedButton(onClick = { manager.closeActionWindow() }) {
                                Text("Close")
                            }
                        }
                    }
                }
            }
        }
    }

    override fun close() {
        closed = true
        mainHandler.post {
            runCatching {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            }
            speechRecognizer = null
        }
        if (!finishedOrCancelled) {
            finishedOrCancelled = true
            inputTransaction.cancel()
        }
    }
}

internal val DeviceSpeechInputAction = Action(
    icon = R.drawable.mic_fill,
    name = R.string.system_voice_input_action_title,
    simplePressImpl = null,
    keepScreenAwake = true,
    shownInEditor = false,
    windowImpl = { manager, _ ->
        DeviceSpeechInputWindow(manager = manager, locale = manager.getActiveLocale())
    }
)

private class VoiceInputNoModelWindow(
    val manager: KeyboardManagerForAction,
    val locale: Locale
) : ActionWindow {
    @Composable
    override fun windowName(): String {
        return stringResource(R.string.voice_input_action_title)
    }

    @Composable
    override fun WindowContents(keyboardShown: Boolean) {
        val context = LocalContext.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "No offline Whisper model installed for ${locale.displayLanguage.ifBlank { locale.language }}.",
                    textAlign = TextAlign.Center,
                    style = Typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        manager.getLifecycleScope().launch {
                            context.setSetting(USE_SYSTEM_VOICE_INPUT, true)
                            manager.activateAction(DeviceSpeechInputAction)
                        }
                    }) {
                        Text("Use Device Voice")
                    }
                    OutlinedButton(onClick = {
                        val intent = Intent(context, SettingsActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("navDest", "voiceInput")
                        }
                        context.startActivity(intent)
                    }) {
                        Text("Voice Settings")
                    }
                }
            }
        }
    }

    override fun close() {

    }

}

val VoiceInputAction = Action(icon = R.drawable.mic_fill,
    name = R.string.voice_input_action_title,
    simplePressImpl = null,
    keepScreenAwake = true,
    persistentState = { VoiceInputPersistentState(it) },
    shownInEditor = false,
    windowImpl = { manager, persistentState ->
        val locale = manager.getActiveLocale()

        val model = ResourceHelper.tryFindingVoiceInputModelForLocale(manager.getContext(), locale)

        if(model == null) {
            VoiceInputNoModelWindow(manager = manager, locale = locale)
        } else {
            VoiceInputActionWindow(
                manager = manager, state = persistentState as VoiceInputPersistentState,
                locale = locale, model = model
            )
        }
    }
)