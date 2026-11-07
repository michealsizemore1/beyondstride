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

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        io.github.michealsizemore1.beyondstride.Theme.load(this, "runsettings")
        intent.getStringExtra("id")?.let { id -> orig = S.list(this).firstOrNull { it.opt("id").toString() == id } }
        orig?.let { type = S.str(it, "type").ifEmpty { "Run" }; source = S.str(it, "source").ifEmpty { "manual" } }
        build()
    }

    private fun dp(v: Int) = RunUi.dp(this, v)

    private fun build() {
        val ctx = this
        val a = orig
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(40), dp(16), dp(32)) }
        col.addView(RunUi.text(this, if (a == null) "➕ Add Activity" else "✏️ Edit Activity", 0xFFE67E22.toInt(), 22f, true))
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
        Toast.makeText(this, "Saved — also goes to the Command Center", Toast.LENGTH_SHORT).show()
        finish()
    }
}
