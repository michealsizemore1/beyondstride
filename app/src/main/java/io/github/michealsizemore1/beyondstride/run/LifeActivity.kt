package io.github.michealsizemore1.beyondstride.run

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * 📓 Life — the My Life Command Center inside BeyondStride (the fifth tab).
 * It's the same website as in Chrome, so nothing moved: it keeps its own data in this screen's storage and stays
 * matched with Chrome and the computer through the Command Center's own cloud sync.
 * Settings → "Life tab" turns the tab off again.
 */
object LifeTab {
    private fun p(ctx: Context) = ctx.getSharedPreferences("runsettings", 0)
    fun on(ctx: Context) = p(ctx).getBoolean("lifeTab", true)
    fun setOn(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("lifeTab", v).apply()
}

class LifeActivity : Activity() {
    private lateinit var web: WebView
    private var fileCb: ValueCallback<Array<Uri>>? = null
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var nativeScroll: ScrollView
    private lateinit var nativeCol: LinearLayout
    private lateinit var tabToday: TextView
    private lateinit var tabFull: TextView
    private lateinit var tabReviews: TextView
    private lateinit var reviewsCol: LinearLayout
    private lateinit var reviewsScroll: ScrollView
    private var page = 0          // 0 🎯 Today · 1 📊 Reviews · 2 📓 Full Command Center
    private var monthCard: PeriodCard? = null
    private lateinit var study: BibleStudyPage
    private var calendar: LifeCalendar? = null
    private lateinit var bar: LinearLayout
    private var yearCard: PeriodCard? = null
    private var weekTab: WeekTabCard? = null
    private var libCard: LibraryCard? = null
    private var habitsCard: LifeHabits? = null
    private var lifeBible: LifeBible? = null
    private var pickCb: ((Uri) -> Unit)? = null
    private var lifeLibrary: LifeLibrary? = null
    private val brain by lazy { LifeBrain(this) }
    private var medHeader: LifeMeditate? = null
    private var wellPeriods: PeriodTables? = null
    private var biblePeriods: PeriodTables? = null
    private var libPeriods: PeriodTables? = null
    private var bibleChecks: ChecksCard? = null
    private var meditCard: MeditCard? = null
    private lateinit var sectionCol: LinearLayout
    private lateinit var sectionScroll: ScrollView
    private lateinit var appsCol: LinearLayout
    private lateinit var appsScroll: ScrollView
    private var section = ""
    private var dashCard: LifeDashboard? = null
    private lateinit var more: LifeMore
    private lateinit var moreScroll: ScrollView
    private lateinit var tabMore: TextView
    private lateinit var tabApps: TextView
    private lateinit var tabTabs: TextView
    private lateinit var tabMonth: TextView
    private lateinit var tabYear: TextView
    private lateinit var tabReview: TextView
    private lateinit var tabsScroll: ScrollView
    private var full = false
    private var focusCard: FocusCard? = null
    private var checksCard: ChecksCard? = null
    private var scheduleCard: ScheduleCard? = null
    private var reviewCard: ReviewCard? = null
    private var wellCard: WellnessCard? = null
    private var bibleCard: BibleCard? = null
    private var weekCard: WeeklyCard? = null
    private var journalCard: JournalCard? = null

