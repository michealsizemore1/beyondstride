package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.util.Locale

/**
 * Activities — a copy of the Command Center's Activities log: source counts, Add / Expand / Collapse / Compare,
 * the stats bar, search · type · source · sort · dates · group by day, and every activity card with the same lines.
 */
class ActivitiesActivity : Activity() {
    private val S = ActivityStore
    private var search = ""; private var fType = ""; private var fSource = "all"; private var sort = "newest"
    private var from = ""; private var to = ""; private var groupByDay = false
    private var compare = false; private val chosen = HashSet<String>()
    private val collapsed = HashSet<String>()
    private lateinit var listBox: LinearLayout
    private lateinit var statsBox: LinearLayout
    private lateinit var showing: TextView
    private var scroller: ScrollView? = null
    private var logOpen = false
    private val panels = HashSet<String>()
    private var wrnOff = 0

    override fun onResume() {
        io.github.michealsizemore1.beyondstride.Theme.load(this, "runsettings")
        super.onResume(); render()
        Thread { val a = S.refresh(this); val b = PlanStore.refresh(this); if (a || b) runOnUiThread { if (!isFinishing) render() } }.start()
    }

    private fun dp(v: Int) = RunUi.dp(this, v)
    private fun txt(t: String, c: Int, sp: Float, bold: Boolean = false) = RunUi.text(this, t, c, sp, bold)

