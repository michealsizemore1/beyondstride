package io.github.michealsizemore1.beyondstride

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Execution score on made-up runs (max HR 185, rest 43.5 → Z1 114–127, Z2 128–141, Z3 142–156). */
class ExecScoreTest {
    private fun run(hrAt: (Int) -> Int, secs: Int, workout: JSONObject?, laps: List<Int>, speed: Double = 3.0): JSONObject {
        val sa = JSONArray()
        for (t in 1..secs) sa.put(JSONArray(listOf(t, (t * speed * 10).toInt(), hrAt(t), (speed * 100).toInt(), 170, 0, t)))
        return JSONObject().put("start", 1L).put("movingSeconds", secs).put("distance", secs * speed).put("samples", sa)
            .put("hrMax", 185.0).put("hrRest", 43.5)
            .put("laps", JSONArray().apply { laps.forEach { put(JSONObject().put("sec", it)) } })
            .apply { workout?.let { w -> put("workout", JSONObject().put("name", w.optString("name")).put("steps", w.getJSONArray("steps")).put("completed", true)) } }
    }
    private val w = Workouts.workout("Tempo", "",
        Workouts.step("warmup", "time", 300, "hr", 1), Workouts.step("run", "time", 1200, "hr", 3), Workouts.step("cooldown", "time", 300))

    @Test fun inZoneScoresHigh() {
        val r = ExecScore.compute(run({ t -> if (t <= 300) 120 else if (t <= 1500) 150 else 125 }, 1800, w, listOf(300, 1200, 300)), null, 185.0, 43.5, 300)
        assertNotNull(r); println("in zone: ${r!!.score} ${r.steps}"); assertTrue(r.score >= 95)
    }
    @Test fun tooHardScoresLow() {
        val r = ExecScore.compute(run({ t -> if (t <= 300) 120 else if (t <= 1500) 170 else 125 }, 1800, w, listOf(300, 1200, 300)), null, 185.0, 43.5, 300)
        assertNotNull(r); println("too hard: ${r!!.score}"); assertTrue(r.score < 40)
    }
    @Test fun slightlyOffGetsPartial() {
        val r = ExecScore.compute(run({ t -> if (t <= 300) 120 else if (t <= 1500) 160 else 125 }, 1800, w, listOf(300, 1200, 300)), null, 185.0, 43.5, 300)
        assertNotNull(r); println("slightly off: ${r!!.score}"); assertTrue(r.score in 30..85)
    }
    @Test fun planFallbackOnFreeRun() {
        val plan = Workouts.fromPlanText("4 mi easy")
        println("plan: $plan")
        val r = ExecScore.compute(run({ 135 }, 2200, null, listOf(536, 536, 536, 536)), plan, 185.0, 43.5, 300)
        println("plan fallback: ${r?.score} ${r?.steps}")
        if (plan != null && Workouts.flatten(plan).any { it.optString("target") == "hr" }) { assertNotNull(r); assertTrue(r!!.score >= 90) }
    }
    @Test fun noTargetsNoScore() {
        val free = Workouts.workout("Free", "", Workouts.step("run", "open", 0))
        assertNull(ExecScore.compute(run({ 140 }, 600, free, listOf(600)), null, 185.0, 43.5, 300))
    }
}
