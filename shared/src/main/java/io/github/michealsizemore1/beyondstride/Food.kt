package io.github.michealsizemore1.beyondstride

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Foods in the log. Values are per serving (like the Command Center); [servings] multiplies them.
 * [summarize] turns a day's foods into the same totals, meals and micronutrient score the Command Center shows.
 */
object Food {
    val MICROS = listOf("fiber", "sugar", "sodium", "vitC", "vitD", "calcium", "iron", "potassium", "caffeine", "magnesium", "vitB12", "zinc")

    data class Item(
        val id: String, val meal: String, val name: String, val servingLabel: String, val servings: Double,
        val cal: Double?, val prot: Double?, val fat: Double?, val carbs: Double?,
        val micros: Map<String, Double> = emptyMap(), val barcode: String = "",
    ) {
        fun total(v: Double?) = (v ?: 0.0) * servings
        val kcal get() = total(cal).roundToInt()
        fun line() = "${fmt(servings)} × $servingLabel · ${kcal} kcal"
    }

    fun fmt(v: Double) = if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(java.util.Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')

    fun newId() = "f" + System.currentTimeMillis() + "_" + (Math.random() * 1_679_616).toInt().toString(36)

    private fun d(o: JSONObject, k: String): Double? = if (o.has(k) && !o.isNull(k)) o.optDouble(k).takeIf { !it.isNaN() } else null

    fun fromJson(o: JSONObject, meal: String? = null): Item {
        val m = HashMap<String, Double>()
        MICROS.forEach { k -> d(o, k)?.let { m[k] = it } }
        return Item(o.optString("id").ifEmpty { newId() }, meal ?: o.optString("meal", "snack").ifEmpty { "snack" },
            o.optString("name", "Food"), o.optString("servingLabel", "1 serving").ifEmpty { "1 serving" },
            o.optDouble("servings", 1.0).takeIf { !it.isNaN() && it > 0 } ?: 1.0,
            d(o, "cal"), d(o, "prot"), d(o, "fat"), d(o, "carbs"), m, o.optString("barcode"))
    }

    fun toJson(i: Item, withId: Boolean = true): JSONObject = JSONObject().apply {
        if (withId) { put("id", i.id); put("meal", i.meal) }
        put("name", i.name); put("servingLabel", i.servingLabel); put("servings", i.servings)
        i.cal?.let { put("cal", it) }; i.prot?.let { put("prot", it) }; i.fat?.let { put("fat", it) }; i.carbs?.let { put("carbs", it) }
        i.micros.forEach { (k, v) -> put(k, v) }
        if (i.barcode.isNotEmpty()) put("barcode", i.barcode)
    }

    fun listJson(items: List<Item>) = JSONArray().apply { items.forEach { put(toJson(it)) } }
    fun parseList(a: JSONArray?): List<Item> = if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let { o -> fromJson(o) } }

    private fun r1(v: Double) = (v * 10).roundToInt() / 10.0

    /**
     * The day rebuilt from [items] — totals, meals, micronutrients and score — keeping [base]'s target,
     * fluids and weights. Same math as the Command Center's Nutrition tab.
     */
    fun summarize(base: Nutrition.Day, items: List<Item>): Nutrition.Day {
        fun sum(f: (Item) -> Double?) = items.sumOf { it.total(f(it)) }
        fun micro(k: String) = items.sumOf { it.total(it.micros[k]) }
        val cal = sum { it.cal }.roundToInt(); val prot = r1(sum { it.prot }); val carbs = r1(sum { it.carbs }); val fat = r1(sum { it.fat })
        val fiber = r1(micro("fiber")); val sodium = micro("sodium").roundToInt()
        val meals = HashMap<String, Int>()
        items.forEach { meals[it.meal] = (meals[it.meal] ?: 0) + it.total(it.cal).roundToInt() }
        val sportsSugar = items.filter { it.meal == "sports" }.sumOf { it.total(it.micros["sugar"]) }
        val sugarCeiling = (36 + sportsSugar).roundToInt().toDouble()
        val list = listOf(
            Nutrition.Micro("Iron", "iron", r1(micro("iron")), 14.0, 0.0, "mg"),
            Nutrition.Micro("Calcium", "calcium", r1(micro("calcium")), 1000.0, 0.0, "mg"),
            Nutrition.Micro("Vitamin D", "vitD", r1(micro("vitD")), 15.0, 0.0, "mcg"),
            Nutrition.Micro("Magnesium", "magnesium", r1(micro("magnesium")), 483.0, 0.0, "mg"),
            Nutrition.Micro("Vitamin B12", "vitB12", r1(micro("vitB12")), 2.4, 0.0, "mcg"),
            Nutrition.Micro("Sodium", "sodium", sodium.toDouble(), 2300.0, 4000.0, "mg"),
            Nutrition.Micro("Potassium", "potassium", r1(micro("potassium")), 3400.0, 0.0, "mg"),
            Nutrition.Micro("Zinc", "zinc", r1(micro("zinc")), 15.0, 0.0, "mg"),
            Nutrition.Micro("Vitamin C", "vitC", r1(micro("vitC")), 90.0, 0.0, "mg"),
            Nutrition.Micro("Fiber", "fiber", fiber, 28.0, 0.0, "g"),
            Nutrition.Micro("Sugar", "sugar", r1(micro("sugar")), sugarCeiling, sugarCeiling, "g"),
            Nutrition.Micro("Caffeine", "caffeine", micro("caffeine").roundToInt().toDouble(), 400.0, 400.0, "mg"),
        )
        val score = if (items.isEmpty()) 0.0 else {
            val comp = list.map { m -> if (m.max > 0 && m.value > m.max) maxOf(0.0, 1 - (m.value - m.max) / m.max) else maxOf(0.0, minOf(1.0, m.value / m.target)) }
            r1(1 + comp.average() * 9)
        }
        val shortItems = items.take(80).map { Nutrition.Item(it.meal, it.name, it.total(it.cal).roundToInt(), r1(it.total(it.prot)), r1(it.total(it.carbs)), r1(it.total(it.fat))) }
        return base.copy(cal = cal, prot = prot, carbs = carbs, fat = fat, fiber = fiber, sodium = sodium, meals = meals, items = shortItems,
            count = items.size, micros = list, microScore = score, foods = items)
    }
}
