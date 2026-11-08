package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import io.github.michealsizemore1.beyondstride.Food
import io.github.michealsizemore1.beyondstride.Nutrition
import io.github.michealsizemore1.beyondstride.NutritionStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * The Command Center's Food Database tools, done in BeyondStride (same rules):
 *  🧪 Enrich All  — AI fills in only the nutrients a saved food is missing (your Anthropic key on this phone), one food at a time;
 *                   restaurant foods are skipped; filled values are marked as estimates.
 *  ↩️ Undo All    — removes every AI-estimated value again.
 *  ↻ Rescan Foods — re-checks every saved nutrition day for Healthy 7+ foods (meal counts recounted) and adds barcode pictures.
 *  🧹 Remove Dupes — removes duplicate foods from the viewed day's log and from your Food Database (same name).
 */
object FoodTools {
    private val MACROS = listOf("cal", "prot", "fat", "carbs")
    private val META = mapOf("cal" to ("Calories" to ""), "prot" to ("Protein" to "g"), "fat" to ("Fat" to "g"), "carbs" to ("Carbs" to "g"),
        "fiber" to ("Fiber" to "g"), "sugar" to ("Sugar" to "g"), "sodium" to ("Sodium" to "mg"), "vitC" to ("Vit C" to "mg"), "vitD" to ("Vit D" to "mcg"),
        "calcium" to ("Calcium" to "mg"), "iron" to ("Iron" to "mg"), "potassium" to ("Potassium" to "mg"), "caffeine" to ("Caffeine" to "mg"),
        "magnesium" to ("Magnesium" to "mg"), "vitB12" to ("Vit B12" to "mcg"), "zinc" to ("Zinc" to "mg"))
    private val FIELDS = MACROS + listOf("fiber", "sugar", "sodium", "vitC", "vitD", "calcium", "iron", "potassium", "caffeine", "magnesium", "vitB12", "zinc")
    private val CHAINS = listOf("mcdonalds","burgerking","wendys","chickfila","tacobell","chipotle","subway","panera","starbucks","dunkin","kfc","popeyes","pizzahut","dominos","papajohns","littlecaesars","fiveguys","innout","shakeshack","sonic","arbys","jimmyjohns","jerseymikes","pandaexpress","qdoba","moes","wingstop","buffalowildwings","applebees","chilis","olivegarden","redlobster","outback","texasroadhouse","crackerbarrel","ihop","dennys","wafflehouse","culvers","whataburger","raisingcanes","zaxbys","bojangles","carlsjr","hardees","dairyqueen","baskinrobbins","coldstone","smoothieking","jambajuice","noodlescompany","chuys","elpolloloco","steaknshake","whitecastle","checkers","rallys","captainds","longjohnsilvers","tacojohns","cornerbakery","firehousesubs","potbelly","jasondeli")

    private fun restaurant(o: JSONObject): Boolean {
        if (o.has("restaurant") && o.opt("restaurant") is Boolean) return o.optBoolean("restaurant")
        val n = o.optString("name").lowercase().replace(Regex("[^a-z0-9]"), "")
        return CHAINS.any { n.contains(it) }
    }
    private fun missing(o: JSONObject) = FIELDS.filter { !o.has(it) || o.isNull(it) }
    private fun enrichable(o: JSONObject) = if (restaurant(o)) emptyList() else missing(o)
    private fun favs(ctx: Context): JSONArray = FoodLibrary.libJson(ctx).optJSONArray("favorites") ?: JSONArray()
    private fun list(a: JSONArray) = (0 until a.length()).mapNotNull { a.optJSONObject(it) }

    /** (foods that need enriching, foods with AI estimates). */
    fun stats(ctx: Context): Pair<Int, Int> { val l = list(favs(ctx)); return l.count { enrichable(it).isNotEmpty() } to l.count { (it.optJSONArray("aiEstimatedFields")?.length() ?: 0) > 0 } }

    @Volatile var running = false
    @Volatile var progress = ""

