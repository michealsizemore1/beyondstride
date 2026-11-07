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

    /** Your favorites and healthy foods together, no repeats (by name). */
    fun mine(ctx: Context): List<Food.Item> {
        val seen = HashSet<String>()
        return (favorites(ctx) + healthy(ctx)).filter { seen.add(it.name.lowercase().trim()) }
    }

    /** Foods logged in the last 3 weeks, newest first, no repeats (by name). */
    fun recent(ctx: Context, limit: Int = 40): List<Food.Item> {
        val seen = HashSet<String>()
        return NutritionStore.days(ctx).sortedByDescending { it.date }.flatMap { it.foods.reversed() }
            .filter { it.name.isNotBlank() && seen.add(it.name.lowercase().trim()) }.take(limit)
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
            if (changed) quickToWatch(ctx)
            changed
        } catch (e: Exception) { false }
    }

    /** A short list for the watch's Quick add: your favorites first, then recent foods. */
    fun quickToWatch(ctx: Context) {
        val seen = HashSet<String>()
        val list = (favorites(ctx).take(25) + recent(ctx, 25)).filter { seen.add(it.name.lowercase().trim()) }.take(40)
        NutritionStore.setQuick(ctx, JSONArray().apply { list.forEach { put(Food.toJson(it, withId = false)) } }.toString())
    }

    // ── Open Food Facts (free, no account) ──
    private const val UA = "BeyondStride-app"
    private val OFF_KEY = mapOf("fiber" to "fiber", "sugar" to "sugars", "sodium" to "sodium", "vitC" to "vitamin-c", "vitD" to "vitamin-d",
        "calcium" to "calcium", "iron" to "iron", "potassium" to "potassium", "caffeine" to "caffeine", "magnesium" to "magnesium",
        "vitB12" to "vitamin-b12", "zinc" to "zinc")

    private fun product(p: JSONObject, meal: String): Food.Item? {
        val n = p.optJSONObject("nutriments") ?: return null
        val hasServing = listOf("energy-kcal", "proteins", "fat", "carbohydrates").any { n.has(it + "_serving") }
        fun v(k: String): Double? {
            val raw = if (hasServing && n.has(k + "_serving")) n.opt(k + "_serving") else n.opt(k + "_100g")
            val d = raw?.toString()?.toDoubleOrNull() ?: return null
            return Math.round(d * 10) / 10.0
        }
        val name = listOf(p.optString("product_name"), p.optString("brands").substringBefore(",")).filter { it.isNotBlank() }.joinToString(" — ")
        if (name.isBlank()) return null
        val micros = HashMap<String, Double>()
        OFF_KEY.forEach { (f, k) -> v(k)?.let { micros[f] = if (f == "sodium") Math.round(it * 1000).toDouble() else it } }
        val serving = if (hasServing && p.optString("serving_size").isNotBlank()) p.optString("serving_size") else "100 g"
        return Food.Item(Food.newId(), meal, name, serving, 1.0, v("energy-kcal"), v("proteins"), v("fat"), v("carbohydrates"), micros, p.optString("code"))
    }

    /** Text search. Call off the main thread. */
    fun search(q: String, meal: String): List<Food.Item> = try {
        val url = "https://world.openfoodfacts.org/cgi/search.pl?search_simple=1&json=1&page_size=25&search_terms=" + URLEncoder.encode(q, "UTF-8") +
            "&fields=code,product_name,brands,serving_size,nutriments"
        val c = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 20_000; setRequestProperty("User-Agent", UA) }
        val a = JSONObject(c.inputStream.bufferedReader().readText()).optJSONArray("products") ?: JSONArray()
        (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let { p -> product(p, meal) } }.filter { it.cal != null }
    } catch (e: Exception) { emptyList() }

    /** Barcode lookup. Call off the main thread. */
    fun barcode(code: String, meal: String): Food.Item? = try {
        val c = (URL("https://world.openfoodfacts.org/api/v2/product/$code.json?fields=code,product_name,brands,serving_size,nutriments")
            .openConnection() as HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 20_000; setRequestProperty("User-Agent", UA) }
        val o = JSONObject(c.inputStream.bufferedReader().readText())
        if (o.optInt("status") != 1) null else o.optJSONObject("product")?.let { product(it, meal)?.copy(barcode = code) }
    } catch (e: Exception) { null }
}
