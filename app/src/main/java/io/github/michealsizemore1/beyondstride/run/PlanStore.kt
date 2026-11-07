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
        Hydration.queue(ctx, "planAction", x)
        Thread { HydrationSync.flush(ctx.applicationContext) }.start()
    }

    fun fetchedAt(ctx: Context) = p(ctx).getLong("at", 0L)

    /** Reads the Command Center's plans. Call off the main thread (after ActivityStore.refresh, which reconciles). */
    fun refresh(ctx: Context): Boolean {
        if (!NutritionSync.ready(ctx)) return false
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
}
