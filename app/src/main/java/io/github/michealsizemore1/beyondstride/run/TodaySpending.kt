package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import org.json.JSONObject
import java.time.LocalDate

/**
 * 💳 Spending on the Today tab, from M&C Money: today, yesterday and this week (Sunday to Saturday) as a bar chart.
 * M&C Money shares only one total per day (all accounts, same rules as its budget: transfers, card payments, income
 * and "excluded from budget" don't count). Spending is only ever entered in M&C Money, which sends new totals on every
 * save, so these are always current. "Details ›" opens the bigger view (SpendingActivity).
 */
object TodaySpending {
    const val PKG = "com.sizemore.mcmoney"
    const val PERM = "com.sizemore.mcmoney.READ_SPENDING"
    const val REQ = 87
    private val URI = Uri.parse("content://com.sizemore.mcmoney.spending/daily")
    val GREEN = 0xFF2EE66B.toInt()

    fun installed(ctx: Context) = try { ctx.packageManager.getPackageInfo(PKG, 0); true } catch (e: Exception) { false }
    /** The installed M&C Money is new enough to share spending. */
    private fun shares(ctx: Context) = try { ctx.packageManager.getPermissionInfo(PERM, 0); true } catch (e: Exception) { false }
    private fun allowed(ctx: Context) = ctx.checkSelfPermission(PERM) == PackageManager.PERMISSION_GRANTED

    private fun read(ctx: Context): Pair<JSONObject, Long>? = try {
        ctx.contentResolver.query(URI, null, null, null, null)?.use { c ->
            if (!c.moveToFirst()) null
            else { val js = c.getString(0) ?: ""; if (js.isEmpty()) null else JSONObject(js).let { root -> root.optJSONObject("days")?.let { root to c.getLong(1) } } }
        }
    } catch (e: Exception) { null }

    fun money(v: Double) = if (v >= 1000) String.format("$%,.0f", v) else String.format("$%,.2f", v)

    fun fill(act: Activity, card: LinearLayout) {
        val ctx: Context = act
        card.removeAllViews()
        val d = { v: Int -> RunUi.dp(ctx, v) }
        val closed = TodayMarkets.collapsed(ctx, "spending")
        val head = TodayMarkets.header(ctx, "💳 Spending", closed) { TodayMarkets.setCollapsed(ctx, "spending", !closed); fill(act, card) }
        card.addView(head)
        fun note(s: String) = card.addView(RunUi.text(ctx, s, RunUi.MUTED, 12f).apply { setPadding(0, d(6), 0, 0) })
        card.setOnClickListener(null)
        if (!shares(ctx)) {
            note(if (installed(ctx)) "Update M&C Money (Settings → Check for updates) to see your daily spending here." else "Install M&C Money to see your daily spending here.")
            return
        }
        if (!allowed(ctx)) {
            note("Let BeyondStride see M&C Money's daily spending totals (one number per day, nothing else).")
            card.addView(RunUi.text(ctx, "Allow", Color.BLACK, 13f, true).apply {
                background = RunUi.rounded(RunUi.ACCENT, d(16).toFloat()); setPadding(d(16), d(7), d(16), d(7))
                setOnClickListener { act.requestPermissions(arrayOf(PERM), REQ) }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = d(8) })
            return
        }
                val data = read(ctx)
        if (data == null) { note("Open M&C Money once and your spending will show here."); return }
        val m = model(data.first)
        TodayMarkets.addToHeader(head, RunUi.text(ctx, "This week ${money(m.weekTotal)}", GREEN, 13f, true))
        if (closed) {   // Collapsed: today and yesterday on one line.
            card.addView(RunUi.text(ctx, "Today ${m.amt(m.today)?.let { money(it) } ?: "—"}  ·  Yesterday ${m.amt(m.today.minusDays(1))?.let { money(it) } ?: "—"}", Color.WHITE, 12f, true).apply { setPadding(0, d(6), 0, 0) })
            return
        }

        // Today | Yesterday
        card.addView(LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, d(8), 0, 0)
            listOf("Today" to m.amt(m.today), "Yesterday" to m.amt(m.today.minusDays(1))).forEach { (label, v) ->
                addView(LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL
                    addView(RunUi.text(ctx, label, RunUi.MUTED, 11f, true))
                    addView(RunUi.text(ctx, v?.let { money(it) } ?: "—", Color.WHITE, 17f, true))
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
        })

        // Sunday → Saturday bars.
        card.addView(Bars(ctx, m.weekVals, m.week.indexOf(m.today)) { i -> showDay(act, m, m.week[i]) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, d(118)).apply { topMargin = d(10) })
        // Only this spot opens the bigger view; the rest of the card doesn't react to taps.
        card.addView(LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, d(8), 0, 0)
            addView(RunUi.text(ctx, "From M&C Money · all accounts · tap a day for its transactions", RunUi.DIM, 10f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(detailsButton(act))
        })
    }

