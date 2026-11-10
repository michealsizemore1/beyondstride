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

    // Parsed once per change (read many times while drawing a screen).
    @Volatile private var daysKey: String? = null; @Volatile private var daysCache: List<Nutrition.Day> = emptyList()
    fun days(ctx: Context): List<Nutrition.Day> {
        val s = p(ctx).getString("days", null)
        if (s != null && s == daysKey) return daysCache
        val d = Nutrition.parse(s); daysCache = d; daysKey = s; return d
    }
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
                // Compressed into one blob: a watch data item can only hold about 100 KB.
                val all = org.json.JSONObject().put("json", json).put("quick", p(ctx).getString("quick", "[]") ?: "[]").put("calc", p(ctx).getString("calc", "{}") ?: "{}")
                    .put("mileage", p(ctx).getString("mileage", "") ?: "").put("acts", p(ctx).getString("acts", "") ?: "")
                val bos = java.io.ByteArrayOutputStream(); java.util.zip.GZIPOutputStream(bos).use { it.write(all.toString().toByteArray()) }
                val req = PutDataMapRequest.create(PATH).apply { dataMap.putByteArray("z", bos.toByteArray()); dataMap.putLong("at", System.currentTimeMillis()) }
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
        m.getByteArray("z")?.let { z ->
            val o = try { org.json.JSONObject(java.util.zip.GZIPInputStream(z.inputStream()).bufferedReader().readText()) } catch (e: Exception) { return }
            p(ctx).edit().putString("days", o.optString("json", "[]")).putString("quick", o.optString("quick", "[]")).putString("calc", o.optString("calc", "{}"))
                .putString("mileage", o.optString("mileage")).putString("acts", o.optString("acts")).putLong("at", System.currentTimeMillis()).apply()
            return
        }
        val s = m.getString("json") ?: return
        p(ctx).edit().putString("days", s).putString("quick", m.getString("quick") ?: "[]").putString("calc", m.getString("calc") ?: "{}").putString("mileage", m.getString("mileage") ?: "").putString("acts", m.getString("acts") ?: "").putLong("at", System.currentTimeMillis()).apply()
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

    /** Phone: this week's / month's miles as Activities counts them (all sources), sent to the watch so both show the same. */
    fun setMileage(ctx: Context, week: Double, runs: Int, month: Double) {
        val mon = java.time.LocalDate.now().let { it.minusDays((it.dayOfWeek.value - 1).toLong()) }.toString()
        val json = org.json.JSONObject().put("week", Math.round(week * 100) / 100.0).put("runs", runs).put("month", Math.round(month * 100) / 100.0).put("monday", mon).put("ym", mon.take(7).let { java.time.LocalDate.now().toString().take(7) }).toString()
        if (json == p(ctx).getString("mileage", "")) return
        p(ctx).edit().putString("mileage", json).apply()
        save(ctx, days(ctx))
    }
    /** Phone: the watch's Activities screen (a small summary). Sent only when it changes (ignoring its time stamp). */
    fun setActs(ctx: Context, o: org.json.JSONObject) {
        val cmp = org.json.JSONObject(o.toString()).apply { remove("at") }.toString()
        if (cmp == p(ctx).getString("actsCmp", "")) return
        p(ctx).edit().putString("acts", o.toString()).putString("actsCmp", cmp).apply()
        save(ctx, days(ctx))
    }
    /** Watch: that summary. */
    fun acts(ctx: Context): org.json.JSONObject? = try { org.json.JSONObject(p(ctx).getString("acts", "") ?: "").takeIf { it.has("count") } } catch (e: Exception) { null }
    /** Watch: that mileage, if it's for the current week. */
    fun mileage(ctx: Context): org.json.JSONObject? = try { org.json.JSONObject(p(ctx).getString("mileage", "") ?: "").takeIf {
        it.optString("monday") == java.time.LocalDate.now().let { d -> d.minusDays((d.dayOfWeek.value - 1).toLong()) }.toString() } } catch (e: Exception) { null }

    /** The calorie target's settings and breakdown (phone sets it; sent to the watch with the days). */
    fun setCalc(ctx: Context, json: String) = p(ctx).edit().putString("calc", json).apply()
    fun calc(ctx: Context) = try { org.json.JSONObject(p(ctx).getString("calc", "{}")) } catch (e: Exception) { org.json.JSONObject() }

    /** Watch: foods for Quick add. */
    fun quick(ctx: Context) = try { Food.parseList(org.json.JSONArray(p(ctx).getString("quick", "[]"))) } catch (e: Exception) { emptyList() }
}
