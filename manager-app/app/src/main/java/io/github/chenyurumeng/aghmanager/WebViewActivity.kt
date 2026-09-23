package io.github.chenyurumeng.aghmanager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

open class WebViewActivity : ComponentActivity() {
    companion object {
        @Volatile
        private var dataDirectoryConfigured = false

        private val BG = Color.rgb(11, 15, 20)
        private val SURFACE = Color.rgb(20, 26, 34)
        private val BORDER = Color.rgb(47, 59, 74)
        private val TEXT = Color.rgb(245, 247, 250)
        private val MUTED = Color.rgb(139, 152, 169)
        private val BLUE = Color.rgb(91, 140, 255)
    }

    private var webView: WebView? = null
    private lateinit var progress: ProgressBar
    private var url: String = "http://127.0.0.1:3000"

    protected open fun dataDirectorySuffix(): String = "web_default"

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        configureWebViewDataDirectoryOnce()

        @Suppress("DEPRECATION")
        window.statusBarColor = BG
        @Suppress("DEPRECATION")
        window.navigationBarColor = BG

        val title = intent.getStringExtra("title") ?: "AdGuard Home"
        url = intent.getStringExtra("url") ?: "http://127.0.0.1:3000"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            @Suppress("DEPRECATION")
            setOnApplyWindowInsetsListener { view, insets ->
                view.setPadding(0, insets.systemWindowInsetTop, 0, 0)
                insets
            }
            requestApplyInsets()
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(SURFACE)
        }

        val back = toolButton("‹").apply {
            textSize = 25f
            setOnClickListener { navigateBack() }
        }
        toolbar.addView(back, LinearLayout.LayoutParams(dp(46), dp(46)))

        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(title, 16, true))
            addView(
                text(url, 11, false).apply {
                    setTextColor(MUTED)
                }
            )
        }
        toolbar.addView(
            titleBox,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val refresh = toolButton("↻").apply {
            setOnClickListener { webView?.reload() }
        }
        toolbar.addView(refresh, LinearLayout.LayoutParams(dp(46), dp(46)))

        val browser = toolButton("↗").apply {
            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Exception) {
                    Toast.makeText(
                        this@WebViewActivity,
                        "没有可用浏览器",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        toolbar.addView(browser, LinearLayout.LayoutParams(dp(46), dp(46)))

        val copy = toolButton("⧉").apply {
            setOnClickListener {
                val clipboard = getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("AGH URL", url))
                Toast.makeText(
                    this@WebViewActivity,
                    "地址已复制",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        toolbar.addView(copy, LinearLayout.LayoutParams(dp(46), dp(46)))

        root.addView(
            toolbar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        progress = ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 100
        }
        root.addView(
            progress,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(3)
            )
        )

        val browserView = WebView(this).apply {
            setBackgroundColor(BG)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadsImagesAutomatically = true
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                builtInZoomControls = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = true
                }
            }
            webViewClient = WebViewClient()
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    this@WebViewActivity.progress.progress = newProgress
                    this@WebViewActivity.progress.visibility =
                        if (newProgress >= 100) ProgressBar.GONE else ProgressBar.VISIBLE
                }
            }
        }
        webView = browserView

        root.addView(
            browserView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (webView?.canGoBack() == true) {
                        webView?.goBack()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )

        browserView.loadUrl(url)
    }

    private fun configureWebViewDataDirectoryOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || dataDirectoryConfigured) return
        synchronized(WebViewActivity::class.java) {
            if (dataDirectoryConfigured) return
            WebView.setDataDirectorySuffix(dataDirectorySuffix())
            dataDirectoryConfigured = true
        }
    }

    private fun navigateBack() {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            finish()
        }
    }

    private fun toolButton(label: String): TextView =
        text(label, 18, true).apply {
            gravity = Gravity.CENTER
            setTextColor(BLUE)
            background = rounded(Color.TRANSPARENT, BORDER, 14)
            isClickable = true
        }

    private fun text(value: String, sp: Int, bold: Boolean): TextView =
        TextView(this).apply {
            text = value
            setTextColor(TEXT)
            textSize = sp.toFloat()
            setTypeface(
                Typeface.DEFAULT,
                if (bold) Typeface.BOLD else Typeface.NORMAL
            )
        }

    private fun rounded(fill: Int, stroke: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radiusDp).toFloat()
            setStroke(dp(1), stroke)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onPause() {
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        webView?.let { view ->
            view.stopLoading()
            view.loadUrl("about:blank")
            view.webChromeClient = null
            view.webViewClient = WebViewClient()
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
        }
        webView = null
        super.onDestroy()
    }
}
