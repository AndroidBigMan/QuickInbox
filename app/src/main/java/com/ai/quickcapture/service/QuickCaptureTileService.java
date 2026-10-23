package com.ai.quickcapture.service;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import com.ai.quickcapture.activity.CaptureDialogActivity;
import com.ai.quickcapture.storage.SettingsManager;

public class QuickCaptureTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setState(Tile.STATE_INACTIVE);
            tile.updateTile();
        }
    }

    @Override
    public void onClick() {
        super.onClick();

        Runnable launchRunnable = this::launchCaptureDialog;

        SettingsManager sm = SettingsManager.getInstance(this);
        if (isLocked() && !sm.isLockscreenCaptureEnabled()) {
            unlockAndRun(launchRunnable);
        } else {
            launchRunnable.run();
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private void launchCaptureDialog() {
        Intent intent = new Intent(this, CaptureDialogActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ requires PendingIntent
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            startActivityAndCollapse(pendingIntent);
        } else {
            // Android 13 and below
            startActivityAndCollapse(intent);
        }
    }
}
