package io.github.michealsizemore1.beyondstride

import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 🎯 Execution Score (0–100), like Garmin's: how well a run followed its workout — time spent inside each step's
 * heart-rate / power / pace target, and whether every step was finished.
 *
 *  - Each second inside the target counts fully; a little outside (up to 8% off) gets partial credit.
 *  - The first moments of a step are skipped so effort can settle (45 s for heart rate, 15 s for power/pace).
 *  - Warm-ups and cool-downs count half as much as the main work.
 *  - Steps that were skipped count as 0; a step cut short loses some credit.
 *
 * The workout is the one run on the watch, or (for a free run) the plan's workout for that day.
 */
object ExecScore {
    data class Step(val name: String, val target: String, val lo: Int, val hi: Int, val sec: Int, val avg: Int, val pct: Int?, val done: Boolean)
    data class Result(val score: Int, val label: String, val color: Int, val basis: String, val steps: List<Step>) {
        fun toJson(): JSONObject = JSONObject().put("score", score).put("label", label).put("color", color).put("basis", basis)
            .put("steps", org.json.JSONArray().apply { steps.forEach { s -> put(JSONObject().put("name", s.name).put("target", s.target).put("lo", s.lo).put("hi", s.hi)
                .put("sec", s.sec).put("avg", s.avg).put("pct", s.pct ?: -1).put("done", s.done)) } })
        companion object {
            fun fromJson(o: JSONObject) = Result(o.optInt("score"), o.optString("label"), o.optInt("color"), o.optString("basis"),
                (0 until (o.optJSONArray("steps")?.length() ?: 0)).map { i -> o.getJSONArray("steps").getJSONObject(i).let { s ->
                    Step(s.optString("name"), s.optString("target"), s.optInt("lo"), s.optInt("hi"), s.optInt("sec"), s.optInt("avg"), s.optInt("pct", -1).takeIf { it >= 0 }, s.optBoolean("done")) } })
        }
    }

    fun label(score: Int) = when { score >= 90 -> "Excellent"; score >= 75 -> "Good"; score >= 60 -> "Fair"; else -> "Off target" }
    fun color(score: Int) = when { score >= 90 -> 0xFF16A34A.toInt(); score >= 75 -> 0xFF65A30D.toInt(); score >= 60 -> 0xFFF97316.toInt(); else -> 0xFFDC2626.toInt() }

    /** Target text for a step: "HR 128–145", "250–280 W", "8:30–9:00/mi". */
    fun targetText(s: Step) = when (s.target) {
        "hr" -> "HR ${s.lo}–${s.hi}"; "power" -> "${s.lo}–${s.hi} W"
        "pace" -> "${RunMath.clock(min(s.lo, s.hi))}–${RunMath.clock(max(s.lo, s.hi))}/mi"; else -> "no target"
    }
    fun avgText(s: Step) = when (s.target) { "hr" -> "avg ${s.avg}"; "power" -> "avg ${s.avg} W"; "pace" -> "avg ${RunMath.clock(s.avg)}/mi"; else -> "" }

