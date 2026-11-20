package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import io.github.michealsizemore1.beyondstride.Food
import io.github.michealsizemore1.beyondstride.Nutrition
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Tables that used to be on Life → Weekly / Monthly / Yearly, now where their numbers live:
 *  - [NutritionWeek]: 🍎 Nutrition and 🧪 Micronutrients by day (Nutrition tab), colored like the Command Center.
 *  - [MileageBreakdown]: 🏃 miles, pace, power and time by day / week / month (Activities → Mileage).
 */
object WeekTables {
    val GREEN = 0xFF4ADE80.toInt(); val AMBER = 0xFFF59E0B.toInt(); val RED = 0xFFF87171.toInt(); val PILL = 0xFFEF4444.toInt(); val GRAY = 0xFF94A3B8.toInt()
    /** One line of a cell: text, color, bold, and a red pill behind it (High). */
    data class Line(val t: String, val c: Int = Color.WHITE, val b: Boolean = false, val pill: Boolean = false)
    fun cell(vararg l: Line) = l.toList()
    fun dash() = cell(Line("-", RunUi.MUTED))

    /** A table: header (with optional colored dots), rows of cells; first column and the last (total) row in the accent color. */
    fun table(act: Activity, head: List<Pair<String, Int?>>, rows: List<Pair<String, List<List<Line>>>>, totalLast: Boolean = true): View {
        val d = { v: Int -> RunUi.dp(act, v) }
        val t = TableLayout(act)
        t.addView(TableRow(act).apply {
            head.forEach { (h, dot) -> addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(d(6), d(4), d(10), d(4))
                if (dot != null) addView(View(act).apply { background = RunUi.rounded(dot, d(4).toFloat()) }, LinearLayout.LayoutParams(d(8), d(8)).apply { marginEnd = d(5) })
                addView(RunUi.text(act, h, RunUi.MUTED, 12f, true)) }) } })
        rows.forEachIndexed { ri, (label, cells) ->
            val total = totalLast && ri == rows.size - 1
            t.addView(TableRow(act).apply {
                addView(RunUi.text(act, label, RunUi.ACCENT, 12f, true).apply { setPadding(d(6), d(4), d(10), d(4)) })
                cells.forEach { lines -> addView(LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(d(6), d(4), d(10), d(4))
                    lines.forEach { l ->
                        val tv = RunUi.text(act, l.t, if (l.pill) Color.WHITE else if (total && l.c == Color.WHITE) RunUi.ACCENT else l.c, 12f, l.b || total).apply { isSingleLine = true }
                        if (l.pill) { tv.background = RunUi.rounded(PILL, d(10).toFloat()); tv.setPadding(d(8), d(1), d(8), d(1))
                            addView(tv, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = d(2) }) } else addView(tv)
                    } }) }
            })
        }
        return LifeUi.pinFirstColumn(t)   // dates stay put while the other columns scroll
    }
    private fun n0(v: Double) = String.format(Locale.US, "%,d", Math.round(v))
    private fun weekLabel(mon: LocalDate): String { val f = DateTimeFormatter.ofPattern("MMM d", Locale.US); return mon.format(f) + " – " + mon.plusDays(6).format(f) }
    private fun nav(act: Activity, title: String, label: String, color: Int, shift: (Int) -> Unit): View {
        val d = { v: Int -> RunUi.dp(act, v) }
        return LinearLayout(act).apply { orientation = LinearLayout.VERTICAL
            if (title.isNotBlank()) addView(RunUi.text(act, title, color, 15f, true))
            addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, d(8), 0, d(6))
                addView(RunUi.text(act, "‹", RunUi.ACCENT, 22f, true).apply { setPadding(d(4), 0, d(14), 0); setOnClickListener { shift(-1) } })
                addView(RunUi.text(act, label, Color.WHITE, 14f, true).apply { gravity = Gravity.CENTER; setOnClickListener { shift(0) } }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(RunUi.text(act, "›", RunUi.ACCENT, 22f, true).apply { setPadding(d(14), 0, d(4), 0); setOnClickListener { shift(1) } }) }) }
    }

    /** 🍎 Nutrition + 🧪 Micronutrients, Mon–Sun, ‹ › for other weeks. */
    class NutritionWeek(private val act: Activity) {
        val view = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        private var off = 0
        init { draw() }
        fun draw() {
            view.removeAllViews()
            val today = LocalDate.now(); val mon = today.minusDays((today.dayOfWeek.value - 1).toLong()).plusWeeks(off.toLong())
            val dates = (0..6).map { mon.plusDays(it.toLong()) }
            val byDate = try { io.github.michealsizemore1.beyondstride.NutritionStore.days(act).associateBy { it.date } } catch (e: Exception) { emptyMap() }
            view.addView(nav(act, "", weekLabel(mon), RunUi.ACCENT) { s -> off = if (s == 0) 0 else minOf(0, off + s); draw() })
            fun lab(dt: LocalDate) = dt.format(DateTimeFormatter.ofPattern("EEE, M/d", Locale.US))
            fun qCell(q: Double, label: String) = if (q <= 0) dash() else { val c = if (q >= 7) GREEN else if (q >= 5) AMBER else RED; cell(Line(String.format(Locale.US, "%.1f", q), c, true), Line(label, c, true)) }
            // Nutrition: quality, calories vs target, protein / fat / carbs vs the macro ratio, fluids.
            val rows = ArrayList<Pair<String, List<List<Line>>>>()
            var nD = 0; var cal = 0.0; var tgt = 0.0; var pr = 0.0; var ft = 0.0; var cb = 0.0; var wat = 0.0; var wt = 0.0; var qs = 0.0; var qn = 0
            dates.forEach { dt ->
                val nd = byDate[dt.toString()]?.takeIf { it.count > 0 }
                if (nd == null || dt.isAfter(today)) { rows += lab(dt) to List(6) { dash() }; return@forEach }
                nD++; cal += nd.cal; tgt += nd.target; pr += nd.prot; ft += nd.fat; cb += nd.carbs; wat += nd.waterOz
                val fT = try { Insights.drinkTarget(act, dt.toString()).total } catch (e: Exception) { 0 }; wt += fT
                val dq = Food.dayQuality(nd); if (dq != null) { qs += dq.score; qn++ }
                val tiles = nd.macroTiles()?.associateBy { it.name } ?: emptyMap()
                fun macro(g: Double, name: String): List<Line> { val m = tiles[name] ?: return cell(Line(n0(g)))
                    val txt = String.format(Locale.US, "%.1f%% / %d%% · %s", m.actual, m.goal, m.status)
                    return if (m.status == "High") cell(Line(n0(g)), Line(txt, pill = true)) else cell(Line(n0(g)), Line(txt, if (m.status == "On target") GREEN else AMBER, true)) }
                val calC = if (nd.target > 0) { val r = nd.cal.toDouble() / nd.target; val c = if (r in 0.9..1.1) GREEN else if (r in 0.75..1.25) AMBER else RED
                    cell(Line(n0(nd.cal.toDouble()) + "  / " + n0(nd.target.toDouble()), c, true), Line("${Math.round(r * 100)}% of target", c, true)) } else cell(Line(n0(nd.cal.toDouble())))
                val fl = if (fT > 0) { val p = nd.waterOz * 100 / fT; val c = if (p >= 100) GREEN else if (p >= 75) AMBER else RED; cell(Line("${nd.waterOz}"), Line("$p% of $fT oz", c, true)) } else cell(Line("${nd.waterOz}"))
                rows += lab(dt) to listOf(qCell(dq?.score ?: 0.0, dq?.label ?: ""), calC, macro(nd.prot, "Protein"), macro(nd.fat, "Fat"), macro(nd.carbs, "Carbs"), fl)
            }
            if (nD > 0) {
                val aq = if (qn > 0) qs / qn else 0.0
                val ac = cal / nD; val at = tgt / nD
                val calC = if (at > 0) { val r = ac / at; val c = if (r in 0.9..1.1) GREEN else if (r in 0.75..1.25) AMBER else RED; cell(Line(n0(ac) + "  / " + n0(at), c, true), Line("${Math.round(r * 100)}% of target", c, true)) } else cell(Line(n0(ac)))
                val aw = wat / nD; val awt = wt / nD
                val fl = if (awt > 0) { val p = (aw * 100 / awt).toInt(); val c = if (p >= 100) GREEN else if (p >= 75) AMBER else RED; cell(Line(n0(aw)), Line("$p% of ${n0(awt)} oz", c, true)) } else cell(Line(n0(aw)))
                rows += "DAILY AVG" to listOf(qCell(aq, Food.label(aq).takeIf { aq > 0 } ?: ""), calC, cell(Line(n0(pr / nD))), cell(Line(n0(ft / nD))), cell(Line(n0(cb / nD))), fl)
            } else rows += "DAILY AVG" to List(6) { dash() }
            view.addView(RunUi.text(act, "🍎 Nutrition", RunUi.ACCENT, 13f, true).apply { setPadding(0, RunUi.dp(act, 6), 0, RunUi.dp(act, 4)) })
            view.addView(table(act, listOf("Period" to null, "Food Quality" to 0xFF4ADE80.toInt(), "Calories" to 0xFFF39C12.toInt(), "Protein (g)" to 0xFFF87171.toInt(), "Fat (g)" to 0xFF60A5FA.toInt(), "Carbs (g)" to 0xFFFBBF24.toInt(), "Fluids (oz)" to 0xFF38BDF8.toInt()), rows))
            if (nD == 0) view.addView(RunUi.text(act, "No food logged this week.", RunUi.DIM, 11f))
            // Micronutrients: micro quality, then each nutrient with % of target and Low / Close / Met / High.
            val fields = LinkedHashMap<String, String>()
            dates.forEach { dt -> byDate[dt.toString()]?.micros?.forEach { m -> if (m.field !in fields) fields[m.field] = "${m.name} (${m.unit})" } }
            if (fields.isEmpty()) return
            val mrows = ArrayList<Pair<String, List<List<Line>>>>()
            val sums = HashMap<String, Double>(); val lastM = HashMap<String, Nutrition.Micro>(); var ms = 0.0; var mn = 0; var md = 0
            fun mCell(v: Double, m: Nutrition.Micro?): List<Line> {
                if (v <= 0) return dash()
                val shown = if (v < 10) String.format(Locale.US, "%.1f", v) else n0(v)
                if (m == null || m.target <= 0) return cell(Line(shown))
                val pct = Math.floor(v / m.target * 100).toInt()
                val st = if (m.max > 0 && v > m.max) "High" else if (pct >= 100) "Met" else if (pct >= 75) "Close" else "Low"
                return if (st == "High") cell(Line(shown), Line("$pct% · High", pill = true)) else cell(Line(shown), Line("$pct% · $st", if (st == "Met") GREEN else if (st == "Close") AMBER else RED, true))
            }
            dates.forEach { dt ->
                val nd = byDate[dt.toString()]?.takeIf { it.count > 0 && !dt.isAfter(today) }
                if (nd == null) { mrows += lab(dt) to List(fields.size + 1) { dash() }; return@forEach }
                md++; if (nd.microScore > 0) { ms += nd.microScore; mn++ }
                val byF = nd.micros.associateBy { it.field }
                byF.forEach { (f, m) -> sums[f] = (sums[f] ?: 0.0) + m.value; lastM[f] = m }
                mrows += lab(dt) to (listOf(qCell(nd.microScore, nd.microLabel())) + fields.keys.map { f -> byF[f]?.let { mCell(it.value, it) } ?: dash() })
            }
            val am = if (mn > 0) ms / mn else 0.0
            mrows += "DAILY AVG" to (listOf(qCell(am, Food.label(am).takeIf { am > 0 } ?: "")) + fields.keys.map { f -> if (md > 0) mCell((sums[f] ?: 0.0) / md, lastM[f]) else dash() })
            view.addView(RunUi.text(act, "🧪 Micronutrients", RunUi.ACCENT, 13f, true).apply { setPadding(0, RunUi.dp(act, 12), 0, RunUi.dp(act, 4)) })
            view.addView(table(act, listOf("Period" to null, "Micro Quality" to 0xFF4ADE80.toInt()) + fields.values.map { it to null }, mrows))
            view.addView(RunUi.text(act, "Swipe a table sideways for more columns.", RunUi.DIM, 10f).apply { setPadding(0, RunUi.dp(act, 6), 0, 0) })
        }
    }

    /** 🏃 Miles, pace, power and time — Week (by day) · Month (by week) · Year (by month), ‹ › to move. */
    class MileageBreakdown(private val act: Activity, private val all: List<JSONObject>, private val exMiles: Map<String, Double>, private val mode: String) {
        val view = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        private var off = 0
        init { draw() }
        private class B { var mi = 0.0; var pace = 0; var paceN = 0; var w = 0.0; var wN = 0; var sec = 0.0 }
        private fun fmtT(s: Double): String { val r = Math.round(s).toInt(); val h = r / 3600; val m = (r % 3600) / 60; val ss = r % 60
            return if (h > 0) "${h}h ${m}m ${ss}s" else if (m > 0) "${m}m ${ss}s" else "${ss}s" }
        fun draw() {
            view.removeAllViews()
            val d = { v: Int -> RunUi.dp(act, v) }
            val S = ActivityStore
            val today = LocalDate.now()
            // Periods for this view.
            val periods: List<Triple<String, LocalDate, LocalDate>>; val label: String
            when (mode) {
                "month" -> { val m1 = today.withDayOfMonth(1).plusMonths(off.toLong()); val last = m1.plusMonths(1).minusDays(1)
                    var w = m1.minusDays((m1.dayOfWeek.value - 1).toLong()); val ps = ArrayList<Triple<String, LocalDate, LocalDate>>()
                    while (!w.isAfter(last)) { val e = w.plusDays(6); val f = DateTimeFormatter.ofPattern("MMM d", Locale.US)
                        ps += Triple("Wk " + w.format(f) + "–" + (if (e.month == w.month) e.dayOfMonth.toString() else e.format(f)), w, e); w = w.plusWeeks(1) }
                    periods = ps; label = m1.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)) }
                "year" -> { val y = today.year + off
                    periods = (1..12).map { mo -> val s = LocalDate.of(y, mo, 1); Triple(s.format(DateTimeFormatter.ofPattern("MMM", Locale.US)), s, s.plusMonths(1).minusDays(1)) }; label = "$y" }
                else -> { val mon = today.minusDays((today.dayOfWeek.value - 1).toLong()).plusWeeks(off.toLong())
                    periods = (0..6).map { i -> val dt = mon.plusDays(i.toLong()); Triple(dt.format(DateTimeFormatter.ofPattern("EEE, M/d", Locale.US)), dt, dt) }; label = weekLabel(mon) }
            }
            val bs = periods.map { B() }
            all.forEach { a -> val dt = try { LocalDate.parse(S.str(a, "date").take(10)) } catch (e: Exception) { return@forEach }
                periods.forEachIndexed { i, (_, s, e) -> if (!dt.isBefore(s) && !dt.isAfter(e)) { val x = bs[i]
                    x.mi += S.num(a, "distance"); S.paceSec(S.str(a, "pace")).takeIf { it > 0 }?.let { x.pace += it; x.paceN++ }
                    S.num(a, "power").takeIf { it > 0 }?.let { x.w += it; x.wN++ }; x.sec += S.durSec(S.str(a, "duration")) } } }
            val covered = all.filter { S.num(it, "distance") > 0 }.map { S.str(it, "date").take(10) }.toSet()
            exMiles.forEach { (ds, m) -> if (ds !in covered) { val dt = try { LocalDate.parse(ds) } catch (e: Exception) { return@forEach }
                periods.forEachIndexed { i, (_, s, e) -> if (!dt.isBefore(s) && !dt.isAfter(e)) bs[i].mi += m } } }
            view.addView(nav(act, "", label, Color.WHITE) { s -> off = if (s == 0) 0 else minOf(0, off + s); draw() })
            fun p(x: B) = if (x.paceN > 0) (x.pace / x.paceN).let { "${it / 60}:${String.format(Locale.US, "%02d", it % 60)}" } else "-"
            val rows = periods.mapIndexed { i, (l, _, _) -> val x = bs[i]
                l to listOf(cell(Line(if (x.mi > 0) String.format(Locale.US, "%.1f", x.mi) else "-")), cell(Line(p(x))), cell(Line(if (x.wN > 0) "${Math.round(x.w / x.wN)}" else "-")), cell(Line(if (x.sec > 0) fmtT(x.sec) else "-"))) }
            val t = B(); bs.forEach { x -> t.mi += x.mi; t.pace += x.pace; t.paceN += x.paceN; t.w += x.w; t.wN += x.wN; t.sec += x.sec }
            view.addView(table(act, listOf("Period" to null, "Miles" to null, "Pace" to null, "Watts" to null, "Time" to null),
                rows + ("TOTAL/AVG" to listOf(cell(Line(String.format(Locale.US, "%.1f", t.mi))), cell(Line(p(t))), cell(Line(if (t.wN > 0) "${Math.round(t.w / t.wN)}" else "-")), cell(Line(if (t.sec > 0) fmtT(t.sec) else "-"))))))
        }
    }

    /** 📅 Weekly · 📆 Monthly · 🗓️ Yearly, each a card you open or close. */
    fun mileageSections(act: Activity, all: List<JSONObject>, ex: Map<String, Double>): List<View> {
        val dr = listOf("week", "month", "year").associateWith { MileageBreakdown(act, all, ex, it) }
        fun add(k: String) = { b: LinearLayout -> val v = dr.getValue(k).view; if (v.parent == null) b.addView(v) }
        return listOf(LifeUi.FoldGroup(act, listOf(
            LifeUi.Fold("act_week", "📅 Weekly — by day", true, add("week")),
            LifeUi.Fold("act_month", "📆 Monthly — by week", false, add("month")),
            LifeUi.Fold("act_year", "🗓️ Yearly — by month", false, add("year")))).view)
    }

    fun nutritionSections(act: Activity, onHistory: () -> Unit = {}): List<View> {
        val wk = NutritionWeek(act)
        val month = NutritionPeriod(act, "month"); val year = NutritionPeriod(act, "year")
        // Older days come from the cloud copy of the day summaries; refresh now and then.
        Thread { if (NutritionHistory.refresh(act.applicationContext)) act.runOnUiThread { month.draw(); year.draw(); onHistory() } }.start()
        fun add(v: View) = { b: LinearLayout -> if (v.parent == null) b.addView(v) }
        return listOf(LifeUi.FoldGroup(act, listOf(
            LifeUi.Fold("nu_week", "📅 Weekly — by day", true, add(wk.view)),
            LifeUi.Fold("nu_month", "📆 Monthly — by week", false, add(month.view)),
            LifeUi.Fold("nu_year", "🗓️ Yearly — by month", false, add(year.view)))).view)
    }

    /** 🍎 Nutrition and 🧪 Micronutrients averaged per week (Monthly) or per month (Yearly), from the saved day summaries. */
    class NutritionPeriod(private val act: Activity, private val kind: String) {
        val view = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        private var off = 0
        init { draw() }
        fun draw() {
            view.removeAllViews()
            val today = LocalDate.now()
            val periods: List<Triple<String, LocalDate, LocalDate>>; val label: String
            if (kind == "month") { val m1 = today.withDayOfMonth(1).plusMonths(off.toLong()); val last = m1.plusMonths(1).minusDays(1)
                var w = m1.minusDays((m1.dayOfWeek.value - 1).toLong()); val ps = ArrayList<Triple<String, LocalDate, LocalDate>>(); val f = DateTimeFormatter.ofPattern("MMM d", Locale.US)
                while (!w.isAfter(last)) { val e = w.plusDays(6); ps += Triple("Wk " + w.format(f) + "–" + (if (e.month == w.month) e.dayOfMonth.toString() else e.format(f)), maxOf(w, m1), minOf(e, last)); w = w.plusWeeks(1) }
                periods = ps; label = m1.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US))
            } else { val y = today.year + off
                periods = (1..12).map { mo -> val st = LocalDate.of(y, mo, 1); Triple(st.format(DateTimeFormatter.ofPattern("MMM", Locale.US)), st, st.plusMonths(1).minusDays(1)) }; label = "$y" }
            view.addView(nav(act, "", label, RunUi.ACCENT) { s -> off = if (s == 0) 0 else minOf(0, off + s); draw() })
            val hist = NutritionHistory.days(act)
            fun recs(s: LocalDate, e: LocalDate) = generateSequence(s) { it.plusDays(1) }.takeWhile { !it.isAfter(e) && !it.isAfter(today) }
                .mapNotNull { hist.optJSONObject(it.toString())?.takeIf { n -> n.optInt("cal") > 0 } }.toList()
            fun avg(l: List<JSONObject>, f: (JSONObject) -> Double) = l.map(f).filter { it > 0 }.let { if (it.isEmpty()) 0.0 else it.average() }
            fun qCell(q: Double) = if (q <= 0) dash() else { val c = if (q >= 7) GREEN else if (q >= 5) AMBER else RED; cell(Line(String.format(Locale.US, "%.1f", q), c, true), Line(Food.label(q), c, true)) }
            fun calCell(c: Double, t: Double) = if (c <= 0) dash() else if (t <= 0) cell(Line(n0(c))) else { val r = c / t; val col = if (r in 0.9..1.1) GREEN else if (r in 0.75..1.25) AMBER else RED
                cell(Line(n0(c) + "  / " + n0(t), col, true), Line("${Math.round(r * 100)}% of target", col, true)) }
            fun macroCell(l: List<JSONObject>, g: Double, name: String): List<Line> {
                if (g <= 0) return dash()
                val ms = l.mapNotNull { it.optJSONObject("macros")?.optJSONObject(name) }; if (ms.isEmpty()) return cell(Line(n0(g)))
                val pct = ms.map { it.optDouble("pct") }.average(); val goal = ms.last().optInt("goal"); val diff = pct - goal
                val st = if (diff < -3) "Low" else if (diff > 3) "High" else "On target"; val txt = String.format(Locale.US, "%.1f%% / %d%% · %s", pct, goal, st)
                return if (st == "High") cell(Line(n0(g)), Line(txt, pill = true)) else cell(Line(n0(g)), Line(txt, if (st == "On target") GREEN else AMBER, true)) }
            fun fluidCell(w: Double, t: Double) = if (w <= 0) dash() else if (t <= 0) cell(Line(n0(w))) else { val p = (w * 100 / t).toInt(); val c = if (p >= 100) GREEN else if (p >= 75) AMBER else RED; cell(Line(n0(w)), Line("$p% of ${n0(t)} oz", c, true)) }
            val all = ArrayList<JSONObject>()
            val rows = periods.map { (lab, s, e) -> val l = recs(s, e); all += l
                lab to if (l.isEmpty()) List(6) { dash() } else listOf(qCell(avg(l) { it.optDouble("quality") }), calCell(avg(l) { it.optDouble("cal") }, avg(l) { it.optDouble("target") }),
                    macroCell(l, avg(l) { it.optDouble("prot") }, "protein"), macroCell(l, avg(l) { it.optDouble("fat") }, "fat"), macroCell(l, avg(l) { it.optDouble("carbs") }, "carbs"),
                    fluidCell(avg(l) { it.optDouble("water") }, avg(l) { it.optDouble("fluidTarget") })) }
            val tot = "DAILY AVG" to if (all.isEmpty()) List(6) { dash() } else listOf(qCell(avg(all) { it.optDouble("quality") }), calCell(avg(all) { it.optDouble("cal") }, avg(all) { it.optDouble("target") }),
                cell(Line(n0(avg(all) { it.optDouble("prot") }))), cell(Line(n0(avg(all) { it.optDouble("fat") }))), cell(Line(n0(avg(all) { it.optDouble("carbs") }))), fluidCell(avg(all) { it.optDouble("water") }, avg(all) { it.optDouble("fluidTarget") }))
            view.addView(RunUi.text(act, "🍎 Nutrition — daily average", RunUi.ACCENT, 13f, true).apply { setPadding(0, RunUi.dp(act, 6), 0, RunUi.dp(act, 4)) })
            view.addView(table(act, listOf("Period" to null, "Food Quality" to 0xFF4ADE80.toInt(), "Calories" to 0xFFF39C12.toInt(), "Protein (g)" to 0xFFF87171.toInt(), "Fat (g)" to 0xFF60A5FA.toInt(), "Carbs (g)" to 0xFFFBBF24.toInt(), "Fluids (oz)" to 0xFF38BDF8.toInt()), rows + tot))
            if (all.isEmpty()) { view.addView(RunUi.text(act, if (hist.length() == 0) "Getting your earlier days… (needs the cloud copy turned on)" else "No food logged in this period.", RunUi.DIM, 11f)); return }
            // Micronutrients: average per day in each period, with % of target and status (targets from the latest day).
            val fields = LinkedHashMap<String, JSONObject>()
            all.forEach { n -> n.optJSONObject("microTargets")?.let { mt -> mt.keys().forEach { k -> fields[k] = mt.optJSONObject(k) ?: JSONObject() } } }
            if (fields.isEmpty()) return
            fun mCell(v: Double, t: JSONObject?): List<Line> {
                if (v <= 0) return dash()
                val shown = if (v < 10) String.format(Locale.US, "%.1f", v) else n0(v)
                val tg = t?.optDouble("t", 0.0) ?: 0.0; if (tg <= 0) return cell(Line(shown))
                val mx = t?.optDouble("mx", 0.0) ?: 0.0; val pct = Math.floor(v / tg * 100).toInt()
                val st = if (mx > 0 && v > mx) "High" else if (pct >= 100) "Met" else if (pct >= 75) "Close" else "Low"
                return if (st == "High") cell(Line(shown), Line("$pct% · High", pill = true)) else cell(Line(shown), Line("$pct% · $st", if (st == "Met") GREEN else if (st == "Close") AMBER else RED, true)) }
            fun mAvg(l: List<JSONObject>, f: String) = l.map { it.optJSONObject("micros")?.optDouble(f, 0.0) ?: 0.0 }.let { if (l.isEmpty()) 0.0 else it.sum() / l.size }
            val mrows = periods.map { (lab, s, e) -> val l = recs(s, e)
                lab to if (l.isEmpty()) List(fields.size + 1) { dash() } else (listOf(qCell(avg(l) { it.optDouble("microScore") })) + fields.map { (f, t) -> mCell(mAvg(l, f), t) }) }
            val mtot = "DAILY AVG" to (listOf(qCell(avg(all) { it.optDouble("microScore") })) + fields.map { (f, t) -> mCell(mAvg(all, f), t) })
            view.addView(RunUi.text(act, "🧪 Micronutrients — daily average", RunUi.ACCENT, 13f, true).apply { setPadding(0, RunUi.dp(act, 12), 0, RunUi.dp(act, 4)) })
            view.addView(table(act, listOf("Period" to null, "Micro Quality" to 0xFF4ADE80.toInt()) + fields.map { (f, t) -> "${t.optString("n").ifBlank { f }} (${t.optString("u")})" to null }, mrows + mtot))
            view.addView(RunUi.text(act, "Averages count only the days you logged food. Swipe a table sideways for more columns.", RunUi.DIM, 10f).apply { setPadding(0, RunUi.dp(act, 6), 0, 0) })
        }
    }
}
