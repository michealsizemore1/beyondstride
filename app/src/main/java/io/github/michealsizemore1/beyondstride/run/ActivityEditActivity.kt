package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import org.json.JSONObject
import java.util.Locale

/** Add / Edit Activity — the Command Center's form, same fields and rules (blank calories are estimated). */
class ActivityEditActivity : Activity() {
    private val S = ActivityStore
    private var orig: JSONObject? = null
    private val fields = LinkedHashMap<String, EditText>()
    private var type = "Run"
    private var source = "watch"
    private var scanStatus: android.widget.TextView? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        io.github.michealsizemore1.beyondstride.Theme.load(this, "runsettings")
        intent.getStringExtra("id")?.let { id -> orig = S.list(this).firstOrNull { S.idOf(it) == id } }
        orig?.let { type = S.str(it, "type").ifEmpty { "Run" }; source = S.str(it, "source").ifEmpty { "manual" } }
        build()
    }

    private fun dp(v: Int) = RunUi.dp(this, v)

    private fun build() {
        val ctx = this
        val a = orig
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(40), dp(16), dp(32)) }
        col.addView(RunUi.text(this, if (a == null) "➕ Add Activity" else "✏️ Edit Activity", 0xFFE67E22.toInt(), 22f, true))
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, 0)
            addView(RunUi.text(ctx, "📷 Scan Screenshot", Color.WHITE, 13f, true).apply {
                background = RunUi.rounded(0xFF8E44AD.toInt(), dp(6).toFloat()); setPadding(dp(14), dp(7), dp(14), dp(7))
                setOnClickListener {
                    if (!AiFood.hasKey(ctx)) { Toast.makeText(ctx, "Please enter your AI key in Settings first.", Toast.LENGTH_LONG).show(); return@setOnClickListener }
                    startActivityForResult(android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).setType("image/*"), 7401) } })
            addView(RunUi.text(ctx, "", RunUi.MUTED, 12f).apply { setPadding(dp(8), 0, 0, 0) }.also { scanStatus = it }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        })
        fun label(t: String) = col.addView(RunUi.text(ctx, t, RunUi.MUTED, 12f, true).apply { setPadding(0, dp(10), 0, dp(2)) })
        fun input(key: String, t: String, hint: String, kind: Int, value: String) {
            label(t)
            col.addView(EditText(ctx).apply {
                setText(value); this.hint = hint; inputType = kind; setTextColor(Color.WHITE); setHintTextColor(RunUi.DIM); textSize = 15f
                background = RunUi.rounded(RunUi.CARD, dp(8).toFloat()); setPadding(dp(12), dp(9), dp(12), dp(9))
                if (kind and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0) { minLines = 3; gravity = android.view.Gravity.TOP }
            }.also { fields[key] = it }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        fun v(k: String) = a?.let { S.str(it, k).takeIf { s -> s != "0" && s != "0.0" } } ?: ""
        val num = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val text = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        input("date", "Date (YYYY-MM-DD)", "2026-11-07", InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_DATE, v("date").ifEmpty { java.time.LocalDate.now().toString() })
        label("Type")
        val types = listOf("Run", "Ride", "Walk", "Swim", "Strength", "Rest Day", "Other")
        val typeView = RunUi.text(this, "$type  ▾", Color.WHITE, 15f)
        col.addView(typeView.apply {
            background = RunUi.rounded(RunUi.CARD, dp(8).toFloat()); setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener { android.app.AlertDialog.Builder(ctx).setTitle("Type").setSingleChoiceItems(types.toTypedArray(), types.indexOf(type)) { d, i -> type = types[i]; typeView.text = "$type  ▾"; d.dismiss() }.show() }
        })
        input("title", "Title / Name", "e.g. Morning Run", text, v("title"))
        input("distance", "Distance (miles)", "0.0", num, v("distance"))
        input("duration", "Time (MM:SS, H:MM:SS, or minutes)", "32:15 or 1:32:15 or 32 min", InputType.TYPE_CLASS_TEXT, v("duration"))
        input("pace", "Avg Pace (MM:SS/mi)", "8:30 (worked out from distance and time if blank)", InputType.TYPE_CLASS_TEXT, v("pace"))
        input("power", "Avg Power (W)", "0", num, v("power"))
        input("airPower", "Air Power (%)", "e.g. 2.8", num, v("airPower").ifEmpty { v("wind") })
        input("heartRate", "Avg HR (bpm)", "0", num, v("heartRate"))
        input("cadence", "Cadence (spm)", "0", num, v("cadence"))
        input("calories", "Calories", "Leave blank to auto-estimate", num, v("calories"))
        input("elevGain", "Elev Gain (ft)", "0", num, v("elevGain"))
        input("temp", "Temp (°F)", "e.g. 68", num, v("temp"))
        input("humidity", "Humidity (%)", "e.g. 55", num, v("humidity"))
        input("aqi", "AQI (Air Quality)", "e.g. 42", InputType.TYPE_CLASS_NUMBER, v("aqi"))
        input("execScore", "Execution Score (0-100)", "0-100", num, v("execScore"))
        input("routeUrl", "Run Map / Route Link", "Garmin, Strava, Google Maps, etc.", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI, v("routeUrl"))
        label("Source")
        val sources = (listOf("watch") + (if (source != "watch") listOf(source) else emptyList()))
        val srcView = RunUi.text(this, S.sourceLabel(source) + "  ▾", Color.WHITE, 15f)
        col.addView(srcView.apply {
            background = RunUi.rounded(RunUi.CARD, dp(8).toFloat()); setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener { android.app.AlertDialog.Builder(ctx).setTitle("Source").setSingleChoiceItems(sources.map { S.sourceLabel(it) }.toTypedArray(), sources.indexOf(source)) { d, i -> source = sources[i]; srcView.text = S.sourceLabel(source) + "  ▾"; d.dismiss() }.show() }
        })
        input("notes", "Notes", "Any notes...", text or InputType.TYPE_TEXT_FLAG_MULTI_LINE, v("notes"))
        input("injuryReport", "Injury Report", "Pain, soreness, location, severity, or changes during the workout...", text or InputType.TYPE_TEXT_FLAG_MULTI_LINE, v("injuryReport"))
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(16), 0, 0)
            addView(RunUi.button(ctx, "Cancel", RunUi.CARD, Color.WHITE) { finish() }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
            addView(RunUi.button(ctx, if (a == null) "Save Activity" else "Save Changes", 0xFFE67E22.toInt(), Color.WHITE) { save() }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        })
        setContentView(ScrollView(this).apply { setBackgroundColor(RunUi.BG); addView(col, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)) })
    }

    private fun save() {
        fun f(k: String) = fields[k]?.text?.toString()?.trim() ?: ""
        val date = f("date")
        if (!Regex("\\d{4}-\\d{2}-\\d{2}").matches(date)) { Toast.makeText(this, "Please enter a date (YYYY-MM-DD).", Toast.LENGTH_SHORT).show(); return }
        val route = f("routeUrl")
        if (route.isNotEmpty() && !route.startsWith("http://") && !route.startsWith("https://")) { Toast.makeText(this, "Route link must begin with http:// or https://", Toast.LENGTH_LONG).show(); return }
        val act = JSONObject()
        val id: Any = orig?.get("id") ?: System.currentTimeMillis()
        act.put("id", id); act.put("date", date); act.put("type", type); act.put("source", source)
        val clear = ArrayList<String>()
        fun put(k: String, v: Any?) { if (v == null || v == "" || v == 0.0 || v == 0) { if (orig?.has(k) == true) clear += k } else act.put(k, v) }
        val dist = f("distance").toDoubleOrNull() ?: 0.0
        put("title", f("title")); put("distance", dist); put("duration", f("duration"))
        var pace = f("pace")
        if (pace.isEmpty() && dist > 0) S.durSec(f("duration")).takeIf { it > 0 }?.let { s -> val p = (s / dist).toInt(); pace = "${p / 60}:${String.format(Locale.US, "%02d", p % 60)}" }
        put("pace", pace)
        put("power", f("power").toDoubleOrNull() ?: 0.0)
        put("airPower", (f("airPower").toDoubleOrNull() ?: 0.0).coerceIn(0.0, 100.0).let { Math.round(it * 10) / 10.0 })
        put("heartRate", f("heartRate").toDoubleOrNull() ?: 0.0); put("cadence", f("cadence").toDoubleOrNull() ?: 0.0)
        val cal = f("calories").toDoubleOrNull() ?: S.estimateCalories(type, dist, f("duration"), f("power").toDoubleOrNull() ?: 0.0, f("elevGain").toDoubleOrNull() ?: 0.0,
            CalorieTargets.weight(io.github.michealsizemore1.beyondstride.NutritionStore.days(this))).toDouble()
        put("calories", cal); put("elevGain", f("elevGain").toDoubleOrNull() ?: 0.0); put("temp", f("temp"))
        put("humidity", (f("humidity").toDoubleOrNull() ?: 0.0).coerceIn(0.0, 100.0)); put("aqi", (f("aqi").toIntOrNull() ?: 0).coerceIn(0, 500))
        put("execScore", f("execScore").toDoubleOrNull() ?: 0.0); put("routeUrl", route); put("notes", f("notes")); put("injuryReport", f("injuryReport"))
        ActivityStore.save(this, act, clear)
        Toast.makeText(this, if (Standalone.on(this)) "Saved" else "Saved — also goes to the Command Center", Toast.LENGTH_SHORT).show()
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 7401 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val type = contentResolver.getType(uri) ?: ""
        if (type !in listOf("image/png", "image/jpeg", "image/webp", "image/gif")) { Toast.makeText(this, "Please choose a PNG, JPEG, WEBP, or GIF screenshot.", Toast.LENGTH_LONG).show(); return }
        val bytes = try { contentResolver.openInputStream(uri)?.use { it.readBytes() } } catch (e: Exception) { null }
        if (bytes == null || bytes.isEmpty()) { scanStatus?.apply { setTextColor(0xFFE74C3C.toInt()); text = "Could not read that image." }; return }
        // Large phone screenshots are shrunk to a JPEG first (the AI accepts up to ~5 MB).
        var b = bytes; var mt = type
        if (b.size > 3_500_000) { try { val bm = android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size); val sc = Math.min(1.0, 1600.0 / Math.max(bm.width, bm.height))
            val sm = android.graphics.Bitmap.createScaledBitmap(bm, (bm.width * sc).toInt(), (bm.height * sc).toInt(), true)
            b = java.io.ByteArrayOutputStream().also { sm.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray(); mt = "image/jpeg" } catch (e: Exception) { } }
        scanStatus?.apply { setTextColor(RunUi.MUTED); text = "⏳ Analyzing screenshot..." }
        val prompt = "This is a screenshot related to a workout/run from an app like Strava, Garmin, Apple Watch, or Nike Run Club.\n" +
            "It may be an overall summary card, OR a per-mile/per-km SPLITS table (columns like Mi/Km, Pace, Elev, HR).\n" +
            "Respond with plain text only, no markdown, in exactly this structure:\n" +
            "Line 1: one pipe-delimited summary line in this exact format:\n" +
            "TYPE|TITLE|DATE|DISTANCE|DURATION|PACE|HR|CADENCE|CALORIES|ELEVGAIN\n" +
            "Then, ONLY if the screenshot is a splits table, one additional line per row using this format:\n" +
            "SPLIT|<mile number as printed, e.g. 1 or 0.1>|<pace MM:SS>|<elevation change, signed feet or 0>|<HR bpm or blank>\n" +
            "Summary line field rules — use ONLY a value that is PRINTED directly on the screenshot. Never calculate, total, average, or otherwise derive a summary value yourself (a separate process computes totals from the SPLIT rows when needed) and never guess a date. If a field is not printed as its own value on the screenshot, return \"\" for it exactly:\n" +
            "TYPE: one of Run, Ride, Walk, Swim, Strength, Other (best guess from icons/labels; default Run if unclear)\n" +
            "TITLE: short activity name/title if printed, else \"\"\n" +
            "DATE: YYYY-MM-DD only if an actual date is printed on the screenshot itself, else \"\"\n" +
            "DISTANCE: number only, in MILES (convert from km by multiplying by 0.621371, round to 2 decimals), only if printed as its own overall total, else \"\"\n" +
            "DURATION: total moving/elapsed time as MM:SS or H:MM:SS, only if printed as its own overall total, else \"\"\n" +
            "PACE: overall average pace as MM:SS per mile (convert from per-km pace by dividing by 0.621371), only if printed as its own overall total, else \"\"\n" +
            "HR: average heart rate in bpm, number only, only if printed as its own overall total, else \"\"\n" +
            "CADENCE: average cadence in steps per minute, number only, else \"\"\n" +
            "CALORIES: number only, else \"\"\n" +
            "ELEVGAIN: elevation gain in FEET (convert from meters by multiplying by 3.28084, round to whole number), only if printed as its own overall total, else \"\"\n" +
            "SPLIT row rules: include one SPLIT line per row of the table, in the order shown, using the exact mile/km label as printed (e.g. \"1\", \"0.1\"). Omit the SPLIT lines entirely if this is not a splits table."
        val b64 = android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP)
        Thread {
            val res = try { Result.success(AiFood.askImage(this, b64, mt, prompt, 2000)) } catch (e: Exception) { Result.failure(e) }
            runOnUiThread { if (isFinishing) return@runOnUiThread
                res.onFailure { scanStatus?.apply { setTextColor(0xFFE74C3C.toInt()); text = "Could not analyze screenshot: ${it.message}" } }
                res.onSuccess { t -> scanned(t) } }
        }.start()
    }

    /** The Command Center's fillActivityFormFromScan: adds the scan to Notes, nothing else is changed. */
    private fun scanned(text: String) {
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val summary = lines.firstOrNull { it.contains("|") && !it.startsWith("SPLIT|") }
        if (summary == null) { scanStatus?.apply { setTextColor(0xFFE74C3C.toInt()); this.text = "Could not read stats from that screenshot." }; return }
        val parts = summary.split("|").map { it.trim() }.toMutableList(); while (parts.size < 10) parts += ""
        val splits = lines.filter { it.startsWith("SPLIT|") }.map { l -> l.split("|").map { it.trim() }.let { sp -> listOf(sp.getOrElse(1) { "" }, sp.getOrElse(2) { "" }, sp.getOrElse(3) { "" }, sp.getOrElse(4) { "" }) } }
        val added = if (splits.isNotEmpty()) {
            fun pad(s: String, w: Int, left: Boolean) = if (left) s.padEnd(w) else s.padStart(w)
            "Mile splits:\n" + pad("Mi", 5, true) + pad("Pace", 7, true) + pad("Elev", 6, false) + pad("HR", 5, false) + "\n" +
                splits.joinToString("\n") { (mi, pace, elev, hr) -> val en = elev.toDoubleOrNull(); val ed = if (en == null) elev else ((if (en > 0) "+" else "") + (if (en == Math.floor(en)) en.toLong().toString() else en.toString()))
                    pad(mi, 5, true) + pad(pace, 7, true) + pad(ed, 6, false) + pad(hr, 5, false) }
        } else {
            val bits = ArrayList<String>()
            val (type, title, date, dist, dur, pace, hr, cad, cal, elev) = Ten(parts)
            if (type.isNotEmpty()) bits += type; if (title.isNotEmpty()) bits += title; if (date.isNotEmpty()) bits += date; if (dist.isNotEmpty()) bits += "$dist mi"
            if (dur.isNotEmpty()) bits += dur; if (pace.isNotEmpty()) bits += "$pace/mi"; if (hr.isNotEmpty()) bits += "HR $hr"; if (cad.isNotEmpty()) bits += "cadence $cad"
            if (cal.isNotEmpty()) bits += "$cal cal"; if (elev.isNotEmpty()) bits += "+$elev ft"
            if (bits.isNotEmpty()) "Scanned screenshot: " + bits.joinToString(", ") else ""
        }
        if (added.isEmpty()) { scanStatus?.apply { setTextColor(0xFFE74C3C.toInt()); this.text = "Could not read stats from that screenshot." }; return }
        fields["notes"]?.let { n -> val ex = n.text.toString().trim(); n.setText(if (ex.isNotEmpty()) "$ex\n\n$added" else added) }
        scanStatus?.apply { setTextColor(0xFF4ADE80.toInt()); this.text = if (splits.isNotEmpty()) "✓ ${splits.size} mile splits added to notes — nothing else was changed." else "✓ Screenshot info added to notes — nothing else was changed." }
    }
    private class Ten(val p: List<String>) { operator fun component1() = p[0]; operator fun component2() = p[1]; operator fun component3() = p[2]; operator fun component4() = p[3]; operator fun component5() = p[4]
        operator fun component6() = p[5]; operator fun component7() = p[6]; operator fun component8() = p[7]; operator fun component9() = p[8]; operator fun component10() = p[9] }
}
