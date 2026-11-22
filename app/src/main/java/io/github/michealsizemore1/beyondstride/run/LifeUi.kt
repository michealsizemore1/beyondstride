package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import org.json.JSONTokener

/** Small pieces shared by the Life tab's Command Center look-alike sections (Bible, Library, Meditate, Habits). */
object LifeUi {
    val CYCLE = listOf("", "green", "black", "purple", "red")
    val ui = Handler(Looper.getMainLooper())

    /** A Command Center check box: empty, green ✓, black N/A, purple partly, red not done. */
    fun paintBox(b: TextView, st: String) {
        val d = RunUi.dp(b.context, 1)
        val c = when (st) { "green" -> 0xFF22C55E; "purple" -> 0xFFA855F7; "red" -> 0xFFEF4444; "black" -> 0xFF0B0F0D; else -> 0 }.toInt()
        b.background = GradientDrawable().apply { cornerRadius = 8f * d; setColor(c); setStroke(2 * d, if (st.isEmpty()) RunUi.MUTED else if (st == "black") 0xFF6B7280.toInt() else c) }
        b.text = if (st.isEmpty()) "" else "✓"; b.setTextColor(Color.WHITE)
    }
    fun box(act: Activity) = TextView(act).apply { gravity = Gravity.CENTER; textSize = 14f; setTypeface(typeface, android.graphics.Typeface.BOLD) }

    /** Parse an evaluateJavascript result that is a JSON string holding a JSON object. */
    fun obj(r: String?): JSONObject? = try {
        when (val v = JSONTokener(r ?: "").nextValue()) { is String -> JSONObject(v); is JSONObject -> v; else -> null }
    } catch (e: Exception) { null }
    fun str(r: String?): String = try { (JSONTokener(r ?: "").nextValue() as? String) ?: "" } catch (e: Exception) { "" }

