package com.ai.quickcapture.storage;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
    private static final String TAG = "QuickCapture";

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
        appendNoteAsync(content, null, callback);
    }

    public void appendNoteAsync(@NonNull final String content, @Nullable final String customTargetFileName, @NonNull final StorageCallback callback) {
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

                Uri vaultUri = settingsManager.getVaultTreeUri();
                String targetFileName = (customTargetFileName != null && !customTargetFileName.trim().isEmpty())
                        ? SettingsManager.resolveFileName(customTargetFileName.trim())
                        : settingsManager.getTargetFileName();
                String savedTargetDescription;

                if (vaultUri != null) {
                    ContentResolver resolver = appContext.getContentResolver();
                    Uri targetFileUri = findOrCreateFileInVault(resolver, vaultUri, targetFileName);

                    if (targetFileUri == null) {
                        throw new IllegalStateException("无法在所选目录中创建目标文件: " + targetFileName);
                    }

                    boolean isNewOrEmpty = true;
                    try (Cursor cursor = resolver.query(targetFileUri, new String[]{DocumentsContract.Document.COLUMN_SIZE}, null, null, null)) {
                        if (cursor != null && cursor.moveToFirst()) {
                            long size = cursor.getLong(0);
                            if (size > 0) {
                                isNewOrEmpty = false;
                            }
                        }
                    } catch (Exception ignored) {}

                    String formattedEntry = isNewOrEmpty ? (line + "\n") : ("\n" + line);
                    byte[] data = formattedEntry.getBytes(StandardCharsets.UTF_8);

                    try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(targetFileUri, "wa")) {
                        if (pfd == null) {
                            throw new IllegalStateException("无法打开文件输出通道: " + targetFileName);
                        }
                        try (FileOutputStream fos = new FileOutputStream(pfd.getFileDescriptor())) {
                            fos.write(data);
                            fos.flush();
                            try {
                                pfd.getFileDescriptor().sync();
                            } catch (Exception ignored) {}
                        }
                    }

                    notifyMediaScanner(vaultUri, targetFileName);
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
                    if (localFile.getParentFile() != null && !localFile.getParentFile().exists()) {
                        localFile.getParentFile().mkdirs();
                    }

                    boolean localEmpty = !localFile.exists() || localFile.length() == 0;
                    String localFormattedEntry = localEmpty ? (line + "\n") : ("\n" + line);
                    byte[] localData = localFormattedEntry.getBytes(StandardCharsets.UTF_8);

                    try (FileOutputStream fos = new FileOutputStream(localFile, true)) {
                        fos.write(localData);
                        fos.flush();
                        try {
                            fos.getFD().sync();
                        } catch (Exception ignored) {}
                    }

                    try {
                        MediaScannerConnection.scanFile(appContext, new String[]{localFile.getAbsolutePath()}, new String[]{"text/markdown"}, null);
                        appContext.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(localFile)));
                    } catch (Exception ignored) {}

                    savedTargetDescription = targetFileName + " (本地内置)";
                }

                final String finalLocation = savedTargetDescription;
                mainHandler.post(() -> callback.onSuccess(finalLocation));
            } catch (final Exception e) {
                Log.e(TAG, "[StorageManager] 写入异常", e);
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "保存失败"));
            }
        });
    }

    @Nullable
    private Uri findOrCreateFileInVault(@NonNull ContentResolver resolver, @NonNull Uri vaultUri, @NonNull String targetPath) throws Exception {
        String treeDocId = DocumentsContract.getTreeDocumentId(vaultUri);
        Uri currentParentUri = DocumentsContract.buildDocumentUriUsingTree(vaultUri, treeDocId);
        String currentParentDocId = treeDocId;

        String normalizedPath = targetPath.replace('\\', '/').trim();
        while (normalizedPath.startsWith("/")) {
            normalizedPath = normalizedPath.substring(1);
        }
        String[] parts = normalizedPath.split("/");

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) continue;
            boolean isLastPart = (i == parts.length - 1);

            Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(vaultUri, currentParentDocId);
            Uri foundUri = null;
            String foundDocId = null;

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
                        if (part.equalsIgnoreCase(name)) {
                            foundDocId = cursor.getString(0);
                            foundUri = DocumentsContract.buildDocumentUriUsingTree(vaultUri, foundDocId);
                            break;
                        }
                    }
                }
            }

            if (foundUri != null) {
                if (isLastPart) {
                    return foundUri;
                } else {
                    currentParentUri = foundUri;
                    currentParentDocId = foundDocId;
                }
            } else {
                if (isLastPart) {
                    return DocumentsContract.createDocument(
                            resolver,
                            currentParentUri,
                            "text/markdown",
                            part
                    );
                } else {
                    Uri newDirUri = DocumentsContract.createDocument(
                            resolver,
                            currentParentUri,
                            DocumentsContract.Document.MIME_TYPE_DIR,
                            part
                    );
                    if (newDirUri == null) {
                        throw new IllegalStateException("无法创建目录: " + part);
                    }
                    currentParentUri = newDirUri;
                    currentParentDocId = DocumentsContract.getDocumentId(newDirUri);
                }
            }
        }
        return null;
    }

    private void notifyMediaScanner(@NonNull Uri vaultUri, @NonNull String targetFileName) {
        try {
            String treeDocId = DocumentsContract.getTreeDocumentId(vaultUri);
            if (treeDocId != null && treeDocId.startsWith("primary:")) {
                String subPath = treeDocId.substring("primary:".length());
                File baseDir = new File(Environment.getExternalStorageDirectory(), subPath);
                File file = new File(baseDir, targetFileName);
                if (file.exists()) {
                    MediaScannerConnection.scanFile(
                            appContext,
                            new String[]{file.getAbsolutePath()},
                            new String[]{"text/markdown"},
                            null
                    );
                    appContext.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)));
                }
            }
        } catch (Exception ignored) {}
    }

    public void release() {
        executor.shutdown();
    }
}
