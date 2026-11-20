package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
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
}
