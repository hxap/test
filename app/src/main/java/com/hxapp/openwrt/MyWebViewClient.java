package com.hxapp.openwrt;

import android.content.Intent;
import android.net.Uri;
import android.net.http.SslError;
import android.util.Log;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MyWebViewClient extends WebViewClient {

    @Override
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
        Log.w("WebViewSSL", "SSL Error: " + error.toString());
        handler.proceed();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
        Log.e("WebView", "Error loading page (old API): " + description + " URL: " + failingUrl);
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        Uri uri = Uri.parse(url);
        return handleUriLoading(view, uri);
    }

    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        Log.e("WebView", "Error loading page (new API): " + error.getDescription() + " URL: " + request.getUrl());
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        Uri uri = request.getUrl();
        return handleUriLoading(view, uri);
    }

    private boolean handleUriLoading(WebView view, Uri uri) {
        if (uri.getScheme() != null && (uri.getScheme().equals("http") || uri.getScheme().equals("https"))) {
            view.loadUrl(uri.toString());
            return true;
        } else {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                if (intent.resolveActivity(view.getContext().getPackageManager()) != null) {
                    view.getContext().startActivity(intent);
                } else {
                    Log.w("WebViewClient", "No app found to handle URI: " + uri.toString());
                }
            } catch (Exception e) {
                Log.e("WebViewClient", "Error handling external URI: " + uri.toString(), e);
            }
            return true;
        }
    }

    @Override
    public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
        super.onPageStarted(view, url, favicon);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
    }
}

