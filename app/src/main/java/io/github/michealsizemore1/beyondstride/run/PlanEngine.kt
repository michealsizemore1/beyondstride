package io.github.michealsizemore1.beyondstride.run

import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The Command Center's training-plan logic, done in BeyondStride (same rules):
 * reading day headers, building the dated / weekly schedule, finding a day's workout, reflowing PDF text,
 * race-date detection, the TRAINING ZONES block, Add Dates, and uploading a plan from text.
 */
object PlanEngine {
    private val META_SAME = Regex("^\\s*(start\\s*date|plan\\s*start|race\\s*day|target\\s*date|finish\\s*date)\\s*:", RegexOption.IGNORE_CASE)
    private val META_ALONE = Regex("^\\s*(start\\s*date|plan\\s*start|race\\s*day|target\\s*date|finish\\s*date)\\s*:?\\s*$", RegexOption.IGNORE_CASE)
    private val MONTHS = mapOf("jan" to 1, "january" to 1, "feb" to 2, "february" to 2, "mar" to 3, "march" to 3, "apr" to 4, "april" to 4, "may" to 5, "jun" to 6, "june" to 6,
        "jul" to 7, "july" to 7, "aug" to 8, "august" to 8, "sep" to 9, "sept" to 9, "september" to 9, "oct" to 10, "october" to 10, "nov" to 11, "november" to 11, "dec" to 12, "december" to 12)
    /** A "Week N" heading, also with markdown / decoration in front ("## WEEK 2 — Base", "**Week 3**", "=== WEEK 4"). */
    val WEEK_HEAD = Regex("^[\\s#*>=_|\\-]*week\\s+(\\d+)\\b", RegexOption.IGNORE_CASE)
    private fun ymd(y: Int, m: Int, d: Int) = try { LocalDate.of(y, m, d).toString() } catch (e: Exception) { null }
    private fun lines(t: String) = t.split(Regex("\r?\n"))

