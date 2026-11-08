package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import io.github.michealsizemore1.beyondstride.Hydration
import io.github.michealsizemore1.beyondstride.Nutrition
import io.github.michealsizemore1.beyondstride.NutritionStore
import io.github.michealsizemore1.beyondstride.Readiness
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stand-alone BeyondStride: every change made here (or on the watch) is saved straight into BeyondStride's own data
 * instead of being sent to the Command Center. The screens keep working the same way — a change shows at once and is
 * then folded into the saved data here — and the cloud copy is refreshed a few minutes later.
 */
object Local {
    /** Folds every waiting change into BeyondStride's own data. */
    fun commit(ctx: Context) {
        if (!Standalone.on(ctx)) return
        var changed = false
        synchronized(Hydration) {
            val ch = Hydration.changes(ctx)
            val dates = Hydration.localDates(ctx)
            if (ch.isEmpty() && dates.isEmpty()) return
            // Activities: the list, settings, goals, shoes and weekly notes as shown (with the changes) become the saved copy.
            if (ch.any { it.f in listOf("actSave", "actDel", "actSettings", "shoeSave", "shoeDel", "wrnSave") }) {
                val o = ActivityStore.rawJson(ctx)
                o.put("list", JSONArray().apply { ActivityStore.list(ctx).forEach { put(it) } })
                o.put("settings", ActivityStore.settings(ctx))
                val goals = o.optJSONObject("goals") ?: JSONObject()
                listOf("week", "month", "year").forEach { goals.put(it, ActivityStore.goal(ctx, it)) }
                o.put("goals", goals)
                o.put("shoes", JSONArray().apply { ActivityStore.shoes(ctx).forEach { put(it) } })
                o.put("weekNotes", JSONObject().apply { ActivityStore.weekNotes(ctx).forEach { (k, v) -> put(k, v) } })
                o.remove("extras")
                ActivityStore.setRaw(ctx, o)
                // Training zones also go to the run settings (the watch uses those).
                ch.filter { it.f == "actSettings" }.lastOrNull()?.let { RunSettings.pushToWatch(ctx) }
            }
            // ⭐ Favorites (photos, saved meals, deleted foods and calorie settings were already saved here).
            ch.filter { it.f == "favAdd" }.forEach { c ->
                val x = try { JSONObject(c.x ?: "{}") } catch (e: Exception) { return@forEach }
                val l = FoodLibrary.libJson(ctx); val a = l.optJSONArray("favorites") ?: JSONArray()
                val name = x.optString("name").lowercase().trim()
                if ((0 until a.length()).none { a.optJSONObject(it)?.optString("name")?.lowercase()?.trim() == name }) {
                    val n = JSONArray().put(x); for (i in 0 until a.length()) n.put(a.get(i)); l.put("favorites", n); FoodLibrary.setLib(ctx, l)
                }
            }
            // A CSV sent before the switch: import it here.
            ch.filter { it.f == "actImport" }.forEach { c ->
                val x = try { JSONObject(c.x ?: "{}") } catch (e: Exception) { return@forEach }
                ActivityStore.setNotice(ctx, ActivityImport.importCsv(ctx, x.optString("csv"), x.optString("hint")))
            }
            // Days: foods, fluids, weights, meal notes and macro ratios.
            val touched = LinkedHashSet<String>(dates)
            ch.forEach { c -> if (c.f in Hydration.FLUIDS || c.f == "weight" || c.f == "postWeight" || c.f.startsWith("food") || c.f == "mealNote" || c.f == "macroRatio") touched += c.date }
            ch.filter { it.f == "macroRatio" }.forEach { c -> try { NutritionEngine.setRatio(ctx, c.date, JSONObject(c.x ?: "{}")) } catch (e: Exception) { } }
            val days = ArrayList<Nutrition.Day>()
            touched.forEach { date ->
                val base = NutriDay.rawHistory(ctx, date) ?: NutritionStore.days(ctx).lastOrNull { it.date == date }
                var d = Hydration.overlay(ctx, listOfNotNull(base)).lastOrNull { it.date == date } ?: base ?: Hydration.blank(date)
                val notes = HashMap(d.notes)
                ch.filter { it.f == "mealNote" && it.date == date }.forEach { c -> try { JSONObject(c.x ?: "{}").let { n -> val t = n.optString("text"); if (t.isBlank()) notes.remove(n.optString("meal")) else notes[n.optString("meal")] = t } } catch (e: Exception) { } }
                d = d.copy(notes = notes)
                days += NutritionEngine.fill(ctx, d)
                // Healthy 7+ foods for Pre-Run Meal Ideas (what the Command Center collected as you logged).
                try { val (run, hard) = NutritionEngine.runContext(ctx, date); NutritionEngine.captureHealthy(ctx, d.foods, run, hard) } catch (e: Exception) { }
            }
            if (days.isNotEmpty()) { NutriDay.putAll(ctx, days); changed = true }
            ch.filter { it.f == "libAction" }.lastOrNull()?.let { ActivityStore.setNotice(ctx, "Food Database tools (enrich, rescan…) ran in the Command Center — they aren't part of stand-alone BeyondStride.") }
            // Only the changes handled here (an import above may have added new ones; they're folded in next time).
            val done = ch.map { "${it.at}|${it.f}|${it.date}" }.toSet()
            Hydration.saveChanges(ctx, Hydration.changes(ctx).filter { "${it.at}|${it.f}|${it.date}" !in done })
            Hydration.clearLocal(ctx, dates)
            CloudCopy.dirty(ctx)
        }
        if (changed) storeFromHistory(ctx)
    }

