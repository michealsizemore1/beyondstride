package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 🌙 Evening report on the phone (also opened from the 11 PM notification). */
class EveningReportActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); render()
        val app = applicationContext; Thread { if (TodayWeather.fetch(app)) runOnUiThread { if (!isFinishing) render() } }.start() }
    override fun onResume() { super.onResume(); render() }

    private fun render() {
        val ctx = this; fun dp(v: Int) = RunUi.dp(ctx, v)
        val r = EveningReport.build(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(40), dp(16), dp(40)) }
        col.addView(RunUi.text(this, "🌙 Evening report", Color.WHITE, 28f, true))
        col.addView(RunUi.text(this, LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)), RunUi.MUTED, 13f).apply { setPadding(dp(4), dp(4), 0, 0) })
        col.addView(RunUi.card(this).apply { background = RunUi.rounded(0xFF26264A.toInt(), dp(14).toFloat())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) }
            addView(RunUi.text(ctx, r.advice, Color.WHITE, 15f, true)) })
        r.sections.forEach { s ->
            col.addView(RunUi.card(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) }
                addView(RunUi.text(ctx, "${s.icon}  ${s.title.uppercase()}", RunUi.MUTED, 11f, true))
                addView(RunUi.text(ctx, s.main, s.color, 18f, true).apply { setPadding(0, dp(2), 0, dp(2)) })
                s.lines.forEach { addView(RunUi.text(ctx, it, Color.WHITE, 13f).apply { setPadding(0, dp(2), 0, 0) }) }
                when (s.title) {
                    "Today's training", "Tomorrow" -> setOnClickListener { startActivity(Intent(ctx, ActivitiesActivity::class.java)) }
                    "Food", "Fluids" -> setOnClickListener { startActivity(Intent(ctx, NutritionActivity::class.java)) }
                }
            })
        }
        col.addView(RunUi.text(this, "Tap a card to open it. The notification comes at ${fmtTime(EveningReport.time(this))} — change it in Settings → Reminders.", RunUi.DIM, 11f).apply { setPadding(dp(4), dp(12), dp(4), 0) })
        setContentView(ScrollView(this).apply { setBackgroundColor(RunUi.BG); addView(col) })
    }
    private fun fmtTime(m: Int) = java.time.LocalTime.of(m / 60, m % 60).format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
}
