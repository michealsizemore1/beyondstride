package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import io.github.michealsizemore1.beyondstride.Food
import io.github.michealsizemore1.beyondstride.Nutrition
import io.github.michealsizemore1.beyondstride.NutritionStore
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * The Command Center's nutrition logic, done in BeyondStride (same rules, same numbers):
 * hard-workout detection, the periodized pre-fueling / training-day target, the Daily Macro Ratio target
 * (custom per day, 15/60/25 by default, or automatic), the fueling note, Pre-Run Meal Ideas from Healthy 7+ foods,
 * and the run-day context used by food quality.
 */
object NutritionEngine {
    private fun p(ctx: Context) = ctx.getSharedPreferences("nutritionEngine", 0)

    // ── Per-day macro ratio (the Command Center's macro_ratio_<date>): {enabled, prot, carbs, fat} ──
    fun ratio(ctx: Context, date: String): JSONObject = try { JSONObject(p(ctx).getString("ratio_$date", "") ?: "") } catch (e: Exception) { JSONObject() }
        .let { o -> JSONObject().put("enabled", o.optBoolean("enabled", true)).put("prot", o.optInt("prot", 15)).put("carbs", o.optInt("carbs", 60)).put("fat", o.optInt("fat", 25)) }
    fun setRatio(ctx: Context, date: String, x: JSONObject) = p(ctx).edit().putString("ratio_$date", x.toString()).apply()

    /** Text of what's planned / done on [date]: activities that day plus the training plan's workout. */
    private fun dayText(ctx: Context, date: LocalDate): String {
        val ds = date.toString(); val parts = ArrayList<String>()
        ActivityStore.list(ctx).filter { ActivityStore.str(it, "date") == ds }.forEach { a -> parts += listOf("title", "type", "workout").map { ActivityStore.str(a, it) }.filter { it.isNotEmpty() }.joinToString(" ") }
        PlanStore.data(ctx).optJSONObject("current")?.let { pl -> pl.optJSONObject("days")?.optString(ds)?.takeIf { it.isNotEmpty() }?.let { parts += it } }
        return parts.joinToString(" ").lowercase()
    }

    /** "long" / "interval" / "tempo" / "quality" / "" for [date]. */
    fun hardWorkout(ctx: Context, date: LocalDate): String {
        val t = dayText(ctx, date)
        return when {
            Regex("\\blong\\s+run\\b|\\bhalf\\s+marathon\\b|\\bmarathon\\b").containsMatchIn(t) -> "long"
            Regex("\\bintervals?\\b|\\brepeats?\\b|\\bspeed\\s*work\\b|\\btrack\\s+(?:workout|session)\\b|\\bhill\\s+repeats?\\b").containsMatchIn(t) -> "interval"
            Regex("\\btempo\\b|\\bthreshold\\b").containsMatchIn(t) -> "tempo"
            Regex("\\bquality\\b").containsMatchIn(t) -> "quality"
            else -> ""
        }
    }

    data class Preset(val workout: String, val phase: String, val calories: Int, val prot: Int, val carbs: Int, val fat: Int, val label: String)
    private val TABLE = mapOf(
        "long" to mapOf("prior" to intArrayOf(3200, 19, 63, 18), "day" to intArrayOf(3300, 20, 60, 20)),
        "tempo" to mapOf("prior" to intArrayOf(2800, 20, 60, 20), "day" to intArrayOf(2900, 22, 55, 23)),
        "interval" to mapOf("prior" to intArrayOf(2800, 20, 60, 20), "day" to intArrayOf(2950, 21, 56, 23)),
        "quality" to mapOf("prior" to intArrayOf(2800, 20, 60, 20), "day" to intArrayOf(2900, 22, 55, 23)))
    private val NAMES = mapOf("long" to "Long Run", "tempo" to "Tempo Run", "interval" to "Interval Run", "quality" to "Quality Run")

