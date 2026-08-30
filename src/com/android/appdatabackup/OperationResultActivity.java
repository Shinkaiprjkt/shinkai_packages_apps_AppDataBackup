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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;

import java.util.Locale;

/**
 * Animated "operation finished" screen shown after a backup or restore run
 * completes, in place of the old plain toast / failure-only dialog. Always
 * shown on completion (success or partial failure) so the person gets a
 * clear, celebratory confirmation of what just happened.
 */
public class OperationResultActivity extends Activity {

    public static final String EXTRA_TYPE = "type";
    public static final int TYPE_BACKUP = 0;
    public static final int TYPE_RESTORE = 1;

    public static final String EXTRA_LABELS = "labels";
    public static final String EXTRA_PACKAGES = "packages";
    public static final String EXTRA_SUCCESS = "success";
    public static final String EXTRA_MESSAGES = "messages";

    /** Result extra: which tab the caller should switch to, if "View details" was tapped. */
    public static final String EXTRA_GOTO_TAB = "goto_tab";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_operation_result);

        final int type = getIntent().getIntExtra(EXTRA_TYPE, TYPE_BACKUP);
        final String[] labels = getIntent().getStringArrayExtra(EXTRA_LABELS);
        final String[] packages = getIntent().getStringArrayExtra(EXTRA_PACKAGES);
        final boolean[] success = getIntent().getBooleanArrayExtra(EXTRA_SUCCESS);
        final String[] messages = getIntent().getStringArrayExtra(EXTRA_MESSAGES);
        final int n = labels != null ? labels.length : 0;

        int okCount = 0;
        for (int i = 0; i < n; i++) if (success != null && success[i]) okCount++;
        final int failCount = n - okCount;
        final boolean allOk = failCount == 0;

        final MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_back_24);
        toolbar.setNavigationOnClickListener(v -> finish());

        final ImageView badgeBg = findViewById(R.id.img_badge_bg);
        final ImageView badgeIcon = findViewById(R.id.img_badge_icon);
        final TextView title = findViewById(R.id.tv_result_title);
        final TextView subtitle = findViewById(R.id.tv_result_subtitle);
        final LinearLayout listContainer = findViewById(R.id.list_container);
        final MaterialButton viewDetailsBtn = findViewById(R.id.btn_view_details);
        final MaterialButton doneBtn = findViewById(R.id.btn_done);

        final int containerAttr = allOk
                ? com.google.android.material.R.attr.colorPrimaryContainer
                : com.google.android.material.R.attr.colorErrorContainer;
        final int onContainerAttr = allOk
                ? com.google.android.material.R.attr.colorOnPrimaryContainer
                : com.google.android.material.R.attr.colorOnErrorContainer;
        final int containerColor = themeColor(containerAttr);
        final int onContainerColor = themeColor(onContainerAttr);

        badgeBg.setImageTintList(ColorStateList.valueOf(containerColor));
        badgeIcon.setImageTintList(ColorStateList.valueOf(onContainerColor));

        if (allOk) {
            badgeIcon.setImageResource(R.drawable.avd_check_draw);
        } else {
            badgeIcon.setImageResource(R.drawable.ic_clear_24);
        }

        title.setText(titleFor(type, allOk));
        subtitle.setText(subtitleFor(type, okCount, failCount));

        toolbar.setTitle(titleFor(type, allOk));

        for (int i = 0; i < n; i++) {
            final View row = LayoutInflater.from(this)
                    .inflate(R.layout.item_operation_result, listContainer, false);
            final FrameLayout avatarBox = row.findViewById(R.id.avatar_box);
            final ImageView icon = row.findViewById(R.id.img_avatar);
            final TextView avatar = row.findViewById(R.id.tv_avatar);
            final TextView label = row.findViewById(R.id.tv_label);
            final ImageView status = row.findViewById(R.id.img_status);
            final TextView message = row.findViewById(R.id.tv_message);

            final String pkg = packages != null && i < packages.length ? packages[i] : "";
            bindAvatar(avatarBox, icon, avatar, labels[i], pkg);
            label.setText(labels[i]);

            final boolean rowOk = success != null && success[i];
            status.setImageResource(rowOk ? R.drawable.ic_check_24 : R.drawable.ic_clear_24);
            status.setImageTintList(ColorStateList.valueOf(
                    themeColor(rowOk ? com.google.android.material.R.attr.colorPrimary
                            : com.google.android.material.R.attr.colorError)));
            final String msg = messages != null && i < messages.length ? messages[i] : "";
            if (!rowOk && msg != null && !msg.isEmpty()) {
                message.setText(msg);
                message.setVisibility(View.VISIBLE);
            }
            listContainer.addView(row);
        }

        doneBtn.setOnClickListener(v -> finish());
        viewDetailsBtn.setOnClickListener(v -> {
            final Intent result = new Intent();
            result.putExtra(EXTRA_GOTO_TAB, type == TYPE_BACKUP
                    ? BackupManageActivity.TAB_BACKUPS : BackupManageActivity.TAB_APPS);
            setResult(RESULT_OK, result);
            finish();
        });

        playEntranceAnimation(badgeBg, badgeIcon, allOk);
    }

    private void playEntranceAnimation(ImageView badgeBg, ImageView badgeIcon, boolean allOk) {
        badgeBg.setScaleX(0f);
        badgeBg.setScaleY(0f);
        badgeBg.animate()
                .scaleX(1f).scaleY(1f)
                .setDuration(420)
                .setInterpolator(new OvershootInterpolator(2.2f))
                .start();

        final Drawable icon = badgeIcon.getDrawable();
        if (allOk && icon instanceof AnimatedVectorDrawable) {
            badgeIcon.setAlpha(0f);
            badgeIcon.animate().alpha(1f).setStartDelay(180).setDuration(120).withEndAction(
                    () -> ((AnimatedVectorDrawable) icon).start()).start();
        } else {
            badgeIcon.setScaleX(0f);
            badgeIcon.setScaleY(0f);
            badgeIcon.animate()
                    .scaleX(1f).scaleY(1f)
                    .setStartDelay(160)
                    .setDuration(300)
                    .setInterpolator(new OvershootInterpolator(2.5f))
                    .start();
        }
    }

    private String titleFor(int type, boolean allOk) {
        if (type == TYPE_BACKUP) {
            return getString(allOk ? R.string.result_title_backup_success
                    : R.string.result_title_backup_partial);
        }
        return getString(allOk ? R.string.result_title_restore_success
                : R.string.result_title_restore_partial);
    }

    private String subtitleFor(int type, int okCount, int failCount) {
        if (failCount > 0) {
            return getString(R.string.result_subtitle_with_failures, okCount, failCount);
        }
        if (type == TYPE_BACKUP) {
            return okCount == 1
                    ? getString(R.string.result_subtitle_backup_one)
                    : getString(R.string.result_subtitle_backup, okCount);
        }
        return okCount == 1
                ? getString(R.string.result_subtitle_restore_one)
                : getString(R.string.result_subtitle_restore, okCount);
    }

    private int themeColor(int attr) {
        return MaterialColors.getColor(this, attr, Color.MAGENTA);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void bindAvatar(FrameLayout box, ImageView icon, TextView initial,
            String label, String pkg) {
        final Drawable appIcon = loadAppIcon(pkg);
        if (appIcon != null) {
            box.setBackground(null);
            initial.setVisibility(View.GONE);
            icon.setImageDrawable(appIcon);
            icon.setVisibility(View.VISIBLE);
            return;
        }
        icon.setVisibility(View.GONE);
        icon.setImageDrawable(null);
        initial.setVisibility(View.VISIBLE);
        final GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(14));
        bg.setColor(themeColor(com.google.android.material.R.attr.colorPrimaryContainer));
        box.setBackground(bg);
        final String text = (label == null || label.isEmpty())
                ? "?" : label.substring(0, 1).toUpperCase(Locale.getDefault());
        initial.setText(text);
    }

    private Drawable loadAppIcon(String packageName) {
        if (packageName == null || packageName.isEmpty()) return null;
        try {
            return getPackageManager().getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }
}