    private fun detailsButton(act: Activity) = RunUi.text(act, "Details ›", Color.BLACK, 13f, true).apply {
        val d = { v: Int -> RunUi.dp(act, v) }
        background = RunUi.rounded(RunUi.ACCENT, d(16).toFloat()); setPadding(d(14), d(7), d(14), d(7))
        setOnClickListener { act.startActivity(android.content.Intent(act, SpendingActivity::class.java)) }
    }

    fun openMc(act: Activity) { act.packageManager.getLaunchIntentForPackage(PKG)?.let { try { act.startActivity(it) } catch (e: Exception) { } } }

    /** M&C Money's daily totals for the bigger view, or null when they can't be read. */
    fun load(ctx: Context): Model? = if (shares(ctx) && allowed(ctx)) read(ctx)?.let { model(it.first) } else null

    class Model(root: JSONObject, val today: LocalDate) {
        val days: JSONObject = root.optJSONObject("days") ?: JSONObject()
        /** Each day's transactions (merchant m, amount a, category c, account acct, person p); null with an older M&C Money. */
        val tx: JSONObject? = root.optJSONObject("tx")
        /** Monthly totals ("2026-11" → $), last 6 months with this month so far; null with an older M&C Money. */
        val months: JSONObject? = root.optJSONObject("months")
        // Spending can only be added inside M&C Money, and it sends new totals the moment you save, so a day after its
        // last send had nothing added: $0.
        private val lastDay = days.keys().asSequence().maxOrNull() ?: ""
        fun amt(day: LocalDate): Double? = when {
            day.isAfter(today) -> null
            days.has(day.toString()) -> days.optDouble(day.toString(), 0.0)
            day.toString() > lastDay -> 0.0
            else -> null
        }
        val sun: LocalDate = today.minusDays((today.dayOfWeek.value % 7).toLong())
        val week = (0..6).map { sun.plusDays(it.toLong()) }
        val lastWeek = week.map { it.minusDays(7) }
        val weekVals = week.map { amt(it) }
        val lastWeekVals = lastWeek.map { amt(it) }
        val weekTotal = weekVals.filterNotNull().sum()
        val lastWeekTotal = lastWeekVals.filterNotNull().sum()
    }
    private fun model(root: JSONObject) = Model(root, LocalDate.now())

