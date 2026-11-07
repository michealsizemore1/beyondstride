package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import org.json.JSONObject
import java.util.Locale

/**
 * 🎯 Training Zones (Fitness tab): Critical Power / Max HR / Resting HR boxes (saved to the Command Center and to
 * BeyondStride's watch zones), then the Heart Rate (Karvonen) and Power tables. Collapsible.
 */
object ZonesCard {
    private val ZONE_COLOR = mapOf("Z1" to 0xFF60A5FA.toInt(), "Z2" to 0xFF22C55E.toInt(), "Z3" to 0xFFEAB308.toInt(), "Z4" to 0xFFF97316.toInt(), "Z5" to 0xFFEF4444.toInt())
    private fun n(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else String.format(Locale.US, "%.1f", v)

    fun build(act: Activity, col: LinearLayout, open: MutableSet<String>, redraw: () -> Unit) {
        val ctx = act; fun dp(v: Int) = RunUi.dp(ctx, v)
        fun txt(t: String, c: Int, sp: Float, b: Boolean = false) = RunUi.text(ctx, t, c, sp, b)
        val S = ActivityStore
        val st = S.settings(ctx)
        val max = st.optDouble("maxHr", 0.0).takeIf { it > 0 } ?: RunSettings.hrMax(ctx)
        val rest = st.optDouble("restHr", 0.0).takeIf { it > 0 } ?: RunSettings.hrRest(ctx)
        val cp = st.optDouble("cp", 0.0).takeIf { it > 0 } ?: RunSettings.cp(ctx).toDouble()
        val res = Math.max(1.0, max - rest); val bnd = listOf(0.5, 0.6, 0.7, 0.8, 0.9).map { Math.round(rest + res * it).toInt() }
        val isOpen = "zones" in open
        col.addView(txt((if (isOpen) "▼" else "▶") + "  🎯 Training Zones" + (if (!isOpen) "  (Z2 ${bnd[1]}–${bnd[2] - 1} bpm)" else ""), 0xFFE67E22.toInt(), 15f, true).apply {
            setPadding(dp(2), dp(16), 0, dp(4)); setOnClickListener { if (isOpen) open.remove("zones") else open.add("zones"); redraw() } })
        if (!isOpen) return
        fun setZone(key: String, v: Double) {
            if (v <= 0) return
            S.saveSettings(ctx, JSONObject().put(key, v))
            RunSettings.save(ctx, if (key == "maxHr") v else RunSettings.hrMax(ctx), if (key == "restHr") v else RunSettings.hrRest(ctx),
                if (key == "cp") v.toInt() else RunSettings.cp(ctx), RunSettings.cal(ctx), RunSettings.ccUrl(ctx))
            redraw()
        }
        col.addView(RunUi.card(ctx).apply {
            // Inputs (used for the zones here, every activity card's zone badges, and the watch's alerts).
            fun input(l: String, v: Double, hint: String, key: String) = addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(3), 0, dp(3))
                addView(txt(l, RunUi.MUTED, 13f), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(EditText(ctx).apply {
                    if (v > 0) setText(n(v)); this.hint = hint; setSingleLine(); textSize = 14f; setTextColor(Color.WHITE); setHintTextColor(RunUi.DIM); gravity = Gravity.END
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                    background = RunUi.rounded(0xFF26302C.toInt(), dp(6).toFloat()); setPadding(dp(8), dp(4), dp(8), dp(4))
                    imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                    var last = v
                    fun go() { val x = text.toString().trim().toDoubleOrNull() ?: 0.0; if (x != last && x > 0) { last = x; setZone(key, x) } }
                    setOnEditorActionListener { _, _, _ -> go(); false }
                    setOnFocusChangeListener { _, has -> if (!has) go() }
                }, LinearLayout.LayoutParams(dp(80), WRAP_CONTENT))
            })
            input("Critical power (W)", st.optDouble("cp", 0.0), "280", "cp")
            input("Max heart rate", st.optDouble("maxHr", 0.0), "174", "maxHr")
            input("Resting HR (30-day avg)", st.optDouble("restHr", 0.0), "55", "restHr")
            // BeyondStride's own (watch) zones differ from the Command Center's? Offer to match.
            val bsMax = RunSettings.hrMax(ctx); val bsRest = RunSettings.hrRest(ctx); val bsCp = RunSettings.cp(ctx)
            if (st.has("cp") && (st.optDouble("maxHr") != bsMax || st.optDouble("restHr") != bsRest || st.optDouble("cp").toInt() != bsCp))
                addView(txt("BeyondStride's watch zones use Max ${n(bsMax)} · Rest ${n(bsRest)} · CP $bsCp.  Use these in both ▸", 0xFFF2C94C.toInt(), 12f, true).apply {
                    setPadding(0, dp(6), 0, 0); setOnClickListener { S.saveSettings(ctx, JSONObject().put("cp", bsCp).put("maxHr", bsMax).put("restHr", bsRest)); redraw() } })
            fun badge(z: String) = txt(" $z ", Color.BLACK, 11f, true).apply { background = RunUi.rounded(ZONE_COLOR[z] ?: 0xFF888888.toInt(), dp(4).toFloat()) }
            fun row(z: String, range: String, label: String) = addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(3), 0, dp(3))
                addView(badge(z)); addView(txt("  $range", Color.WHITE, 13f), LinearLayout.LayoutParams(dp(130), WRAP_CONTENT)); addView(txt(label, RunUi.MUTED, 12f))
            })
            addView(txt("❤️ Heart Rate (Karvonen)", Color.WHITE, 14f, true).apply { setPadding(0, dp(12), 0, 0) })
            addView(txt("Max HR ${n(max)} bpm · Resting HR ${n(rest)} bpm" + st.optString("zonesNote").let { if (it.isNotEmpty()) " · $it" else "" }, RunUi.MUTED, 11f))
            val hrL = listOf("Recovery", "Aerobic base", "Aerobic threshold", "Anaerobic", "Max effort")
            (0 until 5).forEach { i -> row("Z${i + 1}", if (i < 4) "${bnd[i]}–${bnd[i + 1] - 1} bpm" else "${bnd[4]}+ bpm", hrL[i]) }
            addView(txt("⚡ Power", Color.WHITE, 14f, true).apply { setPadding(0, dp(10), 0, 0) })
            if (cp <= 0) addView(txt("Set Critical Power above to see your power zone ranges.", RunUi.MUTED, 12f))
            else listOf(Triple(0.0, 0.55, "Recovery"), Triple(0.55, 0.75, "Aerobic / endurance"), Triple(0.75, 0.90, "Tempo"), Triple(0.90, 1.05, "Threshold"), Triple(1.05, -1.0, "VO2 max"))
                .forEachIndexed { i, (lo, hi, l) -> row("Z${i + 1}", if (hi > 0) "${Math.round(cp * lo)}–${Math.round(cp * hi)} W" else "${Math.round(cp * lo)}+ W", l) }
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }
}
