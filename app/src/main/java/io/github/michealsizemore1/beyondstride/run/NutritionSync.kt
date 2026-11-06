package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import io.github.michealsizemore1.beyondstride.Nutrition
import io.github.michealsizemore1.beyondstride.NutritionStore
import java.net.HttpURLConnection
import java.net.URL

/**
 * Reads the daily nutrition totals the Command Center shares (Firebase: <db>/nutrition/<key>/days).
 * The db is the LiveTrack database; the key is a private random code made here and handed to the
 * Command Center in the Nutrition tab's link.
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

    /** Last error (shown on the Nutrition card), empty when the last read worked. */
    fun lastError(ctx: Context) = ctx.getSharedPreferences("nutrition", 0).getString("err", "") ?: ""

    /** Reads the last 21 days. Returns true if anything changed. Call off the main thread. */
    fun fetch(ctx: Context): Boolean {
        if (!ready(ctx)) return false
        val sp = ctx.getSharedPreferences("nutrition", 0)
        return try {
            val url = Live.db(ctx) + "/nutrition/" + key(ctx) + "/days.json?orderBy=%22%24key%22&limitToLast=21"
            val c = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 12_000; readTimeout = 15_000 }
            val code = c.responseCode
            if (code != 200) {
                sp.edit().putString("err", if (code == 401 || code == 403) "rules" else "HTTP $code").apply()
                return false
            }
            val body = c.inputStream.bufferedReader().readText()
            val days = Nutrition.parse(body)
            val before = Nutrition.json(NutritionStore.days(ctx))
            sp.edit().putString("err", "").apply()
            NutritionStore.save(ctx, days)
            Nutrition.json(days.takeLast(21)) != before
        } catch (e: Exception) { sp.edit().putString("err", "offline").apply(); false }
    }

    /** Fetch if the last read is older than [maxAgeMs]. */
    fun refreshIfStale(ctx: Context, maxAgeMs: Long = 2 * 60_000L) =
        if (System.currentTimeMillis() - NutritionStore.fetchedAt(ctx) > maxAgeMs) fetch(ctx) else false
}
