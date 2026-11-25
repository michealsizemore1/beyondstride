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
 * 📈 Markets on the Today tab: the stock indexes, holdings and futures from MSIZ Terminal (S&P 500, Dow, Nasdaq) and Bitcoin.
 * Same sources as the Terminal: live from Yahoo Finance (Bitcoin from CoinGecko), refreshed every minute.
 * Kept on the phone so the card shows instantly; re-checked every minute while on screen (like the Terminal), or tap.
 */
object TodayMarkets {
    private const val URL_DATA = "https://michealsizemore1.github.io/ChatGPT-The-Master-App/data/market-quotes.json"
    const val URL_TERMINAL = "https://michealsizemore1.github.io/ChatGPT-The-Master-App/msiz-terminal.html"
    private val INDEXES = listOf("^GSPC" to "S&P 500", "^DJI" to "Dow 30", "^IXIC" to "Nasdaq", "BTC-USD" to "₿ Bitcoin")
    private val HOLDINGS = listOf(
        Triple("IBIT", "iShares Bitcoin Trust", 0xFFFF8A3D.toInt()),
        Triple("QQQM", "Invesco Nasdaq 100 ETF", 0xFF3D8BFF.toInt()),
        Triple("SCHD", "Schwab US Dividend ETF", 0xFF2EE66B.toInt()),
        Triple("SGOV", "iShares 0-3M T-Bond ETF", 0xFFFFD43B.toInt()))
    private val FUTURES = listOf("ES=F" to "S&P", "YM=F" to "Dow", "NQ=F" to "Nasdaq")

    private fun p(ctx: Context) = ctx.getSharedPreferences("markets", 0)
    fun stale(ctx: Context) = System.currentTimeMillis() - p(ctx).getLong("at", 0) > 60_000L

    /**
     * Download the latest numbers. True when they changed. GitHub runs the website's scheduled market update only a few
     * times a day, so the snapshot file is just the starting point: every quote is then read live from Yahoo Finance
     * (Bitcoin from CoinGecko), exactly like MSIZ Terminal does every minute, so both show the same numbers.
     */
    fun fetch(ctx: Context): Boolean {
        val base = try { JSONObject(get("$URL_DATA?t=${System.currentTimeMillis() / 60_000}")) } catch (e: Exception) {
            try { JSONObject(p(ctx).getString("json", "") ?: "") } catch (e2: Exception) { JSONObject() } }
        val quotes = base.optJSONObject("quotes") ?: JSONObject().also { base.put("quotes", it) }
        val futures = base.optJSONObject("futures") ?: JSONObject().also { base.put("futures", it) }
        val syms = listOf("^GSPC", "^DJI", "^IXIC", "IBIT", "QQQM", "SCHD", "SGOV").map { it to quotes } + FUTURES.map { it.first to futures }
        val pool = java.util.concurrent.Executors.newFixedThreadPool(6)
        val jobs = syms.map { (sym, into) -> pool.submit(java.util.concurrent.Callable<Long> { yahoo(sym)?.let { (o, t) -> synchronized(base) { into.put(sym, merge(into.optJSONObject(sym), o)) }; t } ?: 0L }) }
        val btc = pool.submit(java.util.concurrent.Callable<Boolean> { bitcoin()?.let { o -> synchronized(base) { quotes.put("BTC-USD", merge(quotes.optJSONObject("BTC-USD"), o)) }; true } ?: false })
        val newest = jobs.maxOfOrNull { try { it.get(25, java.util.concurrent.TimeUnit.SECONDS) } catch (e: Exception) { 0L } } ?: 0L
        try { btc.get(25, java.util.concurrent.TimeUnit.SECONDS) } catch (e: Exception) { }
        pool.shutdownNow()
        if (newest > 0) base.put("liveAt", newest * 1000)
        if (base.length() == 0) return false
        val txt = base.toString()
        val changed = p(ctx).getString("json", "") != txt
        p(ctx).edit().putString("json", txt).putLong("at", System.currentTimeMillis()).apply()
        return changed
    }

