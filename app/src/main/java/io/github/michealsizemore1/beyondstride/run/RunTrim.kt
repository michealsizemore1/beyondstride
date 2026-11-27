package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import io.github.michealsizemore1.beyondstride.RunMath
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.roundToInt

/**
 * ✂️ Trim Run (Nov 26 2026): for when the watch kept recording after the run was over. Cuts the run at a chosen moment
 * (elapsed time) and works everything out again from what's left: distance, moving time, pace, heart rate, power,
 * cadence, NP / intensity / stress, zones, laps, splits, the GPS route and best efforts. The untrimmed run is kept so it
 * can be put back (Undo). The trimmed run replaces the old one on the watch and in Activities.
 *
 * Samples: [moving s, meters*10, hr, speed*100, cadence, power, elapsed s]. Track points: [lat, lon, moving s, elapsed s].
 */
object RunTrim {
    private fun origDir(ctx: Context) = File(ctx.filesDir, "runs_untrimmed").apply { mkdirs() }
    fun hasOriginal(ctx: Context, start: Long) = File(origDir(ctx), "$start.json").exists()

    /** Elapsed seconds of a sample (older runs without it use moving time). */
    fun el(s: JSONArray) = if (s.length() > 6) s.optInt(6) else s.optInt(0)

    /** Where you most likely stopped running: the end of the last stretch at running pace (null if nothing to trim). */
    fun suggest(run: JSONObject): Int? {
        val s = run.optJSONArray("samples") ?: return null
        val n = s.length(); if (n < 60) return null
        val speed = DoubleArray(n) { (s.optJSONArray(it)?.optInt(3) ?: 0) / 100.0 }
        // 30-second average speed; running = faster than ~16:00/mi (1.68 m/s).
        var lastRun = -1; var sum = 0.0
        for (i in 0 until n) { sum += speed[i]; if (i >= 30) sum -= speed[i - 30]; if (i >= 29 && sum / 30 >= 1.68) lastRun = i }
        if (lastRun < 0) return null
        val cut = el(s.getJSONArray(lastRun))
        val end = el(s.getJSONArray(n - 1))
        return if (end - cut >= 60) cut else null
    }

