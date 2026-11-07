package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/**
 * A small bar or line chart for the Activities analysis (26-week volume, cardiovascular drift, heat acclimation,
 * humidity impact, daily miles). Tap a bar / point to see its value.
 */
class ActChart(ctx: Context) : View(ctx) {
    var line = false
    var values: List<Double> = emptyList()
    var labels: List<String> = emptyList()
    var colors: List<Int> = emptyList()
    var color = 0xFFE67E22.toInt()
    var yMin: Double? = null
    var yMax: Double? = null
    var yStep: Double? = null
    var yFormat: (Double) -> String = { if (it == Math.floor(it)) it.toLong().toString() else String.format(java.util.Locale.US, "%.1f", it) }
    /** Coloured background bands (lo, hi, colour) in value units. */
    var bands: List<Triple<Double, Double, Int>> = emptyList()
    /** Value printed above each bar (null = none). */
    var barText: ((Int) -> String?)? = null
    /** Text shown when a bar / point is tapped. */
    var tip: (Int) -> String = { labels.getOrNull(it).orEmpty() + ": " + yFormat(values[it]) }
    /** Which x labels to draw. */
    var showLabel: (Int) -> Boolean = { true }
    var fill = false
    var xTitle = ""; var yTitle = ""
    private var sel = -1

    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9 * d; color = RunUi.MUTED }

    private fun area() = RectF(if (line || yTitle.isNotEmpty() || yMin != null) 34 * d else 6 * d, 18 * d, width - 6 * d, height - (if (xTitle.isNotEmpty()) 28 else 16) * d)

    override fun onDraw(c: Canvas) {
        if (values.isEmpty()) return
        val a = area()
        val lo = yMin ?: 0.0
        val hi = (yMax ?: (values.maxOrNull() ?: 1.0)).let { if (it <= lo) lo + 1 else it }
        fun y(v: Double) = (a.bottom - (v.coerceIn(lo, hi) - lo) / (hi - lo) * a.height()).toFloat()
        bands.forEach { (bl, bh, col) -> if (bh >= lo && bl <= hi) { paint.color = (col and 0x00FFFFFF) or 0x2A000000; c.drawRect(a.left, y(bh), a.right, y(bl), paint) } }
        // Grid and y labels.
        if (yMin != null || line || yTitle.isNotEmpty()) {
            val step = yStep ?: ((hi - lo) / 4)
            var v = lo; paint.color = 0x22FFFFFF; text.textAlign = Paint.Align.RIGHT
            while (v <= hi + 1e-6) { c.drawLine(a.left, y(v), a.right, y(v), paint); c.drawText(yFormat(v), a.left - 3 * d, y(v) + 3 * d, text); v += step }
        }
        val n = values.size
        val slot = a.width() / n
        text.textAlign = Paint.Align.CENTER
        if (line) {
            val path = Path(); val area = Path()
            values.forEachIndexed { i, v -> val x = a.left + slot * (i + 0.5f); if (i == 0) { path.moveTo(x, y(v)); area.moveTo(x, a.bottom); area.lineTo(x, y(v)) } else { path.lineTo(x, y(v)); area.lineTo(x, y(v)) } }
            if (fill) { area.lineTo(a.left + slot * (n - 0.5f), a.bottom); area.close(); paint.color = (color and 0x00FFFFFF) or 0x33000000; paint.style = Paint.Style.FILL; c.drawPath(area, paint) }
            paint.color = color; paint.style = Paint.Style.STROKE; paint.strokeWidth = 2 * d; c.drawPath(path, paint); paint.style = Paint.Style.FILL
        } else {
            values.forEachIndexed { i, v ->
                val x = a.left + slot * i
                paint.color = colors.getOrNull(i) ?: color
                val top = y(v); if (v > lo) c.drawRoundRect(RectF(x + slot * 0.15f, top, x + slot * 0.85f, a.bottom), 2 * d, 2 * d, paint)
                barText?.invoke(i)?.let { t -> text.color = Color.WHITE; c.drawText(t, x + slot / 2, top - 2 * d, text); text.color = RunUi.MUTED }
            }
        }
        labels.forEachIndexed { i, l -> if (l.isNotEmpty() && showLabel(i)) c.drawText(l, a.left + slot * (i + 0.5f), a.bottom + 11 * d, text) }
        if (xTitle.isNotEmpty()) c.drawText(xTitle, a.centerX(), height - 4 * d, text)
        if (yTitle.isNotEmpty()) { c.save(); c.rotate(-90f, 8 * d, a.centerY()); c.drawText(yTitle, 8 * d, a.centerY(), text); c.restore() }
        if (sel in values.indices) {
            val x = a.left + slot * (sel + 0.5f)
            paint.color = 0x55FFFFFF; c.drawLine(x, a.top, x, a.bottom, paint)
            val t = tip(sel); val tp = Paint(text).apply { color = Color.WHITE; textSize = 11 * d; textAlign = Paint.Align.CENTER }
            val w = tp.measureText(t) + 12 * d; val cx = x.coerceIn(w / 2, width - w / 2)
            paint.color = 0xEE121A17.toInt(); c.drawRoundRect(RectF(cx - w / 2, 1 * d, cx + w / 2, 16 * d), 4 * d, 4 * d, paint)
            c.drawText(t, cx, 12 * d, tp)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (values.isEmpty()) return false
        if (e.action == MotionEvent.ACTION_DOWN || e.action == MotionEvent.ACTION_MOVE) {
            parent?.requestDisallowInterceptTouchEvent(true)
            val a = area(); sel = (((e.x - a.left) / (a.width() / values.size)).toInt()).coerceIn(0, values.size - 1); invalidate(); return true
        }
        return e.action == MotionEvent.ACTION_UP
    }
}
