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
    private var checksCard: ChecksCard? = null
    private var scheduleCard: ScheduleCard? = null
    private var reviewCard: ReviewCard? = null

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

    override fun onResume() { super.onResume(); web.onResume(); web.evaluateJavascript("window.ccSync&&ccSync.now()", null); if (!full) { ui.removeCallbacks(reload); ui.postDelayed(reload, 300) } }
    // Upload anything not yet sent before the app goes to the background.
    override fun onPause() { web.evaluateJavascript("window.ccSync&&ccSync.now()", null); web.onPause(); super.onPause() }
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
        nativeCol.addView(RunUi.text(this, "Your Command Center, as BeyondStride screens. Changes save to the Command Center right away and sync with Chrome and your computer.", RunUi.MUTED, 12f).apply { setPadding(0, RunUi.dp(this@LifeActivity, 6), 0, 0) })
        focusCard = FocusCard().also { nativeCol.addView(it.view) }
        scheduleCard = ScheduleCard().also { nativeCol.addView(it.view) }
        checksCard = ChecksCard().also { nativeCol.addView(it.view) }
        reviewCard = ReviewCard().also { nativeCol.addView(it.view) }
        nativeCol.addView(RunUi.text(this, "Everything else is in 📓 Full Command Center (tap above). More sections will move here one at a time.", RunUi.DIM, 11f).apply { setPadding(0, RunUi.dp(this@LifeActivity, 12), 0, 0) })
    }

    private var tries = 0
    private var refreshed = false
    private val reload: Runnable = Runnable {
        web.evaluateJavascript("(window.ccNative&&typeof load==='function')?(ccNative.all?ccNative.all():ccNative.focus()):null") { r ->
            val json = try { (JSONTokener(r).nextValue() as? String)?.let { JSONObject(it) } } catch (e: Exception) { null }
            if (json == null) { focusCard?.loading(); if (tries++ < 60) ui.postDelayed(reload, 700) ; return@evaluateJavascript }
            tries = 0; focusCard?.fill(json); scheduleCard?.fill(json); checksCard?.fill(json); reviewCard?.fill(json)
            // An older copy of the Command Center is still cached (the website updates before this screen can use the
            // new parts): fetch the newest once.
            if ((!json.has("checks") || !json.has("schedule") || !json.has("review")) && !refreshed) { refreshed = true; web.clearCache(true); web.reload(); ui.postDelayed(reload, 2500) }
        }
    }
    private fun js(call: String) = web.evaluateJavascript("window.ccNative&&$call", null)

    /** A Command Center check box: empty, green ✓, black N/A, purple partly, red not done. */
    private fun paintBox(b: TextView, st: String) {
        val d = RunUi.dp(this, 1)
        val c = when (st) { "green" -> 0xFF22C55E; "purple" -> 0xFFA855F7; "red" -> 0xFFEF4444; "black" -> 0xFF0B0F0D; else -> 0 }.toInt()
        b.background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 8f * d; setColor(c); setStroke(2 * d, if (st.isEmpty()) RunUi.MUTED else if (st == "black") 0xFF6B7280.toInt() else c) }
        b.text = if (st.isEmpty()) "" else "✓"; b.setTextColor(Color.WHITE)
    }
    private val CYCLE = listOf("", "green", "black", "purple", "red")

    /** 🕒 My Schedule — today's timeline, progress and what's next, from the Command Center. */
    private inner class ScheduleCard {
        val view = RunUi.card(this@LifeActivity)
        private val head = RunUi.text(this@LifeActivity, "", RunUi.MUTED, 13f, true)
        private val nextLine = RunUi.text(this@LifeActivity, "", RunUi.ACCENT, 13f, true)
        private val barFill = View(this@LifeActivity)
        private val bar = FrameLayout(this@LifeActivity)
        private val body = LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.VERTICAL }
        private var sig = ""
        private fun d(v: Int) = RunUi.dp(this@LifeActivity, v)
        init {
            view.addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
                addView(RunUi.text(this@LifeActivity, "🕒 My Schedule", Color.WHITE, 16f, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(head) })
            bar.background = RunUi.rounded(0xFF2A3430.toInt(), d(4).toFloat())
            barFill.background = RunUi.rounded(RunUi.ACCENT, d(4).toFloat())
            bar.addView(barFill, FrameLayout.LayoutParams(0, d(8)))
            view.addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, d(8)).apply { topMargin = d(8) })
            view.addView(nextLine.apply { setPadding(0, d(8), 0, d(2)) })
            view.addView(body)
            view.addView(RunUi.text(this@LifeActivity, "Tap the circle: done → not done → not marked. Edit times, add or reorder items in 📓 Full Command Center.", RunUi.DIM, 10f).apply { setPadding(0, d(8), 0, 0) })
        }
        private fun paintDot(b: TextView, st: String, partial: Boolean) {
            val c = when { st == "green" -> 0xFF22C55E; st == "red" -> 0xFFEF4444; partial -> 0xFFA855F7; else -> 0 }.toInt()
            b.background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(c); setStroke(d(2), if (c == 0) RunUi.MUTED else c) }
            b.text = when { st == "green" -> "✓"; st == "red" -> "✕"; partial -> "–"; else -> "" }; b.setTextColor(Color.WHITE)
        }
        fun fill(j: JSONObject) {
            val arr = j.optJSONArray("schedule") ?: return
            val items = (0 until arr.length()).map { arr.getJSONObject(it) }
            val track = items.filter { !it.optBoolean("fixed") }; val done = track.count { it.optBoolean("done") }
            head.text = "$done/${track.size}"
            val pct = if (track.isEmpty()) 0f else done.toFloat() / track.size
            bar.post { barFill.layoutParams = FrameLayout.LayoutParams((bar.width * pct).toInt(), d(8)); barFill.requestLayout() }
            val nx = items.firstOrNull { it.optBoolean("next") }
            nextLine.text = nx?.let { "Next: ${it.optString("time")} — ${it.optString("label")}" } ?: if (track.isNotEmpty() && done == track.size) "Schedule complete 🎉" else ""
            val newSig = items.joinToString("|") { it.toString() }
            if (newSig == sig) return; sig = newSig
            body.removeAllViews()
            items.forEach { o ->
                val fixed = o.optBoolean("fixed"); val isNext = o.optBoolean("next")
                val row = LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(d(8), d(7), d(8), d(7))
                    if (isNext) background = RunUi.rounded(0xFF26302C.toInt(), d(10).toFloat()) }
                row.addView(RunUi.text(this@LifeActivity, o.optString("time"), if (isNext) RunUi.ACCENT else RunUi.MUTED, 12f, true), LinearLayout.LayoutParams(d(68), ViewGroup.LayoutParams.WRAP_CONTENT))
                row.addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.VERTICAL
                    addView(RunUi.text(this@LifeActivity, o.optString("label"), if (fixed) RunUi.MUTED else Color.WHITE, 14f, true))
                    o.optString("detail").takeIf { it.isNotBlank() }?.let { addView(RunUi.text(this@LifeActivity, it, RunUi.DIM, 11f)) }
                    alpha = if (o.optBoolean("done") && !fixed) 0.65f else 1f
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                if (!fixed) {
                    val dot = TextView(this@LifeActivity).apply { gravity = android.view.Gravity.CENTER; textSize = 13f; setTypeface(typeface, android.graphics.Typeface.BOLD) }
                    paintDot(dot, o.optString("state"), o.optBoolean("partial"))
                    val id = o.optString("id"); var st = o.optString("state")
                    dot.setOnClickListener {
                        st = when (st) { "green" -> "red"; "red" -> ""; else -> "green" }; paintDot(dot, st, false)
                        js("ccNative.scheduleTap(${JSONObject.quote(id)})")
                    }
                    row.addView(dot, LinearLayout.LayoutParams(d(30), d(30)).apply { marginStart = d(8) })
                }
                body.addView(row)
            }
        }
    }

    /** 🤖 Daily AI Review — Wins, What to Improve, Focus for Tomorrow, Lessons Learned, and the Command Center's AI Fill. */
    private inner class ReviewCard {
        val view = RunUi.card(this@LifeActivity)
        private val note = RunUi.text(this@LifeActivity, "", RunUi.MUTED, 12f)
        private val fill = RunUi.button(this@LifeActivity, "✨ AI Fill Day Review", 0xFF7C3AED.toInt(), Color.WHITE) { aiFill() }
        private val save = RunUi.button(this@LifeActivity, "Save Review", RunUi.ACCENT) { js("ccNative.saveReview()"); ui.postDelayed(reload, 400) }
        private val edits = LinkedHashMap<String, EditText>()
        private var busy = false
        private fun d(v: Int) = RunUi.dp(this@LifeActivity, v)
        init {
            view.addView(RunUi.text(this@LifeActivity, "🤖 Daily AI Review", Color.WHITE, 16f, true))
            view.addView(note)
            view.addView(fill, LinearLayout.LayoutParams(MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = d(8) })
            listOf("dayRevWins" to "Wins Today", "dayRevImprove" to "What to Improve", "dayRevFocus" to "Focus for Tomorrow", "dayRevLessons" to "Lessons Learned").forEach { (id, label) ->
                view.addView(RunUi.text(this@LifeActivity, label, RunUi.ACCENT, 13f, true).apply { setPadding(0, d(12), 0, d(2)) })
                val et = EditText(this@LifeActivity).apply {
                    setTextColor(Color.WHITE); setHintTextColor(RunUi.DIM); textSize = 14f; minLines = 2; gravity = android.view.Gravity.TOP
                    hint = when (id) { "dayRevWins" -> "What went well today?"; "dayRevImprove" -> "Areas to work on…"; "dayRevFocus" -> "Top priorities…"; else -> "Key takeaways…" }
                    background = RunUi.rounded(0xFF26302C.toInt(), d(10).toFloat()); setPadding(d(10), d(8), d(10), d(8))
                    val commit = Runnable { js("ccNative.setReview(${JSONObject.quote(id)},${JSONObject.quote(text.toString())})") }
                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                        override fun afterTextChanged(s: Editable?) { if (hasFocus()) { ui.removeCallbacks(commit); ui.postDelayed(commit, 1000) } }
                    })
                    setOnFocusChangeListener { _, has -> if (!has) { ui.removeCallbacks(commit); commit.run() } }
                }
                edits[id] = et; view.addView(et)
            }
            view.addView(save, LinearLayout.LayoutParams(MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = d(10) })
            view.addView(RunUi.text(this@LifeActivity, "Typing saves on its own. After ✨ AI Fill, read it over and tap Save Review (same as the Command Center). It's still filled in automatically at night if you haven't.", RunUi.DIM, 10f).apply { setPadding(0, d(8), 0, 0) })
        }
        private fun aiFill() {
            web.evaluateJavascript("window.ccNative&&ccNative.reviewFill?ccNative.reviewFill():false") { r ->
                if (r.contains("nokey")) {
                    android.app.AlertDialog.Builder(this@LifeActivity).setTitle("AI key needed in the Command Center")
                        .setMessage("AI Fill uses the Command Center's own AI key, which stays in each browser and isn't synced. Add it once here: 📓 Full Command Center → Settings → Anthropic API key.\n\n(BeyondStride's own key stays in the app and is never given to the Command Center.)")
                        .setPositiveButton("Open Full Command Center") { _, _ -> show(true) }.setNegativeButton("Not now", null).show()
                    return@evaluateJavascript
                }
                if (r == "true") { busy = true; note.text = "✨ Writing your review…"; fill.isEnabled = false; ui.postDelayed(poll, 2000) }
            }
        }
        private val poll: Runnable = Runnable { reload.run(); if (busy) ui.postDelayed(pollAgain, 2000) }
        private val pollAgain: Runnable = Runnable { poll.run() }
        fun fill(j: JSONObject) {
            val r = j.optJSONObject("review") ?: return
            val wasBusy = busy; busy = r.optBoolean("busy")
            if (wasBusy && !busy) ui.removeCallbacks(pollAgain)
            fill.isEnabled = !busy; fill.alpha = if (busy) 0.5f else 1f
            val arr = r.optJSONArray("fields") ?: return
            var unsaved = false
            for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); val et = edits[o.optString("id")] ?: continue
                if (o.optBoolean("unsaved")) unsaved = true
                if (!et.hasFocus() && et.text.toString() != o.optString("value")) et.setText(o.optString("value")) }
            note.text = when { busy -> "✨ Writing your review…"; unsaved -> "Not saved yet — tap Save Review when it looks right."; !r.optBoolean("hasKey") -> "AI Fill needs the Command Center's AI key (one time)."; else -> "" }
            note.visibility = if (note.text.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    /** ✅ Daily Checks — Bible, Meditation, Listening, Wellness, Planning & Review: the Command Center's checkmarks. */
    private inner class ChecksCard {
        val view = RunUi.card(this@LifeActivity)
        private val summary = RunUi.text(this@LifeActivity, "", RunUi.MUTED, 12f)
        private val body = LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.VERTICAL }
        private var sig = ""
        private fun d(v: Int) = RunUi.dp(this@LifeActivity, v)
        init {
            view.addView(RunUi.text(this@LifeActivity, "✅ Daily Checks", Color.WHITE, 16f, true))
            view.addView(summary); view.addView(body)
            view.addView(RunUi.text(this@LifeActivity, "Automatic checks (BP, weight, sleep, steps, dog walk, audiobook…) still fill in on their own. Meds stays manual.", RunUi.DIM, 10f).apply { setPadding(0, d(8), 0, 0) })
        }
        private fun icon(g: String) = when { g.contains("Bible") -> "📖"; g.contains("Meditation") -> "🧘"; g.contains("Listening") -> ""; g.contains("Wellness") -> "💚"; g.contains("Planning") -> "📝"; else -> "•" }
        private fun nice(l: String) = l.replaceFirstChar { it.uppercase() }.replace(Regex("^Goal\\s+"), "")
        fun fill(j: JSONObject) {
            val arr = j.optJSONArray("checks") ?: run { summary.text = "Getting the newest Command Center…"; return }
            val items = (0 until arr.length()).map { arr.getJSONObject(it) }
            val n = { st: String -> items.count { it.optString("state") == st } }
            summary.text = "${n("green")} done · ${n("purple")} partly · ${n("red")} not done · ${n("black")} N/A · ${n("")} not marked"
            val newSig = items.joinToString("|") { it.optString("key") + it.optString("id") + ":" + it.optString("state") + ":" + it.optString("label") }
            if (newSig == sig) return; sig = newSig
            body.removeAllViews()
            items.groupBy { it.optString("group") }.forEach { (g, list) ->
                val done = list.count { it.optString("state") == "green" }
                body.addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, d(10), 0, d(2))
                    addView(RunUi.text(this@LifeActivity, (icon(g) + " " + g).trim(), RunUi.ACCENT, 13f, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(RunUi.text(this@LifeActivity, "$done/${list.size}", RunUi.MUTED, 12f, true)) })
                list.forEach { o ->
                    val key = o.optString("key"); val id = o.optString("id"); var st = o.optString("state")
                    val box = TextView(this@LifeActivity).apply { gravity = android.view.Gravity.CENTER; textSize = 14f; setTypeface(typeface, android.graphics.Typeface.BOLD) }
                    paintBox(box, st)
                    val row = LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, d(4), 0, d(4))
                        addView(box, LinearLayout.LayoutParams(d(28), d(28)))
                        addView(RunUi.text(this@LifeActivity, nice(o.optString("label")), Color.WHITE, 14f).apply { setPadding(d(10), 0, 0, 0) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
                    row.setOnClickListener {
                        st = if (id.isNotEmpty()) (if (st == "green") "" else "green") else CYCLE[(CYCLE.indexOf(st) + 1) % CYCLE.size]
                        paintBox(box, st)
                        if (id.isNotEmpty()) js("ccNative.setBox(${JSONObject.quote(id)},${st == "green"})")
                        else js("ccNative.setCheck(${JSONObject.quote(key)},${JSONObject.quote(st)})")
                    }
                    body.addView(row)
                }
            }
        }
    }

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
