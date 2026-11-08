package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Bringing activities in without the Command Center:
 *  • CSV — the Command Center's Garmin / Stryd / Strava importers (same column rules, same duplicate check).
 *  • Health Connect — workouts that Garmin Connect, Samsung Health, Strava… write there (not BeyondStride's own runs).
 */
object ActivityImport {
    // ── CSV ──
    fun parseCsv(text: String): List<List<String>> = text.split(Regex("\r?\n")).filter { it.isNotBlank() }.map { line ->
        val cols = ArrayList<String>(); val cur = StringBuilder(); var inQ = false; var i = 0
        while (i < line.length) { val c = line[i]
            when { c == '"' -> if (inQ && i + 1 < line.length && line[i + 1] == '"') { cur.append('"'); i++ } else inQ = !inQ
                c == ',' && !inQ -> { cols += cur.toString().trim(); cur.setLength(0) }
                else -> cur.append(c) }
            i++ }
        cols += cur.toString().trim(); cols
    }
    private fun date(s0: String): String {
        val s = s0.trim().split(" ")[0]
        if (Regex("^\\d{4}-\\d{2}-\\d{2}").containsMatchIn(s)) return s.take(10)
        Regex("^(\\d{1,2})/(\\d{1,2})/(\\d{4})").find(s)?.let { m -> return m.groupValues[3] + "-" + m.groupValues[1].padStart(2, '0') + "-" + m.groupValues[2].padStart(2, '0') }
        return s.take(10)
    }
    private fun clock(secs: Double): String { val h = (secs / 3600).toInt(); val m = ((secs % 3600) / 60).toInt(); val s = Math.round(secs % 60).toInt()
        return (if (h > 0) "$h:" else "") + (if (h > 0) String.format(Locale.US, "%02d", m) else "$m") + ":" + String.format(Locale.US, "%02d", s) }
    private fun num(s: String?) = s?.replace(",", "")?.toDoubleOrNull() ?: 0.0

    /** Which importer: Strava, Garmin, Stryd or plain ("manual"). */
    fun detect(headers: List<String>): String {
        val strava = headers.any { Regex("^Activity ID$", RegexOption.IGNORE_CASE).matches(it.trim()) } || headers.any { Regex("Moving Time", RegexOption.IGNORE_CASE).containsMatchIn(it) }
        val garmin = !strava && headers.any { Regex("Activity Type", RegexOption.IGNORE_CASE).containsMatchIn(it) }
        val stryd = !strava && (headers.any { Regex("Avg Power.*W", RegexOption.IGNORE_CASE).containsMatchIn(it) } || headers.any { Regex("distance.*m", RegexOption.IGNORE_CASE).containsMatchIn(it) })
        return if (strava) "strava" else if (garmin) "garmin" else if (stryd) "stryd" else "manual"
    }

    fun map(r: Map<String, String>, source: String, headers: List<String>): JSONObject? {
        fun g(vararg keys: String): String? { for (k in keys) for (h in headers) if (h.lowercase().contains(k.lowercase()) && !r[h].isNullOrBlank()) return r[h]!!.trim(); return null }
        var dt = ""; var title = ""; var type = "Run"; var dist = 0.0; var dur = ""; var pace = ""; var power = 0.0; var hr = 0.0; var cad = 0.0; var cal = 0.0; var elev = 0.0
        when (source) {
            "garmin" -> {
                dt = g("Date")?.let { date(it) } ?: ""; title = g("Title") ?: g("Activity Name") ?: "Activity"
                val raw = g("Activity Type") ?: "Run"
                val map = mapOf("running" to "Run", "trail running" to "Run", "treadmill running" to "Run", "track running" to "Run", "road running" to "Run", "virtual run" to "Run", "indoor running" to "Run",
                    "cycling" to "Bike", "road cycling" to "Bike", "mountain biking" to "Bike", "indoor cycling" to "Bike", "swimming" to "Swim", "open water swimming" to "Swim", "walking" to "Walk",
                    "hiking" to "Hike", "strength training" to "Strength", "yoga" to "Yoga", "cardio" to "Cardio")
                type = map[raw.lowercase()] ?: raw
                val d = num(g("Distance")); dist = Math.round((if (d > 0) (if (d < 30) d else d * 0.621371) else 0.0) * 100) / 100.0
                dur = g("Time") ?: g("Duration") ?: ""; pace = g("Avg Pace") ?: ""
                power = num(g("Avg Power")); hr = num(g("Avg HR")); cad = num(g("Avg Run Cadence")).takeIf { it > 0 } ?: num(g("Cadence")); cal = num(g("Calories"))
                val asc = num(g("Total Ascent") ?: g("Elev Gain")); elev = (if (asc > 500) Math.round(asc * 3.28084) else Math.round(asc)).toDouble()
            }
            "stryd" -> {
                dt = date(g("Date", "Start Date", "Workout Date", "Activity Date", "Start Time", "Timestamp") ?: ""); title = g("Activity Name", "Activity", "Workout Name", "Name", "Title") ?: "Stryd Run"
                type = g("Activity Type", "Workout Type", "Type") ?: "Run"
                val d = num(g("Distance")); dist = if (d > 200) Math.round(d / 1609.34 * 100) / 100.0 else Math.round(d * 100) / 100.0
                val dr = g("Duration", "Moving Duration", "Elapsed Time", "Time") ?: ""; val ds = dr.toDoubleOrNull() ?: 0.0
                dur = if (ds > 0 && !dr.contains(":")) clock(ds) else dr
                val ps = (g("Avg Pace", "Pace") ?: "").trim()
                pace = if (ps.contains(":")) ps else (ps.toDoubleOrNull() ?: 0.0).let { if (it > 0) { val pm = Math.floor(it).toInt(); val s = Math.round((it - pm) * 60).toInt(); "$pm:" + (if (s < 10) "0" else "") + s } else "" }
                power = num(g("Avg Power", "Power")); hr = num(g("Avg HR", "Heart Rate")); cad = num(g("Avg Cadence", "Cadence")); cal = num(g("Calories"))
                val e = num(g("Elev Gain", "Elevation Gain")); elev = (if (e > 0) (if (e < 500) Math.round(e * 3.28084) else Math.round(e)) else 0L).toDouble()
            }
            "strava" -> {
                val raw = g("Activity Date") ?: ""
                dt = Regex("^\\d{4}-\\d{2}-\\d{2}").find(raw)?.value ?: try {
                    java.time.LocalDateTime.parse(raw.trim(), java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm:ss a", Locale.US)).toLocalDate().toString() } catch (e: Exception) { date(raw) }
                title = g("Activity Name") ?: "Strava Activity"
                val map = mapOf("run" to "Run", "trail run" to "Run", "virtual run" to "Run", "treadmill" to "Run", "ride" to "Ride", "virtual ride" to "Ride", "mountain bike ride" to "Ride", "gravel ride" to "Ride",
                    "e-bike ride" to "Ride", "ebike ride" to "Ride", "handcycle" to "Ride", "velomobile" to "Ride", "walk" to "Walk", "hike" to "Walk", "swim" to "Swim", "weight training" to "Strength",
                    "workout" to "Strength", "yoga" to "Strength", "crossfit" to "Strength")
                type = map[(g("Activity Type") ?: "Run").lowercase()] ?: "Other"
                val m = num(g("Distance")); dist = if (m > 0) Math.round(m / 1609.34 * 100) / 100.0 else 0.0
                val mv = num(g("Moving Time")).takeIf { it > 0 } ?: num(g("Elapsed Time"))
                dur = if (mv > 0) clock(mv) else ""
                if (dist > 0 && mv > 0) { val p = mv / dist; val pm = Math.floor(p / 60).toInt(); val s = Math.round(p % 60).toInt(); pace = "$pm:" + (if (s < 10) "0" else "") + s }
                power = num(g("Average Watts")); hr = num(g("Average Heart Rate")); cad = num(g("Average Cadence")); cal = num(g("Calories"))
                val em = num(g("Elevation Gain")); elev = if (em > 0) Math.round(em * 3.28084).toDouble() else 0.0
            }
            else -> {
                dt = date(g("Date") ?: ""); title = g("Title", "Name", "Activity") ?: "Activity"; type = g("Type", "Activity Type") ?: "Run"
                dist = num(g("Distance")); dur = g("Duration", "Time") ?: ""; pace = g("Pace", "Avg Pace") ?: ""; power = num(g("Power", "Avg Power"))
                hr = num(g("HR", "Heart Rate", "Avg HR")); cad = num(g("Cadence")); cal = num(g("Calories")); elev = num(g("Elev Gain", "Elevation Gain"))
            }
        }
        if (dt.isEmpty()) return null
        val route = (g("Route URL", "Map URL", "routeUrl") ?: "").takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: ""
        return JSONObject().put("id", System.currentTimeMillis() + Math.random()).put("source", source).put("date", dt).put("title", title).put("type", type)
            .put("distance", dist).put("duration", dur).put("pace", pace).put("power", power).put("heartRate", hr).put("cadence", cad)
            .put("calories", cal).put("elevGain", elev).put("routeUrl", route).put("notes", "")
    }

    private fun key(a: JSONObject) = ActivityStore.str(a, "date") + "_" + ActivityStore.str(a, "title") + "_" + ActivityStore.num(a, "distance").let { if (it == Math.floor(it)) it.toLong().toString() else it.toString() }

    /** Imports a CSV into BeyondStride's own Activities. Returns the message the Command Center showed. */
    fun importCsv(ctx: Context, text: String, hint: String): String {
        val rows = parseCsv(text); if (rows.size < 2) return "No data rows found in CSV."
        val headers = rows[0].map { it.trim() }
        val source = hint.ifEmpty { detect(headers) }
        val parsed = rows.drop(1).mapNotNull { row -> map(headers.mapIndexed { i, h -> h to (row.getOrNull(i) ?: "") }.toMap(), source, headers) }
        if (parsed.isEmpty()) return "Could not parse any activities from this file."
        val keys = ActivityStore.list(ctx).map { key(it) }.toMutableSet()
        val fresh = parsed.filter { keys.add(key(it)) }
        fresh.forEach { ActivityStore.save(ctx, it, emptyList()) }
        val skip = parsed.size - fresh.size
        return "Imported ${fresh.size} activities ($source)." + (if (skip > 0) " $skip duplicate(s) skipped." else "")
    }

    // ── Health Connect ──
    val PERMS = setOf(ExerciseSessionRecord::class, DistanceRecord::class, HeartRateRecord::class, TotalCaloriesBurnedRecord::class, PowerRecord::class, ElevationGainedRecord::class)
        .map { HealthPermission.getReadPermission(it) }.toSet()
    private fun p(ctx: Context) = ctx.getSharedPreferences("hcimport", 0)
    fun enabled(ctx: Context) = p(ctx).getBoolean("on", false)
    fun setEnabled(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("on", v).apply()
    fun lastInfo(ctx: Context) = p(ctx).getString("info", "") ?: ""
    fun granted(ctx: Context): Boolean = SleepData.available(ctx) && try {
        runBlocking { HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions().containsAll(PERMS) } } catch (e: Exception) { false }

    private val TYPES = mapOf(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING to "Run", ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL to "Run",
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING to "Ride", ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY to "Ride", ExerciseSessionRecord.EXERCISE_TYPE_WALKING to "Walk",
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING to "Walk", ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL to "Swim", ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER to "Swim",
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING to "Strength", ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING to "Strength", ExerciseSessionRecord.EXERCISE_TYPE_YOGA to "Strength")
    private fun sourceOf(pkg: String) = when {
        pkg.contains("garmin") -> "garmin"; pkg.contains("strava") -> "strava"; pkg.contains("shealth") || pkg.contains("samsung") -> "samsung"; pkg.contains("stryd") -> "stryd"; else -> "healthconnect" }

    /** Adds Health Connect workouts from the last [days] days that aren't in Activities yet. Returns how many. Call off the main thread. */
    fun fromHealthConnect(ctx: Context, days: Int = 14): Int {
        if (!granted(ctx)) return 0
        val client = HealthConnectClient.getOrCreate(ctx); val zone = ZoneId.systemDefault()
        val end = Instant.now(); val start = end.minus(days.toLong(), ChronoUnit.DAYS)
        val seen = p(ctx).getStringSet("ids", emptySet())!!.toMutableSet()
        val existing = ActivityStore.list(ctx)
        var added = 0
        val sessions = runBlocking { client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, TimeRangeFilter.between(start, end))).records }
        sessions.forEach { s ->
            val pkg = s.metadata.dataOrigin.packageName
            if (pkg == ctx.packageName || s.metadata.id in seen) return@forEach
            val range = TimeRangeFilter.between(s.startTime, s.endTime)
            fun <T : androidx.health.connect.client.records.Record> read(c: kotlin.reflect.KClass<T>) = try { runBlocking { client.readRecords(ReadRecordsRequest(c, range)).records } } catch (e: Exception) { emptyList() }
            val miles = read(DistanceRecord::class).sumOf { it.distance.inMiles }
            val hrs = read(HeartRateRecord::class).flatMap { it.samples }.map { it.beatsPerMinute }
            val kcal = read(TotalCaloriesBurnedRecord::class).sumOf { it.energy.inKilocalories }
            val watts = read(PowerRecord::class).flatMap { it.samples }.map { it.power.inWatts }
            val elevFt = read(ElevationGainedRecord::class).sumOf { it.elevation.inFeet }
            val secs = ChronoUnit.SECONDS.between(s.startTime, s.endTime).toDouble()
            val dt = s.startTime.atZone(zone).toLocalDate().toString()
            // Already there (e.g. from a CSV or a BeyondStride run)? Same day, distance within 5 %.
            if (existing.any { ActivityStore.str(it, "date") == dt && (miles <= 0 && ActivityStore.num(it, "distance") <= 0 || miles > 0 && Math.abs(ActivityStore.num(it, "distance") - miles) / miles < 0.05) }) { seen += s.metadata.id; return@forEach }
            val type = TYPES[s.exerciseType] ?: "Other"
            val a = JSONObject().put("id", System.currentTimeMillis() + Math.random()).put("source", sourceOf(pkg)).put("date", dt)
                .put("title", s.title?.takeIf { it.isNotBlank() } ?: type).put("type", type).put("distance", Math.round(miles * 100) / 100.0).put("duration", clock(secs))
            if (miles > 0.05) { val p = secs / miles; a.put("pace", "${(p / 60).toInt()}:" + String.format(Locale.US, "%02d", Math.round(p % 60))) }
            if (hrs.isNotEmpty()) a.put("heartRate", Math.round(hrs.average()))
            if (watts.isNotEmpty()) a.put("power", Math.round(watts.average()))
            if (kcal > 0) a.put("calories", Math.round(kcal)); if (elevFt > 0) a.put("elevGain", Math.round(elevFt))
            a.put("notes", "Imported from Health Connect")
            ActivityStore.save(ctx, a, emptyList()); seen += s.metadata.id; added++
        }
        p(ctx).edit().putStringSet("ids", seen).putString("info", "Last checked " + java.text.SimpleDateFormat("MMM d, h:mm a", Locale.US).format(java.util.Date()) + " · $added added").apply()
        return added
    }
}
