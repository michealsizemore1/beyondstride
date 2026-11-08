package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import io.github.michealsizemore1.beyondstride.Food
import io.github.michealsizemore1.beyondstride.NutritionStore
import io.github.michealsizemore1.beyondstride.ReadinessSync
import org.json.JSONObject
import java.time.LocalDate

/**
 * The per-activity chips the Command Center worked out (actExtras), done in BeyondStride:
 * power zone, heart-rate zone (Karvonen), heat & humidity impact, heat acclimation that day, sleep the night before,
 * prior-day fluids / calories / macros, and fuel impact (prior day's food quality vs the pre-fueling target).
 */
object ActivityEngine {
    private val S = ActivityStore

    private fun heatIndex(t: Double, r: Double): Double {
        if (t < 80) return t
        var hi = -42.379 + 2.04901523 * t + 10.14333127 * r - 0.22475541 * t * r - 0.00683783 * t * t - 0.05481717 * r * r + 0.00122874 * t * t * r + 0.00085282 * t * r * r - 0.00000199 * t * t * r * r
        if (r < 13 && t <= 112) hi -= ((13 - r) / 4) * Math.sqrt((17 - Math.abs(t - 95)) / 17)
        if (r > 85 && t <= 87) hi += ((r - 85) / 10) * ((87 - t) / 5)
        return hi
    }
    private fun haLabel(p: Int) = when { p < 20 -> "Not Acclimated" to "#dc2626"; p < 45 -> "Minimally Acclimated" to "#f97316"; p < 70 -> "Acclimatizing" to "#ca8a04"; p < 90 -> "Mostly Acclimated" to "#65a30d"; else -> "Well Acclimated" to "#16a34a" }

