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
 * separate from BeyondStride's — two keys, by choice), whether the AI may read the journal, and sync status.
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
        var aiContext = true
        lateinit var load: () -> Unit
        load = {
            js("ccNative.settings()") { r ->
                val j = LifeUi.obj(r)
                if (j == null) { keyState.text = "Starting the Life tab…"; web.postDelayed({ load() }, 1500); return@js }
                keyState.text = if (j.optBoolean("hasKey")) "🔑 Life tab AI key: saved ✓" else "🔑 Life tab AI key: not set (needed for AI Fill, Ask AI and Bible Study)"
                aiContext = j.optBoolean("aiContext", true)
                ctxToggle.text = (if (aiContext) "☑" else "☐") + "  Let the AI read your journal for answers and reviews"
                val sync = j.optJSONObject("sync")
                status.text = listOfNotNull(
                    sync?.let { if (it.optBoolean("linked")) "🔄 Sync with Chrome: on" + (if (it.optInt("pending") > 0) " · ${it.optInt("pending")} waiting to upload" else " · up to date") else "🔄 Sync: not linked yet" },
                    j.optString("status").takeIf { it.isNotBlank() }, j.optString("storage").takeIf { it.isNotBlank() }?.let { "💾 $it" }).joinToString("\n")
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
            addView(RunUi.button(act, "🔄 Sync now", 0xFF374151.toInt(), Color.WHITE) { js("ccNative.syncNow()") { web.postDelayed({ load() }, 4000) } },
                LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = d(8) })
            load()
        }
    }
}
