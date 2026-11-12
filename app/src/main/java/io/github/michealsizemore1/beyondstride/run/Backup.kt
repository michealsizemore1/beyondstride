package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import android.net.Uri
import io.github.michealsizemore1.beyondstride.ReadinessSync
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backup of everything BeyondStride keeps on the phone, as one .zip file the person picks a place for
 * (Google Drive, Downloads, …): runs, workouts & plan, saved courses, settings (zones, CP, LiveTrack…),
 * morning checks, sleep and personal bests. The watch gets its data back from the phone after a restore.
 * The same file will move everything into the stand-alone BeyondStride app.
 */
object Backup {
    const val VERSION = 2
    /** BeyondStride settings files. */
    private val PREFS = listOf("runsettings", "readiness", "runflags", "trailsel",
        // Nutrition (recent days, food photos & favorites), Activities, training plans, reminders — the Anthropic key is never included.
        "nutrition", "foodlib", "activities", "plans", "reminders", "foodlog", "activitiesUi", "standalone", "nutritionEngine", "hcimport", "nutHistory", "planadjust", "hcweight", "mynotes", "evening", "coach")
    /** Folders and files in the app's storage. */
    private val DIRS = listOf("runs", "courses")
    private val FILES = listOf("workout_library.json", "workout_plan.json", "nutrition_history.json")

