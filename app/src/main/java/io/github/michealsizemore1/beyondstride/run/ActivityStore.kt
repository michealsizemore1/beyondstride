package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import io.github.michealsizemore1.beyondstride.Hydration
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The Command Center's Activities (shared to Firebase by its page): every activity, each card's extra lines
 * (zones, heat, sleep, previous-day water / food / fuel impact) and its CP / max HR / resting HR.
 * Changes made here are shown at once and sent to the Command Center (which keeps the real list).
 */
object ActivityStore {
    private fun p(ctx: Context) = ctx.getSharedPreferences("activities", 0)
    private fun raw(ctx: Context): JSONObject = try { JSONObject(p(ctx).getString("data", "{}")) } catch (e: Exception) { JSONObject() }

    /** An activity id as the Command Center writes it (decimals without "E12"). */
    fun idStr(v: Any?): String = when (v) {
        null -> ""
        is Double -> if (v == Math.floor(v) && !v.isInfinite()) v.toLong().toString() else java.math.BigDecimal.valueOf(v).toPlainString()
        is Float -> idStr(v.toDouble())
        else -> v.toString()
    }
    fun idOf(a: JSONObject) = idStr(a.opt("id"))

    fun fetchedAt(ctx: Context) = p(ctx).getLong("at", 0)
    fun status(ctx: Context) = p(ctx).getString("status", "") ?: ""

    /** Every activity: the Command Center's list with changes made here (not taken by it yet) on top. */
    fun list(ctx: Context): List<JSONObject> {
        val a = raw(ctx).optJSONArray("list") ?: JSONArray()
        val out = (0 until a.length()).mapNotNull { a.optJSONObject(it) }.toMutableList()
        Hydration.changes(ctx).filter { it.f == "actSave" || it.f == "actDel" }.sortedBy { it.at }.forEach { c ->
            val x = try { JSONObject(c.x ?: "{}") } catch (e: Exception) { return@forEach }
            val id = idStr(x.opt("id")).ifEmpty { return@forEach }
            val i = out.indexOfFirst { idOf(it) == id }
            if (c.f == "actDel") { if (i >= 0) out.removeAt(i) }
            else {
                val act = x.optJSONObject("act") ?: return@forEach
                if (i < 0) out += JSONObject(act.toString())
                else { val keep = out[i]; act.keys().forEach { k -> keep.put(k, act.get(k)) }; x.optJSONArray("clear")?.let { cl -> for (j in 0 until cl.length()) keep.remove(cl.optString(j)) } }
            }
        }
        return out
    }

    /** Extras are keyed by id with . $ # [ ] / replaced by _ (Firebase can't store those in keys). */
    fun extras(ctx: Context, id: Any?): JSONObject {
        if (Standalone.on(ctx)) return ActivityEngine.extras(ctx)[idStr(id)] ?: JSONObject()
        val ex = raw(ctx).optJSONObject("extras") ?: return JSONObject()
        val raw = idStr(id)
        return ex.optJSONObject(raw.replace(Regex("[.$#\\[\\]/]"), "_").ifEmpty { "_" }) ?: ex.optJSONObject(raw) ?: JSONObject()
    }
    private fun pending(ctx: Context, vararg ops: String) = Hydration.changes(ctx).filter { it.f in ops }.sortedBy { it.at }
        .mapNotNull { c -> try { c.f to JSONObject(c.x ?: "{}") } catch (e: Exception) { null } }

    /** CP / max HR / resting HR the Command Center uses (with changes made here on top). */
    fun settings(ctx: Context): JSONObject {
        val o = JSONObject((raw(ctx).optJSONObject("settings") ?: JSONObject()).toString())
        pending(ctx, "actSettings").forEach { (_, x) -> listOf("cp", "maxHr", "restHr").forEach { k -> if (x.optDouble(k, 0.0) > 0) o.put(k, x.getDouble(k)) } }
        return o
    }
    /** Mileage goals: week / month / year (0 = none). */
    fun goal(ctx: Context, period: String): Double {
        var g = raw(ctx).optJSONObject("goals")?.optDouble(period, 0.0) ?: 0.0
        pending(ctx, "actSettings").forEach { (_, x) -> if (x.has("goal_$period")) g = x.optDouble("goal_$period", 0.0) }
        return g
    }
    /** Journal miles on days with no activity (the Command Center's mileage summary counts them). */
    fun exMiles(ctx: Context): Map<String, Double> = raw(ctx).optJSONObject("exMiles")?.let { o -> o.keys().asSequence().associateWith { o.optDouble(it, 0.0) } } ?: emptyMap()

