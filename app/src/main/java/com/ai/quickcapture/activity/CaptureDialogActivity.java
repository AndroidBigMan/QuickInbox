package com.ai.quickcapture.activity;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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

import java.util.ArrayList;
import java.util.List;

public class CaptureDialogActivity extends ComponentActivity implements UiBridge.CaptureActionListener {

    private static final String TAG = "QuickCapture";
    private static final int REQ_RECORD_AUDIO = 1001;

    private ComposeView composeView;
    private MarkdownStorageManager storageManager;
    private SpeechVoiceManager speechManager;
    private SettingsManager settingsManager;

    private CaptureUiState currentUiState;
    private String voiceBaseText = "";
    private String lastDismissedClipboardText = null;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<Intent> systemSpeechLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                int resultCode = result.getResultCode();
                Intent data = result.getData();

                if (resultCode == RESULT_OK && data != null) {
                    ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (matches != null && !matches.isEmpty()) {
                        String text = matches.get(0);
                        onSpeechResultReceived(text);
                    } else {
                        updateStatusMessage("未获取到语音文字", false);
                    }
                } else {
                    updateStatusMessage(null, false);
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        settingsManager = SettingsManager.getInstance(this);
        storageManager = new MarkdownStorageManager(this);
        speechManager = new SpeechVoiceManager(this);

        if (settingsManager.isLockscreenCaptureEnabled()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true);
                setTurnScreenOn(true);
            } else {
                getWindow().addFlags(
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                );
            }
        }

        boolean voiceAvailable = settingsManager.isVoiceAvailable();
        String initialText = extractInitialText(getIntent());
        String rawTarget = settingsManager.getRawTargetFileName();
        List<String> presets = settingsManager.getTargetPresets();
        String resolvedTarget = SettingsManager.resolveFileName(rawTarget);

        currentUiState = new CaptureUiState(
                initialText,
                false,
                false,
                resolvedTarget,
                null,
                false,
                voiceAvailable,
                null,
                rawTarget,
                presets
        );

        composeView = new ComposeView(this);
        setContentView(composeView);

        renderUi();

        // 若当前未明确系统语音状态，异步静默探测系统离线语音包
        if (settingsManager.getVoiceSupportStatus() == SettingsManager.VOICE_STATUS_UNKNOWN) {
            speechManager.checkChineseSupportAsync((supported, reason) -> {
                runOnUiThread(() -> {
                    if (currentUiState.isVoiceAvailable() != supported) {
                        currentUiState = copyState(
                                currentUiState.getInputText(),
                                false,
                                currentUiState.isSaving(),
                                currentUiState.getTargetFileName(),
                                null,
                                false,
                                supported,
                                currentUiState.getClipboardText()
                        );
                        renderUi();
                    }
                });
            });
        }
    }

    private CaptureUiState copyState(
            String inputText,
            boolean isRecording,
            boolean isSaving,
            String targetFileName,
            String statusMessage,
            boolean isSuccess,
            boolean isVoiceAvailable,
            String clipboardText
    ) {
        String rawTarget = currentUiState != null ? currentUiState.getRawTargetPattern() : settingsManager.getRawTargetFileName();
        List<String> presets = currentUiState != null ? currentUiState.getTargetPresets() : settingsManager.getTargetPresets();
        return new CaptureUiState(
                inputText,
                isRecording,
                isSaving,
                targetFileName,
                statusMessage,
                isSuccess,
                isVoiceAvailable,
                clipboardText,
                rawTarget,
                presets
        );
    }

    @Override
    public void onSelectTargetFile(@NonNull String targetPattern) {
        String clean = targetPattern.trim();
        String resolved = SettingsManager.resolveFileName(clean);
        settingsManager.setTargetFileName(clean);
        currentUiState = new CaptureUiState(
                currentUiState.getInputText(),
                currentUiState.isRecording(),
                currentUiState.isSaving(),
                resolved,
                null,
                false,
                currentUiState.isVoiceAvailable(),
                currentUiState.getClipboardText(),
                clean,
                currentUiState.getTargetPresets()
        );
        renderUi();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            checkClipboard();
        }
    }

    private void checkClipboard() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip()) {
                ClipData clipData = cm.getPrimaryClip();
                if (clipData != null && clipData.getItemCount() > 0) {
                    CharSequence text = clipData.getItemAt(0).getText();
                    if (text != null) {
                        String clipText = text.toString().trim();
                        if (!clipText.isEmpty()
                                && !clipText.equals(currentUiState.getInputText().trim())
                                && !clipText.equals(lastDismissedClipboardText)) {
                            currentUiState = copyState(
                                    currentUiState.getInputText(),
                                    currentUiState.isRecording(),
                                    currentUiState.isSaving(),
                                    currentUiState.getTargetFileName(),
                                    currentUiState.getStatusMessage(),
                                    currentUiState.isSuccess(),
                                    currentUiState.isVoiceAvailable(),
                                    clipText
                            );
                            renderUi();
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        lastDismissedClipboardText = null;
        String sharedText = extractInitialText(intent);
        if (!sharedText.isEmpty()) {
            currentUiState = copyState(
                    sharedText,
                    currentUiState.isRecording(),
                    currentUiState.isSaving(),
                    currentUiState.getTargetFileName(),
                    null,
                    false,
                    currentUiState.isVoiceAvailable(),
                    null
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
        String clip = currentUiState.getClipboardText();
        if (clip != null && newText.trim().equals(clip.trim())) {
            clip = null;
        }
        currentUiState = copyState(
                newText,
                currentUiState.isRecording(),
                currentUiState.isSaving(),
                currentUiState.getTargetFileName(),
                null,
                false,
                currentUiState.isVoiceAvailable(),
                clip
        );
        renderUi();
    }

    @Override
    public void onSubmit() {
        final String textToSave = currentUiState.getInputText().trim();
        if (textToSave.isEmpty()) {
            return;
        }
        saveNoteText(textToSave);
    }

    private void saveNoteText(@NonNull final String textToSave) {
        currentUiState = copyState(
                textToSave,
                false,
                true,
                currentUiState.getTargetFileName(),
                "正在写入文件...",
                false,
                currentUiState.isVoiceAvailable(),
                null
        );
        renderUi();

        storageManager.appendNoteAsync(textToSave, currentUiState.getRawTargetPattern(), new MarkdownStorageManager.StorageCallback() {
            @Override
            public void onSuccess(@NonNull String savedLocation) {
                runOnUiThread(() -> {
                    currentUiState = copyState(
                            textToSave,
                            false,
                            false,
                            currentUiState.getTargetFileName(),
                            "已成功存入 " + savedLocation,
                            true,
                            currentUiState.isVoiceAvailable(),
                            null
                    );
                    renderUi();

                    handler.postDelayed(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            finish();
                        }
                    }, 400);
                });
            }

            @Override
            public void onError(@NonNull String errorMessage) {
                Log.e(TAG, "[DialogActivity] 文件保存失败: " + errorMessage);
                runOnUiThread(() -> {
                    currentUiState = copyState(
                            textToSave,
                            false,
                            false,
                            currentUiState.getTargetFileName(),
                            "保存失败: " + errorMessage,
                            false,
                            currentUiState.isVoiceAvailable(),
                            null
                    );
                    renderUi();
                });
            }
        });
    }

    @Override
    public void onPasteClipboard(@NonNull String text) {
        lastDismissedClipboardText = text;
        String current = currentUiState.getInputText().trim();
        String combined = current.isEmpty() ? text : current + "\n" + text;
        currentUiState = copyState(
                combined,
                currentUiState.isRecording(),
                currentUiState.isSaving(),
                currentUiState.getTargetFileName(),
                null,
                false,
                currentUiState.isVoiceAvailable(),
                null
        );
        renderUi();
    }

    @Override
    public void onDismissClipboard() {
        lastDismissedClipboardText = currentUiState.getClipboardText();
        currentUiState = copyState(
                currentUiState.getInputText(),
                currentUiState.isRecording(),
                currentUiState.isSaving(),
                currentUiState.getTargetFileName(),
                currentUiState.getStatusMessage(),
                currentUiState.isSuccess(),
                currentUiState.isVoiceAvailable(),
                null
        );
        renderUi();
    }

    @Override
    public void onToggleTodoPrefix() {
        String current = currentUiState.getInputText();
        String updated;
        if (current.startsWith("- [ ] ")) {
            updated = current.substring(6);
        } else if (current.startsWith("- [x] ") || current.startsWith("- [X] ")) {
            updated = current.substring(6);
        } else if (current.startsWith("- ")) {
            updated = "- [ ] " + current.substring(2);
        } else {
            updated = "- [ ] " + current;
        }
        currentUiState = copyState(
                updated,
                currentUiState.isRecording(),
                currentUiState.isSaving(),
                currentUiState.getTargetFileName(),
                null,
                false,
                currentUiState.isVoiceAvailable(),
                currentUiState.getClipboardText()
        );
        renderUi();
    }

    @Override
    public void onAppendTag(@NonNull String tag) {
        String current = currentUiState.getInputText().trim();
        String tagStr = "#" + tag;
        String updated;
        if (current.isEmpty()) {
            updated = tagStr + " ";
        } else if (current.contains(tagStr)) {
            updated = current.replace(tagStr, "").replaceAll(" +", " ").trim();
        } else {
            updated = current + " " + tagStr + " ";
        }
        currentUiState = copyState(
                updated,
                currentUiState.isRecording(),
                currentUiState.isSaving(),
                currentUiState.getTargetFileName(),
                null,
                false,
                currentUiState.isVoiceAvailable(),
                currentUiState.getClipboardText()
        );
        renderUi();
    }

    @Override
    public void onVoiceToggle() {
        if (!currentUiState.isVoiceAvailable()) {
            return;
        }

        if (currentUiState.isRecording()) {
            speechManager.stopListening();
            currentUiState = copyState(
                    currentUiState.getInputText(),
                    false,
                    currentUiState.isSaving(),
                    currentUiState.getTargetFileName(),
                    null,
                    false,
                    currentUiState.isVoiceAvailable(),
                    currentUiState.getClipboardText()
            );
            renderUi();
            return;
        }

        // 检查录音权限
        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
            return;
        }

        startVoiceInput();
    }

    private void startVoiceInput() {
        voiceBaseText = currentUiState.getInputText().trim();

        if (!speechManager.isSupported()) {
            launchSystemSpeechDialog();
            return;
        }

        speechManager.startListening(new SpeechVoiceManager.SpeechCallback() {
            @Override
            public void onListeningStateChanged(boolean listening) {
                runOnUiThread(() -> {
                    currentUiState = copyState(
                            currentUiState.getInputText(),
                            listening,
                            currentUiState.isSaving(),
                            currentUiState.getTargetFileName(),
                            listening ? "正在聆听语音输入..." : null,
                            false,
                            currentUiState.isVoiceAvailable(),
                            currentUiState.getClipboardText()
                    );
                    renderUi();
                });
            }

            @Override
            public void onPartialResult(@NonNull String partialText) {
                runOnUiThread(() -> {
                    String combined = voiceBaseText.isEmpty() ? partialText : voiceBaseText + " " + partialText;
                    currentUiState = copyState(
                            combined,
                            true,
                            currentUiState.isSaving(),
                            currentUiState.getTargetFileName(),
                            "正在识别...",
                            false,
                            currentUiState.isVoiceAvailable(),
                            currentUiState.getClipboardText()
                    );
                    renderUi();
                });
            }

            @Override
            public void onFinalResult(@NonNull String text) {
                onSpeechResultReceived(text);
            }

            @Override
            public void onError(@NonNull String errorMessage, boolean canFallbackToSystemDialog) {
                Log.w(TAG, "[DialogActivity] 语音识别报错: " + errorMessage);
                runOnUiThread(() -> {
                    if (canFallbackToSystemDialog) {
                        launchSystemSpeechDialog();
                    } else {
                        currentUiState = copyState(
                                voiceBaseText,
                                false,
                                currentUiState.isSaving(),
                                currentUiState.getTargetFileName(),
                                errorMessage,
                                false,
                                currentUiState.isVoiceAvailable(),
                                currentUiState.getClipboardText()
                        );
                        renderUi();
                    }
                });
            }

            @Override
            public void onVoiceSupportDisabled(@NonNull String reason) {
                Log.w(TAG, "[DialogActivity] 捕获到系统不支持离线中文语音: " + reason);
                runOnUiThread(() -> {
                    currentUiState = copyState(
                            voiceBaseText,
                            false,
                            currentUiState.isSaving(),
                            currentUiState.getTargetFileName(),
                            "已自动隐藏录音按钮，可直接使用输入法语音",
                            false,
                            false,
                            currentUiState.getClipboardText()
                    );
                    renderUi();
                });
            }
        });
    }

    private void launchSystemSpeechDialog() {
        try {
            Intent intent = SpeechVoiceManager.createSystemSpeechIntent("请说话，将自动写入收件箱...");
            PackageManager pm = getPackageManager();
            List<ResolveInfo> resolves = pm.queryIntentActivities(intent, 0);

            if (resolves != null && !resolves.isEmpty()) {
                systemSpeechLauncher.launch(intent);
            } else {
                updateStatusMessage("系统未内置语音服务，建议直接使用键盘自带语音键", false);
            }
        } catch (Exception e) {
            Log.e(TAG, "[DialogActivity] 唤起系统语音弹窗失败", e);
            runOnUiThread(() -> {
                currentUiState = copyState(
                        voiceBaseText,
                        false,
                        currentUiState.isSaving(),
                        currentUiState.getTargetFileName(),
                        "唤起语音失败: " + e.getMessage(),
                        false,
                        currentUiState.isVoiceAvailable(),
                        currentUiState.getClipboardText()
                );
                renderUi();
            });
        }
    }

    private void onSpeechResultReceived(@NonNull String text) {
        runOnUiThread(() -> {
            String combined = voiceBaseText.isEmpty() ? text.trim() : voiceBaseText + " " + text.trim();
            boolean autoSave = settingsManager.isAutoSaveVoice();

            if (autoSave && !combined.isEmpty()) {
                currentUiState = copyState(
                        combined,
                        false,
                        true,
                        currentUiState.getTargetFileName(),
                        "正在自动存入文件...",
                        true,
                        currentUiState.isVoiceAvailable(),
                        null
                );
                renderUi();
                saveNoteText(combined);
            } else {
                currentUiState = copyState(
                        combined,
                        false,
                        false,
                        currentUiState.getTargetFileName(),
                        "识别完成，点击「存入」即可写入文件",
                        true,
                        currentUiState.isVoiceAvailable(),
                        null
                );
                renderUi();
            }
        });
    }

    private void updateStatusMessage(@Nullable String message, boolean isSuccess) {
        runOnUiThread(() -> {
            currentUiState = copyState(
                    currentUiState.getInputText(),
                    false,
                    currentUiState.isSaving(),
                    currentUiState.getTargetFileName(),
                    message,
                    isSuccess,
                    currentUiState.isVoiceAvailable(),
                    currentUiState.getClipboardText()
            );
            renderUi();
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceInput();
            } else {
                Log.w(TAG, "[DialogActivity] 录音权限被拒绝");
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
