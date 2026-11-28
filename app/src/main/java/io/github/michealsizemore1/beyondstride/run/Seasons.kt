package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import io.github.michealsizemore1.beyondstride.Theme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.random.Random

/**
 * 🎨 Seasonal themes (Nov 27 2026): Settings → Theme picks Halloween, Thanksgiving, Christmas, New Year's, Winter,
 * Spring or 4th of July (or Off) on the phone and the watch. Colors come from Theme. On the phone each theme also has a
 * gentle falling effect over every screen (snow, leaves, petals, confetti, sparks, bats; its own switch, never blocks
 * taps, pauses when hidden) and a countdown line on Today.
 */
object Seasons {
    private const val TAG = "seasonFx"

    fun set(ctx: Context, id: String) {
        ctx.getSharedPreferences("runsettings", 0).edit().putString("season", id).putBoolean("xmas", false)
            .putLong("settingsAt", System.currentTimeMillis()).apply()
        Theme.load(ctx, "runsettings"); try { RunSettings.pushToWatch(ctx) } catch (e: Exception) { }
    }
    fun setEffects(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences("runsettings", 0).edit().putBoolean("xmasSnow", on).apply(); Theme.load(ctx, "runsettings")
    }
    fun effectName(id: String?) = when (id) { "christmas", "winter" -> "Falling snow"; "thanksgiving" -> "Falling leaves"; "spring" -> "Falling petals"
        "newyear" -> "Confetti"; "july4" -> "Sparkles"; "halloween" -> "Bats & embers"; else -> "Effects" }

    private fun next(today: LocalDate, md: (Int) -> LocalDate): LocalDate { val d = md(today.year); return if (d.isBefore(today)) md(today.year + 1) else d }
    /** A line for Today: "🎅 27 days until Christmas", "🎉 Happy New Year!", or null when the day is far off. */
    fun countdown(): String? {
        val id = Theme.season?.id ?: return null; val t = LocalDate.now()
        fun line(d: LocalDate, emoji: String, name: String, onDay: String, window: Long = 60): String? {
            val n = ChronoUnit.DAYS.between(t, d)
            return when { n == 0L -> onDay; n == 1L -> "$emoji $name is tomorrow!"; n in 2..window -> "$emoji $n days until $name"; else -> null }
        }
        return when (id) {
            "halloween" -> line(next(t) { LocalDate.of(it, 10, 31) }, "🎃", "Halloween", "🎃 Happy Halloween!")
            "thanksgiving" -> line(next(t) { LocalDate.of(it, 11, 1).with(TemporalAdjusters.dayOfWeekInMonth(4, DayOfWeek.THURSDAY)) }, "🦃", "Thanksgiving", "🦃 Happy Thanksgiving!")
            "christmas" -> if (t.monthValue == 12 && t.dayOfMonth in 26..31) "🎁 Happy holidays!" else line(next(t) { LocalDate.of(it, 12, 25) }, "🎅", "Christmas", "🎄 Merry Christmas!")
            "newyear" -> if (t.monthValue == 1 && t.dayOfMonth in 2..7) "🎉 Happy New Year!" else line(next(t) { LocalDate.of(it, 1, 1) }, "🎉", "New Year's Day", "🎉 Happy New Year!")
            "winter" -> line(next(t) { LocalDate.of(it, 12, 21) }, "❄️", "the first day of winter", "❄️ Winter starts today")
            "spring" -> line(next(t) { LocalDate.of(it, 3, 20) }, "🌸", "the first day of spring", "🌸 Spring starts today!")
            "july4" -> line(next(t) { LocalDate.of(it, 7, 4) }, "🎆", "the 4th of July", "🎆 Happy 4th of July!")
            else -> null
        }
    }

    /** Called for every screen: adds or removes the falling effect to match the settings. */
    fun install(app: Application) {
        Theme.load(app, "runsettings")
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(a: Activity) { try { apply(a) } catch (e: Exception) { } }
            override fun onActivityCreated(a: Activity, b: Bundle?) { Theme.load(a, "runsettings") }
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
    }

    fun apply(a: Activity) {
        val root = a.window?.decorView as? ViewGroup ?: return
        val have = root.findViewWithTag<Falling>(TAG)
        val s = Theme.season
        val want = s != null && Theme.effects
        if (have != null && (!want || have.id0 != s?.id)) root.removeView(have)
        val now = root.findViewWithTag<Falling>(TAG)
        if (want && now == null) root.addView(Falling(a, s!!).apply { tag = TAG }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        else now?.bringToFront()
    }

    /** The falling (or, for Halloween, rising) particles. Doesn't take touches. */
    private class Falling(ctx: Context, p: Theme.Palette) : View(ctx) {
        val id0 = p.id
        private val fx = p.fx
        private class F(var x: Float, var y: Float, val r: Float, val v: Float, val drift: Float, var ph: Float, val color: Int, val emoji: String?)
        private val flakes = ArrayList<F>()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        private var last = 0L
        init { isClickable = false; isFocusable = false; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
        override fun onTouchEvent(e: android.view.MotionEvent?) = false
        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            flakes.clear(); val d = resources.displayMetrics.density
            repeat(fx.count) { i ->
                // Emoji make up a small share (all of it when there are no dots, e.g. Thanksgiving's leaves).
                val em = if (fx.emoji.isNotEmpty() && (fx.dots.isEmpty() || i % 4 == 0)) fx.emoji[i % fx.emoji.size] else null
                val r = if (em != null) (9f + Random.nextFloat() * 7f) * d else (1.2f + Random.nextFloat() * 2.6f) * d
                val col = if (fx.dots.isEmpty()) 0 else fx.dots[i % fx.dots.size]
                flakes += F(Random.nextFloat() * w, Random.nextFloat() * h, r, (18f + Random.nextFloat() * 40f) * d * fx.speed, (6f + Random.nextFloat() * 16f) * d, Random.nextFloat() * 6.28f, col, em)
            }
        }
        override fun onDraw(c: Canvas) {
            val now = System.nanoTime(); val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f); last = now
            val d = resources.displayMetrics.density
            for (f in flakes) {
                f.y += (if (fx.up) -f.v else f.v) * dt; f.ph += dt * 0.8f
                if (!fx.up && f.y - f.r > height) { f.y = -f.r * 2; f.x = Random.nextFloat() * width }
                if (fx.up && f.y + f.r < 0) { f.y = height + f.r * 2; f.x = Random.nextFloat() * width }
                val x = f.x + Math.sin(f.ph.toDouble()).toFloat() * f.drift
                if (f.emoji != null) { tp.textSize = f.r * 2; tp.alpha = 200; c.save(); c.rotate(Math.sin(f.ph * 1.3).toFloat() * 25f, x, f.y); c.drawText(f.emoji, x, f.y + f.r * 0.7f, tp); c.restore() }
                else { paint.color = f.color; paint.alpha = (110 + (f.r / d) * 35).toInt().coerceAtMost(220); c.drawCircle(x, f.y, f.r, paint) }
            }
            if (isShown && windowVisibility == VISIBLE) postInvalidateOnAnimation() else last = 0L
        }
        override fun onWindowVisibilityChanged(v: Int) { super.onWindowVisibilityChanged(v); if (v == VISIBLE) { last = 0L; postInvalidateOnAnimation() } }
    }
}
