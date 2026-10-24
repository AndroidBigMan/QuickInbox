package com.ai.quickcapture.voice;

import android.content.Context;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.util.Log;
import androidx.annotation.NonNull;

import com.ai.quickcapture.storage.SettingsManager;

/**
 * 语音管理门面（Facade）。
 * 统一调度 Sherpa-ONNX 纯端侧引擎与系统原生 SpeechRecognizer 引擎。
 */
public class SpeechVoiceManager {
    public static final String TAG = "QuickCapture";

    private final Context context;
    private final SettingsManager settingsManager;

    private SherpaOnnxVoiceEngine sherpaOnnxEngine;
    private SystemSpeechVoiceEngine systemSpeechEngine;

    public interface SpeechCallback {
        void onListeningStateChanged(boolean listening);
        void onPartialResult(@NonNull String partialText);
        void onFinalResult(@NonNull String text);
        void onError(@NonNull String errorMessage, boolean canFallbackToSystemDialog);
        void onVoiceSupportDisabled(@NonNull String reason);
    }

    public interface SupportCheckCallback {
        void onResult(boolean supported, @NonNull String reason);
    }

    public SpeechVoiceManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.settingsManager = SettingsManager.getInstance(context);
    }

    @NonNull
    public synchronized VoiceRecognitionEngine getActiveEngine() {
        int type = settingsManager.getVoiceEngineType();
        if (type == SettingsManager.ENGINE_SYSTEM) {
            if (systemSpeechEngine == null) {
                systemSpeechEngine = new SystemSpeechVoiceEngine(context);
            }
            return systemSpeechEngine;
        }

        // 默认使用 Sherpa-ONNX 纯端侧引擎
        if (sherpaOnnxEngine == null) {
            sherpaOnnxEngine = new SherpaOnnxVoiceEngine(context);
        }
        return sherpaOnnxEngine;
    }

    public boolean isSupported() {
        return getActiveEngine().isAvailable();
    }

    public boolean isListening() {
        return getActiveEngine().isListening();
    }

    /**
     * 异步检测当前所选引擎的可用状态
     */
    public void checkChineseSupportAsync(@NonNull final SupportCheckCallback callback) {
        int cached = settingsManager.getVoiceSupportStatus();
        if (cached == SettingsManager.VOICE_STATUS_SUPPORTED) {
            callback.onResult(true, "语音识别已就绪");
            return;
        }

        VoiceRecognitionEngine engine = getActiveEngine();
        engine.checkSupportAsync((supported, reason) -> {
            if (supported) {
                settingsManager.setVoiceSupportStatus(SettingsManager.VOICE_STATUS_SUPPORTED);
                callback.onResult(true, reason);
            } else {
                // 若 Sherpa-ONNX 异常且当前为端侧引擎，尝试平滑探测系统引擎
                if (settingsManager.getVoiceEngineType() == SettingsManager.ENGINE_SHERPA_ONNX) {
                    if (systemSpeechEngine == null) {
                        systemSpeechEngine = new SystemSpeechVoiceEngine(context);
                    }
                    systemSpeechEngine.checkSupportAsync((sysSupported, sysReason) -> {
                        if (sysSupported) {
                            settingsManager.setVoiceEngineType(SettingsManager.ENGINE_SYSTEM);
                            settingsManager.setVoiceSupportStatus(SettingsManager.VOICE_STATUS_SUPPORTED);
                            callback.onResult(true, "端侧模型未就绪，已自动平滑回退至系统语音");
                        } else {
                            settingsManager.setVoiceSupportStatus(SettingsManager.VOICE_STATUS_UNSUPPORTED);
                            callback.onResult(false, reason);
                        }
                    });
                } else {
                    settingsManager.setVoiceSupportStatus(SettingsManager.VOICE_STATUS_UNSUPPORTED);
                    callback.onResult(false, reason);
                }
            }
        });
    }

    @NonNull
    public static Intent createSystemSpeechIntent(@NonNull String prompt) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, prompt);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        return intent;
    }

    public void startListening(@NonNull final SpeechCallback callback) {
        VoiceRecognitionEngine engine = getActiveEngine();

        engine.startListening(new VoiceRecognitionEngine.Callback() {
            @Override
            public void onListeningStateChanged(boolean listening) {
                callback.onListeningStateChanged(listening);
            }

            @Override
            public void onPartialResult(@NonNull String partialText) {
                callback.onPartialResult(partialText);
            }

            @Override
            public void onFinalResult(@NonNull String text) {
                callback.onFinalResult(text);
            }

            @Override
            public void onError(@NonNull String errorMessage, boolean canFallbackToSystemDialog) {
                callback.onError(errorMessage, canFallbackToSystemDialog);
            }

            @Override
            public void onVoiceSupportDisabled(@NonNull String reason) {
                callback.onVoiceSupportDisabled(reason);
            }
        });
    }

    public void stopListening() {
        getActiveEngine().stopListening();
    }

    public void release() {
        if (sherpaOnnxEngine != null) {
            sherpaOnnxEngine.release();
            sherpaOnnxEngine = null;
        }
        if (systemSpeechEngine != null) {
            systemSpeechEngine.release();
            systemSpeechEngine = null;
        }
    }
}
