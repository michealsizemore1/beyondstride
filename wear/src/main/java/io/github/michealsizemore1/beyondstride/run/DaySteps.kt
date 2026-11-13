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

    fun save(ctx: Context, steps: Long) {
        val today = LocalDate.now().toString(); val pr = p(ctx)
        if (pr.getString("date", "") == today && pr.getLong("steps", -1) == steps) return
        pr.edit().putString("date", today).putLong("steps", steps).apply()
        try { androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester.create(ctx,
            android.content.ComponentName(ctx, StepsComplication::class.java)).requestUpdateAll() } catch (e: Exception) { }
    }

    /** Today's steps: 0 once the day has changed and nothing new has come in yet. */
    fun today(ctx: Context): Long = p(ctx).let { if (it.getString("date", "") == LocalDate.now().toString()) it.getLong("steps", 0) else 0 }
}

class StepsPassiveService : PassiveListenerService() {
    override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
        dataPoints.getData(DataType.STEPS_DAILY).lastOrNull()?.let { DaySteps.save(applicationContext, it.value) }
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
