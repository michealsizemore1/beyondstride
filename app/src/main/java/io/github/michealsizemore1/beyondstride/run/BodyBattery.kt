package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import io.github.michealsizemore1.beyondstride.Readiness
import io.github.michealsizemore1.beyondstride.ReadinessSync
import java.time.LocalDate
import java.time.LocalTime

/**
 * 🔋 Body Battery (like Garmin's): 0–100 energy reserve.
 *  • Charges overnight — how long you slept, how calm your sleeping heart was, and this morning's HRV and resting HR
 *    vs your normal (or the readiness score when there's no check).
 *  • Drains through the day — a little every waking hour, plus each workout by how long and how hard it was (heart rate).
 * It's an estimate from the data BeyondStride has (no all-day stress tracking), so treat it as a guide.
 */
object BodyBattery {
    data class Result(val now: Int, val wake: Int, val charged: Int, val activity: Int, val daily: Int, val bedtime: Int,
                      val label: String, val color: Int, val notes: List<String>)

    private const val GREEN = 0xFF2ECC71.toInt(); private const val TEAL = 0xFF1ABC9C.toInt()
    private const val YELLOW = 0xFFF1C40F.toInt(); private const val ORANGE = 0xFFE67E22.toInt(); private const val RED = 0xFFE74C3C.toInt()
    private fun clamp(v: Double, lo: Double, hi: Double) = Math.max(lo, Math.min(hi, v))
    private fun p(ctx: Context) = ctx.getSharedPreferences("bodybattery", 0)

    /** Waking hour (default 8 AM — your usual). */
    private fun wakeMin(ctx: Context, date: String): Int {
        val chk = ReadinessSync.checks(ctx).lastOrNull { it.day == date && it.at > 0 }
        val t = chk?.let { java.time.Instant.ofEpochMilli(it.at).atZone(java.time.ZoneId.systemDefault()).toLocalTime() }
        return t?.let { Math.min(it.hour * 60 + it.minute, 11 * 60) } ?: 8 * 60
    }

    /** Workout drain for a day (each activity: minutes × intensity from average HR). */
    private fun activityDrain(ctx: Context, date: String): Pair<Int, List<String>> {
        val st = ActivityStore.settings(ctx)
        val max = st.optDouble("maxHr", 0.0).takeIf { it > 0 } ?: 174.0; val rest = st.optDouble("restHr", 0.0).takeIf { it > 0 } ?: 55.0
        var total = 0.0; val notes = ArrayList<String>()
        S.list(ctx).filter { S.str(it, "date").take(10) == date && S.str(it, "type") != "Rest Day" }.forEach { a ->
            val mins = S.durSec(S.str(a, "duration")) / 60.0
            if (mins <= 0) return@forEach
            val hr = S.num(a, "heartRate")
            val k = if (hr > 0) clamp((hr - rest) / (max - rest), 0.1, 1.0) else 0.5
            val type = S.str(a, "type").lowercase()
            val base = if (type.contains("walk")) 0.08 else if (type.contains("strength") || type.contains("yoga")) 0.12 else 0.15
            val d = mins * (base + 0.45 * k); total += d
            notes += "−${Math.round(d)} ${S.str(a, "title").ifEmpty { S.str(a, "type").ifEmpty { "Workout" } }} (${Math.round(mins)} min" + (if (hr > 0) ", avg HR ${Math.round(hr)}" else "") + ")"
        }
        return Math.round(total).toInt() to notes
    }

