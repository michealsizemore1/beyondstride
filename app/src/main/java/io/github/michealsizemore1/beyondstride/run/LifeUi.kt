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
    fun lp(top: Int = 0) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = top }
}
