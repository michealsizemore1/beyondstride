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
    /** The food's name, editable (top of the edit / add dialogs). */
    private fun nameField(a: Activity, b: LinearLayout, name: String): android.widget.EditText {
        b.addView(TextView(a).apply { text = "Name"; textSize = 13f })
        return android.widget.EditText(a).apply { setText(name); inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(); b.addView(this) }
    }

    fun edit(a: Activity, f: Food.Item, done: () -> Unit) {
        val b = box(a)
        val nameF = nameField(a, b, f.name)
        val serving = servingPicker(a, b, f.servingLabel)
        val total = TextView(a).apply { text = per(f, f.servings); textSize = 14f; setPadding(0, dp(a, 4), 0, dp(a, 6)) }
        b.addView(total)
        val field = servingsRow(a, b, f.servings) { total.text = per(f, it) }
        val meal = mealPicker(a, b, f.meal)
        // Macro sources (Support / Limit / Unknown) — corrects how food quality and the source mix classify this food.
        val inf = Food.inferSources(f)
        val opts = listOf("support" to "Support", "limit" to "Limit", "unknown" to "Unknown")
        fun srcPicker(label: String, cur: String): Array<String> {
            val sel = arrayOf(cur.ifEmpty { "unknown" })
            b.addView(TextView(a).apply { text = label; textSize = 12f; setPadding(0, dp(a, 8), 0, 0) })
            b.addView(android.widget.Spinner(a).apply {
                adapter = android.widget.ArrayAdapter(a, android.R.layout.simple_spinner_dropdown_item, opts.map { it.second })
                setSelection(opts.indexOfFirst { it.first == sel[0] }.coerceAtLeast(0))
                onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) { sel[0] = opts[pos].first }
                    override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
                }
            })
            return sel
        }
        val ps = if ((f.prot ?: 0.0) > 0) srcPicker("Protein source", inf.proteinSource) else null
        val cs = if ((f.carbs ?: 0.0) > 0) srcPicker("Carb source", inf.carbSource) else null
        val fs = if ((f.fat ?: 0.0) > 0) srcPicker("Fat source", inf.fatSource) else null
        val nutrients = nutrientEditor(a, b, f)
        AlertDialog.Builder(a).setTitle(f.name).setView(ScrollView(a).apply { addView(b) })
            .setPositiveButton("Save") { _, _ ->
                val s = field.text.toString().toDoubleOrNull()?.takeIf { it > 0 } ?: f.servings
                val label = serving().ifEmpty { f.servingLabel }
                val newName = nameF.text.toString().trim().ifEmpty { f.name }
                var upd = nutrients(f).copy(servings = s, meal = meal(), servingLabel = label, name = newName)
                if (newName != f.name) FoodLibrary.rename(a, f.name, newName)
                val srcChanged = (ps != null && ps[0] != inf.proteinSource.ifEmpty { "unknown" }) || (cs != null && cs[0] != inf.carbSource.ifEmpty { "unknown" }) || (fs != null && fs[0] != inf.fatSource.ifEmpty { "unknown" })
                if (srcChanged) upd = upd.copy(proteinSource = ps?.get(0) ?: upd.proteinSource, carbSource = cs?.get(0) ?: upd.carbSource, fatSource = fs?.get(0) ?: upd.fatSource)
                if (upd != f) HydrationSync.food(a, "foodEdit", upd, extra = if (srcChanged) org.json.JSONObject().put("srcSet", true) else null)
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
        val nameF = nameField(a, b, f.name)
        val total = TextView(a).apply { text = per(f, f.servings); textSize = 14f; setPadding(0, dp(a, 4), 0, dp(a, 6)) }
        b.addView(total)
        val field = servingsRow(a, b, f.servings) { total.text = per(f, it) }
        val serving = servingPicker(a, b, f.servingLabel)
        val nutrients = nutrientEditor(a, b, f)
        AlertDialog.Builder(a).setTitle(f.name).setView(ScrollView(a).apply { addView(b) })
            .setPositiveButton("Add to ${Nutrition.MEAL_NAMES[meal] ?: meal}") { _, _ ->
                val s = field.text.toString().toDoubleOrNull()?.takeIf { it > 0 } ?: f.servings
                val newName = nameF.text.toString().trim().ifEmpty { f.name }
                HydrationSync.food(a, "foodAdd", nutrients(f).copy(id = Food.newId(), meal = meal, servings = s, servingLabel = serving().ifEmpty { f.servingLabel }, name = newName))
                Toast.makeText(a, "Added $newName", Toast.LENGTH_SHORT).show()
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

    /** The Command Center's serving units. */
    val UNITS = listOf("serving", "cup", "tablespoon", "teaspoon", "fluid ounce", "ounce", "gram", "milligram", "milliliter", "liter", "piece", "item", "slice",
        "scoop", "packet", "bar", "bottle", "can", "container", "handful", "capsule", "tablet", "softgel", "gummy", "IU", "microgram")

    /**
     * Serving size as amount + unit (like the Command Center), e.g. "20" + "fluid ounce". A label that isn't amount + unit
     * (e.g. "1 Bottle (591 mL)") is kept as "as labeled" until you pick a unit. Shows whether it counts as fluid.
     */
    private fun servingPicker(a: Activity, b: LinearLayout, label: String): () -> String {
        val m = Regex("^\\s*(\\d+(?:\\.\\d+)?)\\s+(.+?)\\s*$").find(label)
        val known = m?.groupValues?.get(2)?.lowercase()?.removeSuffix("s")?.let { u -> UNITS.firstOrNull { it.lowercase() == u } }
        val units = (if (known == null) listOf("as labeled: $label") else emptyList()) + UNITS
        b.addView(TextView(a).apply { text = "Serving size"; textSize = 13f })
        val fluid = TextView(a).apply { textSize = 12f; setPadding(0, dp(a, 2), 0, 0) }
        val amount = EditText(a).apply {
            setText(if (known != null) m!!.groupValues[1] else ""); hint = "amount"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; setSingleLine()
        }
        val unit = android.widget.Spinner(a).apply {
            adapter = android.widget.ArrayAdapter(a, android.R.layout.simple_spinner_dropdown_item, units)
            setSelection(if (known != null) units.indexOf(known) else 0)
        }
        b.addView(LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(amount, LinearLayout.LayoutParams(dp(a, 80), WRAP_CONTENT))
            addView(unit, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        })
        fun current(): String {
            val u = unit.selectedItem?.toString() ?: ""
            if (u.startsWith("as labeled")) return label
            val n = amount.text.toString().trim().ifEmpty { "1" }
            return "$n $u"
        }
        fun show() {
            val oz = Food.fluidOz(current())
            fluid.text = if (oz > 0) "💧 Counts as ${Food.fmt(Math.round(oz * 10) / 10.0)} fl oz per serving (in Liquids or Sports Nutrition)"
                else "For a drink, pick fluid ounce or milliliter so it counts as fluid"
        }
        amount.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(e: android.text.Editable?) = show()
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b2: Int, c: Int) {}
        })
        unit.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                if (!units[pos].startsWith("as labeled") && amount.text.isBlank()) amount.setText("1"); show() }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
        b.addView(fluid); show()
        return { current() }
    }

    private val NUTRIENTS = listOf("cal" to "Calories", "prot" to "Protein g", "carbs" to "Carbs g", "fat" to "Fat g",
        "fiber" to "Fiber g", "sugar" to "Sugar g", "sodium" to "Sodium mg", "potassium" to "Potassium mg", "calcium" to "Calcium mg",
        "iron" to "Iron mg", "magnesium" to "Magnesium mg", "zinc" to "Zinc mg", "vitC" to "Vitamin C mg", "vitD" to "Vitamin D mcg",
        "vitB12" to "Vitamin B12 mcg", "caffeine" to "Caffeine mg")

    /** Every nutrient as a number box (per serving), two per row, like the Command Center's food cells. Returns the edited food. */
    private fun nutrientEditor(a: Activity, b: LinearLayout, f: Food.Item): (Food.Item) -> Food.Item {
        b.addView(TextView(a).apply { text = "Nutrition per serving (tap a number to change it)"; textSize = 13f; setPadding(0, dp(a, 12), 0, 0) })
        fun value(k: String) = when (k) { "cal" -> f.cal; "prot" -> f.prot; "carbs" -> f.carbs; "fat" -> f.fat; else -> f.micros[k] }
        val fields = LinkedHashMap<String, EditText>()
        NUTRIENTS.chunked(2).forEach { row ->
            b.addView(LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL
                row.forEachIndexed { i, (k, n) ->
                    addView(LinearLayout(a).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(TextView(a).apply { text = n; textSize = 11f })
                        addView(EditText(a).apply {
                            value(k)?.let { setText(Food.fmt(Math.round(it * 10) / 10.0)) }; hint = "—"; setSingleLine(); textSize = 15f
                            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                        }.also { fields[k] = it })
                    }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { if (i == 0) marginEnd = dp(a, 12) })
                }
            })
        }
        return { base ->
            fun v(k: String) = fields[k]?.text?.toString()?.trim()?.toDoubleOrNull()
            val micros = HashMap<String, Double>()
            Food.MICROS.forEach { k -> v(k)?.let { micros[k] = it } }
            base.copy(cal = v("cal"), prot = v("prot"), carbs = v("carbs"), fat = v("fat"), micros = micros)
        }
    }
}