    /** 🧾 The transactions behind one day's total, in a popup. */
    fun showDay(act: Activity, m: Model, day: LocalDate) {
        val d = { v: Int -> RunUi.dp(act, v) }
        val col = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(d(20), d(8), d(20), d(4)) }
        val total = m.amt(day)
        val arr = m.tx?.optJSONArray(day.toString())
        when {
            day.isAfter(m.today) -> col.addView(RunUi.text(act, "This day hasn't happened yet.", RunUi.MUTED, 13f))
            m.tx == null -> col.addView(RunUi.text(act, "Update M&C Money (Settings → Check for updates) to see each day's transactions here.", RunUi.MUTED, 13f))
            arr == null -> col.addView(RunUi.text(act, "M&C Money shares the last 14 days; this day is older.", RunUi.MUTED, 13f))
            arr.length() == 0 -> col.addView(RunUi.text(act, "No spending this day.", RunUi.MUTED, 13f))
            else -> for (i in 0 until arr.length()) { val t = arr.getJSONObject(i); val a = t.optDouble("a")
                col.addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, d(7), 0, d(7))
                    addView(LinearLayout(act).apply { orientation = LinearLayout.VERTICAL
                        addView(RunUi.text(act, t.optString("m"), Color.WHITE, 14f, true).apply { maxLines = 2 })
                        listOf(t.optString("c"), t.optString("acct"), t.optString("p")).filter { it.isNotBlank() }.joinToString(" · ").takeIf { it.isNotBlank() }
                            ?.let { addView(RunUi.text(act, it, RunUi.MUTED, 11f)) }
                    }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    addView(RunUi.text(act, if (a < 0) "−" + money(-a) + " refund" else money(a), if (a < 0) GREEN else Color.WHITE, 14f, true).apply { setPadding(d(10), 0, 0, 0) })
                })
                if (i < arr.length() - 1) col.addView(View(act).apply { setBackgroundColor(0x22FFFFFF) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
            }
        }
        val title = when (day) { m.today -> "Today"; m.today.minusDays(1) -> "Yesterday"
            else -> day.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMM d", java.util.Locale.US)) } + (total?.let { " · ${money(it)}" } ?: "")
        val b = android.app.AlertDialog.Builder(act, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(title)
            .setView(android.widget.ScrollView(act).apply { addView(col) })
            .setPositiveButton("Close", null)
        if (act !is SpendingActivity) b.setNeutralButton("Details ›") { _, _ -> act.startActivity(android.content.Intent(act, SpendingActivity::class.java)) }
        b.show()
    }

    /** Seven bars, S M T W T F S, amount over each bar, today highlighted. */
    class Bars(ctx: Context, val vals: List<Double?>, val todayIdx: Int, val big: Boolean = false, onDay: ((Int) -> Unit)? = null) : View(ctx) {
        private var downX = 0f
        init {
            if (onDay != null) {
                setOnTouchListener { _, e -> if (e.action == android.view.MotionEvent.ACTION_DOWN) downX = e.x; false }
                setOnClickListener { if (width > 0) onDay(((downX / width) * 7).toInt().coerceIn(0, 6)) }
                contentDescription = "Spending by day. Tap a day to see its transactions."
            }
        }
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val dp = ctx.resources.displayMetrics.density
        override fun onDraw(c: Canvas) {
            val labels = listOf("S", "M", "T", "W", "T", "F", "S")
            val max = (vals.filterNotNull().maxOrNull() ?: 0.0).coerceAtLeast(1.0)
            val slot = width / 7f; val bw = slot * 0.56f
            val k = if (big) 1.3f else 1f
            val labelH = 16 * dp * k; val topH = 16 * dp * k
            val chartH = height - labelH - topH
            p.textAlign = Paint.Align.CENTER
            for (i in 0..6) {
                val cx = slot * i + slot / 2
                val v = vals[i]
                val isToday = i == todayIdx
                // track
                p.color = 0x22FFFFFF
                c.drawRoundRect(RectF(cx - bw / 2, topH, cx + bw / 2, topH + chartH), 4 * dp, 4 * dp, p)
                if (v != null && v > 0) {
                    val h = (chartH * (v / max)).toFloat().coerceAtLeast(3 * dp)
                    p.color = if (isToday) RunUi.ACCENT else GREEN
                    c.drawRoundRect(RectF(cx - bw / 2, topH + chartH - h, cx + bw / 2, topH + chartH), 4 * dp, 4 * dp, p)
                    p.color = Color.WHITE; p.textSize = 9.5f * dp * k; p.isFakeBoldText = true
                    c.drawText(if (v >= 1000) String.format("%.1fk", v / 1000) else if (big) String.format("$%.0f", v) else String.format("%.0f", v), cx, topH + chartH - h - 3 * dp, p)
                }
                p.color = if (isToday) RunUi.ACCENT else RunUi.MUTED; p.textSize = 11 * dp * k; p.isFakeBoldText = isToday
                c.drawText(labels[i], cx, height - 3 * dp, p)
            }
        }
    }
}
