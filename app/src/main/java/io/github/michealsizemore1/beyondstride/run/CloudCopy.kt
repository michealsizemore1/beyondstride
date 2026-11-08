package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * BeyondStride's automatic cloud copy, in its own place in your Firebase database (<db>/beyondstride/<key>/),
 * separate from anything the Command Center uses:
 *  - copy:   everything except runs & courses (nutrition days and history, foods, activities, plans, settings…),
 *            as the backup zip in pieces — re-sent a few minutes after anything changes;
 *  - files/: each run and course once (they don't change).
 * On a new phone: set up the same database, enter the cloud copy code, and tap Restore from cloud.
 */
object CloudCopy {
    private fun p(ctx: Context) = ctx.getSharedPreferences("cloudcopy", 0)
    private const val CHUNK = 3_000_000

    fun dirty(ctx: Context) = p(ctx).edit().putBoolean("dirty", true).apply()
    fun lastAt(ctx: Context) = p(ctx).getLong("at", 0L)
    fun lastInfo(ctx: Context) = p(ctx).getString("info", "") ?: ""
    fun ready(ctx: Context) = NutritionSync.ready(ctx)
    private fun base(ctx: Context) = Live.db(ctx) + "/beyondstride/" + NutritionSync.key(ctx)

    private fun http(url: String, method: String = "GET", body: String? = null): String? = try {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 15_000; readTimeout = 60_000
            if (body != null) { doOutput = true; setRequestProperty("Content-Type", "application/json") }
        }
        body?.let { b -> c.outputStream.use { it.write(b.toByteArray()) } }
        if (c.responseCode in 200..299) c.inputStream.bufferedReader().readText() else null
    } catch (e: Exception) { null }

    private fun safe(name: String) = name.replace(Regex("[.$#\\[\\]/]"), "_")

    /** Sends what changed. [force] sends the data copy even if nothing is marked changed. Call off the main thread. */
    @Synchronized fun sync(ctx: Context, force: Boolean = false): Boolean {
        if (!ready(ctx) || !Standalone.on(ctx)) return false
        val pr = p(ctx); var ok = true
        val stale = System.currentTimeMillis() - pr.getLong("at", 0L) > 24 * 3600_000L
        if (force || stale || pr.getBoolean("dirty", false)) {
            pr.edit().putBoolean("dirty", false).apply()
            val bos = ByteArrayOutputStream(); Backup.writeTo(ctx, bos, dirs = false)
            val b64 = android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP)
            val parts = JSONArray(); var i = 0; while (i < b64.length) { parts.put(b64.substring(i, Math.min(b64.length, i + CHUNK))); i += CHUNK }
            val info = Backup.info(ctx, java.io.File(ctx.filesDir, "runs").listFiles()?.size ?: 0, java.io.File(ctx.filesDir, "courses").listFiles()?.size ?: 0)
            val doc = JSONObject().put("at", System.currentTimeMillis()).put("version", Backup.VERSION).put("parts", parts).put("info", info)
            if (http(base(ctx) + "/copy.json", "PUT", doc.toString()) != null) pr.edit().putLong("at", System.currentTimeMillis()).putString("info", info).apply()
            else { ok = false; pr.edit().putBoolean("dirty", true).apply() }
        }
        // Runs and courses: each one once.
        val sent = HashSet(pr.getStringSet("sent", emptySet()) ?: emptySet())
        listOf("runs", "courses").forEach { d ->
            java.io.File(ctx.filesDir, d).listFiles()?.filter { it.isFile }?.forEach { f ->
                val id = "$d/${f.name}:${f.length()}"; if (id in sent) return@forEach
                val gz = ByteArrayOutputStream().also { o -> java.util.zip.GZIPOutputStream(o).use { it.write(f.readBytes()) } }.toByteArray()
                val doc = JSONObject().put("dir", d).put("name", f.name).put("gz", android.util.Base64.encodeToString(gz, android.util.Base64.NO_WRAP))
                if (http(base(ctx) + "/files/" + safe("$d/${f.name}") + ".json", "PUT", doc.toString()) != null) sent += id else ok = false
            }
        }
        pr.edit().putStringSet("sent", sent).apply()
        return ok
    }

    /** What's in the cloud copy (when, summary), or null. Call off the main thread. */
    fun peek(ctx: Context): JSONObject? {
        if (!ready(ctx)) return null
        val at = http(base(ctx) + "/copy/at.json")?.toLongOrNull() ?: return null
        val info = http(base(ctx) + "/copy/info.json")?.let { try { JSONObject("{\"v\":$it}").optString("v") } catch (e: Exception) { "" } } ?: ""
        return JSONObject().put("at", at).put("info", info)
    }

    /** Restores everything from the cloud copy onto this phone (replacing its data). Call off the main thread. */
    fun restore(ctx: Context): String? {
        if (!ready(ctx)) return null
        val doc = http(base(ctx) + "/copy.json")?.takeIf { it != "null" }?.let { JSONObject(it) } ?: return null
        val parts = doc.optJSONArray("parts") ?: return null
        val b64 = StringBuilder(); for (i in 0 until parts.length()) b64.append(parts.optString(i))
        val zip = android.util.Base64.decode(b64.toString(), 0)
        val msg = Backup.restoreFrom(ctx, zip.inputStream(), push = false)
        var files = 0
        val names = http(base(ctx) + "/files.json?shallow=true")?.takeIf { it != "null" }?.let { JSONObject(it).keys().asSequence().toList() } ?: emptyList()
        val sent = HashSet<String>()
        names.forEach { k ->
            val o = http(base(ctx) + "/files/$k.json")?.takeIf { it != "null" }?.let { JSONObject(it) } ?: return@forEach
            val d = o.optString("dir"); val n = o.optString("name")
            if (d !in listOf("runs", "courses") || n.isEmpty() || n.contains("/") || n.contains("..")) return@forEach
            val bytes = java.util.zip.GZIPInputStream(android.util.Base64.decode(o.optString("gz"), 0).inputStream()).use { it.readBytes() }
            val f = java.io.File(java.io.File(ctx.filesDir, d).apply { mkdirs() }, n); f.writeBytes(bytes); files++
            sent += "$d/$n:${f.length()}"
        }
        p(ctx).edit().putStringSet("sent", sent).putLong("at", doc.optLong("at")).putString("info", doc.optString("info")).apply()
        Backup.pushAll(ctx)
        return "Restored from the cloud copy · ${doc.optString("info").ifEmpty { msg }} · $files runs & courses"
    }
}