    private val ccListener = object : CcEngine.Listener {
        override fun changed() { ui.removeCallbacks(reload); ui.postDelayed(reload, 400) }
        override fun files(json: String) { if (::more.isInitialized) more.onFiles(json) }
        override fun file(name: String, type: String, b64: String) { if (::more.isInitialized) more.onFile(name, type, b64) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The one shared Command Center page (the Today tab's Life card uses the same one — never two copies).
        web = CcEngine.get(this).also { CcEngine.detachFromParent(it) }
        CcEngine.fileChooser = { cb, params ->
            fileCb?.onReceiveValue(null); fileCb = cb
            try { startActivityForResult(params.createIntent(), 41); true } catch (e: Exception) { fileCb = null; false }
        }
        CcEngine.add(ccListener)
        // Native sections (BeyondStride screens on the Command Center's own data) over the full website.
        nativeCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(RunUi.dp(this@LifeActivity, 14), 0, RunUi.dp(this@LifeActivity, 14), RunUi.dp(this@LifeActivity, 24)) }
        nativeScroll = ScrollView(this).apply { setBackgroundColor(RunUi.BG); isFillViewport = true; addView(nativeCol) }
        reviewsCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(RunUi.dp(this@LifeActivity, 14), 0, RunUi.dp(this@LifeActivity, 14), RunUi.dp(this@LifeActivity, 24)) }
        reviewsScroll = ScrollView(this).apply { setBackgroundColor(RunUi.BG); isFillViewport = true; addView(reviewsCol) }
        val stack = FrameLayout(this).apply {
            addView(web, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(nativeScroll, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(reviewsScroll, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            more = LifeMore(this@LifeActivity, web, { needKey() }, { showPage(2) })
            moreScroll = ScrollView(this@LifeActivity).apply { setBackgroundColor(RunUi.BG); isFillViewport = true; addView(more.col) }
            addView(moreScroll, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            tabsScroll = ScrollView(this@LifeActivity).apply { setBackgroundColor(RunUi.BG); isFillViewport = true; addView(tabsPage()) }
            addView(tabsScroll, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            appsCol = LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(RunUi.dp(this@LifeActivity, 14), RunUi.dp(this@LifeActivity, 8), RunUi.dp(this@LifeActivity, 14), RunUi.dp(this@LifeActivity, 24)) }
            appsScroll = ScrollView(this@LifeActivity).apply { setBackgroundColor(RunUi.BG); isFillViewport = true; addView(appsCol); visibility = View.GONE }
            addView(appsScroll, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            sectionCol = LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(RunUi.dp(this@LifeActivity, 14), 0, RunUi.dp(this@LifeActivity, 14), RunUi.dp(this@LifeActivity, 24)) }
            sectionScroll = ScrollView(this@LifeActivity).apply { setBackgroundColor(RunUi.BG); isFillViewport = true; addView(sectionCol) }
            addView(sectionScroll, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            study = BibleStudyPage(this@LifeActivity, web) { showPage(0) }
            addView(study.view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
        // Top row like the Command Center's: ☰ More | Daily · Weekly · Monthly · Yearly · Review.
        fun tab(label: String, onClick: () -> Unit) = RunUi.text(this, label, RunUi.MUTED, 14f, true).apply {
            isSingleLine = true; gravity = android.view.Gravity.CENTER
            setPadding(RunUi.dp(this@LifeActivity, 10), RunUi.dp(this@LifeActivity, 12), RunUi.dp(this@LifeActivity, 10), RunUi.dp(this@LifeActivity, 12))
            setOnClickListener { onClick() }
        }
        tabTabs = tab("☰ More") { showPage(5) }; tabToday = tab("Daily") { showPage(0) }; tabReviews = tab("Weekly") { showPage(1) }
        tabMonth = tab("Monthly") { showPage(7) }; tabYear = tab("Yearly") { showPage(8) }; tabReview = tab("Review") { showPage(9) }
        tabApps = tab("Apps") { showPage(10) }
        tabMore = tab("Tools") { showPage(4) }; tabFull = tab("📓 Full") { showPage(2) }   // Full is reached from ☰ More
        bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; background = RunUi.rounded(RunUi.CARD, 0f)
            addView(tabTabs, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(View(this@LifeActivity).apply { setBackgroundColor(0x22FFFFFF) }, LinearLayout.LayoutParams(RunUi.dp(this@LifeActivity, 1), ViewGroup.LayoutParams.MATCH_PARENT))
            addView(android.widget.HorizontalScrollView(this@LifeActivity).apply { isHorizontalScrollBarEnabled = false
                addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL
                    // Weekly / Monthly / Yearly now live in each area's section.
                    listOf(tabToday, tabReview, tabMore, tabApps).forEach { addView(it) } }) },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(RunUi.BG)
            addView(bar)
            addView(stack, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            // Stay below the status bar.
            setOnApplyWindowInsetsListener { v, insets ->
                @Suppress("DEPRECATION") val t = if (android.os.Build.VERSION.SDK_INT >= 30) insets.getInsets(android.view.WindowInsets.Type.statusBars()).top else insets.systemWindowInsetTop
                v.setPadding(0, t, 0, 0); insets
            }
        }
        buildNative()
        showPage((savedInstanceState?.getInt("page") ?: 0).let { if (it == 3 || it in 7..8) 0 else if (it == 1) 9 else it })
        setContentView(RunUi.tabbed(this, box, RunUi.TAB_LIFE))
    }

    override fun onResume() { super.onResume(); CcEngine.get(this); web.onResume(); CcEngine.pushWellness(this); Thread { try { CalendarReminders.refresh(applicationContext) } catch (e: Exception) { } }.start(); web.evaluateJavascript("window.ccSync&&ccSync.now()", null); if (!full) { ui.removeCallbacks(reload); ui.postDelayed(reload, 300) } }
    // Upload anything not yet sent before the app goes to the background.
    override fun onPause() { study.stopReading(); more.pause(); CcEngine.exportLife(this); web.evaluateJavascript("window.ccSync&&ccSync.now()", null); web.onPause(); super.onPause() }
    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); out.putInt("page", page) }
    override fun onDestroy() { ui.removeCallbacksAndMessages(null); study.destroy(); CcEngine.remove(ccListener); CcEngine.release(this); super.onDestroy() }

    private fun show(f: Boolean) = showPage(if (f) 2 else 0)

    /** ☰ More — the other Life pages as big buttons (the full view is in Tools); each opens that page full screen. */
    private fun tabsPage(): LinearLayout {
        val d = { v: Int -> RunUi.dp(this, v) }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(d(14), d(8), d(14), d(24)) }
        col.addView(RunUi.text(this, "☰ More", Color.WHITE, 16f, true))
        col.addView(RunUi.text(this, "More Life pages. Tap one to open it.", RunUi.MUTED, 12f).apply { setPadding(0, d(4), 0, d(6)) })
        val items = listOf(
            Triple("📖", "Bible", "bible") to "Bible reading archive, sermon & study notes",
            Triple("🌱", "Habits", "habits2") to "All habits, add / edit, history",
            Triple("📚", "Library", "library") to "Books, audiobooks, finish plan",
            Triple("🧘", "Meditate", "meditate") to "Guided timer, scripture, session log",
            Triple("❤️", "Wellness", "wellness") to "BP + report, meds, weight, sleep, movement",
            Triple("🧠", "Brain Games", "brain") to "Memory, focus and quick math, a daily workout")
        items.chunked(2).forEach { row ->
            col.addView(LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, d(8), 0, 0)
                row.forEachIndexed { i, (t, desc) ->
                    addView(LinearLayout(this@LifeActivity).apply {
                        orientation = LinearLayout.VERTICAL; setPadding(d(14), d(14), d(14), d(14)); background = RunUi.rounded(RunUi.CARD, d(16).toFloat())
                        addView(RunUi.text(this@LifeActivity, t.first, Color.WHITE, 30f))
                        addView(RunUi.text(this@LifeActivity, t.second, Color.WHITE, 16f, true).apply { setPadding(0, d(6), 0, d(2)) })
                        addView(RunUi.text(this@LifeActivity, desc, RunUi.MUTED, 11f))
                        setOnClickListener { when (t.third) { "tools" -> showPage(4); "full" -> showPage(2); else -> openSection(t.third, t.first + " " + t.second) } }
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { if (i == 0) marginEnd = d(8) })
                }
                if (row.size == 1) addView(View(this@LifeActivity), LinearLayout.LayoutParams(0, 1, 1f))
            })
        }
        return col
    }
    /** 📱 Apps tab — shortcuts to your other apps (Nov 26 2026: moved here from the bottom of ☰ More). Rebuilt each time
     *  it's shown so a newly installed app gets its real icon. */
    private fun appsPage() {
        val d = { v: Int -> RunUi.dp(this, v) }
        val col = appsCol; col.removeAllViews()
        // 📱 Shortcuts to your other apps (they open as their own apps, with their own app lock).
        col.addView(RunUi.text(this, "📱 My apps", Color.WHITE, 16f, true))
        col.addView(RunUi.text(this, "Your other apps. Tap one to open it (each keeps its own app lock).", RunUi.MUTED, 12f).apply { setPadding(0, d(4), 0, d(6)) })
        // Two per row: your four apps. (MSIZ Terminal is its own app now; if it isn't installed, the web page opens in Chrome.)
        val tiles = MY_APPS
        tiles.chunked(2).forEach { row ->
            col.addView(LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, d(8), 0, 0)
                row.forEachIndexed { i, (pkg, name, emoji) ->
                    addView(LinearLayout(this@LifeActivity).apply {
                        orientation = LinearLayout.VERTICAL; gravity = android.view.Gravity.CENTER_HORIZONTAL
                        setPadding(d(8), d(14), d(8), d(12)); background = RunUi.rounded(RunUi.CARD, d(16).toFloat())
                        val icon = try { packageManager.getApplicationIcon(pkg) } catch (e: Exception) { null }
                        when {
                            icon != null -> addView(android.widget.ImageView(this@LifeActivity).apply { setImageDrawable(icon) }, LinearLayout.LayoutParams(d(56), d(56)))
                            emoji == "msiz" -> addView(TextView(this@LifeActivity).apply {   // a terminal-style icon: green MSIZ on black
                                text = "MSIZ"; setTextColor(0xFF22E07A.toInt()); textSize = 14f; typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                                gravity = android.view.Gravity.CENTER; background = RunUi.rounded(0xFF05090F.toInt(), d(16).toFloat()).apply { setStroke(d(1), 0xFF1F7A4A.toInt()) }
                            }, LinearLayout.LayoutParams(d(56), d(56)))
                            else -> addView(RunUi.text(this@LifeActivity, emoji, Color.WHITE, 36f))
                        }
                        addView(RunUi.text(this@LifeActivity, name, Color.WHITE, 13f, true).apply { gravity = android.view.Gravity.CENTER; setPadding(0, d(8), 0, 0) })
                        if (emoji != "msiz" && icon == null) addView(RunUi.text(this@LifeActivity, "Not installed", RunUi.DIM, 10f).apply { gravity = android.view.Gravity.CENTER })
                        setOnClickListener {
                            if (emoji == "msiz" && packageManager.getLaunchIntentForPackage(pkg) == null) {
                                val url = android.net.Uri.parse("https://michealsizemore1.github.io/ChatGPT-The-Master-App/msiz-terminal.html")
                                try { startActivity(Intent(Intent.ACTION_VIEW, url).setPackage("com.android.chrome")) } catch (e: Exception) { try { startActivity(Intent(Intent.ACTION_VIEW, url)) } catch (e2: Exception) { } }
                                return@setOnClickListener }
                            val go = packageManager.getLaunchIntentForPackage(pkg)
                            if (go != null) startActivity(go)
                            else android.widget.Toast.makeText(this@LifeActivity, "$name isn't installed on this phone", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { if (i == 0) marginEnd = d(8) })
                }
                if (row.size == 1) addView(View(this@LifeActivity), LinearLayout.LayoutParams(0, 1, 1f))
            })
        }
    }
    /** Your other apps (package, name, fallback icon). Listed in the manifest's <queries> so Android lets us open them. */
    private val MY_APPS = listOf(
        Triple("com.sizemore.mcmoney", "M&C Money", "💵"),
        Triple("com.sizemore.retirement", "Retirement", "📈"),
        Triple("com.sizemore.benefits", "Family Hub", "🏠"),
        Triple("com.sizemore.msiz", "MSIZ Terminal", "msiz"))
    /** A ☰ Tabs section as BeyondStride screens (Uploads still opens the Command Center's own page). */
    private fun openSection(name: String, title: String) {
        val views: List<View> = when (name) {
            "wellness" -> listOfNotNull(wellCard?.view, more.bpView, wellPeriods?.view)
            "bible" -> listOfNotNull(lifeBible?.view, biblePeriods?.view)
            "meditate" -> listOfNotNull(medHeader?.view, more.meditView)
            "library" -> listOfNotNull(lifeLibrary?.view, libPeriods?.view)
            "habits2" -> listOfNotNull(habitsCard?.view)
            "dash" -> listOfNotNull(dashCard?.view)
            "brain" -> listOf(brain.view)
            else -> emptyList()
        }
        if (views.isEmpty()) { openCcPage(name); return }
        val d = { v: Int -> RunUi.dp(this, v) }
        section = name
        sectionCol.removeAllViews()
        sectionCol.addView(LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, d(6), 0, 0)
            addView(RunUi.text(this@LifeActivity, "‹", RunUi.ACCENT, 26f, true).apply { setPadding(d(2), 0, d(12), 0); setOnClickListener { brain.stop(); showPage(5) } })
            addView(RunUi.text(this@LifeActivity, title, Color.WHITE, 20f, true)) })
        views.forEach { v -> (v.parent as? ViewGroup)?.removeView(v); sectionCol.addView(v) }
        if (name != "dash" && name != "brain") sectionCol.addView(RunUi.text(this, "Open the full ${title.substringAfter(' ')} page ›", 0xFFA78BFA.toInt(), 13f, true).apply {
            setPadding(0, d(14), 0, 0); setOnClickListener { openCcPage(name) } })
        showPage(6); sectionScroll.scrollTo(0, 0)
        if (name == "habits2") habitsCard?.load()
        if (name == "dash") dashCard?.load()
        if (name == "brain") brain.show() else brain.stop()
        if (name == "meditate") { more.load(); more.loadSounds() }
        if (name == "bible") { lifeBible?.load(); biblePeriods?.load() }
        if (name == "wellness") wellPeriods?.load()
        if (name == "library") { lifeLibrary?.load(true); libPeriods?.load() }
    }
    private fun openCcPage(name: String) {
        showPage(2)
        web.evaluateJavascript("(function(){var b=document.getElementById('hm-$name');if(b)b.click();else if(typeof hmSwitch==='function')hmSwitch('$name',{preventDefault:function(){}});window.scrollTo(0,0);})()", null)
    }
    private fun showReviews(p: Int) {
        val v = listOfNotNull(journalCard?.view, reviewCard?.view, weekCard?.view)   // 📝 Review: journal, the day's review (‹ › for earlier days), the weekly review
        if (reviewsCol.childCount == v.size && v.withIndex().all { (i, x) -> reviewsCol.getChildAt(i) === x }) return
        reviewsCol.removeAllViews(); v.forEach { x -> (x.parent as? ViewGroup)?.removeView(x); reviewsCol.addView(x) }
        reviewsScroll.scrollTo(0, 0)
    }
    private fun showPage(p: Int) {
        val was = page; page = p; full = p == 2
        if (was == 3 && p != 3) study.hide()
        nativeScroll.visibility = if (p == 0) View.VISIBLE else View.GONE   // the website stays loaded underneath either way
        study.view.visibility = if (p == 3) View.VISIBLE else View.GONE
        bar.visibility = if (p == 3) View.GONE else View.VISIBLE
        reviewsScroll.visibility = if (p == 1 || p in 7..9) View.VISIBLE else View.GONE
        if (p == 1 || p in 7..9) showReviews(p)
        moreScroll.visibility = if (p == 4) View.VISIBLE else View.GONE
        tabsScroll.visibility = if (p == 5) View.VISIBLE else View.GONE
        sectionScroll.visibility = if (p == 6) View.VISIBLE else View.GONE
        appsScroll.visibility = if (p == 10) View.VISIBLE else View.GONE
        if (p == 10) { appsPage(); appsScroll.scrollTo(0, 0) }
        if (p == 4) more.load()
        // Active tab: white with an accent underline (like the Command Center's); ☰ More gets a lighter block.
        fun style(t: TextView, on: Boolean) {
            t.setTextColor(if (on) Color.WHITE else RunUi.MUTED)
            t.background = if (on) android.graphics.drawable.LayerDrawable(arrayOf(android.graphics.drawable.ColorDrawable(0), android.graphics.drawable.ColorDrawable(RunUi.ACCENT))).apply {
                setLayerInset(1, 0, 0, 0, 0); setLayerGravity(1, android.view.Gravity.BOTTOM); setLayerHeight(1, RunUi.dp(this@LifeActivity, 3)) } else null
        }
        style(tabToday, p == 0); style(tabReviews, p == 1); style(tabMonth, p == 7); style(tabYear, p == 8); style(tabReview, p == 9); style(tabMore, p == 4); style(tabApps, p == 10)
        val moreOn = p == 2 || p == 5 || p == 6
        tabTabs.setTextColor(if (moreOn) Color.WHITE else RunUi.MUTED); tabTabs.setBackgroundColor(if (moreOn) 0xFF2A3430.toInt() else 0xFF222B28.toInt())
        if (p == 0 || p == 1 || p == 6 || p in 7..9) reload.run()
        // 📊 This Week (goals) card removed from Review at your request (Nov 21 2026).
        if (p == 7) monthCard?.load()
        if (p == 8) yearCard?.load()
    }

    // ── Native sections ──
    private fun buildNative() {
        nativeCol.removeAllViews()
        nativeCol.addView(RunUi.text(this, "Your Command Center, as BeyondStride screens. Changes save to the Command Center right away and sync with Chrome and your computer.", RunUi.MUTED, 12f).apply { setPadding(0, RunUi.dp(this@LifeActivity, 6), 0, 0) })
        calendar = LifeCalendar(this, web) { pickDay(it) }.also { nativeCol.addView(it.view) }
        focusCard = FocusCard().also { nativeCol.addView(it.view) }
        scheduleCard = ScheduleCard().also { nativeCol.addView(it.view) }
        // (The old "Daily Checks · Planning & Review" card is gone: those two checks are hidden leftovers in the Command Center.)
        // These live in their own sections (☰ Tabs), like the Command Center's ☰ menu pages.
        wellCard = WellnessCard()
        bibleCard = BibleCard()
        bibleChecks = ChecksCard("✅ Bible checks") { g -> g.contains("Bible") }
        meditCard = MeditCard()
        libCard = LibraryCard()
        habitsCard = LifeHabits(this, web, { showPage(2) }, { })
        lifeBible = LifeBible(this, web, { showPage(2) }, { bibleCard?.go?.performClick() }, { needKey() }) { date ->
            study.open(date) { r -> when (r) { "ok" -> showPage(3); "nokey" -> needKey(); else -> android.widget.Toast.makeText(this, "Couldn't open that study", android.widget.Toast.LENGTH_SHORT).show() } } }
        lifeLibrary = LifeLibrary(this, web, { showPage(2) }) { type, cb -> pickCb = cb
            try { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(type), 43) } catch (e: Exception) { pickCb = null } }
        medHeader = LifeMeditate(this, web)
        wellPeriods = PeriodTables("Wellness", "well")
        val listening = { h: String -> h.contains("Reading") || h.contains("Audible") || h.contains("Audiobook") }
        biblePeriods = PeriodTables("Spirituality", "bible", drop = listening)
        libPeriods = PeriodTables("Spirituality", "lib", only = listening)
        reviewCard = ReviewCard()   // on 📝 Review (looking back), not Daily (planning the day)
        journalCard = JournalCard()   // on 📝 Review, above today's review (AI Fill reads it)
        // Weekly · Monthly · Yearly · Review each get their own page (showReviews puts the right card on it).
        weekTab = WeekTabCard(); weekCard = WeeklyCard(); monthCard = PeriodCard("monthly"); yearCard = PeriodCard("yearly")
        dashCard = LifeDashboard(this, web)   // no longer shown: its charts moved under Wellness
        nativeCol.addView(RunUi.text(this, "Bible, Habits, Library, Meditate, Wellness (with the BP report) and the full Command Center are in ☰ More; search, Ask AI, files and settings are in Tools. Your journal and the day's and week's reviews are in 📝 Review.", RunUi.DIM, 11f).apply { setPadding(0, RunUi.dp(this@LifeActivity, 12), 0, 0) })
    }

    /** 📅 The day the Daily screen shows (picked on its calendar); null = today. Other pages always show today. */
    private var viewDate: String? = null
    private fun dayArg() = viewDate?.takeIf { page == 0 && it != java.time.LocalDate.now().toString() }?.let { "," + JSONObject.quote(it) } ?: ""
    private fun pickDay(date: String) {
        currentFocus?.clearFocus()   // saves anything being typed to the day it was typed on
        viewDate = if (date == java.time.LocalDate.now().toString()) null else date
        reload.run()
    }
    private var tries = 0
    private var refreshed = false
    private val reload: Runnable = Runnable {
        // While you're in 📓 Full (maybe looking at another day), don't refresh: the refresh moves the page back to today.
        if (page == 2) return@Runnable
        web.evaluateJavascript("(window.ccNative&&typeof load==='function')?(ccNative.all?ccNative.all(${dayArg().removePrefix(",")}):ccNative.focus()):null") { r ->
            val json = try { (JSONTokener(r).nextValue() as? String)?.let { JSONObject(it) } } catch (e: Exception) { null }
            if (json == null) { focusCard?.loading(); if (tries++ < 60) ui.postDelayed(reload, 700) ; return@evaluateJavascript }
            tries = 0; CcEngine.ready(this@LifeActivity); focusCard?.fill(json); if (page == 0) calendar?.load(); scheduleCard?.fill(json); checksCard?.fill(json); bibleChecks?.fill(json); meditCard?.fill(json); wellCard?.fill(json); bibleCard?.fill(json); libCard?.fill(json); if (page == 6 && section == "habits2") habitsCard?.load(); if (page == 6 && section == "bible") lifeBible?.load(); if (page == 6 && section == "library") lifeLibrary?.load(); medHeader?.fill(json); reviewCard?.fill(json); journalCard?.fill(json); weekCard?.fill(json)
            // An older copy of the Command Center is still cached (the website updates before this screen can use the
            // new parts): fetch the newest once.
            if ((!json.has("checks") || !json.has("schedule") || !json.has("review") || !json.has("wellness") || !json.has("weekly") || !json.has("journal")) && !refreshed) { refreshed = true; web.clearCache(true); web.reload(); ui.postDelayed(reload, 2500) }
        }
    }
    private fun js(call: String) = web.evaluateJavascript("window.ccNative&&$call", null)

    /** A Command Center check box: empty, green ✓, black N/A, purple partly, red not done. */
    private fun paintBox(b: TextView, st: String) {
        val d = RunUi.dp(this, 1)
        val c = when (st) { "green" -> 0xFF22C55E; "purple" -> 0xFFA855F7; "red" -> 0xFFEF4444; "black" -> 0xFF0B0F0D; else -> 0 }.toInt()
        b.background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 8f * d; setColor(c); setStroke(2 * d, if (st.isEmpty()) RunUi.MUTED else if (st == "black") 0xFF6B7280.toInt() else c) }
        b.text = if (st.isEmpty()) "" else "✓"; b.setTextColor(Color.WHITE)
    }
    private val CYCLE = listOf("", "green", "black", "purple", "red")

