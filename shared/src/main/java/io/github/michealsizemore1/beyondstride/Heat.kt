package io.github.michealsizemore1.beyondstride

/**
 * Heat & humidity impact on pace — the same rule on the activity cards and the watch:
 * temperature + dew point (both °F), the runners' chart — 100 or less: none · 110: 0.5% · 120: 1% · 130: 2% · 140: 3% ·
 * 150: 4.5% · 160: 6% · 170: 8% · 180: 10% (in between: proportional). Dew point counts humidity at every temperature.
 */
object Heat {
    fun heatIndex(t: Double, r: Double): Double {
        if (t < 80) return t
        var hi = -42.379 + 2.04901523 * t + 10.14333127 * r - 0.22475541 * t * r - 0.00683783 * t * t - 0.05481717 * r * r + 0.00122874 * t * t * r + 0.00085282 * t * r * r - 0.00000199 * t * t * r * r
        if (r < 13 && t <= 112) hi -= ((13 - r) / 4) * Math.sqrt((17 - Math.abs(t - 95)) / 17)
        if (r > 85 && t <= 87) hi += ((r - 85) / 10) * ((87 - t) / 5)
        return hi
    }
    /** Dew point (°F) from temperature (°F) and relative humidity (%) — Magnus formula. */
    fun dewPoint(tempF: Double, humidity: Double): Double {
        val t = (tempF - 32) * 5 / 9; val rh = humidity.coerceIn(1.0, 100.0)
        val g = Math.log(rh / 100) + 17.62 * t / (243.12 + t)
        return (243.12 * g / (17.62 - g)) * 9 / 5 + 32
    }
    private val CHART = listOf(100.0 to 0.0, 110.0 to 0.5, 120.0 to 1.0, 130.0 to 2.0, 140.0 to 3.0, 150.0 to 4.5, 160.0 to 6.0, 170.0 to 8.0, 180.0 to 10.0)
    /** Temperature + dew point. */
    fun sum(tempF: Double, humidity: Double) = tempF + dewPoint(tempF, humidity)
    /** Percent slower because of heat & humidity (0 when it's cool or dry). */
    fun pct(tempF: Double, humidity: Double): Double {
        val s = sum(tempF, humidity)
        if (s <= 100) return 0.0
        for (i in 1 until CHART.size) { val (x1, y1) = CHART[i]; val (x0, y0) = CHART[i - 1]; if (s <= x1) return y0 + (y1 - y0) * (s - x0) / (x1 - x0) }
        return Math.min(15.0, 10.0 + (s - 180) * 0.25)   // past the chart: hard running isn't advised
    }
    /** The speed you'd hold at the same effort in cool weather (m/s). */
    fun adjustedSpeed(speed: Double, pct: Double) = speed * (1 + pct / 100)
}
