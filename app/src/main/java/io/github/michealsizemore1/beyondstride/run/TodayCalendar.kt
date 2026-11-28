package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.LinearLayout
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 📅 Calendar on the Today tab (Nov 27 2026, in place of the Health card): what's coming up on the Life tab's Daily
 * calendar — your events, money dates and the rest — but not the training plan (workouts have their own cards).
 * The Life tab hands the phone the next two weeks whenever the calendar changes (see daily-planner-native.js
 * sendUpcoming). Tap the card to open Life → Daily. Settings → System health can hide it.
 */
object TodayCalendar {
    private fun p(ctx: Context) = ctx.getSharedPreferences("ccupcoming", 0)
    fun save(ctx: Context, json: String) { p(ctx).edit().putString("json", json).apply() }
    fun shown(ctx: Context) = ctx.getSharedPreferences("syshealth", 0).getBoolean("calendarCard", true)
    fun setShown(ctx: Context, on: Boolean) = ctx.getSharedPreferences("syshealth", 0).edit().putBoolean("calendarCard", on).apply()

    private fun dayLabel(d: LocalDate): String = when (d) {
        LocalDate.now() -> "Today"; LocalDate.now().plusDays(1) -> "Tomorrow"
        else -> d.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)) }
    private fun timeLabel(t: String) = if (t.isEmpty()) "All day" else try { LocalTime.parse(t).format(DateTimeFormatter.ofPattern("h:mm a", Locale.US)) } catch (e: Exception) { t }

    fun fill(act: Activity, card: LinearLayout) {
        val ctx: Context = act
        card.removeAllViews()
        val d = { v: Int -> RunUi.dp(ctx, v) }
        val closed = TodayMarkets.collapsed(ctx, "calendar")
        val head = TodayMarkets.header(ctx, "📅 Calendar", closed) { TodayMarkets.setCollapsed(ctx, "calendar", !closed); fill(act, card) }
        card.addView(head)
        card.setOnClickListener { try { act.startActivity(Intent(act, LifeActivity::class.java)) } catch (e: Exception) { } }
        val j = try { JSONObject(p(ctx).getString("json", null) ?: "") } catch (e: Exception) { null }
        if (j == null) { card.addView(RunUi.text(ctx, "Open the Life tab once and your calendar shows here.", RunUi.MUTED, 12f).apply { setPadding(0, d(6), 0, 0) }); return }
        val today = LocalDate.now(); val until = today.plusDays(7)
        val now = LocalTime.now()
        val items = mutableListOf<Triple<LocalDate, String, JSONObject>>()
        j.optJSONArray("events")?.let { a -> for (i in 0 until a.length()) { val e = a.optJSONObject(i) ?: continue
            if (e.optString("kind") == "training") continue
            val date = try { LocalDate.parse(e.optString("date")) } catch (x: Exception) { continue }
            if (date.isBefore(today) || date.isAfter(until)) continue
            val t = e.optString("time")
            // Today's timed events that ended over an hour ago drop off.
            if (date == today && t.isNotEmpty()) try { if (LocalTime.parse(t).plusHours(1).isBefore(now)) continue } catch (x: Exception) { }
            items += Triple(date, t, e) } }
        val todayCount = items.count { it.first == today }
        if (todayCount > 0) TodayMarkets.addToHeader(head, RunUi.text(ctx, "$todayCount today", RunUi.ACCENT, 13f, true))
        else if (items.isNotEmpty()) TodayMarkets.addToHeader(head, RunUi.text(ctx, "${items.size}", RunUi.ACCENT, 13f, true))
        if (closed) return
        if (items.isEmpty()) { card.addView(RunUi.text(ctx, "Nothing on the calendar this week.", RunUi.MUTED, 12.5f).apply { setPadding(0, d(6), 0, 0) }); return }
        items.take(5).forEach { (date, t, e) ->
            val icon = when (e.optString("kind")) { "money" -> "💵"; "habit" -> "🌱"; else -> "🗓" }
            val color = if (date == today) Color.WHITE else 0xFFD4D4D8.toInt()
            card.addView(RunUi.text(ctx, "$icon ${dayLabel(date)} · ${timeLabel(t)} · ${e.optString("title")}", color, 13.5f, date == today).apply { setPadding(0, d(6), 0, 0) })
        }
        if (items.size > 5) card.addView(RunUi.text(ctx, "+${items.size - 5} more this week", RunUi.MUTED, 12f).apply { setPadding(0, d(4), 0, 0) })
    }
}
