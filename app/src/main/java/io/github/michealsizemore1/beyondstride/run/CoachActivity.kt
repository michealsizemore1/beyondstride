package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Html
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject

/** 🧠 Coach AI — pick what it looks at, then ask it anything (a conversation). */
class CoachActivity : Activity() {
    private lateinit var col: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private var busy = false
    private var pending: String? = null
    private var askedPick = false
    private var attached: ByteArray? = null
    private lateinit var preview: LinearLayout
    private fun dp(v: Int) = RunUi.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(RunUi.BG) }
        col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(16)) }
        scroll = ScrollView(this).apply { addView(col) }
        root.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        // Ask box.
        input = EditText(this).apply {
            hint = "Ask about runs, recovery, food… 📎 adds a photo"; setTextColor(Color.WHITE); setHintTextColor(RunUi.MUTED); textSize = 15f
            background = RunUi.rounded(0xFF22302B.toInt(), dp(20).toFloat()); setPadding(dp(14), dp(10), dp(14), dp(10)); maxLines = 5
            imeOptions = EditorInfo.IME_ACTION_SEND; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        // Attached photo (shown above the ask box until sent).
        preview = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(8), dp(14), 0); setBackgroundColor(0xFF0F1513.toInt()); visibility = View.GONE }
        root.addView(preview, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(12)); setBackgroundColor(0xFF0F1513.toInt())
            addView(RunUi.text(this@CoachActivity, "📎", Color.WHITE, 20f, true).apply {
                gravity = Gravity.CENTER; background = RunUi.rounded(0xFF22302B.toInt(), dp(22).toFloat())
                setOnClickListener { try { startActivityForResult(android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).setType("image/*"), 8801) } catch (e: Exception) { Toast.makeText(this@CoachActivity, "No photo app found", Toast.LENGTH_SHORT).show() } }
            }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(8) })
            addView(input, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(RunUi.text(this@CoachActivity, "➤", Color.BLACK, 18f, true).apply {
                gravity = Gravity.CENTER; background = RunUi.rounded(RunUi.ACCENT, dp(22).toFloat()); setOnClickListener { send(input.text.toString()) }
            }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginStart = dp(8) })
        })
        // Keep the ask box above Android's navigation bar and the keyboard (the app draws edge to edge).
        root.setOnApplyWindowInsetsListener { v, ins ->
            val bottom = if (android.os.Build.VERSION.SDK_INT >= 30)
                ins.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.ime()).bottom
            else @Suppress("DEPRECATION") ins.systemWindowInsetBottom
            val top = if (android.os.Build.VERSION.SDK_INT >= 30) ins.getInsets(android.view.WindowInsets.Type.statusBars()).top else 0
            v.setPadding(0, top, 0, bottom); ins
        }
        setContentView(root)
        render()
        if (intent.getBooleanExtra("fuel", false)) fuelDialog()
    }

    private fun render() {
        val ctx = this; col.removeAllViews()
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(RunUi.text(ctx, "🧠 Coach AI", Color.WHITE, 26f, true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            if (CoachAi.chat(ctx).length() > 0) addView(chip("New chat", false) { AlertDialog.Builder(ctx).setMessage("Start a new conversation?").setPositiveButton("New chat") { _, _ -> CoachAi.saveChat(ctx, org.json.JSONArray()); CoachAi.clearImages(ctx); render() }.setNegativeButton("Cancel", null).show() })
        })
        col.addView(RunUi.text(this, "Analyzes and compares your runs — and anything else you pick below. Ask follow-up questions like a conversation.", RunUi.MUTED, 12f).apply { setPadding(0, dp(2), 0, dp(10)) })
        if (!AiFood.hasKey(this)) col.addView(RunUi.text(this, "Add your Anthropic key in Settings → AI food search to use Coach AI.", 0xFFFF8A80.toInt(), 13f, true).apply { setPadding(0, 0, 0, dp(8)) })

        // What it looks at.
        col.addView(RunUi.text(this, "WHAT TO LOOK AT", RunUi.MUTED, 11f, true))
        val sel = CoachAi.scopes(this)
        col.addView(flow(CoachAi.SCOPES.map { s -> chip(s.label, s.key in sel) {
            if (s.key in sel) sel.remove(s.key) else sel.add(s.key); CoachAi.setScopes(ctx, sel)
            if (s.key == "runs" && s.key in sel && CoachAi.runIds(ctx).isEmpty()) pickRuns() else render()
        } }))
        if ("runs" in sel) {
            val ids = CoachAi.runIds(this); val all = CoachAi.runs(this)
            val chosen = if (ids.isEmpty()) all.take(2) else all.filter { CoachAi.id(it) in ids }
            col.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; background = RunUi.rounded(RunUi.CARD, dp(12).toFloat()); setPadding(dp(12), dp(8), dp(12), dp(8))
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) }
                addView(RunUi.text(ctx, "Runs to analyze (${chosen.size})" + if (ids.isEmpty()) " · your last 2" else "", Color.WHITE, 13f, true))
                chosen.take(8).forEach { addView(RunUi.text(ctx, "• " + CoachAi.runLabel(it), RunUi.MUTED, 12f)) }
                if (chosen.size > 8) addView(RunUi.text(ctx, "…and ${chosen.size - 8} more", RunUi.MUTED, 12f))
                addView(RunUi.text(ctx, "Pick runs ›", RunUi.ACCENT, 13f, true).apply { setPadding(0, dp(4), 0, 0) })
                setOnClickListener { pickRuns() }
            })
        }

        // ⛽ Fuel AI (how a day's eating affected a run).
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = RunUi.rounded(0xFF3A2350.toInt(), dp(12).toFloat()); setPadding(dp(12), dp(9), dp(12), dp(9))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) }
            addView(RunUi.text(ctx, "⛽ Fuel AI ▸", Color.WHITE, 14f, true))
            addView(RunUi.text(ctx, "How a day's eating likely affected a run — pick the food day and the run day", RunUi.MUTED, 12f))
            setOnClickListener { fuelDialog() }
        })

        // Conversation (or ideas to start).
        val chat = CoachAi.chat(this)
        if (chat.length() == 0 && pending == null) {
            col.addView(RunUi.text(this, "TRY ASKING", RunUi.MUTED, 11f, true).apply { setPadding(0, dp(14), 0, dp(2)) })
            CoachAi.QUICK.forEach { q -> col.addView(RunUi.text(this, q, Color.WHITE, 13f).apply {
                background = RunUi.rounded(0xFF1E2A26.toInt(), dp(12).toFloat()); setPadding(dp(12), dp(9), dp(12), dp(9))
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) }
                setOnClickListener { if (q.contains("runs I picked")) pickRuns { send(q) } else send(q) } }) }
        } else {
            col.addView(View(this), LinearLayout.LayoutParams(1, dp(8)))
            for (i in 0 until chat.length()) bubble(chat.getJSONObject(i))
        }
        pending?.let { bubble(JSONObject().put("role", "user").put("content", it)) }
        if (busy) col.addView(RunUi.text(this, "🧠 Thinking… (a minute for a big comparison)", RunUi.ACCENT, 13f, true).apply { setPadding(dp(4), dp(10), 0, dp(10)) })
        scroll.post { if (chat.length() > 0 || busy) scroll.fullScroll(View.FOCUS_DOWN) }
    }

    @Deprecated("") override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 8801 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val app = applicationContext
        Thread { val b = AiFood.jpeg(app, uri); runOnUiThread { if (b == null) Toast.makeText(this, "Couldn't read that photo", Toast.LENGTH_SHORT).show() else { attached = b; showPreview() } } }.start()
    }
    private fun showPreview() {
        preview.removeAllViews(); val b = attached
        if (b == null) { preview.visibility = View.GONE; return }
        preview.visibility = View.VISIBLE
        preview.addView(android.widget.ImageView(this).apply { setImageBitmap(android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size)); scaleType = android.widget.ImageView.ScaleType.CENTER_CROP; clipToOutline = true
            background = RunUi.rounded(RunUi.CARD, dp(8).toFloat()) }, LinearLayout.LayoutParams(dp(56), dp(56)))
        preview.addView(RunUi.text(this, "Photo attached — ask about it (or just send)", RunUi.MUTED, 12f).apply { setPadding(dp(10), 0, dp(10), 0) }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        preview.addView(RunUi.text(this, "✕", Color.WHITE, 18f, true).apply { setPadding(dp(10), dp(6), dp(4), dp(6)); setOnClickListener { attached = null; showPreview() } })
    }

    private fun bubble(m: JSONObject) {
        val me = m.optString("role") == "user"
        if (me && m.optString("img").isNotBlank()) {
            val f = java.io.File(m.optString("img"))
            if (f.exists()) col.addView(android.widget.ImageView(this).apply {
                setImageBitmap(android.graphics.BitmapFactory.decodeFile(f.absolutePath, android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 })); adjustViewBounds = true; clipToOutline = true; background = RunUi.rounded(RunUi.CARD, dp(12).toFloat())
            }, LinearLayout.LayoutParams(dp(200), WRAP_CONTENT).apply { topMargin = dp(8); gravity = Gravity.END })
        }
        col.addView(TextView(this).apply {
            setTextColor(if (me) Color.BLACK else Color.WHITE); textSize = 14f; setLineSpacing(0f, 1.15f); setTextIsSelectable(true)
            text = if (me) m.optString("show", m.optString("content")) else md(m.optString("content"))
            background = RunUi.rounded(if (me) RunUi.ACCENT else RunUi.CARD, dp(14).toFloat()); setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(if (me) WRAP_CONTENT else MATCH_PARENT, WRAP_CONTENT).apply {
                topMargin = dp(8); gravity = if (me) Gravity.END else Gravity.START; if (me) marginStart = dp(40) }
        })
    }

    /** Light markdown: headings, **bold**, bullets. */
    private fun md(t: String): CharSequence {
        val html = t.lines().joinToString("<br>") { line ->
            var l = Html.escapeHtml(line)
            l = Regex("\\*\\*(.+?)\\*\\*").replace(l) { "<b>${it.groupValues[1]}</b>" }
            when {
                l.startsWith("### ") || l.startsWith("## ") || l.startsWith("# ") -> "<b><font color='#C6F432'>" + l.trimStart('#', ' ') + "</font></b>"
                l.trimStart().startsWith("- ") || l.trimStart().startsWith("* ") -> "&nbsp;&nbsp;• " + l.trimStart().drop(2)
                else -> l
            }
        }
        return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
    }

    private fun fuelDialog() {
        val ctx = this
        val dates = (0..30).map { java.time.LocalDate.now().minusDays(it.toLong()).toString() }
        fun label(d: String) = java.time.LocalDate.parse(d).format(java.time.format.DateTimeFormatter.ofPattern("EEE M/d", java.util.Locale.US)) +
            (if (d == dates[0]) " (today)" else if (d == dates[1]) " (yesterday)" else "")
        // Defaults: the latest run day, and the food the day before it.
        val lastRun = CoachAi.runs(this).firstOrNull()?.let { ActivityStore.str(it, "date").take(10) }?.takeIf { it in dates } ?: dates[0]
        var runDate = lastRun; var nutrDate = java.time.LocalDate.parse(lastRun).minusDays(1).toString()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val nutrBtn = RunUi.text(this, "", Color.WHITE, 15f, true); val runBtn = RunUi.text(this, "", Color.WHITE, 15f, true)
        fun refresh() { nutrBtn.text = "🍽️ Food day: ${label(nutrDate)}  ▾"; runBtn.text = "🏃 Run day: ${label(runDate)}  ▾" }
        refresh()
        listOf(nutrBtn to true, runBtn to false).forEach { (b, isNutr) ->
            b.setPadding(0, dp(10), 0, dp(10))
            b.setOnClickListener { AlertDialog.Builder(ctx).setItems(dates.map { label(it) }.toTypedArray()) { _, i -> if (isNutr) nutrDate = dates[i] else runDate = dates[i]; refresh() }.show() }
            box.addView(b)
        }
        val ex = EditText(this).apply { hint = "Anything else? (sleep, heat, how the run felt…)"; setTextColor(Color.WHITE); setHintTextColor(RunUi.MUTED) }
        box.addView(ex)
        AlertDialog.Builder(this).setTitle("⛽ Fuel AI").setView(box)
            .setPositiveButton("Analyze") { _, _ ->
                val q = "⛽ Fuel check: food on ${label(nutrDate)} → run on ${label(runDate)}" + ex.text.toString().trim().let { if (it.isNotEmpty()) " ($it)" else "" }
                send(q, CoachAi.fuelBlock(ctx, nutrDate, runDate, ex.text.toString().trim()))
            }.setNegativeButton("Cancel", null).show()
    }

    private fun send(q0: String, hidden: String? = null) {
        val photo = attached
        val q = q0.trim().ifEmpty { if (photo != null) "What does this show? Analyze it alongside my data." else "" }; if (q.isEmpty() || busy) return
        if (!AiFood.hasKey(this)) { Toast.makeText(this, "Add your Anthropic key in Settings → AI food search first.", Toast.LENGTH_LONG).show(); return }
        if (hidden == null && Regex("\\bcompar", RegexOption.IGNORE_CASE).containsMatchIn(q) && Regex("\\bruns?\\b", RegexOption.IGNORE_CASE).containsMatchIn(q) && !askedPick) {
            askedPick = true; input.setText(""); pickRuns { send(q, null) }; return
        }
        askedPick = false
        busy = true; pending = q; input.setText(""); attached = null; showPreview()
        render()
        val app = applicationContext
        Thread {
            val img = photo?.let { b -> try { java.io.File(CoachAi.imageDir(app), "${System.currentTimeMillis()}.jpg").apply { writeBytes(b) }.absolutePath } catch (e: Exception) { null } }
            val err = try { CoachAi.ask(app, q, hidden, img); null } catch (e: Exception) { e.message ?: "Something went wrong" }
            runOnUiThread {
                busy = false; pending = null; if (isFinishing) return@runOnUiThread
                render()
                if (err != null) { Toast.makeText(this, "Coach AI: $err", Toast.LENGTH_LONG).show(); input.setText(q); if (photo != null) { attached = photo; showPreview() } }
            }
        }.start()
    }

    /** [after] runs once runs are chosen (used by "Compare the runs I picked"). */
    private fun pickRuns(after: (() -> Unit)? = null) {
        val all = CoachAi.runs(this).take(80)
        if (all.isEmpty()) { Toast.makeText(this, "No runs yet.", Toast.LENGTH_SHORT).show(); render(); return }
        val ids = CoachAi.runIds(this).toMutableSet()
        val checked = BooleanArray(all.size) { CoachAi.id(all[it]) in ids }
        AlertDialog.Builder(this).setTitle(if (after != null) "Which runs should I compare?" else "Pick runs to analyze")
            .setMultiChoiceItems(all.map { CoachAi.runLabel(it) }.toTypedArray(), checked) { _, i, on -> checked[i] = on }
            .setPositiveButton(if (after != null) "Compare these" else "Use these") { _, _ ->
                val pick = all.filterIndexed { i, _ -> checked[i] }.map { CoachAi.id(it) }.take(12)
                if (after != null && pick.size < 2) { Toast.makeText(this, "Pick at least 2 runs to compare.", Toast.LENGTH_SHORT).show(); pickRuns(after); return@setPositiveButton }
                CoachAi.setRunIds(this, pick); render()
                if (after != null) { val sel = CoachAi.scopes(this); sel.add("runs"); CoachAi.setScopes(this, sel); after() } }
            .setNeutralButton("Last 2") { _, _ -> CoachAi.setRunIds(this, emptyList()); render()
                if (after != null) { val sel = CoachAi.scopes(this); sel.add("runs"); CoachAi.setScopes(this, sel); after() } }
            .setNegativeButton("Cancel") { _, _ -> askedPick = false; render() }.setOnCancelListener { askedPick = false }.show()
    }

    private fun chip(t: String, on: Boolean, click: () -> Unit) = TextView(this).apply {
        text = t; textSize = 12.5f; typeface = Typeface.DEFAULT_BOLD; setTextColor(if (on) Color.BLACK else Color.WHITE)
        background = RunUi.rounded(if (on) RunUi.ACCENT else 0xFF26332E.toInt(), dp(16).toFloat()); setPadding(dp(11), dp(6), dp(11), dp(6))
        setOnClickListener { click() }
    }

    /** Chips that wrap onto new lines. */
    private fun flow(views: List<View>): LinearLayout {
        val out = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(4), 0, 0) }
        val w = resources.displayMetrics.widthPixels - dp(32); var row: LinearLayout? = null; var used = 0
        views.forEach { v ->
            v.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED); val vw = v.measuredWidth + dp(6)
            if (row == null || used + vw > w) { row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }; out.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) }); used = 0 }
            row!!.addView(v, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(6) }); used += vw
        }
        return out
    }
}