    /** Energy at wake-up for [date]. */
    fun wakeValue(ctx: Context, date: String, carry: Int? = null): Pair<Int, Int> {
        val nights = ReadinessSync.nights(ctx); val night = nights.lastOrNull { it.day == date }
        val checks = ReadinessSync.checks(ctx); val chk = checks.lastOrNull { it.day == date }
        val past = checks.filter { it.day < date }.takeLast(14)
        val hrvBase = past.map { it.rmssd }.takeIf { it.size >= 3 }?.average()
        val rhrBase = past.map { it.hr }.filter { it > 0 }.takeIf { it.size >= 3 }?.average()
        val sleepBase = nights.filter { it.day < date && it.sleepHr > 0 }.takeLast(14).map { it.sleepHr }.takeIf { it.size >= 3 }?.average()
        // Where yesterday ended (it carries over; Garmin's battery doesn't reset to zero at night).
        val start = carry ?: 20
        val mins = night?.minutes ?: 0
        val sleepPart = clamp(mins / 480.0, 0.0, 1.1)
        var quality = 1.0
        if (chk != null && hrvBase != null && hrvBase > 0) quality *= clamp(chk.rmssd / hrvBase, 0.65, 1.25)
        if (chk != null && rhrBase != null && chk.hr > 0) quality *= clamp(1 - (chk.hr - rhrBase) / 20.0, 0.75, 1.15)
        if (night != null && sleepBase != null && night.sleepHr > 0) quality *= clamp(1 - (night.sleepHr - sleepBase) / 15.0, 0.8, 1.1)
        var charge = if (mins > 0) 85.0 * sleepPart * quality else 55.0 * quality
        // No sleep data yet: lean on readiness when it's there.
        if (mins <= 0 && date == Readiness.today()) Insights.readiness(ctx)?.let { r -> return clamp(r.score.toDouble(), 5.0, 100.0).toInt() to Math.max(0, r.score - start) }
        val wake = clamp(start + charge, 5.0, 100.0)
        return Math.round(wake).toInt() to Math.round(wake - start).toInt()
    }

    private fun endOfDay(ctx: Context, date: String, depth: Int = 0): Int {
        val key = "end_$date"
        if (date < Readiness.today()) p(ctx).getInt(key, -1).takeIf { it >= 0 }?.let { return it }
        val carry = if (depth < 6) endOfDay(ctx, LocalDate.parse(date).minusDays(1).toString(), depth + 1) else 20
        val (wake, _) = wakeValue(ctx, date, carry)
        val (act, _) = activityDrain(ctx, date)
        val awakeH = 16.0
        val v = Math.round(clamp(wake - act - awakeH * 3.0, 5.0, 100.0)).toInt()
        if (date < Readiness.today()) p(ctx).edit().putInt(key, v).apply()
        return v
    }

    fun today(ctx: Context): Result {
        val date = Readiness.today()
        val carry = endOfDay(ctx, LocalDate.parse(date).minusDays(1).toString())
        val (wake, charged) = wakeValue(ctx, date, carry)
        val (act, actNotes) = activityDrain(ctx, date)
        val now = LocalTime.now(); val nowMin = now.hour * 60 + now.minute
        val awakeH = Math.max(0, nowMin - wakeMin(ctx, date)) / 60.0
        val daily = Math.round(awakeH * 3.0).toInt()
        val v = Math.round(clamp((wake - act - daily).toDouble(), 5.0, 100.0)).toInt()
        val toBed = Math.max(0.0, (23 * 60 + 59 - nowMin) / 60.0)
        val bed = Math.round(clamp(v - toBed * 3.0, 5.0, 100.0)).toInt()
        val (label, color) = when { v >= 76 -> "High" to GREEN; v >= 51 -> "Good" to TEAL; v >= 26 -> "Medium" to YELLOW; v >= 11 -> "Low" to ORANGE; else -> "Very low" to RED }
        val notes = ArrayList<String>()
        notes += "+$charged overnight → woke at $wake"
        notes += actNotes
        if (daily > 0) notes += "−$daily daily activity & stress"
        notes += when {
            v >= 76 -> "Plenty in the tank — a good day for a hard or long session."
            v >= 51 -> "Solid energy — train as planned."
            v >= 26 -> "Getting used up — keep it easy and get to bed on time."
            else -> "Running low — rest, eat, hydrate and go to bed early."
        }
        return Result(v, wake, charged, act, daily, bed, label, color, notes)
    }

    /** Wake-up values for the last [n] days (oldest first) for the little chart. */
    fun history(ctx: Context, n: Int = 7): List<Pair<String, Int>> = (n - 1 downTo 0).map { i ->
        val d = LocalDate.now().minusDays(i.toLong()).toString()
        d to wakeValue(ctx, d, endOfDay(ctx, LocalDate.parse(d).minusDays(1).toString())).first
    }
}