    /** A day header's date (trainingPlanDateFromLine), or null. */
    fun dateFromLine(line: String, fallbackYear: Int): String? {
        val clean = line.replace(Regex("^[#*\\s>-]+"), "").trim()
        if (clean.isEmpty() || Regex("^week\\s*\\d+", RegexOption.IGNORE_CASE).containsMatchIn(clean)) return null
        Regex("\\b(20\\d{2})-(\\d{1,2})-(\\d{1,2})\\b").find(clean)?.let { m -> return ymd(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }
        Regex("(?:^|\\s)(\\d{1,2})[/-](\\d{1,2})[/-](\\d{2,4})(?:\\s|$|[:–—-])").find(clean)?.let { m ->
            var y = m.groupValues[3].toInt(); if (y < 100) y += 2000; return ymd(y, m.groupValues[1].toInt(), m.groupValues[2].toInt()) }
        if (Regex("\\b(sun(?:day)?|mon(?:day)?|tue(?:sday)?|wed(?:nesday)?|thu(?:rsday)?|fri(?:day)?|sat(?:urday)?)\\b", RegexOption.IGNORE_CASE).containsMatchIn(clean)) {
            Regex("\\b(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\s+(\\d{1,2})(?:,?\\s+(20\\d{2}))?", RegexOption.IGNORE_CASE).find(clean)?.let { m ->
                return ymd(m.groupValues[3].ifEmpty { null }?.toInt() ?: fallbackYear, MONTHS[m.groupValues[1].lowercase()] ?: return null, m.groupValues[2].toInt()) }
        }
        return null
    }

    /** {schedule: {date: text}, mode: "dated" | "weekly"} (buildUploadedTrainingSchedule). */
    fun buildSchedule(text: String, startDate: String, weeks: Int): Pair<Map<String, String>, String> {
        val ls = lines(text); val fy = try { LocalDate.parse(startDate).year } catch (e: Exception) { LocalDate.now().year }
        val headers = ArrayList<Pair<Int, String>>()
        ls.forEachIndexed { i, line ->
            val key = dateFromLine(line, fy) ?: return@forEachIndexed
            if (META_SAME.containsMatchIn(line)) return@forEachIndexed
            val prev = (i - 1 downTo 0).map { ls[it] }.firstOrNull { it.trim().isNotEmpty() }
            if (prev != null && META_ALONE.matches(prev)) return@forEachIndexed
            headers += i to key
        }
        val sched = LinkedHashMap<String, String>()
        headers.forEachIndexed { n, (idx, key) ->
            val end = if (n + 1 < headers.size) headers[n + 1].first else ls.size
            var sec = ls.subList(idx, end)
            for (si in 1 until sec.size) if (WEEK_HEAD.containsMatchIn(sec[si])) { sec = sec.subList(0, si); break }
            val s = sec.joinToString("\n").trim(); if (s.isNotEmpty()) sched[key] = s
        }
        if (sched.isNotEmpty()) return sched to "dated"
        val start = try { LocalDate.parse(startDate) } catch (e: Exception) { LocalDate.now() }
        for (i in 0 until Math.max(7, Math.max(1, weeks) * 7)) { val d = start.plusDays(i.toLong()); workout(JSONObject().put("text", text).put("startDate", startDate), d, false)?.let { sched[d.toString()] = it } }
        return sched to "weekly"
    }

    private fun fmts(d: LocalDate) = listOf("EEEE, MMMM d, yyyy", "MMMM d, yyyy", "EEEE, MMMM d", "MMMM d").map { d.format(DateTimeFormatter.ofPattern(it, Locale.US)).lowercase() } + d.toString()

    /** The section under an exact date header (extractWorkoutSection). */
    private fun section(text: String, d: LocalDate): String? {
        val f = fmts(d); val ls = text.split("\n")
        var start = -1
        for (i in ls.indices) {
            val lower = ls[i].lowercase(); if (f.none { lower.contains(it) }) continue
            if (META_SAME.containsMatchIn(ls[i])) continue
            val prev = (i - 1 downTo 0).map { ls[it] }.firstOrNull { it.trim().isNotEmpty() }
            if (prev != null && META_ALONE.matches(prev)) continue
            start = i; break
        }
        if (start < 0) return null
        val datePat = Regex("\\b(january|february|march|april|may|june|july|august|september|october|november|december)\\s+\\d{1,2}", RegexOption.IGNORE_CASE)
        val out = arrayListOf(ls[start])
        for (i in start + 1 until Math.min(start + 20, ls.size)) { val l = ls[i]; if (datePat.containsMatchIn(l) && f.none { l.lowercase().contains(it) }) break; out += l }
        return out.joinToString("\n").trim().ifEmpty { null }
    }

    private fun byPosition(text: String, dayNum: Int): String? {
        if (dayNum < 1) return null
        val ls = text.split("\n")
        val wd = Regex("\\b(sunday|monday|tuesday|wednesday|thursday|friday|saturday)\\b", RegexOption.IGNORE_CASE)
        val mo = Regex("\\b(january|february|march|april|may|june|july|august|september|october|november|december)\\s+\\d{1,2}", RegexOption.IGNORE_CASE)
        val h = ls.indices.filter { wd.containsMatchIn(ls[it]) && mo.containsMatchIn(ls[it]) && ls[it].trim().length < 130 }
        if (dayNum > h.size) return null
        val si = h[dayNum - 1]; val ei = if (dayNum < h.size) h[dayNum] else Math.min(si + 25, ls.size)
        return ls.subList(si, ei).joinToString("\n").trim().ifEmpty { null }
    }

    /** The workout for [d] (extractWorkoutFromActivePlan): exact date, then week / weekday, then position. */
    fun workout(plan: JSONObject, d: LocalDate, allowPosition: Boolean = true): String? {
        val text = plan.optString("text"); if (text.isEmpty()) return null
        plan.optJSONObject("schedule")?.takeIf { it.length() > 0 }?.let { s -> return if (s.has(d.toString())) s.optString(d.toString()) else "Rest Day / No workout scheduled." }
        section(text, d)?.let { return it }
        val st = plan.optString("startDate"); if (st.isEmpty()) return null
        val dayNum = try { java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(st), d).toInt() + 1 } catch (e: Exception) { 0 }
        if (dayNum < 1) return null
        val week = (dayNum + 6) / 7
        val wdLong = d.format(DateTimeFormatter.ofPattern("EEEE", Locale.US)).lowercase(); val wdShort = d.format(DateTimeFormatter.ofPattern("EEE", Locale.US)).lowercase()
        val ls = lines(text); var ws = -1; var we = ls.size
        val wre = Regex("\\bweek\\s*$week\\b", RegexOption.IGNORE_CASE)
        for (i in ls.indices) if (wre.containsMatchIn(ls[i])) { ws = i; break }
        if (ws >= 0) {
            for (j in ws + 1 until ls.size) if (Regex("\\bweek\\s*\\d+\\b", RegexOption.IGNORE_CASE).containsMatchIn(ls[j])) { we = j; break }
            val wds = Regex("\\b(sun(?:day)?|mon(?:day)?|tue(?:sday)?|wed(?:nesday)?|thu(?:rsday)?|fri(?:day)?|sat(?:urday)?)\\b", RegexOption.IGNORE_CASE)
            var ds = -1
            for (k in ws + 1 until we) { val l = ls[k].lowercase(); if (l.contains(wdLong) || Regex("\\b$wdShort\\b", RegexOption.IGNORE_CASE).containsMatchIn(l)) { ds = k; break } }
            if (ds >= 0) {
                var de = we; for (n in ds + 1 until we) if (wds.containsMatchIn(ls[n])) { de = n; break }
                ls.subList(ds, de).joinToString("\n").trim().takeIf { it.isNotEmpty() }?.let { return it }
            }
        }
        return if (!allowPosition) null else byPosition(text, dayNum)
    }

