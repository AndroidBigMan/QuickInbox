package com.ai.quickcapture.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class SettingsManager {
    private static final String PREF_NAME = "quick_inbox_settings";
    private static final String KEY_VAULT_URI = "vault_tree_uri";
    private static final String KEY_TARGET_FILE_NAME = "target_file_name";
    private static final String KEY_TEMPLATE = "note_template";
    private static final String KEY_APPEND_TIMESTAMP = "append_timestamp";
    private static final String KEY_AUTO_SAVE_VOICE = "auto_save_voice";
    public static final String KEY_LOCKSCREEN_CAPTURE = "lockscreen_capture";
    public static final String KEY_TARGET_PRESETS = "target_file_presets";

    public static final String DEFAULT_FILE_NAME = "Inbox.md";
    public static final String DEFAULT_TEMPLATE = "- **%TIME%** %CONTENT%";
    private static final String[] DEFAULT_PRESETS = new String[]{
            "Inbox.md",
            "Daily/%YYYY%-%MM%-%DD%.md",
            "Todo.md",
            "Reading.md"
    };

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
    public String getRawTargetFileName() {
        String name = prefs.getString(KEY_TARGET_FILE_NAME, DEFAULT_FILE_NAME);
        if (name == null || name.trim().isEmpty()) {
            return DEFAULT_FILE_NAME;
        }
        name = name.trim();
        return name.endsWith(".md") ? name : name + ".md";
    }

    @NonNull
    public String getTargetFileName() {
        return resolveFileName(getRawTargetFileName());
    }

    @NonNull
    public static String resolveFileName(@NonNull String rawPattern) {
        java.util.Date now = new java.util.Date();
        String year = new java.text.SimpleDateFormat("yyyy", java.util.Locale.getDefault()).format(now);
        String month = new java.text.SimpleDateFormat("MM", java.util.Locale.getDefault()).format(now);
        String day = new java.text.SimpleDateFormat("dd", java.util.Locale.getDefault()).format(now);

        String resolved = rawPattern
                .replace("%YYYY%", year)
                .replace("%Y%", year)
                .replace("%MM%", month)
                .replace("%M%", month)
                .replace("%DD%", day)
                .replace("%D%", day)
                .replace("{{date}}", year + "-" + month + "-" + day)
                .replace("{YYYY}", year)
                .replace("{MM}", month)
                .replace("{DD}", day)
                .replace("%YYYY", year)
                .replace("%MM", month)
                .replace("%DD", day);

        if (!resolved.endsWith(".md")) {
            resolved += ".md";
        }
        return resolved;
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

    public boolean isAutoSaveVoice() {
        return prefs.getBoolean(KEY_AUTO_SAVE_VOICE, true);
    }

    public void setAutoSaveVoice(boolean autoSave) {
        prefs.edit().putBoolean(KEY_AUTO_SAVE_VOICE, autoSave).apply();
    }

    public static final String KEY_VOICE_SUPPORT_STATUS = "voice_support_status_v2";
    public static final int VOICE_STATUS_UNKNOWN = 0;
    public static final int VOICE_STATUS_SUPPORTED = 1;
    public static final int VOICE_STATUS_UNSUPPORTED = 2;

    public int getVoiceSupportStatus() {
        return prefs.getInt(KEY_VOICE_SUPPORT_STATUS, VOICE_STATUS_UNKNOWN);
    }

    public void setVoiceSupportStatus(int status) {
        prefs.edit().putInt(KEY_VOICE_SUPPORT_STATUS, status).apply();
    }

    public boolean isVoiceAvailable() {
        return getVoiceSupportStatus() == VOICE_STATUS_SUPPORTED;
    }

    public void resetVoiceSupportStatus() {
        prefs.edit().remove(KEY_VOICE_SUPPORT_STATUS).apply();
    }

    public boolean isLockscreenCaptureEnabled() {
        return prefs.getBoolean(KEY_LOCKSCREEN_CAPTURE, true);
    }

    public void setLockscreenCaptureEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_LOCKSCREEN_CAPTURE, enabled).apply();
    }

    @NonNull
    public List<String> getTargetPresets() {
        String saved = prefs.getString(KEY_TARGET_PRESETS, null);
        List<String> list = new ArrayList<>();
        if (saved == null || saved.trim().isEmpty()) {
            for (String p : DEFAULT_PRESETS) {
                list.add(p);
            }
        } else {
            String[] parts = saved.split("\n");
            for (String p : parts) {
                String trimmed = p.trim();
                if (!trimmed.isEmpty() && !list.contains(trimmed)) {
                    list.add(trimmed);
                }
            }
        }
        String current = getRawTargetFileName();
        if (!list.contains(current)) {
            list.add(0, current);
        }
        return list;
    }

    public void setTargetPresets(@NonNull List<String> presets) {
        StringBuilder sb = new StringBuilder();
        for (String p : presets) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(trimmed);
            }
        }
        prefs.edit().putString(KEY_TARGET_PRESETS, sb.toString()).apply();
    }

    public void addTargetPreset(@NonNull String preset) {
        String clean = preset.trim();
        if (clean.isEmpty()) return;
        if (!clean.endsWith(".md")) clean += ".md";
        List<String> list = getTargetPresets();
        if (!list.contains(clean)) {
            list.add(clean);
            setTargetPresets(list);
        }
    }

    public void removeTargetPreset(@NonNull String preset) {
        String clean = preset.trim();
        List<String> list = getTargetPresets();
        if (list.remove(clean)) {
            setTargetPresets(list);
        }
    }
}
