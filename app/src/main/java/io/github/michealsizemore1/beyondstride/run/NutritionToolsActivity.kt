package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import io.github.michealsizemore1.beyondstride.Food
import io.github.michealsizemore1.beyondstride.MealIdeas
import io.github.michealsizemore1.beyondstride.Nutrition
import io.github.michealsizemore1.beyondstride.NutritionStore
import io.github.michealsizemore1.beyondstride.RunMath
import java.time.LocalDate
import java.util.Locale

/** Nutrition tools: Meal ideas (20/60/20) and the daily calorie target. (Fuel AI lives in Coach AI.) */
class NutritionToolsActivity : Activity() {
    private var filter = "all"

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        io.github.michealsizemore1.beyondstride.Theme.load(this, "runsettings")
        render()
    }

    private fun dp(v: Int) = RunUi.dp(this, v)
    private val mode get() = intent.getStringExtra("mode") ?: "ideas"

    private fun render() {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(40), dp(16), dp(32)) }
        when (mode) { "target" -> target(col); else -> ideas(col) }
        setContentView(ScrollView(this).apply { setBackgroundColor(RunUi.BG); addView(col, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)) })
    }

    // ── Meal ideas ──
    private fun ideas(col: LinearLayout) {
        val ctx = this
        col.addView(RunUi.text(this, "Meal ideas", Color.WHITE, 26f, true))
        col.addView(RunUi.text(this, "20% protein · 60% carbs · 20% fat — the runner's ratio.", RunUi.MUTED, 12f))
        col.addView(android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, dp(4))
                (listOf("all" to "All") + listOf("breakfast", "lunch", "dinner", "snack").map { it to (Nutrition.MEAL_NAMES[it] ?: it) }).forEach { (k, n) ->
                    addView(RunUi.text(ctx, n, if (k == filter) Color.BLACK else Color.WHITE, 13f, true).apply {
                        background = RunUi.rounded(if (k == filter) RunUi.ACCENT else RunUi.CARD, dp(14).toFloat()); setPadding(dp(12), dp(6), dp(12), dp(6))
                        setOnClickListener { filter = k; render() }
                    }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(6) })
                }
            })
        })
        MealIdeas.READY.filter { filter == "all" || it.meal == filter }.forEach { m ->
            col.addView(ideaCard((Nutrition.MEAL_NAMES[m.meal] ?: m.meal) + " · " + m.label, m.parts.map { "${it.name} — ${it.amount}" },
                m.kcal, m.prot, m.carbs, m.fat, m.meal) { meal -> m.items(meal) })
        }
        // From your own foods.
        col.addView(RunUi.section(this, "FROM YOUR FOODS"))
        val mine = MealIdeas.personal(FoodLibrary.mine(this))
        if (mine.isEmpty()) col.addView(RunUi.text(this, "No close matches yet — your food database (favorites and healthy foods) is used to build 20/60/20 combinations.", RunUi.MUTED, 12f))
        mine.forEach { items ->
            col.addView(ideaCard("⭐ " + items.joinToString(" + ") { it.name }, items.map { "${it.name} — ${it.servingLabel}" },
                items.sumOf { it.cal ?: 0.0 }, items.sumOf { it.prot ?: 0.0 }, items.sumOf { it.carbs ?: 0.0 }, items.sumOf { it.fat ?: 0.0 }, null) { meal ->
                items.map { it.copy(id = Food.newId(), meal = meal, servings = 1.0) } })
        }
    }

    private fun ideaCard(title: String, lines: List<String>, kcal: Double, p: Double, c: Double, f: Double, meal: String?, make: (String) -> List<Food.Item>) =
        RunUi.card(this).apply {
            val ctx = this@NutritionToolsActivity
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(RunUi.text(ctx, title, Color.WHITE, 14f, true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(RunUi.text(ctx, "${Math.round(kcal)} kcal", RunUi.MUTED, 12f))
            })
            lines.forEach { addView(RunUi.text(ctx, "• $it", RunUi.MUTED, 12f)) }
            val (pp, cp, fp) = MealIdeas.pct(kcal, p, c, f)
            addView(RunUi.text(ctx, "P ${Math.round(p)}g ($pp%) · C ${Math.round(c)}g ($cp%) · F ${Math.round(f)}g ($fp%)", 0xFF5FD38D.toInt(), 12f, true).apply { setPadding(0, dp(4), 0, dp(6)) })
            addView(RunUi.button(ctx, "+ Log to today's " + (meal?.let { Nutrition.MEAL_NAMES[it] } ?: "…"), RunUi.ACCENT) {
                fun log(m: String) {
                    make(m).forEach { HydrationSync.food(ctx, "foodAdd", it) }
                    Toast.makeText(ctx, "Logged to ${Nutrition.MEAL_NAMES[m] ?: m}", Toast.LENGTH_SHORT).show(); finish()
                }
                if (meal != null) log(meal)
                else android.app.AlertDialog.Builder(ctx).setTitle("Which meal?")
                    .setItems(Nutrition.MEAL_ORDER.map { Nutrition.MEAL_NAMES[it] }.toTypedArray()) { _, i -> log(Nutrition.MEAL_ORDER[i]) }.show()
            })
        }

    // ── Daily calorie target: breakdown and settings (from the Today card's Target line) ──
    private fun target(col: LinearLayout) {
        val ctx = this
        fun dp(v: Int) = RunUi.dp(ctx, v)
        val days = NutritionStore.days(this)
        val t = Nutrition.today(days)
        // DAILY CALORIE TARGET — same settings and math as the Command Center (settings shared both ways).
        run {
            val CT = io.github.michealsizemore1.beyondstride.CalorieTarget
            val st = CalorieTargets.settings(ctx)
            val r = CalorieTargets.result(ctx, days)
            col.addView(RunUi.text(this, "Daily calorie target", Color.WHITE, 26f, true))
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
                    if (r.otherActive > 0) line("Other activities:", "+${CT.kcal(r.otherActive)} kcal from other activities logged", 0xFFB5D99C.toInt())
                    val logged = t?.cal ?: 0
                    line("Logged so far:", "${CT.kcal(logged)} kcal · " + if (logged > r.target) "Over target: ${CT.kcal(logged - r.target)} kcal" else "Remaining: ${CT.kcal(r.target - logged)} kcal",
                        if (logged > r.target) 0xFFFF8A80.toInt() else Color.WHITE)
                    if (!r.manualBase && r.weight > 0) addView(RunUi.text(ctx, "Automatic rest-day target uses your most recent weight of ${String.format(Locale.US, "%.1f", r.weight)} lb.", RunUi.DIM, 11f).apply { setPadding(0, dp(4), 0, 0) })
                })
                addView(RunUi.text(ctx, "Estimate only. It uses your runs and current weight when available; medical conditions and individual metabolism can change your needs.", RunUi.DIM, 11f).apply { setPadding(0, dp(6), 0, 0) })
            })
        }

    }
}
