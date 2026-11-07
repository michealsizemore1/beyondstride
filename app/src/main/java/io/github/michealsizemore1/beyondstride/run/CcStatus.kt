package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL

/**
 * "Command Center synced N min ago · N changes waiting": from the heartbeat its page writes once a minute while open
 * (<db>/nutrition/<key>/ccSeen) and the changes made here that it hasn't applied yet.
 */
object CcStatus {
    private fun p(ctx: Context) = ctx.getSharedPreferences("ccstatus", 0)
    fun seen(ctx: Context) = p(ctx).getLong("seen", 0L)

    /** Reads the heartbeat. Call off the main thread. */
    fun fetch(ctx: Context) {
        if (!NutritionSync.ready(ctx)) return
        try {
            val c = (URL(Live.db(ctx) + "/nutrition/" + NutritionSync.key(ctx) + "/ccSeen.json").openConnection() as HttpURLConnection).apply { connectTimeout = 8_000; readTimeout = 8_000 }
            if (c.responseCode == 200) c.inputStream.bufferedReader().readText().trim().toLongOrNull()?.let { p(ctx).edit().putLong("seen", it).apply() }
        } catch (e: Exception) { }
    }

    fun waiting(ctx: Context) = io.github.michealsizemore1.beyondstride.Hydration.changes(ctx).size

    /** The line and its color (green when recent, yellow when it's been a while or changes are waiting). */
    fun line(ctx: Context): Pair<String, Int> {
        val s = seen(ctx); val w = waiting(ctx)
        val ago = if (s <= 0) null else System.currentTimeMillis() - s
        val agoTxt = when {
            ago == null -> "not seen yet — reload its page"
            ago < 2 * 60_000L -> "synced just now"
            ago < 60 * 60_000L -> "synced ${ago / 60_000} min ago"
            ago < 48 * 3600_000L -> "synced ${ago / 3600_000} h ago"
            else -> "synced ${ago / 86_400_000L} days ago"
        }
        val stale = ago == null || ago > 6 * 3600_000L
        val txt = "Command Center $agoTxt" + (if (w > 0) " · $w change${if (w == 1) "" else "s"} waiting — open its page" else if (stale) " — open its page to sync" else "")
        return txt to (if (stale || w > 0) 0xFFF2C94C.toInt() else 0xFF5FD38D.toInt())
    }
}
