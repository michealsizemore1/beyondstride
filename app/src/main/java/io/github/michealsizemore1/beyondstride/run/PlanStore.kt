package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import io.github.michealsizemore1.beyondstride.Hydration
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Training plans shared by the Command Center (<db>/nutrition/<key>/plans): the active plan with each day's workout,
 * the upcoming plan and archived plans. Buttons tapped here are sent as "planAction" changes and the Command Center
 * runs its own button for them; the simple ones show here at once.
 */
object PlanStore {
    private fun p(ctx: Context) = ctx.getSharedPreferences("plans", 0)
    private fun raw(ctx: Context): JSONObject = try { JSONObject(p(ctx).getString("data", "{}")) } catch (e: Exception) { JSONObject() }

    fun pending(ctx: Context) = Hydration.changes(ctx).filter { it.f == "planAction" }.sortedBy { it.at }

    private fun keyOf(which: String) = if (which == "upcoming") "upcoming" else "current"
    private fun today() = java.time.LocalDate.now().toString()
    private fun archDate() = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.US))

    /** The plans as shown: the Command Center's copy with changes made here (not yet applied there) on top. */
    fun data(ctx: Context): JSONObject {
        if (Standalone.on(ctx)) return localView(ctx)
        val o = raw(ctx)
        val arch = o.optJSONArray("archived") ?: JSONArray().also { o.put("archived", it) }
        fun archive(plan: JSONObject, from: String) {
            val a = JSONObject(plan.toString()).put("archivedAt", archDate()).put("archivedFrom", from); a.remove("days")
            val n = JSONArray().put(a); for (i in 0 until arch.length()) n.put(arch.get(i)); o.put("archived", n)
        }
        pending(ctx).forEach { c ->
            val x = try { JSONObject(c.x ?: "{}") } catch (e: Exception) { return@forEach }
            val k = keyOf(x.optString("which"))
            val archNow = o.optJSONArray("archived") ?: JSONArray()
            when (x.optString("action")) {
                "delete" -> o.remove(k)
                "archive" -> o.optJSONObject(k)?.let { archive(it, k); o.remove(k) }
                "activate" -> o.optJSONObject("upcoming")?.let { up -> o.optJSONObject("current")?.let { archive(it, "current") }; o.put("current", up); o.remove("upcoming") }
                "setText" -> o.optJSONObject(k)?.let { it.put("text", x.optString("text")).put("display", x.optString("text")); if (x.optBoolean("adjusted")) it.put("adjustedAt", archDate()) }
                "deleteArchived", "restore" -> {
                    val n = JSONArray(); var hit: JSONObject? = null
                    for (i in 0 until archNow.length()) { val a = archNow.optJSONObject(i) ?: continue
                        if (hit == null && a.optString("archivedAt") == x.optString("archivedAt") && a.optString("text").take(200) == x.optString("head")) hit = a else n.put(a) }
                    o.put("archived", n)
                    if (x.optString("action") == "restore") hit?.let { o.put(if (it.optString("archivedFrom") == "upcoming") "upcoming" else "current", it) }
                }
                "collapse" -> o.put("collapsed", x.optBoolean("on"))
                "startDate" -> o.optJSONObject("current")?.put("startDate", x.optString("date"))
                "raceDate" -> o.optJSONObject("current")?.let { it.put("raceDate", x.optString("date")).put("race", x.optString("date")) }
            }
        }
        return o
    }

    fun action(ctx: Context, x: JSONObject) {
        if (Standalone.on(ctx)) { localDo(ctx, x); return }
        Hydration.queue(ctx, "planAction", x)
        Thread { HydrationSync.flush(ctx.applicationContext) }.start()
    }

    fun fetchedAt(ctx: Context) = p(ctx).getLong("at", 0L)

    /** Reads the Command Center's plans. Call off the main thread (after ActivityStore.refresh, which reconciles). */
    fun refresh(ctx: Context): Boolean {
        if (Standalone.on(ctx) || !NutritionSync.ready(ctx)) return false
        return try {
            val c = (URL(Live.db(ctx) + "/nutrition/" + NutritionSync.key(ctx) + "/plans.json").openConnection() as HttpURLConnection)
                .apply { connectTimeout = 12_000; readTimeout = 25_000 }
            if (c.responseCode != 200) return false
            val txt = c.inputStream.bufferedReader().readText()
            if (txt == "null" || txt.isBlank()) return false
            val changed = txt != p(ctx).getString("data", "")
            p(ctx).edit().putString("data", txt).putLong("at", System.currentTimeMillis()).apply()
            changed
        } catch (e: Exception) { false }
    }

    /** Today's workout text from the active plan, or null when the card is hidden (no plan / before its start). */
    fun workoutFor(plan: JSONObject, date: String): String =
        plan.optJSONObject("days")?.optString(date)?.takeIf { it.isNotEmpty() } ?: "Rest Day / No workout scheduled."

    /** Head of an archived plan, to find the same one in the Command Center. */
    fun head(a: JSONObject) = a.optString("text").take(200)

    /** The plan's dated workouts from today on, for "Send to watch" — same as the Command Center's "Send plan to BeyondStride". */
    fun upcomingDays(plan: JSONObject): JSONObject {
        val out = JSONObject(); val t = today()
        plan.optJSONObject("schedule")?.let { s -> s.keys().forEach { d -> if (d >= t && s.optString(d).isNotEmpty()) out.put(d, s.optString(d)) } }
        return out
    }

    // ── Stand-alone: the plans live here and every button runs here (PlanEngine), same as the Command Center's ──
    private fun local(ctx: Context): JSONObject = try { JSONObject(p(ctx).getString("local", "{}")) } catch (e: Exception) { JSONObject() }
    private fun saveLocal(ctx: Context, o: JSONObject) { p(ctx).edit().putString("local", o.toString()).putLong("localAt", System.currentTimeMillis()).apply(); daysKey = ""; viewCache = null; CloudCopy.dirty(ctx) }
    fun localJson(ctx: Context): JSONObject = local(ctx)
    fun setLocalJson(ctx: Context, o: JSONObject) = saveLocal(ctx, o)

    /** At cut-over: the Command Center's last copy (with anything still pending) becomes BeyondStride's own. */
    fun snapshot(ctx: Context) {
        val o = JSONObject(dataShared(ctx).toString())
        listOf("current", "upcoming").forEach { k -> o.optJSONObject(k)?.let { pl -> pl.remove("days"); if (pl.optJSONObject("schedule")?.length() ?: 0 == 0) PlanEngine.rebuild(pl) } }
        saveLocal(ctx, o)
    }
    private fun dataShared(ctx: Context): JSONObject { val on = Standalone.on(ctx); return if (!on) data(ctx) else raw(ctx) }

    @Volatile private var daysKey = ""; @Volatile private var daysCache: JSONObject? = null
    @Volatile private var viewKey = ""; @Volatile private var viewCache: JSONObject? = null
    private fun localView(ctx: Context): JSONObject {
        val str = p(ctx).getString("local", "{}") ?: "{}"
        val vk = "${str.length}.${str.hashCode()}.${java.time.LocalDate.now()}.$daysKey"
        viewCache?.let { if (vk == viewKey) return it }
        val o = local(ctx)
        // Once: plans copied from the Command Center get a schedule built with the current rules (each day stops at the
        // next "Week N" heading), and a dated plan starts on its first dated day (so the header says the right week).
        if (!p(ctx).getBoolean("fixed2", false)) {
            listOf("current", "upcoming").forEach { k -> o.optJSONObject(k)?.let { pl ->
                PlanEngine.rebuild(pl)
                if (pl.optString("scheduleMode") == "dated") pl.optJSONObject("schedule")?.keys()?.asSequence()?.minOrNull()?.let { first ->
                    if (pl.optString("startDate").isEmpty() || first > pl.optString("startDate")) pl.put("startDate", first) }
            } }
            p(ctx).edit().putBoolean("fixed2", true).apply(); saveLocal(ctx, o); return localView(ctx)
        }
        o.optJSONObject("current")?.let { pl ->
            val k = pl.optString("text").hashCode().toString() + pl.optString("startDate") + pl.optJSONObject("schedule")?.length() + java.time.LocalDate.now()
            if (k != daysKey || daysCache == null) { daysCache = PlanEngine.days(pl); daysKey = k }
            pl.put("days", daysCache).put("race", PlanEngine.race(pl))
        }
        o.optJSONObject("upcoming")?.let { it.put("race", PlanEngine.race(it)) }
        viewCache = o; viewKey = "${str.length}.${str.hashCode()}.${java.time.LocalDate.now()}.$daysKey"
        return o
    }

    private fun localDo(ctx: Context, x: JSONObject) {
        val o = local(ctx); val k = keyOf(x.optString("which"))
        val arch = o.optJSONArray("archived") ?: JSONArray()
        fun archive(plan: JSONObject, from: String) {
            val a = JSONObject(plan.toString()).put("archivedAt", archDate()).put("archivedFrom", from); a.remove("days")
            val n = JSONArray().put(a); for (i in 0 until arch.length()) n.put(arch.get(i)); o.put("archived", n)
        }
        var msg: String? = null
        when (x.optString("action")) {
            "delete" -> o.remove(k)
            "archive" -> o.optJSONObject(k)?.let { archive(it, k); o.remove(k) }
            "activate" -> o.optJSONObject("upcoming")?.let { up -> o.optJSONObject("current")?.let { archive(it, "current") }
                // makeUpcomingPlanActive: a weekly plan starts today; a dated one keeps its dates.
                if (up.optString("scheduleMode") != "dated") { up.put("startDate", today()); PlanEngine.rebuild(up) }
                o.put("current", up); o.remove("upcoming") }
            "setText" -> o.optJSONObject(k)?.let { it.put("text", x.optString("text")).put("display", x.optString("text")); if (x.optBoolean("adjusted")) it.put("adjustedAt", archDate()); PlanEngine.rebuild(it) }
            "deleteArchived", "restore" -> {
                val n = JSONArray(); var hit: JSONObject? = null
                for (i in 0 until arch.length()) { val a = arch.optJSONObject(i) ?: continue
                    if (hit == null && a.optString("archivedAt") == x.optString("archivedAt") && head(a) == x.optString("head")) hit = a else n.put(a) }
                o.put("archived", n)
                if (x.optString("action") == "restore") hit?.let { h -> val t = if (h.optString("archivedFrom") == "upcoming") "upcoming" else "current"
                    h.remove("archivedAt"); h.remove("archivedFrom"); o.put(t, h) }
            }
            "collapse" -> o.put("collapsed", x.optBoolean("on"))
            "startDate" -> o.optJSONObject("current")?.let { it.put("startDate", x.optString("date")); if (it.optString("scheduleMode") != "dated") PlanEngine.rebuild(it) }
            "raceDate" -> o.optJSONObject("current")?.let { it.put("raceDate", x.optString("date")).put("raceDateConfirmed", true) }
            "zones" -> o.optJSONObject(k)?.let { val st = ActivityStore.settings(ctx)
                msg = PlanEngine.addZones(it, st.optDouble("maxHr", 0.0).takeIf { v -> v > 0 } ?: 174.0, st.optDouble("restHr", 0.0).takeIf { v -> v > 0 } ?: 55.0) }
            "dates" -> o.optJSONObject(k)?.let { msg = PlanEngine.addDates(it) }
            "upload" -> {
                val isPdf = x.has("pdf")
                val text = if (isPdf) PdfText.extract(ctx, android.util.Base64.decode(x.optString("pdf"), 0))?.let { PlanEngine.reflow(it) } else x.optString("text")
                val plan = text?.let { PlanEngine.fromText(it, x.optString("startDate").ifEmpty { today() }, isPdf) }
                if (plan == null) msg = if (isPdf) "No text could be read from that PDF (it may be a scanned image). Save it as a .txt file instead." else "The selected file is empty."
                else { val t = if (x.optBoolean("upcoming")) "upcoming" else "current"; o.optJSONObject(t)?.let { archive(it, t) }; o.put(t, plan)
                    msg = "Loaded ${x.optString("name")} as your ${if (t == "upcoming") "upcoming" else "active"} plan" + if (plan.optInt("weeks") > 0) " (${plan.optInt("weeks")} weeks)" else "" }
            }
        }
        saveLocal(ctx, o)
        lastMessage = msg
    }
    /** What the last button did here (shown as a toast), when there's something to say. */
    @Volatile var lastMessage: String? = null
}
