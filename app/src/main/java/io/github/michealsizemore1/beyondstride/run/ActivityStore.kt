package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import io.github.michealsizemore1.beyondstride.Hydration
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The Command Center's Activities (shared to Firebase by its page): every activity, each card's extra lines
 * (zones, heat, sleep, previous-day water / food / fuel impact) and its CP / max HR / resting HR.
 * Changes made here are shown at once and sent to the Command Center (which keeps the real list).
 */
object ActivityStore {
    private fun p(ctx: Context) = ctx.getSharedPreferences("activities", 0)
    private fun raw(ctx: Context): JSONObject = try { JSONObject(p(ctx).getString("data", "{}")) } catch (e: Exception) { JSONObject() }

    fun fetchedAt(ctx: Context) = p(ctx).getLong("at", 0)

    /** Every activity: the Command Center's list with changes made here (not taken by it yet) on top. */
    fun list(ctx: Context): List<JSONObject> {
        val a = raw(ctx).optJSONArray("list") ?: JSONArray()
        val out = (0 until a.length()).mapNotNull { a.optJSONObject(it) }.toMutableList()
        Hydration.changes(ctx).filter { it.f == "actSave" || it.f == "actDel" }.sortedBy { it.at }.forEach { c ->
            val x = try { JSONObject(c.x ?: "{}") } catch (e: Exception) { return@forEach }
            val id = x.opt("id")?.toString() ?: return@forEach
            val i = out.indexOfFirst { it.opt("id")?.toString() == id }
            if (c.f == "actDel") { if (i >= 0) out.removeAt(i) }
            else {
                val act = x.optJSONObject("act") ?: return@forEach
                if (i < 0) out += JSONObject(act.toString())
                else { val keep = out[i]; act.keys().forEach { k -> keep.put(k, act.get(k)) }; x.optJSONArray("clear")?.let { cl -> for (j in 0 until cl.length()) keep.remove(cl.optString(j)) } }
            }
        }
        return out
    }

    fun extras(ctx: Context, id: Any?): JSONObject = raw(ctx).optJSONObject("extras")?.optJSONObject(id.toString()) ?: JSONObject()
    fun settings(ctx: Context): JSONObject = raw(ctx).optJSONObject("settings") ?: JSONObject()

    /** Saves an activity (new or changed). [clear]: fields emptied. */
    fun save(ctx: Context, act: JSONObject, clear: List<String> = emptyList()) {
        Hydration.queue(ctx, "actSave", JSONObject().put("id", act.get("id")).put("act", act).put("clear", JSONArray(clear)))
        Thread { HydrationSync.flush(ctx.applicationContext) }.start()
    }

    fun delete(ctx: Context, id: Any) {
        Hydration.queue(ctx, "actDel", JSONObject().put("id", id))
        Thread { HydrationSync.flush(ctx.applicationContext) }.start()
    }

    /** Reads the Command Center's Activities. Call off the main thread. */
    fun refresh(ctx: Context): Boolean {
        if (!NutritionSync.ready(ctx)) return false
        HydrationSync.reconcile(ctx)
        return try {
            val c = (URL(Live.db(ctx) + "/nutrition/" + NutritionSync.key(ctx) + "/activities.json").openConnection() as HttpURLConnection)
                .apply { connectTimeout = 12_000; readTimeout = 25_000 }
            if (c.responseCode != 200) return false
            val txt = c.inputStream.bufferedReader().readText()
            if (txt == "null" || txt.isBlank()) return false
            val changed = txt != p(ctx).getString("data", "")
            p(ctx).edit().putString("data", txt).putLong("at", System.currentTimeMillis()).apply()
            changed
        } catch (e: Exception) { false }
    }

    // ── Helpers matching the Command Center ──
    fun num(a: JSONObject, k: String) = a.opt(k)?.toString()?.toDoubleOrNull() ?: 0.0
    fun str(a: JSONObject, k: String) = a.opt(k)?.toString()?.takeIf { it != "null" } ?: ""

