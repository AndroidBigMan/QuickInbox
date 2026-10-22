package com.ai.quickcapture.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.compose.ui.platform.ComposeView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.ai.quickcapture.storage.MarkdownStorageManager;
import com.ai.quickcapture.storage.SettingsManager;
import com.ai.quickcapture.ui.bridge.CaptureUiState;
import com.ai.quickcapture.ui.bridge.UiBridge;
import com.ai.quickcapture.voice.SpeechVoiceManager;

public class CaptureDialogActivity extends ComponentActivity implements UiBridge.CaptureActionListener {

    private static final int REQ_RECORD_AUDIO = 1001;

    private ComposeView composeView;
    private MarkdownStorageManager storageManager;
    private SpeechVoiceManager speechManager;
    private SettingsManager settingsManager;

    private CaptureUiState currentUiState;
    private String voiceBaseText = "";
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        settingsManager = SettingsManager.getInstance(this);
        storageManager = new MarkdownStorageManager(this);
        speechManager = new SpeechVoiceManager(this);

        String initialText = extractInitialText(getIntent());

        currentUiState = new CaptureUiState(
                initialText,
                false,
                false,
                settingsManager.getTargetFileName(),
                null,
                false
        );

        composeView = new ComposeView(this);
        setContentView(composeView);

        renderUi();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String sharedText = extractInitialText(intent);
        if (!sharedText.isEmpty()) {
            currentUiState = new CaptureUiState(
                    sharedText,
                    currentUiState.isRecording(),
                    currentUiState.isSaving(),
                    currentUiState.getTargetFileName(),
                    null,
                    false
            );
            renderUi();
        }
    }

    private String extractInitialText(@Nullable Intent intent) {
        if (intent == null) return "";
        if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
            return shared != null ? shared.trim() : "";
        }
        return "";
    }

    private void renderUi() {
        UiBridge.bindCaptureDialog(composeView, currentUiState, this);
    }

    @Override
    public void onTextChange(@NonNull String newText) {
        currentUiState = new CaptureUiState(
                newText,
                currentUiState.isRecording(),
                currentUiState.isSaving(),
                currentUiState.getTargetFileName(),
                null,
                false
        );
        renderUi();
    }

    @Override
    public void onSubmit() {
        final String textToSave = currentUiState.getInputText().trim();
        if (textToSave.isEmpty()) {
            return;
        }

        // Set saving state
        currentUiState = new CaptureUiState(
                currentUiState.getInputText(),
                false,
                true,
                currentUiState.getTargetFileName(),
                "正在写入...",
                false
        );
        renderUi();

        storageManager.appendNoteAsync(textToSave, new MarkdownStorageManager.StorageCallback() {
            @Override
            public void onSuccess(@NonNull String savedLocation) {
                runOnUiThread(() -> {
                    currentUiState = new CaptureUiState(
                            currentUiState.getInputText(),
                            false,
                            false,
                            currentUiState.getTargetFileName(),
                            "已存入 " + savedLocation,
                            true
                    );
                    renderUi();

                    // Auto finish after short delay for visual feedback
                    handler.postDelayed(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            finish();
                        }
                    }, 400);
                });
            }

            @Override
            public void onError(@NonNull String errorMessage) {
                runOnUiThread(() -> {
                    currentUiState = new CaptureUiState(
                            currentUiState.getInputText(),
                            false,
                            false,
                            currentUiState.getTargetFileName(),
                            "保存失败: " + errorMessage,
                            false
                    );
                    renderUi();
                });
            }
        });
    }

    @Override
    public void onVoiceToggle() {
        if (currentUiState.isRecording()) {
            speechManager.stopListening();
            currentUiState = new CaptureUiState(
                    currentUiState.getInputText(),
                    false,
                    currentUiState.isSaving(),
                    currentUiState.getTargetFileName(),
                    null,
                    false
            );
            renderUi();
            return;
        }

        // Check permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
            return;
        }

        startVoiceInput();
    }

    private void startVoiceInput() {
        voiceBaseText = currentUiState.getInputText().trim();
        speechManager.startListening(new SpeechVoiceManager.SpeechCallback() {
            @Override
            public void onListeningStateChanged(boolean listening) {
                runOnUiThread(() -> {
                    currentUiState = new CaptureUiState(
                            currentUiState.getInputText(),
                            listening,
                            currentUiState.isSaving(),
                            currentUiState.getTargetFileName(),
                            listening ? "正在聆听..." : null,
                            false
                    );
                    renderUi();
                });
            }

            @Override
            public void onPartialResult(@NonNull String partialText) {
                runOnUiThread(() -> {
                    String combined = voiceBaseText.isEmpty() ? partialText : voiceBaseText + " " + partialText;
                    currentUiState = new CaptureUiState(
                            combined,
                            true,
                            currentUiState.isSaving(),
                            currentUiState.getTargetFileName(),
                            "正在识别...",
                            false
                    );
                    renderUi();
                });
            }

            @Override
            public void onFinalResult(@NonNull String text) {
                runOnUiThread(() -> {
                    String combined = voiceBaseText.isEmpty() ? text : voiceBaseText + " " + text;
                    currentUiState = new CaptureUiState(
                            combined,
                            false,
                            currentUiState.isSaving(),
                            currentUiState.getTargetFileName(),
                            "识别完成",
                            false
                    );
                    renderUi();
                });
            }

            @Override
            public void onError(@NonNull String errorMessage) {
                runOnUiThread(() -> {
                    currentUiState = new CaptureUiState(
                            voiceBaseText,
                            false,
                            currentUiState.isSaving(),
                            currentUiState.getTargetFileName(),
                            errorMessage,
                            false
                    );
                    renderUi();
                });
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceInput();
            } else {
                Toast.makeText(this, "需要麦克风权限以支持语音输入", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onDismiss() {
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechManager != null) {
            speechManager.release();
        }
        if (storageManager != null) {
            storageManager.release();
        }
        handler.removeCallbacksAndMessages(null);
    }
}
