package io.github.michealsizemore1.beyondstride

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Foods in the log. Values are per serving (like the Command Center); [servings] multiplies them.
 * [summarize] turns a day's foods into the same totals, meals and micronutrient score the Command Center shows.
 */
object Food {
    val MICROS = listOf("fiber", "sugar", "sodium", "vitC", "vitD", "calcium", "iron", "potassium", "caffeine", "magnesium", "vitB12", "zinc")

    data class Item(
        val id: String, val meal: String, val name: String, val servingLabel: String, val servings: Double,
        val cal: Double?, val prot: Double?, val fat: Double?, val carbs: Double?,
        val micros: Map<String, Double> = emptyMap(), val barcode: String = "",
        /** Picture: a product image link (https…) or a small saved photo (data:…). */
        val image: String = "",
        /** Food quality inputs from the Command Center: "support" / "limit" / "unknown" per macro, and ingredient / category text. */
        val proteinSource: String = "", val carbSource: String = "", val fatSource: String = "", val qtext: String = "",
    ) {
        fun total(v: Double?) = (v ?: 0.0) * servings
        val kcal get() = total(cal).roundToInt()
        fun line() = "${fmt(servings)} × $servingLabel · ${kcal} kcal"
    }

    fun fmt(v: Double) = if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(java.util.Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')

    fun newId() = "f" + System.currentTimeMillis() + "_" + (Math.random() * 1_679_616).toInt().toString(36)

    private fun d(o: JSONObject, k: String): Double? = if (o.has(k) && !o.isNull(k)) o.optDouble(k).takeIf { !it.isNaN() } else null

    fun fromJson(o: JSONObject, meal: String? = null): Item {
        val m = HashMap<String, Double>()
        MICROS.forEach { k -> d(o, k)?.let { m[k] = it } }
        return Item(o.optString("id").ifEmpty { newId() }, meal ?: o.optString("meal", "snack").ifEmpty { "snack" },
            o.optString("name", "Food"), o.optString("servingLabel", "1 serving").ifEmpty { "1 serving" },
            o.optDouble("servings", 1.0).takeIf { !it.isNaN() && it > 0 } ?: 1.0,
            d(o, "cal"), d(o, "prot"), d(o, "fat"), d(o, "carbs"), m, o.optString("barcode"), o.optString("image"),
            o.optString("proteinSource"), o.optString("carbSource"), o.optString("fatSource"),
            o.optString("qtext").ifEmpty { listOf(o.optString("ingredientsText"), o.optString("categoriesText")).filter { it.isNotEmpty() }.joinToString(" ").take(600) })
    }

    /** [withPhoto]: keep a saved photo (data:…); links are always kept. Photos stay out of the daily log to keep it small. */
    fun toJson(i: Item, withId: Boolean = true, withPhoto: Boolean = false): JSONObject = JSONObject().apply {
        if (withId) { put("id", i.id); put("meal", i.meal) }
        put("name", i.name); put("servingLabel", i.servingLabel); put("servings", i.servings)
        i.cal?.let { put("cal", it) }; i.prot?.let { put("prot", it) }; i.fat?.let { put("fat", it) }; i.carbs?.let { put("carbs", it) }
        i.micros.forEach { (k, v) -> put(k, v) }
        if (i.barcode.isNotEmpty()) put("barcode", i.barcode)
        if (i.proteinSource.isNotEmpty()) put("proteinSource", i.proteinSource); if (i.carbSource.isNotEmpty()) put("carbSource", i.carbSource)
        if (i.fatSource.isNotEmpty()) put("fatSource", i.fatSource); if (i.qtext.isNotEmpty()) put("qtext", i.qtext)
        if (i.image.startsWith("http") || (withPhoto && i.image.startsWith("data:"))) put("image", i.image)
    }

    fun listJson(items: List<Item>) = JSONArray().apply { items.forEach { put(toJson(it)) } }
    fun parseList(a: JSONArray?): List<Item> = if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let { o -> fromJson(o) } }

    private fun r1(v: Double) = (v * 10).roundToInt() / 10.0

