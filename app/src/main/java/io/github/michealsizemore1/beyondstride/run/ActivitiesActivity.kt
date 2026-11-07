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

    override fun onResume() {
        io.github.michealsizemore1.beyondstride.Theme.load(this, "runsettings")
        super.onResume(); render()
        Thread { if (S.refresh(this)) runOnUiThread { if (!isFinishing) render() } }.start()
    }

    private fun dp(v: Int) = RunUi.dp(this, v)
    private fun txt(t: String, c: Int, sp: Float, bold: Boolean = false) = RunUi.text(this, t, c, sp, bold)

    private fun render() {
        val ctx = this
        val all = S.list(this)
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
        if (all.isEmpty() && S.status(this).isNotEmpty()) col.addView(txt(S.status(this), RunUi.MUTED, 11f).apply { setPadding(0, dp(4), 0, 0) })

        // Buttons.
        col.addView(FlowLayout(this, dp(6), dp(6)).apply {
            setPadding(0, dp(10), 0, 0)
            fun btn(t: String, bg: Int, fg: Int = Color.WHITE, go: () -> Unit) = addView(txt(t, fg, 13f, true).apply {
                background = RunUi.rounded(bg, dp(8).toFloat()); setPadding(dp(12), dp(7), dp(12), dp(7)); setOnClickListener { go() } })
            btn("+ Add", 0xFFE67E22.toInt()) { startActivity(Intent(ctx, ActivityEditActivity::class.java)) }
            btn("⊞ Expand", RunUi.CARD) { collapsed.clear(); fill() }
            btn("⊟ Collapse", RunUi.CARD) { all.forEach { collapsed += it.opt("id").toString() }; fill() }
            btn(if (compare) "✖ Exit Compare" else "⚖️ Compare", if (compare) 0xFF4C1D95.toInt() else 0xFF7C3AED.toInt()) { compare = !compare; if (!compare) chosen.clear(); render() }
            btn("BeyondStride runs ▸", RunUi.CARD) { startActivity(Intent(ctx, AllRunsActivity::class.java)) }
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
        statsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(statsBox, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) })

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
        showing = txt("", RunUi.MUTED, 12f).apply { setPadding(dp(2), dp(8), 0, 0) }
        col.addView(showing)
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(listBox)
        fill()

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
        val id = a.opt("id").toString()
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
        a.opt("id")?.toString()?.toLongOrNull()?.takeIf { PhoneRuns.read(ctx, it) != null }?.let { start ->
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
        val acts = S.list(this).filter { it.opt("id").toString() in chosen }.sortedBy { S.str(it, "date") }
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
}
