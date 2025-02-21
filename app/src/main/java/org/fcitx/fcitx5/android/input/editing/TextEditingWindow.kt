package org.fcitx.fcitx5.android.input.editing

import android.view.KeyEvent
import android.view.View
import androidx.lifecycle.lifecycleScope
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.FcitxKeyMapping
import org.fcitx.fcitx5.android.daemon.FcitxConnection
import org.fcitx.fcitx5.android.daemon.launchOnFcitxReady
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcastReceiver
import org.fcitx.fcitx5.android.input.clipboard.ClipboardWindow
import org.fcitx.fcitx5.android.input.dependency.fcitx
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.keyboard.CustomGestureView
import org.fcitx.fcitx5.android.input.wm.InputWindow
import org.fcitx.fcitx5.android.input.wm.InputWindowManager
import org.fcitx.fcitx5.android.utils.inputConnection
import org.mechdancer.dependency.manager.must

class TextEditingWindow : InputWindow.ExtendedInputWindow<TextEditingWindow>(),
    InputBroadcastReceiver {

    private val service: FcitxInputMethodService by manager.inputMethodService()
    private val windowManager: InputWindowManager by manager.must()
    private val fcitx: FcitxConnection by manager.fcitx()
    private val theme by manager.theme()

    private var hasSelection = false
    private var userSelection = false

    private fun sendDirectionKey(keyEventCode: Int) {
        service.sendCombinationKeyEvents(keyEventCode, shift = hasSelection || userSelection)
    }

    private val ui by lazy {
        TextEditingUi(context, theme).apply {
            fun CustomGestureView.onClickWithRepeating(block: () -> Unit) {
                setOnClickListener { block() }
                repeatEnabled = true
                onRepeatListener = { block() }
            }

            leftButton.onClickWithRepeating { sendDirectionKey(KeyEvent.KEYCODE_DPAD_LEFT) }

            upButton.onClickWithRepeating { sendDirectionKey(KeyEvent.KEYCODE_DPAD_UP) }

            downButton.onClickWithRepeating { sendDirectionKey(KeyEvent.KEYCODE_DPAD_DOWN) }

            rightButton.onClickWithRepeating { sendDirectionKey(KeyEvent.KEYCODE_DPAD_RIGHT) }

            homeButton.setOnClickListener { sendDirectionKey(KeyEvent.KEYCODE_MOVE_HOME) }
            endButton.setOnClickListener { sendDirectionKey(KeyEvent.KEYCODE_MOVE_END) }
            selectButton.setOnClickListener {
                if (hasSelection) {
                    userSelection = false
                    val end = service.selection.end
                    service.inputConnection?.setSelection(end, end)
                } else {
                    userSelection = !userSelection
                    updateSelection(hasSelection, userSelection)
                }
            }
            selectAllButton.setOnClickListener {
                // activate select button after operation
                userSelection = true
                service.inputConnection?.performContextMenuAction(android.R.id.selectAll)
            }
            cutButton.setOnClickListener {
                // deactivate select button after operation
                userSelection = false
                service.inputConnection?.performContextMenuAction(android.R.id.cut)
            }
            copyButton.setOnClickListener {
                userSelection = false
                service.inputConnection?.performContextMenuAction(android.R.id.copy)
            }
            pasteButton.setOnClickListener {
                userSelection = false
                service.inputConnection?.performContextMenuAction(android.R.id.paste)
            }
            backspaceButton.onClickWithRepeating {
                userSelection = false
                service.lifecycleScope.launchOnFcitxReady(fcitx) {
                    it.sendKey(FcitxKeyMapping.FcitxKey_BackSpace)
                }
            }
            clipboardButton.setOnClickListener {
                windowManager.attachWindow(ClipboardWindow())
            }
        }
    }

    override fun onCreateView(): View = ui.root

    override fun onAttached() {
        val info = service.selection
        onSelectionUpdate(info.start, info.end)
    }

    override fun onDetached() {}

    override fun onSelectionUpdate(start: Int, end: Int) {
        hasSelection = start != end
        ui.updateSelection(hasSelection, userSelection)
    }

    override val title by lazy {
        context.getString(R.string.text_editing)
    }

    override fun onCreateBarExtension(): View = ui.extension
}
