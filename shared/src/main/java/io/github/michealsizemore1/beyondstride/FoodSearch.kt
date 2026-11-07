package io.github.michealsizemore1.beyondstride

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Open Food Facts (free, no account): text search and barcode lookup. Used on the phone and the watch. */
object FoodSearch {
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
