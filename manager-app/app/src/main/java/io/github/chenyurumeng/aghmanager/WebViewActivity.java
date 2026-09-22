package io.github.chenyurumeng.aghmanager;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class WebViewActivity extends Activity {
    private static boolean dataDirectoryConfigured;

    private static final int BG = Color.rgb(11, 15, 20);
    private static final int SURFACE = Color.rgb(20, 26, 34);
    private static final int BORDER = Color.rgb(47, 59, 74);
    private static final int TEXT = Color.rgb(245, 247, 250);
    private static final int MUTED = Color.rgb(139, 152, 169);
    private static final int BLUE = Color.rgb(91, 140, 255);

    private WebView webView;
    private ProgressBar progress;
    private String url;

    protected String dataDirectorySuffix() {
        return "web_default";
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        configureWebViewDataDirectoryOnce();
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        String title = getIntent().getStringExtra("title");
        url = getIntent().getStringExtra("url");
        if (url == null) url = "http://127.0.0.1:3000";

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop(), 0, 0);
            return insets;
        });
        root.requestApplyInsets();

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(10), dp(8), dp(10), dp(8));
        toolbar.setBackgroundColor(SURFACE);

        TextView back = toolButton("‹");
        back.setTextSize(25);
        back.setOnClickListener(v -> {
            if (webView != null && webView.canGoBack()) webView.goBack();
            else finish();
        });
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = text(title == null ? "AdGuard Home" : title, 16, true);
        titleBox.addView(titleView);
        TextView address = text(url, 11, false);
        address.setTextColor(MUTED);
        titleBox.addView(address);
        toolbar.addView(titleBox, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView refresh = toolButton("↻");
        refresh.setOnClickListener(v -> webView.reload());
        toolbar.addView(refresh, new LinearLayout.LayoutParams(dp(46), dp(46)));

        TextView browser = toolButton("↗");
        browser.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception e) {
                Toast.makeText(this, "没有可用浏览器", Toast.LENGTH_SHORT).show();
            }
        });
        toolbar.addView(browser, new LinearLayout.LayoutParams(dp(46), dp(46)));

        TextView copy = toolButton("⧉");
        copy.setOnClickListener(v -> {
            ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cb.setPrimaryClip(ClipData.newPlainText("AGH URL", url));
            Toast.makeText(this, "地址已复制", Toast.LENGTH_SHORT).show();
        });
        toolbar.addView(copy, new LinearLayout.LayoutParams(dp(46), dp(46)));

        root.addView(toolbar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        root.addView(progress, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(3)));

        webView = new WebView(this);
        webView.setBackgroundColor(BG);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setBuiltInZoomControls(false);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            s.setSafeBrowsingEnabled(true);
        }

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? ProgressBar.GONE : ProgressBar.VISIBLE);
            }
        });

        root.addView(webView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
        webView.loadUrl(url);
    }

    private void configureWebViewDataDirectoryOnce() {
        if (android.os.Build.VERSION.SDK_INT < 28 || dataDirectoryConfigured) return;
        synchronized (WebViewActivity.class) {
            if (dataDirectoryConfigured) return;
            WebView.setDataDirectorySuffix(dataDirectorySuffix());
            dataDirectoryConfigured = true;
        }
    }

    private TextView toolButton(String label) {
        TextView v = text(label, 18, true);
        v.setGravity(Gravity.CENTER);
        v.setTextColor(BLUE);
        v.setBackground(rounded(Color.TRANSPARENT, BORDER, 14));
        v.setClickable(true);
        return v;
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextColor(TEXT);
        v.setTextSize(sp);
        v.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return v;
    }

    private GradientDrawable rounded(int fill, int stroke, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(dp(1), stroke);
        return d;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onPause() {
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            CookieManager.getInstance().flush();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            if (webView.getParent() instanceof android.view.ViewGroup) {
                ((android.view.ViewGroup) webView.getParent()).removeView(webView);
            }
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