    /** Enrich All. Returns the message. Call off the main thread. */
    fun enrichAll(ctx: Context, onProgress: () -> Unit): String {
        if (!AiFood.hasKey(ctx)) return "Add your Anthropic key in Settings → AI food search first."
        val todo = list(favs(ctx)).filter { enrichable(it).isNotEmpty() }.map { it.optString("name") }
        if (todo.isEmpty()) return "Nothing to enrich — everything in your Food Database is either complete or marked as a restaurant food."
        running = true; var failed = 0
        try {
            todo.forEachIndexed { n, name ->
                progress = "⏳ Enriching ${n + 1}/${todo.size}…"; onProgress()
                try {
                    val lib = FoodLibrary.libJson(ctx); val a = lib.optJSONArray("favorites") ?: return@forEachIndexed
                    val f = list(a).firstOrNull { it.optString("name") == name } ?: return@forEachIndexed
                    val miss = enrichable(f); if (miss.isEmpty()) return@forEachIndexed
                    val schema = miss.joinToString("\n- ") { k -> val (l, u) = META[k]!!; "\"$k\": $l" + (if (u.isNotEmpty()) " in $u" else "") + " per serving as a plain number" }
                    fun r(k: String) = f.optDouble(k, 0.0)
                    val refs = listOfNotNull(r("cal").takeIf { it > 0 }?.let { "${Math.round(it)} calories" }, r("prot").takeIf { it > 0 }?.let { "${fmt(it)}g protein" },
                        r("fat").takeIf { it > 0 }?.let { "${fmt(it)}g fat" }, r("carbs").takeIf { it > 0 }?.let { "${fmt(it)}g carbs" }).joinToString(", ")
                    val sl = f.optString("servingLabel")
                    val prompt = "Estimate nutrition values for this specific food: $name" + (if (sl.isNotEmpty()) " ($sl)" else "") + ".\n" +
                        (if (refs.isNotEmpty()) "For reference, this serving has about $refs.\n" else "") +
                        "Respond with ONLY a single JSON object -- no markdown, no code fences, no explanation, nothing else before or after. The JSON must have these exact keys:\n" +
                        "- $schema\n" +
                        "Use your best nutritional knowledge for realistic values specific to this food. All numeric values must be numbers, not strings. Use 0 only when a value is genuinely negligible or absent for this food, never to mean unknown."
                    val txt = AiFood.askText(ctx, prompt, 1000)
                    val js = Regex("\\{[\\s\\S]*\\}").find(txt)?.value ?: throw Exception("Could not parse AI response.")
                    val v = JSONObject(js)
                    miss.forEach { k -> f.put(k, v.optDouble(k).takeIf { !it.isNaN() } ?: 0.0) }
                    val est = f.optJSONArray("aiEstimatedFields") ?: JSONArray()
                    val have = (0 until est.length()).map { est.optString(it) }.toMutableSet()
                    miss.forEach { if (have.add(it)) est.put(it) }
                    f.put("aiEstimatedFields", est)
                    FoodLibrary.setLib(ctx, lib)
                } catch (e: Exception) { failed++ }
            }
        } finally { running = false; progress = "" }
        FoodLibrary.libraryToWatch(ctx)
        return "Enriched ${todo.size - failed} of ${todo.size} foods." + if (failed > 0) " $failed failed -- check your AI key/usage and try again." else ""
    }
    private fun fmt(d: Double) = if (d == Math.floor(d)) d.toLong().toString() else d.toString()

    /** Undo All. Returns the message. */
    fun undoAll(ctx: Context): String {
        val lib = FoodLibrary.libJson(ctx); val a = lib.optJSONArray("favorites") ?: JSONArray()
        var foods = 0; var total = 0
        list(a).forEach { f -> val est = f.optJSONArray("aiEstimatedFields") ?: return@forEach; if (est.length() == 0) return@forEach
            foods++; for (i in 0 until est.length()) { f.remove(est.optString(i)); total++ }; f.put("aiEstimatedFields", JSONArray()) }
        if (foods == 0) return "No AI-estimated values to undo across your Food Database."
        FoodLibrary.setLib(ctx, lib); FoodLibrary.libraryToWatch(ctx)
        return "Removed $total AI-estimated value${if (total == 1) "" else "s"} across $foods food${if (foods == 1) "" else "s"}."
    }
    fun undoCount(ctx: Context): Pair<Int, Int> { val l = list(favs(ctx)).filter { (it.optJSONArray("aiEstimatedFields")?.length() ?: 0) > 0 }
        return l.sumOf { it.optJSONArray("aiEstimatedFields")!!.length() } to l.size }

