package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 🩺 Blood pressure from a cuff or watch that writes to Health Connect (Withings, Omron Connect, Samsung Health…).
 * The lowest reading of each back-to-back set (sys/dia, with the pulse the device saved at that moment) is sent with the day
 * summary — the Command Center fills BP and Pulse in its Wellness section (a value you typed there is never replaced).
 */
object BpImport {
    val PERMS = setOf(HealthPermission.getReadPermission(BloodPressureRecord::class), HealthPermission.getReadPermission(HeartRateRecord::class))
    private fun p(ctx: Context) = ctx.getSharedPreferences("hcbp", 0)
    fun enabled(ctx: Context) = p(ctx).getBoolean("on", false)
    fun setEnabled(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("on", v).apply()
    fun lastInfo(ctx: Context) = p(ctx).getString("info", "") ?: ""
    fun granted(ctx: Context): Boolean = SleepData.available(ctx) && try {
        runBlocking { HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions().contains(HealthPermission.getReadPermission(BloodPressureRecord::class)) } } catch (e: Exception) { false }

    /** The day's average: ("118/76", pulse or 0, number of readings), or null. */
    fun day(ctx: Context, date: String): Triple<String, Int, Int>? = try {
        JSONObject(p(ctx).getString("days", "{}") ?: "{}").optJSONObject(date)?.let { Triple(it.optString("bp"), it.optInt("pulse"), it.optInt("n")) }?.takeIf { it.first.isNotEmpty() }
    } catch (e: Exception) { null }

    /** Reads the last [days] days of readings. Returns true when something changed. Call off the main thread. */
    fun sync(ctx: Context, days: Int = 3): Boolean {
        if (!enabled(ctx) || !granted(ctx)) return false
        val zone = ZoneId.systemDefault(); val pr = p(ctx)
        val from = LocalDate.now().minusDays((days - 1).toLong()).atStartOfDay(zone).toInstant()
        val client = HealthConnectClient.getOrCreate(ctx); val range = TimeRangeFilter.between(from, Instant.now())
        var err = ""
        val bps = try { runBlocking { client.readRecords(ReadRecordsRequest(BloodPressureRecord::class, range)).records } } catch (e: Exception) { err = e.message ?: e.javaClass.simpleName; emptyList() }
        // Pulse: a heart-rate reading the same app saved within 2 minutes of the blood pressure.
        val hrs = try { runBlocking { client.readRecords(ReadRecordsRequest(HeartRateRecord::class, range, pageSize = 5000)).records } } catch (e: Exception) { emptyList() }
        val m = try { JSONObject(pr.getString("days", "{}") ?: "{}") } catch (e: Exception) { JSONObject() }
        val before = m.toString()
        bps.groupBy { it.time.atZone(zone).toLocalDate().toString() }.forEach { (date, all) ->
            // Readings taken back-to-back (within 10 minutes) are one set; the lowest reading of each set is the one used
            // (the first usually reads high). Two or more sets in a day are averaged.
            val ok = all.filter { it.systolic.inMillimetersOfMercury in 60.0..260.0 && it.diastolic.inMillimetersOfMercury in 30.0..160.0 }.sortedBy { it.time }
            val sittings = ArrayList<MutableList<BloodPressureRecord>>()
            ok.forEach { r -> val cur = sittings.lastOrNull(); if (cur != null && r.time.epochSecond - cur.last().time.epochSecond <= 600) cur += r else sittings += mutableListOf(r) }
            val list = sittings.map { set -> set.minWith(compareBy<BloodPressureRecord>({ it.systolic.inMillimetersOfMercury }, { it.diastolic.inMillimetersOfMercury })) }
            if (list.isEmpty()) return@forEach
            val sys = list.map { it.systolic.inMillimetersOfMercury }
            val dia = list.map { it.diastolic.inMillimetersOfMercury }
            val pulses = list.mapNotNull { b -> hrs.filter { it.metadata.dataOrigin.packageName == b.metadata.dataOrigin.packageName }
                .flatMap { it.samples }.filter { Math.abs(it.time.epochSecond - b.time.epochSecond) <= 120 }.minByOrNull { Math.abs(it.time.epochSecond - b.time.epochSecond) }?.beatsPerMinute }
            m.put(date, JSONObject().put("bp", "${Math.round(sys.average())}/${Math.round(dia.average())}").put("pulse", if (pulses.isNotEmpty()) Math.round(pulses.average()).toInt() else 0)
                .put("n", list.size).put("taken", ok.size).put("apps", list.map { it.metadata.dataOrigin.packageName.substringAfterLast('.') }.distinct().joinToString(", ")))
        }
        val cut = LocalDate.now().minusDays(60).toString(); m.keys().asSequence().toList().filter { it < cut }.forEach { m.remove(it) }
        val latest = m.keys().asSequence().toList().maxOrNull()?.let { k -> m.optJSONObject(k)?.let { "$k: ${it.optString("bp")}" + (if (it.optInt("pulse") > 0) " · pulse ${it.optInt("pulse")}" else "") +
            (if (it.optInt("taken") > it.optInt("n")) " (lowest of ${it.optInt("taken")} readings)" else "") + " (from ${it.optString("apps")})" } }
        pr.edit().putString("days", m.toString()).putString("info", "Last checked " + java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.US).format(java.util.Date()) + " · " +
            (latest ?: if (err.isNotEmpty()) "couldn't read Health Connect — ${err.take(120)}" else "no blood pressure in Health Connect in the last $days days")).apply()
        return m.toString() != before
    }
}