    private fun get(url: String): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 10_000; readTimeout = 15_000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) BeyondStride"); setRequestProperty("Accept", "application/json") }
        try { if (c.responseCode !in 200..299) throw Exception("HTTP ${c.responseCode}"); return c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
    }
    private fun merge(old: JSONObject?, new: JSONObject) = JSONObject((old ?: JSONObject()).toString()).also { o -> new.keys().forEach { o.put(it, new.get(it)) } }
    private fun r4(v: Double) = Math.round(v * 10_000) / 10_000.0

    /** One quote from Yahoo (same request and math as the Terminal and the snapshot script): price, change, % and its time. */
    private fun yahoo(sym: String): Pair<JSONObject, Long>? {
        val path = "v8/finance/chart/" + java.net.URLEncoder.encode(sym, "UTF-8") + "?interval=1h&range=1d"
        for (host in listOf("query1", "query2")) try {
            val m = JSONObject(get("https://$host.finance.yahoo.com/$path")).getJSONObject("chart").getJSONArray("result").getJSONObject(0).getJSONObject("meta")
            val price = m.optDouble("regularMarketPrice"); val prev = m.optDouble("previousClose").takeIf { !it.isNaN() && it > 0 } ?: m.optDouble("chartPreviousClose")
            if (price.isNaN() || prev.isNaN() || prev <= 0) continue
            val ch = price - prev
            return JSONObject().put("price", r4(price)).put("prev", r4(prev)).put("change", r4(ch)).put("changesPercentage", r4(ch / prev * 100)) to m.optLong("regularMarketTime")
        } catch (e: Exception) { }
        return null
    }

    /** Bitcoin like the Terminal: CoinGecko's price and 24-hour change, Coinbase if that fails. */
    private fun bitcoin(): JSONObject? {
        fun out(price: Double, pct: Double) = (price / (1 + pct / 100)).let { prev -> JSONObject().put("price", price).put("prev", r4(prev)).put("change", r4(price - prev)).put("changesPercentage", r4(pct)) }
        try {
            val b = JSONObject(get("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd&include_24hr_change=true")).getJSONObject("bitcoin")
            val price = b.optDouble("usd"); if (!price.isNaN() && price > 0) return out(price, b.optDouble("usd_24h_change").takeIf { !it.isNaN() } ?: 0.0)
        } catch (e: Exception) { }
        return try {
            val d = JSONObject(get("https://api.exchange.coinbase.com/products/BTC-USD/stats"))
            val price = d.optString("last").toDouble(); val open = d.optString("open").toDoubleOrNull() ?: 0.0
            out(price, if (open > 0) (price - open) / open * 100 else 0.0)
        } catch (e: Exception) { null }
    }

    private fun fmt(v: Double) = if (v >= 1000) String.format("%,.2f", v) else String.format("%.2f", v)

    fun fill(ctx: Context, card: LinearLayout) {
        card.removeAllViews()
        val d = { v: Int -> RunUi.dp(ctx, v) }
        val j = try { JSONObject(p(ctx).getString("json", "") ?: "") } catch (e: Exception) { null }
        card.addView(LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(RunUi.text(ctx, "📈 Markets", Color.WHITE, 16f, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
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
        // 💼 Holdings: your ETFs from MSIZ Terminal (same feed, same colors). TSP funds aren't in the live feed.
        val hold = HOLDINGS.mapNotNull { (sym, name, col) -> q.optJSONObject(sym)?.let { o -> Triple(Triple(sym, name, col), o.optDouble("price"), o.optDouble("changesPercentage")) } }
        if (hold.isNotEmpty()) {
            card.addView(RunUi.text(ctx, "Holdings", RunUi.MUTED, 11f, true).apply { setPadding(0, d(12), 0, d(2)) })
            hold.forEach { (h, price, pct) -> val (sym, name, col) = h
                card.addView(LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, d(3), 0, d(3))
                    addView(RunUi.text(ctx, sym, col, 13f, true).apply { typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD) }, LinearLayout.LayoutParams(d(60), LinearLayout.LayoutParams.WRAP_CONTENT))
                    addView(RunUi.text(ctx, name, RunUi.MUTED, 12f).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    addView(RunUi.text(ctx, "${if (pct > 0) "+" else ""}${String.format("%.2f", pct)}%", color(pct), 12f, true).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(d(66), LinearLayout.LayoutParams.WRAP_CONTENT))
                    addView(RunUi.text(ctx, "$" + String.format("%.2f", price), Color.WHITE, 12f, true).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(d(72), LinearLayout.LayoutParams.WRAP_CONTENT))
                })
            }
        }
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
        val live = j.optLong("liveAt", 0L)
        val at = if (live > 0) live else try { java.time.OffsetDateTime.parse(j.optString("updatedAt")).toInstant().toEpochMilli() } catch (e: Exception) { 0L }
        val ago = if (at > 0) android.text.format.DateUtils.getRelativeTimeSpanString(at, System.currentTimeMillis(), 60_000L).toString() else ""
        card.addView(RunUi.text(ctx, (if (live > 0) "Live · last trade $ago" else if (at > 0) "Market data $ago" else "Market data") + " · refreshes every minute, like MSIZ Terminal (Bitcoin trades 24/7) · tap to refresh", RunUi.DIM, 10f).apply { setPadding(0, d(6), 0, 0) })
    }
}
