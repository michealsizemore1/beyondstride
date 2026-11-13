package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import io.github.michealsizemore1.beyondstride.CalorieTarget
import io.github.michealsizemore1.beyondstride.Hydration
import io.github.michealsizemore1.beyondstride.Nutrition
import io.github.michealsizemore1.beyondstride.NutritionStore
import io.github.michealsizemore1.beyondstride.Readiness
import io.github.michealsizemore1.beyondstride.RunMath
import org.json.JSONObject

/** Phone: the calorie target's settings and today's result. */
object CalorieTargets {
    private fun p(ctx: Context) = ctx.getSharedPreferences("calorietarget", 0)

    /** Your settings (the ones copied from the Command Center at the switch, if never changed here). */
    fun settings(ctx: Context): CalorieTarget.Settings {
        val mine = p(ctx).getString("settings", null)?.let { try { JSONObject(it) } catch (e: Exception) { null } }
        return CalorieTarget.Settings.from(mine ?: FoodLibrary.calSettings(ctx))
    }

    fun set(ctx: Context, s: CalorieTarget.Settings) {
        p(ctx).edit().putString("settings", s.json().toString()).apply()
        Hydration.queue(ctx, "calSettings", s.json())
        val app = ctx.applicationContext
        synchronized(Hydration) { NutritionStore.save(app, apply(app, NutritionStore.days(app))) }
        Thread { HydrationSync.flush(app) }.start()
    }

    /** Most recent weight from the last 45 days (wake-up weight), or 0. */
    fun weight(days: List<Nutrition.Day>) = days.sortedByDescending { it.date }.take(45).firstOrNull { it.weight > 70 }?.weight ?: 0.0

    fun result(ctx: Context, days: List<Nutrition.Day> = NutritionStore.days(ctx), date: String = Readiness.today()): CalorieTarget.Result {
        val w = weight(days)
        val runs = PhoneRuns.list(ctx).take(40).mapNotNull { PhoneRuns.read(it) }.filter {
            java.time.Instant.ofEpochMilli(it.optLong("start")).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString() == date }
        val acts = runs.map { r -> CalorieTarget.Activity(PhoneRuns.title(r), r.optDouble("distance") / RunMath.METERS_PER_MILE,
            RunMath.clock(r.optInt("movingSeconds")), CalorieTarget.runKcal(r, if (w > 0) w else 160.0)) }
        val plan = WorkoutStore.plan(ctx)
        val tomorrow = java.time.LocalDate.parse(date).plusDays(1).toString()
        val todayText = (listOfNotNull(plan.optJSONObject(date)?.optString("name")) + runs.map { PhoneRuns.title(it) }).joinToString(" ")
        val preset = CalorieTarget.preset(todayText, plan.optJSONObject(tomorrow)?.optString("name") ?: "", w)
        val cc = days.lastOrNull { it.date == date }?.active ?: 0
        return CalorieTarget.compute(settings(ctx), w, acts, cc, preset)
    }

    /** Today's target and range set from BeyondStride's own calculation (the watch gets the same). */
    fun apply(ctx: Context, days: List<Nutrition.Day>): List<Nutrition.Day> = try {
        val date = Readiness.today()
        val r = result(ctx, days, date)
        val i = days.indexOfFirst { it.date == date }
        val base = if (i >= 0) days[i] else Hydration.blank(date)
        val d = base.copy(target = r.target, range = r.range)
        NutritionStore.setCalc(ctx, JSONObject().put("settings", settings(ctx).json()).put("base", r.base).put("active", r.active)
            .put("replace", Math.round(r.active * r.refuel)).put("goalAdjust", r.goalAdjust).put("preset", r.preset?.label ?: "")
            .put("target", r.target).put("range", r.range).put("runs", r.activities.size).toString())
        (if (i >= 0) days.toMutableList().also { it[i] = d } else days + d).sortedBy { it.date }
    } catch (e: Exception) { days }
}
