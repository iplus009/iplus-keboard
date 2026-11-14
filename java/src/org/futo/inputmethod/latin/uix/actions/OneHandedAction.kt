package org.futo.inputmethod.latin.uix.actions

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.LatinIME
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.OneHandedModeSetting
import org.futo.inputmethod.latin.uix.PreferenceUtils
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.setSetting

object OneHandedModeHelper {
    fun normalizeMode(mode: String?): String = when (mode) {
        "left", "right" -> mode
        else -> "off"
    }

    fun nextMode(currentMode: String?): String = when (normalizeMode(currentMode)) {
        "off" -> "right"
        "right" -> "left"
        else -> "off"
    }

    fun oppositeSide(currentMode: String?): String = when (normalizeMode(currentMode)) {
        "left" -> "right"
        "right" -> "left"
        else -> "right"
    }

    suspend fun applyMode(context: Context, targetMode: String) {
        val normalized = normalizeMode(targetMode)
        if (normalized != "off") {
            val prefs = PreferenceUtils.getDefaultSharedPreferences(context)
            if (prefs.getBoolean(Settings.PREF_ENABLE_SPLIT_KEYBOARD, false)) {
                prefs.edit { putBoolean(Settings.PREF_ENABLE_SPLIT_KEYBOARD, false) }
                (context as? LatinIME)?.let { ime ->
                    withContext(Dispatchers.Main) {
                        ime.latinIMELegacy.reloadSettingsFromPrefs()
                    }
                }
            }
        }
        context.setSetting(OneHandedModeSetting, normalized)
    }
}

val OneHandedAction = Action(
    icon = R.drawable.one_handed,
    name = R.string.one_handed_action_title,
    simplePressImpl = { manager, _ ->
        val context = manager.getContext()
        val scope = (context as? LifecycleOwner)?.lifecycleScope ?: CoroutineScope(Dispatchers.Main)
        scope.launch {
            val currentMode = context.getSetting(OneHandedModeSetting)
            val nextMode = OneHandedModeHelper.nextMode(currentMode)
            OneHandedModeHelper.applyMode(context, nextMode)
            val label = when (nextMode) {
                "right" -> context.getString(R.string.one_handed_mode_right)
                "left" -> context.getString(R.string.one_handed_mode_left)
                else -> context.getString(R.string.one_handed_mode_off)
            }
            withContext(Dispatchers.Main) {
                manager.announce(label)
            }
        }
    },
    altPressImpl = { manager, _ ->
        val context = manager.getContext()
        val scope = (context as? LifecycleOwner)?.lifecycleScope ?: CoroutineScope(Dispatchers.Main)
        scope.launch {
            OneHandedModeHelper.applyMode(context, "off")
            withContext(Dispatchers.Main) {
                manager.announce(context.getString(R.string.one_handed_mode_off))
            }
        }
    },
    windowImpl = null,
)