    /**
     * The day rebuilt from [items] — totals, meals, micronutrients and score — keeping [base]'s target,
     * fluids and weights. Same math as the Command Center's Nutrition tab.
     */
    fun summarize(base: Nutrition.Day, items: List<Item>): Nutrition.Day {
        fun sum(f: (Item) -> Double?) = items.sumOf { it.total(f(it)) }
        fun micro(k: String) = items.sumOf { it.total(it.micros[k]) }
        val cal = sum { it.cal }.roundToInt(); val prot = r1(sum { it.prot }); val carbs = r1(sum { it.carbs }); val fat = r1(sum { it.fat })
        val fiber = r1(micro("fiber")); val sodium = micro("sodium").roundToInt()
        val meals = HashMap<String, Int>()
        items.forEach { meals[it.meal] = (meals[it.meal] ?: 0) + it.total(it.cal).roundToInt() }
        val sportsSugar = items.filter { it.meal == "sports" }.sumOf { it.total(it.micros["sugar"]) }
        val naturalSugar = items.filter { naturalSugar(it) }.sumOf { it.total(it.micros["sugar"]) }
        val sugarCeiling = 36 + sportsSugar + naturalSugar
        val list = listOf(
            Nutrition.Micro("Iron", "iron", r1(micro("iron")), 14.0, 0.0, "mg"),
            Nutrition.Micro("Calcium", "calcium", r1(micro("calcium")), 1000.0, 0.0, "mg"),
            Nutrition.Micro("Vitamin D", "vitD", r1(micro("vitD")), 15.0, 0.0, "mcg"),
            Nutrition.Micro("Magnesium", "magnesium", r1(micro("magnesium")), 483.0, 0.0, "mg"),
            Nutrition.Micro("Vitamin B12", "vitB12", r1(micro("vitB12")), 2.4, 0.0, "mcg"),
            Nutrition.Micro("Sodium", "sodium", sodium.toDouble(), 2300.0, 4000.0, "mg"),
            Nutrition.Micro("Potassium", "potassium", r1(micro("potassium")), 3400.0, 0.0, "mg"),
            Nutrition.Micro("Zinc", "zinc", r1(micro("zinc")), 15.0, 0.0, "mg"),
            Nutrition.Micro("Vitamin C", "vitC", r1(micro("vitC")), 90.0, 0.0, "mg"),
            Nutrition.Micro("Fiber", "fiber", fiber, 28.0, 0.0, "g"),
            Nutrition.Micro("Sugar", "sugar", r1(micro("sugar")), sugarCeiling, sugarCeiling, "g"),
            Nutrition.Micro("Caffeine", "caffeine", micro("caffeine").roundToInt().toDouble(), 400.0, 400.0, "mg"),
        )
        val score = if (items.isEmpty()) 0.0 else {
            val comp = list.map { m -> if (m.max > 0 && m.value > m.max) maxOf(0.0, 1 - (m.value - m.max) / m.max) else maxOf(0.0, minOf(1.0, m.value / m.target)) }
            r1(1 + comp.average() * 9)
        }
        val shortItems = items.take(80).map { Nutrition.Item(it.meal, it.name, it.total(it.cal).roundToInt(), r1(it.total(it.prot)), r1(it.total(it.carbs)), r1(it.total(it.fat))) }
        return base.copy(cal = cal, prot = prot, carbs = carbs, fat = fat, fiber = fiber, sodium = sodium, meals = meals, items = shortItems,
            count = items.size, micros = list, microScore = score, foods = items)
    }

    // ── Drinks logged as foods count toward fluids, like the Command Center: Sports Nutrition foods → Sports drinks,
    //    Liquids foods → Other drinks, using the fluid ounces in the serving (caffeinated drinks don't count). ──
    private val CAF = Regex("\\b(coffee|espresso|latte|cappuccino|mocha|americano|cold\\s*brew|black\\s*tea|green\\s*tea|energy\\s*drinks?|red\\s*bull|monster(?:\\s*energy)?|celsius|bang\\s*energy|rockstar|reign|ghost\\s*energy|c4\\s*energy|alani\\s*nu)\\b")
    fun caffeinated(i: Item) = CAF.containsMatchIn(i.name.lowercase()) || (i.micros["caffeine"] ?: 0.0) > 0

