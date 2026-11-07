package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * The Command Center's Activities analysis, same math: streak + 12-week load, Training Load Ratio (ACWR),
 * Personal Records, Estimated Race Times (incl. ultras), 26-Week Volume, Daily Miles, Cardiovascular Drift,
 * Heat Acclimation, Humidity Impact and the Run Metrics Summary.
 */
class ActivityAnalysis(private val act: Activity, private val open: MutableSet<String>, private val redraw: () -> Unit) {
    private val S = ActivityStore
    private fun dp(v: Int) = RunUi.dp(act, v)
    private fun txt(t: String, c: Int, sp: Float, bold: Boolean = false) = RunUi.text(act, t, c, sp, bold)
    private val ORANGE = 0xFFE67E22.toInt()
    private val today = LocalDate.now()
    private fun date(a: JSONObject) = try { LocalDate.parse(S.str(a, "date").take(10)) } catch (e: Exception) { null }
    private fun mins(a: JSONObject) = S.durSec(S.str(a, "duration")) / 60.0
    private fun fmtPace(sec: Double): String { val m = Math.floor(sec / 60).toInt(); val s = Math.round(sec % 60).toInt(); return "$m:" + (if (s < 10) "0" else "") + s }
    private fun fmtD(d: LocalDate?, p: String) = d?.format(DateTimeFormatter.ofPattern(p, Locale.US)) ?: ""
    private fun f1(v: Double) = String.format(Locale.US, "%.1f", v)
    private fun f2(v: Double) = String.format(Locale.US, "%.2f", v)
    private fun comma(v: Double) = String.format(Locale.US, "%,d", Math.round(v))
    private fun n(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else v.toString()
    private fun monday(d: LocalDate) = d.minusDays((d.dayOfWeek.value - 1).toLong())

    private fun card(col: LinearLayout, top: Int = 10) = RunUi.card(act).also { col.addView(it, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(top) }) }
    private fun head(t: String) = txt(t.uppercase(), RunUi.MUTED, 12f, true)
    private fun toggle(col: LinearLayout, key: String, title: String): Boolean {
        val isOpen = key in open
        col.addView(txt((if (isOpen) "▼" else "▶") + "  " + title, ORANGE, 15f, true).apply {
            setPadding(dp(2), dp(14), 0, dp(4)); setOnClickListener { if (isOpen) open.remove(key) else open.add(key); redraw() } })
        return isOpen
    }
    private fun grid(box: LinearLayout, cells: List<Triple<String, String, String?>>, extra: List<String?> = emptyList()) {
        cells.chunked(3).forEachIndexed { r, row ->
            box.addView(LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) }
                row.forEachIndexed { i, (v, l, d) ->
                    addView(LinearLayout(act).apply {
                        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                        background = RunUi.rounded(0xFF26302C.toInt(), dp(8).toFloat()); setPadding(dp(4), dp(8), dp(4), dp(8))
                        addView(txt(v, ORANGE, 15f, true).apply { gravity = Gravity.CENTER })
                        addView(txt(l, RunUi.MUTED, 10f, true).apply { gravity = Gravity.CENTER })
                        d?.let { addView(txt(it, RunUi.DIM, 10f).apply { gravity = Gravity.CENTER }) }
                        extra.getOrNull(r * 3 + i)?.let { addView(txt(it, 0xFFFCD34D.toInt(), 10f, true).apply { gravity = Gravity.CENTER }) }
                    }, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply { if (i < 2) marginEnd = dp(6) })
                }
                repeat(3 - row.size) { addView(android.view.View(act), LinearLayout.LayoutParams(0, 1, 1f)) }
            })
        }
    }
    private fun chart(box: LinearLayout, h: Int, setup: ActChart.() -> Unit) = box.addView(ActChart(act).apply(setup), LinearLayout.LayoutParams(MATCH_PARENT, dp(h)).apply { topMargin = dp(6) })

    /** 📈 Charts: 12-week streak, 26-week volume and daily miles (collapsible). */
    fun charts(col: LinearLayout, all: List<JSONObject>) {
        if (!toggle(col, "charts", "📈 Charts  (12 weeks · 26 weeks · 60 days)")) return
        streakLoad(col, all); volume(col, all); dailyMiles(col, all)
    }

    // ── Streak + 12-week load ──
    fun streakLoad(col: LinearLayout, all: List<JSONObject>) {
        val thisMon = monday(today); val N = 12
        val mi = DoubleArray(N); val cnt = IntArray(N)
        all.forEach { a -> val d = date(a) ?: return@forEach; val slot = N - 1 - (ChronoUnit.DAYS.between(monday(d), thisMon) / 7).toInt(); if (slot in 0 until N) { mi[slot] += S.num(a, "distance"); cnt[slot]++ } }
        var streak = 0; for (i in N - 1 downTo 0) { if (cnt[i] > 0) streak++ else break }
        val box = card(col)
        if (streak > 0) box.addView(txt("🔥 $streak-week streak", ORANGE, 14f, true))
        chart(box, 64) {
            values = mi.toList(); colors = (0 until N).map { if (it == N - 1) ORANGE else 0xFFF0C080.toInt() }
            labels = (0 until N).map { val m = thisMon.minusWeeks((N - 1 - it).toLong()); if (it % 3 == 0) m.monthValue.toString() else "" }
            tip = { val m = thisMon.minusWeeks((N - 1 - it).toLong()); "${m.monthValue}/${m.dayOfMonth}: ${f1(mi[it])} mi" }
        }
    }

    // ── ⚡ Training Load Ratio (ACWR) ──
    fun acwr(col: LinearLayout, all: List<JSONObject>) {
        val daily = HashMap<LocalDate, Double>()
        all.forEach { a -> val d = date(a) ?: return@forEach; val m = S.num(a, "distance"); if (m > 0) daily[d] = (daily[d] ?: 0.0) + m }
        fun last(n: Int) = (0 until n).sumOf { daily[today.minusDays(it.toLong())] ?: 0.0 }
        val acute = last(7) / 7; val chronic = last(28) / 28
        if (chronic <= 0) return
        val r = acute / chronic
        val (c, l) = when { r < 0.8 -> 0xFF5DADE2.toInt() to "Low load (easing back)"; r <= 1.3 -> 0xFF27AE60.toInt() to "Sweet spot"
            r <= 1.5 -> 0xFFF39C12.toInt() to "Elevated — watch for soreness"; else -> 0xFFE74C3C.toInt() to "High — classic \"too much too soon\" range" }
        val box = card(col)
        box.addView(head("⚡ Training Load Ratio (ACWR · miles)"))
        box.addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM
            addView(txt(f2(r), c, 22f, true)); addView(txt("   $l", c, 13f, true)) })
        box.addView(txt("Last 7 days’ daily average (${f1(acute)} mi/day) vs. last 28 days’ (${f1(chronic)} mi/day). An estimate from your logged mileage — not a medical assessment.", RunUi.DIM, 11f))
    }

    // ── 🏆 Personal Records ──
    fun prs(col: LinearLayout, all: List<JSONObject>) {
        fun best(sel: (JSONObject) -> Double, lower: Boolean = false) = all.filter { sel(it) > 0 }.let { l -> if (lower) l.minByOrNull(sel) else l.maxByOrNull(sel) }
        val fmt = { a: JSONObject -> fmtD(date(a), "MMM d, yy") }
        val cells = ArrayList<Triple<String, String, String?>>()
        best({ S.num(it, "distance") })?.let { cells += Triple(f2(S.num(it, "distance")) + " mi", "Longest Run", fmt(it)) }
        best({ S.paceSec(S.str(it, "pace")).toDouble() }, true)?.let { cells += Triple(S.str(it, "pace") + "/mi", "Fastest Pace", fmt(it)) }
        best({ S.num(it, "power") })?.let { cells += Triple(n(S.num(it, "power")) + "W", "Peak Avg Power", fmt(it)) }
        best({ S.num(it, "elevGain") })?.let { cells += Triple(comma(S.num(it, "elevGain")) + " ft", "Most Elev Gain", fmt(it)) }
        best({ S.num(it, "calories") })?.let { cells += Triple(comma(S.num(it, "calories")) + " cal", "Most Calories", fmt(it)) }
        if (cells.isEmpty()) return
        if (!toggle(col, "pr", "🏆 Personal Records")) return
        grid(col, cells)
    }

    // ── 🏁 Estimated Race Times ──
    fun racePredictor(col: LinearLayout, all: List<JSONObject>) {
        var best: JSONObject? = null; var bestP = 0
        all.forEach { a -> val d = date(a) ?: return@forEach; val ago = ChronoUnit.DAYS.between(d, today); if (ago !in 0..59) return@forEach
            val p = S.paceSec(S.str(a, "pace")); val mi = S.num(a, "distance"); if (p > 0 && mi >= 1 && (best == null || p < bestP)) { best = a; bestP = p } }
        val b = best ?: return
        val baseD = S.num(b, "distance"); val baseT = bestP * baseD
        if (baseD <= 0 || baseT <= 0) return
        fun clock(sec0: Double): String { val sec = Math.round(sec0); val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
            return (if (h > 0) "$h:" else "") + (if (h > 0) String.format(Locale.US, "%02d", m) else "$m") + ":" + String.format(Locale.US, "%02d", s) }
        fun riegel(mi: Double) = baseT * Math.pow(mi / baseD, 1.06)
        val box = card(col)
        box.addView(head("🏁 Estimated Race Times"))
        grid(box, listOf("Mile" to 1.0, "2 Mile" to 2.0, "5K" to 3.10686, "10K" to 6.21371, "Half" to 13.1094, "Marathon" to 26.2188).map { (l, mi) -> Triple(clock(riegel(mi)), l, null) })
        box.addView(txt("Projected with the Riegel formula from your fastest logged pace in the last 60 days (${S.str(b, "pace")}/mi over ${f1(baseD)} mi on ${S.str(b, "date")}). An estimate, not a guarantee — actual race-day performance depends on training, terrain, and conditions.", RunUi.DIM, 11f).apply { setPadding(0, dp(6), 0, dp(10)) })
        box.addView(head("🏔️ Ultra Distances"))
        fun cr(i: Double) = 155.4 * Math.pow(i, 5.0) - 30.4 * Math.pow(i, 4.0) - 43.3 * Math.pow(i, 3.0) + 46.3 * i * i + 19.5 * i + 3.6
        val elevM = 5000 * 0.3048
        val ultras = listOf(Triple("50K", 31.0686, 0.08), Triple("50 Mile", 50.0, 0.15), Triple("100K", 62.1371, 0.22), Triple("100 Mile", 100.0, 0.30))
        val adj = ultras.map { (_, mi, fade) -> val g = Math.min(elevM / (mi * 1609.344 / 2), 0.45); riegel(mi) * (1 + fade) * ((cr(g) / cr(0.0) + cr(-g) / cr(0.0)) / 2) }
        grid(box, ultras.map { (l, mi, _) -> Triple(clock(riegel(mi)), l, null) }, adj.map { "Adjusted: " + clock(it) })
        box.addView(txt("The top number is the same flat Riegel projection stretched out to ultra distance — Riegel is only validated up to about marathon length, so by itself it tends to run optimistic at 50K and beyond. \"Adjusted\" layers on two corrections: a distance-based fatigue/fueling allowance (+8% at 50K, +15% at 50mi, +22% at 100K, +30% at 100mi — rough estimates from aggregate ultrarunning race data, not a validated constant), and a terrain cost from the Minetti running-economy equation, applied to this distance’s average grade assuming your 5,000 ft of climbing is spread over half the distance and mirrored on the way down. Both numbers are ballparks, not predictions.", RunUi.DIM, 11f).apply { setPadding(0, dp(6), 0, 0) })
    }

    // ── 📊 26-Week Volume (Miles / Power) ──
    fun volume(col: LinearLayout, all: List<JSONObject>) {
        val power = "volPower" in open
        val thisMon = monday(today); val N = 26
        val mi = DoubleArray(N); val ps = DoubleArray(N); val pc = IntArray(N)
        all.forEach { a -> val d = date(a) ?: return@forEach; val slot = N - 1 - (ChronoUnit.DAYS.between(monday(d), thisMon) / 7).toInt()
            if (slot in 0 until N) { mi[slot] += S.num(a, "distance"); val p = S.num(a, "power"); if (p > 0) { ps[slot] += p; pc[slot]++ } } }
        val vals = if (power) (0 until N).map { if (pc[it] > 0) Math.round(ps[it] / pc[it]).toDouble() else 0.0 } else mi.toList()
        val box = card(col)
        box.addView(LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(txt("📊 26-Week Volume", Color.WHITE, 14f, true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            listOf("Miles" to false, "Power" to true).forEach { (l, pw) -> addView(txt(l, if (pw == power) Color.BLACK else Color.WHITE, 12f, true).apply {
                background = RunUi.rounded(if (pw == power) ORANGE else 0xFF26302C.toInt(), dp(6).toFloat()); setPadding(dp(10), dp(4), dp(10), dp(4))
                layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = dp(4) }
                setOnClickListener { if (pw) open.add("volPower") else open.remove("volPower"); redraw() } }) }
        })
        if (all.isEmpty()) { box.addView(txt("No activities yet", RunUi.DIM, 12f)); return }
        chart(box, 130) {
            values = vals; colors = (0 until N).map { if (it == N - 1) ORANGE else if (vals[it] > 0) 0xFFF0A050.toInt() else 0xFF3A3F3D.toInt() }
            labels = (0 until N).map { val m = thisMon.minusWeeks((N - 1 - it).toLong()); "${m.monthValue}/${m.dayOfMonth}" }
            showLabel = { it % 4 == 0 || it == N - 1 }
            barText = { i -> if (vals[i] > 0 && (i % 2 == 1 || i == N - 1)) (if (power) n(vals[i]) else f1(vals[i])) else null }
            tip = { i -> labels[i] + ": " + (if (power) "${n(vals[i])} W" else "${f1(vals[i])} mi") }
        }
    }

    // ── Daily Miles (last 60 days) ──
    fun dailyMiles(col: LinearLayout, all: List<JSONObject>) {
        val ex = S.exMiles(act)
        val byDay = HashMap<String, Double>(); all.forEach { a -> byDay[S.str(a, "date")] = (byDay[S.str(a, "date")] ?: 0.0) + S.num(a, "distance") }
        val days = (59 downTo 0).map { today.minusDays(it.toLong()) }
        val vals = days.map { d -> (byDay[d.toString()] ?: 0.0).takeIf { it > 0 } ?: ex[d.toString()] ?: 0.0 }
        val box = card(col)
        box.addView(txt("🏃 Daily Miles", Color.WHITE, 14f, true))
        val with = vals.filter { it > 0 }
        if (with.isEmpty()) { box.addView(txt("No mileage logged in the last 60 days. Log a run on the Activities tab to see it here.", RunUi.MUTED, 12f)); return }
        chart(box, 120) {
            values = vals; color = 0xBF27AE60.toInt(); yMin = 0.0; yMax = (vals.maxOrNull() ?: 0.0) + 0.5
            labels = days.map { "${it.monthValue}/${it.dayOfMonth}" }; showLabel = { it % 10 == 0 || it == 59 }
            tip = { i -> labels[i] + ": " + f1(vals[i]) + " mi" }
        }
        val tot = with.sum()
        box.addView(txt("60-day total: ${f1(tot)} mi  •  Avg run day: ${f1(tot / with.size)} mi  (${with.size} run days)", RunUi.MUTED, 11f).apply { gravity = Gravity.CENTER })
    }

    // ── ❤️🌡️ Cardiovascular Drift ──
    private fun zones(): List<Triple<String, Pair<Int, Int>, Int>> {
        val st = S.settings(act); val max = st.optDouble("maxHr", 0.0).takeIf { it > 0 } ?: 174.0; val rest = st.optDouble("restHr", 0.0).takeIf { it > 0 } ?: 55.0
        val b = listOf(0.5, 0.6, 0.7, 0.8, 0.9).map { Math.round(rest + Math.max(1.0, max - rest) * it).toInt() }
        val c = listOf(0xFF5DADE2, 0xFF58D68D, 0xFFF4D03F, 0xFFE67E22, 0xFFE74C3C).map { it.toInt() }
        return (0 until 5).map { i -> Triple("Z${i + 1}", b[i] to (if (i < 4) b[i + 1] - 1 else 220), c[i]) }
    }
    private fun hrZone(hr: Double) = zones().let { z -> if (hr < z[0].second.first) null else z.last { hr >= it.second.first } }

    fun cardioDrift(col: LinearLayout, all: List<JSONObject>) {
        if (!toggle(col, "cvd", "❤️🌡️ Cardiovascular Drift")) return
        val box = card(col, 4)
        box.addView(txt("Average heart rate by temperature, measured against your heart-rate training zones. Heat makes the heart work harder to cool the body, so a climb across these bars is expected — a climb that keeps getting steeper over time can be an early sign of heat strain or fading heat acclimation.", RunUi.MUTED, 11f))
        val acts = all.filter { S.str(it, "type") != "Rest Day" && S.num(it, "heartRate") > 0 && S.str(it, "temp").toDoubleOrNull() != null }
        val z = zones()
        if (acts.size < 3) { box.addView(txt("Log at least 3 activities with temperature and heart rate to see your heat drift here.", 0xFFFCD34D.toInt(), 12f).apply { setPadding(0, dp(8), 0, 0) }); return }
        val buckets = listOf(Triple("<60°F", Double.NEGATIVE_INFINITY, 59.0), Triple("60-69°F", 60.0, 69.0), Triple("70-79°F", 70.0, 79.0), Triple("80-89°F", 80.0, 89.0), Triple("90°F+", 90.0, Double.POSITIVE_INFINITY))
        val sum = DoubleArray(5); val cnt = IntArray(5)
        acts.forEach { a -> val t = S.str(a, "temp").toDouble(); val i = buckets.indexOfFirst { t >= it.second && t <= it.third }; if (i >= 0) { sum[i] += S.num(a, "heartRate"); cnt[i]++ } }
        val used = (0 until 5).filter { cnt[it] > 0 }
        val avg = used.map { Math.round(sum[it] / cnt[it]).toDouble() }
        val allHr = acts.map { S.num(it, "heartRate") } + avg
        val lo = Math.max(60.0, Math.floor(((allHr.minOrNull() ?: 60.0) - 10) / 5) * 5); val hi = Math.ceil(((allHr.maxOrNull() ?: 100.0) + 10) / 5) * 5
        chart(box, 220) {
            values = avg; labels = used.map { buckets[it].first }; colors = avg.map { hrZone(it)?.third ?: 0xFFB0B8C1.toInt() }
            yMin = lo; yMax = hi; yStep = 10.0; xTitle = "Temperature"; yTitle = "Avg HR (bpm)"
            bands = z.map { Triple(it.second.first.toDouble(), it.second.second.toDouble(), it.third) }
            tip = { i -> "Avg HR: ${n(avg[i])} bpm" + (hrZone(avg[i])?.let { " (${it.first})" } ?: "") + " • ${cnt[used[i]]} activit" + (if (cnt[used[i]] == 1) "y" else "ies") }
        }
        box.addView(FlowLayout(act, dp(8), dp(2)).apply { setPadding(0, dp(4), 0, 0)
            z.forEach { addView(txt("■ ${it.first} • ${it.second.first}" + (if (it.second.second < 220) "–${it.second.second}" else "+") + " bpm", it.third, 10f, true)) } })
        val hot = acts.filter { S.str(it, "temp").toDouble() >= 80 && S.str(it, "date").isNotEmpty() }.sortedBy { S.str(it, "date") }
        val insight = if (hot.size >= 4) {
            val half = hot.size / 2
            val e = hot.take(half).map { S.num(it, "heartRate") }.average(); val r = hot.drop(half).map { S.num(it, "heartRate") }.average()
            val delta = Math.round(r - e).toInt()
            "In 80°F+ conditions, your average heart rate is now " + (if (delta == 0) "about the same as" else "${Math.abs(delta)} bpm " + (if (delta > 0) "higher" else "lower") + " than") +
                " your earliest logged hot-weather activities (${Math.round(e)} → ${Math.round(r)} bpm across ${hot.size} runs)." +
                (if (delta > 2) " That upward drift can be a sign of heat strain — consider more fluids, slower paces in the heat, or extra recovery." else if (delta < -2) " That’s a good sign of heat acclimation." else "")
        } else "Log a few more 80°F+ activities with heart rate to see your heat-drift trend over time."
        box.addView(txt(insight, 0xFFFCD34D.toInt(), 12f).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) })
    }

    // ── 🌡️💧 Heat Acclimation ──
    fun heatAcclimation(col: LinearLayout, all: List<JSONObject>) {
        if (!toggle(col, "ha", "🌡️💧 Heat Acclimation")) return
        val box = card(col, 4)
        box.addView(txt("Estimated from the temperature and duration you log on each activity — similar to the Heat Acclimation status on higher-end Garmin watches (Enduro, Fenix, Epix), but built from your logged data rather than a core-temperature sensor. It builds up with repeated time spent training in the heat and fades within a couple of weeks without it.", RunUi.MUTED, 11f))
        fun factor(t: Double) = if (t <= 70) 0.0 else if (t >= 95) 1.0 else (t - 70) / 25
        fun pctOf(load: Double) = Math.max(0, Math.min(100, Math.round(100 * (1 - Math.exp(-load / 6))).toInt()))
        val valid = all.filter { S.str(it, "type") != "Rest Day" && S.str(it, "temp").toDoubleOrNull() != null && date(it) != null && mins(it) > 0 }
        if (valid.isEmpty()) { box.addView(txt("—  No data yet", Color.WHITE, 16f, true)); box.addView(txt("Log a few activities with temperature and duration to see your heat acclimation estimate.", RunUi.MUTED, 12f)); return }
        val byDate = HashMap<LocalDate, Double>()
        valid.forEach { a -> val s = factor(S.str(a, "temp").toDouble()) * Math.min(mins(a) / 60, 3.0); if (s > 0) byDate[date(a)!!] = (byDate[date(a)!!] ?: 0.0) + s }
        val earliest = valid.mapNotNull { date(it) }.filter { !it.isAfter(today) }.minOrNull() ?: today
        val daysBack = Math.min(730, Math.max(1, ChronoUnit.DAYS.between(earliest, today).toInt() + 1))
        var load = 0.0; val series = ArrayList<Pair<LocalDate, Double>>()
        for (i in daysBack - 1 downTo 0) { val d = today.minusDays(i.toLong()); load = load * 0.96 + (byDate[d] ?: 0.0); series += d to load }
        val pct = pctOf(series.last().second)
        val (label, color) = when { pct < 20 -> "Not Acclimated" to 0xFFDC2626.toInt(); pct < 45 -> "Minimally Acclimated" to 0xFFF97316.toInt(); pct < 70 -> "Acclimatizing" to 0xFFCA8A04.toInt()
            pct < 90 -> "Mostly Acclimated" to 0xFF65A30D.toInt(); else -> "Well Acclimated" to 0xFF16A34A.toInt() }
        box.addView(LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, dp(4))
            addView(txt("$pct%", color, 30f, true), LinearLayout.LayoutParams(dp(84), WRAP_CONTENT))
            addView(LinearLayout(act).apply { orientation = LinearLayout.VERTICAL
                addView(txt(label, color, 14f, true))
                addView(object : android.view.View(act) {
                    override fun onDraw(c: android.graphics.Canvas) {
                        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG); val h = height.toFloat(); val r = h / 2
                        p.shader = android.graphics.LinearGradient(0f, 0f, width.toFloat(), 0f, intArrayOf(0xFFDC2626.toInt(), 0xFFF97316.toInt(), 0xFFFACC15.toInt(), 0xFF84CC16.toInt(), 0xFF16A34A.toInt()), null, android.graphics.Shader.TileMode.CLAMP)
                        c.drawRoundRect(0f, h * 0.2f, width.toFloat(), h * 0.8f, r, r, p); p.shader = null
                        val x = (width * pct / 100f).coerceIn(r, width - r); p.color = Color.WHITE; c.drawCircle(x, h / 2, r * 0.9f, p)
                        p.color = 0xFF0F172A.toInt(); p.style = android.graphics.Paint.Style.STROKE; p.strokeWidth = r * 0.35f; c.drawCircle(x, h / 2, r * 0.75f, p)
                    } }, LinearLayout.LayoutParams(MATCH_PARENT, dp(16)).apply { topMargin = dp(4) })
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        })
        val last30 = series.takeLast(30)
        chart(box, 160) {
            line = true; fill = true; values = last30.map { pctOf(it.second).toDouble() }; labels = last30.map { "${it.first.monthValue}/${it.first.dayOfMonth}" }
            yMin = 0.0; yMax = 100.0; yStep = 25.0; showLabel = { it % 6 == 0 || it == last30.size - 1 }; tip = { i -> "Heat Acclimation: ${n(values[i])}%" }
        }
        val heat = valid.filter { S.str(it, "temp").toDouble() > 70 }.sortedByDescending { S.str(it, "date") }
        val since = heat.firstOrNull()?.let { ChronoUnit.DAYS.between(date(it), today).toInt() }
        val s14 = heat.count { !date(it)!!.isBefore(today.minusDays(14)) }
        box.addView(txt("$s14 session${if (s14 == 1) "" else "s"} over 70°F in the last 14 days • " + (if (since == null) "no heat sessions logged yet" else "last one " + when (since) { 0 -> "today"; 1 -> "1 day ago"; else -> "$since days ago" }),
            0xFFFCD34D.toInt(), 12f).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) })
    }

    // ── 💧📊 Humidity Impact ──
    fun humidity(col: LinearLayout, all: List<JSONObject>) {
        if (!toggle(col, "hum", "💧📊 Humidity Impact")) return
        val box = card(col, 4)
        box.addView(txt("Average pace grouped by the humidity you logged for each run. Humid air makes it harder for sweat to evaporate and cool the body, so pace tends to slow as humidity climbs — this is a rough correlation across your own logged runs, not a controlled comparison (temperature and effort still vary run to run).", RunUi.MUTED, 11f))
        val acts = all.filter { S.str(it, "type") != "Rest Day" && S.str(it, "humidity").toDoubleOrNull() != null && S.paceSec(S.str(it, "pace")) > 0 }
        if (acts.size < 3) { box.addView(txt("Log at least 3 runs with humidity and pace to see how humidity correlates with your pace.", 0xFFFCD34D.toInt(), 12f).apply { setPadding(0, dp(8), 0, 0) }); return }
        val buckets = listOf(Triple("<40%", Double.NEGATIVE_INFINITY, 39.0), Triple("40-54%", 40.0, 54.0), Triple("55-69%", 55.0, 69.0), Triple("70-84%", 70.0, 84.0), Triple("85%+", 85.0, Double.POSITIVE_INFINITY))
        val sum = DoubleArray(5); val cnt = IntArray(5)
        acts.forEach { a -> val h = S.str(a, "humidity").toDouble(); val i = buckets.indexOfFirst { h >= it.second && h <= it.third }; if (i >= 0) { sum[i] += S.paceSec(S.str(a, "pace")); cnt[i]++ } }
        val used = (0 until 5).filter { cnt[it] > 0 }; val avg = used.map { sum[it] / cnt[it] }
        val allP = acts.map { S.paceSec(S.str(it, "pace")).toDouble() } + avg
        chart(box, 220) {
            values = avg; labels = used.map { buckets[it].first }; color = 0xFF2980B9.toInt()
            yMin = Math.max(0.0, Math.floor(((allP.minOrNull() ?: 0.0) - 20) / 10) * 10); yMax = Math.ceil(((allP.maxOrNull() ?: 600.0) + 20) / 10) * 10
            yStep = 30.0; yFormat = { fmtPace(it) }; xTitle = "Humidity"; yTitle = "Avg Pace (min/mi)"
            tip = { i -> "Avg Pace: ${fmtPace(avg[i])}/mi • ${cnt[used[i]]} run" + (if (cnt[used[i]] == 1) "" else "s") }
        }
        val insight = if (used.size >= 2) {
            val dA = avg.first(); val hA = avg.last(); val diff = hA - dA
            if (Math.abs(diff) >= 3) "Runs at ${buckets[used.last()].first} humidity averaged ${fmtPace(hA)}/mi vs. ${fmtPace(dA)}/mi at ${buckets[used.first()].first} humidity — about ${Math.round(Math.abs(diff))}s ${if (diff > 0) "slower" else "faster"} per mile in the most humid conditions logged so far."
            else "No clear pace difference across humidity levels yet in your logged runs."
        } else "Log runs across a wider range of humidity to see a comparison here."
        box.addView(txt(insight, 0xFFFCD34D.toInt(), 12f).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) })
    }

    // ── 📊 Run Metrics Summary ──
    fun metrics(col: LinearLayout, all: List<JSONObject>) {
        if (!toggle(col, "metrics", "📊 Run Metrics Summary")) return
        val box = card(col, 4)
        if (all.isEmpty()) { box.addView(txt("No activities recorded yet.", RunUi.MUTED, 13f)); return }
        val runs = all.filter { S.str(it, "type").lowercase().contains("run") }
        fun durS(a: JSONObject): Int { val p = S.str(a, "duration").trim().split(":").map { it.toIntOrNull() ?: 0 }; return when (p.size) { 3 -> p[0] * 3600 + p[1] * 60 + p[2]; 2 -> p[0] * 60 + p[1]; else -> 0 } }
        fun fmtS(s: Int): String { val h = s / 3600; val m = (s % 3600) / 60; return if (h > 0) "${h}h " + (if (m > 0) "${m}m" else "") else "${m}m" }
        val long = DoubleArray(1); var longA: JSONObject? = null; var bestP = 0; var bestA: JSONObject? = null
        var tm = 0.0; var ts = 0; var te = 0.0; var hrS = 0.0; var hrN = 0; var cadS = 0.0; var cadN = 0; var pS = 0.0; var pN = 0; var ym = 0.0; var mm = 0.0
        val weeks = HashMap<LocalDate, Double>(); val months = HashMap<String, Double>()
        runs.forEach { a -> val mi = S.num(a, "distance"); tm += mi; ts += durS(a); te += S.num(a, "elevGain")
            if (S.num(a, "heartRate") > 0) { hrS += S.num(a, "heartRate"); hrN++ }; if (S.num(a, "cadence") > 0) { cadS += S.num(a, "cadence"); cadN++ }
            if (mi > long[0]) { long[0] = mi; longA = a }
            val ps = S.paceSec(S.str(a, "pace")); if (ps > 0) { if (bestP == 0 || ps < bestP) { bestP = ps; bestA = a }; pS += ps; pN++ }
            date(a)?.let { d -> if (d.year == today.year) { ym += mi; if (d.monthValue == today.monthValue) mm += mi }
                if (mi > 0) { weeks[monday(d)] = (weeks[monday(d)] ?: 0.0) + mi; val mk = "${d.year}-${String.format("%02d", d.monthValue)}"; months[mk] = (months[mk] ?: 0.0) + mi } } }
        val runs10 = runs.filter { S.num(it, "distance") >= 10 }.sortedByDescending { S.num(it, "distance") }
        fun avgOf(k: String) = all.map { S.num(it, k) }.filter { it > 0 }
        val aqi = avgOf("aqi"); val hum = avgOf("humidity"); val air = all.map { S.num(it, "airPower").takeIf { v -> v > 0 } ?: S.num(it, "wind") }.filter { it > 0 }
        fun aqiCat(v: Int) = when { v <= 50 -> "Good"; v <= 100 -> "Moderate"; v <= 150 -> "Unhealthy for sensitive groups"; v <= 200 -> "Unhealthy"; v <= 300 -> "Very unhealthy"; else -> "Hazardous" }
        fun sub(a: JSONObject?) = a?.let { (S.str(it, "title").takeIf { t -> t.isNotEmpty() }?.let { t -> "$t • " } ?: "") + fmtD(date(it), "MMM d") } ?: ""
        fun maxA(k: String) = all.filter { S.num(it, k) > 0 }.maxByOrNull { S.num(it, k) }
        val hw = weeks.maxByOrNull { it.value }; val hm = months.maxByOrNull { it.value }
        val cells = ArrayList<Triple<String, String, String>>()
        fun m(icon: String, l: String, v: String, s: String) { cells += Triple("$icon $l", v, s) }
        m("🏃", "Total Runs", runs.size.toString(), "all time"); m("📏", "Total Miles", f1(tm) + " mi", "all time")
        m("🏆", "Longest Run", f2(long[0]) + " mi", longA?.let { (S.str(it, "title").takeIf { t -> t.isNotEmpty() }?.let { t -> "$t • " } ?: "") + fmtD(date(it), "MMM d, yyyy") } ?: "")
        m("⚡", "Best Pace", if (bestP > 0) fmtPace(bestP.toDouble()) + "/mi" else "-", if (bestP > 0) sub(bestA) else "")
        m("⏱", "Avg Pace", if (pN > 0) fmtPace(pS / pN) + "/mi" else "-", "per run"); m("📐", "Avg Distance", f2(if (runs.isNotEmpty()) tm / runs.size else 0.0) + " mi", "per run")
        m("🦵", "Average Cadence", if (cadN > 0) "${Math.round(cadS / cadN)} spm" else "-", if (cadN > 0) "$cadN run${if (cadN != 1) "s" else ""} averaged" else "No cadence recorded yet")
        val avgAir = if (air.isNotEmpty()) Math.round(air.average() * 10) / 10.0 else 0.0
        m("💨", "Average Air Power", if (avgAir > 0) "${n(avgAir)}%" else "-", if (avgAir > 0) "of running power • ${air.size} run${if (air.size != 1) "s" else ""}" else "No air power recorded yet")
        val aq = if (aqi.isNotEmpty()) Math.round(aqi.average()).toInt() else 0
        m("🌬️", "Average AQI", if (aq > 0) "$aq" else "-", if (aq > 0) "${aqiCat(aq)} • ${aqi.size} recorded workout${if (aqi.size != 1) "s" else ""}" else "No AQI recorded yet")
        val hu = if (hum.isNotEmpty()) Math.round(hum.average()).toInt() else 0
        m("💧", "Average Humidity", if (hu > 0) "$hu%" else "-", if (hu > 0) "${hum.size} recorded workout${if (hum.size != 1) "s" else ""}" else "No humidity recorded yet")
        m("⏳", "Total Time", if (ts > 0) fmtS(ts) else "-", "all runs"); m("📅", "This Month", f1(mm) + " mi", ""); m("📆", "This Year", f1(ym) + " mi", "")
        m("📈", "Highest Mileage Week", hw?.let { f1(it.value) + " mi" } ?: "-", hw?.let { fmtD(it.key, "MMM d") + " - " + fmtD(it.key.plusDays(6), "MMM d") } ?: "No mileage recorded yet")
        m("🗓️", "Highest Mileage Month", hm?.let { f1(it.value) + " mi" } ?: "-", hm?.let { LocalDate.parse(it.key + "-01").format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)) } ?: "No mileage recorded yet")
        maxA("power")?.let { m("⚡", "Peak Average Watt", "${Math.round(S.num(it, "power"))} W", sub(it)) }
        maxA("elevGain")?.let { m("⛰", "Most Elevation Gain", comma(S.num(it, "elevGain")) + " ft", sub(it)) }
        maxA("calories")?.let { m("🔥", "Most Calories Burned", comma(S.num(it, "calories")) + " cal", sub(it)) }
        if (hrN > 0) m("❤", "Avg Heart Rate", "${Math.round(hrS / hrN)} bpm", "$hrN run${if (hrN != 1) "s" else ""} averaged")
        if (te > 0) m("⛰", "Total Elevation", comma(te) + " ft", "all runs")
        cells.chunked(2).forEach { row -> box.addView(LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) }
            row.forEachIndexed { i, (l, v, s) -> addView(LinearLayout(act).apply { orientation = LinearLayout.VERTICAL
                background = RunUi.rounded(0xFF26302C.toInt(), dp(8).toFloat()); setPadding(dp(10), dp(8), dp(10), dp(8))
                addView(txt(l, 0xFFD4A84B.toInt(), 11f, true)); addView(txt(v, ORANGE, 16f, true)); if (s.isNotEmpty()) addView(txt(s, RunUi.MUTED, 11f)) },
                LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply { if (i == 0) marginEnd = dp(6) }) }
            if (row.size == 1) addView(android.view.View(act), LinearLayout.LayoutParams(0, 1, 1f))
        }) }
        box.addView(txt("⭐ Runs ≥ 10 Miles (${runs10.size})", ORANGE, 13f, true).apply { setPadding(0, dp(12), 0, dp(4)) })
        if (runs10.isEmpty()) box.addView(txt("No runs ≥ 10 miles yet.", RunUi.MUTED, 12f))
        runs10.forEach { a -> box.addView(LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(dp(8), dp(5), dp(8), dp(5)); background = RunUi.rounded(0xFF26302C.toInt(), dp(6).toFloat())
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(4) }
            addView(txt(S.str(a, "title").ifEmpty { S.str(a, "type").ifEmpty { "Run" } }, Color.WHITE, 12f, true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(txt(f2(S.num(a, "distance")) + " mi" + (S.str(a, "pace").takeIf { it.isNotEmpty() }?.let { " • $it/mi" } ?: ""), ORANGE, 12f, true))
            addView(txt("  " + fmtD(date(a), "MMM d, yyyy"), RunUi.DIM, 11f))
        }) }
    }
}