    /**
     * [run] is a watch run (with per-second samples). [planWorkout] is used when the run had no workout of its own.
     * Null when there's nothing to score (no samples, or no step with a target).
     */
    fun compute(run: JSONObject, planWorkout: JSONObject?, hrMaxDef: Double, hrRestDef: Double, cpDef: Int): Result? {
        val sa = run.optJSONArray("samples") ?: return null
        if (sa.length() < 120) return null
        val own = run.optJSONObject("workout")?.takeIf { (it.optJSONArray("steps")?.length() ?: 0) > 0 }
        val w = own ?: planWorkout ?: return null
        val steps = Workouts.flatten(w)
        if (steps.isEmpty() || steps.none { it.optString("target") in setOf("hr", "power", "pace") }) return null
        val hrMax = run.optDouble("hrMax", hrMaxDef).takeIf { it > 0 } ?: hrMaxDef
        val hrRest = run.optDouble("hrRest", hrRestDef).takeIf { it > 0 } ?: hrRestDef
        val cp = run.optInt("cp", cpDef).takeIf { it > 0 } ?: cpDef

        // Samples: [moving s, meters*10, hr, speed*100, cadence, power, elapsed s]
        val n = sa.length()
        val t = IntArray(n); val m = DoubleArray(n); val hr = IntArray(n); val spd = DoubleArray(n); val pw = IntArray(n)
        for (i in 0 until n) { val s = sa.optJSONArray(i) ?: continue
            t[i] = s.optInt(0); m[i] = s.optInt(1) / 10.0; hr[i] = s.optInt(2); spd[i] = s.optInt(3) / 100.0; pw[i] = s.optInt(5) }
        fun avgOf(a: IntArray, i: Int, win: Int): Double { var sum = 0.0; var c = 0; for (k in max(0, i - win + 1)..i) if (a[k] > 0) { sum += a[k]; c++ }; return if (c == 0) 0.0 else sum / c }
        fun spdAvg(i: Int): Double { var sum = 0.0; var c = 0; for (k in max(0, i - 9)..i) { sum += spd[k]; c++ }; return if (c == 0) 0.0 else sum / c }

        // Where each step starts and ends (sample indexes).
        val bounds = ArrayList<IntRange>()
        val laps = run.optJSONArray("laps")
        val done = if (own != null) (if (own.optBoolean("completed")) steps.size else own.optInt("stepsDone", steps.size)).coerceIn(0, steps.size) else steps.size
        if (own != null && laps != null && laps.length() >= 1) {
            // During a workout every step is a lap.
            var sec = 0; var idx = 0
            for (li in 0 until min(laps.length(), steps.size)) {
                sec += laps.optJSONObject(li)?.optInt("sec") ?: 0
                val start = idx
                while (idx < n && t[idx] < sec) idx++
                bounds += start until idx
            }
            if (bounds.size < done && idx < n) bounds += idx until n   // the step running when the run was stopped
        } else {
            // Plan workout on a free run: lay the steps over the run by their time / distance; an open step takes the rest.
            var idx = 0
            for ((si, s) in steps.withIndex()) {
                if (idx >= n) break
                val start = idx
                when (s.optString("dur")) {
                    "time" -> { val end = t[start] + s.optInt("value"); while (idx < n && t[idx] < end) idx++ }
                    "dist" -> { val end = m[start] + s.optInt("value"); while (idx < n && m[idx] < end) idx++ }
                    else -> idx = if (si == steps.lastIndex || steps.drop(si + 1).all { it.optString("dur") == "open" }) n else min(n, idx + 1)
                }
                bounds += start until idx
            }
        }

        var credit = 0.0; var weight = 0.0
        val out = ArrayList<Step>()
        val avgSpeed = run.optDouble("distance", 0.0) / max(1, run.optInt("movingSeconds", n))
        steps.forEachIndexed { si, s ->
            val kind = s.optString("kind")
            val wf = if (kind == "warmup" || kind == "cooldown") 0.5 else if (kind == "rest") 0.0 else 1.0
            val target = s.optString("target").takeIf { it in setOf("hr", "power", "pace") } ?: "none"
            val (lo0, hi0) = Workouts.resolve(s, hrMax, hrRest, cp)
            val lo = min(lo0, hi0); val hi = max(lo0, hi0)
            val name = Workouts.kindName(kind) + (if (s.optInt("reps") > 1) " ${s.optInt("rep")}/${s.optInt("reps")}" else "")
            val planned = when (s.optString("dur")) { "time" -> s.optInt("value").toDouble(); "dist" -> if (avgSpeed > 0.5) s.optInt("value") / avgSpeed else 0.0; else -> 0.0 }
            val r = bounds.getOrNull(si)
            if (r == null || r.isEmpty() || si >= done) {
                // Skipped / not reached.
                out += Step(name, target, lo, hi, 0, 0, if (target != "none") 0 else null, false)
                if (target != "none" && planned > 0) weight += planned * wf
                return@forEachIndexed
            }
            val sec = t[r.last] - t[r.first] + 1
            if (target == "none" || lo <= 0 || hi <= 0) { out += Step(name, "none", 0, 0, sec, 0, null, true); return@forEachIndexed }
            val grace = (if (target == "hr") 45 else 15).let { g -> if (sec < g * 2) g / 3 else g }
            var c = 0.0; var cnt = 0; var vs = 0.0; var vc = 0
            for (i in r) {
                if (t[i] - t[r.first] < grace) continue
                val v = when (target) {
                    "hr" -> hr[i].toDouble().takeIf { it > 0 }
                    "power" -> avgOf(pw, i, 5).takeIf { it > 0 }
                    else -> spdAvg(i).takeIf { it > 0.5 }?.let { RunMath.METERS_PER_MILE / it }
                } ?: continue
                vs += v; vc++; cnt++
                val off = if (v < lo) (lo - v) / lo else if (v > hi) (v - hi) / hi else 0.0
                c += max(0.0, 1 - off / 0.08).let { if (off == 0.0) 1.0 else it * 0.8 }
            }
            if (cnt == 0) { out += Step(name, target, lo, hi, sec, 0, null, true); return@forEachIndexed }
            var pct = c / cnt
            // Cut short: lose up to half the step's credit.
            if (planned > 0 && sec < planned * 0.9) pct *= 0.5 + 0.5 * (sec / planned).coerceIn(0.0, 1.0)
            val wsec = max(sec.toDouble(), planned)
            credit += pct * wsec * wf; weight += wsec * wf
            out += Step(name, target, lo, hi, sec, (vs / vc).roundToInt(), (pct * 100).roundToInt(), true)
        }
        if (weight <= 0) return null
        val score = (credit / weight * 100).roundToInt().coerceIn(0, 100)
        val basis = if (own != null) "Workout: ${own.optString("name").ifEmpty { "guided workout" }}" else "Today's plan: ${w.optString("name").ifEmpty { "planned run" }}"
        return Result(score, label(score), color(score), basis, out)
    }
}
