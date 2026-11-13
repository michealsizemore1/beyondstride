package io.github.michealsizemore1.beyondstride.run

import android.content.Context

/**
 * BeyondStride owns all its data (stand-alone since Nov 7, 2026 — the Command Center was left as it was). The old
 * Command Center sync is gone, so this is always on; [since] is when the switch was made.
 */
object Standalone {
    private fun p(ctx: Context) = ctx.getSharedPreferences("standalone", 0)
    @Suppress("UNUSED_PARAMETER") fun on(ctx: Context) = true
    fun since(ctx: Context) = p(ctx).getLong("at", 0L)
}
