package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.Toast
import io.github.michealsizemore1.beyondstride.RunMath

/** Zones (sent to the watch), Command Center link, and Stryd connection. */
class RunSettingsActivity : Activity() {
    /** null = the list of setting groups; otherwise the open group. */
    private var page: String? = null

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (page != null) { page = null; render() } else @Suppress("DEPRECATION") super.onBackPressed() }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        page = intent.getStringExtra("page")
    }

    override fun onResume() {
        super.onResume()
        io.github.michealsizemore1.beyondstride.Theme.load(this, "runsettings")
        io.github.michealsizemore1.beyondstride.SettingsSync.watch(this, "runsettings", "phone")
        render()
    }

    private fun render() {
        val ctx = this
        fun dp(v: Int) = RunUi.dp(ctx, v)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(40), dp(16), dp(40))
        }
        val titles = mapOf("run" to "Run options", "audio" to "Audio prompts", "zones" to "Zones & power", "cc" to "Command Center", "standalone" to "Stand-alone & cloud copy", "ai" to "AI food search", "backup" to "Backup", "about" to "About")
        col.addView(RunUi.text(this, page?.let { titles[it] } ?: "Settings", Color.WHITE, 26f, true))
        val classic = getSharedPreferences("runsettings", 0).getString("menuStyle", "cards") == "classic"
        if (page == null && !classic) { renderList(col); return }
        /** In the classic style every group shows on one long page, with a heading. */
        fun on(key: String): Boolean {
            if (page == key) return true
            if (!(classic && page == null)) return false
            col.addView(RunUi.section(ctx, (titles[key] ?: key).uppercase()))
            return true
        }
        if (classic && page == null) {
            col.addView(RunUi.button(this, "Menu style: Classic  (tap for cards)", RunUi.CARD, Color.WHITE) { setMenuStyle("cards") }
                .apply { layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) } })
            col.addView(RunUi.button(this, "Background: " + (if (io.github.michealsizemore1.beyondstride.Theme.light) "Lighter" else "Dark") + "  (tap to switch)", RunUi.CARD, Color.WHITE) { toggleBg() }
                .apply { layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) } })
            col.addView(RunUi.button(this, "Watch data screens ›", RunUi.ACCENT) { startActivity(Intent(ctx, ScreensActivity::class.java)) }
                .apply { layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) } })
            col.addView(RunUi.button(this, "LiveTrack ›", 0xFF4FA3E0.toInt(), Color.WHITE) { startActivity(Intent(ctx, LiveTrackActivity::class.java)) }
                .apply { layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) } })
        }
        val sync = RunUi.text(this, "Same on the watch — changes on either show on both.", RunUi.MUTED, 12f).apply { setPadding(dp(4), dp(4), 0, 0) }
        if (page in setOf("run", "audio", "zones")) col.addView(sync)

        fun input(label: String, value: String, type: Int, hint: String = ""): EditText {
            col.addView(RunUi.text(ctx, label, RunUi.MUTED, 13f).apply { setPadding(dp(4), dp(12), 0, dp(2)) })
            return EditText(ctx).apply {
                setText(value); this.hint = hint; inputType = type
                setTextColor(Color.WHITE); setHintTextColor(RunUi.DIM)
                background = RunUi.rounded(RunUi.CARD, dp(12).toFloat())
                setPadding(dp(14), dp(10), dp(14), dp(10))
                col.addView(this, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            }
        }
        val num = InputType.TYPE_CLASS_NUMBER
        val dec = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        fun fmt(d: Double) = if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()

        // ---- run options (same as the watch's Settings; changes on either show on both)
        fun switch(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) =
            col.addView(RunUi.card(ctx).apply {
                addView(Switch(ctx).apply {
                    text = title; setTextColor(Color.WHITE); textSize = 15f
                    isChecked = checked
                    setOnCheckedChangeListener { _, on -> onChange(on) }
                })
                if (sub.isNotBlank()) addView(RunUi.text(ctx, sub, RunUi.MUTED, 12f))
            })
        if (on("run")) {
        switch("Use GPS for pace & distance", "Off = Stryd footpod (recommended)", RunSettings.source(this) == "gps") {
            RunSettings.setSource(ctx, if (it) "gps" else "stryd")
        }
        switch("Auto-pause", "Pauses when you stop, resumes when you run", RunSettings.bool(this, "autoPause")) { RunSettings.setBool(ctx, "autoPause", it) }
        switch("Record GPS map track", "With Stryd pace; uses more battery", RunSettings.bool(this, "track")) {
            RunSettings.setBool(ctx, "track", it)
        }
        switch("Chest strap (H10) with wrist backup", "Off = watch wrist sensor only", RunSettings.bool(this, "useStrap")) { RunSettings.setBool(ctx, "useStrap", it) }
        switch("Keep watch screen on in BeyondStride", "", RunSettings.bool(this, "keepOn")) { RunSettings.setBool(ctx, "keepOn", it) }
        switch("Bottom button: start · lap · double-press finish", "", RunSettings.bool(this, "bottomBtn")) { RunSettings.setBool(ctx, "bottomBtn", it) }

        // ---- watch text size (same setting as on the watch)
        col.addView(RunUi.card(this).apply {
            val p = getSharedPreferences("runsettings", 0)
            val k = p.getFloat("textScale", 1.25f)
            val opts = listOf(1f to "Normal", 1.25f to "Large", 1.5f to "Extra large")
            addView(RunUi.text(ctx, "Watch text size: " + (opts.minByOrNull { kotlin.math.abs(it.first - k) }?.second ?: "Large") + "  ›", RunUi.ACCENT, 15f, true))
            addView(RunUi.text(ctx, "Labels and small text on the watch (big run numbers stay sized to fit)", RunUi.MUTED, 12f))
            setOnClickListener {
                android.app.AlertDialog.Builder(ctx).setTitle("Watch text size").setItems(opts.map { it.second }.toTypedArray()) { _, i ->
                    p.edit().putFloat("textScale", opts[i].first).putLong("settingsAt", System.currentTimeMillis()).apply(); RunSettings.pushToWatch(ctx); render()
                }.show()
            }
        })

        // ---- easy-day heart-rate cap
        col.addView(RunUi.card(this).apply {
            val p = getSharedPreferences("runsettings", 0)
            val mode = p.getString("hrCap", "easy") ?: "easy"; val zone = p.getInt("hrCapZone", 2)
            val b = RunMath.hrBounds(RunSettings.hrMax(ctx), RunSettings.hrRest(ctx))
            val cap = if (zone in 1..4) b[zone] - 1 else RunSettings.hrMax(ctx).toInt()
            addView(RunUi.text(ctx, "Easy-day heart-rate cap: " + when (mode) { "off" -> "Off"; "all" -> "Easy days + free runs"; else -> "Easy days" } + "  ›", RunUi.ACCENT, 15f, true))
            addView(RunUi.text(ctx, "Buzz + \"ease up\" when you go over Zone $zone ($cap bpm) on easy runs, recovery runs, long runs " +
                "and the easy parts of workouts. No \"speed up\" nags on easy steps.", RunUi.MUTED, 12f))
            setOnClickListener {
                val opts = listOf("off" to "Off", "easy" to "Easy days (from your plan)", "all" to "Easy days + free runs")
                android.app.AlertDialog.Builder(ctx).setTitle("Easy-day heart-rate cap").setItems(opts.map { it.second }.toTypedArray()) { _, i ->
                    p.edit().putString("hrCap", opts[i].first).putLong("settingsAt", System.currentTimeMillis()).apply(); RunSettings.pushToWatch(ctx)
                    if (opts[i].first != "off") android.app.AlertDialog.Builder(ctx).setTitle("Stay at or below")
                        .setItems((1..3).map { z -> "Zone $z (under ${b[z] - 1} bpm)" }.toTypedArray()) { _, j ->
                            p.edit().putInt("hrCapZone", j + 1).putLong("settingsAt", System.currentTimeMillis()).apply(); RunSettings.pushToWatch(ctx); render()
                        }.setOnCancelListener { render() }.show()
                    else render()
                }.show()
            }
        })

        }
        // ---- audio prompts
        if (on("audio")) {
        col.addView(RunUi.text(this, "Spoken on the watch: through Bluetooth headphones when connected, otherwise the watch speaker.",
            RunUi.MUTED, 12f).apply { setPadding(dp(4), dp(6), dp(4), 0) })
        switch("Audio prompts", "", RunSettings.bool(this, "voice")) { RunSettings.setBool(ctx, "voice", it) }
        switch("Each lap / mile", "Time, pace, heart rate, power", RunSettings.bool(this, "voiceLaps")) { RunSettings.setBool(ctx, "voiceLaps", it) }
        switch("Workout steps", "What's next, and step results", RunSettings.bool(this, "voiceSteps")) { RunSettings.setBool(ctx, "voiceSteps", it) }
        switch("Target alerts", "Speed up / ease off", RunSettings.bool(this, "voiceAlerts")) { RunSettings.setBool(ctx, "voiceAlerts", it) }
        switch("Mile alerts on easy (zone 2) days", "Each mile: time, pace and heart rate, during easy workouts from your plan", RunSettings.bool(this, "voiceMiles")) { RunSettings.setBool(ctx, "voiceMiles", it) }
        switch("Louder prompts", "Turns the volume up while speaking, then back", RunSettings.bool(this, "voiceBoost")) { RunSettings.setBool(ctx, "voiceBoost", it) }
        switch("Pause music while speaking", "Off = lower the music instead", getSharedPreferences("runsettings", 0).getString("voiceMusic", "duck") == "pause") {
            getSharedPreferences("runsettings", 0).edit().putString("voiceMusic", if (it) "pause" else "duck").putLong("settingsAt", System.currentTimeMillis()).apply()
            RunSettings.pushToWatch(ctx)
        }
        val everyOpts = listOf(0, 5, 10, 15, 20, 30)
        col.addView(RunUi.card(this).apply {
            val every = RunSettings.voiceEvery(ctx)
            addView(RunUi.text(ctx, "Time update every: " + (if (every == 0) "Off" else "$every minutes") + "  ›", RunUi.ACCENT, 15f, true))
            addView(RunUi.text(ctx, "Distance, time, average pace, heart rate", RunUi.MUTED, 12f))
            setOnClickListener {
                android.app.AlertDialog.Builder(ctx).setTitle("Time update every")
                    .setItems(everyOpts.map { if (it == 0) "Off" else "$it minutes" }.toTypedArray()) { _, i ->
                        RunSettings.setVoiceEvery(ctx, everyOpts[i]); render()
                    }.show()
            }
        })

        }
        // ---- zones
        if (on("zones")) {
        val maxHr = input("Max heart rate (bpm)", fmt(RunSettings.hrMax(this)), num)
        val restHr = input("Resting heart rate (bpm)", fmt(RunSettings.hrRest(this)), dec)
        val cp = input("Critical Power (W)", RunSettings.cp(this).toString(), num)
        val cal = input("Stryd calibration factor", String.format(java.util.Locale.US, "%.3f", RunSettings.cal(this)), dec)
        val preview = RunUi.text(this, "", RunUi.MUTED, 12f).apply { setPadding(dp(4), dp(8), 0, 0) }
        col.addView(preview)
        fun updatePreview() {
            val b = RunMath.hrBounds(maxHr.text.toString().toDoubleOrNull() ?: 185.0, restHr.text.toString().toDoubleOrNull() ?: 43.5)
            val p = RunMath.powerBounds(cp.text.toString().toIntOrNull() ?: 333)
            preview.text = "HR zones: " + (0..4).joinToString(" · ") { "Z${it + 1} ${b[it]}" + if (it == 4) "+" else "" } +
                "\nPower zones: " + (0..4).joinToString(" · ") { "Z${it + 1} ${p[it]}" + if (it == 4) "+ W" else "" }
        }
        updatePreview()
        listOf(maxHr, restHr, cp).forEach { it.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = updatePreview()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        }) }

        col.addView(RunUi.button(this, "Save zones", RunUi.ACCENT) {
            val mh = maxHr.text.toString().toDoubleOrNull(); val rh = restHr.text.toString().toDoubleOrNull()
            val c = cp.text.toString().toIntOrNull(); val k = cal.text.toString().toDoubleOrNull()
            if (mh == null || rh == null || c == null || k == null || mh <= rh || k !in 0.8..1.2) {
                Toast.makeText(ctx, "Check the numbers (calibration is usually 0.95–1.05)", Toast.LENGTH_LONG).show(); return@button
            }
            RunSettings.save(ctx, mh, rh, c, k, RunSettings.ccUrl(ctx))
            Toast.makeText(ctx, "Saved — sent to your watch", Toast.LENGTH_SHORT).show()
        }.apply { layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(16) } })
        }
        // ---- Command Center
        if (on("cc")) {
            val cc = input("Command Center page", RunSettings.ccUrl(this), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
            col.addView(RunUi.text(this, "Where finished runs are sent and where \"Send plan to BeyondStride\" comes from.", RunUi.MUTED, 12f).apply { setPadding(dp(4), dp(6), 0, 0) })
            switch("Send runs automatically", "Each finished run goes straight to the Command Center (through your LiveTrack database) — it shows in Activities next time the Command Center is open. Off: tap Command Center to send.",
                getSharedPreferences("runsettings", 0).getBoolean("ccAuto", true)) { getSharedPreferences("runsettings", 0).edit().putBoolean("ccAuto", it).apply() }
            col.addView(RunUi.button(this, "Save", RunUi.ACCENT) {
                RunSettings.save(ctx, RunSettings.hrMax(ctx), RunSettings.hrRest(ctx), RunSettings.cp(ctx), RunSettings.cal(ctx), cc.text.toString().trim().ifBlank { RunSettings.ccUrl(ctx) })
                Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show()
            }.apply { layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(16) } })
        }

        // ---- AI food search (Anthropic key, kept on this phone only)
        if (on("ai")) {
            val key = input("Anthropic API key", if (AiFood.hasKey(this)) "••••••••" + AiFood.key(this).takeLast(4) else "",
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, "sk-ant-…")
            col.addView(RunUi.text(this, "Turns on ✨ AI search and 🏷 Label photo in Add food (phone and watch). Use the same key as the Command Center " +
                "(console.anthropic.com → API keys). It stays on this phone: not in backups, not sent to the watch or the Command Center. Each search costs a fraction of a cent on your Anthropic account.",
                RunUi.MUTED, 12f).apply { setPadding(dp(4), dp(6), 0, 0) })
            col.addView(RunUi.button(this, "Save key", RunUi.ACCENT) {
                val k = key.text.toString().trim()
                if (k.startsWith("••")) { Toast.makeText(ctx, "Key unchanged", Toast.LENGTH_SHORT).show(); return@button }
                if (!k.startsWith("sk-")) { Toast.makeText(ctx, "That doesn't look like an Anthropic key (starts with sk-ant-)", Toast.LENGTH_LONG).show(); return@button }
                AiFood.setKey(ctx, k); Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show(); render()
            }.apply { layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(16) } })
            if (AiFood.hasKey(this)) col.addView(RunUi.button(this, "Remove key", RunUi.CARD, Color.WHITE) {
                AiFood.setKey(ctx, ""); Toast.makeText(ctx, "Removed", Toast.LENGTH_SHORT).show(); render()
            }.apply { layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) } })
        }

        // (Stryd account sign-in removed: Stryd only has runs after the pod's offline sync, which isn't used.
        //  The pod itself still pairs with the watch for pace, distance, power and cadence.)
        getSharedPreferences("strydcloud", 0).edit().clear().apply()   // forget any old Stryd login

        // ---- stand-alone & cloud copy
        if (on("standalone")) standalonePage(col)

        // ---- backup
        if (on("backup")) {
        col.addView(RunUi.card(this).apply {
            val at = Backup.lastAt(ctx)
            addView(RunUi.text(ctx, if (at == 0L) "No backup yet" else "Last backup: " +
                java.text.SimpleDateFormat("EEE, MMM d · h:mm a", java.util.Locale.getDefault()).format(java.util.Date(at)), Color.WHITE, 15f, true))
            Backup.lastInfo(ctx).takeIf { it.isNotBlank() }?.let { addView(RunUi.text(ctx, it, RunUi.MUTED, 12f)) }
            addView(RunUi.text(ctx, "Runs, workouts & plan, courses, settings, zones, LiveTrack, morning checks, sleep, personal bests, your nutrition history (with food photos, favorites and notes), Activities and training plans — in one file. Your Anthropic key is never included. " +
                "Save it to Google Drive so it's safe if the phone is lost.", RunUi.DIM, 12f).apply { setPadding(0, dp(6), 0, 0) })
            addView(RunUi.button(ctx, if (Backup.uri(ctx) == null) "Back up now…" else "Back up now", RunUi.ACCENT) {
                val u = Backup.uri(ctx)
                if (u == null) newBackup() else Thread {
                    val r = try { "Backed up: " + Backup.write(ctx, u) } catch (e: Exception) { null }
                    runOnUiThread { if (r == null) newBackup() else { Toast.makeText(ctx, r, Toast.LENGTH_LONG).show(); render() } }
                }.start()
            }.apply { layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) } })
            if (Backup.uri(ctx) != null) addView(RunUi.button(ctx, "Back up to a new file…", RunUi.CARD, Color.WHITE) { newBackup() }
                .apply { layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) } })
            addView(RunUi.button(ctx, "Restore from a backup…", RunUi.CARD, Color.WHITE) {
                @Suppress("DEPRECATION") startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")), 82)
            }.apply { layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) } })
        })
        switch("Update the backup after every run", "Keeps the saved backup file current automatically", Backup.auto(this)) { Backup.setAuto(ctx, it) }
        // 🔔 Reminders (checked about once an hour; each one at most once).
        col.addView(RunUi.text(this, "🔔 Reminders", Color.WHITE, 15f, true).apply { setPadding(0, dp(14), 0, 0) })
        Reminders.KINDS.forEach { (k, label) -> switch(label, when (k) {
            "prefuel" -> "5–9 pm the day before a long / hard workout: calories and carbs so far vs target"
            "rehydrate" -> "After a run with a post-run weight: how much more to drink"
            "shoes" -> "At 85% and 100% of a shoe's max miles"
            else -> "Sunday evening: miles vs goal, training load, food quality, streak" }, Reminders.on(this, k)) { Reminders.set(ctx, k, it) } }
        col.addView(RunUi.button(this, "Send all runs to the watch", RunUi.CARD, Color.WHITE) {
            Thread {
                val n = RunSettings.sendAllToWatch(ctx, force = true)
                runOnUiThread { Toast.makeText(ctx, if (n > 0) "Sending $n runs — they'll appear in the watch's History" else "Couldn't reach the watch", Toast.LENGTH_LONG).show() }
            }.start()
        }.apply { layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) } })
        }

        if (on("about")) {
        col.addView(RunUi.text(this, "Phone app " + packageManager.getPackageInfo(packageName, 0).versionName, RunUi.MUTED, 13f)
            .apply { setPadding(dp(4), dp(6), 0, 0) })
        val watchVer = RunUi.text(this, "Watch app: checking…", RunUi.MUTED, 13f).apply { setPadding(dp(4), dp(4), 0, 0) }
        col.addView(watchVer)
        Thread {
            val v = try {
                val items = com.google.android.gms.tasks.Tasks.await(com.google.android.gms.wearable.Wearable.getDataClient(ctx)
                    .getDataItems(android.net.Uri.parse("wear://*/watchinfo")))
                val found = items.map { com.google.android.gms.wearable.DataMapItem.fromDataItem(it).dataMap.getString("version") }.firstOrNull()
                items.release()
                found?.let { "Watch app $it" } ?: "Watch app: open Run on the watch once to show its build"
            } catch (e: Exception) { "Watch app: not connected" }
            runOnUiThread { watchVer.text = v }
        }.start()
        // App updates (from GitHub).
        val U = io.github.michealsizemore1.beyondstride.Updater
        val upStatus = RunUi.text(this, "", RunUi.MUTED, 13f).apply { setPadding(dp(4), dp(10), 0, 0) }
        val upBtn = RunUi.text(this, "Check for updates", RunUi.ACCENT, 15f, true).apply { setPadding(dp(4), dp(12), 0, dp(4)) }
        fun showUp() {
            val r = U.available(ctx)
            upBtn.text = if (r != null) "⬆ Install build ${r.build}" else "Check for updates"
            upStatus.text = if (r != null) "Build ${r.build} is ready (you're on ${U.currentBuild(ctx)}). Tap to install on this phone — the watch shows its own update."
                else "Up to date · build ${U.currentBuild(ctx)}" + (if (U.lastChecked(ctx) > 0) " · checked " + android.text.format.DateUtils.getRelativeTimeSpanString(U.lastChecked(ctx)) else "")
        }
        upBtn.setOnClickListener {
            val r = U.available(ctx)
            if (r != null) U.start(this, r) { upStatus.text = it }
            else {
                upStatus.text = "Checking…"
                U.forceNext(ctx)
                Thread { U.check(ctx, false); runOnUiThread { showUp() } }.start()
            }
        }
        col.addView(upBtn); col.addView(upStatus); showUp()
        }

        show(col)
    }

    private fun toggleBg() {
        val T = io.github.michealsizemore1.beyondstride.Theme
        getSharedPreferences("runsettings", 0).edit().putString("bgStyle", if (T.light) "dark" else "light").putLong("settingsAt", System.currentTimeMillis()).apply()
        T.load(this, "runsettings"); RunSettings.pushToWatch(this); render()
    }

    private fun setMenuStyle(v: String) {
        getSharedPreferences("runsettings", 0).edit().putString("menuStyle", v).putLong("settingsAt", System.currentTimeMillis()).apply()
        RunSettings.pushToWatch(this); page = null; render()
    }

    private fun show(col: LinearLayout) = setContentView(ScrollView(this).apply {
        setBackgroundColor(RunUi.BG)
        addView(col, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    })

    /** The list of setting groups: icon · name · current value · ›  (like Gear Tracker II). */
    private fun renderList(col: LinearLayout) {
        val ctx = this
        fun dp(v: Int) = RunUi.dp(ctx, v)
        val p = getSharedPreferences("runsettings", 0)
        fun onOff(b: Boolean) = if (b) "on" else "off"
        fun row(icon: String, title: String, value: String, onClick: () -> Unit) = col.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
            background = RunUi.rounded(RunUi.CARD, dp(18).toFloat())
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) }
            addView(RunUi.text(ctx, icon, RunUi.ACCENT, 26f, true).apply { gravity = android.view.Gravity.CENTER }, LinearLayout.LayoutParams(dp(44), WRAP_CONTENT))
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, dp(8), 0)
                addView(RunUi.text(ctx, title, Color.WHITE, 18f))
                if (value.isNotBlank()) addView(RunUi.text(ctx, value, 0xFF5FD38D.toInt(), 13f))
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(RunUi.text(ctx, "›", RunUi.MUTED, 28f))
            setOnClickListener { onClick() }
        })
        fun fmt(d: Double) = if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()
        val screens = try { org.json.JSONArray(p.getString("screens", "[]")).length() } catch (e: Exception) { 0 }
        row("▦", "Watch data screens", if (screens > 0) "$screens screens" else "Choose what the watch shows") { startActivity(Intent(ctx, ScreensActivity::class.java)) }
        row("➤", "Run options", (if (RunSettings.source(ctx) == "gps") "GPS" else "Stryd") + " · auto-pause ${onOff(RunSettings.bool(ctx, "autoPause"))}" +
            " · HR cap " + when (p.getString("hrCap", "easy")) { "off" -> "off"; "all" -> "all runs"; else -> "easy days" }) { page = "run"; render() }
        row("♪", "Audio prompts", if (!RunSettings.bool(ctx, "voice")) "Off" else "On · mile alerts ${onOff(RunSettings.bool(ctx, "voiceMiles"))}" +
            (if (p.getString("voiceMusic", "duck") == "pause") " · pause music" else " · lower music")) { page = "audio"; render() }
        row("∿", "Zones & power", "Max ${fmt(RunSettings.hrMax(ctx))} · Rest ${fmt(RunSettings.hrRest(ctx))} · CP ${RunSettings.cp(ctx)} W") { page = "zones"; render() }
        row("◉", "LiveTrack", if (Live.db(ctx).isBlank()) "Not set up" else if (p.getBoolean("liveOn", false)) "On · ${Live.contacts(ctx).size} contact(s)" else "Off") {
            startActivity(Intent(ctx, LiveTrackActivity::class.java)) }
        row("⌂", "Command Center", try { Uri.parse(RunSettings.ccUrl(ctx)).host ?: "" } catch (e: Exception) { "" }) { page = "cc"; render() }
        row("☁", "Stand-alone & cloud copy", if (!Standalone.on(ctx)) "Not yet — still syncing with the Command Center" else CcStatus.line(ctx).first.removePrefix("Stand-alone · ").replaceFirstChar { it.uppercase() }) { page = "standalone"; render() }
        row("✨", "AI food search", if (AiFood.hasKey(ctx)) "Key saved · AI search and label photos on" else "Add your Anthropic key") { page = "ai"; render() }
        row("⇪", "Backup", Backup.lastAt(ctx).takeIf { it > 0 }?.let { "Last: " + java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault()).format(java.util.Date(it)) +
            (if (Backup.auto(ctx) && Backup.uri(ctx) != null) " · auto" else "") } ?: "Not set up") { page = "backup"; render() }
        row("ⓘ", "About", "Phone app " + packageManager.getPackageInfo(packageName, 0).versionName) { page = "about"; render() }
        row("☰", "Menu style", "Cards · tap to switch back to classic (phone and watch)") { setMenuStyle("classic") }
        row("◐", "Background", if (io.github.michealsizemore1.beyondstride.Theme.light) "Lighter · tap for dark" else "Dark · tap for lighter") { toggleBg() }
        show(col)
    }

    private fun standalonePage(col: LinearLayout) {
        val ctx = this
        fun dp(v: Int) = RunUi.dp(ctx, v)
        fun lp(top: Int = 8) = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(top) }
        val on = Standalone.on(this)
        col.addView(RunUi.card(this).apply {
            addView(RunUi.text(ctx, if (on) "BeyondStride is stand-alone" else "Still syncing with the Command Center", Color.WHITE, 15f, true))
            if (on) {
                addView(RunUi.text(ctx, "Since " + java.text.SimpleDateFormat("EEE, MMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date(Standalone.since(ctx))) +
                    ". Nutrition, foods, Activities, training plans and settings are kept here and worked out here. The Command Center was left exactly as it was.", RunUi.MUTED, 12f).apply { setPadding(0, dp(4), 0, 0) })
            } else {
                addView(RunUi.text(ctx, "Switching reads everything one last time from the Command Center — every nutrition day, foods & favorites, Activities, training plans, settings — and keeps it here. " +
                    "After that BeyondStride stops sending to and reading from the Command Center. Nothing in the Command Center is changed or removed; it stays as it is now.", RunUi.MUTED, 12f).apply { setPadding(0, dp(4), 0, 0) })
                val prog = RunUi.text(ctx, "", 0xFFF2C94C.toInt(), 12f).apply { setPadding(0, dp(6), 0, 0) }
                addView(RunUi.button(ctx, "Switch to stand-alone", RunUi.ACCENT) {
                    android.app.AlertDialog.Builder(ctx).setTitle("Switch to stand-alone?")
                        .setMessage("BeyondStride will copy everything from the Command Center and stop syncing with it. The Command Center keeps all its data. Make a backup file first if you like.\n\nKeep the phone online until it finishes (it can take a minute).")
                        .setPositiveButton("Switch") { _, _ ->
                            prog.text = "Starting…"
                            Thread {
                                val r = try { Local.cutOver(applicationContext) { runOnUiThread { prog.text = Local.progress } } } catch (e: Exception) { "Something went wrong: ${e.message}" }
                                runOnUiThread { android.app.AlertDialog.Builder(ctx).setMessage(r).setPositiveButton("OK", null).show(); render() }
                            }.start()
                        }.setNegativeButton("Cancel", null).show()
                }.apply { layoutParams = lp(10) })
                addView(prog)
            }
        })
        // Cloud copy.
        col.addView(RunUi.text(this, "☁ Automatic cloud copy", Color.WHITE, 15f, true).apply { setPadding(0, dp(14), 0, 0) })
        col.addView(RunUi.card(this).apply {
            val ready = CloudCopy.ready(ctx); val at = CloudCopy.lastAt(ctx)
            addView(RunUi.text(ctx, when { !ready -> "Needs your Firebase database (set up in LiveTrack)"; !on -> "Starts when you switch to stand-alone"
                at <= 0 -> "No cloud copy yet"; else -> "Last copy: " + java.text.SimpleDateFormat("EEE, MMM d · h:mm a", java.util.Locale.getDefault()).format(java.util.Date(at)) }, Color.WHITE, 14f, true))
            CloudCopy.lastInfo(ctx).takeIf { it.isNotBlank() && on }?.let { addView(RunUi.text(ctx, it, RunUi.MUTED, 12f)) }
            CloudCopy.lastError(ctx).takeIf { it.isNotBlank() && on }?.let { addView(RunUi.text(ctx, "Last try didn't go through: $it — it retries automatically", 0xFFF2C94C.toInt(), 12f)) }
            addView(RunUi.text(ctx, "Kept in BeyondStride's own place in your Firebase database (not the Command Center's), updated a few minutes after anything changes. " +
                "Your Anthropic key is never included. On a new phone: set up the same database in LiveTrack, enter this code below, then Restore from cloud.", RunUi.DIM, 12f).apply { setPadding(0, dp(6), 0, 0) })
            addView(RunUi.text(ctx, "Cloud copy code: " + NutritionSync.key(ctx), RunUi.ACCENT, 13f, true).apply { setPadding(0, dp(8), 0, 0); setTextIsSelectable(true) })
            if (ready && on) addView(RunUi.button(ctx, "Copy to cloud now", RunUi.ACCENT) {
                Toast.makeText(ctx, "Copying…", Toast.LENGTH_SHORT).show()
                Thread { val ok = CloudCopy.sync(applicationContext, force = true); runOnUiThread { Toast.makeText(ctx, if (ok) "Cloud copy updated" else "Didn't go through: ${CloudCopy.lastError(ctx).ifEmpty { "some runs didn't upload" }} — it will retry", Toast.LENGTH_LONG).show(); render() } }.start()
            }.apply { layoutParams = lp(10) })
            if (ready) addView(RunUi.button(ctx, "Restore from cloud…", RunUi.CARD, Color.WHITE) {
                Thread {
                    val meta = CloudCopy.peek(applicationContext)
                    runOnUiThread {
                        if (meta == null) { Toast.makeText(ctx, "No cloud copy found for this code", Toast.LENGTH_LONG).show(); return@runOnUiThread }
                        android.app.AlertDialog.Builder(ctx).setTitle("Restore from cloud?")
                            .setMessage("Cloud copy from " + java.text.SimpleDateFormat("EEE, MMM d · h:mm a", java.util.Locale.getDefault()).format(java.util.Date(meta.optLong("at"))) + "\n" + meta.optString("info") +
                                "\n\nThis replaces BeyondStride's data on this phone with the cloud copy.")
                            .setPositiveButton("Restore") { _, _ -> Thread { val r = try { CloudCopy.restore(applicationContext) ?: "Couldn't read the cloud copy" } catch (e: Exception) { "Restore failed: ${e.message}" }
                                runOnUiThread { android.app.AlertDialog.Builder(ctx).setMessage(r).setPositiveButton("OK", null).show(); render() } }.start() }
                            .setNegativeButton("Cancel", null).show()
                    }
                }.start()
            }.apply { layoutParams = lp() })
            addView(RunUi.button(ctx, "Use a code from another phone…", RunUi.CARD, Color.WHITE) {
                val e = EditText(ctx).apply { hint = "Cloud copy code"; setText("") }
                android.app.AlertDialog.Builder(ctx).setTitle("Cloud copy code").setView(e).setPositiveButton("Use") { _, _ ->
                    val k = e.text.toString().trim().lowercase()
                    if (k.length < 16 || !k.all { it.isLetterOrDigit() }) Toast.makeText(ctx, "That doesn't look like a code", Toast.LENGTH_LONG).show()
                    else { getSharedPreferences("runsettings", 0).edit().putString("nutritionKey", k).apply(); Toast.makeText(ctx, "Code saved — now tap Restore from cloud", Toast.LENGTH_LONG).show(); render() }
                }.setNegativeButton("Cancel", null).show()
            }.apply { layoutParams = lp() })
        })
        // Health Connect import.
        col.addView(RunUi.text(this, "⤓ Import workouts from Health Connect", Color.WHITE, 15f, true).apply { setPadding(0, dp(14), 0, 0) })
        col.addView(RunUi.card(this).apply {
            addView(Switch(ctx).apply {
                text = "Add new workouts automatically"; setTextColor(Color.WHITE); textSize = 15f
                isChecked = ActivityImport.enabled(ctx)
                setOnCheckedChangeListener { _, v ->
                    ActivityImport.setEnabled(ctx, v)
                    if (v) Thread { val g = ActivityImport.granted(applicationContext); runOnUiThread { if (!g) try {
                        @Suppress("DEPRECATION") startActivityForResult(androidx.health.connect.client.PermissionController.createRequestPermissionResultContract().createIntent(ctx, ActivityImport.PERMS), 83)
                    } catch (e: Exception) { Toast.makeText(ctx, "Health Connect isn't available on this phone", Toast.LENGTH_LONG).show() } } }.start()
                }
            })
            addView(RunUi.text(ctx, "Garmin, Strava, Samsung Health and others that write to Health Connect: each new workout (last 2 weeks) is added to Activities about once an hour. " +
                "BeyondStride's own runs and ones already in Activities are skipped. You can also import a CSV from the Activities tab." +
                (if (!on) " Works after you switch to stand-alone." else ""), RunUi.MUTED, 12f))
            ActivityImport.lastInfo(ctx).takeIf { it.isNotBlank() }?.let { addView(RunUi.text(ctx, it, RunUi.DIM, 12f).apply { setPadding(0, dp(4), 0, 0) }) }
            if (on) addView(RunUi.button(ctx, "Import now", RunUi.CARD, Color.WHITE) {
                Thread { val n = try { ActivityImport.fromHealthConnect(applicationContext) } catch (e: Exception) { 0 }
                    runOnUiThread { Toast.makeText(ctx, if (n > 0) "Added $n workout${if (n == 1) "" else "s"}" else if (!ActivityImport.granted(ctx)) "Allow BeyondStride to read workouts in Health Connect first" else "Nothing new", Toast.LENGTH_LONG).show(); render() } }.start()
            }.apply { layoutParams = lp(10) })
        })
    }

    private fun newBackup() {
        @Suppress("DEPRECATION") startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/zip").putExtra(Intent.EXTRA_TITLE, Backup.fileName()), 81)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            81 -> {
                Backup.remember(this, uri)
                Thread {
                    val r = try { "Backed up: " + Backup.write(this, uri) } catch (e: Exception) { "Backup failed: ${e.message}" }
                    runOnUiThread { Toast.makeText(this, r, Toast.LENGTH_LONG).show(); render() }
                }.start()
            }
            82 -> Thread {
                val meta = Backup.peek(this, uri)
                runOnUiThread {
                    if (meta == null) { Toast.makeText(this, "That isn't a BeyondStride backup", Toast.LENGTH_LONG).show(); return@runOnUiThread }
                    val made = java.text.SimpleDateFormat("MMM d, yyyy h:mm a", java.util.Locale.getDefault()).format(java.util.Date(meta.optLong("created")))
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Restore this backup?")
                        .setMessage("Made $made · ${meta.optInt("runs")} runs · ${meta.optInt("courses")} courses.\n\n" +
                            "Runs and courses in the backup are added back. Settings, zones, workouts and plan are replaced with the backup's. " +
                            "The watch gets them too.")
                        .setPositiveButton("Restore") { _, _ ->
                            Thread {
                                val r = try { Backup.restore(this, uri) } catch (e: Exception) { "Restore failed: ${e.message}" }
                                runOnUiThread { Toast.makeText(this, r, Toast.LENGTH_LONG).show(); render() }
                            }.start()
                        }
                        .setNegativeButton("Cancel", null).show()
                }
            }.start()
        }
    }
}
