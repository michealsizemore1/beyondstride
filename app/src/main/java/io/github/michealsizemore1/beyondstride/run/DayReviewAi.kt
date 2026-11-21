package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import java.time.LocalDateTime

/**
 * 🌙 The 11:50 PM job: writes Sunday's 📝 Weekly Run Notes ([WeeklyRunWrap]) even if the app isn't open.
 * (It also used to write a backup Day Review for the Command Center; BeyondStride was disconnected from the
 * Command Center in Nov 2026, so that part is gone.)
 */
object DayReviewAi {
    private fun p(ctx: Context) = ctx.getSharedPreferences("dayreviewai", 0)
    /** When Sunday's Weekly Run Notes are written: 11:50 PM (catches almost the whole day). */
    const val REVIEW_AT = 23 * 60 + 50

    /** Books tonight's run (report time − 5 min). Safe to call often. */
    fun schedule(ctx: Context) {
        // The time changed (10:55 → 11:50 PM): drop the job booked for the old time.
        if (p(ctx).getInt("bookedFor", -1) != REVIEW_AT) { try { androidx.work.WorkManager.getInstance(ctx).cancelUniqueWork("dayReviewAi") } catch (e: Exception) { }
            p(ctx).edit().putInt("bookedFor", REVIEW_AT).apply() }
        val now = LocalDateTime.now(); val m = REVIEW_AT
        var at = now.toLocalDate().atTime(m / 60, m % 60)
        if (!at.isAfter(now)) at = at.plusDays(1)
        try { androidx.work.WorkManager.getInstance(ctx).enqueueUniqueWork("dayReviewAi", androidx.work.ExistingWorkPolicy.KEEP,
            androidx.work.OneTimeWorkRequestBuilder<Work>().setInitialDelay(java.time.Duration.between(now, at).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build()).build()) } catch (e: Exception) { }
    }
    class Work(c: Context, w: androidx.work.WorkerParameters) : androidx.work.Worker(c, w) {
        override fun doWork(): Result { try { WeeklyRunWrap.maybeAuto(applicationContext, force = true) } catch (e: Exception) { }
            return Result.success() }
    }
}
