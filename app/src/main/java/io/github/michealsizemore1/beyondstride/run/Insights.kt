package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import io.github.michealsizemore1.beyondstride.Nutrition
import io.github.michealsizemore1.beyondstride.NutritionStore
import io.github.michealsizemore1.beyondstride.Readiness
import io.github.michealsizemore1.beyondstride.ReadinessSync
import org.json.JSONObject
import java.time.LocalDate

/**
 * Coaching extras worked out from what you already log:
 *  - Planned vs done: the plan's miles for a day vs the runs logged that day, and a score for the plan week so far.
 *  - Recovery warning: low readiness or short sleep on a hard-workout day.
 *  - Sweat rate (wake-up weight − post-run weight, per hour of running) and a daily drink target.
 *  - Gap-filling foods: your Healthy 7+ foods richest in what you're low on today.
 *  - Long-run fueling plan: today's or tomorrow's long run — pre-run meal, carbs / fluid / sodium per hour, gels.
 */
object Insights {
    private val S = ActivityStore

    // ── Planned vs done ──
    /** Miles in a plan day's first line ("Sat, Nov 7: 8 E + STRENGTH" → 8; "Long run 14 mi" → 14). 0 for rest / none. */
    /** Lines added by an accepted plan adjustment ("↪ Moved from …") — the workout itself is on the next line. */
    fun isMarker(l: String) = l.trimStart().let { it.startsWith("↪") || it.startsWith("⇄") || it.startsWith("↘") || it.startsWith("＋") }
    fun headLine(text: String) = text.lines().firstOrNull { it.isNotBlank() && !isMarker(it) }
    fun plannedMiles(text: String): Double {
        val first = headLine(text) ?: return 0.0
        if (Regex("rest\\s*day|no workout", RegexOption.IGNORE_CASE).containsMatchIn(first)) return 0.0
        val body = if (first.contains(":")) first.substringAfter(":") else first
        val units = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:-\\s*\\d+(?:\\.\\d+)?\\s*)?(mi|miles?|E|LR|L|MP|M|T|R|easy|long|recovery|rec|steady|tempo|progression)\\b", RegexOption.IGNORE_CASE)
        val nums = units.findAll(body).mapNotNull { it.groupValues[1].toDoubleOrNull() }.filter { it in 1.0..60.0 }.toList()
        if (nums.isNotEmpty()) return nums.sum()
        return 0.0
    }
    fun planText(ctx: Context, date: String) = PlanStore.data(ctx).optJSONObject("current")?.optJSONObject("days")?.optString(date) ?: ""
    fun doneMiles(ctx: Context, date: String) = S.list(ctx).filter { S.str(it, "date").take(10) == date && S.isRun(S.str(it, "type").ifEmpty { "run" }) }.sumOf { S.num(it, "distance") }

    data class Adh(val planned: Double, val done: Double, val status: String, val color: Int)
    fun adherence(ctx: Context, date: String): Adh? {
        val t = planText(ctx, date); if (t.isEmpty()) return null
        val p = plannedMiles(t); val d = doneMiles(ctx, date); val past = date < LocalDate.now().toString()
        return when {
            p <= 0 && d <= 0 -> Adh(0.0, 0.0, "Rest / no miles planned", 0xFF9AA5A0.toInt())
            p <= 0 -> Adh(0.0, d, "${mi(d)} mi done (none planned)", 0xFF7DD3FC.toInt())
            d >= p * 1.25 && d - p >= 2 -> Adh(p, d, "▲ ${mi(p)} mi planned · ${mi(d)} done (+${mi(Math.round((d - p) * 10) / 10.0)} mi)", 0xFFF2C94C.toInt())
            d >= p * 0.9 -> Adh(p, d, "✓ ${mi(p)} mi planned · ${mi(d)} done", 0xFF5FD38D.toInt())
            d > 0 -> Adh(p, d, "◐ ${mi(p)} mi planned · ${mi(d)} done (${Math.round(d / p * 100)}%)", 0xFFF2C94C.toInt())
            past -> Adh(p, 0.0, "✗ ${mi(p)} mi planned · missed", 0xFFFF8A80.toInt())
            else -> Adh(p, 0.0, "${mi(p)} mi planned · not done yet", 0xFFCFD8DC.toInt())
        }
    }
    /** "4 of 5 workouts hit this plan week" (days before today, plus today once it's done). */
    fun weekScore(ctx: Context): String? {
        val plan = PlanStore.data(ctx).optJSONObject("current") ?: return null
        val today = LocalDate.now()
        val dow = PlanEngine.weekDay(plan, today.toString())?.second ?: (today.dayOfWeek.value % 7 + 1)
        var planned = 0; var hit = 0
        for (i in dow - 1 downTo 0) {
            val d = today.minusDays(i.toLong()).toString(); val a = adherence(ctx, d) ?: continue
            if (a.planned <= 0) continue
            if (i == 0 && a.done < a.planned * 0.9) continue   // today counts once it's done
            planned++; if (a.done >= a.planned * 0.9) hit++
        }
        return if (planned == 0) null else "$hit of $planned run${if (planned == 1) "" else "s"} hit this plan week"
    }
    private fun mi(d: Double) = if (d == Math.floor(d)) d.toLong().toString() else String.format(java.util.Locale.US, "%.1f", d)

    // ── Recovery warning ──
    @Volatile private var rdAt = 0L; @Volatile private var rdKey = ""; @Volatile private var rdCache: Readiness.Result? = null
    fun readiness(ctx: Context): Readiness.Result? {
        val key = "${ReadinessSync.checks(ctx).size}.${ReadinessSync.nightsAt(ctx)}.${PhoneRuns.list(ctx).size}.${Readiness.today()}"
        if (key == rdKey && System.currentTimeMillis() - rdAt < 5 * 60_000L) return rdCache
        val r = readinessCalc(ctx); rdCache = r; rdKey = key; rdAt = System.currentTimeMillis(); return r
    }
    private fun readinessCalc(ctx: Context): Readiness.Result? = try {
        Readiness.compute(ReadinessSync.checks(ctx), ReadinessSync.nights(ctx), PhoneRuns.list(ctx).take(120).mapNotNull { PhoneRuns.read(it) },
            Readiness.today(), fuelYesterday = NutritionStore.yesterday(ctx))
    } catch (e: Exception) { null }
    fun recoveryWarning(ctx: Context, rd: Readiness.Result? = readiness(ctx)): String? {
        val today = LocalDate.now()
        val hard = try { NutritionEngine.hardWorkout(ctx, today) } catch (e: Exception) { "" }
        val sleep = ReadinessSync.nights(ctx).lastOrNull { it.day == today.toString() }?.minutes ?: 0
        val low = rd != null && rd.score < 50; val short = sleep in 1 until 300
        if (!low && !short) return null
        val why = listOfNotNull(if (low) "readiness ${rd!!.score} (${rd.label})" else null, if (short) "${Readiness.hmm(sleep)} sleep" else null).joinToString(" and ")
        val name = mapOf("long" to "long run", "interval" to "interval session", "tempo" to "tempo run", "quality" to "quality workout")[hard]
        return if (name != null) "⚠️ ${why.replaceFirstChar { it.uppercase() }} before today's $name — consider running it easy, shortening it, or moving it to a better-rested day."
        else if (rd != null && rd.score < 35) "⚠️ ${why.replaceFirstChar { it.uppercase() }} — keep today easy or take a rest day."
        else null
    }

    // ── Sweat rate & drink target ──
    data class Sweat(val ozPerHr: Int, val runs: Int)
    /** From days with a wake-up and post-run weight and a run logged (fluids during the run aren't counted, so it reads a little low). */
    @Volatile private var swKey = ""; @Volatile private var swCache: Sweat? = null
    fun sweatRate(ctx: Context): Sweat? {
        val key = "${S.stamp(ctx)}|${NutritionStore.fetchedAt(ctx)}|${NutriDay.historyCount(ctx)}"
        if (key == swKey) return swCache
        val r = sweatCalc(ctx); swCache = r; swKey = key; return r
    }
    private fun sweatCalc(ctx: Context): Sweat? {
        val days = (Nutrition.parse(NutriDay.allJson(ctx).toString()).associateBy { it.date } + NutritionStore.days(ctx).associateBy { it.date }).values
        val zone = java.time.ZoneId.systemDefault()
        val rates = days.filter { it.postWeight > 70 && (it.preWeight > 70 || it.weight > 70) }.sortedByDescending { it.date }.mapNotNull { d ->
            val runs = S.list(ctx).filter { S.str(it, "date").take(10) == d.date && S.isRun(S.str(it, "type").ifEmpty { "run" }) }
            val hrs = runs.sumOf { S.durSec(S.str(it, "duration")) } / 3600.0
            if (hrs < 0.25) return@mapNotNull null
            // Before the run: the pre-run weight; the wake-up weight only counts for a morning run (food and drinks later in the day would hide sweat).
            val before = if (d.preWeight > 70) d.preWeight else {
                val startHour = runs.mapNotNull { S.idStr(it.opt("id")).toLongOrNull()?.takeIf { v -> v > 1_500_000_000_000L } }.minOrNull()
                    ?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).hour }
                if (startHour != null && startHour < 11) d.weight else return@mapNotNull null
            }
            // Drinks taken during the run (logged under Sports / race nutrition) were sweat too.
            val duringOz = io.github.michealsizemore1.beyondstride.Food.liquidOz(d.foods, "sports")
            val lostLb = before - d.postWeight + duringOz / 16.0
            if (lostLb <= 0) null else (lostLb * 16 / hrs).takeIf { it in 4.0..80.0 }
        }.take(10)
        if (rates.isEmpty()) return null
        return Sweat(Math.round(rates.average()).toInt(), rates.size)
    }
    private fun recentPaceSec(ctx: Context): Int {
        val from = LocalDate.now().minusDays(28).toString()
        val ps = S.list(ctx).filter { S.str(it, "date") >= from && S.isRun(S.str(it, "type").ifEmpty { "run" }) }.map { S.paceSec(S.str(it, "pace")) }.filter { it in 300..900 }
        return if (ps.isEmpty()) 570 else ps.average().toInt()
    }
    private fun weightLb(ctx: Context) = CalorieTargets.weight(NutritionStore.days(ctx)).takeIf { it > 0 } ?: 185.0
    data class Drink(val total: Int, val base: Int, val run: Int, val note: String)
    /** About half your weight (lb) in oz, plus your sweat loss for today's run (done, or planned). */
    fun drinkTarget(ctx: Context, date: String = LocalDate.now().toString()): Drink {
        val base = Math.round(weightLb(ctx) * 0.5).toInt()
        val doneHrs = S.list(ctx).filter { S.str(it, "date").take(10) == date && S.isRun(S.str(it, "type").ifEmpty { "run" }) }.sumOf { S.durSec(S.str(it, "duration")) } / 3600.0
        val hrs = if (doneHrs > 0) doneHrs else plannedMiles(planText(ctx, date)) * recentPaceSec(ctx) / 3600.0
        val sw = sweatRate(ctx)?.ozPerHr ?: 20
        val run = Math.round(sw * hrs).toInt()
        return Drink(base + run, base, run, if (hrs <= 0) "rest day" else (if (doneHrs > 0) "today's run" else "today's planned run") + " ≈ ${Readiness.hmm((hrs * 60).toInt())}")
    }

    // ── Gap-filling foods ──
    /** For each nutrient you're Low on today (up to 2), your Healthy 7+ foods with the most of it per serving. */
    fun gapFoods(ctx: Context, d: Nutrition.Day): List<Pair<Nutrition.Micro, List<String>>> {
        val healthy = FoodLibrary.healthyRaw(ctx)
        return d.micros.filter { it.status == "Low" && it.field != "caffeine" }.sortedBy { it.pct }.take(2).mapNotNull { m ->
            val top = healthy.filter { it.optDouble(m.field, 0.0) > 0 }.sortedByDescending { it.optDouble(m.field, 0.0) }.take(3)
                .map { val v = it.optDouble(m.field, 0.0); "${it.optString("name")} (" + (if (v >= 10) Math.round(v).toString() else String.format(java.util.Locale.US, "%.1f", v)) + m.unit + ")" }
            if (top.isEmpty()) null else m to top
        }
    }

    // ── Long-run fueling plan ──
    data class LongRun(val title: String, val lines: List<String>)
    fun longRun(ctx: Context): LongRun? {
        val today = LocalDate.now()
        for (off in 0..1) {
            val d = today.plusDays(off.toLong()); val ds = d.toString()
            val txt = planText(ctx, ds); val miles = plannedMiles(txt)
            val hard = try { NutritionEngine.hardWorkout(ctx, d) } catch (e: Exception) { "" }
            if (!(hard == "long" || miles >= 10)) continue
            if (off == 0 && doneMiles(ctx, ds) >= miles * 0.9 && miles > 0) continue   // already run today
            val pace = recentPaceSec(ctx); val hrs = if (miles > 0) miles * pace / 3600.0 else 1.75
            val kg = weightLb(ctx) * 0.4536
            val (lo, hi) = when { hrs < 1.25 -> 0 to 30; hrs < 2.5 -> 30 to 60; else -> 60 to 90 }
            val fuelHrs = Math.max(0.0, hrs - 0.5)
            val carbsMid = ((lo + hi) / 2.0 * fuelHrs).toInt()
            val gels = if (carbsMid <= 0) 0 else Math.ceil(carbsMid / 22.0).toInt()
            val sw = sweatRate(ctx)
            val fluid = (sw?.ozPerHr ?: 20).coerceIn(12, 28)
            val lines = ArrayList<String>()
            lines += "${if (miles > 0) "${mi(miles)} mi" else "Long run"} · about ${Readiness.hmm((hrs * 60).toInt())} at your recent ${pace / 60}:${String.format("%02d", pace % 60)}/mi"
            if (off == 1) NutritionEngine.preset(ctx, today.toString())?.takeIf { it.phase == "prior" }?.let { p ->
                lines += "Tonight: carb-forward dinner and evening snack — about ${p.carbs}% carbs of ${String.format(java.util.Locale.US, "%,d", p.calories)} kcal today" }
            lines += "Pre-run meal 2–3 h before: ${Math.round(kg)}–${Math.round(kg * 2)} g carbs (1–2 g/kg), low fat and fiber, plus 12–20 oz fluid"
            lines += if (lo == 0) "During: water is enough; a gel is optional"
                else "During: $lo–$hi g carbs per hour from about 30 min in → ~$carbsMid g, about $gels gel${if (gels == 1) "" else "s"} (≈22 g each) or GU drink mix"
            lines += "Fluid: about $fluid oz per hour" + (if (sw != null) " (your sweat rate ~${sw.ozPerHr} oz/hr)" else " (log wake-up and post-run weight for your own number)") + " · sodium 300–600 mg per hour"
            lines += "After: 20–30 g protein + carbs within an hour; drink back about ${Math.round(fluid * hrs * 1.25)} oz over the next few hours"
            return LongRun((if (off == 0) "Today's" else "Tomorrow's") + " long-run fueling", lines)
        }
        return null
    }

    /** A compact copy for the watch. */
    fun watch(ctx: Context): JSONObject {
        val o = JSONObject(); val today = LocalDate.now().toString()
        adherence(ctx, today)?.let { o.put("adh", it.status).put("adhColor", it.color) }
        weekScore(ctx)?.let { o.put("week", it) }
        recoveryWarning(ctx)?.let { o.put("warn", it) }
        val dr = drinkTarget(ctx); o.put("drink", "Drink target ~${dr.total} oz (${dr.base} base + ${dr.run} for ${dr.note})")
        sweatRate(ctx)?.let { o.put("sweat", "Sweat rate ~${it.ozPerHr} oz/hr (${it.runs} run${if (it.runs == 1) "" else "s"})") }
        longRun(ctx)?.let { lr -> o.put("lrTitle", lr.title).put("lr", org.json.JSONArray(lr.lines)) }
        PlanAdjust.suggestions(ctx).size.takeIf { it > 0 }?.let { o.put("adjust", "🔧 $it plan adjustment${if (it == 1) "" else "s"} suggested — accept on the phone (Activities)") }
        return o
    }
}
