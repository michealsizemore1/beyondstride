package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.michealsizemore1.beyondstride.Nutrition
import io.github.michealsizemore1.beyondstride.NutritionStore
import java.util.Locale

/** The Nutrition tab: today, fluids, micronutrients, meals and the week, shown here (like the watch). */
class NutritionActivity : Activity() {
    private var dbTab = "recent"
    private var dbShown = 6
    private var dbQuery = ""
    private var dbSort = "saved"
    private var dbMeal = FoodUi.mealNow()

    private fun dbRow(title: String, sub: String, kcal: Int, delete: (() -> Unit)? = null, image: String = "", add: () -> Unit) = LinearLayout(this).apply {
        val ctx = this@NutritionActivity
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        background = RunUi.rounded(0xFF26302C.toInt(), RunUi.dp(ctx, 10).toFloat()); setPadding(RunUi.dp(ctx, 12), RunUi.dp(ctx, 6), RunUi.dp(ctx, 10), RunUi.dp(ctx, 6))
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = RunUi.dp(ctx, 6) }
        if (image.isNotEmpty()) addView(Thumbs.view(ctx, 40, image))
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(RunUi.text(ctx, title, Color.WHITE, 14f, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            addView(RunUi.text(ctx, sub, RunUi.MUTED, 11f).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        addView(RunUi.text(ctx, "$kcal", RunUi.MUTED, 12f, true).apply { setPadding(RunUi.dp(ctx, 6), 0, RunUi.dp(ctx, 6), 0) })
        addView(RunUi.text(ctx, "+ Add", Color.WHITE, 12f, true).apply {
            background = RunUi.rounded(0xFFE67E22.toInt(), RunUi.dp(ctx, 8).toFloat()); setPadding(RunUi.dp(ctx, 10), RunUi.dp(ctx, 4), RunUi.dp(ctx, 10), RunUi.dp(ctx, 4))
            setOnClickListener { add() }
        })
        if (delete != null) addView(RunUi.text(ctx, "✕", 0xFFFF8A80.toInt(), 15f, true).apply {
            gravity = Gravity.CENTER; setPadding(RunUi.dp(ctx, 10), RunUi.dp(ctx, 4), RunUi.dp(ctx, 2), RunUi.dp(ctx, 4))
            setOnClickListener {
                android.app.AlertDialog.Builder(ctx).setTitle("Delete “$title”?").setMessage(if (dbTab == "recent") "It won't show in Recent anymore (days you already logged keep it)."
                    else "Removes it from your " + (if (dbTab == "meals") "Saved Meals" else if (dbTab == "fav") "Food Database" else "Healthy 7+ list") + " here and in the Command Center.")
                    .setPositiveButton("Delete") { _, _ -> delete(); render() }.setNegativeButton("Cancel", null).show()
            }
        })
    }


    companion object {
        /** The Command Center's sort list. */
        val SORTS = listOf("saved" to "as saved", "prot" to "🥩 Highest protein", "carbs" to "🍞 Highest carbs", "fat" to "🥑 Lowest fat",
            "iron" to "🩸 Highest iron", "calcium" to "🦴 Highest calcium", "vitD" to "☀️ Highest vitamin D", "magnesium" to "💪 Highest magnesium", "vitB12" to "⚡ Highest vitamin B12")
        val MEAL_ICON = mapOf("breakfast" to "🌅", "lunch" to "🥗", "dinner" to "🍽️", "snack" to "🍎", "liquids" to "💧", "sports" to "💪", "supplements" to "💊")
        val MEAL_LABEL = mapOf("breakfast" to "Breakfast", "lunch" to "Lunch", "dinner" to "Dinner", "snack" to "Snacks", "liquids" to "Liquids", "sports" to "Sports Nutrition", "supplements" to "Supplements")
        /** The Command Center's ＋ Add colors. */
        val MEAL_COLOR = mapOf("breakfast" to 0xFFE67E22.toInt(), "lunch" to 0xFF16A34A.toInt(), "dinner" to 0xFF7C3AED.toInt(), "snack" to 0xFFDC2626.toInt(),
            "liquids" to 0xFF0D9488.toInt(), "sports" to 0xFFE67E22.toInt(), "supplements" to 0xFF4B5563.toInt())
    }


    override fun onResume() {
        io.github.michealsizemore1.beyondstride.Theme.load(this, "runsettings")
        super.onResume(); render()
        if (NutritionSync.justOpened) NutritionSync.refreshAfterNutrition(this) { runOnUiThread { if (!isFinishing) render() } }
        else Thread { if (NutritionSync.refreshIfStale(this, 30_000L)) runOnUiThread { if (!isFinishing) render() } }.start()
    }

    private fun render() {
        val ctx = this
        fun dp(v: Int) = RunUi.dp(ctx, v)
        val N = Nutrition
        val days = NutritionStore.days(this)
        val t = N.today(days)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(40), dp(16), dp(32)) }
        col.addView(RunUi.text(this, "Nutrition", Color.WHITE, 26f, true))
        val at = NutritionStore.fetchedAt(this)
        if (at > 0) col.addView(RunUi.text(this, "Updated " + android.text.format.DateUtils.getRelativeTimeSpanString(at, System.currentTimeMillis(), 60_000L), RunUi.DIM, 11f))

        // Today: calories, macros, fluids (and weights).
        col.addView(RunUi.section(this, "TODAY"))
        col.addView(RunUi.card(this).apply { NutritionUi.today(this@NutritionActivity, this, withMicros = false, big = true, chips = false) { render() } })

        // Micronutrients.
        if (t != null && t.count > 0 && t.micros.isNotEmpty()) {
            col.addView(RunUi.section(this, "MICRONUTRIENTS"))
            col.addView(RunUi.card(this).apply {
                addView(RunUi.text(ctx, String.format(Locale.US, "%.1f", t.microScore) + "/10 · " + t.microLabel(),
                    N.statusColor(when { t.microScore >= 7 -> "Met"; t.microScore >= 5 -> "Close"; else -> "Low" }), 18f, true))
                NutritionUi.micros(this@NutritionActivity, this, t, header = false)
            })
        }

        // FOOD LOG: every meal with its foods. Tap a food to change it; ＋ to add.
        col.addView(RunUi.section(this, "FOOD LOG"))
        // Food log tools — the Command Center's six buttons, same colors and place (above the meals).
        run {
            fun tools(mode: String) = startActivity(android.content.Intent(ctx, NutritionToolsActivity::class.java).putExtra("mode", mode))
            val tiles = listOf(
                Triple("🥗 Meal Ideas", 0xFF059669.toInt()) { tools("ideas") }, Triple("🧠 Fuel AI", 0xFF8E44AD.toInt()) { tools("fuel") },
                Triple("📅 Copy Day", 0xFF16A085.toInt()) { FoodUi.copyDay(this@NutritionActivity) { render() } },
            )
            tiles.chunked(3).forEach { row ->
                col.addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }
                    row.forEachIndexed { i, (t, c, go) ->
                        addView(RunUi.text(ctx, t, Color.WHITE, 14f, true).apply {
                            gravity = Gravity.CENTER; maxLines = 1; background = RunUi.rounded(c, dp(12).toFloat())
                            setPadding(dp(4), dp(12), dp(4), dp(12)); setOnClickListener { go() }
                        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { if (i < 2) marginEnd = dp(6) })
                    }
                })
            }
            col.addView(RunUi.text(ctx, "Your foods are below the meals. For something new, tap + Add on the meal. Tap a food in a meal to change or delete it; hold a meal to clear it.", RunUi.DIM, 10f).apply { setPadding(dp(2), dp(4), 0, 0) })
        }

        // One card per meal, like the Command Center: icon · name · count and kcal · 💾 · colored ＋ Add · ▸ to open/close.
        run {
            val foods = t?.foods ?: emptyList()
            val openPrefs = getSharedPreferences("foodlog", 0)
            val order = N.MEAL_ORDER + foods.map { it.meal }.distinct().filter { it !in N.MEAL_ORDER }
            order.forEach { m ->
                val mine = foods.filter { it.meal == m }
                val picker = m == "sports" || m == "supplements"
                val open = openPrefs.getBoolean("open_$m", true) && (mine.isNotEmpty() || picker)
                col.addView(RunUi.card(this).apply {
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }
                    addView(LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                        addView(RunUi.text(ctx, (MEAL_ICON[m] ?: "🍴") + "  " + (MEAL_LABEL[m] ?: N.MEAL_NAMES[m] ?: m), 0xFFE9D8B4.toInt(), 17f, true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                        if (mine.isNotEmpty()) addView(RunUi.text(ctx, "${mine.size} item${if (mine.size == 1) "" else "s"} · ${N.kcal(mine.sumOf { it.kcal })}  ", RunUi.MUTED, 12f, true))
                        addView(RunUi.text(ctx, "💾", Color.WHITE, 14f).apply {
                            background = RunUi.rounded(0xFF26302C.toInt(), dp(10).toFloat()); setPadding(dp(9), dp(5), dp(9), dp(5))
                            setOnClickListener { if (mine.isEmpty()) android.widget.Toast.makeText(ctx, "Add foods to this meal first", android.widget.Toast.LENGTH_SHORT).show() else saveMealDialog(m, mine) }
                        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(6) })
                        addView(RunUi.text(ctx, "+ Add", Color.WHITE, 13f, true).apply {
                            background = RunUi.rounded(MEAL_COLOR[m] ?: 0xFF4B5563.toInt(), dp(10).toFloat()); setPadding(dp(12), dp(5), dp(12), dp(5))
                            setOnClickListener { startActivity(android.content.Intent(ctx, FoodAddActivity::class.java).putExtra("meal", m)) }
                        })
                        val arrow = RunUi.text(ctx, if (open) "▾" else "▸", RunUi.MUTED, 14f).apply { setPadding(dp(8), 0, 0, 0) }
                        addView(arrow)
                        setOnLongClickListener {
                            if (mine.isEmpty()) return@setOnLongClickListener false
                            android.app.AlertDialog.Builder(ctx).setTitle("Clear ${MEAL_LABEL[m]}?").setMessage("Removes all ${mine.size} food${if (mine.size == 1) "" else "s"} from today's ${MEAL_LABEL[m]}.")
                                .setPositiveButton("Clear") { _, _ -> mine.forEach { HydrationSync.food(ctx, "foodDel", it) }; render() }.setNegativeButton("Cancel", null).show()
                            true
                        }
                        setOnClickListener {
                            if (mine.isEmpty() && !picker) return@setOnClickListener
                            val card = parent as LinearLayout
                            val b = card.getChildAt(1)
                            val nowOpen = b.visibility != android.view.View.VISIBLE
                            b.visibility = if (nowOpen) android.view.View.VISIBLE else android.view.View.GONE
                            arrow.text = if (nowOpen) "▾" else "▸"
                            openPrefs.edit().putBoolean("open_$m", nowOpen).apply()
                        }
                    })
                    val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; visibility = if (open) android.view.View.VISIBLE else android.view.View.GONE }
                    addView(body)
                    if (picker) body.addView(quickPicker(m))
                    mine.forEach { f -> body.
                        addView(LinearLayout(ctx).apply {
                            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                            setPadding(dp(4), dp(8), 0, dp(2))
                            FoodLibrary.imageFor(ctx, f).takeIf { it.isNotEmpty() }?.let { addView(Thumbs.view(ctx, 36, it)) }
                            addView(LinearLayout(ctx).apply {
                                orientation = LinearLayout.VERTICAL
                                addView(RunUi.text(ctx, f.name, Color.WHITE, 14f))
                                addView(RunUi.text(ctx, io.github.michealsizemore1.beyondstride.Food.fmt(f.servings) + " × " + f.servingLabel +
                                    " · P ${f.total(f.prot).toInt()} · C ${f.total(f.carbs).toInt()} · F ${f.total(f.fat).toInt()} g", RunUi.DIM, 11f))
                            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                            addView(RunUi.text(ctx, N.kcal(f.kcal), RunUi.MUTED, 13f, true))
                            setOnClickListener { FoodUi.edit(this@NutritionActivity, f) { render() } }
                        })
                    }
                })
            }
        }

        // Your foods, like the Command Center's panel: Recent · Food Database · Healthy 7+ · Saved Meals, search, sort, "Add to …".
        col.addView(RunUi.card(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(14) }
            val favs = FoodLibrary.favorites(ctx); val healthy = FoodLibrary.healthy(ctx); val meals = FoodLibrary.meals(ctx); val recent = FoodLibrary.recent(ctx)
            listOf(listOf("recent" to "🕘 Recent", "fav" to "⭐ Food Database (${favs.size})"),
                listOf("healthy" to "✅ Healthy 7+ (${healthy.size})", "meals" to "🍲 Saved Meals (${meals.size})")).forEachIndexed { r, row ->
                addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL; if (r > 0) setPadding(0, dp(6), 0, 0)
                    row.forEachIndexed { i, (k, n) ->
                        addView(RunUi.text(ctx, n, Color.WHITE, 12f, true).apply {
                            gravity = Gravity.CENTER; background = RunUi.rounded(if (dbTab == k) 0xFF8A5A0E.toInt() else 0xFF26302C.toInt(), dp(10).toFloat())
                            setPadding(dp(4), dp(9), dp(4), dp(9)); setOnClickListener { dbTab = k; dbShown = 6; render() }
                        }, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply { if (i == 0) marginEnd = dp(6) })
                    }
                })
            }
            val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            fun fill() {
                list.removeAllViews()
                fun hit(n: String) = dbQuery.isBlank() || dbQuery.trim().split(" ").all { n.contains(it, ignoreCase = true) }
                if (dbTab == "meals") {
                    val r = meals.filter { hit(it.name) }
                    if (r.isEmpty()) list.addView(RunUi.text(ctx, "No saved meals" + if (dbQuery.isNotBlank()) " match." else " yet — tap 💾 on a meal to save one.", RunUi.MUTED, 12f).apply { setPadding(0, dp(8), 0, 0) })
                    r.forEach { sm ->
                        val kcal = sm.items.sumOf { (it.cal ?: 0.0) * it.servings }.toInt()
                        list.addView(dbRow(sm.name, sm.items.joinToString(", ") { it.name }.take(80), kcal, delete = { FoodLibrary.delete(ctx, "meals", sm.name) }) {
                            sm.items.forEach { HydrationSync.food(ctx, "foodAdd", it.copy(id = io.github.michealsizemore1.beyondstride.Food.newId(), meal = dbMeal)) }
                            android.widget.Toast.makeText(ctx, "Added ${sm.name} to ${MEAL_LABEL[dbMeal]}", android.widget.Toast.LENGTH_SHORT).show(); render()
                        })
                    }
                } else {
                    var r = (when (dbTab) { "fav" -> favs; "recent" -> recent; else -> healthy }).filter { hit(it.name) }
                    r = when (dbSort) {
                        "prot" -> r.sortedByDescending { it.prot ?: 0.0 }; "carbs" -> r.sortedByDescending { it.carbs ?: 0.0 }; "fat" -> r.sortedBy { it.fat ?: Double.MAX_VALUE }
                        "saved" -> r; else -> r.sortedByDescending { it.micros[dbSort] ?: 0.0 } }
                    if (r.isEmpty()) list.addView(RunUi.text(ctx, if (favs.isEmpty() && healthy.isEmpty()) "Your Command Center food database shows here once it's been opened there." else "No match.", RunUi.MUTED, 12f).apply { setPadding(0, dp(8), 0, 0) })
                    r.take(200).forEach { f ->
                        list.addView(dbRow(f.name, f.servingLabel + " · P ${(f.prot ?: 0.0).toInt()} · C ${(f.carbs ?: 0.0).toInt()} · F ${(f.fat ?: 0.0).toInt()} g", ((f.cal ?: 0.0) * f.servings).toInt(),
                            delete = { FoodLibrary.delete(ctx, dbTab, f.name) }, image = FoodLibrary.imageFor(ctx, f)) {
                            FoodUi.confirmAdd(this@NutritionActivity, f, dbMeal) { render() }
                        })
                    }
                }
            }
            addView(android.widget.EditText(ctx).apply {
                hint = "🔍 Search saved foods…"; setText(dbQuery); setSingleLine(); setTextColor(Color.WHITE); setHintTextColor(RunUi.MUTED); textSize = 15f
                background = RunUi.rounded(0xFF26302C.toInt(), dp(10).toFloat()); setPadding(dp(12), dp(9), dp(12), dp(9))
                addTextChangedListener(object : android.text.TextWatcher {
                    override fun afterTextChanged(e: android.text.Editable?) { dbQuery = e?.toString() ?: ""; fill() }
                    override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}
                    override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                })
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) })
            if (dbTab == "fav" || dbTab == "healthy") addView(RunUi.text(ctx, "Sort: " + (SORTS.firstOrNull { it.first == dbSort }?.second ?: "as saved") + "  ▾", Color.WHITE, 14f).apply {
                background = RunUi.rounded(0xFF26302C.toInt(), dp(10).toFloat()); setPadding(dp(12), dp(9), dp(12), dp(9))
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }
                setOnClickListener {
                    android.app.AlertDialog.Builder(ctx).setTitle("Sort").setSingleChoiceItems(SORTS.map { it.second }.toTypedArray(), SORTS.indexOfFirst { it.first == dbSort }) { d, i ->
                        dbSort = SORTS[i].first; dbShown = 6; d.dismiss(); render() }.show()
                }
            })
            addView(RunUi.text(ctx, (MEAL_ICON[dbMeal] ?: "") + "  Add to " + (MEAL_LABEL[dbMeal] ?: dbMeal) + "  ▾", Color.WHITE, 14f).apply {
                background = RunUi.rounded(0xFF26302C.toInt(), dp(10).toFloat()); setPadding(dp(12), dp(9), dp(12), dp(9))
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }
                setOnClickListener {
                    android.app.AlertDialog.Builder(ctx).setTitle("Add to").setItems(N.MEAL_ORDER.map { (MEAL_ICON[it] ?: "") + "  " + (MEAL_LABEL[it] ?: it) }.toTypedArray()) { _, i -> dbMeal = N.MEAL_ORDER[i]; render() }.show()
                }
            })
            // Food Database tools (run in the Command Center, where the database lives).
            if (dbTab == "fav" || dbTab == "healthy") {
                val stats = FoodLibrary.stats(ctx)
                fun tile(icon: String, title: String, sub: String, fg: Int, bg: Int, action: String, ask: String) = RunUi.text(ctx, "$icon\n$title" + (if (sub.isNotEmpty()) "\n$sub" else ""), fg, 12f, true).apply {
                    gravity = Gravity.CENTER; background = RunUi.rounded(bg, dp(10).toFloat()); setPadding(dp(4), dp(10), dp(4), dp(10))
                    setOnClickListener {
                        android.app.AlertDialog.Builder(ctx).setTitle(title).setMessage(ask)
                            .setPositiveButton("Run") { _, _ -> FoodLibrary.libAction(ctx, action)
                                android.widget.Toast.makeText(ctx, "Sent — the Command Center runs it next time it's open", android.widget.Toast.LENGTH_LONG).show() }
                            .setNegativeButton("Cancel", null).show()
                    }
                }
                listOf(
                    listOf(tile("🧪", "Enrich All", if (stats.first > 0) "(${stats.first} need it)" else "(all complete)", 0xFFA5B4FC.toInt(), 0xFF262A45.toInt(), "enrich",
                        "Fill in missing nutrients in your Food Database using AI (your Command Center key). Runs one food at a time."),
                        tile("↩️", "Undo All", "(${stats.second} foods)", 0xFFFDA4AF.toInt(), 0xFF3A2329.toInt(), "undo", "Remove every AI-estimated value from your Food Database. This can't be undone.")),
                    listOf(tile("↻", "Rescan Foods", "", 0xFFFDBA74.toInt(), 0xFF3A2A1A.toInt(), "rescan", "Re-check every saved Nutrition day and barcode picture for healthy foods."),
                        tile("🧹", "Remove Dupes", "", 0xFFFDBA74.toInt(), 0xFF3A2A1A.toInt(), "dedupe", "Remove duplicate foods from your saved foods."))
                ).let { rows ->
                    // Folded into one line; tap to show the four tools.
                    val prefs = getSharedPreferences("foodlog", 0)
                    val tools = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; visibility = if (prefs.getBoolean("toolsOpen", false)) android.view.View.VISIBLE else android.view.View.GONE }
                    rows.forEach { row ->
                        tools.addView(LinearLayout(ctx).apply {
                            orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }
                            row.forEachIndexed { i, v -> addView(v, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply { if (i == 0) marginEnd = dp(8) }) }
                        })
                    }
                    val head = RunUi.text(ctx, "", Color.WHITE, 14f, true).apply {
                        background = RunUi.rounded(0xFF26302C.toInt(), dp(10).toFloat()); setPadding(dp(12), dp(9), dp(12), dp(9))
                        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }
                    }
                    fun label() { head.text = "🧰 Database tools " + (if (tools.visibility == android.view.View.VISIBLE) "▾" else "▸") +
                        (if (stats.first > 0) "  (${stats.first} need enriching)" else "") }
                    label()
                    head.setOnClickListener {
                        val open = tools.visibility != android.view.View.VISIBLE
                        tools.visibility = if (open) android.view.View.VISIBLE else android.view.View.GONE
                        prefs.edit().putBoolean("toolsOpen", open).apply(); label()
                    }
                    addView(head); addView(tools)
                }
            }
            // The list scrolls inside the panel (about six foods tall).
            addView(androidx.core.widget.NestedScrollView(ctx).apply {
                isNestedScrollingEnabled = true; isVerticalScrollBarEnabled = true
                addView(list)
            }, LinearLayout.LayoutParams(MATCH_PARENT, dp(400)).apply { topMargin = dp(4) })
            fill()
        })

        // LIQUIDS — like the Command Center's: Water, Sports Nutrition Drinks, Other Drinks (tap ± for a cup, hold for 1 oz).
        col.addView(RunUi.section(this, "LIQUIDS"))
        run {
            val H = io.github.michealsizemore1.beyondstride.Hydration
            fun row(icon: String, title: String, note: String?, f: String, oz: Int) = col.addView(RunUi.card(this).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }
                addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    addView(RunUi.text(ctx, "$icon  $title" + (note?.let { " ($it)" } ?: "") + ":", Color.WHITE, 15f, true))
                })
                addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, 0)
                    fun btn(t: String, d: Int) = RunUi.text(ctx, t, 0xFF8EA2F2.toInt(), 20f, true).apply {
                        gravity = Gravity.CENTER
                        background = android.graphics.drawable.GradientDrawable().apply { setStroke(dp(2), 0xFF6B7FE0.toInt()); cornerRadius = dp(8).toFloat() }
                        setOnClickListener { HydrationSync.change(ctx, f, d * H.STEP); render() }
                        setOnLongClickListener { HydrationSync.change(ctx, f, d); render(); true }
                    }
                    addView(btn("−", -1), LinearLayout.LayoutParams(dp(48), dp(44)))
                    addView(RunUi.text(ctx, "$oz oz (${String.format(Locale.US, "%.1f", oz / 8.0)} cups)", Color.WHITE, 17f, true).apply { gravity = Gravity.CENTER },
                        LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                    addView(btn("+", 1), LinearLayout.LayoutParams(dp(48), dp(44)))
                })
            })
            row("💧", "Water Intake", null, "water", t?.water ?: 0)
            row("🏃", "Sports Nutrition Drinks", null, "sports", t?.sportsOz ?: 0)
            row("🥤", "Other Drinks", "coffee, tea, juice, soda, etc.", "other", t?.otherOz ?: 0)
            col.addView(RunUi.text(ctx, "Tap − / + for a cup (8 oz); press and hold for 1 oz.", RunUi.DIM, 10f).apply { setPadding(dp(2), dp(4), 0, 0) })
        }

        // The last 7 days.
        val week = days.filter { it.count > 0 }.takeLast(7)
        if (week.isNotEmpty()) {
            col.addView(RunUi.section(this, "LAST 7 DAYS"))
            col.addView(RunUi.card(this).apply {
                val maxCal = maxOf(week.maxOf { maxOf(it.cal, it.target) }, 1)
                addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM
                    week.forEach { d ->
                        addView(LinearLayout(ctx).apply {
                            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                            val ok = d.target <= 0 || d.cal.toDouble() / d.target in 0.85..1.15
                            addView(RunUi.text(ctx, "${(d.cal + 50) / 100 / 10.0}k", RunUi.MUTED, 10f).apply { gravity = Gravity.CENTER })
                            addView(android.view.View(ctx).apply { background = RunUi.rounded(if (ok) 0xFF5FD38D.toInt() else 0xFFF2C94C.toInt(), dp(3).toFloat()) },
                                LinearLayout.LayoutParams(dp(16), dp(8 + d.cal * 80 / maxCal)))
                            val dd = java.time.LocalDate.parse(d.date)
                            addView(RunUi.text(ctx, dd.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() } + "\n" + d.date.substring(5).replace("-", "/"), RunUi.DIM, 10f).apply { gravity = Gravity.CENTER })
                        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                    }
                })
                val avgCal = week.map { it.cal }.average().toInt()
                val withT = week.filter { it.target > 0 }
                addView(RunUi.text(ctx, "Average ${N.kcal(avgCal)} kcal/day" + (if (withT.isNotEmpty()) " · target ${N.kcal(withT.map { it.target }.average().toInt())}" else "") +
                    "\nProtein ${week.map { it.prot }.average().toInt()} g · carbs ${week.map { it.carbs }.average().toInt()} g · fat ${week.map { it.fat }.average().toInt()} g a day" +
                    (week.filter { it.microScore > 0 }.takeIf { it.isNotEmpty() }?.let { "\nMicronutrients " + String.format(Locale.US, "%.1f", it.map { d -> d.microScore }.average()) + "/10 average" } ?: ""),
                    Color.WHITE, 13f).apply { setPadding(0, dp(8), 0, 0) })
                addView(RunUi.text(ctx, "Green = within 15% of that day's target.", RunUi.DIM, 11f).apply { setPadding(0, dp(4), 0, 0) })
            })
        }

        // THIS WEEK: the Command Center's three charts (Mon–Sun).
        run {
            val W = io.github.michealsizemore1.beyondstride.WeekChart
            val byDate = days.associateBy { it.date }
            val dates = W.weekDates()
            fun chart(title: String, help: String?, color: Int, unit: String, v: (Nutrition.Day) -> Double) {
                val vals = dates.map { dt -> byDate[dt]?.let(v)?.takeIf { it > 0 } }
                col.addView(RunUi.card(this).apply {
                    addView(RunUi.text(ctx, "$title — This week", RunUi.ACCENT, 14f, true))
                    help?.let { addView(RunUi.text(ctx, it, RunUi.MUTED, 11f).apply { setPadding(0, dp(2), 0, dp(4)) }) }
                    addView(RunUi.text(ctx, "Tap a day to see its number", RunUi.DIM, 10f))
                    addView(io.github.michealsizemore1.beyondstride.WeekChart(ctx, vals, color, label = title.substringBefore(" ("), unit = unit), LinearLayout.LayoutParams(MATCH_PARENT, dp(190)))
                    addView(RunUi.text(ctx, W.summary(vals, unit), RunUi.MUTED, 12f).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) })
                }.also { it.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) } })
            }
            col.addView(RunUi.section(this, "THIS WEEK"))
            chart("Calories", null, 0xFFF39C12.toInt(), "") { it.cal.toDouble().takeIf { _ -> it.count > 0 } ?: 0.0 }
            chart("Water", null, 0xFF3498DB.toInt(), " oz") { (if (it.water + it.sportsOz + it.otherOz > 0) it.water else it.waterOz).toDouble() }
            chart("Total liquids (excl. caffeine)", "Water + sports drinks + other drinks, minus coffee, energy drinks and black/green tea. GU drink mix still counts.",
                0xFF16A085.toInt(), " oz") { it.waterOz.toDouble() }
        }

        // The Command Center's own Nutrition page (AI search, label scan, food database) — still there too.
        col.addView(RunUi.button(this, "Open Nutrition in the Command Center", RunUi.CARD, Color.WHITE) { RunUi.openNutrition(this) }.apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(22) }
        })
        col.addView(RunUi.text(this, "Food logged in either app shows in both.", RunUi.DIM, 11f).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) })

        // Keep the page where it was when it's redrawn (opening a meal, adding or editing a food…).
        val keepY = scroller?.scrollY ?: 0
        val sv = ScrollView(this).apply {
            setBackgroundColor(RunUi.BG); isNestedScrollingEnabled = true
            addView(col, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        scroller = sv
        setContentView(RunUi.tabbed(this, sv, RunUi.TAB_NUTRITION))
        if (keepY > 0) sv.post { sv.scrollTo(0, keepY) }
    }
    private var scroller: ScrollView? = null

    // Quick add (Command Center): sports nutrition by brand / type / flavor; supplements from a list.
    private var qBrand = ""; private var qLine = ""; private var qSport = 0; private var qSupp = 0
    private fun quickPicker(meal: String): LinearLayout = LinearLayout(this).apply {
        val ctx = this@NutritionActivity
        val P = io.github.michealsizemore1.beyondstride.Products
        fun dp(v: Int) = RunUi.dp(ctx, v)
        orientation = LinearLayout.VERTICAL
        background = RunUi.rounded(0xFF2B2416.toInt(), dp(10).toFloat()); setPadding(dp(10), dp(8), dp(10), dp(10))
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }
        fun chip(t: String, onTap: () -> Unit) = RunUi.text(ctx, "$t  ▾", Color.WHITE, 13f).apply {
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            background = RunUi.rounded(0xFF26302C.toInt(), dp(8).toFloat()); setPadding(dp(10), dp(8), dp(10), dp(8)); setOnClickListener { onTap() }
        }
        fun pick(title: String, items: List<String>, cur: Int, set: (Int) -> Unit) =
            android.app.AlertDialog.Builder(ctx).setTitle(title).setSingleChoiceItems(items.toTypedArray(), cur) { d, i -> set(i); d.dismiss(); render() }.show()
        val product: io.github.michealsizemore1.beyondstride.Products.Product?
        if (meal == "sports") {
            addView(RunUi.text(ctx, "⚡ GU / SiS / Premier Protein quick add", 0xFFF5B041.toInt(), 12f, true))
            val lines = P.lines(qBrand); if (qLine.isNotEmpty() && qLine !in lines) qLine = ""
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, 0)
                addView(chip(P.BRANDS.first { it.first == qBrand }.second) {
                    pick("Brand", P.BRANDS.map { it.second }, P.BRANDS.indexOfFirst { it.first == qBrand }) { qBrand = P.BRANDS[it].first; qLine = ""; qSport = 0 } },
                    LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })
                addView(chip(qLine.ifEmpty { "All Types" }) {
                    pick("Type", listOf("All Types") + lines, if (qLine.isEmpty()) 0 else lines.indexOf(qLine) + 1) { qLine = if (it == 0) "" else lines[it - 1]; qSport = 0 } },
                    LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            })
            val list = P.SPORTS.filter { (qBrand.isEmpty() || it.brand == qBrand) && (qLine.isEmpty() || it.line == qLine) }
            fun label(p: io.github.michealsizemore1.beyondstride.Products.Product) = "${p.name} (${p.cal.toInt()} cal, ${io.github.michealsizemore1.beyondstride.Food.fmt(p.carbs)}g carb" +
                (p.micros["caffeine"]?.let { " · ${it.toInt()}mg caf" } ?: "") + ")"
            product = list.getOrNull(qSport.coerceIn(0, maxOf(0, list.size - 1)))
            if (product != null) addView(chip(label(product)) { pick("Product", list.map { label(it) }, list.indexOf(product)) { qSport = it } }
                .also { it.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) } })
        } else {
            addView(RunUi.text(ctx, "💊 Supplement quick add", 0xFFF5B041.toInt(), 12f, true))
            product = P.SUPPLEMENTS.getOrNull(qSupp)
            if (product != null) addView(chip("${product.brand} ${product.name}") { pick("Supplement", P.SUPPLEMENTS.map { "${it.brand} ${it.name}" }, qSupp) { qSupp = it } }
                .also { it.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) } })
        }
        if (product != null) {
            addView(RunUi.text(ctx, product.preview(), 0xFFF5B041.toInt(), 12f).apply { setPadding(0, dp(6), 0, dp(6)) })
            addView(RunUi.button(ctx, "+ Add to " + (MEAL_LABEL[meal] ?: meal), 0xFFB9770E.toInt(), Color.WHITE) {
                HydrationSync.food(ctx, "foodAdd", product.item(meal))
                android.widget.Toast.makeText(ctx, "Added ${product.name} to ${MEAL_LABEL[meal]}", android.widget.Toast.LENGTH_SHORT).show(); render()
            })
        }
    }

    /** 💾 Save this meal's foods as a saved meal (also added to the Command Center's saved meals). */
    private fun saveMealDialog(meal: String, items: List<io.github.michealsizemore1.beyondstride.Food.Item>) {
        val ctx = this
        val name = android.widget.EditText(this).apply {
            setText((Nutrition.MEAL_NAMES[meal] ?: meal) + " " + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("MMM d")))
            setSelectAllOnFocus(true); setSingleLine()
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(RunUi.dp(ctx, 22), RunUi.dp(ctx, 8), RunUi.dp(ctx, 22), 0)
            addView(name); addView(RunUi.text(ctx, items.joinToString("\n") { "• ${it.name}" }, RunUi.MUTED, 12f).apply { setPadding(0, RunUi.dp(ctx, 6), 0, 0) }) }
        android.app.AlertDialog.Builder(this).setTitle("Save meal").setView(box)
            .setPositiveButton("Save") { _, _ ->
                val n = name.text.toString().trim().ifEmpty { Nutrition.MEAL_NAMES[meal] ?: meal }
                FoodLibrary.saveMeal(ctx, n, meal, items)
                android.widget.Toast.makeText(ctx, "Saved “$n” — in Add food → Saved meals", android.widget.Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Cancel", null).show()
    }
}
