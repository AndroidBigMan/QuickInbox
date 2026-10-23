package com.ai.quickcapture.activity;

import android.app.StatusBarManager;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.compose.ui.platform.ComposeView;

import com.ai.quickcapture.service.QuickCaptureTileService;
import com.ai.quickcapture.storage.MarkdownStorageManager;
import com.ai.quickcapture.storage.SettingsManager;
import com.ai.quickcapture.ui.bridge.UiBridge;
import com.ai.quickcapture.voice.SpeechVoiceManager;

public class MainActivity extends ComponentActivity implements UiBridge.MainActionListener {

    private ComposeView composeView;
    private SettingsManager settingsManager;
    private MarkdownStorageManager storageManager;
    private SpeechVoiceManager speechManager;

    private String lastTestResult = null;

    private final ActivityResultLauncher<Intent> folderPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri treeUri = result.getData().getData();
                    if (treeUri != null) {
                        try {
                            final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                            getContentResolver().takePersistableUriPermission(treeUri, takeFlags);
                            settingsManager.setVaultTreeUri(treeUri);
                            lastTestResult = "✅ 目录授权成功: " + treeUri.getLastPathSegment();
                        } catch (Exception e) {
                            lastTestResult = "⚠️ 权限持久化失败: " + e.getMessage();
                        }
                        renderUi();
                    }
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        settingsManager = SettingsManager.getInstance(this);
        storageManager = new MarkdownStorageManager(this);
        speechManager = new SpeechVoiceManager(this);

        composeView = new ComposeView(this);
        setContentView(composeView);

        renderUi();

        // 首次打开若状态未确定，自动触发一次静默检测
        if (settingsManager.getVoiceSupportStatus() == SettingsManager.VOICE_STATUS_UNKNOWN) {
            speechManager.checkChineseSupportAsync((supported, reason) -> {
                runOnUiThread(this::renderUi);
            });
        }
    }

    private void renderUi() {
        Uri vaultUri = settingsManager.getVaultTreeUri();
        String vaultPathDisplay = vaultUri != null ? vaultUri.getLastPathSegment() : null;

        UiBridge.bindMainSettings(
                composeView,
                vaultPathDisplay,
                settingsManager.getRawTargetFileName(),
                settingsManager.getTemplate(),
                settingsManager.isAutoSaveVoice(),
                settingsManager.isVoiceAvailable(),
                lastTestResult,
                this
        );
    }

    @Override
    public void onPickFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        folderPickerLauncher.launch(intent);
    }

    @Override
    public void onSaveSettings(@NonNull String fileName, @NonNull String template, boolean autoSaveVoice) {
        if (!fileName.trim().isEmpty()) {
            settingsManager.setTargetFileName(fileName.trim());
        }
        if (!template.trim().isEmpty()) {
            settingsManager.setTemplate(template.trim());
        }
        settingsManager.setAutoSaveVoice(autoSaveVoice);

        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
        lastTestResult = "✅ 配置已更新（目标: " + settingsManager.getTargetFileName() + "，语音直存: " + (autoSaveVoice ? "开启" : "关闭") + "）";
        renderUi();
    }

    @Override
    public void onTestAppend() {
        lastTestResult = "正在尝试写入测试笔记...";
        renderUi();

        storageManager.appendNoteAsync("💡 这是一条自用秒级收件箱测试灵感！", new MarkdownStorageManager.StorageCallback() {
            @Override
            public void onSuccess(@NonNull String savedLocation) {
                runOnUiThread(() -> {
                    lastTestResult = "🎉 测试成功！已成功追加写入至: " + savedLocation;
                    renderUi();
                });
            }

            @Override
            public void onError(@NonNull String errorMessage) {
                runOnUiThread(() -> {
                    lastTestResult = "❌ 写入测试失败: " + errorMessage;
                    renderUi();
                });
            }
        });
    }

    @Override
    public void onAddTilePrompt() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            StatusBarManager statusBarManager = getSystemService(StatusBarManager.class);
            if (statusBarManager != null) {
                ComponentName componentName = new ComponentName(this, QuickCaptureTileService.class);
                statusBarManager.requestAddTileService(
                        componentName,
                        "记灵感",
                        android.graphics.drawable.Icon.createWithResource(this, com.ai.quickcapture.R.drawable.ic_tile_inbox),
                        getMainExecutor(),
                        resultCode -> {
                            if (resultCode == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED) {
                                Toast.makeText(MainActivity.this, "磁贴已成功添加至控制中心！", Toast.LENGTH_SHORT).show();
                            }
                        }
                );
                return;
            }
        }

        Toast.makeText(this, "请下拉系统通知栏，点击编辑图标，将「记灵感」拖入快捷面板", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRecheckVoiceSupport() {
        lastTestResult = "正在重新探测系统底层离线语音包与服务...";
        renderUi();

        settingsManager.resetVoiceSupportStatus();
        speechManager.checkChineseSupportAsync((supported, reason) -> {
            runOnUiThread(() -> {
                lastTestResult = (supported ? "✅ " : "⚠️ ") + "语音支持检测: " + reason;
                renderUi();
            });
        });
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
    }
}
