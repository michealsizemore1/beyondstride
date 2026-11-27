package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 🔍 Search everything in BeyondStride (Nov 27 2026). The Life journal is searched by the page (ccNative.search) and
 * passed in; this adds the rest of the app: Activities (titles, notes, injury reports, Fuel AI), food you logged,
 * 📝 My notes, the workout library and plan, and Coach AI conversations. Also builds the facts for 💬 Ask AI.
 */
object AppSearch {
    data class Hit(val date: String, val where: String, val text: String)

    private fun snip(v: String, q: String): String {
        val i = v.lowercase().indexOf(q); if (i < 0) return v.take(180)
        val a = maxOf(0, i - 60); val b = minOf(v.length, i + q.length + 120)
        return (if (a > 0) "…" else "") + v.substring(a, b).replace(Regex("\\s+"), " ") + (if (b < v.length) "…" else "")
    }

    /** Everything outside the journal that mentions [query] (case ignored), newest first. Call off the main thread. */
    fun search(ctx: Context, query: String, limit: Int = 200): List<Hit> {
        val q = query.lowercase().trim(); if (q.length < 2) return emptyList()
        val out = ArrayList<Hit>()
        fun check(date: String, where: String, v: String?) { if (!v.isNullOrBlank() && v.lowercase().contains(q)) out += Hit(date, where, snip(v, q)) }
        // 🏃 Activities.
        try { ActivityStore.list(ctx).forEach { a ->
            val d = ActivityStore.str(a, "date").take(10); val t = ActivityStore.str(a, "title").ifEmpty { ActivityStore.str(a, "type") }
            check(d, "Activity", t)
            check(d, "Activity notes · $t", ActivityStore.str(a, "notes"))
            check(d, "Injury report · $t", ActivityStore.str(a, "injuryReport"))
            check(d, "Fuel AI · $t", ActivityStore.str(a, "fuelAi"))
        } } catch (e: Exception) { }
        // 🍎 Food.
        try { NutriDay.allJson(ctx).let { all -> io.github.michealsizemore1.beyondstride.Nutrition.parse(all.toString()) }.forEach { d ->
            val names = d.foods.filter { it.name.lowercase().contains(q) }.map { it.name }.distinct()
            if (names.isNotEmpty()) out += Hit(d.date, "Food", names.joinToString(", "))
        } } catch (e: Exception) { }
        // 📝 My notes (Today).
        try { check("", "My notes", ctx.getSharedPreferences("mynotes", 0).getString("text", "")) } catch (e: Exception) { }
        // 🏋️ Workouts and the plan.
        try { WorkoutStore.library(ctx).forEach { w -> check("", "Workout", w.optString("name") + " — " + w.optString("notes")) } } catch (e: Exception) { }
        try { val plan = WorkoutStore.plan(ctx); plan.keys().forEach { k -> val v = plan.opt(k)
            val txt = when (v) { is JSONObject -> v.optString("name") + " " + v.optString("notes"); is JSONArray -> v.toString(); else -> v?.toString() ?: "" }
            check(if (Regex("\\d{4}-\\d\\d-\\d\\d").matches(k)) k else "", "Plan", txt) } } catch (e: Exception) { }
        // 🧠 Coach AI conversations.
        try { val chat = CoachAi.chat(ctx); for (i in 0 until chat.length()) { val m = chat.optJSONObject(i) ?: continue
            val text = m.optString("show").ifEmpty { m.opt("content") as? String ?: m.optString("text") }
            check(m.optString("date").take(10), if (m.optString("role") == "user") "Coach AI · you asked" else "Coach AI · answer", text) } } catch (e: Exception) { }
        return out.sortedByDescending { it.date }.take(limit)
    }

    /** Words worth searching for in a question (drops short and common words). */
    fun keywords(question: String): List<String> {
        val stop = setOf("what", "when", "where", "which", "while", "with", "have", "has", "had", "does", "did", "the", "and", "for", "that", "this", "from",
            "your", "about", "been", "were", "was", "are", "how", "many", "much", "last", "time", "times", "ever", "most", "any", "all", "can", "could",
            "would", "should", "tell", "show", "give", "list", "into", "than", "then", "them", "they", "there", "their", "just", "like", "make", "made")
        return Regex("[A-Za-z0-9']{3,}").findAll(question.lowercase()).map { it.value.trim('\'') }.filter { it.length >= 3 && it !in stop }.distinct().take(8).toList()
    }

    /**
     * 💬 Ask AI about everything: the question, Coach AI's facts (runs, plan, sleep, food, weight… as it would pick), the
     * app's matches for the question's words and the journal matches the page found ([journal]). Uses BeyondStride's own
     * AI key. Returns the answer. Call off the main thread.
     */
    fun ask(ctx: Context, question: String, journal: List<Hit>): String {
        val words = keywords(question)
        val hits = words.flatMap { search(ctx, it, 40) }.distinct().take(120)
        val sb = StringBuilder()
        sb.append("You are the assistant inside BeyondStride, the owner's personal app for running, health, food, faith, reading and daily life.\n")
        sb.append("Answer the question using ONLY the data below. Quote dates and numbers. If the data doesn't answer it, say what's missing.\n")
        sb.append("Keep it readable on a phone: short sections with bold headings and bullets, no tables.\n\nQUESTION: $question\n")
        if (journal.isNotEmpty()) { sb.append("\n## Life journal matches (journal, daily reviews, Bible notes, priorities…)\n")
            journal.take(80).forEach { sb.append("- ${it.date} · ${it.where}: ${it.text}\n") } }
        if (hits.isNotEmpty()) { sb.append("\n## App matches (activities, food, notes, workouts, Coach AI)\n")
            hits.forEach { sb.append("- ${it.date.ifEmpty { "—" }} · ${it.where}: ${it.text}\n") } }
        sb.append("\n## Training, health and nutrition data\n").append(try { CoachAi.context(ctx, question) } catch (e: Exception) { "" })
        return AiFood.askText(ctx, sb.toString(), 3000)
    }
}
