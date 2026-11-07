package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.michealsizemore1.beyondstride.Hydration
import io.github.michealsizemore1.beyondstride.Nutrition
import io.github.michealsizemore1.beyondstride.NutritionStore
import java.util.Locale

/** Today's nutrition pieces, shared by the Today tab's Fuel card and the Nutrition screen. */
object NutritionUi {
    private const val TILE = 0xFF26302C.toInt()

    /** Calories, target bar, macros, fluids (with quick taps) and, if [withMicros], the micronutrient tiles. */
    fun today(act: Activity, col: LinearLayout, withMicros: Boolean, big: Boolean = false, summary: Boolean = false, chips: Boolean = true, refresh: () -> Unit) {
        val ctx = act
        fun dp(v: Int) = RunUi.dp(ctx, v)
        val N = Nutrition
        val nd = N.today(NutritionStore.days(ctx))
        when {
            !NutritionSync.ready(ctx) -> col.addView(RunUi.text(ctx, "Set up LiveTrack first — nutrition is shared through the same database.", RunUi.MUTED, 12f))
            NutritionSync.lastError(ctx) == "rules" -> col.addView(RunUi.text(ctx, "Update your Firebase rules to share nutrition (Settings → LiveTrack → copy rules).", 0xFFF2C94C.toInt(), 12f))
        }
        if (nd == null || nd.count == 0) col.addView(RunUi.text(ctx, "No food logged today yet", RunUi.MUTED, 13f))
        else {
            val over = nd.target > 0 && nd.left < 0
            col.addView(RunUi.text(ctx, N.line(nd), if (over) 0xFFF2C94C.toInt() else Color.WHITE, if (big) 18f else 16f, true))
            if (nd.target > 0) col.addView(android.widget.ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = nd.target; progress = nd.cal.coerceAtMost(nd.target)
                progressTintList = android.content.res.ColorStateList.valueOf(if (over) 0xFFF2C94C.toInt() else 0xFF5FD38D.toInt())
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(8)).apply { topMargin = dp(4); bottomMargin = dp(4) }
            })
            val (pp, cp, fp) = nd.ratios()
            col.addView(RunUi.text(ctx, N.macros(nd) + "  ($pp / $cp / $fp %)", RunUi.MUTED, 12f))
            // The Command Center's two quality bars: Daily food quality and Top 7 micronutrient quality (notes on the Nutrition tab).
            io.github.michealsizemore1.beyondstride.Food.dayQuality(nd)?.let { q -> qualityBar(act, col, "Daily food quality", q.score, q.label, if (big) q.note else null) }
            if (nd.micros.isNotEmpty()) qualityBar(act, col, "Top 7 micronutrient quality", nd.microScore, nd.microLabel(), if (big) microNote(nd) else null)
        }
        // One line for the calorie target; tap for the breakdown and settings.
        if (big) try {
            val r = CalorieTargets.result(ctx)
            val parts = mutableListOf("Target ${N.kcal(r.target)}")
            r.preset?.let { parts += it.label.substringAfter(": ").substringBefore(" —").let { k -> if (it.label.startsWith("Tomorrow")) "$k tomorrow" else "$k day" } }
            val rep = Math.round(r.active * r.refuel).toInt()
            if (rep > 0 && r.preset == null) parts += "runs +${N.kcal(rep)}"
            if (r.goalAdjust != 0) parts += (io.github.michealsizemore1.beyondstride.CalorieTarget.GOALS.firstOrNull { it.first == CalorieTargets.settings(ctx).goal }?.second?.lowercase() ?: "goal") +
                (if (r.goalAdjust > 0) " +" else " −") + kotlin.math.abs(r.goalAdjust)
            col.addView(RunUi.text(ctx, parts.joinToString(" · ") + "  ▸", RunUi.ACCENT, 12f, true).apply {
                setPadding(0, dp(4), 0, dp(2))
                setOnClickListener { act.startActivity(android.content.Intent(act, NutritionToolsActivity::class.java).putExtra("mode", "target")) }
            })
        } catch (e: Exception) { }
        // Weigh-ins (Nutrition tab): tap to enter wake-up and post-run weight.
        if (big) col.addView(RunUi.text(ctx, "⚖ Weight: wake-up ${io.github.michealsizemore1.beyondstride.Hydration.lb(nd?.weight ?: 0.0)} · post-run ${io.github.michealsizemore1.beyondstride.Hydration.lb(nd?.postWeight ?: 0.0)} lb  ▸",
            Color.WHITE, 13f, true).apply { setPadding(0, dp(6), 0, dp(2)); setOnClickListener { fluidDialog(act, refresh) } })
        fluids(act, col, nd, refresh, chips)
        if (withMicros && nd != null && nd.micros.isNotEmpty()) micros(act, col, nd)
    }

    /** Today tab's Fuel card — a glance: calories with the bar, both quality scores on one line, and a rehydration
     *  reminder only while one is due. Everything else is on the Nutrition tab. */
    fun glance(act: Activity, col: LinearLayout) {
        val ctx = act
        fun dp(v: Int) = RunUi.dp(ctx, v)
        val N = Nutrition
        val nd = N.today(NutritionStore.days(ctx))
        if (!NutritionSync.ready(ctx)) { col.addView(RunUi.text(ctx, "Set up LiveTrack first — nutrition is shared through the same database.", RunUi.MUTED, 12f)); return }
        if (nd == null || nd.count == 0) col.addView(RunUi.text(ctx, "No food logged today yet  ▸", RunUi.MUTED, 13f))
        else {
            val over = nd.target > 0 && nd.left < 0
            col.addView(RunUi.text(ctx, N.line(nd), if (over) 0xFFF2C94C.toInt() else Color.WHITE, 16f, true))
            if (nd.target > 0) col.addView(android.widget.ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = nd.target; progress = nd.cal.coerceAtMost(nd.target)
                progressTintList = android.content.res.ColorStateList.valueOf(if (over) 0xFFF2C94C.toInt() else 0xFF5FD38D.toInt())
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(8)).apply { topMargin = dp(4); bottomMargin = dp(4) }
            })
            fun col(sc: Double) = N.statusColor(when { sc >= 7 -> "Met"; sc >= 5 -> "Close"; else -> "Low" })
            val sb = android.text.SpannableStringBuilder()
            fun part(t: String, c: Int) { val st = sb.length; sb.append(t); sb.setSpan(android.text.style.ForegroundColorSpan(c), st, sb.length, 0) }
            io.github.michealsizemore1.beyondstride.Food.dayQuality(nd)?.let { q -> part("Food " + String.format(Locale.US, "%.1f", q.score) + " " + q.label, col(q.score)) }
            if (nd.micros.isNotEmpty()) { if (sb.isNotEmpty()) part("  ·  ", RunUi.MUTED); part("Micros " + String.format(Locale.US, "%.1f", nd.microScore) + " " + nd.microLabel(), col(nd.microScore)) }
            if (sb.isNotEmpty()) { part("  ▸", RunUi.MUTED); col.addView(RunUi.text(ctx, "", Color.WHITE, 13f, true).apply { text = sb }) }
        }
        N.rehydrate(nd)?.takeIf { it.needOz > 0 }?.let { h ->
            col.addView(RunUi.text(ctx, "💧 Drink ~${h.needOz} oz more to rehydrate after your run", N.WATER, 12f, true).apply { setPadding(0, dp(4), 0, 0) })
        }
    }

    /** A 1–10 score on the red → green bar with a marker, like the Command Center's. */
    fun qualityBar(act: Activity, col: LinearLayout, title: String, score: Double, label: String, note: String?) {
        val ctx = act
        fun dp(v: Int) = RunUi.dp(ctx, v)
        col.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0)
            addView(RunUi.text(ctx, title, Color.WHITE, 13f, true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(RunUi.text(ctx, String.format(Locale.US, "%.1f", score) + "/10 · " + label, Nutrition.statusColor(when { score >= 7 -> "Met"; score >= 5 -> "Close"; else -> "Low" }), 13f, true))
        })
        val pct = ((score - 1) / 9).coerceIn(0.0, 1.0).toFloat()
        col.addView(object : android.view.View(ctx) {
            override fun onDraw(c: android.graphics.Canvas) {
                val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG); val h = height.toFloat(); val r = h / 2; val bh = h * 0.6f
                p.shader = android.graphics.LinearGradient(0f, 0f, width.toFloat(), 0f, intArrayOf(0xFFDC2626.toInt(), 0xFFF97316.toInt(), 0xFFFACC15.toInt(), 0xFF84CC16.toInt(), 0xFF16A34A.toInt()), null, android.graphics.Shader.TileMode.CLAMP)
                c.drawRoundRect(0f, (h - bh) / 2, width.toFloat(), (h + bh) / 2, bh / 2, bh / 2, p); p.shader = null
                val x = (width * pct).coerceIn(r, width - r)
                p.color = Color.WHITE; c.drawCircle(x, h / 2, r * 0.92f, p)
                p.color = 0xFF0F172A.toInt(); p.style = android.graphics.Paint.Style.STROKE; p.strokeWidth = r * 0.32f; c.drawCircle(x, h / 2, r * 0.76f, p)
            }
        }, LinearLayout.LayoutParams(MATCH_PARENT, dp(20)).apply { topMargin = dp(4) })
        note?.let { col.addView(RunUi.text(ctx, it, RunUi.MUTED, 11f).apply { setPadding(0, dp(3), 0, 0) }) }
    }

    /** The Command Center's note under the micronutrient bar: targets, today's sugar ceiling and the lowest nutrient. */
    fun microNote(nd: Nutrition.Day): String {
        val F = io.github.michealsizemore1.beyondstride.Food
        val sports = nd.foods.filter { it.meal == "sports" }.sumOf { it.total(it.micros["sugar"]) }
        val natural = nd.foods.filter { F.naturalSugar(it) }.sumOf { it.total(it.micros["sugar"]) }
        val ceiling = nd.micros.firstOrNull { it.field == "sugar" }?.target ?: (36 + sports + natural)
        val sNote = if (sports > 0 || natural > 0) " (36g" + (if (sports > 0) " + ${Math.round(sports)}g fueling" else "") + (if (natural > 0) " + ${Math.round(natural)}g fruit/dairy" else "") + " today)" else ""
        val low = nd.micros.minByOrNull { m -> if (m.max > 0 && m.value > m.max) Math.max(0.0, 1 - (m.value - m.max) / m.max) else Math.max(0.0, Math.min(1.0, m.value / m.target)) }
        return "Targets for a 53-year-old male distance runner: iron 14mg · calcium 1,000mg · vitamin D 15mcg · magnesium 483mg · vitamin B12 2.4mcg · sodium 2,300–4,000mg · potassium 3,400mg · zinc 15mg · vitamin C 90mg · fiber 28g · sugar ≤${Math.round(ceiling)}g$sNote · caffeine ≤400mg (a single race-day dose can push this higher by design, not a red flag)" +
            (low?.let { " · lowest today: ${it.name} ${Math.round(it.value)}${it.unit}" } ?: "")
    }

    /** Fluids line, rehydration after a run, and the quick-tap row. */
    fun fluids(act: Activity, col: LinearLayout, nd: Nutrition.Day?, refresh: () -> Unit, chips: Boolean = true) {
        val ctx = act
        fun dp(v: Int) = RunUi.dp(ctx, v)
        val N = Nutrition
        col.addView(RunUi.text(ctx, "Fluids ${nd?.waterOz ?: 0} oz" + (if (nd != null && nd.water + nd.sportsOz + nd.otherOz > 0)
            "  (water ${nd.water} · sports ${nd.sportsOz} · other ${nd.otherOz})" else ""), RunUi.MUTED, 12f).apply {
            setPadding(0, dp(4), 0, dp(2)); setOnClickListener { fluidDialog(act, refresh) } })
        N.rehydrate(nd)?.let { h ->
            col.addView(RunUi.text(ctx, "Lost ${String.format(Locale.US, "%.1f", h.lostLb)} lb on the run → ~${h.targetOz} oz to rehydrate · " +
                (if (h.needOz == 0) "caught up ✓" else "drink ~${h.needOz} oz more"),
                if (h.needOz == 0) 0xFF5FD38D.toInt() else N.WATER, 12f, true).apply { setPadding(0, dp(2), 0, 0); setOnClickListener { fluidDialog(act, refresh) } })
        }
        if (chips) col.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) }
            fun chip(t: String, onTap: () -> Unit) = addView(RunUi.text(ctx, t, N.WATER, 12f, true).apply {
                gravity = Gravity.CENTER; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; background = RunUi.rounded(TILE, dp(14).toFloat())
                setPadding(dp(4), dp(7), dp(4), dp(7)); setOnClickListener { onTap() }
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(4) })
            chip("💧+8 water") { HydrationSync.change(ctx, "water", Hydration.STEP); refresh() }
            chip("+8 sports") { HydrationSync.change(ctx, "sports", Hydration.STEP); refresh() }
            chip("+8 other") { HydrationSync.change(ctx, "other", Hydration.STEP); refresh() }
            chip("⚖ Weight") { fluidDialog(act, refresh) }
        })
    }

    /** Score and every micronutrient as a small tile (lowest first), colored by status. */
    fun micros(act: Activity, col: LinearLayout, nd: Nutrition.Day, header: Boolean = true) {
        val ctx = act
        fun dp(v: Int) = RunUi.dp(ctx, v)
        val N = Nutrition
        if (header) col.addView(RunUi.text(ctx, "MICRONUTRIENTS  " + String.format(Locale.US, "%.1f", nd.microScore) + "/10 · " + nd.microLabel(),
            N.statusColor(when { nd.microScore >= 7 -> "Met"; nd.microScore >= 5 -> "Close"; else -> "Low" }), 12f, true).apply { setPadding(0, dp(10), 0, dp(2)) })
        nd.microsByNeed().chunked(3).forEach { row ->
            col.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(4) }
                row.forEachIndexed { i, m ->
                    addView(LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        background = RunUi.rounded(TILE, dp(8).toFloat())
                        setPadding(dp(6), dp(4), dp(6), dp(4))
                        addView(RunUi.text(ctx, m.name.replace("Vitamin ", "Vit "), RunUi.MUTED, 10f))
                        addView(RunUi.text(ctx, m.shown(), Color.WHITE, 12f, true))
                        addView(RunUi.text(ctx, "${m.pct.toInt()}% · ${m.status}", N.statusColor(m.status), 12f, true))
                    }, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply { if (i < 2) marginEnd = dp(4) })
                }
                repeat(3 - row.size) { addView(android.view.View(ctx), LinearLayout.LayoutParams(0, 1, 1f)) }
            })
        }
    }

    /** Fluids (± a cup) and wake-up / post-run weight for today. Fluid taps save at once; weights on Save. */
    fun fluidDialog(act: Activity, refresh: () -> Unit) {
        val ctx = act
        fun dp(v: Int) = RunUi.dp(ctx, v)
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val today = HydrationSync.today(ctx)
        fun field(hint: String, v: Double) = android.widget.EditText(ctx).apply {
            this.hint = hint; inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            if (v > 0) setText(Hydration.lb(v))
        }
        box.addView(android.widget.TextView(ctx).apply { text = "Wake-up weight (lb)"; setPadding(0, dp(4), 0, 0) })
        val wake = field("e.g. 172.4", today?.weight ?: 0.0); box.addView(wake)
        box.addView(android.widget.TextView(ctx).apply { text = "Post-run weight (lb)"; setPadding(0, dp(8), 0, 0) })
        val post = field("e.g. 171.2", today?.postWeight ?: 0.0); box.addView(post)
        box.addView(android.widget.TextView(ctx).apply {
            text = "Also goes to the Command Center (its Wellness weight fields). Drinks are in Liquids on the Nutrition tab."; textSize = 12f; setPadding(0, dp(8), 0, 0)
        })
        android.app.AlertDialog.Builder(ctx).setTitle("Weight · today").setView(ScrollView(ctx).apply { addView(box) })
            .setPositiveButton("Save") { _, _ ->
                fun num(e: android.widget.EditText) = e.text.toString().trim().toDoubleOrNull()?.takeIf { it in 70.0..600.0 } ?: 0.0
                val w = num(wake); val p = num(post)
                if (w != (today?.weight ?: 0.0)) HydrationSync.change(ctx, "weight", value = w)
                if (p != (today?.postWeight ?: 0.0)) HydrationSync.change(ctx, "postWeight", value = p)
            }
            .setOnDismissListener { refresh() }
            .show()
    }
}
