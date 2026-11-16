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
        // Your Food Database values win over an older logged copy (they're the checked / corrected ones).
        FoodLibrary.favorites(ctx).forEach { fv -> val k = fv.name.trim().lowercase(); if (latest.containsKey(k)) latest[k] = fv.copy(meal = latest[k]!!.meal) }
        latest.forEach { (k, f0) ->
            val f = applyFix(ctx, f0)
            val (run, hard) = ctxFor[k] ?: (false to "")
            val q = Food.quality(f, run, hard)
            if (!healthyOk(ctx, f.name, q.score, q.confidencePct)) return@forEach
            val img = FoodLibrary.imageFor(ctx, f).ifEmpty { old[k]?.optString("image") ?: "" }
            out += Food.toJson(Food.inferSources(f).copy(image = img), withId = false, withPhoto = true).put("score", maxOf(q.score, if (override(ctx, f.name) == "yes") 7.0 else 0.0)).put("mealCounts", counts[k] ?: JSONObject())
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

    // ── 🩺 Check every food (AI review of every saved and logged food) ──
    private fun cp(ctx: Context) = ctx.getSharedPreferences("foodcheck", 0)
    private fun fixes(ctx: Context): JSONObject = try { JSONObject(cp(ctx).getString("fix", "{}")) } catch (e: Exception) { JSONObject() }
    private fun overrides(ctx: Context): JSONObject = try { JSONObject(cp(ctx).getString("healthy", "{}")) } catch (e: Exception) { JSONObject() }
    /** "yes" (belongs in Healthy 7+ even if the score is a bit under 7), "no" (keep it out), or "". */
    fun override(ctx: Context, name: String) = overrides(ctx).optString(name.trim().lowercase())
    fun healthyOk(ctx: Context, name: String, score: Double, conf: Int): Boolean = when (override(ctx, name)) {
        "no" -> false; "yes" -> score >= 5.5; else -> score >= 7 && conf >= 55 }
    /** Corrected values for a logged food that isn't in the Food Database (so later logs pick them up too). */
    fun applyFix(ctx: Context, f: Food.Item): Food.Item {
        val x = fixes(ctx).optJSONObject(f.name.trim().lowercase()) ?: return f
        val m = HashMap(f.micros); Food.MICROS.forEach { k -> if (x.has(k)) m[k] = x.optDouble(k) }
        fun v(k: String, cur: Double?) = if (x.has(k)) x.optDouble(k) else cur
        return f.copy(cal = v("cal", f.cal), prot = v("prot", f.prot), fat = v("fat", f.fat), carbs = v("carbs", f.carbs), micros = m)
    }
    fun lastCheck(ctx: Context) = cp(ctx).getString("report", "") ?: ""
    fun canUndoCheck(ctx: Context) = cp(ctx).contains("before")

    /** Impossible values fixed without AI: negatives → 0, sugar or fiber above carbs → capped at carbs. Returns the changes. */
    private fun ruleFix(o: JSONObject): List<String> {
        val ch = ArrayList<String>()
        (FIELDS).forEach { k -> if (o.has(k) && o.optDouble(k, 0.0) < 0) { ch += "$k ${fmt(o.optDouble(k))}→0"; o.put(k, 0.0) } }
        val carbs = o.optDouble("carbs", -1.0)
        if (carbs >= 0) listOf("sugar", "fiber").forEach { k -> val v = o.optDouble(k, -1.0); if (v > carbs + 0.5) { ch += "$k ${fmt(v)}→${fmt(carbs)} (can't be more than carbs)"; o.put(k, carbs) } }
        return ch
    }

    /**
     * Checks every food in your Food Database and every food you've logged: impossible values are fixed, then AI reviews each
     * food's numbers for its serving (corrects only clear mistakes — more than about 25% off) and whether it belongs on the
     * Healthy 7+ list. Then Healthy 7+ is rebuilt. Everything before the check is kept for "Undo check". Call off the main thread.
     */
    fun checkAll(ctx: Context, onProgress: () -> Unit): String {
        if (!AiFood.hasKey(ctx)) return "Add your Anthropic key in Settings → AI food search first."
        running = true
        try {
            val before = FoodLibrary.libJson(ctx).toString()
            cp(ctx).edit().putString("before", before).putString("beforeFix", fixes(ctx).toString()).putString("beforeHealthy", overrides(ctx).toString()).apply()
            val oldHealthy = FoodLibrary.healthyRaw(ctx).map { it.optString("name") }.toSet()
            val lib = FoodLibrary.libJson(ctx); val favA = lib.optJSONArray("favorites") ?: JSONArray()
            val favs = list(favA)
            // Foods only in your log (not in the Food Database): the latest logged copy of each.
            val all = NutriDay.allJson(ctx)
            val days = Nutrition.parse(JSONObject().apply { all.keys().forEach { put(it, all.get(it)) } }.toString()).associateBy { it.date }.toMutableMap()
            NutritionStore.days(ctx).forEach { days[it.date] = it }
            val favNames = favs.map { it.optString("name").trim().lowercase() }.toSet()
            val logged = LinkedHashMap<String, JSONObject>()
            days.values.sortedBy { it.date }.forEach { d -> d.foods.forEach { f -> val k = f.name.trim().lowercase(); if (k.isNotEmpty() && k !in favNames) logged[k] = Food.toJson(applyFix(ctx, f), withId = false) } }
            val items = favs.map { true to it } + logged.values.map { false to it }
            val report = ArrayList<String>(); var corrected = 0
            val fx = fixes(ctx); val ov = overrides(ctx)
            items.forEach { (_, o) -> val ch = ruleFix(o); if (ch.isNotEmpty()) { corrected++; report += "• ${o.optString("name")}: " + ch.joinToString(", ") } }
            val batches = items.chunked(12)
            var failed = 0
            batches.forEachIndexed { bi, batch ->
                progress = "⏳ Checking foods ${bi * 12 + 1}–${minOf(items.size, bi * 12 + batch.size)} of ${items.size}…"; onProgress()
                try {
                    val arr = JSONArray()
                    batch.forEachIndexed { i, (_, o) -> arr.put(JSONObject().put("n", i).put("name", o.optString("name")).put("serving", o.optString("servingLabel", "1 serving"))
                        .apply { FIELDS.forEach { k -> if (o.has(k)) put(k, o.optDouble(k)) }; if (o.optString("barcode").isNotEmpty()) put("fromLabel", true); if (restaurant(o)) put("restaurant", true) }) }
                    val prompt = "You are checking a runner's food database. Values are PER SERVING (the serving text says how much). Units: cal kcal; prot, fat, carbs, fiber, sugar g; sodium, potassium, calcium, iron, vitC, magnesium, zinc, caffeine mg; vitD, vitB12 mcg.\n" +
                        "For each food:\n1) Correct only values that are clearly wrong for that food and serving (more than ~25% off typical), impossible, or obviously mis-entered (e.g. per-100g numbers on a 1-cup serving, mg vs g mix-ups, calories that don't fit the macros). Foods marked fromLabel came from a package label and restaurant foods from the chain: trust them unless impossible. Don't touch values that are reasonable. Don't add values that are missing.\n" +
                        "2) healthy: \"yes\" if it's a nutritious everyday food for an endurance runner (whole or minimally processed: fruit, vegetables, legumes, whole grains, lean protein, fish, eggs, dairy/yogurt, nuts, seeds, olive oil, oats, potatoes, rice, etc.), \"no\" if it isn't (candy, desserts, pastries, chips, fried or fast food, sugary drinks, alcohol, heavily processed snacks), \"\" if it depends. Sports fuel (gels, chews, drinks) = \"\".\n" +
                        "Return ONLY JSON, no other text: {\"foods\":[{\"n\":0,\"fix\":{\"field\":number},\"why\":\"short reason\",\"healthy\":\"yes|no|\"}]} — include every n; use an empty fix {} when nothing is wrong.\n\nFOODS:\n" + arr.toString()
                    val txt = AiFood.askChat(ctx, "You are a careful sports dietitian checking nutrition data. Be conservative: only correct clear errors.", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)), 3000)
                    val js = JSONObject(Regex("\\{[\\s\\S]*\\}").find(txt)?.value ?: throw Exception("no JSON"))
                    val res = js.optJSONArray("foods") ?: JSONArray()
                    for (r in 0 until res.length()) {
                        val ro = res.optJSONObject(r) ?: continue; val (isFav, o) = batch.getOrNull(ro.optInt("n", -1)) ?: continue
                        val key = o.optString("name").trim().lowercase()
                        ro.optString("healthy").takeIf { it == "yes" || it == "no" }?.let { ov.put(key, it) } ?: ov.remove(key)
                        val fix = ro.optJSONObject("fix") ?: continue; if (fix.length() == 0) continue
                        val ch = ArrayList<String>()
                        fix.keys().forEach { k -> if (k !in FIELDS || !o.has(k)) return@forEach
                            val nv = fix.optDouble(k); if (nv.isNaN() || nv < 0) return@forEach
                            val ov0 = o.optDouble(k, 0.0); if (Math.abs(nv - ov0) < maxOf(1.0, ov0 * 0.15)) return@forEach
                            ch += "${META[k]?.first ?: k} ${fmt(ov0)}→${fmt(Math.round(nv * 10) / 10.0)}"; o.put(k, Math.round(nv * 10) / 10.0) }
                        if (ch.isEmpty()) continue
                        corrected++; report += "• ${o.optString("name")}: " + ch.joinToString(", ") + (ro.optString("why").takeIf { it.isNotBlank() }?.let { " — $it" } ?: "")
                        o.put("aiChecked", true)
                        if (!isFav) fx.put(key, JSONObject().apply { FIELDS.forEach { k -> if (o.has(k)) put(k, o.optDouble(k)) } })
                    }
                } catch (e: Exception) { failed++ }
            }
            // Save the Food Database (the favorite objects were corrected in place), fixes and verdicts.
            lib.put("favorites", JSONArray().apply { favs.forEach { put(it) } }); FoodLibrary.setLib(ctx, lib)
            cp(ctx).edit().putString("fix", fx.toString()).putString("healthy", ov.toString()).apply()
            rescan(ctx)
            val newHealthy = FoodLibrary.healthyRaw(ctx).map { it.optString("name") }.toSet()
            val added = newHealthy - oldHealthy; val removed = oldHealthy - newHealthy
            val head = "✓ Checked ${items.size} foods (${favs.size} in your Food Database, ${logged.size} more from your log).\n" +
                "$corrected corrected · Healthy 7+: ${newHealthy.size} (" + "+${added.size} added, −${removed.size} removed)" +
                (if (failed > 0) "\n⚠️ ${failed} group(s) of 12 couldn't be checked — run it again to finish." else "")
            val body = (if (report.isNotEmpty()) "\n\nCORRECTED\n" + report.joinToString("\n") else "\n\nNo values needed correcting.") +
                (if (added.isNotEmpty()) "\n\nADDED TO HEALTHY 7+\n" + added.sorted().joinToString("\n") { "• $it" } else "") +
                (if (removed.isNotEmpty()) "\n\nREMOVED FROM HEALTHY 7+\n" + removed.sorted().joinToString("\n") { "• $it" + if (override(ctx, it) == "no") " (not an everyday healthy food)" else " (score under 7)" } else "")
            val out = head + body
            cp(ctx).edit().putString("report", out).putLong("at", System.currentTimeMillis()).apply()
            return out
        } finally { running = false; progress = "" }
    }

    /** Puts the Food Database, corrections and Healthy 7+ back the way they were before the last check. */
    fun undoCheck(ctx: Context): String {
        val b = cp(ctx).getString("before", null) ?: return "Nothing to undo."
        FoodLibrary.setLib(ctx, JSONObject(b))
        cp(ctx).edit().putString("fix", cp(ctx).getString("beforeFix", "{}")).putString("healthy", cp(ctx).getString("beforeHealthy", "{}")).remove("before").remove("report").apply()
        FoodLibrary.libraryToWatch(ctx); FoodLibrary.quickToWatch(ctx)
        return "✓ Food Database and Healthy 7+ restored to how they were before the check."
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