    /** Recomputes one day's targets and notes (after Change Ratio). */
    fun refill(ctx: Context, date: String) {
        val d = NutriDay.rawHistory(ctx, date) ?: Hydration.blank(date)
        NutriDay.putDay(ctx, NutritionEngine.fill(ctx, d)); storeFromHistory(ctx)
    }

    /** The last 3 weeks (and today) copied from the history to the store the screens and the watch read. */
    @Synchronized private fun storeFromHistory(ctx: Context): Boolean {
        val today = Readiness.today()
        val from = java.time.LocalDate.now().minusDays(20).toString()
        val all = NutriDay.allJson(ctx)
        val keys = all.keys().asSequence().filter { it in from..today }.sorted().toList()
        val days = Nutrition.parse(JSONObject().apply { keys.forEach { put(it, all.get(it)) } }.toString()).toMutableList()
        if (days.none { it.date == today }) days += NutritionEngine.fill(ctx, Hydration.blank(today))
        val before = Nutrition.json(NutritionStore.days(ctx))
        val out = CalorieTargets.apply(ctx, days.sortedBy { it.date })
        NutritionStore.save(ctx, out)
        return Nutrition.json(out.takeLast(21)) != before
    }

    /** Stand-alone stand-in for reading the Command Center: folds in changes, refreshes today's targets / ideas. */
    fun refreshDays(ctx: Context): Boolean {
        commit(ctx)
        val today = Readiness.today()
        // Today's computed parts (pre-run ideas, targets) follow the plan and runs, so refresh them once in a while.
        val p = ctx.getSharedPreferences("standalone", 0)
        if (p.getString("filled", "") != today || System.currentTimeMillis() - p.getLong("filledAt", 0L) > 30 * 60_000L) {
            val d = NutriDay.rawHistory(ctx, today) ?: Hydration.blank(today)
            NutriDay.putDay(ctx, NutritionEngine.fill(ctx, d))
            p.edit().putString("filled", today).putLong("filledAt", System.currentTimeMillis()).apply()
        }
        val changed = storeFromHistory(ctx)
        FoodLibrary.quickToWatch(ctx); FoodLibrary.libraryToWatch(ctx)
        return changed
    }

    /** Background upkeep (hourly worker and when the app opens): Health Connect import and the cloud copy. Call off the main thread. */
    fun upkeep(ctx: Context, hourly: Boolean = false) {
        if (!Standalone.on(ctx)) return
        val p = ctx.getSharedPreferences("standalone", 0); val now = System.currentTimeMillis()
        if (ActivityImport.enabled(ctx) && (hourly || now - p.getLong("hcAt", 0L) > 60 * 60_000L)) {
            p.edit().putLong("hcAt", now).apply()
            try { ActivityImport.fromHealthConnect(ctx) } catch (e: Exception) { }
        }
        commit(ctx)
        if (now - p.getLong("cloudTry", 0L) > 3 * 60_000L || hourly) { p.edit().putLong("cloudTry", now).apply(); try { CloudCopy.sync(ctx) } catch (e: Exception) { } }
    }

    // ── The switch ──
    @Volatile var progress = ""

    /**
     * Cut-over: reads everything one last time from the Command Center (nothing there is changed or removed),
     * keeps it as BeyondStride's own data, switches stand-alone on and makes the first cloud copy.
     * Returns a summary. Call off the main thread.
     */
    fun cutOver(ctx: Context, onProgress: () -> Unit = {}): String {
        fun step(s: String) { progress = s; onProgress() }
        if (NutritionSync.ready(ctx) && !Standalone.on(ctx)) {
            step("Sending changes still waiting…"); HydrationSync.flush(ctx)
            step("Reading nutrition…"); NutritionSync.fetch(ctx)
            step("Reading every older nutrition day…"); NutriDay.fetchAll(ctx) { step("Reading older days… ${NutriDay.loaded} added") }
            step("Reading activities…"); ActivityStore.refresh(ctx)
            step("Reading the food database…"); FoodLibrary.refresh(ctx, force = true)
            step("Reading training plans…"); PlanStore.refresh(ctx)
        }
        step("Saving it as BeyondStride's own…")
        PlanStore.snapshot(ctx)
        CalorieTargets.keep(ctx)
        // Recent days (with anything still waiting on top) join the history, so one place holds every day.
        NutriDay.putAll(ctx, Hydration.overlay(ctx, NutritionStore.days(ctx)))
        // Activities (their chips are worked out here from now on).
        val acts = ActivityStore.rawJson(ctx)
        if (acts.length() == 0) acts.put("list", JSONArray())
        ActivityStore.setRaw(ctx, acts)
        Standalone.set(ctx, true)
        step("Folding in changes still waiting…"); commit(ctx); refreshDays(ctx)
        step("Making the first cloud copy…")
        val cloud = CloudCopy.ready(ctx) && CloudCopy.sync(ctx, force = true)
        val n = NutriDay.historyCount(ctx); val a = ActivityStore.list(ctx).size
        val pl = PlanStore.data(ctx).let { listOf("current", "upcoming").count { k -> it.has(k) } + (it.optJSONArray("archived")?.length() ?: 0) }
        progress = ""
        return "Stand-alone now · $n nutrition days · $a activities · $pl plans" + if (cloud) " · cloud copy made" else if (CloudCopy.ready(ctx)) " · cloud copy will retry" else ""
    }
}