    /** Readable PDF text (reflowPlanText). */
    fun reflow(text: String): String {
        if (text.isEmpty()) return text
        var t = text.replace(Regex("[ \t]{2,}"), "\u0000").replace(Regex("[ \t]+([,.;:])"), "$1").replace(Regex("\u0000[ \t]+"), "\u0000").replace(Regex("\n[ \t]+"), "\n")
        val seg = t.split("\u0000"); val sb = StringBuilder(seg[0])
        for (i in 1 until seg.size) {
            val prev = sb.toString().split("\n").last(); val next = seg[i].split("\n").first()
            val endsSentence = Regex("[.!?:]$").containsMatchIn(prev.trim()); val bothLong = prev.trim().length > 25 && next.trim().length > 25
            sb.append(if (endsSentence || bothLong) "\n\n" else "\n").append(seg[i])
        }
        return sb.toString().replace(Regex("\n{3,}"), "\n\n").trim()
    }

    /** Latest "Month D, YYYY" date; the day after it unless that line mentions a race (detectRaceDateFromPlan). */
    fun raceDate(text: String): String? {
        val re = Regex("\\b(january|february|march|april|may|june|july|august|september|october|november|december)\\s+(\\d{1,2}),?\\s+(202\\d)\\b", RegexOption.IGNORE_CASE)
        var latest: LocalDate? = null; var isRaceL = false
        re.findAll(text).forEach { m ->
            val d = try { LocalDate.of(m.groupValues[3].toInt(), MONTHS[m.groupValues[1].lowercase()]!!, m.groupValues[2].toInt()) } catch (e: Exception) { return@forEach }
            val ctx = text.substring(Math.max(0, m.range.first - 60), Math.min(text.length, m.range.last + 1 + 60)).lowercase()
            val race = Regex("\\brace\\b").containsMatchIn(ctx)
            if (latest == null || d.isAfter(latest)) { latest = d; isRaceL = race } else if (race && d == latest) isRaceL = true
        }
        val l = latest ?: return null
        return (if (isRaceL) l else l.plusDays(1)).toString()
    }

    /** The date of the first dated day under the plan's "Week 1" heading, or null. */
    fun firstWeekStart(plan: JSONObject): String? {
        val ls = lines(plan.optString("text")); val fy = try { LocalDate.parse(plan.optString("startDate")).year } catch (e: Exception) { LocalDate.now().year }
        var inWeek1 = false
        for (l in ls) {
            WEEK_HEAD.find(l)?.let { m -> if (dateFromLine(l, fy) == null) { inWeek1 = m.groupValues[1] == "1"; return@let } }
            if (!inWeek1 || META_SAME.containsMatchIn(l)) continue
            dateFromLine(l, fy)?.let { return it }
        }
        return null
    }

