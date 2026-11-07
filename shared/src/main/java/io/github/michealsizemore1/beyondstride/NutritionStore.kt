package io.github.michealsizemore1.beyondstride

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

/** The last few weeks of daily nutrition, kept on the phone and copied to the watch (/nutrition). */
object NutritionStore {
    const val PATH = "/nutrition"
    private fun p(ctx: Context) = ctx.getSharedPreferences("nutrition", 0)

    fun days(ctx: Context) = Nutrition.parse(p(ctx).getString("days", null))
    fun fetchedAt(ctx: Context) = p(ctx).getLong("at", 0L)
    fun yesterday(ctx: Context) = Nutrition.yesterday(days(ctx))

    /** Saves (phone, after reading Firebase) and sends to the watch. */
    fun save(ctx: Context, days: List<Nutrition.Day>) {
        val json21 = Nutrition.json(days.takeLast(21))
        p(ctx).edit().putString("days", json21).putLong("at", System.currentTimeMillis()).apply()
        val json = Nutrition.json(days.takeLast(8))   // the watch shows a week (keeps the transfer small)
        val app = ctx.applicationContext
        Thread {
            try {
                val req = PutDataMapRequest.create(PATH).apply {
                    dataMap.putString("json", json); dataMap.putString("quick", p(ctx).getString("quick", "[]") ?: "[]"); dataMap.putLong("at", System.currentTimeMillis()) }
                    .asPutDataRequest().setUrgent()
                Tasks.await(Wearable.getDataClient(app).putDataItem(req))
            } catch (e: Exception) { }
        }.start()
    }

    /** Watch: shows a change right away (the phone sends the real day back a moment later). */
    fun putLocal(ctx: Context, days: List<Nutrition.Day>) =
        p(ctx).edit().putString("days", Nutrition.json(days.takeLast(21))).apply()

    /** Watch: data from the phone. */
    fun apply(ctx: Context, m: DataMap) {
        val s = m.getString("json") ?: return
        p(ctx).edit().putString("days", s).putString("quick", m.getString("quick") ?: "[]").putLong("at", System.currentTimeMillis()).apply()
    }

    /** Phone: the watch's Quick add list (sent with the next save). */
    fun setQuick(ctx: Context, json: String) {
        if (json == p(ctx).getString("quick", "")) return
        p(ctx).edit().putString("quick", json).apply()
        save(ctx, days(ctx))
    }
    const val LIB_PATH = "/foodlib"
    /** Phone: your food database, sent to the watch (separate item so the daily data stays small). */
    fun setLibrary(ctx: Context, json: String) {
        if (json == p(ctx).getString("libSent", "")) return
        p(ctx).edit().putString("libSent", json).apply()
        val app = ctx.applicationContext
        Thread {
            try {
                val req = PutDataMapRequest.create(LIB_PATH).apply { dataMap.putString("json", json); dataMap.putLong("at", System.currentTimeMillis()) }.asPutDataRequest()
                Tasks.await(Wearable.getDataClient(app).putDataItem(req))
            } catch (e: Exception) { }
        }.start()
    }
    /** Watch: the food database from the phone. */
    fun applyLibrary(ctx: Context, m: DataMap) { m.getString("json")?.let { p(ctx).edit().putString("lib", it).apply() } }
    fun library(ctx: Context) = try { Food.parseList(org.json.JSONArray(p(ctx).getString("lib", "[]"))) } catch (e: Exception) { emptyList() }

    /** Watch: foods for Quick add. */
    fun quick(ctx: Context) = try { Food.parseList(org.json.JSONArray(p(ctx).getString("quick", "[]"))) } catch (e: Exception) { emptyList() }
}
