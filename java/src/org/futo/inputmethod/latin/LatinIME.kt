package org.futo.inputmethod.latin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.app.NotificationManager
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import androidx.core.view.ViewCompat
import kotlin.math.max
import kotlin.math.min
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodSubtype
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import android.widget.Toast
import androidx.core.content.ContextCompat
import org.futo.inputmethod.keyboard.KeyboardSwitcher
import org.futo.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.define.ProductionFlags
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.uix.BasicThemeProvider
import org.futo.inputmethod.latin.uix.DynamicThemeProvider
import org.futo.inputmethod.latin.uix.DynamicThemeProviderOwner
import org.futo.inputmethod.latin.uix.EmojiTracker.unuseEmoji
import org.futo.inputmethod.latin.uix.EmojiTracker.useEmoji
import org.futo.inputmethod.latin.uix.KeyboardBottomOffsetSetting
import org.futo.inputmethod.latin.uix.LEGACY_ORBIT_THEME_KEY
import org.futo.inputmethod.latin.uix.OneHandedModeSetting
import org.futo.inputmethod.latin.uix.SUGGESTION_BLACKLIST
import org.futo.inputmethod.latin.uix.THEME_KEY
import org.futo.inputmethod.latin.uix.UixManager
import org.futo.inputmethod.latin.uix.USE_SYSTEM_VOICE_INPUT
import org.futo.inputmethod.latin.uix.actions.DeviceSpeechInputAction
import org.futo.inputmethod.latin.uix.actions.VoiceInputAction
import org.futo.inputmethod.latin.uix.createInlineSuggestionsRequest
import org.futo.inputmethod.latin.uix.dataStore
import org.futo.inputmethod.latin.uix.deferGetSetting
import org.futo.inputmethod.latin.uix.deferSetSetting
import org.futo.inputmethod.latin.uix.differsFrom
import org.futo.inputmethod.latin.uix.get
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.getSettingBlocking
import org.futo.inputmethod.latin.uix.getSettingFlow
import org.futo.inputmethod.latin.uix.isDirectBootUnlocked
import org.futo.inputmethod.latin.uix.migrateLegacyOrbitThemeIfNeeded
import org.futo.inputmethod.latin.uix.normalizeThemeKey
import org.futo.inputmethod.latin.uix.setSetting
import org.futo.inputmethod.latin.uix.theme.DarkColorScheme
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.ThemeOptions
import org.futo.inputmethod.latin.uix.theme.applyWindowColors
import org.futo.inputmethod.latin.uix.theme.presets.IPlusMatteTheme
import org.futo.inputmethod.latin.uix.theme.presets.VoiceInputTheme
import org.futo.inputmethod.latin.xlm.LanguageModelFacilitator
import org.futo.inputmethod.updates.scheduleUpdateCheckingJob

private class UnlockedBroadcastReceiver(val onDeviceUnlocked: () -> Unit) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        println("Unlocked Broadcast Receiver: ${intent?.action}")
        if (intent?.action == Intent.ACTION_USER_UNLOCKED) {
            onDeviceUnlocked()
        }
    }
}

object ImeInsetsMath {
    fun resolveEffectiveTouchableHeight(
        measuredTouchableHeight: Int,
        legacyKeyboardHeight: Int,
        actionBarHeightPx: Int,
        fallbackKeyboardHeight: Int
    ): Int {
        if (measuredTouchableHeight > 0) {
            return measuredTouchableHeight
        }
        val baseHeight = if (legacyKeyboardHeight > 0) legacyKeyboardHeight else fallbackKeyboardHeight
        return max(0, baseHeight) + max(0, actionBarHeightPx)
    }

    fun computeImeInsetsTopY(
        composeTopInWindow: Int,
        composeHeight: Int,
        decorHeight: Int,
        effectiveTouchableHeight: Int
    ): Int {
        val safeDecorHeight = max(0, decorHeight)
        val safeTop = if (safeDecorHeight > 0) {
            composeTopInWindow.coerceIn(0, safeDecorHeight)
        } else {
            max(0, composeTopInWindow)
        }
        val safeHeight = max(0, composeHeight)
        val composeBottom = if (safeDecorHeight > 0) {
            min(safeDecorHeight, safeTop + safeHeight)
        } else {
            safeTop + safeHeight
        }
        val clampedTouchable = max(0, effectiveTouchableHeight)
        return max(0, composeBottom - clampedTouchable).coerceAtMost(composeBottom)
    }
}

