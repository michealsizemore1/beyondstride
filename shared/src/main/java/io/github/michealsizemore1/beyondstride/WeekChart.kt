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
class WeekChart(ctx: Context, private val values: List<Double?>, private val color: Int, private val small: Boolean = false) : View(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFF9AA5A1.toInt(); textSize = (if (small) 8f else 11f) * d }
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0x2E94A3B8; strokeWidth = d }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = (if (small) 1.5f else 2.5f) * d; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.argb(40, Color.red(color), Color.green(color), Color.blue(color)); style = Paint.Style.FILL }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = 2f * d; style = Paint.Style.STROKE }

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
    }

    companion object {
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
