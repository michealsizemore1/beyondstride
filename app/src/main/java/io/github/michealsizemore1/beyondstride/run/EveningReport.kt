package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import io.github.michealsizemore1.beyondstride.Food
import io.github.michealsizemore1.beyondstride.Readiness
import io.github.michealsizemore1.beyondstride.ReadinessSync
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * How today went and what tomorrow looks like (training, food, fluids, Body Battery, sleep, tomorrow). The evening
 * report notification was removed (Nov 2026); this now only feeds the phone's backup Daily AI Review.
 */
object EveningReport {
    private val S = ActivityStore

    private const val GREEN = 0xFF5FD38D.toInt(); private const val YELLOW = 0xFFF2C94C.toInt(); private const val RED = 0xFFFF8A80.toInt(); private const val GREY = 0xFF9AA5A0.toInt()
    private fun f1(d: Double) = if (d == Math.floor(d)) d.toLong().toString() else String.format(Locale.US, "%.1f", d)
    private fun kc(v: Int) = String.format(Locale.US, "%,d", v)

    fun build(ctx: Context): MorningReport.Report {
        val today = Readiness.today(); val tomorrow = LocalDate.now().plusDays(1).toString()
        val sec = ArrayList<MorningReport.Section>()
        val plan = PlanStore.data(ctx).optJSONObject("current"); val days = plan?.optJSONObject("days")

        // Training today.
        val acts = S.list(ctx).filter { S.str(it, "date").take(10) == today && S.str(it, "type") != "Rest Day" }
        val adh = Insights.adherence(ctx, today)
        val tl = ArrayList<String>()
        acts.forEach { a -> tl += listOfNotNull(S.str(a, "title").ifEmpty { S.str(a, "type").ifEmpty { "Workout" } },
            S.num(a, "distance").takeIf { it > 0 }?.let { "${f1(Math.round(it * 100) / 100.0)} mi" }, S.str(a, "duration").ifEmpty { null },
            S.str(a, "pace").ifEmpty { null }?.let { "$it/mi" }, S.num(a, "heartRate").takeIf { it > 0 }?.let { "HR ${Math.round(it)}" }).joinToString(" · ") }
        val wtext = days?.optString(today).orEmpty()
        if (acts.isEmpty() && wtext.isNotEmpty()) tl += "Planned: " + (Insights.headLine(wtext) ?: wtext).trim().substringAfter(": ").take(80)
        Insights.weekScore(ctx)?.let { tl += it }
        sec += MorningReport.Section("🏃", "Today's training", adh?.status ?: if (acts.isEmpty()) "No workout logged" else "${acts.size} workout${if (acts.size > 1) "s" else ""}", tl, adh?.color ?: GREY)

        // Food.
        val nd = NutriDay.day(ctx, today); val nl = ArrayList<String>()
        if (nd != null && nd.count > 0) {
            nl += "Protein ${Math.round(nd.prot)} g · carbs ${Math.round(nd.carbs)} g · fat ${Math.round(nd.fat)} g"
            Food.dayQuality(nd)?.let { nl += "Food quality ${String.format(Locale.US, "%.1f", it.score)}/10 (${it.label})" }
            if (nd.micros.isNotEmpty()) nl += "Micronutrients ${String.format(Locale.US, "%.1f", nd.microScore)}/10 (${nd.microLabel()})"
            nd.micros.filter { it.status == "Low" && it.field != "caffeine" }.sortedBy { it.pct }.take(3).takeIf { it.isNotEmpty() }?.let { l -> nl += "Low: " + l.joinToString(", ") { "${it.name} ${Math.round(it.pct)}%" } }
            Insights.gapFoods(ctx, nd).firstOrNull()?.let { (m, f) -> nl += "For ${m.name}: " + f.take(2).joinToString(", ") }
        } else nl += "No food logged today"
        val calMain = if (nd != null && nd.target > 0) "${kc(nd.cal)} of ${kc(nd.target)} kcal" else nd?.let { "${kc(it.cal)} kcal" } ?: "—"
        val calColor = if (nd == null || nd.target <= 0) GREY else { val r = nd.cal.toDouble() / nd.target; if (r in 0.9..1.1) GREEN else if (r in 0.75..1.25) YELLOW else RED }
        if (nd != null && nd.target > 0 && nd.cal < nd.target * 0.85) nl += "${kc(nd.target - nd.cal)} kcal under — a recovery snack before bed helps (protein + carbs)"
        sec += MorningReport.Section("🍽️", "Food", calMain, nl, calColor)

        // Fluids.
        val dr = Insights.drinkTarget(ctx, today); val oz = nd?.waterOz ?: 0
        sec += MorningReport.Section("💧", "Fluids", "$oz of ~${dr.total} oz", listOfNotNull(nd?.let { io.github.michealsizemore1.beyondstride.Nutrition.fluids(it) },
            if (oz < dr.total * 0.85) "A glass or two now (not a lot right before bed)" else "On target ✓"), if (oz >= dr.total * 0.85) GREEN else YELLOW)

        // Body Battery.
        val bb = BodyBattery.today(ctx)
        sec += MorningReport.Section("🔋", "Body Battery", "${bb.now} · ${bb.label}", bb.notes.dropLast(1), bb.color)

        // Sleep tonight: aim for 8 h before your usual 8 AM wake-up (more before a hard or long day).
        val hardTom = try { NutritionEngine.hardWorkout(ctx, LocalDate.now().plusDays(1)) } catch (e: Exception) { "" }
        val needMin = if (hardTom.isNotEmpty()) 8 * 60 + 30 else 8 * 60
        val wake = LocalTime.of(8, 0); val bed = wake.minusMinutes(needMin.toLong() + 15)
        val nights = ReadinessSync.nights(ctx); val last = nights.lastOrNull { it.day == today }
        val avg = nights.filter { it.minutes > 0 }.takeLast(7).map { it.minutes }.takeIf { it.isNotEmpty() }?.average()
        sec += MorningReport.Section("😴", "Sleep tonight", "Lights out by ${bed.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))}", listOfNotNull(
            "${Readiness.hmm(needMin)} before an 8:00 AM wake-up" + if (hardTom.isNotEmpty()) " (extra for tomorrow's $hardTom workout)" else "",
            last?.takeIf { it.minutes > 0 }?.let { "Last night ${Readiness.hmm(it.minutes)}" + (avg?.let { a -> " · 7-night avg ${Readiness.hmm(a.toInt())}" } ?: "") }))

        // Tomorrow.
        val tt = days?.optString(tomorrow).orEmpty(); val ml = ArrayList<String>()
        plan?.let { PlanEngine.weekDay(it, tomorrow) }?.let { (w, d) -> ml += "Plan week $w, day $d" }
        tt.lines().drop(1).filter { it.isNotBlank() && !Insights.isMarker(it) }.take(2).forEach { ml += it.trim().take(90) }
        if (nd != null && nd.perfNote.startsWith("Pre-fueling")) ml += "⛽ Pre-fueling day — carbs at your evening snack"
        TodayWeather.tomorrowMorning(ctx)?.let { ml += "🌤️ $it" }
        ml += "⚖️ Weigh in and do the morning check when you wake"
        sec += MorningReport.Section("📅", "Tomorrow", if (tt.isEmpty()) "No plan for tomorrow" else (Insights.headLine(tt) ?: tt).trim().substringAfter(": ").take(90), ml)

        val advice = when {
            bb.now < 26 -> "Low on energy — eat a recovery snack, hydrate a little and get to bed early."
            nd != null && nd.target > 0 && nd.cal < nd.target * 0.75 -> "You under-ate today — a protein + carb snack before bed will help recovery."
            hardTom.isNotEmpty() -> "Big day tomorrow ($hardTom) — lay out your gear, top up carbs and get a full night's sleep."
            tt.isEmpty() || Insights.plannedMiles(tt) <= 0 -> "Rest day tomorrow — sleep in if you can and recover well."
            else -> "Nice work today — wind down and get to bed on time."
        }
        val headline = listOfNotNull(adh?.status?.takeIf { acts.isNotEmpty() }, nd?.takeIf { it.target > 0 }?.let { "${kc(it.cal)}/${kc(it.target)} kcal" }, "Battery ${bb.now}",
            if (tt.isNotEmpty()) "Tomorrow: " + (Insights.headLine(tt) ?: tt).trim().substringAfter(": ").take(40) else null).joinToString(" · ")
        return MorningReport.Report(today, headline, sec, advice)
    }
}