    /** Rescan Foods: Healthy 7+ rebuilt from every saved day (latest version of each food, meal counts recounted), then barcode pictures. */
    fun rescan(ctx: Context): String {
        val all = NutriDay.allJson(ctx)
        val days = Nutrition.parse(JSONObject().apply { all.keys().forEach { put(it, all.get(it)) } }.toString()).associateBy { it.date }.toMutableMap()
        NutritionStore.days(ctx).forEach { days[it.date] = it }
        val latest = LinkedHashMap<String, Food.Item>(); val counts = HashMap<String, JSONObject>(); val ctxFor = HashMap<String, Pair<Boolean, String>>()
        FoodLibrary.favorites(ctx).forEach { latest[it.name.trim().lowercase()] = it }
        days.values.sortedBy { it.date }.forEach { d -> d.foods.forEach { f ->
            val k = f.name.trim().lowercase(); if (k.isEmpty()) return@forEach
            latest[k] = f; ctxFor[k] = try { NutritionEngine.runContext(ctx, d.date) } catch (e: Exception) { false to "" }
            val c = counts.getOrPut(k) { JSONObject() }; c.put(f.meal, c.optInt(f.meal) + 1)
        } }
        val old = FoodLibrary.healthyRaw(ctx).associateBy { it.optString("name").trim().lowercase() }
        val out = ArrayList<JSONObject>()
        latest.forEach { (k, f) ->
            val (run, hard) = ctxFor[k] ?: (false to "")
            val q = Food.quality(f, run, hard); if (q.score < 7 || q.confidencePct < 55) return@forEach
            val img = FoodLibrary.imageFor(ctx, f).ifEmpty { old[k]?.optString("image") ?: "" }
            out += Food.toJson(Food.inferSources(f).copy(image = img), withId = false, withPhoto = true).put("score", q.score).put("mealCounts", counts[k] ?: JSONObject())
        }
        FoodLibrary.setHealthyRaw(ctx, out.sortedWith(compareByDescending<JSONObject> { it.optDouble("score", 0.0) }.thenBy { it.optString("name") }))
        // Barcode pictures from Open Food Facts for saved foods that have none.
        var pics = 0
        try {
            val lib = FoodLibrary.libJson(ctx); var changed = false
            val codes = HashMap<String, String>()
            listOf("favorites", "healthy").forEach { key -> list(lib.optJSONArray(key) ?: JSONArray()).forEach { f ->
                val code = f.optString("barcode").replace(Regex("\\D"), ""); if (code.isEmpty() || f.optString("image").isNotEmpty()) return@forEach
                val url = codes.getOrPut(code) { try {
                    val c = (java.net.URL("https://world.openfoodfacts.org/api/v2/product/$code.json?fields=image_front_small_url,image_front_url,image_url").openConnection() as java.net.HttpURLConnection).apply { connectTimeout = 10_000; readTimeout = 15_000 }
                    if (c.responseCode != 200) "" else JSONObject(c.inputStream.bufferedReader().readText()).optJSONObject("product")?.let { p -> p.optString("image_front_small_url").ifEmpty { p.optString("image_front_url").ifEmpty { p.optString("image_url") } } } ?: ""
                } catch (e: Exception) { "" } }
                if (url.isNotEmpty()) { f.put("image", url); changed = true; pics++ }
            } }
            if (changed) FoodLibrary.setLib(ctx, lib)
        } catch (e: Exception) { }
        FoodLibrary.libraryToWatch(ctx); FoodLibrary.quickToWatch(ctx)
        return "✓ Rescan complete: ${out.size} healthy food${if (out.size == 1) "" else "s"} found · $pics barcode picture${if (pics == 1) "" else "s"} added."
    }

    /** Remove Dupes: the viewed day's food log (same food, meal, numbers and servings) and Food Database entries with the same name. */
    fun dedupe(ctx: Context): String {
        var removedDay = 0
        NutriDay.day(ctx)?.let { d ->
            val seen = HashSet<String>()
            d.foods.forEach { f ->
                val key = listOf(f.name.trim().lowercase(), f.meal, f.cal ?: 0.0, f.prot ?: 0.0, f.carbs ?: 0.0, f.fat ?: 0.0, f.servings).joinToString("|")
                if (!seen.add(key)) { HydrationSync.food(ctx, "foodDel", f, d.date); removedDay++ }
            }
        }
        val lib = FoodLibrary.libJson(ctx); val a = lib.optJSONArray("favorites") ?: JSONArray()
        val keep = JSONArray(); val seen = HashSet<String>(); var removedFav = 0
        for (i in 0 until a.length()) { val f = a.optJSONObject(i) ?: continue; val k = f.optString("name").trim().lowercase()
            if (k.isNotEmpty() && !seen.add(k)) removedFav++ else keep.put(f) }
        if (removedFav > 0) { lib.put("favorites", keep); FoodLibrary.setLib(ctx, lib); FoodLibrary.libraryToWatch(ctx) }
        val total = removedDay + removedFav
        if (total == 0) return "No duplicate foods found - everything already looks unique."
        val parts = listOfNotNull(if (removedDay > 0) "$removedDay from the food log" else null, if (removedFav > 0) "$removedFav from your Food Database" else null)
        return "✓ Removed $total duplicate${if (total == 1) "" else "s"} - ${parts.joinToString(", ")}."
    }
}
