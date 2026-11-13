package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import io.github.michealsizemore1.beyondstride.Hydration
import io.github.michealsizemore1.beyondstride.Nutrition
import io.github.michealsizemore1.beyondstride.NutritionStore
import io.github.michealsizemore1.beyondstride.Readiness
import org.json.JSONObject

/**
 * Phone side of fluids, weights and foods entered in BeyondStride (or on the watch): records each change in the local
 * change log, shows it at once (and sends the day to the watch), then applies it to BeyondStride's own data.
 */
object HydrationSync {
    /** A change made here or on the watch. Fluids: [delta] oz. Weights: [value] lb (0 clears). */
    fun change(ctx: Context, f: String, delta: Int = 0, value: Double = 0.0, date: String = NutriDay.date()) {
        val app = ctx.applicationContext
        synchronized(Hydration) {
            val days = NutritionStore.days(app)
            Hydration.record(app, NutriDay.raw(app, date), date, f, delta, value)
            NutritionStore.save(app, CalorieTargets.apply(app, Hydration.overlay(app, days)))
        }
        Thread { flush(app) }.start()
    }

    /** A food added, changed (servings / meal) or removed — here or on the watch. */
    fun food(ctx: Context, op: String, item: io.github.michealsizemore1.beyondstride.Food.Item, date: String = NutriDay.date(), extra: JSONObject? = null) {
        val app = ctx.applicationContext
        synchronized(Hydration) {
            val days = NutritionStore.days(app)
            // The day on the Nutrition tab (today unless you've gone back); older days come from NutriDay's cache.
            Hydration.recordFood(app, NutriDay.raw(app, date), date, op, item, extra)
            NutritionStore.save(app, CalorieTargets.apply(app, Hydration.overlay(app, days)))
        }
        Thread { flush(app) }.start()
    }

    /** From the watch: {"date":…, "op":…, "item":{…}}. */
    fun foodFromWatch(ctx: Context, data: ByteArray) = try {
        val o = JSONObject(String(data))
        food(ctx, o.getString("op"), io.github.michealsizemore1.beyondstride.Food.fromJson(o.getJSONObject("item")), o.optString("date").ifEmpty { Readiness.today() })
    } catch (e: Exception) { }

    /** From the watch: "date|f|delta|value". */
    fun fromWatch(ctx: Context, data: ByteArray) {
        val c = Hydration.decode(String(data)) ?: return
        change(ctx, c.f, c.d, c.v, c.date)
    }

    /** Applies the waiting changes to BeyondStride's own data (stand-alone). Call off the main thread. */
    @Synchronized fun flush(ctx: Context) { Local.commit(ctx) }

    /** Same as [flush] (kept for the places that call it before reading the days). */
    fun reconcile(ctx: Context) { Local.commit(ctx) }

    fun today(ctx: Context): Nutrition.Day? = NutritionStore.days(ctx).lastOrNull { it.date == Readiness.today() }
}
