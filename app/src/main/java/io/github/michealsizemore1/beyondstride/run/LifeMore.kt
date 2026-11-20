package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.print.PrintAttributes
import android.print.PrintManager
import android.text.InputType
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.util.Locale

/**
 * 🗂️ The Life tab's "More" page — the rest of the Command Center as BeyondStride screens:
 * 🔍 journal search · 💬 Ask AI + saved answers · 🩺 BP report · 📁 files · ⚙️ settings (the 🧘 meditation timer is built here but shown in ☰ Tabs → Meditate).
 * Everything reads and changes the Command Center's own data through window.ccNative.
 */
class LifeMore(private val act: Activity, private val web: WebView, private val needKey: () -> Unit) {
    val col = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(d(14), 0, d(14), d(24)) }
    private val ui = Handler(Looper.getMainLooper())
    lateinit var meditView: LinearLayout
    /** 🩺 Blood pressure report — shown in ☰ More → Wellness, with the BP chart (not in Tools). */
    lateinit var bpView: LinearLayout
    private fun d(v: Int) = RunUi.dp(act, v)
    private fun str(r: String?) = try { JSONTokener(r ?: "").nextValue() as? String ?: "" } catch (e: Exception) { "" }
    private fun js(call: String, cb: (String) -> Unit = {}) = web.evaluateJavascript("window.ccNative&&ccNative.search?$call:null") { cb(it ?: "") }
    private fun input(hint: String, lines: Int = 1) = EditText(act).apply {
        this.hint = hint; setTextColor(Color.WHITE); setHintTextColor(RunUi.DIM); textSize = 15f
        background = RunUi.rounded(0xFF26302C.toInt(), d(10).toFloat()); setPadding(d(12), d(9), d(12), d(9))
        if (lines > 1) { minLines = lines; gravity = Gravity.TOP } else isSingleLine = true
    }
    private fun lp(top: Int = 8) = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = d(top) }
    private fun title(c: LinearLayout, t: String) = c.addView(RunUi.text(act, t, Color.WHITE, 16f, true))
    private fun note(t: String) = RunUi.text(act, t, RunUi.DIM, 10f).apply { setPadding(0, d(6), 0, 0) }

    fun load() { loadMeditation(); loadArchive(); loadSettings(); js("ccNative.filesLoad()") }

    private fun build() {
        col.removeAllViews()
        meditView = meditCard()   // shown in ☰ Tabs → Meditate
        bpView = bpCard()   // shown in ☰ More → Wellness
        col.addView(searchCard()); col.addView(askCard()); col.addView(filesCard())
        // ⚙️ Command Center settings (AI key, sync) moved to BeyondStride's Settings → Life tab.
        col.addView(RunUi.card(act).apply { title(this, "⚙️ Settings"); addView(note("The Life tab's AI key, journal access and sync are in BeyondStride's Settings → 📓 Life tab."))
            addView(RunUi.button(act, "Open Settings › Life tab", 0xFF374151.toInt(), Color.WHITE) { act.startActivity(Intent(act, RunSettingsActivity::class.java).putExtra("page", "life")) }, lp()) })
    }

    // ── 🔍 Search ──
    private val results = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
    private fun searchCard() = RunUi.card(act).apply {
        title(this, "🔍 Search your journal")
        val q = input("Search days, reviews, Bible notes, runs…").apply { imeOptions = EditorInfo.IME_ACTION_SEARCH }
        fun run() {
            val text = q.text.toString(); if (text.trim().length < 2) return
            js("ccNative.search(${JSONObject.quote(text)})") { r ->
                val arr = try { JSONArray(str(r)) } catch (e: Exception) { JSONArray() }
                results.removeAllViews()
                results.addView(RunUi.text(act, if (arr.length() == 0) "Nothing found for \"$text\"." else "${arr.length()} result${if (arr.length() == 1) "" else "s"}", RunUi.MUTED, 12f).apply { setPadding(0, d(8), 0, d(2)) })
                for (i in 0 until arr.length()) { val o = arr.getJSONObject(i)
                    results.addView(LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(d(10), d(8), d(10), d(8)); background = RunUi.rounded(0xFF26302C.toInt(), d(10).toFloat())
                        addView(RunUi.text(act, o.optString("date") + " · " + o.optString("where"), RunUi.ACCENT, 12f, true))
                        addView(RunUi.text(act, o.optString("text"), Color.WHITE, 13f)) }, lp(6))
                }
            }
        }
        q.setOnEditorActionListener { _, _, _ -> run(); true }
        addView(q, lp())
        addView(RunUi.button(act, "Search", 0xFF374151.toInt(), Color.WHITE) { run() }, lp())
        addView(results)
    }

    // ── 💬 Ask AI + saved answers ──
    private val answer = RunUi.text(act, "", Color.WHITE, 14f).apply { setLineSpacing(0f, 1.25f) }
    private val saveBtn = RunUi.button(act, "📁 Save answer", 0xFF374151.toInt(), Color.WHITE) { js("ccNative.askSave()") { loadArchive(); saveBtnDone() } }
    private val archive = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
    private fun saveBtnDone() { saveBtn.text = "✓ Saved"; saveBtn.isEnabled = false }
    private fun askCard() = RunUi.card(act).apply {
        title(this, "💬 Ask AI about your journal")
        val q = input("e.g. When did my knee start bothering me?", 2)
        addView(q, lp())
        addView(RunUi.button(act, "✨ Ask", 0xFF7C3AED.toInt(), Color.WHITE) {
            val text = q.text.toString().trim(); if (text.isEmpty()) return@button
            js("ccNative.ask(${JSONObject.quote(text)})") { r ->
                if (r.contains("nokey")) { needKey(); return@js }
                answer.text = "✨ Reading your journal…"; saveBtn.visibility = View.GONE; ui.postDelayed(askPoll, 2500)
            }
        }, lp())
        addView(answer.apply { setPadding(0, d(10), 0, 0) })
        addView(saveBtn.apply { visibility = View.GONE }, lp())
        addView(note("Uses the Command Center's Ask AI: all your days, reviews and runs."))
        addView(RunUi.text(act, "📁 Saved AI answers", RunUi.ACCENT, 13f, true).apply { setPadding(0, d(14), 0, d(2)) })
        addView(archive)
    }
    private val askPoll: Runnable = object : Runnable { override fun run() {
        js("ccNative.askState()") { r ->
            val j = try { JSONObject(str(r)) } catch (e: Exception) { JSONObject() }
            val a = j.optString("answer")
            when {
                a.isNotEmpty() && !j.optBoolean("busy") -> { answer.text = a; saveBtn.text = "📁 Save answer"; saveBtn.isEnabled = true; saveBtn.visibility = View.VISIBLE }
                j.optBoolean("busy") || j.optString("status").startsWith("Preparing") -> ui.postDelayed(this, 2000)
                j.optString("status").isNotEmpty() -> answer.text = j.optString("status")
                else -> ui.postDelayed(this, 2000)
            }
        } } }
    private fun loadArchive() {
        js("ccNative.archive()") { r ->
            val arr = try { JSONArray(str(r)) } catch (e: Exception) { JSONArray() }
            archive.removeAllViews()
            if (arr.length() == 0) archive.addView(RunUi.text(act, "None saved yet.", RunUi.MUTED, 12f))
            for (i in 0 until minOf(arr.length(), 40)) { val o = arr.getJSONObject(i)
                archive.addView(RunUi.text(act, "${o.optString("ts")} · ${o.optString("label")}\n${o.optString("text").take(110).replace('\n', ' ')}…", Color.WHITE, 13f).apply {
                    setPadding(d(10), d(8), d(10), d(8)); background = RunUi.rounded(0xFF26302C.toInt(), d(10).toFloat())
                    setOnClickListener { AlertDialog.Builder(act).setTitle(o.optString("label") + " · " + o.optString("ts")).setMessage(o.optString("text"))
                        .setPositiveButton("Close", null).setNeutralButton("Delete") { _, _ -> js("ccNative.archiveDelete($i)") { loadArchive() } }.show() }
                }, lp(6))
            }
        }
    }

    // ── 🧘 Meditation timer (looks like the Command Center's ⏱ Session Timer) ──
    private val timerText = RunUi.text(act, "10:00", Color.WHITE, 40f, true).apply { gravity = Gravity.CENTER }
    private val timerStatus = RunUi.text(act, "READY", 0xFFC4B5FD.toInt(), 11f, true).apply { gravity = Gravity.CENTER; letterSpacing = 0.1f }
    private val ring = Ring(act)
    private val timerBtn = RunUi.button(act, "▶ Start", 0xFF8B5CF6.toInt(), Color.WHITE) { toggleTimer() }
    private val presets = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
    private val medLog = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
    private var total = 600; private var endAt = 0L; private var leftMs = 600_000L; private var running = false
    private val medNotes = input("Notes (optional)", 2)
    private val customMin = input("min").apply { inputType = InputType.TYPE_CLASS_NUMBER; gravity = Gravity.CENTER }
    /** The purple progress ring around the time. */
    private class Ring(ctx: android.content.Context) : View(ctx) {
        var frac = 1f
        private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE; strokeCap = android.graphics.Paint.Cap.ROUND }
        override fun onDraw(c: android.graphics.Canvas) {
            val w = 10 * resources.displayMetrics.density; p.strokeWidth = w
            val r = android.graphics.RectF(w, w, width - w, height - w)
            p.color = 0xFF3B2A66.toInt(); c.drawArc(r, 0f, 360f, false, p)
            p.color = 0xFF8B5CF6.toInt(); c.drawArc(r, -90f, 360f * frac, false, p)
        }
    }
    private fun setDuration(m: Int) { if (running) return; total = m.coerceIn(1, 120) * 60; leftMs = total * 1000L; timerStatus.text = "READY"; show(); drawPresets() }
    private fun drawPresets() {
        presets.removeAllViews()
        listOf(5, 10, 15, 20, 30).forEach { m -> val on = total == m * 60
            presets.addView(RunUi.text(act, "${m}m", if (on) Color.WHITE else 0xFFD1D5DB.toInt(), 13f, true).apply { gravity = Gravity.CENTER
                setPadding(d(10), d(6), d(10), d(6)); background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = d(7).toFloat()
                    setColor(if (on) 0xFF8B5CF6.toInt() else 0xFF26302C.toInt()); setStroke(d(1), if (on) 0xFF8B5CF6.toInt() else 0xFF3A4440.toInt()) }
                setOnClickListener { setDuration(m) } }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = d(3); marginEnd = d(3) }) }
    }
    private fun meditCard() = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL
        addView(RunUi.card(act).apply { gravity = Gravity.CENTER_HORIZONTAL; layoutParams = lp(12)
            addView(RunUi.text(act, "⏱ SESSION TIMER", 0xFFA78BFA.toInt(), 12f, true).apply { gravity = Gravity.CENTER; letterSpacing = 0.06f }, lp(0))
            addView(FrameLayout(act).apply {
                addView(ring, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                addView(LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; addView(timerText); addView(timerStatus) }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            }, LinearLayout.LayoutParams(d(170), d(170)).apply { topMargin = d(12); gravity = Gravity.CENTER_HORIZONTAL })
            addView(presets, lp(14)); drawPresets()
            addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
                addView(customMin, LinearLayout.LayoutParams(d(80), WRAP_CONTENT))
                addView(RunUi.text(act, "Set", 0xFFC4B5FD.toInt(), 13f, true).apply { setPadding(d(14), d(7), d(14), d(7)); background = RunUi.rounded(0xFF3B2A66.toInt(), d(7).toFloat())
                    setOnClickListener { setDuration(customMin.text.toString().toIntOrNull() ?: 10) } }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = d(6) }) }, lp(10))
            addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
                addView(timerBtn, LinearLayout.LayoutParams(d(120), WRAP_CONTENT).apply { marginEnd = d(8) })
                addView(RunUi.button(act, "↺ Reset", 0xFF374151.toInt(), Color.WHITE) { stopTimer(); leftMs = total * 1000L; timerStatus.text = "READY"; show() }, LinearLayout.LayoutParams(d(110), WRAP_CONTENT)) }, lp(12))
        })
        addView(soundsCard)
        addView(RunUi.card(act).apply { layoutParams = lp(12)
            addView(RunUi.text(act, "📝 Session notes", 0xFFA78BFA.toInt(), 13f, true))
            addView(medNotes, lp())
            addView(note("When the time is up you'll hear a bell; the session is logged and Meditation is checked off. Keeps the screen on while running."))
            addView(RunUi.text(act, "Recent sessions", RunUi.ACCENT, 13f, true).apply { setPadding(0, d(12), 0, d(2)) })
            addView(medLog)
        })
    }
    // 🎵 Ambient Sounds — played by the Command Center page (same sounds as the website).
    private val soundBtns = HashMap<String, TextView>()
    private val soundsCard: LinearLayout by lazy { RunUi.card(act).apply { layoutParams = lp(12)
        addView(RunUi.text(act, "🎵 AMBIENT SOUNDS", 0xFF34D399.toInt(), 12f, true).apply { letterSpacing = 0.06f })
        addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, d(10), 0, 0)
            listOf(Triple("rain", "🌧️ Rain", 0xFF38BDF8.toInt()), Triple("ocean", "🌊 Ocean", 0xFF60A5FA.toInt()), Triple("forest", "🌳 Forest", 0xFF4ADE80.toInt())).forEach { (k, t, c) ->
                val b = RunUi.text(act, t, c, 13f, true).apply { gravity = Gravity.CENTER; setPadding(d(12), d(8), d(12), d(8))
                    setOnClickListener { web.evaluateJavascript("window.ccNative&&ccNative.meditSound&&ccNative.meditSound('$k')") { loadSounds() } } }
                soundBtns[k] = b; paintSound(k, false)
                addView(b, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = d(8) }) } })
        addView(note("Tap to turn a sound on or off. They keep playing while you stay in the Life tab."))
    } }
    private fun paintSound(k: String, on: Boolean) {
        val c = when (k) { "rain" -> 0xFF38BDF8; "ocean" -> 0xFF60A5FA; else -> 0xFF4ADE80 }.toInt()
        soundBtns[k]?.background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = d(10).toFloat(); setColor(if (on) c and 0x55FFFFFF else 0xFF1F2925.toInt()); setStroke(d(1), c) }
        soundBtns[k]?.setTextColor(if (on) Color.WHITE else c)
    }
    fun loadSounds() { web.evaluateJavascript("window.ccNative&&ccNative.meditSounds?ccNative.meditSounds():'{}'") { r ->
        val o = try { JSONObject(str(r)) } catch (e: Exception) { JSONObject() }; soundBtns.keys.forEach { paintSound(it, o.optBoolean(it)) } } }
    private fun show() { val s = ((leftMs + 999) / 1000).toInt(); timerText.text = String.format(Locale.US, "%d:%02d", s / 60, s % 60); ring.frac = (leftMs.toFloat() / (total * 1000f)).coerceIn(0f, 1f); ring.invalidate() }
    private val tick: Runnable = object : Runnable { override fun run() {
        if (!running) return
        leftMs = endAt - SystemClock.elapsedRealtime()
        if (leftMs <= 0) { leftMs = 0; show(); finishTimer(); return }
        show(); ui.postDelayed(this, 250)
    } }
    private fun toggleTimer() {
        if (running) { stopTimer(); timerStatus.text = "PAUSED"; return }
        running = true; endAt = SystemClock.elapsedRealtime() + leftMs; timerBtn.text = "⏸ Pause"; timerStatus.text = "BREATHE"
        act.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); ui.post(tick)
    }
    private fun stopTimer() { running = false; timerBtn.text = "▶ Start"; ui.removeCallbacks(tick); act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    private fun finishTimer() {
        stopTimer()
        try { val tg = ToneGenerator(AudioManager.STREAM_ALARM, 90); tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 900); ui.postDelayed({ tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 900) }, 1500); ui.postDelayed({ tg.release() }, 3500) } catch (e: Exception) { }
        js("ccNative.meditLog(${total / 60},${JSONObject.quote(medNotes.text.toString())})") { medNotes.setText(""); loadMeditation() }
        timerText.text = "Done ✓"; timerStatus.text = "COMPLETE"; leftMs = total * 1000L
    }
    fun pause() { if (running) stopTimer() }
    private fun loadMeditation() {
        js("ccNative.meditSessions()") { r ->
            val arr = try { JSONArray(str(r)) } catch (e: Exception) { JSONArray() }
            medLog.removeAllViews()
            if (arr.length() == 0) medLog.addView(RunUi.text(act, "No sessions yet.", RunUi.MUTED, 12f))
            for (i in 0 until minOf(arr.length(), 7)) { val o = arr.getJSONObject(i)
                medLog.addView(RunUi.text(act, "${o.optString("date")} · ${o.optInt("dur")} min" + (o.optString("notes").takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), Color.WHITE, 13f).apply { setPadding(0, d(3), 0, d(3)) })
            }
        }
    }

    // ── 🩺 BP report ──
    private var bpDays = 30
    private val bpBody = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
    private var bpRows: List<JSONObject> = emptyList()
    private fun bpCard() = RunUi.card(act).apply {
        title(this, "🩺 Blood pressure report")
        val ranges = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, d(8), 0, 0) }
        fun drawRanges() { ranges.removeAllViews(); listOf(14, 30, 60, 90).forEach { n -> ranges.addView(RunUi.text(act, "$n days", if (n == bpDays) Color.BLACK else Color.WHITE, 12f, true).apply {
            setPadding(d(10), d(5), d(10), d(5)); background = RunUi.rounded(if (n == bpDays) RunUi.ACCENT else 0xFF2A3430.toInt(), d(14).toFloat())
            setOnClickListener { bpDays = n; drawRanges(); loadBP() } }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = d(6) }) } }
        drawRanges(); addView(ranges)
        addView(RunUi.button(act, "Show report", 0xFF374151.toInt(), Color.WHITE) { loadBP() }, lp())
        addView(bpBody)
    }
    /** 🖨️ Print the BP report for the last [days] days straight away (Wellness section button). */
    fun printReport(days: Int) {
        bpDays = days
        web.evaluateJavascript("window.ccNative&&ccNative.dash?ccNative.dash($days):null") { r ->
            val arr = try { JSONArray(str(r)) } catch (e: Exception) { JSONArray() }
            bpRows = (0 until arr.length()).map { arr.getJSONObject(it) }.filter { it.optInt("sys") > 0 }.reversed()
            if (bpRows.isEmpty()) return@evaluateJavascript
            printBP(bpRows.map { it.optInt("sys") }.average(), bpRows.map { it.optInt("dia") }.average())
        }
    }
    private fun loadBP() {
        web.evaluateJavascript("window.ccNative&&ccNative.dash?ccNative.dash($bpDays):null") { r ->
            val arr = try { JSONArray(str(r)) } catch (e: Exception) { JSONArray() }
            bpRows = (0 until arr.length()).map { arr.getJSONObject(it) }.filter { it.optInt("sys") > 0 }.reversed()
            bpBody.removeAllViews()
            if (bpRows.isEmpty()) { bpBody.addView(RunUi.text(act, "No blood pressure readings in the last $bpDays days.", RunUi.MUTED, 12f).apply { setPadding(0, d(8), 0, 0) }); return@evaluateJavascript }
            val s = bpRows.map { it.optInt("sys") }.average(); val di = bpRows.map { it.optInt("dia") }.average()
            val pulses = bpRows.map { it.optInt("pulse") }.filter { it > 0 }
            bpBody.addView(RunUi.text(act, String.format(Locale.US, "Average %.0f/%.0f%s · %d readings", s, di, if (pulses.isNotEmpty()) " · pulse ${pulses.average().toInt()}" else "", bpRows.size), RunUi.ACCENT, 14f, true).apply { setPadding(0, d(10), 0, d(4)) })
            val t = android.widget.TableLayout(act)
            fun row(vararg c: String, head: Boolean = false) = android.widget.TableRow(act).apply { c.forEach { addView(RunUi.text(act, it, if (head) RunUi.MUTED else Color.WHITE, 12f, head).apply { setPadding(d(4), d(3), d(16), d(3)) }) } }
            t.addView(row("Date", "BP", "Pulse", "Morning HR", head = true))
            bpRows.forEach { o -> t.addView(row(o.optString("d"), "${o.optInt("sys")}/${o.optInt("dia")}", o.optInt("pulse").takeIf { it > 0 }?.toString() ?: "–", o.optInt("restHR").takeIf { it > 0 }?.toString() ?: "–")) }
            bpBody.addView(t)
            bpBody.addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, d(10), 0, 0)
                addView(RunUi.button(act, "🖨️ Print / PDF", 0xFF374151.toInt(), Color.WHITE) { printBP(s, di) }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = d(8) })
                addView(RunUi.button(act, "Share", RunUi.ACCENT) { shareBP(s, di) }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)) })
        }
    }
    private fun bpText(s: Double, di: Double) = "Blood pressure — last $bpDays days\nAverage ${s.toInt()}/${di.toInt()} (${bpRows.size} readings)\n\n" +
        bpRows.joinToString("\n") { "${it.optString("d")}  ${it.optInt("sys")}/${it.optInt("dia")}" + (it.optInt("pulse").takeIf { p -> p > 0 }?.let { p -> "  pulse $p" } ?: "") }
    private fun shareBP(s: Double, di: Double) {
        act.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "Blood pressure report").putExtra(Intent.EXTRA_TEXT, bpText(s, di)), "Share BP report"))
    }
    private var printView: WebView? = null
    private fun printBP(s: Double, di: Double) {
        val rows = bpRows.joinToString("") { "<tr><td>${it.optString("d")}</td><td>${it.optInt("sys")}/${it.optInt("dia")}</td><td>${it.optInt("pulse").takeIf { p -> p > 0 } ?: "–"}</td><td>${it.optInt("restHR").takeIf { p -> p > 0 } ?: "–"}</td></tr>" }
        val html = "<html><body style='font-family:sans-serif'><h2>Blood pressure report</h2><p>Last $bpDays days · ${bpRows.size} readings · average <b>${s.toInt()}/${di.toInt()}</b></p>" +
            "<table border='1' cellpadding='6' style='border-collapse:collapse'><tr><th>Date</th><th>BP</th><th>Pulse</th><th>Morning HR</th></tr>$rows</table></body></html>"
        printView = WebView(act).apply { webViewClient = object : WebViewClient() { override fun onPageFinished(v: WebView, url: String) {
            (act.getSystemService(Activity.PRINT_SERVICE) as PrintManager).print("BP report", v.createPrintDocumentAdapter("BP report"), PrintAttributes.Builder().build()) } }
            loadDataWithBaseURL(null, html, "text/html", "utf-8", null) }
    }

    // ── 📁 Files ──
    private val fileList = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
    private fun filesCard() = RunUi.card(act).apply {
        title(this, "📁 Files")
        addView(RunUi.button(act, "+ Add a file", 0xFF374151.toInt(), Color.WHITE) {
            try { act.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), PICK) } catch (e: Exception) { }
        }, lp())
        addView(fileList)
        addView(note("Your Command Center uploads on this phone (the journal archive and other files). Tap a file to open or delete it. Files stay on the device they were added on — they're too big to sync."))
    }
    /** From the Command Center: the file list. */
    fun onFiles(json: String) = ui.post {
        val arr = try { JSONArray(json) } catch (e: Exception) { JSONArray() }
        fileList.removeAllViews()
        if (arr.length() == 0) fileList.addView(RunUi.text(act, "No files on this phone yet.", RunUi.MUTED, 12f).apply { setPadding(0, d(8), 0, 0) })
        for (i in 0 until arr.length()) { val o = arr.getJSONObject(i)
            val size = o.optLong("size").let { if (it > 1_000_000) String.format(Locale.US, "%.1f MB", it / 1e6) else "${it / 1000} KB" }
            fileList.addView(LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(d(10), d(8), d(10), d(8)); background = RunUi.rounded(0xFF26302C.toInt(), d(10).toFloat())
                addView(RunUi.text(act, (if (o.optString("src") == "ja") "📓 " else "📄 ") + o.optString("name"), Color.WHITE, 14f, true))
                addView(RunUi.text(act, listOf(o.optString("date"), size, if (o.optString("src") == "ja") "journal archive" else "").filter { it.isNotBlank() }.joinToString(" · "), RunUi.MUTED, 11f))
                setOnClickListener { AlertDialog.Builder(act).setTitle(o.optString("name"))
                    .setPositiveButton("Open") { _, _ -> js("ccNative.fileGet(${JSONObject.quote(o.optString("src"))},${o.optInt("id")})") }
                    .setNeutralButton("Delete") { _, _ -> AlertDialog.Builder(act).setMessage("Delete ${o.optString("name")}?").setPositiveButton("Delete") { _, _ ->
                        js("ccNative.fileDelete(${JSONObject.quote(o.optString("src"))},${o.optInt("id")})") }.setNegativeButton("Keep", null).show() }
                    .setNegativeButton("Cancel", null).show() }
            }, lp(6))
        }
    }
    /** From the Command Center: one file's bytes — save to the cache and open it with the right app. */
    fun onFile(name: String, type: String, b64: String) {
        Thread {
            try {
                val dir = File(act.cacheDir, "backup").apply { mkdirs() }
                val f = File(dir, name.replace(Regex("[^A-Za-z0-9._ -]"), "_")); f.writeBytes(Base64.decode(b64, Base64.DEFAULT))
                val uri = FileProvider.getUriForFile(act, act.packageName + ".files", f)
                act.runOnUiThread { try { act.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(uri, type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), name)) } catch (e: Exception) { } }
            } catch (e: Exception) { }
        }.start()
    }
    /** A file picked on the phone → add it to the Command Center's uploads (sent in pieces). */
    fun onPicked(uri: Uri) {
        Thread {
            try {
                val cr = act.contentResolver
                var name = "file"; cr.query(uri, null, null, null, null)?.use { c -> if (c.moveToFirst()) { val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME); if (i >= 0) name = c.getString(i) } }
                val bytes = cr.openInputStream(uri)?.use { it.readBytes() } ?: return@Thread
                if (bytes.size > 25_000_000) { act.runOnUiThread { AlertDialog.Builder(act).setMessage("That file is over 25 MB — add it in 📓 Full → Uploads instead.").setPositiveButton("OK", null).show() }; return@Thread }
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP); val type = cr.getType(uri) ?: "application/octet-stream"
                act.runOnUiThread {
                    web.evaluateJavascript("ccNative.fileStart()", null)
                    var i = 0; while (i < b64.length) { web.evaluateJavascript("ccNative.filePart('${b64.substring(i, minOf(b64.length, i + 500_000))}')", null); i += 500_000 }
                    web.evaluateJavascript("ccNative.fileEnd(${JSONObject.quote(name)},${JSONObject.quote(type)})", null)
                }
            } catch (e: Exception) { }
        }.start()
    }

    // ── ⚙️ Settings ──
    private val setText = RunUi.text(act, "", RunUi.MUTED, 12f)
    private val keyState = RunUi.text(act, "", Color.WHITE, 14f, true)
    private val ctxToggle = RunUi.text(act, "", RunUi.ACCENT, 13f, true)
    private var aiContext = true
    private fun settingsCard() = RunUi.card(act).apply {
        title(this, "⚙️ Command Center settings")
        addView(keyState.apply { setPadding(0, d(8), 0, d(4)) })
        val key = input("Paste Anthropic API key (sk-ant-…)").apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        addView(key, lp())
        addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, d(8), 0, 0)
            addView(RunUi.button(act, "Save key", RunUi.ACCENT) { val k = key.text.toString().trim(); if (k.isNotEmpty()) js("ccNative.setKey(${JSONObject.quote(k)})") { key.setText(""); loadSettings() } },
                LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = d(8) })
            addView(RunUi.button(act, "Remove key", 0xFF374151.toInt(), Color.WHITE) { js("ccNative.setKey('')") { loadSettings() } }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)) })
        addView(note("The Command Center's own key: it stays in this phone's Command Center only — never synced or backed up. BeyondStride's key is separate and is never given to it."))
        addView(ctxToggle.apply { setPadding(0, d(12), 0, d(4)); setOnClickListener { js("ccNative.setAIContext(${!aiContext})") { loadSettings() } } })
        addView(setText.apply { setPadding(0, d(8), 0, 0) })
        addView(RunUi.button(act, "🔄 Sync now", 0xFF374151.toInt(), Color.WHITE) { js("ccNative.syncNow()") { ui.postDelayed({ loadSettings() }, 4000) } }, lp())
    }
    private fun loadSettings() {
        js("ccNative.settings()") { r ->
            val j = try { JSONObject(str(r)) } catch (e: Exception) { return@js }
            keyState.text = if (j.optBoolean("hasKey")) "🔑 AI key: saved ✓" else "🔑 AI key: not set (needed for AI Fill, Ask AI and Bible Study)"
            aiContext = j.optBoolean("aiContext", true)
            ctxToggle.text = (if (aiContext) "☑" else "☐") + "  Let the AI read your journal for answers and reviews"
            val sync = j.optJSONObject("sync")
            setText.text = listOfNotNull(
                sync?.let { if (it.optBoolean("linked")) "🔄 Sync: on" + (if (it.optInt("pending") > 0) " · ${it.optInt("pending")} waiting to upload" else " · up to date") else "🔄 Sync: not linked to BeyondStride yet" },
                j.optString("status").takeIf { it.isNotBlank() }, j.optString("storage").takeIf { it.isNotBlank() }?.let { "💾 $it" }).joinToString("\n")
        }
    }

    init { build() }   // last, so every view above exists first

    companion object { const val PICK = 42 }
}
