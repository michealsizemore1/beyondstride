package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import io.github.michealsizemore1.beyondstride.Hydration
import io.github.michealsizemore1.beyondstride.Nutrition
import io.github.michealsizemore1.beyondstride.NutritionStore
import io.github.michealsizemore1.beyondstride.Readiness
import org.json.JSONObject

/**
 * The day shown on the Nutrition tab (today unless you've gone back). The last 3 weeks come with the usual
 * refresh; every older day the Command Center has shared is downloaded here (all of them, a month at a time).
 * Adding / changing foods, fluids and weights on the Nutrition tab goes to this day.
 */
object NutriDay {
    @Volatile var viewed: String? = null
    fun date(): String = viewed ?: Readiness.today()
    fun isToday() = date() == Readiness.today()

    private fun p(ctx: Context) = ctx.getSharedPreferences("nutHistory", 0)
    // Every older day, kept in a file (it can be a few MB): {"2025-03-04": {...}, ...}.
    private fun file(ctx: Context) = java.io.File(ctx.filesDir, "nutrition_history.json")
    @Volatile private var mem: JSONObject? = null
    @Synchronized private fun cache(ctx: Context): JSONObject {
        mem?.let { return it }
        val f = file(ctx)
        val o = try { if (f.exists()) JSONObject(f.readText()) else JSONObject(p(ctx).getString("days", "{}")) } catch (e: Exception) { JSONObject() }
        mem = o; return o
    }
    @Synchronized private fun write(ctx: Context, o: JSONObject) { mem = o; try { file(ctx).writeText(o.toString()) } catch (e: Exception) { } }
    fun historyCount(ctx: Context) = cache(ctx).length()
    /** After a restore: read the history file again. */
    fun forget() { mem = null }
    /** Every day with food (newest first) — the 3 recent weeks plus the history. */
    fun allDates(ctx: Context): List<String> = (cache(ctx).keys().asSequence().toList() + NutritionStore.days(ctx).filter { it.count > 0 }.map { it.date }).distinct()
        .filter { d -> cache(ctx).optJSONObject(d)?.optBoolean("empty") != true }.sortedDescending()

    /** The raw day (no local changes on top): recent days from the store, older ones from the cache. */
    fun raw(ctx: Context, date: String): Nutrition.Day? =
        NutritionStore.days(ctx).lastOrNull { it.date == date } ?: cache(ctx).optJSONObject(date)?.let { Nutrition.parse(JSONObject().put(date, it).toString()).firstOrNull() }

    /** The day as shown, with changes made here that the Command Center hasn't taken yet. */
    fun day(ctx: Context, date: String = date()): Nutrition.Day? {
        if (NutritionStore.days(ctx).any { it.date == date }) return NutritionStore.days(ctx).lastOrNull { it.date == date }
        val base = raw(ctx, date)
        return Hydration.overlay(ctx, listOfNotNull(base)).lastOrNull { it.date == date } ?: base
    }

    /** A Change Ratio made here, not yet applied by the Command Center: (enabled, p, c, f) or null. */
    fun pendingRatio(ctx: Context, date: String): JSONObject? = Hydration.changes(ctx).lastOrNull { it.f == "macroRatio" && it.date == date }?.x?.let { try { JSONObject(it) } catch (e: Exception) { null } }
    fun setRatio(ctx: Context, date: String, x: JSONObject) {
        NutritionEngine.setRatio(ctx, date, x); Thread { Local.refill(ctx.applicationContext, date) }.start() }
    /** A meal's notes, with any typed here on top. */
    fun note(ctx: Context, d: Nutrition.Day?, date: String, meal: String): String =
        Hydration.changes(ctx).lastOrNull { it.f == "mealNote" && it.date == date && it.x?.contains("\"$meal\"") == true }?.x?.let { try { JSONObject(it).optString("text") } catch (e: Exception) { null } } ?: d?.notes?.get(meal) ?: ""
    fun setNote(ctx: Context, date: String, meal: String, text: String) { Hydration.queue(ctx, "mealNote", JSONObject().put("meal", meal).put("text", text), date); Thread { HydrationSync.flush(ctx.applicationContext) }.start() }

    /** Stand-alone: every day lives in the history file (the last 3 weeks are copied to the store for the screens and the watch). */
    fun allJson(ctx: Context): JSONObject = cache(ctx)
    fun putDay(ctx: Context, d: Nutrition.Day) {
        val all = JSONObject(cache(ctx).toString()); all.put(d.date, JSONObject(Nutrition.json(listOf(d))).getJSONObject(d.date)); write(ctx, all); CloudCopy.dirty(ctx)
    }
    fun putAll(ctx: Context, days: List<Nutrition.Day>) {
        val all = JSONObject(cache(ctx).toString()); val o = JSONObject(Nutrition.json(days)); o.keys().forEach { all.put(it, o.get(it)) }; write(ctx, all); CloudCopy.dirty(ctx)
    }
    fun rawHistory(ctx: Context, date: String): Nutrition.Day? = cache(ctx).optJSONObject(date)?.let { Nutrition.parse(JSONObject().put(date, it).toString()).firstOrNull() }

}