    /** Heat acclimation % for every date from the first activity through today. */
    private fun haByDate(all: List<JSONObject>): Map<String, Int> {
        val today = LocalDate.now()
        val stim = HashMap<String, Double>()
        all.forEach { a -> val t = S.str(a, "temp").toDoubleOrNull() ?: return@forEach; val d = S.str(a, "date")
            if (S.str(a, "type") == "Rest Day" || d.isEmpty()) return@forEach
            val hrs = S.durSec(S.str(a, "duration")) / 3600.0; if (hrs <= 0) return@forEach
            val f = if (t <= 70) 0.0 else if (t >= 95) 1.0 else (t - 70) / 25; if (f > 0) stim[d] = (stim[d] ?: 0.0) + f * Math.min(hrs, 3.0) }
        val first = all.filter { S.str(it, "type") != "Rest Day" }.map { S.str(it, "date") }.filter { it.isNotEmpty() && it <= today.toString() }.minOrNull() ?: return emptyMap()
        val back = Math.min(730L, Math.max(1L, java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(first), today) + 1))
        val out = HashMap<String, Int>(); var load = 0.0
        for (i in back - 1 downTo 0) { val d = today.minusDays(i).toString(); load = load * 0.96 + (stim[d] ?: 0.0); out[d] = Math.max(0, Math.min(100, Math.round(100 * (1 - Math.exp(-load / 6))).toInt())) }
        return out
    }

    @Volatile private var cacheKey = ""; @Volatile private var cache: Map<String, JSONObject> = emptyMap()

    /** Chips for every activity, by id (cached until activities, settings or nutrition change). */
    fun extras(ctx: Context): Map<String, JSONObject> {
        val all = S.list(ctx); val st = S.settings(ctx)
        val key = "${S.stamp(ctx)}|$st|${NutritionStore.fetchedAt(ctx)}|${LocalDate.now()}"
        if (key == cacheKey) return cache
        val cp = st.optDouble("cp", 0.0); val max = st.optDouble("maxHr", 0.0).takeIf { it > 0 } ?: 174.0; val rest = st.optDouble("restHr", 0.0).takeIf { it > 0 } ?: 55.0
        val b = listOf(0.5, 0.6, 0.7, 0.8, 0.9).map { Math.round(rest + Math.max(1.0, max - rest) * it).toDouble() }
        val ha = haByDate(all)
        val nights = ReadinessSync.nights(ctx).associateBy { it.day }
        val recent = NutritionStore.days(ctx).associateBy { it.date }
        val dayCtx = HashMap<String, JSONObject>()
        fun ctxFor(date: String): JSONObject = dayCtx.getOrPut(date) {
            val c = JSONObject()
            nights[date]?.let { n -> if (n.minutes > 0) c.put("sleepMins", n.minutes) }
            try {
                val prev = LocalDate.parse(date).minusDays(1).toString()
                val pd = recent[prev] ?: NutriDay.rawHistory(ctx, prev)
                if (pd != null) {
                    if (pd.waterOz > 0) c.put("prevWaterOz", pd.waterOz)
                    if (pd.cal > 0) { c.put("prevCal", pd.cal); val kc = pd.cal.toDouble()
                        if (pd.prot + pd.carbs + pd.fat > 0) c.put("prevP", Math.round(pd.prot * 4 / kc * 100)).put("prevC", Math.round(pd.carbs * 4 / kc * 100)).put("prevF", Math.round(pd.fat * 9 / kc * 100)) }
                    if (pd.foods.isNotEmpty()) {
                        val (run, hard) = NutritionEngine.runContext(ctx, prev)
                        var qt = 0.0; var qw = 0.0
                        pd.foods.forEach { f -> val q = Food.quality(f, run, hard); val sv = Math.max(0.01, f.servings); val w = Math.max(0.0, (f.cal ?: 0.0) * sv).takeIf { it > 0 } ?: sv; qt += q.score * w; qw += w }
                        if (qw > 0) {
                            val stretched = 5 + (qt / qw - 5) * 2.5; var pen = 0.0; val notes = ArrayList<String>()
                            val pr = NutritionEngine.preset(ctx, prev)
                            if (pr != null && pr.phase == "prior" && pd.cal > 0) {
                                val off = Math.abs(pd.cal - pr.calories).toDouble() / pr.calories
                                if (off > 0.2) { pen += Math.min(3.0, (off - 0.2) * 10); notes += "${pd.cal} cal vs ${pr.calories} target" }
                                if (c.has("prevC")) { val co = Math.abs(c.optInt("prevC") - pr.carbs); if (co > 10) { pen += Math.min(2.0, (co - 10) / 10.0); notes += "${c.optInt("prevC")}% carbs vs ${pr.carbs}% target" } }
                            }
                            val imp = Math.max(0.0, Math.min(10.0, Math.round((stretched - pen) * 10) / 10.0))
                            c.put("fuel", imp).put("fuelLabel", if (imp >= 9) "Excellent" else if (imp >= 7) "Good" else if (imp >= 5) "Fair" else if (imp >= 3) "Low" else "Poor")
                            c.put("fuelNote", (if (pr != null && pr.phase == "prior") pr.label.replace("Tomorrow: ", "") else "") + (if (notes.isNotEmpty()) (if (pr != null) " · " else "") + notes.joinToString(", ") else ""))
                        }
                    }
                }
            } catch (e: Exception) { }
            c
        }
        val out = HashMap<String, JSONObject>()
        all.forEach { a ->
            val x = JSONObject()
            val p = S.num(a, "power"); if (cp > 0 && p > 0) { val r = p / cp; x.put("pz", if (r < 0.55) "Z1" else if (r < 0.75) "Z2" else if (r < 0.90) "Z3" else if (r < 1.05) "Z4" else "Z5") }
            val hr = S.num(a, "heartRate"); if (hr > 0 && hr >= b[0]) x.put("hz", if (hr < b[1]) "Z1" else if (hr < b[2]) "Z2" else if (hr < b[3]) "Z3" else if (hr < b[4]) "Z4" else "Z5")
            val t = S.str(a, "temp").toDoubleOrNull(); val ps = S.paceSec(S.str(a, "pace"))
            if (t != null && ps > 0) { val h = S.str(a, "humidity").toDoubleOrNull() ?: 30.0; val fl = heatIndex(t, h); val pct = Math.max(0.0, (fl - 60) * 0.4)
                if (pct >= 0.5) x.put("hhPct", Math.round(pct * 10) / 10.0).put("hhSec", Math.round(ps * pct / 100)).put("feels", Math.round(fl)) }
            val d = S.str(a, "date")
            ha[d]?.let { v -> val (l, c) = haLabel(v); x.put("ha", v).put("haLabel", l).put("haColor", c) }
            if (d.isNotEmpty()) ctxFor(d).let { c -> c.keys().forEach { k -> x.put(k, c.get(k)) } }
            out[S.idStr(a.opt("id"))] = x
        }
        cacheKey = key; cache = out
        return out
    }
}
