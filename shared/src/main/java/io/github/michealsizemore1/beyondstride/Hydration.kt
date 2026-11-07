package io.github.michealsizemore1.beyondstride

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Fluids and weights entered in BeyondStride (phone or watch). They're kept here as the day's values and
 * shown at once; each change is also queued for the Command Center (the phone posts it to Firebase), which
 * applies it like a tap on its own page. Once the Command Center has taken every change for a day, its
 * shared totals (which also hold anything entered there) are used again.
 */
object Hydration {
    val FLUIDS = listOf("water", "sports", "other")
    const val STEP = 8   // oz per tap (one cup)

    /** A change waiting for the Command Center. [id] is Firebase's key once posted. */
    data class Change(val date: String, val f: String, val d: Int, val v: Double, val at: Long, val id: String?, val x: String? = null)
    val FOOD_OPS = listOf("foodAdd", "foodEdit", "foodDel", "mealSave", "calSettings", "libAction", "libDelete", "actSave", "actDel", "actSettings", "shoeSave", "shoeDel", "planAction", "wrnSave", "actImport")

    /** Queues a change that isn't tied to the day's values (a saved meal, calorie-target settings). */
    @Synchronized fun queue(ctx: Context, f: String, x: JSONObject, date: String = Readiness.today()) =
        saveChanges(ctx, changes(ctx) + Change(date, f, 0, 0.0, System.currentTimeMillis(), null, x.toString()))

    private fun p(ctx: Context) = ctx.getSharedPreferences("hydration", 0)

    fun changes(ctx: Context): List<Change> = try {
        val a = JSONArray(p(ctx).getString("changes", "[]"))
        (0 until a.length()).map { i -> a.getJSONObject(i).let {
            Change(it.getString("date"), it.getString("f"), it.optInt("d"), it.optDouble("v", 0.0), it.optLong("at"), it.optString("id").ifEmpty { null }, it.optString("x").ifEmpty { null })
        } }
    } catch (e: Exception) { emptyList() }

    fun saveChanges(ctx: Context, list: List<Change>) {
        val a = JSONArray()
        list.forEach { c -> a.put(JSONObject().put("date", c.date).put("f", c.f).put("d", c.d).put("v", c.v).put("at", c.at).put("id", c.id ?: "").put("x", c.x ?: "")) }
        p(ctx).edit().putString("changes", a.toString()).apply()
    }

    /** Local values per day: {"2026-11-06": {"water":8,"sports":0,"other":20,"weight":172.4,"postWeight":171.2}}. */
    private fun local(ctx: Context): JSONObject = try { JSONObject(p(ctx).getString("days", "{}")) } catch (e: Exception) { JSONObject() }

    /** Drops local values for days with nothing still waiting (the Command Center's copy has them now). */
    fun settle(ctx: Context) {
        // Only fluids, weights and foods change a day's values; the other changes (meals, activities, plans…) don't.
        val waiting = changes(ctx).filter { it.f !in FOOD_OPS || it.f == "foodAdd" || it.f == "foodEdit" || it.f == "foodDel" }.map { it.date }.toSet()
        val l = local(ctx); val drop = l.keys().asSequence().filter { it !in waiting }.toList()
        if (drop.isEmpty()) return
        drop.forEach { l.remove(it) }
        p(ctx).edit().putString("days", l.toString()).apply()
    }

    /** The day as shown: the Command Center's copy with this app's newer values on top. */
    fun overlay(ctx: Context, days: List<Nutrition.Day>): List<Nutrition.Day> {
        val l = local(ctx); if (l.length() == 0) return days
        val out = days.toMutableList()
        l.keys().forEach { date ->
            val o = l.getJSONObject(date)
            val i = out.indexOfFirst { it.date == date }
            val base = if (i >= 0) out[i] else blank(date)
            val w = o.optInt("water", base.water); val s = o.optInt("sports", base.sportsOz); val ot = o.optInt("other", base.otherOz)
            var d = base.copy(water = w, sportsOz = s, otherOz = ot, waterOz = w + s + ot,
                weight = o.optDouble("weight", base.weight), postWeight = o.optDouble("postWeight", base.postWeight))
            o.optJSONArray("foods")?.let { d = Food.summarize(d, Food.parseList(it)) }
            if (i >= 0) out[i] = d else out += d
        }
        return out.sortedBy { it.date }
    }

