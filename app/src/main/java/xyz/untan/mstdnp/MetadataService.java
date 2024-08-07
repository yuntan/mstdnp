package xyz.untan.mstdnp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.service.notification.NotificationListenerService;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.Volley;

import org.json.JSONObject;

import java.util.List;
import java.util.Set;


// How to get currently playing song's metadata on thirdparty music player
// Method1: IntentFilter
//   http://stackoverflow.com/a/17002415/2707413
//   http://stackoverflow.com/a/26376138/2707413
//   http://stackoverflow.com/a/14536732/2707413
// Method2: MediaSessionManager (Android 5+)
//   http://stackoverflow.com/a/27114050/2707413
// This app uses method2
public class MetadataService extends NotificationListenerService
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = MetadataService.class.getSimpleName();
    private final Handler _timer = new Handler();
    private final String _tootFormat = "#nowplaying %s / %s / %s (%s)";
    private String _lastToot;
    private RequestQueue _requestQueue;
    private MediaSessionListener _mediaSessionListener;
    private final int _notifyId = 1;
    private AppStatus _appStatus;

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "onCreate");
        Log.i(TAG, "Service not started yet, initializing...");

        // initialize members
        _requestQueue = Volley.newRequestQueue(this);
        _appStatus = new AppStatus();

        PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind");
        return super.onBind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "onUnbind");
        return super.onUnbind(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand");

        if (checkPermission()) {
            Log.i(TAG, "permission GRANTED");
            registerListener();
            startForeground(_notifyId, updateNotification());
            _appStatus.permissionCheckCompleted = true;
            _appStatus.save();
        } else {
            Log.i(TAG, "permission DENIED");
            _appStatus.permissionCheckCompleted = false;
            _appStatus.save();
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.i(TAG, "finishing service...");
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        updateNotification();
    }

    private boolean checkPermission() {
        // not works
        /*
        int check = ContextCompat.checkSelfPermission(this,
                Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE);

        switch (check) {
            case PackageManager.PERMISSION_GRANTED:
                return true;
            case PackageManager.PERMISSION_DENIED:
            default:
                return false;
        }
        */

        // http://stackoverflow.com/a/21392852/2707413
        boolean found = false;
        Set<String> set = NotificationManagerCompat.getEnabledListenerPackages(this);
        for (String packageName : set) {
            if (packageName.equals(getApplicationContext().getPackageName())) {
                found = true;
            }
        }
        return found;
    }

    private void registerListener() {
        // http://stackoverflow.com/a/27114050/2707413
        MediaSessionManager mediaSessionManager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        ComponentName componentName = new ComponentName(this, MetadataService.class);
        _mediaSessionListener = new MediaSessionListener();
        mediaSessionManager.addOnActiveSessionsChangedListener(_mediaSessionListener, componentName);
    }

    private Notification updateNotification() {
        boolean enabled = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(SettingsActivity.KEY_ENABLED, true);

        // show notification
        Notification.Builder builder = new Notification.Builder(this, "default")
                .setSmallIcon(enabled
                        ? R.drawable.ic_notification_enabled
                        : R.drawable.ic_notification_disabled)
                .setContentTitle(getText(enabled
                        ? R.string.notify_title_enabled
                        : R.string.notify_title_disabled))
                .setContentText(getText(R.string.notify_text));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder = builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }

        // tap to open settings activity
        Intent intent = new Intent(this, SettingsActivity.class);
        PendingIntent pendingIntent = TaskStackBuilder.create(this)
                .addNextIntent(intent)
                .getPendingIntent(
                        0,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
        builder = builder.setContentIntent(pendingIntent);

        // add button to enable/disable auto toot
        intent = new Intent(this, AutoTootPrefReceiver.class)
                .putExtra(enabled ? "disable" : "enable", true);
//        pendingIntent = TaskStackBuilder.create(this)
//                .addNextIntent(intent)
//                .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        pendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification.Action action = new Notification.Action.Builder(
                enabled ? R.drawable.ic_notification_disabled : R.drawable.ic_notification_enabled,
                getText(enabled ? R.string.action_disable : R.string.action_enable),
                pendingIntent
        ).build();
        builder = builder.addAction(action);

        Notification notification = builder.build();

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationChannel channel = new NotificationChannel(
                "default",
                "mstdnp toot posting service",
                NotificationManager.IMPORTANCE_LOW
        );
        notificationManager.createNotificationChannel(channel);

        notificationManager.notify(_notifyId, notification);
        return notification;
    }

    private void makeTootReservation(@NonNull MediaMetadata metadata, @NonNull final String packageName) {
        // remove all pending posts of runnable
        // https://developer.android.com/reference/android/os/Handler.html#removeCallbacksAndMessages(java.lang.Object)
        _timer.removeCallbacksAndMessages(null);

        // https://developer.android.com/reference/android/media/MediaMetadata.html
        String title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE),
                album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM),
                artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (artist == null) {
            artist = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        }

        if (album == null) {
            // no album data means it's a video metadata
            return;
        }

        // get player app name
        PackageManager packageManager = getApplicationContext().getPackageManager();
        String appName = "";
        try {
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
            appName = packageManager.getApplicationLabel(appInfo).toString();
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        final String status = String.format(_tootFormat, title, album, artist, appName);

        // schedule runnable to be run after delay
        // https://developer.android.com/reference/android/os/Handler.html#postDelayed(java.lang.Runnable, long)
        _timer.postDelayed(new Runnable() {
            @Override
            public void run() {
                // prevent overlapping toot
                if (_lastToot != null && status.equals(_lastToot)) {
                    return;
                }
                toot(status);
                _lastToot = status;
            }
        }, 15000);
    }

    private void cancelAllTootReservation() {
        _timer.removeCallbacksAndMessages(null);
    }

    private void toot(final String status) {
        SharedPreferences preference = PreferenceManager.getDefaultSharedPreferences(this);

        if (!preference.getBoolean(SettingsActivity.KEY_ENABLED, true)) {
            return; // auto toot not enabled
        }

        String scope = preference.getString(SettingsActivity.KEY_SCOPE, "unlisted");

        Log.i(TAG, "tooting...");

        MastodonApi.toot(_requestQueue, _appStatus.instanceHost, _appStatus.accessToken,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.i(TAG, "toot done");
                        // TODO custom style
                        Toast.makeText(MetadataService.this, R.string.message_toot_sent, Toast.LENGTH_SHORT).show();
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.i(TAG, "toot error");
                        // TODO custom style
                        Toast.makeText(MetadataService.this, R.string.error_toot_failed, Toast.LENGTH_SHORT).show();
                    }
                }, status, scope);

        // TODO custom toast style
        Toast.makeText(this, R.string.message_tooting, Toast.LENGTH_SHORT).show();
    }


    // https://developer.android.com/reference/android/media/session/MediaController.Callback.html
    private class MediaSessionListener extends MediaController.Callback
            implements MediaSessionManager.OnActiveSessionsChangedListener {
        private MediaController _controller;

        @Override
        public void onActiveSessionsChanged(@Nullable List<MediaController> controllers) {
            Log.i(TAG, "onActiveSessionsChanged");
            if (controllers == null) {
                return;
            }

            Log.i(TAG, "  Active sessions:");
            for (MediaController controller : controllers) {
                Log.i(TAG, "    " + controller.getPackageName());
            }

            if (controllers.size() > 0) {
                if (_controller != null) {
                    _controller.unregisterCallback(this);
                }
                _controller = controllers.get(0);
                _controller.registerCallback(this);

                Log.i(TAG, "  Current session: " + _controller.getPackageName());
                logState();

                MediaMetadata metadata = _controller.getMetadata();
                logMetadata(metadata);

                if (isPlaying() && metadata != null) {
                    makeTootReservation(metadata, _controller.getPackageName());
                } else {
                    cancelAllTootReservation();
                }
            }
        }

        @Override
        public void onAudioInfoChanged(MediaController.PlaybackInfo info) {
            super.onAudioInfoChanged(info);

            Log.i(TAG, "onAudioInfoChanged");
            logMetadata(_controller.getMetadata());
        }

        @Override
        public void onExtrasChanged(@Nullable Bundle extras) {
            super.onExtrasChanged(extras);

            Log.i(TAG, "onExtrasChanged");
            logMetadata(_controller.getMetadata());
        }

        @Override
        public void onMetadataChanged(@Nullable MediaMetadata metadata) {
            super.onMetadataChanged(metadata);

            Log.i(TAG, "onMetadataChanged");
            logState();
            logMetadata(metadata);

            if (isPlaying() && metadata != null) {
                makeTootReservation(metadata, _controller.getPackageName());
            } else {
                cancelAllTootReservation();
            }
        }

        @Override
        public void onPlaybackStateChanged(@NonNull PlaybackState state) {
            super.onPlaybackStateChanged(state);

            Log.i(TAG, "onPlaybackStateChanged");
            logState();

            MediaMetadata metadata = _controller.getMetadata();
            logMetadata(metadata);

            if (isPlaying() && metadata != null) {
                makeTootReservation(metadata, _controller.getPackageName());
            } else {
                cancelAllTootReservation();
            }
        }

        @Override
        public void onQueueChanged(@Nullable List<MediaSession.QueueItem> queue) {
            super.onQueueChanged(queue);

            Log.i(TAG, "onQueueChanged");
            logMetadata(_controller.getMetadata());
        }

        @Override
        public void onQueueTitleChanged(@Nullable CharSequence title) {
            super.onQueueTitleChanged(title);

            Log.i(TAG, "onQueueTitleChanged");
            Log.i(TAG, "  title: " + title);
            logMetadata(_controller.getMetadata());
        }

        @Override
        public void onSessionEvent(@NonNull String event, @Nullable Bundle extras) {
            super.onSessionEvent(event, extras);

            Log.i(TAG, "onSessionEvent event: " + event);
        }

        @Override
        public void onSessionDestroyed() {
            super.onSessionDestroyed();

            Log.i(TAG, "onSessionDestroyed");
//            _controller = null;
        }

        private boolean isPlaying() {
            if (_controller == null) {
                return false;
            }
            PlaybackState state = _controller.getPlaybackState();
            return state != null && state.getState() == PlaybackState.STATE_PLAYING;
        }

        private void logState() {
            if (_controller == null) {
                return;
            }
            PlaybackState state = _controller.getPlaybackState();
            if (state == null) {
                return;
            }

            // https://developer.android.com/reference/android/media/session/PlaybackState.html
            switch (state.getState()) {
                case PlaybackState.STATE_PAUSED:
                    Log.i(TAG, "  state: paused");
                    break;
                case PlaybackState.STATE_PLAYING:
                    Log.i(TAG, "  state: playing");
                    break;
                case PlaybackState.STATE_STOPPED:
                    Log.i(TAG, "  state: stopped");
                    break;
                default:
                    Log.i(TAG, "  state: other state");
                    break;
            }
        }
    }

    private static void logMetadata(@Nullable MediaMetadata metadata) {
        if (metadata == null) {
            Log.i(TAG, "  no metadata");
            return;
        }

        CharSequence album = metadata.getText(MediaMetadata.METADATA_KEY_ALBUM),
                artist = metadata.getText(MediaMetadata.METADATA_KEY_ARTIST),
                title = metadata.getText(MediaMetadata.METADATA_KEY_TITLE);
        if (artist == null) {
            artist = metadata.getText(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        }
        Log.i(TAG, String.format("  album: %s, artist: %s, album artist: %s, title: %s, display title: %s",
                metadata.getString(MediaMetadata.METADATA_KEY_ALBUM),
                metadata.getString(MediaMetadata.METADATA_KEY_ARTIST),
                metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
                metadata.getString(MediaMetadata.METADATA_KEY_TITLE),
                metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)));
    }
}
