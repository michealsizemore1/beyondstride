package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.WebView
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.Locale

/**
 * 📖 The Daily Bible Study as a BeyondStride screen (Life tab).
 * The Command Center still does the thinking — the same study prompt, the same saved session (bstudy_<date>), the same
 * Bible checks ticked — through window.bibleStudy.n*; this draws the five steps natively and reads the passage aloud
 * with the phone's own voice, shading the verse and the word being spoken.
 */
class BibleStudyPage(private val act: Activity, private val web: WebView, private val onClose: () -> Unit) {
    val view = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(RunUi.BG) }
    private val ui = Handler(Looper.getMainLooper())
    private val refText = RunUi.text(act, "", RunUi.MUTED, 12f).apply { gravity = Gravity.END }
    private val chips = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
    private val body = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(d(14), d(4), d(14), d(24)) }
    private val scroll = ScrollView(act).apply { addView(body) }
    private val back = RunUi.button(act, "‹ Back", 0xFF374151.toInt(), Color.WHITE) { go(step - 1) }
    private val next = RunUi.button(act, "Next ›", 0xFF7C3AED.toInt(), Color.WHITE) { go(step + 1) }
    private var step = 0
    private var sig = ""
    private var state: JSONObject? = null
    private var visible = false
    private val STEPS = listOf("🙏 Open", "📖 Read", "🔍 Study", "🎶 Worship", "🙏 Close")
    private fun d(v: Int) = RunUi.dp(act, v)

    init {
        view.addView(LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(d(14), d(8), d(14), d(6))
            addView(RunUi.text(act, "✕", Color.WHITE, 20f, true).apply { setPadding(d(4), 0, d(14), 0); setOnClickListener { close() } })
            addView(RunUi.text(act, "📖 Bible Study", Color.WHITE, 17f, true))
            addView(refText, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = d(10) })
        })
        view.addView(HorizontalScrollView(act).apply { isHorizontalScrollBarEnabled = false; setPadding(d(10), 0, d(10), d(6)); addView(chips) })
        view.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        view.addView(LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(d(14), d(8), d(14), d(10))
            addView(back, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = d(8) })
            addView(next, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.4f))
        })
    }

    // ── Bridge ──
    private fun call(js: String, cb: ((String) -> Unit)? = null) = web.evaluateJavascript("window.bibleStudy&&bibleStudy.nStart?$js:null") { r -> cb?.invoke(r ?: "") }
    private fun unwrap(r: String): String = try { JSONTokener(r).nextValue() as? String ?: "" } catch (e: Exception) { "" }

    /** Start (or continue) today's study. [onResult] gets "ok", "nokey", "noreading" or "" (Command Center not ready). */
    fun open(onResult: (String) -> Unit) = open(null, onResult)
    /** A past day's saved study (from the 🗂️ Journal); null = today. A past study never ticks today's boxes. */
    fun open(date: String?, onResult: (String) -> Unit) {
        call("bibleStudy.nStart(${if (date == null) "" else org.json.JSONObject.quote(date)})") { r ->
            val res = unwrap(r)
            if (res == "ok") { visible = true; sig = ""; refresh() }
            onResult(res)
        }
    }
    fun hide() { visible = false; stopReading(); ui.removeCallbacksAndMessages(null) }
    private fun close() { hide(); onClose() }

    private val poll = Runnable { refresh() }
    private fun refresh() {
        if (!visible) return
        call("bibleStudy.nState()") { r ->
            val j = try { JSONObject(unwrap(r)) } catch (e: Exception) { null } ?: return@call
            state = j; draw(j)
            val waiting = j.optBoolean("preparing") || j.optBoolean("loadingVerses") ||
                (j.optInt("step") == 4 && j.optString("closing").isEmpty() && j.optJSONObject("study") != null) ||
                j.optJSONObject("xref")?.let { x -> x.keys().asSequence().any { x.optString(it) == "⏳" } } == true ||
                (j.optInt("step") == 1 && j.isNull("verses") && j.optJSONObject("study") != null)
            ui.removeCallbacks(poll); if (waiting) ui.postDelayed(poll, 1000)
        }
    }
    private fun go(i: Int) {
        stopReading()
        call("bibleStudy.nStep($i)") { _ ->
            if (i > 4) { close(); return@call }
            sig = ""; refresh(); scroll.scrollTo(0, 0)
        }
    }

    // ── Drawing ──
    private fun draw(j: JSONObject) {
        step = j.optInt("step")
        refText.text = j.optString("label").substringAfter("· ") + "\n" + j.optString("ref")
        val done = j.optJSONObject("done") ?: JSONObject()
        chips.removeAllViews()
        STEPS.forEachIndexed { i, s ->
            val on = i == step; val ok = done.optBoolean(i.toString())
            chips.addView(RunUi.text(act, (if (ok && !on) "✓ " else "") + s, if (on) Color.BLACK else if (ok) 0xFF22C55E.toInt() else Color.WHITE, 12f, true).apply {
                setPadding(d(12), d(6), d(12), d(6)); background = RunUi.rounded(if (on) RunUi.ACCENT else RunUi.CARD, d(16).toFloat())
                setOnClickListener { go(i) }
            }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = d(6) })
        }
        back.visibility = if (step > 0) View.VISIBLE else View.INVISIBLE
        next.text = if (step == 4) (if (j.optBoolean("finished")) "Done" else "✓ Finish study") else "Next ›"
        // Redraw the body only when what it shows changed (answers you're typing don't count).
        val st = j.optJSONObject("study")
        val newSig = listOf(step, st?.toString()?.length ?: 0, j.optBoolean("preparing"), j.optString("error"), j.isNull("verses"), j.optString("closing").length,
            j.optJSONObject("xref")?.toString(), done.optBoolean("audio")).joinToString("|")
        if (newSig == sig) return; sig = newSig
        body.removeAllViews()
        if (st == null) {
            val err = j.optString("error")
            body.addView(card().apply {
                addView(RunUi.text(act, if (err.isNotEmpty()) "Couldn't prepare the study: $err" else "⏳ Preparing today's study of ${j.optString("ref")}…", Color.WHITE, 15f))
                if (err.isNotEmpty()) addView(RunUi.button(act, "Try again", 0xFF7C3AED.toInt(), Color.WHITE) { call("bibleStudy.nRetry()") { sig = ""; refresh() } })
            })
            return
        }
        when (step) {
            0 -> {
                body.addView(card(true).apply { h("Opening prayer"); p(st.optString("open_prayer")) })
                body.addView(RunUi.text(act, "Today's theme: " + st.optString("theme") + (st.optString("focus_label").takeIf { it.isNotEmpty() }?.let { " · Focus: $it" } ?: ""), RunUi.MUTED, 13f).apply { setPadding(0, d(10), 0, 0) })
                st.optString("overview").takeIf { it.isNotEmpty() }?.let { o -> body.addView(card().apply { h("Today's reading at a glance"); p(o) }) }
            }
            1 -> drawRead(j)
            2 -> drawStudy(j, st)
            3 -> {
                val song = st.optJSONObject("song") ?: JSONObject()
                val q = Uri.encode(song.optString("title") + " " + song.optString("artist"))
                body.addView(card(true).apply {
                    h("Worship response")
                    addView(RunUi.text(act, song.optString("title") + (song.optString("artist").takeIf { it.isNotEmpty() }?.let { " — $it" } ?: ""), Color.WHITE, 16f, true))
                    addView(RunUi.text(act, song.optString("why"), RunUi.MUTED, 12f))
                    addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, d(10), 0, 0)
                        addView(RunUi.button(act, "▶ YouTube", 0xFFEF4444.toInt(), Color.WHITE) { link("https://www.youtube.com/results?search_query=$q") }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = d(8) })
                        addView(RunUi.button(act, "Spotify", 0xFF22C55E.toInt()) { link("https://open.spotify.com/search/$q") }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)) })
                })
                body.addView(RunUi.text(act, "Listen, sing, or sit quietly with the passage for a few minutes.", RunUi.MUTED, 12f).apply { setPadding(0, d(10), 0, 0) })
            }
            else -> {
                val c = j.optString("closing")
                body.addView(card(true).apply { h("Closing prayer"); p(if (c.isEmpty()) "⏳ Writing a closing prayer from what you wrote…" else c) })
                j.optString("memory").takeIf { it.isNotEmpty() }?.let { m -> body.addView(card().apply { h("This week's memory verse"); p(m) }) }
                body.addView(RunUi.text(act, "✓ Finish study saves it, ticks Daily Bread, AI Prayer and Worship, and fills today's Daily Bread verse.", RunUi.DIM, 11f).apply { setPadding(0, d(10), 0, 0) })
            }
        }
    }

    private fun card(pray: Boolean = false) = LinearLayout(act).apply {
        orientation = LinearLayout.VERTICAL; setPadding(d(14), d(12), d(14), d(12))
        background = RunUi.rounded(if (pray) 0xFF2A2140.toInt() else RunUi.CARD, d(14).toFloat())
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = d(10) }
    }
    private fun LinearLayout.h(t: String) = addView(RunUi.text(act, t.uppercase(Locale.US), 0xFFA78BFA.toInt(), 11f, true).apply { setPadding(0, 0, 0, d(6)) })
    private fun LinearLayout.p(t: String) = addView(RunUi.text(act, t, Color.WHITE, 15f).apply { setLineSpacing(0f, 1.25f) })
    private fun link(u: String) { try { act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) } catch (e: Exception) { } }

    // ── 📖 Read: the passage, read aloud by the phone ──
    private val verseViews = ArrayList<Pair<View, String>>()   // each verse's text block and its spoken text
    private fun drawRead(j: JSONObject) {
        val done = j.optJSONObject("done") ?: JSONObject()
        body.addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, d(6), 0, 0)
            addView(RunUi.button(act, if (reading) "⏹ Stop" else "🎧 Read aloud", 0xFF7C3AED.toInt(), Color.WHITE) { if (reading) stopReading() else readAloud(); sig = ""; state?.let { draw(it) } },
                LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = d(8) })
            addView(RunUi.button(act, if (done.optBoolean("audio")) "✓ Listened" else "✓ I listened", RunUi.ACCENT) { stopReading(); call("bibleStudy.nListenedApp()") { sig = ""; refresh() } },
                LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)) })
        body.addView(RunUi.text(act, "Read in the NLT ↗", 0xFFA78BFA.toInt(), 13f, true).apply { setPadding(0, d(10), 0, 0); setOnClickListener { link(j.optString("nlt")) } })
        body.addView(RunUi.text(act, "Already listened to today's audiobook? Tap ✓ I listened — no need to read it again; it moves on to the study. Text: World English Bible. Purple is today's focus; gold is the key verse.", RunUi.MUTED, 12f).apply { setPadding(0, d(8), 0, 0) })
        verseViews.clear(); verseKeys.clear(); verseNum.clear(); verseNo.clear(); verseStart.clear(); verseBlock.clear(); blocks.clear()
        val lists = j.optJSONArray("verses")
        if (lists == null) { body.addView(card().apply { p("⏳ Loading today's passages…") }); return }
        hl = j.optJSONObject("hl") ?: JSONObject()
        // 🔍 Find words in today's reading.
        val box = EditText(act).apply { hint = "🔍 Search today's reading"; setText(query); setSingleLine(); textSize = 15f; setTextColor(Color.WHITE); setHintTextColor(RunUi.DIM)
            background = RunUi.rounded(0xFF26302C.toInt(), d(12).toFloat()); setPadding(d(12), d(10), d(12), d(10)) }
        val bar = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(d(2), d(6), 0, 0)
            addView(findInfo, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(RunUi.text(act, "‹", RunUi.ACCENT, 24f, true).apply { setPadding(d(14), 0, d(14), 0); setOnClickListener { jump(-1) } })
            addView(RunUi.text(act, "›", RunUi.ACCENT, 24f, true).apply { setPadding(d(14), 0, d(10), 0); setOnClickListener { jump(1) } }) }
        val find = Runnable { query = box.text.toString().trim(); hit = -1; applySearch(); if (hits.isNotEmpty()) jump(1) }
        box.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { ui.removeCallbacks(find); ui.postDelayed(find, 350) } })
        (findInfo.parent as? ViewGroup)?.removeView(findInfo)
        body.addView(box, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = d(10) })
        body.addView(bar)
        body.addView(RunUi.text(act, "🖍 To highlight: press and hold the text, drag to select, then pick a color. Select highlighted text and tap Remove highlight to clear it.", RunUi.DIM, 11f).apply { setPadding(d(2), d(2), 0, d(4)) })
        for (i in 0 until lists.length()) {
            val seg = lists.getJSONObject(i); val vs = seg.optJSONArray("verses") ?: JSONArray()
            val c = card(); c.h(seg.optString("ref"))
            if (vs.length() == 0) { c.addView(RunUi.text(act, "Couldn't load this passage here — tap to read it in the NLT ↗", 0xFFA78BFA.toInt(), 13f).apply { setOnClickListener { link(seg.optString("nlt")) } }) }
            // One block of text per chapter, so a selection can run across several verses.
            var lastC = -1; var blockIdx = ArrayList<Int>()
            fun closeBlock() { if (blockIdx.isEmpty()) return
                val tv = TextView(act).apply { setTextColor(Color.WHITE); textSize = 17f; typeface = Typeface.SERIF; setLineSpacing(0f, 1.3f); setPadding(d(4), d(3), d(4), d(3))
                    setTextIsSelectable(true); highlightColor = 0x667C3AED }
                val bi = blocks.size; blocks.add(tv to ArrayList(blockIdx)); blockIdx.forEach { verseBlock[it] = bi; verseViews[it] = tv to verseViews[it].second }
                tv.customSelectionActionModeCallback = hlMenu(bi)
                c.addView(tv); blockIdx = ArrayList() }
            for (k in 0 until vs.length()) {
                val v = vs.getJSONObject(k)
                if (v.optInt("c") != lastC) { closeBlock(); lastC = v.optInt("c"); c.addView(RunUi.text(act, "Chapter $lastC", 0xFFA78BFA.toInt(), 12f, true).apply { setPadding(0, d(8), 0, d(2)) }) }
                blockIdx.add(verseViews.size)
                verseViews.add(body to v.optString("t")); verseKeys.add(v.optString("b") + " " + v.optInt("c") + ":" + v.optInt("v")); verseNum.add(v.optInt("k"))
                verseNo.add(v.optInt("v")); verseStart.add(0); verseBlock.add(-1)
            }
            closeBlock()
            body.addView(c)
        }
        applySearch()
    }

    // ── 🔍 Search and 🖍 highlights in the Read step ──
    private val verseKeys = ArrayList<String>()
    private val verseNum = ArrayList<Int>()        // 1 = key verse, 2 = today's focus
    private var hl = JSONObject()
    private var query = ""
    private val hits = ArrayList<Int>()            // verse index of each match
    private var hit = -1
    private val findInfo = RunUi.text(act, "", RunUi.MUTED, 12f)
    private val HL = listOf("Yellow" to 0xAAEAB308.toInt(), "Green" to 0xAA16A34A.toInt(), "Blue" to 0xAA2563EB.toInt(), "Pink" to 0xAADB2777.toInt())

    private val verseNo = ArrayList<Int>()
    private val verseStart = ArrayList<Int>()      // where each verse's words start inside its block
    private val verseBlock = ArrayList<Int>()
    private val blocks = ArrayList<Pair<TextView, List<Int>>>()
    private var readWs = -1; private var readWe = -1

    /** Draw one chapter block: verse numbers, focus / key shading, your highlights, search matches, the verse being read. */
    private fun render(bi: Int) {
        if (bi !in blocks.indices) return
        val (tv, idx) = blocks[bi]; val sb = android.text.SpannableStringBuilder(); val q = query.lowercase(Locale.US)
        idx.forEachIndexed { n, i ->
            if (n > 0) sb.append("\n")
            val ns = sb.length; sb.append(verseNo[i].toString() + "  ")
            sb.setSpan(ForegroundColorSpan(0xFFA78BFA.toInt()), ns, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val o = sb.length; val txt = verseViews[i].second; verseStart[i] = o; sb.append(txt)
            when (verseNum[i]) { 1 -> sb.setSpan(BackgroundColorSpan(0x66B45309), o, sb.length, 0); 2 -> sb.setSpan(BackgroundColorSpan(0x335B21B6), o, sb.length, 0) }
            val a = hl.optJSONArray(verseKeys[i]) ?: JSONArray()
            for (k in 0 until a.length()) { val r = a.optJSONArray(k) ?: continue
                val f = r.optInt(0).coerceIn(0, txt.length); val t = r.optInt(1).coerceIn(f, txt.length)
                if (t > f) sb.setSpan(BackgroundColorSpan(r.optInt(2)), o + f, o + t, 0) }
            if (q.length >= 2) { val low = txt.lowercase(Locale.US); var at = low.indexOf(q)
                while (at >= 0) { sb.setSpan(BackgroundColorSpan(0xFFFACC15.toInt()), o + at, o + at + q.length, 0); sb.setSpan(ForegroundColorSpan(Color.BLACK), o + at, o + at + q.length, 0); at = low.indexOf(q, at + q.length) } }
            if (reading && i == current) { sb.setSpan(BackgroundColorSpan(0xFF173A4A.toInt()), o, sb.length, 0)
                if (readWs >= 0 && readWe > readWs && readWe <= txt.length) sb.setSpan(BackgroundColorSpan(0xFF7C3AED.toInt()), o + readWs, o + readWe, 0) }
        }
        tv.text = sb
    }
    private fun show(i: Int) { if (i in verseBlock.indices) render(verseBlock[i]) }
    private fun applySearch() {
        hits.clear()
        val q = query.lowercase(Locale.US)
        if (q.length >= 2) for (i in verseViews.indices) { val low = verseViews[i].second.lowercase(Locale.US); var at = low.indexOf(q); while (at >= 0) { hits.add(i); at = low.indexOf(q, at + q.length) } }
        blocks.indices.forEach { render(it) }
        findInfo.text = when { q.length < 2 -> ""; hits.isEmpty() -> "No matches"; else -> "${hits.size} match${if (hits.size == 1) "" else "es"} in ${hits.distinct().size} verse${if (hits.distinct().size == 1) "" else "s"}" }
    }
    /** Scroll so verse [i] sits about a third of the way down. */
    private fun scrollTo(i: Int) {
        val bi = verseBlock.getOrNull(i) ?: return; val tv = blocks.getOrNull(bi)?.first ?: return
        tv.post { val lay = tv.layout ?: return@post
            val y = ((tv.parent as? View)?.top ?: 0) + tv.top + lay.getLineTop(lay.getLineForOffset(verseStart[i]))
            scroll.smoothScrollTo(0, (y - scroll.height / 3).coerceAtLeast(0)) }
    }
    private fun jump(dir: Int) {
        if (hits.isEmpty()) return
        hit = ((if (hit < 0 && dir < 0) 0 else hit) + dir + hits.size) % hits.size
        findInfo.text = "${hit + 1} of ${hits.size} · ${verseKeys[hits[hit]]}"
        scrollTo(hits[hit])
    }
    /** The menu when you select text (it can cover several verses): highlight colors, or remove. */
    private fun hlMenu(bi: Int) = object : android.view.ActionMode.Callback {
        override fun onCreateActionMode(mode: android.view.ActionMode, menu: android.view.Menu): Boolean {
            HL.forEachIndexed { n, (name, _) -> menu.add(0, 9100 + n, n, "🖍 $name") }
            menu.add(0, 9199, HL.size, "Remove highlight"); return true }
        override fun onPrepareActionMode(mode: android.view.ActionMode, menu: android.view.Menu) = false
        override fun onActionItemClicked(mode: android.view.ActionMode, item: android.view.MenuItem): Boolean {
            val id = item.itemId; if (id < 9100 || id > 9199) return false
            val tv = blocks[bi].first; val s0 = minOf(tv.selectionStart, tv.selectionEnd); val s1 = maxOf(tv.selectionStart, tv.selectionEnd)
            mode.finish()
            blocks[bi].second.forEach { i -> val o = verseStart[i]; val len = verseViews[i].second.length
                val f = (s0 - o).coerceIn(0, len); val t = (s1 - o).coerceIn(0, len)
                if (t > f) setHighlight(i, f, t, if (id == 9199) null else HL[id - 9100].second, draw = false) }
            render(bi); return true }
        override fun onDestroyActionMode(mode: android.view.ActionMode) {}
    }
    /** Paint [f, t) with [color] (or clear it), keep the rest, save. */
    private fun setHighlight(i: Int, f: Int, t: Int, color: Int?, draw: Boolean = true) {
        val key = verseKeys[i]; val old = hl.optJSONArray(key) ?: JSONArray(); val out = JSONArray()
        for (k in 0 until old.length()) { val r = old.optJSONArray(k) ?: continue; val a = r.optInt(0); val b = r.optInt(1); val c = r.optInt(2)
            if (b <= f || a >= t) { out.put(r); continue }
            if (a < f) out.put(JSONArray().put(a).put(f).put(c))
            if (b > t) out.put(JSONArray().put(t).put(b).put(c)) }
        if (color != null) out.put(JSONArray().put(f).put(t).put(color))
        if (out.length() == 0) hl.remove(key) else hl.put(key, out)
        if (draw) show(i)
        call("bibleStudy.nHl(${JSONObject.quote(key)},${JSONObject.quote(out.toString())})")
    }

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var reading = false
    private var current = -1
    private fun readAloud() {
        if (verseViews.isEmpty()) return
        reading = true
        val t = tts
        if (t == null || !ttsReady) {
            tts = TextToSpeech(act) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) ui.post { tts?.language = Locale.US; tts?.setSpeechRate(0.95f); speakFrom(0) } else ui.post { reading = false }
            }.also { it.setOnUtteranceProgressListener(listener) }
        } else speakFrom(0)
    }
    private fun speakFrom(start: Int) {
        val t = tts ?: return
        t.stop()
        for (i in start until verseViews.size) t.speak(verseViews[i].second, if (i == start) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, Bundle(), "v$i")
    }
    private val listener = object : UtteranceProgressListener() {
        override fun onStart(id: String?) { val i = id?.removePrefix("v")?.toIntOrNull() ?: return; ui.post { mark(i, -1, -1) } }
        override fun onDone(id: String?) {
            val i = id?.removePrefix("v")?.toIntOrNull() ?: return
            if (i == verseViews.size - 1) ui.post { stopReading(); call("bibleStudy.nListened()") { sig = ""; refresh() } }
        }
        @Deprecated("Deprecated in Java") override fun onError(id: String?) {}
        override fun onRangeStart(id: String?, start: Int, end: Int, frame: Int) { val i = id?.removePrefix("v")?.toIntOrNull() ?: return; ui.post { mark(i, start, end) } }
    }
    /** Shade the verse being read and the word being spoken; keep the verse in view. */
    private fun mark(i: Int, ws: Int, we: Int) {
        if (!reading || i !in verseViews.indices) return
        val prev = current; current = i; readWs = ws; readWe = we
        if (prev in verseBlock.indices && verseBlock[prev] != verseBlock[i]) show(prev)
        show(i)
        if (ws < 0) scrollTo(i)
    }
    fun stopReading() {
        if (!reading && current < 0) return
        reading = false; try { tts?.stop() } catch (e: Exception) { }
        val c0 = current; current = -1; readWs = -1; readWe = -1; if (c0 in verseViews.indices) show(c0)
    }
    fun destroy() { hide(); try { tts?.shutdown() } catch (e: Exception) { }; tts = null }

    // ── 🔍 Study: context, key word, cross-references, SOAP ──
    private fun drawStudy(j: JSONObject, st: JSONObject) {
        val a = j.optJSONObject("answers") ?: JSONObject()
        body.addView(card().apply { h("Context"); p(st.optString("context")) })
        st.optJSONObject("word")?.let { w -> body.addView(card().apply { h("Key word")
            addView(RunUi.text(act, w.optString("term") + (w.optString("original").takeIf { it.isNotEmpty() }?.let { " — $it" } ?: ""), Color.WHITE, 15f, true)); p(w.optString("meaning")) }) }
        val xr = st.optJSONArray("crossrefs"); val xt = j.optJSONObject("xref") ?: JSONObject()
        if (xr != null && xr.length() > 0) body.addView(card().apply { h("Cross-references")
            for (i in 0 until xr.length()) {
                addView(RunUi.text(act, "📖 " + xr.optString(i), 0xFFA78BFA.toInt(), 14f, true).apply { setPadding(0, d(5), 0, d(3)); setOnClickListener { call("bibleStudy.nXref($i)") { sig = ""; refresh() } } })
                xt.optString(i.toString()).takeIf { it.isNotEmpty() }?.let { addView(RunUi.text(act, it, RunUi.MUTED, 14f).apply { typeface = Typeface.SERIF }) }
            } })
        fun q(title: String?, question: String, key: String, value: String, lines: Int) {
            title?.let { body.addView(RunUi.text(act, it, RunUi.ACCENT, 15f, true).apply { setPadding(0, d(16), 0, d(2)) }) }
            body.addView(RunUi.text(act, question, Color.WHITE, 14f).apply { setPadding(0, d(6), 0, d(4)) })
            body.addView(EditText(act).apply {
                setText(value); setTextColor(Color.WHITE); textSize = 15f; minLines = lines; gravity = Gravity.TOP
                background = RunUi.rounded(0xFF26302C.toInt(), d(10).toFloat()); setPadding(d(10), d(8), d(10), d(8))
                val commit = Runnable { call("bibleStudy.nAnswer(${JSONObject.quote(key)},${JSONObject.quote(text.toString())})") }
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun afterTextChanged(s: Editable?) { ui.removeCallbacks(commit); ui.postDelayed(commit, 700) }
                })
                setOnFocusChangeListener { _, has -> if (!has) { ui.removeCallbacks(commit); commit.run() } }
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        val o = a.optJSONArray("o") ?: JSONArray(); val ap = a.optJSONArray("a") ?: JSONArray()
        fun at(arr: JSONArray, i: Int) = if (i >= arr.length() || arr.isNull(i)) "" else arr.optString(i)
        q("S — Scripture", "Which verse stood out to you? Write it out.", "s", a.optString("s"), 2)
        val obs = st.optJSONArray("observe") ?: JSONArray()
        for (i in 0 until obs.length()) q(if (i == 0) "O — Observation" else null, obs.optString(i), "o$i", at(o, i), 2)
        val apl = st.optJSONArray("apply") ?: JSONArray()
        for (i in 0 until apl.length()) q(if (i == 0) "A — Application" else null, apl.optString(i), "a$i", at(ap, i), 2)
        q("P — Prayer", "Your response to God in your own words.", "p", a.optString("p"), 3)
    }
}
