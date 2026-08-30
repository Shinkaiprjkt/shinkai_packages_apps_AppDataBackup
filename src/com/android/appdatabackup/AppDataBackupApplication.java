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

import android.app.Application;

import com.google.android.material.color.DynamicColors;

/**
 * Enables Android's dynamic (Monet) color system for every screen in this
 * app. {@link DynamicColors#applyToActivitiesIfAvailable(Application)}
 * registers an {@link android.app.Application.ActivityLifecycleCallbacks}
 * that wraps each {@link android.app.Activity}'s theme with an overlay
 * derived from the device's current wallpaper palette, replacing the
 * static {@code @color/md_*} roles defined in {@code themes.xml} with the
 * system accent/neutral tones (colorPrimary, colorPrimaryContainer,
 * colorSecondaryContainer, colorTertiaryContainer, colorSurface, etc.).
 *
 * This covers {@link AppDataBackupActivity} (dashboard) and
 * {@link BackupManageActivity} (Apps / Backups tabs) automatically — no
 * per-screen changes are needed, since both already source their colors
 * through Material3 theme attributes rather than hardcoded values.
 *
 * On devices below Android 12 (API 31), or if dynamic colors are
 * unavailable, this is a no-op and the static {@code md_*} palette in
 * {@code colors.xml} is used as-is.
 */
public class AppDataBackupApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
