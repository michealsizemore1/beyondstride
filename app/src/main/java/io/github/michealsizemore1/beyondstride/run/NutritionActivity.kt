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

        // DAILY CALORIE TARGET — same settings and math as the Command Center (settings shared both ways).
        run {
            val CT = io.github.michealsizemore1.beyondstride.CalorieTarget
            val st = CalorieTargets.settings(ctx)
            val r = CalorieTargets.result(ctx, days)
            col.addView(RunUi.section(this, "DAILY CALORIE TARGET"))
            col.addView(RunUi.card(this).apply {
                addView(RunUi.text(ctx, java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")), RunUi.MUTED, 12f))
                // Settings: rest-day target · goal · replace workout calories.
                addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0)
                    addView(LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(RunUi.text(ctx, "Rest-day target", RunUi.MUTED, 11f, true))
                        addView(android.widget.EditText(ctx).apply {
                            setText(st.base); hint = "auto (${r.base})"; setTextColor(Color.WHITE); setHintTextColor(RunUi.DIM); textSize = 16f
                            inputType = android.text.InputType.TYPE_CLASS_NUMBER; setSingleLine()
                            background = RunUi.rounded(0xFF26302C.toInt(), dp(8).toFloat()); setPadding(dp(10), dp(8), dp(10), dp(8))
                            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                            fun commit() { val v = text.toString().trim(); if (v != st.base) { CalorieTargets.set(ctx, st.copy(base = v)); render() } }
                            setOnEditorActionListener { _, _, _ -> commit(); true }
                            setOnFocusChangeListener { _, has -> if (!has) commit() }
                        })
                    }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
                    addView(LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(RunUi.text(ctx, "Goal", RunUi.MUTED, 11f, true))
                        addView(RunUi.text(ctx, (CT.GOALS.firstOrNull { it.first == st.goal }?.second ?: "Maintain weight") + "  ▾", Color.WHITE, 15f, true).apply {
                            background = RunUi.rounded(0xFF26302C.toInt(), dp(8).toFloat()); setPadding(dp(10), dp(9), dp(10), dp(9))
                            setOnClickListener {
                                android.app.AlertDialog.Builder(ctx).setTitle("Goal").setItems(CT.GOALS.map { it.second }.toTypedArray()) { _, i ->
                                    CalorieTargets.set(ctx, st.copy(goal = CT.GOALS[i].first)); render() }.show()
                            }
                        })
                    }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                })
                addView(RunUi.text(ctx, "Replace workout calories", RunUi.MUTED, 11f, true).apply { setPadding(0, dp(8), 0, 0) })
                addView(RunUi.text(ctx, "${st.refuel}%  ▾", Color.WHITE, 15f, true).apply {
                    background = RunUi.rounded(0xFF26302C.toInt(), dp(8).toFloat()); setPadding(dp(10), dp(9), dp(10), dp(9))
                    setOnClickListener {
                        android.app.AlertDialog.Builder(ctx).setTitle("Replace workout calories").setItems(CT.REFUELS.map { "$it%" }.toTypedArray()) { _, i ->
                            CalorieTargets.set(ctx, st.copy(refuel = CT.REFUELS[i])); render() }.show()
                    }
                })
                // Result.
                addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL; background = RunUi.rounded(0xFF26302C.toInt(), dp(12).toFloat())
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) }
                    addView(RunUi.text(ctx, "${CT.kcal(r.target)} kcal  suggested intake", 0xFF5FD38D.toInt(), 20f, true))
                    r.preset?.let { pr ->
                        addView(RunUi.text(ctx, pr.label, 0xFF93C5FD.toInt(), 12f, true).apply { setPadding(0, dp(4), 0, 0) })
                        addView(RunUi.text(ctx, "Hard-workout fueling is on. The goal adjustment is paused for this day.", RunUi.MUTED, 11f))
                    }
                    fun line(b: String, t: String, c: Int = Color.WHITE) = addView(RunUi.text(ctx, "$b $t", c, 13f).apply { setPadding(0, dp(3), 0, 0) })
                    line("Practical range:", "${CT.kcal(maxOf(1200, r.target - r.range))}–${CT.kcal(r.target + r.range)} kcal")
                    line("Calculation:", (r.preset?.let { "Higher of ${CT.kcal(it.calories)} performance target or " } ?: "") +
                        "${CT.kcal(r.base)} rest-day target + ${CT.kcal(Math.round(r.active * r.refuel).toInt())} workout replacement" +
                        (if (r.goalAdjust != 0) (if (r.goalAdjust > 0) " + " else " − ") + "${kotlin.math.abs(r.goalAdjust)} goal adjustment" else ""))
                    line("Activities:", if (r.activities.isEmpty() && r.otherActive == 0) "No workout yet · 0 active kcal"
                        else "${r.activities.size} run${if (r.activities.size == 1) "" else "s"} · ${CT.kcal(r.active)} active kcal")
                    if (r.activities.isNotEmpty()) line("Used:", r.activities.joinToString("; ") { String.format(Locale.US, "%s (%.2f mi · %s · %s kcal)", it.name, it.miles, it.duration, CT.kcal(it.kcal)) }, 0xFFB5D99C.toInt())
                    if (r.otherActive > 0) line("Command Center:", "+${CT.kcal(r.otherActive)} kcal from other activities logged there", 0xFFB5D99C.toInt())
                    val logged = t?.cal ?: 0
                    line("Logged so far:", "${CT.kcal(logged)} kcal · " + if (logged > r.target) "Over target: ${CT.kcal(logged - r.target)} kcal" else "Remaining: ${CT.kcal(r.target - logged)} kcal",
                        if (logged > r.target) 0xFFFF8A80.toInt() else Color.WHITE)
                    if (!r.manualBase && r.weight > 0) addView(RunUi.text(ctx, "Automatic rest-day target uses your most recent weight of ${String.format(Locale.US, "%.1f", r.weight)} lb.", RunUi.DIM, 11f).apply { setPadding(0, dp(4), 0, 0) })
                })
                addView(RunUi.text(ctx, "Estimate only. It uses your runs and current weight when available; medical conditions and individual metabolism can change your needs.", RunUi.DIM, 11f).apply { setPadding(0, dp(6), 0, 0) })
            })
        }

        // Micronutrients.
        if (t != null && t.count > 0 && t.micros.isNotEmpty()) {
            col.addView(RunUi.section(this, "MICRONUTRIENTS"))
            col.addView(RunUi.card(this).apply {
                addView(RunUi.text(ctx, String.format(Locale.US, "%.1f", t.microScore) + "/10 · " + t.microLabel(),
                    N.statusColor(when { t.microScore >= 7 -> "Met"; t.microScore >= 5 -> "Close"; else -> "Low" }), 18f, true))
                NutritionUi.micros(this@NutritionActivity, this, t, header = false)
            })
        }

        // FOOD LOG: every meal with its foods. Tap a food to change it; ＋ to add.
        col.addView(RunUi.section(this, "FOOD LOG"))
        // Food log tools — the Command Center's six buttons, same colors and place (above the meals).
        run {
            fun add(action: String) = startActivity(android.content.Intent(ctx, FoodAddActivity::class.java).putExtra("meal", FoodUi.mealNow()).putExtra("action", action))
            fun tools(mode: String) = startActivity(android.content.Intent(ctx, NutritionToolsActivity::class.java).putExtra("mode", mode))
            val tiles = listOf(
                Triple("📷 Camera", 0xFFE67E22.toInt()) { add("camera") }, Triple("🖼 Upload", 0xFFC0640F.toInt()) { add("upload") },
                Triple("▥ Barcode", 0xFF0F766E.toInt()) { add("barcode") }, Triple("🥗 Meal Ideas", 0xFF059669.toInt()) { tools("ideas") },
                Triple("🧠 Fuel AI", 0xFF8E44AD.toInt()) { tools("fuel") }, Triple("📅 Copy Day", 0xFF16A085.toInt()) { FoodUi.copyDay(this@NutritionActivity) { render() } },
            )
            tiles.chunked(3).forEach { row ->
                col.addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }
                    row.forEachIndexed { i, (t, c, go) ->
                        addView(RunUi.text(ctx, t, Color.WHITE, 14f, true).apply {
                            gravity = Gravity.CENTER; maxLines = 1; background = RunUi.rounded(c, dp(12).toFloat())
                            setPadding(dp(4), dp(12), dp(4), dp(12)); setOnClickListener { go() }
                        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { if (i < 2) marginEnd = dp(6) })
                    }
                })
            }
            col.addView(RunUi.text(ctx, "Camera and Upload read a nutrition label; Camera, Upload and Fuel AI use your Anthropic key.", RunUi.DIM, 10f).apply { setPadding(dp(2), dp(4), 0, 0) })
        }

        col.addView(android.view.View(ctx), LinearLayout.LayoutParams(MATCH_PARENT, dp(6)))
        col.addView(RunUi.card(this).apply {
            val foods = t?.foods ?: emptyList()
            val order = N.MEAL_ORDER + foods.map { it.meal }.distinct().filter { it !in N.MEAL_ORDER }
            order.forEachIndexed { idx, m ->
                val mine = foods.filter { it.meal == m }
                addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, if (idx == 0) 0 else dp(10), 0, dp(2))
                    addView(RunUi.text(ctx, N.MEAL_NAMES[m] ?: m.replaceFirstChar { it.uppercase() }, Color.WHITE, 15f, true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                    if (mine.isNotEmpty()) addView(RunUi.text(ctx, "${N.kcal(mine.sumOf { it.kcal })} kcal  ", RunUi.ACCENT, 14f, true))
                    if (mine.isNotEmpty()) addView(RunUi.text(ctx, "💾", Color.WHITE, 15f).apply {
                        background = RunUi.rounded(0xFF26302C.toInt(), dp(10).toFloat()); setPadding(dp(8), dp(3), dp(8), dp(3))
                        setOnClickListener { saveMealDialog(m, mine) }
                    }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(6) })
                    addView(RunUi.text(ctx, "＋ Add", Color.BLACK, 12f, true).apply {
                        background = RunUi.rounded(RunUi.ACCENT, dp(12).toFloat()); setPadding(dp(10), dp(4), dp(10), dp(4))
                        setOnClickListener { startActivity(android.content.Intent(ctx, FoodAddActivity::class.java).putExtra("meal", m)) }
                    })
                })
                mine.forEach { f ->
                    addView(LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(dp(8), dp(4), 0, dp(2))
                        addView(LinearLayout(ctx).apply {
                            orientation = LinearLayout.VERTICAL
                            addView(RunUi.text(ctx, f.name, RunUi.MUTED, 13f))
                            addView(RunUi.text(ctx, io.github.michealsizemore1.beyondstride.Food.fmt(f.servings) + " × " + f.servingLabel +
                                " · P ${f.total(f.prot).toInt()} · C ${f.total(f.carbs).toInt()} · F ${f.total(f.fat).toInt()} g", RunUi.DIM, 11f))
                        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                        addView(RunUi.text(ctx, N.kcal(f.kcal), RunUi.MUTED, 13f))
                        setOnClickListener { FoodUi.edit(this@NutritionActivity, f) { render() } }
                    })
                }
            }
        })

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

        // THIS WEEK: the Command Center's three charts (Mon–Sun).
        run {
            val W = io.github.michealsizemore1.beyondstride.WeekChart
            val byDate = days.associateBy { it.date }
            val dates = W.weekDates()
            fun chart(title: String, help: String?, color: Int, unit: String, v: (Nutrition.Day) -> Double) {
                val vals = dates.map { dt -> byDate[dt]?.let(v)?.takeIf { it > 0 } }
                col.addView(RunUi.card(this).apply {
                    addView(RunUi.text(ctx, "$title — This week", RunUi.ACCENT, 14f, true))
                    help?.let { addView(RunUi.text(ctx, it, RunUi.MUTED, 11f).apply { setPadding(0, dp(2), 0, dp(4)) }) }
                    addView(io.github.michealsizemore1.beyondstride.WeekChart(ctx, vals, color), LinearLayout.LayoutParams(MATCH_PARENT, dp(190)))
                    addView(RunUi.text(ctx, W.summary(vals, unit), RunUi.MUTED, 12f).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) })
                }.also { it.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) } })
            }
            col.addView(RunUi.section(this, "THIS WEEK"))
            chart("Calories", null, 0xFFF39C12.toInt(), "") { it.cal.toDouble().takeIf { _ -> it.count > 0 } ?: 0.0 }
            chart("Water", null, 0xFF3498DB.toInt(), " oz") { (if (it.water + it.sportsOz + it.otherOz > 0) it.water else it.waterOz).toDouble() }
            chart("Total liquids (excl. caffeine)", "Water + sports drinks + other drinks, minus coffee, energy drinks and black/green tea. GU drink mix still counts.",
                0xFF16A085.toInt(), " oz") { it.waterOz.toDouble() }
        }

        // The Command Center's own Nutrition page (AI search, label scan, food database) — still there too.
        col.addView(RunUi.button(this, "Open Nutrition in the Command Center", RunUi.CARD, Color.WHITE) { RunUi.openNutrition(this) }.apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(22) }
        })
        col.addView(RunUi.text(this, "Food logged in either app shows in both.", RunUi.DIM, 11f).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) })

        setContentView(RunUi.tabbed(this, ScrollView(this).apply {
            setBackgroundColor(RunUi.BG)
            addView(col, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }, RunUi.TAB_NUTRITION))
    }

    /** 💾 Save this meal's foods as a saved meal (also added to the Command Center's saved meals). */
    private fun saveMealDialog(meal: String, items: List<io.github.michealsizemore1.beyondstride.Food.Item>) {
        val ctx = this
        val name = android.widget.EditText(this).apply {
            setText((Nutrition.MEAL_NAMES[meal] ?: meal) + " " + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("MMM d")))
            setSelectAllOnFocus(true); setSingleLine()
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(RunUi.dp(ctx, 22), RunUi.dp(ctx, 8), RunUi.dp(ctx, 22), 0)
            addView(name); addView(RunUi.text(ctx, items.joinToString("\n") { "• ${it.name}" }, RunUi.MUTED, 12f).apply { setPadding(0, RunUi.dp(ctx, 6), 0, 0) }) }
        android.app.AlertDialog.Builder(this).setTitle("Save meal").setView(box)
            .setPositiveButton("Save") { _, _ ->
                val n = name.text.toString().trim().ifEmpty { Nutrition.MEAL_NAMES[meal] ?: meal }
                FoodLibrary.saveMeal(ctx, n, meal, items)
                android.widget.Toast.makeText(ctx, "Saved “$n” — in Add food → Saved meals", android.widget.Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Cancel", null).show()
    }
}
