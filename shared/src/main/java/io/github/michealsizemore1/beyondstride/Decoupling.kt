package io.github.michealsizemore1.beyondstride

import org.json.JSONObject
import java.util.Locale
import kotlin.math.sqrt

/**
 * Aerobic decoupling: how much heart rate drifts up against pace (Pa:HR) and against Stryd power
 * (Pw:HR) from the first half of a run to the second. Goal: easy runs 5% or under; quality runs aren't graded (reference only).
 * The warm-up is left out; only moving seconds with a heart rate count.
 */
object Decoupling {
    data class Result(
        val paHr: Double?,          // % (positive = heart rate drifted up)
        val pwHr: Double?,
        val minutes: Int,           // minutes analyzed (after warm-up)
        val steady: Boolean,        // false = intervals / very uneven effort, so less meaningful
        val hr1: Int, val hr2: Int, // average heart rate, first and second half
    ) {
        /** The headline number: power when there is power (Stryd), else pace. */
        val main: Double? get() = pwHr ?: paHr
        fun toJson() = JSONObject().apply {
            paHr?.let { put("paHr", Math.round(it * 10) / 10.0) }
            pwHr?.let { put("pwHr", Math.round(it * 10) / 10.0) }
            put("minutes", minutes); put("steady", steady); put("hr1", hr1); put("hr2", hr2)
        }
    }

    // 🎯 Goal (Nov 26 2026, your call): easy / long runs 5% or under. Quality runs (tempo, threshold, intervals, races…)
    // aren't graded: heart rate is meant to climb at those efforts, so decoupling there is shown for reference only.
    private val QUALITY = Regex("\\b(tempo|threshold|lt|cruise|interval|intervals|repeats?|reps|fartlek|progression|race|racing|time trial|tt|hills?|hill repeats|vo2|speed|track|marathon pace|mp|half marathon pace|hmp|10k pace|5k pace|steady state|quality)\\b", RegexOption.IGNORE_CASE)
    private val EASY = Regex("\\b(easy|recovery|long run|long|base|aerobic|zone 2|z2|shakeout|jog)\\b", RegexOption.IGNORE_CASE)
    /** A quality run: the planned workout's (or run's) name says so, else an intensity of 0.85+ (tempo effort or harder). */
    fun isQuality(run: JSONObject): Boolean {
        val name = listOfNotNull(run.optJSONObject("workout")?.optString("name"), run.optString("name"), run.optString("title")).joinToString(" ")
        if (QUALITY.containsMatchIn(name)) return true
        if (EASY.containsMatchIn(name)) return false
        val inten = run.optDouble("intensity"); return !inten.isNaN() && inten >= 0.85
    }
    const val REF_NOTE = "for reference only — not meaningful for quality workouts"
    fun goal(quality: Boolean) = if (quality) "not graded on quality runs" else "goal 5% or under"
    fun goalShort(quality: Boolean) = if (quality) "—" else "≤5%"
    fun onTarget(pct: Double, quality: Boolean = false) = !quality && pct <= 5
    fun rating(pct: Double, quality: Boolean = false) = when {
        quality -> "Reference only"
        pct < 0 -> "No drift"
        pct <= 5 -> "On target"
        pct <= 10 -> "Some drift"
        else -> "High drift"
    }
    fun color(pct: Double, quality: Boolean = false) = if (quality) 0xFF94A3B8.toInt()
        else when { pct <= 5 -> 0xFF22C55E.toInt(); pct <= 10 -> 0xFFEAB308.toInt(); else -> 0xFFEF4444.toInt() }
    fun text(pct: Double) = String.format(Locale.US, "%.1f%%", pct)

    fun fromJson(o: JSONObject?): Result? = o?.let {
        Result(if (it.has("paHr")) it.optDouble("paHr") else null, if (it.has("pwHr")) it.optDouble("pwHr") else null,
            it.optInt("minutes"), it.optBoolean("steady", true), it.optInt("hr1"), it.optInt("hr2"))
    }

    /** The run's decoupling — saved value, or worked out from its samples. Null if too short or no heart rate. */
    fun of(run: JSONObject): Result? = fromJson(run.optJSONObject("decoupling")) ?: compute(run)

    /** Samples: [moving s, meters*10, hr, speed*100, cadence, power, elapsed s], one per moving second. */
    fun compute(run: JSONObject): Result? {
        val a = run.optJSONArray("samples") ?: return null
        data class S(val t: Int, val m: Double, val hr: Int, val pw: Int)
        val all = (0 until a.length()).mapNotNull { i ->
            a.optJSONArray(i)?.let { s -> S(s.optInt(0), s.optInt(1) / 10.0, s.optInt(2), s.optInt(5)) }
        }
        if (all.size < 60) return null
        val total = all.last().t
        val warm = if (total >= 40 * 60) 10 * 60 else 5 * 60
        val use = all.filter { it.t >= warm }
        val span = (use.lastOrNull()?.t ?: 0) - (use.firstOrNull()?.t ?: 0)
        if (span < 20 * 60) return null
        if (use.count { it.hr > 0 } < use.size * 0.8) return null
        val mid = use.first().t + span / 2
        val h1 = use.filter { it.t < mid }; val h2 = use.filter { it.t >= mid }
        fun avgHr(l: List<S>) = l.filter { it.hr > 0 }.map { it.hr }.average()
        fun speed(l: List<S>) = (l.last().m - l.first().m) / (l.last().t - l.first().t).coerceAtLeast(1)
        fun avgPw(l: List<S>) = l.map { it.pw }.average()
        val hr1 = avgHr(h1); val hr2 = avgHr(h2)
        if (hr1 <= 0 || hr2 <= 0) return null
        val pa = speed(h1).takeIf { it > 0.5 }?.let { s1 -> val s2 = speed(h2); val e1 = s1 / hr1; val e2 = s2 / hr2; (e1 - e2) / e1 * 100 }
        val hasPower = use.count { it.pw > 0 } > use.size * 0.8
        val pw = if (hasPower) { val e1 = avgPw(h1) / hr1; val e2 = avgPw(h2) / hr2; if (e1 > 0) (e1 - e2) / e1 * 100 else null } else null
        // Steady = 1-minute average power (or speed) doesn't swing much — intervals make decoupling meaningless.
        val minutes = use.groupBy { it.t / 60 }.values.filter { it.size >= 30 }
        val vals = minutes.map { m -> if (hasPower) m.map { it.pw }.average() else speed(m) }.filter { it > 0 }
        val steady = if (vals.size < 5) true else {
            val mean = vals.average(); val sd = sqrt(vals.sumOf { (it - mean) * (it - mean) } / vals.size)
            sd / mean < 0.15
        }
        if (pa == null && pw == null) return null
        return Result(pa, pw, span / 60, steady, hr1.toInt(), hr2.toInt())
    }

    /** One line for notes, e.g. "Aerobic decoupling 3.2% (Excellent) · Pw:HR 3.2% · Pa:HR 4.0% · HR 142→147". */
    fun line(r: Result, quality: Boolean = false): String {
        val m = r.main ?: return ""
        val parts = mutableListOf("Aerobic decoupling ${text(m)} (${rating(m, quality)}, ${goal(quality)}${if (!r.steady) ", uneven effort" else ""})")
        r.pwHr?.let { parts += "Pw:HR ${text(it)}" }
        r.paHr?.let { parts += "Pa:HR ${text(it)}" }
        parts += "HR ${r.hr1}→${r.hr2} over ${r.minutes} min"
        return parts.joinToString(" · ")
    }
}
