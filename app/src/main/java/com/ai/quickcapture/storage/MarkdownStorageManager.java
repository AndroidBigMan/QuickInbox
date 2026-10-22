package com.ai.quickcapture.storage;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MarkdownStorageManager {
    private final Context appContext;
    private final SettingsManager settingsManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface StorageCallback {
        void onSuccess(@NonNull String savedLocation);
        void onError(@NonNull String errorMessage);
    }

    public MarkdownStorageManager(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.settingsManager = SettingsManager.getInstance(appContext);
    }

    public void appendNoteAsync(@NonNull final String content, @NonNull final StorageCallback callback) {
        if (content.trim().isEmpty()) {
            callback.onError("内容不能为空");
            return;
        }

        executor.execute(() -> {
            try {
                // 1. 格式化文本与时间戳
                String timestamp = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
                String template = settingsManager.getTemplate();
                String line;
                if (template.contains("%TIME%") || template.contains("%CONTENT%")) {
                    line = template.replace("%TIME%", timestamp).replace("%CONTENT%", content.trim());
                } else {
                    line = "- **" + timestamp + "** " + content.trim();
                }

                String formattedEntry = "\n" + line;
                byte[] data = formattedEntry.getBytes(StandardCharsets.UTF_8);

                Uri vaultUri = settingsManager.getVaultTreeUri();
                String targetFileName = settingsManager.getTargetFileName();
                String savedTargetDescription;

                if (vaultUri != null) {
                    ContentResolver resolver = appContext.getContentResolver();
                    String treeDocId = DocumentsContract.getTreeDocumentId(vaultUri);
                    Uri parentDocUri = DocumentsContract.buildDocumentUriUsingTree(vaultUri, treeDocId);
                    Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(vaultUri, treeDocId);

                    Uri targetFileUri = null;
                    try (Cursor cursor = resolver.query(
                            childrenUri,
                            new String[]{
                                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                            },
                            null,
                            null,
                            null
                    )) {
                        if (cursor != null) {
                            while (cursor.moveToNext()) {
                                String name = cursor.getString(1);
                                if (targetFileName.equalsIgnoreCase(name)) {
                                    String docId = cursor.getString(0);
                                    targetFileUri = DocumentsContract.buildDocumentUriUsingTree(vaultUri, docId);
                                    break;
                                }
                            }
                        }
                    }

                    if (targetFileUri == null) {
                        targetFileUri = DocumentsContract.createDocument(
                                resolver,
                                parentDocUri,
                                "text/markdown",
                                targetFileName
                        );
                    }

                    if (targetFileUri == null) {
                        throw new IllegalStateException("无法在所选目录中创建文件: " + targetFileName);
                    }

                    try (OutputStream os = resolver.openOutputStream(targetFileUri, "wa")) {
                        if (os == null) {
                            throw new IllegalStateException("无法打开输出流");
                        }
                        os.write(data);
                        os.flush();
                    }
                    savedTargetDescription = targetFileName + " (Obsidian 库)";
                } else {
                    File baseDir = appContext.getExternalFilesDir("Notes");
                    if (baseDir == null) {
                        baseDir = appContext.getFilesDir();
                    }
                    if (!baseDir.exists()) {
                        baseDir.mkdirs();
                    }

                    File localFile = new File(baseDir, targetFileName);
                    try (FileOutputStream fos = new FileOutputStream(localFile, true)) {
                        fos.write(data);
                        fos.flush();
                    }
                    savedTargetDescription = targetFileName + " (本地内置)";
                }

                final String finalLocation = savedTargetDescription;
                mainHandler.post(() -> callback.onSuccess(finalLocation));
            } catch (final Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "保存失败"));
            }
        });
    }

    public void release() {
        executor.shutdown();
    }
}