    /** The race date shown on the workout card (confirmed, detected, set, or start + weeks). */
    fun race(plan: JSONObject): String {
        if (plan.optString("raceDate").isNotEmpty() && plan.optBoolean("raceDateConfirmed")) return plan.optString("raceDate")
        // A dated plan ends on race day: its last dated day (or a "Month D, YYYY" race mention within the plan's dates).
        val start = plan.optString("startDate")
        val last = if (plan.optString("scheduleMode") == "dated") plan.optJSONObject("schedule")?.keys()?.asSequence()?.maxOrNull() else null
        val det = raceDate(plan.optString("text"))?.takeIf { start.isEmpty() || it >= start }
        if (last != null) return if (det != null && det >= last && det <= LocalDate.parse(last).plusDays(1).toString()) det else last
        det?.let { return it }
        if (plan.optString("raceDate").isNotEmpty()) return plan.optString("raceDate")
        val st = plan.optString("startDate"); val w = plan.optInt("weeks")
        return if (st.isNotEmpty() && w > 0) try { LocalDate.parse(st).plusDays(w * 7L).toString() } catch (e: Exception) { "" } else ""
    }

    /** The TRAINING ZONES block from the current Max / Resting HR (buildTrainingZonesBlock). */
    fun zonesBlock(maxHr: Double, restHr: Double): String {
        val reserve = Math.max(1.0, maxHr - restHr)
        val b = listOf(0.5, 0.6, 0.7, 0.8, 0.9).map { Math.round(restHr + reserve * it).toInt() }
        fun s(old: Int) = Math.round(restHr + reserve * (old - 49) / 127.0).toInt()
        val z2s = b[1]; val z2c = b[2] - 1
        val raceLo = s(130); val raceHi = s(140); val stLo = raceHi; val stHi = s(148); val tLo = stHi; val tHi = s(155); val cLo = s(152); val cHi = s(158)
        return listOf("TRAINING ZONES",
            "Paces are approximate McMillan-style zones for a ~4:00 marathon runner. On easy and long days, heart rate wins over pace; on trails and hills, effort wins over both.", "",
            "RECOVERY (Endurance)", "Pace: 10:45–11:30/mi  ·  HR: under $z2s", "Day after a hard effort", "",
            "EASY / LONG (Endurance)", "Pace: 9:45–10:45/mi  ·  HR: $z2s–$z2c", "Most miles, long runs", "",
            "50K RACE EFFORT (Endurance)", "Pace: 10:15–11:00/mi (trail varies)  ·  HR: $raceLo–$raceHi", "Long-run segments, race day", "",
            "STEADY STATE (Stamina)", "Pace: 9:00–9:20/mi  ·  HR: $stLo–$stHi", "Fast finishes, steady runs", "",
            "TEMPO (Stamina)", "Pace: 8:35–8:50/mi  ·  HR: $tLo–$tHi", "Tempo runs", "",
            "CRUISE / TEMPO INTERVALS (Stamina)", "Pace: 8:15–8:35/mi  ·  HR: $cLo–$cHi", "Interval-style threshold work", "",
            "SPEED (Speed)", "Pace: 7:45–8:05/mi  ·  HR: $cHi+", "Short VO2 repeats, Phase 1-2 only", "",
            "STRIDES / HILL SPRINTS (Sprint)", "Pace: Fast, relaxed  ·  HR: not used", "15-20s pickups", "",
            "Set the $z2c bpm Zone 2 ceiling as a custom HR alert on your Galaxy Watch Ultra. Use the Polar H10 for long runs so the readings are trustworthy.").joinToString("\n")
    }

    /** True when a week-by-week plan still has day lines without dates ("Sun: …" under a "Week N" heading). */
    fun needsDates(plan: JSONObject): Boolean {
        var inWeek = false
        lines(plan.optString("text")).forEach { l ->
            if (Regex("^\\s*week\\s+\\d+\\b", RegexOption.IGNORE_CASE).containsMatchIn(l)) { inWeek = true; return@forEach }
            if (inWeek && Regex("^\\s*(sun|mon|tue|wed|thu|fri|sat)(day)?\\s*:", RegexOption.IGNORE_CASE).containsMatchIn(l)) return true
        }
        return false
    }

    fun hasZones(text: String) = lines(text).any { Regex("^\\s*training zones\\s*$", RegexOption.IGNORE_CASE).matches(it) }

