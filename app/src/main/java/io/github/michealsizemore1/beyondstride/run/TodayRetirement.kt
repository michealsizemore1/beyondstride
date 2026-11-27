package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.view.Gravity
import android.widget.LinearLayout
import org.json.JSONObject
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 🏖️ Retirement on the Today tab, from the Retirement Planner app: a countdown to your retirement date, plan confidence
 * (1,000 simulated market scenarios), total current assets, net worth and how long the money lasts. The planner shares
 * these headline numbers each time it recalculates. Also gives the plan's monthly household budget to Spending → Details.
 */
object TodayRetirement {
    const val PKG = "com.sizemore.retirement"
    const val PERM = "com.sizemore.retirement.READ_SUMMARY"
    const val REQ = 88
    private val URI = Uri.parse("content://com.sizemore.retirement.summary/plan")

    private fun installed(ctx: Context) = try { ctx.packageManager.getPackageInfo(PKG, 0); true } catch (e: Exception) { false }
    private fun shares(ctx: Context) = try { ctx.packageManager.getPermissionInfo(PERM, 0); true } catch (e: Exception) { false }
    private fun allowed(ctx: Context) = ctx.checkSelfPermission(PERM) == PackageManager.PERMISSION_GRANTED

    /** The planner's latest numbers and when they were shared, or null. */
    fun read(ctx: Context): Pair<JSONObject, Long>? = if (!shares(ctx) || !allowed(ctx)) null else try {
        ctx.contentResolver.query(URI, null, null, null, null)?.use { c ->
            if (!c.moveToFirst()) null else (c.getString(0) ?: "").takeIf { it.isNotEmpty() }?.let { JSONObject(it) to c.getLong(1) }
        }
    } catch (e: Exception) { null }

    /** The plan's household budget per month (today's dollars), for comparing with actual spending. 0 when not known. */
    fun plannedMonthly(ctx: Context): Int = read(ctx)?.first?.optInt("plannedMonthly", 0) ?: 0

    private fun money(v: Double) = when {
        Math.abs(v) >= 1_000_000 -> String.format(Locale.US, "$%.2fM", v / 1_000_000)
        Math.abs(v) >= 10_000 -> String.format(Locale.US, "$%,.0fK", v / 1000)
        else -> String.format(Locale.US, "$%,.0f", v)
    }

