package io.github.michealsizemore1.beyondstride

/**
 * Heat & humidity impact on pace — the same rule the activity cards use ("+6.0% (31 s/mi)"):
 * feels-like (NWS heat index) above 60°F costs about 0.4% per degree.
 */
object Heat {
    fun heatIndex(t: Double, r: Double): Double {
        if (t < 80) return t
        var hi = -42.379 + 2.04901523 * t + 10.14333127 * r - 0.22475541 * t * r - 0.00683783 * t * t - 0.05481717 * r * r + 0.00122874 * t * t * r + 0.00085282 * t * r * r - 0.00000199 * t * t * r * r
        if (r < 13 && t <= 112) hi -= ((13 - r) / 4) * Math.sqrt((17 - Math.abs(t - 95)) / 17)
        if (r > 85 && t <= 87) hi += ((r - 85) / 10) * ((87 - t) / 5)
        return hi
    }
    /** Percent slower because of heat (0 when it's cool). */
    fun pct(tempF: Double, humidity: Double): Double = Math.max(0.0, (heatIndex(tempF, humidity) - 60) * 0.4)
    /** The speed you'd hold at the same effort in cool weather (m/s). */
    fun adjustedSpeed(speed: Double, pct: Double) = speed * (1 + pct / 100)
}
