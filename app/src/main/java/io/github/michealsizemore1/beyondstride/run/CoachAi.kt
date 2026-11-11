package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import io.github.michealsizemore1.beyondstride.Food
import io.github.michealsizemore1.beyondstride.Readiness
import io.github.michealsizemore1.beyondstride.ReadinessSync
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale

/**
 * 🧠 Coach AI: you pick what it looks at (specific runs, the plan, mileage history, recovery, Body Battery, food,
 * weight, weather) and ask anything — compare runs, find why one felt harder, check progress toward the race.
 * Unlike Fuel AI (one day's food vs one run) it's a conversation over whatever you choose.
 */
object CoachAi {
    private val S = ActivityStore
    data class Scope(val key: String, val label: String, val hint: String)
    val SCOPES = listOf(
        Scope("runs", "🏃 Runs I pick", "full details of the runs you select"),
        Scope("plan", "📅 Training plan", "this week, next week and race day"),
        Scope("history", "📈 Mileage history", "weekly miles, runs and long run for 16 weeks, plus recent runs"),
        Scope("recovery", "😴 Sleep & HRV", "sleep, sleeping HR, morning HRV / resting HR and readiness, 21 days"),
        Scope("battery", "🔋 Body Battery", "today's level, what drained it and the last 7 mornings"),
        Scope("food", "🍽️ Nutrition", "calories, macros, fluids, food quality and low nutrients, 10 days"),
        Scope("weight", "⚖️ Weight", "wake-up and post-run weights, 30 days"),
        Scope("weather", "🌤️ Weather", "now and the next hours (for heat / pacing questions)"),
    )
    val QUICK = listOf(
        "Compare the runs I picked — what's better, what's worse and why?",
        "Am I getting fitter? Look at pace vs heart rate over time.",
        "Why did my last run feel harder than usual?",
        "Am I on track for my race? What should I focus on?",
        "How did my sleep and food affect my runs this week?",
        "Am I doing too much? Check for signs of overtraining.",
    )

    fun p(ctx: Context) = ctx.getSharedPreferences("coach", 0)
    fun scopes(ctx: Context): MutableSet<String> = p(ctx).getStringSet("scopes", setOf("runs", "plan", "recovery"))!!.toMutableSet()
    fun setScopes(ctx: Context, s: Set<String>) = p(ctx).edit().putStringSet("scopes", s).apply()
    fun runIds(ctx: Context): MutableList<String> = (p(ctx).getString("runs", "") ?: "").split(",").filter { it.isNotBlank() }.toMutableList()
    fun setRunIds(ctx: Context, ids: List<String>) = p(ctx).edit().putString("runs", ids.joinToString(",")).apply()
    fun chat(ctx: Context): JSONArray = try { JSONArray(p(ctx).getString("chat", "[]")) } catch (e: Exception) { JSONArray() }
    fun saveChat(ctx: Context, a: JSONArray) {
        val keep = JSONArray(); val from = Math.max(0, a.length() - 40); for (i in from until a.length()) keep.put(a.get(i))
        p(ctx).edit().putString("chat", keep.toString()).apply()
    }

    fun id(a: JSONObject) = S.str(a, "id").ifEmpty { S.str(a, "date") + "|" + S.num(a, "distance") }
    fun isRun(a: JSONObject) = Regex("run|jog|tempo|interval|race|long|recovery|easy|fartlek|stride", RegexOption.IGNORE_CASE)
        .containsMatchIn(S.str(a, "type") + " " + S.str(a, "title")) && S.num(a, "distance") > 0 && !Regex("walk|bike|cycl|swim", RegexOption.IGNORE_CASE).containsMatchIn(S.str(a, "type"))
    fun runs(ctx: Context) = S.list(ctx).filter { isRun(it) }.sortedByDescending { S.str(it, "date") }
    fun runLabel(a: JSONObject): String {
        val d = S.str(a, "date").take(10)
        val day = try { LocalDate.parse(d).format(java.time.format.DateTimeFormatter.ofPattern("EEE MMM d", Locale.US)) } catch (e: Exception) { d }
        return listOfNotNull(day, S.str(a, "title").ifEmpty { S.str(a, "type") }.take(30), String.format(Locale.US, "%.2f mi", S.num(a, "distance")),
            S.str(a, "pace").ifEmpty { null }?.let { "$it/mi" }).joinToString(" · ")
    }

