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
    fun open(onResult: (String) -> Unit) {
        call("bibleStudy.nStart()") { r ->
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
    private val verseViews = ArrayList<Pair<TextView, String>>()   // each verse's view and its spoken text
    private val verseBase = ArrayList<SpannableString>()
    private fun drawRead(j: JSONObject) {
        val done = j.optJSONObject("done") ?: JSONObject()
        body.addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, d(6), 0, 0)
            addView(RunUi.button(act, if (reading) "⏹ Stop" else "🎧 Read aloud", 0xFF7C3AED.toInt(), Color.WHITE) { if (reading) stopReading() else readAloud(); sig = ""; state?.let { draw(it) } },
                LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = d(8) })
            addView(RunUi.button(act, if (done.optBoolean("audio")) "✓ Listened" else "✓ I listened", RunUi.ACCENT) { stopReading(); call("bibleStudy.nListenedApp()") { sig = ""; refresh() } },
                LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)) })
        body.addView(RunUi.text(act, "Read in the NLT ↗", 0xFFA78BFA.toInt(), 13f, true).apply { setPadding(0, d(10), 0, 0); setOnClickListener { link(j.optString("nlt")) } })
        body.addView(RunUi.text(act, "Already listened to today's audiobook? Tap ✓ I listened — no need to read it again; it moves on to the study. Text: World English Bible. Purple is today's focus; gold is the key verse.", RunUi.MUTED, 12f).apply { setPadding(0, d(8), 0, 0) })
        verseViews.clear(); verseBase.clear()
        val lists = j.optJSONArray("verses")
        if (lists == null) { body.addView(card().apply { p("⏳ Loading today's passages…") }); return }
        for (i in 0 until lists.length()) {
            val seg = lists.getJSONObject(i); val vs = seg.optJSONArray("verses") ?: JSONArray()
            val c = card(); c.h(seg.optString("ref"))
            if (vs.length() == 0) { c.addView(RunUi.text(act, "Couldn't load this passage here — tap to read it in the NLT ↗", 0xFFA78BFA.toInt(), 13f).apply { setOnClickListener { link(seg.optString("nlt")) } }) }
            var lastC = -1
            for (k in 0 until vs.length()) {
                val v = vs.getJSONObject(k)
                if (v.optInt("c") != lastC) { lastC = v.optInt("c"); c.addView(RunUi.text(act, "Chapter $lastC", 0xFFA78BFA.toInt(), 12f, true).apply { setPadding(0, d(8), 0, d(2)) }) }
                val num = v.optInt("v").toString() + "  "; val txt = v.optString("t")
                val sp = SpannableString(num + txt)
                sp.setSpan(ForegroundColorSpan(0xFFA78BFA.toInt()), 0, num.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                when (v.optInt("k")) { 1 -> sp.setSpan(BackgroundColorSpan(0x66B45309), num.length, sp.length, 0); 2 -> sp.setSpan(BackgroundColorSpan(0x335B21B6), num.length, sp.length, 0) }
                val tv = TextView(act).apply { setTextColor(Color.WHITE); textSize = 17f; typeface = Typeface.SERIF; setLineSpacing(0f, 1.3f); setPadding(d(4), d(3), d(4), d(3)); text = sp }
                verseViews.add(tv to txt); verseBase.add(sp)
                tv.tag = num.length
                c.addView(tv)
            }
            body.addView(c)
        }
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
        if (current != i && current in verseViews.indices) { verseViews[current].first.text = verseBase[current]; verseViews[current].first.background = null }
        current = i
        val (tv, _) = verseViews[i]; val off = tv.tag as? Int ?: 0
        val sp = SpannableString(verseBase[i])
        if (ws >= 0 && we > ws && off + we <= sp.length) {
            sp.setSpan(BackgroundColorSpan(0xFF7C3AED.toInt()), off + ws, off + we, 0)
        }
        tv.text = sp; tv.background = RunUi.rounded(0xFF173A4A.toInt(), d(6).toFloat())
        val y = (tv.parent as? View)?.let { it.top + tv.top } ?: tv.top
        if (ws < 0) scroll.smoothScrollTo(0, (y - scroll.height / 3).coerceAtLeast(0))
    }
    fun stopReading() {
        if (!reading && current < 0) return
        reading = false; try { tts?.stop() } catch (e: Exception) { }
        if (current in verseViews.indices) { verseViews[current].first.text = verseBase[current]; verseViews[current].first.background = null }
        current = -1
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
