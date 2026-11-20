package io.github.michealsizemore1.beyondstride.run

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.JavascriptInterface
import org.json.JSONObject
import org.json.JSONTokener

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
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var nativeScroll: ScrollView
    private lateinit var nativeCol: LinearLayout
    private lateinit var tabToday: TextView
    private lateinit var tabFull: TextView
    private var full = false
    private var focusCard: FocusCard? = null

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
        // The Command Center tells us when it saves (auto-checks, edits in the full view) so the native screens update.
        web.addJavascriptInterface(object {
            @JavascriptInterface fun changed() { ui.removeCallbacks(reload); ui.postDelayed(reload, 400) }
        }, "BSNative")
        if (savedInstanceState != null) web.restoreState(savedInstanceState)
        else {
            // The #app=bs link part tells the Command Center where BeyondStride's daily summaries are (same as
            // Settings → Link the Command Center review), so the review, steps and wellness work here too.
            val base = RunSettings.ccUrl(this).substringBefore("#")
            web.loadUrl(base + (NutritionSync.linkPart(this).takeIf { it.isNotEmpty() }?.let { "#app=bs$it" } ?: ""))
        }
        // Native sections (BeyondStride screens on the Command Center's own data) over the full website.
        nativeCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(RunUi.dp(this@LifeActivity, 14), 0, RunUi.dp(this@LifeActivity, 14), RunUi.dp(this@LifeActivity, 24)) }
        nativeScroll = ScrollView(this).apply { setBackgroundColor(RunUi.BG); isFillViewport = true; addView(nativeCol) }
        val stack = FrameLayout(this).apply {
            addView(web, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(nativeScroll, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
        fun pill(label: String, onClick: () -> Unit) = RunUi.text(this, label, Color.WHITE, 13f, true).apply {
            gravity = android.view.Gravity.CENTER; setPadding(0, RunUi.dp(this@LifeActivity, 8), 0, RunUi.dp(this@LifeActivity, 8))
            setOnClickListener { onClick() }
        }
        tabToday = pill("🎯 Today") { show(false) }; tabFull = pill("📓 Full Command Center") { show(true) }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setBackgroundColor(RunUi.BG)
            setPadding(RunUi.dp(this@LifeActivity, 14), RunUi.dp(this@LifeActivity, 6), RunUi.dp(this@LifeActivity, 14), RunUi.dp(this@LifeActivity, 6))
            addView(tabToday, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = RunUi.dp(this@LifeActivity, 6) })
            addView(tabFull, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f))
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(RunUi.BG)
            addView(bar)
            addView(stack, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            // Stay below the status bar.
            setOnApplyWindowInsetsListener { v, insets ->
                @Suppress("DEPRECATION") val t = if (android.os.Build.VERSION.SDK_INT >= 30) insets.getInsets(android.view.WindowInsets.Type.statusBars()).top else insets.systemWindowInsetTop
                v.setPadding(0, t, 0, 0); insets
            }
        }
        buildNative()
        show(savedInstanceState?.getBoolean("full") ?: false)
        setContentView(RunUi.tabbed(this, box, RunUi.TAB_LIFE))
    }

    override fun onResume() { super.onResume(); web.onResume(); if (!full) { ui.removeCallbacks(reload); ui.postDelayed(reload, 300) } }
    override fun onPause() { web.onPause(); super.onPause() }
    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); web.saveState(out); out.putBoolean("full", full) }
    override fun onDestroy() { ui.removeCallbacksAndMessages(null); web.destroy(); super.onDestroy() }

    private fun show(f: Boolean) {
        full = f
        nativeScroll.visibility = if (f) View.GONE else View.VISIBLE   // the website stays loaded underneath either way
        fun style(t: TextView, on: Boolean) { t.background = RunUi.rounded(if (on) RunUi.ACCENT else RunUi.CARD, RunUi.dp(this, 20).toFloat()); t.setTextColor(if (on) Color.BLACK else Color.WHITE) }
        style(tabToday, !f); style(tabFull, f)
        if (!f) reload.run()
    }

    // ── Native sections ──
    private fun buildNative() {
        nativeCol.removeAllViews()
        nativeCol.addView(RunUi.text(this, "Your Command Center, as BeyondStride screens. Changes save to the Command Center right away and sync like always.", RunUi.MUTED, 12f).apply { setPadding(0, RunUi.dp(this@LifeActivity, 6), 0, 0) })
        focusCard = FocusCard().also { nativeCol.addView(it.view) }
        nativeCol.addView(RunUi.text(this, "Everything else is in 📓 Full Command Center (tap above). More sections will move here one at a time.", RunUi.DIM, 11f).apply { setPadding(0, RunUi.dp(this@LifeActivity, 12), 0, 0) })
    }

    private var tries = 0
    private val reload: Runnable = Runnable {
        web.evaluateJavascript("(window.ccNative&&typeof load==='function')?ccNative.focus():null") { r ->
            val json = try { (JSONTokener(r).nextValue() as? String)?.let { JSONObject(it) } } catch (e: Exception) { null }
            if (json == null) { focusCard?.loading(); if (tries++ < 60) ui.postDelayed(reload, 700) ; return@evaluateJavascript }
            tries = 0; focusCard?.fill(json)
        }
    }
    private fun js(call: String) = web.evaluateJavascript("window.ccNative&&$call", null)

    /** 🎯 Today's Focus — Priority Tasks + Goals, the same six lines and checks as the Command Center. */
    private inner class FocusCard {
        val view = RunUi.card(this@LifeActivity)
        private val count = RunUi.text(this@LifeActivity, "", RunUi.MUTED, 13f, true)
        private val status = RunUi.text(this@LifeActivity, "Loading your Command Center…", RunUi.MUTED, 12f)
        private val boxes = HashMap<String, TextView>(); private val edits = HashMap<String, EditText>(); private val states = HashMap<String, String>()
        private val ORDER = listOf("", "green", "black", "purple", "red")
        private fun d(v: Int) = RunUi.dp(this@LifeActivity, v)
        init {
            view.addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
                addView(RunUi.text(this@LifeActivity, "🎯 Today's Focus", Color.WHITE, 16f, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(count) })
            view.addView(status)
            group("✅ Priority Tasks", 0xFF818CF8.toInt(), listOf("pt0", "pt1", "pt2"), "Task")
            group("🎯 Goals", 0xFFA78BFA.toInt(), listOf("dg0", "dg1", "dg2"), "Goal")
            view.addView(RunUi.text(this@LifeActivity, "Tap a box to change it: ✓ green done · purple partly · red not done · black N/A · empty not marked.", RunUi.DIM, 10f).apply { setPadding(0, d(8), 0, 0) })
        }
        private fun group(title: String, color: Int, ids: List<String>, word: String) {
            view.addView(RunUi.text(this@LifeActivity, title, color, 13f, true).apply { setPadding(0, d(10), 0, d(2)) })
            ids.forEachIndexed { i, id ->
                val box = TextView(this@LifeActivity).apply { gravity = android.view.Gravity.CENTER; textSize = 16f; setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setOnClickListener { val n = ORDER[(ORDER.indexOf(states[id] ?: "") + 1) % ORDER.size]; paint(id, n); js("ccNative.setState(${JSONObject.quote(id)},${JSONObject.quote(n)})"); recount() } }
                val et = EditText(this@LifeActivity).apply {
                    hint = "$word ${i + 1}…"; setTextColor(Color.WHITE); setHintTextColor(RunUi.DIM); textSize = 15f; isSingleLine = true
                    imeOptions = EditorInfo.IME_ACTION_DONE; background = null; setPadding(d(10), d(6), 0, d(6))
                    val commit = Runnable { js("ccNative.setText(${JSONObject.quote(id)},${JSONObject.quote(text.toString())})") }
                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                        override fun afterTextChanged(s: Editable?) { if (hasFocus()) { ui.removeCallbacks(commit); ui.postDelayed(commit, 800) } }
                    })
                    setOnFocusChangeListener { _, has -> if (!has) { ui.removeCallbacks(commit); commit.run() } }
                    setOnEditorActionListener { v, _, _ -> v.clearFocus(); false }
                }
                boxes[id] = box; edits[id] = et; paint(id, "")
                view.addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, d(3), 0, d(3))
                    addView(box, LinearLayout.LayoutParams(d(32), d(32)))
                    addView(et, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) })
            }
        }
        private fun paint(id: String, st: String) {
            states[id] = st; val b = boxes[id] ?: return
            val c = when (st) { "green" -> 0xFF22C55E; "purple" -> 0xFFA855F7; "red" -> 0xFFEF4444; "black" -> 0xFF0B0F0D; else -> 0 }.toInt()
            b.background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = d(8).toFloat(); setColor(c); setStroke(d(2), if (st.isEmpty()) RunUi.MUTED else if (st == "black") 0xFF6B7280.toInt() else c) }
            b.text = if (st.isEmpty()) "" else "✓"; b.setTextColor(Color.WHITE)
            edits[id]?.alpha = if (st == "green") 0.6f else 1f
        }
        private fun recount() { count.text = "${states.values.count { it == "green" }}/6" }
        fun loading() { status.visibility = View.VISIBLE }
        fun fill(j: JSONObject) {
            status.visibility = View.GONE
            val items = j.optJSONArray("items") ?: return
            for (i in 0 until items.length()) { val o = items.getJSONObject(i); val id = o.optString("id")
                paint(id, o.optString("state"))
                edits[id]?.let { if (!it.hasFocus() && it.text.toString() != o.optString("text")) it.setText(o.optString("text")) } }
            recount()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 41) { fileCb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data)); fileCb = null; return }
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
    }

    // Back goes back a page in the full Command Center first, then to the native Today view.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (full && web.canGoBack()) web.goBack()
        else if (full) show(false)
        else @Suppress("DEPRECATION") super.onBackPressed()
    }
}
