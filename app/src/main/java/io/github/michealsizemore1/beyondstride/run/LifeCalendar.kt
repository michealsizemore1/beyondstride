package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.WebView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.time.LocalDate
import java.util.Locale

/**
 * 📅 Calendar card for the Life tab — the Command Center's calendar: each day's status (complete / partly / saved),
 * event dots, the chosen day's events (yours, training plan, habits, bills) and add / edit / delete for your own.
 */
class LifeCalendar(private val act: Activity, private val web: WebView) {
    val view = RunUi.card(act)
    private var month = LocalDate.now().withDayOfMonth(1)
    private var selected = LocalDate.now().toString()
    private val title = RunUi.text(act, "", Color.WHITE, 14f, true).apply { gravity = Gravity.CENTER }
    private val grid = GridLayout(act).apply { columnCount = 7 }
    private val dayLabel = RunUi.text(act, "", RunUi.ACCENT, 13f, true)
    private val list = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
    private fun d(v: Int) = RunUi.dp(act, v)
    private val REM = listOf(-1 to "No reminder", 0 to "At event time", 15 to "15 minutes before", 30 to "30 minutes before", 60 to "1 hour before", 1440 to "1 day before")

    init {
        view.addView(RunUi.text(act, "📅 Calendar", Color.WHITE, 16f, true))
        view.addView(LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, d(4), 0, d(4))
            addView(arrow("‹") { month = month.minusMonths(1); load() })
            addView(title, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(arrow("›") { month = month.plusMonths(1); load() })
        })
        view.addView(grid)
        view.addView(RunUi.text(act, "● complete   ● partly   ● saved   • event", RunUi.DIM, 10f).apply { setPadding(0, d(6), 0, 0) })
        view.addView(LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, d(10), 0, d(2))
            addView(dayLabel, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(RunUi.text(act, "+ Add Event", 0xFFA78BFA.toInt(), 13f, true).apply { setPadding(d(8), d(4), 0, d(4)); setOnClickListener { edit(null) } })
        })
        view.addView(list)
    }
    private fun arrow(t: String, on: () -> Unit) = RunUi.text(act, t, RunUi.ACCENT, 22f, true).apply { setPadding(d(14), 0, d(14), 0); setOnClickListener { on() } }
    private fun js(call: String, cb: (String) -> Unit) = web.evaluateJavascript("window.ccNative&&ccNative.calendar?$call:null") { r ->
        cb(try { JSONTokener(r).nextValue() as? String ?: "" } catch (e: Exception) { "" }) }

    fun load() {
        js("ccNative.calendar(${month.year},${month.monthValue - 1})") { r ->
            val j = try { JSONObject(r) } catch (e: Exception) { null } ?: run { view.visibility = View.GONE; return@js }
            view.visibility = View.VISIBLE
            title.text = j.optString("title"); grid.removeAllViews()
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { grid.addView(RunUi.text(act, it, RunUi.DIM, 11f, true).apply { gravity = Gravity.CENTER }, cell(d(20))) }
            repeat(j.optInt("lead")) { grid.addView(View(act), cell(d(40))) }
            val days = j.optJSONArray("days") ?: JSONArray(); val today = j.optString("today")
            for (i in 0 until days.length()) {
                val o = days.getJSONObject(i); val date = o.optString("date")
                val dot = when (o.optString("status")) { "complete" -> 0xFF22C55E; "partial" -> 0xFFA855F7; "saved" -> 0xFF9AA3A0; else -> 0 }.toInt()
                val box = LinearLayout(act).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                    if (date == selected) background = RunUi.rounded(0xFF4F46E5.toInt(), d(10).toFloat())
                    else if (date == today) background = RunUi.rounded(0xFF26302C.toInt(), d(10).toFloat())
                    addView(RunUi.text(act, o.optInt("d").toString(), Color.WHITE, 14f, date == today || date == selected).apply { gravity = Gravity.CENTER })
                    addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
                        if (dot != 0) addView(View(act).apply { background = RunUi.rounded(dot, d(3).toFloat()) }, LinearLayout.LayoutParams(d(6), d(6)).apply { marginEnd = d(2) })
                        if (o.optBoolean("event")) addView(View(act).apply { background = RunUi.rounded(0xFFF59E0B.toInt(), d(3).toFloat()) }, LinearLayout.LayoutParams(d(6), d(6)))
                    }, LinearLayout.LayoutParams(WRAP_CONTENT, d(8)))
                    setOnClickListener { selected = date; load() }
                }
                grid.addView(box, cell(d(44)))
            }
            loadDay()
        }
    }
    private fun cell(h: Int) = GridLayout.LayoutParams().apply {
        width = 0; height = h; columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); setMargins(d(1), d(1), d(1), d(1))
    }

    private fun loadDay() {
        val dt = LocalDate.parse(selected)
        dayLabel.text = dt.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US))
        js("ccNative.events(${JSONObject.quote(selected)})") { r ->
            val arr = try { JSONArray(r) } catch (e: Exception) { JSONArray() }
            list.removeAllViews()
            if (arr.length() == 0) { list.addView(RunUi.text(act, "No events.", RunUi.MUTED, 13f).apply { setPadding(0, d(4), 0, 0) }); return@js }
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i); val mine = e.optString("kind") == "mine"
                val badge = when (e.optString("kind")) { "training" -> "🏃 Training plan"; "habit" -> "🌱 Habit"; "money" -> "💵 Bill"; else -> "" }
                list.addView(LinearLayout(act).apply {
                    orientation = LinearLayout.HORIZONTAL; setPadding(d(10), d(8), d(10), d(8))
                    background = RunUi.rounded(0xFF26302C.toInt(), d(10).toFloat())
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = d(6) }
                    addView(RunUi.text(act, e.optString("timeLabel"), RunUi.ACCENT, 12f, true), LinearLayout.LayoutParams(d(70), WRAP_CONTENT))
                    addView(LinearLayout(act).apply { orientation = LinearLayout.VERTICAL
                        addView(RunUi.text(act, e.optString("title"), Color.WHITE, 14f, true))
                        e.optString("note").takeIf { it.isNotBlank() }?.let { addView(RunUi.text(act, it, RunUi.MUTED, 12f)) }
                        val rem = e.optInt("reminder", -1)
                        val sub = listOfNotNull(badge.takeIf { it.isNotEmpty() }, if (mine && rem >= 0) "🔔 " + (REM.firstOrNull { it.first == rem }?.second ?: "$rem min before") else null).joinToString(" · ")
                        if (sub.isNotEmpty()) addView(RunUi.text(act, sub, RunUi.DIM, 11f))
                    }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                    if (mine) setOnClickListener { edit(e) }
                })
            }
        }
    }

    /** Add (e == null) or edit one of your events. */
    private fun edit(e: JSONObject?) {
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(d(20), d(8), d(20), 0) }
        val t = EditText(act).apply { hint = "Event title"; setText(e?.optString("title") ?: "") }
        var time = e?.optString("time") ?: ""
        val timeBtn = RunUi.text(act, "", 0xFFA78BFA.toInt(), 15f, true).apply { setPadding(0, d(12), 0, d(12)) }
        fun showTime() { timeBtn.text = if (time.isEmpty()) "🕒 All day (tap to set a time)" else "🕒 " + LocalDateTimeFmt(time) }
        showTime()
        val note = EditText(act).apply { hint = "Note (optional)"; setText(e?.optString("note") ?: "") }
        val rem = Spinner(act).apply { adapter = ArrayAdapter(act, android.R.layout.simple_spinner_dropdown_item, REM.map { it.second })
            setSelection(REM.indexOfFirst { it.first == (e?.optInt("reminder", 60) ?: 60) }.coerceAtLeast(0)) }
        timeBtn.setOnClickListener {
            val (h, m) = if (time.isNotEmpty()) time.split(":").let { it[0].toInt() to it[1].toInt() } else 9 to 0
            TimePickerDialog(act, { _, hh, mm -> time = String.format(Locale.US, "%02d:%02d", hh, mm); showTime() }, h, m, false).apply {
                setButton(AlertDialog.BUTTON_NEUTRAL, "All day") { _, _ -> time = ""; showTime() }
            }.show()
        }
        box.addView(t); box.addView(timeBtn); box.addView(note)
        box.addView(RunUi.text(act, "Reminder (timed events)", Color.GRAY, 12f).apply { setPadding(0, d(10), 0, 0) }); box.addView(rem)
        val dlg = AlertDialog.Builder(act).setTitle((if (e == null) "New event · " else "Edit event · ") + LocalDate.parse(selected).format(java.time.format.DateTimeFormatter.ofPattern("MMM d", Locale.US)))
            .setView(box)
            .setPositiveButton(if (e == null) "Save" else "Update") { _, _ ->
                if (t.text.isBlank()) return@setPositiveButton
                val id = e?.optString("id") ?: ""
                web.evaluateJavascript("window.ccNative&&ccNative.saveEvent(${JSONObject.quote(id)},${JSONObject.quote(selected)},${JSONObject.quote(t.text.toString())},${JSONObject.quote(time)},${JSONObject.quote(note.text.toString())},${REM[rem.selectedItemPosition].first})") { load() }
            }
            .setNegativeButton("Cancel", null)
        if (e != null) dlg.setNeutralButton("Delete") { _, _ ->
            AlertDialog.Builder(act).setMessage("Delete \"${e.optString("title")}\"?").setPositiveButton("Delete") { _, _ ->
                web.evaluateJavascript("window.ccNative&&ccNative.deleteEvent(${JSONObject.quote(e.optString("id"))})") { load() } }.setNegativeButton("Keep", null).show()
        }
        dlg.show()
    }
    private fun LocalDateTimeFmt(t: String): String = try { java.time.LocalTime.parse(t).format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", Locale.US)) } catch (x: Exception) { t }
}
