package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.view.Gravity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import io.github.michealsizemore1.beyondstride.RunMath

/** Fitness details: training load, Zone 2 trend, personal bests, aerobic decoupling (from the home screen's Fitness card). */
class FitnessActivity : Activity() {
    private var shownOnce = false   // after the first time, a tab switch shows at once and refreshes right after
    override fun onResume() {
        super.onResume(); if (shownOnce) RunUi.afterShown(this) { render() } else { shownOnce = true; render() }
        Thread { if (NutritionSync.refreshIfStale(this, 60_000L)) runOnUiThread { if (!isFinishing) render() } }.start()
    }

    private val panels = HashSet<String>()

    private fun render() {
        val ctx = this
        val dp = { v: Int -> RunUi.dp(ctx, v) }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(40), dp(16), dp(32)) }
        col.addView(RunUi.text(this, "Fitness", Color.WHITE, 26f, true))
        val runs = PhoneRuns.list(this).mapNotNull { PhoneRuns.read(it) }
        val I = io.github.michealsizemore1.beyondstride.Insights
        val hrMax = RunSettings.hrMax(this); val hrRest = RunSettings.hrRest(this)
        col.addView(RunUi.section(this, "OVERVIEW"))
        // Overview tiles (charts below).
        if (runs.isNotEmpty()) {
            val (z2now, z2before) = I.z2Trend(runs.take(200), hrMax, hrRest)
            val D = io.github.michealsizemore1.beyondstride.Decoupling
            val now = System.currentTimeMillis()
            val dec = runs.take(120).filter { now - it.optLong("start") < 28 * 86_400_000L }
                .mapNotNull { r -> D.of(r)?.takeIf { it.steady && it.minutes >= 30 && it.main != null }?.main }
            val cpNow = RunSettings.cp(this)
            val cpE = io.github.michealsizemore1.beyondstride.CriticalPower.estimate(runs.take(200), currentCp = cpNow)
            val rec = I.records(runs.take(200))
            val latestPb = rec.maxByOrNull { it.value.start }
            fun tile(label: String, value: String, sub: String, color: Int, onClick: () -> Unit) = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                background = RunUi.rounded(RunUi.CARD, dp(16).toFloat())
                setPadding(dp(12), dp(10), dp(12), dp(10))
                addView(RunUi.text(ctx, label, RunUi.MUTED, 11f, true))
                addView(RunUi.text(ctx, value, color, 18f, true))
                addView(RunUi.text(ctx, sub, RunUi.MUTED, 11f))
                setOnClickListener { onClick() }
            }
            fun row(a: android.view.View, b: android.view.View) = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }
                addView(a, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply { marginEnd = dp(8) })
                addView(b, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f))
            }
            val openFit = { }
            col.addView(row(
                tile("ZONE 2 PACE", z2now?.let { RunMath.paceFor(it, RunMath.METERS_PER_MILE) + " /mi" } ?: "—",
                    z2now?.let { n -> z2before?.let { b -> val d = b - n; if (d >= 0) "${d}s faster than before" else "${-d}s slower than before" } ?: "last 4 weeks" } ?: "no Zone 2 runs yet",
                    Color.WHITE, openFit),
                tile("DECOUPLING", if (dec.isEmpty()) "—" else D.text(dec.average()), if (dec.isEmpty()) "needs a 30+ min steady run" else D.rating(dec.average()),
                    if (dec.isEmpty()) Color.WHITE else D.color(dec.average()), openFit),
            ))
            col.addView(row(
                tile("CRITICAL POWER", "$cpNow W", cpE.cp?.let { if (kotlin.math.abs(it - cpNow) >= 3) "runs say $it W ▸" else "matches your runs" } ?: "needs a hard effort to update",
                    Color.WHITE) { startActivity(Intent(ctx, ReadinessActivity::class.java)) },
                tile("FASTEST EFFORTS", latestPb?.let { I.clock(it.value.sec) } ?: "—",
                    latestPb?.let { I.pbName(it.key) + " · " + SimpleDateFormat("MMM d", Locale.US).format(Date(it.value.start)) } ?: "after your first runs",
                    if (latestPb != null) 0xFFF2C94C.toInt() else Color.WHITE, openFit),
            ))
        }
        // Resting heart rate: sleeping HR each night (Samsung Health), last 30 nights.
        // With the Life tab on, it lives in Life → Wellness with the other body numbers (one place for each number).
        if (!LifeTab.on(this)) run {
            val R = io.github.michealsizemore1.beyondstride.Readiness
            val nights = io.github.michealsizemore1.beyondstride.ReadinessSync.nights(this)
                .filter { it.sleepHr > 0 && R.daysBetween(it.day, R.today()) in 0..29 }.sortedBy { it.day }
            col.addView(RunUi.section(this, "RESTING HEART RATE"))
            col.addView(RunUi.card(this).apply {
                if (nights.isEmpty()) addView(RunUi.text(ctx, "Comes from sleeping heart rate in Samsung Health (Readiness → Connect sleep).", RunUi.MUTED, 13f))
                else {
                    val tr = R.rhrTrend(nights)
                    addView(RunUi.text(ctx, String.format(Locale.US, "%.1f bpm", tr?.recent ?: nights.last().sleepHr.toDouble()) + "  7-night average" +
                        (tr?.change?.let { String.format(Locale.US, "  ·  %s%.1f vs week before", if (it > 0) "+" else "", it) } ?: ""), Color.WHITE, 15f, true))
                    // Bars: lower is better, so the scale starts a little under your lowest night.
                    val lo = nights.minOf { it.sleepHr } - 3; val hi = maxOf(nights.maxOf { it.sleepHr }, lo + 6)
                    addView(LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM
                        setPadding(0, dp(8), 0, 0)
                        nights.forEach { n ->
                            addView(LinearLayout(ctx).apply {
                                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                                val high = tr != null && n.sleepHr - tr.recent >= 5
                                if (nights.size <= 14) addView(RunUi.text(ctx, "${n.sleepHr}", RunUi.MUTED, 9f).apply { gravity = Gravity.CENTER })
                                addView(android.view.View(ctx).apply { background = RunUi.rounded(if (high) 0xFFEF4444.toInt() else 0xFFFF6B6B.toInt(), dp(2).toFloat()) },
                                    LinearLayout.LayoutParams(dp(if (nights.size <= 14) 12 else 6), dp(8 + (n.sleepHr - lo) * 60 / (hi - lo))))
                            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                        }
                    })
                    addView(LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        addView(RunUi.text(ctx, nights.first().day.substring(5).replace("-", "/"), RunUi.DIM, 9f), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                        addView(RunUi.text(ctx, nights.last().day.substring(5).replace("-", "/"), RunUi.DIM, 9f))
                    })
                    tr?.let { addView(RunUi.text(ctx, it.note, it.color, 13f).apply { setPadding(0, dp(6), 0, 0) }) }
                    addView(RunUi.text(ctx, "Sleeping heart rate (lowest overnight readings). Your zones use ${RunSettings.hrRest(ctx)} bpm.", RunUi.DIM, 11f).apply { setPadding(0, dp(4), 0, 0) })
                }
                setOnClickListener { startActivity(Intent(ctx, ReadinessActivity::class.java)) }
            })
        }

        // Training load, Zone 2 fitness, personal bests.
        run {
            val all = PhoneRuns.list(this).take(200).mapNotNull { PhoneRuns.read(it) }
            if (all.isNotEmpty()) {
                val hrMax = RunSettings.hrMax(this); val hrRest = RunSettings.hrRest(this)
                // Training load.
                val ld = io.github.michealsizemore1.beyondstride.Insights.load(all)
                col.addView(RunUi.section(this, "TRAINING LOAD"))
                col.addView(RunUi.card(this).apply {
                    addView(RunUi.text(ctx, ld.status, ld.color, 18f, true))
                    addView(RunUi.statGrid(ctx, listOfNotNull(
                        Triple("${ld.acute}", "stress, last 7 days", Color.WHITE),
                        ld.ratio?.let { Triple("${ld.chronicWeek}", "usual week", Color.WHITE) },
                        ld.ratio?.let { Triple(String.format(java.util.Locale.US, "%.2f", it), "ratio (0.8–1.3 healthy)", ld.color) },
                        Triple(String.format(java.util.Locale.US, "%.1f", ld.milesWeek), "miles, last 7 days", Color.WHITE),
                        ld.ratio?.let { Triple(String.format(java.util.Locale.US, "%.1f", ld.milesAvg), "usual weekly miles", Color.WHITE) },
                    )))
                    addView(RunUi.text(ctx, ld.advice, RunUi.MUTED, 12f).apply { setPadding(0, dp(6), 0, 0) })
                    // The same 7-day vs usual ratio by miles, from every activity (Garmin, Stryd, manual too).
                    addView(RunUi.text(ctx, "By stress (heart rate & power, BeyondStride runs)" + (ld.ratio?.let { String.format(java.util.Locale.US, ": %.2f", it) } ?: ": building baseline"), ld.color, 12f, true).apply { setPadding(0, dp(10), 0, 0) })
                    ActivityStore.acwr(ctx)?.let { (r, ac, ch) ->
                        val (c, l) = when { r < 0.8 -> 0xFF5DADE2.toInt() to "Low load (easing back)"; r <= 1.3 -> 0xFF27AE60.toInt() to "Sweet spot"; r <= 1.5 -> 0xFFF39C12.toInt() to "Elevated — watch for soreness"; else -> 0xFFE74C3C.toInt() to "High — classic \"too much too soon\" range" }
                        addView(RunUi.text(ctx, String.format(java.util.Locale.US, "By miles (every activity): %.2f · %s", r, l), c, 12f, true).apply { setPadding(0, dp(4), 0, 0) })
                        addView(RunUi.text(ctx, String.format(java.util.Locale.US, "Last 7 days’ daily average (%.1f mi/day) vs. last 28 days’ (%.1f mi/day).", ac, ch), RunUi.DIM, 11f))
                        ld.ratio?.let { s -> when {
                            s - r > 0.2 -> "Miles steady but stress rising — runs are getting harder."
                            r - s > 0.2 -> "Miles rising faster than stress — mostly easy running."
                            else -> null }?.let { addView(RunUi.text(ctx, it, Color.WHITE, 12f).apply { setPadding(0, dp(4), 0, 0) }) } }
                    }
                    addView(RunUi.text(ctx, "Estimates from your logged training — not a medical assessment. 0.8–1.3 is the healthy range.", RunUi.DIM, 10f).apply { setPadding(0, dp(4), 0, 0) })
                })
                // 📈 Training Status · 💓 HRV Status · ⏱️ Recovery (Garmin / Coros style).
                try {
                    val C = io.github.michealsizemore1.beyondstride.Coaching
                    val today = io.github.michealsizemore1.beyondstride.Readiness.today()
                    val checks = io.github.michealsizemore1.beyondstride.ReadinessSync.checks(this)
                    val hv = C.hrvStatus(checks, today)
                    val (zn, zb) = io.github.michealsizemore1.beyondstride.Insights.z2Trend(all, hrMax, hrRest)
                    val race = try { PlanStore.data(this).optJSONObject("current")?.let { PlanEngine.race(it) }?.takeIf { it.length == 10 } } catch (e: Exception) { null }
                    val toRace = race?.let { java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), java.time.LocalDate.parse(it)) }?.takeIf { it >= 0 }
                    val ts = C.trainingStatus(ld.ratio, hv, zn, zb, toRace)
                    val rdScore = try { io.github.michealsizemore1.beyondstride.Readiness.compute(checks, io.github.michealsizemore1.beyondstride.ReadinessSync.nights(this), all.take(120), today)?.score } catch (e: Exception) { null }
                    val rc = C.recovery(all.take(20), rdScore)
                    col.addView(RunUi.section(this, "TRAINING STATUS"))
                    col.addView(RunUi.card(this).apply {
                        addView(RunUi.text(ctx, "📈 " + ts.name, ts.color, 20f, true))
                        addView(RunUi.text(ctx, ts.why, RunUi.MUTED, 12f).apply { setPadding(0, dp(2), 0, dp(8)) })
                        addView(RunUi.text(ctx, "⏱️ " + C.recoveryText(rc), if (rc == null) 0xFF22C55E.toInt() else Color.WHITE, 14f, true))
                        rc?.let { addView(RunUi.text(ctx, "Your last run needs about ${it.totalHours} h in all (bigger, harder runs take longer; a low readiness adds time). Easy running is fine while recovering.", RunUi.DIM, 11f)) }
                        addView(RunUi.text(ctx, "💓 HRV Status: " + (hv?.status ?: "needs 3 morning checks this week"), hv?.color ?: RunUi.MUTED, 14f, true).apply { setPadding(0, dp(10), 0, 0) })
                        hv?.let { h ->
                            addView(RunUi.statGrid(ctx, listOfNotNull(
                                Triple(String.format(java.util.Locale.US, "%.0f ms", h.avg7), "7-day average (${h.n7} checks)", h.color),
                                if (h.lo > 0) Triple(String.format(java.util.Locale.US, "%.0f–%.0f", h.lo, h.hi), "your normal range (ms)", Color.WHITE) else null)))
                            addView(RunUi.text(ctx, h.note, RunUi.MUTED, 12f).apply { setPadding(0, dp(4), 0, 0) })
                        }
                        addView(RunUi.text(ctx, "Training Status combines your 7-day load vs usual, HRV Status (Polar morning checks vs your last 2 months) and whether Zone 2 pace is getting faster." +
                            (toRace?.let { " Race day in $it days." } ?: ""), RunUi.DIM, 10f).apply { setPadding(0, dp(6), 0, 0) })
                    })
                } catch (e: Exception) { }
                // Zone 2 fitness: pace while HR is in Zone 2, run by run.
                val z2 = all.mapNotNull { r -> io.github.michealsizemore1.beyondstride.Insights.zone2(r, hrMax, hrRest)?.let { r.optLong("start") to it } }.take(12)
                if (z2.isNotEmpty()) {
                    val (nowP, beforeP) = io.github.michealsizemore1.beyondstride.Insights.z2Trend(all, hrMax, hrRest)
                    col.addView(RunUi.section(this, "ZONE 2 FITNESS"))
                    col.addView(RunUi.card(this).apply {
                        nowP?.let { n ->
                            addView(RunUi.text(ctx, "Zone 2 pace " + io.github.michealsizemore1.beyondstride.RunMath.paceFor(n, io.github.michealsizemore1.beyondstride.RunMath.METERS_PER_MILE) + " /mi, last 4 weeks", Color.WHITE, 16f, true))
                            addView(RunUi.text(ctx, beforeP?.let { b -> val d = b - n
                                if (d >= 0) "${d} s/mi faster than the 4 weeks before — aerobic fitness is improving" else "${-d} s/mi slower than the 4 weeks before — heat, fatigue or terrain?" }
                                ?: "Faster pace at the same heart rate = fitter. The comparison starts after 4 weeks.", RunUi.MUTED, 12f))
                        }
                        // One bar per run (taller = faster in Zone 2).
                        val paces = z2.map { it.second.secPerMile }
                        val slow = paces.max() + 15; val fast = paces.min() - 15
                        addView(LinearLayout(ctx).apply {
                            orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.BOTTOM
                            setPadding(0, dp(10), 0, 0)
                            z2.reversed().forEach { (st, z) ->
                                addView(LinearLayout(ctx).apply {
                                    orientation = LinearLayout.VERTICAL; gravity = android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.BOTTOM
                                    addView(RunUi.text(ctx, io.github.michealsizemore1.beyondstride.RunMath.paceFor(z.secPerMile, io.github.michealsizemore1.beyondstride.RunMath.METERS_PER_MILE), RunUi.MUTED, 9f).apply { gravity = android.view.Gravity.CENTER })
                                    addView(android.view.View(ctx).apply { background = RunUi.rounded(0xFF22C55E.toInt(), dp(3).toFloat()) },
                                        LinearLayout.LayoutParams(dp(14), dp(10 + ((slow - z.secPerMile) * 60 / maxOf(1, slow - fast)))))
                                    addView(RunUi.text(ctx, java.text.SimpleDateFormat("M/d", java.util.Locale.US).format(java.util.Date(st)), RunUi.DIM, 9f).apply { gravity = android.view.Gravity.CENTER })
                                }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                            }
                        })
                    })
                }
                // Personal bests.
                val rec = io.github.michealsizemore1.beyondstride.Insights.records(all)
                if (rec.isNotEmpty()) {
                    col.addView(RunUi.section(this, "FASTEST EFFORTS"))
                    col.addView(RunUi.card(this).apply {
                        rec.forEach { (k, pb) ->
                            addView(LinearLayout(ctx).apply {
                                orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(4), 0, dp(4))
                                addView(RunUi.text(ctx, io.github.michealsizemore1.beyondstride.Insights.pbName(k), Color.WHITE, 15f, true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                                addView(RunUi.text(ctx, io.github.michealsizemore1.beyondstride.Insights.clock(pb.sec) + "   " + java.text.SimpleDateFormat("MMM d", java.util.Locale.US).format(java.util.Date(pb.start)),
                                    0xFFF2C94C.toInt(), 15f, true))
                            })
                        }
                        addView(RunUi.text(ctx, "Fastest stretch inside any run recorded in BeyondStride.", RunUi.DIM, 11f).apply { setPadding(0, dp(4), 0, 0) })
                    })
                }
            }
        }

        // 🎯 Aerobic decoupling vs your goals: easy / long runs ≤ 5%, quality runs (tempo, threshold, intervals…) 5–10%.
        run {
            val D = io.github.michealsizemore1.beyondstride.Decoupling
            data class P(val start: Long, val pct: Double, val quality: Boolean)
            val all = PhoneRuns.list(this).take(150).mapNotNull { f -> PhoneRuns.read(f) }.mapNotNull { r ->
                val d = D.of(r) ?: return@mapNotNull null; val m = d.main ?: return@mapNotNull null
                if (d.minutes < 20) return@mapNotNull null
                val q = D.isQuality(r)
                // Easy runs must be steady to count; a tempo with a warm-up and cool-down can look uneven, so quality runs count either way.
                if (!q && !d.steady) return@mapNotNull null
                P(r.optLong("start"), m, q)
            }
            if (all.isNotEmpty()) {
                col.addView(RunUi.section(this, "AEROBIC DECOUPLING"))
                col.addView(RunUi.card(this).apply {
                    addView(RunUi.text(ctx, "How much heart rate drifts up against the same power or pace, first half to second. Goals: easy runs 5% or under; quality runs 5–10%.", RunUi.MUTED, 12f))
                    val now = System.currentTimeMillis()
                    listOf(false to "🟢 Easy & long runs", true to "🔥 Quality runs (tempo, threshold, intervals…)").forEach { (q, title) ->
                        val pts = all.filter { it.quality == q }.take(10)
                        addView(LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, dp(14), 0, 0)
                            addView(RunUi.text(ctx, title, Color.WHITE, 14f, true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                            addView(RunUi.text(ctx, "goal " + D.goalShort(q), RunUi.ACCENT, 12f, true)) })
                        if (pts.isEmpty()) { addView(RunUi.text(ctx, if (q) "No quality runs with decoupling yet (20+ min after warm-up)." else "No steady easy runs with decoupling yet (20+ min after warm-up).", RunUi.DIM, 12f).apply { setPadding(0, dp(4), 0, 0) }); return@forEach }
                        val recent = pts.filter { now - it.start < 28 * 86_400_000L }.map { it.pct }
                        val use = recent.ifEmpty { pts.map { it.pct } }
                        val avg = use.average()
                        val hit = pts.count { D.onTarget(it.pct, q) }
                        addView(RunUi.text(ctx, D.text(avg) + " average" + (if (recent.isNotEmpty()) ", last 4 weeks" else ", recent runs") + " · " + D.rating(avg, q).lowercase(), D.color(avg, q), 17f, true).apply { setPadding(0, dp(4), 0, 0) })
                        addView(RunUi.text(ctx, "$hit of ${pts.size} recent runs on target", RunUi.MUTED, 12f))
                        // One bar per run, oldest → newest, colored against this kind of run's goal; dashed goal line.
                        val maxV = 20.0
                        val chart = object : android.view.View(ctx) {
                            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                            val list = pts.reversed()
                            override fun onDraw(c: android.graphics.Canvas) {
                                val w = width.toFloat(); val labelH = dp(14).toFloat(); val topH = dp(14).toFloat(); val h = height - labelH - topH
                                val slot = w / maxOf(list.size, 1); val bw = (slot * 0.55f).coerceAtMost(dp(22).toFloat())
                                fun y(v: Double) = topH + h - (v.coerceIn(0.0, maxV) / maxV * h).toFloat()
                                p.textAlign = android.graphics.Paint.Align.CENTER
                                list.forEachIndexed { i, pt ->
                                    val cx = slot * i + slot / 2
                                    p.color = D.color(pt.pct, q); p.style = android.graphics.Paint.Style.FILL
                                    c.drawRoundRect(android.graphics.RectF(cx - bw / 2, y(pt.pct).coerceAtMost(topH + h - dp(3)), cx + bw / 2, topH + h), dp(3).toFloat(), dp(3).toFloat(), p)
                                    p.color = RunUi.MUTED; p.textSize = dp(9).toFloat(); c.drawText(String.format(java.util.Locale.US, "%.0f", pt.pct), cx, y(pt.pct).coerceAtMost(topH + h - dp(3)) - dp(3), p)
                                    p.color = RunUi.DIM; c.drawText(java.text.SimpleDateFormat("M/d", java.util.Locale.US).format(java.util.Date(pt.start)), cx, height - dp(2).toFloat(), p)
                                }
                                // Goal lines: 5% for easy; 5% and 10% for quality (the band between is the target).
                                p.style = android.graphics.Paint.Style.STROKE; p.strokeWidth = dp(1).toFloat(); p.color = RunUi.ACCENT
                                p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(dp(4).toFloat(), dp(4).toFloat()), 0f)
                                (if (q) listOf(5.0, 10.0) else listOf(5.0)).forEach { g -> c.drawLine(0f, y(g), w, y(g), p) }
                                p.pathEffect = null
                            }
                        }
                        addView(chart, LinearLayout.LayoutParams(MATCH_PARENT, dp(110)).apply { topMargin = dp(6) })
                    }
                    addView(RunUi.text(ctx, "A run counts as quality when its planned workout is a tempo, threshold, interval, hill, progression or race (or it was run at 0.85+ intensity). Easy runs need a steady effort to count.", RunUi.DIM, 11f).apply { setPadding(0, dp(10), 0, 0) })
                })
            }
        }
        // From Activities: Estimated Race Times, Heat & Humidity, Training Zones (each collapsible).
        run {
            val acts = ActivityStore.list(this)
            col.addView(RunUi.section(this, "RACE FITNESS & CONDITIONS"))
            ActivityAnalysis(this, panels) { render() }.apply { racePredictor(col, acts); heatHumidity(col, acts) }
            ZonesCard.build(this, col, panels) { render() }
            RacePacing.card(this, col, acts, panels) { render() }
        }
        RunUi.styleGroups(col)
        setContentView(RunUi.tabbed(this, ScrollView(this).apply {
            setBackgroundColor(RunUi.BG)
            addView(col, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }, RunUi.TAB_FITNESS))
    }
}
