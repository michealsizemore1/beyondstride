package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import io.github.michealsizemore1.beyondstride.Readiness
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime

/**
 * 🌙 Backup Day Review: at 11:50 PM,
 * the phone writes the day's review (Wins / What to Improve / Focus / Lessons) with your AI key from today's training,
 * food, sleep and recovery, and puts it in the cloud. The Command Center fills its Daily AI Review from it whenever the
 * review is still empty — even if the Command Center wasn't open that night. (Your Command Center journal and tasks
 * aren't on the phone, so this one is based on BeyondStride's data only.) The key itself never leaves the phone.
 */
object DayReviewAi {
    private fun p(ctx: Context) = ctx.getSharedPreferences("dayreviewai", 0)
    /** When the Daily (and Sunday's weekly) reviews are written: 11:50 PM (catches almost the whole day). */
    const val REVIEW_AT = 23 * 60 + 50
    fun doneFor(ctx: Context) = p(ctx).getString("done", "") ?: ""

    /** Books tonight's run (report time − 5 min). Safe to call often. */
    fun schedule(ctx: Context) {
        // The time changed (10:55 → 11:50 PM): drop the job booked for the old time.
        if (p(ctx).getInt("bookedFor", -1) != REVIEW_AT) { try { androidx.work.WorkManager.getInstance(ctx).cancelUniqueWork("dayReviewAi") } catch (e: Exception) { }
            p(ctx).edit().putInt("bookedFor", REVIEW_AT).apply() }
        val now = LocalDateTime.now(); val m = REVIEW_AT
        var at = now.toLocalDate().atTime(m / 60, m % 60)
        if (!at.isAfter(now)) at = at.plusDays(1)
        try { androidx.work.WorkManager.getInstance(ctx).enqueueUniqueWork("dayReviewAi", androidx.work.ExistingWorkPolicy.KEEP,
            androidx.work.OneTimeWorkRequestBuilder<Work>().setInputData(androidx.work.workDataOf("date" to at.toLocalDate().toString())).setInitialDelay(java.time.Duration.between(now, at).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build()).build()) } catch (e: Exception) { }
    }
    class Work(c: Context, w: androidx.work.WorkerParameters) : androidx.work.Worker(c, w) {
        override fun doWork(): Result {
            // The evening this job was booked for. Android can hold it past midnight (Doze, no network): it still writes
            // THAT evening's review, never the new day's (Nov 27 2026: a late 11:50 PM job filled the next day at 1:46 AM).
            val now = java.time.LocalDateTime.now()
            val booked = inputData.getString("date") ?: (if (now.hour < 12) now.toLocalDate().minusDays(1) else now.toLocalDate()).toString()
            try { if (booked <= Readiness.today()) runFor(applicationContext, booked) } catch (e: Exception) { }
            try { WeeklyRunWrap.maybeAuto(applicationContext, force = true) } catch (e: Exception) { }; return Result.success() }
    }

    /** Writes today's review when it's due and not done yet (from report time − 5 min until midnight). Call off the main thread. */
    fun maybeRun(ctx: Context, force: Boolean = false) {
        try { clearEarly(ctx) } catch (e: Exception) { }
        val today = Readiness.today()
        val t = java.time.LocalTime.now(); val mins = t.hour * 60 + t.minute
        if (!force && mins < REVIEW_AT) return
        runFor(ctx, today)
    }

    /** Writes [date]'s review unless it's done. Today's waits until 9 PM; a day still to come never gets one. */
    private fun runFor(ctx: Context, date: String) {
        if (!AiFood.hasKey(ctx) || !NutritionSync.ready(ctx) || doneFor(ctx) == date) return
        val today = Readiness.today(); if (date > today) return
        if (date == today && java.time.LocalTime.now().hour < 21) return
        run(ctx, date)
    }

    /** Once a day: today's review in the cloud that was written before 9 PM today (too early, e.g. by a job that ran
     *  late after midnight) is removed, so the Command Center doesn't show it and tonight's review is written as usual. */
    private fun clearEarly(ctx: Context) {
        val today = Readiness.today(); if (p(ctx).getString("earlyChecked", "") == today || !NutritionSync.ready(ctx)) return
        val url = Live.db(ctx) + "/nutrition/" + NutritionSync.key(ctx) + "/beyondstride/review/$today.json"
        val c = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 20_000 }
        val body = try { if (c.responseCode in 200..299) c.inputStream.bufferedReader().readText() else return } finally { c.disconnect() }
        p(ctx).edit().putString("earlyChecked", today).apply()
        val j = try { JSONObject(body) } catch (e: Exception) { return }
        val at = j.optLong("at", 0L); if (at <= 0L) return
        val nine = java.time.LocalDate.now().atTime(21, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (at >= nine) return
        val d = (URL(url).openConnection() as HttpURLConnection).apply { requestMethod = "DELETE"; connectTimeout = 15_000; readTimeout = 20_000 }
        try { d.responseCode } finally { d.disconnect() }
        if (doneFor(ctx) == today) p(ctx).edit().remove("done").apply()
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
        val evening = if (date != Readiness.today()) "" else try { EveningReport.build(ctx).let { r -> r.headline + "\n" + r.sections.joinToString("\n") { s -> "${s.title}: ${s.main}" + (if (s.lines.isNotEmpty()) " — " + s.lines.joinToString("; ") else "") } + "\nAdvice: " + r.advice } } catch (e: Exception) { "" }
        if (day.isBlank() && evening.isBlank()) return
        val prompt = "You are completing a personal daily review for $date. Use only the training / nutrition / recovery data below from the BeyondStride app. " +
            "Return ONLY one valid JSON object with exactly these four string keys:\n{\"wins\":\"\",\"improve\":\"\",\"focus\":\"\",\"lessons\":\"\"}\n\n" +
            "- wins: 1-3 specific achievements or positive moments from today\n- improve: 1-3 constructive, realistic improvements\n" +
            "- focus: 1-2 specific priorities for tomorrow\n- lessons: 1-3 concise takeaways from today\n" +
            "Format (easy to scan): inside each string, one point per line, each line starting with \"• \" and separated by \\n. Start every point with a 1–3 word label and a colon (\"• Training:\", \"• Nutrition:\", \"• Sleep:\", \"• Recovery:\") then one clear sentence under 25 words. Do not invent events. Use numbers exactly as written; HRV in ms is not a readiness score.\n" +
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
