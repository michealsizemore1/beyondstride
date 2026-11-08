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

    @Volatile var loading = false
    @Volatile var loaded = 0
    /**
     * Downloads every older day the Command Center has shared that isn't here yet, a month at a time.
     * Returns how many days were added. Call off the main thread.
     */
    fun fetchAll(ctx: Context, onProgress: () -> Unit = {}): Int {
        if (Standalone.on(ctx) || !NutritionSync.ready(ctx) || loading) return 0
        loading = true; loaded = 0
        try {
            val base = Live.db(ctx) + "/nutrition/" + NutritionSync.key(ctx) + "/days"
            val c = (URL("$base.json?shallow=true").openConnection() as HttpURLConnection).apply { connectTimeout = 12_000; readTimeout = 30_000 }
            if (c.responseCode != 200) return 0
            val txt = c.inputStream.bufferedReader().readText(); if (txt == "null" || txt.isBlank()) return 0
            val recent = NutritionStore.days(ctx).map { it.date }.toSet()
            val have = cache(ctx)
            val need = JSONObject(txt).keys().asSequence().filter { Regex("\\d{4}-\\d{2}-\\d{2}").matches(it) && it !in recent && !have.has(it) }.toList()
            if (need.isEmpty()) { p(ctx).edit().putLong("fullAt", System.currentTimeMillis()).apply(); return 0 }
            var added = 0
            need.groupBy { it.take(7) }.toSortedMap(compareByDescending { it }).forEach { (month, _) ->
                val url = "$base.json?orderBy=%22%24key%22&startAt=%22$month-01%22&endAt=%22$month-31%22"
                val mc = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 12_000; readTimeout = 40_000 }
                if (mc.responseCode != 200) return@forEach
                val body = mc.inputStream.bufferedReader().readText(); if (body == "null" || body.isBlank()) return@forEach
                val mo = JSONObject(body); val all = JSONObject(cache(ctx).toString())
                mo.keys().forEach { d -> if (d in recent) return@forEach
                    var o = mo.optJSONObject(d) ?: return@forEach
                    Nutrition.parse(JSONObject().put(d, o).toString()).firstOrNull()?.let { day ->
                        if (day.foods.isNotEmpty() && day.micros.isEmpty()) o = JSONObject(Nutrition.json(listOf(io.github.michealsizemore1.beyondstride.Food.summarize(day, day.foods).copy(cal = day.cal)))).getJSONObject(d) }
                    if (!all.has(d)) added++
                    all.put(d, o)
                }
                write(ctx, all); loaded = added; onProgress()
            }
            p(ctx).edit().putLong("fullAt", System.currentTimeMillis()).apply()
            return added
        } catch (e: Exception) { return 0 } finally { loading = false }
    }
    /** Checks for history not here yet at most every few hours (older days rarely change; a viewed day is re-read anyway). */
    fun fetchAllIfDue(ctx: Context, onProgress: () -> Unit): Int =
        if (System.currentTimeMillis() - p(ctx).getLong("fullAt", 0L) > 3 * 3600_000L || historyCount(ctx) == 0) fetchAll(ctx, onProgress) else 0

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
    fun missing(ctx: Context, date: String) = !Standalone.on(ctx) && NutritionStore.days(ctx).none { it.date == date } && !cache(ctx).has(date)

    /** Reads an older day. Returns true if it changed. If the Command Center hasn't shared it yet, asks it to. Call off the main thread. */
    fun fetch(ctx: Context, date: String): Boolean {
        if (Standalone.on(ctx) || !NutritionSync.ready(ctx) || NutritionStore.days(ctx).any { it.date == date }) return false
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
            val all = JSONObject(cache(ctx).toString()); val changed = all.optJSONObject(date)?.toString() != o.toString()
            all.put(date, o)
            write(ctx, all)
            changed
        } catch (e: Exception) { false }
    }

    /** A Change Ratio made here, not yet applied by the Command Center: (enabled, p, c, f) or null. */
    fun pendingRatio(ctx: Context, date: String): JSONObject? = Hydration.changes(ctx).lastOrNull { it.f == "macroRatio" && it.date == date }?.x?.let { try { JSONObject(it) } catch (e: Exception) { null } }
    fun setRatio(ctx: Context, date: String, x: JSONObject) {
        if (Standalone.on(ctx)) { NutritionEngine.setRatio(ctx, date, x); Thread { Local.refill(ctx.applicationContext, date) }.start(); return } Hydration.queue(ctx, "macroRatio", x, date); Thread { HydrationSync.flush(ctx.applicationContext) }.start() }
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

    fun waiting(ctx: Context, date: String) = Hydration.changes(ctx).any { it.f == "dayRequest" && it.x?.contains(date) == true }
}
