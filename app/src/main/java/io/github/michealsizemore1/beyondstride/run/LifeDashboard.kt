package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.WebView
import android.widget.LinearLayout
import org.json.JSONArray
import org.json.JSONTokener
import java.util.Locale

/**
 * 📈 Dashboard card for the Life tab's Reviews page — the Command Center's numbers (weight, blood pressure, pulse /
 * morning HR, sleep, sleep score, miles, steps) as simple charts over 7, 30 or 90 days.
 */
class LifeDashboard(private val act: Activity, private val web: WebView) {
    val view = RunUi.card(act)
    private var days = 30
    private val body = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
    private val ranges = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
    private fun d(v: Int) = RunUi.dp(act, v)

    init {
        view.addView(RunUi.text(act, "📈 Dashboard", Color.WHITE, 16f, true))
        view.addView(ranges.apply { setPadding(0, d(6), 0, 0) })
        view.addView(body)
        drawRanges()
    }
    private fun drawRanges() {
        ranges.removeAllViews()
        listOf(7 to "7 days", 30 to "30 days", 90 to "90 days").forEach { (n, l) ->
            ranges.addView(RunUi.text(act, l, if (n == days) Color.BLACK else Color.WHITE, 12f, true).apply {
                setPadding(d(12), d(5), d(12), d(5)); background = RunUi.rounded(if (n == days) RunUi.ACCENT else 0xFF2A3430.toInt(), d(14).toFloat())
                setOnClickListener { days = n; drawRanges(); load() }
            }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = d(6) })
        }
    }

    fun load() {
        web.evaluateJavascript("window.ccNative&&ccNative.dash?ccNative.dash($days):null") { r ->
            val arr = try { JSONArray(JSONTokener(r).nextValue() as String) } catch (e: Exception) { null } ?: run { view.visibility = View.GONE; return@evaluateJavascript }
            view.visibility = View.VISIBLE; fill(arr)
        }
    }

    private fun series(arr: JSONArray, key: String) = (0 until arr.length()).map { arr.getJSONObject(it).optDouble(key, 0.0).takeIf { v -> v > 0 } }
    private fun fill(arr: JSONArray) {
        body.removeAllViews()
        val labels = (0 until arr.length()).map { arr.getJSONObject(it).optString("d") }
        fun chart(title: String, unit: String, lines: List<Pair<List<Double?>, Int>>, bars: Boolean = false, fmt: (Double) -> String = { String.format(Locale.US, "%.1f", it) }) {
            val all = lines.flatMap { it.first }.filterNotNull()
            body.addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, d(14), 0, d(4))
                addView(RunUi.text(act, title, RunUi.ACCENT, 13f, true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(RunUi.text(act, if (all.isEmpty()) "no data" else {
                    val last = lines.first().first.lastOrNull { it != null }
                    val avg = lines.first().first.filterNotNull().let { if (it.isEmpty()) 0.0 else it.average() }
                    if (bars) "total ${fmt(lines.first().first.filterNotNull().sum())}$unit · avg ${fmt(avg)}" else "latest ${last?.let(fmt) ?: "–"}$unit · avg ${fmt(avg)}"
                }, RunUi.MUTED, 11f)) })
            if (all.isNotEmpty()) body.addView(Chart(act, lines, bars, labels), LinearLayout.LayoutParams(MATCH_PARENT, d(120)))
        }
        chart("⚖️ Weight", " lb", listOf(series(arr, "weight") to 0xFFEF4444.toInt()))
        chart("🩺 Blood pressure", "", listOf(series(arr, "sys") to 0xFFF97316.toInt(), series(arr, "dia") to 0xFF60A5FA.toInt())) { it.toInt().toString() }
        chart("💓 Morning HR", " bpm", listOf(series(arr, "restHR") to 0xFFF472B6.toInt())) { it.toInt().toString() }
        chart("😴 Sleep", " h", listOf(series(arr, "sleep") to 0xFFA78BFA.toInt()))
        chart("⌛ Sleep score", "", listOf(series(arr, "sleepScore") to 0xFF818CF8.toInt())) { it.toInt().toString() }
        chart("🏃 Miles", " mi", listOf(series(arr, "miles") to 0xFF2DD4BF.toInt()), bars = true)
        chart("👟 Steps", "", listOf(series(arr, "steps") to 0xFFD4E635.toInt()), bars = true) { String.format(Locale.US, "%,d", it.toLong()) }
        body.addView(RunUi.text(act, "Blood pressure: orange systolic, blue diastolic. The BP report and habit streak chart are in 📓 Full → Dashboard.", RunUi.DIM, 10f).apply { setPadding(0, d(8), 0, 0) })
    }

    /** A small line (or bar) chart: min / max on the left, first and last date along the bottom. */
    /** Also used by the Wellness section's 60-day charts. */
    class Chart(ctx: Context, val lines: List<Pair<List<Double?>, Int>>, val bars: Boolean, val labels: List<String>) : View(ctx) {
        private val dens = ctx.resources.displayMetrics.density
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: Canvas) {
            val vals = lines.flatMap { it.first }.filterNotNull(); if (vals.isEmpty()) return
            var lo = if (bars) 0.0 else vals.min(); var hi = vals.max(); if (hi - lo < 1e-6) { hi += 1; if (!bars) lo -= 1 }
            val padL = 34 * dens; val padB = 16 * dens; val w = width - padL; val h = height - padB - 4 * dens
            val n = lines.first().first.size
            fun x(i: Int) = padL + if (n <= 1) w / 2 else w * i / (n - 1)
            fun y(v: Double) = (4 * dens + h - (v - lo) / (hi - lo) * h).toFloat()
            p.color = 0xFF2A3430.toInt(); p.strokeWidth = dens
            c.drawLine(padL, y(lo), width.toFloat(), y(lo), p); c.drawLine(padL, y(hi), width.toFloat(), y(hi), p)
            p.color = 0xFF9AA3A0.toInt(); p.textSize = 10 * dens; p.style = Paint.Style.FILL
            fun lab(v: Double) = if (hi >= 1000) String.format(Locale.US, "%.0fk", v / 1000) else if (hi - lo < 10) String.format(Locale.US, "%.1f", v) else v.toInt().toString()
            c.drawText(lab(hi), 0f, y(hi) + 4 * dens, p); c.drawText(lab(lo), 0f, y(lo), p)
            fun md(s: String) = s.takeLast(5).replace('-', '/')
            if (labels.isNotEmpty()) { c.drawText(md(labels.first()), padL, height - 2 * dens, p)
                val t = md(labels.last()); c.drawText(t, width - p.measureText(t), height - 2 * dens, p) }
            lines.forEach { (s, col) ->
                p.color = col
                if (bars) {
                    val bw = (w / n.coerceAtLeast(1)) * 0.7f
                    s.forEachIndexed { i, v -> if (v != null) c.drawRect(x(i) - bw / 2, y(v), x(i) + bw / 2, y(lo), p) }
                } else {
                    p.style = Paint.Style.STROKE; p.strokeWidth = 2.2f * dens
                    val path = Path(); var started = false
                    s.forEachIndexed { i, v -> if (v != null) { if (!started) { path.moveTo(x(i), y(v)); started = true } else path.lineTo(x(i), y(v)) } }
                    c.drawPath(path, p); p.style = Paint.Style.FILL
                    s.forEachIndexed { i, v -> if (v != null && n <= 31) c.drawCircle(x(i), y(v), 2.5f * dens, p) }
                }
            }
        }
    }
}
