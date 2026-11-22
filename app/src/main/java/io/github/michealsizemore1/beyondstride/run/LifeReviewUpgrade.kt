package io.github.michealsizemore1.beyondstride.run

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import java.time.LocalDate

/**
 * 🌙 Full Daily AI Review for yesterday (Nov 22 2026). The phone writes a backup review at 11:50 PM from training, food
 * and sleep only. The fuller one (journal, Wellness, Bible study, habits, Library, Spiritual checks) used to be written
 * by the Command Center website and synced in; since the Life tab is disconnected, it's written here instead: the first
 * time the Life tab loads each day, if yesterday's review is empty or still just the phone's backup (and you haven't
 * edited it), a hidden copy of the page writes the full one with the Life tab's AI key. The Life tab you're using never
 * moves to another day.
 */
object LifeReviewUpgrade {
    private fun p(ctx: Context) = ctx.getSharedPreferences("lifereview", 0)
    private var running = false
    fun last(ctx: Context) = p(ctx).getString("last", "") ?: ""

    fun check(act: Activity) {
        val app = act.applicationContext; val today = LocalDate.now().toString()
        if (running || p(app).getString("checked", "") == today) return
        p(app).edit().putString("checked", today).apply()
        val day = LocalDate.now().minusDays(1).toString()
        val main = try { CcEngine.get(act) } catch (e: Exception) { return }
        main.evaluateJavascript("window.ccNative&&ccNative.reviewNeeds?ccNative.reviewNeeds('$day'):'no'") { r ->
            if (r?.contains("yes") == true) write(app, day)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun write(app: Context, day: String) {
        running = true
        val ui = Handler(Looper.getMainLooper())
        val w = WebView(app).apply {
            settings.javaScriptEnabled = true; settings.domStorageEnabled = true
            @Suppress("DEPRECATION") settings.databaseEnabled = true
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest) = CcEngine.serve(view.context, req.url)
                override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest) = req.url.host != "michealsizemore1.github.io"
            }
            // Marks this as the app's copy, so the page doesn't sync with the website.
            addJavascriptInterface(object { @JavascriptInterface fun changed() {} }, "BSNative")
        }
        fun finish(msg: String) {
            running = false
            p(app).edit().putString("last", "$day · $msg").apply()
            try { w.destroy() } catch (e: Exception) { }
            if (msg == "done") CcEngine.notifyChanged()
        }
        var started = false
        w.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest) = CcEngine.serve(view.context, req.url)
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest) = req.url.host != "michealsizemore1.github.io"
            override fun onPageFinished(view: WebView, url: String) {
                if (started) return; started = true
                // Give the page a few seconds to set itself up, then write the review and wait (up to 3 minutes).
                ui.postDelayed({
                    w.evaluateJavascript("window.ccNative&&ccNative.upgradeReview?ccNative.upgradeReview('$day'):'error'") { r0 ->
                        if (r0?.contains("running") != true) { finish(r0?.trim('"') ?: "error"); return@evaluateJavascript }
                        var n = 0
                        val poll = object : Runnable { override fun run() {
                            w.evaluateJavascript("window.__upg||''") { r ->
                                val st = r?.trim('"') ?: ""
                                if (st == "running" && ++n < 60) ui.postDelayed(this, 3000) else finish(if (st == "running") "timed out" else st)
                            }
                        } }
                        ui.postDelayed(poll, 3000)
                    }
                }, 5000)
            }
        }
        w.loadUrl(LifeActivity.CC_PAGE + (NutritionSync.linkPart(app).takeIf { it.isNotEmpty() }?.let { "#app=bs$it" } ?: ""))
        ui.postDelayed({ if (running && !started) finish("page didn't load") }, 60_000)
    }
}
