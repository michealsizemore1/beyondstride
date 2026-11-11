package io.github.michealsizemore1.beyondstride.run

import android.app.AlertDialog
import android.content.Context
import io.github.michealsizemore1.beyondstride.ExecScore
import io.github.michealsizemore1.beyondstride.RunMath
import io.github.michealsizemore1.beyondstride.Workouts
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 🎯 Execution Score for the phone's runs (worked out from the watch's record; remembered so lists stay quick). */
object ExecScores {
    private const val VER = 1
    private fun p(ctx: Context) = ctx.getSharedPreferences("execscore", 0)

    private var daysAt = 0L; private var daysCache: JSONObject? = null
    private val wCache = HashMap<String, JSONObject?>()
    @Synchronized fun planWorkout(ctx: Context, date: String): JSONObject? = try {
        val now = System.currentTimeMillis()
        if (now - daysAt > 10_000) { daysCache = PlanStore.data(ctx).optJSONObject("current")?.optJSONObject("days"); daysAt = now; wCache.clear() }
        val text = daysCache?.optString(date).orEmpty()
        if (text.isBlank()) null else wCache.getOrPut(text) { Workouts.fromPlanText(text) }
    } catch (e: Exception) { null }

    fun compute(ctx: Context, run: JSONObject): ExecScore.Result? = try {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(run.optLong("start")))
        ExecScore.compute(run, if (run.has("workout")) null else planWorkout(ctx, date), RunSettings.hrMax(ctx), RunSettings.hrRest(ctx), RunSettings.cp(ctx))
    } catch (e: Exception) { null }

    /** The score for the run that started at [start] (null if it wasn't recorded by the watch or had no targets). */
    fun forRun(ctx: Context, start: Long): ExecScore.Result? {
        val f = PhoneRuns.file(ctx, start); if (!f.exists()) return null
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(start))
        val sig = "$VER.${f.lastModified()}.${planWorkout(ctx, date)?.optJSONArray("steps")?.toString()?.hashCode() ?: 0}"
        p(ctx).getString("r_$start", null)?.let { c -> try { val o = JSONObject(c); if (o.optString("sig") == sig) return o.optJSONObject("r")?.let { ExecScore.Result.fromJson(it) } } catch (e: Exception) { } }
        val r = PhoneRuns.read(f)?.let { compute(ctx, it) }
        p(ctx).edit().putString("r_$start", JSONObject().put("sig", sig).apply { r?.let { put("r", it.toJson()) } }.toString()).apply()
        return r
    }

    /** The score shown for an activity: worked out from the watch's record, else the one saved on the activity. */
    fun of(ctx: Context, a: JSONObject): Double =
        ActivityStore.idOf(a).toLongOrNull()?.let { forRun(ctx, it) }?.score?.toDouble() ?: ActivityStore.num(a, "execScore")

    fun lines(r: ExecScore.Result): List<String> = r.steps.map { s ->
        val pct = s.pct?.let { "$it%" } ?: if (!s.done) "skipped" else "—"
        listOf(s.name, if (s.sec > 0) RunMath.clock(s.sec) else null, if (s.target != "none") ExecScore.targetText(s) else null,
            if (s.done && s.target != "none" && s.avg > 0) ExecScore.avgText(s) else null).filterNotNull().joinToString(" · ") +
            if (s.target != "none" || !s.done) "  →  $pct" else ""
    }

    fun explain(ctx: Context, r: ExecScore.Result) {
        AlertDialog.Builder(ctx).setTitle("🎯 Execution ${r.score}/100 · ${r.label}")
            .setMessage(r.basis + "\n\n" + lines(r).joinToString("\n") +
                "\n\nTime inside each step's target (a little outside gets partial credit). The first 45 s of a heart-rate step (15 s for pace/power) " +
                "are skipped so effort can settle. Warm-up and cool-down count half; skipped or short steps lower the score.")
            .setPositiveButton("OK", null).show()
    }
}