class LatinIME : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner,
    LatinIMELegacy.SuggestionStripController, DynamicThemeProviderOwner {

    private lateinit var mLifecycleRegistry: LifecycleRegistry
    private lateinit var mViewModelStore: ViewModelStore
    private lateinit var mSavedStateRegistryController: SavedStateRegistryController



    fun setOwners() {
        val decorView = window.window?.decorView
        if (decorView?.findViewTreeLifecycleOwner() == null) {
            decorView?.setViewTreeLifecycleOwner(this)
        }
        if (decorView?.findViewTreeViewModelStoreOwner() == null) {
            decorView?.setViewTreeViewModelStoreOwner(this)
        }
        if (decorView?.findViewTreeSavedStateRegistryOwner() == null) {
            decorView?.setViewTreeSavedStateRegistryOwner(this)
        }
    }

    val latinIMELegacy = LatinIMELegacy(
        this as InputMethodService,
        this as LatinIMELegacy.SuggestionStripController
    )

    val inputLogic get() = latinIMELegacy.mInputLogic

    lateinit var languageModelFacilitator: LanguageModelFacilitator

    val uixManager = UixManager(this)
    lateinit var suggestionBlacklist: SuggestionBlacklist

    private var activeThemeOption: ThemeOption? = null
    private var activeColorScheme = DarkColorScheme
    private var pendingRecreateKeyboard: Boolean = false
    private val isSplitKeyboardState = mutableStateOf(false)

    val themeOption get() = activeThemeOption
    val colorScheme get() = activeColorScheme
    val keyboardColor get() = drawableProvider?.primaryKeyboardColor?.let { androidx.compose.ui.graphics.Color(it) } ?: colorScheme.surface

    private var drawableProvider: DynamicThemeProvider? = null

    private var lastEditorInfo: EditorInfo? = null

    private var settingsRefreshRequired = false
    private fun recreateKeyboard() {
        latinIMELegacy.updateTheme()

        if(settingsRefreshRequired) {
            latinIMELegacy.mKeyboardSwitcher.loadKeyboard(
                currentInputEditorInfo ?: return,
                latinIMELegacy.mSettings.current,
                latinIMELegacy.currentAutoCapsState,
                latinIMELegacy.currentRecapitalizeState
            )
        } else {
            latinIMELegacy.mKeyboardSwitcher.mState.onLoadKeyboard(
                latinIMELegacy.currentAutoCapsState,
                latinIMELegacy.currentRecapitalizeState
            )
        }

        settingsRefreshRequired = false
    }

    private var isNavigationBarVisible = false
    fun updateNavigationBarVisibility(visible: Boolean? = null) {
        if(visible != null) isNavigationBarVisible = visible

        val color = drawableProvider?.primaryKeyboardColor

        window.window?.let { window ->
            if(color == null || !isNavigationBarVisible) {
                applyWindowColors(window, Color.TRANSPARENT, statusBar = false)
            } else {
                applyWindowColors(window, color, statusBar = false)
            }
        }
    }

    private fun updateDrawableProvider(colorScheme: ColorScheme) {
        activeColorScheme = colorScheme
        drawableProvider = BasicThemeProvider(this, overrideColorScheme = colorScheme, themeKey = activeThemeOption?.key)

        updateNavigationBarVisibility()
        uixManager.onColorSchemeChanged()
    }

    override fun getDrawableProvider(): DynamicThemeProvider {
        return drawableProvider ?: BasicThemeProvider(this, activeColorScheme, themeKey = activeThemeOption?.key).let {
            drawableProvider = it
            it
        }
    }

    private fun updateColorsIfDynamicChanged() {
        if(activeThemeOption?.dynamic == true) {
            val currColors = activeColorScheme
            val nextColors = activeThemeOption!!.obtainColors(this)

            if(currColors.differsFrom(nextColors)) {
                updateDrawableProvider(nextColors)
                recreateKeyboard()
            }
        }

        deferGetSetting(THEME_KEY) { rawKey ->
            val key = normalizeThemeKey(rawKey)
            if (rawKey == LEGACY_ORBIT_THEME_KEY) {
                deferSetSetting(THEME_KEY, IPlusMatteTheme.key)
            }
            if(key != activeThemeOption?.key) {
                ThemeOptions[key]?.let { if(it.available(this)) updateTheme(it) }
            }
        }
    }

    private fun invalidateKeyboard(refreshSettings: Boolean = false) {
        settingsRefreshRequired = settingsRefreshRequired || refreshSettings

        if(!uixManager.isMainKeyboardHidden) {
            println("Recreating keyboard")
            recreateKeyboard()
        } else {
            println("Pend recreate keyboard")
            pendingRecreateKeyboard = true
        }
    }

    fun updateTheme(newTheme: ThemeOption) {
        assert(newTheme.available(this))

        if (activeThemeOption != newTheme) {
            activeThemeOption = newTheme
            updateDrawableProvider(newTheme.obtainColors(this))
            deferSetSetting(THEME_KEY, newTheme.key)
            invalidateKeyboard()
        }
    }

    // Called by UixManager when the intention is to subsequently call LegacyKeyboardView with hidden=false
    // Maybe this can be changed to LaunchedEffect
    fun onKeyboardShown() {
        if(pendingRecreateKeyboard) {
            pendingRecreateKeyboard = false
            recreateKeyboard()
        }
    }

    private var currentSubtype = ""

    val jobs = mutableListOf<Job>()
    private fun launchJob(task: suspend CoroutineScope.() -> Unit) {
        jobs.add(lifecycleScope.launch(block = task))
    }

    private fun stopJobs() {
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

    private var unlockReceiver = UnlockedBroadcastReceiver { onDeviceUnlocked() }

    private val screenshotReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val bytes = intent?.getByteArrayExtra(org.futo.inputmethod.latin.capture.CaptureService.EXTRA_SCREENSHOT_BYTES)
            val pkg = intent?.getStringExtra(org.futo.inputmethod.latin.capture.CaptureService.EXTRA_TARGET_PACKAGE)
            if (bytes != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    processScreenshot(bytes, pkg)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        val unlockFilter = IntentFilter(Intent.ACTION_USER_UNLOCKED)
        ContextCompat.registerReceiver(this, unlockReceiver, unlockFilter, ContextCompat.RECEIVER_EXPORTED)

        mLifecycleRegistry = LifecycleRegistry(this)
        mLifecycleRegistry.currentState = Lifecycle.State.INITIALIZED

        mViewModelStore = ViewModelStore()

        mSavedStateRegistryController = SavedStateRegistryController.create(this)
        mSavedStateRegistryController.performRestore(null)

        mLifecycleRegistry.currentState = Lifecycle.State.CREATED

        suggestionBlacklist = SuggestionBlacklist(latinIMELegacy.mSettings, this, lifecycleScope)

        Subtypes.addDefaultSubtypesIfNecessary(this)

        languageModelFacilitator = LanguageModelFacilitator(
            this,
            latinIMELegacy.mInputLogic,
            latinIMELegacy.mDictionaryFacilitator,
            latinIMELegacy.mSettings,
            latinIMELegacy.mKeyboardSwitcher,
            lifecycleScope,
            suggestionBlacklist
        )

        getSettingBlocking(THEME_KEY).let { rawKey ->
            val normalizedKey = normalizeThemeKey(rawKey)
            val themeOptionFromSettings = ThemeOptions[normalizedKey]
            val themeOption = when {
                themeOptionFromSettings == null -> VoiceInputTheme
                !themeOptionFromSettings.available(this@LatinIME) -> VoiceInputTheme
                else -> themeOptionFromSettings
            }

            activeThemeOption = themeOption
            activeColorScheme = themeOption.obtainColors(this@LatinIME)
        }

        launchJob {
            migrateLegacyOrbitThemeIfNeeded()
        }

        latinIMELegacy.onCreate()
        isSplitKeyboardState.value = Settings.getInstance().current?.mIsSplitKeyboardEnabled == true

        languageModelFacilitator.launchProcessor()

        if(isDirectBootUnlocked) {
            languageModelFacilitator.loadHistoryLog()
        }

        scheduleUpdateCheckingJob(this)
        launchJob { uixManager.showUpdateNoticeIfNeeded() }

        suggestionBlacklist.init()

        launchJob {
            dataStore.data.collect {
                drawableProvider?.let { provider ->
                    if(provider is BasicThemeProvider) {
                        if (provider.hasUpdated(it)) {
                            val needsLayoutRefresh = it.get(OneHandedModeSetting) != provider.oneHandedMode
                            activeThemeOption?.obtainColors?.let { f ->
                                updateDrawableProvider(f(this@LatinIME))
                                invalidateKeyboard(refreshSettings = needsLayoutRefresh)
                            }
                        }
                    }
                }
            }
        }

        launchJob {
            val onNewSubtype: suspend (String) -> Unit = {
                val activeSubtype = it.ifEmpty {
                    getSettingBlocking(SubtypesSetting).firstOrNull()
                }

                if(activeSubtype != null && activeSubtype != currentSubtype) {
                    currentSubtype = activeSubtype

                    withContext(Dispatchers.Main) {
                        changeInputMethodSubtype(Subtypes.convertToSubtype(activeSubtype))
                    }
                }
            }

            onNewSubtype(getSetting(ActiveSubtype))

            dataStore.data.collect {
                onNewSubtype(it[ActiveSubtype.key] ?: ActiveSubtype.default)
            }
        }

        launchJob {
            dataStore.data.collect {
                CrashLoggingApplication.logPreferences(it)
            }
        }

        uixManager.onCreate()

        // Register receiver for screenshots captured by CaptureService
        val screenshotFilter = IntentFilter("org.futo.inputmethod.SCREENSHOT_READY")
        ContextCompat.registerReceiver(this, screenshotReceiver, screenshotFilter, ContextCompat.RECEIVER_NOT_EXPORTED)

        Settings.getInstance().settingsChangedListeners.add { oldSettings, newSettings ->
            isSplitKeyboardState.value = newSettings.mIsSplitKeyboardEnabled
            val differs = (oldSettings?.mActionKeyId != newSettings.mActionKeyId)
                    || (oldSettings?.mShowsActionKey != newSettings.mShowsActionKey)
                    || (oldSettings?.mIsSplitKeyboardEnabled != newSettings.mIsSplitKeyboardEnabled)

            if (differs) {
                invalidateKeyboard(refreshSettings = true)
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(unlockReceiver)
        try { unregisterReceiver(screenshotReceiver) } catch (e: Exception) { /* ignore */ }

        stopJobs()
        mLifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()

        languageModelFacilitator.saveHistoryLog()

        runBlocking {
            languageModelFacilitator.destroyModel()
        }

        Settings.getInstance().settingsChangedListeners.clear()

        latinIMELegacy.onDestroy()
        super.onDestroy()
    }

    /** Called by keyboard when AI-assist key is pressed. Starts permission flow. */
    fun startAiAssist() {
        val targetPkg = lastEditorInfo?.packageName ?: getCurrentInputEditorInfo()?.packageName
        val perm = Intent(this, org.futo.inputmethod.latin.capture.CapturePermissionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(org.futo.inputmethod.latin.capture.CapturePermissionActivity.EXTRA_TARGET_PACKAGE, targetPkg)
        }
        // Log and start the permission activity using ActivityOptions to request launch on the
        // same display. Background-starts from an IME can be blocked on newer Android versions
        // unless started with appropriate options.
        try {
            Log.i("LatinIME", "Starting CapturePermissionActivity for package=$targetPkg")
            val currentDisplayId = window?.window?.windowManager?.defaultDisplay?.displayId ?: 0
            val opts = android.app.ActivityOptions.makeBasic().setLaunchDisplayId(currentDisplayId).toBundle()
            startActivity(perm, opts)
        } catch (e: Exception) {
            // Fallback: try simple startActivity and log the exception
            Log.w("LatinIME", "startActivity with ActivityOptions failed: ${e.message}. Falling back to startActivity(perm)")
            try {
                startActivity(perm)
            } catch (ex: Exception) {
                Log.e("LatinIME", "Failed to launch CapturePermissionActivity: ${ex.message}")
                // Show a notification the user can tap to start the permission flow
                showCaptureRequestNotification(targetPkg)
            }
        }
    }

    private fun showCaptureRequestNotification(targetPkg: String?) {
        try {
            val channelId = "capture_request"
            val nm = getSystemService(android.app.NotificationManager::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val ch = android.app.NotificationChannel(channelId, "Capture Requests", NotificationManager.IMPORTANCE_HIGH)
                nm.createNotificationChannel(ch)
            }

            val intent = Intent(this, org.futo.inputmethod.latin.capture.CapturePermissionActivity::class.java).apply {
                putExtra(org.futo.inputmethod.latin.capture.CapturePermissionActivity.EXTRA_TARGET_PACKAGE, targetPkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pi = android.app.PendingIntent.getActivity(this, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE)

            val n = android.app.Notification.Builder(this, channelId)
                .setContentTitle("Allow screen capture")
                .setContentText("Tap to allow screen capture for AI assistant")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()

            nm.notify(23456, n)
            Log.i("LatinIME", "Posted capture request notification for package=$targetPkg")
        } catch (e: Exception) {
            Log.e("LatinIME", "Failed to post capture request notification: ${e.message}")
        }
    }

    private fun processScreenshot(bytes: ByteArray, packageName: String?) {
        // Removed hardcoded unauthenticated external endpoint (web.ndpnt.co); fail fast locally
        // instead of transmitting user screen captures without a configured backend.
        Log.i("LatinIME", "processScreenshot skipped (${bytes.size} bytes, pkg=$packageName): no assistant backend configured")
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                uixManager.showAssistantMessage("AI Assistant is not configured")
            } catch (e: Exception) {
                Log.w("LatinIME", "Failed to show assistant message: ${e.message}")
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        Log.w("LatinIME", "Configuration changed")
        latinIMELegacy.onConfigurationChanged(newConfig)
        super.onConfigurationChanged(newConfig)
    }

    override fun onInitializeInterface() {
        latinIMELegacy.onInitializeInterface()
    }

    private var legacyInputView: View? = null
    private var touchableHeight: Int = 0
    override fun onCreateInputView(): View {
        Log.w("LatinIME", "Create input view")
        legacyInputView = latinIMELegacy.onCreateInputView()

        val composeView = uixManager.createComposeView()
        latinIMELegacy.setComposeInputView(composeView)

        return composeView
    }

    private var inputViewHeight: Int = -1

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private var insetsRefreshScheduled = false

    private fun requestRecomputeInsets() {
        if (uixManager.getComposeView() == null && legacyInputView == null) {
            return
        }
        if (insetsRefreshScheduled) {
            return
        }
        insetsRefreshScheduled = true
        mainHandler.post {
            insetsRefreshScheduled = false
            val currentView = uixManager.getComposeView() ?: legacyInputView ?: return@post
            if (currentView.isAttachedToWindow) {
                currentView.requestLayout()
                ViewCompat.requestApplyInsets(currentView)
            }
        }
    }

    // Both called by UixManager
    fun updateTouchableHeight(to: Int) {
        if (to > 0 && touchableHeight != to) {
            touchableHeight = to
            requestRecomputeInsets()
        }
    }
    fun getInputViewHeight(): Int = inputViewHeight

    private var isInputModal = false
    fun setInputModal(to: Boolean) {
        if (isInputModal != to) {
            isInputModal = to
            requestRecomputeInsets()
        }
    }

    // The keyboard view really doesn't like being detached, so it's always
    // shown, but resized to 0 if an action window is open
    @Composable
    internal fun LegacyKeyboardView(hidden: Boolean) {
        val modifier = if(hidden) {
            Modifier
                .clipToBounds()
                .size(0.dp)
        } else {
            Modifier
                .fillMaxWidth()
                .onSizeChanged {
                    inputViewHeight = it.height
                }
        }

        val padding = getSettingFlow(KeyboardBottomOffsetSetting).collectAsState(initial = 0.0f)
        val oneHandedMode = getSettingFlow(OneHandedModeSetting).collectAsState(
            initial = remember { getSettingBlocking(OneHandedModeSetting) }
        )
        val isSplitActive = ProductionFlags.IS_SPLIT_KEYBOARD_SUPPORTED && isSplitKeyboardState.value
        val targetGravity = Gravity.BOTTOM or when {
            isSplitActive -> Gravity.CENTER_HORIZONTAL
            oneHandedMode.value == "left" -> Gravity.LEFT
            oneHandedMode.value == "right" -> Gravity.RIGHT
            else -> Gravity.CENTER_HORIZONTAL
        }

        key(legacyInputView) {
            AndroidView(factory = {
                legacyInputView!!
            }, modifier = modifier.padding(0.dp, 0.dp, 0.dp, padding.value.dp), update = { view ->
                val frame = view.findViewById<View>(R.id.main_keyboard_frame)
                val lp = frame?.layoutParams as? FrameLayout.LayoutParams
                if (lp != null && lp.gravity != targetGravity) {
                    lp.gravity = targetGravity
                    frame.layoutParams = lp
                }
            }, onRelease = {
                val view = it as InputView
                view.deallocateMemory()
                view.removeAllViews()
            })
        }
    }

    // necessary for when KeyboardSwitcher updates the theme
    fun updateLegacyView(newView: View) {
        Log.w("LatinIME", "Updating legacy view")
        legacyInputView = newView

        uixManager.setContent()
        uixManager.getComposeView()?.let {
            latinIMELegacy.setComposeInputView(it)
        }

        latinIMELegacy.setInputView(newView)
    }

    override fun setInputView(view: View?) {
        super.setInputView(view)

        uixManager.getComposeView()?.let {
            latinIMELegacy.setComposeInputView(it)
        }

        latinIMELegacy.setInputView(legacyInputView)
    }

    override fun setCandidatesView(view: View?) {
        return latinIMELegacy.setCandidatesView(view)
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        latinIMELegacy.onStartInput(attribute, restarting)
        languageModelFacilitator.onStartInput()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        mLifecycleRegistry.currentState = Lifecycle.State.STARTED

        lastEditorInfo = info

        super.onStartInputView(info, restarting)
        latinIMELegacy.onStartInputView(info, restarting)
        requestRecomputeInsets()
        lifecycleScope.launch { uixManager.showUpdateNoticeIfNeeded() }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        isInputModal = false
        latinIMELegacy.onFinishInputView(finishingInput)
        uixManager.onInputFinishing()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        latinIMELegacy.onFinishInput()

        uixManager.onInputFinishing()
        languageModelFacilitator.saveHistoryLog()
    }

    private fun changeInputMethodSubtype(newSubtype: InputMethodSubtype?) {
        latinIMELegacy.onCurrentInputMethodSubtypeChanged(newSubtype)
    }

    override fun onWindowShown() {
        super.onWindowShown()
        latinIMELegacy.onWindowShown()

        updateColorsIfDynamicChanged()
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        isInputModal = false
        latinIMELegacy.onWindowHidden()

        uixManager.onInputFinishing()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart,
            oldSelEnd,
            newSelStart,
            newSelEnd,
            candidatesStart,
            candidatesEnd
        )

        if (!isInputConnectionOverridden) {
            latinIMELegacy.onUpdateSelection(
                oldSelStart,
                oldSelEnd,
                newSelStart,
                newSelEnd,
                candidatesStart,
                candidatesEnd
            )
        }
    }

    override fun onExtractedTextClicked() {
        latinIMELegacy.onExtractedTextClicked()
        super.onExtractedTextClicked()
    }

    override fun onExtractedCursorMovement(dx: Int, dy: Int) {
        latinIMELegacy.onExtractedCursorMovement(dx, dy)
        super.onExtractedCursorMovement(dx, dy)
    }

    override fun hideWindow() {
        latinIMELegacy.hideWindow()
        super.hideWindow()
    }

    override fun onDisplayCompletions(completions: Array<out CompletionInfo>?) {
        latinIMELegacy.onDisplayCompletions(completions)
    }

    override fun onComputeInsets(outInsets: Insets?) {
        val composeView = uixManager.getComposeView()

        // This method may be called before {@link #setInputView(View)}.
        if (outInsets == null || legacyInputView == null || composeView == null) {
            return
        }

        val inputHeight: Int = composeView.height
        val decorHeight: Int = window?.window?.decorView?.height?.takeIf { it > 0 } ?: inputHeight
        val location = IntArray(2)
        composeView.getLocationInWindow(location)
        val composeTopInWindow = location[1].coerceIn(0, decorHeight)
        val composeBottom = min(decorHeight, composeTopInWindow + max(0, inputHeight))

        if (latinIMELegacy.isImeSuppressedByHardwareKeyboard && !legacyInputView!!.isShown) {
            // If there is a hardware keyboard and a visible software keyboard view has been hidden,
            // no visual element will be shown on the screen.
            val hiddenBottom = max(composeBottom, inputHeight)
            latinIMELegacy.setInsets(outInsets.apply {
                contentTopInsets = hiddenBottom
                visibleTopInsets = hiddenBottom
            })
            return
        }

        val fallbackKeyboardHeight = resources.getDimensionPixelSize(R.dimen.config_default_keyboard_height)
        val actionBarHeightPx = (40f * resources.displayMetrics.density).toInt()
        val effectiveTouchableHeight = ImeInsetsMath.resolveEffectiveTouchableHeight(
            measuredTouchableHeight = touchableHeight,
            legacyKeyboardHeight = inputViewHeight.takeIf { it > 0 } ?: (legacyInputView?.height ?: 0),
            actionBarHeightPx = actionBarHeightPx,
            fallbackKeyboardHeight = fallbackKeyboardHeight
        )
        val visibleTopY = ImeInsetsMath.computeImeInsetsTopY(
            composeTopInWindow = composeTopInWindow,
            composeHeight = inputHeight,
            decorHeight = decorHeight,
            effectiveTouchableHeight = effectiveTouchableHeight
        )

        val touchLeft = max(0, location[0])
        val touchTop = if (isInputModal) { 0 } else { visibleTopY }
        val touchRight = max(touchLeft, touchLeft + composeView.width)
        val touchBottom = max(visibleTopY, composeBottom)

        latinIMELegacy.setInsets(outInsets.apply {
            touchableInsets = Insets.TOUCHABLE_INSETS_REGION
            touchableRegion.set(touchLeft, touchTop, touchRight, touchBottom)
            contentTopInsets = visibleTopY
            visibleTopInsets = visibleTopY
        })
    }

    override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean {
        return latinIMELegacy.onShowInputRequested(
            flags,
            configChange
        ) || super.onShowInputRequested(flags, configChange)
    }

    override fun onEvaluateInputViewShown(): Boolean {
        return latinIMELegacy.onEvaluateInputViewShown() || super.onEvaluateInputViewShown()
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        // TODO: Revisit fullscreen mode
        return false //latinIMELegacy.onEvaluateFullscreenMode(super.onEvaluateFullscreenMode())
    }

    override fun updateFullscreenMode() {
        super.updateFullscreenMode()
        latinIMELegacy.updateFullscreenMode()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return latinIMELegacy.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return latinIMELegacy.onKeyUp(keyCode, event) || super.onKeyUp(keyCode, event)
    }

    override fun updateVisibility(shouldShowSuggestionsStrip: Boolean, fullscreenMode: Boolean) {
        uixManager.updateVisibility(shouldShowSuggestionsStrip, fullscreenMode)
    }

    override fun setSuggestions(suggestedWords: SuggestedWords?, rtlSubtype: Boolean) {
        uixManager.setSuggestions(suggestedWords, rtlSubtype)
    }

    override fun maybeShowImportantNoticeTitle(): Boolean {
        return false
    }

    override fun onLowMemory() {
        super.onLowMemory()
        uixManager.cleanUpPersistentStates()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        uixManager.cleanUpPersistentStates()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest {
        return createInlineSuggestionsRequest(this, this.activeColorScheme)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        return uixManager.onInlineSuggestionsResponse(response)
    }

    fun postUpdateSuggestionStrip(inputStyle: Int): Boolean {
        if (latinIMELegacy.mInputLogic.isAvroActive() || languageModelFacilitator.shouldPassThroughToLegacy()) return false

        languageModelFacilitator.updateSuggestionStripAsync(inputStyle);
        return true
    }

    fun requestForgetWord(suggestedWordInfo: SuggestedWordInfo) {
        uixManager.requestForgetWord(suggestedWordInfo)
    }

    fun refreshSuggestions() {
        latinIMELegacy.mInputLogic.performUpdateSuggestionStripSync(latinIMELegacy.mSettings.current, SuggestedWords.INPUT_STYLE_TYPING)
    }

    fun forceForgetWord(suggestedWordInfo: SuggestedWordInfo) {
        lifecycleScope.launch {
            val existingWords = getSetting(SUGGESTION_BLACKLIST).toMutableSet()
            existingWords.add(suggestedWordInfo.mWord)
            setSetting(SUGGESTION_BLACKLIST, existingWords)
        }

        latinIMELegacy.mDictionaryFacilitator.unlearnFromUserHistory(
            suggestedWordInfo.mWord, NgramContext.EMPTY_PREV_WORDS_INFO,
            -1, Constants.NOT_A_CODE
        )

        refreshSuggestions()
    }

    fun rememberEmojiSuggestion(suggestion: SuggestedWordInfo) {
        if(suggestion.mKindAndFlags == SuggestedWordInfo.KIND_EMOJI_SUGGESTION) {
            lifecycleScope.launch {
                withContext(Dispatchers.Default) {
                    useEmoji(suggestion.mWord)
                }
            }
        }
    }

    fun onEmojiDeleted(emoji: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.Default) {
                unuseEmoji(emoji)
            }
        }
    }

    fun cancelOngoingKeyboardTouches() {
        KeyboardSwitcher.getInstance().mainKeyboardView?.cancelAllOngoingEvents()
    }

    fun onVoiceShortcutPressed(preferSystemVoice: Boolean = false) {
        val useSystemVoiceInput = preferSystemVoice || getSettingBlocking(USE_SYSTEM_VOICE_INPUT)
        if (!useSystemVoiceInput) {
            uixManager.onActionActivated(VoiceInputAction)
            return
        }

        val richImm = RichInputMethodManager.getInstance()
        if (richImm.hasShortcutIme() && richImm.switchToShortcutIme(this)) {
            return
        }

        uixManager.onActionActivated(DeviceSpeechInputAction)
    }

    private var overrideInputConnection: InputConnection? = null
    private var overrideEditorInfo: EditorInfo? = null
    fun overrideInputConnection(to: InputConnection?, editorInfo: EditorInfo?) {
        if (to == null && this.overrideInputConnection == null) {
            return
        }
        if (to != null && to === this.overrideInputConnection) {
            return
        }

        cancelOngoingKeyboardTouches()
        this.overrideInputConnection = to
        this.overrideEditorInfo = editorInfo

        latinIMELegacy.loadSettings()

        inputLogic.finishInput()
        inputLogic.startInput(RichInputMethodManager.getInstance().combiningRulesExtraValueOfCurrentSubtype, latinIMELegacy.mSettings.current)

        val currentIC = currentInputConnection
        currentIC?.requestCursorUpdates(InputConnection.CURSOR_UPDATE_IMMEDIATE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            super.getCurrentInputConnection()?.setImeConsumesInput(to != null)
        }
    }

    val isInputConnectionOverridden
        get() = overrideInputConnection != null

    override fun getCurrentInputConnection(): InputConnection? {
        return overrideInputConnection ?: super.getCurrentInputConnection()
    }

    override fun getCurrentInputEditorInfo(): EditorInfo? {
        return overrideEditorInfo ?: super.getCurrentInputEditorInfo()
    }

    fun getBaseInputConnection(): InputConnection? {
        return super.getCurrentInputConnection()
    }

    override val lifecycle: Lifecycle
        get() = mLifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = mSavedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore
        get() = mViewModelStore


    private fun onDeviceUnlocked() {
        Log.i("LatinIME", "DEVICE has UNLOCKED!!! Reloading settings...")
        // Every place that called getDefaultSharedPreferences now needs to be refreshed or call it again

        // Mainly Settings singleton needs to be refreshed
        Settings.init(applicationContext)
        Settings.getInstance().onSharedPreferenceChanged(null /* unused */, "")
        latinIMELegacy.loadSettings()
        recreateKeyboard()

        languageModelFacilitator.loadHistoryLog()

        uixManager.onPersistentStatesUnlocked()

        // TODO: Spell checker service
    }
}
