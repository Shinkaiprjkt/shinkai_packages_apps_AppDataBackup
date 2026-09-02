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

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lets the user browse photos and videos on the device (via MediaStore) and
 * copy a selected subset into the same backup directory tree used by the
 * app-data backup flow, under a "Media/Photos" and "Media/Videos" split.
 *
 * Photos and videos are shown in a single RecyclerView with two view types
 * (section header, media thumbnail) so the grid can hold thousands of items
 * without measuring itself as one giant wrap_content view — that was the
 * earlier bug: two separate wrap_content RecyclerViews nested in a
 * ScrollView tried to lay out every item up front, which broke down once
 * the photo library got into the thousands and starved the video section
 * of any layout space at all.
 */
public class MediaBackupActivity extends Activity {

    private static final String TAG = "MediaBackupUI";
    private static final int SPAN_COUNT = 3;
    private static final int REQ_MEDIA_PERMISSIONS = 100;
    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_MEDIA = 1;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private File mMediaBackupDir;

    private RecyclerView mRvMedia;
    private TextView mTvSelectedCount;
    private MaterialButton mBtnBackup;
    private MaterialCardView mProgressCard;
    private LinearProgressIndicator mProgressBar;
    private TextView mTvProgress;

    private List<MediaItem> mPhotos = new ArrayList<>();
    private List<MediaItem> mVideos = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_backup);

        mMediaBackupDir = new File(
                "/storage/emulated/" + UserHandle.myUserId() + "/AppDataBackup/Media");

        final MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        mRvMedia = findViewById(R.id.rv_media);
        mTvSelectedCount = findViewById(R.id.tv_selected_count);
        mBtnBackup = findViewById(R.id.btn_backup_media);
        mProgressCard = findViewById(R.id.card_backup_progress);
        mProgressBar = findViewById(R.id.progress_media_backup);
        mTvProgress = findViewById(R.id.tv_media_backup_progress);

        mBtnBackup.setOnClickListener(v -> startBackup());

        for (String perm : requiredPermissions()) {
            Log.i(TAG, perm + " granted="
                    + (checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED));
        }
        if (hasMediaPermissions()) {
            loadMediaAsync();
        } else {
            requestPermissions(requiredPermissions(), REQ_MEDIA_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MEDIA_PERMISSIONS) {
            if (hasMediaPermissions()) {
                loadMediaAsync();
            } else {
                Toast.makeText(this, "Izin akses media ditolak", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private String[] requiredPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            return new String[]{
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO};
        }
        return new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
    }

    private boolean hasMediaPermissions() {
        for (String perm : requiredPermissions()) {
            if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void loadMediaAsync() {
        mExecutor.submit(() -> {
            final List<MediaItem> photos = queryMedia(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_ADDED);
            final List<MediaItem> videos = queryMedia(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.DATE_ADDED);
            Log.i(TAG, "Query result: " + photos.size() + " photos, "
                    + videos.size() + " videos");
            mMainHandler.post(() -> bindMedia(photos, videos));
        });
    }

    private List<MediaItem> queryMedia(Uri collection, String idColumn, String nameColumn,
            String dateColumn) {
        final List<MediaItem> list = new ArrayList<>();
        final String[] projection = {idColumn, nameColumn};
        try (Cursor cursor = getContentResolver().query(collection, projection, null, null,
                dateColumn + " DESC")) {
            if (cursor != null) {
                final int idIdx = cursor.getColumnIndexOrThrow(idColumn);
                final int nameIdx = cursor.getColumnIndexOrThrow(nameColumn);
                while (cursor.moveToNext()) {
                    final long id = cursor.getLong(idIdx);
                    final String name = cursor.getString(nameIdx);
                    final Uri uri = ContentUris.withAppendedId(collection, id);
                    list.add(new MediaItem(uri, name));
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to query " + collection, e);
        }
        return list;
    }

    private void bindMedia(List<MediaItem> photos, List<MediaItem> videos) {
        mPhotos = photos;
        mVideos = videos;

        final List<ListEntry> entries = new ArrayList<>();
        if (!photos.isEmpty()) {
            entries.add(ListEntry.header("Foto"));
            for (MediaItem item : photos) entries.add(ListEntry.media(item));
        }
        if (!videos.isEmpty()) {
            entries.add(ListEntry.header("Video"));
            for (MediaItem item : videos) entries.add(ListEntry.media(item));
        }
        if (entries.isEmpty()) {
            entries.add(ListEntry.header("Tidak ada foto maupun video ditemukan"));
        }

        final GridLayoutManager lm = new GridLayoutManager(this, SPAN_COUNT);
        lm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return entries.get(position).viewType == VIEW_TYPE_HEADER ? SPAN_COUNT : 1;
            }
        });
        mRvMedia.setLayoutManager(lm);
        mRvMedia.setAdapter(new MediaAdapter(entries));

        updateSelectionSummary();
    }

    private void updateSelectionSummary() {
        int count = 0;
        for (MediaItem item : mPhotos) if (item.selected) count++;
        for (MediaItem item : mVideos) if (item.selected) count++;
        mTvSelectedCount.setText(count + " dipilih");
        mBtnBackup.setEnabled(count > 0);
    }

    private void startBackup() {
        final List<MediaItem> selectedPhotos = new ArrayList<>();
        final List<MediaItem> selectedVideos = new ArrayList<>();
        for (MediaItem item : mPhotos) if (item.selected) selectedPhotos.add(item);
        for (MediaItem item : mVideos) if (item.selected) selectedVideos.add(item);

        final int total = selectedPhotos.size() + selectedVideos.size();
        if (total == 0) {
            return;
        }

        mProgressCard.setVisibility(View.VISIBLE);
        mBtnBackup.setEnabled(false);
        mProgressBar.setMax(total);
        mProgressBar.setProgressCompat(0, false);
        mTvProgress.setText("Menyalin 0 dari " + total + " berkas...");

        mExecutor.submit(() -> {
            final File photoDir = new File(mMediaBackupDir, "Photos");
            final File videoDir = new File(mMediaBackupDir, "Videos");
            photoDir.mkdirs();
            videoDir.mkdirs();
            Log.i(TAG, "photoDir=" + photoDir + " exists=" + photoDir.exists()
                    + " canWrite=" + photoDir.canWrite());
            Log.i(TAG, "videoDir=" + videoDir + " exists=" + videoDir.exists()
                    + " canWrite=" + videoDir.canWrite());

            int done = 0;
            int failed = 0;
            for (MediaItem item : selectedPhotos) {
                if (!copyMediaItem(item, photoDir)) failed++;
                done++;
                final int progress = done;
                mMainHandler.post(() -> updateBackupProgress(progress, total));
            }
            for (MediaItem item : selectedVideos) {
                if (!copyMediaItem(item, videoDir)) failed++;
                done++;
                final int progress = done;
                mMainHandler.post(() -> updateBackupProgress(progress, total));
            }
            final int finalFailed = failed;
            mMainHandler.post(() -> onBackupFinished(total, finalFailed));
        });
    }

    private boolean copyMediaItem(MediaItem item, File destDir) {
        final File destFile = new File(destDir, item.displayName);
        try (InputStream in = getContentResolver().openInputStream(item.uri);
                OutputStream out = new FileOutputStream(destFile)) {
            if (in == null) {
                Log.w(TAG, "openInputStream returned null for " + item.uri);
                return false;
            }
            final byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return true;
        } catch (IOException e) {
            Log.w(TAG, "Failed to copy " + item.displayName + " to " + destFile, e);
            return false;
        }
    }

    private void updateBackupProgress(int done, int total) {
        mProgressBar.setProgressCompat(done, true);
        mTvProgress.setText("Menyalin " + done + " dari " + total + " berkas...");
    }

    private void onBackupFinished(int total, int failed) {
        mProgressCard.setVisibility(View.GONE);
        mBtnBackup.setEnabled(true);
        if (failed == 0) {
            Toast.makeText(this, "Pencadangan media selesai (" + total + " berkas)",
                    Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, failed + " dari " + total
                    + " berkas gagal disalin \u2014 cek logcat tag " + TAG,
                    Toast.LENGTH_LONG).show();
        }
    }

    /** One selectable photo or video entry. */
    private static final class MediaItem {
        final Uri uri;
        final String displayName;
        boolean selected;

        MediaItem(Uri uri, String displayName) {
            this.uri = uri;
            this.displayName = displayName;
        }
    }

    /** One row in the combined list: either a section header or a media thumbnail. */
    private static final class ListEntry {
        final int viewType;
        final String headerText;
        final MediaItem item;

        private ListEntry(int viewType, String headerText, MediaItem item) {
            this.viewType = viewType;
            this.headerText = headerText;
            this.item = item;
        }

        static ListEntry header(String text) {
            return new ListEntry(VIEW_TYPE_HEADER, text, null);
        }

        static ListEntry media(MediaItem item) {
            return new ListEntry(VIEW_TYPE_MEDIA, null, item);
        }
    }

    /** Single adapter backing the whole screen: section headers + thumbnail grid cells. */
    private final class MediaAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final List<ListEntry> entries;

        MediaAdapter(List<ListEntry> entries) {
            this.entries = entries;
        }

        @Override
        public int getItemViewType(int position) {
            return entries.get(position).viewType;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if (viewType == VIEW_TYPE_HEADER) {
                final View v = getLayoutInflater().inflate(
                        R.layout.item_media_header, parent, false);
                return new HeaderViewHolder(v);
            }
            final View v = getLayoutInflater().inflate(
                    R.layout.item_media_thumb, parent, false);
            return new ThumbViewHolder(v);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            final ListEntry entry = entries.get(position);

            if (holder instanceof HeaderViewHolder) {
                ((HeaderViewHolder) holder).title.setText(entry.headerText);
                return;
            }

            final ThumbViewHolder h = (ThumbViewHolder) holder;
            final MediaItem item = entry.item;

            h.thumb.setImageDrawable(null);
            h.thumb.setTag(item.uri);
            mExecutor.submit(() -> {
                Bitmap bmp = null;
                try {
                    bmp = getContentResolver()
                            .loadThumbnail(item.uri, new Size(200, 200), null);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to load thumbnail for " + item.uri, e);
                }
                final Bitmap finalBmp = bmp;
                mMainHandler.post(() -> {
                    if (finalBmp != null && item.uri.equals(h.thumb.getTag())) {
                        h.thumb.setImageBitmap(finalBmp);
                    }
                });
            });

            final int visibility = item.selected ? View.VISIBLE : View.GONE;
            h.overlay.setVisibility(visibility);
            h.check.setVisibility(visibility);

            h.itemView.setOnClickListener(v -> {
                final int pos = h.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                final ListEntry clicked = entries.get(pos);
                if (clicked.viewType != VIEW_TYPE_MEDIA) return;
                clicked.item.selected = !clicked.item.selected;
                notifyItemChanged(pos);
                updateSelectionSummary();
            });
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }

        final class HeaderViewHolder extends RecyclerView.ViewHolder {
            final TextView title;

            HeaderViewHolder(View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.tv_header_title);
            }
        }

        final class ThumbViewHolder extends RecyclerView.ViewHolder {
            final ImageView thumb;
            final View overlay;
            final View check;

            ThumbViewHolder(View itemView) {
                super(itemView);
                thumb = itemView.findViewById(R.id.iv_thumb);
                overlay = itemView.findViewById(R.id.iv_selected_overlay);
                check = itemView.findViewById(R.id.iv_selected_check);
            }
        }
    }
}