    /** Adds / replaces the TRAINING ZONES block (addTrainingZonesToPlan). Returns the message. */
    fun addZones(plan: JSONObject, maxHr: Double, restHr: Double): String {
        val ls = lines(plan.optString("text")); val block = zonesBlock(maxHr, restHr)
        val zs = ls.indexOfFirst { Regex("^\\s*training zones\\s*$", RegexOption.IGNORE_CASE).matches(it) }
        val before: String; val after: String
        if (zs >= 0) {
            var ze = ls.size; for (e in zs + 1 until ls.size) if (Regex("^\\s*=+\\s*$").matches(ls[e]) || Regex("^\\s*week\\s+1\\b", RegexOption.IGNORE_CASE).containsMatchIn(ls[e])) { ze = e; break }
            before = ls.subList(0, zs).dropLastWhile { it.trim().isEmpty() }.joinToString("\n")
            after = ls.subList(ze, ls.size).dropWhile { it.trim().isEmpty() }.joinToString("\n")
        } else {
            val wk = ls.indexOfFirst { Regex("^\\s*week\\s+1\\b", RegexOption.IGNORE_CASE).containsMatchIn(it) }
            val at = if (wk >= 0) ((wk - 1) downTo Math.max(0, wk - 4)).firstOrNull { Regex("^\\s*=+\\s*$").matches(ls[it]) } ?: wk else ls.size
            before = ls.subList(0, at).joinToString("\n").trimEnd(); after = ls.subList(at, ls.size).joinToString("\n")
        }
        plan.put("text", (if (before.isNotEmpty()) "$before\n\n" else "") + block + (if (after.isNotEmpty()) "\n\n$after" else "")); plan.remove("display")
        rebuild(plan)
        return if (zs >= 0) "Training zones updated." else "Training zones added to the plan."
    }

    /** Puts the real date next to each "Sun:" / "Mon:" day in a week-by-week schedule (addDatesToSchedule). */
    fun addDates(plan: JSONObject): String {
        val ls = lines(plan.optString("text")).toMutableList()
        val fy = try { LocalDate.parse(plan.optString("startDate")).year } catch (e: Exception) { LocalDate.now().year }
        var start: LocalDate? = null
        val lbl = Regex("^\\s*(start\\s*date|plan\\s*start)\\s*:?\\s*", RegexOption.IGNORE_CASE)
        for (i in ls.indices) {
            if (!lbl.containsMatchIn(ls[i])) continue
            val rest = ls[i].replace(lbl, "")
            val same = if (rest.isNotEmpty()) dateFromLine(rest, fy) else null
            if (same != null) start = LocalDate.parse(same)
            else for (n in i + 1 until ls.size) { if (ls[n].trim().isEmpty()) continue; dateFromLine(ls[n], fy)?.let { start = LocalDate.parse(it) }; break }
            break
        }
        if (start == null) plan.optString("startDate").takeIf { it.isNotEmpty() }?.let { start = try { LocalDate.parse(it) } catch (e: Exception) { null } }
        val st = start ?: return "Could not find a start date in this plan to calculate dates from."
        val idx = mapOf("sun" to 0, "mon" to 1, "tue" to 2, "wed" to 3, "thu" to 4, "fri" to 5, "sat" to 6)
        val startIdx = st.dayOfWeek.value % 7
        var week: Int? = null; var changed = 0
        for (li in ls.indices) {
            Regex("^\\s*week\\s+(\\d+)\\b", RegexOption.IGNORE_CASE).find(ls[li])?.let { week = it.groupValues[1].toInt() }?.let { return@let }
            if (Regex("^\\s*week\\s+(\\d+)\\b", RegexOption.IGNORE_CASE).containsMatchIn(ls[li])) continue
            val w = week ?: continue
            val stripped = ls[li].replace(Regex("^(\\s*(?:sun|mon|tue|wed|thu|fri|sat)(?:day)?),\\s*[A-Za-z]{3,9}\\s+\\d{1,2}(\\s*:)", RegexOption.IGNORE_CASE), "$1$2")
            val m = Regex("^(\\s*)(sun|mon|tue|wed|thu|fri|sat)(day)?(\\s*:)", RegexOption.IGNORE_CASE).find(stripped) ?: continue
            val off = ((idx[m.groupValues[2].lowercase()]!! - startIdx) + 7) % 7
            val actual = st.plusDays((w - 1) * 7L + off)
            ls[li] = m.groupValues[1] + m.groupValues[2] + m.groupValues[3] + ", " + actual.format(DateTimeFormatter.ofPattern("MMM d", Locale.US)) + m.groupValues[4] + stripped.substring(m.value.length)
            changed++
        }
        if (changed == 0) return "No \"Week N\" day-by-day schedule lines were found to add dates to."
        plan.put("text", ls.joinToString("\n")); plan.remove("display"); rebuild(plan)
        return "Added dates to $changed day${if (changed == 1) "" else "s"} in the schedule."
    }

