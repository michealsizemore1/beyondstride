package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.WebView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.Spinner
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * 📚 Library — the Command Center's Audiobook Library page: this week's Audiobook Time chart, 🎯 Finish an Audiobook by
 * Date (schedule + progress), 🎧 Today's Listening, sort / Expand / Collapse / + Add Book, and each book with its cover,
 * stars, notes, ✏️ Edit, ✕ Delete, 📝 Reading Notes (+ Add Note) and 📁 Documents.
 */
class LifeLibrary(private val act: Activity, private val web: WebView, private val openFull: () -> Unit, private val pick: (String, (android.net.Uri) -> Unit) -> Unit) {
    val view = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
    private fun d(v: Int) = RunUi.dp(act, v)
    private val BLUE = 0xFF8B9CF6.toInt(); private val GREEN = 0xFF27AE60.toInt()
    private var sort = "newest"
    private var planOpen = false
    private val expanded = HashSet<String>(); private val notesOpen = HashSet<String>()
    private var data: JSONObject? = null
    private var covers = JSONObject()
    private val box = LifeUi.box(act); private var st = ""
    private val what = LifeUi.field(act, "e.g. Atomic Habits") { v -> listen("gaAudiobookText", v) }
    private val time = LifeUi.field(act, "e.g. 45 min") { v -> listen("gaAudiobookTime", v) }
    private val body = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
    // Plan form
    private val pBook = LifeUi.field(act, "Enter the book title"); private val pAuthor = LifeUi.field(act, "Enter the author")
    private val pHours = LifeUi.field(act, "Hours").apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER }
    private val pMins = LifeUi.field(act, "Minutes").apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER }
    private val pStart = LifeUi.field(act, "Start date (yyyy-mm-dd)"); private val pEnd = LifeUi.field(act, "Finish by (yyyy-mm-dd)")

    private fun listen(id: String, v: String) = web.evaluateJavascript("window.ccNative&&ccNative.setListen(${JSONObject.quote(id)},${JSONObject.quote(v)})", null)
    private fun card() = RunUi.card(act).apply { layoutParams = LifeUi.lp(d(12)) }
    private fun label(t: String) = RunUi.text(act, t, BLUE, 12f, true).apply { setPadding(0, d(8), 0, d(3)) }

    fun load(fresh: Boolean = false) {
        if (fresh) web.evaluateJavascript("window.ccNative&&ccNative.bookCoversReset&&ccNative.bookCoversReset()", null)
        web.evaluateJavascript("window.ccNative&&ccNative.libraryPage?ccNative.libraryPage(${JSONObject.quote(sort)}):null") { r ->
            data = LifeUi.obj(r) ?: return@evaluateJavascript
            if (listOf(what, time, pBook, pAuthor, pHours, pMins, pStart, pEnd).any { it.hasFocus() }) return@evaluateJavascript   // don't pull the field out from under the keyboard
            draw(); loadCovers(3)
        }
    }
    private var coverGen = 0
    /** Covers come from the page's picture store a moment after asking; keep asking until it says none are still loading. */
    private fun loadCovers(tries: Int, gen: Int = ++coverGen) {
        if (gen != coverGen) return
        web.evaluateJavascript("window.ccNative&&ccNative.bookCovers?ccNative.bookCovers():'{}'") { r ->
            val c = LifeUi.obj(r) ?: JSONObject()
            val pending = c.optInt("__pending", 0)
            // Add or update only — a cover still loading in the page must not make one already shown disappear (removing is done by Remove Cover).
            var changed = false
            c.keys().forEach { k -> if (k == "__pending") return@forEach; val v = c.optString(k); if (v.isNotBlank() && covers.optString(k) != v) { covers.put(k, v); changed = true } }
            if (changed) draw()
            if ((pending > 0 && tries > -30) || tries > 1) LifeUi.ui.postDelayed({ loadCovers(tries - 1, gen) }, 600)
        }
    }

    private fun draw() {
        val j = data ?: return
        view.removeAllViews()
        // Audiobook Time — This Week
        j.optJSONObject("week")?.let { w ->
            val vals = w.optJSONArray("values") ?: JSONArray(); val labels = w.optJSONArray("labels") ?: JSONArray()
            val list = (0 until vals.length()).map { vals.optDouble(it, 0.0).takeIf { v -> v > 0 } }
            val logged = list.filterNotNull()
            view.addView(card().apply {
                addView(RunUi.text(act, "Audiobook Time — This Week", Color.WHITE, 15f, true))
                if (logged.isNotEmpty()) addView(LifeDashboard.Chart(act, listOf(list to GREEN), false, (0 until labels.length()).map { labels.optString(it) }), LinearLayout.LayoutParams(MATCH_PARENT, d(150)).apply { topMargin = d(8) })
                addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, d(6), 0, 0)
                    (0 until labels.length()).forEach { i -> addView(RunUi.text(act, labels.optString(i), RunUi.DIM, 10f).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)) } })
                addView(RunUi.text(act, if (logged.isEmpty()) "No data logged this week" else
                    "${logged.average().toInt()} min weekly average  •  ${logged.min().toInt()} to ${logged.max().toInt()} min range", RunUi.MUTED, 12f).apply { gravity = Gravity.CENTER; setPadding(0, d(6), 0, 0) }, LifeUi.lp())
            })
        }
        view.addView(RunUi.text(act, "📚 Audiobook Library", BLUE, 20f, true).apply { setPadding(0, d(18), 0, 0) })
        view.addView(planCard(j.optJSONObject("plan")))
        // 🎧 Today's Listening
        view.addView(card().apply {
            st = j.optString("state"); LifeUi.paintBox(box, st)
            box.setOnClickListener { st = LifeUi.CYCLE[(LifeUi.CYCLE.indexOf(st) + 1) % LifeUi.CYCLE.size]; LifeUi.paintBox(box, st)
                web.evaluateJavascript("window.ccNative&&ccNative.setCheck('gaAudiobook',${JSONObject.quote(st)})", null) }
            (box.parent as? android.view.ViewGroup)?.removeView(box)
            addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(box, LinearLayout.LayoutParams(d(28), d(28)))
                addView(View(act).apply { setBackgroundColor(BLUE) }, LinearLayout.LayoutParams(d(4), d(18)).apply { marginStart = d(10) })
                addView(RunUi.text(act, "🎧 TODAY'S LISTENING", BLUE, 13f, true).apply { letterSpacing = 0.08f; setPadding(d(8), 0, 0, 0) }) })
            LifeUi.setIfIdle(what, j.optString("text")); LifeUi.setIfIdle(time, j.optString("time"))
            listOf(what, time).forEach { (it.parent as? android.view.ViewGroup)?.removeView(it) }
            addView(label("What are you listening to?")); addView(what, LifeUi.lp())
            addView(label("Time listened today")); addView(time, LifeUi.lp())
            addView(RunUi.text(act, "30+ minutes checks it off.", RunUi.DIM, 10f).apply { setPadding(0, d(4), 0, 0) })
        })
        // Toolbar
        val books = j.optJSONArray("books") ?: JSONArray()
        view.addView(card().apply {
            addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                val opts = listOf("newest" to "📅 Newest", "oldest" to "📅 Oldest", "title" to "A→Z Title", "author" to "A→Z Author", "rating" to "⭐ Rating")
                addView(Spinner(act).apply {
                    adapter = ArrayAdapter(act, android.R.layout.simple_spinner_dropdown_item, opts.map { it.second })
                    setSelection(opts.indexOfFirst { it.first == sort }.coerceAtLeast(0), false)
                    onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { if (opts[pos].first != sort) { sort = opts[pos].first; load() } }
                        override fun onNothingSelected(p: AdapterView<*>?) {}
                    } })
                addView(RunUi.text(act, if (books.length() > 0) "${books.length()} book${if (books.length() == 1) "" else "s"}" else "", RunUi.MUTED, 12f).apply { setPadding(d(8), 0, 0, 0) }) })
            addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, d(8), 0, 0)
                fun b(t: String, c: Int, on: () -> Unit) = addView(LifeUi.pill(act, t, c, Color.WHITE, 13f, on).apply { setPadding(d(12), d(7), d(12), d(7)) }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = d(6) })
                b("⊞ Expand", GREEN) { for (i in 0 until books.length()) expanded.add(books.getJSONObject(i).optString("id")); draw() }
                b("⊟ Collapse", 0xFF6B7280.toInt()) { expanded.clear(); draw() }
                b("+ Add Book", 0xFF3B0764.toInt()) { bookDialog(null) } })
        })
        if (books.length() == 0) view.addView(RunUi.text(act, "No books yet — tap + Add Book to log your first completed audiobook.", RunUi.MUTED, 14f).apply { gravity = Gravity.CENTER; setPadding(d(20), d(30), d(20), d(30)) }, LifeUi.lp())
        for (i in 0 until books.length()) view.addView(bookCard(books.getJSONObject(i)))
    }

    private fun planCard(p: JSONObject?) = card().apply {
        addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(RunUi.text(act, "🎯 Finish an Audiobook by Date", 0xFFC7D2FE.toInt(), 15f, true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(RunUi.text(act, if (planOpen) "▼" else "▶", RunUi.MUTED, 13f))
            setOnClickListener { planOpen = !planOpen; draw() } })
        if (planOpen) {
            if (pStart.text.isEmpty()) pStart.setText(java.time.LocalDate.now().toString())
            if (pEnd.text.isEmpty()) pEnd.setText(java.time.LocalDate.now().plusDays(30).toString())
            if (p != null && pBook.text.isEmpty()) { pBook.setText(p.optString("title")); pAuthor.setText(p.optString("author")) }
            listOf(pBook, pAuthor, pHours, pMins, pStart, pEnd).forEach { (it.parent as? android.view.ViewGroup)?.removeView(it) }
            addView(label("Audiobook name")); addView(pBook, LifeUi.lp())
            addView(label("Author")); addView(pAuthor, LifeUi.lp())
            addView(label("Total audiobook length"))
            addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL
                addView(pHours, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = d(6) }); addView(pMins, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)) })
            addView(label("Start date")); addView(pStart, LifeUi.lp())
            addView(label("Finish by")); addView(pEnd, LifeUi.lp())
            addView(LifeUi.pill(act, "Create Schedule", 0xFF4F46E5.toInt()) {
                web.evaluateJavascript("window.ccNative&&ccNative.planCreate(${listOf(pBook, pAuthor, pHours, pMins, pStart, pEnd).joinToString(",") { JSONObject.quote(it.text.toString().trim()) }})") { r ->
                    val m = LifeUi.str(r); if (m == "ok") load() else AlertDialog.Builder(act).setMessage(m.ifBlank { "Couldn't create the schedule." }).setPositiveButton("OK", null).show() }
            }, LifeUi.lp(d(10)))
        }
        if (p != null) addView(LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; layoutParams = LifeUi.lp(d(10))
            background = RunUi.rounded(0xFF1E2238.toInt(), d(10).toFloat()); setPadding(d(12), d(10), d(12), d(10))
            addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL
                addView(LinearLayout(act).apply { orientation = LinearLayout.VERTICAL
                    addView(RunUi.text(act, p.optString("title"), 0xFFC7D2FE.toInt(), 15f, true))
                    if (p.optString("author").isNotBlank()) addView(RunUi.text(act, "by " + p.optString("author"), RunUi.MUTED, 12f))
                    addView(RunUi.text(act, "Total: ${p.optString("total")} • ${p.optInt("days")} day${if (p.optInt("days") == 1) "" else "s"}", RunUi.MUTED, 12f).apply { setPadding(0, d(4), 0, 0) })
                    addView(RunUi.text(act, "Listen ${p.optString("daily")} per day", 0xFFA5B4FC.toInt(), 14f, true).apply { setPadding(0, d(3), 0, 0) }) }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(RunUi.text(act, "✕", RunUi.MUTED, 14f).apply { setPadding(d(8), d(2), d(8), d(2)); background = RunUi.rounded(0xFF2A2F45.toInt(), d(5).toFloat())
                    setOnClickListener { AlertDialog.Builder(act).setMessage("Remove this audiobook completion plan?").setPositiveButton("Remove") { _, _ -> web.evaluateJavascript("window.ccNative&&ccNative.planDelete()") { load() } }.setNegativeButton("Keep", null).show() } }) })
            val col = try { Color.parseColor(p.optString("color")) } catch (e: Exception) { GREEN }
            addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, d(10), 0, d(6))
                addView(RunUi.text(act, p.optString("label"), col, 13f, true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(RunUi.text(act, "${p.optInt("pct")}% complete", RunUi.MUTED, 12f, true)) })
            val bar = FrameLayout(act).apply { background = RunUi.rounded(0xFF2E3550.toInt(), d(7).toFloat()) }
            val fill = View(act).apply { background = RunUi.rounded(col, d(7).toFloat()) }; val mark = View(act).apply { setBackgroundColor(0xFFE2E8F0.toInt()) }
            bar.addView(fill, FrameLayout.LayoutParams(0, d(13))); bar.addView(mark, FrameLayout.LayoutParams(d(2), d(13)))
            bar.post { fill.layoutParams = FrameLayout.LayoutParams(bar.width * p.optInt("pct") / 100, d(13)); mark.layoutParams = FrameLayout.LayoutParams(d(2), d(13)).apply { leftMargin = (bar.width * p.optInt("plannedPct") / 100 - d(1)).coerceAtLeast(0) }; bar.requestLayout() }
            addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, d(13)))
            addView(RunUi.text(act, "Listened: ${p.optString("listened")}   Planned by today: ${p.optString("planned")}   Remaining: ${p.optString("remaining")}", RunUi.MUTED, 11f).apply { setPadding(0, d(6), 0, d(6)) })
            val ms = p.optJSONArray("milestones") ?: JSONArray()
            for (i in 0 until ms.length()) { val m = ms.getJSONObject(i)
                addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, d(4), 0, d(4))
                    addView(RunUi.text(act, m.optString("l"), Color.WHITE, 12f), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)); addView(RunUi.text(act, "${m.optInt("pct")}%", Color.WHITE, 12f, true)) }) }
            addView(RunUi.text(act, "Schedule based on ${p.optString("source")}.", RunUi.DIM, 10f).apply { setPadding(0, d(6), 0, 0) })
        })
    }

    private fun bookCard(b: JSONObject): View {
        val id = b.optString("id"); val open = id in expanded
        val c = RunUi.card(act).apply { layoutParams = LifeUi.lp(d(10)); setPadding(d(14), d(12), d(14), d(12)) }
        c.addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            covers.optString(id).takeIf { it.startsWith("data:") || it.startsWith("http") }?.let { url ->
                addView(ImageView(act).apply { scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true
                    background = RunUi.rounded(0xFF26302C.toInt(), d(5).toFloat()); LifeUi.loadImage(url, this) }, LinearLayout.LayoutParams(d(44), d(60)).apply { marginEnd = d(10) }) }
            addView(LinearLayout(act).apply { orientation = LinearLayout.VERTICAL
                addView(RunUi.text(act, b.optString("title"), Color.WHITE, 15f, true))
                if (b.optString("author").isNotBlank()) addView(RunUi.text(act, "by " + b.optString("author"), RunUi.MUTED, 12f).apply { setTypeface(typeface, Typeface.ITALIC) })
                addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, d(3), 0, 0)
                    niceDate(b.optString("date"))?.let { addView(RunUi.text(act, "📅 $it", RunUi.MUTED, 12f).apply { setPadding(0, 0, d(10), 0) }) }
                    val r = b.optInt("rating"); if (r > 0) addView(RunUi.text(act, "★".repeat(r) + "☆".repeat(5 - r), 0xFFF59E0B.toInt(), 13f)) }) },
                LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(RunUi.text(act, if (open) "▼" else "▶", RunUi.DIM, 12f))
            setOnClickListener { if (open) expanded.remove(id) else expanded.add(id); draw() } })
        if (!open) return c
        c.addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, d(10), 0, d(4))
            fun b(t: String, bg: Int, fg: Int, on: () -> Unit) = addView(LifeUi.pill(act, t, bg, fg, 11f, on).apply { setPadding(d(10), d(5), d(10), d(5)) }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = d(6) })
            b("🖼️ Cover", 0xFF4F46E5.toInt(), Color.WHITE) { coverDialog(b) }
            b("✏️ Edit", 0xFF26302C.toInt(), Color.WHITE) { bookDialog(b) }
            b("✕ Delete", 0xFF3A1F22.toInt(), 0xFFF87171.toInt()) {
                AlertDialog.Builder(act).setMessage("Remove this book from your library?").setPositiveButton("Remove") { _, _ -> web.evaluateJavascript("window.ccNative&&ccNative.bookDelete(${JSONObject.quote(id)})") { load() } }.setNegativeButton("Keep", null).show() } })
        if (b.optString("notes").isNotBlank()) c.addView(RunUi.text(act, b.optString("notes"), 0xFFD1D5DB.toInt(), 13f).apply {
            background = RunUi.rounded(0xFF232B28.toInt(), d(8).toFloat()); setPadding(d(10), d(8), d(10), d(8)) }, LifeUi.lp(d(6)))
        val notes = b.optJSONArray("readingNotes") ?: JSONArray(); val nOpen = id in notesOpen
        c.addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, d(10), 0, 0)
            addView(RunUi.text(act, "📝 Reading Notes" + if (notes.length() > 0) " (${notes.length()})" else "", BLUE, 13f, true).apply { setOnClickListener { if (nOpen) notesOpen.remove(id) else notesOpen.add(id); draw() } }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(LifeUi.pill(act, "+ Add Note", 0xFF667EEA.toInt(), Color.WHITE, 11f) { noteDialog(id, -1, null) }.apply { setPadding(d(10), d(4), d(10), d(4)) }) })
        if (nOpen) for (i in 0 until notes.length()) { val n = notes.getJSONObject(i)
            c.addView(LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; layoutParams = LifeUi.lp(d(8))
                background = RunUi.rounded(0xFF232B28.toInt(), d(8).toFloat()); setPadding(d(10), d(8), d(10), d(8))
                addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    addView(RunUi.text(act, n.optString("date") + (n.optString("chapter").takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""), RunUi.MUTED, 11f, true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                    addView(RunUi.text(act, "✏️", BLUE, 13f).apply { setPadding(d(6), 0, d(8), 0); setOnClickListener { noteDialog(id, i, n) } })
                    addView(RunUi.text(act, "✕", RunUi.DIM, 13f).apply { setOnClickListener {
                        AlertDialog.Builder(act).setMessage("Delete this note?").setPositiveButton("Delete") { _, _ -> web.evaluateJavascript("window.ccNative&&ccNative.bookNoteDelete(${JSONObject.quote(id)},$i)") { load() } }.setNegativeButton("Keep", null).show() } }) })
                n.optString("quote").takeIf { it.isNotBlank() }?.let { addView(RunUi.text(act, "“$it”", 0xFFC7D2FE.toInt(), 13f).apply { setTypeface(typeface, Typeface.ITALIC); setPadding(0, d(4), 0, 0) }) }
                listOf("summary" to "Summary", "insights" to "💡 Key Insights", "application" to "🎯 Application", "questions" to "❓ Questions").forEach { (k, l) ->
                    n.optString(k).takeIf { it.isNotBlank() }?.let { addView(RunUi.text(act, l, BLUE, 11f, true).apply { setPadding(0, d(6), 0, 0) }); addView(RunUi.text(act, it, Color.WHITE, 13f)) } }
            })
        }
        val dOpen = id in docsOpen
        c.addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, d(10), 0, 0)
            addView(RunUi.text(act, "📁 Documents" + (docs[id]?.let { if (it.length() > 0) " (${it.length()})" else "" } ?: ""), GREEN, 13f, true).apply {
                setOnClickListener { if (dOpen) docsOpen.remove(id) else { docsOpen.add(id); loadDocs(id) }; draw() } }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(LifeUi.pill(act, "+ Upload", GREEN, Color.WHITE, 11f) { uploadDoc(id) }.apply { setPadding(d(10), d(4), d(10), d(4)) }) })
        if (dOpen) {
            val list = docs[id]
            if (list == null) c.addView(RunUi.text(act, "Loading…", RunUi.DIM, 12f).apply { setPadding(0, d(4), 0, 0) })
            else if (list.length() == 0) c.addView(RunUi.text(act, "No documents yet.", RunUi.DIM, 12f).apply { setPadding(0, d(4), 0, 0) })
            else for (i in 0 until list.length()) { val doc = list.getJSONObject(i)
                c.addView(LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutParams = LifeUi.lp(d(6))
                    background = RunUi.rounded(0xFF1E2A24.toInt(), d(8).toFloat()); setPadding(d(10), d(8), d(10), d(8))
                    addView(RunUi.text(act, "📄 " + doc.optString("name"), Color.WHITE, 13f).apply { setOnClickListener { web.evaluateJavascript("window.ccNative&&ccNative.docOpen(${doc.optLong("id")})", null) } }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                    addView(RunUi.text(act, size(doc.optLong("size")), RunUi.DIM, 11f).apply { setPadding(d(6), 0, d(10), 0) })
                    addView(RunUi.text(act, "✕", 0xFFF87171.toInt(), 14f).apply { setOnClickListener {
                        AlertDialog.Builder(act).setMessage("Delete \"${doc.optString("name")}\"?").setPositiveButton("Delete") { _, _ ->
                            web.evaluateJavascript("window.ccNative&&ccNative.docDelete(${doc.optLong("id")},${JSONObject.quote(id)})") { LifeUi.ui.postDelayed({ loadDocs(id) }, 400) } }.setNegativeButton("Keep", null).show() } }) })
            }
        }
        return c
    }
    private val docsOpen = HashSet<String>(); private val docs = HashMap<String, JSONArray>()
    private fun size(n: Long) = if (n >= 1_048_576) String.format(Locale.US, "%.1f MB", n / 1_048_576.0) else if (n >= 1024) "${n / 1024} KB" else "$n B"
    private fun loadDocs(id: String) {
        web.evaluateJavascript("window.ccNative&&ccNative.docsLoad(${JSONObject.quote(id)})") {
            LifeUi.poll(web, "ccNative.docsResult(${JSONObject.quote(id)})", 15) { r -> docs[id] = try { JSONArray(r ?: "[]") } catch (e: Exception) { JSONArray() }; draw() } }
    }
    private fun uploadDoc(id: String) = pick("*/*") { uri ->
        Thread {
            try {
                var name = "document"; var len = 0L
                act.contentResolver.query(uri, null, null, null, null)?.use { cu -> if (cu.moveToFirst()) {
                    cu.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = cu.getString(it) ?: name }
                    cu.getColumnIndex(android.provider.OpenableColumns.SIZE).takeIf { it >= 0 }?.let { len = cu.getLong(it) } } }
                if (len > 25L * 1024 * 1024) { LifeUi.ui.post { AlertDialog.Builder(act).setMessage("That file is over 25 MB — too big to keep here.").setPositiveButton("OK", null).show() }; return@Thread }
                val bytes = act.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                val type = act.contentResolver.getType(uri) ?: "application/octet-stream"
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                LifeUi.ui.post {
                    web.evaluateJavascript("ccNative.docStart()", null)
                    var i = 0; while (i < b64.length) { web.evaluateJavascript("ccNative.docPart('${b64.substring(i, minOf(b64.length, i + 500_000))}')", null); i += 500_000 }
                    web.evaluateJavascript("ccNative.docEnd(${JSONObject.quote(id)},${JSONObject.quote(name)},${JSONObject.quote(type)},${bytes.size})") { docsOpen.add(id); LifeUi.ui.postDelayed({ loadDocs(id) }, 600) }
                }
            } catch (e: Exception) { LifeUi.ui.post { AlertDialog.Builder(act).setMessage("Couldn't read that file.").setPositiveButton("OK", null).show() } }
        }.start()
    }
    private fun coverDialog(b: JSONObject) {
        val id = b.optString("id"); val has = covers.optString(id).isNotBlank()
        val opts = mutableListOf("🔍 Find Cover", "📷 Upload Cover"); if (has) opts += "Remove Cover"
        AlertDialog.Builder(act).setTitle(b.optString("title")).setItems(opts.toTypedArray()) { _, w ->
            when (opts[w]) {
                "🔍 Find Cover" -> findCover(b)
                "📷 Upload Cover" -> pick("image/*") { uri -> Thread { val url = LifeUi.photoDataUrl(act, uri)
                    LifeUi.ui.post { if (url == null) return@post; web.evaluateJavascript("window.ccNative&&ccNative.coverSet(${JSONObject.quote(id)},${JSONObject.quote(url)})") { covers.put(id, url); draw() } } }.start() }
                else -> AlertDialog.Builder(act).setMessage("Remove book cover?").setPositiveButton("Remove") { _, _ ->
                    web.evaluateJavascript("window.ccNative&&ccNative.coverDelete(${JSONObject.quote(id)})") { covers.remove(id); draw() } }.setNegativeButton("Keep", null).show()
            } }.show()
    }
    private fun findCover(b: JSONObject) {
        val id = b.optString("id")
        val grid = android.widget.GridLayout(act).apply { columnCount = 3; setPadding(d(14), d(10), d(14), d(4)) }
        val status = RunUi.text(act, "Searching…", RunUi.MUTED, 13f).apply { setPadding(d(20), d(12), d(20), 0) }
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; addView(status); addView(grid) }
        val dlg = AlertDialog.Builder(act).setTitle("🔍 Covers for ${b.optString("title")}").setView(android.widget.ScrollView(act).apply { addView(box) }).setNegativeButton("Close", null).show()
        web.evaluateJavascript("window.ccNative&&ccNative.coverSearch(${JSONObject.quote(id)})") {
            LifeUi.poll(web, "ccNative.coverResults(${JSONObject.quote(id)})", 30) { r ->
                val arr = try { JSONArray(r ?: "[]") } catch (e: Exception) { JSONArray() }
                status.text = if (arr.length() == 0) "No covers found." else "Tap a cover to use it."
                for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); val url = o.optString("url")
                    grid.addView(LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(d(4), d(4), d(4), d(4))
                        addView(ImageView(act).apply { scaleType = ImageView.ScaleType.CENTER_CROP; LifeUi.loadImage(url, this) }, LinearLayout.LayoutParams(d(70), d(96)))
                        addView(RunUi.text(act, o.optString("source"), RunUi.DIM, 10f).apply { gravity = Gravity.CENTER })
                        setOnClickListener { dlg.dismiss()
                            web.evaluateJavascript("window.ccNative&&ccNative.coverApply(${JSONObject.quote(id)},${JSONObject.quote(url)})") { covers.put(id, url); draw()
                                LifeUi.ui.postDelayed({ loadCovers(4) }, 1500) } } })
                }
            }
        }
    }
    private fun niceDate(s: String): String? = try { java.time.LocalDate.parse(s).format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)) } catch (e: Exception) { null }

    private fun dlgField(hint: String, v: String, lines: Int = 1) = EditText(act).apply { this.hint = hint; setText(v); if (lines > 1) { minLines = lines; gravity = Gravity.TOP or Gravity.START } else isSingleLine = true }
    private fun bookDialog(b: JSONObject?) {
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(d(20), d(8), d(20), 0) }
        val t = dlgField("Title", b?.optString("title") ?: ""); val a = dlgField("Author", b?.optString("author") ?: "")
        val dt = dlgField("Date completed (yyyy-mm-dd)", b?.optString("date") ?: java.time.LocalDate.now().toString())
        val stars = RatingBar(act).apply { numStars = 5; stepSize = 1f; rating = (b?.optInt("rating") ?: 0).toFloat() }
        val nt = dlgField("Notes / Takeaways", b?.optString("notes") ?: "", 3)
        box.addView(t); box.addView(a); box.addView(dt); box.addView(stars, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)); box.addView(nt)
        val sv = android.widget.ScrollView(act).apply { addView(box) }
        AlertDialog.Builder(act).setTitle(if (b == null) "Add Book" else "✏️ Editing: ${b.optString("title")}").setView(sv)
            .setPositiveButton(if (b == null) "Save Book" else "Save Changes") { _, _ ->
                if (t.text.isBlank()) return@setPositiveButton
                web.evaluateJavascript("window.ccNative&&ccNative.bookSave(${JSONObject.quote(b?.optString("id") ?: "")},${JSONObject.quote(t.text.toString())},${JSONObject.quote(a.text.toString())},${JSONObject.quote(dt.text.toString())},${stars.rating.toInt()},${JSONObject.quote(nt.text.toString())})") { load() } }
            .setNegativeButton("Cancel", null).show()
    }
    private fun noteDialog(bookId: String, idx: Int, n: JSONObject?) {
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(d(20), d(8), d(20), 0) }
        val fields = listOf("isoDate" to "Date (yyyy-mm-dd)", "chapter" to "Chapter / section", "quote" to "Quote", "summary" to "Summary", "insights" to "💡 Key insights", "application" to "🎯 Application", "questions" to "❓ Questions")
            .map { (k, h) -> k to dlgField(h, n?.optString(k) ?: if (k == "isoDate") java.time.LocalDate.now().toString() else "", if (k in setOf("summary", "insights", "application", "questions")) 2 else 1) }
        fields.forEach { box.addView(it.second) }
        AlertDialog.Builder(act).setTitle(if (idx < 0) "📝 Add Reading Note" else "📝 Edit Reading Note").setView(android.widget.ScrollView(act).apply { addView(box) })
            .setPositiveButton("Save Note") { _, _ ->
                val o = JSONObject(); fields.forEach { (k, e) -> o.put(k, e.text.toString()) }
                web.evaluateJavascript("window.ccNative&&ccNative.bookNote(${JSONObject.quote(bookId)},$idx,${JSONObject.quote(o.toString())})") { r ->
                    if (r != "true") AlertDialog.Builder(act).setMessage("Please fill in at least one field.").setPositiveButton("OK", null).show()
                    notesOpen.add(bookId); load() } }
            .setNegativeButton("Cancel", null).show()
    }
}
