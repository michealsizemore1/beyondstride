package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.Gravity
import android.widget.LinearLayout
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 📈 Markets on the Today tab: the stock indexes and futures from MSIZ Terminal (S&P 500, Dow, Nasdaq) and Bitcoin. Same numbers
 * as the Terminal: its market file is refreshed about every 15 minutes during market hours (prices ~15 min delayed).
 * Kept on the phone so the card shows instantly; tap to refresh, "Open MSIZ Terminal ›" for the full page.
 */
object TodayMarkets {
    private const val URL_DATA = "https://michealsizemore1.github.io/ChatGPT-The-Master-App/data/market-quotes.json"
    const val URL_TERMINAL = "https://michealsizemore1.github.io/ChatGPT-The-Master-App/msiz-terminal.html"
    private val INDEXES = listOf("^GSPC" to "S&P 500", "^DJI" to "Dow 30", "^IXIC" to "Nasdaq", "BTC-USD" to "₿ Bitcoin")
    private val FUTURES = listOf("ES=F" to "S&P", "YM=F" to "Dow", "NQ=F" to "Nasdaq")

    private fun p(ctx: Context) = ctx.getSharedPreferences("markets", 0)
    fun stale(ctx: Context) = System.currentTimeMillis() - p(ctx).getLong("at", 0) > 15 * 60_000L

    /** Download the latest numbers. True when they changed. */
    fun fetch(ctx: Context): Boolean = try {
        val c = (URL("$URL_DATA?t=${System.currentTimeMillis() / 60_000}").openConnection() as HttpURLConnection).apply { connectTimeout = 10_000; readTimeout = 15_000 }
        val txt = c.inputStream.bufferedReader().use { it.readText() }; c.disconnect()
        JSONObject(txt)   // make sure it's whole
        val changed = p(ctx).getString("json", "") != txt
        p(ctx).edit().putString("json", txt).putLong("at", System.currentTimeMillis()).apply()
        changed
    } catch (e: Exception) { false }

    private fun fmt(v: Double) = if (v >= 1000) String.format("%,.2f", v) else String.format("%.2f", v)

    fun fill(ctx: Context, card: LinearLayout) {
        card.removeAllViews()
        val d = { v: Int -> RunUi.dp(ctx, v) }
        val j = try { JSONObject(p(ctx).getString("json", "") ?: "") } catch (e: Exception) { null }
        card.addView(LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(RunUi.text(ctx, "📈 Markets", Color.WHITE, 16f, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(RunUi.text(ctx, "Open MSIZ Terminal ›", RunUi.ACCENT, 12f, true).apply {
                setPadding(d(8), d(4), 0, d(4))
                setOnClickListener { val u = Uri.parse(URL_TERMINAL)
                    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, u).setPackage("com.android.chrome")) } catch (e: Exception) { try { ctx.startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (e2: Exception) { } } }
            })
        })
        if (j == null) { card.addView(RunUi.text(ctx, "Loading market numbers…", RunUi.MUTED, 12f).apply { setPadding(0, d(6), 0, 0) }); return }
        val q = j.optJSONObject("quotes") ?: JSONObject(); val f = j.optJSONObject("futures") ?: JSONObject()
        fun color(pct: Double) = if (pct > 0) 0xFF4ADE80.toInt() else if (pct < 0) 0xFFF87171.toInt() else RunUi.MUTED
        fun arrow(pct: Double) = if (pct > 0) "▲" else if (pct < 0) "▼" else ""
        // The indexes and Bitcoin, two per row.
        INDEXES.chunked(2).forEach { row -> card.addView(LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, d(8), 0, 0)
            row.forEach { (sym, name) ->
                val o = q.optJSONObject(sym)
                addView(LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL
                    addView(RunUi.text(ctx, name, RunUi.MUTED, 11f, true))
                    if (o == null) addView(RunUi.text(ctx, "—", Color.WHITE, 15f, true))
                    else { val pct = o.optDouble("changesPercentage")
                        addView(RunUi.text(ctx, fmt(o.optDouble("price")), Color.WHITE, 15f, true))
                        addView(RunUi.text(ctx, "${arrow(pct)}${String.format("%.2f", Math.abs(pct))}%  ${if (o.optDouble("change") >= 0) "+" else ""}${fmt(o.optDouble("change"))}", color(pct), 11f, true)) }
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
        }) }
        // Futures, one line.
        val fl = FUTURES.mapNotNull { (sym, name) -> f.optJSONObject(sym)?.let { o -> Triple(name, o.optDouble("price"), o.optDouble("changesPercentage")) } }
        if (fl.isNotEmpty()) {
            val sb = android.text.SpannableStringBuilder("Futures  ")
            sb.setSpan(android.text.style.ForegroundColorSpan(RunUi.MUTED), 0, sb.length, 0)
            fl.forEachIndexed { i, (name, price, pct) ->
                if (i > 0) sb.append("  ·  ")
                sb.append("$name ${fmt(price)} ")
                val st = sb.length; sb.append("${arrow(pct)}${String.format("%.2f", Math.abs(pct))}%")
                sb.setSpan(android.text.style.ForegroundColorSpan(color(pct)), st, sb.length, 0)
            }
            card.addView(RunUi.text(ctx, "", Color.WHITE, 11f).apply { text = sb; setPadding(0, d(8), 0, 0) })
        }
        val at = try { java.time.OffsetDateTime.parse(j.optString("updatedAt")).toInstant().toEpochMilli() } catch (e: Exception) { 0L }
        card.addView(RunUi.text(ctx, (if (at > 0) "Market data " + android.text.format.DateUtils.getRelativeTimeSpanString(at, System.currentTimeMillis(), 60_000L) + " · " else "") + "about 15 min delayed (Bitcoin trades 24/7) · tap to refresh", RunUi.DIM, 10f).apply { setPadding(0, d(6), 0, 0) })
    }
}
