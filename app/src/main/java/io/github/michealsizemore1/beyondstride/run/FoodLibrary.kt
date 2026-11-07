package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import io.github.michealsizemore1.beyondstride.Food
import io.github.michealsizemore1.beyondstride.NutritionStore
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Where foods come from on the Add food screen: recent foods (your log), your Command Center food database
 * (favorites, healthy foods, saved meals — shared to Firebase by the Command Center), and Open Food Facts.
 */
object FoodLibrary {
    data class Meal(val name: String, val category: String, val items: List<Food.Item>)

    private fun p(ctx: Context) = ctx.getSharedPreferences("foodlib", 0)
    private fun lib(ctx: Context): JSONObject = try { JSONObject(p(ctx).getString("lib", "{}")) } catch (e: Exception) { JSONObject() }

    fun favorites(ctx: Context) = Food.parseList(lib(ctx).optJSONArray("favorites"))
    fun healthy(ctx: Context) = Food.parseList(lib(ctx).optJSONArray("healthy"))
    fun meals(ctx: Context): List<Meal> = lib(ctx).optJSONArray("meals")?.let { a ->
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { Meal(it.optString("name"), it.optString("category"), Food.parseList(it.optJSONArray("items"))) }
    } ?: emptyList()

    /**
     * Deletes a food or saved meal from your lists: [list] "fav" / "healthy" / "meals" (also in the Command Center),
     * or "recent" (just hidden from Recent here).
     */
    fun delete(ctx: Context, list: String, name: String) {
        if (list == "recent") {
            val hid = (p(ctx).getStringSet("hiddenRecent", emptySet()) ?: emptySet()) + name.lowercase().trim()
            p(ctx).edit().putStringSet("hiddenRecent", hid).apply(); return
        }
        val key = if (list == "fav") "favorites" else list
        val l = lib(ctx); val a = l.optJSONArray(key) ?: return
        val out = JSONArray(); var removed = false
        for (i in 0 until a.length()) { val o = a.optJSONObject(i); if (!removed && o?.optString("name") == name) removed = true else out.put(a.get(i)) }
        l.put(key, out); p(ctx).edit().putString("lib", l.toString()).apply()
        io.github.michealsizemore1.beyondstride.Hydration.queue(ctx, "libDelete", JSONObject().put("list", list).put("name", name))
        Thread { HydrationSync.flush(ctx.applicationContext) }.start()
    }

    /** A food's picture: its own, or the one saved with the same name in your food database. */
    private var picCache: Pair<String, Map<String, String>>? = null
    fun imageFor(ctx: Context, f: Food.Item): String {
        if (f.image.isNotEmpty()) return f.image
        val raw = p(ctx).getString("lib", "") ?: ""
        val map = picCache?.takeIf { it.first.length == raw.length }?.second ?: run {
            val m = HashMap<String, String>()
            (healthy(ctx) + favorites(ctx) + meals(ctx).flatMap { it.items }).forEach { if (it.image.isNotEmpty()) m[it.name.lowercase().trim()] = it.image }
            picCache = raw to m; m
        }
        return map[f.name.lowercase().trim()] ?: ""
    }

    /** (foods that need enriching, foods with AI estimates) from the Command Center. */
    fun stats(ctx: Context): Pair<Int, Int> = lib(ctx).optJSONObject("stats")?.let { it.optInt("needEnrich") to it.optInt("estimated") } ?: (0 to 0)

    /** Asks the Command Center to run a Food Database tool: enrich, undo, rescan, dedupe. */
    fun libAction(ctx: Context, action: String) {
        io.github.michealsizemore1.beyondstride.Hydration.queue(ctx, "libAction", JSONObject().put("action", action))
        Thread { HydrationSync.flush(ctx.applicationContext) }.start()
    }

    /** The Command Center's calorie-target settings (shared with the food database). */
    fun calSettings(ctx: Context): JSONObject? = lib(ctx).optJSONObject("calSettings")

    /** Saves a meal (shown at once; the Command Center adds it to its saved meals). */
    fun saveMeal(ctx: Context, name: String, category: String, items: List<Food.Item>) {
        val l = lib(ctx)
        val meals = l.optJSONArray("meals") ?: JSONArray()
        val m = JSONObject().put("name", name).put("category", category).put("items", JSONArray().apply { items.forEach { put(Food.toJson(it, withId = false)) } })
        val out = JSONArray().put(m); for (i in 0 until meals.length()) out.put(meals.get(i))
        l.put("meals", out)
        p(ctx).edit().putString("lib", l.toString()).apply()
        io.github.michealsizemore1.beyondstride.Hydration.queue(ctx, "mealSave", m)
        Thread { HydrationSync.flush(ctx.applicationContext) }.start()
    }

    /** Your favorites and healthy foods together, no repeats (by name). */
    fun mine(ctx: Context): List<Food.Item> {
        val seen = HashSet<String>()
        return (favorites(ctx) + healthy(ctx)).filter { seen.add(it.name.lowercase().trim()) }
    }

    /** Foods logged in the last 3 weeks, newest first, no repeats (by name). */
    fun recent(ctx: Context, limit: Int = 40): List<Food.Item> {
        val seen = HashSet<String>()
        val hidden = p(ctx).getStringSet("hiddenRecent", emptySet()) ?: emptySet()
        return NutritionStore.days(ctx).sortedByDescending { it.date }.flatMap { it.foods.reversed() }
            .filter { it.name.isNotBlank() && it.name.lowercase().trim() !in hidden && seen.add(it.name.lowercase().trim()) }.take(limit)
    }

    /** Reads the Command Center's food database (every 10 minutes at most). Call off the main thread. */
    fun refresh(ctx: Context, force: Boolean = false): Boolean {
        if (!NutritionSync.ready(ctx)) return false
        if (!force && System.currentTimeMillis() - p(ctx).getLong("at", 0) < 10 * 60_000L) return false
        return try {
            val c = (URL(Live.db(ctx) + "/nutrition/" + NutritionSync.key(ctx) + "/library.json").openConnection() as HttpURLConnection)
                .apply { connectTimeout = 12_000; readTimeout = 20_000 }
            if (c.responseCode != 200) return false
            val txt = c.inputStream.bufferedReader().readText()
            val changed = txt != p(ctx).getString("lib", "")
            p(ctx).edit().putString("lib", if (txt == "null") "{}" else txt).putLong("at", System.currentTimeMillis()).apply()
            if (changed) { quickToWatch(ctx); libraryToWatch(ctx) }
            changed
        } catch (e: Exception) { false }
    }

    /** A short list for the watch's Quick add: your favorites first, then recent foods. */
    fun quickToWatch(ctx: Context) {
        val seen = HashSet<String>()
        val list = (favorites(ctx).take(25) + recent(ctx, 25)).filter { seen.add(it.name.lowercase().trim()) }.take(40)
        NutritionStore.setQuick(ctx, JSONArray().apply { list.forEach { put(Food.toJson(it, withId = false)) } }.toString())
    }

    fun search(q: String, meal: String) = io.github.michealsizemore1.beyondstride.FoodSearch.search(q, meal)
    fun barcode(code: String, meal: String) = io.github.michealsizemore1.beyondstride.FoodSearch.barcode(code, meal)

    /** Your food database on the watch, for Quick add's search. */
    fun libraryToWatch(ctx: Context) {
        val list = mine(ctx).take(300)
        NutritionStore.setLibrary(ctx, JSONArray().apply { list.forEach { put(Food.toJson(it, withId = false)) } }.toString())
    }
}
