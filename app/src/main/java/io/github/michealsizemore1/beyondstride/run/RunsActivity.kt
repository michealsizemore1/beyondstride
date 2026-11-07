package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import io.github.michealsizemore1.beyondstride.Hydration
import io.github.michealsizemore1.beyondstride.RunMath
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The phone's Running home: runs from the watch, and where they've been sent. */
open class RunsActivity : Activity() {
    private var askedSms = false

    /** True when this file was installed on the wrong kind of device (a message is shown instead). */
    private var wrongDevice = false

    private val ui get() = RunUi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (io.github.michealsizemore1.beyondstride.WrongDevice.check(this, isWatchApp = false)) { wrongDevice = true; return }
        // One-time cleanup: cancel any leftover background Stryd lookups from older versions.
        getSharedPreferences("runflags", 0).let { f -> if (!f.getBoolean("strydJobsCleared", false)) {
            try { val wm = androidx.work.WorkManager.getInstance(this)
                PhoneRuns.list(this).forEach { wm.cancelUniqueWork("stryd-" + it.nameWithoutExtension) } } catch (e: Exception) { }
            f.edit().putBoolean("strydJobsCleared", true).apply()
        } }
        handleLink(intent)
        // Clear the sign-in left from the removed Strava connection.
        try { deleteSharedPreferences("strava") } catch (e: Exception) { }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    override fun onResume() {
        io.github.michealsizemore1.beyondstride.Theme.load(this, "runsettings")
        super.onResume()
        if (wrongDevice) return
        // LiveTrack is on (e.g. restored from a backup) but this app may not text yet: ask once per launch.
        if (!askedSms && getSharedPreferences("runsettings", 0).getBoolean("liveOn", false) && Live.smsOn(this) && Live.contacts(this).isNotEmpty() &&
            checkSelfPermission(android.Manifest.permission.SEND_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            askedSms = true
            requestPermissions(arrayOf(android.Manifest.permission.SEND_SMS), 31)
        }
        WorkoutStore.pushToWatch(this)   // keeps the watch's "today" plan current
        render()
        // New BeyondStride build out? (asks GitHub at most every 3 hours)
        Thread {
            val had = io.github.michealsizemore1.beyondstride.Updater.available(this)?.build
            val now = io.github.michealsizemore1.beyondstride.Updater.check(this, false)?.build
            if (now != had) runOnUiThread { if (!isFinishing) render() }
        }.start()
        // Today's nutrition from the Command Center.
        if (NutritionSync.justOpened) NutritionSync.refreshAfterNutrition(this) { runOnUiThread { if (!isFinishing) render() } }
        else Thread { if (NutritionSync.refreshIfStale(this, 60_000L)) runOnUiThread { if (!isFinishing) render() } }.start()
        // Last night's sleep from Samsung Health (via Health Connect), for the readiness score.
        Thread {
            if (System.currentTimeMillis() - io.github.michealsizemore1.beyondstride.ReadinessSync.nightsAt(this) > 30 * 60_000L && SleepData.granted(this)) {
                SleepData.refresh(this); runOnUiThread { if (!isFinishing) render() }
            }
        }.start()
    }

    /** The Command Center's "Send plan to Running app" opens beyondstride://localhost/plan#<days>. */
    private fun handlePlanImport(data: android.net.Uri): Boolean {
        if (data.path?.startsWith("/plan") != true) return false
        val b64 = data.encodedFragment ?: return true
        Thread {
            val msg = try {
                val days = org.json.JSONObject(String(android.util.Base64.decode(b64, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)))
                val n = WorkoutStore.importPlan(this, days)
                "Received ${days.length()} days: $n workouts imported" +
                    (if (WorkoutStore.restDays > 0) ", ${WorkoutStore.restDays} rest days" else "") +
                    (if (WorkoutStore.keptEdited > 0) ", ${WorkoutStore.keptEdited} kept as you edited them" else "") +
                    ". Sent to your watch."
            } catch (e: Exception) { "Couldn't read the plan: ${e.message}" }
            runOnUiThread {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                startActivity(Intent(this, WorkoutsActivity::class.java).putExtra("tab", "plan"))
            }
        }.start()
        return true
    }

    /** Links into the app (the Command Center's "Send plan to Running app"). */
    private fun handleLink(i: Intent?) {
        val data = i?.data ?: return
        if (data.scheme != "beyondstride" && data.scheme != "scoresrun") return
        handlePlanImport(data)
    }

    private fun render() {
        PhoneStatus.push(this)
        // Opened from the watch's Command Center button notification.
        if (intent?.getBooleanExtra("sendCc", false) == true) {
            intent.removeExtra("sendCc")
            PhoneStatus.unsentCc(this).takeIf { it.isNotEmpty() }?.let { CommandCenter.send(this, it) }
        }
        val ctx = this
        val dp = { v: Int -> RunUi.dp(ctx, v) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(40), dp(16), dp(32))
        }
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(RunUi.text(ctx, if (this@RunsActivity is AllRunsActivity) "Runs" else "BeyondStride", Color.WHITE, 28f, true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(RunUi.button(ctx, "Settings", RunUi.CARD, Color.WHITE) {
                startActivity(Intent(ctx, RunSettingsActivity::class.java))
            })
        })

        val files = PhoneRuns.list(this)
        val runs = files.mapNotNull { PhoneRuns.read(it) }
        val showAll = this is AllRunsActivity
        if (!showAll) {
        val I = io.github.michealsizemore1.beyondstride.Insights
        val R = io.github.michealsizemore1.beyondstride.Readiness
        val hrMax = RunSettings.hrMax(this); val hrRest = RunSettings.hrRest(this)
        fun divider() = android.view.View(ctx).apply { setBackgroundColor(0x22FFFFFF) }
            .also { it.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(1)).apply { topMargin = dp(10); bottomMargin = dp(10) } }

        // 0. Update available.
        io.github.michealsizemore1.beyondstride.Updater.available(this)?.let { up ->
            col.addView(RunUi.card(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(RunUi.text(ctx, "⬆ Update available · build ${up.build}", RunUi.ACCENT, 16f, true))
                val st = RunUi.text(ctx, "You're on build ${io.github.michealsizemore1.beyondstride.Updater.currentBuild(ctx)}. Tap to install.", RunUi.MUTED, 12f)
                addView(st)
                setOnClickListener { io.github.michealsizemore1.beyondstride.Updater.start(this@RunsActivity, up) { st.text = it } }
            })
        }

        // 1. TODAY: readiness + today's workout (+ trail).
        val rd = R.compute(io.github.michealsizemore1.beyondstride.ReadinessSync.checks(this),
            io.github.michealsizemore1.beyondstride.ReadinessSync.nights(this), runs.take(120), R.today(), fuelYesterday = io.github.michealsizemore1.beyondstride.NutritionStore.yesterday(this))
        val todayW = WorkoutStore.plan(this).optJSONObject(WorkoutStore.dayFmt.format(Date()))
        col.addView(RunUi.section(this, "TODAY"))
        col.addView(RunUi.card(this).apply {
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                if (rd == null) addView(RunUi.text(ctx, "Readiness: do the 2½-min morning check on the watch ▸", RunUi.ACCENT, 15f, true))
                else {
                    addView(RunUi.text(ctx, "${rd.score}  ${rd.label}", rd.color, 22f, true))
                    if (!rd.checkedToday) addView(RunUi.text(ctx, "No morning check yet — based on sleep and training", RunUi.MUTED, 12f))
                    addView(RunUi.text(ctx, rd.parts.filter { it.pts >= 0 }.joinToString(" · ") { "${it.name} ${it.value}" }, RunUi.MUTED, 12f))
                    addView(RunUi.text(ctx, rd.advice.substringBefore(". ") + ".", Color.WHITE, 13f).apply { setPadding(0, dp(4), 0, 0) })
                }
                setOnClickListener { startActivity(Intent(ctx, ReadinessActivity::class.java)) }
            })
            addView(divider())
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(RunUi.text(ctx, todayW?.optString("name") ?: "No workout planned", Color.WHITE, 17f, true))
                todayW?.let { w ->
                    addView(RunUi.text(ctx, io.github.michealsizemore1.beyondstride.Workouts.outline(w, hrMax, hrRest, RunSettings.cp(ctx)).take(4).joinToString("\n"), RunUi.MUTED, 12f))
                    io.github.michealsizemore1.beyondstride.Workouts.cleanNotes(w.optString("notes")).takeIf { it.isNotBlank() }?.let { n ->
                        addView(RunUi.text(ctx, n, Color.WHITE, 12f).apply { setPadding(0, dp(6), 0, 0) })
                    }
                }
                io.github.michealsizemore1.beyondstride.TrailSync.current(ctx)?.let { (t, oab) ->
                    addView(RunUi.text(ctx, "Trail: ${t.name} · " + String.format(Locale.US, "%.1f mi", t.lengthM / 1609.344 * (if (oab) 2 else 1)) +
                        (if (oab) " out & back" else ""), 0xFF46C46E.toInt(), 13f, true).apply { setPadding(0, dp(6), 0, 0) })
                }
                setOnClickListener { startActivity(Intent(ctx, WorkoutsActivity::class.java)) }
            })
            // Fuel: today's eating from the Command Center's Nutrition tab.
            addView(divider())
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                val N = io.github.michealsizemore1.beyondstride.Nutrition
                val nd = N.today(io.github.michealsizemore1.beyondstride.NutritionStore.days(ctx))
                addView(RunUi.text(ctx, "FUEL", RunUi.ACCENT, 11f, true))
                // Fluids and post-run rehydration, part of Fuel (right under the macros). Entered here or in the Command Center.
                fun fluidLines() {
                    addView(RunUi.text(ctx, "Fluids ${nd?.waterOz ?: 0} oz" + (if (nd != null && nd.water + nd.sportsOz + nd.otherOz > 0)
                        "  (water ${nd.water} · sports ${nd.sportsOz} · other ${nd.otherOz})" else ""), RunUi.MUTED, 12f).apply {
                        setPadding(0, dp(4), 0, dp(2)); setOnClickListener { fluidDialog() } })
                    N.rehydrate(nd)?.let { h ->
                        addView(RunUi.text(ctx, "Lost ${String.format(Locale.US, "%.1f", h.lostLb)} lb on the run → ~${h.targetOz} oz to rehydrate · " +
                            (if (h.needOz == 0) "caught up ✓" else "drink ~${h.needOz} oz more"),
                            if (h.needOz == 0) 0xFF5FD38D.toInt() else N.WATER, 12f, true).apply { setPadding(0, dp(2), 0, 0); setOnClickListener { fluidDialog() } })
                    }
                    // Quick taps: a cup of water / sports drink / other, and weights.
                    addView(LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) }
                        fun chip(t: String, onTap: () -> Unit) = addView(RunUi.text(ctx, t, N.WATER, 12f, true).apply {
                            gravity = Gravity.CENTER; background = RunUi.rounded(0xFF26302C.toInt(), dp(14).toFloat())
                            setPadding(dp(4), dp(7), dp(4), dp(7)); setOnClickListener { onTap() }
                        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(4) })
                        chip("💧 +8 water") { HydrationSync.change(ctx, "water", Hydration.STEP); render() }
                        chip("+8 sports") { HydrationSync.change(ctx, "sports", Hydration.STEP); render() }
                        chip("+8 other") { HydrationSync.change(ctx, "other", Hydration.STEP); render() }
                        chip("⚖ Weight") { fluidDialog() }
                    })
                }
                when {
                    !NutritionSync.ready(ctx) -> addView(RunUi.text(ctx, "Set up LiveTrack first — nutrition is shared through the same database.", RunUi.MUTED, 12f))
                    NutritionSync.lastError(ctx) == "rules" -> addView(RunUi.text(ctx, "Update your Firebase rules to share nutrition (Settings → LiveTrack → copy rules).", 0xFFF2C94C.toInt(), 12f))
                    nd == null || nd.count == 0 -> { addView(RunUi.text(ctx, "Nothing logged today · tap to open 🍎 Nutrition", RunUi.MUTED, 13f)); fluidLines() }
                    else -> {
                        val over = nd.target > 0 && nd.left < 0
                        addView(RunUi.text(ctx, N.line(nd), if (over) 0xFFF2C94C.toInt() else Color.WHITE, 16f, true))
                        if (nd.target > 0) addView(android.widget.ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
                            max = nd.target; progress = nd.cal.coerceAtMost(nd.target)
                            progressTintList = android.content.res.ColorStateList.valueOf(if (over) 0xFFF2C94C.toInt() else 0xFF5FD38D.toInt())
                            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(8)).apply { topMargin = dp(4); bottomMargin = dp(4) }
                        })
                        val (pp, cp, fp) = nd.ratios()
                        addView(RunUi.text(ctx, N.macros(nd) + "  ($pp / $cp / $fp %)", RunUi.MUTED, 12f))
                        fluidLines()
                        // Micronutrients: score + every nutrient as a small tile (lowest first), colored by status.
                        if (nd.micros.isNotEmpty()) {
                            addView(RunUi.text(ctx, "MICRONUTRIENTS  " + String.format(Locale.US, "%.1f", nd.microScore) + "/10 · " + nd.microLabel(),
                                N.statusColor(when { nd.microScore >= 7 -> "Met"; nd.microScore >= 5 -> "Close"; else -> "Low" }), 12f, true).apply { setPadding(0, dp(10), 0, dp(2)) })
                            nd.microsByNeed().chunked(3).forEach { row ->
                                addView(LinearLayout(ctx).apply {
                                    orientation = LinearLayout.HORIZONTAL
                                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(4) }
                                    row.forEachIndexed { i, m ->
                                        addView(LinearLayout(ctx).apply {
                                            orientation = LinearLayout.VERTICAL
                                            background = RunUi.rounded(0xFF26302C.toInt(), dp(8).toFloat())
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
                    }
                }
                setOnClickListener { RunUi.openNutrition(this@RunsActivity) }
            })
        })

        // 2. Command Center + LiveTrack.
        val unsentCc = runs.map { it.optLong("start") }.filter { !PhoneRuns.sentToCommandCenter(this, it) }
        val live = getSharedPreferences("runsettings", 0).getBoolean("liveOn", false)
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
            fun tile(title: String, status: String, color: Int, onClick: () -> Unit) = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                background = RunUi.rounded(color, dp(16).toFloat())
                setPadding(dp(4), dp(10), dp(4), dp(10))
                addView(RunUi.text(ctx, title, Color.WHITE, 13f, true).apply { gravity = Gravity.CENTER })
                addView(RunUi.text(ctx, status, 0xCCFFFFFF.toInt(), 11f).apply { gravity = Gravity.CENTER })
                setOnClickListener { onClick() }
            }
            val lp = { last: Boolean -> LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply { if (!last) marginEnd = dp(6) } }
            addView(tile("Command Center", if (unsentCc.isEmpty()) "✓ sent" else "Send ${unsentCc.size}", RunUi.CC) {
                if (unsentCc.isEmpty()) Toast.makeText(ctx, "All runs are already in the Command Center", Toast.LENGTH_SHORT).show()
                else if (CommandCenter.autoOn(ctx)) Thread {
                    val n = CommandCenter.upload(ctx, unsentCc)
                    runOnUiThread { Toast.makeText(ctx, if (n > 0) "Sent $n — they appear in Activities next time the Command Center is open" else "Couldn't send — check your connection", Toast.LENGTH_LONG).show(); render() }
                }.start()
                else CommandCenter.send(ctx, unsentCc)
            }, lp(false))
            val smsBlocked = live && Live.smsOn(ctx) && checkSelfPermission(android.Manifest.permission.SEND_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            addView(tile("LiveTrack", if (smsBlocked) "● ON · texts not allowed" else if (live) "● ON" else "Off", if (live) 0xFF1F6FB2.toInt() else RunUi.CARD) {
                if (Live.db(ctx).isBlank()) startActivity(Intent(ctx, LiveTrackActivity::class.java))
                else {
                    RunSettings.setBool(ctx, "liveOn", !live)
                    Toast.makeText(ctx, if (!live) "LiveTrack on — on your watch too" else "LiveTrack off", Toast.LENGTH_SHORT).show()
                    recreate()
                }
            }, lp(true))
        })

        // 3. THIS WEEK: miles, runs, training load.
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0); cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        val weekStart = cal.timeInMillis
        cal.timeInMillis = System.currentTimeMillis(); cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0)
        val monthStart = cal.timeInMillis
        fun miles(from: Long) = runs.filter { it.optLong("start") >= from }.sumOf { it.optDouble("distance") }
        val ld = I.load(runs.take(200))
        col.addView(RunUi.section(this, "THIS WEEK"))
        col.addView(RunUi.card(this).apply {
            addView(RunUi.statGrid(ctx, listOf(
                Triple(RunMath.miles(miles(weekStart)), "miles", Color.WHITE),
                Triple("${runs.count { it.optLong("start") >= weekStart }}", "runs", Color.WHITE),
                Triple(RunMath.miles(miles(monthStart)), "this month", Color.WHITE),
            )))
            if (runs.isNotEmpty()) {
                addView(RunUi.text(ctx, "Load: ${ld.status}" + (ld.ratio?.let { String.format(Locale.US, " · ratio %.2f", it) } ?: "") +
                    "  ·  ${ld.acute} stress, last 7 days", ld.color, 13f, true).apply { setPadding(0, dp(8), 0, 0) })
                if (ld.ratio != null && ld.ratio > 1.3) addView(RunUi.text(ctx, ld.advice, RunUi.MUTED, 12f))
            }
            setOnClickListener { startActivity(Intent(ctx, FitnessActivity::class.java)) }
        })

        }  // !showAll
        col.addView(RunUi.section(this, if (showAll) "ALL RUNS" else "LAST RUN"))
        if (runs.isEmpty()) {
            col.addView(RunUi.text(this, "No runs yet. Finish a run on the watch and it appears here automatically " +
                "(the watch sends it whenever it's connected to this phone).", RunUi.MUTED, 14f).apply { setPadding(dp(4), dp(10), dp(4), 0) })
        }
        val fmt = SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault())
        (if (showAll) runs else runs.take(1)).forEach { run ->
            val start = run.optLong("start")
            col.addView(RunUi.card(this).apply {
                addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(RunUi.text(ctx, PhoneRuns.title(run), Color.WHITE, 17f, true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                    val badges = buildList {
                        if (run.has("stryd")) add("Stryd ✓")
                        if (PhoneRuns.sentToCommandCenter(ctx, start)) add("CC ✓")
                    }.joinToString("  ")
                    addView(RunUi.text(ctx, badges, RunUi.MUTED, 12f))
                })
                addView(RunUi.text(ctx, fmt.format(Date(start)), RunUi.MUTED, 12f))
                addView(RunUi.statGrid(ctx, listOf(
                    Triple(RunMath.miles(run.optDouble("distance")), "miles", Color.WHITE),
                    Triple(RunMath.clock(run.optInt("movingSeconds")), "time", Color.WHITE),
                    Triple(RunMath.paceFor(run.optInt("movingSeconds"), run.optDouble("distance")), "pace /mi", Color.WHITE),
                ) + listOfNotNull(
                    run.optInt("avgHr").takeIf { it > 0 }?.let { Triple("$it", "avg HR", RunUi.HR) },
                    run.optInt("avgPower").takeIf { it > 0 }?.let { Triple("$it", "avg W", RunUi.POWER) },
                    run.optInt("stress").takeIf { it > 0 }?.let { Triple("$it", "stress", Color.WHITE) },
                )))
                setOnClickListener { startActivity(Intent(ctx, RunDetailActivity::class.java).putExtra("start", start)) }
            })
        }
        setContentView(RunUi.tabbed(this, ScrollView(this).apply {
            setBackgroundColor(RunUi.BG)
            addView(col, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }, if (showAll) RunUi.TAB_RUNS else RunUi.TAB_TODAY))
    }

    /** Fluids (± a cup) and wake-up / post-run weight for today. Fluid taps save at once; weights on Save. */
    private fun fluidDialog() {
        val ctx = this
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val names = mapOf("water" to "Water", "sports" to "Sports drinks", "other" to "Other drinks")
        fun value(f: String) = HydrationSync.today(ctx).let { d -> when (f) { "water" -> d?.water; "sports" -> d?.sportsOz; else -> d?.otherOz } ?: 0 }
        Hydration.FLUIDS.forEach { f ->
            box.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(6))
                addView(android.widget.TextView(ctx).apply { text = names[f]; textSize = 16f }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                val v = android.widget.TextView(ctx).apply { text = "${value(f)} oz"; textSize = 16f; gravity = Gravity.CENTER; setTypeface(typeface, android.graphics.Typeface.BOLD) }
                fun btn(t: String, d: Int) = android.widget.Button(ctx).apply {
                    text = t; minWidth = 0; minimumWidth = 0
                    setOnClickListener { HydrationSync.change(ctx, f, d); v.text = "${value(f)} oz" }
                }
                addView(btn("−8", -Hydration.STEP), LinearLayout.LayoutParams(dp(56), WRAP_CONTENT))
                addView(v, LinearLayout.LayoutParams(dp(72), WRAP_CONTENT))
                addView(btn("+8", Hydration.STEP), LinearLayout.LayoutParams(dp(56), WRAP_CONTENT))
            })
        }
        val today = HydrationSync.today(ctx)
        fun field(hint: String, v: Double) = android.widget.EditText(ctx).apply {
            this.hint = hint; inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            if (v > 0) setText(Hydration.lb(v))
        }
        box.addView(android.widget.TextView(ctx).apply { text = "Wake-up weight (lb)"; setPadding(0, dp(12), 0, 0) })
        val wake = field("e.g. 172.4", today?.weight ?: 0.0); box.addView(wake)
        box.addView(android.widget.TextView(ctx).apply { text = "Post-run weight (lb)"; setPadding(0, dp(8), 0, 0) })
        val post = field("e.g. 171.2", today?.postWeight ?: 0.0); box.addView(post)
        box.addView(android.widget.TextView(ctx).apply {
            text = "Also goes to the Command Center (its Nutrition and Wellness fields)."; textSize = 12f; setPadding(0, dp(8), 0, 0)
        })
        android.app.AlertDialog.Builder(this).setTitle("Fluids & weight · today").setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Save") { _, _ ->
                fun num(e: android.widget.EditText) = e.text.toString().trim().toDoubleOrNull()?.takeIf { it in 70.0..600.0 } ?: 0.0
                val w = num(wake); val p = num(post)
                if (w != (today?.weight ?: 0.0)) HydrationSync.change(ctx, "weight", value = w)
                if (p != (today?.postWeight ?: 0.0)) HydrationSync.change(ctx, "postWeight", value = p)
                render()
            }
            .setOnDismissListener { render() }
            .show()
    }
}

/** Every run (the Runs tab). */
class AllRunsActivity : RunsActivity()
