package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.WebView
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject

/**
 * 📖 Bible — the Command Center's Bible page: the purple Daily Bible Study card (Start study, Day, Fix reading, Plan,
 * Journal), 📖 Scripture Focus (✨ Suggest, the verse, ✨ Explain This Verse), 🎧 Bible Audio, 🎶 Worship (Guided Worship)
 * and ✨ AI Prayer (Guided Prayer), each with its check box.
 */
class LifeBible(private val act: Activity, private val web: WebView, private val openFull: () -> Unit,
                private val startStudy: () -> Unit, private val needKey: () -> Unit) {
    val view = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
    private fun d(v: Int) = RunUi.dp(act, v)
    private val PURPLE = 0xFFC4B5FD.toInt(); private val HEAD = 0xFFA78BFA.toInt()

    // Hero
    private val heroStatus = RunUi.text(act, "", Color.WHITE, 13f)
    private val heroPassages = RunUi.text(act, "", Color.WHITE, 13f)
    private val heroMemory = RunUi.text(act, "", Color.WHITE, 13f)
    private val heroGo = LifeUi.pill(act, "Start study ›", Color.WHITE, 0xFF4C1D95.toInt(), 14f) { startStudy() }
    private val heroRow = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
    // Cards
    private val boxes = HashMap<String, TextView>(); private val states = HashMap<String, String>()
    private val verse = RunUi.text(act, "", PURPLE, 17f).apply { setTypeface(Typeface.SERIF, Typeface.ITALIC) }
    private val verseField = LifeUi.field(act, "Type or paste a verse / Daily Bread reference...") { v ->
        web.evaluateJavascript("window.ccNative&&ccNative.setVerse(${JSONObject.quote(v)})") { load() } }
    private val explain = result(); private val worship = result(); private val prayer = result()
    private var polls = 0

    private fun result() = RunUi.text(act, "", Color.WHITE, 13f).apply { visibility = View.GONE; setLineSpacing(0f, 1.25f)
        background = RunUi.rounded(0xFF241B3A.toInt(), d(10).toFloat()); setPadding(d(12), d(10), d(12), d(10)) }
    private fun card() = RunUi.card(act).apply { layoutParams = LifeUi.lp(d(12)) }
    private fun header(c: LinearLayout, key: String, title: String, extra: View? = null) {
        val b = LifeUi.box(act); boxes[key] = b; LifeUi.paintBox(b, "")
        b.setOnClickListener { val st = LifeUi.CYCLE[(LifeUi.CYCLE.indexOf(states[key] ?: "") + 1) % LifeUi.CYCLE.size]; states[key] = st; LifeUi.paintBox(b, st)
            web.evaluateJavascript("window.ccNative&&ccNative.setCheck(${JSONObject.quote(key)},${JSONObject.quote(st)})", null) }
        c.addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(b, LinearLayout.LayoutParams(d(28), d(28)))
            addView(RunUi.text(act, title, HEAD, 13f, true).apply { setPadding(d(10), 0, d(6), 0); letterSpacing = 0.03f }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            if (extra != null) addView(extra) })
    }
    private fun hint(t: String) = RunUi.text(act, t, 0xFFC4B5FD.toInt(), 12f).apply { setPadding(0, d(8), 0, 0) }
    private fun ai(kind: String) {
        web.evaluateJavascript("window.ccNative&&ccNative.bibleAI(${JSONObject.quote(kind)})") { r ->
            if (r.contains("nokey")) { needKey(); return@evaluateJavascript }
            polls = 40; LifeUi.ui.postDelayed(poll, 400)
        }
    }
    private val poll: Runnable = object : Runnable { override fun run() { load(); if (--polls > 0) LifeUi.ui.postDelayed(this, 1500) } }

    init {
        // 📖 Daily Bible Study
        view.addView(LinearLayout(act).apply { orientation = LinearLayout.VERTICAL
            background = LifeUi.gradient(0xFF4C1D95.toInt(), 0xFF7C3AED.toInt(), d(14).toFloat()); setPadding(d(16), d(14), d(16), d(14))
            layoutParams = LifeUi.lp(d(8))
            addView(RunUi.text(act, "📖 Daily Bible Study", Color.WHITE, 18f, true))
            addView(heroStatus.apply { setPadding(0, d(2), 0, 0) }); addView(heroPassages.apply { setPadding(0, d(2), 0, 0) })
            addView(heroGo, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = d(10) })
            addView(RunUi.text(act, "Prayer → Read & listen → Study (SOAP) → Worship → Prayer. Ticks your four checks below as you go.", Color.WHITE, 12f).apply { alpha = 0.9f; setPadding(0, d(8), 0, 0) })
            addView(heroMemory.apply { background = RunUi.rounded(0x24FFFFFF, d(9).toFloat()); setPadding(d(10), d(7), d(10), d(7)); visibility = View.GONE }, LifeUi.lp(d(8)))
            addView(heroRow, LifeUi.lp(d(8)))
        })
        // 📖 Scripture Focus
        view.addView(card().apply {
            header(this, "spDailyBread", "📖 SCRIPTURE FOCUS (Daily Bread)", RunUi.text(act, "✨ Suggest", Color.WHITE, 12f, true).apply {
                background = LifeUi.gradient(0xFF581C87.toInt(), 0xFFEA580C.toInt(), d(14).toFloat()); setPadding(d(10), d(5), d(10), d(5)); setOnClickListener { ai("suggest") } })
            addView(verse.apply { setPadding(0, d(10), 0, d(8)) })
            addView(verseField, LifeUi.lp())
            addView(hint("💡 Let the word dwell in you. Read slowly, pause, listen, and respond."))
            addView(LifeUi.pill(act, "✨ Explain This Verse", 0xFF7C3AED.toInt(), Color.WHITE, 12f) { ai("explain") }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = d(8) })
            addView(explain, LifeUi.lp(d(8)))
        })
        // 🎧 Bible Audio
        view.addView(card().apply {
            header(this, "spBibleAudio", "🎧 BIBLE AUDIO")
            addView(hint("💡 Tracked on its own now, separate from Worship below, and rolled into the weekly Spirituality summary."))
        })
        // 🎶 Worship
        view.addView(card().apply {
            header(this, "spWorship", "🎶 WORSHIP")
            addView(LifeUi.pill(act, "🎶 Guided Worship", 0xFF3B2A66.toInt(), 0xFFDDD6FE.toInt(), 13f) { ai("worship") }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = d(10) })
            addView(worship, LifeUi.lp(d(8)))
        })
        // ✨ AI Prayer
        view.addView(card().apply {
            header(this, "spAIPrayer", "✨ AI PRAYER")
            addView(LifeUi.pill(act, "🙏 Guided Prayer", 0xFF3B2A66.toInt(), 0xFFDDD6FE.toInt(), 13f) { ai("prayer") }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = d(10) })
            addView(prayer, LifeUi.lp(d(8)))
        })
    }

    fun load() {
        web.evaluateJavascript("window.ccNative&&ccNative.biblePage?JSON.stringify({info:(window.bibleStudy&&bibleStudy.info?bibleStudy.info():null),page:JSON.parse(ccNative.biblePage())}):null") { r ->
            val j = LifeUi.obj(r) ?: return@evaluateJavascript
            j.optJSONObject("info")?.let { fillHero(it) }
            val p = j.optJSONObject("page") ?: return@evaluateJavascript
            p.optJSONObject("checks")?.let { c -> boxes.forEach { (k, b) -> val st = c.optString(k); states[k] = st; LifeUi.paintBox(b, st) } }
            verse.text = p.optString("verse"); LifeUi.setIfIdle(verseField, p.optString("text"))
            fun res(t: TextView, s: String) { t.text = s; t.visibility = if (s.isBlank()) View.GONE else View.VISIBLE }
            res(explain, p.optString("explain")); res(worship, p.optString("worship")); res(prayer, p.optString("prayer"))
        }
    }
    private fun fillHero(b: JSONObject) {
        val st = b.optString("status")
        heroStatus.text = android.text.SpannableStringBuilder().apply {
            val s = when (st) { "done" -> "✓ Done today"; "progress" -> "In progress"; else -> "Today" }
            append(s); setSpan(android.text.style.StyleSpan(Typeface.BOLD), 0, s.length, 0); append(" · " + b.optString("label")) }
        heroPassages.text = b.optString("passages")
        heroGo.text = when (st) { "done" -> "Review study"; "progress" -> "Continue ›"; else -> "Start study ›" }
        val mem = b.optString("memory"); heroMemory.text = "🧠 Memory verse this week: $mem"; heroMemory.visibility = if (mem.isBlank()) View.GONE else View.VISIBLE
        heroRow.removeAllViews()
        val btns = mutableListOf<Pair<String, String>>()
        if (b.optString("planType") == "oneyear") { btns += "📅 Day: ${b.optString("day")}" to "day"; btns += "✏️ Fix reading" to "fix" }
        btns += "📚 ${b.optString("planLabel").ifBlank { "Plan" }}" to "plan"; btns += "🗂️ Journal" to "journal"
        btns.chunked(3).forEach { row -> heroRow.addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, d(6))
            row.forEach { (t, a) -> addView(LifeUi.pill(act, t, 0x29FFFFFF, Color.WHITE, 12f) {
                web.evaluateJavascript("window.ccNative&&ccNative.bibleHero(${JSONObject.quote(a)})") { openFull() } }.apply { setPadding(d(11), d(7), d(11), d(7)) },
                LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = d(6) }) } }) }
    }
}
