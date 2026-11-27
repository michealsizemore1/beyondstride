package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * 📱 Apps (its own bottom tab since Nov 27 2026; before that a tab inside Life): shortcuts to your other apps, grouped by
 * category. Each opens as its own app, with its own app lock. Rebuilt each time it's shown so a newly installed app gets
 * its real icon.
 */
class AppsActivity : Activity() {
    override fun onResume() { super.onResume(); render() }

    private fun render() {
        val d = { v: Int -> RunUi.dp(this, v) }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(d(14), d(40), d(14), d(28)) }
        // 📱 Shortcuts to your other apps (they open as their own apps, with their own app lock).
        // The total, next to the title (and how many are on this phone, when some aren't).
        val total = MY_APPS.size
        val missing = MY_APPS.filterNot { installed(it.first) }
        val onPhone = total - missing.size
        col.addView(RunUi.text(this, "📱 Apps", Color.WHITE, 26f, true))
        col.addView(RunUi.text(this, "$total apps" + (if (onPhone < total) " · $onPhone on this phone (not installed: ${missing.joinToString(", ") { it.second }})" else ""), RunUi.ACCENT, 15f, true).apply { setPadding(0, d(2), 0, 0) })
        col.addView(RunUi.text(this, "Your other apps. Tap one to open it (each keeps its own app lock).", RunUi.MUTED, 12f).apply { setPadding(0, d(4), 0, d(6)) })
        // 🔎 Search: narrows the tiles as you type (by app name or category).
        val listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val search = android.widget.EditText(this).apply {
            hint = "🔎 Search apps"; setText(query); setTextColor(Color.WHITE); setHintTextColor(RunUi.DIM); textSize = 15f; isSingleLine = true
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            background = RunUi.rounded(RunUi.CARD, d(12).toFloat()); setPadding(d(14), d(10), d(14), d(10))
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(e: android.text.Editable?) { query = e?.toString().orEmpty(); fillTiles(listBox) }
            })
        }
        col.addView(search, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = d(6) })
        col.addView(listBox)
        fillTiles(listBox)
        setContentView(RunUi.tabbed(this, ScrollView(this).apply { setBackgroundColor(RunUi.BG); addView(col) }, RunUi.TAB_APPS))
    }

    private var query = ""
    /** On this phone? (One check for both the count and the tiles: the app is installed and can be opened.) */
    private fun installed(pkg: String) = try { packageManager.getLaunchIntentForPackage(pkg) != null } catch (e: Exception) { false }

    /** The tiles, grouped by category, two per row; only the ones matching the search (name or category). */
    private fun fillTiles(box: LinearLayout) {
        val d = { v: Int -> RunUi.dp(this, v) }
        val col = box; col.removeAllViews()
        val q = query.trim().lowercase()
        var shown = 0
        // Grouped by category, two per row. (MSIZ Terminal: if the app isn't installed, the web page opens in Chrome.)
        APP_GROUPS.forEach { (heading, pkgs) ->
        val tiles = pkgs.mapNotNull { p -> MY_APPS.firstOrNull { it.first == p } }
            .filter { q.isEmpty() || it.second.lowercase().contains(q) || heading.lowercase().contains(q) }
        if (tiles.isEmpty()) return@forEach
        shown += tiles.size
        col.addView(RunUi.text(this, heading, RunUi.ACCENT, 13f, true).apply { setPadding(d(2), d(16), 0, 0) })
        tiles.chunked(2).forEach { row ->
            col.addView(LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, d(8), 0, 0)
                row.forEachIndexed { i, (pkg, name, emoji) ->
                    addView(LinearLayout(this@AppsActivity).apply {
                        orientation = LinearLayout.VERTICAL; gravity = android.view.Gravity.CENTER_HORIZONTAL
                        setPadding(d(8), d(14), d(8), d(12)); background = RunUi.rounded(RunUi.CARD, d(16).toFloat())
                        val icon = try { packageManager.getApplicationIcon(pkg) } catch (e: Exception) { null }
                        when {
                            icon != null -> addView(android.widget.ImageView(this@AppsActivity).apply { setImageDrawable(icon) }, LinearLayout.LayoutParams(d(56), d(56)))
                            emoji == "msiz" -> addView(TextView(this@AppsActivity).apply {   // a terminal-style icon: green MSIZ on black
                                text = "MSIZ"; setTextColor(0xFF22E07A.toInt()); textSize = 14f; typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                                gravity = android.view.Gravity.CENTER; background = RunUi.rounded(0xFF05090F.toInt(), d(16).toFloat()).apply { setStroke(d(1), 0xFF1F7A4A.toInt()) }
                            }, LinearLayout.LayoutParams(d(56), d(56)))
                            else -> addView(RunUi.text(this@AppsActivity, emoji, Color.WHITE, 36f))
                        }
                        addView(RunUi.text(this@AppsActivity, name, Color.WHITE, 13f, true).apply { gravity = android.view.Gravity.CENTER; setPadding(0, d(8), 0, 0) })
                        if (!installed(pkg)) addView(RunUi.text(this@AppsActivity, if (emoji == "msiz") "Not installed · opens the website" else "Not installed", 0xFFFBBF24.toInt(), 10f).apply { gravity = android.view.Gravity.CENTER })
                        setOnClickListener {
                            if (emoji == "msiz" && packageManager.getLaunchIntentForPackage(pkg) == null) {
                                val url = android.net.Uri.parse("https://michealsizemore1.github.io/ChatGPT-The-Master-App/msiz-terminal.html")
                                try { startActivity(Intent(Intent.ACTION_VIEW, url).setPackage("com.android.chrome")) } catch (e: Exception) { try { startActivity(Intent(Intent.ACTION_VIEW, url)) } catch (e2: Exception) { } }
                                return@setOnClickListener }
                            val go = packageManager.getLaunchIntentForPackage(pkg)
                            if (go != null) startActivity(go)
                            else android.widget.Toast.makeText(this@AppsActivity, "$name isn't installed on this phone", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { if (i == 0) marginEnd = d(8) })
                }
                if (row.size == 1) addView(View(this@AppsActivity), LinearLayout.LayoutParams(0, 1, 1f))
            })
        }
        }
        if (shown == 0) col.addView(RunUi.text(this, "No apps match \"${query.trim()}\".", RunUi.MUTED, 13f).apply { setPadding(d(2), d(16), 0, 0) })
    }
    /** 🗂 The Apps tab's categories (Nov 27 2026), in order. Every app in MY_APPS belongs to one. */
    private val APP_GROUPS = listOf(
        "💰 Money & Business" to listOf("com.sizemore.mcmoney", "com.sizemore.retirement", "com.sizemore.msiz", "com.sizemore.ebay"),
        "❤️ Health" to listOf("com.sizemore.health"),
        "🏠 Home & Hobbies" to listOf("com.sizemore.benefits", "com.sizemore.cookbook", "com.sizemore.books", "com.sizemore.records", "com.sizemore.spanish"),
        "🏟️ Sports" to listOf("com.sizemore.top25", "com.sizemore.nfl", "com.sizemore.nhl", "com.sizemore.thew"),
        "🏃 Running & Athletics" to listOf("com.sizemore.trackfield", "com.sizemore.ncaatf", "com.sizemore.xc"))
    /** Your other apps (package, name, fallback icon). Listed in the manifest's <queries> so Android lets us open them. */
    private val MY_APPS = listOf(
        Triple("com.sizemore.mcmoney", "M&C Money", "💵"),
        Triple("com.sizemore.retirement", "Retirement", "📈"),
        Triple("com.sizemore.benefits", "Family Hub", "🏠"),
        Triple("com.sizemore.msiz", "MSIZ Terminal", "msiz"),
        Triple("com.sizemore.spanish", "Luma Spanish", "🇪🇸"),
        Triple("com.sizemore.ebay", "MCS Treasures", "🏷️"),
        Triple("com.sizemore.cookbook", "Cindy's Cookbook", "📖"),
        Triple("com.sizemore.top25", "Saturday Top 25", "🏈"),
        Triple("com.sizemore.thew", "The W", "🏀"),
        Triple("com.sizemore.nfl", "NFL Gameday", "🏈"),
        Triple("com.sizemore.nhl", "NHL Faceoff", "🏒"),
        Triple("com.sizemore.trackfield", "Track & Field", "🏃"),
        Triple("com.sizemore.xc", "Cross Country", "🌲"),
        Triple("com.sizemore.ncaatf", "NCAA Track & Field", "🏅"),
        Triple("com.sizemore.books", "Cindy's Books", "📚"),
        Triple("com.sizemore.records", "Cindy's Records", "💿"),
        Triple("com.sizemore.health", "Health Records", "❤️"))
}
