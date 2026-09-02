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
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Simple Material 3 dashboard: a couple of at-a-glance overview cards plus
 * quick action tiles that jump into {@link BackupManageActivity} for the
 * actual app selection / backup management work.
 */
public class AppDataBackupActivity extends Activity {

    private static final String TAG = "AppDataBackupUI";

    private AppDataBackupRestoreManager mManager;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final SimpleDateFormat mDateFormat =
            new SimpleDateFormat("MMM d, HH:mm", Locale.getDefault());

    private File mBackupDir;

    private TextView mLastBackupValue;
    private TextView mLastBackupTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        mManager = (AppDataBackupRestoreManager)
                getSystemService(APP_DATA_BACKUP_SERVICE);
        mBackupDir = new File("/data/media/" + UserHandle.myUserId() + "/AppDataBackup");

        mLastBackupValue = findViewById(R.id.tv_last_backup_value);
        mLastBackupTime = findViewById(R.id.tv_last_backup_time);

        final MaterialCardView backupTile = findViewById(R.id.card_action_backup);
        final MaterialCardView restoreTile = findViewById(R.id.card_action_restore);
        final MaterialCardView mediaTile = findViewById(R.id.card_action_media);
        backupTile.setOnClickListener(v -> openManage(BackupManageActivity.TAB_APPS));
        restoreTile.setOnClickListener(v -> openManage(BackupManageActivity.TAB_BACKUPS));
        mediaTile.setOnClickListener(v ->
                startActivity(new Intent(this, MediaBackupActivity.class)));
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
        if (backedUpCount == 0) {
            mLastBackupValue.setText(R.string.overview_last_backup_none);
            mLastBackupTime.setVisibility(View.GONE);
        } else {
            mLastBackupValue.setText(backedUpCount == 1
                    ? getString(R.string.overview_last_backup_summary_one)
                    : getString(R.string.overview_last_backup_summary, backedUpCount));
            mLastBackupTime.setText(getString(R.string.overview_last_backup_time,
                    mDateFormat.format(new Date(latestMs))));
            mLastBackupTime.setVisibility(View.VISIBLE);
        }
    }
}
