package com.anglesgirl.wmsbrowser;

import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private NoImeWebView webView;
    private EditText urlInput;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        urlInput = findViewById(R.id.urlInput);
        status = findViewById(R.id.status);
        FrameLayout container = findViewById(R.id.webContainer);

        webView = new NoImeWebView(this);
        setupWebView();
        container.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        findViewById(R.id.btnLoad).setOnClickListener(v -> loadUrl());
        findViewById(R.id.btnImeOn).setOnClickListener(v -> {
            webView.enableIme();
            Toast.makeText(this, "输入法已开启（手动输入可用）", Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.btnImeOff).setOnClickListener(v -> {
            webView.disableIme();
            Toast.makeText(this, "输入法已关闭（扫码优先）", Toast.LENGTH_SHORT).show();
        });

        getWindow().setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        // 锁定字号，避免小屏字被放大
        s.setTextZoom(100);
        // 重要：内联 transform scale(1,1) 在小屏可能错位，强制按设备宽度排版
        s.setSupportZoom(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedSslError(android.webkit.SslErrorHandler handler,
                                           android.net.http.SslError error) {
                // 内网自签证书：信任
                handler.proceed();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                status.setText("已加载：" + url);
                // 注入：锁定 viewport，避免框架写死 240x320 在真机过小/错位
                view.evaluateJavascript(
                        "(function(){var m=document.querySelector('meta[name=viewport]');" +
                        "if(m){m.setAttribute('content','width=device-width,initial-scale=1,user-scalable=no');}" +
                        "else{var n=document.createElement('meta');n.name='viewport';" +
                        "n.content='width=device-width,initial-scale=1,user-scalable=no';" +
                        "document.head.appendChild(n);}" +
                        "var b=document.body;b.style.width='100%';b.style.height='100%';" +
                        "b.style.transform='none';b.style.transformOrigin='0 0';})();", null);
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
    }

    private void loadUrl() {
        String url = urlInput.getText().toString().trim();
        if (url.isEmpty()) {
            Toast.makeText(this, "请输入地址", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
            urlInput.setText(url);
        }
        status.setText("加载中…");
        webView.loadUrl(url);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeAllViews();
            webView.destroy();
        }
        super.onDestroy();
    }
}
