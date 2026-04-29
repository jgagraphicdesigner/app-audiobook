package com.app.positivelygeared;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadService extends Service {

    private static final String TAG = "DownloadService";
    private static final String CHANNEL_ID = "positively_geared_download";
    private static final int NOTIF_ID = 1001;

    private ExecutorService executor;
    private NotificationManager notifManager;
    private int totalChapters = 11;
    private int downloadedCount = 0;
    private boolean isDownloading = false;

    public static final String BASE_URL = "https://raw.githubusercontent.com/jgagraphicdesigner/app-audiobook/main/audio/";

    public static final String[][] CHAPTERS = {
        {"1",  "chapter_1.mp3",  "Know Your Why",                        "47:24"},
        {"2",  "chapter_2.mp3",  "Strategy Session",                     "35:05"},
        {"3",  "chapter_3.mp3",  "Money, Money, Money",                  "44:37"},
        {"4",  "chapter_4.mp3",  "Location, Location, Location",         "42:09"},
        {"5",  "chapter_5.mp3",  "Property Market Cycle Myth Busting",   "58:46"},
        {"6",  "chapter_6.mp3",  "Buy Like a Buyer's Agent",             "38:48"},
        {"7",  "chapter_7.mp3",  "Winning Auctions",                     "45:46"},
        {"8",  "chapter_8.mp3",  "Duplex Developer: Pre-Build Plan",     "48:15"},
        {"9",  "chapter_9.mp3",  "Duplex Developer: Construction",       "53:14"},
        {"10", "chapter_10.mp3", "A Landlord's Toolkit",                 "34:54"},
        {"11", "chapter_11.mp3", "The Dream Team",                       "39:45"},
    };

    @Override
    public void onCreate() {
        super.onCreate();
        executor = Executors.newSingleThreadExecutor(); // Download one at a time
        notifManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isDownloading) {
            isDownloading = true;
            startForeground(NOTIF_ID, buildNotification("Checking audiobook files...", 0));
            executor.execute(this::downloadAllChapters);
        }
        return START_STICKY;
    }

    private void downloadAllChapters() {
        File audioDir = getAudioDir();
        if (!audioDir.exists()) audioDir.mkdirs();

        // Count already downloaded
        int alreadyDone = 0;
        for (String[] ch : CHAPTERS) {
            File f = new File(audioDir, ch[1]);
            if (f.exists() && f.length() > 1000000) alreadyDone++; // >1MB = valid
        }

        if (alreadyDone == totalChapters) {
            // All done, nothing to download
            updateNotification("All chapters ready for offline listening!", 100);
            stopSelf();
            return;
        }

        downloadedCount = alreadyDone;

        for (String[] ch : CHAPTERS) {
            File destFile = new File(audioDir, ch[1]);

            // Skip if already downloaded (valid file > 1MB)
            if (destFile.exists() && destFile.length() > 1000000) {
                downloadedCount++;
                updateNotification(
                    "Downloaded " + downloadedCount + "/" + totalChapters + " chapters",
                    (downloadedCount * 100) / totalChapters
                );
                continue;
            }

            // Download this chapter
            String chapterName = "Ch" + ch[0] + ": " + ch[2];
            updateNotification("Downloading " + chapterName + "...",
                (downloadedCount * 100) / totalChapters);

            boolean success = downloadFile(BASE_URL + ch[1], destFile);

            if (success) {
                downloadedCount++;
                updateNotification(
                    "Downloaded " + downloadedCount + "/" + totalChapters + " chapters",
                    (downloadedCount * 100) / totalChapters
                );
                Log.d(TAG, "Downloaded: " + ch[1] + " (" + destFile.length() / 1024 / 1024 + "MB)");
            } else {
                Log.e(TAG, "Failed to download: " + ch[1]);
            }
        }

        if (downloadedCount == totalChapters) {
            updateNotification("✓ All 11 chapters ready for offline listening!", 100);
        } else {
            updateNotification(downloadedCount + "/" + totalChapters + " chapters ready. Tap to retry.", 
                (downloadedCount * 100) / totalChapters);
        }

        isDownloading = false;
        stopForeground(false);
        stopSelf();
    }

    private boolean downloadFile(String urlStr, File dest) {
        // Use a temp file to avoid partial downloads
        File tempFile = new File(dest.getParent(), dest.getName() + ".tmp");

        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);
            conn.setRequestProperty("User-Agent", "PositivelyGearedApp/1.0");
            conn.connect();

            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "HTTP " + conn.getResponseCode() + " for " + urlStr);
                return false;
            }

            InputStream input = conn.getInputStream();
            FileOutputStream output = new FileOutputStream(tempFile);

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }

            output.flush();
            output.close();
            input.close();
            conn.disconnect();

            // Rename temp to final
            if (tempFile.exists() && tempFile.length() > 0) {
                tempFile.renameTo(dest);
                return true;
            }
            return false;

        } catch (IOException e) {
            Log.e(TAG, "Download error for " + urlStr + ": " + e.getMessage());
            if (tempFile.exists()) tempFile.delete();
            return false;
        }
    }

    private File getAudioDir() {
        return new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "PositivelyGeared");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Audiobook Downloads",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Downloads Positively Geared chapters for offline listening");
            notifManager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text, int progress) {
        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, openApp,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Positively Geared Audiobook")
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true);

        if (progress > 0 && progress < 100) {
            builder.setProgress(100, progress, false);
        } else if (progress == 100) {
            builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
                   .setProgress(0, 0, false)
                   .setOngoing(false);
        }

        return builder.build();
    }

    private void updateNotification(String text, int progress) {
        notifManager.notify(NOTIF_ID, buildNotification(text, progress));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executor != null) executor.shutdown();
    }
}
