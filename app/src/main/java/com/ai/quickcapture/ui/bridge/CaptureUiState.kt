package com.ai.quickcapture.ui.bridge

data class CaptureUiState @JvmOverloads constructor(
    val inputText: String = "",
    val isRecording: Boolean = false,
    val isSaving: Boolean = false,
    val targetFileName: String = "Inbox.md",
    val statusMessage: String? = null,
    val isSuccess: Boolean = false,
    val isVoiceAvailable: Boolean = false,
    val clipboardText: String? = null,
    val rawTargetPattern: String = "Inbox.md",
    val targetPresets: List<String> = emptyList()
)
