package com.ai.quickcapture.ui.bridge

import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.ai.quickcapture.ui.screen.CaptureDialogScreen
import com.ai.quickcapture.ui.screen.MainSettingsScreen
import com.ai.quickcapture.ui.theme.QuickInboxTheme

object UiBridge {

    /**
     * Interface for Java CaptureDialogActivity to handle user UI actions.
     */
    interface CaptureActionListener {
        fun onTextChange(newText: String)
        fun onSubmit()
        fun onVoiceToggle()
        fun onDismiss()
        fun onPasteClipboard(text: String)
        fun onDismissClipboard()
        fun onToggleTodoPrefix()
        fun onAppendTag(tag: String)
    }

    /**
     * Interface for Java MainActivity to handle setup UI actions.
     */
    interface MainActionListener {
        fun onPickFolder()
        fun onSaveSettings(fileName: String, template: String, autoSaveVoice: Boolean)
        fun onTestAppend()
        fun onAddTilePrompt()
        fun onRecheckVoiceSupport()
    }

    @JvmStatic
    fun bindCaptureDialog(
        composeView: ComposeView,
        uiState: CaptureUiState?,
        listener: CaptureActionListener
    ) {
        val safeState = uiState ?: CaptureUiState()

        composeView.apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                QuickInboxTheme {
                    CaptureDialogScreen(
                        uiState = safeState,
                        onTextChange = { listener.onTextChange(it) },
                        onSubmit = { listener.onSubmit() },
                        onVoiceToggle = { listener.onVoiceToggle() },
                        onDismiss = { listener.onDismiss() },
                        onPasteClipboard = { listener.onPasteClipboard(it) },
                        onDismissClipboard = { listener.onDismissClipboard() },
                        onToggleTodoPrefix = { listener.onToggleTodoPrefix() },
                        onAppendTag = { listener.onAppendTag(it) }
                    )
                }
            }
        }
    }

    @JvmStatic
    fun bindMainSettings(
        composeView: ComposeView,
        vaultPath: String?,
        fileName: String,
        template: String,
        autoSaveVoice: Boolean,
        isVoiceAvailable: Boolean,
        lastTestResult: String?,
        listener: MainActionListener
    ) {
        composeView.apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                QuickInboxTheme {
                    MainSettingsScreen(
                        vaultPath = vaultPath,
                        fileName = fileName,
                        template = template,
                        autoSaveVoice = autoSaveVoice,
                        isVoiceAvailable = isVoiceAvailable,
                        lastTestResult = lastTestResult,
                        onPickFolder = { listener.onPickFolder() },
                        onSaveSettings = { fName, tpl, autoSave -> listener.onSaveSettings(fName, tpl, autoSave) },
                        onTestAppend = { listener.onTestAppend() },
                        onAddTilePrompt = { listener.onAddTilePrompt() },
                        onRecheckVoiceSupport = { listener.onRecheckVoiceSupport() }
                    )
                }
            }
        }
    }
}
