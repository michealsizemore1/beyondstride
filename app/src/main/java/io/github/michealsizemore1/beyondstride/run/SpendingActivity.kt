package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 💳 The bigger Spending view (Today → Spending → Details): this week and last week, Sunday to Saturday, and each day. */
class SpendingActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); render() }
    override fun onResume() { super.onResume(); render() }

    private fun render() {
        val ctx = this; fun dp(v: Int) = RunUi.dp(ctx, v)
        val MP = LinearLayout.LayoutParams.MATCH_PARENT; val WC = LinearLayout.LayoutParams.WRAP_CONTENT
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(40), dp(16), dp(40)) }
        col.addView(RunUi.text(this, "💳 Spending", Color.WHITE, 28f, true))
        col.addView(RunUi.text(this, "From M&C Money · all accounts", RunUi.MUTED, 13f).apply { setPadding(dp(4), dp(4), 0, 0) })
        val m = TodaySpending.load(this)
        if (m == null) {
            col.addView(RunUi.text(this, "Spending isn't available yet. Open the Today screen's Spending card to set it up.", RunUi.MUTED, 14f).apply { setPadding(0, dp(16), 0, 0) })
            setContentView(ScrollView(this).apply { setBackgroundColor(RunUi.BG); addView(col) }); return
        }
        val md = DateTimeFormatter.ofPattern("MMM d", Locale.US)
        fun range(w: List<LocalDate>) = "${w.first().format(md)} – ${w.last().format(md)}"
        fun card() = RunUi.card(ctx).apply { orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(12) } }
        fun stat(row: LinearLayout, label: String, value: String, color: Int = Color.WHITE) =
            row.addView(LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL
                addView(RunUi.text(ctx, label, RunUi.MUTED, 11f, true))
                addView(RunUi.text(ctx, value, color, 17f, true))
            }, LinearLayout.LayoutParams(0, WC, 1f))

        // This week.
        val soFar = m.weekVals.count { it != null }.coerceAtLeast(1)
        val sameDaysLast = m.lastWeekVals.take(soFar).filterNotNull().sum()
        col.addView(card().apply {
            addView(LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(RunUi.text(ctx, "This week", Color.WHITE, 17f, true), LinearLayout.LayoutParams(0, WC, 1f))
                addView(RunUi.text(ctx, range(m.week), RunUi.MUTED, 12f)) })
            addView(LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0)
                stat(this, "Total", TodaySpending.money(m.weekTotal), TodaySpending.GREEN)
                stat(this, "Per day", TodaySpending.money(m.weekTotal / soFar))
                stat(this, "Today", m.amt(m.today)?.let { TodaySpending.money(it) } ?: "—") })
            addView(TodaySpending.Bars(ctx, m.weekVals, m.week.indexOf(m.today), big = true), LinearLayout.LayoutParams(MP, dp(200)).apply { topMargin = dp(12) })
            val diff = m.weekTotal - sameDaysLast
            val days = if (soFar == 1) "Sunday" else "Sunday–${m.today.format(DateTimeFormatter.ofPattern("EEEE", Locale.US))}"
            addView(RunUi.text(ctx, when {
                Math.abs(diff) < 0.005 -> "Same as last week at this point ($days)."
                diff < 0 -> "${TodaySpending.money(-diff)} less than last week at this point ($days)."
                else -> "${TodaySpending.money(diff)} more than last week at this point ($days)."
            }, if (diff <= 0) TodaySpending.GREEN else 0xFFF87171.toInt(), 12f, true).apply { setPadding(0, dp(8), 0, 0) })
        })

        // Last week.
        col.addView(card().apply {
            addView(LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(RunUi.text(ctx, "Last week", Color.WHITE, 17f, true), LinearLayout.LayoutParams(0, WC, 1f))
                addView(RunUi.text(ctx, range(m.lastWeek), RunUi.MUTED, 12f)) })
            addView(LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0)
                stat(this, "Total", TodaySpending.money(m.lastWeekTotal), TodaySpending.GREEN)
                stat(this, "Per day", TodaySpending.money(m.lastWeekTotal / 7))
                val top = m.lastWeek.zip(m.lastWeekVals).filter { (it.second ?: 0.0) > 0 }.maxByOrNull { it.second ?: 0.0 }
                stat(this, "Biggest day", top?.let { "${it.first.format(DateTimeFormatter.ofPattern("EEE", Locale.US))} ${TodaySpending.money(it.second!!)}" } ?: "—") })
            addView(TodaySpending.Bars(ctx, m.lastWeekVals, -1, big = true), LinearLayout.LayoutParams(MP, dp(170)).apply { topMargin = dp(12) })
        })

        // Each day, newest first.
        col.addView(card().apply {
            addView(RunUi.text(ctx, "Day by day", Color.WHITE, 17f, true).apply { setPadding(0, 0, 0, dp(4)) })
            val dayFmt = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
            (m.week.filter { !it.isAfter(m.today) }.reversed() + m.lastWeek.reversed()).forEach { day ->
                val v = m.amt(day)
                addView(LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, dp(6))
                    val label = when (day) { m.today -> "Today"; m.today.minusDays(1) -> "Yesterday"; else -> day.format(dayFmt) }
                    addView(RunUi.text(ctx, label, if (day == m.today) RunUi.ACCENT else Color.WHITE, 14f, day == m.today), LinearLayout.LayoutParams(0, WC, 1f))
                    addView(RunUi.text(ctx, v?.let { TodaySpending.money(it) } ?: "—", if ((v ?: 0.0) > 0) Color.WHITE else RunUi.MUTED, 14f, true))
                })
                if (day == m.sun) addView(android.view.View(ctx).apply { setBackgroundColor(0x33FFFFFF) }, LinearLayout.LayoutParams(MP, dp(1)).apply { topMargin = dp(4); bottomMargin = dp(4) })
            }
        })

        col.addView(RunUi.button(this, "Open M&C Money", RunUi.ACCENT) { TodaySpending.openMc(this) }.apply {
            layoutParams = LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(14) } })
        col.addView(RunUi.text(this, "Same rules as your M&C budget: transfers, credit card payments, income and anything excluded from the budget don't count; refunds lower a day's total. M&C Money sends new totals each time you save a transaction.", RunUi.DIM, 11f).apply { setPadding(dp(4), dp(12), dp(4), 0) })
        setContentView(RunUi.keepScroll(this, ScrollView(this).apply { setBackgroundColor(RunUi.BG); addView(col) }))
    }
}
