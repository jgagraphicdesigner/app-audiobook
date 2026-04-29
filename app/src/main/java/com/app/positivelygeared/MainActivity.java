package com.app.positivelygeared;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private MediaPlayerService mediaService;
    private boolean serviceBound = false;

    private static final String PREF_CHAPTER  = "last_chapter";
    private static final String PREF_POSITION = "last_position";

    // Receives play/pause/next/prev from notification buttons
    private BroadcastReceiver controlReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            String action = intent.getStringExtra("action");
            if (action == null || webView == null) return;
            switch (action) {
                case "play":   webView.evaluateJavascript("if(!playing)togglePlay();", null); break;
                case "pause":  webView.evaluateJavascript("if(playing)togglePlay();", null); break;
                case "next":   webView.evaluateJavascript("nextChapter();", null); break;
                case "prev":   webView.evaluateJavascript("prevChapter();", null); break;
            }
        }
    };

    private ServiceConnection serviceConn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder b) {
            mediaService = ((MediaPlayerService.LocalBinder) b).getService();
            serviceBound = true;
        }
        @Override public void onServiceDisconnected(ComponentName n) {
            serviceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        progressBar = findViewById(R.id.progressBar);
        webView     = findViewById(R.id.webView);
        setupWebView();
        // Register notification button receiver
        IntentFilter filter = new IntentFilter("com.app.pg.WEBVIEW_CONTROL");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(controlReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(controlReceiver, filter);
        }
        // Bind to media service
        Intent serviceIntent = new Intent(this, MediaPlayerService.class);
        bindService(serviceIntent, serviceConn, Context.BIND_AUTO_CREATE);
    }

    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setAllowFileAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setDatabaseEnabled(true);

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
                "if(window.showResumePrompt) window.showResumePrompt(" + ch + "," + pos + ");", null);
        }
    }

    // ── JS Bridge ────────────────────────────────────────────────────────
    public class Bridge {

        // Called by JS when playback starts/changes
        @JavascriptInterface
        public void updatePlaybackState(String chapterTitle, String chapterNum, boolean isPlaying) {
            runOnUiThread(() -> {
                // Start / update notification
                Intent i = new Intent(MainActivity.this, MediaPlayerService.class);
                i.putExtra(MediaPlayerService.EXTRA_TITLE,   chapterTitle);
                i.putExtra(MediaPlayerService.EXTRA_CHAPTER, chapterNum);
                i.putExtra(MediaPlayerService.EXTRA_PLAYING, isPlaying);
                startService(i);
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

        @JavascriptInterface
        public String getResumeState() {
            android.content.SharedPreferences p = getSharedPreferences("prefs", MODE_PRIVATE);
            return "{\"chapter\":" + p.getInt(PREF_CHAPTER,-1) +
                   ",\"position\":" + p.getInt(PREF_POSITION,0) + "}";
        }
    }

    @Override public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(controlReceiver);
        if (serviceBound) { unbindService(serviceConn); serviceBound = false; }
    }
}
