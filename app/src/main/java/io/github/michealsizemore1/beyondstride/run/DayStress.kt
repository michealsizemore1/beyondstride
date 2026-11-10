package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/**
 * All-day data from the Galaxy Watch (through Samsung Health → Health Connect) for Body Battery:
 * heart rate, steps and HRV, grouped by hour. Read in the background at most every 15 minutes.
 */
object DayStress {
    val HR = HealthPermission.getReadPermission(HeartRateRecord::class)
    val STEPS = HealthPermission.getReadPermission(StepsRecord::class)
    val HRV = HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class)
    val PERMS = setOf(HR, STEPS, HRV)

    data class Hour(val hr: Double, val hrMin: Int, val n: Int, val steps: Int, val hrv: Double)

    private fun p(ctx: Context) = ctx.getSharedPreferences("daystress", 0)

    fun granted(ctx: Context): Set<String> = try {
        if (!SleepData.available(ctx)) emptySet() else runBlocking { HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions() }.intersect(PERMS)
    } catch (e: Exception) { emptySet() }
    fun ready(ctx: Context) = HR in granted(ctx)

    /** Reads today's hours (call off the main thread). */
    fun sync(ctx: Context, force: Boolean = false) {
        val pr = p(ctx); val now = System.currentTimeMillis()
        if (!force && now - pr.getLong("at", 0L) < 15 * 60_000L) return
        val g = granted(ctx); if (HR !in g) return
        pr.edit().putLong("at", now).apply()
        val zone = ZoneId.systemDefault(); val day = LocalDate.now()
        val start = day.atStartOfDay(zone).toInstant(); val end = java.time.Instant.now()
        val client = HealthConnectClient.getOrCreate(ctx); val range = TimeRangeFilter.between(start, end)
        val hrSum = DoubleArray(24); val hrN = IntArray(24); val hrMin = IntArray(24) { 999 }
        val steps = IntArray(24); val hrvSum = DoubleArray(24); val hrvN = IntArray(24)
        try {
            runBlocking { client.readRecords(ReadRecordsRequest(HeartRateRecord::class, range, pageSize = 5000)).records }.flatMap { it.samples }.forEach { s ->
                val h = s.time.atZone(zone).hour; val b = s.beatsPerMinute.toInt()
                if (b in 30..220) { hrSum[h] += b.toDouble(); hrN[h]++; if (b < hrMin[h]) hrMin[h] = b }
            }
        } catch (e: Exception) { }
        if (STEPS in g) try {
            runBlocking { client.readRecords(ReadRecordsRequest(StepsRecord::class, range, pageSize = 5000)).records }.forEach { r ->
                // Spread a record over the hours it covers.
                val a = r.startTime.atZone(zone); val b = r.endTime.atZone(zone)
                val secs = Math.max(1L, java.time.Duration.between(a, b).seconds)
                var t = a
                while (t.isBefore(b)) {
                    val next = t.withMinute(0).withSecond(0).withNano(0).plusHours(1).let { if (it.isAfter(b)) b else it }
                    val share = java.time.Duration.between(t, next).seconds.toDouble() / secs
                    if (t.toLocalDate() == day) steps[t.hour] += Math.round(r.count * share).toInt()
                    t = next
                }
            }
        } catch (e: Exception) { }
        if (HRV in g) try {
            runBlocking { client.readRecords(ReadRecordsRequest(HeartRateVariabilityRmssdRecord::class, range)).records }.forEach { r ->
                val h = r.time.atZone(zone).hour; hrvSum[h] += r.heartRateVariabilityMillis; hrvN[h]++
            }
        } catch (e: Exception) { }
        val arr = JSONArray()
        for (h in 0 until 24) arr.put(JSONObject().put("hr", if (hrN[h] > 0) hrSum[h] / hrN[h] else 0.0).put("min", if (hrN[h] > 0) hrMin[h] else 0)
            .put("n", hrN[h]).put("steps", steps[h]).put("hrv", if (hrvN[h] > 0) hrvSum[h] / hrvN[h] else 0.0))
        pr.edit().putString("day", day.toString()).putString("hours", arr.toString()).apply()
    }

    /** Today's hours, or null when nothing has been read today. */
    fun hours(ctx: Context): List<Hour>? {
        val pr = p(ctx); if (pr.getString("day", "") != LocalDate.now().toString()) return null
        val a = try { JSONArray(pr.getString("hours", "[]")) } catch (e: Exception) { return null }
        if (a.length() < 24) return null
        val out = (0 until 24).map { val o = a.getJSONObject(it); Hour(o.optDouble("hr"), o.optInt("min"), o.optInt("n"), o.optInt("steps"), o.optDouble("hrv")) }
        return if (out.sumOf { it.n } == 0) null else out
    }
    fun lastRead(ctx: Context) = p(ctx).getLong("at", 0L)
}