    /** Hard workout today ("day") or tomorrow ("prior"), with its periodized target (scaled to weight). */
    fun preset(ctx: Context, date: String): Preset? {
        val d = LocalDate.parse(date)
        var w = hardWorkout(ctx, d); var phase = "day"
        if (w.isEmpty()) { w = hardWorkout(ctx, d.plusDays(1)); phase = "prior" }
        if (w.isEmpty()) return null
        val t = TABLE[w]!![phase]!!
        val weight = latestWeight(ctx, date).takeIf { it > 0 } ?: 185.0
        val cal = Math.round(t[0] * (weight / 185) / 25).toInt() * 25
        return Preset(w, phase, cal, t[1], t[2], t[3], if (phase == "day") "Today: ${NAMES[w]} — Training & Recovery Target" else "Tomorrow: ${NAMES[w]} — Pre-Fueling Target")
    }

    private fun latestWeight(ctx: Context, date: String): Double =
        (NutritionStore.days(ctx) + listOfNotNull(NutriDay.raw(ctx, date))).filter { it.date <= date && it.weight > 70 }.maxByOrNull { it.date }?.weight ?: 0.0

    data class MacroTarget(val prot: Int, val carbs: Int, val fat: Int, val label: String, val calories: Int)
    /** The Daily Macro Ratio target for [date] (runnerMacroTarget). */
    fun macroTarget(ctx: Context, date: String, calories: Int): MacroTarget {
        val acts = ActivityStore.list(ctx).filter { ActivityStore.str(it, "date") == date }
        val workouts = acts.filter { !Regex("rest\\s*day", RegexOption.IGNORE_CASE).containsMatchIn(ActivityStore.str(it, "type").ifEmpty { ActivityStore.str(it, "title") }) }
        val actText = workouts.joinToString(" ") { listOf(ActivityStore.str(it, "title"), ActivityStore.str(it, "type")).filter { s -> s.isNotEmpty() }.joinToString(" ") }
        val planText = PlanStore.data(ctx).optJSONObject("current")?.optJSONObject("days")?.optString(date) ?: ""
        val text = actText.ifEmpty { planText }.lowercase()
        val longest = workouts.maxOfOrNull { ActivityStore.num(it, "distance") } ?: 0.0
        var t = MacroTarget(20, 50, 30, "Rest / recovery day", calories)
        if (Regex("race|long run|marathon|half marathon").containsMatchIn(text) || longest >= 8) t = MacroTarget(18, 65, 17, "Long-run / race-day target", calories)
        else if (Regex("tempo|interval|speed|threshold|track|hill repeat|quality").containsMatchIn(text)) t = MacroTarget(20, 60, 20, "Speed / quality-workout target", calories)
        else if (Regex("run|cycle|bike|strength|weight|cross.train").containsMatchIn(text) || acts.isNotEmpty()) t = MacroTarget(20, 55, 25, "Training-day target", calories)
        preset(ctx, date)?.let { pr -> t = MacroTarget(pr.prot, pr.carbs, pr.fat, pr.label, calories) }
        val c = ratio(ctx, date)
        if (c.optBoolean("enabled") && c.optInt("prot") + c.optInt("carbs") + c.optInt("fat") == 100) t = MacroTarget(c.optInt("prot"), c.optInt("carbs"), c.optInt("fat"), "Custom macro target", calories)
        return t
    }

    fun perfNote(ctx: Context, date: String): String = preset(ctx, date)?.let {
        if (it.phase == "day") "Morning workout: aim for 30–40g protein with the post-run meal, then spread the remaining protein across the day."
        else "Pre-fueling day: emphasize carbohydrates at lunch, dinner, and the evening snack."
    } ?: ""

    /** Run-day context for food quality: (run day, hard workout). */
    fun runContext(ctx: Context, date: String): Pair<Boolean, String> {
        val hard = hardWorkout(ctx, LocalDate.parse(date))
        val acts = ActivityStore.list(ctx).filter { ActivityStore.str(it, "date") == date }.joinToString(" ") { listOf("title", "type", "workout").map { k -> ActivityStore.str(it, k) }.joinToString(" ") }
        return (hard.isNotEmpty() || Regex("\\b(run|race|interval|tempo|threshold|speed|track|recovery)\\b", RegexOption.IGNORE_CASE).containsMatchIn(acts)) to hard
    }

