package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import io.github.michealsizemore1.beyondstride.Theme
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/**
 * 🎄 Christmas theme (Nov 27 2026): Settings → Christmas theme turns it on and off (phone and watch).
 * Colors come from Theme (evergreen background and tiles, gold accent). On the phone it also adds gentle falling snow
 * over every screen (its own switch; it never blocks taps and pauses when the screen is hidden) and a countdown on Today.
 */
object Christmas {
    private const val TAG = "xmasSnow"

    fun setOn(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences("runsettings", 0).edit().putBoolean("xmas", on).putLong("settingsAt", System.currentTimeMillis()).apply()
        Theme.load(ctx, "runsettings"); try { RunSettings.pushToWatch(ctx) } catch (e: Exception) { }
    }
    fun setSnow(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences("runsettings", 0).edit().putBoolean("xmasSnow", on).apply(); Theme.load(ctx, "runsettings")
    }

    /** "🎅 27 days until Christmas", "🎄 Merry Christmas!", or null outside the season. */
    fun countdown(): String? {
        val t = LocalDate.now(); var x = LocalDate.of(t.year, 12, 25)
        if (t.isAfter(x)) x = x.plusYears(1)
        val n = ChronoUnit.DAYS.between(t, x)
        return when { n == 0L -> "🎄 Merry Christmas!"; t.monthValue == 12 && t.dayOfMonth in 26..31 -> "🎁 Happy holidays!"; n == 1L -> "🎅 Christmas is tomorrow!"; n <= 60 -> "🎅 $n days until Christmas"; else -> null }
    }

    /** Called for every screen: adds or removes the snow layer to match the settings. */
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
        val have = root.findViewWithTag<View>(TAG)
        val want = Theme.xmas && Theme.snow
        if (want && have == null) root.addView(Snow(a).apply { tag = TAG }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        else if (!want && have != null) root.removeView(have)
        else have?.bringToFront()
    }

    /** Light snowfall: ~45 soft flakes drifting down. Doesn't take touches. */
    private class Snow(ctx: Context) : View(ctx) {
        private class F(var x: Float, var y: Float, val r: Float, val v: Float, val drift: Float, var ph: Float)
        private val flakes = ArrayList<F>()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private var last = 0L
        init { isClickable = false; isFocusable = false; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
        override fun onTouchEvent(e: android.view.MotionEvent?) = false
        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            flakes.clear(); val d = resources.displayMetrics.density
            repeat(45) { flakes += F(Random.nextFloat() * w, Random.nextFloat() * h, (1.2f + Random.nextFloat() * 2.6f) * d, (18f + Random.nextFloat() * 40f) * d, (6f + Random.nextFloat() * 14f) * d, Random.nextFloat() * 6.28f) }
        }
        override fun onDraw(c: Canvas) {
            val now = System.nanoTime(); val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f); last = now
            for (f in flakes) {
                f.y += f.v * dt; f.ph += dt * 0.8f
                if (f.y - f.r > height) { f.y = -f.r * 2; f.x = Random.nextFloat() * width }
                val x = f.x + Math.sin(f.ph.toDouble()).toFloat() * f.drift
                paint.alpha = (110 + (f.r / resources.displayMetrics.density) * 35).toInt().coerceAtMost(220)
                c.drawCircle(x, f.y, f.r, paint)
            }
            if (isShown && windowVisibility == VISIBLE) postInvalidateOnAnimation() else last = 0L
        }
        override fun onWindowVisibilityChanged(v: Int) { super.onWindowVisibilityChanged(v); if (v == VISIBLE) { last = 0L; postInvalidateOnAnimation() } }
    }
}
