package com.ai.quickcapture.voice;

import androidx.annotation.NonNull;

/**
 * 语音识别引擎通用接口。
 * 屏蔽底层 Sherpa-ONNX 端侧引擎与系统原生 SpeechRecognizer 的实现差异。
 */
public interface VoiceRecognitionEngine {

    interface Callback {
        void onListeningStateChanged(boolean listening);
        void onPartialResult(@NonNull String partialText);
        void onFinalResult(@NonNull String text);
        void onError(@NonNull String errorMessage, boolean canFallbackToSystemDialog);
        void onVoiceSupportDisabled(@NonNull String reason);
    }

    interface SupportCheckCallback {
        void onResult(boolean supported, @NonNull String reason);
    }

    /**
     * 获取引擎显示名称（如 "Sherpa-ONNX 纯端侧引擎" 或 "系统原生引擎"）
     */
    @NonNull
    String getEngineName();

    /**
     * 当前引擎是否可用
     */
    boolean isAvailable();

    /**
     * 异步检查引擎是否就绪（模型校验或系统服务探测）
     */
    void checkSupportAsync(@NonNull SupportCheckCallback callback);

    /**
     * 开始监听录音并流式出字
     */
    void startListening(@NonNull Callback callback);

    /**
     * 停止监听
     */
    void stopListening();

    /**
     * 当前是否正在录音
     */
    boolean isListening();

    /**
     * 释放引擎占用的资源
     */
    void release();
}
