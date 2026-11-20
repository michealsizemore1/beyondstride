package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * A year and more of daily nutrition for the Monthly / Yearly tables on the Nutrition tab. The phone keeps only the last
 * three weeks itself, so this reads back the day summaries BeyondStride already sends to its cloud copy (one per day:
 * calories, macros, fluids, quality, micronutrients) and keeps them in a file. Nothing new is sent anywhere.
 */
object NutritionHistory {
    private fun file(ctx: Context) = File(ctx.filesDir, "nuhist.json")
    private fun p(ctx: Context) = ctx.getSharedPreferences("nuhist", 0)
    @Volatile private var cache: JSONObject? = null

    /** date → the day's nutrition (same fields as the summaries: cal, target, prot, fat, carbs, water, fluidTarget, quality, macros, micros…). */
    fun days(ctx: Context): JSONObject = cache ?: try { JSONObject(file(ctx).readText()) } catch (e: Exception) { JSONObject() }.also { cache = it }

    /** Reads new days from the cloud copy (at most every 3 hours unless [force]). True when something changed. Call off the main thread. */
    fun refresh(ctx: Context, force: Boolean = false): Boolean {
        if (!NutritionSync.ready(ctx)) return false
        val now = System.currentTimeMillis(); val pr = p(ctx)
        if (!force && now - pr.getLong("at", 0L) < 3 * 3600_000L) return false
        val have = days(ctx)
        // The first time: from January 1 of last year. After that: the last 3 weeks (days still being filled in).
        val from = if (have.length() == 0) LocalDate.now().minusYears(1).withDayOfYear(1) else LocalDate.now().minusDays(21)
        return try {
            val url = Live.db(ctx) + "/nutrition/" + NutritionSync.key(ctx) + "/beyondstride/daily.json?orderBy=%22%24key%22&startAt=%22$from%22"
            val c = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 40_000 }
            if (c.responseCode !in 200..299) return false
            val txt = c.inputStream.bufferedReader().use { it.readText() }
            val o = if (txt.trim() == "null") JSONObject() else JSONObject(txt)
            var changed = false
            o.keys().forEach { k -> o.optJSONObject(k)?.optJSONObject("nutrition")?.let { n -> if (have.optJSONObject(k)?.toString() != n.toString()) { have.put(k, n); changed = true } } }
            if (changed) { file(ctx).writeText(have.toString()); cache = have }
            pr.edit().putLong("at", now).apply()
            changed
        } catch (e: Exception) { false }
    }
}
