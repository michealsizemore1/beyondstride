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
    /** A micronutrient: value vs target (and a ceiling for sodium / sugar / caffeine). */
    data class Micro(val name: String, val field: String, val value: Double, val target: Double, val max: Double, val unit: String) {
        val pct get() = if (target > 0) value / target * 100 else 0.0
        /** Low / Close / Met / High — same as the Command Center. */
        val status get() = when { max > 0 && value > max -> "High"; pct >= 100 -> "Met"; pct >= 75 -> "Close"; else -> "Low" }
        fun shown(): String {
            val v = if (unit == "mg" && value >= 100) String.format(java.util.Locale.US, "%,d", value.roundToInt())
                else if (value >= 10) value.roundToInt().toString() else String.format(java.util.Locale.US, "%.1f", value)
            return "$v$unit"
        }
    }
    data class Day(
        val date: String, val cal: Int, val prot: Double, val carbs: Double, val fat: Double,
        val fiber: Double, val sodium: Int, val waterOz: Int, val target: Int, val range: Int, val active: Int,
        val meals: Map<String, Int>, val items: List<Item>, val count: Int, val updated: Long,
        val micros: List<Micro> = emptyList(), val microScore: Double = 0.0,
        /** Fluid breakdown (oz): plain water, sports drinks, other drinks (caffeine excluded). waterOz is their total. */
        val water: Int = 0, val sportsOz: Int = 0, val otherOz: Int = 0,
        /** Wake-up and post-run weight (lb), 0 when not entered. */
        val weight: Double = 0.0, val postWeight: Double = 0.0,
        /** Every food with its serving and per-serving values (for the food log). */
        val foods: List<Food.Item> = emptyList(),
        /** Training that day, for food quality (from the Command Center): a run day, and which hard workout ("long", "tempo"…). */
        val qRun: Boolean = false, val qHard: String = "",
        /** Daily Macro Ratio target (% protein / carbs / fat), its label and calorie goal — from the Command Center. */
        val mtProt: Int = 0, val mtCarbs: Int = 0, val mtFat: Int = 0, val mtLabel: String = "", val mtCal: Int = 0,
        /** Meal notes, the fueling note (hard-workout days) and pre-run meal ideas (JSON) — from the Command Center. */
        val notes: Map<String, String> = emptyMap(), val perfNote: String = "", val prerun: String = "",
    ) {
        fun microLabel() = when { microScore >= 9 -> "Excellent"; microScore >= 7 -> "Good"; microScore >= 5 -> "Fair"; microScore >= 3 -> "Low"; else -> "Poor" }
        /** Lowest first (Low, then High, Close, Met), like the Command Center's breakdown. */
        fun microsByNeed() = micros.sortedWith(compareBy({ mapOf("Low" to 0, "High" to 1, "Close" to 2, "Met" to 3)[it.status] }, { it.pct }))
        val left get() = if (target > 0) target - cal else 0
        data class MacroTile(val name: String, val actual: Double, val goal: Int, val remaining: Int, val status: String)
        /** The Command Center's Daily Macro Ratio tiles (null until it has shared today's target). */
        fun macroTiles(): List<MacroTile>? {
            if (mtProt + mtCarbs + mtFat == 0) return null
            val p = prot * 4; val c = carbs * 4; val f = fat * 9; val t = p + c + f
            val kcal = if (mtCal > 0) mtCal else target
            fun tile(n: String, kc: Double, grams: Double, goal: Int, per: Int): MacroTile {
                val a = if (t > 0) kc / t * 100 else 0.0; val diff = a - goal
                return MacroTile(n, a, goal, Math.max(0, Math.round(kcal * (goal / 100.0) / per - grams).toInt()), if (diff < -3) "Low" else if (diff > 3) "High" else "On target")
            }
            return listOf(tile("Protein", p, prot, mtProt, 4), tile("Carbs", c, carbs, mtCarbs, 4), tile("Fat", f, fat, mtFat, 9))
        }
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
        // Full foods: saved here as "foods"; from the Command Center as each item's "x".
        val foods = o.optJSONArray("foods")?.let { Food.parseList(it) } ?: run {
            val a = o.optJSONArray("items")
            if (a == null) emptyList() else (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { it ->
                it.optJSONObject("x")?.let { x -> Food.fromJson(x, null) }
                    ?: Food.Item("old$i", it.optString("meal", "snack"), it.optString("name"), "1 serving", 1.0,
                        it.optDouble("cal", 0.0), it.optDouble("prot", 0.0), it.optDouble("fat", 0.0), it.optDouble("carbs", 0.0))
            } }
        }
        val micros = ArrayList<Micro>()
        o.optJSONArray("micros")?.let { a ->
            for (i in 0 until a.length()) a.optJSONObject(i)?.let {
                micros += Micro(it.optString("n"), it.optString("f"), it.optDouble("v", 0.0), it.optDouble("t", 0.0), it.optDouble("mx", 0.0), it.optString("u"))
            }
        }
        return Day(date, o.optInt("cal"), o.optDouble("prot", 0.0), o.optDouble("carbs", 0.0), o.optDouble("fat", 0.0),
            o.optDouble("fiber", 0.0), o.optInt("sodium"), o.optInt("waterOz"), o.optInt("target"), o.optInt("range"), o.optInt("active"),
            meals, items, o.optInt("count", items.size), o.optLong("updated"), micros, o.optDouble("microScore", 0.0),
            o.optInt("water"), o.optInt("sportsOz"), o.optInt("otherOz"), o.optDouble("weight", 0.0), o.optDouble("postWeight", 0.0), foods, o.optBoolean("qrun"), o.optString("qhard"),
            o.optJSONObject("mt")?.optInt("prot") ?: 0, o.optJSONObject("mt")?.optInt("carbs") ?: 0, o.optJSONObject("mt")?.optInt("fat") ?: 0,
            o.optJSONObject("mt")?.optString("label") ?: "", o.optJSONObject("mt")?.optInt("cal") ?: 0,
            o.optJSONObject("notes")?.let { n -> n.keys().asSequence().associateWith { n.optString(it) } } ?: emptyMap(), o.optString("perfNote"), o.optJSONObject("prerun")?.toString() ?: "")
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
                .put("items", JSONArray().apply { d.items.forEach { put(JSONObject().put("meal", it.meal).put("name", it.name).put("cal", it.cal).put("prot", it.prot).put("carbs", it.carbs).put("fat", it.fat)) } })
                .put("microScore", d.microScore)
                .put("foods", Food.listJson(d.foods))
                .put("water", d.water).put("sportsOz", d.sportsOz).put("otherOz", d.otherOz).put("weight", d.weight).put("postWeight", d.postWeight).put("qrun", d.qRun).put("qhard", d.qHard)
                .put("notes", JSONObject().apply { d.notes.forEach { (k, v) -> put(k, v) } }).put("perfNote", d.perfNote).apply { if (d.prerun.isNotEmpty()) put("prerun", JSONObject(d.prerun)) }
                .put("mt", JSONObject().put("prot", d.mtProt).put("carbs", d.mtCarbs).put("fat", d.mtFat).put("label", d.mtLabel).put("cal", d.mtCal))
                .put("micros", JSONArray().apply { d.micros.forEach { put(JSONObject().put("n", it.name).put("f", it.field).put("v", it.value).put("t", it.target).put("mx", it.max).put("u", it.unit)) } }))
        }
    }.toString()

    fun today(days: List<Day>, date: String = Readiness.today()) = days.lastOrNull { it.date == date }
    fun yesterday(days: List<Day>, date: String = Readiness.today()) =
        days.lastOrNull { it.date == java.time.LocalDate.parse(date).minusDays(1).toString() }

    const val MET = 0xFF5FD38D.toInt(); const val CLOSE = 0xFFF2C94C.toInt(); const val LOW = 0xFFFF8A80.toInt(); const val HIGH = 0xFFEF4444.toInt()
    fun statusColor(s: String) = when (s) { "Met" -> MET; "Close" -> CLOSE; "High" -> HIGH; else -> LOW }

    /**
     * Post-run rehydration, same as the Command Center: weight lost (wake-up minus post-run) × 20 oz per lb
     * (ACSM: 16–24 oz per lb lost), minus the fluids had today. Null when both weights aren't entered.
     */
    data class Rehydrate(val lostLb: Double, val targetOz: Int, val hadOz: Int, val needOz: Int)
    fun rehydrate(d: Day?): Rehydrate? {
        if (d == null || d.weight <= 0 || d.postWeight <= 0 || d.postWeight >= d.weight) return null
        val lost = d.weight - d.postWeight
        val target = (lost * 20).roundToInt()
        return Rehydrate(lost, target, d.waterOz, (target - d.waterOz).coerceAtLeast(0))
    }
    /** "Water 24 · Sports 8 · Other 0 oz" (or just the total for older shares). */
    fun fluids(d: Day) = if (d.water + d.sportsOz + d.otherOz > 0) "Water ${d.water} · Sports ${d.sportsOz} · Other ${d.otherOz} oz" else "${d.waterOz} oz"
    const val WATER = 0xFF4FC3F7.toInt()

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
