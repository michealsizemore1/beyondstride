package io.github.michealsizemore1.beyondstride.run

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.MutableContextWrapper
import android.net.Uri
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * The one Command Center page the whole phone app shares (Life tab and the Today tab's Life card).
 *
 * There must be only ONE copy of the page running: it keeps the day in memory and saves it whole, so two copies would
 * overwrite each other's changes. Each screen borrows this WebView (the Life tab shows it; Today only talks to it).
 * Phone only — nothing here goes to the watch.
 */
object CcEngine {
    interface Listener { fun changed() {}; fun files(json: String) {}; fun file(name: String, type: String, b64: String) {} }

    private val listeners = LinkedHashSet<Listener>()
    private var wrapper: MutableContextWrapper? = null
    private var web: WebView? = null
    private var loaded = false
    private var bundled: Set<String> = emptySet()
    /** The Life tab's file picker for the page's own upload buttons. */
    var fileChooser: ((ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams) -> Boolean)? = null

    fun add(l: Listener) { listeners += l }
    fun remove(l: Listener) { listeners -= l }

    /** The shared page, now using [act] for its dialogs. Loads the built-in Command Center the first time. */
    @SuppressLint("SetJavaScriptEnabled")
    fun get(act: Activity): WebView {
        val w = web ?: run {
            bundled = (try { act.assets.list("cc")?.toSet() } catch (e: Exception) { null }) ?: emptySet()
            val wr = MutableContextWrapper(act); wrapper = wr
            WebView(wr).apply {
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
                        try { view.context.startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
                        return true
                    }
                    // The Command Center lives inside the app (assets/cc), served at the same web address so its saved data stays put.
                    override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): android.webkit.WebResourceResponse? {
                        val u = req.url
                        if (u.host != "michealsizemore1.github.io") return null
                        val path = u.path ?: return null
                        if (!path.startsWith(LifeActivity.CC_DIR)) return null
                        val name = path.removePrefix(LifeActivity.CC_DIR)
                        if (name !in bundled) return null
                        val mime = if (name.endsWith(".html")) "text/html" else "application/javascript"
                        return try { android.webkit.WebResourceResponse(mime, "utf-8", view.context.applicationContext.assets.open("cc/$name")).apply {
                            responseHeaders = mapOf("Cache-Control" to "no-store", "Access-Control-Allow-Origin" to "*") } } catch (e: Exception) { null }
                    }
                }
                webChromeClient = object : WebChromeClient() {   // alert / confirm boxes use the current screen
                    override fun onShowFileChooser(v: WebView, cb: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean =
                        fileChooser?.invoke(cb, params) ?: false
                }
                // The Command Center tells us when it saves, so every screen showing its data updates.
                addJavascriptInterface(object {
                    @JavascriptInterface fun changed() { post { listeners.toList().forEach { it.changed() } } }
                    @JavascriptInterface fun files(json: String) { post { listeners.toList().forEach { it.files(json) } } }
                    @JavascriptInterface fun file(name: String, type: String, b64: String) { post { listeners.toList().forEach { it.file(name, type, b64) } } }
                    // 💾 Life data for BeyondStride's Backup, sent in parts.
                    @JavascriptInterface fun lifePart(part: String) { synchronized(lifeBuf) { lifeBuf.append(part) } }
                    @JavascriptInterface fun lifeDone(msg: String) { post { lifeFinished(context.applicationContext, msg) } }
                }, "BSNative")
            }.also { web = it }
        }
        wrapper?.baseContext = act
        if (!loaded) {
            loaded = true
            // The #app=bs link part tells the Command Center where BeyondStride's daily summaries are.
            w.loadUrl(LifeActivity.CC_PAGE + (NutritionSync.linkPart(act).takeIf { it.isNotEmpty() }?.let { "#app=bs$it" } ?: ""))
        }
        return w
    }

    /** [act] is going away: take the page off its screen (it keeps running for the other screens). */
    fun release(act: Activity) {
        val w = web ?: return
        (w.parent as? ViewGroup)?.let { p -> if (p.context === act) p.removeView(w) }
        if (wrapper?.baseContext === act) wrapper?.baseContext = act.applicationContext
    }

    fun detachFromParent(w: WebView) { (w.parent as? ViewGroup)?.removeView(w) }

    /**
     * Today's body numbers from the phone (sleep, sleep score, morning HR, weight, steps, BP and pulse) go straight into
     * Wellness — no trip through the cloud. Same rule as before: a value you typed yourself is never replaced.
     */
    private var pushedAt = 0L
    fun pushWellness(act: Activity, force: Boolean = false) {
        val now = System.currentTimeMillis(); if (!force && now - pushedAt < 60_000L) return; pushedAt = now
        val app = act.applicationContext
        Thread {
            val date = java.time.LocalDate.now().toString()
            val w = try { DailySummary.build(app, date).optJSONObject("wellness") } catch (e: Exception) { null } ?: return@Thread
            fun tryPush(n: Int) {
                val v = web ?: return
                v.post { v.evaluateJavascript("(typeof bsFillWellnessNow==='function'&&typeof dk==='function'&&typeof today!=='undefined')?bsFillWellnessNow(${org.json.JSONObject.quote(date)},$w):'wait'") { r ->
                    if (r?.contains("wait") == true && n > 0) v.postDelayed({ tryPush(n - 1) }, 1000) } }
            }
            tryPush(30)
        }.start()
    }

    // ── 💾 Life data in BeyondStride's Backup ──
    // The Command Center keeps its data inside its page (saved items + pictures/files). A copy is written to
    // life_backup.json (now and then, and when you leave Life or Today), and the Backup .zip includes that file.
    // Never the AI key or other secrets. Restoring a backup puts it back the next time the page opens.
    private val lifeBuf = StringBuilder()
    private var lifeBusy = false
    private var lifeStarted = 0L
    fun lifeLastTry(ctx: android.content.Context) = lp(ctx).getString("try", "") ?: ""
    private var lifeCb: ((String) -> Unit)? = null
    fun lifeFile(ctx: android.content.Context) = java.io.File(ctx.filesDir, "life_backup.json")
    private fun lp(ctx: android.content.Context) = ctx.getSharedPreferences("lifebackup", 0)
    fun lifeInfo(ctx: android.content.Context) = lp(ctx).getString("info", "") ?: ""
    fun lifeAt(ctx: android.content.Context) = lp(ctx).getLong("at", 0L)
    /** Saves a fresh copy of the Life data (at most every 20 minutes unless [force]). */
    fun exportLife(act: Activity, force: Boolean = false, done: (String) -> Unit = {}) {
        val now = System.currentTimeMillis()
        if (!force && now - lifeAt(act) < 20 * 60_000L) return
        if (lp(act).getBoolean("pending", false)) { done("error: a restore is waiting to be put back first (open Life once)"); return }
        if (lifeBusy && now - lifeStarted < 90_000L) { val prev = lifeCb; lifeCb = { m -> prev?.invoke(m); done(m) }; return }   // one already running: tell both when it ends
        lifeBusy = true; lifeStarted = now; lifeCb = done; synchronized(lifeBuf) { lifeBuf.setLength(0) }
        val w = get(act)
        // The page may still be starting (e.g. straight from Settings): wait for it, up to 40 seconds.
        fun attempt(n: Int) {
            w.evaluateJavascript("(window.ccNative&&ccNative.lifeBackup&&typeof load==='function'&&typeof getBooks==='function')?ccNative.lifeBackup():'wait'") { r ->
                if (r == null || r.contains("wait")) { if (n > 0) w.postDelayed({ attempt(n - 1) }, 1000) else { lifeBusy = false; lifeFinished(act.applicationContext, "error: the Life tab didn't start") } }
            }
        }
        attempt(40)
        w.postDelayed({ if (lifeBusy && System.currentTimeMillis() - lifeStarted >= 115_000L) { lifeBusy = false; lifeFinished(act.applicationContext, "error: took too long (no answer from the Life tab)") } }, 120_000L)
    }
    private fun lifeFinished(ctx: android.content.Context, msg: String) {
        val cb = lifeCb; lifeCb = null
        lp(ctx).edit().putString("try", java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).format(java.util.Date()) + ": " + msg.take(160)).apply()
        if (msg.startsWith("error")) { lifeBusy = false; cb?.invoke(msg); return }
        val txt = synchronized(lifeBuf) { lifeBuf.toString().also { lifeBuf.setLength(0) } }
        Thread {
            try {
                val tmp = java.io.File(ctx.filesDir, "life_backup.json.tmp"); tmp.writeText(txt)
                if (tmp.length() > 50) { tmp.renameTo(lifeFile(ctx)); lp(ctx).edit().putLong("at", System.currentTimeMillis()).putString("info", msg).apply() }
            } catch (e: Exception) { }
            lifeBusy = false
            web?.post { cb?.invoke(msg) }
        }.start()
    }
    /** After a backup was restored: put the Life data back into the page (once), then it reloads. */
    fun markLifeRestorePending(ctx: android.content.Context) = lp(ctx).edit().putBoolean("pending", true).apply()
    fun ready(act: Activity) {
        if (!lp(act).getBoolean("pending", false)) return
        lp(act).edit().putBoolean("pending", false).apply()
        val app = act.applicationContext; val w = web ?: return
        Thread {
            val txt = try { lifeFile(app).readText() } catch (e: Exception) { return@Thread }
            w.post {
                w.evaluateJavascript("window.ccNative&&ccNative.lifeRestoreStart()", null)
                var i = 0; while (i < txt.length) { w.evaluateJavascript("ccNative.lifeRestorePart(${org.json.JSONObject.quote(txt.substring(i, minOf(txt.length, i + 400_000)))})", null); i += 400_000 }
                w.evaluateJavascript("ccNative.lifeRestoreEnd()") { r -> android.widget.Toast.makeText(app, if (r?.contains("ok") == true) "🌱 Life data restored from the backup" else "Couldn't restore the Life data: $r", android.widget.Toast.LENGTH_LONG).show() }
            }
        }.start()
    }
}
