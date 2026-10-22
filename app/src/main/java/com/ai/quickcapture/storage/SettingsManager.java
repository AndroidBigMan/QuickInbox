package com.ai.quickcapture.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class SettingsManager {
    private static final String PREF_NAME = "quick_inbox_settings";
    private static final String KEY_VAULT_URI = "vault_tree_uri";
    private static final String KEY_TARGET_FILE_NAME = "target_file_name";
    private static final String KEY_TEMPLATE = "note_template";
    private static final String KEY_APPEND_TIMESTAMP = "append_timestamp";

    public static final String DEFAULT_FILE_NAME = "Inbox.md";
    public static final String DEFAULT_TEMPLATE = "- **%TIME%** %CONTENT%";

    private static volatile SettingsManager instance;
    private final SharedPreferences prefs;
    private final Context appContext;

    private SettingsManager(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    public static SettingsManager getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (SettingsManager.class) {
                if (instance == null) {
                    instance = new SettingsManager(context);
                }
            }
        }
        return instance;
    }

    @Nullable
    public Uri getVaultTreeUri() {
        String uriStr = prefs.getString(KEY_VAULT_URI, null);
        if (uriStr == null || uriStr.trim().isEmpty()) {
            return null;
        }
        try {
            return Uri.parse(uriStr);
        } catch (Exception e) {
            return null;
        }
    }

    public void setVaultTreeUri(@Nullable Uri uri) {
        prefs.edit().putString(KEY_VAULT_URI, uri != null ? uri.toString() : null).apply();
    }

    @NonNull
    public String getTargetFileName() {
        String name = prefs.getString(KEY_TARGET_FILE_NAME, DEFAULT_FILE_NAME);
        if (name == null || name.trim().isEmpty()) {
            return DEFAULT_FILE_NAME;
        }
        name = name.trim();
        return name.endsWith(".md") ? name : name + ".md";
    }

    public void setTargetFileName(@NonNull String fileName) {
        String name = fileName.trim();
        if (name.isEmpty()) {
            name = DEFAULT_FILE_NAME;
        } else if (!name.endsWith(".md")) {
            name = name + ".md";
        }
        prefs.edit().putString(KEY_TARGET_FILE_NAME, name).apply();
    }

    @NonNull
    public String getTemplate() {
        return prefs.getString(KEY_TEMPLATE, DEFAULT_TEMPLATE);
    }

    public void setTemplate(@NonNull String template) {
        prefs.edit().putString(KEY_TEMPLATE, template).apply();
    }

    public boolean isAppendTimestamp() {
        return prefs.getBoolean(KEY_APPEND_TIMESTAMP, true);
    }

    public void setAppendTimestamp(boolean append) {
        prefs.edit().putBoolean(KEY_APPEND_TIMESTAMP, append).apply();
    }
}
