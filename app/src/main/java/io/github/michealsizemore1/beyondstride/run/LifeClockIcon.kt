package io.github.michealsizemore1.beyondstride.run

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The Life tab's icon: a clock with a leaf and a kettlebell (life balance), drawn in color. */
class LifeClockIcon : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(c: Canvas) {
        val b = bounds; val s = min(b.width(), b.height()).toFloat()
        val ox = b.exactCenterX() - s / 2; val oy = b.exactCenterY() - s / 2
        fun x(v: Float) = ox + v * s
        fun y(v: Float) = oy + v * s
        val cx = x(0.47f); val cy = y(0.46f); val r = s * 0.36f
        // Face and ring
        p.style = Paint.Style.FILL; p.color = 0xFF0F2A33.toInt(); c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.075f; p.color = 0xFF22D3EE.toInt()
        p.strokeCap = Paint.Cap.ROUND
        c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 150f, 300f, false, p)
        // Hour ticks
        p.strokeWidth = s * 0.035f; p.color = 0xFFE0F2FE.toInt()
        for (i in 0 until 12) { if (i in 4..8) continue
            val a = Math.toRadians(i * 30.0 - 90); val r1 = r * 0.68f; val r2 = r * 0.82f
            c.drawLine(cx + (r1 * cos(a)).toFloat(), cy + (r1 * sin(a)).toFloat(), cx + (r2 * cos(a)).toFloat(), cy + (r2 * sin(a)).toFloat(), p) }
        // Hands
        p.strokeWidth = s * 0.06f; p.color = 0xFFFB923C.toInt()
        c.drawLine(cx, cy, cx, cy - r * 0.6f, p)
        c.drawLine(cx, cy, cx - r * 0.38f, cy + r * 0.28f, p)
        p.style = Paint.Style.FILL; c.drawCircle(cx, cy, s * 0.045f, p)
        // Leaf (bottom-left)
        p.color = 0xFF4ADE80.toInt()
        val leaf = Path().apply { moveTo(x(0.08f), y(0.58f)); cubicTo(x(0.10f), y(0.92f), x(0.40f), y(0.98f), x(0.52f), y(0.90f))
            cubicTo(x(0.42f), y(0.70f), x(0.22f), y(0.58f), x(0.08f), y(0.58f)); close() }
        c.drawPath(leaf, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.025f; p.color = 0xFF166534.toInt()
        c.drawLine(x(0.13f), y(0.63f), x(0.48f), y(0.88f), p)
        // Kettlebell (bottom-right)
        p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.06f; p.color = 0xFFC084FC.toInt()
        c.drawArc(RectF(x(0.68f), y(0.56f), x(0.90f), y(0.76f)), 180f, 180f, false, p)
        p.style = Paint.Style.FILL; c.drawCircle(x(0.79f), y(0.83f), s * 0.15f, p)
        p.color = 0xFF7E22CE.toInt(); c.drawRect(x(0.70f), y(0.95f), x(0.88f), y(0.98f), p)
    }
    override fun setAlpha(a: Int) { p.alpha = a }
    override fun setColorFilter(cf: ColorFilter?) { p.colorFilter = cf }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
