package io.github.michealsizemore1.beyondstride

import io.github.michealsizemore1.beyondstride.run.PlanEngine
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanEngineTest {
    private val text = "\nWEEK 1 — BASE\nSun: Easy 4 miles\nTue: Easy 4 miles\nWed: 6 × 1 mile at SPEED (7:45–8:05/mi) with 2-min recovery\nThu: Easy 4 miles\nSat: Easy/Long 8 miles\n\n" +
        "WEEK 2 — BASE\nSun: Recovery 3 miles\nTue: Easy 5 miles\nSat: Easy/Long 9 miles\n"

    @Test fun datesThenZones() {
        val plan = PlanEngine.fromText(text, "2026-11-08", false)!!
        println("start=" + plan.optString("startDate") + " mode=" + plan.optString("scheduleMode"))
        plan.put("startDate", "2026-11-01")
        println(PlanEngine.addDates(plan))
        println(PlanEngine.addZones(plan, 185.0, 43.5))
        println("----\n" + plan.optString("text").take(1500))
        assertTrue(plan.optString("text").contains("TRAINING ZONES"))
        assertTrue(plan.optString("text").contains("Sun, Nov 1:"))
    }
}