    /** "1:32:15" / "32:15" / "32 min" → seconds. */
    fun durSec(s: String): Int {
        val t = s.trim(); if (t.isEmpty()) return 0
        val parts = t.split(":").map { it.trim().toDoubleOrNull() ?: return 0 }
        return when (parts.size) { 3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]).toInt(); 2 -> (parts[0] * 60 + parts[1]).toInt()
            else -> Regex("(\\d+(?:\\.\\d+)?)").find(t)?.groupValues?.get(1)?.toDoubleOrNull()?.let { (it * 60).toInt() } ?: 0 }
    }
    /** "1h 32m 15s" like the Command Center's fmtDuration. */
    fun fmtDur(s: String): String {
        val tot = if (s.contains(":")) durSec(s) else 0
        if (tot <= 0) return s
        val h = tot / 3600; val m = (tot % 3600) / 60; val sec = tot % 60
        return listOfNotNull(if (h > 0) "${h}h" else null, if (m > 0) "${m}m" else null, if (sec > 0) "${sec}s" else null).joinToString(" ")
    }
    fun paceSec(s: String): Int {
        val t = s.trim().replace("--", ""); if (t.isEmpty()) return 0
        Regex("(\\d+):(\\d{1,2})").find(t)?.let { return it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt() }
        return t.toDoubleOrNull()?.takeIf { it > 0 }?.let { (it * 60).toInt() } ?: 0
    }
    fun isRun(type: String) = type.lowercase().let { it.contains("run") || it in listOf("treadmill", "treadmill running") }

    val TYPE_COLOR = mapOf("Run" to 0xFFE67E22.toInt(), "Ride" to 0xFF8E44AD.toInt(), "Walk" to 0xFF27AE60.toInt(), "Swim" to 0xFF2980B9.toInt(),
        "Strength" to 0xFFE74C3C.toInt(), "Other" to 0xFF95A5A6.toInt())
    val SOURCE_LABEL = mapOf("manual" to "Manual", "garmin" to "Garmin", "stryd" to "Stryd", "strava" to "Strava", "gear2" to "Gear Tracker II",
        "screenshot" to "Screenshot", "watch" to "BeyondStride")
    val SOURCE_COLOR = mapOf("garmin" to 0xFF00B2E3.toInt(), "strava" to 0xFFFC4C02.toInt(), "stryd" to 0xFFFF6B35.toInt(), "gear2" to 0xFF16A085.toInt(),
        "watch" to 0xFF667EEA.toInt(), "screenshot" to 0xFF6B7280.toInt(), "manual" to 0xFF888888.toInt())
    fun sourceLabel(s: String) = SOURCE_LABEL[s.ifEmpty { "manual" }.lowercase()] ?: s.replaceFirstChar { it.uppercase() }

    /** Calories when left blank — the Command Center's estimate (ACSM running / walking, cycling power, METs). */
    fun estimateCalories(type: String, distMi: Double, duration: String, power: Double, elevFt: Double, weightLb: Double): Int {
        val hours = durSec(duration) / 3600.0; if (hours <= 0 || type == "Rest Day") return 0
        val kg = (if (weightLb > 0) weightLb else 160.0) * 0.453592
        val mph = if (distMi > 0) distMi / hours else 0.0
        var met = 5.0
        when (type) {
            "Run", "Walk" -> {
                val mmin = mph * 26.8224; val distM = distMi * 1609.34; val grade = if (distM > 0) elevFt * 0.3048 / distM else 0.0
                met = if (mmin > 0) (if (type == "Run") 0.2 * mmin + 0.9 * mmin * grade + 3.5 else 0.1 * mmin + 1.8 * mmin * grade + 3.5) / 3.5 else if (type == "Run") 8.0 else 3.5
            }
            "Ride" -> { if (power > 0) return Math.round(power * hours * 3.6).toInt()
                met = if (mph > 0) (if (mph < 10) 4.0 else if (mph < 14) 6.0 else if (mph < 16) 8.0 else if (mph < 20) 10.0 else 12.0) else 8.0 }
            "Swim" -> met = 7.0
        }
        return Math.max(0, Math.round(met * kg * hours).toInt())
    }
}
