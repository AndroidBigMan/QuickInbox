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

import com.ai.quickcapture.storage.SettingsManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SpeechVoiceManager {
    public static final String TAG = "QuickCapture";

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;

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

    private SpeechCallback currentCallback;

    public SpeechVoiceManager(@NonNull Context context) {
        this.context = context;
    }

    public boolean isSupported() {
        try {
            return SpeechRecognizer.isRecognitionAvailable(context);
        } catch (Exception e) {
            Log.w(TAG, "[VoiceManager] 检查 isRecognitionAvailable 异常", e);
            return false;
        }
    }

    /**
     * 异步检测系统当前是否真实具备可用的中文语音识别能力
     */
    public void checkChineseSupportAsync(@NonNull final SupportCheckCallback callback) {
        int cached = SettingsManager.getInstance(context).getVoiceSupportStatus();
        if (cached == SettingsManager.VOICE_STATUS_UNSUPPORTED) {
            callback.onResult(false, "已检测到系统缺少中文离线语音包");
            return;
        } else if (cached == SettingsManager.VOICE_STATUS_SUPPORTED) {
            callback.onResult(true, "系统支持语音识别");
            return;
        }

        if (!isSupported()) {
            SettingsManager.getInstance(context).setVoiceSupportStatus(SettingsManager.VOICE_STATUS_UNSUPPORTED);
            callback.onResult(false, "系统未找到任何可用的语音识别服务");
            return;
        }

        // Android 13+ (API 33+) 官方语言包支持探测
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                final SpeechRecognizer probe = SpeechRecognizer.createSpeechRecognizer(context);
                if (probe == null) {
                    SettingsManager.getInstance(context).setVoiceSupportStatus(SettingsManager.VOICE_STATUS_UNSUPPORTED);
                    callback.onResult(false, "无法初始化语音识别实例");
                    return;
                }

                Intent intent = createSystemSpeechIntent("");
                probe.checkRecognitionSupport(intent, context.getMainExecutor(), new RecognitionSupportCallback() {
                    @Override
                    public void onSupportResult(@NonNull RecognitionSupport support) {
                        List<String> installed = support.getInstalledOnDeviceLanguages();

                        // 严格检查已下载/预装到设备的离线语言包 (installed)
                        boolean hasChineseOffline = isChineseLocaleInList(installed);

                        try {
                            probe.destroy();
                        } catch (Exception ignored) {}

                        if (hasChineseOffline) {
                            Log.i(TAG, "[VoiceManager] 检测到系统内置中文离线语音包");
                            SettingsManager.getInstance(context).setVoiceSupportStatus(SettingsManager.VOICE_STATUS_SUPPORTED);
                            callback.onResult(true, "支持系统离线语音识别");
                        } else {
                            Log.w(TAG, "[VoiceManager] 本地无中文离线语音包，已自动标记为不支持");
                            SettingsManager.getInstance(context).setVoiceSupportStatus(SettingsManager.VOICE_STATUS_UNSUPPORTED);
                            callback.onResult(false, "系统缺少中文离线语音包，已自动隐藏录音按钮");
                        }
                    }

                    @Override
                    public void onError(int error) {
                        try {
                            probe.destroy();
                        } catch (Exception ignored) {}
                        SettingsManager.getInstance(context).setVoiceSupportStatus(SettingsManager.VOICE_STATUS_UNSUPPORTED);
                        callback.onResult(false, "无法验证系统离线语音包，已自动隐藏录音按钮");
                    }
                });
                return;
            } catch (Exception e) {
                Log.w(TAG, "[VoiceManager] checkRecognitionSupport 异常", e);
            }
        }

        // Android 13 以下或无 API 33 支持
        SettingsManager.getInstance(context).setVoiceSupportStatus(SettingsManager.VOICE_STATUS_UNSUPPORTED);
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
        this.currentCallback = callback;

        if (!SettingsManager.getInstance(context).isVoiceAvailable() || !isSupported()) {
            handleVoiceUnsupported("系统缺少中文离线语音包");
            return;
        }

        mainHandler.post(() -> {
            stopListening();

            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
                if (speechRecognizer == null) {
                    handleVoiceUnsupported("无法创建语音识别器");
                    return;
                }

                speechRecognizer.setRecognitionListener(new RecognitionListener() {
                    @Override
                    public void onReadyForSpeech(Bundle params) {
                        isListening = true;
                        notifyState(true);
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
                        notifyState(false);
                    }

                    @Override
                    public void onError(int error) {
                        isListening = false;
                        notifyState(false);
                        String reason;
                        boolean fallback = false;

                        if (error == 12 || error == 13) {
                            reason = "系统缺少中文离线语音包 (错误码 " + error + ")";
                            handleVoiceUnsupported(reason);
                            return;
                        }

                        switch (error) {
                            case SpeechRecognizer.ERROR_AUDIO:
                                reason = "音频录制错误";
                                break;
                            case SpeechRecognizer.ERROR_CLIENT:
                                reason = "客户端错误";
                                fallback = true;
                                break;
                            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                                reason = "缺少录音权限";
                                break;
                            case SpeechRecognizer.ERROR_NETWORK:
                                reason = "网络错误 (无法连接云端识别服务器)";
                                break;
                            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                                reason = "网络连接超时";
                                break;
                            case SpeechRecognizer.ERROR_NO_MATCH:
                                reason = "未匹配到有效文字";
                                break;
                            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                                reason = "语音引擎繁忙";
                                break;
                            case SpeechRecognizer.ERROR_SERVER:
                                reason = "服务端异常";
                                break;
                            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                                reason = "说话超时未检测到声音";
                                break;
                            case 11: // ERROR_SERVER_DISCONNECTED
                                reason = "语音服务连接断开";
                                break;
                            default:
                                reason = "语音识别错误 (代码: " + error + ")";
                                fallback = (error == 5 || error == 9);
                                break;
                        }

                        Log.w(TAG, "[VoiceManager] 语音识别报错: " + reason);

                        if (currentCallback != null) {
                            currentCallback.onError(reason, fallback);
                        }
                    }

                    @Override
                    public void onResults(Bundle results) {
                        isListening = false;
                        notifyState(false);
                        if (results != null) {
                            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                            if (matches != null && !matches.isEmpty()) {
                                String recognizedText = matches.get(0);
                                if (currentCallback != null) {
                                    currentCallback.onFinalResult(recognizedText);
                                }
                                return;
                            }
                        }
                        if (currentCallback != null) {
                            currentCallback.onError("未识别到文字内容", false);
                        }
                    }

                    @Override
                    public void onPartialResults(Bundle partialResults) {
                        if (partialResults != null) {
                            ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                            if (matches != null && !matches.isEmpty()) {
                                String recognizedText = matches.get(0);
                                if (currentCallback != null) {
                                    currentCallback.onPartialResult(recognizedText);
                                }
                            }
                        }
                    }

                    @Override
                    public void onEvent(int eventType, Bundle params) {}
                });

                Intent intent = createSystemSpeechIntent("正在聆听，请说话...");
                intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                speechRecognizer.startListening(intent);
            } catch (Exception e) {
                Log.e(TAG, "[VoiceManager] 启动语音识别异常", e);
                isListening = false;
                notifyState(false);
                if (currentCallback != null) {
                    currentCallback.onError("启动语音失败: " + e.getMessage(), true);
                }
            }
        });
    }

    private void handleVoiceUnsupported(@NonNull String reason) {
        Log.w(TAG, "[VoiceManager] " + reason + " -> 记录不支持并隐藏录音按键");
        isListening = false;
        notifyState(false);
        SettingsManager.getInstance(context).setVoiceSupportStatus(SettingsManager.VOICE_STATUS_UNSUPPORTED);
        if (currentCallback != null) {
            currentCallback.onVoiceSupportDisabled(reason);
        }
    }

    public void stopListening() {
        mainHandler.post(() -> {
            if (speechRecognizer != null) {
                try {
                    speechRecognizer.stopListening();
                    speechRecognizer.cancel();
                    speechRecognizer.destroy();
                } catch (Exception ignored) {}
                speechRecognizer = null;
            }
            if (isListening) {
                isListening = false;
                notifyState(false);
            }
        });
    }

    private void notifyState(boolean listening) {
        if (currentCallback != null) {
            currentCallback.onListeningStateChanged(listening);
        }
    }

    public void release() {
        stopListening();
        this.currentCallback = null;
    }
}