    /** Fluid ounces in a serving label: "20 fl oz", "1 Bottle (591 mL)", "2 cups", "500 ml", "8 oz water"…; 0 if none. */
    fun fluidOz(label: String): Double {
        val s = label.lowercase()
        Regex("^\\s*(\\d+(?:\\.\\d+)?)\\s*(fl\\.?\\s*oz|fluid\\s*ounces?|oz|ounces?|milliliters?|millilitres?|ml|liters?|litres?|cups?)\\b").find(s)?.let { m ->
            val q = m.groupValues[1].toDouble(); val u = m.groupValues[2]
            return when { u.contains("oz") || u.contains("ounce") -> q; u.contains("cup") -> q * 8; u.contains("ml") || u.contains("milli") -> q / 29.5735; else -> q * 33.814 }
        }
        Regex("^\\s*(\\d+(?:\\.\\d+)?)[^()]*\\((\\d+(?:\\.\\d+)?)\\s*(fl\\.?\\s*oz|fluid\\s*ounces?|ml|milliliters?|millilitres?|liters?|litres?)\\)").find(s)?.let { m ->
            val c = m.groupValues[1].toDouble(); val v = m.groupValues[2].toDouble(); val u = m.groupValues[3]
            val each = when { u.contains("fl") || u.contains("fluid") -> v; u.contains("ml") || u.contains("milli") -> v / 29.5735; else -> v * 33.814 }
            if (each > 0) return c * each
        }
        Regex("(\\d+(?:\\.\\d+)?)\\s*(fl\\.?\\s*oz|oz|ounces?|milliliters?|millilitres?|ml|liters?|litres?)\\s*(?:of\\s*)?water\\b").find(s)?.let { m ->
            val q = m.groupValues[1].toDouble(); val u = m.groupValues[2]
            return when { u.contains("ml") || u.contains("milli") -> q / 29.5735; u.contains("liter") || u.contains("litre") -> q * 33.814; else -> q }
        }
        return 0.0
    }

    /** Ounces of non-caffeinated drinks logged in [meal]. */
    fun liquidOz(items: List<Item>, meal: String): Int = Math.round(items.filter { it.meal == meal && !caffeinated(it) }.sumOf { i ->
        var oz = fluidOz(i.servingLabel)
        if (oz == 0.0) Products.SPORTS.firstOrNull { i.name.lowercase().contains(it.line.lowercase()) }?.let { oz = fluidOz(it.serving) }
        oz * i.servings
    }).toInt()

    // ── Food quality — the Command Center's runnerFoodQuality / inferFoodMacroSources / isNaturalSugarFood ──
    private fun allText(i: Item) = listOf(i.name, i.qtext).filter { it.isNotEmpty() }.joinToString(" ").lowercase()

    /** Whole fruit or plain dairy: its sugar isn't counted as added sugar. */
    fun naturalSugar(i: Item): Boolean {
        val t = allText(i); if (t.isEmpty()) return false
        if (Regex("\\b(chocolate|vanilla|caramel|cookie|cookies|flavored|flavoured|sweetened|sweet cream|ice cream|milkshake|shake|pudding|syrup|candy|yogurt covered|fruit snack|fruit leather|fruit roll|juice|nectar|smoothie|jam|jelly|preserves?|in syrup|honey)\\b").containsMatchIn(t)) return false
        return Regex("\\b(banana|bananas|apple|apples|orange|oranges|grape|grapes|berry|berries|strawberry|strawberries|blueberry|blueberries|raspberry|raspberries|blackberry|blackberries|melon|watermelon|cantaloupe|honeydew|mango|mangoes|pineapple|kiwi|peach|peaches|plum|plums|cherry|cherries|pear|pears|grapefruit|apricot|apricots|pomegranate|nectarine|tangerine|clementine|fruit salad)\\b").containsMatchIn(t) ||
            Regex("\\b(raisin|raisins|unsweetened dried fruit|dried apricot|dried mango|dried fig|figs?)\\b").containsMatchIn(t) ||
            Regex("\\b(milk|yogurt|kefir|cottage cheese)\\b").containsMatchIn(t)
    }