    /** One run with everything recorded (long lists like GPS points left out, splits kept). */
    private fun runJson(a: JSONObject): JSONObject {
        val o = JSONObject()
        a.keys().forEach { k ->
            val v = a.opt(k)
            when {
                k.lowercase() in setOf("points", "track", "gps", "route", "polyline", "samples", "hrseries", "series", "stream", "streams", "map", "photo", "photos") -> {}
                v is JSONArray -> { val t = v.toString(); if (k.lowercase().contains("split") || k.lowercase().contains("lap")) o.put(k, if (t.length > 4000) JSONArray(t.take(3990).substringBeforeLast("},") + "}]") else v) else if (t.length < 600) o.put(k, v) }
                v is JSONObject -> { val t = v.toString(); if (t.length < 1500) o.put(k, v) }
                else -> { val t = v.toString(); if (t.isNotBlank() && t != "null") o.put(k, if (t.length > 1200) t.take(1200) else v) }
            }
        }
        return o
    }

    /** The data you chose, as text for the AI. */
    fun context(ctx: Context): String {
        val sc = scopes(ctx); val sb = StringBuilder(); val today = LocalDate.now()
        sb.append("Today is ${today.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US))}.\n")
        val st = ActivityStore.settings(ctx)
        sb.append("Runner: 53-year-old male distance runner (Boise / Meridian, Idaho, ~2,600 ft). Runs with a Galaxy Watch Ultra 2 and a Polar H10 chest strap; power in watts from the watch.")
        listOf("maxHr" to "max HR", "restHr" to "resting HR", "lthr" to "lactate threshold HR", "ftp" to "threshold power").forEach { (k, l) -> st.optDouble(k, 0.0).takeIf { it > 0 }?.let { sb.append(" $l ${Math.round(it)}.") } }
        sb.append("\n")
        if ("runs" in sc) {
            val all = runs(ctx); val ids = runIds(ctx)
            val chosen = if (ids.isEmpty()) all.take(2) else all.filter { id(it) in ids }
            sb.append("\n## Selected runs (${chosen.size})\n")
            chosen.sortedBy { S.str(it, "date") }.forEach { sb.append(runJson(it).toString()).append("\n") }
        }
        if ("plan" in sc) try {
            val plan = PlanStore.data(ctx).optJSONObject("current"); val days = plan?.optJSONObject("days")
            if (days != null) {
                sb.append("\n## Training plan\n")
                (plan!!.optString("raceDate").takeIf { it.length == 10 } ?: PlanEngine.raceDate(plan.optString("text")))?.let { sb.append("Race day: $it (${java.time.temporal.ChronoUnit.DAYS.between(today, LocalDate.parse(it))} days away)\n") }
                PlanEngine.weekDay(plan, today.toString())?.let { (w, d) -> sb.append("Today is plan week $w, day $d\n") }
                for (i in -7L..13L) { val d = today.plusDays(i).toString(); days.optString(d).takeIf { it.isNotBlank() }?.let { sb.append("$d: ${it.take(400).replace("\n", " / ")}\n") } }
                Insights.adherence(ctx, today.toString())?.let { sb.append("Planned vs done today: ${it.status}\n") }
            }
        } catch (e: Exception) { }
        if ("history" in sc) {
            sb.append("\n## Weekly mileage (Mon–Sun, newest last)\n")
            val all = runs(ctx); var mon = today.minusDays((today.dayOfWeek.value - 1).toLong()).minusWeeks(15)
            repeat(16) {
                val end = mon.plusDays(6); val w = all.filter { S.str(it, "date").take(10) in mon.toString()..end.toString() }
                val mi = w.sumOf { S.num(it, "distance") }; val long = w.maxOfOrNull { S.num(it, "distance") } ?: 0.0
                val hr = w.filter { S.num(it, "heartRate") > 0 }.let { l -> if (l.isEmpty()) "" else " · avg HR ${Math.round(l.sumOf { S.num(it, "heartRate") * S.num(it, "distance") } / l.sumOf { S.num(it, "distance") })}" }
                sb.append("week of $mon: ${String.format(Locale.US, "%.1f", mi)} mi · ${w.size} runs · long ${String.format(Locale.US, "%.1f", long)} mi$hr\n")
                mon = mon.plusWeeks(1)
            }
            sb.append("\nRecent runs (date · title · miles · time · pace · avg HR · watts · temp):\n")
            all.take(30).sortedBy { S.str(it, "date") }.forEach { a ->
                sb.append(listOf(S.str(a, "date").take(10), S.str(a, "title").take(40), String.format(Locale.US, "%.2f", S.num(a, "distance")), S.str(a, "duration"), S.str(a, "pace"),
                    S.num(a, "heartRate").takeIf { it > 0 }?.let { Math.round(it).toString() } ?: "", S.num(a, "power").takeIf { it > 0 }?.let { Math.round(it).toString() } ?: "",
                    S.str(a, "temp").ifEmpty { S.str(a, "temperature") }).joinToString(" · ")).append("\n")
            }
        }
        if ("recovery" in sc) {
            sb.append("\n## Sleep & morning checks (21 days)\n")
            val from = today.minusDays(21).toString()
            val nights = ReadinessSync.nights(ctx).filter { it.day >= from }.associateBy { it.day }
            val checks = ReadinessSync.checks(ctx).filter { it.day >= from }.associateBy { it.day }
            (nights.keys + checks.keys).toSortedSet().forEach { d ->
                val n = nights[d]; val c = checks[d]
                sb.append("$d: " + listOfNotNull(n?.takeIf { it.minutes > 0 }?.let { "sleep ${Readiness.hmm(it.minutes)}" }, n?.takeIf { it.sleepHr > 0 }?.let { "sleeping HR ${it.sleepHr}" },
                    c?.let { "HRV ${Math.round(it.rmssd)} ms" }, c?.takeIf { it.hr > 0 }?.let { "resting HR ${it.hr}" },
                    c?.takeIf { it.legs >= 0 }?.let { "legs ${it.legs}/5" }, c?.takeIf { it.sleepFeel >= 0 }?.let { "sleep feel ${it.sleepFeel}/5" }).joinToString(" · ") + "\n")
            }
            Insights.readiness(ctx)?.let { sb.append("Readiness today: ${it.score} (${it.label}) — ${it.advice}\n") }
        }
        if ("battery" in sc) try {
            val b = BodyBattery.today(ctx)
            sb.append("\n## Body Battery (estimate, 0–100)\nNow ${b.now} (${b.label}); woke at ${b.wake}; ${b.notes.joinToString("; ")}\n")
            sb.append("Last 7 mornings: " + BodyBattery.history(ctx).joinToString(", ") { "${it.first.drop(5)} ${it.second}" } + "\n")
        } catch (e: Exception) { }
        if ("food" in sc) {
            sb.append("\n## Nutrition (10 days)\n")
            for (i in 9L downTo 0L) {
                val d = today.minusDays(i).toString(); val n = NutriDay.day(ctx, d) ?: continue
                if (n.count == 0) continue
                sb.append("$d: ${n.cal} kcal" + (if (n.target > 0) " of ${n.target}" else "") + " · P ${Math.round(n.prot)} g · C ${Math.round(n.carbs)} g · F ${Math.round(n.fat)} g · fluids ${n.waterOz} oz" +
                    (Food.dayQuality(n)?.let { " · food quality ${String.format(Locale.US, "%.1f", it.score)}" } ?: "") +
                    n.micros.filter { it.status == "Low" }.takeIf { it.isNotEmpty() }?.let { l -> " · low: " + l.joinToString(", ") { "${it.name} ${Math.round(it.pct)}%" } }.orEmpty() + "\n")
            }
        }
        if ("weight" in sc) {
            sb.append("\n## Weight (30 days, lb)\n")
            for (i in 29L downTo 0L) { val d = today.minusDays(i).toString(); val n = NutriDay.day(ctx, d) ?: continue
                if (n.weight > 0 || n.postWeight > 0) sb.append("$d: " + listOfNotNull(n.weight.takeIf { it > 0 }?.let { "wake-up $it" }, n.postWeight.takeIf { it > 0 }?.let { "post-run $it" }).joinToString(" · ") + "\n") }
        }
        if ("weather" in sc) TodayWeather.watchJson(ctx)?.let { w ->
            sb.append("\n## Weather now (${w.optString("place")})\n${w.optString("temp")}°F, feels ${w.optString("feels")}°, dew point ${w.optString("dew")}°, ${w.optString("cond")}, wind ${w.optString("wind")} mph, high ${w.optString("hi")}° low ${w.optString("lo")}°, rain ${w.optInt("rain")}%\n")
            w.optJSONArray("next")?.let { a -> sb.append("Next hours: " + (0 until a.length()).joinToString(", ") { val h = a.getJSONObject(it); "${h.optString("h")} ${h.optString("t")}°" } + "\n") }
        }
        return sb.toString()
    }

