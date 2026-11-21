package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.WebView
import android.widget.LinearLayout
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 🧘 Meditation & Stillness heading (check box + date, like the Command Center); the timer and sounds sit below it. */
class LifeMeditate(private val act: Activity, private val web: WebView) {
    private fun d(v: Int) = RunUi.dp(act, v)
    private val box = LifeUi.box(act); private var st = ""
    private val date = RunUi.text(act, "", RunUi.MUTED, 13f)
    private val line = RunUi.text(act, "", RunUi.MUTED, 12f)
    val view = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(0, d(10), 0, 0)
        addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            LifeUi.paintBox(box, "")
            box.setOnClickListener { st = LifeUi.CYCLE[(LifeUi.CYCLE.indexOf(st) + 1) % LifeUi.CYCLE.size]; LifeUi.paintBox(box, st)
                web.evaluateJavascript("window.ccNative&&ccNative.setCheck('spMeditation',${JSONObject.quote(st)})", null) }
            addView(box, LinearLayout.LayoutParams(d(28), d(28)))
            addView(RunUi.text(act, "🧘 Meditation & Stillness", 0xFFA78BFA.toInt(), 20f, true).apply { setPadding(d(10), 0, 0, 0) }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)) })
        addView(date.apply { setPadding(0, d(4), 0, 0) }); addView(line.apply { setPadding(0, d(2), 0, 0) })
    }
    fun fill(j: JSONObject) {
        date.text = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US))
        j.optJSONObject("meditation")?.let { m ->
            st = m.optString("state"); LifeUi.paintBox(box, st)
            val n = m.optInt("weekSessions")
            line.text = listOf(m.optString("line"), "This week: $n session${if (n == 1) "" else "s"} · ${m.optInt("weekMins")} min").filter { it.isNotBlank() }.joinToString("  ·  ")
        }
    }
}
