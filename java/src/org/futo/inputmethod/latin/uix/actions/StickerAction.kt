package org.futo.inputmethod.latin.uix.actions

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.themestore.ui.StickerPickerPanel
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionWindow

/**
 * Standalone sticker action, so chat stickers are reachable from the action bar / an
 * action key without going through Emoji ➔ Stickers. Reuses the full existing picker
 * (pack tabs, commitContent insertion, store link) with no duplicated UI.
 */
val StickerAction = Action(
    icon = R.drawable.ic_stickers,
    name = R.string.sticker_action_title,
    canShowKeyboard = true,
    simplePressImpl = null,
    windowImpl = { manager, _ ->
        object : ActionWindow {
            @Composable
            override fun windowName(): String = stringResource(R.string.sticker_action_title)

            @Composable
            override fun WindowContents(keyboardShown: Boolean) {
                val view = LocalView.current
                StickerPickerPanel(
                    manager = manager,
                    onExit = {
                        manager.closeActionWindow()
                        manager.performHapticAndAudioFeedback(
                            Constants.CODE_SWITCH_ALPHA_SYMBOL,
                            view
                        )
                    }
                )
            }

            override fun close() = Unit
        }
    }
)