    /** For Add Dates: the date the last "Week N" day line lands on if Week 1 starts on [start] (usually race day), or null. */
    fun lastDayFor(plan: JSONObject, start: LocalDate): LocalDate? {
        val idx = mapOf("sun" to 0, "mon" to 1, "tue" to 2, "wed" to 3, "thu" to 4, "fri" to 5, "sat" to 6)
        var week: Int? = null; var best: Pair<Int, String>? = null
        lines(plan.optString("text")).forEach { l ->
            Regex("^\\s*week\\s+(\\d+)\\b", RegexOption.IGNORE_CASE).find(l)?.let { week = it.groupValues[1].toInt(); return@forEach }
            val w = week ?: return@forEach
            val m = Regex("^\\s*(sun|mon|tue|wed|thu|fri|sat)(?:day)?\\s*[,:]", RegexOption.IGNORE_CASE).find(l) ?: return@forEach
            best = w to m.groupValues[1].lowercase()
        }
        val (w, d) = best ?: return null
        return start.plusDays((w - 1) * 7L + ((idx[d]!! - start.dayOfWeek.value % 7) + 7) % 7)
    }

    /** Rebuilds a plan's schedule after its text changed. */
    fun rebuild(plan: JSONObject) {
        val (s, mode) = buildSchedule(plan.optString("text"), plan.optString("startDate").ifEmpty { LocalDate.now().toString() }, plan.optInt("weeks"))
        plan.put("schedule", JSONObject().apply { s.forEach { (k, v) -> put(k, v) } }).put("scheduleMode", mode).put("scheduleVersion", 3)
    }

    /** A new plan from uploaded text (processPlanText). */
    fun fromText(text0: String, startDate: String, isPdf: Boolean): JSONObject? {
        val text = text0.trim(); if (text.isEmpty()) return null
        val weeks = Regex("\\bweek\\s+(\\d{1,2})\\b", RegexOption.IGNORE_CASE).findAll(text).map { it.groupValues[1].toInt() }.filter { it in 1..52 }.maxOrNull() ?: 0
        val (s, mode) = buildSchedule(text, startDate, weeks)
        val start = if (mode == "dated" && s.isNotEmpty()) s.keys.sorted().first() else startDate
        return JSONObject().put("text", text).put("dist", "").put("weeks", weeks).put("raceDate", "").put("startDate", start)
            .put("schedule", JSONObject().apply { s.forEach { (k, v) -> put(k, v) } }).put("scheduleMode", mode).put("scheduleVersion", 3)
            .put("meta", (if (isPdf) "Uploaded PDF plan" else "Uploaded text plan") + if (weeks > 0) " • $weeks weeks" else "")
            .put("createdAt", LocalDate.now().format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)))
    }

    /** Each day's workout from 3 days ago to 120 ahead (markdown removed) — the workout card and the watch. */
    fun days(plan: JSONObject): JSONObject {
        val out = JSONObject(); val st = plan.optString("startDate")
        for (i in -3..120) { val d = LocalDate.now().plusDays(i.toLong()); if (st.isNotEmpty() && d.toString() < st) continue
            workout(plan, d)?.let { w -> out.put(d.toString(), w.replace(Regex("(?m)^#+\\s*"), "").replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1").replace(Regex("\\*([^*]+)\\*"), "$1").replace(Regex("(?m)^---+$"), "").trim().take(3000)) } }
        return out
    }

    /**
     * (week, day of week) for [date] read from the plan itself: the "Week N" heading above that day's dated header and
     * how many dated days come before it in that week. Null when the plan has no such heading for that day.
     */
    fun weekDay(plan: JSONObject, date: String): Pair<Int, Int>? {
        val ls = lines(plan.optString("text")); val fy = try { LocalDate.parse(date).year } catch (e: Exception) { LocalDate.now().year }
        var week: Int? = null; var day = 0
        for (i in ls.indices) {
            WEEK_HEAD.find(ls[i])?.let { m -> if (dateFromLine(ls[i], fy) == null) { week = m.groupValues[1].toInt(); day = 0; return@let } }
            val k = dateFromLine(ls[i], fy) ?: continue
            if (META_SAME.containsMatchIn(ls[i])) continue
            day++
            if (k == date) return week?.let { it to day }
        }
        return null
    }
}
