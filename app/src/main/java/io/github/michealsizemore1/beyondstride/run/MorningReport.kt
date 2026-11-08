package io.github.michealsizemore1.beyondstride.run

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import io.github.michealsizemore1.beyondstride.Readiness
import io.github.michealsizemore1.beyondstride.ReadinessSync
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.Locale

/**
 * ☀️ Morning Report — everything for today on one screen, ready when you wake up (phone, notification and watch):
 * sleep, readiness / HRV / resting HR vs your normal, today's workout (plus any plan adjustment), the best weather window
 * for the run, fuel / fluid targets and a pre-run meal idea, wake-up weight and trend, the week so far, race countdown,
 * and one line of advice.
 */
object MorningReport {
    private fun p(ctx: Context) = ctx.getSharedPreferences("morning", 0)
    fun enabled(ctx: Context) = p(ctx).getBoolean("on", true)
    fun setEnabled(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("on", v).apply()
    /** Latest time the notification waits for your sleep to come in (minutes after midnight). */
    fun time(ctx: Context) = p(ctx).getInt("time", 7 * 60)
    fun setTime(ctx: Context, m: Int) = p(ctx).edit().putInt("time", m).apply()

    data class Section(val icon: String, val title: String, val main: String, val lines: List<String>, val color: Int = 0xFFFFFFFF.toInt())
    data class Report(val date: String, val headline: String, val sections: List<Section>, val advice: String)

    private const val GREEN = 0xFF5FD38D.toInt(); private const val YELLOW = 0xFFF2C94C.toInt(); private const val RED = 0xFFFF8A80.toInt()
    private fun f1(d: Double) = if (d == Math.floor(d)) d.toLong().toString() else String.format(Locale.US, "%.1f", d)

    // ── Weather (Open-Meteo forecast; cached for an hour) ──
    data class Wx(val now: String, val window: String, val note: String)
    @SuppressLint("MissingPermission")
    private fun where(ctx: Context): DoubleArray {
        if (ctx.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) try {
            val lm = ctx.getSystemService(LocationManager::class.java)
            lm.getProviders(true).mapNotNull { try { lm.getLastKnownLocation(it) } catch (e: Exception) { null } }.maxByOrNull { it.time }?.let { return doubleArrayOf(it.latitude, it.longitude) }
        } catch (e: Exception) { }
        return doubleArrayOf(43.6121, -116.3915)   // Meridian, ID
    }
    /** Fetches today's hourly forecast (call off the main thread). */
    fun fetchWeather(ctx: Context) {
        val pr = p(ctx); if (System.currentTimeMillis() - pr.getLong("wxAt", 0L) < 60 * 60_000L && pr.getString("wxDay", "") == LocalDate.now().toString()) return
        try {
            val (la, lo) = where(ctx).let { it[0] to it[1] }
            val c = (URL("https://api.open-meteo.com/v1/forecast?latitude=$la&longitude=$lo&hourly=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,precipitation_probability" +
                "&temperature_unit=fahrenheit&wind_speed_unit=mph&timezone=auto&forecast_days=1").openConnection() as HttpURLConnection).apply { connectTimeout = 12_000; readTimeout = 15_000 }
            if (c.responseCode != 200) return
            pr.edit().putString("wx", c.inputStream.bufferedReader().readText()).putLong("wxAt", System.currentTimeMillis()).putString("wxDay", LocalDate.now().toString()).apply()
        } catch (e: Exception) { }
    }
    private fun weather(ctx: Context): Wx? {
        val pr = p(ctx); if (pr.getString("wxDay", "") != LocalDate.now().toString()) return null
        val h = try { JSONObject(pr.getString("wx", "{}")!!).optJSONObject("hourly") } catch (e: Exception) { null } ?: return null
        val t = h.optJSONArray("temperature_2m") ?: return null; val fl = h.optJSONArray("apparent_temperature") ?: return null
        val rh = h.optJSONArray("relative_humidity_2m"); val wind = h.optJSONArray("wind_speed_10m"); val rain = h.optJSONArray("precipitation_probability")
        val nowH = java.time.LocalTime.now().hour
        fun at(i: Int) = "${Math.round(t.optDouble(i))}°F" + (if (Math.abs(fl.optDouble(i) - t.optDouble(i)) >= 3) ", feels ${Math.round(fl.optDouble(i))}°" else "") +
            (wind?.optDouble(i)?.takeIf { it >= 8 }?.let { " · wind ${Math.round(it)} mph" } ?: "") + (rain?.optInt(i)?.takeIf { it >= 30 }?.let { " · rain $it%" } ?: "")
        // Best 2-hour window from now (6 AM–8 PM): closest to ~50°F feels-like, least rain and wind.
        var best = -1; var bestS = Double.MAX_VALUE
        for (i in Math.max(6, nowH) until Math.min(20, t.length() - 1)) {
            val s = (0..1).sumOf { k -> val j = i + k; Math.abs(fl.optDouble(j) - 50) + (rain?.optInt(j) ?: 0) * 0.4 + (wind?.optDouble(j) ?: 0.0) * 0.8 }
            if (s < bestS) { bestS = s; best = i }
        }
        fun hr(i: Int) = java.time.LocalTime.of(i % 24, 0).format(java.time.format.DateTimeFormatter.ofPattern("h a", Locale.US))
        val window = if (best >= 0) "Best window ${hr(best)}–${hr(best + 2)}: ${at(best)}" else ""
        val hot = (0 until t.length()).maxOfOrNull { fl.optDouble(it) } ?: 0.0; val cold = (Math.max(6, nowH) until Math.min(20, t.length())).minOfOrNull { fl.optDouble(it) } ?: 99.0
        val note = when {
            hot >= 80 -> "Hot later — run early, add electrolytes, expect ~${Math.round(Math.max(0.0, (hot - 60) * 0.4))}% slower at the same effort"
            cold <= 25 -> "Cold — layer up, cover hands and ears; the first mile will feel stiff"
            (rh?.let { a -> (0 until a.length()).maxOfOrNull { a.optInt(it) } } ?: 0) >= 85 && hot >= 65 -> "Humid — heart rate runs higher; go by effort"
            else -> ""
        }
        return Wx("Now ${at(Math.min(nowH, t.length() - 1))}", window, note)
    }

    // ── The report ──
    fun build(ctx: Context): Report {
        val today = Readiness.today(); val sec = ArrayList<Section>()
        // Sleep.
        val nights = ReadinessSync.nights(ctx); val night = nights.lastOrNull { it.day == today }
        val avgSleep = nights.filter { it.day < today && it.minutes > 0 }.takeLast(7).map { it.minutes }.takeIf { it.isNotEmpty() }?.average()
        if (night != null && night.minutes > 0) {
            val c = if (night.minutes >= 420) GREEN else if (night.minutes >= 360) YELLOW else RED
            sec += Section("😴", "Sleep", Readiness.hmm(night.minutes), listOfNotNull(
                if (night.sleepHr > 0) "Sleeping HR ${night.sleepHr} bpm" else null,
                avgSleep?.let { a -> val d = night.minutes - a; "7-night average ${Readiness.hmm(a.toInt())} (${if (d >= 0) "+" else "−"}${Readiness.hmm(Math.abs(d.toInt()))})" }), c)
        } else sec += Section("😴", "Sleep", "Not in yet", listOf("Samsung Health sends it after you wake — open Samsung Health if it's late"), 0xFF9AA5A0.toInt())
        // Readiness, HRV and resting HR vs your normal.
        val rd = Insights.readiness(ctx)
        val checks = ReadinessSync.checks(ctx); val chk = checks.lastOrNull { it.day == today }
        val past = checks.filter { it.day < today }.takeLast(14)
        val hrvBase = past.map { it.rmssd }.takeIf { it.size >= 3 }?.average(); val rhrBase = past.map { it.hr }.filter { it > 0 }.takeIf { it.size >= 3 }?.average()
        val rl = ArrayList<String>()
        if (chk != null) {
            rl += "HRV ${Math.round(chk.rmssd)} ms" + (hrvBase?.let { b -> val pct = (chk.rmssd - b) / b * 100; " · ${if (pct >= 0) "+" else ""}${Math.round(pct)}% vs your ${Math.round(b)} ms normal" } ?: " · building your normal (${past.size + 1} of 3 checks)")
            rl += "Resting HR ${chk.hr}" + (rhrBase?.let { b -> val d = chk.hr - b; " · ${if (d >= 0) "+" else ""}${Math.round(d)} vs normal" } ?: "")
        } else rl += "Morning check not done yet — tap Start morning check"
        rd?.let { r -> rl += r.advice.substringBefore(". ").let { if (it.endsWith(".")) it else "$it." } }
        sec += Section("💚", "Readiness", rd?.let { "${it.score} · ${it.label}" } ?: "—", rl, rd?.let { if (it.score >= 70) GREEN else if (it.score >= 50) YELLOW else RED } ?: 0xFF9AA5A0.toInt())
        // Today's workout.
        val plan = PlanStore.data(ctx).optJSONObject("current")
        val wtext = plan?.optJSONObject("days")?.optString(today).orEmpty()
        val wl = ArrayList<String>()
        plan?.let { PlanEngine.weekDay(it, today) }?.let { (w, d) -> wl += "Plan week $w, day $d" }
        Insights.adherence(ctx, today)?.takeIf { it.done > 0 }?.let { wl += it.status }
        Insights.recoveryWarning(ctx, rd)?.let { wl += it }
        PlanAdjust.suggestions(ctx).firstOrNull()?.let { wl += "🔧 Suggested: " + it.optString("note") + " (accept in Activities)" }
        val tom = plan?.optJSONObject("days")?.optString(LocalDate.now().plusDays(1).toString())?.takeIf { it.isNotEmpty() }
        tom?.let { wl += "Tomorrow: " + (Insights.headLine(it) ?: it).trim().substringAfter(": ").take(80) }
        sec += Section("🏃", "Today's workout", if (wtext.isEmpty()) "No plan for today" else (Insights.headLine(wtext) ?: wtext).trim().substringAfter(": ").take(90), wl,
            if (Insights.recoveryWarning(ctx, rd) != null) YELLOW else 0xFFFFFFFF.toInt())
        // Weather.
        weather(ctx)?.let { w -> sec += Section("🌤️", "Weather", w.now, listOfNotNull(w.window.ifEmpty { null }, w.note.ifEmpty { null })) }
        // Fuel and fluids.
        val nd = NutriDay.day(ctx, today); val fl = ArrayList<String>()
        val preset = try { NutritionEngine.preset(ctx, today) } catch (e: Exception) { null }
        preset?.let { fl += it.label.ifEmpty { "${it.carbs}% carbs today" } }
        val dr = Insights.drinkTarget(ctx, today); fl += "Drink ~${dr.total} oz (${dr.base} base + ${dr.run} for ${dr.note})"
        try { nd?.prerun?.takeIf { it.isNotEmpty() }?.let { JSONObject(it) }?.optJSONArray("sets")?.optJSONObject(0)?.let { s ->
            val foods = s.optJSONArray("foods"); val names = (0 until (foods?.length() ?: 0)).mapNotNull { foods?.optJSONObject(it)?.optString("name") }.take(3)
            if (names.isNotEmpty()) fl += "Pre-run idea: " + names.joinToString(" + ") } } catch (e: Exception) { }
        Insights.longRun(ctx)?.let { fl += "🏃 ${it.title} — see Nutrition" }
        sec += Section("🍽️", "Fuel", if ((nd?.target ?: 0) > 0) "${String.format(Locale.US, "%,d", nd!!.target)} kcal target" else "—", fl)
        // Body.
        val bl = ArrayList<String>()
        val wk = (0..7).mapNotNull { i -> NutriDay.day(ctx, LocalDate.now().minusDays(i.toLong()).toString())?.weight?.takeIf { it > 0 } }
        if (wk.size >= 2) { val d = wk.first() - wk.last(); bl += "7-day trend ${if (d >= 0) "+" else "−"}${f1(Math.abs(Math.round(d * 10) / 10.0))} lb" }
        sec += Section("⚖️", "Body", nd?.weight?.takeIf { it > 0 }?.let { "Wake-up ${f1(it)} lb" } ?: "Weigh in to fill this in", bl)
        // Week so far + race.
        val ml = ArrayList<String>(); val mi = ActivityStore.mileage(ctx)
        val wkPlan = plan?.let { pl -> val days = pl.optJSONObject("days"); val dow = PlanEngine.weekDay(pl, today)?.second ?: (LocalDate.now().dayOfWeek.value % 7 + 1)
            (0..6).sumOf { k -> Insights.plannedMiles(days?.optString(LocalDate.now().minusDays((dow - 1).toLong()).plusDays(k.toLong()).toString()).orEmpty()) } } ?: 0.0
        Insights.weekScore(ctx)?.let { ml += it }
        plan?.let { PlanEngine.race(it) }?.takeIf { it.isNotEmpty() && it >= today }?.let { r ->
            ml += "🏁 ${java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(r))} days to race day (" + LocalDate.parse(r).format(java.time.format.DateTimeFormatter.ofPattern("MMM d", Locale.US)) + ")" }
        sec += Section("📅", "This week", "${f1(Math.round(mi.week * 10) / 10.0)} mi" + (if (wkPlan > 0) " of ${f1(wkPlan)} planned" else ""), ml)
        // Advice.
        val hard = try { NutritionEngine.hardWorkout(ctx, LocalDate.now()) } catch (e: Exception) { "" }
        val advice = when {
            Insights.recoveryWarning(ctx, rd) != null -> "Take it easier today — recovery is behind. Easy effort or swap the hard day."
            rd != null && rd.score >= 70 && hard.isNotEmpty() -> "Good to go — hit today's ${hard} workout as planned and fuel carbs before it."
            wtext.isEmpty() || Insights.plannedMiles(wtext) <= 0 -> "Rest / recovery day — easy movement, hydrate, and eat well."
            else -> "Steady day — run it easy in Zone 2 and keep the fluids up."
        }
        val headline = listOfNotNull(rd?.let { "Readiness ${it.score}" }, night?.takeIf { it.minutes > 0 }?.let { "sleep ${Readiness.hmm(it.minutes)}" },
            if (wtext.isNotEmpty()) (Insights.headLine(wtext) ?: wtext).trim().substringAfter(": ").take(40) else null).joinToString(" · ")
        return Report(today, headline, sec, advice)
    }

    fun json(r: Report): JSONObject = JSONObject().put("date", r.date).put("headline", r.headline).put("advice", r.advice).put("sections", JSONArray().apply {
        r.sections.forEach { s -> put(JSONObject().put("icon", s.icon).put("title", s.title).put("main", s.main).put("color", s.color).put("lines", JSONArray(s.lines))) } })

    /** Sends the notification once a day: as soon as last night's sleep is in (after 4 AM), or at the set time at the latest. */
    fun maybeNotify(ctx: Context, notify: (String, String) -> Unit) {
        if (!enabled(ctx)) return
        val pr = p(ctx); val today = Readiness.today(); if (pr.getString("sent", "") == today) return
        val now = java.time.LocalTime.now(); val mins = now.hour * 60 + now.minute
        if (mins < 4 * 60 || mins > 12 * 60) return
        val sleepIn = ReadinessSync.nights(ctx).any { it.day == today && it.minutes > 0 }
        if (!sleepIn && mins < time(ctx)) return
        fetchWeather(ctx)
        val r = build(ctx)
        pr.edit().putString("sent", today).apply()
        notify("☀️ Morning report", r.headline + "\n" + r.advice)
    }
}