    /** Shoes (with changes made here on top). */
    fun shoes(ctx: Context): List<JSONObject> {
        val a = raw(ctx).optJSONArray("shoes") ?: JSONArray()
        val out = (0 until a.length()).mapNotNull { a.optJSONObject(it) }.toMutableList()
        pending(ctx, "shoeSave", "shoeDel").forEach { (f, x) ->
            val id = x.optString("id"); val i = out.indexOfFirst { it.optString("id") == id }
            if (f == "shoeDel") { if (i >= 0) out.removeAt(i) }
            else x.optJSONObject("shoe")?.let { sh -> if (i >= 0) { val k = out[i]; sh.keys().forEach { key -> k.put(key, sh.get(key)) } } else out += JSONObject(sh.toString()) }
        }
        return out
    }
    /** This week (Mon–Sun) and this month, the Activities mileage way: every activity's distance plus journal-only miles. Null before Activities arrive. */
    data class Mileage(val week: Double, val runs: Int, val month: Double)
    fun mileage(ctx: Context): Mileage? {
        if (fetchedAt(ctx) == 0L) return null
        val all = list(ctx); val today = java.time.LocalDate.now()
        val mon = today.minusDays((today.dayOfWeek.value - 1).toLong()).toString(); val m1 = today.withDayOfMonth(1).toString()
        val covered = all.filter { num(it, "distance") > 0 }.map { str(it, "date") }.toSet()
        fun mi(from: String) = all.filter { str(it, "date") >= from }.sumOf { num(it, "distance") } + exMiles(ctx).filter { (d, _) -> d >= from && d !in covered }.values.sum()
        val runs = all.count { str(it, "date") >= mon && str(it, "type").lowercase().contains("run") }
        return Mileage(mi(mon), runs, mi(m1))
    }

