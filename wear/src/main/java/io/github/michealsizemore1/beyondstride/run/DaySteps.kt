package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import android.content.pm.PackageManager
import androidx.health.services.client.HealthServices
import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.PassiveListenerConfig
import java.time.LocalDate

/**
 * 👣 Today's steps on the watch (Health Services' daily step count — the watch's own counter, which starts again at
 * midnight). Feeds the "BeyondStride · Steps" watch-face spot, which shows 0 after midnight until you move.
 */
object DaySteps {
    private fun p(ctx: Context) = ctx.getSharedPreferences("daysteps", 0)
    fun granted(ctx: Context) = ctx.checkSelfPermission(android.Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    /** Starts listening (safe to call often). */
    fun start(ctx: Context) {
        if (!granted(ctx)) return
        try {
            HealthServices.getClient(ctx).passiveMonitoringClient.setPassiveListenerServiceAsync(StepsPassiveService::class.java,
                PassiveListenerConfig.builder().setDataTypes(setOf(DataType.STEPS_DAILY)).build())
        } catch (e: Exception) { }
    }

    /**
     * [day] is the day the count belongs to (from the reading's own end time). Just after midnight the watch can still
     * deliver yesterday's final total; that goes to the phone as yesterday's, and today's count isn't touched.
     */
    fun save(ctx: Context, steps: Long, day: LocalDate = LocalDate.now()) {
        val today = LocalDate.now().toString(); val pr = p(ctx)
        if (day.toString() != today) { sendToPhone(ctx, day.toString(), steps, force = true); return }
        if (pr.getString("date", "") == today && pr.getLong("steps", -1) == steps) return
        pr.edit().putString("date", today).putLong("steps", steps).apply()
        sendToPhone(ctx, today, steps)
        try { androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester.create(ctx,
            android.content.ComponentName(ctx, StepsComplication::class.java)).requestUpdateAll() } catch (e: Exception) { }
    }

    /**
     * Tells the phone too (its count from Samsung Health via Health Connect can lag hours behind): every 250 steps or
     * 15 minutes, whichever comes first. The phone uses whichever count is higher.
     */
    private fun sendToPhone(ctx: Context, date: String, steps: Long, force: Boolean = false) {
        val pr = p(ctx); val now = System.currentTimeMillis()
        val sameDay = pr.getString("sentDate", "") == date
        if (!force && sameDay && steps - pr.getLong("sentSteps", 0) < 250 && now - pr.getLong("sentAt", 0) < 15 * 60_000L) return
        if (!force) pr.edit().putString("sentDate", date).putLong("sentSteps", steps).putLong("sentAt", now).apply()
        val app = ctx.applicationContext
        Thread { try {
            val req = com.google.android.gms.wearable.PutDataMapRequest.create("/steps").apply {
                dataMap.putString("date", date); dataMap.putLong("steps", steps); dataMap.putLong("at", now) }.asPutDataRequest()
            com.google.android.gms.tasks.Tasks.await(com.google.android.gms.wearable.Wearable.getDataClient(app).putDataItem(req))
        } catch (e: Exception) { } }.start()
    }

    /** Today's steps: 0 once the day has changed and nothing new has come in yet. */
    fun today(ctx: Context): Long = p(ctx).let { if (it.getString("date", "") == LocalDate.now().toString()) it.getLong("steps", 0) else 0 }
}

class StepsPassiveService : PassiveListenerService() {
    override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
        val boot = java.time.Instant.ofEpochMilli(System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime())
        dataPoints.getData(DataType.STEPS_DAILY).forEach { dp ->
            // The day this count belongs to: from the reading's end time (a moment before it, so a count ending at midnight is yesterday's).
            val day = try { dp.getEndInstant(boot).minusMillis(1).atZone(java.time.ZoneId.systemDefault()).toLocalDate() } catch (e: Exception) { LocalDate.now() }
            DaySteps.save(applicationContext, dp.value, day)
        }
    }
}

class StepsComplication : FaceComplication() {
    override fun value(ctx: Context): FaceData.V {
        DaySteps.start(ctx)
        val s = DaySteps.today(ctx)
        return FaceData.V(String.format(java.util.Locale.US, "%,d", s), "steps", s.toFloat(), 10000f, desc = "$s steps today")
    }
    override val sample = FaceData.V("6,240", "steps", 6240f, 10000f)
}
