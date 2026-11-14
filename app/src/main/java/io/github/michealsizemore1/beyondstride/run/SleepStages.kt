package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import androidx.health.connect.client.records.SleepSessionRecord
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * 😴 The night in detail, from Samsung Health's sleep stages (via Health Connect): deep, light, REM and awake minutes,
 * bed and wake times — and BeyondStride's own Sleep Score (Samsung keeps its score to itself).
 *
 * Sleep Score (0–100) = half amount + half quality (calibrated to sit close to Samsung's score):
 *  • Amount (0–100): full from 7½ hours, zero at 4 hours.
 *  • Quality (0–100): deep sleep (25 — full at 15%+ of the night), REM (25 — full at 20%+), little time awake
 *    (30 — full at 30 min or less, none at 2 hours), and a calm sleeping heart (20 — at or below your 30-night average,
 *    none at 6+ bpm above). Without stages it's judged on the sleeping heart rate and time awake.
 * 85+ Excellent · 70+ Good · 55+ Fair · below Poor.
 */
object SleepStages {
    data class Day(val deep: Int, val light: Int, val rem: Int, val awake: Int, val bed: Long, val wake: Long, val score: Int, val stages: Boolean) {
        fun label() = SleepStages.label(score)
        fun bedText() = fmt(bed); fun wakeText() = fmt(wake)
    }
    private fun p(ctx: Context) = ctx.getSharedPreferences("sleepstages", 0)
    fun label(s: Int) = when { s >= 85 -> "Excellent"; s >= 70 -> "Good"; s >= 55 -> "Fair"; else -> "Poor" }
    fun color(s: Int) = when { s >= 85 -> 0xFF5FD38D.toInt(); s >= 70 -> 0xFF9BE15D.toInt(); s >= 55 -> 0xFFF2C94C.toInt(); else -> 0xFFFF8A80.toInt() }
    private fun fmt(t: Long) = if (t <= 0) "" else java.time.Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", Locale.US))
    fun hm(m: Int) = if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"

    fun get(ctx: Context, date: String): Day? = try {
        JSONObject(p(ctx).getString("days", "{}") ?: "{}").optJSONObject(date)?.let {
            Day(it.optInt("deep"), it.optInt("light"), it.optInt("rem"), it.optInt("awake"), it.optLong("bed"), it.optLong("wake"), it.optInt("score"), it.optBoolean("stages")) }
    } catch (e: Exception) { null }

    /** Works out the night for [day] from its sleep sessions (the main night), given minutes asleep and sleeping HR. */
    fun record(ctx: Context, day: String, sessions: List<SleepSessionRecord>, minutes: Int, sleepHr: Int, hrHistory: List<Int>) {
        if (sessions.isEmpty() || minutes <= 0) return
        val bed = sessions.minOf { it.startTime }; val wake = sessions.maxOf { it.endTime }
        // Stages from one app only (the one with the most stage time), so two apps logging the night don't double it.
        val byApp = sessions.groupBy { it.metadata.dataOrigin.packageName }
        val src = byApp.values.maxByOrNull { g -> g.sumOf { s -> s.stages.sumOf { ChronoUnit.SECONDS.between(it.startTime, it.endTime) } } } ?: sessions
        fun secs(type: Int) = src.sumOf { s -> s.stages.filter { it.stage == type }.sumOf { ChronoUnit.SECONDS.between(it.startTime, it.endTime) } }
        val deep = Math.round(secs(SleepSessionRecord.STAGE_TYPE_DEEP) / 60.0).toInt()
        val rem = Math.round(secs(SleepSessionRecord.STAGE_TYPE_REM) / 60.0).toInt()
        val light = Math.round((secs(SleepSessionRecord.STAGE_TYPE_LIGHT) + secs(SleepSessionRecord.STAGE_TYPE_SLEEPING)) / 60.0).toInt()
        val hasStages = deep + rem > 0
        val span = ChronoUnit.MINUTES.between(bed, wake).toInt()
        val awake = Math.max(0, if (hasStages) Math.round((secs(SleepSessionRecord.STAGE_TYPE_AWAKE) + secs(SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED) + secs(SleepSessionRecord.STAGE_TYPE_OUT_OF_BED)) / 60.0).toInt()
            .let { a -> Math.max(a, span - minutes) } else span - minutes)
        val score = score(minutes, deep, rem, awake, sleepHr, hrHistory, hasStages)
        val m = try { JSONObject(p(ctx).getString("days", "{}") ?: "{}") } catch (e: Exception) { JSONObject() }
        m.put(day, JSONObject().put("deep", deep).put("light", light).put("rem", rem).put("awake", awake).put("bed", bed.toEpochMilli()).put("wake", wake.toEpochMilli())
            .put("score", score).put("stages", hasStages))
        val cut = java.time.LocalDate.now().minusDays(120).toString(); m.keys().asSequence().toList().filter { it < cut }.forEach { m.remove(it) }
        p(ctx).edit().putString("days", m.toString()).apply()
    }

    fun score(minutes: Int, deep: Int, rem: Int, awake: Int, sleepHr: Int, hrHistory: List<Int>, hasStages: Boolean): Int {
        fun lin(v: Double, zero: Double, full: Double) = ((v - zero) / (full - zero)).coerceIn(0.0, 1.0)
        val hrAvg = hrHistory.filter { it > 0 }.takeIf { it.size >= 5 }?.average()
        val hrPts = if (sleepHr > 0 && hrAvg != null) 1 - lin(sleepHr - hrAvg, 0.0, 6.0) else 0.7
        // Half amount, half quality (added, not multiplied — a short night isn't punished twice).
        val amount = 100 * lin(minutes.toDouble(), 240.0, 450.0)
        val quality = if (hasStages) {
            val asleep = Math.max(1, minutes).toDouble()
            25 * lin(deep / asleep, 0.05, 0.15) + 25 * lin(rem / asleep, 0.05, 0.20) + 30 * (1 - lin(awake.toDouble(), 30.0, 120.0)) + 20 * hrPts
        } else 100 * (0.6 + 0.4 * hrPts) * (1 - 0.4 * lin(awake.toDouble(), 30.0, 120.0))
        return Math.round(0.5 * amount + 0.5 * quality).toInt().coerceIn(0, 100)
    }

    /** "Deep 1h 2m · REM 1h 15m · Light 3h 1m · Awake 22m · 11:48 PM–6:05 AM". */
    fun line(d: Day): String = (if (d.stages) "Deep ${hm(d.deep)} · REM ${hm(d.rem)} · Light ${hm(d.light)} · " else "") + "Awake ${hm(d.awake)}" +
        (if (d.bed > 0) " · ${d.bedText()}–${d.wakeText()}" else "")
}
