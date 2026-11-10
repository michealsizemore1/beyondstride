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
        try { RunSettings.addHeatScreenOnce(this) } catch (e: Exception) { }
        if (io.github.michealsizemore1.beyondstride.WrongDevice.check(this, isWatchApp = false)) { wrongDevice = true; return }
        // One-time cleanup: cancel any leftover background Stryd lookups from older versions.
        getSharedPreferences("runflags", 0).let { f -> if (!f.getBoolean("strydJobsCleared", false)) {
            try { val wm = androidx.work.WorkManager.getInstance(this)
                PhoneRuns.list(this).forEach { wm.cancelUniqueWork("stryd-" + it.nameWithoutExtension) } } catch (e: Exception) { }
            f.edit().putBoolean("strydJobsCleared", true).apply()
        } }
        handleLink(intent)
        Reminders.schedule(this)
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
        if (Standalone.on(this)) { val app = applicationContext; Thread { try { Local.upkeep(app) } catch (e: Exception) { } }.start() }
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

    /** 📝 Notes card: shows what's written; tap to edit. */
    private fun notesCard(): android.view.View {
        val ctx = this; val dp = { v: Int -> RunUi.dp(ctx, v) }
        val p = getSharedPreferences("mynotes", 0); val t = p.getString("text", "").orEmpty()
        return RunUi.card(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) }
            background = RunUi.rounded(RunUi.CARD, dp(14).toFloat()).apply { setStroke(dp(1), RunUi.ACCENT) }
            addView(RunUi.text(ctx, "📝 My notes ${if (t.isBlank()) "▸" else "✎"}", RunUi.ACCENT, 15f, true))
            addView(RunUi.text(ctx, t.ifBlank { "Tap to jot down ideas — things to add or change later." }, if (t.isBlank()) RunUi.MUTED else Color.WHITE, 14f).apply {
                setPadding(0, dp(4), 0, 0) })
            setOnClickListener { editNotes() }
        }
    }

    private fun editNotes() {
        val p = getSharedPreferences("mynotes", 0)
        val e = android.widget.EditText(this).apply {
            setText(p.getString("text", "")); hint = "Ideas, things to add…"; minLines = 6; gravity = Gravity.TOP
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSelection(text.length)
        }
        val box = android.widget.FrameLayout(this).apply { setPadding(RunUi.dp(context, 20), RunUi.dp(context, 8), RunUi.dp(context, 20), 0); addView(e) }
        android.app.AlertDialog.Builder(this).setTitle("📝 My notes").setView(box)
            .setPositiveButton("Save") { _, _ -> p.edit().putString("text", e.text.toString().trimEnd()).putLong("at", System.currentTimeMillis()).apply(); CloudCopy.dirty(this); render() }
            .setNegativeButton("Cancel", null).show()
    }

    /** 🔋 Body Battery card: the number, a bar, what charged / drained it, and the last 7 mornings. */
    private fun bodyBatteryCard(): android.view.View {
        val ctx = this; val dp = { v: Int -> RunUi.dp(ctx, v) }
        val b = BodyBattery.today(this)
        return RunUi.card(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) }
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(RunUi.text(ctx, "🔋 Body Battery", Color.WHITE, 15f, true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(RunUi.text(ctx, "${b.now}", b.color, 30f, true))
                addView(RunUi.text(ctx, "  ${b.label}", b.color, 13f, true))
            })
            // Bar.
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; background = RunUi.rounded(0xFF2A3330.toInt(), dp(6).toFloat())
                addView(android.view.View(ctx).apply { background = RunUi.rounded(b.color, dp(6).toFloat()) }, LinearLayout.LayoutParams(0, MATCH_PARENT, b.now.toFloat()))
                addView(android.view.View(ctx), LinearLayout.LayoutParams(0, MATCH_PARENT, (100 - b.now).toFloat()))
            }, LinearLayout.LayoutParams(MATCH_PARENT, dp(12)).apply { topMargin = dp(6) })
            addView(RunUi.text(ctx, "Woke at ${b.wake} · drained ${b.activity + b.daily} so far · ~${b.bedtime} by bedtime", RunUi.MUTED, 12f).apply { setPadding(0, dp(6), 0, 0) })
            addView(RunUi.text(ctx, b.notes.last(), Color.WHITE, 13f).apply { setPadding(0, dp(4), 0, 0) })
            // Last 7 mornings.
            val hist = try { BodyBattery.history(ctx) } catch (e: Exception) { emptyList() }
            if (hist.isNotEmpty()) addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM; setPadding(0, dp(10), 0, 0)
                hist.forEach { (d, v) ->
                    addView(LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                        addView(RunUi.text(ctx, "$v", RunUi.MUTED, 10f))
                        val c = when { v >= 76 -> 0xFF2ECC71.toInt(); v >= 51 -> 0xFF1ABC9C.toInt(); v >= 26 -> 0xFFF1C40F.toInt(); else -> 0xFFE67E22.toInt() }
                        addView(android.view.View(ctx).apply { background = RunUi.rounded(c, dp(3).toFloat()) }, LinearLayout.LayoutParams(dp(14), Math.max(dp(3), dp(40) * v / 100)))
                        addView(RunUi.text(ctx, java.time.LocalDate.parse(d).dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, Locale.US), RunUi.DIM, 10f))
                    }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                }
            })
            addView(RunUi.text(ctx, "Wake-up level, last 7 days · tap for details", RunUi.DIM, 10f).apply { gravity = Gravity.CENTER; setPadding(0, dp(2), 0, 0) })
            setOnClickListener {
                android.app.AlertDialog.Builder(ctx).setTitle("🔋 Body Battery ${b.now} · ${b.label}")
                    .setMessage(b.notes.joinToString("\n") + "\n\nHow it works: it charges overnight from how long you slept, your sleeping heart rate and this morning's HRV and resting HR vs your normal. " +
                        "It drains a little every waking hour and with each workout by how long and how hard it was. It's an estimate (BeyondStride doesn't track all-day stress like Garmin), so use it as a guide.\n\n" +
                        "76–100 High · 51–75 Good · 26–50 Medium · 11–25 Low · 0–10 Very low")
                    .setPositiveButton("OK", null).show()
            }
        }
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

        // 📝 My notes — ideas to add later (top of Today).
        col.addView(notesCard())

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
        // 🌤 Weather.
        col.addView(RunUi.card(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) }
            val card = this
            TodayWeather.fill(ctx, card)
            fun refresh(force: Boolean) = Thread {
                if (TodayWeather.fetch(ctx, force)) runOnUiThread { if (card.isAttachedToWindow || !isFinishing) TodayWeather.fill(ctx, card) }
            }.start()
            setOnClickListener { refresh(true) }
            if (TodayWeather.stale(ctx)) refresh(false)
        })
        // 🔋 Body Battery.
        try { col.addView(bodyBatteryCard()) } catch (e: Exception) { }
        // 🌙 Evening report (from 8 pm).
        if (java.time.LocalTime.now().hour >= 20 && EveningReport.enabled(this)) col.addView(RunUi.card(this).apply {
            background = RunUi.rounded(0xFF26264A.toInt(), dp(14).toFloat())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
            addView(RunUi.text(ctx, "🌙 Evening report ▸", Color.WHITE, 16f, true))
            addView(RunUi.text(ctx, "How today went, what's left to eat and drink, and tomorrow's plan", RunUi.MUTED, 12f))
            setOnClickListener { startActivity(Intent(ctx, EveningReportActivity::class.java)) }
        })
        // ☀️ Morning report (until noon).
        if (java.time.LocalTime.now().hour < 12 && MorningReport.enabled(this)) col.addView(RunUi.card(this).apply {
            background = RunUi.rounded(0xFF243B2E.toInt(), dp(14).toFloat())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
            addView(RunUi.text(ctx, "☀️ Morning report ▸", Color.WHITE, 16f, true))
            addView(RunUi.text(ctx, "Sleep, readiness, today's workout, weather, fuel, weight and your week — on one screen", RunUi.MUTED, 12f))
            setOnClickListener { startActivity(Intent(ctx, MorningReportActivity::class.java)) }
        })
        col.addView(RunUi.card(this).apply {
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                if (rd == null) addView(RunUi.text(ctx, "Readiness: do the 2½-min morning check on the watch ▸", RunUi.ACCENT, 15f, true))
                else {
                    addView(RunUi.text(ctx, "${rd.score}  ${rd.label}", rd.color, 22f, true))
                    if (!rd.checkedToday) addView(RunUi.text(ctx, "No morning check yet — based on sleep and training", RunUi.MUTED, 12f))
                    addView(RunUi.text(ctx, rd.parts.filter { it.pts >= 0 }.joinToString(" · ") { "${it.name} ${it.value}" }, RunUi.MUTED, 12f))
                    addView(RunUi.text(ctx, rd.advice.substringBefore(". ") + ".", Color.WHITE, 13f).apply { setPadding(0, dp(4), 0, 0) })
                    Insights.recoveryWarning(ctx, rd)?.let { addView(RunUi.text(ctx, it, 0xFFF2C94C.toInt(), 12f, true).apply { setPadding(0, dp(4), 0, 0) }) }
                    addView(RunUi.text(ctx, "Details, sleep, resting HR and critical power ▸", RunUi.ACCENT, 12f, true).apply { setPadding(0, dp(6), 0, 0) })
                }
                setOnClickListener { startActivity(Intent(ctx, ReadinessActivity::class.java)) }
            })
            addView(divider())
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(RunUi.text(ctx, todayW?.optString("name") ?: "No workout planned", Color.WHITE, 17f, true))
                Insights.adherence(ctx, R.today())?.let { a -> if (a.planned > 0 || a.done > 0) addView(RunUi.text(ctx, a.status, a.color, 12f, true)) }
                PlanAdjust.suggestions(ctx).size.takeIf { it > 0 }?.let { n -> addView(RunUi.text(ctx, "🔧 $n plan adjustment${if (n == 1) "" else "s"} suggested — review in Activities ▸", 0xFFC4B5FD.toInt(), 12f, true).apply {
                    setPadding(0, dp(4), 0, 0); setOnClickListener { startActivity(Intent(ctx, ActivitiesActivity::class.java)) } }) }
                todayW?.let { w ->
                    addView(RunUi.text(ctx, io.github.michealsizemore1.beyondstride.Workouts.outline(w, hrMax, hrRest, RunSettings.cp(ctx)).take(4).joinToString("\n"), RunUi.MUTED, 12f))
                    // Just the name and outline here; the full notes are on the Plan tab and the Activities workout card.
                }
                io.github.michealsizemore1.beyondstride.TrailSync.current(ctx)?.let { (t, oab) ->
                    addView(RunUi.text(ctx, "Trail: ${t.name} · " + String.format(Locale.US, "%.1f mi", t.lengthM / 1609.344 * (if (oab) 2 else 1)) +
                        (if (oab) " out & back" else ""), 0xFF46C46E.toInt(), 13f, true).apply { setPadding(0, dp(6), 0, 0) })
                }
                setOnClickListener { startActivity(Intent(ctx, WorkoutsActivity::class.java)) }
            })
            // Fuel: today's eating (tap for the Nutrition screen).
            addView(divider())
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(RunUi.text(ctx, "FUEL", RunUi.ACCENT, 11f, true))
                NutritionUi.glance(this@RunsActivity, this)
                setOnClickListener { startActivity(Intent(ctx, NutritionActivity::class.java)) }
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
            val sa = Standalone.on(ctx)
            addView(tile(if (sa) "Activities" else "Command Center", if (unsentCc.isEmpty()) (if (sa) "✓ added" else "✓ sent") else (if (sa) "Add ${unsentCc.size}" else "Send ${unsentCc.size}"), RunUi.CC) {
                if (unsentCc.isEmpty()) Toast.makeText(ctx, if (sa) "All runs are already in Activities" else "All runs are already in the Command Center", Toast.LENGTH_SHORT).show()
                else if (CommandCenter.autoOn(ctx)) Thread {
                    val n = CommandCenter.upload(ctx, unsentCc)
                    runOnUiThread { Toast.makeText(ctx, if (n > 0) (if (sa) "Added $n to Activities" else "Sent $n — they appear in Activities next time the Command Center is open") else "Couldn't send — check your connection", Toast.LENGTH_LONG).show(); render() }
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

        CcStatus.line(this).let { (t, c) -> col.addView(RunUi.text(this, t, c, 12f, true).apply { setPadding(dp(2), dp(6), 0, 0) }) }

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
        // Same miles as Activities → Mileage (every source, Mon–Sun); BeyondStride's own runs until Activities arrive.
        val am = ActivityStore.mileage(this)
        am?.let { io.github.michealsizemore1.beyondstride.NutritionStore.setMileage(this, it.week, it.runs, it.month) }
        ActivityStore.watchSummary(this)?.let { io.github.michealsizemore1.beyondstride.NutritionStore.setActs(this, it) }
        col.addView(RunUi.section(this, "THIS WEEK"))
        col.addView(RunUi.card(this).apply {
            addView(RunUi.statGrid(ctx, listOf(
                Triple(am?.let { String.format(Locale.US, "%.2f", it.week) } ?: RunMath.miles(miles(weekStart)), "miles", Color.WHITE),
                Triple("${am?.runs ?: runs.count { it.optLong("start") >= weekStart }}", "runs", Color.WHITE),
                Triple(am?.let { String.format(Locale.US, "%.2f", it.month) } ?: RunMath.miles(miles(monthStart)), "this month", Color.WHITE),
            )))
            if (runs.isNotEmpty()) {
                addView(RunUi.text(ctx, "Training load (stress): ${ld.status}" + (ld.ratio?.let { String.format(Locale.US, " · ratio %.2f", it) } ?: "") +
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
                        if (PhoneRuns.sentToCommandCenter(ctx, start)) add(if (Standalone.on(ctx)) "In Activities ✓" else "CC ✓")
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

}

/** Every run (the Runs tab). */
class AllRunsActivity : RunsActivity()
