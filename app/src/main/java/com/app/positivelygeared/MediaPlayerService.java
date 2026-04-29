package com.app.positivelygeared;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

public class MediaPlayerService extends Service {

    public static final String CHANNEL_ID    = "pg_player_v2";
    public static final int    NOTIF_ID      = 3001;
    public static final String ACTION_PLAY   = "pg.PLAY";
    public static final String ACTION_PAUSE  = "pg.PAUSE";
    public static final String ACTION_NEXT   = "pg.NEXT";
    public static final String ACTION_PREV   = "pg.PREV";
    public static final String EXTRA_TITLE   = "title";
    public static final String EXTRA_CHAPTER = "chapter";
    public static final String EXTRA_PLAYING = "playing";

    private String  title   = "Positively Geared";
    private String  chapter = "Audiobook";
    private boolean playing = false;

    private MediaSessionCompat mediaSession;
    private NotificationManager notifManager;

    @Override
    public void onCreate() {
        super.onCreate();
        notifManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createChannel();
        setupMediaSession();
    }

    private void setupMediaSession() {
        mediaSession = new MediaSessionCompat(this, "PGAudiobook");
        mediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override public void onPlay()             { sendControl("play"); }
            @Override public void onPause()            { sendControl("pause"); }
            @Override public void onSkipToNext()       { sendControl("next"); }
            @Override public void onSkipToPrevious()   { sendControl("prev"); }
        });
        mediaSession.setActive(true);
    }

    private void sendControl(String action) {
        Intent i = new Intent("pg.WEBVIEW_CONTROL");
        i.putExtra("action", action);
        sendBroadcast(i);
        if (action.equals("play"))  playing = true;
        if (action.equals("pause")) playing = false;
        notifManager.notify(NOTIF_ID, buildNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case ACTION_PLAY:  playing = true;  sendControl("play");  break;
                    case ACTION_PAUSE: playing = false; sendControl("pause"); break;
                    case ACTION_NEXT:  sendControl("next"); break;
                    case ACTION_PREV:  sendControl("prev"); break;
                }
            }
            if (intent.hasExtra(EXTRA_TITLE))   title   = intent.getStringExtra(EXTRA_TITLE);
            if (intent.hasExtra(EXTRA_CHAPTER)) chapter = intent.getStringExtra(EXTRA_CHAPTER);
            if (intent.hasExtra(EXTRA_PLAYING)) playing = intent.getBooleanExtra(EXTRA_PLAYING, false);
        }

        // Update MediaSession
        mediaSession.setMetadata(new MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE,  chapter)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Lloyd Edge")
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM,  "Positively Geared")
            .build());

        mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
            .setState(
                playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            .setActions(
                PlaybackStateCompat.ACTION_PLAY |
                PlaybackStateCompat.ACTION_PAUSE |
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
            .build());

        startForeground(NOTIF_ID, buildNotification());
        return START_STICKY;
    }

    private Notification buildNotification() {
        Intent openApp = new Intent(this, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPI = PendingIntent.getActivity(this, 0, openApp,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent prevPI = PendingIntent.getService(this, 1,
            new Intent(this, MediaPlayerService.class).setAction(ACTION_PREV),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent playPI = PendingIntent.getService(this, 2,
            new Intent(this, MediaPlayerService.class)
                .setAction(playing ? ACTION_PAUSE : ACTION_PLAY),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent nextPI = PendingIntent.getService(this, 3,
            new Intent(this, MediaPlayerService.class).setAction(ACTION_NEXT),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(chapter)
            .setContentText("Positively Geared · Lloyd Edge")
            .setContentIntent(openPI)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(playing)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setStyle(new MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2))
            .addAction(android.R.drawable.ic_media_previous, "Prev", prevPI)
            .addAction(
                playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                playing ? "Pause" : "Play", playPI)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPI)
            .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Audiobook Player", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Positively Geared playback controls");
            ch.setShowBadge(false);
            ch.setSound(null, null);
            notifManager.createNotificationChannel(ch);
        }
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
    }
}
