package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Weigh-ins from a smart scale (Withings Health Mate and others that write to Health Connect):
 *  - the first weigh-in of the morning (before noon) → Weight: wake-up
 *  - a weigh-in within 2 hours after a run ends → post-run (feeds the sweat rate)
 * A weight typed by hand is never replaced; one the scale filled in is updated if the scale has a newer one.
 */
object WeightImport {
    val PERMS = setOf(HealthPermission.getReadPermission(WeightRecord::class))
    private val EXERCISE = HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    private fun p(ctx: Context) = ctx.getSharedPreferences("hcweight", 0)
    fun enabled(ctx: Context) = p(ctx).getBoolean("on", false)
    fun setEnabled(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("on", v).apply()
    fun lastInfo(ctx: Context) = p(ctx).getString("info", "") ?: ""
    fun granted(ctx: Context): Boolean = SleepData.available(ctx) && try {
        runBlocking { HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions().containsAll(PERMS) } } catch (e: Exception) { false }

    private fun r1(d: Double) = Math.round(d * 10) / 10.0

    /** Reads the last [days] days of weigh-ins and fills wake-up / post-run weights. Returns how many were filled. Call off the main thread. */
    fun sync(ctx: Context, days: Int = 3): Int {
        if (!granted(ctx)) return 0
        val zone = ZoneId.systemDefault(); val pr = p(ctx)
        val from = LocalDate.now().minusDays((days - 1).toLong()).atStartOfDay(zone).toInstant()
        val client = HealthConnectClient.getOrCreate(ctx)
        var err = ""
        val raw = try { runBlocking { client.readRecords(ReadRecordsRequest(WeightRecord::class, TimeRangeFilter.between(from, Instant.now()))).records } } catch (e: Exception) { err = e.javaClass.simpleName + ": " + (e.message ?: ""); emptyList() }
        val apps = raw.map { it.metadata.dataOrigin.packageName.substringAfterLast('.') }.distinct().joinToString(", ")
        val weights = raw.map { it.time to it.weight.inPounds }.filter { it.second in 70.0..500.0 }.sortedBy { it.first }
        // When runs ended: BeyondStride's own runs (id = start time) and running sessions in Health Connect.
        val ends = ArrayList<Instant>()
        ActivityStore.list(ctx).forEach { a -> val start = ActivityStore.idStr(a.opt("id")).toLongOrNull() ?: return@forEach
            if (start < 1_500_000_000_000L || !ActivityStore.isRun(ActivityStore.str(a, "type").ifEmpty { "run" })) return@forEach
            ends += Instant.ofEpochMilli(start + ActivityStore.durSec(ActivityStore.str(a, "duration")) * 1000L) }
        try { if (runBlocking { client.permissionController.getGrantedPermissions() }.contains(EXERCISE))
            runBlocking { client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, TimeRangeFilter.between(from, Instant.now()))).records }
                .filter { it.exerciseType in setOf(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL) }.forEach { ends += it.endTime } } catch (e: Exception) { }
        var filled = 0
        weights.groupBy { it.first.atZone(zone).toLocalDate().toString() }.forEach { (date, list) ->
            val day = NutriDay.day(ctx, date)
            val wake = list.firstOrNull { it.first.atZone(zone).hour < 12 }?.second?.let { r1(it) }
            val post = list.firstOrNull { w -> ends.any { e -> !w.first.isBefore(e) && w.first.isBefore(e.plusSeconds(2 * 3600)) } }?.second?.let { r1(it) }
            fun put(field: String, v: Double?, cur: Double) {
                if (v == null) return
                val mine = pr.getFloat("${field}_$date", -1f).toDouble()
                if (cur > 0 && Math.abs(cur - mine) > 0.05) return          // typed by hand: leave it
                if (Math.abs(cur - v) < 0.05) return
                HydrationSync.change(ctx, field, value = v, date = date)
                pr.edit().putFloat("${field}_$date", v.toFloat()).apply(); filled++
            }
            put("weight", wake, day?.weight ?: 0.0)
            if (post != null && post != wake) put("postWeight", post, day?.postWeight ?: 0.0)
        }
        pr.edit().putString("info", "Last checked " + java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.US).format(java.util.Date()) +
            " · " + (weights.lastOrNull()?.let { "latest ${r1(it.second)} lb (from $apps)" } ?: if (err.isNotEmpty()) "couldn't read Health Connect — ${err.take(120)}" else "no weigh-ins in Health Connect in the last $days days")).apply()
        return filled
    }
}