    /** A pill button. */
    fun pill(act: Activity, t: String, bg: Int, fg: Int = Color.WHITE, size: Float = 13f, on: () -> Unit) = RunUi.text(act, t, fg, size, true).apply {
        gravity = Gravity.CENTER; background = RunUi.rounded(bg, RunUi.dp(act, 18).toFloat())
        setPadding(RunUi.dp(act, 14), RunUi.dp(act, 8), RunUi.dp(act, 14), RunUi.dp(act, 8)); setOnClickListener { on() }
    }
    fun gradient(a: Int, b: Int, r: Float) = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(a, b)).apply { cornerRadius = r }

    /** A text field that saves (debounced, and when it loses focus) through [commit]. */
    fun field(act: Activity, hint: String, lines: Int = 1, commit: ((String) -> Unit)? = null) = EditText(act).apply {
        this.hint = hint; setTextColor(Color.WHITE); setHintTextColor(RunUi.DIM); textSize = 14f
        if (lines <= 1) isSingleLine = true else { minLines = lines; gravity = Gravity.TOP or Gravity.START }
        background = RunUi.rounded(0xFF26302C.toInt(), RunUi.dp(act, 8).toFloat())
        setPadding(RunUi.dp(act, 10), RunUi.dp(act, 8), RunUi.dp(act, 10), RunUi.dp(act, 8))
        if (commit != null) {
            imeOptions = EditorInfo.IME_ACTION_DONE
            val run = Runnable { commit(text.toString()) }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { if (hasFocus()) { ui.removeCallbacks(run); ui.postDelayed(run, 900) } }
            })
            setOnFocusChangeListener { _, has -> if (!has) { ui.removeCallbacks(run); run.run() } }
            setOnEditorActionListener { v, _, _ -> v.clearFocus(); false }
        }
    }
    fun setIfIdle(e: EditText, v: String) { if (!e.hasFocus() && e.text.toString() != v) e.setText(v) }

    // ── Images: covers from the web (cached), and photos turned into small JPEG data URLs like the Command Center makes. ──
    private val imgCache = android.util.LruCache<String, android.graphics.Bitmap>(40)
    fun loadImage(url: String, into: android.widget.ImageView) {
        into.tag = url
        imgCache.get(url)?.let { into.setImageBitmap(it); return }
        if (url.startsWith("data:")) {
            try { val b = android.util.Base64.decode(url.substringAfter(","), android.util.Base64.DEFAULT); android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size)?.let { imgCache.put(url, it); into.setImageBitmap(it) } } catch (e: Exception) {}
            return
        }
        Thread {
            val bm = try { (java.net.URL(url).openConnection() as java.net.HttpURLConnection).run { connectTimeout = 8000; readTimeout = 10000; instanceFollowRedirects = true
                inputStream.use { android.graphics.BitmapFactory.decodeStream(it) } } } catch (e: Exception) { null }
            if (bm != null) { imgCache.put(url, bm); ui.post { if (into.tag == url) into.setImageBitmap(bm) } }
        }.start()
    }
    fun photoDataUrl(ctx: android.content.Context, uri: android.net.Uri, maxW: Int = 160, maxH: Int = 220): String? = try {
        val src = ctx.contentResolver.openInputStream(uri)!!.use { android.graphics.BitmapFactory.decodeStream(it) }
        val sc = minOf(maxW.toFloat() / src.width, maxH.toFloat() / src.height, 1f)
        val bm = android.graphics.Bitmap.createScaledBitmap(src, (src.width * sc).toInt().coerceAtLeast(1), (src.height * sc).toInt().coerceAtLeast(1), true)
        val out = java.io.ByteArrayOutputStream(); bm.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
        "data:image/jpeg;base64," + android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
    } catch (e: Exception) { null }
    /** Poll [js] (a call returning '' until ready) every 700 ms, up to [tries] times. */
    fun poll(web: android.webkit.WebView, js: String, tries: Int = 25, done: (String?) -> Unit) {
        web.evaluateJavascript("window.ccNative?($js):''") { r -> val v = str(r)
            if (v.isNotEmpty()) done(v) else if (tries > 1) ui.postDelayed({ poll(web, js, tries - 1, done) }, 700) else done(null) }
    }
    fun lp(top: Int = 0) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = top }

    /** A card with a title you tap to open or close (remembered); the body is drawn when it opens. */
    class Collapsible(private val act: Activity, private val key: String, title: String, color: Int, defaultOpen: Boolean, private val onOpen: (LinearLayout) -> Unit) {
        private val pr = act.getSharedPreferences("collapse", 0)
        var open = pr.getBoolean(key, defaultOpen); private set
        val body = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        private val arrow = RunUi.text(act, "", RunUi.MUTED, 14f, true)
        val view: LinearLayout = RunUi.card(act).apply {
            addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(RunUi.text(act, title, color, 15f, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(arrow)
                setOnClickListener { open = !open; pr.edit().putBoolean(key, open).apply(); show() } })
            addView(body)
        }
        init { show() }
        private fun show() { arrow.text = if (open) "▼" else "▶"; body.visibility = if (open) android.view.View.VISIBLE else android.view.View.GONE; if (open) refresh() }
        /** Redraw the body (only while open). */
        fun refresh() { if (open) onOpen(body) }
    }

    /** One row of a [FoldGroup]: "📅 Weekly — by day" style title, remembered open/closed, body drawn when it opens. */
    class Fold(val key: String, val label: String, val defaultOpen: Boolean, val onOpen: (LinearLayout) -> Unit)

    /**
     * Rows that open in place, joined like the Activities tab's list (icon, white title, › that turns down when open).
     * The open row's content sits right under it in the same rounded block.
     */
    class FoldGroup(private val act: Activity, private val folds: List<Fold>) {
        private val pr = act.getSharedPreferences("collapse", 0)
        val view = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        private val rows = ArrayList<RunUi.GroupRow>(); private val bodies = ArrayList<LinearLayout>()
        init {
            folds.forEachIndexed { i, f ->
                val l = f.label.trim(); val sp = l.indexOf(' ')
                val icon = if (sp > 0 && l.substring(0, sp).none { it.isLetterOrDigit() }) l.substring(0, sp) else ""
                val title = if (icon.isNotEmpty()) l.substring(sp + 1).trim() else l
                val body = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(RunUi.dp(act, 14), RunUi.dp(act, 2), RunUi.dp(act, 14), RunUi.dp(act, 12)) }
                lateinit var row: RunUi.GroupRow
                row = RunUi.groupRow(act, icon, title, null, pr.getBoolean(f.key, f.defaultOpen)) {
                    val o = !row.open; row.setOpen(o); pr.edit().putBoolean(f.key, o).apply(); body.visibility = if (o) View.VISIBLE else View.GONE
                    if (o) f.onOpen(body); style() }
                rows += row; bodies += body
                view.addView(row); view.addView(body)
                body.visibility = if (row.open) View.VISIBLE else View.GONE
                if (row.open) f.onOpen(body)
            }
            style()
        }
        /** Redraw the open rows' content. */
        fun refresh() { folds.forEachIndexed { i, f -> if (rows[i].open) f.onOpen(bodies[i]) } }
        private fun style() {
            val r = RunUi.dp(act, 14).toFloat()
            rows.forEachIndexed { i, row ->
                val first = i == 0; val last = i == rows.size - 1; val bodyOpen = bodies[i].visibility == View.VISIBLE
                val t = if (first) r else 0f; val b = if (last && !bodyOpen) r else 0f
                row.background = GradientDrawable().apply { setColor(row.bg); cornerRadii = floatArrayOf(t, t, t, t, b, b, b, b) }
                (row.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { it.topMargin = if (first) RunUi.dp(act, 12) else RunUi.dp(act, 1); row.layoutParams = it }
                val bb = if (last) r else 0f
                bodies[i].background = GradientDrawable().apply { setColor(row.bg); cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, bb, bb, bb, bb) }
            }
        }
    }

    /**
     * A table whose first column (dates / weeks / months) stays put while the other columns scroll sideways.
     * Takes a built table and splits off its first column; row heights are matched after layout.
     */
    fun pinFirstColumn(t: android.widget.TableLayout): View {
        val ctx = t.context
        val left = android.widget.TableLayout(ctx)
        val pairs = ArrayList<Pair<android.widget.TableRow, android.widget.TableRow>>()
        for (i in 0 until t.childCount) {
            val r = t.getChildAt(i) as? android.widget.TableRow ?: continue
            val first = r.getChildAt(0) ?: continue
            r.removeViewAt(0)
            val lr = android.widget.TableRow(ctx); lr.addView(first); left.addView(lr); pairs += lr to r
        }
        val scroll = android.widget.HorizontalScrollView(ctx).apply { addView(t); isHorizontalScrollBarEnabled = true }
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL
            addView(left, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(scroll, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)) }
        // Match each row's height on both sides (a cell with two lines on one side makes that row taller).
        box.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                var changed = false
                pairs.forEach { (l, r) -> val h = maxOf(l.height, r.height); if (h > 0 && (l.minimumHeight != h || r.minimumHeight != h)) { l.minimumHeight = h; r.minimumHeight = h; changed = true } }
                if (changed) box.requestLayout() else if (pairs.all { it.first.height > 0 }) box.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })
        return box
    }

    /**
     * 📝 Reviews easier to read: each "• " point gets a hanging indent and a little space after it, and a short
     * "Label:" at its start is bold. Only how it looks; the saved text stays exactly as written.
     */
    /** A color for each kind of point, so the labels stand apart (and the same kind always looks the same). */
    fun labelColor(label: String): Int {
        val l = label.lowercase()
        return when {
            listOf("faith", "spirit", "bible", "prayer", "worship", "church").any { it in l } -> 0xFFC4B5FD.toInt()   // lavender
            listOf("family", "wife", "son", "relationship", "friend").any { it in l } -> 0xFFF9A8D4.toInt()          // pink
            listOf("training", "run", "workout", "recovery", "fitness", "race").any { it in l } -> 0xFFFDBA74.toInt() // orange
            listOf("nutrition", "food", "sugar", "protein", "diet", "eating").any { it in l } -> 0xFF86EFAC.toInt()   // green
            listOf("hydration", "fluid", "water").any { it in l } -> 0xFF67E8F9.toInt()                              // cyan
            listOf("sleep", "rest").any { it in l } -> 0xFF93C5FD.toInt()                                             // blue
            listOf("wellness", "health", "bp", "blood", "weight", "meds").any { it in l } -> 0xFFFCA5A5.toInt()      // coral
            listOf("reading", "library", "book", "audible", "learning").any { it in l } -> 0xFFFDE68A.toInt()        // gold
            listOf("habit", "routine", "work", "focus", "mindset").any { it in l } -> 0xFF5EEAD4.toInt()              // teal
            else -> 0xFF7DD3FC.toInt()                                                                                 // sky
        }
    }

    fun bulletStyle(et: android.widget.EditText) {
        val ctx = et.context
        val gap = RunUi.dp(ctx, 8); val indent = et.paint.measureText("• ").toInt()
        class Gap : android.text.style.LineHeightSpan {
            override fun chooseHeight(t: CharSequence, start: Int, end: Int, spanstartv: Int, v: Int, fm: android.graphics.Paint.FontMetricsInt) {
                val sp = t as? android.text.Spanned ?: return
                if (end == sp.getSpanEnd(this) && end < t.length) { fm.descent += gap; fm.bottom += gap }
            }
        }
        class Bold : android.text.style.StyleSpan(android.graphics.Typeface.BOLD)
        class Tint(c: Int) : android.text.style.ForegroundColorSpan(c)
        val style = Runnable {
            val e = et.text ?: return@Runnable
            e.getSpans(0, e.length, Gap::class.java).forEach { e.removeSpan(it) }
            e.getSpans(0, e.length, Bold::class.java).forEach { e.removeSpan(it) }
            e.getSpans(0, e.length, Tint::class.java).forEach { e.removeSpan(it) }
            e.getSpans(0, e.length, android.text.style.LeadingMarginSpan.Standard::class.java).forEach { e.removeSpan(it) }
            var at = 0
            while (at < e.length) {
                val nl = e.indexOf('\n', at).let { if (it < 0) e.length else it }
                val line = e.substring(at, nl); val end = if (nl < e.length) nl + 1 else nl
                val bullet = line.startsWith("•") || line.startsWith("- ")
                if (bullet) {
                    e.setSpan(android.text.style.LeadingMarginSpan.Standard(0, indent), at, end, android.text.Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
                    Regex("^[•\\-]\\s*([^:]{2,32}):").find(line)?.let { m ->
                        val f = at + m.groups[1]!!.range.first; val t = at + m.range.last + 1
                        e.setSpan(Bold(), f, t, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        e.setSpan(Tint(labelColor(m.groupValues[1])), f, t, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                }
                if (end > at && end < e.length) e.setSpan(Gap(), at, end, android.text.Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
                at = end
            }
        }
        et.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { et.removeCallbacks(style); et.post(style) }
        })
        et.post(style)
    }
}