    fun blank(date: String) = Nutrition.Day(date, 0, 0.0, 0.0, 0.0, 0.0, 0, 0, 0, 0, 0, emptyMap(), emptyList(), 0, 0L)

    /**
     * Records a change for [date] on top of [shown] (the day as currently displayed) and queues it.
     * Fluids: [delta] oz. Weights: [value] lb (0 clears). Returns the new day.
     */
    @Synchronized fun record(ctx: Context, shown: Nutrition.Day?, date: String, f: String, delta: Int = 0, value: Double = 0.0): Nutrition.Day {
        val base = shown ?: blank(date)
        val l = local(ctx)
        val o = l.optJSONObject(date) ?: JSONObject().put("water", base.water).put("sports", base.sportsOz).put("other", base.otherOz)
            .put("weight", base.weight).put("postWeight", base.postWeight)
        var d = delta
        if (f in FLUIDS) {
            val cur = o.optInt(f); val next = (cur + delta).coerceAtLeast(0); d = next - cur
            if (d == 0) return overlayOne(base, o)
            o.put(f, next)
        } else o.put(f, value)
        l.put(date, o)
        p(ctx).edit().putString("days", l.toString()).apply()
        saveChanges(ctx, changes(ctx) + Change(date, f, d, value, System.currentTimeMillis(), null))
        return overlayOne(base, o)
    }

    /**
     * A food change for [date] on top of [shown]: "foodAdd" ([item] new), "foodEdit" ([item] with new servings / meal),
     * "foodDel" ([item]'s id). Saves the day's foods here and queues the change. Returns the new day.
     */
    @Synchronized fun recordFood(ctx: Context, shown: Nutrition.Day?, date: String, op: String, item: Food.Item): Nutrition.Day {
        val base = shown ?: blank(date)
        val l = local(ctx)
        val o = l.optJSONObject(date) ?: JSONObject().put("water", base.water).put("sports", base.sportsOz).put("other", base.otherOz)
            .put("weight", base.weight).put("postWeight", base.postWeight)
        val foods = (o.optJSONArray("foods")?.let { Food.parseList(it) } ?: base.foods).toMutableList()
        val i = foods.indexOfFirst { it.id == item.id }
        val x = when (op) {
            "foodAdd" -> { if (i < 0) foods += item; Food.toJson(item) }
            "foodEdit" -> { if (i >= 0) foods[i] = item.copy(image = foods[i].image.ifEmpty { item.image })
                Food.toJson(item).put("full", true) }
            else -> { if (i >= 0) foods.removeAt(i); JSONObject().put("id", item.id) }
        }
        // Drinks logged as foods move the Sports / Other drinks counters (the Command Center does the same when it gets the food).
        val before = (o.optJSONArray("foods")?.let { Food.parseList(it) } ?: base.foods)
        val dSports = Food.liquidOz(foods, "sports") - Food.liquidOz(before, "sports")
        val dOther = Food.liquidOz(foods, "liquids") - Food.liquidOz(before, "liquids")
        if (dSports != 0) o.put("sports", (o.optInt("sports") + dSports).coerceAtLeast(0))
        if (dOther != 0) o.put("other", (o.optInt("other") + dOther).coerceAtLeast(0))
        o.put("foods", Food.listJson(foods))
        l.put(date, o)
        p(ctx).edit().putString("days", l.toString()).apply()
        saveChanges(ctx, changes(ctx) + Change(date, op, 0, 0.0, System.currentTimeMillis(), null, x.toString()))
        return Food.summarize(overlayOne(base, o), foods)
    }

    private fun overlayOne(base: Nutrition.Day, o: JSONObject): Nutrition.Day {
        val w = o.optInt("water"); val s = o.optInt("sports"); val ot = o.optInt("other")
        return base.copy(water = w, sportsOz = s, otherOz = ot, waterOz = w + s + ot, weight = o.optDouble("weight", 0.0), postWeight = o.optDouble("postWeight", 0.0))
    }

    /** "date|f|delta|value" — how the watch sends a change to the phone. */
    fun encode(date: String, f: String, delta: Int, value: Double) = "$date|$f|$delta|$value"
    fun decode(s: String): Change? = s.split("|").takeIf { it.size == 4 }?.let {
        Change(it[0], it[1], it[2].toIntOrNull() ?: 0, it[3].toDoubleOrNull() ?: 0.0, System.currentTimeMillis(), null)
    }

    fun lb(v: Double) = if (v <= 0) "—" else String.format(java.util.Locale.US, "%.1f", v)
}
