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
        val nd = if (big) NutriDay.day(ctx) else N.today(NutritionStore.days(ctx))
        when {
            !NutritionSync.ready(ctx) -> col.addView(RunUi.text(ctx, "Set up LiveTrack first — nutrition is shared through the same database.", RunUi.MUTED, 12f))
            NutritionSync.lastError(ctx) == "rules" -> col.addView(RunUi.text(ctx, "Update your Firebase rules to share nutrition (Settings → LiveTrack → copy rules).", 0xFFF2C94C.toInt(), 12f))
        }
        if (nd == null || nd.count == 0) col.addView(RunUi.text(ctx, if (!big || NutriDay.isToday()) "No food logged today yet" else "No food logged this day", RunUi.MUTED, 13f))
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

        }
        // One line for the calorie target; tap for the breakdown and settings.
        if (big && !NutriDay.isToday()) nd?.target?.takeIf { it > 0 }?.let { col.addView(RunUi.text(ctx, "Target ${N.kcal(it)}", RunUi.MUTED, 12f).apply { setPadding(0, dp(4), 0, dp(2)) }) }
        if (big && NutriDay.isToday()) try {
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
        if (big) col.addView(RunUi.text(ctx, "⚖ Weight: wake-up ${io.github.michealsizemore1.beyondstride.Hydration.lb(nd?.weight ?: 0.0)}" + (if ((nd?.preWeight ?: 0.0) > 0) " · pre-run ${io.github.michealsizemore1.beyondstride.Hydration.lb(nd!!.preWeight)}" else "") + " · post-run ${io.github.michealsizemore1.beyondstride.Hydration.lb(nd?.postWeight ?: 0.0)} lb  ▸",
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

    /** "Macros & Micronutrients": Daily food quality, Daily Macro Ratio tiles, Top 7 micronutrient quality, then the micronutrient tiles. */
    fun macrosMicros(act: Activity, col: LinearLayout, nd: Nutrition.Day) {
        val ctx = act
        fun dp(v: Int) = RunUi.dp(ctx, v)
        io.github.michealsizemore1.beyondstride.Food.dayQuality(nd)?.let { q -> qualityBar(act, col, "Daily food quality", q.score, q.label, q.note) }
        // A Change Ratio made here shows at once (the Command Center works out "automatic" itself).
        val pr = NutriDay.pendingRatio(ctx, nd.date)
        val nd2 = if (pr != null && pr.optBoolean("enabled", true)) nd.copy(mtProt = pr.optInt("prot"), mtCarbs = pr.optInt("carbs"), mtFat = pr.optInt("fat"), mtLabel = "Custom macro target") else nd
        nd2.macroTiles()?.let { tiles ->
            col.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, dp(14), 0, 0)
                addView(RunUi.text(ctx, "Daily macro ratio", Color.WHITE, 13f, true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(RunUi.text(ctx, "Change Ratio", Color.WHITE, 12f, true).apply {
                    background = RunUi.rounded(TILE, dp(6).toFloat()); setPadding(dp(10), dp(5), dp(10), dp(5)); setOnClickListener { ratioDialog(act, nd2) } })
            })
            col.addView(RunUi.text(ctx, (if (pr != null && !pr.optBoolean("enabled", true)) "Automatic target — updating from the Command Center…" else nd2.mtLabel) + " · " + Nutrition.kcal(if (nd2.mtCal > 0) nd2.mtCal else nd2.target) + " kcal goal", RunUi.MUTED, 11f))
            if (nd.perfNote.isNotEmpty()) col.addView(RunUi.text(ctx, nd.perfNote, 0xFF93C5FD.toInt(), 11f).apply { setPadding(0, dp(3), 0, 0) })
            val colors = mapOf("Protein" to 0xFFF87171.toInt(), "Carbs" to 0xFFFBBF24.toInt(), "Fat" to 0xFF60A5FA.toInt())
            col.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) }
                tiles.forEachIndexed { i, t ->
                    addView(LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL; background = RunUi.rounded(TILE, dp(10).toFloat()); setPadding(dp(8), dp(8), dp(8), dp(8))
                        addView(RunUi.text(ctx, t.name, colors[t.name] ?: Color.WHITE, 12f, true))
                        addView(RunUi.text(ctx, "", Color.WHITE, 15f, true).apply {
                            val sb = android.text.SpannableStringBuilder(String.format(Locale.US, "%.1f%%", t.actual)); val st = sb.length
                            sb.append(" / ${t.goal}%"); sb.setSpan(android.text.style.ForegroundColorSpan(RunUi.MUTED), st, sb.length, 0); sb.setSpan(android.text.style.RelativeSizeSpan(0.72f), st, sb.length, 0); text = sb })
                        addView(RunUi.text(ctx, "${t.remaining}g remaining", RunUi.MUTED, 11f))
                        val sc = when (t.status) { "On target" -> 0xFF4ADE80.toInt(); "High" -> 0xFFF87171.toInt(); else -> 0xFFFBBF24.toInt() }
                        addView(RunUi.text(ctx, t.status, sc, 12f, true).apply { setPadding(0, dp(3), 0, 0) })
                        setOnClickListener { macroSources(act, nd2, t) }
                    }, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply { if (i < 2) marginEnd = dp(6) })
                }
            })
        }
        if (nd.micros.isNotEmpty()) {
            qualityBar(act, col, "Top 7 micronutrient quality", nd.microScore, nd.microLabel(), microNote(nd))
            micros(act, col, nd, header = false)
            col.addView(RunUi.text(ctx, "Tap a nutrient to see which foods it came from.", RunUi.DIM, 11f).apply { setPadding(0, dp(4), 0, 0) })
            Insights.gapFoods(act, nd).takeIf { it.isNotEmpty() }?.let { gaps ->
                col.addView(RunUi.text(ctx, "🥕 Fill the gaps (from your Healthy 7+ foods)", 0xFF5FD38D.toInt(), 13f, true).apply { setPadding(0, dp(10), 0, 0) })
                gaps.forEach { (m, foods) -> col.addView(RunUi.text(ctx, "Low on ${m.name} (${Math.round(m.pct)}%): " + foods.joinToString(" · "), Color.WHITE, 12f).apply { setPadding(0, dp(3), 0, 0) }) }
            }
            fold(act, col, "Nutrient Interactions") { box ->
                box.addView(RunUi.text(ctx, "How these 12 can affect each other -- mostly meal-timing or supplement-dose issues, not a concern from food alone.", RunUi.DIM, 11f))
                INTERACTIONS.forEach { (k, v) -> box.addView(RunUi.text(ctx, k, Color.WHITE, 13f, true).apply { setPadding(0, dp(8), 0, 0) }); box.addView(RunUi.text(ctx, v, RunUi.MUTED, 12f)) }
            }
        }
    }

    private val INTERACTIONS = listOf(
        "Calcium + Iron" to "Calcium blocks iron absorption when eaten together. Take iron-rich food or a supplement 2+ hours apart from calcium.",
        "Sodium → Calcium" to "High sodium increases how much calcium is lost in urine. A long-term bone-health concern, not an immediate one.",
        "Magnesium → Potassium" to "Low magnesium makes it hard for the kidneys to retain potassium. Low potassium that won't correct on its own often means magnesium needs fixing first.",
        "Calcium + Magnesium" to "Share gut absorption pathways. High-dose supplements of one can modestly reduce absorption of the other; minor from food alone.",
        "Vitamin D + Calcium" to "Vitamin D improves calcium absorption. A helpful pairing, not a conflict.",
        "Magnesium → Vitamin D" to "Magnesium is needed to activate vitamin D. Low magnesium can blunt vitamin D's effectiveness even when vitamin D intake looks fine.",
        "Vitamin C + Iron" to "Vitamin C improves absorption of plant-based (non-heme) iron when eaten together. Pair iron-rich meals with a vitamin C-rich food for better uptake.",
        "Zinc + Iron" to "High-dose iron supplements (25mg+) can reduce zinc absorption when taken together. Space iron and zinc supplements a few hours apart; iron from fortified food isn't a concern.",
        "Fiber → Iron, Zinc" to "Phytates in whole grains, beans, and nuts bind iron and zinc, reducing absorption from high-fiber plant meals. Not a concern as part of a varied diet overall.",
        "Caffeine → Calcium" to "Caffeine modestly increases calcium loss in urine, roughly offset by 1-2 tablespoons of milk per cup. Not established as a bone-health risk if calcium intake is otherwise adequate.",
        "Caffeine → Iron" to "Coffee and tea can cut non-heme (plant-based) iron absorption by roughly 60-80% when drunk with a meal. Wait 1-2 hours after iron-rich food before coffee or tea.",
        "Vitamin B12" to "No meaningful interaction with the other eleven nutrients at normal intake levels.",
        "Sugar" to "No meaningful absorption interaction with the other tracked nutrients -- tracked separately as a diet-quality marker, not a mineral/vitamin interaction.",
    )

    /** A ▸ heading that opens / closes [build]'s content (closed at first). */
    private val folds = HashSet<String>()
    private fun fold(act: Activity, col: LinearLayout, title: String, build: (LinearLayout) -> Unit) {
        val ctx = act; fun dp(v: Int) = RunUi.dp(ctx, v)
        val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; visibility = if (title in folds) android.view.View.VISIBLE else android.view.View.GONE; setPadding(dp(4), 0, 0, dp(4)) }
        val head = RunUi.text(ctx, (if (title in folds) "▾  " else "▸  ") + title, 0xFFCBD5E1.toInt(), 13f, true).apply { setPadding(0, dp(12), 0, dp(2)) }
        head.setOnClickListener { val open = body.visibility != android.view.View.VISIBLE; body.visibility = if (open) android.view.View.VISIBLE else android.view.View.GONE
            head.text = (if (open) "▾  " else "▸  ") + title; if (open) folds += title else folds -= title }
        col.addView(head); build(body); col.addView(body)
    }

    /** Change Ratio — that day only, like the Command Center (each 5–80%, total 100%). */
    fun ratioDialog(act: Activity, nd: Nutrition.Day) {
        val ctx = act; fun dp(v: Int) = RunUi.dp(ctx, v)
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        fun f(l: String, v: Int) = android.widget.EditText(ctx).apply { setText(v.toString()); inputType = android.text.InputType.TYPE_CLASS_NUMBER }
            .also { box.addView(android.widget.TextView(ctx).apply { text = l; setPadding(0, dp(6), 0, 0) }); box.addView(it) }
        val p = f("Protein %", nd.mtProt.takeIf { it > 0 } ?: 15); val c = f("Carbs %", nd.mtCarbs.takeIf { it > 0 } ?: 60); val fa = f("Fat %", nd.mtFat.takeIf { it > 0 } ?: 25)
        box.addView(android.widget.TextView(ctx).apply { text = "Total must equal 100%. Applies to this day only."; textSize = 12f; setPadding(0, dp(8), 0, 0) })
        android.app.AlertDialog.Builder(ctx).setTitle("Change Ratio").setView(box)
            .setPositiveButton("Save Ratio") { _, _ ->
                val pv = p.text.toString().toIntOrNull() ?: 0; val cv = c.text.toString().toIntOrNull() ?: 0; val fv = fa.text.toString().toIntOrNull() ?: 0
                if (listOf(pv, cv, fv).any { it < 5 || it > 80 } || pv + cv + fv != 100) { android.widget.Toast.makeText(ctx, "Protein, carbs, and fat must each be 5–80% and total exactly 100%. Current total: ${pv + cv + fv}%.", android.widget.Toast.LENGTH_LONG).show(); return@setPositiveButton }
                NutriDay.setRatio(ctx, nd.date, org.json.JSONObject().put("enabled", true).put("prot", pv).put("carbs", cv).put("fat", fv)); act.recreate()
            }
            .setNeutralButton("Use Automatic") { _, _ -> NutriDay.setRatio(ctx, nd.date, org.json.JSONObject().put("enabled", false)); act.recreate() }
            .setNegativeButton("Cancel", null).show()
    }

    /** Which foods a macro came from (tap a Protein / Carbs / Fat tile): grams, calories and share, plus the target. */
    fun macroSources(act: Activity, nd: Nutrition.Day, t: Nutrition.Day.MacroTile) {
        val per = if (t.name == "Fat") 9 else 4
        val rows = nd.foods.map { it to it.total(when (t.name) { "Protein" -> it.prot; "Carbs" -> it.carbs; else -> it.fat }) }.filter { it.second > 0.05 }.sortedByDescending { it.second }
        val tot = rows.sumOf { it.second }
        val kcal = if (nd.mtCal > 0) nd.mtCal else nd.target
        val goalG = Math.round(kcal * (t.goal / 100.0) / per)
        fun g(v: Double) = if (v >= 10) Math.round(v).toString() else (Math.round(v * 10) / 10.0).toString()
        val head = "${g(tot)}g so far (${Math.round(tot * per)} kcal · " + String.format(Locale.US, "%.1f", t.actual) + "% of calories)\n" +
            "Target ${t.goal}% of ${Nutrition.kcal(kcal)} kcal ≈ ${goalG}g · ${t.remaining}g remaining · ${t.status}"
        val msg = if (rows.isEmpty()) "$head\n\nNone of the foods logged this day have ${t.name.lowercase()} recorded yet."
            else "$head\n\nFrom ${rows.size} logged food${if (rows.size == 1) "" else "s"}:\n\n" + rows.joinToString("\n") { (f, v) ->
                "${f.name} (${Nutrition.MEAL_NAMES[f.meal] ?: f.meal}) — ${g(v)}g · ${if (tot > 0) Math.round(v / tot * 100) else 0}%" }
        android.app.AlertDialog.Builder(act).setTitle("${t.name} sources").setMessage(msg).setPositiveButton("OK", null).show()
    }

    /** Which foods a micronutrient came from (tap a tile), like the Command Center's sources list. */
    fun microSources(act: Activity, nd: Nutrition.Day, m: Nutrition.Micro) {
        val rows = nd.foods.map { it to it.total(it.micros[m.field]) }.filter { it.second > 0.0001 }.sortedByDescending { it.second }
        val tot = rows.sumOf { it.second }
        fun fmt(v: Double) = if (m.unit == "mg" && v >= 100) String.format(Locale.US, "%,d", Math.round(v)) else (Math.round(v * 10) / 10.0).toString()
        val msg = if (rows.isEmpty()) "None of the foods logged this day have ${m.name.lowercase()} data recorded yet. Items added without full nutrition info show 0 until edited."
            else "From ${rows.size} logged food${if (rows.size == 1) "" else "s"} · ${fmt(tot)}${m.unit} total\n\n" + rows.joinToString("\n") { (f, v) ->
                "${f.name} (${Nutrition.MEAL_NAMES[f.meal] ?: f.meal}) — ${fmt(v)}${m.unit} · ${if (tot > 0) Math.round(v / tot * 100) else 0}%" }
        android.app.AlertDialog.Builder(act).setTitle("${m.name} sources").setMessage(msg).setPositiveButton("OK", null).show()
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
                        setOnClickListener { microSources(act, nd, m) }
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
        val today = NutriDay.day(ctx)
        fun field(hint: String, v: Double) = android.widget.EditText(ctx).apply {
            this.hint = hint; inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            if (v > 0) setText(Hydration.lb(v))
        }
        box.addView(android.widget.TextView(ctx).apply { text = "Wake-up weight (lb)"; setPadding(0, dp(4), 0, 0) })
        val wake = field("e.g. 172.4", today?.weight ?: 0.0); box.addView(wake)
        box.addView(android.widget.TextView(ctx).apply { text = "Pre-run weight (lb) — right before you head out"; setPadding(0, dp(8), 0, 0) })
        val pre = field("e.g. 173.0", today?.preWeight ?: 0.0); box.addView(pre)
        box.addView(android.widget.TextView(ctx).apply { text = "Post-run weight (lb)"; setPadding(0, dp(8), 0, 0) })
        val post = field("e.g. 171.2", today?.postWeight ?: 0.0); box.addView(post)
        box.addView(android.widget.TextView(ctx).apply {
            text = (if (Standalone.on(ctx)) "" else "Also goes to the Command Center (its Wellness weight fields). ") + "Sweat rate uses pre-run → post-run (or wake-up → post-run when there's no pre-run weight), plus sports drinks logged. Drinks are in Liquids on the Nutrition tab."; textSize = 12f; setPadding(0, dp(8), 0, 0)
        })
        android.app.AlertDialog.Builder(ctx).setTitle("Weight · " + if (NutriDay.isToday()) "today" else java.time.LocalDate.parse(NutriDay.date()).format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US))).setView(ScrollView(ctx).apply { addView(box) })
            .setPositiveButton("Save") { _, _ ->
                fun num(e: android.widget.EditText) = e.text.toString().trim().toDoubleOrNull()?.takeIf { it in 70.0..600.0 } ?: 0.0
                val w = num(wake); val p = num(post); val pr = num(pre)
                if (pr != (today?.preWeight ?: 0.0)) HydrationSync.change(ctx, "preWeight", value = pr)
                if (w != (today?.weight ?: 0.0)) HydrationSync.change(ctx, "weight", value = w)
                if (p != (today?.postWeight ?: 0.0)) HydrationSync.change(ctx, "postWeight", value = p)
            }
            .setOnDismissListener { refresh() }
            .show()
    }
}
