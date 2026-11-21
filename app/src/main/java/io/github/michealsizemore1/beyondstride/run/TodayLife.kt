package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject

/**
 * 🌱 Life card on the Today tab — the morning screen's piece of the Command Center: My Schedule (progress, what's next,
 * tick the next items), Today's Focus (priority tasks and goals, tap a box) and the day's checks by group.
 * Same data as Life → Daily (one shared page — see [CcEngine]); Life → Daily keeps the full detail. Phone only.
 */
class TodayLife(private val act: Activity) : CcEngine.Listener {
    private fun d(v: Int) = RunUi.dp(act, v)
    private val ui = LifeUi.ui
    private val head = RunUi.text(act, "", RunUi.MUTED, 13f, true)
    private val status = RunUi.text(act, "Loading your day…", RunUi.MUTED, 12f)
    private val bar = FrameLayout(act); private val barFill = View(act)
    private val nextLine = RunUi.text(act, "", RunUi.ACCENT, 14f, true)
    private val sched = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
    private val focus = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
    private val checks = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
    private var tries = 0
    /** Shown only when the Command Center's storage is 80%+ full. */
    private val storageWarn = RunUi.text(act, "", 0xFFF87171.toInt(), 12f, true).apply { visibility = View.GONE; setPadding(0, RunUi.dp(act, 10), 0, 0)
        setOnClickListener { act.startActivity(Intent(act, RunSettingsActivity::class.java).putExtra("page", "life")) } }
    private var sig = ""

