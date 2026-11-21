package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.graphics.Color
import android.text.InputType
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import org.json.JSONObject

/**
 * 🌱 Life tab settings, in BeyondStride's Settings (moved from Life → Tools): the Command Center's own AI key (kept
 * separate from BeyondStride's — two keys, by choice), whether the AI may read the journal, and that the Life tab is its own copy (not synced with the website).
 * Talks to the same shared Command Center page as the Life tab (CcEngine).
 */
object LifeSettings {
    fun card(act: Activity): LinearLayout {
        val d = { v: Int -> RunUi.dp(act, v) }
        val web = CcEngine.get(act)
        fun js(call: String, cb: (String) -> Unit = {}) = web.evaluateJavascript("window.ccNative&&ccNative.settings?$call:null") { cb(it ?: "") }
        val keyState = RunUi.text(act, "Loading…", Color.WHITE, 15f, true)
        val ctxToggle = RunUi.text(act, "", Color.WHITE, 14f)
        val status = RunUi.text(act, "", RunUi.MUTED, 12f)
        val storage = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        var aiContext = true
        lateinit var load: () -> Unit
        load = {
            js("ccNative.settings()") { r ->
                val j = LifeUi.obj(r)
                if (j == null) { keyState.text = "Starting the Life tab…"; web.postDelayed({ load() }, 1500); return@js }
                keyState.text = if (j.optBoolean("hasKey")) "🔑 Life tab AI key: saved ✓" else "🔑 Life tab AI key: not set (needed for AI Fill, Ask AI and Bible Study)"
                aiContext = j.optBoolean("aiContext", true)
                ctxToggle.text = (if (aiContext) "☑" else "☐") + "  Let the AI read your journal for answers and reviews"
                status.text = listOfNotNull(
                    "🔌 Not synced with the Command Center website: the Life tab is its own copy. It's kept safe by BeyondStride's Backup.",
                    j.optString("status").takeIf { it.isNotBlank() }).joinToString("\n")
                // 💾 Storage, laid out line by line, colored by how full it is.
                storage.removeAllViews()
                j.optJSONObject("store")?.let { st -> val pct = st.optInt("pct")
                    val col = if (pct >= 85) 0xFFF87171.toInt() else if (pct >= 70) 0xFFF59E0B.toInt() else 0xFF4ADE80.toInt()
                    storage.addView(RunUi.text(act, "💾 Storage: ${pct}% full · ${st.optDouble("mb")} MB · ${st.optInt("entries")} daily entries", col, 13f, true))
                    if (st.optInt("invalid") > 0) storage.addView(RunUi.text(act, "⚠️ ${st.optInt("invalid")} daily entr${if (st.optInt("invalid") == 1) "y needs" else "ies need"} attention", 0xFFF87171.toInt(), 12f))
                    val top = st.optJSONArray("top")
                    if (top != null && top.length() > 0) { storage.addView(RunUi.text(act, "Largest items:", RunUi.MUTED, 12f).apply { setPadding(0, d(4), 0, 0) })
                        for (i in 0 until top.length()) { val o = top.getJSONObject(i)
                            storage.addView(RunUi.text(act, "• ${o.optString("key")} — ${o.optInt("kb")} KB" + (o.optString("field").takeIf { it.isNotBlank() }?.let { " (mostly $it)" } ?: ""), RunUi.MUTED, 12f)) } }
                    storage.addView(RunUi.text(act, if (pct >= 80) "Getting full — when it's full, new saves fail. Tell Claude \"Life storage is getting full\" to make room (nothing is lost: it's all in your backup and sync)."
                        else "You'll get a heads-up at 80%.", if (pct >= 80) col else RunUi.DIM, 11f).apply { setPadding(0, d(4), 0, 0) })
                }
            }
        }
        val key = EditText(act).apply { hint = "Paste Anthropic API key (sk-ant-…)"; isSingleLine = true; setTextColor(Color.WHITE); setHintTextColor(RunUi.DIM); textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            background = RunUi.rounded(0xFF26302C.toInt(), d(8).toFloat()); setPadding(d(10), d(8), d(10), d(8)) }
        return RunUi.card(act).apply {
            addView(RunUi.text(act, "⚙️ Command Center (Life tab)", Color.WHITE, 16f, true))
            addView(keyState.apply { setPadding(0, d(8), 0, d(4)) })
            addView(key, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = d(4) })
            addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, d(8), 0, 0)
                addView(RunUi.button(act, "Save key", RunUi.ACCENT) { val k = key.text.toString().trim(); if (k.isNotEmpty()) js("ccNative.setKey(${JSONObject.quote(k)})") { key.setText(""); load() } },
                    LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = d(8) })
                addView(RunUi.button(act, "Remove key", 0xFF374151.toInt(), Color.WHITE) { js("ccNative.setKey('')") { load() } }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)) })
            addView(RunUi.text(act, "The Life tab's own key: it stays in this phone's Command Center only — never synced or backed up. BeyondStride's key (AI food search, Coach) is separate and is never given to it.", RunUi.DIM, 11f).apply { setPadding(0, d(6), 0, 0) })
            addView(ctxToggle.apply { setPadding(0, d(12), 0, d(4)); setOnClickListener { js("ccNative.setAIContext(${!aiContext})") { load() } } })
            addView(status.apply { setPadding(0, d(8), 0, 0) })
            addView(storage.apply { setPadding(0, d(8), 0, 0) })
            load()
        }
    }
}
