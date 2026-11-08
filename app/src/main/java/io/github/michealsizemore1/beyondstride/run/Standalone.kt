package io.github.michealsizemore1.beyondstride.run

import android.content.Context

/** Once switched on, BeyondStride owns all its data and no longer talks to the Command Center (which stays as it was). */
object Standalone {
    private fun p(ctx: Context) = ctx.getSharedPreferences("standalone", 0)
    fun on(ctx: Context) = p(ctx).getBoolean("on", false)
    fun since(ctx: Context) = p(ctx).getLong("at", 0L)
    fun set(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("on", v).putLong("at", if (v) System.currentTimeMillis() else 0L).apply()
}
