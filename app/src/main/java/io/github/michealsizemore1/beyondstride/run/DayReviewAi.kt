package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import io.github.michealsizemore1.beyondstride.Readiness
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime

/**
 * 🌙 Backup Day Review: at 10:55 PM,
 * the phone writes the day's review (Wins / What to Improve / Focus / Lessons) with your AI key from today's training,
 * food, sleep and recovery, and puts it in the cloud. The Command Center fills its Daily AI Review from it whenever the
 * review is still empty — even if the Command Center wasn't open that night. (Your Command Center journal and tasks
 * aren't on the phone, so this one is based on BeyondStride's data only.) The key itself never leaves the phone.
 */
object DayReviewAi {
    private fun p(ctx: Context) = ctx.getSharedPreferences("dayreviewai", 0)
    /** When the Daily (and Sunday's weekly) reviews are written: 10:55 PM. */
    const val REVIEW_AT = 22 * 60 + 55
    fun doneFor(ctx: Context) = p(ctx).getString("done", "") ?: ""

    /** Books tonight's run (report time − 5 min). Safe to call often. */
    fun schedule(ctx: Context) {
        val now = LocalDateTime.now(); val m = REVIEW_AT
        var at = now.toLocalDate().atTime(m / 60, m % 60)
        if (!at.isAfter(now)) at = at.plusDays(1)
        try { androidx.work.WorkManager.getInstance(ctx).enqueueUniqueWork("dayReviewAi", androidx.work.ExistingWorkPolicy.KEEP,
            androidx.work.OneTimeWorkRequestBuilder<Work>().setInitialDelay(java.time.Duration.between(now, at).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build()).build()) } catch (e: Exception) { }
    }
    class Work(c: Context, w: androidx.work.WorkerParameters) : androidx.work.Worker(c, w) {
        override fun doWork(): Result { try { maybeRun(applicationContext, force = true) } catch (e: Exception) { }
            try { WeeklyRunWrap.maybeAuto(applicationContext, force = true) } catch (e: Exception) { }; return Result.success() }
    }

    /** Writes today's review when it's due and not done yet (from report time − 5 min until midnight). Call off the main thread. */
    fun maybeRun(ctx: Context, force: Boolean = false) {
        if (!AiFood.hasKey(ctx) || !NutritionSync.ready(ctx)) return
        val today = Readiness.today(); if (doneFor(ctx) == today) return
        val t = java.time.LocalTime.now(); val mins = t.hour * 60 + t.minute
        if (!force && mins < REVIEW_AT) return
        run(ctx, today)
    }

    /** Tonight's to-dos belong to the Evening Report, not the review. */
    private val TONIGHT = Regex("lights?[- ]?out|bed ?time|before bed|go(ing)? to bed|to bed early|bedtime snack|recovery snack|evening snack|lay out (your )?gear|sleep tonight", RegexOption.IGNORE_CASE)
    /** Drops review lines about tonight; in a mixed line, cuts the tonight part off. */
    private fun clean(t: String) = t.lines().mapNotNull { l ->
        if (!TONIGHT.containsMatchIn(l)) l else l.split(Regex("\\s*[—–;]\\s*|,\\s+")).filterNot { TONIGHT.containsMatchIn(it) }.joinToString(", ").trim()
            .takeIf { it.trimStart('•', '-', ' ').length > 25 }
    }.filter { it.isNotBlank() }.joinToString("\n").trim()

    private fun run(ctx: Context, date: String) {
        val day = try { DailySummary.build(ctx, date).optString("text").lines().filterNot { TONIGHT.containsMatchIn(it) }.joinToString("\n") } catch (e: Exception) { "" }
        val evening = try { EveningReport.build(ctx).let { r -> r.headline + "\n" + r.sections.joinToString("\n") { s -> "${s.title}: ${s.main}" + (if (s.lines.isNotEmpty()) " — " + s.lines.joinToString("; ") else "") } + "\nAdvice: " + r.advice } } catch (e: Exception) { "" }
        if (day.isBlank() && evening.isBlank()) return
        val prompt = "You are completing a personal daily review for $date. Use only the training / nutrition / recovery data below from the BeyondStride app. " +
            "Return ONLY one valid JSON object with exactly these four string keys:\n{\"wins\":\"\",\"improve\":\"\",\"focus\":\"\",\"lessons\":\"\"}\n\n" +
            "- wins: 1-3 specific achievements or positive moments from today\n- improve: 1-3 constructive, realistic improvements\n" +
            "- focus: 1-2 specific priorities for tomorrow\n- lessons: 1-3 concise takeaways from today\n" +
            "Use short bullet-style lines inside each string. Do not invent events. Use numbers exactly as written; HRV in ms is not a readiness score.\n" +
            "This is a reflective record of the day, kept for later. Do NOT include tonight's to-dos: " +
            "no lights-out / bedtime times, no bedtime or recovery snack, no drinking before bed, no laying out gear. Focus for tomorrow is about the day itself " +
            "(training, eating habits), not tonight. Prefer patterns and lessons over restating numbers.\n\n" +
            "TODAY (BeyondStride):\n$day\n\nEVENING REPORT (already seen — don't repeat):\n$evening"
        val text = AiFood.askChat(ctx, "You write short, specific, encouraging daily reviews for a runner.", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)), 1500)
        val s = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").let { val a = it.indexOf('{'); val b = it.lastIndexOf('}'); if (a >= 0 && b > a) it.substring(a, b + 1) else it }
        val j = JSONObject(s)
        val out = JSONObject().put("wins", clean(j.optString("wins"))).put("improve", clean(j.optString("improve"))).put("focus", clean(j.optString("focus")))
            .put("lessons", clean(j.optString("lessons"))).put("at", System.currentTimeMillis()).put("source", "BeyondStride")
        val c = (URL(Live.db(ctx) + "/nutrition/" + NutritionSync.key(ctx) + "/beyondstride/review/$date.json").openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"; doOutput = true; connectTimeout = 15_000; readTimeout = 20_000; setRequestProperty("Content-Type", "application/json") }
        c.outputStream.use { it.write(out.toString().toByteArray()) }
        if (c.responseCode in 200..299) p(ctx).edit().putString("done", date).apply()
    }
}
