package org.futo.inputmethod.latin.uix.actions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import org.futo.inputmethod.latin.uix.VERBOSE_PROGRESS
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.getSettingBlocking
import org.futo.inputmethod.latin.uix.setSetting
import org.futo.inputmethod.latin.uix.settings.SettingsActivity
import org.futo.inputmethod.latin.uix.theme.Typography
import org.futo.inputmethod.latin.uix.voiceinput.downloader.DownloadActivity
import org.futo.inputmethod.latin.xlm.UserDictionaryObserver
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

internal fun buildVoiceSpeechLanguageOptions(defaultTag: String): List<Pair<String, String>> {
    val normalized = defaultTag.ifBlank { "en-US" }
    val bnTag = if (normalized.startsWith("bn", ignoreCase = true)) normalized else "bn-BD"
    val enTag = if (normalized.startsWith("en", ignoreCase = true)) normalized else "en-US"
    val base = mutableListOf(
        bnTag to "বাংলা",
        enTag to "English"
    )
    if (!normalized.startsWith("bn", ignoreCase = true) &&
        !normalized.startsWith("en", ignoreCase = true)
    ) {
        base.add(0, normalized to normalized)
    }
    return base
}

internal fun resolveVoiceSpeechCandidates(results: List<String>?, fallbackPartial: String): String {
    val primary = results?.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    return primary.ifBlank { fallbackPartial.trim() }
}

internal fun shouldAutoCommitOnSpeechError(
    errorCode: Int,
    isFinishingByUser: Boolean,
    transcript: String
): Boolean {
    if (transcript.isBlank()) return false
    return isFinishingByUser && (
        errorCode == SpeechRecognizer.ERROR_NO_MATCH ||
            errorCode == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
        )
}

