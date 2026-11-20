package io.github.michealsizemore1.beyondstride.run

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

/**
 * 📓 Life — the My Life Command Center inside BeyondStride (the fifth tab).
 * It's the same website as in Chrome, so nothing moved: it keeps its own data in this screen's storage and stays
 * matched with Chrome and the computer through the Command Center's own cloud sync.
 * Settings → "Life tab" turns the tab off again.
 */
object LifeTab {
    private fun p(ctx: Context) = ctx.getSharedPreferences("runsettings", 0)
    fun on(ctx: Context) = p(ctx).getBoolean("lifeTab", true)
    fun setOn(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("lifeTab", v).apply()
}

class LifeActivity : Activity() {
    private lateinit var web: WebView
    private var fileCb: ValueCallback<Array<Uri>>? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true      // the Command Center keeps its data in localStorage
            @Suppress("DEPRECATION") settings.databaseEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            setBackgroundColor(RunUi.BG)
            webViewClient = object : WebViewClient() {
                // Command Center pages stay here; other sites open in the browser.
                override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                    val u = req.url
                    if (u.host == "michealsizemore1.github.io") return false
                    try { startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (e: Exception) { }
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {   // also shows the page's alert / confirm boxes
                override fun onShowFileChooser(v: WebView, cb: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                    fileCb?.onReceiveValue(null); fileCb = cb
                    return try { startActivityForResult(params.createIntent(), 41); true } catch (e: Exception) { fileCb = null; false }
                }
            }
        }
        if (savedInstanceState != null) web.restoreState(savedInstanceState)
        else {
            // The #app=bs link part tells the Command Center where BeyondStride's daily summaries are (same as
            // Settings → Link the Command Center review), so the review, steps and wellness work here too.
            val base = RunSettings.ccUrl(this).substringBefore("#")
            web.loadUrl(base + (NutritionSync.linkPart(this).takeIf { it.isNotEmpty() }?.let { "#app=bs$it" } ?: ""))
        }
        val box = FrameLayout(this).apply {
            setBackgroundColor(RunUi.BG)
            addView(web, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            // Stay below the status bar.
            setOnApplyWindowInsetsListener { v, insets ->
                @Suppress("DEPRECATION") val t = if (android.os.Build.VERSION.SDK_INT >= 30) insets.getInsets(android.view.WindowInsets.Type.statusBars()).top else insets.systemWindowInsetTop
                v.setPadding(0, t, 0, 0); insets
            }
        }
        setContentView(RunUi.tabbed(this, box, RunUi.TAB_LIFE))
    }

    override fun onResume() { super.onResume(); web.onResume() }
    override fun onPause() { web.onPause(); super.onPause() }
    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); web.saveState(out) }
    override fun onDestroy() { web.destroy(); super.onDestroy() }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 41) { fileCb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data)); fileCb = null; return }
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
    }

    // Back goes back a page in the Command Center first.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (web.canGoBack()) web.goBack() else @Suppress("DEPRECATION") super.onBackPressed() }
}
