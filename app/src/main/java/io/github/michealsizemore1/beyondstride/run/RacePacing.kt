package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.text.InputType
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import io.github.michealsizemore1.beyondstride.Coaching
import io.github.michealsizemore1.beyondstride.Heat
import io.github.michealsizemore1.beyondstride.RunMath
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * 🏁 Race Pacing Plan (Fitness tab): mile-by-mile splits for a goal time — your own goal, or the estimated time from your
 * recent running — even or negative split, slowed for race-day heat & humidity. "Send to watch" adds it to the watch's
 * workouts with a pace target for every mile.
 */
object RacePacing {
    private var redraw: () -> Unit = {}
    private fun p(a: Activity) = a.getSharedPreferences("racepacing", 0)
    private val DISTS = listOf("Marathon" to 26.2188, "Half marathon" to 13.1094, "10K" to 6.21371, "5K" to 3.10686)
    private fun dp(a: Activity, v: Int) = RunUi.dp(a, v)

    private fun hms(sec: Int): String { val h = sec / 3600; val m = sec % 3600 / 60; val s = sec % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s) }
    private fun parse(t: String): Int? { val p = t.trim().split(":").map { it.trim().toIntOrNull() ?: return null }
        return when (p.size) { 3 -> p[0] * 3600 + p[1] * 60 + p[2]; 2 -> p[0] * 3600 + p[1] * 60; else -> null }?.takeIf { it > 600 } }

    /** Estimated time for [miles] from the fastest pace of the last 60 days (≥ 1 mi), like Estimated Race Times. */
    private fun estimate(acts: List<JSONObject>, miles: Double): Int? {
        val S = ActivityStore; val today = LocalDate.now()
        var bestP = 0; var bestD = 0.0
        acts.forEach { a -> val d = try { LocalDate.parse(S.str(a, "date").take(10)) } catch (e: Exception) { return@forEach }
            if (ChronoUnit.DAYS.between(d, today) !in 0..59) return@forEach
            val pc = S.paceSec(S.str(a, "pace")); val mi = S.num(a, "distance")
            if (pc > 0 && mi >= 1 && (bestP == 0 || pc < bestP)) { bestP = pc; bestD = mi } }
        if (bestP == 0) return null
        return Coaching.riegel(bestP * bestD, bestD, miles).toInt()
    }

    fun card(a: Activity, col: LinearLayout, acts: List<JSONObject>, redraw: () -> Unit = { a.recreate() }) {
        this.redraw = redraw
        val pr = p(a)
        val di = pr.getInt("dist", 0).coerceIn(0, DISTS.lastIndex); val (dName, miles) = DISTS[di]
        val est = estimate(acts, miles)
        val goal = pr.getInt("goal_$di", 0).takeIf { it > 0 } ?: est
        val strat = pr.getString("strategy", "negative") ?: "negative"
        val temp = pr.getFloat("temp", 0f).toDouble(); val hum = pr.getFloat("hum", 0f).toDouble()
        val heat = if (temp > 0 && hum > 0) Heat.pct(temp, hum) else 0.0
        col.addView(RunUi.card(a).apply {
            addView(RunUi.text(a, "🏁 Race Pacing Plan", Color.WHITE, 16f, true))
            if (goal == null) {
                addView(RunUi.text(a, "Set a goal time to see your mile splits (no recent run of 1+ mi to estimate from).", RunUi.MUTED, 12f))
            } else {
                val src = if (pr.getInt("goal_$di", 0) > 0) "your goal" else "estimated from recent runs"
                addView(RunUi.text(a, "$dName · ${hms(goal)} ($src) · " + (if (strat == "even") "even pace" else "negative split"), RunUi.ACCENT, 13f, true))
                if (heat > 0.05) addView(RunUi.text(a, String.format(Locale.US, "Race-day %.0f°F, %.0f%% humidity: +%.1f%% → finish %s", temp, hum, heat, hms((goal * (1 + heat / 100)).toInt())), 0xFFF97316.toInt(), 12f, true))
                else if (temp > 0) addView(RunUi.text(a, String.format(Locale.US, "Race-day %.0f°F, %.0f%% humidity: no heat slowdown", temp, hum), RunUi.MUTED, 12f))
                val splits = Coaching.pacingPlan(goal.toDouble(), miles, strat, heat)
                // Splits in rows of three: "Mile 1  9:12  (9:12)".
                splits.chunked(3).forEach { row ->
                    addView(LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(a, 3), 0, 0)
                        row.forEach { s -> addView(RunUi.text(a, "${s.label.removePrefix("Mile ").let { if (it.startsWith("Last")) it.removePrefix("Last ").removeSuffix(" mi") else it }}  " +
                            "${RunMath.clock(s.paceSec)}  ·  ${hms(s.cumSec.toInt())}", Color.WHITE, 12f), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)) } })
                }
                addView(RunUi.text(a, "Each: mile · pace /mi · time at that point. " + (if (strat == "even") "Same pace every mile." else
                    "Start ~10 s slower for the first mile, run the first half about 1% easy and the second half 1% quicker — the way most best races are run.") +
                    " Hills aren't included; run by effort on them.", RunUi.DIM, 10f).apply { setPadding(0, dp(a, 6), 0, 0) })
            }
            addView(LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(a, 8), 0, 0)
                addView(RunUi.button(a, "Goal & distance", RunUi.ACCENT) { editGoal(a, est) }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(a, 6) })
                addView(RunUi.button(a, "Race weather", 0xFF374151.toInt(), Color.WHITE) { editWeather(a) }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            })
            if (goal != null) addView(RunUi.button(a, "⌚ Send splits to watch", 0xFF22C55E.toInt()) {
                val w = Coaching.pacingWorkout("$dName pacing ${hms((goal * (1 + heat / 100)).toInt())}", Coaching.pacingPlan(goal.toDouble(), miles, strat, heat))
                    .put("id", "racepace$di")
                WorkoutStore.upsertLibrary(a, w)
                Toast.makeText(a, "Added to the watch's workouts: \"${w.optString("name")}\" — a pace target for every mile.", Toast.LENGTH_LONG).show()
            }.apply { layoutParams = LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(a, 6) } })
        })
    }

    private fun editGoal(a: Activity, est: Int?) {
        val pr = p(a)
        AlertDialog.Builder(a).setTitle("Race distance").setItems(DISTS.map { it.first }.toTypedArray()) { _, di ->
            val box = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(a, 20), dp(a, 8), dp(a, 20), 0) }
            val cur = pr.getInt("goal_$di", 0)
            val e = EditText(a).apply { hint = "Goal time, e.g. 3:59:00"; inputType = InputType.TYPE_CLASS_DATETIME; if (cur > 0) setText(hms(cur)) }
            box.addView(e)
            val estD = if (di == pr.getInt("dist", 0)) est else null
            box.addView(RunUi.text(a, "Leave empty to use the estimate from your recent runs" + (estD?.let { " (${hms(it)})" } ?: "") + ".", Color.GRAY, 12f))
            AlertDialog.Builder(a).setTitle(DISTS[di].first + " goal").setView(box)
                .setPositiveButton("Even pace") { _, _ -> save(a, di, e.text.toString(), "even") }
                .setNeutralButton("Negative split") { _, _ -> save(a, di, e.text.toString(), "negative") }
                .setNegativeButton("Cancel", null).show()
        }.show()
    }
    private fun save(a: Activity, di: Int, t: String, strat: String) {
        val sec = parse(t)
        if (t.isNotBlank() && sec == null) { Toast.makeText(a, "Use h:mm:ss, like 3:59:00", Toast.LENGTH_LONG).show(); return }
        p(a).edit().putInt("dist", di).putInt("goal_$di", sec ?: 0).putString("strategy", strat).apply()
        redraw()
    }
    private fun editWeather(a: Activity) {
        val pr = p(a)
        val box = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(a, 20), dp(a, 8), dp(a, 20), 0) }
        val t = EditText(a).apply { hint = "Temperature °F at the start"; inputType = InputType.TYPE_CLASS_NUMBER; pr.getFloat("temp", 0f).takeIf { it > 0 }?.let { setText(it.toInt().toString()) } }
        val h = EditText(a).apply { hint = "Humidity %"; inputType = InputType.TYPE_CLASS_NUMBER; pr.getFloat("hum", 0f).takeIf { it > 0 }?.let { setText(it.toInt().toString()) } }
        box.addView(t); box.addView(h)
        box.addView(RunUi.text(a, "From the race-day forecast. The plan slows by the same heat & humidity rule as your watch's heat-adjusted pace.", Color.GRAY, 12f))
        AlertDialog.Builder(a).setTitle("Race-day weather").setView(box)
            .setPositiveButton("Save") { _, _ -> pr.edit().putFloat("temp", t.text.toString().toFloatOrNull() ?: 0f).putFloat("hum", h.text.toString().toFloatOrNull() ?: 0f).apply(); redraw() }
            .setNeutralButton("Clear") { _, _ -> pr.edit().remove("temp").remove("hum").apply(); redraw() }
            .setNegativeButton("Cancel", null).show()
    }
}
