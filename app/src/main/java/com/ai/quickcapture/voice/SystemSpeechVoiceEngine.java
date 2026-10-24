package com.ai.quickcapture.voice;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognitionSupport;
import android.speech.RecognitionSupportCallback;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SystemSpeechVoiceEngine implements VoiceRecognitionEngine {
    private static final String TAG = "SystemVoiceEngine";

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;
    private VoiceRecognitionEngine.Callback currentCallback;

    public SystemSpeechVoiceEngine(@NonNull Context context) {
        this.context = context;
    }

    @NonNull
    @Override
    public String getEngineName() {
        return "系统原生引擎";
    }

    @Override
    public boolean isAvailable() {
        try {
            return SpeechRecognizer.isRecognitionAvailable(context);
        } catch (Exception e) {
            Log.w(TAG, "检查 isRecognitionAvailable 异常", e);
            return false;
        }
    }

    @Override
    public void checkSupportAsync(@NonNull final VoiceRecognitionEngine.SupportCheckCallback callback) {
        if (!isAvailable()) {
            callback.onResult(false, "系统未找到任何可用的语音识别服务");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                final SpeechRecognizer probe = SpeechRecognizer.createSpeechRecognizer(context);
                if (probe == null) {
                    callback.onResult(false, "无法初始化系统语音识别实例");
                    return;
                }

                Intent intent = createSpeechIntent("");
                probe.checkRecognitionSupport(intent, context.getMainExecutor(), new RecognitionSupportCallback() {
                    @Override
                    public void onSupportResult(@NonNull RecognitionSupport support) {
                        List<String> installed = support.getInstalledOnDeviceLanguages();
                        boolean hasChineseOffline = isChineseLocaleInList(installed);

                        try {
                            probe.destroy();
                        } catch (Exception ignored) {}

                        if (hasChineseOffline) {
                            callback.onResult(true, "支持系统离线语音识别");
                        } else {
                            callback.onResult(false, "系统缺少中文离线语音包，已自动隐藏录音按钮");
                        }
                    }

                    @Override
                    public void onError(int error) {
                        try {
                            probe.destroy();
                        } catch (Exception ignored) {}
                        callback.onResult(false, "无法验证系统离线语音包，已自动隐藏录音按钮");
                    }
                });
                return;
            } catch (Exception e) {
                Log.w(TAG, "checkRecognitionSupport 异常", e);
            }
        }

        // Android 13 以下或无 API 33 支持
        callback.onResult(false, "系统未检测到中文离线语音包");
    }

    private static boolean isChineseLocaleInList(@Nullable List<String> list) {
        if (list == null || list.isEmpty()) return false;
        for (String lang : list) {
            if (lang == null) continue;
            String l = lang.toLowerCase(Locale.ROOT);
            if (l.contains("zh") || l.contains("cmn") || l.contains("chinese") || l.contains("mandarin")) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    public static Intent createSpeechIntent(@NonNull String prompt) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, prompt);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        return intent;
    }

    @Override
    public void startListening(@NonNull final VoiceRecognitionEngine.Callback callback) {
        this.currentCallback = callback;

        mainHandler.post(() -> {
            stopListening();

            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
                if (speechRecognizer == null) {
                    callback.onError("无法创建系统语音识别器", false);
                    return;
                }

                speechRecognizer.setRecognitionListener(new RecognitionListener() {
                    @Override
                    public void onReadyForSpeech(Bundle params) {
                        isListening = true;
                        callback.onListeningStateChanged(true);
                    }

                    @Override
                    public void onBeginningOfSpeech() {}

                    @Override
                    public void onRmsChanged(float rmsdB) {}

                    @Override
                    public void onBufferReceived(byte[] buffer) {}

                    @Override
                    public void onEndOfSpeech() {
                        isListening = false;
                        callback.onListeningStateChanged(false);
                    }

                    @Override
                    public void onError(int error) {
                        isListening = false;
                        callback.onListeningStateChanged(false);

                        String reason;
                        boolean canFallback = false;
                        switch (error) {
                            case SpeechRecognizer.ERROR_AUDIO:
                                reason = "音频录入错误";
                                break;
                            case SpeechRecognizer.ERROR_CLIENT:
                                reason = "客户端调用错误";
                                break;
                            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                                reason = "缺少录音权限";
                                break;
                            case SpeechRecognizer.ERROR_NETWORK:
                            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                                reason = "系统语音缺少离线中文包（离线模式不可用）";
                                callback.onVoiceSupportDisabled(reason);
                                break;
                            case SpeechRecognizer.ERROR_NO_MATCH:
                                reason = "未能识别出清晰文字，请重试";
                                canFallback = true;
                                break;
                            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                                reason = "语音引擎正忙，请稍候";
                                break;
                            case SpeechRecognizer.ERROR_SERVER:
                                reason = "语音识别服务异常";
                                break;
                            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                                reason = "未检测到说话声";
                                canFallback = true;
                                break;
                            default:
                                reason = "未知语音错误 (" + error + ")";
                                break;
                        }

                        callback.onError(reason, canFallback);
                    }

                    @Override
                    public void onResults(Bundle results) {
                        isListening = false;
                        callback.onListeningStateChanged(false);

                        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) {
                            callback.onFinalResult(matches.get(0));
                        } else {
                            callback.onError("未获取到文字结果", true);
                        }
                    }

                    @Override
                    public void onPartialResults(Bundle partialResults) {
                        ArrayList<String> partials = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (partials != null && !partials.isEmpty()) {
                            callback.onPartialResult(partials.get(0));
                        }
                    }

                    @Override
                    public void onEvent(int eventType, Bundle params) {}
                });

                speechRecognizer.startListening(createSpeechIntent("请说话..."));

            } catch (Exception e) {
                Log.e(TAG, "启动系统语音识别异常", e);
                callback.onError("启动系统语音失败: " + e.getMessage(), false);
            }
        });
    }

    @Override
    public void stopListening() {
        mainHandler.post(() -> {
            if (speechRecognizer != null) {
                try {
                    speechRecognizer.stopListening();
                    speechRecognizer.destroy();
                } catch (Exception ignored) {}
                speechRecognizer = null;
            }
            if (isListening) {
                isListening = false;
                if (currentCallback != null) {
                    currentCallback.onListeningStateChanged(false);
                }
            }
        });
    }

    @Override
    public boolean isListening() {
        return isListening;
    }

    @Override
    public void release() {
        stopListening();
    }
}