    private val P_SUP = listOf("chicken breast","chicken","turkey","fish","salmon","tuna","tilapia","cod","halibut","mahi mahi","shrimp","lean beef","sirloin","bison","pork tenderloin","egg white","egg","greek yogurt","yogurt","cottage cheese","milk protein","casein","milk","tofu","tempeh","edamame","bean","lentil","chickpea","pea protein","soy protein","protein isolate","protein concentrate","protein powder","protein shake","whey protein","whey","seitan")
    private val P_LIM = listOf("bacon","sausage","hot dog","pepperoni","salami","bologna","fried chicken","processed meat","mechanically separated","cured meat","pork rind","rib","cheese")
    private val C_SUP = listOf("oatmeal","oat","brown rice","wild rice","quinoa","whole grain","whole wheat","whole rye","barley","farro","bran","bean","lentil","chickpea","sweet potato","potato","corn","sweet corn","fruit","dried fruit","raisin","apple","banana","berry","berries","strawberry","strawberries","blueberry","blueberries","raspberry","raspberries","blackberry","blackberries","grape","pear","peach","peaches","mango","mangoes","pineapple","watermelon","cantaloupe","kiwi","plum","cherry","cherries","grapefruit","apricot","pomegranate","orange","vegetable","broccoli","spinach","carrot","kale","cauliflower","zucchini","cucumber","bell pepper","asparagus","pea","cabbage","brussels sprout","tomato","tomatoes","squash","green bean")
    private val C_LIM = listOf("candy","soda","soft drink","juice","sports drink","energy gel","gel","chew","sugar","cane sugar","corn syrup","high fructose corn syrup","honey","syrup","enriched wheat flour","refined flour","white flour","cookie","cake","pastry","donut","doughnut","white bread","white rice","sweetened cereal")
    private val F_SUP = listOf("olive oil","canola oil","sunflower oil","safflower oil","soybean oil","avocado oil","sesame oil","avocado","almond","walnut","peanut","cashew","pistachio","pecan","hazelnut","macadamia","nut butter","seed","chia","flax","salmon","tuna","mackerel","sardine","trout","olive")
    private val F_LIM = listOf("butter","lard","shortening","coconut oil","palm oil","palm kernel oil","hydrogenated oil","partially hydrogenated oil","cream","cheese","bacon","sausage","pepperoni","fried")
    private fun choice(text: String, sup: List<String>, lim: List<String>): String {
        val n = " " + text.lowercase().replace(Regex("[^a-z0-9]+"), " ").replace(Regex("\\s+"), " ").trim() + " "
        fun first(terms: List<String>) = terms.mapNotNull { t -> n.indexOf(" $t ").takeIf { it >= 0 } ?: n.indexOf(" ${t}s ").takeIf { it >= 0 } }.minOrNull() ?: Int.MAX_VALUE
        val a = first(sup); val b = first(lim)
        return if (a == Int.MAX_VALUE && b == Int.MAX_VALUE) "" else if (a <= b) "support" else "limit"
    }
    /** Fills in missing macro sources from the food's name and ingredients (like the Command Center does on its page). */
    fun inferSources(i: Item): Item {
        val t = listOf(i.name, i.qtext).filter { it.isNotEmpty() }.joinToString(" ")
        return i.copy(proteinSource = i.proteinSource.ifEmpty { if ((i.prot ?: 0.0) > 0) choice(t, P_SUP, P_LIM) else "" },
            carbSource = i.carbSource.ifEmpty { if ((i.carbs ?: 0.0) > 0) choice(t, C_SUP, C_LIM) else "" },
            fatSource = i.fatSource.ifEmpty { if ((i.fat ?: 0.0) > 0) choice(t, F_SUP, F_LIM) else "" })
    }

