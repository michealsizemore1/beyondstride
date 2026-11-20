package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.WebView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * 🌱 Habits — the Command Center's Habits page as a BeyondStride screen: day picker, status colors, Today / Momentum /
 * Current streak / Perfect days, All · Due · Complete · Remaining + category filter, habit cards (status bar, badge,
 * why, ▲ ▼ ✎ 🗑, Complete / Partial / Skip or − +, streak · consistency · best, the week strip, Last 30 Days) and
 * Progress & Consistency (12-week heat map, habit performance).
 */
class LifeHabits(private val act: Activity, private val web: WebView, private val openFull: () -> Unit, private val changed: () -> Unit) {
    val view = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
    private var off = 0
    private var filter = "all"
    private var category = "all"
    private val open = HashSet<String>()
    private var data: JSONObject? = null
    private fun d(v: Int) = RunUi.dp(act, v)
    private val GREEN = 0xFF22C55E.toInt(); private val PURPLE = 0xFFA855F7.toInt(); private val SLATE = 0xFF64748B.toInt(); private val EMPTY = 0xFF2E3935.toInt()
    private val ORANGE = 0xFFF97316.toInt(); private val VIOLET = 0xFFA78BFA.toInt()
    private fun color(c: Char) = when (c) { 'd' -> GREEN; 'p' -> PURPLE; 's' -> SLATE; 'n' -> 0xFF1A211F.toInt(); else -> EMPTY }

    fun load() {
        web.evaluateJavascript("window.ccNative&&ccNative.habitsPage?ccNative.habitsPage($off):null") { r ->
            val j = try { (JSONTokener(r).nextValue() as? String)?.let { JSONObject(it) } } catch (e: Exception) { null } ?: return@evaluateJavascript
            data = j; draw()
        }
    }
    private fun act(js: String) = web.evaluateJavascript("window.ccNative&&$js") { load(); changed() }

