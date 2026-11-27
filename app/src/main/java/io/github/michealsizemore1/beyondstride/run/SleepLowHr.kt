package io.github.michealsizemore1.beyondstride.run

import android.content.Context

/** 💤 Each night's lowest heart rate (one reading, from Health Connect / Samsung Health), keyed by the day you woke up. */
object SleepLowHr {
    private fun p(ctx: Context) = ctx.getSharedPreferences("sleep_low_hr", 0)
    fun save(ctx: Context, day: String, bpm: Int) {
        val e = p(ctx).edit().putInt(day, bpm)
        // Keep about 4 months.
        val cutoff = java.time.LocalDate.now().minusDays(120).toString()
        p(ctx).all.keys.filter { it < cutoff }.forEach { e.remove(it) }
        e.apply()
    }
    fun get(ctx: Context, day: String): Int = p(ctx).getInt(day, 0)
}