    private fun meta(ctx: Context) = ctx.getSharedPreferences("backup", 0)
    fun uri(ctx: Context): Uri? = meta(ctx).getString("uri", null)?.let { Uri.parse(it) }
    fun lastAt(ctx: Context) = meta(ctx).getLong("at", 0L)
    fun lastInfo(ctx: Context) = meta(ctx).getString("info", "") ?: ""
    fun auto(ctx: Context) = meta(ctx).getBoolean("auto", true)
    fun setAuto(ctx: Context, on: Boolean) = meta(ctx).edit().putBoolean("auto", on).apply()
    fun fileName() = "BeyondStride-backup-" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) + ".zip"

    /** Remember the chosen file so later backups (and automatic ones) update it. */
    fun remember(ctx: Context, uri: Uri) {
        try {
            ctx.contentResolver.takePersistableUriPermission(uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (e: Exception) { }
        meta(ctx).edit().putString("uri", uri.toString()).apply()
    }

    private fun prefsJson(ctx: Context): JSONObject {
        val out = JSONObject()
        PREFS.forEach { name ->
            val o = JSONObject()
            ctx.getSharedPreferences(name, 0).all.forEach { (k, v) ->
                val t = when (v) { is Boolean -> "b"; is Int -> "i"; is Long -> "l"; is Float -> "f"; is String -> "s"; is Set<*> -> "set"; else -> return@forEach }
                o.put(k, JSONObject().put("t", t).put("v", if (v is Set<*>) JSONArray(v.toList()) else v))
            }
            out.put(name, o)
        }
        return out
    }

    /** Writes the backup zip to [os] (with or without the runs / courses folders). Returns (runs, courses). */
    fun writeTo(ctx: Context, os: java.io.OutputStream, dirs: Boolean = true): Pair<Int, Int> {
        var runs = 0; var courses = 0
        ZipOutputStream(os.buffered()).use { z ->
            fun put(name: String, bytes: ByteArray) { z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry() }
            if (dirs) DIRS.forEach { d ->
                File(ctx.filesDir, d).listFiles()?.filter { it.isFile }?.forEach { f ->
                    put("$d/${f.name}", f.readBytes()); if (d == "runs") runs++ else courses++
                }
            }
            FILES.forEach { n -> File(ctx.filesDir, n).takeIf { it.exists() }?.let { put(n, it.readBytes()) } }
            put("prefs.json", prefsJson(ctx).toString().toByteArray())
            put("backup.json", JSONObject().put("app", "BeyondStride").put("version", VERSION)
                .put("created", System.currentTimeMillis()).put("runs", runs).put("courses", courses).toString(2).toByteArray())
        }
        return runs to courses
    }
    fun info(ctx: Context, runs: Int, courses: Int) = "$runs runs · $courses courses · ${ReadinessSync.checks(ctx).size} morning checks · ${NutriDay.historyCount(ctx) + io.github.michealsizemore1.beyondstride.NutritionStore.days(ctx).size} nutrition days · ${ActivityStore.list(ctx).size} activities"

    /** Writes the backup to [uri]. Returns a short summary ("42 runs · 3 courses"). */
    fun write(ctx: Context, uri: Uri): String {
        val (runs, courses) = ctx.contentResolver.openOutputStream(uri, "wt")!!.use { writeTo(ctx, it) }
        val info = info(ctx, runs, courses)
        meta(ctx).edit().putLong("at", System.currentTimeMillis()).putString("info", info).apply()
        return info
    }

    /** After a new run arrives: refresh the saved backup file (if the person turned that on). */
    fun autoUpdate(ctx: Context) {
        val u = uri(ctx) ?: return
        if (!auto(ctx)) return
        try { write(ctx, u) } catch (e: Exception) { meta(ctx).edit().putString("info", "Automatic backup failed — tap Back up now").apply() }
    }

    // ---------------------------------------------------------------- 🌙 nightly backups
    /** A dated backup every night (about 11:45 PM) in Downloads/BeyondStride — the last [KEEP] days are kept. */
    const val KEEP = 14
    const val FOLDER = "Download/BeyondStride/"
    fun dailyOn(ctx: Context) = meta(ctx).getBoolean("daily", true)
    fun setDailyOn(ctx: Context, on: Boolean) { meta(ctx).edit().putBoolean("daily", on).apply(); if (on) scheduleDaily(ctx) else try { androidx.work.WorkManager.getInstance(ctx).cancelUniqueWork("dailyBackup") } catch (e: Exception) { } }
    fun dailyLast(ctx: Context) = meta(ctx).getString("dailyDate", "") ?: ""
    fun dailyLastAt(ctx: Context) = meta(ctx).getLong("dailyAt", 0L)
    fun dailyInfo(ctx: Context) = meta(ctx).getString("dailyInfo", "") ?: ""

    /** Books tonight's backup (11:45 PM). Safe to call often — a waiting one is kept. */
    fun scheduleDaily(ctx: Context) {
        if (!dailyOn(ctx)) return
        val now = java.time.LocalDateTime.now()
        var at = now.toLocalDate().atTime(23, 45)
        if (!at.isAfter(now)) at = at.plusDays(1)
        try { androidx.work.WorkManager.getInstance(ctx).enqueueUniqueWork("dailyBackup", androidx.work.ExistingWorkPolicy.KEEP,
            androidx.work.OneTimeWorkRequestBuilder<DailyWork>().setInitialDelay(java.time.Duration.between(now, at).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS).build()) } catch (e: Exception) { }
    }
    class DailyWork(c: Context, w: androidx.work.WorkerParameters) : androidx.work.Worker(c, w) {
        override fun doWork(): Result {
            try { daily(applicationContext, force = true) } catch (e: Exception) { }
            return Result.success()   // tomorrow night's is booked by the hourly check once this one has finished
        }
    }

    /**
     * Makes tonight's backup when it's due: from 11:30 PM, or straight away if a night was missed (phone off).
     * Also refreshes your chosen backup file. Returns a summary, or null if nothing was due. Call off the main thread.
     */
    fun daily(ctx: Context, force: Boolean = false): String? {
        if (!dailyOn(ctx)) return null
        val today = java.time.LocalDate.now(); val t = java.time.LocalTime.now(); val last = dailyLast(ctx)
        val due = force || (last < today.toString() && t.hour * 60 + t.minute >= 23 * 60 + 30) || last < today.minusDays(1).toString()
        if (!due || (last == today.toString() && !force)) return null
        val name = "BeyondStride-daily-$today.zip"
        val info = try {
            val (runs, courses) = writeDaily(ctx, name)
            info(ctx, runs, courses)
        } catch (e: Exception) { meta(ctx).edit().putString("dailyInfo", "Nightly backup failed: ${e.message}").apply(); return null }
        meta(ctx).edit().putString("dailyDate", today.toString()).putLong("dailyAt", System.currentTimeMillis()).putString("dailyInfo", info).apply()
        try { autoUpdate(ctx) } catch (e: Exception) { }
        return info
    }

    private fun writeDaily(ctx: Context, name: String): Pair<Int, Int> {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val cr = ctx.contentResolver; val col = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
            // Ours from earlier nights (newest first): replace tonight's, keep the last KEEP.
            val mine = ArrayList<Pair<Long, String>>()
            cr.query(col, arrayOf(android.provider.MediaStore.MediaColumns._ID, android.provider.MediaStore.MediaColumns.DISPLAY_NAME),
                "${android.provider.MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
                arrayOf(FOLDER, "BeyondStride-daily-%"), null)?.use { c -> while (c.moveToNext()) mine += c.getLong(0) to c.getString(1) }
            mine.filter { it.second == name }.forEach { try { cr.delete(android.content.ContentUris.withAppendedId(col, it.first), null, null) } catch (e: Exception) { } }
            val v = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name); put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, FOLDER); put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = cr.insert(col, v) ?: throw java.io.IOException("couldn't create the file")
            val r = try { cr.openOutputStream(uri, "w")!!.use { writeTo(ctx, it) } } catch (e: Exception) { cr.delete(uri, null, null); throw e }
            cr.update(uri, android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            mine.filter { it.second != name }.sortedByDescending { it.second }.drop(KEEP - 1)
                .forEach { try { cr.delete(android.content.ContentUris.withAppendedId(col, it.first), null, null) } catch (e: Exception) { } }
            return r
        }
        val dir = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "backups").apply { mkdirs() }
        val r = File(dir, name).outputStream().use { writeTo(ctx, it) }
        dir.listFiles()?.filter { it.name.startsWith("BeyondStride-daily-") }?.sortedByDescending { it.name }?.drop(KEEP)?.forEach { it.delete() }
        return r
    }

    /** Reads a backup file without changing anything: what's in it. */
    fun peek(ctx: Context, uri: Uri): JSONObject? = try {
        ctx.contentResolver.openInputStream(uri)!!.use { ins ->
            ZipInputStream(ins.buffered()).use { z ->
                var e = z.nextEntry; var meta: JSONObject? = null
                while (e != null) { if (e.name == "backup.json") meta = JSONObject(z.readBytes().toString(Charsets.UTF_8)); e = z.nextEntry }
                meta
            }
        }
    } catch (e: Exception) { null }

    /**
     * Restores a backup: runs and courses are added (existing ones with the same name are replaced, others kept),
     * workouts/plan and settings are replaced. Then everything is sent to the watch.
     */
    fun restore(ctx: Context, uri: Uri): String = ctx.contentResolver.openInputStream(uri)!!.use { restoreFrom(ctx, it) }

    /** Restores from a backup zip stream (a file, or the cloud copy). */
    fun restoreFrom(ctx: Context, ins: java.io.InputStream, push: Boolean = true): String {
        var runs = 0; var courses = 0
        var prefs: JSONObject? = null
        val base = ctx.filesDir.canonicalPath
        ZipInputStream(ins.buffered()).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val name = e.name
                val bytes = z.readBytes()
                when {
                    name == "prefs.json" -> prefs = JSONObject(bytes.toString(Charsets.UTF_8))
                    name == "backup.json" -> { }
                    (DIRS.any { name.startsWith("$it/") } || name in FILES) && !name.contains("..") -> {
                        val f = File(ctx.filesDir, name)
                        if (f.canonicalPath.startsWith(base)) {
                            f.parentFile?.mkdirs(); f.writeBytes(bytes)
                            if (name.startsWith("runs/")) runs++ else if (name.startsWith("courses/")) courses++
                        }
                    }
                }
                e = z.nextEntry
            }
        }
        NutriDay.forget()
        prefs?.let { all ->
            PREFS.forEach { name ->
                val o = all.optJSONObject(name) ?: return@forEach
                val ed = ctx.getSharedPreferences(name, 0).edit()
                o.keys().forEach { k ->
                    // Sync bookkeeping is left out, so the restored settings count as new and reach the watch.
                    if (k.startsWith("at_") || k.startsWith("syn_")) return@forEach
                    val x = o.getJSONObject(k)
                    when (x.getString("t")) {
                        "b" -> ed.putBoolean(k, x.getBoolean("v"))
                        "i" -> ed.putInt(k, x.getInt("v"))
                        "l" -> ed.putLong(k, x.getLong("v"))
                        "f" -> ed.putFloat(k, x.getDouble("v").toFloat())
                        "s" -> ed.putString(k, x.getString("v"))
                        "set" -> ed.putStringSet(k, x.getJSONArray("v").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() })
                    }
                }
                if (name == "runsettings") {
                    ctx.getSharedPreferences(name, 0).all.keys.filter { it.startsWith("at_") || it.startsWith("syn_") }.forEach { ed.remove(it) }
                    ed.putLong("settingsAt", System.currentTimeMillis())
                }
                ed.commit()
            }
        }
        if (push) pushAll(ctx)
        return "$runs runs · $courses courses restored"
    }

    /** Sends it all to the watch. */
    fun pushAll(ctx: Context) {
        RunSettings.pushToWatch(ctx)
        WorkoutStore.pushToWatch(ctx)
        io.github.michealsizemore1.beyondstride.Courses.list(ctx).forEach { io.github.michealsizemore1.beyondstride.Courses.save(ctx, it, "phone") }
        ReadinessSync.pushAll(ctx)
        RunSettings.sendAllToWatch(ctx, force = true)
    }
}