    /** 🕒 My Schedule — today's timeline, progress and what's next, from the Command Center. */
    private inner class ScheduleCard {
        val view = RunUi.card(this@LifeActivity)
        private val head = RunUi.text(this@LifeActivity, "", RunUi.MUTED, 13f, true)
        private val nextLine = RunUi.text(this@LifeActivity, "", RunUi.ACCENT, 13f, true)
        private val barFill = View(this@LifeActivity)
        private val bar = FrameLayout(this@LifeActivity)
        private val body = LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.VERTICAL }
        private var sig = ""
        private fun d(v: Int) = RunUi.dp(this@LifeActivity, v)
        init {
            view.addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
                addView(RunUi.text(this@LifeActivity, "🕒 My Schedule", Color.WHITE, 16f, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(head) })
            bar.background = RunUi.rounded(0xFF2A3430.toInt(), d(4).toFloat())
            barFill.background = RunUi.rounded(RunUi.ACCENT, d(4).toFloat())
            bar.addView(barFill, FrameLayout.LayoutParams(0, d(8)))
            view.addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, d(8)).apply { topMargin = d(8) })
            view.addView(nextLine.apply { setPadding(0, d(8), 0, d(2)) })
            view.addView(body)
            view.addView(RunUi.text(this@LifeActivity, "Tap a time to change it (just that day or every day). Tap the circle: done → not done → not marked. Add or reorder items in Tools → 📓 Full view.", RunUi.DIM, 10f).apply { setPadding(0, d(8), 0, 0) })
        }
        private fun editTime(o: JSONObject) {
            val id = o.optString("id"); val label = o.optString("label"); val mins = o.optInt("mins")
            val other = viewDate?.takeIf { it != java.time.LocalDate.now().toString() }
            val dayName = other?.let { java.time.LocalDate.parse(it).format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d", java.util.Locale.US)) } ?: "today"
            android.app.TimePickerDialog(this@LifeActivity, { _, h, m ->
                val nm = h * 60 + m
                fun go(scope: String) { js("ccNative.setTime(${JSONObject.quote(id)},$nm,'$scope'${dayArg()})"); sig = "" }   // the page reports the change and the card reloads
                if (!o.optBoolean("everyOk")) { go("day"); return@TimePickerDialog }
                val opts = mutableListOf("Just $dayName", "Every day from ${if (other == null) "today" else dayName} on")
                if (o.optBoolean("changed")) opts += "Back to the usual time"
                android.app.AlertDialog.Builder(this@LifeActivity)
                    .setTitle("$label at ${String.format("%d:%02d %s", if (h % 12 == 0) 12 else h % 12, m, if (h < 12) "AM" else "PM")}")
                    .setItems(opts.toTypedArray()) { _, which -> go(when (which) { 0 -> "day"; 1 -> "every"; else -> "reset" }) }
                    .setNegativeButton("Cancel", null).show()
            }, mins / 60, mins % 60, false).apply { setTitle("Time for $label") }.show()
        }
        private fun paintDot(b: TextView, st: String, partial: Boolean) {
            val c = when { st == "green" -> 0xFF22C55E; st == "red" -> 0xFFEF4444; partial -> 0xFFA855F7; else -> 0 }.toInt()
            b.background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(c); setStroke(d(2), if (c == 0) RunUi.MUTED else c) }
            b.text = when { st == "green" -> "✓"; st == "red" -> "✕"; partial -> "–"; else -> "" }; b.setTextColor(Color.WHITE)
        }
        fun fill(j: JSONObject) {
            val arr = j.optJSONArray("schedule") ?: return
            val items = (0 until arr.length()).map { arr.getJSONObject(it) }
            val track = items.filter { !it.optBoolean("fixed") }; val done = track.count { it.optBoolean("done") }
            head.text = "$done/${track.size}"
            val pct = if (track.isEmpty()) 0f else done.toFloat() / track.size
            bar.post { barFill.layoutParams = FrameLayout.LayoutParams((bar.width * pct).toInt(), d(8)); barFill.requestLayout() }
            val nx = items.firstOrNull { it.optBoolean("next") }
            nextLine.text = nx?.let { "Next: ${it.optString("time")} — ${it.optString("label")}" } ?: if (track.isNotEmpty() && done == track.size) "Schedule complete 🎉" else ""
            val newSig = items.joinToString("|") { it.toString() }
            if (newSig == sig) return; sig = newSig
            body.removeAllViews()
            items.forEach { o ->
                val fixed = o.optBoolean("fixed"); val isNext = o.optBoolean("next")
                val row = LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(d(8), d(7), d(8), d(7))
                    if (isNext) background = RunUi.rounded(0xFF26302C.toInt(), d(10).toFloat()) }
                // Tap the time to change it: just this day, or every day from now on.
                row.addView(RunUi.text(this@LifeActivity, o.optString("time") + if (o.optBoolean("changed")) " ✎" else "", if (isNext) RunUi.ACCENT else RunUi.MUTED, 12f, true).apply {
                    paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
                    setPadding(0, d(6), d(4), d(6))
                    setOnClickListener { editTime(o) }
                }, LinearLayout.LayoutParams(d(72), ViewGroup.LayoutParams.WRAP_CONTENT))
                row.addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.VERTICAL
                    addView(RunUi.text(this@LifeActivity, o.optString("label"), if (fixed) RunUi.MUTED else Color.WHITE, 14f, true))
                    o.optString("detail").takeIf { it.isNotBlank() }?.let { addView(RunUi.text(this@LifeActivity, it, RunUi.DIM, 11f)) }
                    alpha = if (o.optBoolean("done") && !fixed) 0.65f else 1f
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                if (!fixed) {
                    val dot = TextView(this@LifeActivity).apply { gravity = android.view.Gravity.CENTER; textSize = 13f; setTypeface(typeface, android.graphics.Typeface.BOLD) }
                    paintDot(dot, o.optString("state"), o.optBoolean("partial"))
                    val id = o.optString("id"); var st = o.optString("state")
                    dot.setOnClickListener {
                        st = when (st) { "green" -> "red"; "red" -> ""; else -> "green" }; paintDot(dot, st, false)
                        js("ccNative.scheduleTap(${JSONObject.quote(id)}${dayArg()})")
                    }
                    row.addView(dot, LinearLayout.LayoutParams(d(30), d(30)).apply { marginStart = d(8) })
                }
                body.addView(row)
            }
        }
    }

    /** 🤖 Daily AI Review — Wins, What to Improve, Focus for Tomorrow, Lessons Learned, and the Command Center's AI Fill. */
    private inner class ReviewCard {
        val view = RunUi.card(this@LifeActivity)
        private val note = RunUi.text(this@LifeActivity, "", RunUi.MUTED, 12f)
        private val fill = RunUi.button(this@LifeActivity, "✨ AI Fill Day Review", 0xFF7C3AED.toInt(), Color.WHITE) { aiFill() }
        private val save = RunUi.button(this@LifeActivity, "Save Review", RunUi.ACCENT) {
            if (off == 0L) { js("ccNative.saveReview()"); ui.postDelayed(reload, 400) }
            else { edits.forEach { (id, et) -> js("ccNative.setReviewDay(${JSONObject.quote(date().toString())},${JSONObject.quote(id)},${JSONObject.quote(et.text.toString())})") }
                android.widget.Toast.makeText(this@LifeActivity, "Saved", android.widget.Toast.LENGTH_SHORT).show() } }
        private val edits = LinkedHashMap<String, EditText>()
        private var busy = false
        private var off = 0L          // 0 = today, -1 = yesterday …
        private val dayLabel = RunUi.text(this@LifeActivity, "", Color.WHITE, 14f, true).apply { gravity = android.view.Gravity.CENTER }
        private lateinit var nextArrow: TextView
        private fun date() = java.time.LocalDate.now().plusDays(off)
        private fun d(v: Int) = RunUi.dp(this@LifeActivity, v)
        private fun arrow(t: String, n: Int) = RunUi.text(this@LifeActivity, t, RunUi.ACCENT, 22f, true).apply { setPadding(d(14), 0, d(14), 0)
            setOnClickListener { if (off + n <= 0) { edits.values.forEach { it.clearFocus() }; off += n; showDay() } } }
        init {
            view.addView(RunUi.text(this@LifeActivity, "🤖 Daily AI Review", Color.WHITE, 16f, true))
            nextArrow = arrow("›", 1)
            view.addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, d(4), 0, 0)
                addView(arrow("‹", -1)); addView(dayLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); addView(nextArrow) })
            view.addView(note)
            view.addView(fill, LinearLayout.LayoutParams(MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = d(8) })
            listOf("dayRevWins" to "Wins Today", "dayRevImprove" to "What to Improve", "dayRevFocus" to "Focus for Tomorrow", "dayRevLessons" to "Lessons Learned").forEach { (id, label) ->
                view.addView(RunUi.text(this@LifeActivity, label, RunUi.ACCENT, 13f, true).apply { setPadding(0, d(12), 0, d(2)) })
                val et = EditText(this@LifeActivity).apply {
                    setTextColor(Color.WHITE); setHintTextColor(RunUi.DIM); textSize = 14f; minLines = 2; gravity = android.view.Gravity.TOP
                    hint = when (id) { "dayRevWins" -> "What went well today?"; "dayRevImprove" -> "Areas to work on…"; "dayRevFocus" -> "Top priorities…"; else -> "Key takeaways…" }
                    background = RunUi.rounded(0xFF26302C.toInt(), d(10).toFloat()); setPadding(d(10), d(8), d(10), d(8))
                    val commit = Runnable { js(if (off == 0L) "ccNative.setReview(${JSONObject.quote(id)},${JSONObject.quote(text.toString())})"
                        else "ccNative.setReviewDay(${JSONObject.quote(date().toString())},${JSONObject.quote(id)},${JSONObject.quote(text.toString())})") }
                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                        override fun afterTextChanged(s: Editable?) { if (hasFocus()) { ui.removeCallbacks(commit); ui.postDelayed(commit, 1000) } }
                    })
                    setOnFocusChangeListener { _, has -> if (!has) { ui.removeCallbacks(commit); commit.run() } }
                }
                LifeUi.bulletStyle(et); edits[id] = et; view.addView(et)
            }
            view.addView(save, LinearLayout.LayoutParams(MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = d(10) })
            view.addView(RunUi.text(this@LifeActivity, "Typing saves on its own. After ✨ AI Fill, read it over and tap Save Review. It's still filled in automatically at night if you haven't. ‹ › shows earlier days.", RunUi.DIM, 10f).apply { setPadding(0, d(8), 0, 0) })
            paintDay()
        }
        private fun paintDay() {
            val dt = date()
            dayLabel.text = (if (off == 0L) "Today · " else if (off == -1L) "Yesterday · " else "") + dt.format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", java.util.Locale.US))
            nextArrow.alpha = if (off < 0) 1f else 0.25f
            fill.visibility = View.VISIBLE      // earlier days too: written in a hidden copy of the page
            edits.forEach { (id, et) -> et.hint = when (id) { "dayRevWins" -> if (off == 0L) "What went well today?" else "What went well?"; "dayRevImprove" -> "Areas to work on…"; "dayRevFocus" -> "Top priorities…"; else -> "Key takeaways…" } }
        }
        /** Show the chosen day: today comes from the regular refresh; an earlier day is read directly. */
        private fun showDay() {
            paintDay()
            if (off == 0L) { note.visibility = View.GONE; reload.run(); return }
            val dt = date().toString()
            web.evaluateJavascript("window.ccNative&&ccNative.reviewDay?ccNative.reviewDay(${JSONObject.quote(dt)}):null") { r ->
                if (date().toString() != dt) return@evaluateJavascript
                val j = try { (JSONTokener(r).nextValue() as? String)?.let { JSONObject(it) } } catch (e: Exception) { null } ?: return@evaluateJavascript
                val arr = j.optJSONArray("fields") ?: return@evaluateJavascript
                var any = false
                for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); val et = edits[o.optString("id")] ?: continue
                    if (o.optString("value").isNotBlank()) any = true; et.setText(o.optString("value")) }
                note.text = if (any) "An earlier day. Changes you type here save to that day." else "No review was saved for this day. You can type one here."
                note.visibility = View.VISIBLE
            }
        }
        private fun aiFill() {
            if (off != 0L) { pastFill(); return }
            web.evaluateJavascript("window.ccNative&&ccNative.reviewFill?ccNative.reviewFill():false") { r ->
                if (r.contains("nokey")) {
                    android.app.AlertDialog.Builder(this@LifeActivity).setTitle("AI key needed in the Command Center")
                        .setMessage("AI Fill uses the Command Center's own AI key, which stays in each browser and isn't synced. Add it once here: Tools → 📓 Full view → Settings → Anthropic API key.\n\n(BeyondStride's own key stays in the app and is never given to the Command Center.)")
                        .setPositiveButton("Open full view") { _, _ -> show(true) }.setNegativeButton("Not now", null).show()
                    return@evaluateJavascript
                }
                if (r == "true") { busy = true; note.text = "✨ Writing your review…"; fill.isEnabled = false; ui.postDelayed(poll, 2000) }
            }
        }
        /** ✨ AI Fill for an earlier day: the full review (journal, Wellness, Bible, habits, Library, training, food), saved to that day. */
        private fun pastFill() {
            val dt = date().toString()
            edits.values.forEach { it.clearFocus() }
            fill.isEnabled = false; fill.alpha = 0.5f; note.visibility = View.VISIBLE; note.text = "✨ Writing the review for this day… (about 30 seconds)"
            LifeReviewUpgrade.fill(applicationContext, dt) { msg ->
                fill.isEnabled = true; fill.alpha = 1f
                if (date().toString() == dt) showDay()
                when (msg) {
                    "done" -> android.widget.Toast.makeText(this@LifeActivity, "✨ Review written and saved", android.widget.Toast.LENGTH_SHORT).show()
                    "nokey" -> android.app.AlertDialog.Builder(this@LifeActivity).setTitle("AI key needed in the Command Center")
                        .setMessage("AI Fill uses the Command Center's own AI key. Add it once here: Tools → 📓 Full view → Settings → Anthropic API key.")
                        .setPositiveButton("OK", null).show()
                    else -> android.widget.Toast.makeText(this@LifeActivity, "Couldn't write the review ($msg). Try again in a minute.", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
        private val poll: Runnable = Runnable { reload.run(); if (busy) ui.postDelayed(pollAgain, 2000) }
        private val pollAgain: Runnable = Runnable { poll.run() }
        fun fill(j: JSONObject) {
            if (off != 0L) return          // looking at an earlier day: leave it on screen
            paintDay()
            val r = j.optJSONObject("review") ?: return
            val wasBusy = busy; busy = r.optBoolean("busy")
            if (wasBusy && !busy) ui.removeCallbacks(pollAgain)
            fill.isEnabled = !busy; fill.alpha = if (busy) 0.5f else 1f
            val arr = r.optJSONArray("fields") ?: return
            var unsaved = false
            for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); val et = edits[o.optString("id")] ?: continue
                if (o.optBoolean("unsaved")) unsaved = true
                if (!et.hasFocus() && et.text.toString() != o.optString("value")) et.setText(o.optString("value")) }
            note.text = when { busy -> "✨ Writing your review…"; unsaved -> "Not saved yet — tap Save Review when it looks right."; !r.optBoolean("hasKey") -> "AI Fill needs the Command Center's AI key (one time)."; else -> "" }
            note.visibility = if (note.text.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun needKey() {
        // Check what the Command Center in this tab actually has, so the message can say why.
        val diag = "(function(){var n=0;try{for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);n+=k.length+(localStorage.getItem(k)||'').length;}}catch(e){}" +
            "var ok=false;try{localStorage.setItem('__bs_probe','x');localStorage.removeItem('__bs_probe');ok=true;}catch(e){}" +
            "return JSON.stringify({key:!!String(localStorage.getItem('ai_key')||'').trim(),pct:Math.round(n/5242880*100),canSave:ok});})()"
        web.evaluateJavascript(diag) { r ->
            val j = try { JSONObject(JSONTokener(r).nextValue() as String) } catch (e: Exception) { JSONObject() }
            val full = !j.optBoolean("canSave", true) || j.optInt("pct") >= 98
            val msg = when {
                j.optBoolean("key") -> "The key is saved here now — try again."
                full -> "The Command Center's storage in this tab is full (${j.optInt("pct")}%), so the key couldn't be saved. " +
                    "Your data is safe (it's also in Chrome and the cloud copy). Tell Claude \"Life tab storage is full\" and it will make room."
                else -> "This tab doesn't have the key yet. The key typed into Chrome doesn't carry over — add it here:\n\n" +
                    "Open Settings → 📓 Life tab → paste the key → Save key.\n\n(BeyondStride's own key stays in the app and is never given to the Command Center.)"
            }
            android.app.AlertDialog.Builder(this).setTitle(if (j.optBoolean("key")) "AI key found" else "AI key needed in the Command Center").setMessage(msg)
                .setPositiveButton(if (j.optBoolean("key")) "OK" else "Open Settings") { _, _ ->
                    if (!j.optBoolean("key")) startActivity(Intent(this, RunSettingsActivity::class.java).putExtra("page", "life"))
                }.setNegativeButton("Not now", null).show()
        }
    }

    /** 📖 Bible Study + 🧘 Meditation — today's reading, progress, memory verse; the guided session / timer open full screen. */
    private inner class BibleCard {
        val view = RunUi.card(this@LifeActivity)
        private val status = RunUi.text(this@LifeActivity, "", RunUi.ACCENT, 13f, true)
        private val passages = RunUi.text(this@LifeActivity, "", Color.WHITE, 15f, true)
        private val memory = RunUi.text(this@LifeActivity, "", RunUi.MUTED, 12f)
        val go = RunUi.button(this@LifeActivity, "Start study ›", 0xFF7C3AED.toInt(), Color.WHITE) {
            study.open { r -> when (r) {
                "ok" -> showPage(3)
                "nokey" -> needKey()
                // An older Command Center (or a reading it can't find): use its own screen.
                else -> web.evaluateJavascript("window.ccNative&&ccNative.bibleOpen?ccNative.bibleOpen():false") { x -> if (x.contains("nokey")) needKey() else if (x == "true") show(true) }
            } } }
        private fun d(v: Int) = RunUi.dp(this@LifeActivity, v)
        init {
            view.addView(RunUi.text(this@LifeActivity, "📖 Daily Bible Study", Color.WHITE, 16f, true))
            view.addView(status.apply { setPadding(0, d(4), 0, 0) }); view.addView(passages.apply { setPadding(0, d(2), 0, 0) }); view.addView(memory.apply { setPadding(0, d(4), 0, 0) })
            view.addView(go, LinearLayout.LayoutParams(MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = d(8) })
            view.addView(RunUi.text(this@LifeActivity, "Prayer → Read & listen → Study → Worship → Prayer, right here in the app. Ticks your Bible checks as you go.", RunUi.DIM, 10f).apply { setPadding(0, d(6), 0, 0) })
        }
        fun fill(j: JSONObject) {
            j.optJSONObject("bible")?.let { b ->
                val st = b.optString("status")
                status.text = (when (st) { "done" -> "✓ Done today"; "progress" -> "In progress" + (b.optString("step").takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""); else -> "Today" }) + " · " + b.optString("label")
                passages.text = b.optString("passages")
                memory.text = b.optString("memory").takeIf { it.isNotBlank() }?.let { "🧠 Memory verse this week: $it" } ?: ""
                memory.visibility = if (memory.text.isEmpty()) View.GONE else View.VISIBLE
                go.text = when (st) { "done" -> "Review study"; "progress" -> "Continue ›"; else -> "Start study ›" }
            }
        }
    }

    /** 🧘 Meditation — today's check, today's session and this week's total (the timer is right below it). */
    private inner class MeditCard {
        val view = RunUi.card(this@LifeActivity)
        private val medLine = RunUi.text(this@LifeActivity, "", Color.WHITE, 14f)
        private val medWeek = RunUi.text(this@LifeActivity, "", RunUi.MUTED, 12f)
        private val medBox = TextView(this@LifeActivity)
        private var st = ""
        private fun d(v: Int) = RunUi.dp(this@LifeActivity, v)
        init {
            view.addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
                medBox.gravity = android.view.Gravity.CENTER; medBox.textSize = 14f
                medBox.setOnClickListener { st = CYCLE[(CYCLE.indexOf(st) + 1) % CYCLE.size]; paintBox(medBox, st); js("ccNative.setCheck('spMeditation',${JSONObject.quote(st)})") }
                addView(medBox, LinearLayout.LayoutParams(d(28), d(28)))
                addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(d(10), 0, 0, 0)
                    addView(RunUi.text(this@LifeActivity, "🧘 Meditation today", Color.WHITE, 16f, true)); addView(medLine); addView(medWeek) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) })
        }
        fun fill(j: JSONObject) {
            j.optJSONObject("meditation")?.let { m ->
                st = m.optString("state"); paintBox(medBox, st)
                medLine.text = m.optString("line")
                medWeek.text = "This week: ${m.optInt("weekSessions")} session${if (m.optInt("weekSessions") == 1) "" else "s"} · ${m.optInt("weekMins")} min"
            }
        }
    }

    /** ✍️ Daily Journal — today's entry (the AI reviews read it too). */
    private inner class JournalCard {
        val view = RunUi.card(this@LifeActivity)
        private val et = EditText(this@LifeActivity)
        private fun d(v: Int) = RunUi.dp(this@LifeActivity, v)
        private var off = 0L          // 0 = today, -1 = yesterday …
        private fun date() = java.time.LocalDate.now().plusDays(off)
        private val dayLabel = RunUi.text(this@LifeActivity, "", Color.WHITE, 14f, true).apply { gravity = android.view.Gravity.CENTER }
        private lateinit var nextArrow: TextView
        private fun arrow(t: String, n: Int) = RunUi.text(this@LifeActivity, t, RunUi.ACCENT, 22f, true).apply { setPadding(d(14), 0, d(14), 0)
            setOnClickListener { if (off + n <= 0) { et.clearFocus(); off += n; showDay() } } }
        private fun paintDay() {
            dayLabel.text = (if (off == 0L) "Today · " else if (off == -1L) "Yesterday · " else "") + date().format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", java.util.Locale.US))
            nextArrow.alpha = if (off < 0) 1f else 0.25f
            et.hint = if (off == 0L) "Write whatever's on your mind today…" else "Nothing written this day. You can add to it here."
        }
        /** Show the chosen day's entry: today from the regular refresh, an earlier day read directly. */
        private fun showDay() {
            paintDay()
            if (off == 0L) { reload.run(); return }
            val dt = date().toString()
            web.evaluateJavascript("window.ccNative&&ccNative.journalDay?ccNative.journalDay(${JSONObject.quote(dt)}):''") { r ->
                if (date().toString() != dt) return@evaluateJavascript
                val t = try { JSONTokener(r).nextValue() as? String } catch (e: Exception) { null } ?: ""
                if (!et.hasFocus()) et.setText(t)
            }
        }
        init {
            view.addView(RunUi.text(this@LifeActivity, "✍️ Daily Journal", Color.WHITE, 16f, true))
            nextArrow = arrow("›", 1)
            view.addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, d(4), 0, 0)
                addView(arrow("‹", -1)); addView(dayLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); addView(nextArrow) })
            et.apply {
                hint = "Write whatever's on your mind today…"; setTextColor(Color.WHITE); setHintTextColor(RunUi.DIM); textSize = 15f
                minLines = 5; gravity = android.view.Gravity.TOP
                background = RunUi.rounded(0xFF26302C.toInt(), d(10).toFloat()); setPadding(d(12), d(10), d(12), d(10))
                val commit = Runnable { js(if (off == 0L) "ccNative.setJournal(${JSONObject.quote(text.toString())})"
                    else "ccNative.setJournalDay(${JSONObject.quote(date().toString())},${JSONObject.quote(text.toString())})") }
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun afterTextChanged(s: Editable?) { if (hasFocus()) { ui.removeCallbacks(commit); ui.postDelayed(commit, 1200) } }
                })
                setOnFocusChangeListener { _, has -> if (!has) { ui.removeCallbacks(commit); commit.run() } }
            }
            view.addView(et, LinearLayout.LayoutParams(MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = d(8) })
            view.addView(RunUi.text(this@LifeActivity, "Saves as you type, to the day shown. ‹ › shows earlier days. Search and the archive are in Tools → 📓 Full view.", RunUi.DIM, 10f).apply { setPadding(0, d(6), 0, 0) })
            paintDay()
        }
        fun fill(j: JSONObject) {
            if (!j.has("journal")) { view.visibility = View.GONE; return }
            view.visibility = View.VISIBLE
            if (off != 0L) return          // looking at an earlier day: leave it on screen
            paintDay()
            val t = j.optString("journal"); if (!et.hasFocus() && et.text.toString() != t) et.setText(t)
        }
    }

    /** A Command Center summary table (first column and TOTAL row highlighted), scrolling sideways when wide. */
    /** A Command Center summary table, with its colors (status lines in green / amber / red, red "High" pills, colored header dots). */
    private fun ccTable(head: org.json.JSONArray?, rows: org.json.JSONArray?, cells: org.json.JSONArray? = null, headColors: org.json.JSONArray? = null): View {
        val d = { v: Int -> RunUi.dp(this, v) }
        val t = android.widget.TableLayout(this)
        fun css(c: String): Int? {
            if (c.isBlank()) return null
            val m = Regex("rgba?\\((\\d+),\\s*(\\d+),\\s*(\\d+)").find(c)
            val v = if (m != null) Color.rgb(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) else try { Color.parseColor(c) } catch (e: Exception) { return null }
            return v
        }
        // The Command Center's colors are made for a white page; lift dark ones a little so they read on dark cards.
        fun lift(c: Int): Int { val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c); if (maxOf(r, g, b) > 200) return c
            return Color.rgb(r + (255 - r) * 30 / 100, g + (255 - g) * 30 / 100, b + (255 - b) * 30 / 100) }
        head?.let { h -> t.addView(android.widget.TableRow(this).apply {
            for (k in 0 until h.length()) addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(d(6), d(4), d(10), d(4))
                css(headColors?.optString(k) ?: "")?.let { c -> addView(View(this@LifeActivity).apply { background = RunUi.rounded(c, d(4).toFloat()) }, LinearLayout.LayoutParams(d(8), d(8)).apply { marginEnd = d(5) }) }
                addView(RunUi.text(this@LifeActivity, h.optString(k), RunUi.MUTED, 12f, true)) }) }) }
        if (rows != null) for (i in 0 until rows.length()) {
            val r = rows.getJSONArray(i); val total = r.optString(0).let { it.startsWith("TOTAL") || it.startsWith("DAILY AVG") || it.startsWith("AVG") }
            val rc = cells?.optJSONArray(i)
            t.addView(android.widget.TableRow(this).apply {
                for (k in 0 until r.length()) {
                    val base = if (k == 0 || total && rc == null) RunUi.ACCENT else Color.WHITE
                    val lines = rc?.optJSONArray(k)
                    if (lines == null || lines.length() == 0) { addView(RunUi.text(this@LifeActivity, r.optString(k), base, 12f, total || k == 0).apply { setPadding(d(6), d(4), d(10), d(4)) }); continue }
                    addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(d(6), d(4), d(10), d(4))
                        for (li in 0 until lines.length()) { val segs = lines.getJSONArray(li)
                            val pill = (0 until segs.length()).map { segs.getJSONObject(it) }.firstOrNull { it.optString("bg").isNotBlank() }
                            val sb = android.text.SpannableStringBuilder()
                            for (si in 0 until segs.length()) { val o = segs.getJSONObject(si); val st = sb.length; sb.append(o.optString("t"))
                                val col = if (pill != null) Color.WHITE else css(o.optString("c"))?.let { lift(it) } ?: base
                                sb.setSpan(android.text.style.ForegroundColorSpan(col), st, sb.length, 0)
                                if (o.optBoolean("b") || k == 0 || total) sb.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), st, sb.length, 0)
                            }
                            val tv = RunUi.text(this@LifeActivity, "", base, 12f).apply { text = sb; isSingleLine = true }
                            if (pill != null) { tv.background = RunUi.rounded(css(pill.optString("bg")) ?: 0xFFEF4444.toInt(), d(10).toFloat()); tv.setPadding(d(8), d(1), d(8), d(1))
                                addView(tv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = d(2) }) }
                            else addView(tv)
                        } })
                }
            })
        }
        return LifeUi.pinFirstColumn(t)   // dates stay put while the other columns scroll
    }

    /**
     * 📅 Weekly · 📆 Monthly · 🗓️ Yearly tables for one area (💚 Wellness or 📖 Spirituality & Reading), shown in that
     * area's own section (they replaced the separate Weekly / Monthly / Yearly pages). Each opens and closes.
     */
    private inner class PeriodTables(private val keyword: String, private val tag: String, private val only: ((String) -> Boolean)? = null, private val drop: ((String) -> Boolean)? = null) {
        val view = LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.VERTICAL }
        private val offs = intArrayOf(0, 0, 0)
        private fun d(v: Int) = RunUi.dp(this@LifeActivity, v)
        private val group = LifeUi.FoldGroup(this@LifeActivity, listOf(Triple("week", "📅 Weekly — by day", 0), Triple("monthly", "📆 Monthly — by week", 1), Triple("yearly", "🗓️ Yearly — by month", 2)).map { (k, t, i) ->
            LifeUi.Fold("life_${tag}_$k", t, k == "week") { b -> draw(k, i, b) } })
        init { view.addView(group.view) }
        fun load() { group.refresh() }
        private fun draw(kind: String, i: Int, b: LinearLayout) {
            val call = if (kind == "week") "ccNative.weekTab(${offs[i]})" else "ccNative.period('$kind',${offs[i]})"
            web.evaluateJavascript("window.ccNative&&ccNative.period?$call:null") { r ->
                val j = try { (JSONTokener(r).nextValue() as? String)?.let { JSONObject(it) } } catch (e: Exception) { null } ?: return@evaluateJavascript
                b.removeAllViews()
                b.addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, d(8), 0, d(4))
                    addView(RunUi.text(this@LifeActivity, "‹", RunUi.ACCENT, 22f, true).apply { setPadding(d(4), 0, d(14), 0); setOnClickListener { offs[i]--; draw(kind, i, b) } })
                    addView(RunUi.text(this@LifeActivity, j.optString("label"), Color.WHITE, 14f, true).apply { gravity = android.view.Gravity.CENTER; setOnClickListener { offs[i] = 0; draw(kind, i, b) } }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(RunUi.text(this@LifeActivity, "›", RunUi.ACCENT, 22f, true).apply { setPadding(d(14), 0, d(4), 0); setOnClickListener { if (offs[i] < 0) { offs[i]++; draw(kind, i, b) } } }) })
                // 🎧 Library: this week's audiobook goal bar (also on 📝 Review).
                if (kind == "week" && tag == "lib") j.optJSONArray("goals")?.let { g -> for (q in 0 until g.length()) { val o = g.getJSONObject(q)
                    if (!o.optString("label").contains("Audio")) continue
                    b.addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, d(6), 0, d(3))
                        addView(RunUi.text(this@LifeActivity, "🎧 " + o.optString("label") + " goal", Color.WHITE, 13f, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                        addView(RunUi.text(this@LifeActivity, o.optString("value").replace(Regex("^-\\s*/"), "0m /") + (if (o.optBoolean("reached")) "  ✓" else ""), if (o.optBoolean("reached")) 0xFF22C55E.toInt() else RunUi.MUTED, 12f, true)) })
                    val bar = FrameLayout(this@LifeActivity).apply { background = RunUi.rounded(0xFF2A3430.toInt(), d(4).toFloat()) }
                    val fillV = View(this@LifeActivity).apply { background = RunUi.rounded(if (o.optBoolean("reached")) 0xFF22C55E.toInt() else 0xFF818CF8.toInt(), d(4).toFloat()) }
                    bar.addView(fillV, FrameLayout.LayoutParams(0, d(8))); b.addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, d(8)))
                    val pct = o.optInt("pct"); bar.post { fillV.layoutParams = FrameLayout.LayoutParams(bar.width * pct / 100, d(8)); fillV.requestLayout() } } }
                val secs = j.optJSONArray("sections"); var any = false
                if (secs != null) for (k in 0 until secs.length()) { val sec = secs.getJSONObject(k)
                    if (!sec.optString("title").contains(keyword)) continue
                    // Keep the Period column plus the columns this area shows (Reading / Audible goes to Library, not Bible).
                    val head = sec.optJSONArray("head") ?: org.json.JSONArray()
                    val keep = (0 until head.length()).filter { c -> c == 0 || head.optString(c).let { h -> (only == null || only.invoke(h)) && (drop == null || !drop.invoke(h)) } }
                    if (keep.size <= 1) continue; any = true
                    fun pick(a: org.json.JSONArray?) = a?.let { arr -> org.json.JSONArray().apply { keep.forEach { put(arr.opt(it)) } } }
                    fun pickRows(a: org.json.JSONArray?) = a?.let { arr -> org.json.JSONArray().apply { for (r in 0 until arr.length()) put(pick(arr.optJSONArray(r))) } }
                    b.addView(ccTable(pick(head), pickRows(sec.optJSONArray("rows")), pickRows(sec.optJSONArray("cells")), pick(sec.optJSONArray("headColors"))))
                }
                if (!any) b.addView(RunUi.text(this@LifeActivity, "Nothing recorded for this period.", RunUi.MUTED, 12f))
            }
        }
    }

    /** 📊 Weekly — goal progress bars (set your targets here), the week's numbers and its Fitness / Wellness / Spirituality / Nutrition tables. */
    private inner class WeekTabCard {
        val view = RunUi.card(this@LifeActivity)
        private var off = 0
        private var targets = JSONObject()
        private val label = RunUi.text(this@LifeActivity, "", Color.WHITE, 14f, true).apply { gravity = android.view.Gravity.CENTER }
        private val body = LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.VERTICAL }
        private fun d(v: Int) = RunUi.dp(this@LifeActivity, v)
        private fun arrow(t: String, n: Int) = RunUi.text(this@LifeActivity, t, RunUi.ACCENT, 22f, true).apply { setPadding(d(14), 0, d(14), 0); setOnClickListener { off += n; load() } }
        init {
            view.addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
                addView(RunUi.text(this@LifeActivity, "📊 This Week", Color.WHITE, 16f, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(RunUi.text(this@LifeActivity, "⚙️ Goals", 0xFFA78BFA.toInt(), 13f, true).apply { setPadding(d(8), d(4), 0, d(4)); setOnClickListener { goals() } }) })
            view.addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, d(4), 0, 0)
                addView(arrow("‹", -1)); addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); addView(arrow("›", 1)) })
            view.addView(body)
        }
        fun load() {
            web.evaluateJavascript("window.ccNative&&ccNative.weekTab?ccNative.weekTab($off):null") { r ->
                val j = try { (JSONTokener(r).nextValue() as? String)?.let { JSONObject(it) } } catch (e: Exception) { null } ?: run { view.visibility = View.GONE; return@evaluateJavascript }
                view.visibility = View.VISIBLE; fill(j)
            }
        }
        private fun fill(j: JSONObject) {
            label.text = j.optString("label"); targets = j.optJSONObject("targets") ?: JSONObject(); body.removeAllViews()
            // One weekly miles goal: Activities → Mileage's goal is the one; the Command Center's "Miles" follows it.
            run { val act = ActivityStore.goal(this@LifeActivity, "week"); val cc = targets.optDouble("Miles", 0.0).let { if (it.isNaN()) 0.0 else it }
                if (act > 0 && kotlin.math.abs(act - cc) > 0.01) { web.evaluateJavascript("window.ccNative&&ccNative.setGoals(${JSONObject.quote(JSONObject().put("Miles", act).toString())})") { load() }; return }
                if (act <= 0 && cc > 0) ActivityStore.saveSettings(this@LifeActivity, JSONObject().put("goal_week", cc)) }
            val g = j.optJSONArray("goals")
            if (g == null || g.length() == 0) body.addView(RunUi.text(this@LifeActivity, "Tap ⚙️ Goals to set weekly targets (miles, Bible days, prayer days, run days, audiobook minutes).", RunUi.MUTED, 12f).apply { setPadding(0, d(6), 0, 0) })
            else for (i in 0 until g.length()) { val o = g.getJSONObject(i); val pct = o.optInt("pct")
                body.addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, d(8), 0, d(3))
                    addView(RunUi.text(this@LifeActivity, o.optString("label"), Color.WHITE, 13f, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(RunUi.text(this@LifeActivity, o.optString("value") + (if (o.optBoolean("reached")) "  ✓" else ""), if (o.optBoolean("reached")) 0xFF22C55E.toInt() else RunUi.MUTED, 12f, true)) })
                val bar = FrameLayout(this@LifeActivity).apply { background = RunUi.rounded(0xFF2A3430.toInt(), d(4).toFloat()) }
                val fillV = View(this@LifeActivity).apply { background = RunUi.rounded(if (o.optBoolean("reached")) 0xFF22C55E.toInt() else 0xFF818CF8.toInt(), d(4).toFloat()) }
                bar.addView(fillV, FrameLayout.LayoutParams(0, d(8)))
                body.addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, d(8)))
                bar.post { fillV.layoutParams = FrameLayout.LayoutParams(bar.width * pct / 100, d(8)); fillV.requestLayout() }
            }
            j.optString("digest").takeIf { it.isNotBlank() }?.let { body.addView(RunUi.text(this@LifeActivity, it, RunUi.MUTED, 12f).apply { setPadding(0, d(10), 0, 0) }) }
            val secs = j.optJSONArray("sections") ?: return
            // The weekly / monthly / yearly tables now live in their own areas.
            body.addView(RunUi.text(this@LifeActivity, "Weekly, monthly and yearly tables: ☰ More → Wellness and Bible · Activities → Mileage · Nutrition.", RunUi.DIM, 11f).apply { setPadding(0, d(10), 0, 0) })
            if (secs.length() >= 0) return
            for (i in 0 until secs.length()) { val sec = secs.getJSONObject(i)
                // Running numbers live in Activities and food in Nutrition now (each number in one place).
                if (sec.optString("title").let { it.contains("Fitness") || it.contains("Nutrition") || it.contains("Micronutrients") }) continue
                body.addView(RunUi.text(this@LifeActivity, sec.optString("title"), RunUi.ACCENT, 13f, true).apply { setPadding(0, d(12), 0, d(4)) })
                body.addView(ccTable(sec.optJSONArray("head"), sec.optJSONArray("rows"), sec.optJSONArray("cells"), sec.optJSONArray("headColors")))
            }
        }
        private fun goals() {
            val box = LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(d(20), d(8), d(20), 0) }
            val fields = listOf("Miles" to "Miles / week (same goal as Activities → Mileage)", "Bible" to "Bible days (of 7)", "Prayer" to "Prayer days (of 7)", "Exercise" to "Run days (your plan's count is used when there is one)", "Audible" to "Audiobook minutes / week")
            val edits = fields.map { (k, l) ->
                box.addView(RunUi.text(this@LifeActivity, l, Color.GRAY, 12f).apply { setPadding(0, d(8), 0, 0) })
                EditText(this@LifeActivity).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                    targets.optDouble(k).takeIf { !it.isNaN() && it > 0 }?.let { setText(if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()) }; hint = "none" }.also { box.addView(it) } to k
            }
            android.app.AlertDialog.Builder(this@LifeActivity).setTitle("Weekly goals").setView(android.widget.ScrollView(this@LifeActivity).apply { addView(box) })
                .setPositiveButton("Save") { _, _ ->
                    val o = JSONObject(); edits.forEach { (et, k) -> o.put(k, et.text.toString().toDoubleOrNull() ?: 0.0) }
                    ActivityStore.saveSettings(this@LifeActivity, JSONObject().put("goal_week", o.optDouble("Miles", 0.0)))   // same goal as Activities → Mileage
                    web.evaluateJavascript("window.ccNative&&ccNative.setGoals(${JSONObject.quote(o.toString())})") { load() }
                }.setNegativeButton("Cancel", null).show()
        }
    }

    /** 📆 Monthly / 📅 Yearly — the habit heat map (month) and the Fitness / Wellness / Spirituality tables, ‹ › to move. */
    private inner class PeriodCard(val kind: String) {
        val view = RunUi.card(this@LifeActivity)
        private var off = 0
        private val label = RunUi.text(this@LifeActivity, "", Color.WHITE, 14f, true).apply { gravity = android.view.Gravity.CENTER }
        private val body = LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.VERTICAL }
        private fun d(v: Int) = RunUi.dp(this@LifeActivity, v)
        private fun arrow(t: String, n: Int) = RunUi.text(this@LifeActivity, t, RunUi.ACCENT, 22f, true).apply { setPadding(d(14), 0, d(14), 0); setOnClickListener { off += n; load() } }
        init {
            view.addView(RunUi.text(this@LifeActivity, if (kind == "monthly") "📆 Monthly" else "🗓️ Yearly", Color.WHITE, 16f, true))
            view.addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, d(4), 0, 0)
                addView(arrow("‹", -1)); addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); addView(arrow("›", 1)) })
            view.addView(body)
        }
        fun load() {
            web.evaluateJavascript("window.ccNative&&ccNative.period?ccNative.period('$kind',$off):null") { r ->
                val j = try { (JSONTokener(r).nextValue() as? String)?.let { JSONObject(it) } } catch (e: Exception) { null } ?: return@evaluateJavascript
                fill(j)
            }
        }
        private fun fill(j: JSONObject) {
            label.text = j.optString("label"); body.removeAllViews()
            // (The habit completion calendar was removed from BeyondStride's Monthly page at Micheal's request; still on the website.)
            val secs = j.optJSONArray("sections") ?: return
            body.addView(RunUi.text(this@LifeActivity, "🏃 Miles by day / week / month are in Activities → Mileage · 🍎 food by day is in Nutrition → By day.", RunUi.DIM, 11f).apply { setPadding(0, d(10), 0, 0) })
            for (i in 0 until secs.length()) { val sec = secs.getJSONObject(i)
                // Running numbers live in Activities and food in Nutrition now (each number in one place).
                if (sec.optString("title").let { it.contains("Fitness") || it.contains("Nutrition") || it.contains("Micronutrients") }) continue
                body.addView(RunUi.text(this@LifeActivity, sec.optString("title"), RunUi.ACCENT, 13f, true).apply { setPadding(0, d(12), 0, d(4)) })
                body.addView(ccTable(sec.optJSONArray("head"), sec.optJSONArray("rows"), sec.optJSONArray("cells"), sec.optJSONArray("headColors")))
            }
        }
        private fun heat(h: String) {
            body.addView(RunUi.text(this@LifeActivity, "Habit completion (Bible audio · prayer · exercise)", RunUi.MUTED, 12f).apply { setPadding(0, d(8), 0, d(4)) })
            val grid = android.widget.GridLayout(this@LifeActivity).apply { columnCount = 7 }
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { grid.addView(RunUi.text(this@LifeActivity, it, RunUi.DIM, 11f, true).apply { gravity = android.view.Gravity.CENTER }, android.widget.GridLayout.LayoutParams().apply { width = d(40); height = d(20) }) }
            var day = 0
            h.forEach { c ->
                val cell = TextView(this@LifeActivity).apply { gravity = android.view.Gravity.CENTER; textSize = 11f }
                if (c != 'e') { day++; cell.text = day.toString() }
                val col = when (c) { 'e' -> 0; 'f' -> 0xFF1F2725.toInt(); '1' -> 0xFF365314.toInt(); '2' -> 0xFF65A30D.toInt(); '3' -> 0xFFA3E635.toInt(); else -> 0xFF2A3430.toInt() }
                cell.setTextColor(if (c == '3') Color.BLACK else if (c == 'f') RunUi.DIM else Color.WHITE)
                if (col != 0) cell.background = RunUi.rounded(col, d(6).toFloat())
                grid.addView(cell, android.widget.GridLayout.LayoutParams().apply { width = d(36); height = d(30); setMargins(d(2), d(2), d(2), d(2)) })
            }
            body.addView(grid)
        }
        private fun table(head: org.json.JSONArray?, rows: org.json.JSONArray?): View {
            val t = android.widget.TableLayout(this@LifeActivity)
            fun row(cells: org.json.JSONArray, header: Boolean, total: Boolean) = android.widget.TableRow(this@LifeActivity).apply {
                for (k in 0 until cells.length()) addView(RunUi.text(this@LifeActivity, cells.optString(k), if (header) RunUi.MUTED else if (k == 0 || total) RunUi.ACCENT else Color.WHITE, 12f, header || total || k == 0).apply { setPadding(d(6), d(4), d(10), d(4)) })
            }
            head?.let { t.addView(row(it, true, false)) }
            if (rows != null) for (i in 0 until rows.length()) { val r = rows.getJSONArray(i); t.addView(row(r, false, r.optString(0).startsWith("TOTAL"))) }
            return android.widget.HorizontalScrollView(this@LifeActivity).apply { addView(t) }
        }
    }

    /** 📅 Weekly Review — this week's numbers, Wins / Improve / Focus / Lessons, AI Fill; ‹ › for other weeks. */
    private inner class WeeklyCard {
        val view = RunUi.card(this@LifeActivity)
        private val label = RunUi.text(this@LifeActivity, "", Color.WHITE, 14f, true).apply { gravity = android.view.Gravity.CENTER }
        private val stats = RunUi.text(this@LifeActivity, "", RunUi.MUTED, 12f)
        private val note = RunUi.text(this@LifeActivity, "", RunUi.MUTED, 12f)
        private val fillBtn = RunUi.button(this@LifeActivity, "✨ AI Fill Review", 0xFF7C3AED.toInt(), Color.WHITE) { aiFill() }
        private val edits = LinkedHashMap<String, EditText>()
        private var busy = false
        private fun d(v: Int) = RunUi.dp(this@LifeActivity, v)
        private fun arrow(t: String, n: Int) = RunUi.text(this@LifeActivity, t, RunUi.ACCENT, 22f, true).apply { setPadding(d(14), 0, d(14), 0)
            setOnClickListener { web.evaluateJavascript("window.ccNative&&ccNative.weeklyShift($n)") { reload.run() } } }
        init {
            view.addView(RunUi.text(this@LifeActivity, "📅 Weekly Review", Color.WHITE, 16f, true))
            view.addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, d(4), 0, 0)
                addView(arrow("‹", -1)); addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); addView(arrow("›", 1)) })
            view.addView(stats); view.addView(note)
            view.addView(fillBtn, LinearLayout.LayoutParams(MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = d(8) })
            listOf("revWins" to "Wins This Week", "revImprove" to "What to Improve", "revFocus" to "Focus for Next Week", "revLessons" to "Lessons Learned").forEach { (id, lab) ->
                view.addView(RunUi.text(this@LifeActivity, lab, RunUi.ACCENT, 13f, true).apply { setPadding(0, d(12), 0, d(2)) })
                val et = EditText(this@LifeActivity).apply {
                    setTextColor(Color.WHITE); setHintTextColor(RunUi.DIM); textSize = 14f; minLines = 2; gravity = android.view.Gravity.TOP; hint = "…"
                    background = RunUi.rounded(0xFF26302C.toInt(), d(10).toFloat()); setPadding(d(10), d(8), d(10), d(8))
                    val commit = Runnable { js("ccNative.setWeekly(${JSONObject.quote(id)},${JSONObject.quote(text.toString())})") }
                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                        override fun afterTextChanged(s: Editable?) { if (hasFocus()) { ui.removeCallbacks(commit); ui.postDelayed(commit, 1000) } }
                    })
                    setOnFocusChangeListener { _, has -> if (!has) { ui.removeCallbacks(commit); commit.run() } }
                }
                LifeUi.bulletStyle(et); edits[id] = et; view.addView(et)
            }
            view.addView(RunUi.button(this@LifeActivity, "Save Review", RunUi.ACCENT) { js("ccNative.saveWeekly()"); ui.postDelayed(reload, 400) },
                LinearLayout.LayoutParams(MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = d(10) })
            view.addView(RunUi.text(this@LifeActivity, "Typing here saves the week's review. After ✨ AI Fill, read it over and tap Save Review. Charts and the injury roll-up are in Tools → 📓 Full view → Review.", RunUi.DIM, 10f).apply { setPadding(0, d(8), 0, 0) })
        }
        private fun aiFill() {
            web.evaluateJavascript("window.ccNative&&ccNative.weeklyFill?ccNative.weeklyFill():false") { r ->
                if (r.contains("nokey")) needKey() else if (r == "true") { busy = true; note.text = "✨ Writing the week's review…"; fillBtn.isEnabled = false; ui.postDelayed(poll, 2500) } }
        }
        private val poll: Runnable = object : Runnable { override fun run() { reload.run(); if (busy) ui.postDelayed(this, 2500) } }
        fun fill(j: JSONObject) {
            val w = j.optJSONObject("weekly") ?: run { view.visibility = View.GONE; return }
            view.visibility = View.VISIBLE
            label.text = w.optString("label"); stats.text = w.optString("stats")
            busy = w.optBoolean("busy"); fillBtn.isEnabled = !busy; fillBtn.alpha = if (busy) 0.5f else 1f
            val arr = w.optJSONArray("fields") ?: return; var unsaved = false
            for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); val et = edits[o.optString("id")] ?: continue
                if (o.optBoolean("unsaved")) unsaved = true
                if (!et.hasFocus() && et.text.toString() != o.optString("value")) et.setText(o.optString("value")) }
            note.text = when { busy -> "✨ Writing the week's review…"; unsaved -> "Not saved yet — tap Save Review when it looks right."; else -> "" }
            note.visibility = if (note.text.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    /** 🌱 Habits — today's habits with the same controls as the Habits tab (complete / partial / skip, − +, automatic ones). */
    private inner class HabitsCard {
        val view = RunUi.card(this@LifeActivity)
        private val head = RunUi.text(this@LifeActivity, "", RunUi.MUTED, 13f, true)
        private val body = LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.VERTICAL }
        private var sig = ""
        private fun d(v: Int) = RunUi.dp(this@LifeActivity, v)
        init {
            view.addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
                addView(RunUi.text(this@LifeActivity, "🌱 Habits", Color.WHITE, 16f, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(head) })
            view.addView(body)
            view.addView(RunUi.text(this@LifeActivity, "Adding, editing and history are in 📓 Full → Habits.", RunUi.DIM, 10f).apply { setPadding(0, d(8), 0, 0) })
        }
        fun load() {
            web.evaluateJavascript("window.ccNative&&ccNative.habits?ccNative.habits():null") { r ->
                val j = try { (JSONTokener(r).nextValue() as? String)?.let { JSONObject(it) } } catch (e: Exception) { null } ?: run { view.visibility = View.GONE; return@evaluateJavascript }
                fill(j)
            }
        }
        private fun pill(t: String, on: Boolean, color: Int, click: () -> Unit) = RunUi.text(this@LifeActivity, t, if (on) Color.BLACK else Color.WHITE, 12f, true).apply {
            setPadding(d(10), d(5), d(10), d(5)); background = RunUi.rounded(if (on) color else 0xFF2A3430.toInt(), d(14).toFloat()); setOnClickListener { click() } }
        private fun act(js: String) { web.evaluateJavascript("window.ccNative&&$js") { load(); ui.postDelayed(reload, 300) } }
        private fun fill(j: JSONObject) {
            val list = j.optJSONArray("list") ?: org.json.JSONArray()
            view.visibility = if (list.length() == 0) View.GONE else View.VISIBLE
            head.text = "${j.optInt("done")}/${j.optInt("due")} today" + (if (j.optInt("best") > 0) " · 🔥 ${j.optInt("best")}" else "")
            if (list.toString() == sig) return; sig = list.toString(); body.removeAllViews()
            for (i in 0 until list.length()) { val h = list.getJSONObject(i); val id = JSONObject.quote(h.optString("id"))
                val due = h.optBoolean("due"); val done = h.optBoolean("done"); val st = h.optString("status")
                body.addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(0, d(10), 0, 0); alpha = if (due || done) 1f else 0.55f
                    addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
                        addView(RunUi.text(this@LifeActivity, (if (done) "✓ " else "") + h.optString("name"), if (done) 0xFF22C55E.toInt() else Color.WHITE, 14f, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                        if (h.optInt("streak") > 0) addView(RunUi.text(this@LifeActivity, "🔥 ${h.optInt("streak")}", RunUi.MUTED, 12f)) })
                    addView(RunUi.text(this@LifeActivity, listOf(h.optString("category"), h.optString("schedule"), h.optString("type")).filter { it.isNotBlank() }.joinToString(" · ") + (if (!due && !done) " · not due today" else ""), RunUi.DIM, 11f))
                    addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, d(5), 0, 0)
                        when (h.optString("kind")) {
                            "check" -> {
                                addView(pill("✓ Complete", done, 0xFF22C55E.toInt()) { act("ccNative.habitLog($id,'done')") }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = d(6) })
                                addView(pill("Partial", st == "partial", 0xFFA855F7.toInt()) { act("ccNative.habitLog($id,'partial')") }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = d(6) })
                                addView(pill("Skip", st == "skip", 0xFF9AA3A0.toInt()) { act("ccNative.habitLog($id,'skip')") })
                            }
                            "count" -> {
                                addView(pill("−", false, 0) { act("ccNative.habitAdjust($id,-1)") })
                                addView(RunUi.text(this@LifeActivity, h.optString("value"), Color.WHITE, 13f, true).apply { setPadding(d(12), 0, d(12), 0) })
                                addView(pill("+", false, 0) { act("ccNative.habitAdjust($id,1)") })
                            }
                            else -> addView(RunUi.text(this@LifeActivity, (h.optString("value").ifBlank { "Automatic" }) + " · fills in on its own", RunUi.MUTED, 12f))
                        } })
                })
            }
        }
    }

    /** 🎧 Library — today's audiobook (what + time; 30+ min checks it off), the finish-by plan and your finished books. */
    private inner class LibraryCard {
        val view = RunUi.card(this@LifeActivity)
        private val box = TextView(this@LifeActivity)
        private var st = ""
        private val what = EditText(this@LifeActivity); private val time = EditText(this@LifeActivity)
        private val plan = RunUi.text(this@LifeActivity, "", RunUi.MUTED, 12f)
        private val books = LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.VERTICAL }
        private val booksHead = RunUi.text(this@LifeActivity, "", RunUi.ACCENT, 13f, true)
        private var sig = ""
        private fun d(v: Int) = RunUi.dp(this@LifeActivity, v)
        private fun field(et: EditText, hint: String, id: String, number: Boolean) = et.apply {
            this.hint = hint; setTextColor(Color.WHITE); setHintTextColor(RunUi.DIM); textSize = 14f; isSingleLine = true
            background = RunUi.rounded(0xFF26302C.toInt(), d(8).toFloat()); setPadding(d(10), d(7), d(10), d(7))
            if (number) inputType = android.text.InputType.TYPE_CLASS_DATETIME
            imeOptions = EditorInfo.IME_ACTION_DONE
            val commit = Runnable { js("ccNative.setListen(${JSONObject.quote(id)},${JSONObject.quote(text.toString())})") }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { if (hasFocus()) { ui.removeCallbacks(commit); ui.postDelayed(commit, 900) } }
            })
            setOnFocusChangeListener { _, has -> if (!has) { ui.removeCallbacks(commit); commit.run(); ui.postDelayed(reload, 9000) } }
            setOnEditorActionListener { v, _, _ -> v.clearFocus(); false }
        }
        init {
            view.addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
                box.gravity = android.view.Gravity.CENTER; box.textSize = 14f
                box.setOnClickListener { st = CYCLE[(CYCLE.indexOf(st) + 1) % CYCLE.size]; paintBox(box, st); js("ccNative.setCheck('gaAudiobook',${JSONObject.quote(st)})") }
                addView(box, LinearLayout.LayoutParams(d(28), d(28)))
                addView(RunUi.text(this@LifeActivity, "🎧 Today's Listening", Color.WHITE, 16f, true).apply { setPadding(d(10), 0, 0, 0) }) })
            view.addView(field(what, "What are you listening to?", "gaAudiobookText", false), LinearLayout.LayoutParams(MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = d(8) })
            view.addView(field(time, "Time listened today (45 or 1:10:00)", "gaAudiobookTime", true), LinearLayout.LayoutParams(MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = d(6) })
            view.addView(RunUi.text(this@LifeActivity, "30+ minutes checks it off (and goals like \"Audible for 30 minutes\").", RunUi.DIM, 10f).apply { setPadding(0, d(4), 0, 0) })
            view.addView(plan.apply { setPadding(0, d(8), 0, 0) })
            view.addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, d(12), 0, d(2))
                addView(booksHead, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(RunUi.text(this@LifeActivity, "+ Add Book", 0xFFA78BFA.toInt(), 13f, true).apply { setPadding(d(8), d(4), 0, d(4)); setOnClickListener { addBook() } }) })
            view.addView(books)
        }
        fun fill(j: JSONObject) {
            val l = j.optJSONObject("library") ?: run { view.visibility = View.GONE; return }
            view.visibility = View.VISIBLE
            st = l.optString("state"); paintBox(box, st)
            if (!what.hasFocus() && what.text.toString() != l.optString("text")) what.setText(l.optString("text"))
            if (!time.hasFocus() && time.text.toString() != l.optString("time")) time.setText(l.optString("time"))
            plan.text = l.optString("plan").takeIf { it.isNotBlank() }?.let { "📅 $it" } ?: ""; plan.visibility = if (plan.text.isEmpty()) View.GONE else View.VISIBLE
            booksHead.text = "📚 Books finished: ${l.optInt("year")} this year · ${l.optInt("count")} in your library"
            val rec = l.optJSONArray("recent") ?: org.json.JSONArray()
            if (rec.toString() == sig) return; sig = rec.toString(); books.removeAllViews()
            for (i in 0 until rec.length()) { val b = rec.getJSONObject(i)
                books.addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, d(5), 0, d(5))
                    addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.VERTICAL
                        addView(RunUi.text(this@LifeActivity, b.optString("title"), Color.WHITE, 14f, true))
                        addView(RunUi.text(this@LifeActivity, listOf(b.optString("author"), b.optString("date")).filter { it.isNotBlank() }.joinToString(" · "), RunUi.MUTED, 11f)) },
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(RunUi.text(this@LifeActivity, "★".repeat(b.optInt("rating").coerceIn(0, 5)), 0xFFF59E0B.toInt(), 13f)) })
            }
        }
        private fun addBook() {
            val b = LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(d(20), d(8), d(20), 0) }
            val t = EditText(this@LifeActivity).apply { hint = "Title" }; val a = EditText(this@LifeActivity).apply { hint = "Author" }
            val date = EditText(this@LifeActivity).apply { hint = "Date finished (yyyy-mm-dd)"; setText(java.time.LocalDate.now().toString()) }
            val stars = android.widget.RatingBar(this@LifeActivity).apply { numStars = 5; stepSize = 1f }
            b.addView(t); b.addView(a); b.addView(date); b.addView(stars, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            android.app.AlertDialog.Builder(this@LifeActivity).setTitle("Add a finished book").setView(b)
                .setPositiveButton("Save") { _, _ -> if (t.text.isNotBlank()) web.evaluateJavascript("window.ccNative&&ccNative.addBook(${JSONObject.quote(t.text.toString())},${JSONObject.quote(a.text.toString())},${JSONObject.quote(date.text.toString())},${stars.rating.toInt()})") { reload.run() } }
                .setNegativeButton("Cancel", null).show()
        }
    }

    /**
     * ❤️ Wellness — laid out like the Command Center's Wellness page: BP (💓 pulse, 🫀 morning HR), Meds, Weight, Sleep
     * (⌛ score + 😴 BeyondStride's night), 🧘 Daily Movement (dog walk, steps), then the 60-day BP & pulse and weight charts.
     */
    private inner class WellnessCard {
        val view = LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.VERTICAL }
        private val card = RunUi.card(this@LifeActivity)
        private val boxes = HashMap<String, TextView>(); private val states = HashMap<String, String>(); private val edits = HashMap<String, EditText>()
        private val dateText = RunUi.text(this@LifeActivity, "", RunUi.MUTED, 12f)
        private val sleepDetail = RunUi.text(this@LifeActivity, "", RunUi.MUTED, 12f)
        private val moveCount = RunUi.text(this@LifeActivity, "", RunUi.MUTED, 13f, true)
        private val charts = LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.VERTICAL }
        private var built = false
        private var chartsAt = 0L
        private fun d(v: Int) = RunUi.dp(this@LifeActivity, v)
        private val RED = 0xFFF87171.toInt(); private val GREEN = 0xFF4ADE80.toInt()
        init {
            card.addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
                addView(RunUi.text(this@LifeActivity, "❤️ Wellness", RED, 18f, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(dateText) })
            view.addView(card); view.addView(charts)
        }
        private fun field(id: String, hint: String, number: Boolean) = EditText(this@LifeActivity).apply {
            this.hint = hint; setTextColor(Color.WHITE); setHintTextColor(RunUi.DIM); textSize = 15f; isSingleLine = true
            inputType = if (number) android.text.InputType.TYPE_CLASS_NUMBER else android.text.InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_DONE
            background = RunUi.rounded(0xFF26302C.toInt(), d(8).toFloat()); setPadding(d(10), d(6), d(10), d(6))
            val commit = Runnable { js("ccNative.setField(${JSONObject.quote(id)},${JSONObject.quote(text.toString())})") }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { if (hasFocus()) { ui.removeCallbacks(commit); ui.postDelayed(commit, 900) } }
            })
            setOnFocusChangeListener { _, has -> if (!has) { ui.removeCallbacks(commit); commit.run(); ui.postDelayed(reload, 1200)
                // One weight: typed here, it's also today's wake-up weight in Nutrition (sweat rate, Coach, charts).
                if (id == "wWghtVal") text.toString().trim().toDoubleOrNull()?.takeIf { it in 70.0..600.0 }?.let { w ->
                    val app = applicationContext
                    Thread { try { HydrationSync.change(app, "weight", value = w, date = java.time.LocalDate.now().toString()) } catch (e: Exception) { } }.start() } } }
            setOnEditorActionListener { v, _, _ -> v.clearFocus(); false }
        }.also { edits[id] = it }
        /** A row: [box] Label: [field] — or, for a sub-row (no box), indented with its icon. */
        private fun row(key: String?, label: String, labelColor: Int, id: String, hint: String, number: Boolean = false) {
            card.addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(if (key == null) d(38) else 0, d(5), 0, d(5))
                if (key != null) {
                    val box = TextView(this@LifeActivity).apply { gravity = android.view.Gravity.CENTER; textSize = 14f; setTypeface(typeface, android.graphics.Typeface.BOLD)
                        setOnClickListener { val n = CYCLE[(CYCLE.indexOf(states[key] ?: "") + 1) % CYCLE.size]; states[key] = n; paintBox(this, n); js("ccNative.setCheck(${JSONObject.quote(key)},${JSONObject.quote(n)})") } }
                    boxes[key] = box; paintBox(box, ""); addView(box, LinearLayout.LayoutParams(d(28), d(28)).apply { marginEnd = d(10) })
                }
                addView(RunUi.text(this@LifeActivity, label, labelColor, if (key == null) 14f else 15f, key != null).apply { setPadding(0, 0, d(8), 0) })
                addView(field(id, hint, number), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) })
        }
        private fun build() {
            built = true
            row("wBP", "BP:", Color.WHITE, "wBPVal", "120/80")
            row(null, "💓 Pulse:", RunUi.MUTED, "wPulseVal", "bpm", true)
            row(null, "🫀 Morning HR:", RunUi.MUTED, "wRestHRVal", "bpm", true)
            row("wMeds", "Meds:", Color.WHITE, "wMedsVal", "HCTZ, Vitamins…")
            row("wWght", "Weight:", Color.WHITE, "wWghtVal", "lbs")
            row("wSleep", "Sleep:", Color.WHITE, "wSleepVal", "hrs (7.5 or 6:30)")
            row(null, "⌛ Sleep Score:", RunUi.MUTED, "wSleepScore", "0-100", true)
            card.addView(sleepDetail.apply { setPadding(d(38), d(2), 0, d(6)) })
            card.addView(View(this@LifeActivity).apply { setBackgroundColor(0xFF2A3430.toInt()) }, LinearLayout.LayoutParams(MATCH_PARENT, d(1)).apply { topMargin = d(8); bottomMargin = d(8) })
            card.addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
                addView(RunUi.text(this@LifeActivity, "🧘 Daily Movement", GREEN, 15f, true).apply { setPadding(0, 0, d(8), 0) }); addView(moveCount) })
            row("exDogWalk", "Dog Walk:", Color.WHITE, "exDogWalkVal", "Duration/notes…")
            row("exStepsCheck", "Steps today:", Color.WHITE, "exSteps", "Goal 10,000", true)
            card.addView(RunUi.text(this@LifeActivity, "Typing a value checks it off on its own (BP, weight, sleep, dog walk, 10,000+ steps). Meds stays manual: tap its box.", RunUi.DIM, 10f).apply { setPadding(0, d(8), 0, 0) })
        }
        fun fill(j: JSONObject) {
            val arr = j.optJSONArray("wellness") ?: return
            if (!built) build()
            dateText.text = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMM d", java.util.Locale.US))
            val rows = (0 until arr.length()).map { arr.getJSONObject(it) }
            rows.forEach { r ->
                val key = r.optString("key"); states[key] = r.optString("state"); boxes[key]?.let { paintBox(it, r.optString("state")) }
                val f = r.getJSONArray("fields")
                for (i in 0 until f.length()) { val o = f.getJSONObject(i); edits[o.optString("id")]?.let { if (!it.hasFocus() && it.text.toString() != o.optString("value")) it.setText(o.optString("value")) } }
            }
            moveCount.text = "${listOf("exDogWalk", "exStepsCheck").count { states[it] == "green" }}/2"
            val sd = j.optString("sleepDetail"); sleepDetail.text = sd; sleepDetail.visibility = if (sd.isBlank()) View.GONE else View.VISIBLE
            if (System.currentTimeMillis() - chartsAt > 60_000) { chartsAt = System.currentTimeMillis(); loadCharts() }
        }
        /**
         * Charts under Wellness (they replaced the separate Dashboard): ❤️ BP & Pulse, ⚖️ Weight, 🫀 Morning HR, 😴 Sleep,
         * ⌛ Sleep Score and 👟 Steps, over 7 / 30 / 60 / 90 days (60 like the Command Center's Wellness charts).
         */
        private var range = 60
        private fun loadCharts() {
            web.evaluateJavascript("window.ccNative&&ccNative.dash?ccNative.dash($range):null") { r ->
                val arr = try { org.json.JSONArray(JSONTokener(r).nextValue() as String) } catch (e: Exception) { null } ?: return@evaluateJavascript
                val days = (0 until arr.length()).map { arr.getJSONObject(it) }
                val labels = days.map { it.optString("d") }
                fun ser(k: String) = days.map { it.optDouble(k, 0.0).takeIf { v -> v > 0 } }
                val L = "Last $range Days"
                charts.removeAllViews()
                // Range switch
                charts.addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, d(14), 0, 0)
                    addView(RunUi.text(this@LifeActivity, "📈 Charts", Color.WHITE, 15f, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    listOf(7, 30, 60, 90).forEach { n -> addView(RunUi.text(this@LifeActivity, "${n}d", if (n == range) Color.BLACK else Color.WHITE, 12f, true).apply {
                        setPadding(d(10), d(5), d(10), d(5)); background = RunUi.rounded(if (n == range) RunUi.ACCENT else 0xFF2A3430.toInt(), d(14).toFloat())
                        setOnClickListener { if (range != n) { range = n; chartsAt = System.currentTimeMillis(); loadCharts() } } }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = d(6) }) } })
                fun avg(l: List<Double?>) = l.filterNotNull().let { if (it.isEmpty()) 0.0 else it.average() }
                fun chartCard(title: String, legend: String?, legendColor: Int, lines: List<Pair<List<Double?>, Int>>, empty: String, bars: Boolean = false, summary: () -> String): LinearLayout {
                    val c = RunUi.card(this@LifeActivity)
                    c.addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
                        addView(RunUi.text(this@LifeActivity, title, RED, 15f, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                        if (legend != null) addView(RunUi.text(this@LifeActivity, legend, legendColor, 12f, true)) })
                    if (lines.first().first.any { it != null }) {
                        c.addView(LifeDashboard.Chart(this@LifeActivity, lines, bars, labels), LinearLayout.LayoutParams(MATCH_PARENT, d(150)).apply { topMargin = d(6) })
                        c.addView(RunUi.text(this@LifeActivity, summary(), RunUi.MUTED, 12f).apply { gravity = android.view.Gravity.CENTER; setPadding(0, d(6), 0, 0) })
                    } else c.addView(RunUi.text(this@LifeActivity, empty, RunUi.MUTED, 12f).apply { setPadding(0, d(6), 0, 0) })
                    charts.addView(c); return c
                }
                // ❤️ Blood Pressure & Pulse
                val sys = ser("sys"); val dia = ser("dia"); val pul = ser("pulse")
                val bp = chartCard("❤️ Blood Pressure & Pulse — $L", null, 0, listOf(sys to 0xFFEF4444.toInt(), dia to 0xFF60A5FA.toInt(), pul to 0xFFF97316.toInt()), "No readings in the last $range days.") {
                    val ps = pul.filterNotNull()
                    "$range-day avg — BP: ${avg(sys).toInt()}/${avg(dia).toInt()} mmHg" + (if (ps.isNotEmpty()) " • Pulse: ${ps.average().toInt()} bpm" else "") + " (${sys.count { it != null }} readings)" }
                bp.addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, d(6), 0, d(4))
                    listOf("● Systolic" to 0xFFEF4444.toInt(), "● Diastolic" to 0xFF60A5FA.toInt(), "● Pulse" to 0xFFF97316.toInt()).forEach { (t, c) ->
                        addView(RunUi.text(this@LifeActivity, t, c, 13f, true).apply { setPadding(0, 0, d(14), 0) }) } }, 1)
                bp.addView(RunUi.button(this@LifeActivity, "🖨️ Print BP Report", 0xFF334155.toInt(), Color.WHITE) { more.printReport(range) },
                    LinearLayout.LayoutParams(MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = d(8) })
                // ⚖️ Weight
                val w = ser("weight")
                chartCard("⚖️ Weight — $L", "● Weight (lbs)", 0xFFC084FC.toInt(), listOf(w to 0xFFC084FC.toInt()), "No weigh-ins in the last $range days.") {
                    val ws = w.filterNotNull(); String.format(java.util.Locale.US, "$range-day avg: %.1f lbs • Range: %.1f – %.1f lbs (%d readings)", ws.average(), ws.min(), ws.max(), ws.size) }
                // 🫀 Morning HR
                val hr = ser("restHR")
                chartCard("🫀 Morning HR — $L", "● bpm", 0xFFF472B6.toInt(), listOf(hr to 0xFFF472B6.toInt()), "No morning heart rates in the last $range days.") {
                    val hs = hr.filterNotNull(); "$range-day avg: ${hs.average().toInt()} bpm • Range: ${hs.min().toInt()} – ${hs.max().toInt()} bpm" }
                // 💤 Sleeping HR (Samsung Health, lowest overnight) — moved here from Fitness, with its trend note.
                val nights = try { io.github.michealsizemore1.beyondstride.ReadinessSync.nights(this@LifeActivity).filter { it.sleepHr > 0 } } catch (e: Exception) { emptyList() }
                val byDay = nights.associate { it.day to it.sleepHr.toDouble() }
                val shr = labels.map { byDay[it] }
                val shc = chartCard("💤 Sleeping HR — $L", "● bpm", 0xFFFF6B6B.toInt(), listOf(shr to 0xFFFF6B6B.toInt()), "No sleeping heart rate in the last $range days (Samsung Health sleep).") {
                    val xs = shr.filterNotNull(); "$range-day avg: ${xs.average().toInt()} bpm • Range: ${xs.min().toInt()} – ${xs.max().toInt()} bpm • zones use ${RunSettings.hrRest(this@LifeActivity)} bpm" }
                try { io.github.michealsizemore1.beyondstride.Readiness.rhrTrend(nights.sortedBy { it.day }.takeLast(30))?.let { tr ->
                    shc.addView(RunUi.text(this@LifeActivity, String.format(java.util.Locale.US, "7-night average %.1f bpm", tr.recent) + (tr.change?.let { String.format(java.util.Locale.US, " · %s%.1f vs week before", if (it > 0) "+" else "", it) } ?: ""), Color.WHITE, 13f, true).apply { setPadding(0, d(6), 0, 0) })
                    shc.addView(RunUi.text(this@LifeActivity, tr.note, tr.color, 13f).apply { setPadding(0, d(2), 0, 0) }) } } catch (e: Exception) { }
                shc.setOnClickListener { startActivity(Intent(this@LifeActivity, ReadinessActivity::class.java)) }
                // 😴 Sleep
                val sl = ser("sleep")
                chartCard("😴 Sleep — $L", "● hours", 0xFFA78BFA.toInt(), listOf(sl to 0xFFA78BFA.toInt()), "No sleep in the last $range days.") {
                    val ss = sl.filterNotNull(); String.format(java.util.Locale.US, "$range-day avg: %.1f h • Range: %.1f – %.1f h", ss.average(), ss.min(), ss.max()) }
                // ⌛ Sleep Score
                val sc = ser("sleepScore")
                chartCard("⌛ Sleep Score — $L", "● 0-100", 0xFF818CF8.toInt(), listOf(sc to 0xFF818CF8.toInt()), "No sleep scores in the last $range days.") {
                    val xs = sc.filterNotNull(); "$range-day avg: ${xs.average().toInt()} • Range: ${xs.min().toInt()} – ${xs.max().toInt()}" }
                // 👟 Steps (Daily Movement)
                val st = ser("steps")
                chartCard("👟 Steps — $L", "● steps", GREEN, listOf(st to GREEN), "No steps in the last $range days.", bars = true) {
                    val xs = st.filterNotNull(); String.format(java.util.Locale.US, "$range-day avg: %,d steps • 10,000+ on %d of %d days", xs.average().toLong(), xs.count { it >= 10000 }, xs.size) }
            }
        }
    }

    /** ✅ Daily Checks — Bible, Meditation, Listening, Wellness, Planning & Review: the Command Center's checkmarks. */
    private inner class ChecksCard(private val title: String, private val include: (String) -> Boolean) {
        val view = RunUi.card(this@LifeActivity)
        private val summary = RunUi.text(this@LifeActivity, "", RunUi.MUTED, 12f)
        private val body = LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.VERTICAL }
        private var sig = ""
        private fun d(v: Int) = RunUi.dp(this@LifeActivity, v)
        init {
            view.addView(RunUi.text(this@LifeActivity, title, Color.WHITE, 16f, true))
            view.addView(summary); view.addView(body)
            view.addView(RunUi.text(this@LifeActivity, "Automatic checks (audiobook, Bible Study…) still fill in on their own.", RunUi.DIM, 10f).apply { setPadding(0, d(8), 0, 0) })
        }
        private fun icon(g: String) = when { g.contains("Bible") -> "📖"; g.contains("Meditation") -> "🧘"; g.contains("Listening") -> ""; g.contains("Wellness") -> "💚"; g.contains("Planning") -> "📝"; else -> "•" }
        private fun nice(l: String) = l.replaceFirstChar { it.uppercase() }.replace(Regex("^Goal\\s+"), "")
        fun fill(j: JSONObject) {
            val arr = j.optJSONArray("checks") ?: run { summary.text = "Getting the newest Command Center…"; return }
            val items = (0 until arr.length()).map { arr.getJSONObject(it) }.filter { include(it.optString("group")) }
            val n = { st: String -> items.count { it.optString("state") == st } }
            summary.text = "${n("green")} done · ${n("purple")} partly · ${n("red")} not done · ${n("black")} N/A · ${n("")} not marked"
            val newSig = items.joinToString("|") { it.optString("key") + it.optString("id") + ":" + it.optString("state") + ":" + it.optString("label") }
            if (newSig == sig) return; sig = newSig
            body.removeAllViews()
            items.groupBy { it.optString("group") }.forEach { (g, list) ->
                val done = list.count { it.optString("state") == "green" }
                body.addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, d(10), 0, d(2))
                    addView(RunUi.text(this@LifeActivity, (icon(g) + " " + g).trim(), RunUi.ACCENT, 13f, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(RunUi.text(this@LifeActivity, "$done/${list.size}", RunUi.MUTED, 12f, true)) })
                list.forEach { o ->
                    val key = o.optString("key"); val id = o.optString("id"); var st = o.optString("state")
                    val box = TextView(this@LifeActivity).apply { gravity = android.view.Gravity.CENTER; textSize = 14f; setTypeface(typeface, android.graphics.Typeface.BOLD) }
                    paintBox(box, st)
                    val row = LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, d(4), 0, d(4))
                        addView(box, LinearLayout.LayoutParams(d(28), d(28)))
                        addView(RunUi.text(this@LifeActivity, nice(o.optString("label")), Color.WHITE, 14f).apply { setPadding(d(10), 0, 0, 0) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
                    row.setOnClickListener {
                        st = if (id.isNotEmpty()) (if (st == "green") "" else "green") else CYCLE[(CYCLE.indexOf(st) + 1) % CYCLE.size]
                        paintBox(box, st)
                        if (id.isNotEmpty()) js("ccNative.setBox(${JSONObject.quote(id)},${st == "green"}${dayArg()})")
                        else js("ccNative.setCheck(${JSONObject.quote(key)},${JSONObject.quote(st)}${dayArg()})")
                    }
                    body.addView(row)
                }
            }
        }
    }

    /** 🎯 Today's Focus — Priority Tasks + Goals, the same six lines and checks as the Command Center. */
    private inner class FocusCard {
        val view = RunUi.card(this@LifeActivity)
        private val count = RunUi.text(this@LifeActivity, "", RunUi.MUTED, 13f, true)
        private val status = RunUi.text(this@LifeActivity, "Loading your Command Center…", RunUi.MUTED, 12f)
        private val boxes = HashMap<String, TextView>(); private val edits = HashMap<String, EditText>(); private val states = HashMap<String, String>()
        private val ORDER = listOf("", "green", "black", "purple", "red")
        private val title = RunUi.text(this@LifeActivity, "🎯 Today's Focus", Color.WHITE, 16f, true)
        private fun d(v: Int) = RunUi.dp(this@LifeActivity, v)
        init {
            view.addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
                addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(count) })
            view.addView(status)
            group("✅ Priority Tasks", 0xFF818CF8.toInt(), listOf("pt0", "pt1", "pt2"), "Task")
            group("🎯 Goals", 0xFFA78BFA.toInt(), listOf("dg0", "dg1", "dg2"), "Goal")
            view.addView(RunUi.text(this@LifeActivity, "Tap a box to change it: ✓ green done · purple partly · red not done · black N/A · empty not marked.", RunUi.DIM, 10f).apply { setPadding(0, d(8), 0, 0) })
        }
        private fun group(title: String, color: Int, ids: List<String>, word: String) {
            view.addView(RunUi.text(this@LifeActivity, title, color, 13f, true).apply { setPadding(0, d(10), 0, d(2)) })
            ids.forEachIndexed { i, id ->
                val box = TextView(this@LifeActivity).apply { gravity = android.view.Gravity.CENTER; textSize = 16f; setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setOnClickListener { val n = ORDER[(ORDER.indexOf(states[id] ?: "") + 1) % ORDER.size]; paint(id, n); js("ccNative.setState(${JSONObject.quote(id)},${JSONObject.quote(n)}${dayArg()})"); recount() } }
                val et = EditText(this@LifeActivity).apply {
                    hint = "$word ${i + 1}…"; setTextColor(Color.WHITE); setHintTextColor(RunUi.DIM); textSize = 15f; isSingleLine = true
                    imeOptions = EditorInfo.IME_ACTION_DONE; background = null; setPadding(d(10), d(6), 0, d(6))
                    val commit = Runnable { js("ccNative.setText(${JSONObject.quote(id)},${JSONObject.quote(text.toString())}${dayArg()})") }
                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                        override fun afterTextChanged(s: Editable?) { if (hasFocus()) { ui.removeCallbacks(commit); ui.postDelayed(commit, 800) } }
                    })
                    setOnFocusChangeListener { _, has -> if (!has) { ui.removeCallbacks(commit); commit.run() } }
                    setOnEditorActionListener { v, _, _ -> v.clearFocus(); false }
                }
                boxes[id] = box; edits[id] = et; paint(id, "")
                view.addView(LinearLayout(this@LifeActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, d(3), 0, d(3))
                    addView(box, LinearLayout.LayoutParams(d(32), d(32)))
                    addView(et, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) })
            }
        }
        private fun paint(id: String, st: String) {
            states[id] = st; val b = boxes[id] ?: return
            val c = when (st) { "green" -> 0xFF22C55E; "purple" -> 0xFFA855F7; "red" -> 0xFFEF4444; "black" -> 0xFF0B0F0D; else -> 0 }.toInt()
            b.background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = d(8).toFloat(); setColor(c); setStroke(d(2), if (st.isEmpty()) RunUi.MUTED else if (st == "black") 0xFF6B7280.toInt() else c) }
            b.text = if (st.isEmpty()) "" else "✓"; b.setTextColor(Color.WHITE)
            edits[id]?.alpha = if (st == "green") 0.6f else 1f
        }
        private fun recount() { count.text = "${states.values.count { it == "green" }}/6" }
        fun loading() { status.visibility = View.VISIBLE }
        fun fill(j: JSONObject) {
            status.visibility = View.GONE
            val v = j.optString("viewing")
            title.text = if (v.isEmpty()) "🎯 Today's Focus" else "🎯 Focus · " + try { java.time.LocalDate.parse(v).format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d", java.util.Locale.US)) } catch (e: Exception) { v }
            val items = j.optJSONArray("items") ?: return
            for (i in 0 until items.length()) { val o = items.getJSONObject(i); val id = o.optString("id")
                paint(id, o.optString("state"))
                edits[id]?.let { if (!it.hasFocus() && it.text.toString() != o.optString("text")) it.setText(o.optString("text")) } }
            recount()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 43) { val cb = pickCb; pickCb = null; if (resultCode == RESULT_OK) data?.data?.let { cb?.invoke(it) }; return }
        if (requestCode == LifeMore.PICK) { if (resultCode == RESULT_OK) data?.data?.let { more.onPicked(it) }; return }
        if (requestCode == 41) { fileCb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data)); fileCb = null; return }
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
    }

    // Back goes back a page in the full Command Center first, then to the native Today view.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (full && web.canGoBack()) web.goBack()
        else if (page == 6) showPage(5)
        else if (full || page in listOf(1, 3, 4, 5, 7, 8, 9, 10)) show(false)
        else @Suppress("DEPRECATION") super.onBackPressed()
    }

    companion object {
        const val CC_DIR = "/ChatGPT-The-Master-App/"
        const val CC_PAGE = "https://michealsizemore1.github.io/ChatGPT-The-Master-App/daily-planner.html"
    }
}
