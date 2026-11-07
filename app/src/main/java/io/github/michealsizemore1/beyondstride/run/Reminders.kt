package io.github.michealsizemore1.beyondstride.run

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.github.michealsizemore1.beyondstride.Nutrition
import io.github.michealsizemore1.beyondstride.NutritionStore
import io.github.michealsizemore1.beyondstride.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Reminders (checked about once an hour, each sent at most once):
 *  • Pre-fueling — the evening before a long / hard workout: calories and carbs so far vs the target.
 *  • Rehydration — after a run with a post-run weight: how much more to drink.
 *  • Shoes — when a shoe passes 85 % and 100 % of its miles.
 *  • Sunday summary — the week's miles vs goal, training load, food quality and streak.
 */
object Reminders {
    val KINDS = listOf("prefuel" to "Pre-fueling the evening before a hard workout", "rehydrate" to "Rehydration after a run",
        "shoes" to "Shoes nearing replacement", "sunday" to "Sunday weekly summary")
    private fun p(ctx: Context) = ctx.getSharedPreferences("reminders", 0)
    fun on(ctx: Context, k: String) = p(ctx).getBoolean("on_$k", true)
    fun set(ctx: Context, k: String, v: Boolean) = p(ctx).edit().putBoolean("on_$k", v).apply()

    fun schedule(ctx: Context) {
        try {
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("reminders", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<Work>(1, TimeUnit.HOURS).build())
        } catch (e: Exception) { }
    }

    class Work(c: Context, w: WorkerParameters) : Worker(c, w) {
        override fun doWork(): Result { try { check(applicationContext) } catch (e: Exception) { }; return Result.success() }
    }

    private fun once(ctx: Context, key: String): Boolean {
        if (p(ctx).getBoolean("sent_$key", false)) return false
        p(ctx).edit().putBoolean("sent_$key", true).apply(); return true
    }

    private fun notify(ctx: Context, id: Int, title: String, text: String, open: Class<*>) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel("reminders", "Reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val pi = PendingIntent.getActivity(ctx, id, Intent(ctx, open).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        try {
            nm.notify(id, android.app.Notification.Builder(ctx, "reminders").setSmallIcon(R.drawable.ic_stat_run).setContentTitle(title)
                .setContentText(text).setStyle(android.app.Notification.BigTextStyle().bigText(text)).setContentIntent(pi).setAutoCancel(true).build())
        } catch (e: SecurityException) { }
    }

    fun check(ctx: Context) {
        val now = LocalTime.now(); val today = LocalDate.now()
        try { NutritionSync.refreshIfStale(ctx, 30 * 60_000L) } catch (e: Exception) { }
        val d = Nutrition.today(NutritionStore.days(ctx))

        // Pre-fueling: 5–9 pm on the day before a hard workout (the Command Center's "Pre-fueling day").
        if (on(ctx, "prefuel") && now.hour in 17..20 && d != null && d.perfNote.startsWith("Pre-fueling") && once(ctx, "prefuel_$today")) {
            val (_, cp, _) = d.ratios()
            val what = try { org.json.JSONObject(d.prerun).optString("title").substringAfter("Tomorrow: ").substringBefore(" —") } catch (e: Exception) { "" }
            notify(ctx, 7101, "Tomorrow: ${what.ifEmpty { "hard workout" }} — pre-fuel today",
                "You're at ${Nutrition.kcal(d.cal)}" + (if (d.target > 0) " of ${Nutrition.kcal(d.target)} kcal" else " kcal") +
                    (if (d.mtCarbs > 0) ", carbs $cp% vs ${d.mtCarbs}% target" else "") + ". Emphasize carbohydrates at dinner and the evening snack.", NutritionActivity::class.java)
        }
        // Rehydration: once a day, after a post-run weight shows a deficit.
        if (on(ctx, "rehydrate")) Nutrition.rehydrate(d)?.takeIf { it.needOz >= 8 }?.let { h ->
            if (once(ctx, "rehydrate_$today")) notify(ctx, 7102, "Rehydrate after your run",
                "Lost ${String.format(Locale.US, "%.1f", h.lostLb)} lb — drink ~${h.needOz} oz more (target ~${h.targetOz} oz).", NutritionActivity::class.java)
        }
        // Shoes: 85 % and 100 % of max miles, once each per shoe.
        if (on(ctx, "shoes")) ActivityStore.shoes(ctx).filter { !it.optBoolean("retired") }.forEach { sh ->
            val max = sh.optDouble("maxMiles", 500.0).takeIf { it > 0 } ?: 500.0
            val mi = ActivityStore.shoeMiles(ctx, sh); val pct = mi / max * 100; val id = sh.optString("id")
            val level = if (pct >= 100) 100 else if (pct >= 85) 85 else 0
            if (level > 0 && once(ctx, "shoe_${id}_$level")) notify(ctx, 7103 + (id.hashCode() and 0xff), if (level == 100) "👟 Time to replace ${sh.optString("name")}" else "👟 ${sh.optString("name")} is at ${pct.toInt()}%",
                String.format(Locale.US, "%.0f of %.0f miles", mi, max) + if (level == 100) " — it's past its mileage." else " — about ${Math.max(0, (max - mi).toInt())} miles left.", ActivitiesActivity::class.java)
        }
        // Sunday summary: Sunday from 6 pm.
        if (on(ctx, "sunday") && today.dayOfWeek == DayOfWeek.SUNDAY && now.hour >= 18 && once(ctx, "sunday_$today")) {
            val m = ActivityStore.mileage(ctx); val goal = ActivityStore.goal(ctx, "week"); val ac = ActivityStore.acwr(ctx)
            val mon = today.minusDays(6).toString()
            val week = NutritionStore.days(ctx).filter { it.date >= mon && it.count > 0 }
            val fq = week.mapNotNull { io.github.michealsizemore1.beyondstride.Food.dayQuality(it)?.score }.takeIf { it.isNotEmpty() }?.average()
            val streak = ActivityStore.watchSummary(ctx)?.optInt("streak") ?: 0
            notify(ctx, 7199, "Your week", listOfNotNull(
                m?.let { String.format(Locale.US, "%.1f mi", it.week) + (if (goal > 0) String.format(Locale.US, " of %.0f goal (%d%%)", goal, Math.round(it.week / goal * 100)) else "") + " · ${it.runs} runs" },
                ac?.let { String.format(Locale.US, "Training load (miles) %.2f", it.first) },
                fq?.let { String.format(Locale.US, "Food quality %.1f/10 avg", it) },
                if (streak > 0) "🔥 $streak-week streak" else null).joinToString(" · ").ifEmpty { "Open BeyondStride for your week." }, RunsActivity::class.java)
        }
    }
}
