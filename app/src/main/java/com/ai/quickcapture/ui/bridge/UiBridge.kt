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
        fun onSelectTargetFile(targetPattern: String)
    }

    /**
     * Interface for Java MainActivity to handle setup UI actions.
     */
    interface MainActionListener {
        fun onPickFolder()
        fun onSaveSettings(fileName: String, template: String, autoSaveVoice: Boolean, lockscreenCapture: Boolean)
        fun onTestAppend()
        fun onAddTilePrompt()
        fun onRecheckVoiceSupport()
        fun onOpenAssistSettings()
        fun onAddTargetPreset(preset: String)
        fun onDeleteTargetPreset(preset: String)
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
                        onAppendTag = { listener.onAppendTag(it) },
                        onSelectTargetFile = { listener.onSelectTargetFile(it) }
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
        lockscreenCapture: Boolean,
        targetPresets: List<String>,
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
                        lockscreenCapture = lockscreenCapture,
                        targetPresets = targetPresets,
                        isVoiceAvailable = isVoiceAvailable,
                        lastTestResult = lastTestResult,
                        onPickFolder = { listener.onPickFolder() },
                        onSaveSettings = { fName, tpl, autoSave, lockscreen ->
                            listener.onSaveSettings(fName, tpl, autoSave, lockscreen)
                        },
                        onTestAppend = { listener.onTestAppend() },
                        onAddTilePrompt = { listener.onAddTilePrompt() },
                        onRecheckVoiceSupport = { listener.onRecheckVoiceSupport() },
                        onOpenAssistSettings = { listener.onOpenAssistSettings() },
                        onAddTargetPreset = { listener.onAddTargetPreset(it) },
                        onDeleteTargetPreset = { listener.onDeleteTargetPreset(it) }
                    )
                }
            }
        }
    }
}