    /**
     * A small copy of the Activities tab for the watch: today's plan workout, the four mileage tiles with goals,
     * streak, Training Load Ratio (miles), Personal Records and the 25 most recent activities.
     */
    fun watchSummary(ctx: Context): JSONObject? {
        if (fetchedAt(ctx) == 0L) return null
        val all = list(ctx); val today = java.time.LocalDate.now()
        fun d(a: JSONObject) = try { java.time.LocalDate.parse(str(a, "date").take(10)) } catch (e: Exception) { null }
        val mon = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val covered = all.filter { num(it, "distance") > 0 }.map { str(it, "date") }.toSet()
        val ex = exMiles(ctx)
        fun tile(t: String, from: java.time.LocalDate, to: java.time.LocalDate?, goal: Double): JSONObject {
            val acts = all.filter { a -> d(a)?.let { !it.isBefore(from) && (to == null || it.isBefore(to)) } == true }
            val mi = acts.sumOf { num(it, "distance") } + ex.filter { (ds, _) -> ds !in covered && ds >= from.toString() && (to == null || ds < to.toString()) }.values.sum()
            val ps = acts.map { paceSec(str(it, "pace")) }.filter { it > 0 }; val pw = acts.map { num(it, "power") }.filter { it > 0 }
            val mins = acts.sumOf { durSec(str(it, "duration")) } / 60
            return JSONObject().put("t", t).put("mi", Math.round(mi * 10) / 10.0).put("pace", if (ps.isEmpty()) "" else ps.average().toInt().let { "${it / 60}:${String.format("%02d", it % 60)}" })
                .put("w", if (pw.isEmpty()) 0 else Math.round(pw.average()).toInt()).put("min", mins).put("goal", goal)
        }
        val o = JSONObject().put("count", all.size).put("at", System.currentTimeMillis())
        o.put("mileage", org.json.JSONArray().put(tile("This Week", mon, null, goal(ctx, "week"))).put(tile("Last Week", mon.minusWeeks(1), mon, 0.0))
            .put(tile("Month to Date", today.withDayOfMonth(1), null, goal(ctx, "month"))).put(tile("Year to Date", today.withDayOfYear(1), null, goal(ctx, "year"))))
        // Streak (weeks in a row with an activity) and ACWR from miles.
        var streak = 0; while (streak < 52) { val w0 = mon.minusWeeks(streak.toLong()); if (all.none { a -> d(a)?.let { !it.isBefore(w0) && it.isBefore(w0.plusWeeks(1)) } == true }) break; streak++ }
        o.put("streak", streak)
        val daily = HashMap<java.time.LocalDate, Double>(); all.forEach { a -> d(a)?.let { dd -> daily[dd] = (daily[dd] ?: 0.0) + num(a, "distance") } }
        fun lastN(n: Int) = (0 until n).sumOf { daily[today.minusDays(it.toLong())] ?: 0.0 }
        val acute = lastN(7) / 7; val chronic = lastN(28) / 28
        if (chronic > 0) o.put("acwr", Math.round(acute / chronic * 100) / 100.0).put("acute", Math.round(acute * 10) / 10.0).put("chronic", Math.round(chronic * 10) / 10.0)
        // Personal Records.
        val pr = org.json.JSONArray(); val fmt = java.time.format.DateTimeFormatter.ofPattern("MMM d, yy", java.util.Locale.US)
        fun best(lbl: String, sel: (JSONObject) -> Double, lower: Boolean, show: (JSONObject) -> String) =
            all.filter { sel(it) > 0 }.let { l -> if (lower) l.minByOrNull(sel) else l.maxByOrNull(sel) }?.let { pr.put(JSONObject().put("l", lbl).put("v", show(it)).put("d", d(it)?.format(fmt) ?: "")) }
        best("Longest Run", { num(it, "distance") }, false) { String.format("%.2f mi", num(it, "distance")) }
        best("Fastest Pace", { paceSec(str(it, "pace")).toDouble() }, true) { str(it, "pace") + "/mi" }
        best("Peak Avg Power", { num(it, "power") }, false) { "${Math.round(num(it, "power"))}W" }
        best("Most Elev Gain", { num(it, "elevGain") }, false) { String.format(java.util.Locale.US, "%,d ft", Math.round(num(it, "elevGain"))) }
        best("Most Calories", { num(it, "calories") }, false) { String.format(java.util.Locale.US, "%,d cal", Math.round(num(it, "calories"))) }
        o.put("prs", pr)
        // Estimated race times (Riegel, fastest pace ≥1 mi in the last 60 days) and heat acclimation — for the watch's Fitness.
        run {
            var bp = 0; var bd = 0.0
            all.forEach { a -> val dd = d(a) ?: return@forEach; val ago = java.time.temporal.ChronoUnit.DAYS.between(dd, today); if (ago !in 0..59) return@forEach
                val p = paceSec(str(a, "pace")); val mi = num(a, "distance"); if (p > 0 && mi >= 1 && (bp == 0 || p < bp)) { bp = p; bd = mi } }
            if (bp > 0) {
                fun clock(s0: Double): String { val s = Math.round(s0); val h = s / 3600; val m = (s % 3600) / 60; val ss = s % 60
                    return (if (h > 0) "$h:" + String.format("%02d", m) else "$m") + ":" + String.format("%02d", ss) }
                val base = bp * bd; val race = org.json.JSONArray()
                listOf("5K" to 3.10686, "10K" to 6.21371, "Half" to 13.1094, "Marathon" to 26.2188, "50K" to 31.0686).forEach { (l, mi) -> race.put(JSONObject().put("l", l).put("t", clock(base * Math.pow(mi / bd, 1.06)))) }
                o.put("race", race)
            }
            val heat = HashMap<java.time.LocalDate, Double>()
            all.forEach { a -> val t = str(a, "temp").toDoubleOrNull() ?: return@forEach; val dd = d(a) ?: return@forEach; val hrs = durSec(str(a, "duration")) / 3600.0
                if (str(a, "type") == "Rest Day" || hrs <= 0) return@forEach
                val f = if (t <= 70) 0.0 else if (t >= 95) 1.0 else (t - 70) / 25; if (f > 0) heat[dd] = (heat[dd] ?: 0.0) + f * Math.min(hrs, 3.0) }
            val first = all.mapNotNull { a -> d(a)?.takeIf { str(a, "type") != "Rest Day" && !it.isAfter(today) } }.minOrNull()
            if (first != null) { var load = 0.0; var dd = today.minusDays(Math.min(729L, java.time.temporal.ChronoUnit.DAYS.between(first, today)))
                while (!dd.isAfter(today)) { load = load * 0.96 + (heat[dd] ?: 0.0); dd = dd.plusDays(1) }
                o.put("heat", Math.max(0, Math.min(100, Math.round(100 * (1 - Math.exp(-load / 6))).toInt()))) }
        }
        // Today's training-plan workout.
        PlanStore.data(ctx).optJSONObject("current")?.let { p ->
            val st = p.optString("startDate")
            val dn = try { java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(st), today).toInt() + 1 } catch (e: Exception) { 0 }
            if (dn >= 1) o.put("plan", JSONObject().put("week", (dn + 6) / 7).put("day", (dn - 1) % 7 + 1).put("text", PlanStore.workoutFor(p, today.toString()).take(1500)).put("race", p.optString("race")))
        }
        // Recent activities.
        val rec = org.json.JSONArray()
        all.sortedByDescending { str(it, "date") }.take(25).forEach { a -> rec.put(JSONObject().put("date", str(a, "date")).put("title", str(a, "title").ifEmpty { str(a, "type") }).put("type", str(a, "type"))
            .put("mi", num(a, "distance")).put("dur", str(a, "duration")).put("pace", str(a, "pace")).put("hr", num(a, "heartRate")).put("w", num(a, "power")).put("src", sourceLabel(str(a, "source")))) }
        o.put("recent", rec)
        return o
    }

    /** Training Load Ratio by miles (every activity): (ratio, 7-day mi/day, 28-day mi/day), or null. */
    fun acwr(ctx: Context): Triple<Double, Double, Double>? {
        if (fetchedAt(ctx) == 0L) return null
        val today = java.time.LocalDate.now(); val daily = HashMap<String, Double>()
        list(ctx).forEach { a -> val d = str(a, "date").take(10); daily[d] = (daily[d] ?: 0.0) + num(a, "distance") }
        fun lastN(n: Int) = (0 until n).sumOf { daily[today.minusDays(it.toLong()).toString()] ?: 0.0 }
        val acute = lastN(7) / 7; val chronic = lastN(28) / 28
        return if (chronic > 0) Triple(acute / chronic, acute, chronic) else null
    }

    /** Weekly Run Notes by Monday (with notes saved here on top). */
    fun weekNotes(ctx: Context): Map<String, String> {
        val out = HashMap<String, String>()
        raw(ctx).optJSONObject("weekNotes")?.let { o -> o.keys().forEach { out[it] = o.optString(it) } }
        pending(ctx, "wrnSave").forEach { (_, x) -> val w = x.optString("week"); val t = x.optString("text").trim(); if (t.isEmpty()) out.remove(w) else out[w] = t }
        return out
    }
    fun saveWeekNote(ctx: Context, week: String, text: String) { Hydration.queue(ctx, "wrnSave", JSONObject().put("week", week).put("text", text)); Thread { HydrationSync.flush(ctx.applicationContext) }.start() }
    /** A Garmin / Stryd / Strava CSV, imported by the Command Center's own importer. */
    /** Stand-alone: the whole data (list, settings, goals, shoes, notes…) saved as BeyondStride's own. */
    fun rawJson(ctx: Context) = raw(ctx)
    fun setRaw(ctx: Context, o: JSONObject) { p(ctx).edit().putString("data", o.toString()).putLong("at", Math.max(1L, fetchedAt(ctx))).apply(); CloudCopy.dirty(ctx) }
    fun setNotice(ctx: Context, msg: String) = p(ctx).edit().putString("notice", JSONObject().put("msg", msg).put("at", System.currentTimeMillis()).toString()).apply()
    fun importCsv(ctx: Context, csv: String, hint: String, name: String) {
        if (Standalone.on(ctx)) { val app = ctx.applicationContext; Thread { setNotice(app, ActivityImport.importCsv(app, csv, hint)) }.start(); return }
        Hydration.queue(ctx, "actImport", JSONObject().put("csv", csv).put("hint", hint).put("name", name)); Thread { HydrationSync.flush(ctx.applicationContext) }.start() }
    /** The Command Center's last message (e.g. "Imported 12 activities (garmin).") and when. */
    fun notice(ctx: Context): Pair<String, Long>? = try { JSONObject(p(ctx).getString("notice", "") ?: "").let { it.optString("msg") to it.optLong("at") }.takeIf { it.first.isNotEmpty() } } catch (e: Exception) { null }

    fun saveSettings(ctx: Context, x: JSONObject) { Hydration.queue(ctx, "actSettings", x); Thread { HydrationSync.flush(ctx.applicationContext) }.start() }
    fun saveShoe(ctx: Context, shoe: JSONObject) { Hydration.queue(ctx, "shoeSave", JSONObject().put("id", shoe.optString("id")).put("shoe", shoe)); Thread { HydrationSync.flush(ctx.applicationContext) }.start() }
    fun deleteShoe(ctx: Context, id: String) { Hydration.queue(ctx, "shoeDel", JSONObject().put("id", id)); Thread { HydrationSync.flush(ctx.applicationContext) }.start() }

    /** Miles on a shoe: every activity on or after its start date, plus its starting miles (the Command Center's shoeCalcMiles). */
    fun shoeMiles(ctx: Context, shoe: JSONObject): Double {
        val start = shoe.optString("startDate")
        val tot = list(ctx).filter { val d = str(it, "date"); d.isNotEmpty() && (start.isEmpty() || d >= start) }
            .sumOf { num(it, "distance") * (if (str(it, "distUnit").lowercase() == "km") 0.621371 else 1.0) }
        return Math.round((tot + shoe.optDouble("offsetMiles", 0.0)) * 100) / 100.0
    }

    /** Saves an activity (new or changed). [clear]: fields emptied. */
    fun save(ctx: Context, act: JSONObject, clear: List<String> = emptyList()) {
        Hydration.queue(ctx, "actSave", JSONObject().put("id", act.get("id")).put("act", act).put("clear", JSONArray(clear)))
        Thread { HydrationSync.flush(ctx.applicationContext) }.start()
    }

    fun delete(ctx: Context, id: Any) {
        Hydration.queue(ctx, "actDel", JSONObject().put("id", id))
        Thread { HydrationSync.flush(ctx.applicationContext) }.start()
    }

    /** Reads the Command Center's Activities. Call off the main thread. */
    fun refresh(ctx: Context): Boolean {
        if (Standalone.on(ctx)) { Local.commit(ctx); return false }
        if (!NutritionSync.ready(ctx)) return false
        HydrationSync.reconcile(ctx)
        CcStatus.fetch(ctx)
        return try {
            val c = (URL(Live.db(ctx) + "/nutrition/" + NutritionSync.key(ctx) + "/activities.json").openConnection() as HttpURLConnection)
                .apply { connectTimeout = 12_000; readTimeout = 25_000 }
            if (c.responseCode != 200) { p(ctx).edit().putString("status", "Couldn't read Activities (HTTP ${c.responseCode})").apply(); return false }
            val txt = c.inputStream.bufferedReader().readText()
            // What the Command Center last said about sharing (shown when nothing has arrived).
            try {
                val sc = (URL(Live.db(ctx) + "/nutrition/" + NutritionSync.key(ctx) + "/activitiesStatus.json").openConnection() as HttpURLConnection).apply { connectTimeout = 10_000; readTimeout = 10_000 }
                val st = if (sc.responseCode == 200) sc.inputStream.bufferedReader().readText() else "null"
                val o = if (st == "null") null else JSONObject(st)
                p(ctx).edit().putString("status", if (o == null) "The Command Center hasn't tried to share Activities yet — reload its page (it needs the latest version)."
                    else "Command Center: " + o.optString("stage") + (if (o.has("count")) " · ${o.optInt("count")} activities" else "") + (if (o.has("bytes")) " · ${o.optInt("bytes") / 1024} KB" else "") +
                        (if (o.has("status")) " · HTTP ${o.optInt("status")}" else "") + (o.optString("err").takeIf { it.isNotEmpty() }?.let { " · $it" } ?: "") +
                        " · " + android.text.format.DateUtils.getRelativeTimeSpanString(o.optLong("at"))).apply()
            } catch (e: Exception) { }
            try {
                val nc = (URL(Live.db(ctx) + "/nutrition/" + NutritionSync.key(ctx) + "/notice.json").openConnection() as HttpURLConnection).apply { connectTimeout = 10_000; readTimeout = 10_000 }
                if (nc.responseCode == 200) nc.inputStream.bufferedReader().readText().takeIf { it != "null" && it.isNotBlank() }?.let { p(ctx).edit().putString("notice", it).apply() }
            } catch (e: Exception) { }
            if (txt == "null" || txt.isBlank()) return true
            val changed = txt != p(ctx).getString("data", "")
            p(ctx).edit().putString("data", txt).putLong("at", System.currentTimeMillis()).apply()
            changed
        } catch (e: Exception) { false }
    }

    // ── Helpers matching the Command Center ──
    fun num(a: JSONObject, k: String) = a.opt(k)?.toString()?.toDoubleOrNull() ?: 0.0
    fun str(a: JSONObject, k: String) = a.opt(k)?.toString()?.takeIf { it != "null" } ?: ""

    /** "1:32:15" / "32:15" / "32 min" → seconds. */
    fun durSec(s: String): Int {
        val t = s.trim(); if (t.isEmpty()) return 0
        val parts = t.split(":").map { it.trim().toDoubleOrNull() ?: return 0 }
        return when (parts.size) { 3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]).toInt(); 2 -> (parts[0] * 60 + parts[1]).toInt()
            else -> Regex("(\\d+(?:\\.\\d+)?)").find(t)?.groupValues?.get(1)?.toDoubleOrNull()?.let { (it * 60).toInt() } ?: 0 }
    }
    /** "1h 32m 15s" like the Command Center's fmtDuration. */
    fun fmtDur(s: String): String {
        val tot = if (s.contains(":")) durSec(s) else 0
        if (tot <= 0) return s
        val h = tot / 3600; val m = (tot % 3600) / 60; val sec = tot % 60
        return listOfNotNull(if (h > 0) "${h}h" else null, if (m > 0) "${m}m" else null, if (sec > 0) "${sec}s" else null).joinToString(" ")
    }
    fun paceSec(s: String): Int {
        val t = s.trim().replace("--", ""); if (t.isEmpty()) return 0
        Regex("(\\d+):(\\d{1,2})").find(t)?.let { return it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt() }
        return t.toDoubleOrNull()?.takeIf { it > 0 }?.let { (it * 60).toInt() } ?: 0
    }
    fun isRun(type: String) = type.lowercase().let { it.contains("run") || it in listOf("treadmill", "treadmill running") }

    val TYPE_COLOR = mapOf("Run" to 0xFFE67E22.toInt(), "Ride" to 0xFF8E44AD.toInt(), "Walk" to 0xFF27AE60.toInt(), "Swim" to 0xFF2980B9.toInt(),
        "Strength" to 0xFFE74C3C.toInt(), "Other" to 0xFF95A5A6.toInt())
    val SOURCE_LABEL = mapOf("manual" to "Manual", "garmin" to "Garmin", "stryd" to "Stryd", "strava" to "Strava", "gear2" to "Gear Tracker II",
        "screenshot" to "Screenshot", "watch" to "BeyondStride")
    val SOURCE_COLOR = mapOf("garmin" to 0xFF00B2E3.toInt(), "strava" to 0xFFFC4C02.toInt(), "stryd" to 0xFFFF6B35.toInt(), "gear2" to 0xFF16A085.toInt(),
        "watch" to 0xFF667EEA.toInt(), "screenshot" to 0xFF6B7280.toInt(), "manual" to 0xFF888888.toInt())
    fun sourceLabel(s: String) = SOURCE_LABEL[s.ifEmpty { "manual" }.lowercase()] ?: s.replaceFirstChar { it.uppercase() }

    /** Calories when left blank — the Command Center's estimate (ACSM running / walking, cycling power, METs). */
    fun estimateCalories(type: String, distMi: Double, duration: String, power: Double, elevFt: Double, weightLb: Double): Int {
        val hours = durSec(duration) / 3600.0; if (hours <= 0 || type == "Rest Day") return 0
        val kg = (if (weightLb > 0) weightLb else 160.0) * 0.453592
        val mph = if (distMi > 0) distMi / hours else 0.0
        var met = 5.0
        when (type) {
            "Run", "Walk" -> {
                val mmin = mph * 26.8224; val distM = distMi * 1609.34; val grade = if (distM > 0) elevFt * 0.3048 / distM else 0.0
                met = if (mmin > 0) (if (type == "Run") 0.2 * mmin + 0.9 * mmin * grade + 3.5 else 0.1 * mmin + 1.8 * mmin * grade + 3.5) / 3.5 else if (type == "Run") 8.0 else 3.5
            }
            "Ride" -> { if (power > 0) return Math.round(power * hours * 3.6).toInt()
                met = if (mph > 0) (if (mph < 10) 4.0 else if (mph < 14) 6.0 else if (mph < 16) 8.0 else if (mph < 20) 10.0 else 12.0) else 8.0 }
            "Swim" -> met = 7.0
        }
        return Math.max(0, Math.round(met * kg * hours).toInt())
    }
}
