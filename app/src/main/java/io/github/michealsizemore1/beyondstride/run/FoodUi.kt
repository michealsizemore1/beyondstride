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
        // Serving size — editable, so a drink can be given fluid ounces / mL and count toward fluids.
        b.addView(TextView(a).apply { text = "Serving size"; textSize = 13f })
        val fluid = TextView(a).apply { textSize = 12f; setPadding(0, dp(a, 2), 0, 0) }
        fun showFluid(label: String) {
            val oz = Food.fluidOz(label)
            fluid.text = if (oz > 0) "💧 Counts as ${Food.fmt(Math.round(oz * 10) / 10.0)} fl oz per serving (in Liquids or Sports Nutrition)"
                else "For a drink, use fl oz or mL (e.g. 20 fl oz, 591 mL) so it counts as fluid"
        }
        val serving = EditText(a).apply {
            setText(f.servingLabel); setSingleLine(); inputType = InputType.TYPE_CLASS_TEXT
            addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(e: android.text.Editable?) = showFluid(e?.toString() ?: "")
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b2: Int, c: Int) {}
            })
        }
        b.addView(serving)
        b.addView(LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            listOf("8 fl oz", "12 fl oz", "16.9 fl oz", "20 fl oz", "591 mL").forEach { u ->
                addView(TextView(a).apply {
                    text = u; textSize = 12f; setPadding(dp(a, 8), dp(a, 4), dp(a, 8), dp(a, 4))
                    background = RunUi.rounded(0x33FFFFFF, dp(a, 10).toFloat())
                    setOnClickListener { serving.setText(u) }
                }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(a, 4) })
            }
        })
        b.addView(fluid); showFluid(f.servingLabel)
        val total = TextView(a).apply { text = per(f, f.servings); textSize = 14f; setPadding(0, dp(a, 4), 0, dp(a, 6)) }
        b.addView(total)
        val field = servingsRow(a, b, f.servings) { total.text = per(f, it) }
        val meal = mealPicker(a, b, f.meal)
        AlertDialog.Builder(a).setTitle(f.name).setView(ScrollView(a).apply { addView(b) })
            .setPositiveButton("Save") { _, _ ->
                val s = field.text.toString().toDoubleOrNull()?.takeIf { it > 0 } ?: f.servings
                val label = serving.text.toString().trim().ifEmpty { f.servingLabel }
                if (s != f.servings || meal() != f.meal || label != f.servingLabel) HydrationSync.food(a, "foodEdit", f.copy(servings = s, meal = meal(), servingLabel = label))
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

    /** Copy Day: pick a day, tick the foods you want (whole meals or single foods), copy them to today or tomorrow. */
    fun copyDay(a: Activity, done: () -> Unit) {
        val days = io.github.michealsizemore1.beyondstride.NutritionStore.days(a).filter { it.foods.isNotEmpty() }.sortedByDescending { it.date }
        if (days.isEmpty()) { Toast.makeText(a, "No logged days to copy from yet", Toast.LENGTH_SHORT).show(); return }
        val today = java.time.LocalDate.now()
        fun label(d: String) = java.time.LocalDate.parse(d).let { dd ->
            when (dd) { today -> "Today"; today.minusDays(1) -> "Yesterday"; today.plusDays(1) -> "Tomorrow"
                else -> dd.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() } + " " + dd.monthValue + "/" + dd.dayOfMonth } }
        val b = box(a)
        b.addView(TextView(a).apply { text = "Copy from"; textSize = 13f })
        val from = android.widget.Spinner(a).apply {
            adapter = android.widget.ArrayAdapter(a, android.R.layout.simple_spinner_dropdown_item, days.map { label(it.date) + " · ${it.count} foods · ${Nutrition.kcal(it.cal)} kcal" })
            setSelection(days.indexOfFirst { it.date != today.toString() }.coerceAtLeast(0))
        }
        b.addView(from)
        b.addView(TextView(a).apply { text = "Foods to copy (tap a meal to tick all of it)"; textSize = 13f; setPadding(0, dp(a, 10), 0, 0) })
        val list = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        b.addView(list)
        val checks = ArrayList<Pair<android.widget.CheckBox, Food.Item>>()
        fun fill(day: Nutrition.Day) {
            list.removeAllViews(); checks.clear()
            Nutrition.MEAL_ORDER.filter { m -> day.foods.any { it.meal == m } }.forEach { m ->
                val mine = ArrayList<android.widget.CheckBox>()
                list.addView(TextView(a).apply {
                    text = (Nutrition.MEAL_NAMES[m] ?: m) + "  ▸ all"; textSize = 15f; setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(0, dp(a, 8), 0, 0)
                    setOnClickListener { val on = mine.any { !it.isChecked }; mine.forEach { it.isChecked = on } }
                })
                day.foods.filter { it.meal == m }.forEach { f ->
                    val cb = android.widget.CheckBox(a).apply { text = "${f.name}  (${Food.fmt(f.servings)}× · ${f.kcal} kcal)"; textSize = 13f }
                    mine += cb; checks += cb to f; list.addView(cb)
                }
            }
        }
        from.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) = fill(days[pos])
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
        fill(days[from.selectedItemPosition])
        b.addView(TextView(a).apply { text = "Copy to"; textSize = 13f; setPadding(0, dp(a, 10), 0, 0) })
        val toDates = listOf(today.toString(), today.plusDays(1).toString())
        val to = android.widget.Spinner(a).apply { adapter = android.widget.ArrayAdapter(a, android.R.layout.simple_spinner_dropdown_item, toDates.map { label(it) }) }
        b.addView(to)
        AlertDialog.Builder(a).setTitle("Copy food between days").setView(ScrollView(a).apply { addView(b) })
            .setPositiveButton("Copy") { _, _ ->
                val items = checks.filter { it.first.isChecked }.map { it.second }
                if (items.isEmpty()) { Toast.makeText(a, "Tick at least one food", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val dest = toDates[to.selectedItemPosition]
                items.forEach { HydrationSync.food(a, "foodAdd", it.copy(id = Food.newId()), dest) }
                Toast.makeText(a, "Copied ${items.size} food${if (items.size == 1) "" else "s"} to ${label(dest).lowercase()}", Toast.LENGTH_SHORT).show()
                done()
            }.setNegativeButton("Cancel", null).show()
    }
}
