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
                ch.filter { it.f == "actSettings" }.lastOrNull()?.let { RunSettings.pushToWatch(ctx); try { PlanStore.refreshZones(ctx) } catch (e: Exception) { } }
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
            ch.forEach { c -> if (c.f in Hydration.FLUIDS || c.f == "weight" || c.f == "postWeight" || c.f == "preWeight" || c.f == "pre2Weight" || c.f == "post2Weight" || c.f.startsWith("food") || c.f == "mealNote" || c.f == "macroRatio") touched += c.date }
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

    /**
     * 🔄 Sync everything now (the Today screen's sync button): Health Connect (weigh-ins, blood pressure, sleep, steps,
     * heart rate, activities), food & fluids, weather, the watch and the cloud / Command Center — skipping the usual
     * "only every 15–60 minutes" waits. Returns a short summary. Call off the main thread.
     */
    fun syncNow(ctx: Context): String {
        val out = ArrayList<String>()
        try { refreshDays(ctx) } catch (e: Exception) { }
        try { if (ActivityImport.enabled(ctx)) ActivityImport.fromHealthConnect(ctx) } catch (e: Exception) { }
        try { commit(ctx) } catch (e: Exception) { }
        try { if (WeightImport.enabled(ctx)) { val n = WeightImport.sync(ctx); out += "⚖️ " + (if (n > 0) "weight updated · " else "") + WeightImport.lastInfo(ctx).substringAfter(" · ") } } catch (e: Exception) { }
        try { if (SleepData.granted(ctx)) SleepData.refresh(ctx, 7) } catch (e: Exception) { }
        try { DayStress.sync(ctx, force = true) } catch (e: Exception) { }
        try { DayStress.steps(ctx, Readiness.today())?.let { out += String.format(java.util.Locale.US, "👣 %,d steps", it) } } catch (e: Exception) { }
        try { TodayWeather.fetch(ctx) } catch (e: Exception) { }
        try { ActivityStore.watchSummary(ctx)?.let { NutritionStore.setActs(ctx, it) } } catch (e: Exception) { }
        try { PlanStore.syncWatchIfNeeded(ctx) } catch (e: Exception) { }
        try { PhoneStatus.push(ctx) } catch (e: Exception) { }
        try { DailySummary.publish(ctx, force = true) } catch (e: Exception) { }
        try { CloudCopy.sync(ctx) } catch (e: Exception) { }
        val p = ctx.getSharedPreferences("standalone", 0); val now = System.currentTimeMillis()
        p.edit().putLong("hcAt", now).putLong("wtAt", now).putLong("bpAt", now).putLong("cloudTry", now).putLong("syncNowAt", now).apply()
        return out.joinToString("\n")
    }
    fun lastSyncNow(ctx: Context) = ctx.getSharedPreferences("standalone", 0).getLong("syncNowAt", 0L)

    /** Background upkeep (hourly worker and when the app opens): Health Connect import and the cloud copy. Call off the main thread. */
    fun upkeep(ctx: Context, hourly: Boolean = false) {
        val p = ctx.getSharedPreferences("standalone", 0); val now = System.currentTimeMillis()
        if (ActivityImport.enabled(ctx) && (hourly || now - p.getLong("hcAt", 0L) > 60 * 60_000L)) {
            p.edit().putLong("hcAt", now).apply()
            try { ActivityImport.fromHealthConnect(ctx) } catch (e: Exception) { }
        }
        commit(ctx)
        try { PlanStore.syncWatchIfNeeded(ctx) } catch (e: Exception) { }
        Reminders.morning(ctx)
        if (WeightImport.enabled(ctx) && (hourly || now - p.getLong("wtAt", 0L) > 15 * 60_000L)) { p.edit().putLong("wtAt", now).apply(); try { if (WeightImport.sync(ctx) > 0) DailySummary.publish(ctx, force = true) } catch (e: Exception) { } }
        var sleepNew = false
        // Last night's sleep from Samsung Health (it can reach Health Connect hours after you wake), every 30 minutes in the background too.
        try { if (now - io.github.michealsizemore1.beyondstride.ReadinessSync.nightsAt(ctx) > 30 * 60_000L && SleepData.granted(ctx)) { SleepData.refresh(ctx, 7); sleepNew = true } } catch (e: Exception) { }
        try { DayStress.sync(ctx) } catch (e: Exception) { }
        // Keep the watch's Today items (Body Battery, weather, reports) fresh.
        try { if (hourly) { TodayWeather.fetch(ctx); ActivityStore.watchSummary(ctx)?.let { io.github.michealsizemore1.beyondstride.NutritionStore.setActs(ctx, it) } } } catch (e: Exception) { }
        try { DailySummary.publish(ctx, force = sleepNew) } catch (e: Exception) { }
        if (now - p.getLong("cloudTry", 0L) > 3 * 60_000L || hourly) { p.edit().putLong("cloudTry", now).apply(); try { CloudCopy.sync(ctx) } catch (e: Exception) { } }
    }
}
