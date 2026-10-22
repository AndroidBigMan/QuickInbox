package com.ai.quickcapture.voice;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Locale;

public class SpeechVoiceManager {
    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;

    public interface SpeechCallback {
        void onListeningStateChanged(boolean listening);
        void onPartialResult(@NonNull String partialText);
        void onFinalResult(@NonNull String text);
        void onError(@NonNull String errorMessage);
    }

    private SpeechCallback currentCallback;

    public SpeechVoiceManager(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    public boolean isSupported() {
        return SpeechRecognizer.isRecognitionAvailable(appContext);
    }

    public void startListening(@NonNull final SpeechCallback callback) {
        if (!isSupported()) {
            callback.onError("系统不支持语音识别或未安装语音引擎");
            return;
        }

        this.currentCallback = callback;

        mainHandler.post(() -> {
            stopListening();

            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext);
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
                        switch (error) {
                            case SpeechRecognizer.ERROR_AUDIO: reason = "音频录制错误"; break;
                            case SpeechRecognizer.ERROR_CLIENT: reason = "客户端错误"; break;
                            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: reason = "缺少录音权限"; break;
                            case SpeechRecognizer.ERROR_NETWORK:
                            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: reason = "网络或识别超时"; break;
                            case SpeechRecognizer.ERROR_NO_MATCH: reason = "未识别到语音"; break;
                            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: reason = "语音识别引擎忙碌"; break;
                            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: reason = "无语音输入"; break;
                            default: reason = "语音识别出错 (" + error + ")"; break;
                        }
                        if (currentCallback != null) {
                            currentCallback.onError(reason);
                        }
                    }

                    @Override
                    public void onResults(Bundle results) {
                        isListening = false;
                        notifyState(false);
                        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) {
                            String recognizedText = matches.get(0);
                            if (currentCallback != null) {
                                currentCallback.onFinalResult(recognizedText);
                            }
                        }
                    }

                    @Override
                    public void onPartialResults(Bundle partialResults) {
                        ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) {
                            String recognizedText = matches.get(0);
                            if (currentCallback != null) {
                                currentCallback.onPartialResult(recognizedText);
                            }
                        }
                    }

                    @Override
                    public void onEvent(int eventType, Bundle params) {}
                });

                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
                intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

                speechRecognizer.startListening(intent);
            } catch (Exception e) {
                isListening = false;
                notifyState(false);
                if (currentCallback != null) {
                    currentCallback.onError(e.getMessage() != null ? e.getMessage() : "启动录音失败");
                }
            }
        });
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
