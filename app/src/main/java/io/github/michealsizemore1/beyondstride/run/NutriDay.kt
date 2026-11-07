package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import io.github.michealsizemore1.beyondstride.Hydration
import io.github.michealsizemore1.beyondstride.Nutrition
import io.github.michealsizemore1.beyondstride.NutritionStore
import io.github.michealsizemore1.beyondstride.Readiness
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The day shown on the Nutrition tab (today unless you've gone back). The last 3 weeks come with the usual
 * refresh; older days are read one at a time from the Command Center's shared days and kept here (up to 120).
 * Adding / changing foods, fluids and weights on the Nutrition tab goes to this day.
 */
object NutriDay {
    @Volatile var viewed: String? = null
    fun date(): String = viewed ?: Readiness.today()
    fun isToday() = date() == Readiness.today()

    private fun p(ctx: Context) = ctx.getSharedPreferences("nutHistory", 0)
    private fun cache(ctx: Context): JSONObject = try { JSONObject(p(ctx).getString("days", "{}")) } catch (e: Exception) { JSONObject() }

    /** The raw day (no local changes on top): recent days from the store, older ones from the cache. */
    fun raw(ctx: Context, date: String): Nutrition.Day? =
        NutritionStore.days(ctx).lastOrNull { it.date == date } ?: cache(ctx).optJSONObject(date)?.let { Nutrition.parse(JSONObject().put(date, it).toString()).firstOrNull() }

    /** The day as shown, with changes made here that the Command Center hasn't taken yet. */
    fun day(ctx: Context, date: String = date()): Nutrition.Day? {
        if (NutritionStore.days(ctx).any { it.date == date }) return NutritionStore.days(ctx).lastOrNull { it.date == date }
        val base = raw(ctx, date)
        return Hydration.overlay(ctx, listOfNotNull(base)).lastOrNull { it.date == date } ?: base
    }

    /** True when this day is older than the store and hasn't been read yet (or the Command Center hasn't shared it). */
    fun missing(ctx: Context, date: String) = NutritionStore.days(ctx).none { it.date == date } && !cache(ctx).has(date)

    /** Reads an older day. Returns true if it changed. If the Command Center hasn't shared it yet, asks it to. Call off the main thread. */
    fun fetch(ctx: Context, date: String): Boolean {
        if (!NutritionSync.ready(ctx) || NutritionStore.days(ctx).any { it.date == date }) return false
        HydrationSync.reconcile(ctx)
        return try {
            val c = (URL(Live.db(ctx) + "/nutrition/" + NutritionSync.key(ctx) + "/days/" + date + ".json").openConnection() as HttpURLConnection)
                .apply { connectTimeout = 12_000; readTimeout = 20_000 }
            if (c.responseCode != 200) return false
            val body = c.inputStream.bufferedReader().readText()
            if (body == "null" || body.isBlank()) {
                // Not shared yet: ask the Command Center for it (it answers next time its page is open).
                if (Hydration.changes(ctx).none { it.f == "dayRequest" && it.x?.contains(date) == true }) {
                    Hydration.queue(ctx, "dayRequest", JSONObject().put("day", date)); HydrationSync.flush(ctx)
                }
                return false
            }
            var o = JSONObject(body)
            // A day with foods but no totals (older shares): work them out here.
            Nutrition.parse(JSONObject().put(date, o).toString()).firstOrNull()?.let { d ->
                if (d.foods.isNotEmpty() && d.micros.isEmpty()) o = JSONObject(Nutrition.json(listOf(io.github.michealsizemore1.beyondstride.Food.summarize(d, d.foods).copy(cal = d.cal)))).getJSONObject(date)
            }
            val all = cache(ctx); val changed = all.optJSONObject(date)?.toString() != o.toString()
            all.put(date, o)
            // Keep the 120 most recently viewed days.
            val keys = all.keys().asSequence().toList()
            if (keys.size > 120) keys.sorted().take(keys.size - 120).forEach { all.remove(it) }
            p(ctx).edit().putString("days", all.toString()).apply()
            changed
        } catch (e: Exception) { false }
    }

    fun waiting(ctx: Context, date: String) = Hydration.changes(ctx).any { it.f == "dayRequest" && it.x?.contains(date) == true }
}
