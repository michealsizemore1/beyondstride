package io.github.michealsizemore1.beyondstride

import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Daily calorie intake target — same math as the Command Center's "Daily Calorie Intake Target":
 * rest-day target + a share of workout calories ± a goal adjustment, or a higher hard-workout
 * (long / tempo / interval) fueling target on those days and the day before.
 */
object CalorieTarget {
    data class Settings(val base: String = "", val goal: String = "maintain", val refuel: Int = 100) {
        fun json() = JSONObject().put("base", base).put("goal", goal).put("refuel", refuel)
        companion object {
            fun from(o: JSONObject?) = if (o == null) Settings() else
                Settings(o.optString("base", ""), o.optString("goal", "maintain").ifEmpty { "maintain" }, o.optInt("refuel", 100))
        }
    }
    data class Activity(val name: String, val miles: Double, val duration: String, val kcal: Int)
    data class Preset(val label: String, val calories: Int)
    data class Result(
        val base: Int, val manualBase: Boolean, val weight: Double, val active: Int, val refuel: Double, val goalAdjust: Int,
        val activityBased: Int, val target: Int, val range: Int, val preset: Preset?, val activities: List<Activity>, val otherActive: Int,
    )

    val GOALS = listOf("maintain" to "Maintain weight", "loss" to "Gradual loss", "gain" to "Gradual gain")
    val REFUELS = listOf(100, 85, 75, 50)

    /** Running calories from pace, hill grade and weight (ACSM running equation), like the Command Center's estimate. */
    fun runKcal(run: JSONObject, weightLb: Double): Int {
        val secs = run.optInt("movingSeconds").takeIf { it > 0 } ?: return 0
        val meters = run.optDouble("distance", 0.0)
        val hours = secs / 3600.0
        val speed = if (meters > 0) meters / (secs / 60.0) else 0.0       // m/min
        val grade = if (meters > 0) (run.optDouble("elevGain", 0.0).takeIf { !it.isNaN() } ?: 0.0) / meters else 0.0
        val met = if (speed > 0) (0.2 * speed + 0.9 * speed * grade + 3.5) / 3.5 else 8.0
        return (met * weightLb * 0.453592 * hours).roundToInt().coerceAtLeast(0)
    }

    /** "long" / "interval" / "tempo" / "quality" from workout or run names, or null. */
    fun hardKind(text: String): String? {
        val t = text.lowercase()
        return when {
            Regex("\\blong\\s+run\\b|\\bhalf\\s+marathon\\b|\\bmarathon\\b").containsMatchIn(t) -> "long"
            Regex("\\bintervals?\\b|\\brepeats?\\b|\\bspeed\\s*work\\b|\\btrack\\s+(?:workout|session)\\b|\\bhill\\s+repeats?\\b").containsMatchIn(t) -> "interval"
            Regex("\\btempo\\b|\\bthreshold\\b").containsMatchIn(t) -> "tempo"
            Regex("\\bquality\\b").containsMatchIn(t) -> "quality"
            else -> null
        }
    }

    /** Hard-workout fueling for today (from [todayText]) or the eve of one (from [tomorrowText]); scaled to weight. */
    fun preset(todayText: String, tomorrowText: String, weightLb: Double): Preset? {
        val day = hardKind(todayText); val prior = if (day == null) hardKind(tomorrowText) else null
        val kind = day ?: prior ?: return null
        val cal = when (kind) { "long" -> if (day != null) 3300 else 3200; "interval" -> if (day != null) 2950 else 2800; else -> if (day != null) 2900 else 2800 }
        val names = mapOf("long" to "Long Run", "tempo" to "Tempo Run", "interval" to "Interval Run", "quality" to "Quality Run")
        val scaled = (cal * ((if (weightLb > 0) weightLb else 185.0) / 185.0) / 25).roundToInt() * 25
        return Preset(if (day != null) "Today: ${names[kind]} — Training & Recovery Target" else "Tomorrow: ${names[kind]} — Pre-Fueling Target", scaled)
    }

    /**
     * [activities]: today's runs from BeyondStride; [ccActive]: the Command Center's active kcal for today
     * (it may include strength, walks, bike…) — the larger total is used.
     */
    fun compute(s: Settings, weightLb: Double, activities: List<Activity>, ccActive: Int, preset: Preset?): Result {
        val manual = s.base.toDoubleOrNull()?.takeIf { it > 0 }
        val base = manual?.roundToInt() ?: if (weightLb > 0) (weightLb * 12.5 / 50).roundToInt() * 50 else 2200
        val runs = activities.sumOf { it.kcal }
        val active = maxOf(runs, ccActive)
        val refuel = s.refuel.coerceIn(0, 100) / 100.0
        val goalAdjust = if (preset != null) 0 else when (s.goal) { "loss" -> -300; "gain" -> 250; else -> 0 }
        val activityBased = maxOf(1200, ((base + active * refuel + goalAdjust) / 25).roundToInt() * 25)
        val target = if (preset != null) maxOf(preset.calories, activityBased) else activityBased
        val range = maxOf(100, (target * 0.05 / 25).roundToInt() * 25)
        return Result(base, manual != null, weightLb, active, refuel, goalAdjust, activityBased, target, range, preset, activities, maxOf(0, ccActive - runs))
    }

    fun kcal(v: Int) = String.format(java.util.Locale.US, "%,d", v)
}
