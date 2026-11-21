package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalDate
import kotlin.random.Random

/**
 * 🧠 Brain Games — a quick daily workout in ☰ More, three short games:
 *  🔲 Memory Grid (remember the lit squares; one more each level, two misses ends it),
 *  🎨 Color Clash (tap the ink color, not the word; 30 seconds),
 *  ➕ Quick Math (pick the right answer; 45 seconds, gets harder).
 * Scores, bests and the daily streak stay on this phone (prefs "brain"). Phone only, like the rest of Life.
 */
class LifeBrain(private val act: Activity) {
    val view = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
    private val ui = Handler(Looper.getMainLooper())
    private val p = act.getSharedPreferences("brain", 0)
    private var run = 0          // bumps on every new screen so old timers stop
    private fun d(v: Int) = RunUi.dp(act, v)
    private fun lp(top: Int = 8) = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = d(top) }
    private fun today() = LocalDate.now().toString()

    private data class Game(val id: String, val icon: String, val name: String, val what: String, val unit: String)
    private val games = listOf(
        Game("grid", "🔲", "Memory Grid", "Remember which squares light up, then tap them. One more square each level.", "level"),
        Game("color", "🎨", "Color Clash", "Tap the color of the ink, not the word. 30 seconds.", "points"),
        Game("math", "➕", "Quick Math", "Pick the right answer. 45 seconds, and it gets harder.", "points"))

    // ── Scores ──
    private fun best(g: String) = p.getInt("best_$g", 0)
    private fun todayBest(g: String) = p.getInt("day_${today()}_$g", -1)
    private fun record(g: String, score: Int): Boolean {
        val t = today(); val newBest = score > best(g)
        p.edit().apply {
            if (score > p.getInt("day_${t}_$g", -1)) putInt("day_${t}_$g", score)
            if (newBest) putInt("best_$g", score)
            putStringSet("days", (p.getStringSet("days", emptySet()) ?: emptySet()) + t)
        }.apply()
        return newBest
    }
    private fun streak(): Int {
        val days = p.getStringSet("days", emptySet()) ?: emptySet()
        var day = LocalDate.now(); if (day.toString() !in days) day = day.minusDays(1)
        var n = 0; while (day.toString() in days) { n++; day = day.minusDays(1) }
        return n
    }

    // ── Home ──
    fun show() {
        run++; view.removeAllViews()
        val done = games.count { todayBest(it.id) >= 0 }
        view.addView(RunUi.card(act).apply {
            addView(RunUi.text(act, "Today's workout", Color.WHITE, 16f, true))
            addView(RunUi.text(act, if (done == games.size) "✅ All 3 done today. Nice work!" else "$done of 3 games played today. A few minutes keeps your mind sharp.", RunUi.MUTED, 13f).apply { setPadding(0, d(4), 0, 0) })
            val s = streak()
            addView(RunUi.text(act, if (s > 0) "🔥 $s-day streak" else "Play any game to start a streak.", if (s > 0) 0xFFF59E0B.toInt() else RunUi.DIM, 13f, true).apply { setPadding(0, d(6), 0, 0) })
            addView(week())
        })
        games.forEach { g ->
            view.addView(LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(d(14), d(14), d(14), d(14))
                background = RunUi.rounded(RunUi.CARD, d(16).toFloat())
                addView(RunUi.text(act, g.icon, Color.WHITE, 30f).apply { setPadding(0, 0, d(12), 0) })
                addView(LinearLayout(act).apply { orientation = LinearLayout.VERTICAL
                    addView(RunUi.text(act, g.name, Color.WHITE, 16f, true))
                    addView(RunUi.text(act, g.what, RunUi.MUTED, 12f))
                    val tb = todayBest(g.id)
                    addView(RunUi.text(act, (if (tb >= 0) "Today: $tb · " else "") + "Best: ${best(g.id)} ${g.unit}", RunUi.ACCENT, 12f, true).apply { setPadding(0, d(4), 0, 0) })
                }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(RunUi.text(act, "▶", RunUi.ACCENT, 22f, true).apply { setPadding(d(8), 0, 0, 0) })
                setOnClickListener { start(g) }
            }, lp(10))
        }
    }

    /** The last 7 days: a filled dot for each day you played. */
    private fun week() = LinearLayout(act).apply {
        orientation = LinearLayout.HORIZONTAL; setPadding(0, d(10), 0, 0)
        val days = p.getStringSet("days", emptySet()) ?: emptySet()
        for (i in 6 downTo 0) { val dt = LocalDate.now().minusDays(i.toLong())
            addView(LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                addView(RunUi.text(act, if (dt.toString() in days) "●" else "○", if (dt.toString() in days) RunUi.ACCENT else RunUi.DIM, 18f, true))
                addView(RunUi.text(act, dt.dayOfWeek.name.take(1), RunUi.DIM, 11f))
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)) }
    }

    private fun start(g: Game) = when (g.id) { "grid" -> grid(g, 3, 0); "color" -> color(g); else -> math(g) }

    /** Game screen frame: ‹ back, title, a status line and the play area. */
    private fun frame(g: Game, status: TextView): LinearLayout {
        run++; view.removeAllViews()
        view.addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(RunUi.text(act, "‹ Games", RunUi.ACCENT, 15f, true).apply { setPadding(0, d(4), d(12), d(4)); setOnClickListener { show() } })
            addView(RunUi.text(act, g.icon + " " + g.name, Color.WHITE, 16f, true)) })
        view.addView(status.apply { setPadding(0, d(8), 0, 0); gravity = Gravity.CENTER })
        val area = RunUi.card(act).apply { gravity = Gravity.CENTER_HORIZONTAL }
        view.addView(area, lp(10))
        return area
    }

    private fun finish(g: Game, score: Int, line: String) {
        val newBest = record(g.id, score)
        run++; view.removeAllViews()
        view.addView(RunUi.card(act).apply { gravity = Gravity.CENTER_HORIZONTAL
            addView(RunUi.text(act, g.icon + " " + g.name, Color.WHITE, 16f, true))
            addView(RunUi.text(act, "$score", RunUi.ACCENT, 48f, true).apply { setPadding(0, d(8), 0, 0) })
            addView(RunUi.text(act, g.unit, RunUi.MUTED, 13f))
            addView(RunUi.text(act, line, Color.WHITE, 14f).apply { gravity = Gravity.CENTER; setPadding(0, d(10), 0, 0) })
            addView(RunUi.text(act, if (newBest) "🏆 New best!" else "Best: ${best(g.id)}", if (newBest) 0xFFF59E0B.toInt() else RunUi.MUTED, 14f, true).apply { setPadding(0, d(6), 0, 0) })
            addView(RunUi.button(act, "Play again", RunUi.ACCENT) { start(g) }, lp(14))
            addView(RunUi.button(act, "Back to games", 0xFF374151.toInt(), Color.WHITE) { show() }, lp(8))
        })
    }

    // ── 🔲 Memory Grid ──
    private fun grid(g: Game, level: Int, misses: Int) {
        val size = if (level <= 5) 4 else if (level <= 9) 5 else 6
        val status = RunUi.text(act, "Level $level · watch…", Color.WHITE, 15f, true)
        val area = frame(g, status); val my = run
        val lit = (0 until size * size).shuffled().take(level.coerceAtMost(size * size - 1)).toSet()
        val picked = HashSet<Int>(); var open = false; var wrong = 0
        val cellSize = ((act.resources.displayMetrics.widthPixels - d(80)) / size).coerceAtMost(d(72))
        val off = 0xFF2A3430.toInt(); val on = 0xFF818CF8.toInt()
        val cells = ArrayList<View>()
        area.addView(GridLayout(act).apply { columnCount = size
            for (i in 0 until size * size) { val c = View(act).apply { background = RunUi.rounded(off, d(8).toFloat()) }
                cells += c
                addView(c, GridLayout.LayoutParams().apply { width = cellSize - d(6); height = cellSize - d(6); setMargins(d(3), d(3), d(3), d(3)) })
                c.setOnClickListener {
                    if (!open || i in picked || run != my) return@setOnClickListener
                    picked += i
                    if (i in lit) { c.background = RunUi.rounded(0xFF22C55E.toInt(), d(8).toFloat())
                        if (picked.count { it in lit } == lit.size) { open = false; status.text = "✓ Level $level done!"; ui.postDelayed({ if (run == my) grid(g, level + 1, misses) }, 700) }
                    } else { c.background = RunUi.rounded(0xFFEF4444.toInt(), d(8).toFloat()); wrong++
                        if (wrong >= 1) { open = false
                            lit.forEach { cells[it].background = RunUi.rounded(on, d(8).toFloat()) }
                            if (misses + 1 >= 2) { status.text = "Missed — game over"; ui.postDelayed({ if (run == my) finish(g, level - 1, "You reached level ${level - 1}. Two misses ends the game.") }, 1200) }
                            else { status.text = "Missed — one more try"; ui.postDelayed({ if (run == my) grid(g, level, misses + 1) }, 1200) } }
                    }
                }
            }
        })
        area.addView(RunUi.text(act, "Misses: $misses of 2", RunUi.DIM, 12f).apply { setPadding(0, d(10), 0, 0) })
        ui.postDelayed({ if (run == my) lit.forEach { cells[it].background = RunUi.rounded(on, d(8).toFloat()) } }, 500)
        ui.postDelayed({ if (run == my) { lit.forEach { cells[it].background = RunUi.rounded(off, d(8).toFloat()) }; open = true; status.text = "Level $level · tap the ${lit.size} squares" } },
            500L + 900L + 120L * level)
    }

    // ── 🎨 Color Clash ──
    private val colors = listOf("RED" to 0xFFEF4444.toInt(), "BLUE" to 0xFF3B82F6.toInt(), "GREEN" to 0xFF22C55E.toInt(), "YELLOW" to 0xFFEAB308.toInt())
    private fun color(g: Game) {
        val status = RunUi.text(act, "", Color.WHITE, 15f, true)
        val area = frame(g, status); val my = run
        var right = 0; var wrongN = 0; val end = System.currentTimeMillis() + 30_000
        val word = RunUi.text(act, "", Color.WHITE, 44f, true).apply { gravity = Gravity.CENTER; setPadding(0, d(20), 0, d(20)) }
        val flash = RunUi.text(act, " ", RunUi.MUTED, 14f, true).apply { gravity = Gravity.CENTER }
        area.addView(word, lp(0)); area.addView(flash)
        var ink = 0
        fun next() { val w = colors.indices.random(); ink = if (Random.nextInt(4) == 0) w else (colors.indices - w).random()
            word.text = colors[w].first; word.setTextColor(colors[ink].second) }
        val grid = GridLayout(act).apply { columnCount = 2 }
        colors.forEachIndexed { i, (name, c) ->
            grid.addView(RunUi.button(act, name, c, Color.WHITE) {
                if (run != my || System.currentTimeMillis() > end) return@button
                if (i == ink) { right++; flash.text = "✓"; flash.setTextColor(0xFF22C55E.toInt()) } else { wrongN++; flash.text = "✗"; flash.setTextColor(0xFFEF4444.toInt()) }
                next()
            }, GridLayout.LayoutParams().apply { width = (act.resources.displayMetrics.widthPixels - d(90)) / 2; setMargins(d(4), d(4), d(4), d(4)) })
        }
        area.addView(grid, lp(10))
        next()
        val tick = object : Runnable { override fun run() {
            if (run != my) return
            val left = ((end - System.currentTimeMillis()) / 1000).toInt()
            if (left <= 0) { val sc = (right - wrongN).coerceAtLeast(0); finish(g, sc, "$right right · $wrongN wrong"); return }
            status.text = "⏱ $left s · $right right"; ui.postDelayed(this, 250) } }
        tick.run()
    }

    // ── ➕ Quick Math ──
    private fun math(g: Game) {
        val status = RunUi.text(act, "", Color.WHITE, 15f, true)
        val area = frame(g, status); val my = run
        var right = 0; var wrongN = 0; val end = System.currentTimeMillis() + 45_000
        val q = RunUi.text(act, "", Color.WHITE, 38f, true).apply { gravity = Gravity.CENTER; setPadding(0, d(20), 0, d(20)) }
        val flash = RunUi.text(act, " ", RunUi.MUTED, 14f, true).apply { gravity = Gravity.CENTER }
        area.addView(q, lp(0)); area.addView(flash)
        val grid = GridLayout(act).apply { columnCount = 2 }
        area.addView(grid, lp(10))
        var answer = 0
        fun next() {
            val lvl = right / 4      // a step harder every 4 right
            val hi = 10 + lvl * 6
            val (text, ans) = when (Random.nextInt(if (lvl < 2) 2 else if (lvl < 4) 3 else 4)) {
                0 -> { val a = Random.nextInt(2, hi); val b = Random.nextInt(2, hi); "$a + $b" to a + b }
                1 -> { val a = Random.nextInt(5, hi + 5); val b = Random.nextInt(1, a); "$a − $b" to a - b }
                2 -> { val a = Random.nextInt(2, 6 + lvl); val b = Random.nextInt(2, 10); "$a × $b" to a * b }
                else -> { val b = Random.nextInt(2, 10); val c = Random.nextInt(2, 6 + lvl); "${b * c} ÷ $b" to c }
            }
            answer = ans; q.text = text
            val opts = mutableSetOf(ans); while (opts.size < 4) { val o = ans + Random.nextInt(-10, 11); if (o >= 0 && o != ans) opts += o }
            grid.removeAllViews()
            opts.shuffled().forEach { o ->
                grid.addView(RunUi.button(act, "$o", 0xFF374151.toInt(), Color.WHITE) {
                    if (run != my || System.currentTimeMillis() > end) return@button
                    if (o == answer) { right++; flash.text = "✓"; flash.setTextColor(0xFF22C55E.toInt()) } else { wrongN++; flash.text = "✗  $text = $answer"; flash.setTextColor(0xFFEF4444.toInt()) }
                    next()
                }, GridLayout.LayoutParams().apply { width = (act.resources.displayMetrics.widthPixels - d(90)) / 2; setMargins(d(4), d(4), d(4), d(4)) })
            }
        }
        next()
        val tick = object : Runnable { override fun run() {
            if (run != my) return
            val left = ((end - System.currentTimeMillis()) / 1000).toInt()
            if (left <= 0) { val sc = (right - wrongN).coerceAtLeast(0); finish(g, sc, "$right right · $wrongN wrong"); return }
            status.text = "⏱ $left s · $right right"; ui.postDelayed(this, 250) } }
        tick.run()
    }

    /** Leaving the page: stop any timers. */
    fun stop() { run++ }
}