    fun system(ctx: Context) = """You are Coach AI inside BeyondStride, the runner's personal analyst: an experienced distance-running coach and exercise physiologist.
Analyze and compare the data below to answer the runner's questions. Be specific: quote the actual numbers (pace, heart rate, watts, cadence, splits, decoupling, temperature, sleep, HRV, calories) and the dates, and compare them side by side.
Explain the likely reasons behind differences (heat and dew point, elevation, fatigue from prior days, sleep / HRV, fueling and hydration, pacing, terrain) and say how confident you are.
Finish with 2–4 clear, practical takeaways. Use only the data given — if something needed isn't there, say what's missing (the runner can add it with the data buttons) instead of guessing.
Keep it readable on a phone: short sections with bold headings, bullets, no tables, no long preambles.

DATA THE RUNNER CHOSE:
${context(ctx)}"""

    /**
     * ⛽ Fuel AI (folded in from the Nutrition screen): how the food on [nutrDate] likely affected the run on [runDate].
     * Same facts as before — the day's calories, macros, fluids and every meal, the run, and fuel taken during it.
     */
    fun fuelBlock(ctx: Context, nutrDate: String, runDate: String, extra: String): String {
        fun fmt(d: String) = LocalDate.parse(d).format(java.time.format.DateTimeFormatter.ofPattern("EEE MMM d", Locale.US))
        val n = NutriDay.day(ctx, nutrDate); val r = NutriDay.day(ctx, runDate)
        val sb = StringBuilder("FUEL ANALYSIS REQUEST — act as a sports nutritionist. Analyze how the nutrition on ${fmt(nutrDate)} likely affected the run on ${fmt(runDate)}. Be specific and practical (max about 350 words), then give what to eat differently next time.\n")
        if (extra.isNotBlank()) sb.append("Runner's notes: $extra\n")
        sb.append("\nNUTRITION (${fmt(nutrDate)}): " + (if (n != null && n.cal > 0) "${n.cal} kcal" + (if (n.target > 0) " of ${n.target} target" else "") else "No calories logged"))
        if (n != null && n.cal > 0) {
            sb.append(" | Protein ${Math.round(n.prot)} g | Carbs ${Math.round(n.carbs)} g | Fat ${Math.round(n.fat)} g | Fluids ${n.waterOz} oz")
            Food.dayQuality(n)?.let { sb.append(" | Food quality ${String.format(Locale.US, "%.1f", it.score)}/10") }
            sb.append("\n")
            n.foods.groupBy { it.meal }.takeIf { it.isNotEmpty() }?.let { g -> sb.append("Meals:\n")
                io.github.michealsizemore1.beyondstride.Nutrition.MEAL_ORDER.filter { g.containsKey(it) }.forEach { m ->
                    sb.append((io.github.michealsizemore1.beyondstride.Nutrition.MEAL_NAMES[m] ?: m) + ": " + g[m]!!.joinToString(", ") { "${it.name} (${it.kcal} cal)" } + "\n") } }
            n.micros.filter { it.status == "Low" }.takeIf { it.isNotEmpty() }?.let { l -> sb.append("Low nutrients: " + l.joinToString(", ") { "${it.name} ${Math.round(it.pct)}%" } + "\n") }
        }
        val runs = runs(ctx).filter { S.str(it, "date").take(10) == runDate }
        sb.append("\nRUN (${fmt(runDate)}): ")
        if (runs.isEmpty()) sb.append("No run recorded\n") else runs.forEach { sb.append(runJson(it).toString()).append("\n") }
        if (runDate != nutrDate && r != null) {
            if (r.waterOz > 0) sb.append("Fluids on run day: ${r.waterOz} oz\n")
            r.foods.filter { it.meal != "sports" }.takeIf { it.isNotEmpty() }?.let { f -> sb.append("Also eaten on run day: " + f.joinToString(", ") { "${it.name} (${it.kcal} cal)" } + "\n") }
        }
        (r ?: n)?.foods?.filter { it.meal == "sports" }?.takeIf { it.isNotEmpty() }?.let { sp -> sb.append("Sports/race nutrition (during run): " + sp.joinToString(", ") { "${it.name} (${it.kcal} cal)" } + "\n") }
        return sb.toString()
    }

    /** Sends the conversation plus the new question; returns the answer. [hidden] = extra facts sent with it but not shown. Call off the main thread. */
    fun ask(ctx: Context, question: String, hidden: String? = null): String {
        val chat = chat(ctx)
        val msgs = JSONArray(); val from = Math.max(0, chat.length() - 12)
        for (i in from until chat.length()) chat.getJSONObject(i).let { msgs.put(JSONObject().put("role", it.optString("role")).put("content", it.optString("content"))) }
        val full = if (hidden.isNullOrBlank()) question else hidden + "\n\n" + question
        msgs.put(JSONObject().put("role", "user").put("content", full))
        val answer = AiFood.askChat(ctx, system(ctx), msgs)
        chat.put(JSONObject().put("role", "user").put("content", full).put("show", question)); chat.put(JSONObject().put("role", "assistant").put("content", answer))
        saveChat(ctx, chat)
        return answer
    }
}