    data class Quality(val score: Double, val confidencePct: Int)
    /** One food's runner quality (1–10) and data confidence; [runDay] / [hard] are the day's training. */
    fun quality(item: Item, runDay: Boolean, hard: String): Quality {
        val i = inferSources(item)
        val cal = Math.max(0.0, i.cal ?: 0.0); val prot = Math.max(0.0, i.prot ?: 0.0); val carbs = Math.max(0.0, i.carbs ?: 0.0); val fat = Math.max(0.0, i.fat ?: 0.0)
        val fiber = Math.max(0.0, i.micros["fiber"] ?: 0.0); val sugar = Math.max(0.0, i.micros["sugar"] ?: 0.0); val sodium = Math.max(0.0, i.micros["sodium"] ?: 0.0)
        val sports = i.meal == "sports" || Regex("\\b(energy gel|gu energy|gels?|chews?|electrolyte|sports drink|gatorade|powerade|nuun|tailwind|skratch|maurten|clif shot|hammer gel|recovery drink|bcaa)\\b", RegexOption.IGNORE_CASE).containsMatchIn(i.name)
        var s = 5.0
        fun src(amount: Double, source: String, sup: Double, lim: Double) { if (amount <= 0) return; if (source == "support") s += sup else if (source == "limit") s += lim }
        src(prot, i.proteinSource, 1.0, -1.0); src(carbs, i.carbSource, 1.0, if (sports) 0.5 else -1.0); src(fat, i.fatSource, 0.75, -1.0)
        if (fiber >= 5) s += 1.0 else if (fiber >= 3) s += 0.6
        if (cal > 0 && prot / cal >= 0.1) s += 1.0 else if (cal > 0 && prot / cal >= 0.05) s += 0.4
        if (!sports && sugar >= 20 && fiber < 3) s -= 1.0 else if (!sports && sugar >= 10 && fiber < 2) s -= 0.5
        if (!sports && sodium >= 700) s -= 0.5
        val t = allText(i)
        if (Regex("\\b(vegetable|fruit|dried fruit|raisin|raisins|berries|strawberry|strawberries|blueberry|blueberries|raspberry|raspberries|blackberry|blackberries|grape|grapes|pear|pears|peach|peaches|mango|pineapple|watermelon|banana|apple|orange|oat|oatmeal|whole grain|whole wheat|bean|lentil|quinoa|brown rice|sweet potato|potato|potatoes|corn|carrot|carrots|kale|cauliflower|zucchini|cucumber|pepper|peppers|asparagus|pea|peas|cabbage|tomato|tomatoes|squash|salmon|tuna|tilapia|cod)\\b").containsMatchIn(t)) s += 0.75
        if (Regex("\\b(oatmeal|rolled oats?|steel[ -]?cut oats?|old fashioned oats?)\\b").containsMatchIn(t) && sugar <= 10) s += 0.75
        if (Regex("\\b(raisin|raisins|unsweetened dried fruit)\\b").containsMatchIn(t) && !Regex("\\b(candy|chocolate|yogurt covered|added sugar|corn syrup)\\b").containsMatchIn(t)) s += 1.0
        if (Regex("\\b(potato|potatoes|russet|yukon gold)\\b").containsMatchIn(t) && fat <= 5 && !Regex("\\b(fries|french fry|chips?|hash browns?|tater tots?|loaded|cheesy|scalloped|au gratin|fried)\\b").containsMatchIn(t)) s += 1.5
        if (Regex("\\b(corn|sweet corn|corn kernels?)\\b").containsMatchIn(t) && fat <= 5 && !Regex("\\b(corn chips?|tortilla chips?|corn dog|creamed corn|caramel corn|popcorn|corn syrup|fried)\\b").containsMatchIn(t)) s += 1.25
        if ((i.micros["potassium"] ?: 0.0) >= 600) s += 0.5
        if (Regex("\\b(candy|soda|donut|doughnut|pastry|fried|hydrogenated|corn syrup)\\b").containsMatchIn(t) && !sports) s -= 0.75
        if (runDay && sports && carbs >= 15) s += 0.75
        if (runDay && i.meal == "breakfast" && carbs >= 25) s += 0.4
        if (runDay && i.meal == "breakfast" && prot >= 15) s += 0.4
        s = Math.max(1.0, Math.min(10.0, Math.round(s * 10) / 10.0))
        var complete = 0; var checks = 7
        if (i.cal != null) complete++; if (i.prot != null) complete++; if (i.fat != null) complete++; if (i.carbs != null) complete++
        listOf("fiber", "sugar", "sodium").forEach { if (i.micros.containsKey(it)) complete++ }
        listOf(prot to i.proteinSource, carbs to i.carbSource, fat to i.fatSource).forEach { (a, src) -> if (a > 0) { checks++; if (src.isNotEmpty() && src != "unknown") complete++ } }
        return Quality(s, Math.round(complete * 100.0 / checks).toInt())
    }

    data class DayQuality(val score: Double, val label: String, val note: String)
    fun label(score: Double) = when { score >= 9 -> "Excellent"; score >= 7 -> "Good"; score >= 5 -> "Fair"; score >= 3 -> "Low"; else -> "Poor" }
    /** "Daily food quality": calorie-weighted across every food logged today. */
    fun dayQuality(d: Nutrition.Day): DayQuality? {
        if (d.foods.isEmpty()) return null
        var tot = 0.0; var w = 0.0; var conf = 0.0
        d.foods.forEach { f -> val q = quality(f, d.qRun, d.qHard); val sv = Math.max(0.01, f.servings); val wt = Math.max(0.0, (f.cal ?: 0.0) * sv).takeIf { it > 0 } ?: sv
            tot += q.score * wt; conf += q.confidencePct * wt; w += wt }
        val score = if (w > 0) tot / w else 5.0; val c = if (w > 0) Math.round(conf / w).toInt() else 0
        val ctx = if (d.qRun) (if (d.qHard.isNotEmpty()) "${d.qHard} workout" else "run day") else "rest / recovery day"
        return DayQuality(score, label(score), "Across ${d.foods.size} food item${if (d.foods.size == 1) "" else "s"} · $ctx · data confidence: " +
            (if (c >= 80) "High" else if (c >= 55) "Medium" else "Low") + " ($c%)")
    }
}