    /** The run cut at [cut] elapsed seconds. Null when too little would be left. */
    fun trimmed(ctx: Context, run: JSONObject, cut: Int): JSONObject? {
        val r = JSONObject(run.toString())
        val all = r.optJSONArray("samples") ?: return null
        val kept = JSONArray(); for (i in 0 until all.length()) { val s = all.optJSONArray(i) ?: continue; if (el(s) <= cut) kept.put(s) }
        if (kept.length() < 10) return null
        val last = kept.getJSONArray(kept.length() - 1)
        val moving = last.optInt(0); val meters = last.optInt(1) / 10.0
        val oldMeters = r.optDouble("distance", 0.0)
        r.put("samples", kept)
        r.optJSONArray("track")?.let { t -> val nt = JSONArray()
            for (i in 0 until t.length()) { val p = t.optJSONArray(i) ?: continue; val pe = if (p.length() > 3) p.optInt(3) else p.optInt(2); if (pe <= cut) nt.put(p) }
            r.put("track", nt) }
        // Averages and maxes from what's left.
        var hrS = 0L; var hrN = 0; var hrMax = 0; var pwS = 0L; var pwN = 0; var pwMax = 0; var cadS = 0L; var cadN = 0
        val cp = r.optInt("cp"); val pb = if (cp > 0) RunMath.powerBounds(cp) else null
        val hb = RunMath.hrBounds(r.optDouble("hrMax", RunSettings.hrMax(ctx)), r.optDouble("hrRest", RunSettings.hrRest(ctx)))
        val zs = IntArray(5); val hz = IntArray(5)
        val win = ArrayDeque<Int>(); var np4 = 0.0; var npN = 0
        for (i in 0 until kept.length()) { val s = kept.getJSONArray(i)
            val hr = s.optInt(2); val pw = s.optInt(5); val cad = s.optInt(4)
            if (hr > 0) { hrS += hr; hrN++; if (hr > hrMax) hrMax = hr; hb.indexOfLast { hr >= it }.takeIf { it >= 0 }?.let { hz[it]++ } }
            if (pw > 0) { pwS += pw; pwN++; if (pw > pwMax) pwMax = pw; pb?.let { b -> b.indexOfLast { pw >= it }.coerceAtLeast(0).let { zs[it]++ } } }
            if (cad > 0) { cadS += cad; cadN++ }
            win.addLast(pw); if (win.size > 30) win.removeFirst()
            if (win.size == 30) { val a = win.average(); np4 += a * a * a * a; npN++ }
        }
        val avgPw = if (pwN > 0) (pwS / pwN).toInt() else 0
        val np = if (npN > 0) Math.pow(np4 / npN, 0.25).roundToInt() else avgPw
        r.put("movingSeconds", moving); r.put("elapsedSeconds", cut); r.put("end", r.optLong("start") + cut * 1000L)
        r.put("distance", meters)
        r.put("avgHr", if (hrN > 0) (hrS / hrN).toInt() else 0); r.put("maxHr", hrMax)
        r.put("avgPower", avgPw); r.put("maxPower", pwMax); r.put("np", np)
        r.put("avgCadence", if (cadN > 0) (cadS / cadN).toInt() else 0)
        r.put("hrZoneSeconds", JSONArray(hz.toList()))
        if (cp > 0) { val inten = if (np > 0) np.toDouble() / cp else 0.0
            r.put("intensity", inten); r.put("stress", (moving / 3600.0 * inten * inten * 100).roundToInt()); r.put("zoneSeconds", JSONArray(zs.toList())) }
        // Climb: the barometer total isn't kept second by second, so it's cut in proportion to the distance kept.
        if (oldMeters > 0 && meters < oldMeters) listOf("elevGain", "elevLoss").forEach { k ->
            r.optDouble(k).takeIf { !it.isNaN() }?.let { r.put(k, Math.round(it * meters / oldMeters * 10) / 10.0) } }
        // Laps: whole laps before the cut stay; the one the cut lands in is shortened.
        r.optJSONArray("laps")?.let { laps -> val nl = JSONArray(); var acc = 0
            for (i in 0 until laps.length()) { val l = laps.getJSONObject(i); val sec = l.optInt("sec")
                if (acc + sec <= moving) { nl.put(l); acc += sec; continue }
                val part = (0 until kept.length()).map { kept.getJSONArray(it) }.filter { it.optInt(0) > acc }
                if (moving - acc >= 5 && part.isNotEmpty()) {
                    val m0 = (0 until kept.length()).map { kept.getJSONArray(it) }.lastOrNull { it.optInt(0) <= acc }?.optInt(1)?.div(10.0) ?: 0.0
                    fun avg(ix: Int) = part.map { it.optInt(ix) }.filter { it > 0 }.let { if (it.isEmpty()) 0 else it.average().toInt() }
                    nl.put(JSONObject(l.toString()).put("sec", moving - acc).put("m", meters - m0).put("hr", avg(2)).put("pw", avg(5)).put("cad", avg(4)))
                }
                break }
            r.put("laps", nl) }
        // Heartbeat intervals (ms): keep the first [cut] seconds' worth.
        r.optJSONArray("rr")?.let { rr -> val nr = JSONArray(); var t = 0L
            for (i in 0 until rr.length()) { t += rr.optLong(i); if (t > cut * 1000L) break; nr.put(rr.opt(i)) }
            r.put("rr", nr) }
        // Best efforts (critical power) from what's left.
        r.remove("mmp")
        try { io.github.michealsizemore1.beyondstride.CriticalPower.bests(r).takeIf { it.isNotEmpty() }?.let { b ->
            r.put("mmp", JSONObject().apply { b.forEach { (k, v) -> put("$k", v) } }) } } catch (e: Exception) { }
        val prev = run.optJSONObject("trimmed")
        r.put("trimmed", JSONObject().put("at", System.currentTimeMillis()).put("cut", cut)
            .put("origElapsed", prev?.optInt("origElapsed") ?: run.optInt("elapsedSeconds"))
            .put("origDistance", prev?.optDouble("origDistance") ?: oldMeters)
            .put("origMoving", prev?.optInt("origMoving") ?: run.optInt("movingSeconds")))
        r.remove("untrimmed")
        return r
    }

