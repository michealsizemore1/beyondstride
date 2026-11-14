package io.github.michealsizemore1.beyondstride

import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Garmin / Coros-style coaching numbers, worked out from what BeyondStride already records:
 *  - 💓 HRV Status: the last 7 morning-check HRVs vs your own normal range.
 *  - 📈 Training Status: training load + HRV + Zone 2 fitness trend, in one word.
 *  - ⏱️ Recovery time: hours until you're ready for another hard run.
 *  - 🏁 Race pacing plan: mile-by-mile splits for a goal time (heat-adjusted).
 */
object Coaching {
    private const val DAY = 86_400_000L

    // ── 💓 HRV Status ──
    data class Hrv(val avg7: Double, val lo: Double, val hi: Double, val status: String, val color: Int, val note: String, val n7: Int, val nBase: Int)

    /** Needs ≥3 checks in the last 7 days and ≥14 in the 8–60 days before (the normal range). */
    fun hrvStatus(checks: List<Readiness.Check>, today: String): Hrv? {
        val t = try { java.time.LocalDate.parse(today) } catch (e: Exception) { return null }
        fun ago(c: Readiness.Check) = try { java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(c.day), t) } catch (e: Exception) { -1L }
        val byDay = checks.filter { it.rmssd > 0 }.groupBy { it.day }.mapValues { e -> e.value.maxBy { it.at }.rmssd }
        val recent = byDay.filterKeys { k -> ago(Readiness.Check(k, 0.0, 0)) in 0..6 }.values
        val base = byDay.filterKeys { k -> ago(Readiness.Check(k, 0.0, 0)) in 7..60 }.values
        if (recent.size < 3) return null
        val avg7 = recent.average()
        if (base.size < 14) return Hrv(avg7, 0.0, 0.0, "Building baseline", 0xFF9AA3A0.toInt(),
            "Needs about 3 weeks of morning checks for your normal range (${base.size}/14 so far).", recent.size, base.size)
        val m = base.average(); val sd = max(3.0, sqrt(base.sumOf { (it - m).pow(2) } / base.size))
        val lo = m - sd * 0.75; val hi = m + sd * 0.75
        val (st, col, note) = when {
            avg7 < m - sd * 1.5 -> Triple("Low", 0xFFEF4444.toInt(), "Well below your normal — your body is under strain (hard training, illness, stress or poor sleep). Keep runs easy.")
            avg7 < lo -> Triple("Unbalanced", 0xFFF97316.toInt(), "Below your normal range — recovery is lagging. Go easy until it's back in range.")
            avg7 > m + sd * 1.5 -> Triple("Unbalanced", 0xFFF97316.toInt(), "Unusually high — often after a big effort, travel or illness coming on. Watch how you feel.")
            else -> Triple("Balanced", 0xFF22C55E.toInt(), "Inside your normal range — you're absorbing training well.")
        }
        return Hrv(avg7, lo, hi, st, col, note, recent.size, base.size)
    }

    // ── 📈 Training Status ──
    data class Status(val name: String, val color: Int, val why: String)

    /**
     * [ratio] = 7-day load ÷ usual week (null while building baseline). [z2Now]/[z2Before] = Zone 2 pace (s/mi), last 4 weeks vs 4 before.
     * [daysToRace] = days to race day (null when none).
     */
    fun trainingStatus(ratio: Double?, hrv: Hrv?, z2Now: Int?, z2Before: Int?, daysToRace: Long?): Status {
        val hrvLow = hrv?.status == "Low"; val hrvOff = hrv?.status == "Unbalanced" || hrvLow
        val gain = if (z2Now != null && z2Before != null) z2Before - z2Now else null   // + = faster at the same heart rate
        val fit = when { gain == null -> "Zone 2 trend not ready yet"; gain >= 3 -> "Zone 2 pace ${gain}s/mi faster"; gain <= -5 -> "Zone 2 pace ${-gain}s/mi slower"; else -> "Zone 2 pace steady" }
        val load = ratio?.let { String.format(java.util.Locale.US, "load ratio %.2f", it) } ?: "load baseline building"
        val hv = hrv?.let { "HRV ${it.status.lowercase()}" } ?: "no HRV status yet"
        val why = "$load · $hv · $fit"
        return when {
            ratio == null -> Status("Building baseline", 0xFF9AA3A0.toInt(), why)
            ratio > 1.5 && hrvOff -> Status("Strained", 0xFFEF4444.toInt(), "$why. Load jumped and your body is showing it — take 1–2 easy or rest days.")
            ratio > 1.5 -> Status("Overreaching", 0xFFEF4444.toInt(), "$why. Much more than your usual load — fine for a short block, but ease off soon.")
            hrvLow -> Status("Strained", 0xFFF97316.toInt(), "$why. HRV is well below normal — something besides running may be adding stress. Keep it easy.")
            daysToRace != null && daysToRace in 0..21 && ratio < 1.0 -> Status("Peaking", 0xFFA78BFA.toInt(), "$why. Tapering into race day — fitness holds while fatigue drops. Keep a little speed, rest up.")
            ratio < 0.6 -> Status("Detraining", 0xFF4FA3E0.toInt(), "$why. Well under your usual load for a while costs fitness. Fine for a planned break.")
            ratio < 0.8 -> Status("Recovery", 0xFF4FA3E0.toInt(), "$why. A lighter week — good for soaking up recent training.")
            gain != null && gain <= -5 && !hrvOff -> Status("Unproductive", 0xFFF97316.toInt(), "$why. Normal load but slower at the same heart rate — heat, sleep, fueling or too little easy running.")
            gain != null && gain >= 3 && !hrvOff -> Status("Productive", 0xFF22C55E.toInt(), "$why. Load is healthy and you're getting fitter.")
            hrvOff -> Status("Maintaining", 0xFFF2C94C.toInt(), "$why. Load is fine but recovery is lagging — hold steady rather than add more.")
            else -> Status("Maintaining", 0xFF22C55E.toInt(), "$why. Healthy load; fitness is holding. Add a little volume or a quality day to build.")
        }
    }

    // ── ⏱️ Recovery time ──
    data class Recovery(val hoursLeft: Int, val fromRun: Long, val totalHours: Int)

    fun stress(r: JSONObject) = r.optInt("stress").takeIf { it > 0 } ?: (r.optInt("movingSeconds") / 60.0 * 0.9).toInt()
    /** Hours a run takes to recover from: about 4 h + ⅓ h per stress point (easy hour ≈ 1 day, long run ≈ 2 days), 6–72 h. */
    fun runHours(stress: Int) = (4 + stress / 3.0).coerceIn(6.0, 72.0)

    /** Hours still to go from the last 4 days of runs. A second run on top adds half of what it has left. Readiness < 60 adds 20%, > 80 takes off 10%. */
    fun recovery(runs: List<JSONObject>, readiness: Int?, now: Long = System.currentTimeMillis()): Recovery? {
        val f = when { readiness == null -> 1.0; readiness < 60 -> 1.2; readiness > 80 -> 0.9; else -> 1.0 }
        val left = runs.mapNotNull { r ->
            val end = r.optLong("start") + max(r.optLong("elapsedSeconds"), r.optLong("movingSeconds")) * 1000
            if (now - end !in 0 until 4 * DAY) return@mapNotNull null
            val total = runHours(stress(r)) * f
            val rest = total - (now - end) / 3_600_000.0
            if (rest > 0) Triple(rest, r.optLong("start"), total) else null
        }.sortedByDescending { it.first }
        if (left.isEmpty()) return null
        val h = left.first().first + left.drop(1).sumOf { it.first } * 0.5
        return Recovery(min(96.0, h).roundToInt().coerceAtLeast(1), left.first().second, left.first().third.roundToInt())
    }
    fun recoveryText(r: Recovery?): String = if (r == null) "Recovered — ready for a hard session" else {
        val ready = java.time.LocalDateTime.now().plusHours(r.hoursLeft.toLong())
        val day = when (java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), ready.toLocalDate())) { 0L -> "today"; 1L -> "tomorrow"; else -> ready.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.US) }
        "${r.hoursLeft} h to recover · ready for hard work $day ~${ready.format(java.time.format.DateTimeFormatter.ofPattern("h a", java.util.Locale.US))}"
    }

    // ── 🏁 Race pacing plan ──
    data class Split(val label: String, val meters: Double, val sec: Double, val paceSec: Int, val cumSec: Double)

    /** Riegel: time for [toMi] from a known [baseSec] over [baseMi]. */
    fun riegel(baseSec: Double, baseMi: Double, toMi: Double) = baseSec * (toMi / baseMi).pow(1.06)

    /**
     * Mile splits for [goalSec] over [miles]. "negative": first mile +10 s, first half ~1% slower, second half ~1% faster;
     * "even": the same every mile. [heatPct] slows the whole plan (from Heat.pct). Splits always add up to the (heat-adjusted) goal.
     */
    fun pacingPlan(goalSec: Double, miles: Double, strategy: String = "negative", heatPct: Double = 0.0): List<Split> {
        val total = goalSec * (1 + heatPct / 100)
        val full = miles.toInt(); val part = miles - full
        val segs = (1..full).map { it.toString() to 1.0 } + (if (part > 0.01) listOf(String.format(java.util.Locale.US, "%.1f", part) to part) else emptyList())
        val base = total / miles
        var at = 0.0
        val w = segs.map { (_, len) -> val mid = at + len / 2; at += len
            (if (strategy == "even") 1.0 else if (mid < miles / 2) 1.01 else 0.99) * len + (if (strategy != "even" && mid < 1) 10.0 / base * len else 0.0) }
        val k = total / (w.sum() * base)
        var cum = 0.0
        return segs.mapIndexed { i, (label, len) -> val s = w[i] * base * k; cum += s
            Split(if (len < 1) "Last $label mi" else "Mile $label", len * RunMath.METERS_PER_MILE, s, (s / len).roundToInt(), cum) }
    }

    /** The plan as a watch workout: one step per mile with a pace target (±6 s/mi). */
    fun pacingWorkout(name: String, splits: List<Split>): JSONObject = Workouts.workout(name,
        "Race pacing plan — ${splits.size} splits, finish ${RunMath.clock(splits.lastOrNull()?.cumSec?.roundToInt() ?: 0)}.",
        *splits.map { s -> Workouts.step("run", "dist", s.meters.roundToInt(), "pace", 0, s.paceSec - 6, s.paceSec + 6) }.toTypedArray())
}
