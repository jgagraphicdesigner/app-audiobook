package com.app.positivelygeared;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private static final int NOTIF_PERM = 100;
    private static final String PREF_CHAPTER  = "last_chapter";
    private static final String PREF_POSITION = "last_position";

    // Receives control actions from notification buttons → sends to WebView JS
    private final BroadcastReceiver controlReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            if (webView == null) return;
            String action = intent.getStringExtra("action");
            if (action == null) return;
            runOnUiThread(() -> {
                switch (action) {
                    case "play":
                        webView.evaluateJavascript(
                            "(function(){ if(!window.playing){ togglePlay(); } })()", null);
                        break;
                    case "pause":
                        webView.evaluateJavascript(
                            "(function(){ if(window.playing){ togglePlay(); } })()", null);
                        break;
                    case "next":
                        webView.evaluateJavascript("nextChapter();", null);
                        break;
                    case "prev":
                        webView.evaluateJavascript("prevChapter();", null);
                        break;
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        progressBar = findViewById(R.id.progressBar);
        webView     = findViewById(R.id.webView);
        setupWebView();
        requestNotifPermission();
        registerReceiver();
    }

    private void requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIF_PERM);
            }
        }
    }

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter("pg.WEBVIEW_CONTROL");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(controlReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(controlReceiver, filter);
        }
    }

    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setAllowFileAccess(true);
        s.setDatabaseEnabled(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        webView.addJavascriptInterface(new Bridge(), "AndroidBridge");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView v, int p) {
                progressBar.setVisibility(p < 100 ? View.VISIBLE : View.GONE);
                progressBar.setProgress(p);
                if (p == 100) injectResumeState();
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String url = r.getUrl().toString();
                if (!url.contains("jgagraphicdesigner.github.io") &&
                    !url.contains("auspropertyprofessionals.com.au") &&
                    !url.startsWith("file://")) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    return true;
                }
                return false;
            }
        });

        webView.loadUrl("https://jgagraphicdesigner.github.io/app-audiobook/");
    }

    private void injectResumeState() {
        android.content.SharedPreferences p = getSharedPreferences("prefs", MODE_PRIVATE);
        int ch  = p.getInt(PREF_CHAPTER, -1);
        int pos = p.getInt(PREF_POSITION, 0);
        if (ch > 0 && pos > 5) {
            webView.evaluateJavascript(
                "if(window.showResumePrompt)window.showResumePrompt(" + ch + "," + pos + ");", null);
        }
    }

    public class Bridge {
        // Called by JS when chapter starts playing or pauses
        @JavascriptInterface
        public void updatePlaybackState(String chapterTitle, String chapterNum, boolean isPlaying) {
            runOnUiThread(() -> {
                Intent i = new Intent(MainActivity.this, MediaPlayerService.class);
                i.putExtra(MediaPlayerService.EXTRA_CHAPTER, chapterNum + ": " + chapterTitle);
                i.putExtra(MediaPlayerService.EXTRA_TITLE,   "Positively Geared Audiobook");
                i.putExtra(MediaPlayerService.EXTRA_PLAYING, isPlaying);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(i);
                } else {
                    startService(i);
                }
            });
        }

        @JavascriptInterface
        public void savePlaybackPosition(int ch, int pos) {
            getSharedPreferences("prefs", MODE_PRIVATE).edit()
                .putInt(PREF_CHAPTER, ch).putInt(PREF_POSITION, pos).apply();
        }

        @JavascriptInterface
        public void clearPlaybackPosition() {
            getSharedPreferences("prefs", MODE_PRIVATE).edit()
                .remove(PREF_CHAPTER).remove(PREF_POSITION).apply();
        }
    }

    @Override public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(controlReceiver); } catch (Exception ignored) {}
    }
}