    private fun draw() {
        val j = data ?: return
        view.removeAllViews()
        // Header
        view.addView(RunUi.text(act, "CONSISTENCY & MOMENTUM", ORANGE, 11f, true).apply { letterSpacing = 0.08f; setPadding(0, d(4), 0, 0) })
        view.addView(RunUi.text(act, "🌱 Habits", Color.WHITE, 22f, true))
        view.addView(RunUi.text(act, "A low-friction tracker built around consistency. Journal-linked habits are verified by their labeled source section.", RunUi.MUTED, 12f).apply { setPadding(0, d(2), 0, d(8)) })
        view.addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            fun nav(t: String, n: Int) = RunUi.text(act, t, Color.WHITE, 16f, true).apply { gravity = Gravity.CENTER; background = RunUi.rounded(RunUi.CARD, d(10).toFloat()); setPadding(d(14), d(8), d(14), d(8)); setOnClickListener { off += n; load() } }
            addView(nav("‹", -1))
            addView(RunUi.text(act, j.optString("label"), Color.WHITE, 14f, true).apply { gravity = Gravity.CENTER; background = RunUi.rounded(RunUi.CARD, d(10).toFloat()); setPadding(0, d(9), 0, d(9)); setOnClickListener { off = 0; load() } },
                LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = d(6); marginEnd = d(6) })
            addView(nav("›", 1))
            addView(RunUi.text(act, "+ Add Habit", Color.WHITE, 13f, true).apply { background = RunUi.rounded(0xFF5B21B6.toInt(), d(10).toFloat()); setPadding(d(12), d(9), d(12), d(9))
                setOnClickListener { web.evaluateJavascript("window.ccNative&&ccNative.habitForm('')") { openFull() } } }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = d(8) })
        })
        // Status colors
        view.addView(RunUi.card(act).apply {
            addView(RunUi.text(act, "Habit status colors", Color.WHITE, 13f, true))
            addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, d(6), 0, 0)
                listOf("Complete" to GREEN, "In progress" to PURPLE, "Skipped" to SLATE, "Not started" to EMPTY).forEach { (t, c) ->
                    addView(View(act).apply { background = RunUi.rounded(c, d(3).toFloat()) }, LinearLayout.LayoutParams(d(12), d(12)).apply { marginEnd = d(5); gravity = Gravity.CENTER_VERTICAL })
                    addView(RunUi.text(act, t, RunUi.MUTED, 12f).apply { setPadding(0, 0, d(12), 0) }) } })
        })
        // Summary tiles
        listOf(listOf("TODAY" to (j.optInt("done").toString() + "/" + j.optInt("due")) to "due habits complete", "MOMENTUM" to "${j.optInt("momentum")}%" to "90-day consistency"),
               listOf("CURRENT STREAK" to "${j.optInt("best")} days" to "best active streak", "PERFECT DAYS" to j.optInt("perfect").toString() to "in the last 30 days")).forEach { row ->
            view.addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, d(8), 0, 0)
                row.forEachIndexed { i, (tv, sub) -> addView(LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; background = RunUi.rounded(RunUi.CARD, d(14).toFloat()); setPadding(d(14), d(12), d(14), d(12))
                    addView(RunUi.text(act, tv.first, RunUi.MUTED, 11f, true)); addView(RunUi.text(act, tv.second, VIOLET, 24f, true)); addView(RunUi.text(act, sub, RunUi.DIM, 11f)) },
                    LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { if (i == 0) marginEnd = d(8) }) } })
        }
        // Filters
        val habits = j.optJSONArray("habits") ?: JSONArray()
        val all = (0 until habits.length()).map { habits.getJSONObject(it) }
        view.addView(RunUi.card(act).apply {
            addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL
                listOf("all" to "All", "due" to "Due", "complete" to "Complete", "remaining" to "Remaining").forEach { (k, t) ->
                    addView(RunUi.text(act, t, if (k == filter) Color.WHITE else RunUi.MUTED, 13f, true).apply {
                        setPadding(d(12), d(6), d(12), d(6)); background = RunUi.rounded(if (k == filter) 0xFF5B21B6.toInt() else 0xFF26302C.toInt(), d(16).toFloat())
                        setOnClickListener { filter = k; draw() } }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = d(6) }) } })
            val cats = listOf("all") + all.map { it.optString("category") }.distinct().sorted()
            addView(Spinner(act).apply {
                adapter = ArrayAdapter(act, android.R.layout.simple_spinner_dropdown_item, cats.map { if (it == "all") "All categories" else it })
                setSelection(cats.indexOf(category).coerceAtLeast(0), false)
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { if (cats[pos] != category) { category = cats[pos]; draw() } }
                    override fun onNothingSelected(p: AdapterView<*>?) {}
                }
            }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = d(6) })
        })
        val shown = all.filter { h ->
            (category == "all" || h.optString("category") == category) && when (filter) {
                "due" -> h.optBoolean("due"); "complete" -> h.optString("status") == "d"; "remaining" -> h.optBoolean("due") && h.optString("status") != "d"; else -> true }
        }
        if (shown.isEmpty()) view.addView(RunUi.card(act).apply { addView(RunUi.text(act, if (all.isEmpty()) "Build your first habit — tap + Add Habit." else "No habits match this view.", RunUi.MUTED, 13f)) })
        shown.forEachIndexed { idx, h -> view.addView(habitCard(h, idx, shown.size)) }
        view.addView(progressCard(all, j.optString("heat")))
    }

    private fun habitCard(h: JSONObject, idx: Int, n: Int): View {
        val id = JSONObject.quote(h.optString("id")); val st = h.optString("status")
        val outer = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; background = RunUi.rounded(RunUi.CARD, d(14).toFloat())
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = d(10) } }
        outer.addView(View(act).apply { background = RunUi.rounded(if (st == "o") 0xFF475569.toInt() else color(st[0]), d(3).toFloat()) }, LinearLayout.LayoutParams(d(5), MATCH_PARENT))
        val c = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(d(12), d(12), d(12), d(12)) }
        outer.addView(c, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val badge = when (st) { "d" -> "✓ Complete"; "p" -> "In progress"; "s" -> "Skipped"; else -> "Not started" }
        c.addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(RunUi.text(act, h.optString("name"), Color.WHITE, 16f, true).apply { setPadding(0, 0, d(8), 0) })
            addView(RunUi.text(act, badge, if (st == "o") RunUi.MUTED else Color.WHITE, 11f, true).apply { setPadding(d(8), d(2), d(8), d(2))
                background = RunUi.rounded(if (st == "o") 0xFF2E3935.toInt() else color(st[0]), d(10).toFloat()) }) })
        c.addView(RunUi.text(act, h.optString("meta"), RunUi.MUTED, 12f).apply { setPadding(0, d(3), 0, 0) })
        h.optString("why").takeIf { it.isNotBlank() }?.let { c.addView(RunUi.text(act, "“$it”", RunUi.MUTED, 12f).apply { setTypeface(typeface, Typeface.ITALIC); setPadding(0, d(3), 0, 0) }) }
        // ▲ ▼ ✎ 🗑
        c.addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END; setPadding(0, d(6), 0, d(6))
            fun b(t: String, enabled: Boolean, on: () -> Unit) = RunUi.text(act, t, Color.WHITE, 13f, true).apply { gravity = Gravity.CENTER; alpha = if (enabled) 1f else 0.3f
                background = RunUi.rounded(0xFF26302C.toInt(), d(8).toFloat()); setPadding(d(10), d(5), d(10), d(5)); if (enabled) setOnClickListener { on() } }
            val lp = { LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = d(6) } }
            addView(b("▲", idx > 0) { act("ccNative.habitMove($id,-1)") }, lp())
            addView(b("▼", idx < n - 1) { act("ccNative.habitMove($id,1)") }, lp())
            addView(b("✎", true) { web.evaluateJavascript("window.ccNative&&ccNative.habitForm($id)") { openFull() } }, lp())
            addView(b("🗑", true) { AlertDialog.Builder(act).setMessage("Delete \"${h.optString("name")}\" and its tracked history?").setPositiveButton("Delete") { _, _ -> act("ccNative.habitDelete($id)") }.setNegativeButton("Keep", null).show() }, lp()) })
        // Controls
        when (h.optString("kind")) {
            "check" -> c.addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL
                listOf(Triple("✓ Complete", "done", st == "d"), Triple("Partial", "partial", st == "p"), Triple("Skip", "skip", st == "s")).forEachIndexed { i, (t, k, on) ->
                    addView(RunUi.text(act, t, if (on) Color.WHITE else Color.WHITE, 13f, true).apply { gravity = Gravity.CENTER; setPadding(0, d(8), 0, d(8))
                        background = RunUi.rounded(if (on) color(k[0]) else 0xFF26302C.toInt(), d(10).toFloat()); setOnClickListener { act("ccNative.habitLogOn($off,$id,'$k')") } },
                        LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { if (i < 2) marginEnd = d(6) }) } })
            "count" -> c.addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                fun pm(t: String, dlt: Int) = RunUi.text(act, t, Color.WHITE, 18f, true).apply { gravity = Gravity.CENTER; background = RunUi.rounded(0xFF26302C.toInt(), d(10).toFloat()); setPadding(d(16), d(4), d(16), d(4)); setOnClickListener { act("ccNative.habitAdjustOn($off,$id,$dlt)") } }
                addView(pm("−", -1)); addView(RunUi.text(act, h.optString("value"), Color.WHITE, 15f, true).apply { gravity = Gravity.CENTER; setPadding(d(14), 0, d(14), 0) }); addView(pm("+", 1)) })
            else -> c.addView(RunUi.text(act, h.optString("value"), Color.WHITE, 15f, true).apply { setPadding(d(4), d(4), 0, d(4)) })
        }
        // Stats
        c.addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, d(10), 0, d(6))
            listOf("${h.optInt("streak")}d" to " streak", "${h.optInt("rate")}%" to " consistency", "${h.optInt("best")}d" to " best").forEach { (a, b) ->
                addView(RunUi.text(act, a, Color.WHITE, 12f, true)); addView(RunUi.text(act, b, RunUi.MUTED, 12f).apply { setPadding(0, 0, d(14), 0) }) } })
        // Week strip
        val week = h.optString("week")
        c.addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL
            listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, l -> addView(LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                addView(RunUi.text(act, l, RunUi.DIM, 10f).apply { gravity = Gravity.CENTER })
                addView(View(act).apply { background = RunUi.rounded(color(week.getOrElse(i) { 'o' }), d(4).toFloat()) }, LinearLayout.LayoutParams(MATCH_PARENT, d(8)).apply { topMargin = d(3) }) },
                LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = if (i < 6) d(4) else 0 }) } })
        // Last 30 days
        val key = h.optString("id"); val isOpen = key in open
        val panel = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; visibility = if (isOpen) View.VISIBLE else View.GONE; setPadding(0, d(6), 0, 0) }
        if (isOpen) {
            val g = GridLayout(act).apply { columnCount = 10 }
            h.optString("month").forEach { ch -> g.addView(View(act).apply { background = RunUi.rounded(color(ch), d(4).toFloat()) },
                GridLayout.LayoutParams().apply { width = 0; height = d(16); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); setMargins(d(2), d(2), d(2), d(2)) }) }
            panel.addView(g); panel.addView(RunUi.text(act, h.optString("monthText"), Color.WHITE, 12f, true).apply { setPadding(0, d(6), 0, 0) })
        }
        c.addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(d(10), d(8), d(10), d(8))
            background = RunUi.rounded(0xFF26302C.toInt(), d(10).toFloat())
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = d(10) }
            addView(RunUi.text(act, (if (isOpen) "⌄ " else "› ") + "Last 30 Days", Color.WHITE, 13f, true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(RunUi.text(act, if (isOpen) "Hide monthly view" else "Show monthly view", RunUi.MUTED, 12f))
            setOnClickListener { if (isOpen) open.remove(key) else open.add(key); draw() } })
        c.addView(panel)
        return outer
    }

    private fun progressCard(all: List<JSONObject>, heat: String) = RunUi.card(act).apply {
        addView(RunUi.text(act, "Progress & Consistency", VIOLET, 15f, true))
        addView(RunUi.text(act, "LAST 12 WEEKS", RunUi.MUTED, 11f, true).apply { setPadding(0, d(10), 0, d(6)) })
        // 7 rows (days) × 12 columns (weeks), oldest at the left.
        val g = GridLayout(act).apply { columnCount = 12; rowCount = 7 }
        for (r in 0 until 7) for (col in 0 until 12) {
            val ch = heat.getOrElse(col * 7 + r) { '0' }
            val c = when (ch) { '1' -> 0xFF14532D.toInt(); '2' -> 0xFF15803D.toInt(); '3' -> 0xFF22C55E.toInt(); else -> EMPTY }
            g.addView(View(act).apply { background = RunUi.rounded(c, d(3).toFloat()) }, GridLayout.LayoutParams(GridLayout.spec(r), GridLayout.spec(col, 1f)).apply { width = 0; height = d(14); setMargins(d(2), d(2), d(2), d(2)) })
        }
        addView(g)
        addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END or Gravity.CENTER_VERTICAL; setPadding(0, d(4), 0, 0)
            addView(RunUi.text(act, "Less ", RunUi.DIM, 10f))
            listOf(EMPTY, 0xFF14532D.toInt(), 0xFF15803D.toInt(), 0xFF22C55E.toInt()).forEach { c -> addView(View(act).apply { background = RunUi.rounded(c, d(2).toFloat()) }, LinearLayout.LayoutParams(d(10), d(10)).apply { marginEnd = d(3) }) }
            addView(RunUi.text(act, " More", RunUi.DIM, 10f)) })
        addView(RunUi.text(act, "HABIT PERFORMANCE", RunUi.MUTED, 11f, true).apply { setPadding(0, d(12), 0, d(4)) })
        addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL
            listOf("● Under 50%" to 0xFFDC2626.toInt(), "● 50–74%" to 0xFFD97706.toInt(), "● 75–100%" to 0xFF16A34A.toInt()).forEach { (t, c) -> addView(RunUi.text(act, t, c, 11f, true).apply { setPadding(0, 0, d(10), 0) }) } })
        all.forEach { h ->
            val rate = h.optInt("rate"); val col = if (rate < 50) 0xFFDC2626.toInt() else if (rate < 75) 0xFFD97706.toInt() else 0xFF16A34A.toInt()
            addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, d(8), 0, d(3))
                addView(RunUi.text(act, h.optString("name"), Color.WHITE, 13f), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)); addView(RunUi.text(act, "$rate%", col, 13f, true)) })
            val bar = FrameLayout(act).apply { background = RunUi.rounded(EMPTY, d(4).toFloat()) }
            val fill = View(act).apply { background = RunUi.rounded(col, d(4).toFloat()) }
            bar.addView(fill, FrameLayout.LayoutParams(0, d(7)))
            addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, d(7)))
            bar.post { fill.layoutParams = FrameLayout.LayoutParams(bar.width * rate / 100, d(7)); fill.requestLayout() }
        }
    }
}
