package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import io.github.michealsizemore1.beyondstride.Food
import io.github.michealsizemore1.beyondstride.Readiness
import io.github.michealsizemore1.beyondstride.ReadinessSync
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * A short, read-only summary of each day for the Command Center's Daily / Weekly Review (AI Fill Review reads it).
 * Written to <db>/nutrition/<key>/beyondstride/daily/<date> — one way only: BeyondStride never reads anything back.
 */
object DailySummary {
    private val S = ActivityStore
    private fun p(ctx: Context) = ctx.getSharedPreferences("dailysummary", 0)
    private fun f1(d: Double) = if (d == Math.floor(d)) d.toLong().toString() else String.format(java.util.Locale.US, "%.1f", d)

    /** The day's summary as plain text (what the AI reads) plus a few numbers. */
    fun build(ctx: Context, date: String): JSONObject {
        val lines = ArrayList<String>()
        // Runs / workouts.
        val acts = S.list(ctx).filter { S.str(it, "date").take(10) == date && S.str(it, "type") != "Rest Day" }
        var miles = 0.0
        if (acts.isEmpty()) lines += "Workouts: none logged"
        acts.forEach { a ->
            val d = S.num(a, "distance"); miles += d
            val parts = listOfNotNull(S.str(a, "type").ifEmpty { "Run" }, S.str(a, "title").ifEmpty { null }, if (d > 0) "${f1(d)} mi" else null,
                S.str(a, "duration").ifEmpty { null }, S.str(a, "pace").ifEmpty { null }?.let { "$it/mi" },
                S.num(a, "heartRate").takeIf { it > 0 }?.let { "avg HR ${Math.round(it)}" }, S.num(a, "power").takeIf { it > 0 }?.let { "${Math.round(it)} W" },
                S.str(a, "temp").ifEmpty { null }?.let { "${it}°F" })
            lines += "Workout: " + parts.joinToString(" · ") + S.str(a, "notes").takeIf { it.isNotBlank() }?.let { " — notes: " + it.take(1500) }.orEmpty()
        }
        // Training plan.
        val plan = PlanStore.data(ctx).optJSONObject("current")
        val days = plan?.optJSONObject("days")
        days?.optString(date)?.takeIf { it.isNotEmpty() }?.let { lines += "Planned: " + (Insights.headLine(it) ?: it.lines().first()).trim() }
        Insights.adherence(ctx, date)?.let { lines += "Planned vs done: " + it.status.replace(Regex("^[✓◐✗]\\s*"), "") }
        val tomorrow = LocalDate.parse(date).plusDays(1).toString()
        days?.optString(tomorrow)?.takeIf { it.isNotEmpty() }?.let { lines += "Tomorrow's plan: " + (Insights.headLine(it) ?: it.lines().first()).trim() }
        if (plan != null) { plan.let { PlanEngine.weekDay(it, date) }?.let { (w, d) -> lines += "Plan week $w, day $d" }
            PlanEngine.race(plan).takeIf { it.isNotEmpty() && it >= date }?.let { r -> lines += "Race day: $r (${java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(date), LocalDate.parse(r))} days away)" } }
        // Recovery.
        ReadinessSync.nights(ctx).lastOrNull { it.day == date }?.let { n -> lines += "Sleep: ${Readiness.hmm(n.minutes)}" + (if (n.sleepHr > 0) " · sleeping HR ${n.sleepHr}" else "") }
        ReadinessSync.checks(ctx).lastOrNull { it.day == date }?.let { c -> lines += "Morning check: HRV ${Math.round(c.rmssd)} ms · resting HR ${c.hr}" }
        if (date == Readiness.today()) Insights.readiness(ctx)?.let { r -> lines += "Readiness: ${r.score} (${r.label}) — ${r.advice.substringBefore(". ")}" }
        if (date == Readiness.today()) Insights.recoveryWarning(ctx)?.let { lines += "Recovery note: " + it.removePrefix("⚠️ ") }
        // Nutrition.
        val nd = NutriDay.day(ctx, date)
        if (nd == null || (nd.cal == 0 && nd.foods.isEmpty())) lines += "Nutrition: nothing logged"
        else {
            val tgt = if (nd.target > 0) " of ${nd.target} target" else ""
            lines += "Calories: ${nd.cal}$tgt · protein ${Math.round(nd.prot)} g · carbs ${Math.round(nd.carbs)} g · fat ${Math.round(nd.fat)} g"
            Food.dayQuality(nd)?.let { q -> lines += "Food quality: ${f1(Math.round(q.score * 10) / 10.0)}/10 (${q.label})" }
            if (nd.micros.isNotEmpty()) {
                lines += "Micronutrients: ${f1(Math.round(nd.microScore * 10) / 10.0)}/10"
                nd.micros.filter { it.status == "Low" && it.field != "caffeine" }.takeIf { it.isNotEmpty() }?.let { l -> lines += "Low: " + l.joinToString(", ") { "${it.name} ${Math.round(it.pct)}%" } }
                nd.micros.filter { it.status == "High" }.takeIf { it.isNotEmpty() }?.let { l -> lines += "Over limit: " + l.joinToString(", ") { "${it.name} ${Math.round(it.pct)}%" } }
            }
            lines += "Fluids: ${nd.waterOz} oz (water ${nd.water} · sports ${nd.sportsOz} · other ${nd.otherOz})"
            if (nd.weight > 0) lines += "Weight: wake-up ${f1(nd.weight)} lb" + (if (nd.postWeight > 0) " · post-run ${f1(nd.postWeight)} lb" else "")
        }
        // Numbers the Command Center's Wellness fields fill in (Sleep, Weight, Resting HR).
        val w = JSONObject()
        ReadinessSync.nights(ctx).lastOrNull { it.day == date }?.takeIf { it.minutes > 0 }?.let { w.put("sleep", "${it.minutes / 60}:${String.format("%02d", it.minutes % 60)}") }
        ReadinessSync.checks(ctx).lastOrNull { it.day == date }?.takeIf { it.hr > 0 }?.let { w.put("restHr", it.hr) }
        nd?.weight?.takeIf { it > 0 }?.let { w.put("weight", f1(it)) }
        return JSONObject().put("date", date).put("text", lines.joinToString("\n")).put("miles", Math.round(miles * 100) / 100.0)
            .put("cal", nd?.cal ?: 0).put("wellness", w).put("at", System.currentTimeMillis())
    }

