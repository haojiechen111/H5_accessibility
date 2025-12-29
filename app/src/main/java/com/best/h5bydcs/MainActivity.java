package com.best.h5bydcs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.chehejia.car.aipluginsdk.dcs.VoiceCapability;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "WebViewAccessibility";
    private WebView webView;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.accessibility_custom_Web);
        setupWebView();
    }

    private void setupWebView() {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);

        webView.addJavascriptInterface(new AndroidBridge(this), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "页面加载完成");
            }
        });

        webView.loadUrl("file:///android_asset/demo_static.html");
    }

    public static class AndroidBridge {
        private Context context;

        public AndroidBridge(Context ctx) {
            this.context = ctx;
        }

        @JavascriptInterface
        public String getBadgeDescription(@NonNull String action, @NonNull String originText, String viewId) {
            Log.d(TAG, String.format("getBadgeDescription调用 - action: %s, originText: %s, viewId: %s",
                    action, originText, viewId));

            CharSequence json = VoiceCapability.buildActionCapability(action, originText, viewId);
            String result = json.toString();

            Log.d(TAG, "返回JSON: " + result);
            return result;
        }

        @JavascriptInterface
        public void onImageDetailOpened(int itemId, String imageUrl, String title) {
            Log.d(TAG, String.format("图片详情已打开 - ID: %d, URL: %s, Title: %s",
                    itemId, imageUrl, title));
        }

        @JavascriptInterface
        public void onImageDetailClosed(int itemId) {
            Log.d(TAG, "图片详情已关闭 - ID: " + itemId);
        }

        @JavascriptInterface
        public void onBadgeToggle(boolean isVisible) {
            Log.d(TAG, "角标切换 - 可见: " + isVisible);
        }

        @JavascriptInterface
        public void onPageLoaded() {
            Log.d(TAG, "H5页面加载完成");
        }
    }

    @SuppressLint("GestureBackNavigation")
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            webView.evaluateJavascript("window.getCurrentPage()", value -> {
                if ("\"detail\"".equals(value)) {
                    webView.evaluateJavascript("window.closeDetailPage()", null);
                } else {
                    finish();
                }
            });
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
