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
    }

    /**
     * Interface for Java MainActivity to handle setup UI actions.
     */
    interface MainActionListener {
        fun onPickFolder()
        fun onSaveSettings(fileName: String, template: String)
        fun onTestAppend()
        fun onAddTilePrompt()
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
                        onDismiss = { listener.onDismiss() }
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
                        lastTestResult = lastTestResult,
                        onPickFolder = { listener.onPickFolder() },
                        onSaveSettings = { fName, tpl -> listener.onSaveSettings(fName, tpl) },
                        onTestAppend = { listener.onTestAppend() },
                        onAddTilePrompt = { listener.onAddTilePrompt() }
                    )
                }
            }
        }
    }
}
