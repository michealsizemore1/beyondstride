package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale

/**
 * 📝 Weekly Run Notes — the "AI Weekly Run Wrap-Up" (same prompt and facts as the Command Center's), and its automatic run:
 * on Sunday, 5 minutes before the Evening Report (or when tonight's report is asked for early), this week's empty note is
 * written and saved; if the phone missed it, last week's empty note is written at the next chance. A note you've written
 * yourself is never replaced, and each week is done once.
 */
object WeeklyRunWrap {
    private val S = ActivityStore
    private fun p(ctx: Context) = ctx.getSharedPreferences("weeklywrap", 0)

    fun monday(d: LocalDate = LocalDate.now()): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong())
    fun label(week: String): String { val m = LocalDate.parse(week)
        return m.format(java.time.format.DateTimeFormatter.ofPattern("MMM d", Locale.US)) + " – " + m.plusDays(6).format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)) }

    fun runsInWeek(ctx: Context, week: String): List<JSONObject> {
        val start = LocalDate.parse(week); val end = start.plusDays(6)
        val seen = HashSet<String>()
        return S.list(ctx).filter { a ->
            val d = S.str(a, "date").take(10); val rt = listOf("type", "title", "name", "workout").joinToString(" ") { S.str(a, it) }
            val isRun = Regex("run|jog|tempo|interval|fartlek|long|recovery|race|marathon|stride", RegexOption.IGNORE_CASE).containsMatchIn(rt) && !Regex("rest day|walk|bike|cycl|strength|swim", RegexOption.IGNORE_CASE).containsMatchIn(S.str(a, "type"))
            if (d < start.toString() || d > end.toString() || !isRun || S.num(a, "distance") <= 0 && S.durSec(S.str(a, "duration")) <= 0) false
            else seen.add(d + "|" + String.format(Locale.US, "%.3f", S.num(a, "distance")) + "|" + S.str(a, "duration").trim())
        }
    }

    /** The wrap-up prompt for [week] (its Monday), or null when there were no runs. */
    fun prompt(ctx: Context, week: String, notes: String): String? {
        val runs = runsInWeek(ctx, week); if (runs.isEmpty()) return null
        var mi = 0.0; var mins = 0.0; var elev = 0.0; var cal = 0.0; var pS = 0.0; var pN = 0; var longest: JSONObject? = null
        runs.forEach { a -> val m = S.num(a, "distance"); mi += m; mins += S.durSec(S.str(a, "duration")) / 60.0; elev += S.num(a, "elevGain"); cal += S.num(a, "calories")
            if (S.num(a, "power") > 0) { pS += S.num(a, "power"); pN++ }; if (longest == null || m > S.num(longest!!, "distance")) longest = a }
        fun pace(sec: Double): String { val m = Math.floor(sec / 60).toInt(); val s = Math.round(sec % 60).toInt(); return "$m:" + (if (s < 10) "0" else "") + s }
        fun fmtMins(m: Double): String { val r = Math.round(m).toInt(); return if (r < 60) "${r}m" else "${r / 60}h ${r % 60}m" }
        val avgPace = if (mi > 0 && mins > 0) pace(mins * 60 / mi) + "/mi" else "not available"
        val lines = runs.sortedBy { S.str(it, "date") }.joinToString("\n") { a -> listOf(
            S.str(a, "date").ifEmpty { "No date" }, S.str(a, "title").ifEmpty { S.str(a, "type").ifEmpty { "Run" } }, String.format(Locale.US, "%.2f mi", S.num(a, "distance")),
            S.str(a, "duration").ifEmpty { "duration unavailable" }, S.str(a, "pace").takeIf { it.isNotEmpty() }?.let { "$it/mi" } ?: "pace unavailable",
            if (S.num(a, "power") > 0) "${Math.round(S.num(a, "power"))} W" else "power unavailable", if (S.num(a, "heartRate") > 0) "${Math.round(S.num(a, "heartRate"))} bpm" else "heart rate unavailable",
            if (S.num(a, "elevGain") > 0) "${Math.round(S.num(a, "elevGain"))} ft gain" else "elevation unavailable",
            S.str(a, "notes").takeIf { it.isNotEmpty() }?.let { "notes: $it" } ?: "", S.str(a, "injuryReport").takeIf { it.isNotEmpty() }?.let { "injury report: $it" } ?: "").filter { it.isNotEmpty() }.joinToString(" | ") }
        return "Write a concise weekly running wrap-up for ${label(week)}. Use only the supplied facts and do not invent performance, recovery, injury, or health claims. Keep it encouraging, practical, and about 150-220 words. Use short headings: Week at a Glance, Strongest Session, Patterns & Recovery, and Focus for Next Week. If a conclusion is uncertain, say so. This is training reflection, not medical advice.\n\n" +
            "SUMMARY\nRuns: ${runs.size}\nDistance: ${String.format(Locale.US, "%.2f", mi)} miles\nTime: ${fmtMins(mins)}\nAverage pace: $avgPace\nElevation gain: ${Math.round(elev)} ft\nCalories: ${Math.round(cal)}\nAverage recorded power: " +
            (if (pN > 0) "${Math.round(pS / pN)} W" else "not available") + "\nLongest run: " + (longest?.let { (S.str(it, "title").ifEmpty { "Run" }) + ", " + String.format(Locale.US, "%.2f", S.num(it, "distance")) + " miles" } ?: "not available") + "\n\n" +
            "ACTIVITIES\n$lines\n\nATHLETE NOTES\n" + notes.trim().ifEmpty { "No notes entered." }
    }

    /** Writes this week's note on Sunday night, or last week's if it was missed. Call off the main thread. */
    fun maybeAuto(ctx: Context, force: Boolean = false) {
        if (!AiFood.hasKey(ctx)) return
        val today = LocalDate.now(); val t = java.time.LocalTime.now(); val mins = t.hour * 60 + t.minute
        val sundayDue = today.dayOfWeek == java.time.DayOfWeek.SUNDAY && (force || mins >= EveningReport.time(ctx) - 5 || EveningReport.early(ctx))
        val weeks = listOfNotNull(if (sundayDue) monday(today).toString() else null, monday(today).minusWeeks(1).toString())
        val notes = S.weekNotes(ctx); val pr = p(ctx)
        for (w in weeks) {
            if (pr.getBoolean("done_$w", false)) continue
            if (!notes[w].isNullOrBlank()) { pr.edit().putBoolean("done_$w", true).apply(); continue }
            val prompt = prompt(ctx, w, "") ?: run { if (w < monday(today).toString()) pr.edit().putBoolean("done_$w", true).apply(); null } ?: continue
            pr.edit().putBoolean("done_$w", true).apply()
            try { val text = AiFood.askText(ctx, prompt, 2200).trim(); if (text.isNotEmpty()) S.saveWeekNote(ctx, w, text) }
            catch (e: Exception) { pr.edit().putBoolean("done_$w", false).apply() }
            return
        }
    }
}
