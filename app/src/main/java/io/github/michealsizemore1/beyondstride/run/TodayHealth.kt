package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.widget.LinearLayout
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * ❤️ Health on the Today tab, from the Health Records app (Nov 27 2026): the next appointment (and how many questions
 * you've written down for it), refills coming due, labs outside their range and vaccines due. The app shares only
 * this short summary, and only with your permission; nothing here leaves the phone. Shown only when Health Records is
 * installed. Settings → System health can hide it.
 */
object TodayHealth {
    const val PKG = "com.sizemore.health"
    const val PERM = "com.sizemore.health.READ_SUMMARY"
    const val REQ = 89
    private val URI = Uri.parse("content://com.sizemore.health.summary/plan")

    fun installed(ctx: Context) = try { ctx.packageManager.getPackageInfo(PKG, 0); true } catch (e: Exception) { false }
    private fun shares(ctx: Context) = try { ctx.packageManager.getPermissionInfo(PERM, 0); true } catch (e: Exception) { false }
    private fun allowed(ctx: Context) = ctx.checkSelfPermission(PERM) == PackageManager.PERMISSION_GRANTED
    fun shown(ctx: Context) = ctx.getSharedPreferences("syshealth", 0).getBoolean("healthCard", true)
    fun setShown(ctx: Context, on: Boolean) = ctx.getSharedPreferences("syshealth", 0).edit().putBoolean("healthCard", on).apply()

    /** The app's latest summary and when it was shared, or null. */
    fun read(ctx: Context): Pair<JSONObject, Long>? = if (!shares(ctx) || !allowed(ctx)) null else try {
        ctx.contentResolver.query(URI, null, null, null, null)?.use { c ->
            if (!c.moveToFirst()) null else (c.getString(0) ?: "").takeIf { it.isNotEmpty() }?.let { JSONObject(it) to c.getLong(1) }
        }
    } catch (e: Exception) { null }

    private fun daysTo(s: String): Long? = try { java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(s)) } catch (e: Exception) { null }
    private fun rel(n: Long) = when { n < 0 -> "overdue"; n == 0L -> "today"; n == 1L -> "tomorrow"; else -> "in $n days" }
    private fun niceDate(s: String) = try { LocalDate.parse(s).format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)) } catch (e: Exception) { s }
    private fun niceTime(t: String) = try { java.time.LocalTime.parse(t).format(DateTimeFormatter.ofPattern("h:mm a", Locale.US)) } catch (e: Exception) { "" }

    fun fill(act: Activity, card: LinearLayout) {
        val ctx: Context = act
        card.removeAllViews()
        val d = { v: Int -> RunUi.dp(ctx, v) }
        val closed = TodayMarkets.collapsed(ctx, "health")
        val head = TodayMarkets.header(ctx, "❤️ Health", closed) { TodayMarkets.setCollapsed(ctx, "health", !closed); fill(act, card) }
        card.addView(head)
        fun note(s: String) = card.addView(RunUi.text(ctx, s, RunUi.MUTED, 12f).apply { setPadding(0, d(6), 0, 0) })
        if (!shares(ctx)) { note("Update Health Records (App → App updates) to see it here."); return }
        if (!allowed(ctx)) {
            note("Let BeyondStride see a short summary from Health Records (next appointment, refills due, labs to watch). It stays on this phone.")
            card.addView(RunUi.text(ctx, "Allow", Color.BLACK, 13f, true).apply {
                background = RunUi.rounded(RunUi.ACCENT, d(16).toFloat()); setPadding(d(14), d(7), d(14), d(7)); setOnClickListener { act.requestPermissions(arrayOf(PERM), REQ) } },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = d(8) })
            return
        }
        val j = read(ctx)?.first
        if (j == null) { note("Open Health Records once and your summary will show here."); return }
        val alerts = mutableListOf<Pair<String, Int>>()
        j.optJSONObject("nextVisit")?.let { v ->
            val n = daysTo(v.optString("date"))
            val q = v.optInt("questions")
            alerts += ("🩺 " + niceDate(v.optString("date")) + (niceTime(v.optString("time")).takeIf { it.isNotEmpty() }?.let { " · $it" } ?: "") +
                (v.optString("doctor").takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") + (n?.let { " (${rel(it)})" } ?: "") +
                (if (q > 0) " · $q question${if (q == 1) "" else "s"}" else "")) to (if (n != null && n <= 2) 0xFFFBBF24.toInt() else Color.WHITE)
        }
        j.optJSONArray("refills")?.let { a -> for (i in 0 until a.length()) { val r = a.getJSONObject(i); val n = daysTo(r.optString("due")) ?: continue
            if (n <= 10) alerts += "💊 ${r.optString("name")} refill ${rel(n)}" to (if (n < 0) 0xFFF87171.toInt() else if (n <= 7) 0xFFFBBF24.toInt() else Color.WHITE) } }
        j.optJSONArray("outOfRange")?.let { a -> for (i in 0 until a.length()) { val r = a.getJSONObject(i)
            alerts += "🧪 ${r.optString("name")} ${r.opt("value")} ${r.optString("unit")} (${r.optString("flag")})".replace("  ", " ") to 0xFFF87171.toInt() } }
        j.optJSONArray("vaccinesDue")?.let { a -> for (i in 0 until a.length()) { val r = a.getJSONObject(i); val n = daysTo(r.optString("due")) ?: continue
            alerts += "💉 ${r.optString("name")} ${rel(n)}" to (if (n < 0) 0xFFF87171.toInt() else 0xFFFBBF24.toInt()) } }
        if (alerts.isNotEmpty()) TodayMarkets.addToHeader(head, RunUi.text(ctx, "${alerts.size}", RunUi.ACCENT, 13f, true))
        if (closed) return
        if (alerts.isEmpty()) note("✅ Nothing coming up: no appointments, refills or labs to watch.")
        else alerts.forEach { (t, c) -> card.addView(RunUi.text(ctx, t, c, 13.5f).apply { setPadding(0, d(6), 0, 0) }) }
        card.setOnClickListener { try { act.packageManager.getLaunchIntentForPackage(PKG)?.let { act.startActivity(it) } } catch (e: Exception) { } }
    }
}
