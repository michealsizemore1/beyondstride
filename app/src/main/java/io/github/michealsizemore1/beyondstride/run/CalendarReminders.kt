package io.github.michealsizemore1.beyondstride.run

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.github.michealsizemore1.beyondstride.R
import org.json.JSONArray
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 🔔 Command Center calendar reminders as phone notifications — even with the app closed.
 * Your events come from the Life tab itself: it hands the phone a copy whenever the calendar changes
 * (since Nov 21 2026 the Life tab no longer syncs with the Command Center website). Checked hourly (with the
 * other reminders) and whenever the Life tab opens; each reminder is scheduled once for its exact time.
 */
object CalendarReminders {
    private fun p(ctx: Context) = ctx.getSharedPreferences("ccreminders", 0)
    fun on(ctx: Context) = p(ctx).getBoolean("on", true)
    /** Save the Life tab's calendar events. Returns true if they changed. */
    fun saveEvents(ctx: Context, json: String): Boolean {
        if (p(ctx).getString("events", null) == json) return false
        p(ctx).edit().putString("events", json).apply(); return true
    }
    fun setOn(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("on", v).apply(); Thread { refresh(ctx) }.start() }

    /** Fetch the events and (re)schedule the next 14 days' reminders. Call off the main thread. */
    fun refresh(ctx: Context) {
        val wm = try { WorkManager.getInstance(ctx) } catch (e: Exception) { return }
        val old = p(ctx).getStringSet("names", emptySet()) ?: emptySet()
        val keep = HashSet<String>()
        if (on(ctx)) {
            val str = p(ctx).getString("events", null) ?: return     // not handed over yet: leave what's scheduled alone
            val list = try { JSONArray(str) } catch (e: Exception) { JSONArray() }
            val now = LocalDateTime.now(); val until = now.plusDays(14)
            for (i in 0 until list.length()) {
                val e = list.optJSONObject(i) ?: continue
                val time = e.optString("time"); if (time.isEmpty() || e.optBoolean("noReminder")) continue
                val rem = e.optInt("reminderMinutes", 60); if (rem < 0) continue
                val at = try { LocalDate.parse(e.optString("date")).atTime(LocalTime.parse(time)).minusMinutes(rem.toLong()) } catch (x: Exception) { continue }
                if (!at.isAfter(now) || at.isAfter(until)) continue
                val name = "ccrem_" + e.optString("id") + "_" + e.optString("date") + "_" + time + "_" + rem
                keep.add(name)
                if (name in old) continue
                val whenText = try { LocalTime.parse(time).format(DateTimeFormatter.ofPattern("h:mm a", Locale.US)) } catch (x: Exception) { time }
                val day = LocalDate.parse(e.optString("date"))
                val dayText = when (day) { LocalDate.now() -> "today"; LocalDate.now().plusDays(1) -> "tomorrow"; else -> day.format(DateTimeFormatter.ofPattern("EEE MMM d", Locale.US)) }
                val data = Data.Builder().putString("title", e.optString("title", "Event"))
                    .putString("text", "$dayText at $whenText" + (e.optString("note").takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""))
                    .putInt("id", name.hashCode()).build()
                wm.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<Fire>()
                    .setInitialDelay(Duration.between(now, at).toMillis(), TimeUnit.MILLISECONDS).setInputData(data).build())
            }
        }
        (old - keep).forEach { wm.cancelUniqueWork(it) }      // deleted / moved / changed events
        p(ctx).edit().putStringSet("names", keep).apply()
    }

    class Fire(c: Context, w: WorkerParameters) : Worker(c, w) {
        override fun doWork(): Result {
            val ctx = applicationContext; val d = inputData
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return Result.success()
            nm.createNotificationChannel(NotificationChannel("ccevents", "Calendar reminders", NotificationManager.IMPORTANCE_HIGH))
            val pi = PendingIntent.getActivity(ctx, d.getInt("id", 0), Intent(ctx, LifeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            try {
                nm.notify(d.getInt("id", 0), android.app.Notification.Builder(ctx, "ccevents").setSmallIcon(R.drawable.ic_stat_run)
                    .setContentTitle("🔔 " + d.getString("title")).setContentText(d.getString("text"))
                    .setContentIntent(pi).setAutoCancel(true).build())
            } catch (e: SecurityException) { }
            return Result.success()
        }
    }
}
