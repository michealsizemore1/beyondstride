package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.github.michealsizemore1.beyondstride.Food
import io.github.michealsizemore1.beyondstride.Nutrition
import kotlin.math.roundToInt

/** Dialogs for the food log: change a logged food, confirm one to add, enter one by hand. */
object FoodUi {
    private fun dp(a: Activity, v: Int) = RunUi.dp(a, v)

    private fun per(f: Food.Item, s: Double): String {
        fun g(v: Double?) = ((v ?: 0.0) * s).roundToInt()
        return "${g(f.cal)} kcal · P ${g(f.prot)} g · C ${g(f.carbs)} g · F ${g(f.fat)} g"
    }

    /** −½ / servings / +½ row; returns the field. [onChange] gets the new number. */
    private fun servingsRow(a: Activity, box: LinearLayout, start: Double, onChange: (Double) -> Unit): EditText {
        val field = EditText(a).apply {
            setText(Food.fmt(start)); gravity = Gravity.CENTER; textSize = 18f
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(e: android.text.Editable?) { e?.toString()?.toDoubleOrNull()?.takeIf { it > 0 }?.let(onChange) }
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            })
        }
        fun step(d: Double) { val v = ((field.text.toString().toDoubleOrNull() ?: start) + d).coerceAtLeast(0.25); field.setText(Food.fmt(Math.round(v * 4) / 4.0)) }
        box.addView(LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(TextView(a).apply { text = "Servings"; textSize = 15f }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(android.widget.Button(a).apply { text = "−½"; setOnClickListener { step(-0.5) } }, LinearLayout.LayoutParams(dp(a, 58), WRAP_CONTENT))
            addView(field, LinearLayout.LayoutParams(dp(a, 72), WRAP_CONTENT))
            addView(android.widget.Button(a).apply { text = "+½"; setOnClickListener { step(0.5) } }, LinearLayout.LayoutParams(dp(a, 58), WRAP_CONTENT))
        })
        return field
    }

    /** Meal picker as a row of choices; returns a getter. */
    private fun mealPicker(a: Activity, box: LinearLayout, start: String): () -> String {
        var chosen = start
        val group = android.widget.RadioGroup(a).apply { orientation = android.widget.RadioGroup.VERTICAL }
        Nutrition.MEAL_ORDER.forEachIndexed { i, m ->
            group.addView(android.widget.RadioButton(a).apply {
                id = 1000 + i; text = Nutrition.MEAL_NAMES[m]; isChecked = m == start
                setOnClickListener { chosen = m }
            })
        }
        box.addView(TextView(a).apply { text = "Meal"; textSize = 13f; setPadding(0, dp(a, 10), 0, 0) })
        box.addView(group)
        return { chosen }
    }

    private fun box(a: Activity) = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(a, 22), dp(a, 8), dp(a, 22), 0) }

    /** A logged food: servings, meal, delete. */
    fun edit(a: Activity, f: Food.Item, done: () -> Unit) {
        val b = box(a)
        b.addView(TextView(a).apply { text = f.servingLabel + " per serving"; textSize = 13f })
        val total = TextView(a).apply { text = per(f, f.servings); textSize = 14f; setPadding(0, dp(a, 4), 0, dp(a, 6)) }
        b.addView(total)
        val field = servingsRow(a, b, f.servings) { total.text = per(f, it) }
        val meal = mealPicker(a, b, f.meal)
        AlertDialog.Builder(a).setTitle(f.name).setView(ScrollView(a).apply { addView(b) })
            .setPositiveButton("Save") { _, _ ->
                val s = field.text.toString().toDoubleOrNull()?.takeIf { it > 0 } ?: f.servings
                if (s != f.servings || meal() != f.meal) HydrationSync.food(a, "foodEdit", f.copy(servings = s, meal = meal()))
                done()
            }
            .setNeutralButton("Delete") { _, _ ->
                AlertDialog.Builder(a).setMessage("Remove ${f.name}?").setPositiveButton("Remove") { _, _ -> HydrationSync.food(a, "foodDel", f); done() }
                    .setNegativeButton("Keep", null).show()
            }
            .setNegativeButton("Cancel", null).show()
    }

    /** A food chosen on the Add screen: how many servings, then add. */
    fun confirmAdd(a: Activity, f: Food.Item, meal: String, done: () -> Unit) {
        val b = box(a)
        b.addView(TextView(a).apply { text = f.servingLabel + " per serving"; textSize = 13f })
        val total = TextView(a).apply { text = per(f, f.servings); textSize = 14f; setPadding(0, dp(a, 4), 0, dp(a, 6)) }
        b.addView(total)
        val field = servingsRow(a, b, f.servings) { total.text = per(f, it) }
        val micro = f.micros.entries.filter { it.value > 0 }.joinToString(" · ") { "${it.key} ${Food.fmt(it.value)}" }
        if (micro.isNotEmpty()) b.addView(TextView(a).apply { text = micro; textSize = 11f; setPadding(0, dp(a, 6), 0, 0) })
        AlertDialog.Builder(a).setTitle(f.name).setView(ScrollView(a).apply { addView(b) })
            .setPositiveButton("Add to ${Nutrition.MEAL_NAMES[meal] ?: meal}") { _, _ ->
                val s = field.text.toString().toDoubleOrNull()?.takeIf { it > 0 } ?: f.servings
                HydrationSync.food(a, "foodAdd", f.copy(id = Food.newId(), meal = meal, servings = s))
                Toast.makeText(a, "Added ${f.name}", Toast.LENGTH_SHORT).show()
                done()
            }
            .setNegativeButton("Cancel", null).show()
    }

    /** Type a food in: name, serving and the numbers from the label. */
    fun manual(a: Activity, meal: String, done: () -> Unit) {
        val b = box(a)
        fun field(hint: String, number: Boolean) = EditText(a).apply {
            this.hint = hint
            inputType = if (number) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }.also { b.addView(it) }
        val name = field("Food name", false)
        val serving = field("Serving (e.g. 1 cup, 30 g)", false)
        val servings = field("Servings (1)", true)
        val cal = field("Calories", true); val prot = field("Protein g", true); val carbs = field("Carbs g", true); val fat = field("Fat g", true)
        b.addView(TextView(a).apply { text = "Optional"; textSize = 12f; setPadding(0, dp(a, 8), 0, 0) })
        val fiber = field("Fiber g", true); val sugar = field("Sugar g", true); val sodium = field("Sodium mg", true)
        val potassium = field("Potassium mg", true); val caffeine = field("Caffeine mg", true)
        AlertDialog.Builder(a).setTitle("Add to ${Nutrition.MEAL_NAMES[meal] ?: meal}").setView(ScrollView(a).apply { addView(b) })
            .setPositiveButton("Add") { _, _ ->
                val n = name.text.toString().trim()
                if (n.isEmpty()) { Toast.makeText(a, "Give the food a name", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                fun num(e: EditText) = e.text.toString().trim().toDoubleOrNull()
                val micros = HashMap<String, Double>()
                mapOf("fiber" to fiber, "sugar" to sugar, "sodium" to sodium, "potassium" to potassium, "caffeine" to caffeine).forEach { (k, e) -> num(e)?.let { micros[k] = it } }
                val item = Food.Item(Food.newId(), meal, n, serving.text.toString().trim().ifEmpty { "1 serving" }, num(servings)?.takeIf { it > 0 } ?: 1.0,
                    num(cal), num(prot), num(fat), num(carbs), micros)
                HydrationSync.food(a, "foodAdd", item)
                Toast.makeText(a, "Added $n", Toast.LENGTH_SHORT).show()
                done()
            }
            .setNegativeButton("Cancel", null).show()
    }

    /** The meal that fits the time of day (for the tiles' shortcuts). */
    fun mealNow() = when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
        in 4..10 -> "breakfast"; in 11..14 -> "lunch"; in 17..21 -> "dinner"; else -> "snack" }

    /** Copy Day: the foods from one day (all, or one meal) onto today or tomorrow. */
    fun copyDay(a: Activity, done: () -> Unit) {
        val days = io.github.michealsizemore1.beyondstride.NutritionStore.days(a).filter { it.foods.isNotEmpty() }.sortedByDescending { it.date }
        if (days.isEmpty()) { Toast.makeText(a, "No logged days to copy from yet", Toast.LENGTH_SHORT).show(); return }
        val today = java.time.LocalDate.now()
        fun label(d: String) = java.time.LocalDate.parse(d).let { dd ->
            when (dd) { today -> "Today"; today.minusDays(1) -> "Yesterday"; today.plusDays(1) -> "Tomorrow"
                else -> dd.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() } + " " + dd.monthValue + "/" + dd.dayOfMonth } }
        val b = box(a)
        fun spinner(title: String, items: List<String>, start: Int) = android.widget.Spinner(a).apply {
            adapter = android.widget.ArrayAdapter(a, android.R.layout.simple_spinner_dropdown_item, items); setSelection(start)
        }.also { b.addView(TextView(a).apply { text = title; textSize = 13f; setPadding(0, dp(a, 8), 0, 0) }); b.addView(it) }
        val fromIdx = days.indexOfFirst { it.date != today.toString() }.coerceAtLeast(0)
        val from = spinner("Copy from", days.map { label(it.date) + " · ${it.count} foods · ${Nutrition.kcal(it.cal)} kcal" }, fromIdx)
        val meals = listOf("all") + Nutrition.MEAL_ORDER
        val meal = spinner("Which foods", meals.map { if (it == "all") "Everything" else Nutrition.MEAL_NAMES[it] ?: it }, 0)
        val toDates = listOf(today.toString(), today.plusDays(1).toString())
        val to = spinner("Copy to", toDates.map { label(it) }, 0)
        AlertDialog.Builder(a).setTitle("Copy food between days").setView(ScrollView(a).apply { addView(b) })
            .setPositiveButton("Copy") { _, _ ->
                val src = days[from.selectedItemPosition]; val m = meals[meal.selectedItemPosition]; val dest = toDates[to.selectedItemPosition]
                val items = src.foods.filter { m == "all" || it.meal == m }
                if (items.isEmpty()) { Toast.makeText(a, "Nothing in that meal", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                items.forEach { HydrationSync.food(a, "foodAdd", it.copy(id = Food.newId()), dest) }
                Toast.makeText(a, "Copied ${items.size} food${if (items.size == 1) "" else "s"} to ${label(dest).lowercase()}", Toast.LENGTH_SHORT).show()
                done()
            }.setNegativeButton("Cancel", null).show()
    }
}
