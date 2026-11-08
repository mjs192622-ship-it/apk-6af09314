package com.brokentheatretest20.app;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.View;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.widget.ProgressBar;
import android.app.DownloadManager;
import android.net.Uri;
import android.os.Environment;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.widget.Toast;
import android.Manifest;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.os.Build;
import android.widget.Toast;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.view.Menu;
import android.view.MenuItem;
import android.app.AlertDialog;
import android.net.Uri;
import android.webkit.ValueCallback;
import android.provider.MediaStore;
import android.os.Environment;
import android.content.ContentValues;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

public class MainActivity extends androidx.appcompat.app.AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private String fcmTokenForWebView = "";
    private ProgressBar progressBarCircular;
    private static final String WEBSITE_URL = "https://gamejolt.com";
    private SwipeRefreshLayout swipeRefresh;
    private ValueCallback<Uri[]> fileUploadCallback;
    private Uri cameraImageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        progressBarCircular = findViewById(R.id.progressBarCircular);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setColorSchemeColors(Color.parseColor("#FFFFFF"));
        swipeRefresh.setOnRefreshListener(() -> {
            webView.reload();
        });
        checkAndShowRateDialog(3);
        
        setupWebView();
        
        // Enable Service Worker support (API 24+)
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            ServiceWorkerController swController = ServiceWorkerController.getInstance();
            swController.setServiceWorkerClient(new ServiceWorkerClient() {
                @Override
                public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                    return null;
                }
            });
        }

        // Request POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }

        // Explicitly fetch and send FCM token at app startup
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        final String fcmToken = task.getResult();
                        fcmTokenForWebView = fcmToken;
                        android.util.Log.d("FCM_TOKEN", "Token received: " + fcmToken);

                        // Show token status via JavaScript in WebView (debug)
                        runOnUiThread(() -> {
                            if (webView != null) {
                                webView.evaluateJavascript(
                                    "window.FCM_TOKEN = '" + fcmToken + "'; window.dispatchEvent(new Event('fcm_token_ready')); console.log('FCM Token set');", null);
                            }
                        });

                        new Thread(() -> {
                            try {
                                // Endpoint is generated by PHP based on Website URL:
                                // User app  => /fcm_token.php
                                // Admin app => /admin/fcm_token.php
                                String endpoint = "https://gamejolt.com/fcm_token.php";
                                android.util.Log.d("FCM_TOKEN", "Sending token to: " + endpoint);

                                java.net.URL tokenUrl = new java.net.URL(endpoint);
                                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) tokenUrl.openConnection();
                                conn.setRequestMethod("POST");
                                conn.setRequestProperty("Content-Type", "application/json");
                                conn.setRequestProperty("User-Agent", "BPWalletApp/1.0");
                                conn.setDoOutput(true);
                                conn.setConnectTimeout(15000);
                                conn.setReadTimeout(15000);
                                String body = "{\"token\":\"" + fcmToken + "\"}";
                                try (java.io.OutputStream os = conn.getOutputStream()) {
                                    os.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                                }
                                int status = conn.getResponseCode();
                                android.util.Log.d("FCM_TOKEN", "POST response: HTTP " + status);
                                conn.disconnect();

                                // Fallback: GET request if POST fails
                                if (status < 200 || status >= 300) {
                                    android.util.Log.d("FCM_TOKEN", "POST failed, trying GET fallback...");
                                    String t = java.net.URLEncoder.encode(fcmToken, "UTF-8");
                                    java.net.URL fallbackUrl = new java.net.URL(endpoint + "?token=" + t);
                                    java.net.HttpURLConnection fallbackConn = (java.net.HttpURLConnection) fallbackUrl.openConnection();
                                    fallbackConn.setRequestMethod("GET");
                                    fallbackConn.setRequestProperty("User-Agent", "BPWalletApp/1.0");
                                    fallbackConn.setConnectTimeout(15000);
                                    fallbackConn.setReadTimeout(15000);
                                    int fbStatus = fallbackConn.getResponseCode();
                                    android.util.Log.d("FCM_TOKEN", "GET fallback response: HTTP " + fbStatus);
                                    fallbackConn.disconnect();
                                }
                            } catch (Exception e) {
                                android.util.Log.e("FCM_TOKEN", "Error sending token: " + e.getMessage(), e);
                            }
                        }).start();
                    } else {
                        android.util.Log.e("FCM_TOKEN", "Failed to get token: " + (task.getException() != null ? task.getException().getMessage() : "unknown error"));
                        // Show error as Toast so user can see
                        runOnUiThread(() -> {
                            String errMsg = task.getException() != null ? task.getException().getMessage() : "Unknown FCM error";
                            Toast.makeText(MainActivity.this, "FCM Error: " + errMsg, Toast.LENGTH_LONG).show();
                        });
                    }
                });
        } catch (Exception e) {
            android.util.Log.e("FCM_TOKEN", "Firebase init error: " + e.getMessage(), e);
            Toast.makeText(this, "Firebase Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        // Schedule background notification polling every 15 minutes
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
            NotificationWorker.class, 15, TimeUnit.MINUTES)
            .build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "notification_check",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest);
        
        
        
        // Handle deep link intent
        handleIntent(getIntent());
        
        // Load directly; ConnectivityManager can be unreliable on some devices/VPNs.
        // WebView will show its own error page if the connection is actually unavailable.
        webView.loadUrl(WEBSITE_URL);
    }

    private void checkAndShowRateDialog(int targetLaunches) {
        android.content.SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        boolean ratedAlready = prefs.getBoolean("rated", false);
        if (ratedAlready) return;
        int launches = prefs.getInt("launch_count", 0) + 1;
        prefs.edit().putInt("launch_count", launches).apply();
        if (launches == targetLaunches) {
            new AlertDialog.Builder(this)
                .setTitle("Enjoying the app?")
                .setMessage("If you like the app, please take a moment to rate it. It won't take more than a minute. Thanks!")
                .setPositiveButton("Rate Now", (d, w) -> {
                    prefs.edit().putBoolean("rated", true).apply();
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + getPackageName())));
                    } catch (android.content.ActivityNotFoundException e) {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName())));
                    }
                })
                .setNeutralButton("Remind Later", null)
                .setNegativeButton("No Thanks", (d, w) -> prefs.edit().putBoolean("rated", true).apply())
                .show();
        }
    }

    private void setupWebView() {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setBuiltInZoomControls(false);
        webSettings.setDisplayZoomControls(false);
        webSettings.setSupportZoom(false);
        webSettings.setDefaultTextEncodingName("utf-8");
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setLoadsImagesAutomatically(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setDatabaseEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        android.webkit.CookieManager.getInstance().setAcceptCookie(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
                if (progressBarCircular != null) progressBarCircular.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                if (progressBarCircular != null) progressBarCircular.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                // Save cookies for background notification worker
                String cookies = CookieManager.getInstance().getCookie(WEBSITE_URL);
                if (cookies != null && !cookies.isEmpty()) {
                    getSharedPreferences("bp_prefs", MODE_PRIVATE)
                        .edit()
                        .putString("session_cookies", cookies)
                        .putString("website_url", WEBSITE_URL)
                        .apply();
                }
                // Re-inject FCM token after every page load so website JS can POST it with user_id/session.
                if (fcmTokenForWebView != null && !fcmTokenForWebView.isEmpty()) {
                    view.evaluateJavascript("window.FCM_TOKEN = '" + fcmTokenForWebView + "'; window.dispatchEvent(new Event('fcm_token_ready'));", null);
                }
                // Custom JavaScript injection
                view.evaluateJavascript("<!DOCTYPE html> <html lang=\"en\"> <head>     <meta charset=\"UTF-8\">     <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">     <title>Broken Theatre</title>     <style>         body, html {             margin: 0;             padding: 0;             overflow: hidden;             width: 100%;             height: 100%;             background-color: #000;             font-family: sans-serif;             user-select: none;             touch-action: none;         }         canvas {             display: block;             width: 100%;             height: 100%;         }         #version-tag {             position: absolute;             top: 20px;             left: 20px;             color: rgba(180, 180, 180, 0.5);             font-size: 18px;             font-weight: bold;             z-index: 10;             pointer-events: none;         }         #ui-layer {             position: absolute;             bottom: 25px;             left: 25px;             color: #fff;             font-size: 20px;             z-index: 10;             pointer-events: none;         }         #screamer {             position: absolute;             top: 0;             left: 0;             width: 100%;             height: 100%;             background-color: #000;             display: none;             justify-content: center;             align-items: center;             z-index: 100;         }         .joystick-container {             position: absolute;             bottom: 45px;             left: 45px;             width: 120px;             height: 120px;             background: rgba(255, 255, 255, 0.15);             border: 2px solid rgba(255, 255, 255, 0.3);             border-radius: 50%;             z-index: 10;             touch-action: none;         }         .joystick-stick {             position: absolute;             top: 35px;             left: 35px;             width: 50px;             height: 50px;             background: rgba(255, 255, 255, 0.6);             border-radius: 50%;             pointer-events: none;         }         #action-btn {             position: absolute;             bottom: 55px;             right: 55px;             width: 95px;             height: 95px;             background: rgba(200, 0, 0, 0.5);             border: 2px solid rgba(255, 255, 255, 0.7);             border-radius: 50%;             color: white;             font-weight: bold;             font-size: 18px;             z-index: 10;             display: flex;             justify-content: center;             align-items: center;             touch-action: none;         }     </style>     <script src=\"https://cloudflare.com\"></script> </head> <body>      <div id=\"version-tag\">2.0</div>     <div id=\"ui-layer\">Ammo: <span id=\"ammo-val\">0</span></div>          <div id=\"screamer\">         <svg width=\"100%\" height=\"100%\" viewBox=\"0 0 100 100\" preserveAspectRatio=\"none\" style=\"background:#150000;\">             <circle cx=\"30\" cy=\"40\" r=\"12\" fill=\"#ff0000\"/>             <circle cx=\"70\" cy=\"40\" r=\"12\" fill=\"#ff0000\"/>             <path d=\"M 15 80 Q 50 30 85 80\" stroke=\"#ff0000\" stroke-width=\"7\" fill=\"none\"/>         </svg>     </div>      <div class=\"joystick-container\" id=\"joy-box\">         <div class=\"joystick-stick\" id=\"joy-stick\"></div>     </div>      <div id=\"action-btn\">SHOOT</div>      <script>         let scene, camera, renderer;         let player = { x: 0, z: 0, rotY: 0, ammo: 15 };         let monsters = [];         let ammoBoxes = [];         let isGameOver = false;                  let joyStart = { x: 0, y: 0 }, joyMove = { x: 0, y: 0 }, isJoyPressed = false;         let touchLookStart = { x: 0, y: 0 };         let activeJoyTouchId = null;          const audioCtx = new (window.AudioContext || window.webkitAudioContext)();          function createBeepSound(type) {             try {                 if (audioCtx.state === \'suspended\') audioCtx.resume();                 const osc = audioCtx.createOscillator();                 const gain = audioCtx.createGain();                 osc.connect(gain);                 gain.connect(audioCtx.destination);                                  if (type === \'shoot\') {                     osc.type = \'sawtooth\';                     osc.frequency.setValueAtTime(220, audioCtx.currentTime);                     osc.frequency.exponentialRampToValueAtTime(10, audioCtx.currentTime + 0.2);                     gain.gain.setValueAtTime(0.3, audioCtx.currentTime);                     gain.gain.linearRampToValueAtTime(0, audioCtx.currentTime + 0.2);                     osc.start(); osc.stop(audioCtx.currentTime + 0.2);                 } else if (type === \'pickup\') {                     osc.type = \'sine\';                     osc.frequency.setValueAtTime(450, audioCtx.currentTime);                     osc.frequency.exponentialRampToValueAtTime(750, audioCtx.currentTime + 0.12);                     gain.gain.setValueAtTime(0.2, audioCtx.currentTime);                     gain.gain.linearRampToValueAtTime(0, audioCtx.currentTime + 0.12);                     osc.start(); osc.stop(audioCtx.currentTime + 0.12);                 } else if (type === \'screamer\') {                     osc.type = \'sawtooth\';                     osc.frequency.setValueAtTime(130, audioCtx.currentTime);                     osc.frequency.linearRampToValueAtTime(30, audioCtx.currentTime + 3);                     gain.gain.setValueAtTime(0.8, audioCtx.currentTime);                     gain.gain.linearRampToValueAtTime(0, audioCtx.currentTime + 3);                     osc.start(); osc.stop(audioCtx.currentTime + 3);                 }             } catch(e) {}         }          function init() {             scene = new THREE.Scene();             scene.background = new THREE.Color(0x020204);             scene.fog = new THREE.FogExp2(0x020204, 0.07);              camera = new THREE.PerspectiveCamera(75, window.innerWidth / window.innerHeight, 0.1, 1000);                          renderer = new THREE.WebGLRenderer({ antialias: true });             renderer.setSize(window.innerWidth, window.innerHeight);             document.body.appendChild(renderer.domElement);              document.getElementById(\'ammo-val\').innerText = player.ammo;              const floorGeo = new THREE.PlaneGeometry(150, 150);             const floorMat = new THREE.MeshBasicMaterial({ color: 0x1a1a24 });             const floor = new THREE.Mesh(floorGeo, floorMat);             floor.rotation.x = -Math.PI / 2;             scene.add(floor);              const wallGeo = new THREE.BoxGeometry(150, 15, 1);             const wallMat = new THREE.MeshBasicMaterial({ color: 0x0f0b0b });                          let w1 = new THREE.Mesh(wallGeo, wallMat); w1.position.set(0, 7.5, -75); scene.add(w1);             let w2 = new THREE.Mesh(wallGeo, wallMat); w2.position.set(0, 7.5, 75); scene.add(w2);             let w3 = new THREE.Mesh(wallGeo, wallMat); w3.rotation.y = Math.PI / 2; w3.position.set(-75, 7.5, 0); scene.add(w3);             let w4 = new THREE.Mesh(wallGeo, wallMat); w4.rotation.y = Math.PI / 2; w4.position.set(75, 7.5, 0); scene.add(w4);              for(let i=0; i<15; i++) {                 let colGeo = new THREE.BoxGeometry(5, 15, 5);                 let colMat = new THREE.MeshBasicMaterial({ color: 0x0b0b0e });                 let col = new THREE.Mesh(colGeo, colMat);                 col.position.set((Math.random()-0.5)*100, 7.5, (Math.random()-0.5)*100);                 scene.add(col);             }              const flashlight = new THREE.SpotLight(0xffffff, 4.5, 60, Math.PI / 4.5, 0.4, 1);             flashlight.position.set(0, 0, 0);             camera.add(flashlight);             flashlight.target = camera;             scene.add(camera);              for (let i = 0; i < 12; i++) {                 let boxGeo = new THREE.BoxGeometry(1.0, 0.5, 1.0);                 let boxMat = new THREE.MeshBasicMaterial({ color: 0xeeaa00 });                 let amBox = new THREE.Mesh(boxGeo, boxMat);                 amBox.position.set((Math.random() - 0.5) * 120, 0.25, (Math.random() - 0.5) * 120);                 scene.add(amBox);                 ammoBoxes.push(amBox);             }              for (let i = 0; i < 6; i++) {                 let mGeo = new THREE.CylinderGeometry(1.0, 1.0, 3.5, 8);                 let mMat = new THREE.MeshBasicMaterial({ color: 0x550000 });                 let monster = new THREE.Mesh(mGeo, mMat);                 monster.position.set((Math.random() - 0.5) * 120, 1.75, (Math.random() - 0.5) * 120);                                  monster.userData = {                     targetX: (Math.random() - 0.5) * 120,                     targetZ: (Math.random() - 0.5) * 120,                     speed: 0.07 + Math.random() * 0.04,                     stepTimer: 0                 };                 scene.add(monster);                 monsters.push(monster);             }              setupControls();             animate();         }          function setupControls() {             const joyBox = document.getElementById(\'joy-box\');             const joyStick = document.getElementById(\'joy-stick\');              joyBox.addEventListener(\'touchstart\', (e) => {                 e.preventDefault();                 let touch = e.changedTouches[0];                 isJoyPressed = true;                 activeJoyTouchId = touch.identifier;                 joyStart.x = touch.clientX;                 joyStart.y = touch.clientY;             }, { passive: false });              window.addEventListener(\'touchmove\', (e) => {                 let joyTouch = null;                 let lookTouch = null;                  for (let i = 0; i < e.touches.length; i++) {                     if (e.touches[i].identifier === activeJoyTouchId) {                         joyTouch = e.touches[i];                     } else {                         lookTouch = e.touches[i];                     }                 }", null);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleWebViewUrl(view, url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && request != null && request.getUrl() != null) {
                    return handleWebViewUrl(view, request.getUrl().toString());
                }
                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (progressBar != null) progressBar.setProgress(newProgress);
            }
            
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.grant(request.getResources());
            }
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (fileUploadCallback != null) {
                    fileUploadCallback.onReceiveValue(null);
                }
                fileUploadCallback = filePathCallback;

                // Camera intent
                Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.TITLE, "camera_photo");
                cameraImageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);

                // File chooser intent
                Intent fileIntent = new Intent(Intent.ACTION_GET_CONTENT);
                fileIntent.addCategory(Intent.CATEGORY_OPENABLE);
                fileIntent.setType("*/*");

                // Combine into chooser
                Intent chooserIntent = Intent.createChooser(fileIntent, "Select file");
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cameraIntent});

                fileUploadLauncher.launch(chooserIntent);
                return true;
            }
        });

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);
                String cookies = CookieManager.getInstance().getCookie(url);
                request.addRequestHeader("cookie", cookies);
                request.addRequestHeader("User-Agent", userAgent);
                request.setDescription("Downloading file...");
                request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType));
                request.allowScanningByMediaScanner();
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimeType));
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                dm.enqueue(request);
                Toast.makeText(getApplicationContext(), "Downloading File", Toast.LENGTH_LONG).show();
            }
        });
    }


    private boolean handleWebViewUrl(WebView view, String url) {
        if (url == null || url.trim().isEmpty()) return false;
        String lower = url.toLowerCase();
        if (lower.startsWith("tel:") || lower.startsWith("mailto:") || lower.startsWith("sms:") || lower.startsWith("smsto:") || lower.startsWith("whatsapp:") || lower.startsWith("market:") || lower.startsWith("intent:")) {
            try {
                Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                startActivity(intent);
                return true;
            } catch (Exception ignored) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
                    return true;
                } catch (Exception ignoredAgain) {
                    return true;
                }
            }
        }
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return true;
        }
        view.loadUrl(url);
        return true;
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private void handleIntent(Intent intent) {
        if (intent != null && intent.getData() != null) {
            String deepUrl = intent.getData().toString();
            if (deepUrl.startsWith("http")) {
                webView.loadUrl(deepUrl);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private final ActivityResultLauncher<Intent> fileUploadLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(), result -> {
            if (fileUploadCallback == null) return;
            if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                fileUploadCallback.onReceiveValue(new Uri[]{result.getData().getData()});
            } else if (result.getResultCode() == RESULT_OK && cameraImageUri != null) {
                fileUploadCallback.onReceiveValue(new Uri[]{cameraImageUri});
            } else {
                fileUploadCallback.onReceiveValue(null);
            }
            fileUploadCallback = null;
        });

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 999, 0, "Privacy Policy");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 999) {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://gamejolt.com")));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView != null) {
            webView.destroy();
        }
    }
}