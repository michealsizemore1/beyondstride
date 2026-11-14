package io.github.michealsizemore1.beyondstride

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CoachingTest {
    private val today = "2026-11-14"
    private fun checks(recent: Double, base: Double) = (0..40).map { i ->
        Readiness.Check(LocalDate.parse(today).minusDays(i.toLong()).toString(), if (i < 7) recent else base + (i % 3 - 1) * 2.0, 50, at = i.toLong()) }

    @Test fun hrvBalancedAndLow() {
        val ok = Coaching.hrvStatus(checks(60.0, 60.0), today); assertEquals("Balanced", ok!!.status)
        val low = Coaching.hrvStatus(checks(45.0, 60.0), today); assertEquals("Low", low!!.status)
        val unb = Coaching.hrvStatus(checks(56.5, 60.0), today); assertEquals("Unbalanced", unb!!.status)
        assertNull(Coaching.hrvStatus(checks(60.0, 60.0).take(2), today))
        assertEquals("Building baseline", Coaching.hrvStatus(checks(60.0, 60.0).take(10), today)!!.status)
    }

    @Test fun trainingStatusCases() {
        val bal = Coaching.hrvStatus(checks(60.0, 60.0), today)
        val low = Coaching.hrvStatus(checks(45.0, 60.0), today)
        assertEquals("Productive", Coaching.trainingStatus(1.1, bal, 600, 610, null).name)
        assertEquals("Maintaining", Coaching.trainingStatus(1.1, bal, 600, 601, null).name)
        assertEquals("Overreaching", Coaching.trainingStatus(1.6, bal, 600, 610, null).name)
        assertEquals("Strained", Coaching.trainingStatus(1.6, low, 600, 610, null).name)
        assertEquals("Peaking", Coaching.trainingStatus(0.7, bal, 600, 610, 10).name)
        assertEquals("Detraining", Coaching.trainingStatus(0.5, bal, null, null, 160).name)
        assertEquals("Unproductive", Coaching.trainingStatus(1.0, bal, 620, 610, 160).name)
        assertEquals("Building baseline", Coaching.trainingStatus(null, bal, null, null, null).name)
    }

    @Test fun recoveryHours() {
        val now = 1_000_000_000_000L
        val easy = JSONObject().put("start", now - 2 * 3_600_000L).put("movingSeconds", 3600).put("stress", 60)
        val r = Coaching.recovery(listOf(easy), 70, now); assertNotNull(r)
        println("easy hour 2 h ago: ${r!!.hoursLeft} h of ${r.totalHours}"); assertTrue(r.hoursLeft in 20..23)
        val tired = Coaching.recovery(listOf(easy), 50, now)!!; assertTrue(tired.hoursLeft > r.hoursLeft)
        val old = JSONObject().put("start", now - 3 * 86_400_000L).put("movingSeconds", 3600).put("stress", 60)
        assertNull(Coaching.recovery(listOf(old), 70, now))
    }

    @Test fun pacingPlanAddsUp() {
        val goal = 4 * 3600.0
        val p = Coaching.pacingPlan(goal, 26.2188)
        assertEquals(27, p.size); assertEquals(goal, p.last().cumSec, 0.5)
        assertTrue(p[0].paceSec > p[20].paceSec)
        println(p.joinToString { "${it.label} ${RunMath.clock(it.paceSec)}" })
        val hot = Coaching.pacingPlan(goal, 26.2188, "even", 3.0)
        assertEquals(goal * 1.03, hot.last().cumSec, 0.5); assertEquals(hot[0].paceSec, hot[20].paceSec)
        val w = Coaching.pacingWorkout("Marathon", p); assertEquals(27, w.getJSONArray("steps").length())
    }
}