    private fun render() {
        val ctx = this
        val all = S.list(this)
        S.mileage(this)?.let { io.github.michealsizemore1.beyondstride.NutritionStore.setMileage(this, it.week, it.runs, it.month) }   // the watch's This Week
        S.watchSummary(this)?.let { io.github.michealsizemore1.beyondstride.NutritionStore.setActs(this, it) }   // the watch's Activities screen
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(40), dp(16), dp(32)) }
        col.addView(txt("🏃 Activities", Color.WHITE, 26f, true))

        // Count with source breakdown (G / St / S / G2 / BS / M), like the Command Center.
        if (all.isNotEmpty()) col.addView(FlowLayout(this, dp(5), dp(4)).apply {
            addView(txt("${all.size} activit${if (all.size == 1) "y" else "ies"}", RunUi.MUTED, 13f, true))
            val counts = all.groupingBy { S.str(it, "source").ifEmpty { "manual" }.lowercase() }.eachCount()
            listOf("garmin" to "G", "strava" to "St", "stryd" to "S", "gear2" to "G2", "watch" to "BS", "manual" to "M").forEach { (k, l) ->
                counts[k]?.let { n -> addView(txt("$l:$n", Color.WHITE, 10f, true).apply { background = RunUi.rounded(S.SOURCE_COLOR[k] ?: 0xFF888888.toInt(), dp(4).toFloat()); setPadding(dp(5), dp(1), dp(5), dp(1)) }) }
            }
        })
        if (S.fetchedAt(this) == 0L) col.addView(txt(if (NutritionSync.ready(this)) "Open the Command Center once (after reloading it) so it can share your Activities." else "Set up LiveTrack first — Activities come through the same database.", 0xFFF2C94C.toInt(), 12f).apply { setPadding(0, dp(6), 0, 0) })
        S.notice(this)?.let { (m, at) -> if (System.currentTimeMillis() - at < 15 * 60_000L) col.addView(txt("Command Center: $m", 0xFF7DD3FC.toInt(), 12f, true).apply { setPadding(0, dp(6), 0, 0) }) }
        if (all.isEmpty() && S.status(this).isNotEmpty()) col.addView(txt(S.status(this), RunUi.MUTED, 11f).apply { setPadding(0, dp(4), 0, 0) })

        // The Command Center's order: plans, mileage, zones, shoes, plan upload, today's workout, the log, archived plans.
        val plans = PlanStore.data(this)
        // The Command Center's arrangement: today's workout on top; the active / upcoming plans below the activities.
        planWaiting(col)
        plans.optJSONObject("current")?.let { todayWorkout(col, it, plans.optBoolean("collapsed")) }
        mileage(col, all)
        // Collapsible sections, most-used first: how training is going → records / race times → conditions →
        // notes → setup and gear (zones, shoes, plan upload). Each starts closed.
        ActivityAnalysis(this, panels) { render() }.apply {
            metrics(col, all); charts(col, all)   // race times, heat & humidity and training zones are on Fitness
        }
        weekNotes(col)
        shoes(col)
        uploadPlan(col)
        // 🏃 Activities: collapsed each time the screen opens, like the Command Center's Activities section.
        col.addView(txt((if (logOpen) "▼" else "▶") + "  🏃 Activities" + (if (all.isNotEmpty()) "  (${all.size})" else ""), 0xFFE67E22.toInt(), 17f, true).apply {
            background = RunUi.rounded(RunUi.CARD, dp(10).toFloat()); setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(16) }
            setOnClickListener { logOpen = !logOpen; render() }
        })
        val logFrom = col.childCount

        // Buttons.
        col.addView(FlowLayout(this, dp(6), dp(6)).apply {
            setPadding(0, dp(4), 0, 0)
            fun btn(t: String, bg: Int, fg: Int = Color.WHITE, go: () -> Unit) = addView(txt(t, fg, 13f, true).apply {
                background = RunUi.rounded(bg, dp(8).toFloat()); setPadding(dp(12), dp(7), dp(12), dp(7)); setOnClickListener { go() } })
            btn("+ Add", 0xFFE67E22.toInt()) { startActivity(Intent(ctx, ActivityEditActivity::class.java)) }
            btn("📥 Import ▾", 0xFF00B2E3.toInt()) { importMenu() }
            btn("💾 Export", 0xFF27AE60.toInt()) { exportCsv() }
            btn("🖨️ Print", 0xFF2980B9.toInt()) { printLog(all) }
            btn("⊞ Expand", RunUi.CARD) { collapsed.clear(); fill() }
            btn("⊟ Collapse", RunUi.CARD) { all.forEach { collapsed += S.idOf(it) }; fill() }
            btn(if (compare) "✖ Exit Compare" else "⚖️ Compare", if (compare) 0xFF4C1D95.toInt() else 0xFF7C3AED.toInt()) { compare = !compare; if (!compare) chosen.clear(); render() }
        })

        // Compare bar.
        if (compare) col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = RunUi.rounded(0xFF2E1F4F.toInt(), dp(10).toFloat()); setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }
            addView(txt("${chosen.size} selected", Color.WHITE, 13f, true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(txt("📊 Compare Now", Color.WHITE, 13f, true).apply { background = RunUi.rounded(0xFF7C3AED.toInt(), dp(8).toFloat()); setPadding(dp(10), dp(5), dp(10), dp(5)); setOnClickListener { showCompare() } })
            addView(txt("  Clear", RunUi.MUTED, 13f, true).apply { setOnClickListener { chosen.clear(); render() } })
        })

        // Stats bar (filtered).

        // Filters.
        col.addView(RunUi.card(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) }
            addView(EditText(ctx).apply {
                hint = "🔍 Search activities..."; setText(search); setSingleLine(); setTextColor(Color.WHITE); setHintTextColor(RunUi.MUTED); textSize = 15f
                background = RunUi.rounded(0xFF26302C.toInt(), dp(8).toFloat()); setPadding(dp(12), dp(8), dp(12), dp(8))
                addTextChangedListener(object : android.text.TextWatcher {
                    override fun afterTextChanged(e: android.text.Editable?) { search = e?.toString() ?: ""; fill() }
                    override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}
                    override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                })
            })
            fun pick(label: String, options: List<Pair<String, String>>, cur: String, set: (String) -> Unit) = txt((options.firstOrNull { it.first == cur }?.second ?: label) + " ▾", Color.WHITE, 13f).apply {
                background = RunUi.rounded(0xFF26302C.toInt(), dp(8).toFloat()); setPadding(dp(10), dp(7), dp(10), dp(7)); maxLines = 1
                setOnClickListener { AlertDialog.Builder(ctx).setTitle(label).setSingleChoiceItems(options.map { it.second }.toTypedArray(), options.indexOfFirst { it.first == cur }) { d, i -> set(options[i].first); d.dismiss(); render() }.show() }
            }
            addView(FlowLayout(ctx, dp(6), dp(6)).apply {
                setPadding(0, dp(8), 0, 0)
                addView(pick("Type", listOf("" to "All Types") + listOf("Run", "Ride", "Walk", "Swim", "Strength", "Rest Day", "Other").map { it to it }, fType) { fType = it })
                addView(pick("Source", listOf("all" to "All Sources", "watch" to "BeyondStride") + all.map { S.str(it, "source").ifEmpty { "manual" }.lowercase() }.distinct().filter { it != "watch" }.map { it to S.sourceLabel(it) }, fSource) { fSource = it })
                addView(pick("Sort", listOf("newest" to "📅 Newest", "oldest" to "📅 Oldest", "distance" to "📏 Distance ↓", "pace" to "🏃 Best Pace", "power" to "⚡ Power ↓",
                    "duration" to "⏳ Duration ↓", "heartrate" to "❤ Heart Rate ↓", "elevation" to "⛰ Elevation ↓"), sort) { sort = it })
            })
            addView(FlowLayout(ctx, dp(6), dp(6)).apply {
                setPadding(0, dp(8), 0, 0)
                fun date(label: String, cur: String, set: (String) -> Unit) = txt("$label ${cur.ifEmpty { "—" }}", Color.WHITE, 13f).apply {
                    background = RunUi.rounded(0xFF26302C.toInt(), dp(8).toFloat()); setPadding(dp(10), dp(7), dp(10), dp(7))
                    setOnClickListener {
                        val d = if (cur.isNotEmpty()) java.time.LocalDate.parse(cur) else java.time.LocalDate.now()
                        android.app.DatePickerDialog(ctx, { _, y, m, dd -> set(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, dd)); render() }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                    }
                }
                addView(date("From:", from) { from = it }); addView(date("To:", to) { to = it })
                if (from.isNotEmpty() || to.isNotEmpty()) addView(txt("✕ Clear", RunUi.MUTED, 13f, true).apply { setPadding(dp(6), dp(7), dp(6), dp(7)); setOnClickListener { from = ""; to = ""; render() } })
                addView(txt((if (groupByDay) "☑" else "☐") + " Group by day", Color.WHITE, 13f).apply { setPadding(dp(6), dp(7), dp(6), dp(7)); setOnClickListener { groupByDay = !groupByDay; fill() } })
            })
        })
        // Stats bar: totals of what the filters show, right above the list.
        statsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(statsBox, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) })
        showing = txt("", RunUi.MUTED, 12f).apply { setPadding(dp(2), dp(8), 0, 0) }
        col.addView(showing)
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(listBox)
        fill()
        if (!logOpen) for (i in logFrom until col.childCount) col.getChildAt(i).visibility = View.GONE
        plans.optJSONObject("current")?.let { planCard(col, it, "current") }
        plans.optJSONObject("upcoming")?.let { planCard(col, it, "upcoming") }
        archived(col, plans.optJSONArray("archived"))

        val keepY = scroller?.scrollY ?: 0
        val sv = ScrollView(this).apply { setBackgroundColor(RunUi.BG); addView(col, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)) }
        scroller = sv
        setContentView(RunUi.tabbed(this, sv, RunUi.TAB_RUNS))
        if (keepY > 0) sv.post { sv.scrollTo(0, keepY) }
    }

    private fun filtered(all: List<JSONObject>): List<JSONObject> {
        val q = search.lowercase()
        val r = all.filter { a ->
            val type = S.str(a, "type")
            if (fType.isNotEmpty()) { if (fType == "Run") { if (!S.isRun(type)) return@filter false } else if (type.lowercase() != fType.lowercase()) return@filter false }
            if (fSource != "all" && S.str(a, "source").ifEmpty { "manual" }.lowercase() != fSource) return@filter false
            if (q.isNotEmpty() && !(S.str(a, "title").lowercase().contains(q) || S.str(a, "date").contains(q) || type.lowercase().contains(q))) return@filter false
            val d = S.str(a, "date")
            if (from.isNotEmpty() && d.isNotEmpty() && d < from) return@filter false
            if (to.isNotEmpty() && d.isNotEmpty() && d > to) return@filter false
            true
        }
        return when (sort) {
            "oldest" -> r.sortedBy { S.str(it, "date") }
            "distance" -> r.sortedByDescending { S.num(it, "distance") }
            "power" -> r.sortedByDescending { S.num(it, "power") }
            "pace" -> r.sortedBy { S.paceSec(S.str(it, "pace")).takeIf { p -> p > 0 } ?: 999 }
            "duration" -> r.sortedByDescending { S.durSec(S.str(it, "duration")) }
            "heartrate" -> r.sortedByDescending { S.num(it, "heartRate") }
            "elevation" -> r.sortedByDescending { S.num(it, "elevGain") }
            else -> r.sortedByDescending { S.str(it, "date") }
        }
    }

    /** Stats bar + list (search / group by day redraw just this part). */
    private fun fill() {
        val all = S.list(this); val f = filtered(all)
        // Stats bar — same sums as the Command Center's calcStatsBar.
        statsBox.removeAllViews()
        if (f.isNotEmpty()) {
            val mi = f.sumOf { S.num(it, "distance") }; val sec = f.sumOf { S.durSec(S.str(it, "duration")) }; val cal = f.sumOf { S.num(it, "calories") }
            val paces = f.map { S.paceSec(S.str(it, "pace")) }.filter { it > 0 }; val pw = f.map { S.num(it, "power") }.filter { it > 0 }
            val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
            val time = if (h > 0) "${h}h ${m}m" else if (m > 0) (if (s > 0) "${m}m ${s}s" else "${m}m") else "—"
            val ap = if (paces.isNotEmpty()) paces.average().toInt() else 0
            val avgPw = if (pw.isNotEmpty() && pw.size >= Math.ceil(f.size * 0.25)) "${Math.round(pw.average())}W" else "—"
            val items = listOf((if (mi > 0) String.format(Locale.US, "%.1f", mi) else "—") to "Miles", time to "Time",
                (if (ap > 0) "${ap / 60}:${String.format(Locale.US, "%02d", ap % 60)}/mi" else "—") to "Avg Pace", avgPw to "Avg Power",
                (if (cal > 0) String.format(Locale.US, "%,d", cal.toInt()) else "—") to "Calories", "${f.size}" to "Activities")
            statsBox.addView(RunUi.card(this).apply {
                items.chunked(3).forEach { row ->
                    addView(LinearLayout(this@ActivitiesActivity).apply {
                        orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(4), 0, dp(4))
                        row.forEach { (v, l) -> addView(LinearLayout(this@ActivitiesActivity).apply {
                            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                            addView(txt(v, Color.WHITE, 17f, true).apply { gravity = Gravity.CENTER }); addView(txt(l, RunUi.MUTED, 11f).apply { gravity = Gravity.CENTER })
                        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)) }
                    })
                }
            })
        }
        showing.text = if (all.isEmpty()) "" else "Showing ${f.size} of ${all.size}"
        listBox.removeAllViews()
        if (all.isEmpty()) { listBox.addView(txt("No activities yet — tap + Add.", RunUi.MUTED, 14f).apply { gravity = Gravity.CENTER; setPadding(0, dp(30), 0, dp(30)) }); return }
        if (f.isEmpty()) { listBox.addView(txt("No activities match your filters.", RunUi.MUTED, 14f).apply { gravity = Gravity.CENTER; setPadding(0, dp(30), 0, dp(30)) }); return }
        if (groupByDay) {
            val groups = f.groupBy { S.str(it, "date").ifEmpty { "Unknown" } }
            val dates = groups.keys.sortedWith(if (sort == "oldest") naturalOrder() else reverseOrder())
            dates.take(120).forEach { d ->
                val acts = groups[d]!!
                val miles = acts.sumOf { S.num(it, "distance") }
                listBox.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; background = RunUi.rounded(0xFF3A2C14.toInt(), dp(8).toFloat()); setPadding(dp(10), dp(6), dp(10), dp(6))
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) }
                    addView(txt("📅 " + longDate(d), 0xFFE67E22.toInt(), 14f, true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                    if (miles > 0) addView(txt(String.format(Locale.US, "%.2f mi", miles), 0xFFD4A017.toInt(), 13f, true))
                })
                acts.forEach { listBox.addView(card(it, null, true)) }
            }
        } else f.take(300).forEach { listBox.addView(card(it, shortDate(S.str(it, "date")), false)) }
    }

    private fun shortDate(d: String) = try { java.time.LocalDate.parse(d).format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US)) } catch (e: Exception) { d }
    private fun longDate(d: String) = try { java.time.LocalDate.parse(d).format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US)) } catch (e: Exception) { d }

    private val ZONE_COLOR = mapOf("Z1" to 0xFF60A5FA.toInt(), "Z2" to 0xFF22C55E.toInt(), "Z3" to 0xFFEAB308.toInt(), "Z4" to 0xFFF97316.toInt(), "Z5" to 0xFFEF4444.toInt())

    private fun chip(text: CharSequence) = TextView(this).apply {
        this.text = text; textSize = 13f; setTextColor(Color.WHITE)
        background = RunUi.rounded(0xFF26302C.toInt(), dp(8).toFloat()); setPadding(dp(8), dp(4), dp(8), dp(4))
    }
    /** "📏 4.00 mi" with the number bold (and optionally colored). */
    private fun stat(pre: String, value: String, post: String, color: Int? = null, badge: String? = null): CharSequence {
        val sb = android.text.SpannableStringBuilder(pre)
        val st = sb.length; sb.append(value)
        sb.setSpan(android.text.style.StyleSpan(Typeface.BOLD), st, sb.length, 0)
        color?.let { sb.setSpan(android.text.style.ForegroundColorSpan(it), st, sb.length, 0) }
        sb.append(post)
        badge?.let { b -> sb.append(" "); val bs = sb.length; sb.append(" $b ")
            sb.setSpan(android.text.style.BackgroundColorSpan(ZONE_COLOR[b] ?: 0xFF888888.toInt()), bs, sb.length, 0)
            sb.setSpan(android.text.style.ForegroundColorSpan(Color.BLACK), bs, sb.length, 0); sb.setSpan(android.text.style.StyleSpan(Typeface.BOLD), bs, sb.length, 0) }
        return sb
    }

    private fun card(a: JSONObject, date: String?, inGroup: Boolean): View {
        val ctx = this
        val id = S.idOf(a)
        val x = S.extras(this, a.opt("id"))
        val type = S.str(a, "type"); val tc = S.TYPE_COLOR[type] ?: 0xFF95A5A6.toInt()
        val src = S.str(a, "source").ifEmpty { "manual" }.lowercase()
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8); if (inGroup) marginStart = dp(12) }
            background = RunUi.rounded(RunUi.CARD, dp(12).toFloat()); clipToOutline = true
            addView(View(ctx).apply { setBackgroundColor(tc) }, LinearLayout.LayoutParams(dp(4), MATCH_PARENT))
        }
        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10)) }
        outer.addView(inner, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = if (id in collapsed) View.GONE else View.VISIBLE }
        // Header.
        inner.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            if (compare) addView(android.widget.CheckBox(ctx).apply { isChecked = id in chosen; setOnCheckedChangeListener { _, c -> if (c) chosen += id else chosen -= id; render() } })
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    addView(txt(S.sourceLabel(src), Color.WHITE, 10f, true).apply { background = RunUi.rounded(S.SOURCE_COLOR[src] ?: 0xFF888888.toInt(), dp(4).toFloat()); setPadding(dp(5), dp(1), dp(5), dp(1)) })
                    addView(txt("  " + type.uppercase(), tc, 11f, true))
                })
                addView(txt(S.str(a, "title").ifEmpty { "Activity" }, Color.WHITE, 16f, true).apply { setPadding(0, dp(2), 0, 0) })
                date?.let { addView(txt("📅 $it", RunUi.MUTED, 12f)) }
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(txt("✏️ Edit", 0xFFC4B5FD.toInt(), 12f, true).apply {
                background = RunUi.rounded(0xFF3B2A5E.toInt(), dp(6).toFloat()); setPadding(dp(8), dp(3), dp(8), dp(3))
                setOnClickListener { startActivity(Intent(ctx, ActivityEditActivity::class.java).putExtra("id", id)) }
            })
            val chev = txt(if (id in collapsed) "  ▶" else "  ▼", RunUi.MUTED, 12f)
            addView(chev)
            setOnClickListener {
                val open = body.visibility != View.VISIBLE
                body.visibility = if (open) View.VISIBLE else View.GONE; chev.text = if (open) "  ▼" else "  ▶"
                if (open) collapsed -= id else collapsed += id
            }
        })
        inner.addView(body)

        // Performance row.
        val perf = FlowLayout(this, dp(6), dp(6)).apply { setPadding(0, dp(8), 0, 0) }
        val dist = S.num(a, "distance"); val pace = S.str(a, "pace"); val elev = S.num(a, "elevGain")
        if (dist > 0) perf.addView(chip(stat("📏 ", fmtNum(dist), " mi")))
        S.str(a, "duration").takeIf { it.isNotEmpty() }?.let { perf.addView(chip(stat("⏱ ", S.fmtDur(it), ""))) }
        if (pace.isNotEmpty()) perf.addView(chip(stat("🏃 ", pace, "/mi")))
        S.num(a, "power").takeIf { it > 0 }?.let { perf.addView(chip(stat("⚡ ", fmtNum(it), "W", badge = x.optString("pz").ifEmpty { null }))) }
        S.num(a, "heartRate").takeIf { it > 0 }?.let { perf.addView(chip(stat("❤ ", fmtNum(it), " bpm", badge = x.optString("hz").ifEmpty { null }))) }
        (S.num(a, "airPower").takeIf { it > 0 } ?: S.num(a, "wind").takeIf { it > 0 })?.let { perf.addView(chip(stat("💨 Air Power ", fmtNum(it), "%"))) }
        S.num(a, "cadence").takeIf { it > 0 }?.let { perf.addView(chip(stat("🦵 ", fmtNum(it), " spm"))) }
        S.num(a, "calories").takeIf { it > 0 }?.let { perf.addView(chip(stat("🔥 ", fmtNum(it), " cal"))) }
        if (elev > 0) perf.addView(chip(stat("⛰ ", fmtNum(elev), " ft")))
        if (elev > 0 && dist > 0) {
            perf.addView(chip(stat("⛰ ", "${Math.round(elev / dist)}", " ft/mi")))
            val ps = S.paceSec(pace)
            if (ps > 0) { val gap = ps + (elev / (dist * 5280)) * 100 * 12; perf.addView(chip(stat("≈GAP ", "${(gap / 60).toInt()}:${String.format(Locale.US, "%02d", Math.round(gap % 60))}", "/mi"))) }
        }
        if (perf.childCount > 0) body.addView(perf)

        // Conditions / recovery row.
        val cond = FlowLayout(this, dp(6), dp(6)).apply { setPadding(0, dp(6), 0, 0) }
        S.str(a, "temp").takeIf { it.isNotEmpty() && it != "0" }?.let { cond.addView(chip(stat("🌡️ ", it, "°F"))) }
        S.num(a, "humidity").takeIf { it > 0 }?.let { cond.addView(chip(stat("💧 Humidity ", fmtNum(it), "%"))) }
        S.num(a, "aqi").takeIf { it > 0 }?.let { cond.addView(chip(stat("🌬️ AQI ", fmtNum(it), ""))) }
        if (x.has("hhPct")) cond.addView(chip(stat("🥵 +", String.format(Locale.US, "%.1f%%", x.optDouble("hhPct")), " (${x.optInt("hhSec")}s/mi)")))
        if (x.has("ha")) cond.addView(chip(stat("🌡️💧 Heat Accl. ", "${x.optInt("ha")}%", "", color = parseColor(x.optString("haColor")))))
        if (x.has("sleepMins") || x.has("sleepScore")) {
            val sb = android.text.SpannableStringBuilder("😴 ")
            if (x.has("sleepMins")) { val m = x.optInt("sleepMins"); val st = sb.length; sb.append(if (m < 60) "${m}m" else "${m / 60}h ${m % 60}m"); sb.setSpan(android.text.style.StyleSpan(Typeface.BOLD), st, sb.length, 0) }
            if (x.has("sleepScore")) { val sc = x.optInt("sleepScore"); if (x.has("sleepMins")) sb.append(" · "); val st = sb.length; sb.append("$sc")
                sb.setSpan(android.text.style.StyleSpan(Typeface.BOLD), st, sb.length, 0)
                sb.setSpan(android.text.style.ForegroundColorSpan(if (sc >= 85) 0xFF16A34A.toInt() else if (sc >= 70) 0xFF65A30D.toInt() else if (sc >= 50) 0xFFF97316.toInt() else 0xFFDC2626.toInt()), st, sb.length, 0); sb.append(" score") }
            cond.addView(chip(sb))
        }
        if (x.has("prevWaterOz")) cond.addView(chip(stat("💧 ", "${x.optInt("prevWaterOz")}", " oz prev-day")))
        if (x.has("prevCal")) cond.addView(chip(stat("🍽️ ", "${x.optInt("prevCal")}", " cal" + (if (x.has("prevP")) " · P ${x.optInt("prevP")}% · C ${x.optInt("prevC")}% · F ${x.optInt("prevF")}%" else "") + " prev-day")))
        if (x.has("fuel")) { val f = x.optDouble("fuel"); cond.addView(chip(stat("🏅 ", String.format(Locale.US, "%.1f", f), "/10 fuel impact",
            color = if (f >= 7) 0xFF16A34A.toInt() else if (f >= 5) 0xFF65A30D.toInt() else if (f >= 3) 0xFFF97316.toInt() else 0xFFDC2626.toInt())).apply {
            setOnClickListener { android.widget.Toast.makeText(ctx, "Previous day food quality + fueling vs target (${x.optString("fuelLabel")})" + x.optString("fuelNote").let { n -> if (n.isNotEmpty()) " · $n" else "" }, android.widget.Toast.LENGTH_LONG).show() } }) }
        S.num(a, "execScore").takeIf { it > 0 }?.let { cond.addView(chip(stat("🎯 ", fmtNum(it), "/100"))) }
        if (cond.childCount > 0) body.addView(View(this).apply { setBackgroundColor(0x22FFFFFF) }, LinearLayout.LayoutParams(MATCH_PARENT, 1).apply { topMargin = dp(8) })
        if (cond.childCount > 0) body.addView(cond)

        // Route, notes, injury report.
        S.str(a, "routeUrl").takeIf { it.startsWith("http") }?.let { url ->
            body.addView(txt("🗺️ Open Run Map", 0xFF7DD3FC.toInt(), 13f, true).apply {
                background = RunUi.rounded(0xFF0C3A52.toInt(), dp(7).toFloat()); setPadding(dp(10), dp(6), dp(10), dp(6))
                layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(8) }
                setOnClickListener { try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) } catch (e: Exception) { } }
            })
        }
        // BeyondStride's own record of the run (map, splits, charts) when it came from the watch.
        S.idOf(a).toLongOrNull()?.takeIf { PhoneRuns.read(ctx, it) != null }?.let { start ->
            body.addView(txt("📍 Map, splits & charts ▸", RunUi.ACCENT, 13f, true).apply {
                setPadding(0, dp(8), 0, 0); setOnClickListener { startActivity(Intent(ctx, RunDetailActivity::class.java).putExtra("start", start)) } })
        }
        S.str(a, "notes").takeIf { it.isNotEmpty() }?.let { n ->
            body.addView(txt("Notes", 0xFF94A3B8.toInt(), 12f, true).apply { setPadding(0, dp(8), 0, 0) })
            body.addView(txt(n, RunUi.MUTED, 13f))
        }
        S.str(a, "injuryReport").takeIf { it.isNotEmpty() }?.let { n ->
            body.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL; background = RunUi.rounded(0xFF3A1E1E.toInt(), dp(7).toFloat()); setPadding(dp(10), dp(8), dp(10), dp(8))
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }
                addView(txt("Injury Report", 0xFFF87171.toInt(), 12f, true)); addView(txt(n, 0xFFFECACA.toInt(), 13f))
            })
        }
        // Source and actions.
        body.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, 0)
            addView(txt("Source: ", RunUi.MUTED, 12f, true))
            (listOf(src) + (if (src != "watch") listOf("watch") else emptyList())).forEach { sKey ->
                val cur = sKey == src
                addView(txt(S.sourceLabel(sKey), if (cur) Color.WHITE else RunUi.MUTED, 12f, cur).apply {
                    background = RunUi.rounded(if (cur) 0xFF667EEA.toInt() else 0xFF26302C.toInt(), dp(10).toFloat()); setPadding(dp(9), dp(3), dp(9), dp(3))
                    layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(5) }
                    setOnClickListener { if (!cur) { S.save(ctx, JSONObject().put("id", a.get("id")).put("source", sKey)); render() } }
                })
            }
        })
        body.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, 0)
            fun b(t: String, go: () -> Unit) = addView(txt(t, Color.WHITE, 15f).apply { background = RunUi.rounded(0xFF26302C.toInt(), dp(6).toFloat()); setPadding(dp(10), dp(4), dp(10), dp(4))
                layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(6) }; setOnClickListener { go() } })
            b("📝") { quickNote(a) }
            b("✏") { startActivity(Intent(ctx, ActivityEditActivity::class.java).putExtra("id", id)) }
            b("✕") { AlertDialog.Builder(ctx).setMessage("Delete this activity?").setPositiveButton("Delete") { _, _ -> S.delete(ctx, a.get("id")); chosen -= id; render() }.setNegativeButton("Cancel", null).show() }
        })
        return outer
    }

    private fun fmtNum(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
    private fun parseColor(s: String) = try { if (s.isNotEmpty()) Color.parseColor(s) else null } catch (e: Exception) { null }

    private fun quickNote(a: JSONObject) {
        val e = EditText(this).apply { setText(S.str(a, "notes")); hint = "Add a note..."; minLines = 3 }
        AlertDialog.Builder(this).setTitle("Note").setView(LinearLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(e, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)) })
            .setPositiveButton("Save") { _, _ ->
                val n = e.text.toString().trim()
                S.save(this, JSONObject().put("id", a.get("id")).apply { if (n.isNotEmpty()) put("notes", n) }, if (n.isEmpty()) listOf("notes") else emptyList()); render()
            }.setNegativeButton("Cancel", null).show()
    }

    /** Side-by-side table with 🏆 on the best value per row — the Command Center's Compare. */
    private fun showCompare() {
        val acts = S.list(this).filter { S.idOf(it) in chosen }.sortedBy { S.str(it, "date") }
        if (acts.size < 2) { android.widget.Toast.makeText(this, "Select at least 2 activities to compare.", android.widget.Toast.LENGTH_SHORT).show(); return }
        data class Row(val label: String, val v: (JSONObject) -> Double, val show: (JSONObject, Double) -> String, val best: String?)
        val rows = listOf(
            Row("Date", { 0.0 }, { a, _ -> shortDate(S.str(a, "date")).substringAfter(", ") }, null),
            Row("Type", { 0.0 }, { a, _ -> S.str(a, "type").ifEmpty { "—" } }, null),
            Row("Distance (mi)", { S.num(it, "distance") }, { _, v -> if (v > 0) String.format(Locale.US, "%.2f", v) else "—" }, "max"),
            Row("Duration", { S.durSec(S.str(it, "duration")).toDouble() }, { a, _ -> S.str(a, "duration").takeIf { it.isNotEmpty() }?.let { S.fmtDur(it) } ?: "—" }, null),
            Row("Avg Pace (/mi)", { S.paceSec(S.str(it, "pace")).toDouble() }, { a, _ -> S.str(a, "pace").ifEmpty { "—" } }, "min"),
            Row("Avg Power (W)", { S.num(it, "power") }, { _, v -> if (v > 0) "${fmtNum(v)} W" else "—" }, "max"),
            Row("Avg HR (bpm)", { S.num(it, "heartRate") }, { a, v -> if (v > 0) "${fmtNum(v)} bpm" + S.extras(this, a.opt("id")).optString("hz").let { z -> if (z.isNotEmpty()) " ($z)" else "" } else "—" }, null),
            Row("Cadence (spm)", { S.num(it, "cadence") }, { _, v -> if (v > 0) "${fmtNum(v)} spm" else "—" }, "max"),
            Row("Calories", { S.num(it, "calories") }, { _, v -> if (v > 0) fmtNum(v) else "—" }, "max"),
            Row("Elev Gain (ft)", { S.num(it, "elevGain") }, { _, v -> if (v > 0) "${fmtNum(v)} ft" else "—" }, "max"),
            Row("Execution Score", { S.num(it, "execScore") }, { _, v -> if (v > 0) "${fmtNum(v)}/100" else "—" }, "max"),
            Row("Source", { 0.0 }, { a, _ -> S.sourceLabel(S.str(a, "source")) }, null),
        )
        val table = android.widget.TableLayout(this).apply { setPadding(dp(12), dp(8), dp(12), dp(8)) }
        fun cell(t: String, bold: Boolean = false, color: Int = Color.WHITE, bg: Int? = null) = txt(t, color, 12f, bold).apply { setPadding(dp(6), dp(5), dp(6), dp(5)); bg?.let { setBackgroundColor(it) } }
        table.addView(android.widget.TableRow(this).apply {
            addView(cell("Metric", true, RunUi.MUTED))
            acts.forEach { addView(cell(S.str(it, "title").ifEmpty { S.str(it, "type") }, true, S.TYPE_COLOR[S.str(it, "type")] ?: Color.WHITE)) }
        })
        rows.forEach { r ->
            val vals = acts.map { r.v(it) }
            val nz = vals.filter { it > 0 }
            val best = if (r.best != null && nz.size > 1) (if (r.best == "max") nz.max() else nz.min()) else null
            table.addView(android.widget.TableRow(this).apply {
                addView(cell(r.label, true, RunUi.MUTED))
                acts.forEachIndexed { i, a -> val isBest = best != null && vals[i] == best
                    addView(cell(r.show(a, vals[i]) + if (isBest) " 🏆" else "", isBest, if (isBest) 0xFF5FD38D.toInt() else Color.WHITE, if (isBest) 0xFF16302A.toInt() else null)) }
            })
        }
        AlertDialog.Builder(this).setTitle("📊 Comparing ${acts.size} Activities")
            .setView(android.widget.HorizontalScrollView(this).apply { addView(LinearLayout(this@ActivitiesActivity).apply {
                orientation = LinearLayout.VERTICAL; addView(table); addView(txt("🏆 = best value among the selected activities for that metric.", RunUi.MUTED, 11f).apply { setPadding(dp(16), 0, dp(16), dp(8)) }) }) })
            .setPositiveButton("Close", null).show()
    }

    // ── Mileage summary: This Week · Last Week · Month to Date · Year to Date (the Command Center's math) ──
    private fun mileage(col: LinearLayout, all: List<JSONObject>) {
        val ctx = this
        val today = java.time.LocalDate.now()
        val mon = today.minusDays((today.dayOfWeek.value - 1).toLong()); val lastMon = mon.minusDays(7)
        val mtd = today.withDayOfMonth(1); val ytd = today.withDayOfYear(1)
        class B { var mi = 0.0; var pace = 0; var paceN = 0; var w = 0.0; var wN = 0; var mins = 0.0 }
        val b = linkedMapOf("ThisWeek" to B(), "LastWeek" to B(), "MTD" to B(), "YTD" to B())
        fun inP(k: String, d: java.time.LocalDate) = when (k) { "ThisWeek" -> !d.isBefore(mon); "LastWeek" -> !d.isBefore(lastMon) && d.isBefore(mon); "MTD" -> !d.isBefore(mtd); else -> !d.isBefore(ytd) }
        all.forEach { a ->
            val d = try { java.time.LocalDate.parse(S.str(a, "date")) } catch (e: Exception) { return@forEach }
            b.forEach { (k, x) -> if (inP(k, d)) {
                x.mi += S.num(a, "distance"); S.paceSec(S.str(a, "pace")).takeIf { it > 0 }?.let { x.pace += it; x.paceN++ }
                S.num(a, "power").takeIf { it > 0 }?.let { x.w += it; x.wN++ }; x.mins += S.durSec(S.str(a, "duration")) / 60.0 } }
        }
        val covered = all.filter { S.num(it, "distance") > 0 }.map { S.str(it, "date") }.toSet()
        S.exMiles(this).forEach { (ds, m) -> if (ds !in covered) { val d = try { java.time.LocalDate.parse(ds) } catch (e: Exception) { return@forEach }; b.forEach { (k, x) -> if (inP(k, d)) x.mi += m } } }
        val st = S.settings(this)
        fun fmtMins(m: Double): String { val r = Math.round(m).toInt(); return if (r < 60) "${r}m" else "${r / 60}h ${r % 60}m" }
        fun numIn(v: Double, hint: String, commit: (Double) -> Unit) = EditText(ctx).apply {
            if (v > 0) setText(if (v == Math.floor(v)) v.toLong().toString() else v.toString()); this.hint = hint; setSingleLine(); textSize = 14f
            setTextColor(Color.WHITE); setHintTextColor(RunUi.DIM); gravity = Gravity.END
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            background = RunUi.rounded(0xFF26302C.toInt(), dp(6).toFloat()); setPadding(dp(8), dp(4), dp(8), dp(4))
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            var last = v
            fun go() { val n = text.toString().trim().toDoubleOrNull() ?: 0.0; if (n != last) { last = n; commit(n) } }
            setOnEditorActionListener { _, _, _ -> go(); false }
            setOnFocusChangeListener { _, has -> if (!has) go() }
        }
        val titles = mapOf("ThisWeek" to "This Week", "LastWeek" to "Last Week", "MTD" to "Month to Date", "YTD" to "Year to Date")
        val goalKey = mapOf("ThisWeek" to "week", "MTD" to "month", "YTD" to "year")
        val goalUnit = mapOf("week" to "mi/wk", "month" to "mi/mo", "year" to "mi/yr")
        col.addView(RunUi.section(this, "MILEAGE"))
        b.entries.chunked(2).forEach { pair ->
            col.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }
                pair.forEachIndexed { i, (k, x) ->
                    addView(LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL; background = RunUi.rounded(RunUi.CARD, dp(12).toFloat()); setPadding(dp(12), dp(10), dp(12), dp(10))
                        addView(txt(titles[k]!!, 0xFFE67E22.toInt(), 13f, true))
                        addView(txt(String.format(Locale.US, "%.1f", x.mi), Color.WHITE, 26f, true)); addView(txt("miles", RunUi.MUTED, 11f))
                        fun line(l: String, v: String) = addView(LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(3), 0, 0)
                            addView(txt(l, RunUi.MUTED, 11f), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)); addView(txt(v, Color.WHITE, 12f, true)) })
                        line("Avg pace", if (x.paceN > 0) (x.pace / x.paceN).let { "${it / 60}:${String.format(Locale.US, "%02d", it % 60)}/mi" } else "—")
                        line("Avg power", if (x.wN > 0) "${Math.round(x.w / x.wN)} W" else "—")
                        line("Exercise time", if (x.mins > 0) fmtMins(x.mins) else "—")
                        goalKey[k]?.let { gk ->
                            val goal = S.goal(ctx, gk)
                            addView(LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, 0)
                                addView(txt("Goal:", RunUi.MUTED, 11f, true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                                addView(numIn(goal, mapOf("week" to "20", "month" to "80", "year" to "1000")[gk]!!) { n -> S.saveSettings(ctx, JSONObject().put("goal_$gk", n)); render() }, LinearLayout.LayoutParams(dp(64), WRAP_CONTENT))
                                addView(txt(" " + goalUnit[gk], RunUi.MUTED, 11f)) })
                            if (goal > 0) {
                                val pct = Math.min(100, Math.round(x.mi / goal * 100).toInt())
                                addView(android.widget.ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = pct
                                    progressTintList = android.content.res.ColorStateList.valueOf(if (pct >= 100) 0xFF27AE60.toInt() else 0xFFE67E22.toInt())
                                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(8)).apply { topMargin = dp(4) } })
                                addView(txt(String.format(Locale.US, "%.1f / %s mi (%d%%)", x.mi, fmtNum(goal), pct), RunUi.MUTED, 11f))
                            }
                        }
                    }, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply { if (i == 0) marginEnd = dp(8) })
                }
            })
        }
    }

    /** CP / max HR / resting HR: saved in the Command Center and in BeyondStride's own zones (watch). */
    private fun setZone(key: String, v: Double) {
        if (v <= 0) return
        S.saveSettings(this, JSONObject().put(key, v))
        RunSettings.save(this, if (key == "maxHr") v else RunSettings.hrMax(this), if (key == "restHr") v else RunSettings.hrRest(this),
            if (key == "cp") v.toInt() else RunSettings.cp(this), RunSettings.cal(this), RunSettings.ccUrl(this))
        render()
    }

    // ── Training zones: Heart Rate (Karvonen) and Power, from the Command Center's settings ──
    private fun zones(col: LinearLayout) {
        val ctx = this
        val st = S.settings(this)
        val max = st.optDouble("maxHr", RunSettings.hrMax(this)); val rest = st.optDouble("restHr", RunSettings.hrRest(this)); val cp = st.optDouble("cp", 0.0)
        val res = Math.max(1.0, max - rest); val bnd = listOf(0.5, 0.6, 0.7, 0.8, 0.9).map { Math.round(rest + res * it).toInt() }
        val hrLabels = listOf("Recovery", "Aerobic base", "Aerobic threshold", "Anaerobic", "Max effort")
        val pw = listOf(Triple(0.0, 0.55, "Recovery"), Triple(0.55, 0.75, "Aerobic / endurance"), Triple(0.75, 0.90, "Tempo"), Triple(0.90, 1.05, "Threshold"), Triple(1.05, -1.0, "VO2 max"))
        // Collapsible (closed each time the screen opens); the heading shows the Z2 range at a glance.
        val zOpen = "zones" in panels
        col.addView(txt((if (zOpen) "▼" else "▶") + "  🎯 Training Zones" + (if (!zOpen) "  (Z2 ${bnd[1]}–${bnd[2] - 1} bpm)" else ""), 0xFFE67E22.toInt(), 15f, true).apply {
            setPadding(dp(2), dp(16), 0, dp(4)); setOnClickListener { if (zOpen) panels.remove("zones") else panels.add("zones"); render() } })
        if (!zOpen) return
        col.addView(RunUi.card(this).apply {
            fun badge(z: String) = txt(" $z ", Color.BLACK, 11f, true).apply { background = RunUi.rounded(ZONE_COLOR[z] ?: 0xFF888888.toInt(), dp(4).toFloat()) }
            fun row(z: String, range: String, label: String) = addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(3), 0, dp(3))
                addView(badge(z)); addView(txt("  $range", Color.WHITE, 13f), LinearLayout.LayoutParams(dp(130), WRAP_CONTENT)); addView(txt(label, RunUi.MUTED, 12f))
            })
            addView(txt("❤️ Heart Rate (Karvonen)", Color.WHITE, 14f, true))
            addView(txt("Max HR ${fmtNum(max)} bpm · Resting HR ${fmtNum(rest)} bpm" + st.optString("zonesNote").let { if (it.isNotEmpty()) " · $it" else "" }, RunUi.MUTED, 11f))
            (0 until 5).forEach { i -> row("Z${i + 1}", if (i < 4) "${bnd[i]}–${bnd[i + 1] - 1} bpm" else "${bnd[4]}+ bpm", hrLabels[i]) }
            addView(txt("⚡ Power", Color.WHITE, 14f, true).apply { setPadding(0, dp(10), 0, 0) })
            if (cp <= 0) addView(txt("Set Critical Power above to see your power zone ranges.", RunUi.MUTED, 12f))
            else pw.forEachIndexed { i, (lo, hi, l) -> row("Z${i + 1}", if (hi > 0) "${Math.round(cp * lo)}–${Math.round(cp * hi)} W" else "${Math.round(cp * lo)}+ W", l) }
        })
    }

    // ── Shoe tracker ──
    private fun shoes(col: LinearLayout) {
        val ctx = this
        val prefs = getSharedPreferences("activitiesUi", 0)
        val open = prefs.getBoolean("shoesOpen", false)
        val list = S.shoes(this)
        col.addView(txt((if (open) "▼" else "▶") + "  👟 Shoe Tracker" + (if (list.isNotEmpty()) "  (${list.count { !it.optBoolean("retired") }} active)" else ""), 0xFFE67E22.toInt(), 15f, true).apply {
            setPadding(dp(2), dp(16), 0, dp(4)); setOnClickListener { prefs.edit().putBoolean("shoesOpen", !open).apply(); render() }
        })
        if (!open) return
        col.addView(txt("+ Add Shoe", Color.WHITE, 13f, true).apply {
            background = RunUi.rounded(0xFFE67E22.toInt(), dp(8).toFloat()); setPadding(dp(12), dp(7), dp(12), dp(7))
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(4) }
            setOnClickListener { shoeDialog(null) }
        })
        if (list.isEmpty()) col.addView(txt("No shoes yet — add one to track its miles.", RunUi.MUTED, 13f).apply { setPadding(0, dp(8), 0, 0) })
        (list.filter { !it.optBoolean("retired") } + list.filter { it.optBoolean("retired") }).forEach { sh ->
            val miles = S.shoeMiles(this, sh); val max = sh.optDouble("maxMiles", 500.0).takeIf { it > 0 } ?: 500.0
            val pct = Math.min(Math.round(miles / max * 100).toInt(), 100)
            val bar = if (pct < 60) 0xFF22C55E.toInt() else if (pct < 85) 0xFFF59E0B.toInt() else 0xFFEF4444.toInt()
            val retired = sh.optBoolean("retired"); val remaining = Math.max(0.0, Math.round((max - miles) * 10) / 10.0)
            val status = if (retired) "RETIRED" else if (pct >= 100) "⚠️ REPLACE" else "ACTIVE"
            val stColor = if (retired) 0xFF94A3B8.toInt() else if (pct >= 100) 0xFFEF4444.toInt() else 0xFF22C55E.toInt()
            col.addView(RunUi.card(this).apply {
                alpha = if (retired) 0.7f else 1f
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }
                addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(txt("👟 " + sh.optString("name"), Color.WHITE, 15f, true))
                        val bc = listOf(sh.optString("brand"), sh.optString("color")).filter { it.isNotEmpty() }.joinToString(" · ")
                        if (bc.isNotEmpty()) addView(txt(bc, RunUi.MUTED, 12f))
                        addView(txt(status, Color.WHITE, 10f, true).apply { background = RunUi.rounded(stColor, dp(10).toFloat()); setPadding(dp(7), dp(2), dp(7), dp(2))
                            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(4) } })
                    }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                    fun b(t: String, bg: Int, fg: Int, go: () -> Unit) = addView(txt(t, fg, 12f, true).apply { background = RunUi.rounded(bg, dp(6).toFloat()); setPadding(dp(8), dp(4), dp(8), dp(4))
                        layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = dp(5) }; setOnClickListener { go() } })
                    b("✏️", 0xFF26302C.toInt(), Color.WHITE) { shoeDialog(sh) }
                    b(if (retired) "↩" else "📦", 0xFF3A2A1A.toInt(), 0xFFFCD34D.toInt()) { S.saveShoe(ctx, JSONObject(sh.toString()).put("retired", !retired)); render() }
                    b("🗑", 0xFF3A1E1E.toInt(), 0xFFF87171.toInt()) { AlertDialog.Builder(ctx).setMessage("Delete this shoe?").setPositiveButton("Delete") { _, _ -> S.deleteShoe(ctx, sh.optString("id")); render() }.setNegativeButton("Cancel", null).show() }
                })
                addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, dp(4))
                    addView(android.widget.ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply { this.max = 100; progress = pct
                        progressTintList = android.content.res.ColorStateList.valueOf(bar) }, LinearLayout.LayoutParams(0, dp(10), 1f))
                    addView(txt("  $pct%", bar, 12f, true))
                })
                addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    fun stat(v: String, l: String, c: Int = Color.WHITE) = addView(LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                        addView(txt(v, c, 15f, true)); addView(txt(l, RunUi.MUTED, 9f)) }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                    stat(String.format(Locale.US, "%.1f", miles), "CURRENT MI"); stat(fmtNum(max), "MAX MI", RunUi.MUTED)
                    if (!retired) stat(fmtNum(remaining), "REMAINING", if (remaining < 50) 0xFFEF4444.toInt() else 0xFF22C55E.toInt())
                    sh.optString("startDate").takeIf { it.isNotEmpty() }?.let { stat(try { java.time.LocalDate.parse(it).format(java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy")) } catch (e: Exception) { it }, "DATE ADDED") }
                })
                sh.optString("notes").takeIf { it.isNotEmpty() }?.let { addView(txt(it, RunUi.MUTED, 12f).apply { setPadding(0, dp(6), 0, 0) }) }
            })
        }
    }

    private fun shoeDialog(sh: JSONObject?) {
        val ctx = this
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        fun f(label: String, v: String, kind: Int = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS) = EditText(ctx).apply {
            hint = label; setText(v); inputType = kind }.also { box.addView(TextView(ctx).apply { text = label; textSize = 12f; setPadding(0, dp(6), 0, 0) }); box.addView(it) }
        val num = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        val name = f("Shoe name", sh?.optString("name") ?: ""); val brand = f("Brand", sh?.optString("brand") ?: ""); val color = f("Color", sh?.optString("color") ?: "")
        val start = f("Start date (YYYY-MM-DD)", sh?.optString("startDate")?.ifEmpty { null } ?: java.time.LocalDate.now().toString(), android.text.InputType.TYPE_CLASS_DATETIME)
        val maxM = f("Max miles", fmtNum(sh?.optDouble("maxMiles", 500.0) ?: 500.0), num)
        val off = f("Starting miles (already on the shoe)", fmtNum(sh?.optDouble("offsetMiles", 0.0) ?: 0.0), num)
        val notes = f("Notes", sh?.optString("notes") ?: "", android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        AlertDialog.Builder(this).setTitle(if (sh == null) "👟 Add Shoe" else "✏️ Edit Shoe").setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Save") { _, _ ->
                val n = name.text.toString().trim()
                if (n.isEmpty()) { android.widget.Toast.makeText(ctx, "Please enter a shoe name.", android.widget.Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val o = JSONObject().put("id", sh?.optString("id")?.ifEmpty { null } ?: System.currentTimeMillis().toString()).put("name", n)
                    .put("brand", brand.text.toString().trim()).put("color", color.text.toString().trim())
                    .put("startDate", start.text.toString().trim().ifEmpty { java.time.LocalDate.now().toString() })
                    .put("maxMiles", maxM.text.toString().toDoubleOrNull() ?: 500.0).put("offsetMiles", off.text.toString().toDoubleOrNull() ?: 0.0)
                    .put("notes", notes.text.toString().trim()).put("retired", sh?.optBoolean("retired") ?: false)
                S.saveShoe(ctx, o); render()
            }.setNegativeButton("Cancel", null).show()
    }

    // ══ Phase 3: training plans ══
    private val PURPLE = 0xFF667EEA.toInt()
    private fun pbtn(box: android.view.ViewGroup, t: String, bg: Int, fg: Int = Color.WHITE, go: () -> Unit) = box.addView(txt(t, fg, 12f, true).apply {
        background = RunUi.rounded(bg, dp(6).toFloat()); setPadding(dp(10), dp(6), dp(10), dp(6)); setOnClickListener { go() } })
    private fun confirm(msg: String, yes: String, go: () -> Unit) = AlertDialog.Builder(this).setMessage(msg).setPositiveButton(yes) { _, _ -> go() }.setNegativeButton("Cancel", null).show()
    private fun planDo(x: JSONObject, toast: String? = null) {
        PlanStore.action(this, x); toast?.let { android.widget.Toast.makeText(this, it, android.widget.Toast.LENGTH_LONG).show() }; render()
    }
    private fun fmtDate(ds: String, pattern: String = "MMM d, yyyy") = try { java.time.LocalDate.parse(ds).format(java.time.format.DateTimeFormatter.ofPattern(pattern, Locale.US)) } catch (e: Exception) { ds.ifEmpty { "—" } }

    private fun planWaiting(col: LinearLayout) {
        val n = PlanStore.pending(this).size
        if (n > 0) col.addView(txt("⏳ $n training-plan change${if (n == 1) "" else "s"} waiting for the Command Center (open its page to apply).", 0xFFF2C94C.toInt(), 12f).apply { setPadding(0, dp(6), 0, 0) })
    }

    /** "📋 Active Training Plan" / "📅 Upcoming Training Plan" with the Command Center's buttons and the plan text. */
    private fun planCard(col: LinearLayout, plan: JSONObject, which: String) {
        val ctx = this
        val up = which == "upcoming"
        col.addView(RunUi.card(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) }
            // Collapsed each time the screen opens; tap the title to open it.
            val key = "plan_$which"; val isOpen = key in panels
            addView(txt((if (isOpen) "▼  " else "▶  ") + (if (up) "📅 Upcoming Training Plan" else "📋 Active Training Plan"), 0xFFE67E22.toInt(), 16f, true).apply {
                setOnClickListener { if (isOpen) panels.remove(key) else panels.add(key); render() } })
            if (!isOpen) {
                val m = listOfNotNull(plan.optString("type").ifEmpty { null }, plan.optInt("weeks").takeIf { it > 0 }?.let { "$it-week plan" },
                    if (up) "Starts " + fmtDate(plan.optString("startDate")) else null).joinToString(" • ")
                if (m.isNotEmpty()) addView(txt(m, RunUi.MUTED, 12f).apply { setPadding(0, dp(4), 0, 0) })
                return@apply
            }
            addView(FlowLayout(ctx, dp(6), dp(6)).apply {
                setPadding(0, dp(8), 0, dp(4))
                pbtn(this, "🪄 Clean Up Formatting", 0xFFD35400.toInt()) { aiPlan(which, plan, null) }
                pbtn(this, "➕ Add Training Zones", 0xFF16A085.toInt()) { planDo(JSONObject().put("action", "zones").put("which", which), "Adding training zones — the Command Center updates the plan") }
                pbtn(this, "📅 Add Dates", 0xFF2C3E50.toInt()) { planDo(JSONObject().put("action", "dates").put("which", which), "Adding dates — the Command Center updates the plan") }
                pbtn(this, "✏ Adjust", PURPLE) { adjustDialog(which, plan) }
                pbtn(this, "📦 Archive", 0xFF27AE60.toInt()) { confirm("Archive this ${if (up) "upcoming" else "active"} plan?", "Archive") { planDo(JSONObject().put("action", "archive").put("which", which)) } }
                if (up) pbtn(this, "▶ Make Active Now", 0xFF2980B9.toInt()) {
                    confirm(if (PlanStore.data(ctx).has("current")) "This will archive your current active plan and make this upcoming plan active now. Continue?" else "Make this plan active now?", "Make Active") { planDo(JSONObject().put("action", "activate")) } }
                pbtn(this, "⌚ Send to watch", 0xFFD4E635.toInt(), 0xFF121A17.toInt()) { sendToWatch(plan) }
                pbtn(this, "🗑 Delete", 0xFFE74C3C.toInt()) { confirm("Delete the ${if (up) "upcoming" else "active training"} plan? This cannot be undone.", "Delete") { planDo(JSONObject().put("action", "delete").put("which", which)) } }
            })
            if (up && plan.optString("startDate").let { it.isNotEmpty() && it <= java.time.LocalDate.now().toString() })
                addView(txt("⚠️ This plan was set to start ${fmtDate(plan.optString("startDate"))}, which has passed, but it's still sitting here as Upcoming — your Activities card and Daily calendar won't show any of its workouts until you tap ▶ Make Active Now.",
                    0xFFFCA5A5.toInt(), 12f, true).apply { background = RunUi.rounded(0xFF3A1E1E.toInt(), dp(8).toFloat()); setPadding(dp(10), dp(8), dp(10), dp(8)) })
            val meta = ArrayList<String>()
            plan.optString("type").takeIf { it.isNotEmpty() }?.let { meta += it }
            plan.optInt("weeks").takeIf { it > 0 }?.let { meta += "$it-week plan" }
            if (up) meta += "Starts " + fmtDate(plan.optString("startDate"))
            if (up) plan.optString("createdAt").takeIf { it.isNotEmpty() }?.let { meta += "Saved $it" }
            else if (plan.optString("createdAt").isNotEmpty()) meta += "Created " + plan.optString("createdAt") else plan.optString("startDate").takeIf { it.isNotEmpty() }?.let { meta += "Start $it" }
            if (!up) plan.optString("adjustedAt").takeIf { it.isNotEmpty() }?.let { meta += "Adjusted $it" }
            if (meta.isNotEmpty()) addView(txt(meta.joinToString(" • "), RunUi.MUTED, 12f).apply { setPadding(0, dp(4), 0, dp(6)) })
            if (plan.optBoolean("hasPdf")) addView(txt("📄 The original PDF can be viewed in the Command Center.", RunUi.DIM, 11f))
            addView(androidx.core.widget.NestedScrollView(ctx).apply {
                background = RunUi.rounded(0xFF141A18.toInt(), dp(8).toFloat()); setPadding(dp(10), dp(8), dp(10), dp(8))
                addView(txt(plan.optString("display").ifEmpty { plan.optString("text") }.ifEmpty { "(No plan text)" }, Color.WHITE, 13f).apply { setTextIsSelectable(true); setLineSpacing(0f, 1.15f) })
            }, LinearLayout.LayoutParams(MATCH_PARENT, dp(340)).apply { topMargin = dp(4) })
        })
    }

    private fun adjustDialog(which: String, plan: JSONObject) {
        val input = EditText(this).apply { hint = "e.g. I missed this week due to illness, please shift the plan forward one week and adjust the long run schedule accordingly..."; minLines = 4; gravity = Gravity.TOP
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES }
        AlertDialog.Builder(this).setTitle("💬 Tell AI how to adjust the plan:").setView(LinearLayout(this).apply { setPadding(dp(20), dp(4), dp(20), 0); addView(input, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)) })
            .setPositiveButton("✨ Adjust Plan") { _, _ -> val t = input.text.toString().trim()
                if (t.isEmpty()) android.widget.Toast.makeText(this, "Please describe how you want to adjust the plan.", android.widget.Toast.LENGTH_SHORT).show() else aiPlan(which, plan, t) }
            .setNegativeButton("Cancel", null).show()
    }

    /** Clean Up Formatting ([instruction] null) or Adjust, with the same prompts as the Command Center and your Anthropic key. */
    private fun aiPlan(which: String, plan: JSONObject, instruction: String?) {
        val ctx = this
        if (!AiFood.hasKey(this)) { android.widget.Toast.makeText(this, "Add your Anthropic key in Settings → AI food search first.", android.widget.Toast.LENGTH_LONG).show(); return }
        val text = plan.optString("text"); if (text.isEmpty()) { android.widget.Toast.makeText(this, "This plan has no text.", android.widget.Toast.LENGTH_SHORT).show(); return }
        val prompt = if (instruction == null) "You are formatting a training plan for easy reading in a mobile app. Reformat the plan text below into a clean, consistently structured document, following this style:\n\n" +
            "- A short title line in capitals (e.g. the distance and plan type)\n" +
            "- A one-line subtitle describing the plan's structure/phases, using · as a separator\n" +
            "- A full-width separator line of = characters\n" +
            "- Key stats as their own short labeled lines in capitals (e.g. TARGET PACE, FINISH GOAL, PEAK LONG RUN) directly followed by their values, if the plan includes stats like these\n" +
            "- The plan start date on its own line\n" +
            "- A week-by-week schedule, each week starting with a clear \"Week N — Phase\" header, followed by each training day on its own line (e.g. \"Mon: Easy 4 miles\")\n\n" +
            "Keep ALL of the original plan's real information -- do not invent, remove, or summarize away any detail, workout, note, or number. Only reorganize and reformat for readability. Reply with ONLY the reformatted plan text, no preamble or commentary.\n\n" +
            "Plan text to reformat:\n\n" + text
        else "You are a running coach. The athlete has " + (if (which == "upcoming") "an upcoming" else "an active") + " training plan and wants to adjust it.\n\nCURRENT PLAN:\n" + text +
            "\n\nATHLETE'S REQUEST:\n" + instruction + "\n\nPlease rewrite the training plan incorporating the requested adjustments. Keep the same format and structure as the original plan. Return only the updated plan text."
        val wait = AlertDialog.Builder(this).setMessage(if (instruction == null) "⏳ Reformatting…" else "⏳ Adjusting…").setCancelable(false).show()
        Thread {
            val res = try { Result.success(AiFood.askText(ctx, prompt, if (instruction == null) 6000 else 5000)) } catch (e: Exception) { Result.failure(e) }
            runOnUiThread {
                wait.dismiss(); if (isFinishing) return@runOnUiThread
                res.onSuccess { t -> if (t.isBlank()) android.widget.Toast.makeText(ctx, "The AI did not return any text. Please try again.", android.widget.Toast.LENGTH_LONG).show()
                    else planDo(JSONObject().put("action", "setText").put("which", which).put("text", t).put("adjusted", instruction != null).put("rebuild", instruction == null),
                        if (instruction == null) "Plan reformatted." else "Plan adjusted.") }
                    .onFailure { android.widget.Toast.makeText(ctx, "AI error: ${it.message}", android.widget.Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun sendToWatch(plan: JSONObject) {
        val ctx = this
        val days = PlanStore.upcomingDays(plan)
        if (days.length() == 0) { android.widget.Toast.makeText(this, "This plan has no dated days from today on. Use \"Add Dates\" first so each day has a date.", android.widget.Toast.LENGTH_LONG).show(); return }
        Thread {
            val msg = try { val n = WorkoutStore.importPlan(ctx, days)
                "Sent ${days.length()} days: $n workouts" + (if (WorkoutStore.restDays > 0) ", ${WorkoutStore.restDays} rest days" else "") +
                    (if (WorkoutStore.keptEdited > 0) ", ${WorkoutStore.keptEdited} kept as you edited them" else "") + ". On your watch now."
            } catch (e: Exception) { "Couldn't send the plan: ${e.message}" }
            runOnUiThread { android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show() }
        }.start()
    }

    // ── 📂 Upload Plan from Text File ──
    private var uploadStart = ""; private var uploadUpcoming = false
    private val PICK_PLAN = 7301
    private fun uploadPlan(col: LinearLayout) {
        val ctx = this
        val prefs = getSharedPreferences("activitiesUi", 0)
        val open = prefs.getBoolean("uploadOpen", false)
        col.addView(txt((if (open) "▼" else "▶") + "  📂 Upload Plan from Text File", 0xFFE67E22.toInt(), 15f, true).apply {
            setPadding(dp(2), dp(16), 0, dp(4)); setOnClickListener { prefs.edit().putBoolean("uploadOpen", !open).apply(); render() } })
        if (!open) return
        if (uploadStart.isEmpty()) uploadStart = java.time.LocalDate.now().toString()
        col.addView(RunUi.card(this).apply {
            addView(txt("Plan start date:  ${fmtDate(uploadStart)}  ▾", Color.WHITE, 13f, true).apply {
                background = RunUi.rounded(0xFF26302C.toInt(), dp(8).toFloat()); setPadding(dp(10), dp(7), dp(10), dp(7))
                setOnClickListener { val d = java.time.LocalDate.parse(uploadStart)
                    android.app.DatePickerDialog(ctx, { _, y, m, dd -> uploadStart = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, dd); render() }, d.year, d.monthValue - 1, d.dayOfMonth).show() } })
            addView(txt((if (uploadUpcoming) "☑" else "☐") + " Save as a second plan for later (keeps your current active plan running)", Color.WHITE, 13f).apply {
                setPadding(0, dp(10), 0, dp(10)); setOnClickListener { uploadUpcoming = !uploadUpcoming; render() } })
            addView(txt("📄 Choose .txt or .pdf File", Color.WHITE, 13f, true).apply {
                background = RunUi.rounded(0xFF166534.toInt(), dp(6).toFloat()); setPadding(dp(14), dp(8), dp(14), dp(8))
                layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
                setOnClickListener { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                    .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "application/pdf")), PICK_PLAN) } })
            addView(txt("Accepts .txt or .pdf (text-based, not a scanned image). Dated workouts are imported onto their exact calendar dates. The start date is used only when the file is organized by week and weekday.", RunUi.MUTED, 11f).apply { setPadding(0, dp(8), 0, 0) })
        })
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        if (requestCode == PICK_CSV) { onCsv(data?.data); return }
        if (requestCode == SAVE_CSV) { data?.data?.let { u -> try { contentResolver.openOutputStream(u)?.use { it.write(csvText.toByteArray()) }
            android.widget.Toast.makeText(this, "Exported ${S.list(this).size} activities", android.widget.Toast.LENGTH_SHORT).show() } catch (e: Exception) { android.widget.Toast.makeText(this, "Couldn't save the file", android.widget.Toast.LENGTH_LONG).show() } }; return }
        if (requestCode != PICK_PLAN) return
        val uri = data?.data ?: return
        try {
            var name = "plan.txt"
            contentResolver.query(uri, null, null, null, null)?.use { c -> val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME); if (i >= 0 && c.moveToFirst()) name = c.getString(i) ?: name }
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return
            val isPdf = name.lowercase().endsWith(".pdf") || contentResolver.getType(uri) == "application/pdf"
            if (isPdf && bytes.size > 4 * 1024 * 1024) { android.widget.Toast.makeText(this, "That PDF is too large (over 4 MB). Save it as a .txt file instead.", android.widget.Toast.LENGTH_LONG).show(); return }
            val x = JSONObject().put("action", "upload").put("name", name).put("startDate", uploadStart).put("upcoming", uploadUpcoming)
            if (isPdf) x.put("pdf", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
            else { val t = String(bytes).trim(); if (t.isEmpty()) { android.widget.Toast.makeText(this, "The selected text file is empty.", android.widget.Toast.LENGTH_LONG).show(); return }; x.put("text", t) }
            uploadUpcoming = false
            planDo(x, "Sent $name — the Command Center loads it as your ${if (x.optBoolean("upcoming")) "upcoming" else "active"} plan")
        } catch (e: Exception) { android.widget.Toast.makeText(this, "The selected file could not be read.", android.widget.Toast.LENGTH_LONG).show() }
    }

    // ── 🏃 Training Plan — today's workout card ──
    private fun todayWorkout(col: LinearLayout, plan: JSONObject, collapsedCard: Boolean) {
        val ctx = this
        val today = java.time.LocalDate.now()
        val start = plan.optString("startDate")
        var dayNum = 0; var weekNum = 0; var dow = 0; var isFinal = false
        if (start.isNotEmpty()) {
            dayNum = try { (java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(start), today) + 1).toInt() } catch (e: Exception) { 0 }
            if (dayNum < 1) return
            weekNum = (dayNum + 6) / 7; dow = (dayNum - 1) % 7 + 1
            val total = plan.optInt("weeks") * 7; isFinal = total > 0 && dayNum == total
        }
        if (dayNum == 1 && Regex("rest\\s*day|no\\s*scheduled\\s*workout|recovery").containsMatchIn(plan.optString("text").split("\n").take(8).joinToString(" ").lowercase())) return
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = RunUi.rounded(0xFF1E2140.toInt(), dp(12).toFloat())
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) }
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR, intArrayOf(PURPLE, 0xFF764BA2.toInt())).apply {
                    cornerRadii = floatArrayOf(dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat(), 0f, 0f, 0f, 0f) }
                setPadding(dp(12), dp(10), dp(8), dp(10))
                // Tap the header (or ▾) to collapse / expand the workout.
                setOnClickListener { planDo(JSONObject().put("action", "collapse").put("on", !collapsedCard)) }
                addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    val badge = if (weekNum > 0) (if (isFinal) " (Week $weekNum, Day $dow — Final Day 🎉)" else " (Week $weekNum, Day $dow)") else ""
                    addView(txt("🏃 Training Plan — " + today.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US)) + badge, Color.WHITE, 14f, true))
                    plan.optString("race").takeIf { it.isNotEmpty() }?.let { r -> try {
                        val days = java.time.temporal.ChronoUnit.DAYS.between(today, java.time.LocalDate.parse(r)).toInt()
                        val t = when { days > 0 -> "🏁 $days day${if (days != 1) "s" else ""} to race day (${fmtDate(r)})"; days == 0 -> "🏁 Race day!"; else -> null }
                        t?.let { addView(txt(it, 0xCCFFFFFF.toInt(), 12f)) } } catch (e: Exception) { } }
                }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                fun round(t: String, go: () -> Unit) = addView(txt(t, Color.WHITE, 14f, true).apply { gravity = Gravity.CENTER
                    background = RunUi.rounded(0x33FFFFFF, dp(14).toFloat()); setOnClickListener { go() } }, LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginStart = dp(5) })
                round("↺") { android.widget.Toast.makeText(ctx, "Refreshing…", android.widget.Toast.LENGTH_SHORT).show(); Thread { val a = S.refresh(ctx); val b = PlanStore.refresh(ctx); runOnUiThread { if (!isFinishing) render() } }.start() }
                round("✏️") { datesDialog(plan) }
                round(if (collapsedCard) "▸" else "▾") { planDo(JSONObject().put("action", "collapse").put("on", !collapsedCard)) }
            })
            if (start.isEmpty()) addView(txt("⚠️ Plan start date not set — tap ✏️ to set it.", 0xFF795548.toInt(), 12f, true).apply { setBackgroundColor(0xFFFFF8E1.toInt()); setPadding(dp(12), dp(8), dp(12), dp(8)) })
            if (!collapsedCard) addView(txt(PlanStore.workoutFor(plan, today.toString()), Color.WHITE, 14f).apply { setPadding(dp(14), dp(12), dp(14), dp(12)); setLineSpacing(0f, 1.2f); setTextIsSelectable(true) })
        })
    }

    private fun datesDialog(plan: JSONObject) {
        val ctx = this
        fun pick(cur: String, go: (String) -> Unit) { val d = try { java.time.LocalDate.parse(cur) } catch (e: Exception) { java.time.LocalDate.now() }
            android.app.DatePickerDialog(ctx, { _, y, m, dd -> go(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, dd)) }, d.year, d.monthValue - 1, d.dayOfMonth).show() }
        AlertDialog.Builder(this).setTitle("Training plan dates")
            .setItems(arrayOf("⚠️ Plan start date: " + fmtDate(plan.optString("startDate")), "🏁 Race date: " + fmtDate(plan.optString("race").ifEmpty { plan.optString("raceDate") }))) { _, i ->
                if (i == 0) pick(plan.optString("startDate")) { planDo(JSONObject().put("action", "startDate").put("date", it)) }
                else pick(plan.optString("race").ifEmpty { plan.optString("raceDate") }) { planDo(JSONObject().put("action", "raceDate").put("date", it)) }
            }.setNegativeButton("Close", null).show()
    }

    // ── 💼 Archived Plans ──
    private fun archived(col: LinearLayout, arr: org.json.JSONArray?) {
        if (arr == null || arr.length() == 0) return
        val ctx = this
        val prefs = getSharedPreferences("activitiesUi", 0)
        val open = prefs.getBoolean("archOpen", false)
        col.addView(txt((if (open) "▼" else "▶") + "  💼 Archived Plans  (${arr.length()})", 0xFFE67E22.toInt(), 15f, true).apply {
            setPadding(dp(2), dp(18), 0, dp(4)); setOnClickListener { prefs.edit().putBoolean("archOpen", !open).apply(); render() } })
        if (!open) return
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            col.addView(RunUi.card(this).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }
                addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    val lbl = (if (p.optString("archivedFrom") == "upcoming") "Upcoming " else "Active ") + p.optString("type").ifEmpty { "Plan" } + " • " +
                        (if (p.optInt("weeks") > 0) "${p.optInt("weeks")}-wk • " else "") + "Archived " + p.optString("archivedAt")
                    addView(txt(lbl, Color.WHITE, 12f, true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                    pbtn(this, "Restore", PURPLE) {
                        val target = if (p.optString("archivedFrom") == "upcoming") "upcoming" else "current"
                        val go = { planDo(JSONObject().put("action", "restore").put("archivedAt", p.optString("archivedAt")).put("head", PlanStore.head(p))) }
                        if (PlanStore.data(ctx).has(target)) confirm("Replace your ${if (target == "upcoming") "upcoming" else "current active"} plan with this archived one?", "Replace") { go() } else go()
                    }
                    addView(View(ctx), LinearLayout.LayoutParams(dp(6), 1))
                    pbtn(this, "Delete", 0xFFE74C3C.toInt()) { confirm("Permanently delete this archived plan?", "Delete") {
                        planDo(JSONObject().put("action", "deleteArchived").put("archivedAt", p.optString("archivedAt")).put("head", PlanStore.head(p))) } }
                })
                val d = p.optString("display").ifEmpty { p.optString("text") }
                addView(txt(d.take(300) + if (d.length > 300) "…" else "", RunUi.MUTED, 12f).apply { setPadding(0, dp(6), 0, 0) })
            })
        }
    }

    // ══ Phase 4: Weekly Run Notes, Import / Export / Print ══
    private fun wrnMonday() = java.time.LocalDate.now().let { it.minusDays((it.dayOfWeek.value - 1).toLong()) }.plusWeeks(wrnOff.toLong())
    private fun wrnLabel(): String { val m = wrnMonday(); return m.format(java.time.format.DateTimeFormatter.ofPattern("MMM d", Locale.US)) + " – " + m.plusDays(6).format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)) }

    private fun weekNotes(col: LinearLayout) {
        val ctx = this
        val isOpen = "wrn" in panels
        col.addView(txt((if (isOpen) "▼" else "▶") + "  📝 Weekly Run Notes", 0xFFE67E22.toInt(), 15f, true).apply {
            setPadding(dp(2), dp(14), 0, dp(4)); setOnClickListener { if (isOpen) panels.remove("wrn") else panels.add("wrn"); render() } })
        if (!isOpen) return
        val week = wrnMonday().toString()
        val note = EditText(this).apply {
            setText(S.weekNotes(ctx)[week] ?: ""); hint = "Reflect on this week's runs — effort, progress, how your body felt, what to focus on next week..."
            minLines = 5; gravity = Gravity.TOP; setTextColor(Color.WHITE); setHintTextColor(RunUi.DIM); textSize = 14f
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            background = RunUi.rounded(0xFF26302C.toInt(), dp(8).toFloat()); setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        col.addView(RunUi.card(this).apply {
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                fun arrow(t: String, d: Int) = addView(txt(t, Color.WHITE, 16f, true).apply { background = RunUi.rounded(0xFFE67E22.toInt(), dp(6).toFloat()); setPadding(dp(12), dp(2), dp(12), dp(4))
                    setOnClickListener { wrnOff += d; render() } })
                arrow("‹", -1); addView(txt(wrnLabel(), 0xFFFCD34D.toInt(), 14f, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)); arrow("›", 1)
            })
            addView(note, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
            addView(FlowLayout(ctx, dp(6), dp(6)).apply {
                setPadding(0, dp(8), 0, 0)
                pbtn(this, "💾 Save Note", 0xFFE67E22.toInt()) { S.saveWeekNote(ctx, week, note.text.toString().trim()); android.widget.Toast.makeText(ctx, "Saved ✓", android.widget.Toast.LENGTH_SHORT).show() }
                pbtn(this, "✨ AI Weekly Run Wrap-Up", 0xFF6D28D9.toInt()) { wrapUp(week, note) }
                pbtn(this, "🗑 Clear", 0xFF3A3F3D.toInt()) { note.setText(""); S.saveWeekNote(ctx, week, ""); android.widget.Toast.makeText(ctx, "Saved ✓", android.widget.Toast.LENGTH_SHORT).show() }
            })
        })
    }

    /** Same prompt and facts as the Command Center's "AI Weekly Run Wrap-Up". */
    private fun wrapUp(week: String, note: EditText) {
        val ctx = this
        if (!AiFood.hasKey(this)) { android.widget.Toast.makeText(this, "Add your Anthropic key in Settings → AI food search first.", android.widget.Toast.LENGTH_LONG).show(); return }
        val start = java.time.LocalDate.parse(week); val end = start.plusDays(6)
        val seen = HashSet<String>()
        val runs = S.list(this).filter { a ->
            val d = S.str(a, "date").take(10); val rt = listOf("type", "title", "name", "workout").joinToString(" ") { S.str(a, it) }
            if (d < start.toString() || d > end.toString() || !Regex("run", RegexOption.IGNORE_CASE).containsMatchIn(rt)) false
            else seen.add(d + "|" + String.format(Locale.US, "%.3f", S.num(a, "distance")) + "|" + S.str(a, "duration").trim())
        }
        if (runs.isEmpty()) { android.widget.Toast.makeText(this, "No running activities were found for ${wrnLabel()}.", android.widget.Toast.LENGTH_LONG).show(); return }
        var mi = 0.0; var mins = 0.0; var elev = 0.0; var cal = 0.0; var pS = 0.0; var pN = 0; var longest: JSONObject? = null
        runs.forEach { a -> val m = S.num(a, "distance"); mi += m; mins += S.durSec(S.str(a, "duration")) / 60.0; elev += S.num(a, "elevGain"); cal += S.num(a, "calories")
            if (S.num(a, "power") > 0) { pS += S.num(a, "power"); pN++ }; if (longest == null || m > S.num(longest!!, "distance")) longest = a }
        fun pace(sec: Double): String { val m = Math.floor(sec / 60).toInt(); val s = Math.round(sec % 60).toInt(); return "$m:" + (if (s < 10) "0" else "") + s }
        fun fmtMins(m: Double): String { val r = Math.round(m).toInt(); return if (r < 60) "${r}m" else "${r / 60}h ${r % 60}m" }
        val avgPace = if (mi > 0 && mins > 0) pace(mins * 60 / mi) + "/mi" else "not available"
        val lines = runs.sortedBy { S.str(it, "date") }.joinToString("\n") { a -> listOf(
            S.str(a, "date").ifEmpty { "No date" }, S.str(a, "title").ifEmpty { S.str(a, "type").ifEmpty { "Run" } }, String.format(Locale.US, "%.2f mi", S.num(a, "distance")),
            S.str(a, "duration").ifEmpty { "duration unavailable" }, S.str(a, "pace").takeIf { it.isNotEmpty() }?.let { "$it/mi" } ?: "pace unavailable",
            if (S.num(a, "power") > 0) "${Math.round(S.num(a, "power"))} W" else "power unavailable", if (S.num(a, "heartRate") > 0) "${Math.round(S.num(a, "heartRate"))} bpm" else "heart rate unavailable",
            if (S.num(a, "elevGain") > 0) "${Math.round(S.num(a, "elevGain"))} ft gain" else "elevation unavailable",
            S.str(a, "notes").takeIf { it.isNotEmpty() }?.let { "notes: $it" } ?: "", S.str(a, "injuryReport").takeIf { it.isNotEmpty() }?.let { "injury report: $it" } ?: "").filter { it.isNotEmpty() }.joinToString(" | ") }
        val prompt = "Write a concise weekly running wrap-up for ${wrnLabel()}. Use only the supplied facts and do not invent performance, recovery, injury, or health claims. Keep it encouraging, practical, and about 150-220 words. Use short headings: Week at a Glance, Strongest Session, Patterns & Recovery, and Focus for Next Week. If a conclusion is uncertain, say so. This is training reflection, not medical advice.\n\n" +
            "SUMMARY\nRuns: ${runs.size}\nDistance: ${String.format(Locale.US, "%.2f", mi)} miles\nTime: ${fmtMins(mins)}\nAverage pace: $avgPace\nElevation gain: ${Math.round(elev)} ft\nCalories: ${Math.round(cal)}\nAverage recorded power: " +
            (if (pN > 0) "${Math.round(pS / pN)} W" else "not available") + "\nLongest run: " + (longest?.let { (S.str(it, "title").ifEmpty { "Run" }) + ", " + String.format(Locale.US, "%.2f", S.num(it, "distance")) + " miles" } ?: "not available") + "\n\n" +
            "ACTIVITIES\n$lines\n\nATHLETE NOTES\n" + note.text.toString().trim().ifEmpty { "No notes entered." }
        val wait = AlertDialog.Builder(this).setMessage("⏳ Thinking...").setCancelable(false).show()
        Thread {
            val r = try { Result.success(AiFood.askText(ctx, prompt, 2200)) } catch (e: Exception) { Result.failure(e) }
            runOnUiThread { wait.dismiss(); if (isFinishing) return@runOnUiThread
                r.onSuccess { t -> note.setText(t); S.saveWeekNote(ctx, week, t.trim()); android.widget.Toast.makeText(ctx, "Saved ✓", android.widget.Toast.LENGTH_SHORT).show() }
                 .onFailure { android.widget.Toast.makeText(ctx, "AI wrap-up could not be created: ${it.message}", android.widget.Toast.LENGTH_LONG).show() } }
        }.start()
    }

    private val PICK_CSV = 7302; private val SAVE_CSV = 7303
    private var csvHint = ""; private var csvText = ""
    private fun importMenu() {
        val opts = listOf("garmin" to "Garmin CSV", "stryd" to "Stryd CSV", "strava" to "Strava CSV (activities.csv)")
        AlertDialog.Builder(this).setTitle("📥 Import").setItems(opts.map { it.second }.toTypedArray()) { _, i -> csvHint = opts[i].first
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel")), PICK_CSV) }.show()
    }
    private fun onCsv(uri: android.net.Uri?) {
        uri ?: return
        try {
            var name = "activities.csv"
            contentResolver.query(uri, null, null, null, null)?.use { c -> val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME); if (i >= 0 && c.moveToFirst()) name = c.getString(i) ?: name }
            val text = contentResolver.openInputStream(uri)?.use { String(it.readBytes()) } ?: return
            if (text.lines().count { it.isNotBlank() } < 2) { android.widget.Toast.makeText(this, "No data rows found in CSV.", android.widget.Toast.LENGTH_LONG).show(); return }
            if (text.length > 5_000_000) { android.widget.Toast.makeText(this, "That CSV is too large to send (over 5 MB).", android.widget.Toast.LENGTH_LONG).show(); return }
            S.importCsv(this, text, csvHint, name)
            android.widget.Toast.makeText(this, "Sent $name — the Command Center imports it (duplicates are skipped).", android.widget.Toast.LENGTH_LONG).show()
        } catch (e: Exception) { android.widget.Toast.makeText(this, "The selected file could not be read.", android.widget.Toast.LENGTH_LONG).show() }
    }

    /** 💾 Export — the Command Center's CSV: same columns, same quoting. */
    private fun exportCsv() {
        val acts = S.list(this)
        if (acts.isEmpty()) { android.widget.Toast.makeText(this, "No activities to export.", android.widget.Toast.LENGTH_SHORT).show(); return }
        val headers = listOf("date", "type", "title", "distance", "duration", "pace", "power", "heartRate", "cadence", "calories", "elevGain", "routeUrl", "source", "notes")
        csvText = (listOf(headers.joinToString(",")) + acts.map { a -> headers.joinToString(",") { h -> val v = S.str(a, h).let { if (it == "0" || it == "0.0" || it == "false") "" else it }; if (v.contains(",")) "\"$v\"" else v } }).joinToString("\n")
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/csv")
            .putExtra(Intent.EXTRA_TITLE, "activities_" + java.time.LocalDate.now() + ".csv"), SAVE_CSV)
    }

    /** 🖨️ Print — the Command Center's training-log export (current filters, oldest first): print / save as PDF, or copy as text for an AI. */
    private fun printLog(all: List<JSONObject>) {
        val ctx = this
        val list = filtered(all).sortedBy { S.str(it, "date") }
        if (all.isEmpty()) { android.widget.Toast.makeText(this, "No activities to print.", android.widget.Toast.LENGTH_SHORT).show(); return }
        if (list.isEmpty()) { android.widget.Toast.makeText(this, "No activities match your current filters.", android.widget.Toast.LENGTH_SHORT).show(); return }
        fun fd(ds: String) = try { java.time.LocalDate.parse(ds).format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US)) } catch (e: Exception) { ds }
        fun heatIndex(t: Double, r: Double): Double { if (t < 80) return t
            var hi = -42.379 + 2.04901523 * t + 10.14333127 * r - 0.22475541 * t * r - 0.00683783 * t * t - 0.05481717 * r * r + 0.00122874 * t * t * r + 0.00085282 * t * r * r - 0.00000199 * t * t * r * r
            if (r < 13 && t <= 112) hi -= ((13 - r) / 4) * Math.sqrt((17 - Math.abs(t - 95)) / 17); if (r > 85 && t <= 87) hi += ((r - 85) / 10) * ((87 - t) / 5); return hi }
        fun perf(a: JSONObject): List<String> { val p = ArrayList<String>(); val x = S.extras(this, a.opt("id")); val d = S.num(a, "distance")
            if (d > 0) p += "${S.str(a, "distance")} mi"; S.str(a, "duration").takeIf { it.isNotEmpty() }?.let { p += it }; S.str(a, "pace").takeIf { it.isNotEmpty() }?.let { p += "$it/mi pace" }
            if (S.num(a, "power") > 0) p += S.str(a, "power") + "W" + x.optString("pz").let { if (it.isNotEmpty()) " ($it)" else "" }
            if (S.num(a, "heartRate") > 0) p += S.str(a, "heartRate") + " bpm" + x.optString("hz").let { if (it.isNotEmpty()) " ($it)" else "" }
            if (S.num(a, "cadence") > 0) p += S.str(a, "cadence") + " spm"; if (S.num(a, "calories") > 0) p += S.str(a, "calories") + " cal"
            if (S.num(a, "elevGain") > 0) p += S.str(a, "elevGain") + " ft gain" + (if (d > 0) " (${Math.round(S.num(a, "elevGain") / d)} ft/mi)" else ""); return p }
        fun cond(a: JSONObject): List<String> { val c = ArrayList<String>()
            S.str(a, "temp").takeIf { it.isNotEmpty() }?.let { c += "$it°F" }; if (S.num(a, "humidity") > 0) c += S.str(a, "humidity") + "% humidity"
            (S.str(a, "airPower").takeIf { S.num(a, "airPower") > 0 } ?: S.str(a, "wind").takeIf { S.num(a, "wind") > 0 })?.let { c += "$it% air power" }
            if (S.num(a, "aqi") > 0) c += "AQI " + S.str(a, "aqi")
            val t = S.str(a, "temp").toDoubleOrNull(); val ps = S.paceSec(S.str(a, "pace"))
            if (t != null && ps > 0) { val h = S.str(a, "humidity").toDoubleOrNull() ?: 30.0; val over = heatIndex(t, h) - 60
                if (over > 0) { val pct = over * 0.4; if (pct >= 0.5) c += "est. heat/humidity impact +" + String.format(Locale.US, "%.1f", pct) + "% (" + Math.round(ps * pct / 100) + "s/mi)" } }
            return c }
        val miles = list.sumOf { S.num(it, "distance") }
        val first = S.str(list.first(), "date"); val last = S.str(list.last(), "date")
        val weeks = try { Math.max(1, Math.round(java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(first), java.time.LocalDate.parse(last)) / 7.0).toInt() + 1) } catch (e: Exception) { 1 }
        val s1 = "Range: " + (if (first.isNotEmpty()) fd(first) else "—") + " to " + (if (last.isNotEmpty()) fd(last) else "—") + " ($weeks week${if (weeks != 1) "s" else ""})"
        val s2 = "Activities: ${list.size} | Total distance: ${String.format(Locale.US, "%.1f", miles)} mi | Avg/week: ${String.format(Locale.US, "%.1f", miles / weeks)} mi"
        val plain = StringBuilder("TRAINING LOG EXPORT\nGenerated: " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date()) + "\n$s1\n$s2\n\n")
        list.forEach { a -> plain.append(fd(S.str(a, "date")) + " — " + S.str(a, "title").ifEmpty { "Activity" } + " (" + S.str(a, "type") + ")\n")
            perf(a).takeIf { it.isNotEmpty() }?.let { plain.append("  " + it.joinToString(" | ") + "\n") }
            cond(a).takeIf { it.isNotEmpty() }?.let { plain.append("  Conditions: " + it.joinToString(" | ") + "\n") }
            S.str(a, "notes").takeIf { it.isNotEmpty() }?.let { plain.append("  Notes: " + it.replace(Regex("\n+"), " ") + "\n") }; plain.append("\n") }
        AlertDialog.Builder(this).setTitle("🖨️ Training Log Export").setMessage("$s1\n$s2")
            .setPositiveButton("🖨️ Print / Save as PDF") { _, _ ->
                fun esc(t: String) = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                val rows = list.joinToString("") { a -> "<div class='e'><div class='h'>" + esc(fd(S.str(a, "date"))) + " &mdash; " + esc(S.str(a, "title").ifEmpty { "Activity" }) + " <span class='t'>(" + esc(S.str(a, "type")) + ")</span></div>" +
                    perf(a).let { if (it.isEmpty()) "" else "<div class='l'>" + esc(it.joinToString("  |  ")) + "</div>" } + cond(a).let { if (it.isEmpty()) "" else "<div class='l c'>" + esc(it.joinToString("  |  ")) + "</div>" } +
                    S.str(a, "notes").let { if (it.isEmpty()) "" else "<div class='n'>" + esc(it) + "</div>" } + "</div>" }
                val html = "<!DOCTYPE html><html><head><meta charset='utf-8'><style>body{font-family:sans-serif;color:#222;line-height:1.5;margin:24px}h1{font-size:1.3rem;margin-bottom:2px}.m{color:#555;font-size:.9rem;margin-bottom:18px}.e{border-bottom:1px solid #eee;padding:10px 0;page-break-inside:avoid}.h{font-weight:700;font-size:.95rem}.t{font-weight:400;color:#888}.l{font-size:.85rem;color:#333;margin-top:3px}.c{color:#777}.n{font-size:.83rem;color:#555;margin-top:4px;white-space:pre-wrap}</style></head><body><h1>Training Log Export</h1><div class='m'>" + esc(s1) + "<br>" + esc(s2) + "</div>" + rows + "</body></html>"
                val wv = android.webkit.WebView(ctx)
                wv.webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: android.webkit.WebView, url: String?) {
                        (getSystemService(PRINT_SERVICE) as android.print.PrintManager).print("Training Log Export", view.createPrintDocumentAdapter("Training Log Export"), android.print.PrintAttributes.Builder().build())
                    } }
                wv.loadDataWithBaseURL(null, html, "text/html", "utf-8", null); printView = wv
            }
            .setNeutralButton("📋 Copy as Text (for AI)") { _, _ ->
                (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Training log", plain.toString()))
                android.widget.Toast.makeText(ctx, "Copied!", android.widget.Toast.LENGTH_SHORT).show() }
            .setNegativeButton("Close", null).show()
    }
    private var printView: android.webkit.WebView? = null
}
