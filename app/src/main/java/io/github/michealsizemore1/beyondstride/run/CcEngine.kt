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

}
