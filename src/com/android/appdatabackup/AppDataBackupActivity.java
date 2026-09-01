/*
 * Copyright (C) 2026 VoltageOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.appdatabackup;

import android.app.Activity;
import android.app.appbackup.AppDataBackupRestoreManager;
import android.app.appbackup.BackupRecord;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.DynamicColors;

import java.io.File;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppDataBackupActivity extends Activity {

    private static final String TAG = "AppDataBackupUI";

    private AppDataBackupRestoreManager mManager;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final SimpleDateFormat mDateFormat =
            new SimpleDateFormat("MMM d, HH:mm", Locale.getDefault());

    private File mBackupDir;
    private TextView mLastBackupValue;
    private TextView mTvDeviceName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        mManager = (AppDataBackupRestoreManager)
                getSystemService(APP_DATA_BACKUP_SERVICE);
        mBackupDir = new File("/data/media/" + UserHandle.myUserId() + "/AppDataBackup");

        mLastBackupValue = findViewById(R.id.tv_last_backup_value);
        mTvDeviceName = findViewById(R.id.tv_device_name);

        if (mTvDeviceName != null) {
            mTvDeviceName.setText(getDeviceMarketName());
        }

        final MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        final View btnBackupNow = findViewById(R.id.btn_backup_now);
        final View backupTile = findViewById(R.id.card_action_backup);
        final View restoreTile = findViewById(R.id.card_action_restore);

        if (btnBackupNow != null) {
            btnBackupNow.setOnClickListener(v -> openManage(BackupManageActivity.TAB_APPS));
        }
        if (backupTile != null) {
            backupTile.setOnClickListener(v -> openManage(BackupManageActivity.TAB_APPS));
        }
        if (restoreTile != null) {
            restoreTile.setOnClickListener(v -> openManage(BackupManageActivity.TAB_BACKUPS));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadOverviewAsync();
    }

    private void openManage(int initialTab) {
        final Intent intent = new Intent(this, BackupManageActivity.class);
        intent.putExtra(BackupManageActivity.EXTRA_INITIAL_TAB, initialTab);
        startActivity(intent);
    }

    private void loadOverviewAsync() {
        mExecutor.submit(() -> {
            mBackupDir.mkdirs();
            final List<BackupRecord> backups =
                    mManager.getAvailableBackups(mBackupDir.getAbsolutePath());

            final Set<String> packages = new HashSet<>();
            long latest = 0;
            for (BackupRecord r : backups) {
                packages.add(r.getPackageName());
                if (r.getTimestampMs() > latest) latest = r.getTimestampMs();
            }
            final int backedUpCount = packages.size();
            final long latestMs = latest;

            mMainHandler.post(() -> bindOverview(backedUpCount, latestMs));
        });
    }

    private void bindOverview(int backedUpCount, long latestMs) {
        if (mLastBackupValue == null) return;

        if (backedUpCount == 0) {
            mLastBackupValue.setText("No Backup Yet");
        } else {
            mLastBackupValue.setText("Active • Last: " + mDateFormat.format(new Date(latestMs)));
        }
    }

    private String getDeviceMarketName() {
        String deviceName = Settings.Global.getString(
                getContentResolver(),
                Settings.Global.DEVICE_NAME
        );

        if (deviceName != null && !deviceName.trim().isEmpty()) {
            return deviceName;
        }

        String marketName = getSystemProperty("ro.product.marketname", "");
        if (!marketName.trim().isEmpty()) {
            return marketName;
        }

        String model = getSystemProperty("ro.product.model", Build.MODEL);

        String manufacturer = Build.MANUFACTURER;
        if (!model.toLowerCase().startsWith(manufacturer.toLowerCase())) {
            return capitalize(manufacturer) + " " + model;
        }

        return model;
    }

    private String getSystemProperty(String key, String defaultValue) {
        try {
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            Method get = systemProperties.getMethod("get", String.class, String.class);
            return (String) get.invoke(systemProperties, key, defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return "";
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
}
