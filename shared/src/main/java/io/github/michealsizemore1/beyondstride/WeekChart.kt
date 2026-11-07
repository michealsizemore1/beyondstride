package io.github.michealsizemore1.beyondstride

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/**
 * A Monday–Sunday line chart like the Command Center's "— This Week" charts: one point per day
 * (days with nothing logged are skipped and the line joins across them), soft fill, light grid.
 */
class WeekChart(ctx: Context, private val values: List<Double?>, @Suppress("UNUSED_PARAMETER") requested: Int = LIME, private val small: Boolean = false, private val showValues: Boolean = false, private val label: String = "", private val unit: String = "") : View(ctx) {
    /** Tapped day (shows its number, like the Command Center's tooltip); -1 = none. */
    private var picked = -1
    // Every chart uses BeyondStride's lime so the line stands out on the dark cards.
    private val lineColor = LIME
    private val d = ctx.resources.displayMetrics.density
    private val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFF9AA5A1.toInt(); textSize = (if (small) 8f else 11f) * d }
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0x2E94A3B8; strokeWidth = d }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lineColor; strokeWidth = (if (small) 1.5f else 2.5f) * d; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(40, Color.red(lineColor), Color.green(lineColor), Color.blue(lineColor)); style = Paint.Style.FILL }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lineColor; strokeWidth = 2f * d; style = Paint.Style.STROKE }

    private fun nice(v: Double): Double {
        if (v <= 0) return 1.0
        val e = Math.pow(10.0, Math.floor(Math.log10(v)))
        return listOf(1.0, 2.0, 2.5, 5.0, 10.0).first { it * e >= v } * e
    }

    override fun onDraw(c: Canvas) {
        val logged = values.filterNotNull().filter { it > 0 }
        val left = (if (small) 26f else 42f) * d; val right = width - 8f * d
        val top = 8f * d; val bottom = height - (if (small) 14f else 22f) * d
        // Scale: a little room above and below the data (like the Command Center's 12% grace).
        val hi0 = logged.maxOrNull() ?: 1.0; val lo0 = logged.minOrNull() ?: 0.0
        val pad = maxOf((hi0 - lo0) * 0.12, hi0 * 0.05, 1.0)
        val step = nice((hi0 - lo0 + 2 * pad) / 4)
        val lo = maxOf(0.0, Math.floor((lo0 - pad) / step) * step); val hi = lo + step * 4
        fun y(v: Double) = (bottom - (v - lo) / (hi - lo) * (bottom - top)).toFloat()
        fun x(i: Int) = left + (right - left) * i / 6f
        for (k in 0..4) {
            val v = lo + step * k; val yy = y(v)
            c.drawLine(left, yy, right, yy, grid)
            val s = if (v >= 1000) String.format(java.util.Locale.US, "%.0f", v) else if (step < 1) String.format(java.util.Locale.US, "%.1f", v) else v.toInt().toString()
            c.drawText(s, left - text.measureText(s) - 4 * d, yy + text.textSize / 3, text)
        }
        days.forEachIndexed { i, n -> c.drawText(n, x(i) - text.measureText(n) / 2, height - 3 * d, text) }
        val pts = values.mapIndexedNotNull { i, v -> if (v != null && v > 0) x(i) to y(v) else null }
        if (pts.isEmpty()) return
        val path = Path().apply { pts.forEachIndexed { i, (px, py) -> if (i == 0) moveTo(px, py) else lineTo(px, py) } }
        val area = Path(path).apply { lineTo(pts.last().first, bottom); lineTo(pts.first().first, bottom); close() }
        c.drawPath(area, fill); c.drawPath(path, line)
        pts.forEach { (px, py) -> c.drawCircle(px, py, (if (small) 2.5f else 5f) * d, dot) }
        // Tapped day: a small box with the day and its value.
        if (picked in values.indices) values[picked]?.takeIf { it > 0 }?.let { v ->
            val px = x(picked); val py = y(v)
            val tp = Paint(text).apply { color = Color.WHITE; textSize = (if (small) 9f else 13f) * d; isFakeBoldText = true }
            val l1 = days[picked]; val l2 = (if (label.isNotEmpty()) "$label: " else "") + Math.round(v) + unit
            val w = maxOf(tp.measureText(l1), tp.measureText(l2)) + 16 * d; val h = tp.textSize * 2 + 16 * d
            var bx = px - w / 2; bx = bx.coerceIn(0f, width - w)
            val by = if (py - h - 10 * d > 0) py - h - 10 * d else py + 10 * d
            c.drawRoundRect(bx, by, bx + w, by + h, 8 * d, 8 * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xEE111111.toInt() })
            c.drawText(l1, bx + 8 * d, by + 6 * d + tp.textSize, tp)
            c.drawText(l2, bx + 8 * d, by + 10 * d + tp.textSize * 2, tp)
            c.drawCircle(px, py, (if (small) 4f else 7f) * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lineColor })
        }
        // Enlarged view: each day's number above its point.
        if (showValues) {
            val vp = Paint(text).apply { color = Color.WHITE; isFakeBoldText = true }
            values.forEachIndexed { i, v ->
                if (v == null || v <= 0) return@forEachIndexed
                val s = if (v >= 100) Math.round(v).toString() else String.format(java.util.Locale.US, "%.0f", v)
                c.drawText(s, (x(i) - vp.measureText(s) / 2).coerceIn(0f, width - vp.measureText(s)), maxOf(vp.textSize, y(v) - 7 * d), vp)
            }
        }
    }

    override fun onTouchEvent(e: android.view.MotionEvent): Boolean {
        if (small) return super.onTouchEvent(e)   // watch thumbnail: the card's tap opens the big chart
        if (e.action == android.view.MotionEvent.ACTION_DOWN) return true
        if (e.action == android.view.MotionEvent.ACTION_UP) {
            val left = (if (small) 26f else 42f) * d; val right = width - 8f * d
            val i = Math.round((e.x - left) / ((right - left) / 6f)).coerceIn(0, 6)
            picked = if (picked == i || values.getOrNull(i)?.let { it > 0 } != true) -1 else i
            invalidate(); performClick(); return true
        }
        return super.onTouchEvent(e)
    }
    override fun performClick(): Boolean { super.performClick(); return true }

    companion object {
        const val LIME = 0xFFD4E635.toInt()
        /** This week's dates, Monday first. */
        fun weekDates(): List<String> {
            val t = java.time.LocalDate.now()
            val mon = t.minusDays((t.dayOfWeek.value - 1).toLong())
            return (0..6).map { mon.plusDays(it.toLong()).toString() }
        }
        /** "2295 weekly average · 1646 to 2984 range" (or "No data logged this week"). */
        fun summary(values: List<Double?>, unit: String = ""): String {
            val l = values.filterNotNull().filter { it > 0 }
            if (l.isEmpty()) return "No data logged this week"
            fun f(v: Double) = Math.round(v).toString() + unit
            return "${f(l.average())} weekly average · ${f(l.min())} to ${f(l.max())} range"
        }
    }
}
