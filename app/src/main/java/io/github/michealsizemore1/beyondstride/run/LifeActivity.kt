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
    private lateinit var tabReviews: TextView
    private lateinit var reviewsCol: LinearLayout
    private lateinit var reviewsScroll: ScrollView
    private var page = 0          // 0 🎯 Today · 1 📊 Reviews · 2 📓 Full Command Center
    private var monthCard: PeriodCard? = null
    private lateinit var study: BibleStudyPage
    private var calendar: LifeCalendar? = null
    private lateinit var bar: LinearLayout
    private var yearCard: PeriodCard? = null
    private var full = false
    private var focusCard: FocusCard? = null
    private var checksCard: ChecksCard? = null
    private var scheduleCard: ScheduleCard? = null
    private var reviewCard: ReviewCard? = null
    private var wellCard: WellnessCard? = null
    private var bibleCard: BibleCard? = null
    private var weekCard: WeeklyCard? = null
    private var journalCard: JournalCard? = null

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
        reviewsCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(RunUi.dp(this@LifeActivity, 14), 0, RunUi.dp(this@LifeActivity, 14), RunUi.dp(this@LifeActivity, 24)) }
        reviewsScroll = ScrollView(this).apply { setBackgroundColor(RunUi.BG); isFillViewport = true; addView(reviewsCol) }
        val stack = FrameLayout(this).apply {
            addView(web, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(nativeScroll, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(reviewsScroll, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            study = BibleStudyPage(this@LifeActivity, web) { showPage(0) }
            addView(study.view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
        fun pill(label: String, onClick: () -> Unit) = RunUi.text(this, label, Color.WHITE, 13f, true).apply {
            gravity = android.view.Gravity.CENTER; setPadding(0, RunUi.dp(this@LifeActivity, 8), 0, RunUi.dp(this@LifeActivity, 8))
            setOnClickListener { onClick() }
        }
        tabToday = pill("🎯 Today") { showPage(0) }; tabReviews = pill("📊 Reviews") { showPage(1) }; tabFull = pill("📓 Full") { showPage(2) }
        bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setBackgroundColor(RunUi.BG)
            setPadding(RunUi.dp(this@LifeActivity, 14), RunUi.dp(this@LifeActivity, 6), RunUi.dp(this@LifeActivity, 14), RunUi.dp(this@LifeActivity, 6))
            addView(tabToday, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = RunUi.dp(this@LifeActivity, 6) })
            addView(tabReviews, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.1f).apply { marginEnd = RunUi.dp(this@LifeActivity, 6) })
            addView(tabFull, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f))
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
        showPage((savedInstanceState?.getInt("page") ?: 0).let { if (it == 3) 0 else it })
        setContentView(RunUi.tabbed(this, box, RunUi.TAB_LIFE))
    }

    override fun onResume() { super.onResume(); web.onResume(); Thread { try { CalendarReminders.refresh(applicationContext) } catch (e: Exception) { } }.start(); web.evaluateJavascript("window.ccSync&&ccSync.now()", null); if (!full) { ui.removeCallbacks(reload); ui.postDelayed(reload, 300) } }
    // Upload anything not yet sent before the app goes to the background.
    override fun onPause() { study.stopReading(); web.evaluateJavascript("window.ccSync&&ccSync.now()", null); web.onPause(); super.onPause() }
    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); web.saveState(out); out.putInt("page", page) }
    override fun onDestroy() { ui.removeCallbacksAndMessages(null); study.destroy(); web.destroy(); super.onDestroy() }

    private fun show(f: Boolean) = showPage(if (f) 2 else 0)
    private fun showPage(p: Int) {
        val was = page; page = p; full = p == 2
        if (was == 3 && p != 3) study.hide()
        nativeScroll.visibility = if (p == 0) View.VISIBLE else View.GONE   // the website stays loaded underneath either way
        study.view.visibility = if (p == 3) View.VISIBLE else View.GONE
        bar.visibility = if (p == 3) View.GONE else View.VISIBLE
        reviewsScroll.visibility = if (p == 1) View.VISIBLE else View.GONE
        fun style(t: TextView, on: Boolean) { t.background = RunUi.rounded(if (on) RunUi.ACCENT else RunUi.CARD, RunUi.dp(this, 20).toFloat()); t.setTextColor(if (on) Color.BLACK else Color.WHITE) }
        style(tabToday, p == 0); style(tabReviews, p == 1); style(tabFull, p == 2)
        if (p == 0 || p == 1) reload.run()
        if (p == 1) { monthCard?.load(); yearCard?.load() }
    }

    // ── Native sections ──
    private fun buildNative() {
        nativeCol.removeAllViews()
        nativeCol.addView(RunUi.text(this, "Your Command Center, as BeyondStride screens. Changes save to the Command Center right away and sync with Chrome and your computer.", RunUi.MUTED, 12f).apply { setPadding(0, RunUi.dp(this@LifeActivity, 6), 0, 0) })
        calendar = LifeCalendar(this, web).also { nativeCol.addView(it.view) }
        focusCard = FocusCard().also { nativeCol.addView(it.view) }
        scheduleCard = ScheduleCard().also { nativeCol.addView(it.view) }
        checksCard = ChecksCard().also { nativeCol.addView(it.view) }
        wellCard = WellnessCard().also { nativeCol.addView(it.view) }
        bibleCard = BibleCard().also { nativeCol.addView(it.view) }
        reviewCard = ReviewCard().also { nativeCol.addView(it.view) }
        journalCard = JournalCard().also { nativeCol.addView(it.view) }
        reviewsCol.removeAllViews()
        weekCard = WeeklyCard().also { reviewsCol.addView(it.view) }
        monthCard = PeriodCard("monthly").also { reviewsCol.addView(it.view) }
        yearCard = PeriodCard("yearly").also { reviewsCol.addView(it.view) }
        reviewsCol.addView(RunUi.text(this, "Charts, the dashboard and the injury roll-up are in 📓 Full.", RunUi.DIM, 11f).apply { setPadding(0, RunUi.dp(this@LifeActivity, 12), 0, 0) })
        nativeCol.addView(RunUi.text(this, "Weekly, monthly and yearly reviews are in 📊 Reviews. Everything else is in 📓 Full. More sections will move here one at a time.", RunUi.DIM, 11f).apply { setPadding(0, RunUi.dp(this@LifeActivity, 12), 0, 0) })
    }

    private var tries = 0
    private var refreshed = false
    private val reload: Runnable = Runnable {
        web.evaluateJavascript("(window.ccNative&&typeof load==='function')?(ccNative.all?ccNative.all():ccNative.focus()):null") { r ->
            val json = try { (JSONTokener(r).nextValue() as? String)?.let { JSONObject(it) } } catch (e: Exception) { null }
            if (json == null) { focusCard?.loading(); if (tries++ < 60) ui.postDelayed(reload, 700) ; return@evaluateJavascript }
            tries = 0; focusCard?.fill(json); if (page == 0) calendar?.load(); scheduleCard?.fill(json); checksCard?.fill(json); wellCard?.fill(json); bibleCard?.fill(json); reviewCard?.fill(json); journalCard?.fill(json); weekCard?.fill(json)
            // An older copy of the Command Center is still cached (the website updates before this screen can use the
            // new parts): fetch the newest once.
            if ((!json.has("checks") || !json.has("schedule") || !json.has("review") || !json.has("wellness") || !json.has("weekly") || !json.has("journal")) && !refreshed) { refreshed = true; web.clearCache(true); web.reload(); ui.postDelayed(reload, 2500) }
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

    private fun needKey() {
        // Check what the Command Center in this tab actually has, so the message can say why.
        val diag = "(function(){var n=0;try{for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);n+=k.length+(localStorage.getItem(k)||'').length;}}catch(e){}" +
            "var ok=false;try{localStorage.setItem('__bs_probe','x');localStorage.removeItem('__bs_probe');ok=true;}catch(e){}" +
            "return JSON.stringify({key:!!String(localStorage.getItem('ai_key')||'').trim(),pct:Math.round(n/5242880*100),canSave:ok});})()"
        web.evaluateJavascript(diag) { r ->
            val j = try { JSONObject(JSONTokener(r).nextValue() as String) } catch (e: Exception) { JSONObject() }
            val full = !j.optBoolean("canSave", true) || j.optInt("pct") >= 98
            val msg = when {
                j.optBoolean("key") -> "The key is saved here now — try again."
                full -> "The Command Center's storage in this tab is full (${j.optInt("pct")}%), so the key couldn't be saved. " +
                    "Your data is safe (it's also in Chrome and the cloud copy). Tell Claude \"Life tab storage is full\" and it will make room."
                else -> "This tab doesn't have the key yet. The key typed into Chrome doesn't carry over — add it here:\n\n" +
                    "Open Settings below → AI Connection → Anthropic API key → paste → Save Settings (at the bottom).\n\n(BeyondStride's own key stays in the app and is never given to the Command Center.)"
            }
            android.app.AlertDialog.Builder(this).setTitle(if (j.optBoolean("key")) "AI key found" else "AI key needed in the Command Center").setMessage(msg)
                .setPositiveButton(if (j.optBoolean("key")) "OK" else "Open Settings") { _, _ ->
                    if (!j.optBoolean("key")) { show(true); web.evaluateJavascript("try{openSettings();setTimeout(function(){var e=document.getElementById('journalAnthropicKey');if(e){e.scrollIntoView({block:'center'});e.focus();}},300)}catch(e){}", null) }
                }.setNegativeButton("Not now", null).show()
        }
    }

    /** 📖 Bible Study + 🧘 Meditation — today's reading, progress, memory verse; the guided session / timer open full screen. */
    private inner class BibleCard {
        val view = RunUi.card(this@LifeActivity)
        private val status = RunUi.text(this@LifeActivity, "", RunUi.ACCENT, 13f, true)
        private val passages = RunUi.text(this@LifeActivity, "", Color.WHITE, 15f, true)
        private val memory = RunUi.text(this@LifeActivity, "", RunUi.MUTED, 12f)
        private val go = RunUi.button(this@LifeActivity, "Start study ›", 0xFF7C3AED.toInt(), Color.WHITE) {
            study.open { r -> when (r) {
                "ok" -> showPage(3)
                "nokey" -> needKey()
                // An older Command Center (or a reading it can't find): use its own screen.
                else -> web.evaluateJavascript("window.ccNative&&ccNative.bibleOpen?ccNative.bibleOpen():false") { x -> if (x.contains("nokey")) needKey() else if (x == "true") show(true) }
            } } }
        private val medLine = RunUi.text(this@LifeActivity, "", Color.WHITE, 14f)
        private val medWeek = RunUi.text(this@LifeActivity, "", RunUi.MUTED, 12f)
        private val medBox = TextView(this@LifeActivity)
        private fun d(v: Int) = RunUi.dp(this@LifeActivity, v)
        init {
            view.addView(RunUi.text(this@LifeActivity, "📖 Daily Bible Study", Color.WHITE, 16f, true))
            view.addView(status.apply { setPadding(0, d(4), 0, 0) }); view.addView(passages.apply { setPadding(0, d(2), 0, 0) }); view.addView(memory.apply { setPadding(0, d(4), 0, 0) })
            view.addView(go, LinearLayout.LayoutParams(MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = d(8) })
            view.addView(RunUi.text(this@LifeActivity, "Prayer → Read & listen → Study → Worship → Prayer, right here in the app. Ticks your Bible checks as you go.", RunUi.DIM, 10f).apply { setPadding(0, d(6), 0, 0) })
            view.addView(View(this@LifeActivity).apply { setBackgroundColor(0xFF2A3430.toInt()) }, LinearLayout.LayoutParams(MATCH_PARENT, d(1)).apply { topMargin = d(12); bottomMargin = d(10) })
            view.addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
                medBox.gravity = android.view.Gravity.CENTER; medBox.textSize = 14f
                addView(medBox, LinearLayout.LayoutParams(d(28), d(28)))
                addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(d(10), 0, 0, 0)
                    addView(RunUi.text(this@LifeActivity, "🧘 Meditation", Color.WHITE, 15f, true)); addView(medLine); addView(medWeek) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) })
            view.addView(RunUi.button(this@LifeActivity, "Start meditation ›", 0xFF374151.toInt(), Color.WHITE) {
                web.evaluateJavascript("window.ccNative&&ccNative.meditOpen?ccNative.meditOpen():false") { r -> if (r == "true") show(true) } }, LinearLayout.LayoutParams(MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = d(8) })
        }
        fun fill(j: JSONObject) {
            j.optJSONObject("bible")?.let { b ->
                val st = b.optString("status")
                status.text = (when (st) { "done" -> "✓ Done today"; "progress" -> "In progress" + (b.optString("step").takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""); else -> "Today" }) + " · " + b.optString("label")
                passages.text = b.optString("passages")
                memory.text = b.optString("memory").takeIf { it.isNotBlank() }?.let { "🧠 Memory verse this week: $it" } ?: ""
                memory.visibility = if (memory.text.isEmpty()) View.GONE else View.VISIBLE
                go.text = when (st) { "done" -> "Review study"; "progress" -> "Continue ›"; else -> "Start study ›" }
            }
            j.optJSONObject("meditation")?.let { m ->
                paintBox(medBox, m.optString("state"))
                medLine.text = m.optString("line")
                medWeek.text = "This week: ${m.optInt("weekSessions")} session${if (m.optInt("weekSessions") == 1) "" else "s"} · ${m.optInt("weekMins")} min"
            }
        }
    }

    /** ✍️ Daily Journal — today's entry (the AI reviews read it too). */
    private inner class JournalCard {
        val view = RunUi.card(this@LifeActivity)
        private val et = EditText(this@LifeActivity)
        private fun d(v: Int) = RunUi.dp(this@LifeActivity, v)
        init {
            view.addView(RunUi.text(this@LifeActivity, "✍️ Daily Journal", Color.WHITE, 16f, true))
            et.apply {
                hint = "Write whatever's on your mind today…"; setTextColor(Color.WHITE); setHintTextColor(RunUi.DIM); textSize = 15f
                minLines = 5; gravity = android.view.Gravity.TOP
                background = RunUi.rounded(0xFF26302C.toInt(), d(10).toFloat()); setPadding(d(12), d(10), d(12), d(10))
                val commit = Runnable { js("ccNative.setJournal(${JSONObject.quote(text.toString())})") }
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun afterTextChanged(s: Editable?) { if (hasFocus()) { ui.removeCallbacks(commit); ui.postDelayed(commit, 1200) } }
                })
                setOnFocusChangeListener { _, has -> if (!has) { ui.removeCallbacks(commit); commit.run() } }
            }
            view.addView(et, LinearLayout.LayoutParams(MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = d(8) })
            view.addView(RunUi.text(this@LifeActivity, "Saves as you type. Past entries, search and the archive are in 📓 Full Command Center.", RunUi.DIM, 10f).apply { setPadding(0, d(6), 0, 0) })
        }
        fun fill(j: JSONObject) {
            if (!j.has("journal")) { view.visibility = View.GONE; return }
            view.visibility = View.VISIBLE
            val t = j.optString("journal"); if (!et.hasFocus() && et.text.toString() != t) et.setText(t)
        }
    }

    /** 📆 Monthly / 📅 Yearly — the habit heat map (month) and the Fitness / Wellness / Spirituality tables, ‹ › to move. */
    private inner class PeriodCard(val kind: String) {
        val view = RunUi.card(this@LifeActivity)
        private var off = 0
        private val label = RunUi.text(this@LifeActivity, "", Color.WHITE, 14f, true).apply { gravity = android.view.Gravity.CENTER }
        private val body = LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.VERTICAL }
        private fun d(v: Int) = RunUi.dp(this@LifeActivity, v)
        private fun arrow(t: String, n: Int) = RunUi.text(this@LifeActivity, t, RunUi.ACCENT, 22f, true).apply { setPadding(d(14), 0, d(14), 0); setOnClickListener { off += n; load() } }
        init {
            view.addView(RunUi.text(this@LifeActivity, if (kind == "monthly") "📆 Monthly" else "🗓️ Yearly", Color.WHITE, 16f, true))
            view.addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, d(4), 0, 0)
                addView(arrow("‹", -1)); addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); addView(arrow("›", 1)) })
            view.addView(body)
        }
        fun load() {
            web.evaluateJavascript("window.ccNative&&ccNative.period?ccNative.period('$kind',$off):null") { r ->
                val j = try { (JSONTokener(r).nextValue() as? String)?.let { JSONObject(it) } } catch (e: Exception) { null } ?: return@evaluateJavascript
                fill(j)
            }
        }
        private fun fill(j: JSONObject) {
            label.text = j.optString("label"); body.removeAllViews()
            j.optString("heat").takeIf { it.isNotEmpty() }?.let { heat(it) }
            val secs = j.optJSONArray("sections") ?: return
            for (i in 0 until secs.length()) { val sec = secs.getJSONObject(i)
                body.addView(RunUi.text(this@LifeActivity, sec.optString("title"), RunUi.ACCENT, 13f, true).apply { setPadding(0, d(12), 0, d(4)) })
                body.addView(table(sec.optJSONArray("head"), sec.optJSONArray("rows")))
            }
        }
        private fun heat(h: String) {
            body.addView(RunUi.text(this@LifeActivity, "Habit completion (Bible audio · prayer · exercise)", RunUi.MUTED, 12f).apply { setPadding(0, d(8), 0, d(4)) })
            val grid = android.widget.GridLayout(this@LifeActivity).apply { columnCount = 7 }
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { grid.addView(RunUi.text(this@LifeActivity, it, RunUi.DIM, 11f, true).apply { gravity = android.view.Gravity.CENTER }, android.widget.GridLayout.LayoutParams().apply { width = d(40); height = d(20) }) }
            var day = 0
            h.forEach { c ->
                val cell = TextView(this@LifeActivity).apply { gravity = android.view.Gravity.CENTER; textSize = 11f }
                if (c != 'e') { day++; cell.text = day.toString() }
                val col = when (c) { 'e' -> 0; 'f' -> 0xFF1F2725.toInt(); '1' -> 0xFF365314.toInt(); '2' -> 0xFF65A30D.toInt(); '3' -> 0xFFA3E635.toInt(); else -> 0xFF2A3430.toInt() }
                cell.setTextColor(if (c == '3') Color.BLACK else if (c == 'f') RunUi.DIM else Color.WHITE)
                if (col != 0) cell.background = RunUi.rounded(col, d(6).toFloat())
                grid.addView(cell, android.widget.GridLayout.LayoutParams().apply { width = d(36); height = d(30); setMargins(d(2), d(2), d(2), d(2)) })
            }
            body.addView(grid)
        }
        private fun table(head: org.json.JSONArray?, rows: org.json.JSONArray?): View {
            val t = android.widget.TableLayout(this@LifeActivity)
            fun row(cells: org.json.JSONArray, header: Boolean, total: Boolean) = android.widget.TableRow(this@LifeActivity).apply {
                for (k in 0 until cells.length()) addView(RunUi.text(this@LifeActivity, cells.optString(k), if (header) RunUi.MUTED else if (k == 0 || total) RunUi.ACCENT else Color.WHITE, 12f, header || total || k == 0).apply { setPadding(d(6), d(4), d(10), d(4)) })
            }
            head?.let { t.addView(row(it, true, false)) }
            if (rows != null) for (i in 0 until rows.length()) { val r = rows.getJSONArray(i); t.addView(row(r, false, r.optString(0).startsWith("TOTAL"))) }
            return android.widget.HorizontalScrollView(this@LifeActivity).apply { addView(t) }
        }
    }

    /** 📅 Weekly Review — this week's numbers, Wins / Improve / Focus / Lessons, AI Fill; ‹ › for other weeks. */
    private inner class WeeklyCard {
        val view = RunUi.card(this@LifeActivity)
        private val label = RunUi.text(this@LifeActivity, "", Color.WHITE, 14f, true).apply { gravity = android.view.Gravity.CENTER }
        private val stats = RunUi.text(this@LifeActivity, "", RunUi.MUTED, 12f)
        private val note = RunUi.text(this@LifeActivity, "", RunUi.MUTED, 12f)
        private val fillBtn = RunUi.button(this@LifeActivity, "✨ AI Fill Review", 0xFF7C3AED.toInt(), Color.WHITE) { aiFill() }
        private val edits = LinkedHashMap<String, EditText>()
        private var busy = false
        private fun d(v: Int) = RunUi.dp(this@LifeActivity, v)
        private fun arrow(t: String, n: Int) = RunUi.text(this@LifeActivity, t, RunUi.ACCENT, 22f, true).apply { setPadding(d(14), 0, d(14), 0)
            setOnClickListener { web.evaluateJavascript("window.ccNative&&ccNative.weeklyShift($n)") { reload.run() } } }
        init {
            view.addView(RunUi.text(this@LifeActivity, "📅 Weekly Review", Color.WHITE, 16f, true))
            view.addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, d(4), 0, 0)
                addView(arrow("‹", -1)); addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); addView(arrow("›", 1)) })
            view.addView(stats); view.addView(note)
            view.addView(fillBtn, LinearLayout.LayoutParams(MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = d(8) })
            listOf("revWins" to "Wins This Week", "revImprove" to "What to Improve", "revFocus" to "Focus for Next Week", "revLessons" to "Lessons Learned").forEach { (id, lab) ->
                view.addView(RunUi.text(this@LifeActivity, lab, RunUi.ACCENT, 13f, true).apply { setPadding(0, d(12), 0, d(2)) })
                val et = EditText(this@LifeActivity).apply {
                    setTextColor(Color.WHITE); setHintTextColor(RunUi.DIM); textSize = 14f; minLines = 2; gravity = android.view.Gravity.TOP; hint = "…"
                    background = RunUi.rounded(0xFF26302C.toInt(), d(10).toFloat()); setPadding(d(10), d(8), d(10), d(8))
                    val commit = Runnable { js("ccNative.setWeekly(${JSONObject.quote(id)},${JSONObject.quote(text.toString())})") }
                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                        override fun afterTextChanged(s: Editable?) { if (hasFocus()) { ui.removeCallbacks(commit); ui.postDelayed(commit, 1000) } }
                    })
                    setOnFocusChangeListener { _, has -> if (!has) { ui.removeCallbacks(commit); commit.run() } }
                }
                edits[id] = et; view.addView(et)
            }
            view.addView(RunUi.button(this@LifeActivity, "Save Review", RunUi.ACCENT) { js("ccNative.saveWeekly()"); ui.postDelayed(reload, 400) },
                LinearLayout.LayoutParams(MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = d(10) })
            view.addView(RunUi.text(this@LifeActivity, "Typing here saves the week's review. After ✨ AI Fill, read it over and tap Save Review. Charts and the injury roll-up are in 📓 Full Command Center → Review.", RunUi.DIM, 10f).apply { setPadding(0, d(8), 0, 0) })
        }
        private fun aiFill() {
            web.evaluateJavascript("window.ccNative&&ccNative.weeklyFill?ccNative.weeklyFill():false") { r ->
                if (r.contains("nokey")) needKey() else if (r == "true") { busy = true; note.text = "✨ Writing the week's review…"; fillBtn.isEnabled = false; ui.postDelayed(poll, 2500) } }
        }
        private val poll: Runnable = object : Runnable { override fun run() { reload.run(); if (busy) ui.postDelayed(this, 2500) } }
        fun fill(j: JSONObject) {
            val w = j.optJSONObject("weekly") ?: run { view.visibility = View.GONE; return }
            view.visibility = View.VISIBLE
            label.text = w.optString("label"); stats.text = w.optString("stats")
            busy = w.optBoolean("busy"); fillBtn.isEnabled = !busy; fillBtn.alpha = if (busy) 0.5f else 1f
            val arr = w.optJSONArray("fields") ?: return; var unsaved = false
            for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); val et = edits[o.optString("id")] ?: continue
                if (o.optBoolean("unsaved")) unsaved = true
                if (!et.hasFocus() && et.text.toString() != o.optString("value")) et.setText(o.optString("value")) }
            note.text = when { busy -> "✨ Writing the week's review…"; unsaved -> "Not saved yet — tap Save Review when it looks right."; else -> "" }
            note.visibility = if (note.text.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    /** 💚 Wellness — BP, meds, weight, sleep, dog walk, steps, bike: the check and its numbers, like the Wellness tab. */
    private inner class WellnessCard {
        val view = RunUi.card(this@LifeActivity)
        private val body = LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.VERTICAL }
        private val boxes = HashMap<String, TextView>(); private val states = HashMap<String, String>(); private val edits = HashMap<String, EditText>()
        private var built = ""
        private val count = RunUi.text(this@LifeActivity, "", RunUi.MUTED, 13f, true)
        private fun d(v: Int) = RunUi.dp(this@LifeActivity, v)
        init {
            view.addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
                addView(RunUi.text(this@LifeActivity, "💚 Wellness", Color.WHITE, 16f, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(count) })
            view.addView(body)
            view.addView(RunUi.text(this@LifeActivity, "Typing a value checks it off on its own (BP, weight, sleep, dog walk, 10,000+ steps) — same as the Command Center. Meds stays manual: tap its box.", RunUi.DIM, 10f).apply { setPadding(0, d(8), 0, 0) })
        }
        fun fill(j: JSONObject) {
            val arr = j.optJSONArray("wellness") ?: run { view.visibility = View.GONE; return }
            view.visibility = View.VISIBLE
            val rows = (0 until arr.length()).map { arr.getJSONObject(it) }.filter { it.optString("key") != "exBike" }   // Bike left off (Micheal, Nov 2026)
            val shape = rows.joinToString("|") { r -> r.optString("key") + ":" + (0 until r.getJSONArray("fields").length()).joinToString(",") { r.getJSONArray("fields").getJSONObject(it).optString("id") } }
            if (shape != built) { built = shape; build(rows) }
            rows.forEach { r ->
                val key = r.optString("key"); states[key] = r.optString("state"); boxes[key]?.let { paintBox(it, r.optString("state")) }
                val f = r.getJSONArray("fields")
                for (i in 0 until f.length()) { val o = f.getJSONObject(i); edits[o.optString("id")]?.let { if (!it.hasFocus() && it.text.toString() != o.optString("value")) it.setText(o.optString("value")) } }
            }
            count.text = "${rows.count { it.optString("state") == "green" }}/${rows.size}"
        }
        private fun build(rows: List<JSONObject>) {
            body.removeAllViews(); boxes.clear(); edits.clear()
            rows.forEach { r ->
                val key = r.optString("key")
                val box = TextView(this@LifeActivity).apply { gravity = android.view.Gravity.CENTER; textSize = 14f; setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setOnClickListener { val n = CYCLE[(CYCLE.indexOf(states[key] ?: "") + 1) % CYCLE.size]; states[key] = n; paintBox(this, n); js("ccNative.setCheck(${JSONObject.quote(key)},${JSONObject.quote(n)})") } }
                boxes[key] = box
                body.addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, d(10), 0, d(2))
                    addView(box, LinearLayout.LayoutParams(d(28), d(28)))
                    addView(RunUi.text(this@LifeActivity, r.optString("label"), Color.WHITE, 14f, true).apply { setPadding(d(10), 0, 0, 0) }) })
                val f = r.getJSONArray("fields")
                val line = LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(d(38), d(2), 0, 0) }
                for (i in 0 until f.length()) { val o = f.getJSONObject(i); val id = o.optString("id")
                    val et = EditText(this@LifeActivity).apply {
                        hint = if (f.length() > 1) o.optString("label") + " · " + o.optString("hint") else o.optString("hint")
                        setTextColor(Color.WHITE); setHintTextColor(RunUi.DIM); textSize = 14f; isSingleLine = true
                        inputType = if (o.optBoolean("number")) android.text.InputType.TYPE_CLASS_NUMBER else android.text.InputType.TYPE_CLASS_TEXT
                        imeOptions = EditorInfo.IME_ACTION_DONE
                        background = RunUi.rounded(0xFF26302C.toInt(), d(8).toFloat()); setPadding(d(10), d(7), d(10), d(7))
                        val commit = Runnable { js("ccNative.setField(${JSONObject.quote(id)},${JSONObject.quote(text.toString())})") }
                        addTextChangedListener(object : TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                            override fun afterTextChanged(s: Editable?) { if (hasFocus()) { ui.removeCallbacks(commit); ui.postDelayed(commit, 900) } }
                        })
                        setOnFocusChangeListener { _, has -> if (!has) { ui.removeCallbacks(commit); commit.run(); ui.postDelayed(reload, 1200) } }
                        setOnEditorActionListener { v, _, _ -> v.clearFocus(); false }
                    }
                    edits[id] = et
                    line.addView(et, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { if (i > 0) marginStart = d(6) })
                }
                body.addView(line)
            }
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
            view.addView(RunUi.text(this@LifeActivity, "Automatic checks (audiobook, Bible Study…) still fill in on their own.", RunUi.DIM, 10f).apply { setPadding(0, d(8), 0, 0) })
        }
        private fun icon(g: String) = when { g.contains("Bible") -> "📖"; g.contains("Meditation") -> "🧘"; g.contains("Listening") -> ""; g.contains("Wellness") -> "💚"; g.contains("Planning") -> "📝"; else -> "•" }
        private fun nice(l: String) = l.replaceFirstChar { it.uppercase() }.replace(Regex("^Goal\\s+"), "")
        fun fill(j: JSONObject) {
            val arr = j.optJSONArray("checks") ?: run { summary.text = "Getting the newest Command Center…"; return }
            val hasWell = j.has("wellness")   // Wellness has its own card (with the numbers) below
            val items = (0 until arr.length()).map { arr.getJSONObject(it) }.filter { !(hasWell && it.optString("group").contains("Wellness")) }
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
        else if (full || page == 1 || page == 3) show(false)
        else @Suppress("DEPRECATION") super.onBackPressed()
    }
}
