package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import io.github.michealsizemore1.beyondstride.NutritionStore

/**
 * The private key BeyondStride uses in your Firebase database (the LiveTrack database) — for the cloud copy, the day
 * summaries the Command Center reads, and Activities shared to it — and the nutrition refresh.
 */
object NutritionSync {
    private fun p(ctx: Context) = ctx.getSharedPreferences("runsettings", 0)

    fun key(ctx: Context): String {
        p(ctx).getString("nutritionKey", null)?.takeIf { it.length >= 16 }?.let { return it }
        val chars = "abcdefghijkmnpqrstuvwxyz23456789"
        val r = java.security.SecureRandom()
        val k = (1..24).map { chars[r.nextInt(chars.length)] }.joinToString("")
        p(ctx).edit().putString("nutritionKey", k).apply()
        return k
    }

    fun ready(ctx: Context) = Live.db(ctx).startsWith("https://")

    /** "&bs=…" for the Command Center link (turns on sharing there). Empty if LiveTrack's database isn't set up. */
    fun linkPart(ctx: Context): String {
        if (!ready(ctx)) return ""
        val raw = Live.db(ctx) + "|" + key(ctx)
        return "&bs=" + android.util.Base64.encodeToString(raw.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
    }


    /** Brings the nutrition days up to date from BeyondStride's own data. Returns true if anything changed. Call off the main thread. */
    fun fetch(ctx: Context): Boolean = Local.refreshDays(ctx)

    /** Fetch if the last read is older than [maxAgeMs]. */
    fun refreshIfStale(ctx: Context, maxAgeMs: Long = 2 * 60_000L) =
        if (System.currentTimeMillis() - NutritionStore.fetchedAt(ctx) > maxAgeMs) fetch(ctx) else false
}
