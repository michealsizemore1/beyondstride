package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.michealsizemore1.beyondstride.Nutrition
import io.github.michealsizemore1.beyondstride.NutritionStore
import java.util.Locale

/** The Nutrition tab: today, fluids, micronutrients, meals and the week, shown here (like the watch). */
class NutritionActivity : Activity() {
    override fun onResume() {
        io.github.michealsizemore1.beyondstride.Theme.load(this, "runsettings")
        super.onResume(); render()
        if (NutritionSync.justOpened) NutritionSync.refreshAfterNutrition(this) { runOnUiThread { if (!isFinishing) render() } }
        else Thread { if (NutritionSync.refreshIfStale(this, 30_000L)) runOnUiThread { if (!isFinishing) render() } }.start()
    }

    private fun render() {
        val ctx = this
        fun dp(v: Int) = RunUi.dp(ctx, v)
        val N = Nutrition
        val days = NutritionStore.days(this)
        val t = N.today(days)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(40), dp(16), dp(32)) }
        col.addView(RunUi.text(this, "Nutrition", Color.WHITE, 26f, true))
        val at = NutritionStore.fetchedAt(this)
        if (at > 0) col.addView(RunUi.text(this, "Updated " + android.text.format.DateUtils.getRelativeTimeSpanString(at, System.currentTimeMillis(), 60_000L), RunUi.DIM, 11f))

        // Today: calories, macros, fluids (and weights).
        col.addView(RunUi.section(this, "TODAY"))
        col.addView(RunUi.card(this).apply { NutritionUi.today(this@NutritionActivity, this, withMicros = false, big = true) { render() } })

        // Micronutrients.
        if (t != null && t.micros.isNotEmpty()) {
            col.addView(RunUi.section(this, "MICRONUTRIENTS"))
            col.addView(RunUi.card(this).apply {
                addView(RunUi.text(ctx, String.format(Locale.US, "%.1f", t.microScore) + "/10 · " + t.microLabel(),
                    N.statusColor(when { t.microScore >= 7 -> "Met"; t.microScore >= 5 -> "Close"; else -> "Low" }), 18f, true))
                NutritionUi.micros(this@NutritionActivity, this, t, header = false)
            })
        }

        // Meals: each meal's calories and what was in it.
        if (t != null && t.count > 0) {
            col.addView(RunUi.section(this, "MEALS"))
            col.addView(RunUi.card(this).apply {
                val order = N.MEAL_ORDER + t.meals.keys.filter { it !in N.MEAL_ORDER }
                var first = true
                order.filter { (t.meals[it] ?: 0) > 0 || t.items.any { i -> i.meal == it } }.forEach { m ->
                    addView(LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, if (first) 0 else dp(12), 0, dp(2))
                        addView(RunUi.text(ctx, N.MEAL_NAMES[m] ?: m.replaceFirstChar { it.uppercase() }, Color.WHITE, 15f, true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                        addView(RunUi.text(ctx, "${N.kcal(t.meals[m] ?: 0)} kcal", RunUi.ACCENT, 14f, true))
                    })
                    first = false
                    t.items.filter { it.meal == m }.forEach { i ->
                        addView(LinearLayout(ctx).apply {
                            orientation = LinearLayout.HORIZONTAL
                            setPadding(dp(8), dp(3), 0, dp(1))
                            addView(LinearLayout(ctx).apply {
                                orientation = LinearLayout.VERTICAL
                                addView(RunUi.text(ctx, i.name, RunUi.MUTED, 13f))
                                addView(RunUi.text(ctx, "P ${i.prot.toInt()} · C ${i.carbs.toInt()} · F ${i.fat.toInt()} g", RunUi.DIM, 11f))
                            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                            addView(RunUi.text(ctx, N.kcal(i.cal), RunUi.MUTED, 13f))
                        })
                    }
                }
            })
        }

        // The last 7 days.
        val week = days.filter { it.count > 0 }.takeLast(7)
        if (week.isNotEmpty()) {
            col.addView(RunUi.section(this, "LAST 7 DAYS"))
            col.addView(RunUi.card(this).apply {
                val maxCal = maxOf(week.maxOf { maxOf(it.cal, it.target) }, 1)
                addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM
                    week.forEach { d ->
                        addView(LinearLayout(ctx).apply {
                            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                            val ok = d.target <= 0 || d.cal.toDouble() / d.target in 0.85..1.15
                            addView(RunUi.text(ctx, "${(d.cal + 50) / 100 / 10.0}k", RunUi.MUTED, 10f).apply { gravity = Gravity.CENTER })
                            addView(android.view.View(ctx).apply { background = RunUi.rounded(if (ok) 0xFF5FD38D.toInt() else 0xFFF2C94C.toInt(), dp(3).toFloat()) },
                                LinearLayout.LayoutParams(dp(16), dp(8 + d.cal * 80 / maxCal)))
                            val dd = java.time.LocalDate.parse(d.date)
                            addView(RunUi.text(ctx, dd.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() } + "\n" + d.date.substring(5).replace("-", "/"), RunUi.DIM, 10f).apply { gravity = Gravity.CENTER })
                        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                    }
                })
                val avgCal = week.map { it.cal }.average().toInt()
                val withT = week.filter { it.target > 0 }
                addView(RunUi.text(ctx, "Average ${N.kcal(avgCal)} kcal/day" + (if (withT.isNotEmpty()) " · target ${N.kcal(withT.map { it.target }.average().toInt())}" else "") +
                    "\nProtein ${week.map { it.prot }.average().toInt()} g · carbs ${week.map { it.carbs }.average().toInt()} g · fat ${week.map { it.fat }.average().toInt()} g a day" +
                    (week.filter { it.microScore > 0 }.takeIf { it.isNotEmpty() }?.let { "\nMicronutrients " + String.format(Locale.US, "%.1f", it.map { d -> d.microScore }.average()) + "/10 average" } ?: ""),
                    Color.WHITE, 13f).apply { setPadding(0, dp(8), 0, 0) })
                addView(RunUi.text(ctx, "Green = within 15% of that day's target.", RunUi.DIM, 11f).apply { setPadding(0, dp(4), 0, 0) })
            })
        }

        // Logging food still happens in the Command Center (until BeyondStride has its own food log).
        col.addView(RunUi.button(this, "🍎  Log food in the Command Center", RunUi.ACCENT) { RunUi.openNutrition(this) }.apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(22) }
        })
        col.addView(RunUi.text(this, "Pull down the page there to refresh; totals show here a few seconds after you log.", RunUi.DIM, 11f).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) })

        setContentView(RunUi.tabbed(this, ScrollView(this).apply {
            setBackgroundColor(RunUi.BG)
            addView(col, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }, RunUi.TAB_NUTRITION))
    }
}
