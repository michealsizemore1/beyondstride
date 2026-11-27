package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.widget.LinearLayout
import org.json.JSONObject

/**
 * 📅 Coming up on the Today tab (Nov 27 2026): renewals, birthdays and document expirations from Dates & Docs, and
 * vehicle services and other upkeep from Vehicle Upkeep, in one short list. Each app shares only "what's coming up",
 * and only with your permission. Shown when either app is installed; Settings → System health can hide it.
 */
object TodayComingUp {
    const val REQ = 90
    class Src(val pkg: String, val perm: String, val uri: Uri, val name: String, val icon: String)
    val SOURCES = listOf(
        Src("com.sizemore.dates", "com.sizemore.dates.READ_SUMMARY", Uri.parse("content://com.sizemore.dates.summary/plan"), "Dates & Docs", "📅"),
        Src("com.sizemore.upkeep", "com.sizemore.upkeep.READ_SUMMARY", Uri.parse("content://com.sizemore.upkeep.summary/plan"), "Vehicle Upkeep", "🔧"))

    fun installed(ctx: Context, s: Src) = try { ctx.packageManager.getPackageInfo(s.pkg, 0); true } catch (e: Exception) { false }
    private fun shares(ctx: Context, s: Src) = try { ctx.packageManager.getPermissionInfo(s.perm, 0); true } catch (e: Exception) { false }
    private fun allowed(ctx: Context, s: Src) = ctx.checkSelfPermission(s.perm) == PackageManager.PERMISSION_GRANTED
    fun anyInstalled(ctx: Context) = SOURCES.any { installed(ctx, it) }
    fun shown(ctx: Context) = ctx.getSharedPreferences("syshealth", 0).getBoolean("comingUpCard", true)
    fun setShown(ctx: Context, on: Boolean) = ctx.getSharedPreferences("syshealth", 0).edit().putBoolean("comingUpCard", on).apply()

    fun read(ctx: Context, s: Src): Pair<JSONObject, Long>? = if (!shares(ctx, s) || !allowed(ctx, s)) null else try {
        ctx.contentResolver.query(s.uri, null, null, null, null)?.use { c ->
            if (!c.moveToFirst()) null else (c.getString(0) ?: "").takeIf { it.isNotEmpty() }?.let { JSONObject(it) to c.getLong(1) }
        }
    } catch (e: Exception) { null }

    private fun rel(n: Int) = when { n < 0 -> "overdue"; n == 0 -> "today"; n == 1 -> "tomorrow"; n < 60 -> "in $n days"; else -> "in ${Math.round(n / 30.4)} months" }

    fun fill(act: Activity, card: LinearLayout) {
        val ctx: Context = act
        card.removeAllViews()
        val d = { v: Int -> RunUi.dp(ctx, v) }
        val closed = TodayMarkets.collapsed(ctx, "comingup")
        val head = TodayMarkets.header(ctx, "📅 Coming up", closed) { TodayMarkets.setCollapsed(ctx, "comingup", !closed); fill(act, card) }
        card.addView(head)
        val inst = SOURCES.filter { installed(ctx, it) }
        val needAllow = inst.filter { shares(ctx, it) && !allowed(ctx, it) }
        val items = mutableListOf<Triple<Int, String, Int>>()   // (days, text, color)
        inst.forEach { s -> read(ctx, s)?.first?.optJSONArray("due")?.let { a -> for (i in 0 until a.length()) { val o = a.getJSONObject(i)
            val days = if (o.isNull("days")) 9999 else o.optInt("days", 9999)
            val miles = if (o.isNull("milesLeft")) null else o.optInt("milesLeft")
            val whenTxt = when { miles != null && (days == 9999 || miles <= 500) -> if (miles < 0) "${-miles} mi overdue" else "$miles mi left"; days == 9999 -> ""; else -> rel(days) }
            val color = if (days < 0 || (miles != null && miles < 0)) 0xFFF87171.toInt() else if (days <= 14 || (miles != null && miles <= 500)) 0xFFFBBF24.toInt() else Color.WHITE
            items += Triple(minOf(days, if (miles != null) miles / 30 else 9999), "${s.icon} ${o.optString("title")}" + (if (whenTxt.isNotEmpty()) " · $whenTxt" else ""), color) } } }
        if (items.isNotEmpty()) TodayMarkets.addToHeader(head, RunUi.text(ctx, "${items.size}", RunUi.ACCENT, 13f, true))
        if (closed) return
        if (needAllow.isNotEmpty()) {
            card.addView(RunUi.text(ctx, "Let BeyondStride see what's coming up in ${needAllow.joinToString(" and ") { it.name }}. It stays on this phone.", RunUi.MUTED, 12f).apply { setPadding(0, d(6), 0, 0) })
            card.addView(RunUi.text(ctx, "Allow", Color.BLACK, 13f, true).apply {
                background = RunUi.rounded(RunUi.ACCENT, d(16).toFloat()); setPadding(d(14), d(7), d(14), d(7))
                setOnClickListener { act.requestPermissions(needAllow.map { it.perm }.toTypedArray(), REQ) } },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = d(8) })
        }
        if (items.isEmpty() && needAllow.isEmpty()) card.addView(RunUi.text(ctx, "✅ Nothing coming up: no renewals, dates or services due soon.", RunUi.MUTED, 12f).apply { setPadding(0, d(6), 0, 0) })
        items.sortedBy { it.first }.take(6).forEach { (_, t, c) -> card.addView(RunUi.text(ctx, t, c, 13.5f).apply { setPadding(0, d(6), 0, 0) }) }
        inst.filter { !shares(ctx, it) }.forEach { s -> card.addView(RunUi.text(ctx, "Update ${s.name} to see it here.", RunUi.MUTED, 12f).apply { setPadding(0, d(6), 0, 0) }) }
    }
}