    val view: LinearLayout = RunUi.card(act).apply {
        addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(RunUi.text(act, "🕒 My Schedule", Color.WHITE, 16f, true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(head) })
        addView(status)
        bar.background = RunUi.rounded(0xFF2A3430.toInt(), d(4).toFloat()); barFill.background = RunUi.rounded(RunUi.ACCENT, d(4).toFloat())
        bar.addView(barFill, FrameLayout.LayoutParams(0, d(8)))
        addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, d(8)).apply { topMargin = d(8) })
        addView(nextLine.apply { setPadding(0, d(8), 0, d(2)) })
        addView(sched)
        addView(focus)
        addView(checks)
        addView(storageWarn)
        addView(RunUi.text(act, "Open Life → Daily for the full schedule, calendar and the day's review ›", 0xFFA78BFA.toInt(), 12f, true).apply {
            setPadding(0, d(12), 0, 0); setOnClickListener { openLife() } })
    }

    private fun openLife() {
        act.startActivity(Intent(act, LifeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION))
        @Suppress("DEPRECATION") act.overridePendingTransition(0, 0)
    }

    /** Call from onResume (and it refreshes itself whenever the Command Center saves). */
    fun start() { CcEngine.add(this); CcEngine.get(act).onResume(); CcEngine.pushWellness(act); tries = 0; load() }
    fun stop() { CcEngine.remove(this); ui.removeCallbacks(retry); CcEngine.exportLife(act) }
    override fun changed() { ui.removeCallbacks(retry); ui.postDelayed(retry, 400) }
    private val retry = Runnable { load() }

    private fun load() {
        val web = CcEngine.get(act)
        web.evaluateJavascript("(window.ccNative&&ccNative.all&&typeof load==='function')?ccNative.all():null") { r ->
            val j = LifeUi.obj(r)
            if (j == null || !j.has("schedule")) { if (tries++ < 40) ui.postDelayed(retry, 1000); return@evaluateJavascript }
            status.visibility = View.GONE; CcEngine.ready(act); fill(j)
            CcEngine.storagePct(act).let { p -> storageWarn.text = "💾 Life storage is $p% full — tap to see what's taking the room"; storageWarn.visibility = if (p >= 80) View.VISIBLE else View.GONE }
        }
    }
    private fun js(call: String) = CcEngine.get(act).evaluateJavascript("window.ccNative&&$call", null)

    private fun dot(st: String, partial: Boolean) = TextView(act).apply { gravity = Gravity.CENTER; textSize = 13f; setTypeface(typeface, android.graphics.Typeface.BOLD); paintDot(this, st, partial) }
    private fun paintDot(b: TextView, st: String, partial: Boolean) {
        val c = when { st == "green" -> 0xFF22C55E; st == "red" -> 0xFFEF4444; partial -> 0xFFA855F7; else -> 0 }.toInt()
        b.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c); setStroke(d(2), if (c == 0) RunUi.MUTED else c) }
        b.text = when { st == "green" -> "✓"; st == "red" -> "✕"; partial -> "–"; else -> "" }; b.setTextColor(Color.WHITE)
    }
    private fun sub(t: String, c: Int) = RunUi.text(act, t, c, 13f, true).apply { setPadding(0, d(14), 0, d(4)) }

    private fun fill(j: JSONObject) {
        val s = (j.optJSONArray("schedule")?.toString() ?: "") + (j.optJSONArray("items")?.toString() ?: "") + (j.optJSONArray("checks")?.toString() ?: "")
        if (s == sig) return; sig = s
        // 🕒 My Schedule: progress, what's next, and the next three to tick.
        val arr = j.optJSONArray("schedule")
        val items = if (arr == null) emptyList() else (0 until arr.length()).map { arr.getJSONObject(it) }
        val track = items.filter { !it.optBoolean("fixed") }; val done = track.count { it.optBoolean("done") }
        head.text = "$done/${track.size}"
        val pct = if (track.isEmpty()) 0f else done.toFloat() / track.size
        bar.post { barFill.layoutParams = FrameLayout.LayoutParams((bar.width * pct).toInt(), d(8)); barFill.requestLayout() }
        val nx = items.firstOrNull { it.optBoolean("next") }
        nextLine.text = nx?.let { "Next: ${it.optString("time")} — ${it.optString("label")}" } ?: if (track.isNotEmpty() && done == track.size) "Schedule complete 🎉" else ""
        sched.removeAllViews()
        val ni = items.indexOfFirst { it.optBoolean("next") }.coerceAtLeast(0)
        val upcoming = items.drop(ni).filter { !it.optBoolean("fixed") && !it.optBoolean("done") }.take(3)
        upcoming.forEach { o ->
            val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(d(6), d(6), d(6), d(6)) }
            row.addView(RunUi.text(act, o.optString("time"), RunUi.MUTED, 12f, true), LinearLayout.LayoutParams(d(68), WRAP_CONTENT))
            row.addView(LinearLayout(act).apply { orientation = LinearLayout.VERTICAL
                addView(RunUi.text(act, o.optString("label"), Color.WHITE, 14f, true))
                o.optString("detail").takeIf { it.isNotBlank() }?.let { addView(RunUi.text(act, it, RunUi.DIM, 11f)) } }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            var st = o.optString("state"); val id = o.optString("id")
            val b = dot(st, o.optBoolean("partial"))
            b.setOnClickListener { st = when (st) { "green" -> "red"; "red" -> ""; else -> "green" }; paintDot(b, st, false); js("ccNative.scheduleTap(${JSONObject.quote(id)})") }
            row.addView(b, LinearLayout.LayoutParams(d(30), d(30)).apply { marginStart = d(8) })
            sched.addView(row)
        }
        // 🎯 Today's Focus: the six lines with their boxes (type them in Life → Daily).
        focus.removeAllViews()
        val f = j.optJSONArray("items")
        if (f != null) {
            val list = (0 until f.length()).map { f.getJSONObject(it) }
            val greens = list.count { it.optString("state") == "green" }
            focus.addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, d(14), 0, d(2))
                addView(RunUi.text(act, "🎯 Today's Focus", Color.WHITE, 16f, true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(RunUi.text(act, "$greens/${list.size}", RunUi.MUTED, 13f, true)) })
            val filled = list.filter { it.optString("text").isNotBlank() }
            if (filled.isEmpty()) focus.addView(RunUi.text(act, "No priorities or goals set yet — tap to add them in Life → Daily.", RunUi.MUTED, 12f).apply { setOnClickListener { openLife() } })
            listOf("pt" to ("✅ Priority Tasks" to 0xFF818CF8.toInt()), "dg" to ("🎯 Goals" to 0xFFA78BFA.toInt())).forEach { (pre, t) ->
                val g = filled.filter { it.optString("id").startsWith(pre) }; if (g.isEmpty()) return@forEach
                focus.addView(RunUi.text(act, t.first, t.second, 13f, true).apply { setPadding(0, d(8), 0, d(2)) })
                g.forEach { o ->
                    val id = o.optString("id"); var st = o.optString("state")
                    val box = LifeUi.box(act); LifeUi.paintBox(box, st)
                    val txt = RunUi.text(act, o.optString("text"), Color.WHITE, 14f).apply { alpha = if (st == "green") 0.6f else 1f; setPadding(d(10), 0, 0, 0) }
                    box.setOnClickListener { st = LifeUi.CYCLE[(LifeUi.CYCLE.indexOf(st) + 1) % LifeUi.CYCLE.size]; LifeUi.paintBox(box, st); txt.alpha = if (st == "green") 0.6f else 1f
                        js("ccNative.setState(${JSONObject.quote(id)},${JSONObject.quote(st)})") }
                    focus.addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, d(3), 0, d(3))
                        addView(box, LinearLayout.LayoutParams(d(28), d(28))); addView(txt, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)) })
                }
            }
        }
        // ✅ Checks by group (Bible, Wellness, Meditation, Listening…): done / total, tap for Life.
        checks.removeAllViews()
        val c = j.optJSONArray("checks")
        if (c != null) {
            val list = (0 until c.length()).map { c.getJSONObject(it) }.filter { !it.optString("group").contains("Planning") }
            if (list.isNotEmpty()) {
                checks.addView(sub("✅ Today's checks", Color.WHITE).apply { textSize = 16f })
                val groups = list.groupBy { it.optString("group") }
                groups.entries.chunked(2).forEach { row ->
                    checks.addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, d(4), 0, 0)
                        row.forEachIndexed { i, (g, l) ->
                            val dn = l.count { it.optString("state") == "green" }; val all = dn == l.size
                            addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                                background = RunUi.rounded(if (all) 0xFF14532D.toInt() else 0xFF26302C.toInt(), d(10).toFloat()); setPadding(d(10), d(8), d(10), d(8))
                                addView(RunUi.text(act, icon(g) + " " + g.replace(Regex("^Daily\\s+"), ""), Color.WHITE, 13f, true).apply { isSingleLine = true }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                                addView(RunUi.text(act, "$dn/${l.size}", if (all) 0xFF4ADE80.toInt() else RunUi.MUTED, 13f, true))
                                setOnClickListener { openLife() } }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { if (i == 0) marginEnd = d(6) })
                        }
                        if (row.size == 1) addView(View(act), LinearLayout.LayoutParams(0, 1, 1f)) })
                }
            }
        }
    }
    private fun icon(g: String) = when { g.contains("Bible") -> "📖"; g.contains("Meditation") -> "🧘"; g.contains("Listening") -> "🎧"; g.contains("Wellness") -> "❤️"; g.contains("Movement") || g.contains("Exercise") -> "🏃"; else -> "•" }
}
