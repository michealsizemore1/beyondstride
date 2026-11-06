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
        val json = Nutrition.json(days.takeLast(21))
        p(ctx).edit().putString("days", json).putLong("at", System.currentTimeMillis()).apply()
        val app = ctx.applicationContext
        Thread {
            try {
                val req = PutDataMapRequest.create(PATH).apply { dataMap.putString("json", json); dataMap.putLong("at", System.currentTimeMillis()) }
                    .asPutDataRequest().setUrgent()
                Tasks.await(Wearable.getDataClient(app).putDataItem(req))
            } catch (e: Exception) { }
        }.start()
    }

    /** Watch: data from the phone. */
    fun apply(ctx: Context, m: DataMap) {
        val s = m.getString("json") ?: return
        p(ctx).edit().putString("days", s).putLong("at", System.currentTimeMillis()).apply()
    }
}