    /** Saves the trimmed run (keeping the original once) and updates Activities, the watch and today's totals. */
    fun apply(ctx: Context, start: Long, cut: Int): Boolean {
        val run = PhoneRuns.read(ctx, start) ?: return false
        val t = trimmed(ctx, run, cut) ?: return false
        val orig = File(origDir(ctx), "$start.json")
        if (!orig.exists()) orig.writeText(run.toString())
        PhoneRuns.save(ctx, t)
        afterChange(ctx, start)
        return true
    }

    /** Puts the untrimmed run back. */
    fun undo(ctx: Context, start: Long): Boolean {
        val orig = File(origDir(ctx), "$start.json"); if (!orig.exists()) return false
        val o = try { JSONObject(orig.readText()) } catch (e: Exception) { return false }
        // Keep anything added since (weather, Stryd details, a renamed title).
        PhoneRuns.read(ctx, start)?.let { cur -> listOf("weather", "stryd", "name").forEach { k -> if (cur.has(k)) o.put(k, cur.get(k)) } }
        o.put("untrimmed", System.currentTimeMillis()); o.remove("trimmed")
        PhoneRuns.save(ctx, o); orig.delete()
        afterChange(ctx, start)
        return true
    }

    private fun afterChange(ctx: Context, start: Long) {
        val app = ctx.applicationContext
        Thread {
            // Activities: new numbers; the notes too, unless you've written your own.
            try {
                PhoneRuns.read(app, start)?.let { run ->
                    val act = CommandCenter.activity(app, run)
                    val cur = ActivityStore.list(app).firstOrNull { ActivityStore.idOf(it) == ActivityStore.idStr(act.opt("id")) }
                    if (cur != null) {
                        val upd = JSONObject().put("id", act.get("id"))
                        listOf("distance", "duration", "pace", "power", "heartRate", "cadence", "execScore", "elevGain").forEach { k -> if (act.has(k)) upd.put(k, act.get(k)) }
                        if (ActivityStore.str(cur, "notes").let { it.isEmpty() || it.trimEnd().endsWith(")") && it.contains("Recorded on Galaxy Watch") }) upd.put("notes", act.optString("notes"))
                        ActivityStore.save(app, upd)
                    }
                }
            } catch (e: Exception) { }
            // The watch's copy (it replaces its own when the run says it was trimmed or put back).
            try {
                val f = PhoneRuns.file(app, start)
                val bytes = java.io.ByteArrayOutputStream().also { bo -> java.util.zip.GZIPOutputStream(bo).use { it.write(f.readBytes()) } }.toByteArray()
                val req = com.google.android.gms.wearable.PutDataMapRequest.create("/torun/$start").apply {
                    dataMap.putAsset("run", com.google.android.gms.wearable.Asset.createFromBytes(bytes)); dataMap.putLong("at", System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
                com.google.android.gms.tasks.Tasks.await(com.google.android.gms.wearable.Wearable.getDataClient(app).putDataItem(req))
            } catch (e: Exception) { }
            try { PhoneStatus.push(app) } catch (e: Exception) { }
            try { DailySummary.publish(app, force = true) } catch (e: Exception) { }
            try { CloudCopy.dirty(app) } catch (e: Exception) { }
            try { Backup.autoUpdate(app) } catch (e: Exception) { }
        }.start()
    }
}