    fun fill(act: Activity, card: LinearLayout) {
        val ctx: Context = act
        card.removeAllViews()
        val d = { v: Int -> RunUi.dp(ctx, v) }
        val closed = TodayMarkets.collapsed(ctx, "retirement")
        val head = TodayMarkets.header(ctx, "🏖️ Retirement", closed) { TodayMarkets.setCollapsed(ctx, "retirement", !closed); fill(act, card) }
        card.addView(head)
        fun note(s: String) = card.addView(RunUi.text(ctx, s, RunUi.MUTED, 12f).apply { setPadding(0, d(6), 0, 0) })
        fun pill(label: String, onClick: () -> Unit) = RunUi.text(ctx, label, Color.BLACK, 13f, true).apply {
            background = RunUi.rounded(RunUi.ACCENT, d(16).toFloat()); setPadding(d(14), d(7), d(14), d(7)); setOnClickListener { onClick() } }
        if (!shares(ctx)) {
            note(if (installed(ctx)) "Update Retirement Planner (Settings → App updates) to see your plan here." else "Install Retirement Planner to see your plan here.")
            return
        }
        if (!allowed(ctx)) {
            note("Let BeyondStride see your retirement plan's headline numbers (date, confidence, assets, net worth).")
            card.addView(pill("Allow") { act.requestPermissions(arrayOf(PERM), REQ) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = d(8) })
            return
        }
        val j = read(ctx)?.first
        if (j == null) { note("Open Retirement Planner once and your plan will show here."); return }
        val conf = if (j.isNull("confidence")) null else j.optInt("confidence")
        val confColor = when { conf == null -> RunUi.MUTED; conf >= 80 -> 0xFF4ADE80.toInt(); conf >= 60 -> 0xFF86EFAC.toInt(); conf >= 40 -> 0xFFFBBF24.toInt(); else -> 0xFFF87171.toInt() }
        conf?.let { TodayMarkets.addToHeader(head, RunUi.text(ctx, "$it% · ${j.optString("label")}", confColor, 13f, true)) }

        // Countdown to the retirement date.
        val date = try { LocalDate.parse(j.optString("retireDate")) } catch (e: Exception) { null }
        val today = LocalDate.now()
        val countdown = when {
            date == null -> null
            !date.isAfter(today) -> "Retired 🎉"
            else -> Period.between(today, date).let { p -> listOfNotNull(
                p.years.takeIf { it > 0 }?.let { "$it yr${if (it == 1) "" else "s"}" },
                p.months.takeIf { it > 0 }?.let { "$it mo" },
                p.days.takeIf { it > 0 || (p.years == 0 && p.months == 0) }?.let { "$it day${if (it == 1) "" else "s"}" }).joinToString(" ") }
        }
        if (closed) {
            card.addView(RunUi.text(ctx, listOfNotNull(countdown?.let { if (date != null && date.isAfter(today)) "$it to go" else it },
                j.optDouble("netWorth").takeIf { !it.isNaN() }?.let { "Net worth ${money(it)}" }).joinToString("  ·  "), Color.WHITE, 12f, true).apply { setPadding(0, d(6), 0, 0) })
            return
        }
        if (countdown != null && date != null) {
            card.addView(RunUi.text(ctx, countdown, Color.WHITE, 22f, true).apply { setPadding(0, d(8), 0, 0) })
            val days = java.time.temporal.ChronoUnit.DAYS.between(today, date)
            card.addView(RunUi.text(ctx, (if (days > 0) "until ${date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US))}" + (j.optInt("retireAge").takeIf { it > 0 }?.let { " · age $it" } ?: "") + String.format(Locale.US, " · %,d days", days)
                else "since ${date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US))}"), RunUi.MUTED, 12f))
        } else note("Set your planned last working day in Retirement Planner (Personal) to see a countdown.")

        // Assets and net worth.
        card.addView(LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, d(10), 0, 0)
            listOf("Total assets" to j.optDouble("totalAssets"), "Net worth" to j.optDouble("netWorth")).forEach { (label, v) ->
                addView(LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL
                    addView(RunUi.text(ctx, label, RunUi.MUTED, 11f, true))
                    addView(RunUi.text(ctx, if (v.isNaN()) "—" else money(v), Color.WHITE, 17f, true))
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
        })
        val parts = listOf("TSP" to j.optDouble("tsp"), "Brokerage" to j.optDouble("brokerage"), "Cash" to j.optDouble("cash")).filter { !it.second.isNaN() && it.second > 0 }
        if (parts.isNotEmpty()) card.addView(RunUi.text(ctx, parts.joinToString("  ·  ") { "${it.first} ${money(it.second)}" }, RunUi.MUTED, 12f).apply { setPadding(0, d(4), 0, 0) })
        // How long the money lasts.
        val dep = j.optInt("depletionAge", 0); val life = j.optInt("lifeExpectancy", 0)
        if (dep > 0) card.addView(RunUi.text(ctx, "⚠️ Plan runs out of money at age $dep", 0xFFF87171.toInt(), 13f, true).apply { setPadding(0, d(8), 0, 0) })
        else if (life > 0) card.addView(RunUi.text(ctx, "✓ Money lasts through age $life", 0xFF4ADE80.toInt(), 13f, true).apply { setPadding(0, d(8), 0, 0) })

        card.addView(LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, d(8), 0, 0)
            addView(RunUi.text(ctx, "From Retirement Planner · plan confidence = 1,000 simulated markets", RunUi.DIM, 10f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(pill("Open ›") { act.packageManager.getLaunchIntentForPackage(PKG)?.let { try { act.startActivity(it) } catch (e: Exception) { } } })
        })
    }
}
