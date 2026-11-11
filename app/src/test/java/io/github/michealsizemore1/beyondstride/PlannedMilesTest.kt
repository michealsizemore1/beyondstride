package io.github.michealsizemore1.beyondstride

import io.github.michealsizemore1.beyondstride.run.Insights
import org.junit.Assert.assertEquals
import org.junit.Test

/** Planned miles for a plan day match the Workouts list (warm-up + repeats + cool-down). */
class PlannedMilesTest {
    @Test fun intervalsCountEveryRep() {
        val v = Insights.plannedMiles("Wed, Nov 11: Tempo 2 × 2 miles (8:35–8:50/mi) with 2-min recovery")
        println("tempo 2x2: $v"); assertEquals(6.0, v, 0.01)
    }
    @Test fun easyRun() { val v = Insights.plannedMiles("Thu, Nov 12: Easy 4 miles"); println("easy 4: $v"); assertEquals(4.0, v, 0.01) }
    @Test fun restDay() { assertEquals(0.0, Insights.plannedMiles("Fri, Nov 13: Rest day"), 0.01) }
}