internal fun shouldRetrySpeechRecognizerError(errorCode: Int, retryCount: Int): Boolean {
    return retryCount < 1 && (
        errorCode == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
            errorCode == SpeechRecognizer.ERROR_CLIENT
        )
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
    private val initialTag = resolveSpeechLocaleTag(locale)
    private val languageOptions = buildVoiceSpeechLanguageOptions(initialTag)
    private val selectedLanguageTag = mutableStateOf(initialTag)
    private val uiState = mutableStateOf(DeviceSpeechState.STARTING)
    private val transcriptText = mutableStateOf("")
    private val statusMessage = mutableStateOf("Starting voice input...")
    private val rmsLevel = mutableFloatStateOf(0f)

    private var speechRecognizer: SpeechRecognizer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var activeSessionId = 0
    private var retryCount = 0
    private var isFinishingByUser = false
    private var pendingRetryRunnable: Runnable? = null
    private var pendingFinishTimeoutRunnable: Runnable? = null
    private var finishedOrCancelled = false
    private var closed = false

    private fun selectedLanguageLabel(): String {
        val current = selectedLanguageTag.value
        return languageOptions.firstOrNull { it.first == current }?.second ?: current
    }

    private fun requestAudioFocusIfNeeded() {
        val shouldFocus = runCatching { context.getSettingBlocking(AUDIO_FOCUS) }.getOrDefault(true)
        if (!shouldFocus || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (focusRequest == null) {
                focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .build()
            }
            am.requestAudioFocus(focusRequest!!)
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val req = focusRequest ?: return
        focusRequest = null
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            am.abandonAudioFocusRequest(req)
        }
    }

    private fun cancelPendingCallbacks() {
        pendingRetryRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingRetryRunnable = null
        pendingFinishTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingFinishTimeoutRunnable = null
    }

    private fun destroyRecognizerOnMain() {
        val old = speechRecognizer
        speechRecognizer = null
        if (old != null) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                runCatching {
                    old.cancel()
                    old.destroy()
                }
            } else {
                mainHandler.post {
                    runCatching {
                        old.cancel()
                        old.destroy()
                    }
                }
            }
        }
    }

    private fun commitTextAndClose(text: String) {
        if (closed || finishedOrCancelled) return
        val clean = text.trim()
        if (clean.isEmpty()) return
        finishedOrCancelled = true
        cancelPendingCallbacks()
        destroyRecognizerOnMain()
        abandonAudioFocus()
        inputTransaction.commit(clean)
        manager.announce(clean)
        manager.closeActionWindow()
    }

    private fun createListenerForSession(sessionId: Int): RecognitionListener {
        return object : RecognitionListener {
            private fun isStale(): Boolean =
                closed || finishedOrCancelled || sessionId != activeSessionId

            override fun onReadyForSpeech(params: Bundle?) {
                if (isStale()) return
                retryCount = 0
                uiState.value = DeviceSpeechState.LISTENING
                statusMessage.value = "Listening (${selectedLanguageLabel()})... Speak now."
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                if (isStale()) return
                rmsLevel.floatValue = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                if (isStale()) return
                statusMessage.value = "Finishing transcription..."
            }

            override fun onError(error: Int) {
                if (isStale()) return
                pendingFinishTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                pendingFinishTimeoutRunnable = null
                rmsLevel.floatValue = 0f

                if (shouldAutoCommitOnSpeechError(error, isFinishingByUser, transcriptText.value)) {
                    commitTextAndClose(transcriptText.value)
                    return
                }

                if (shouldRetrySpeechRecognizerError(error, retryCount)) {
                    retryCount++
                    statusMessage.value = "Reconnecting microphone..."
                    destroyRecognizerOnMain()
                    val retryRunnable = Runnable {
                        if (!isStale()) {
                            startListeningOnMain()
                        }
                    }
                    pendingRetryRunnable = retryRunnable
                    mainHandler.postDelayed(retryRunnable, 200L)
                    return
                }

                isFinishingByUser = false
                destroyRecognizerOnMain()
                abandonAudioFocus()
                uiState.value = DeviceSpeechState.ERROR
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
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch clear speech. Tap Try Again."
                    else -> "Voice input error ($error). Tap Try Again."
                }
            }

            override fun onResults(results: Bundle?) {
                if (isStale()) return
                pendingFinishTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                pendingFinishTimeoutRunnable = null
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val finalText = resolveVoiceSpeechCandidates(matches, transcriptText.value)
                if (finalText.isNotEmpty()) {
                    commitTextAndClose(finalText)
                } else {
                    isFinishingByUser = false
                    destroyRecognizerOnMain()
                    abandonAudioFocus()
                    uiState.value = DeviceSpeechState.ERROR
                    statusMessage.value = "Didn't catch any speech. Tap Try Again."
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (isStale()) return
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
                if (partial.isNotEmpty()) {
                    uiState.value = DeviceSpeechState.PARTIAL
                    transcriptText.value = partial
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    init {
        ensurePermissionAndStart()
    }

    private fun ensurePermissionAndStart() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            uiState.value = DeviceSpeechState.UNAVAILABLE
            statusMessage.value = "No speech recognition service is enabled on this device. Install/enable Google Speech Services or configure Voice Settings."
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
                        if (closed || finishedOrCancelled) return@post
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
            mainHandler.post { if (!closed && !finishedOrCancelled) startListeningOnMain() }
        }
    }

    private fun startListeningOnMain() {
        if (closed || finishedOrCancelled) return
        cancelPendingCallbacks()
        activeSessionId++
        val sessionId = activeSessionId
        isFinishingByUser = false
        transcriptText.value = ""
        rmsLevel.floatValue = 0f
        uiState.value = DeviceSpeechState.STARTING
        statusMessage.value = "Starting microphone (${selectedLanguageLabel()})..."

        destroyRecognizerOnMain()
        requestAudioFocusIfNeeded()

        runCatching {
            val tag = selectedLanguageTag.value
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = recognizer
            recognizer.setRecognitionListener(createListenerForSession(sessionId))
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }
            recognizer.startListening(intent)
        }.onFailure {
            destroyRecognizerOnMain()
            abandonAudioFocus()
            uiState.value = DeviceSpeechState.UNAVAILABLE
            statusMessage.value = "Could not start device voice input. Open Voice Settings to configure."
        }
    }

    private fun finishListening() {
        if (closed || finishedOrCancelled) return
        isFinishingByUser = true
        statusMessage.value = "Finishing transcription..."
        mainHandler.post {
            if (closed || finishedOrCancelled) return@post
            val current = speechRecognizer
            if (current == null) {
                if (transcriptText.value.isNotBlank()) {
                    commitTextAndClose(transcriptText.value)
                }
                return@post
            }
            runCatching { current.stopListening() }
            val sessionAtFinish = activeSessionId
            val timeoutRunnable = Runnable {
                if (!closed && !finishedOrCancelled && sessionAtFinish == activeSessionId) {
                    if (transcriptText.value.isNotBlank()) {
                        commitTextAndClose(transcriptText.value)
                    } else {
                        isFinishingByUser = false
                        destroyRecognizerOnMain()
                        abandonAudioFocus()
                        uiState.value = DeviceSpeechState.ERROR
                        statusMessage.value = "Didn't catch any speech. Tap Try Again."
                    }
                }
            }
            pendingFinishTimeoutRunnable = timeoutRunnable
            mainHandler.postDelayed(timeoutRunnable, 700L)
        }
    }

    private fun switchSpeechLanguage(tag: String) {
        if (selectedLanguageTag.value == tag || closed || finishedOrCancelled) return
        selectedLanguageTag.value = tag
        retryCount = 0
        mainHandler.post {
            if (!closed && !finishedOrCancelled) {
                startListeningOnMain()
            }
        }
    }

    private fun cancelAndClose() {
        if (!finishedOrCancelled) {
            finishedOrCancelled = true
            inputTransaction.discard()
        }
        manager.closeActionWindow()
    }

    private fun openVoiceSettings() {
        val intent = Intent(context, SettingsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navDest", "voiceInput")
        }
        context.startActivity(intent)
        cancelAndClose()
    }

    @Composable
    override fun windowName(): String {
        return stringResource(R.string.voice_input_action_title)
    }

    @Composable
    override fun WindowContents(keyboardShown: Boolean) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    languageOptions.forEach { (tag, label) ->
                        val isSelected = selectedLanguageTag.value == tag
                        val pillShape = RoundedCornerShape(16.dp)
                        Surface(
                            modifier = Modifier
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    shape = pillShape
                                )
                                .clip(pillShape)
                                .clickable { switchSpeechLanguage(tag) },
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = pillShape
                        ) {
                            Text(
                                text = label,
                                style = Typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                when (uiState.value) {
                    DeviceSpeechState.STARTING,
                    DeviceSpeechState.LISTENING,
                    DeviceSpeechState.PARTIAL -> {
                        Text(
                            text = if (transcriptText.value.isNotBlank()) transcriptText.value else statusMessage.value,
                            style = Typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        LinearProgressIndicator(
                            progress = rmsLevel.floatValue.coerceAtLeast(0.15f),
                            modifier = Modifier.fillMaxWidth(0.75f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { finishListening() }) {
                                Text("Done")
                            }
                            OutlinedButton(onClick = { cancelAndClose() }) {
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
                            Button(onClick = {
                                retryCount = 0
                                ensurePermissionAndStart()
                            }) {
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
                        if (transcriptText.value.isNotBlank()) {
                            Text(
                                text = transcriptText.value,
                                style = Typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = statusMessage.value,
                            style = Typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (transcriptText.value.isNotBlank()) {
                                Button(onClick = { commitTextAndClose(transcriptText.value) }) {
                                    Text("Insert")
                                }
                            }
                            Button(onClick = {
                                retryCount = 0
                                ensurePermissionAndStart()
                            }) {
                                Text("Try Again")
                            }
                            OutlinedButton(onClick = { cancelAndClose() }) {
                                Text("Cancel")
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
                            OutlinedButton(onClick = { cancelAndClose() }) {
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
        activeSessionId++
        cancelPendingCallbacks()
        destroyRecognizerOnMain()
        abandonAudioFocus()
        if (!finishedOrCancelled) {
            finishedOrCancelled = true
            inputTransaction.discard()
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
            DeviceSpeechInputWindow(manager = manager, locale = locale)
        } else {
            VoiceInputActionWindow(
                manager = manager, state = persistentState as VoiceInputPersistentState,
                locale = locale, model = model
            )
        }
    }
)