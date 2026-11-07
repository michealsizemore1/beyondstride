package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import io.github.michealsizemore1.beyondstride.Hydration
import io.github.michealsizemore1.beyondstride.Nutrition
import io.github.michealsizemore1.beyondstride.NutritionStore
import io.github.michealsizemore1.beyondstride.Readiness
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Phone side of fluids and weights entered in BeyondStride: saves them (and sends the day to the watch),
 * then posts each change to the Command Center's inbox (<db>/nutrition/<key>/inbox) in Firebase.
 */
object HydrationSync {
    /** A change made here or on the watch. Fluids: [delta] oz. Weights: [value] lb (0 clears). */
    fun change(ctx: Context, f: String, delta: Int = 0, value: Double = 0.0, date: String = Readiness.today()) {
        val app = ctx.applicationContext
        synchronized(Hydration) {
            val days = NutritionStore.days(app)
            Hydration.record(app, days.lastOrNull { it.date == date }, date, f, delta, value)
            NutritionStore.save(app, Hydration.overlay(app, days))
        }
        Thread { flush(app) }.start()
    }

    /** From the watch: "date|f|delta|value". */
    fun fromWatch(ctx: Context, data: ByteArray) {
        val c = Hydration.decode(String(data)) ?: return
        change(ctx, c.f, c.d, c.v, c.date)
    }

    private fun base(ctx: Context) = Live.db(ctx) + "/nutrition/" + NutritionSync.key(ctx) + "/inbox"

    /** Posts changes not sent yet. Call off the main thread. */
    @Synchronized fun flush(ctx: Context) {
        if (!NutritionSync.ready(ctx)) return
        for (c in Hydration.changes(ctx).filter { it.id == null }) {
            try {
                val body = JSONObject().put("date", c.date).put("f", c.f).put("at", c.at)
                if (c.f in Hydration.FLUIDS) body.put("d", c.d) else body.put("v", c.v)
                val conn = (URL(base(ctx) + ".json").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; doOutput = true; connectTimeout = 12_000; readTimeout = 15_000
                    setRequestProperty("Content-Type", "application/json")
                }
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                if (conn.responseCode != 200) return
                val id = JSONObject(conn.inputStream.bufferedReader().readText()).optString("name").ifEmpty { return }
                synchronized(Hydration) {
                    Hydration.saveChanges(ctx, Hydration.changes(ctx).map { if (it.at == c.at && it.f == c.f && it.date == c.date && it.id == null) it.copy(id = id) else it })
                }
            } catch (e: Exception) { return }
        }
    }

    /**
     * Forgets changes the Command Center has taken (gone from the inbox) and, for days with none left,
     * goes back to its totals. Call off the main thread, before reading the days.
     */
    fun reconcile(ctx: Context) {
        if (!NutritionSync.ready(ctx)) return
        flush(ctx)
        if (Hydration.changes(ctx).none { it.id != null }) { synchronized(Hydration) { Hydration.settle(ctx) }; return }
        try {
            val conn = (URL(base(ctx) + ".json?shallow=true").openConnection() as HttpURLConnection).apply { connectTimeout = 12_000; readTimeout = 15_000 }
            if (conn.responseCode != 200) return
            val txt = conn.inputStream.bufferedReader().readText()
            val keys = if (txt.isBlank() || txt == "null") emptySet() else JSONObject(txt).keys().asSequence().toSet()
            synchronized(Hydration) {
                Hydration.saveChanges(ctx, Hydration.changes(ctx).filter { it.id == null || it.id in keys })
                Hydration.settle(ctx)
            }
        } catch (e: Exception) { }
    }

    fun today(ctx: Context): Nutrition.Day? = NutritionStore.days(ctx).lastOrNull { it.date == Readiness.today() }
}
