package com.hxapp.openwrt;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.MimeTypeMap;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends Activity {

    private WebView mWebView;
    private FrameLayout fullscreenContainer;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private View customView;

    private ValueCallback<Uri[]> mUploadMessage;
    public static final int FILE_CHOOSER_REQUEST_CODE = 1;

    private static final int PERMISSION_REQUEST_CODE = 2;
    private boolean isDownloadPendingPermission = false;

    // New variables to store pending download details
    private String pendingDownloadUrl;
    private String pendingDownloadUserAgent;
    private String pendingDownloadContentDisposition;
    private String pendingDownloadMimeType;
    private String pendingDownloadReferer;
    private String pendingDownloadCookies;

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mWebView = findViewById(R.id.activity_main_webview);
        fullscreenContainer = findViewById(R.id.fullscreen_container_id);

        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setLoadsImagesAutomatically(true);

        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setSupportZoom(true);

        mWebView.setWebViewClient(new MyWebViewClient());

        mWebView.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    hideFullscreenView();
                }
                customView = view;
                customViewCallback = callback;

                mWebView.setVisibility(View.GONE);
                fullscreenContainer.addView(customView, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                fullscreenContainer.setVisibility(View.VISIBLE);
                setFullscreen(true);

                // Keep screen on when entering fullscreen video
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }

            @Override
            public void onHideCustomView() {
                hideFullscreenView();
                // Clear FLAG_KEEP_SCREEN_ON when exiting fullscreen video
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }

            @Override
            public boolean onShowFileChooser(WebView webView,
                                             ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                if (mUploadMessage != null) {
                    mUploadMessage.onReceiveValue(null);
                    mUploadMessage = null;
                }
                mUploadMessage = filePathCallback;

                // For file chooser, READ_EXTERNAL_STORAGE is needed on Android 6-9
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE)
                            != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                                PERMISSION_REQUEST_CODE);
                        // IMPORTANT: Cancel the upload message if permission is not granted yet
                        mUploadMessage.onReceiveValue(null);
                        mUploadMessage = null;
                        Toast.makeText(MainActivity.this, "Please grant storage permission to upload files.", Toast.LENGTH_LONG).show();
                        return true; // Indicate that we handled the request
                    }
                }

                Intent contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
                contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);

                String[] acceptTypes = fileChooserParams.getAcceptTypes();
                if (acceptTypes != null && acceptTypes.length > 0 && !(acceptTypes.length == 1 && acceptTypes[0].equals("*/*"))) {
                    contentSelectionIntent.setType(TextUtils.join("|", acceptTypes));
                } else {
                    contentSelectionIntent.setType("*/*");
                }

                if (fileChooserParams.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                    contentSelectionIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                }

                Intent chooserIntent = Intent.createChooser(contentSelectionIntent, "Choose File");

                try {
                    startActivityForResult(chooserIntent, FILE_CHOOSER_REQUEST_CODE);
                } catch (Exception e) {
                    Log.e("WebViewUpload", "Error launching file chooser", e);
                    mUploadMessage.onReceiveValue(null);
                    mUploadMessage = null;
                    Toast.makeText(MainActivity.this, "No application found to pick files.", Toast.LENGTH_LONG).show();
                    return false;
                }
                return true;
            }

            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                // You can update a ProgressBar here
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                super.onReceivedTitle(view, title);
            }
        });

        mWebView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent,
                                        String contentDisposition, String mimeType,
                                        long contentLength) {
                // Get current WebView URL for Referer header
                String referer = mWebView.getUrl();
                // Get cookies for the download URL's domain
                CookieManager cookieManager = CookieManager.getInstance();
                String cookies = cookieManager.getCookie(url); // Pass the download URL here

                // Check for WRITE_EXTERNAL_STORAGE permission on Android 6-9 (API 23-28)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            != PackageManager.PERMISSION_GRANTED) {
                        isDownloadPendingPermission = true;
                        // Store download details before requesting permission
                        pendingDownloadUrl = url;
                        pendingDownloadUserAgent = userAgent;
                        pendingDownloadContentDisposition = contentDisposition;
                        pendingDownloadMimeType = mimeType;
                        pendingDownloadReferer = referer; // Store referer
                        pendingDownloadCookies = cookies; // Store cookies

                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                PERMISSION_REQUEST_CODE);
                        Toast.makeText(MainActivity.this, "Please grant storage permission to download.", Toast.LENGTH_LONG).show();
                        return; // Stop the download process until permission is granted
                    }
                }
                // If permission is already granted or not needed (Android 10+), proceed with download
                startFileDownload(url, userAgent, contentDisposition, mimeType, referer, cookies);
            }
        });

        String urlToLoad = BuildConfig.WEB_URL;
        mWebView.loadUrl(urlToLoad);
    }

    // Modified startFileDownload to accept referer and cookies
    private void startFileDownload(String url, String userAgent, String contentDisposition, String mimeType,
                                   String referer, String cookies) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));

            // Add Referer header if available
            if (referer != null && !referer.isEmpty()) {
                request.addRequestHeader("Referer", referer);
                Log.d("DownloadDebug", "Referer added: " + referer);
            }
            // Add Cookie header if available
            if (cookies != null && !cookies.isEmpty()) {
                request.addRequestHeader("Cookie", cookies);
                Log.d("DownloadDebug", "Cookies added: " + cookies);
            }

            String fileName = url.substring(url.lastIndexOf('/') + 1);
            if (contentDisposition != null && contentDisposition.contains("filename=")) {
                try {
                    int index = contentDisposition.indexOf("filename=");
                    if (index != -1) {
                        fileName = contentDisposition.substring(index + "filename=".length());
                        if (fileName.startsWith("\"") && fileName.endsWith("\"")) {
                            fileName = fileName.substring(1, fileName.length() - 1);
                        }
                        fileName = fileName.replaceAll("[^a-zA-Z0-9\\.\\-_]", "_");
                    }
                } catch (Exception e) {
                    Log.e("WebViewDownload", "Failed to parse filename from header", e);
                }
            }

            if (fileName == null || fileName.isEmpty() || fileName.equals("/") || fileName.equals("?")) {
                fileName = Uri.parse(url).getLastPathSegment();
                if (fileName == null || fileName.isEmpty()) {
                    fileName = "downloaded_file";
                }
                if (mimeType != null && !fileName.contains(".")) {
                    String extension = getExtensionFromMimeType(mimeType);
                    if (extension != null) {
                        fileName += "." + extension;
                    }
                }
            }

            request.setDescription("Downloading file...")
                    .setTitle(fileName)
                    .setMimeType(mimeType)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                request.allowScanningByMediaScanner();
            }

            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            dm.enqueue(request);

            Toast.makeText(getApplicationContext(), "Downloading File: " + fileName, Toast.LENGTH_LONG).show();
            Log.d("DownloadDebug", "Download request enqueued for URL: " + url);


        } catch (Exception e) {
            Log.e("WebViewDownload", "Download failed", e);
            Toast.makeText(getApplicationContext(), "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            // Reset pending flag, even if download failed for other reasons
            isDownloadPendingPermission = false;
            // Clear stored pending download details
            pendingDownloadUrl = null;
            pendingDownloadUserAgent = null;
            pendingDownloadContentDisposition = null;
            pendingDownloadMimeType = null;
            pendingDownloadReferer = null;
            pendingDownloadCookies = null;
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (mUploadMessage == null) {
                return;
            }
            Uri[] results = null;
            if (resultCode == RESULT_OK && intent != null) {
                String dataString = intent.getDataString();
                if (dataString != null) {
                    results = new Uri[]{Uri.parse(dataString)};
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        if (intent.getClipData() != null) {
                            final int numSelectedFiles = intent.getClipData().getItemCount();
                            results = new Uri[numSelectedFiles];
                            for (int i = 0; i < numSelectedFiles; i++) {
                                results[i] = intent.getClipData().getItemAt(i).getUri();
                            }
                        }
                    }
                }
            }
            mUploadMessage.onReceiveValue(results);
            mUploadMessage = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show();
                // If a download was pending, re-initiate it now that permission is granted
                if (isDownloadPendingPermission && pendingDownloadUrl != null) {
                    startFileDownload(pendingDownloadUrl,
                                      pendingDownloadUserAgent,
                                      pendingDownloadContentDisposition,
                                      pendingDownloadMimeType,
                                      pendingDownloadReferer, // Pass stored referer
                                      pendingDownloadCookies); // Pass stored cookies
                    // Reset the flag and clear stored data after resuming
                    isDownloadPendingPermission = false;
                    pendingDownloadUrl = null;
                    pendingDownloadUserAgent = null;
                    pendingDownloadContentDisposition = null;
                    pendingDownloadMimeType = null;
                    pendingDownloadReferer = null;
                    pendingDownloadCookies = null;
                }
            } else {
                Toast.makeText(this, "Permission Denied. Some features may not work.", Toast.LENGTH_LONG).show();
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[0])) {
                    Toast.makeText(this, "Permission permanently denied. Enable in app settings.", Toast.LENGTH_LONG).show();
                }
                // If permission denied, also reset the flag and clear stored data
                isDownloadPendingPermission = false;
                pendingDownloadUrl = null;
                pendingDownloadUserAgent = null;
                pendingDownloadContentDisposition = null;
                pendingDownloadMimeType = null;
                pendingDownloadReferer = null;
                pendingDownloadCookies = null;
            }
        }
    }

    private void setFullscreen(boolean fullscreen) {
        WindowManager.LayoutParams attrs = getWindow().getAttributes();
        if (fullscreen) {
            attrs.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
        } else {
            attrs.flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
        }
        getWindow().setAttributes(attrs);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            int uiOptions = getWindow().getDecorView().getSystemUiVisibility();
            if (fullscreen) {
                uiOptions |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                           | View.SYSTEM_UI_FLAG_FULLSCREEN
                           | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            } else {
                uiOptions &= ~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            & ~View.SYSTEM_UI_FLAG_FULLSCREEN
                            & ~View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            }
            getWindow().getDecorView().setSystemUiVisibility(uiOptions);
        }
    }

    public void hideFullscreenView() {
        if (customView == null) {
            return;
        }

        setFullscreen(false);

        fullscreenContainer.removeView(customView);
        customView = null;
        fullscreenContainer.setVisibility(View.GONE);

        mWebView.setVisibility(View.VISIBLE);

        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            hideFullscreenView();
        } else if (mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private String getExtensionFromMimeType(String mimeType) {
        if (mimeType == null || mimeType.isEmpty()) {
            return null;
        }
        MimeTypeMap mime = MimeTypeMap.getSingleton();
        return mime.getExtensionFromMimeType(mimeType);
    }
}