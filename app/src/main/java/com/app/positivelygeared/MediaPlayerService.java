package com.app.positivelygeared;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

public class MediaPlayerService extends Service {

    public static final String CHANNEL_ID    = "pg_player";
    public static final int    NOTIF_ID      = 2001;

    public static final String ACTION_PLAY   = "com.app.pg.PLAY";
    public static final String ACTION_PAUSE  = "com.app.pg.PAUSE";
    public static final String ACTION_NEXT   = "com.app.pg.NEXT";
    public static final String ACTION_PREV   = "com.app.pg.PREV";
    public static final String ACTION_STOP   = "com.app.pg.STOP";

    public static final String EXTRA_TITLE   = "title";
    public static final String EXTRA_CHAPTER = "chapter";
    public static final String EXTRA_PLAYING = "playing";

    private String currentTitle   = "Positively Geared";
    private String currentChapter = "Chapter 1";
    private boolean isPlaying     = false;

    private final IBinder binder = new LocalBinder();
    private BroadcastReceiver controlReceiver;

    public class LocalBinder extends Binder {
        MediaPlayerService getService() { return MediaPlayerService.this; }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        registerControlReceiver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case ACTION_PLAY:
                    case ACTION_PAUSE:
                        isPlaying = !isPlaying;
                        sendControlToWebView(isPlaying ? "play" : "pause");
                        break;
                    case ACTION_NEXT:
                        sendControlToWebView("next");
                        break;
                    case ACTION_PREV:
                        sendControlToWebView("prev");
                        break;
                    case ACTION_STOP:
                        isPlaying = false;
                        stopForeground(true);
                        stopSelf();
                        return START_NOT_STICKY;
                }
            }
            // Update state from intent
            if (intent.hasExtra(EXTRA_TITLE))   currentTitle   = intent.getStringExtra(EXTRA_TITLE);
            if (intent.hasExtra(EXTRA_CHAPTER)) currentChapter = intent.getStringExtra(EXTRA_CHAPTER);
            if (intent.hasExtra(EXTRA_PLAYING)) isPlaying      = intent.getBooleanExtra(EXTRA_PLAYING, false);
        }

        startForeground(NOTIF_ID, buildNotification());
        return START_STICKY;
    }

    private void sendControlToWebView(String action) {
        // Broadcast to MainActivity which forwards to WebView JS
        Intent i = new Intent("com.app.pg.WEBVIEW_CONTROL");
        i.putExtra("action", action);
        sendBroadcast(i);
        updateNotification();
    }

    public void updateState(String title, String chapter, boolean playing) {
        this.currentTitle   = title;
        this.currentChapter = chapter;
        this.isPlaying      = playing;
        updateNotification();
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIF_ID, buildNotification());
    }

    private Notification buildNotification() {
        // Open app intent
        Intent openApp = new Intent(this, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPI = PendingIntent.getActivity(this, 0, openApp,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Prev
        PendingIntent prevPI = PendingIntent.getService(this, 1,
            new Intent(this, MediaPlayerService.class).setAction(ACTION_PREV),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Play/Pause
        PendingIntent playPI = PendingIntent.getService(this, 2,
            new Intent(this, MediaPlayerService.class).setAction(isPlaying ? ACTION_PAUSE : ACTION_PLAY),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Next
        PendingIntent nextPI = PendingIntent.getService(this, 3,
            new Intent(this, MediaPlayerService.class).setAction(ACTION_NEXT),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        int playIcon = isPlaying
            ? android.R.drawable.ic_media_pause
            : android.R.drawable.ic_media_play;

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(currentChapter)
            .setContentText(currentTitle)
            .setSubText("Positively Geared Audiobook")
            .setContentIntent(openPI)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            // Media style — like Spotify
            .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2))
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPI)
            .addAction(playIcon, isPlaying ? "Pause" : "Play", playPI)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPI)
            .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Audiobook Player", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Shows playback controls for Positively Geared");
            channel.setShowBadge(false);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                .createNotificationChannel(channel);
        }
    }

    private void registerControlReceiver() {
        controlReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) {
                // No-op here; MainActivity handles WebView control
            }
        };
        IntentFilter filter = new IntentFilter("com.app.pg.WEBVIEW_CONTROL");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(controlReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(controlReceiver, filter);
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (controlReceiver != null) unregisterReceiver(controlReceiver);
    }
}
