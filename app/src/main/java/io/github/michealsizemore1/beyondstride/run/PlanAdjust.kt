package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Plan adjustments, suggested from what actually happened and applied only when you tap Accept.
 * The plan's text is never rewritten: an accepted adjustment replaces a day's workout on top of the plan (Undo puts it back).
 *
 *  ↪ Missed long / hard workout → move it to the next open easy day this plan week (never next to another hard day).
 *  ↘ Missed long run with no room this week → next week's long run is held to about +15% over your longest recent run.
 *  ⇄ Low readiness or short sleep on a hard day → swap today with the next easy day this week.
 *  ＋ Short easy run (30–70% done) → carry up to 3 missed miles to the next easy day (not before a hard day, not in the taper).
 *  ↘ Three or more planned run days missed in a row → the next hard workout becomes an easy run (80% of its miles).
 */
object PlanAdjust {
    private fun p(ctx: Context) = ctx.getSharedPreferences("planadjust", 0)
    private val FMT = DateTimeFormatter.ofPattern("EEE MMM d", Locale.US)
    private fun nice(d: String) = try { LocalDate.parse(d).format(FMT) } catch (e: Exception) { d }

    /** Accepted / declined adjustments for the current plan: {plan: key, items: [{id, kind, src, note, changes:{date:text}, status}]}. */
    private fun store(ctx: Context, plan: JSONObject?): JSONObject {
        val key = planKey(plan)
        val o = try { JSONObject(p(ctx).getString("data", "{}")) } catch (e: Exception) { JSONObject() }
        return if (o.optString("plan") == key) o else JSONObject().put("plan", key).put("items", JSONArray())
    }
    private fun save(ctx: Context, o: JSONObject) { p(ctx).edit().putString("data", o.toString()).apply(); CloudCopy.dirty(ctx); PlanStore.invalidate() }
    private fun planKey(plan: JSONObject?) = if (plan == null) "" else "${plan.optString("text").hashCode()}.${plan.optString("startDate")}"
    private fun items(o: JSONObject) = o.optJSONArray("items")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } } ?: emptyList()
    fun stamp(ctx: Context) = (p(ctx).getString("data", "") ?: "").hashCode()

    /** Accepted changes by date (used by PlanStore to show the adjusted day). */
    fun accepted(ctx: Context, plan: JSONObject): Map<String, String> {
        val out = HashMap<String, String>()
        items(store(ctx, plan)).filter { it.optString("status") == "accepted" }.forEach { it.optJSONObject("changes")?.let { c -> c.keys().forEach { k -> out[k] = c.optString(k) } } }
        return out
    }
    fun acceptedList(ctx: Context): List<JSONObject> { val plan = PlanStore.data(ctx).optJSONObject("current") ?: return emptyList()
        return items(store(ctx, plan)).filter { it.optString("status") == "accepted" && it.optJSONObject("changes")?.keys()?.asSequence()?.any { k -> k >= LocalDate.now().minusDays(1).toString() } == true } }

    fun decide(ctx: Context, s: JSONObject, accept: Boolean) {
        val plan = PlanStore.data(ctx).optJSONObject("current") ?: return
        val o = store(ctx, plan); val a = o.optJSONArray("items") ?: JSONArray()
        val n = JSONArray(); for (i in 0 until a.length()) { val it = a.optJSONObject(i); if (it?.optString("id") != s.optString("id")) n.put(it) }
        n.put(JSONObject(s.toString()).put("status", if (accept) "accepted" else "declined").put("at", System.currentTimeMillis()))
        o.put("items", n); save(ctx, o)
    }
    fun undo(ctx: Context, id: String) {
        val plan = PlanStore.data(ctx).optJSONObject("current") ?: return
        val o = store(ctx, plan); val a = o.optJSONArray("items") ?: return
        val n = JSONArray(); for (i in 0 until a.length()) { val it = a.optJSONObject(i); if (it?.optString("id") == id) it.put("status", "declined"); n.put(it) }
        o.put("items", n); save(ctx, o)
    }

    // ── Reading a plan day ──
    private fun kind(t: String): String {
        val first = Insights.headLine(t)?.lowercase() ?: return "none"
        return when {
            Regex("rest\\s*day|no workout|^\\s*rest\\b|:\\s*rest\\b|\\boff\\b").containsMatchIn(first) && Insights.plannedMiles(t) <= 0 -> "rest"
            Regex("\\blong\\b|\\blr\\b|half marathon|\\bmarathon\\b|\\brace\\b").containsMatchIn(first) -> "long"
            Regex("interval|repeat|\\d+\\s*[x×]\\s*\\d|\\bvo2|speed|track|hill repeat|\\bfartlek").containsMatchIn(first) -> "interval"
            Regex("tempo|threshold|\\bt\\b|cruise|\\bmp\\b|marathon pace|progression|steady").containsMatchIn(first) -> "tempo"
            Insights.plannedMiles(t) > 0 -> "easy"
            else -> "other"
        }
    }
    private fun hard(k: String) = k == "long" || k == "interval" || k == "tempo"
    private fun firstLine(t: String) = Insights.headLine(t)?.trim() ?: ""
    /** The day's text with its (first) miles number changed. */
    private fun setMiles(t: String, miles: Double): String {
        val ls = t.lines().toMutableList(); val i = ls.indexOfFirst { it.isNotBlank() && !Insights.isMarker(it) }; if (i < 0) return t
        val head = ls[i]; val cut = if (head.contains(":")) head.indexOf(':') + 1 else 0
        val re = Regex("(\\d+(?:\\.\\d+)?)(\\s*(?:-\\s*\\d+(?:\\.\\d+)?\\s*)?(?:mi|miles?|E|LR|L|MP|M|T|R|easy|long|recovery|rec|steady|tempo|progression)\\b)", RegexOption.IGNORE_CASE)
        val m = re.find(head, cut) ?: return t
        ls[i] = head.substring(0, m.range.first) + mi(miles) + head.substring(m.groups[1]!!.range.last + 1)
        return ls.joinToString("\n")
    }
    private fun mi(d: Double) = if (d == Math.floor(d)) d.toLong().toString() else String.format(Locale.US, "%.1f", d)

    /** Pending suggestions (not yet accepted or declined). */
    fun suggestions(ctx: Context): List<JSONObject> {
        val plan = PlanStore.data(ctx).optJSONObject("current") ?: return emptyList()
        val days = plan.optJSONObject("days") ?: return emptyList()
        val st = store(ctx, plan); val decided = items(st)
        val decidedIds = decided.map { it.optString("id") }.toSet()
        val usedSrc = decided.map { it.optString("src") }.toSet()
        val today = LocalDate.now(); val T = today.toString()
        fun text(d: LocalDate) = days.optString(d.toString())
        fun k(d: LocalDate) = kind(text(d))
        fun done(d: LocalDate) = Insights.doneMiles(ctx, d.toString())
        val dow = PlanEngine.weekDay(plan, T)?.second ?: (today.dayOfWeek.value % 7 + 1)
        val wStart = today.minusDays((dow - 1).toLong()); val wEnd = wStart.plusDays(6)
        val race = PlanEngine.race(plan).let { r -> try { LocalDate.parse(r) } catch (e: Exception) { null } }
        val taper = race != null && !today.isBefore(race.minusDays(21))
        val touched = HashSet<String>()
        val out = ArrayList<JSONObject>()
        fun add(kind: String, src: String, note: String, changes: Map<String, String>) {
            val id = "$kind:$src:" + changes.keys.sorted().joinToString(",")
            if (id in decidedIds || src in usedSrc || changes.keys.any { it in touched } || src in touched) return
            touched += changes.keys; touched += src
            out += JSONObject().put("id", id).put("kind", kind).put("src", src).put("note", note).put("changes", JSONObject(changes))
        }
        /** An easy / rest day from [from] to the end of the week that isn't next to another hard day (other than [except]). */
        fun openDay(from: LocalDate, except: LocalDate? = null): LocalDate? {
            var d = from
            while (!d.isAfter(wEnd)) {
                val dk = k(d)
                if (!hard(dk) && d.toString() !in touched && text(d).isNotEmpty() &&
                    (d.minusDays(1) == except || !hard(k(d.minusDays(1)))) && (d.plusDays(1) == except || !hard(k(d.plusDays(1))))) return d
                d = d.plusDays(1)
            }
            return null
        }

        // ⇄ Low readiness / short sleep on a hard day → swap with the next easy day this week.
        val tk = k(today)
        if (hard(tk) && done(today) <= 0 && Insights.recoveryWarning(ctx) != null) {
            openDay(today.plusDays(1), except = today)?.let { e ->
                add("swap", T, "Low readiness / short sleep today: run ${nice(e.toString())}'s easier day today and today's ${tk} workout on ${nice(e.toString())}.",
                    mapOf(T to "⇄ Swapped with ${nice(e.toString())} (low readiness)\n" + text(e), e.toString() to "⇄ Swapped from ${nice(T)} (low readiness)\n" + text(today)))
            }
        }
        // ↪ / ↘ Missed long or hard workouts in the last 3 days.
        for (back in 1..3) {
            val x = today.minusDays(back.toLong()); val xk = k(x); val pm = Insights.plannedMiles(text(x))
            if (!hard(xk) || done(x) >= pm.coerceAtLeast(1.0) * 0.3) continue
            val slot = if (!x.isBefore(wStart)) openDay(today) else null
            if (slot != null && !(xk == "long" && race != null && !today.isBefore(race.minusDays(7)))) {
                add("move", x.toString(), "Missed ${nice(x.toString())}'s $xk workout — move it to ${nice(slot.toString())} (an easy day this week).",
                    mapOf(slot.toString() to "↪ Moved from ${nice(x.toString())}\n" + text(x) + "\n\n(Was: ${firstLine(text(slot))})"))
            } else if (xk == "long") {
                // Hold next week's long run to ~15% over the longest run in the last 2 weeks.
                val from = today.minusDays(14).toString()
                val longest = ActivityStore.list(ctx).filter { ActivityStore.str(it, "date") >= from && ActivityStore.isRun(ActivityStore.str(it, "type").ifEmpty { "run" }) }.maxOfOrNull { ActivityStore.num(it, "distance") } ?: 0.0
                var d = wEnd.plusDays(1); val end = wEnd.plusDays(7)
                while (!d.isAfter(end)) {
                    if (k(d) == "long") { val lm = Insights.plannedMiles(text(d)); val cap = Math.max(longest + 1, Math.round(longest * 1.15).toDouble())
                        if (longest > 0 && lm > cap) add("shorten", x.toString(), "Missed ${nice(x.toString())}'s long run — hold ${nice(d.toString())}'s long run to ${mi(cap)} mi instead of ${mi(lm)} (about +15% over your longest recent run, ${mi(longest)} mi).",
                            mapOf(d.toString() to "↘ Shortened from ${mi(lm)} to ${mi(cap)} mi (missed long run ${nice(x.toString())})\n" + setMiles(text(d), cap)))
                        break }
                    d = d.plusDays(1)
                }
            }
        }
        // ＋ Short easy run yesterday → carry the missed miles to the next easy day (not in the taper).
        val y = today.minusDays(1); val yp = Insights.plannedMiles(text(y)); val yd = done(y)
        if (!taper && k(y) == "easy" && yp > 0 && yd >= yp * 0.3 && yd < yp * 0.7 && !y.isBefore(wStart)) {
            val carry = Math.min(3.0, Math.round((yp - yd) * 2) / 2.0)
            openDay(today)?.takeIf { k(it) == "easy" && !hard(k(it.plusDays(1))) }?.let { e -> val em = Insights.plannedMiles(text(e))
                if (carry >= 1 && em > 0) add("carry", y.toString(), "Yesterday was ${mi(yd)} of ${mi(yp)} mi — add ${mi(carry)} mi to ${nice(e.toString())}'s easy run.",
                    mapOf(e.toString() to "＋ ${mi(carry)} mi carried from ${nice(y.toString())}\n" + setMiles(text(e), em + carry))) }
        }
        // ▲ Ran well over the plan (today or yesterday): protect the next hard day, and keep the week near what was planned.
        for (back in 0..1) {
            val x = today.minusDays(back.toLong()); val pm = Insights.plannedMiles(text(x)); val dm = done(x)
            if (pm <= 0 || dm < pm * 1.25 || dm - pm < 2) continue
            val extra = dm - pm
            // The next hard workout within 2 days becomes easy.
            for (k in 1..2) { val h = x.plusDays(k.toLong()); if (h.isBefore(today)) continue; val hk = k(h)
                if (hard(hk)) { val m = Insights.plannedMiles(text(h)); val easy = Math.max(3.0, Math.round(m * 0.8).toDouble())
                    add("overHard", "over-${x}", "You ran ${mi(dm)} mi on ${nice(x.toString())} (${mi(pm)} planned). Make ${nice(h.toString())}'s $hk workout an easy ${mi(easy)} mi so you recover.",
                        mapOf(h.toString() to "↘ Easy instead (extra miles ${nice(x.toString())})\n${mi(easy)} mi easy\n\nOriginal: " + text(h))); break } }
            // The week: if done + still planned is >10% over the week's plan, trim the remaining easy days (not long / hard ones).
            var wkPlan = 0.0; var wkDone = 0.0; var d = wStart
            while (!d.isAfter(wEnd)) { wkPlan += Insights.plannedMiles(text(d)); if (!d.isAfter(today)) wkDone += done(d); d = d.plusDays(1) }
            var remaining = 0.0; d = today.plusDays(1); while (!d.isAfter(wEnd)) { remaining += Insights.plannedMiles(text(d)); d = d.plusDays(1) }
            var over = wkDone + remaining - wkPlan * 1.10
            if (over >= 1.5) {
                val ch = LinkedHashMap<String, String>(); val parts = ArrayList<String>()
                d = today.plusDays(1)
                while (!d.isAfter(wEnd) && over >= 0.5) {
                    val ds = d.toString()
                    if (k(d) == "easy" && ds !in touched) { val m = Insights.plannedMiles(text(d)); val cut = Math.min(over, m - 2.0).let { Math.floor(it) }
                        if (cut >= 1) { ch[ds] = "↘ ${mi(cut)} mi less (extra miles ${nice(x.toString())})\n" + setMiles(text(d), m - cut); parts += "${nice(ds)} ${mi(m)}→${mi(m - cut)}"; over -= cut } }
                    d = d.plusDays(1)
                }
                if (ch.isNotEmpty()) add("overWeek", "overwk-${x}", "Your ${mi(extra)} extra miles put this week about ${mi(Math.round((wkDone + remaining - wkPlan) * 10) / 10.0)} mi over plan. Trim easy days to stay near ${mi(Math.round(wkPlan * 1.1 * 10) / 10.0)} mi: " + parts.joinToString(", ") + ".", ch)
            }
            break
        }
        // ↘ 3+ planned run days missed in a row → ease back in.
        var missed = 0; var d0 = today.minusDays(1)
        while (missed < 14) { val pm = Insights.plannedMiles(text(d0)); if (text(d0).isEmpty()) break
            if (pm > 0) { if (done(d0) > 0) break; missed++ }; d0 = d0.minusDays(1) }
        if (missed >= 3) {
            var d = today; val end = today.plusDays(7)
            while (!d.isAfter(end)) { val dk = k(d)
                if (hard(dk)) { val m = Insights.plannedMiles(text(d)); val easy = Math.max(3.0, Math.round(m * 0.8).toDouble())
                    add("ease", "gap-${d0}", "You've missed $missed planned runs in a row — ease back in: make ${nice(d.toString())}'s $dk workout an easy ${mi(easy)} mi.",
                        mapOf(d.toString() to "↘ Easy instead (missed $missed runs)\n${mi(easy)} mi easy\n\nOriginal: " + text(d))); break }
                d = d.plusDays(1) }
        }
        return out
    }
}
