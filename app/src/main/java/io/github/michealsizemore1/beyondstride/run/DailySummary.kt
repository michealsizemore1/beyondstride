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
            if (nd.weight > 0) lines += "Weight: wake-up ${f1(nd.weight)} lb" + (if (nd.preWeight > 0) " · pre-run ${f1(nd.preWeight)} lb" else "") + (if (nd.postWeight > 0) " · post-run ${f1(nd.postWeight)} lb" else "")
        }
        // Numbers the Command Center's Wellness fields fill in (Sleep, Weight, Resting HR).
        val w = JSONObject()
        ReadinessSync.nights(ctx).lastOrNull { it.day == date }?.takeIf { it.minutes > 0 }?.let { w.put("sleep", "${it.minutes / 60}:${String.format("%02d", it.minutes % 60)}") }
        ReadinessSync.checks(ctx).lastOrNull { it.day == date }?.takeIf { it.hr > 0 }?.let { w.put("restHr", it.hr) }
        ReadinessSync.nights(ctx).lastOrNull { it.day == date }?.takeIf { it.sleepHr > 0 }?.let { w.put("sleepHr", it.sleepHr) }
        // Blood pressure + pulse from the cuff (Health Connect).
        BpImport.day(ctx, date)?.let { (bp, pulse, _) -> w.put("bp", bp); if (pulse > 0) w.put("pulse", pulse) }
        // Steps (Samsung Health, via Health Connect).
        DayStress.steps(ctx, date)?.takeIf { it > 0 }?.let { w.put("steps", it) }
        // HRV: the morning check (Polar H10), ms.
        ReadinessSync.checks(ctx).lastOrNull { it.day == date }?.takeIf { it.rmssd > 0 }?.let { w.put("hrv", Math.round(it.rmssd)) }
        nd?.weight?.takeIf { it > 0 }?.let { w.put("weight", f1(it)) }
        return JSONObject().put("date", date).put("text", lines.joinToString("\n")).put("miles", Math.round(miles * 100) / 100.0)
            .put("cal", nd?.cal ?: 0).put("wellness", w).put("at", System.currentTimeMillis())
            // Evening report time (and whether tonight's was asked for early) — the Command Center fills its Day Review just before.
            .apply { if (date == Readiness.today()) put("evening", JSONObject().put("time", if (EveningReport.enabled(ctx)) EveningReport.time(ctx) else 0).put("early", EveningReport.early(ctx))) }
            .apply { if (nd != null && nd.count > 0) put("nutrition", JSONObject().put("cal", nd.cal).put("target", nd.target).put("prot", Math.round(nd.prot)).put("carbs", Math.round(nd.carbs))
                .put("fat", Math.round(nd.fat)).put("water", nd.waterOz).put("waterOnly", nd.water).put("sports", nd.sportsOz).put("other", nd.otherOz)
                .put("quality", Food.dayQuality(nd)?.score?.let { Math.round(it * 10) / 10.0 } ?: 0.0).put("microScore", Math.round(nd.microScore * 10) / 10.0)
                .put("qualityLabel", Food.dayQuality(nd)?.label ?: "").put("fluidTarget", try { Insights.drinkTarget(ctx, date).total } catch (e: Exception) { 0 })
                .apply { nd.macroTiles()?.let { t -> put("macros", JSONObject().apply { t.forEach { m -> put(m.name.lowercase(), JSONObject().put("pct", Math.round(m.actual * 10) / 10.0).put("goal", m.goal).put("status", m.status)) } }) } }
                .put("micros", JSONObject().apply { put("fiber", Math.round(nd.fiber)); put("sodium", nd.sodium); nd.micros.forEach { m -> put(m.field, Math.round(m.value * 10) / 10.0) } })
                .put("microTargets", JSONObject().apply { nd.micros.forEach { m -> put(m.field, JSONObject().put("t", m.target).put("mx", m.max).put("u", m.unit).put("s", m.status).put("n", m.name)) } })) }
    }

    /** Posts today's and yesterday's summaries when they changed (at most every 10 minutes). Call off the main thread. */
    fun publish(ctx: Context, force: Boolean = false) {
        if (!NutritionSync.ready(ctx)) return
        val pr = p(ctx); val now = System.currentTimeMillis()
        // Day summaries every 10 minutes; activities and plan days whenever they change.
        val days = DayStress.takeStepsChanged(ctx) or (force || !pr.getBoolean("backHrv", false) || now - pr.getLong("at", 0L) >= 10 * 60_000L)
        if (days) pr.edit().putLong("at", now).apply()
        val base = Live.db(ctx) + "/nutrition/" + NutritionSync.key(ctx) + "/beyondstride/daily/"
        // The first time (again when new numbers were added, like HRV): the last 4 months, so Weekly / Monthly tables have every day.
        val back = if (!days) -1L else if (pr.getBoolean("backHrv", false)) 1L else 120L
        (back downTo 0L).map { LocalDate.now().minusDays(it).toString() }.forEach { d ->
            try {
                val o = build(ctx, d); val sig = (o.optString("text") + o.optJSONObject("wellness") + o.optJSONObject("nutrition") + o.optJSONObject("evening")).hashCode()
                if (!force && pr.getInt("sig_$d", 0) == sig) return@forEach
                val c = (URL("$base$d.json").openConnection() as HttpURLConnection).apply {
                    requestMethod = "PUT"; doOutput = true; connectTimeout = 15_000; readTimeout = 20_000; setRequestProperty("Content-Type", "application/json") }
                c.outputStream.use { it.write(o.toString().toByteArray()) }
                if (c.responseCode in 200..299) pr.edit().putInt("sig_$d", sig).apply()
            } catch (e: Exception) { }
        }
        if (days) pr.edit().putBoolean("backHrv", true).apply()
        // Activities (the last 400 days) for the Command Center's Weekly / Monthly / Yearly fitness and goals.
        try {
            val from = LocalDate.now().minusDays(400).toString()
            val keys = listOf("id", "date", "title", "type", "distance", "duration", "pace", "power", "heartRate", "elevGain", "calories", "cadence", "notes")
            val arr = org.json.JSONArray()
            S.list(ctx).filter { S.str(it, "date").take(10) >= from }.sortedBy { S.str(it, "date") }.forEach { a ->
                val o = JSONObject(); keys.forEach { k -> a.opt(k)?.takeIf { it.toString().isNotBlank() && it.toString() != "null" }?.let { v -> o.put(k, if (k == "notes") v.toString().take(500) else v) } }
                arr.put(o)
            }
            val sig = arr.toString().hashCode()
            if (force || pr.getInt("actsig", 0) != sig) {
                val c = (URL(Live.db(ctx) + "/nutrition/" + NutritionSync.key(ctx) + "/beyondstride/activities.json").openConnection() as HttpURLConnection).apply {
                    requestMethod = "PUT"; doOutput = true; connectTimeout = 15_000; readTimeout = 30_000; setRequestProperty("Content-Type", "application/json") }
                c.outputStream.use { it.write(JSONObject().put("list", arr).put("at", System.currentTimeMillis()).toString().toByteArray()) }
                if (c.responseCode in 200..299) pr.edit().putInt("actsig", sig).apply()
            }
        } catch (e: Exception) { }
        // Training plan days (a week back, three weeks ahead) for the Command Center's My Schedule.
        try {
            val days = PlanStore.data(ctx).optJSONObject("current")?.optJSONObject("days")
            val o = JSONObject()
            if (days != null) for (i in -7L..21L) { val d = LocalDate.now().plusDays(i).toString(); days.optString(d).takeIf { it.isNotBlank() }?.let { o.put(d, it.take(1500)) } }
            val sig = o.toString().hashCode()
            if (force || pr.getInt("plansig", 0) != sig) {
                val c = (URL(Live.db(ctx) + "/nutrition/" + NutritionSync.key(ctx) + "/beyondstride/plan.json").openConnection() as HttpURLConnection).apply {
                    requestMethod = "PUT"; doOutput = true; connectTimeout = 15_000; readTimeout = 20_000; setRequestProperty("Content-Type", "application/json") }
                c.outputStream.use { it.write(JSONObject().put("days", o).put("at", System.currentTimeMillis()).toString().toByteArray()) }
                if (c.responseCode in 200..299) pr.edit().putInt("plansig", sig).apply()
            }
        } catch (e: Exception) { }
    }
}
