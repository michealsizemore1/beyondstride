package io.github.michealsizemore1.beyondstride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NutritionTest {
    private val sample = """{"2026-11-05":{"cal":2400,"prot":110.5,"carbs":320,"fat":70,"target":2600,"count":9,"meals":{"breakfast":500},"items":[{"meal":"breakfast","name":"Oats","cal":300,"prot":10,"carbs":54,"fat":5}]},
        "2026-11-06":{"cal":800,"prot":90,"carbs":54,"fat":21,"count":2}}"""

    @Test fun parsesDays() {
        val d = Nutrition.parse(sample)
        assertEquals(2, d.size); assertEquals("2026-11-05", d[0].date); assertEquals(2600, d[0].target)
        assertEquals(500, d[0].meals["breakfast"]); assertEquals("Oats", d[0].items[0].name)
        assertEquals(d, Nutrition.parse(Nutrition.json(d)))
    }

    @Test fun fuelScore() {
        val d = Nutrition.parse(sample)
        assertEquals(95, Nutrition.fuelScore(d[0])!!.first)          // 2400 of 2600 → on target
        assertNull(Nutrition.fuelScore(d[1]))                          // no target saved
        assertEquals(d[0], Nutrition.yesterday(d, "2026-11-06"))
    }

    @Test fun emptyIsSafe() { assertEquals(0, Nutrition.parse("null").size) }
}

class RhrTrendTest {
    private fun nights(vararg hr: Int) = hr.mapIndexed { i, h -> Readiness.Night(java.time.LocalDate.parse("2026-11-06").minusDays((hr.size - 1 - i).toLong()).toString(), 400, h) }
    @Test fun downIsGood() {
        val t = Readiness.rhrTrend(nights(46, 46, 45, 46, 45, 46, 45, 43, 43, 42, 43, 42, 43, 42), "2026-11-06")!!
        org.junit.Assert.assertTrue(t.change!! < -1); org.junit.Assert.assertEquals(Readiness.GREEN, t.color)
    }
    @Test fun spikeWarns() {
        val t = Readiness.rhrTrend(nights(42, 43, 42, 43, 42, 43, 50), "2026-11-06")!!
        org.junit.Assert.assertEquals(Readiness.RED, t.color)
    }
}