    // ── Pre-Run Meal Ideas (Healthy 7+ foods, the day before a hard workout) ──
    private fun slot(o: JSONObject): String {
        val c = o.optJSONObject("mealCounts") ?: return ""; var best = ""; var n = 0
        listOf("breakfast", "lunch", "dinner").forEach { m -> val v = c.optInt(m); if (v > n) { n = v; best = m } }
        return best
    }
    private fun rng(seed: String): () -> Double { var h = 0L; seed.forEach { h = (h * 31 + it.code) and 0xFFFFFFFFL }
        return { h = (h * 1664525 + 1013904223) and 0xFFFFFFFFL; h / 4294967296.0 } }
    private fun <T> shuffle(a: List<T>, r: () -> Double): List<T> { val x = a.toMutableList(); for (i in x.size - 1 downTo 1) { val j = Math.floor(r() * (i + 1)).toInt(); val t = x[i]; x[i] = x[j]; x[j] = t }; return x }

    /** {title, sets:[[{label, foods:[…]}…]…]} or null — 6 shuffles, like the Command Center's. */
    fun preRun(ctx: Context, date: String): JSONObject? {
        val pr = preset(ctx, date)?.takeIf { it.phase == "prior" } ?: return null
        val healthy = FoodLibrary.healthyRaw(ctx)
        val sets = JSONArray()
        for (sh in 0 until 6) {
            val ex = ArrayList<String>(); val set = JSONArray()
            listOf("breakfast" to "🌅 Breakfast", "lunch" to "🥗 Lunch", "dinner" to "🍽️ Dinner").forEach { (key, label) ->
                val r = rng("$date|$key|$sh")
                val all = healthy.filter { it.optString("name").lowercase() !in ex }
                val picks = (shuffle(all.filter { slot(it) == key }, r) + shuffle(all.filter { slot(it).isEmpty() }, r) + shuffle(all.filter { slot(it).let { s -> s.isNotEmpty() && s != key } }, r)).take(2)
                picks.forEach { ex += it.optString("name").lowercase() }
                set.put(JSONObject().put("label", label).put("foods", JSONArray().apply { picks.forEach { put(JSONObject(it.toString()).apply { remove("mealCounts") }) } }))
            }
            sets.put(set)
        }
        return JSONObject().put("title", "🍽️ Tomorrow: ${NAMES[pr.workout]} — Meal Ideas").put("sets", sets)
    }

    /** Keeps Healthy 7+ foods (quality ≥ 7, confidence not Low) up to date as foods are logged — captureHealthyFoods. */
    fun captureHealthy(ctx: Context, items: List<Food.Item>, runDay: Boolean, hard: String) {
        val list = FoodLibrary.healthyRaw(ctx).toMutableList(); var changed = false
        items.forEach { f ->
            if (f.name.isBlank()) return@forEach
            val q = Food.quality(f, runDay, hard); val key = f.name.trim().lowercase()
            val idx = list.indexOfFirst { it.optString("name").trim().lowercase() == key }
            if (q.score < 7 || q.confidencePct < 55) { if (idx >= 0) { list.removeAt(idx); changed = true }; return@forEach }
            val counts = JSONObject((if (idx >= 0) list[idx].optJSONObject("mealCounts") else null)?.toString() ?: "{}")
            counts.put(f.meal, counts.optInt(f.meal) + 1)
            val inf = Food.inferSources(f)
            val o = Food.toJson(inf.copy(servings = f.servings), withId = false, withPhoto = true).put("score", q.score).put("mealCounts", counts)
            if (idx < 0) list += o else list[idx] = o
            changed = true
        }
        if (changed) FoodLibrary.setHealthyRaw(ctx, list.sortedWith(compareByDescending<JSONObject> { it.optDouble("score", 0.0) }.thenBy { it.optString("name") }))
    }

    /** The day with everything the Command Center used to add (target, macro ratio, notes stay as they are). */
    fun fill(ctx: Context, d: Nutrition.Day): Nutrition.Day {
        val (run, hard) = runContext(ctx, d.date)
        val today = d.date == io.github.michealsizemore1.beyondstride.Readiness.today()
        val cal = if (d.target > 0) d.target else try { CalorieTargets.result(ctx, date = d.date).target } catch (e: Exception) { 0 }
        val mt = macroTarget(ctx, d.date, cal)
        return d.copy(qRun = run, qHard = hard, mtProt = mt.prot, mtCarbs = mt.carbs, mtFat = mt.fat, mtLabel = mt.label, mtCal = mt.calories,
            perfNote = perfNote(ctx, d.date), prerun = if (today) preRun(ctx, d.date)?.toString() ?: "" else "")
    }
}
