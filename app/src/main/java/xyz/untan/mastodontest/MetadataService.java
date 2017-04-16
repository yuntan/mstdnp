package xyz.untan.mastodontest;

import android.content.ComponentName;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static xyz.untan.mastodontest.Secrets.host;
import static xyz.untan.mastodontest.Secrets.token;


// How to get currently playing song's metadata on thirdparty music player
// Method1: IntentFilter
//   http://stackoverflow.com/a/17002415/2707413
//   http://stackoverflow.com/a/26376138/2707413
//   http://stackoverflow.com/a/14536732/2707413
// Method2: MediaSessionManager (Android 5+)
//   http://stackoverflow.com/a/27114050/2707413
// This app uses method2
public class MetadataService extends NotificationListenerService {
    private final String TAG = getClass().getSimpleName();
    private final Handler _timer = new Handler();
    private final Deque<TimerEntry> _timerStack = new ArrayDeque<>();
    private final String _tootFormat = "#nowplaying %s / %s / %s";
    private MediaMetadata _lastTootedMetadata;
    private RequestQueue _requestQueue;

    public MetadataService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "Service not started yet, initializing...");

        // http://stackoverflow.com/a/27114050/2707413
        MediaSessionManager mediaSessionManager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        ComponentName componentName = new ComponentName(this, MetadataService.class);
        mediaSessionManager.addOnActiveSessionsChangedListener(new ActiveSessionsChangedListener(), componentName);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.i(TAG, "finishing service...");
    }

    private void makeTootReservation(@NonNull MediaMetadata metadata) {
        _timerStack.push(new TimerEntry(new Date().getTime(), metadata));
//        _timer.purge();
//        _timer.schedule(new TimerTask() {
//            @Override
//            public void run() {
//                onTimer();
//            }
//        }, 15000);

        // remove all pending posts of runnable
        // https://developer.android.com/reference/android/os/Handler.html#removeCallbacksAndMessages(java.lang.Object)
        _timer.removeCallbacksAndMessages(null);

        // schedule runnable to be run after delay
        // https://developer.android.com/reference/android/os/Handler.html#postDelayed(java.lang.Runnable, long)
        _timer.postDelayed(new Runnable() {
            @Override
            public void run() {
                onTimer();
            }
        }, 15000);
    }

    private void cancelAllTootReservation() {
        _timer.removeCallbacksAndMessages(null);
        _timerStack.clear();
    }

    private void onTimer() {
        Log.i(TAG, "onTimer");

        // return null if stack is empty
        TimerEntry entry = _timerStack.poll();
        _timerStack.clear();
//        _timer.purge();
        _timer.removeCallbacksAndMessages(null);

        if (entry == null) {
            return;
        }
        MediaMetadata metadata = entry.metadata;

        Log.i(TAG, "  metadata.title: " + metadata.getText(MediaMetadata.METADATA_KEY_TITLE));
        if (_lastTootedMetadata != null)
            Log.i(TAG, "  last.title:     " + _lastTootedMetadata.getText(MediaMetadata.METADATA_KEY_TITLE));
        if ((_lastTootedMetadata != null && !metadata.getString(MediaMetadata.METADATA_KEY_TITLE).equals(_lastTootedMetadata.getString(MediaMetadata.METADATA_KEY_TITLE)))
                || _lastTootedMetadata == null) {
            toot(metadata);
            _lastTootedMetadata = metadata;
        }
    }

    private void toot(final MediaMetadata metadata) {
        Log.i(TAG, "tooting...");

        // https://developer.android.com/reference/android/media/MediaMetadata.html
        CharSequence album = metadata.getText(MediaMetadata.METADATA_KEY_ALBUM),
                artist = metadata.getText(MediaMetadata.METADATA_KEY_ARTIST),
                title = metadata.getText(MediaMetadata.METADATA_KEY_TITLE);
        if (artist == null) {
            artist = metadata.getText(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        }

        String status = String.format(_tootFormat, title, album, artist);
        // TODO player app name
        String url = null;
        try {
            url = "https://" + host + "/api/v1/statuses?status=" + URLEncoder.encode(status, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

//        RequestQueue queue = Volley.newRequestQueue(this);
        if (_requestQueue == null) {
            _requestQueue = Volley.newRequestQueue(this);
        }

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST, url, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.i(TAG, "toot done");
                        //_lastTootedMetadata = metadata;
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.i(TAG, "toot error");
                    }
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = super.getHeaders();
                // copy map
                headers = new HashMap<>(headers);
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };
        _requestQueue.add(request);
    }

    private class ActiveSessionsChangedListener implements MediaSessionManager.OnActiveSessionsChangedListener {
        @Override
        public void onActiveSessionsChanged(@Nullable List<MediaController> controllers) {
            Log.i(TAG, "onActiveSessionsChanged");
            if (controllers == null) {
                return;
            }

            Log.i(TAG, "  Active sessions:");
            for (MediaController controller : controllers) {
                Log.i(TAG, "    " + controller.getPackageName());
//                controller.unregisterCallback(_callback);
//                controller.registerCallback(_callback);
            }
            if (controllers.size() > 0) {
                MediaController controller = controllers.get(0);
                controller.registerCallback(new MediaControllerCallback(controller));

                MediaMetadata metadata = controller.getMetadata();

                if (metadata == null) {
                    return;
                }
                CharSequence album = metadata.getText(MediaMetadata.METADATA_KEY_ALBUM),
                        artist = metadata.getText(MediaMetadata.METADATA_KEY_ARTIST),
                        title = metadata.getText(MediaMetadata.METADATA_KEY_TITLE);
                if (artist == null) {
                    artist = metadata.getText(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
                }
                Log.i(TAG, String.format("  album: %s, artist: %s, title: %s", album, artist, title));

                PlaybackState playbackState = controller.getPlaybackState();
                int state = 0;
                if (playbackState != null) {
                    state = playbackState.getState();
                }
                switch (state) {
                    case PlaybackState.STATE_PLAYING:
                        Log.i(TAG, "  state: playing");
                        makeTootReservation(metadata);
                        break;
                    case PlaybackState.STATE_PAUSED:
                        Log.i(TAG, "  state: paused");
                        break;
                    case PlaybackState.STATE_STOPPED:
                        Log.i(TAG, "  state: stopped");
                        break;
                }
            }
        }
    }

    // https://developer.android.com/reference/android/media/session/MediaController.Callback.html
    private class MediaControllerCallback extends MediaController.Callback {
        private final MediaController _controller;

        MediaControllerCallback(MediaController controller) {
            _controller = controller;
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
            logMetadata(metadata);

            if (metadata != null) {
                makeTootReservation(metadata);
            }
        }

        @Override
        public void onPlaybackStateChanged(@NonNull PlaybackState state) {
            super.onPlaybackStateChanged(state);

            Log.i(TAG, "onPlaybackStateChanged");

            // https://developer.android.com/reference/android/media/session/PlaybackState.html
            int s = state.getState();
            switch (s) {
                case PlaybackState.STATE_PAUSED:
                    Log.i(TAG, "  -> paused");
                    cancelAllTootReservation();
                    break;
                case PlaybackState.STATE_PLAYING:
                    Log.i(TAG, "  -> playing");
                    break;
                case PlaybackState.STATE_STOPPED:
                    Log.i(TAG, "  -> stopped");
                    cancelAllTootReservation();
                    break;
            }

            MediaMetadata metadata = _controller.getMetadata();
            logMetadata(metadata);
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

        private void logMetadata(@Nullable MediaMetadata metadata) {
            if (metadata == null) {
                Log.i(TAG, "Failed to get metadata");
                return;
            }

            CharSequence album = metadata.getText(MediaMetadata.METADATA_KEY_ALBUM),
                    artist = metadata.getText(MediaMetadata.METADATA_KEY_ARTIST),
                    title = metadata.getText(MediaMetadata.METADATA_KEY_TITLE);
            if (artist == null) {
                artist = metadata.getText(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            }
            Log.i(TAG, String.format("  album: %s, artist: %s, title: %s", album, artist, title));
        }
    }

    private class TimerEntry {
        final long timestamp;
        final MediaMetadata metadata;

        TimerEntry(long timestamp, MediaMetadata metadata) {
            this.timestamp = timestamp;
            this.metadata = metadata;
        }
    }
}