    /** Posts today's and yesterday's summaries when they changed (at most every 10 minutes). Call off the main thread. */
    fun publish(ctx: Context, force: Boolean = false) {
        if (!Standalone.on(ctx) || !NutritionSync.ready(ctx)) return
        val pr = p(ctx); val now = System.currentTimeMillis()
        if (!force && now - pr.getLong("at", 0L) < 10 * 60_000L) return
        pr.edit().putLong("at", now).apply()
        val base = Live.db(ctx) + "/nutrition/" + NutritionSync.key(ctx) + "/beyondstride/daily/"
        // The first time: the last 2 weeks, so this week's review has every day.
        val back = if (pr.getBoolean("back14c", false)) 1L else 13L
        (back downTo 0L).map { LocalDate.now().minusDays(it).toString() }.forEach { d ->
            try {
                val o = build(ctx, d); val sig = (o.optString("text") + o.optJSONObject("wellness")).hashCode()
                if (!force && pr.getInt("sig_$d", 0) == sig) return@forEach
                val c = (URL("$base$d.json").openConnection() as HttpURLConnection).apply {
                    requestMethod = "PUT"; doOutput = true; connectTimeout = 15_000; readTimeout = 20_000; setRequestProperty("Content-Type", "application/json") }
                c.outputStream.use { it.write(o.toString().toByteArray()) }
                if (c.responseCode in 200..299) pr.edit().putInt("sig_$d", sig).apply()
            } catch (e: Exception) { }
        }
        pr.edit().putBoolean("back14c", true).apply()
    }
}
