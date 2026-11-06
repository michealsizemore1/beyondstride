package io.github.michealsizemore1.beyondstride

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Daily nutrition from the My Life Command Center (its Nutrition tab shares each day's totals).
 * The phone reads them from Firebase and passes them to the watch; this file is the shared model.
 */
object Nutrition {
    data class Item(val meal: String, val name: String, val cal: Int, val prot: Double, val carbs: Double, val fat: Double)
    data class Day(
        val date: String, val cal: Int, val prot: Double, val carbs: Double, val fat: Double,
        val fiber: Double, val sodium: Int, val waterOz: Int, val target: Int, val range: Int, val active: Int,
        val meals: Map<String, Int>, val items: List<Item>, val count: Int, val updated: Long,
    ) {
        val left get() = if (target > 0) target - cal else 0
        /** Share of the day's calories from protein / carbs / fat (%). */
        fun ratios(): Triple<Int, Int, Int> {
            val p = prot * 4; val c = carbs * 4; val f = fat * 9; val t = p + c + f
            return if (t <= 0) Triple(0, 0, 0) else Triple((p / t * 100).roundToInt(), (c / t * 100).roundToInt(), (f / t * 100).roundToInt())
        }
    }

    val MEAL_ORDER = listOf("breakfast", "lunch", "dinner", "snack", "liquids", "sports", "supplements")
    val MEAL_NAMES = mapOf("breakfast" to "Breakfast", "lunch" to "Lunch", "dinner" to "Dinner", "snack" to "Snacks",
        "liquids" to "Liquids", "sports" to "Sports nutrition", "supplements" to "Supplements")

    private fun day(date: String, o: JSONObject): Day {
        val meals = HashMap<String, Int>()
        o.optJSONObject("meals")?.let { m -> m.keys().forEach { k -> meals[k] = m.optInt(k) } }
        val items = ArrayList<Item>()
        o.optJSONArray("items")?.let { a ->
            for (i in 0 until a.length()) a.optJSONObject(i)?.let {
                items += Item(it.optString("meal"), it.optString("name"), it.optInt("cal"), it.optDouble("prot", 0.0), it.optDouble("carbs", 0.0), it.optDouble("fat", 0.0))
            }
        }
        return Day(date, o.optInt("cal"), o.optDouble("prot", 0.0), o.optDouble("carbs", 0.0), o.optDouble("fat", 0.0),
            o.optDouble("fiber", 0.0), o.optInt("sodium"), o.optInt("waterOz"), o.optInt("target"), o.optInt("range"), o.optInt("active"),
            meals, items, o.optInt("count", items.size), o.optLong("updated"))
    }

    /** Firebase's {"2026-11-06": {...}, ...} (or null) → days, oldest first. */
    fun parse(s: String?): List<Day> = try {
        val o = JSONObject(if (s.isNullOrBlank() || s == "null") "{}" else s)
        o.keys().asSequence().filter { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }.map { day(it, o.getJSONObject(it)) }.sortedBy { it.date }.toList()
    } catch (e: Exception) { emptyList() }

    fun json(days: List<Day>): String = JSONObject().apply {
        days.forEach { d ->
            put(d.date, JSONObject().put("cal", d.cal).put("prot", d.prot).put("carbs", d.carbs).put("fat", d.fat).put("fiber", d.fiber)
                .put("sodium", d.sodium).put("waterOz", d.waterOz).put("target", d.target).put("range", d.range).put("active", d.active)
                .put("count", d.count).put("updated", d.updated)
                .put("meals", JSONObject().apply { d.meals.forEach { (k, v) -> put(k, v) } })
                .put("items", JSONArray().apply { d.items.forEach { put(JSONObject().put("meal", it.meal).put("name", it.name).put("cal", it.cal).put("prot", it.prot).put("carbs", it.carbs).put("fat", it.fat)) } }))
        }
    }.toString()

    fun today(days: List<Day>, date: String = Readiness.today()) = days.lastOrNull { it.date == date }
    fun yesterday(days: List<Day>, date: String = Readiness.today()) =
        days.lastOrNull { it.date == java.time.LocalDate.parse(date).minusDays(1).toString() }

    fun kcal(v: Int) = String.format(java.util.Locale.US, "%,d", v)

    /** One line for cards: "1,240 of 2,600 kcal · 1,360 left". */
    fun line(d: Day): String = if (d.target > 0)
        "${kcal(d.cal)} of ${kcal(d.target)} kcal · " + (if (d.left >= 0) "${kcal(d.left)} left" else "${kcal(-d.left)} over")
    else "${kcal(d.cal)} kcal"

    fun macros(d: Day) = "P ${d.prot.roundToInt()}g · C ${d.carbs.roundToInt()}g · F ${d.fat.roundToInt()}g"

    /**
     * Readiness "Fuel" part from yesterday: eating well under the target on a training day slows recovery.
     * Returns points 0–100 and a note, or null when there's no target to compare with.
     */
    fun fuelScore(y: Day?): Pair<Int, String>? {
        if (y == null || y.target <= 0 || y.count == 0) return null
        val r = y.cal.toDouble() / y.target
        return when {
            r < 0.7 -> 35 to "ate ${(100 - r * 100).roundToInt()}% under target yesterday"
            r < 0.85 -> 60 to "a bit under target yesterday"
            r <= 1.15 -> 95 to "on target yesterday"
            r <= 1.3 -> 80 to "a bit over target yesterday"
            else -> 70 to "well over target yesterday"
        }
    }
}
