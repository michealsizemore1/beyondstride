package io.github.michealsizemore1.beyondstride.run

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import kotlin.math.abs

/**
 * The run screens: a start screen (sensor status, Stryd or GPS), swipeable live pages
 * during the run, and a summary when you finish.
 */
class RunActivity : Activity() {
    private var askedSms = false

    /** True when this file was installed on the wrong kind of device (a message is shown instead). */
    private var wrongDevice = false

    private object C {
        const val ACCENT = 0xFFD4E635.toInt()
        val MUTED: Int get() = if (io.github.michealsizemore1.beyondstride.Theme.light) 0xFFC9D1CE.toInt() else 0xFF9AA3A0.toInt()
        val DIM: Int get() = if (io.github.michealsizemore1.beyondstride.Theme.light) 0xFFB4BDB9.toInt() else 0xFF6B7370.toInt()
        const val CARD = 0xFF1C2421.toInt()
        const val PAUSE = 0xFFFFB547.toInt()
        const val STOP = 0xFFE5484D.toInt()
        const val OK = 0xFF46C46E.toInt()
        val BG: Int get() = io.github.michealsizemore1.beyondstride.Theme.bg   // same background as the phone app
    }

    /** Start area: phone-style home, or the settings list. */
    private var showSettings = false

    private enum class Screen { START, LIVE, SUMMARY }

    private lateinit var root: FrameLayout
    private var screen = Screen.START
    private var page = 1                 // 0 controls, 1 main, 2 heart, 3 power, 4 lap, 5 averages, 6 stress
    private var confirmingFinish = false
    private var summary: JSONObject? = null
    private val pageCount = 7
    /** While a number editor is open, the bezel adjusts it. */
    private var editorRotary: ((Int) -> Unit)? = null
    /** True while the workout picker covers the start screen. */
    private var overlayOpen = false
    /** Live pages in order: 0 controls, 7 workout step (only with a workout), 1 main, 2 heart, 3 power, 4 lap, 5 averages, 6 stress. */
    /** Live pages: 0 controls, 7 workout step (with a workout), then your data screens (100 + index). */
    private fun pageOrder() = listOf(0) + (if (Run.hasWorkout) listOf(7) else emptyList()) +
        (if (Run.nav != null) listOf(8) else emptyList()) + (0 until screens.length()).map { 100 + it }   // 8 = trail map
    private var builtScreen = -1
    /** Data screens set up on the phone (Run settings → Watch data screens). */
    private var screens: org.json.JSONArray = io.github.michealsizemore1.beyondstride.RunFields.defaults()
    private fun loadScreens() {
        screens = try { org.json.JSONArray(prefs.getString("screens", null) ?: "") } catch (e: Exception) { null }
            ?.takeIf { it.length() > 0 } ?: io.github.michealsizemore1.beyondstride.RunFields.defaults()
        builtScreen = -1
    }
    private var lastRotary = 0L
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("run", 0) }

    private val onRunChanged: () -> Unit = { runOnUiThread { render() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (io.github.michealsizemore1.beyondstride.WrongDevice.check(this, isWatchApp = true)) { wrongDevice = true; return }
        root = FrameLayout(this).apply { setBackgroundColor(C.BG) }
        setContentView(root)
        Speaker.init(this)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                logKey("bottom button (back) · screen=$screen")
                handleBack()
            }
        }

        RunSync.loadSettings(this)
        // Changes made here go to the phone; changes made on the phone come back (RunSettingsListener).
        io.github.michealsizemore1.beyondstride.SettingsSync.watch(this, "run", "watch")
        loadScreens()
        // Keep the screen awake so the app keeps focus — otherwise, once the watch dims, the bottom
        // button goes to the system instead of the app (and closes it).
        if (prefs.getBoolean("keepOn", true)) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (!RunService.running) RunStore.recoverInterrupted(this)
        // Load today's planned workout once a day (you can switch to a free run on the start screen).
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        if (!Run.isActive && Run.workout == null && prefs.getString("autoPicked", "") != today) {
            WatchWorkouts.today(this)?.let { Run.chooseWorkout(it) }
            prefs.edit().putString("autoPicked", today).apply()
        }
        RunSync.pushAll(this)   // send any runs the phone doesn't have yet
        RunSync.reportVersion(this)
        // Catch up on anything the phone sent while this app wasn't listening, then redraw the home screen.
        RunSync.pullLatest(this) {
            if (!wrongDevice && screen == Screen.START && !overlayOpen && editorRotary == null) {
                if (!Run.isActive && Run.workout == null) WatchWorkouts.today(this)?.let { Run.chooseWorkout(it) }
                if (!Run.isActive) { RunSync.loadSettings(this); loadScreens(); RunSync.loadTrail(this) }
                startBuilt = false; render()
            }
        }
        screen = if (Run.isActive) Screen.LIVE else Screen.START
        requestNeeded()
        tileStart(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        tileStart(intent)
    }

    /** "Start" on the Running tile: open and start the run right away. */
    private fun tileStart(i: android.content.Intent?) {
        if (i?.getBooleanExtra(RunTileService.EXTRA_START, false) != true) return
        i.removeExtra(RunTileService.EXTRA_START)
        if (Run.isActive || wrongDevice) return
        handler.postDelayed({
            if (!Run.isActive && screen == Screen.START) { overlayOpen = false; showSettings = false; startRun(); flash("Run started") }
        }, 700)
    }

    override fun onResume() {
        io.github.michealsizemore1.beyondstride.Theme.load(this, "run")
        super.onResume()
        if (wrongDevice) return
        // LiveTrack on (e.g. restored from a backup) but no texting permission yet on this watch app: ask.
        if (!askedSms && !Run.isActive && prefs.getBoolean("liveOn", false) &&
            checkSelfPermission(android.Manifest.permission.SEND_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            askedSms = true
            requestPermissions(arrayOf(android.Manifest.permission.SEND_SMS), 7)
        }
        if (!Run.isActive) { RunSync.loadSettings(this); RunSync.loadTrail(this) }
        loadScreens()
        Run.listen(onRunChanged)
        // Coming back from History (which stopped the idle sensors): turn them on again.
        if (!RunService.running && screen != Screen.SUMMARY) RunService.start(this)
        if (screen == Screen.START && !showSettings && !overlayOpen && !Run.isActive) startBuilt = false   // fresh readiness after a check
        render()
        // New BeyondStride build out? (asks GitHub at most every 3 hours; never during a run)
        if (!Run.isActive) Thread {
            val had = io.github.michealsizemore1.beyondstride.Updater.available(this)?.build
            val now = io.github.michealsizemore1.beyondstride.Updater.check(this, true)?.build
            if (now != had) runOnUiThread { if (!isFinishing && !Run.isActive && screen == Screen.START) { startBuilt = false; render() } }
        }.start()
    }
    override fun onPause() {
        RunTileService.refresh(this)
        if (isOnScreen || hasWindowFocus()) lastShownAt = System.currentTimeMillis()
        isOnScreen = false
        super.onPause()
        if (!wrongDevice) Run.unlisten(onRunChanged)
    }

    /** Last few button events, shown in Settings to diagnose the orange button. */
    private fun logButton(line: String) {
        val t = java.text.SimpleDateFormat("h:mm:ss", java.util.Locale.US).format(java.util.Date())
        val old = prefs.getString("buttonLog", "").orEmpty().lines().filter { it.isNotBlank() }.take(4)
        prefs.edit().putString("buttonLog", (listOf("$t $line") + old).joinToString("\n")).apply()
    }
    private var stoppedAt = 0L
    /** When the Running screen was last on the display, for telling a button press from opening the app. */
    private var lastShownAt = 0L
    private var isOnScreen = false
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) isOnScreen = true else { isOnScreen = false; lastShownAt = System.currentTimeMillis() }
    }
    /** True while the Running screen is on the display (not behind another app or the watch face). */
    private var wasVisible = false
    override fun onStart() { super.onStart(); handler.postDelayed({ wasVisible = true }, 600) }

    override fun onStop() {
        super.onStop()
        stoppedAt = System.currentTimeMillis()
        wasVisible = false
        handler.removeCallbacksAndMessages(null)
        if (wrongDevice) return
        // Leaving the start screen without running: turn the sensors back off.
        if (!Run.isActive && screen != Screen.LIVE && !isChangingConfigurations) RunService.stop(this)
    }

    // ---------------------------------------------------------------- permissions

    private fun neededPermissions(): List<String> {
        val p = mutableListOf(Manifest.permission.BODY_SENSORS, Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 31) { p += Manifest.permission.BLUETOOTH_SCAN; p += Manifest.permission.BLUETOOTH_CONNECT }
        if (Build.VERSION.SDK_INT >= 33) p += Manifest.permission.POST_NOTIFICATIONS
        return p.filter { !RunService.has(this, it) }
    }

    private fun requestNeeded() {
        val missing = neededPermissions()
        if (missing.isEmpty()) RunService.start(this) else requestPermissions(missing.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 7) {   // texting permission for LiveTrack
            if (grantResults.firstOrNull() != android.content.pm.PackageManager.PERMISSION_GRANTED)
                flash("Watch can't text — your phone will still send texts")
            return
        }
        RunService.start(this)
        render()
    }

    // ---------------------------------------------------------------- rendering

    private fun render() {
        when (screen) {
            Screen.START -> if (Run.isActive) { screen = Screen.LIVE; renderLive() } else if (editorRotary == null && !overlayOpen) renderStart()
            Screen.LIVE -> renderLive()
            Screen.SUMMARY -> {}   // static; drawn once in finish()
        }
    }

    // ---------------------------------------------------------------- start screen

    private var startBuilt = false
    private val startValues = HashMap<String, TextView>()
    private var trackRow: View? = null
    private var calRow: View? = null

    private fun renderStart() {
        if (!startBuilt) buildStart()
        fun set(key: String, text: String, color: Int = Color.WHITE) {
            startValues[key]?.apply { this.text = text; setTextColor(color) }
        }
        set("screensRow", "${screens.length()} screens ▸", C.ACCENT)
        set("voiceRow", (if (prefs.getBoolean("voice", true)) "On" else "Off") + " ▸", C.ACCENT)
        set("bottomBtn", if (prefs.getBoolean("bottomBtn", true)) "On" else "Off")
        set("keepOn", if (prefs.getBoolean("keepOn", true)) "On" else "Off")
        set("source", if (Run.source == Run.Source.STRYD) "Stryd ▸" else "GPS ▸", C.ACCENT)
        startValues["trailLine"]?.apply {
            val n = Run.nav
            text = if (n == null) "" else "Trail: ${n.trail.name} · " + String.format(java.util.Locale.US, "%.1f mi", n.total / 1609.344) +
                (if (n.outAndBack) " out & back" else "") +
                (if (n != null && TrailTiles.enabled(this@RunActivity) && TrailTiles.status.isNotBlank()) "\n" + TrailTiles.status else "")
            visibility = if (n == null) View.GONE else View.VISIBLE
        }
        set("workout", (Run.workout?.optString("name") ?: "Free run") + " ›", if (Run.workout != null) Color.WHITE else C.ACCENT)
        startValues["workoutOutline"]?.apply {
            val w = Run.workout
            text = if (w == null) "" else io.github.michealsizemore1.beyondstride.Workouts.outline(w, Run.hrMax, Run.hrRest, Run.criticalPower)
                .take(6).joinToString("\n")
            visibility = if (w == null) View.GONE else View.VISIBLE
        }
        // Green = connected (same as the heart-rate strap); orange = low battery or connected but quiet; gray = not connected.
        val strydLive = Run.strydConnected && System.currentTimeMillis() - Run.strydLastUpdate < 5000
        set("stryd", (if (Run.strydConnected) "✓ " else "") + Run.strydStatus +
                (if (Run.strydConnected && Run.strydBattery >= 0) " · ${Run.strydBattery}%" else "") +
                (if (strydLive && Run.strydPower > 0) " · ${Run.strydPower} W" else ""),
            when {
                !Run.strydConnected -> C.MUTED
                Run.strydBattery in 0..15 -> C.PAUSE
                else -> C.OK
            })
        set("cp", "${Run.criticalPower} W ▸")
        set("hrmax", "${fmtHr(Run.hrMax)} bpm ▸")
        set("hrrest", "${fmtHr(Run.hrRest)} bpm ▸")
        set("cal", String.format(java.util.Locale.US, "%.3f ▸", Run.strydCalibration))
        val gpsNeeded = Run.source == Run.Source.GPS || Run.recordGpsTrack
        set("gps", if (gpsNeeded) Run.gpsStatus else "Off (not needed)", if (Run.gpsFix && gpsNeeded) C.OK else C.MUTED)
        set("hrmode", if (Run.useStrap) "Chest strap + wrist backup ▸" else "Wrist only ▸", C.ACCENT)
        val hrText = when {
            !Run.useStrap -> if (Run.heartRate > 0) "Wrist · ${Run.heartRate} bpm" else "Waiting for wrist…"
            Run.strapActive -> "${Run.strapStatus} · ${Run.heartRate} bpm" +
                (if (Run.strapBattery >= 0) " · ${Run.strapBattery}%" else "")
            Run.strapConnected && !Run.strapContact -> "Strap: wet the pads / no skin contact"
            Run.heartRate > 0 -> "Wrist · ${Run.heartRate} bpm" +
                (if (Run.strapStatus.startsWith("Searching") || Run.strapStatus.startsWith("Connecting") ||
                     Run.strapStatus.startsWith("Reconnecting")) " · strap: ${Run.strapStatus}" else "")
            else -> "Waiting… · strap: ${Run.strapStatus}"
        }
        set("hr", hrText, if (Run.strapActive || (!Run.useStrap && Run.heartRate > 0)) C.OK else if (Run.heartRate > 0) C.PAUSE else C.MUTED)
        set("autopause", if (Run.autoPause) "On" else "Off")
        set("track", if (Run.recordGpsTrack) "On" else "Off")
        set("trailMap", if (prefs.getBoolean("trailMapTiles", true)) "On" else "Off")
        set("textScale", when { prefs.getFloat("textScale", 1.25f) >= 1.45f -> "Extra large"; prefs.getFloat("textScale", 1.25f) >= 1.2f -> "Large"; else -> "Normal" } + " ▸")
        set("bgStyle", if (io.github.michealsizemore1.beyondstride.Theme.light) "Lighter" else "Dark (saves battery)")
        set("menuStyle", if (prefs.getString("menuStyle", "cards") == "classic") "Classic" else "Cards")
        set("cpauto", when (prefs.getString("cpAuto", "ask")) { "auto" -> "Automatically (raises only)"; "off" -> "Off"; else -> "Ask on the phone" })
        set("hrcap", when (Run.hrCapMode) { "off" -> "Off"; "all" -> "Easy days + free runs · under ${Run.hrCap}"; else -> "Easy days · under ${Run.hrCap}" })
        set("live", if (!prefs.getBoolean("liveOn", false)) "Off" else if (Run.isActive) "On · ${LiveTrack.status}" else "On")
        trackRow?.visibility = if (Run.source == Run.Source.STRYD) View.VISIBLE else View.GONE
        calRow?.visibility = if (Run.source == Run.Source.STRYD) View.VISIBLE else View.GONE

        val ready = if (Run.source == Run.Source.STRYD) Run.strydConnected else Run.gpsFix
        startValues["startBtn"]?.apply {
            text = if (!ready) "START ANYWAY" else if (Run.workout != null) "START WORKOUT" else "START"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (ready) 20f else 14f)
        }
    }

    private fun buildStart() {
        startBuilt = true
        startValues.clear()
        root.setBackgroundColor(C.BG)
        if (showSettings) buildSettings() else buildHome()
    }

    /** Miles and runs since the start of this week, from the runs saved on the watch. */
    private fun weekTotals(month: Boolean = false): Pair<Double, Int> {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0)
            if (month) set(java.util.Calendar.DAY_OF_MONTH, 1) else set(java.util.Calendar.DAY_OF_WEEK, firstDayOfWeek)
        }
        val from = cal.timeInMillis
        var meters = 0.0; var n = 0
        RunStore.list(this).forEach { f ->
            if ((f.nameWithoutExtension.toLongOrNull() ?: 0L) < from) return@forEach
            // Only the totals near the top of the file are needed — avoid parsing every sample.
            val head = f.bufferedReader().use { r -> CharArray(400).let { buf -> String(buf, 0, r.read(buf).coerceAtLeast(0)) } }
            Regex("\"distance\":([0-9.]+)").find(head)?.groupValues?.get(1)?.toDoubleOrNull()?.let { meters += it; n++ }
        }
        return meters to n
    }

    /** Home, laid out like the phone's Running screen. */
    private fun buildHome() {
        val w = resources.displayMetrics.widthPixels
        val col = column().apply { setPadding(w / 9, w / 8, w / 9, w / 5) }
        val I = io.github.michealsizemore1.beyondstride.Insights
        val RM = io.github.michealsizemore1.beyondstride.RunMath
        fun card() = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(C.CARD, 16)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) }
        }
        fun head(t: String) = label(t, C.ACCENT, 11f, true).apply { letterSpacing = 0.08f }
        col.addView(label("BeyondStride", Color.WHITE, 20f, true))
        io.github.michealsizemore1.beyondstride.Updater.available(this)?.let { up ->
            col.addView(card().apply {
                addView(head("UPDATE"))
                addView(label("Install build ${up.build}", Color.WHITE, 14f, true))
                val st = label("You're on build ${io.github.michealsizemore1.beyondstride.Updater.currentBuild(this@RunActivity)}. Tap to install.", C.MUTED, 10f)
                addView(st)
                setOnClickListener { io.github.michealsizemore1.beyondstride.Updater.start(this@RunActivity, up) { st.text = it } }
            })
        }

        // 1. TODAY: readiness + workout (+ trail), then START right under it.
        col.addView(card().apply {
            addView(head("TODAY"))
            val ready = label("", C.ACCENT, 15f, true); val readySub = label("", C.MUTED, 10f)
            val readyAdvice = label("", Color.WHITE, 10f).apply { visibility = View.GONE }
            addView(ready); addView(readySub); addView(readyAdvice)
            ready.setOnClickListener { startActivity(Intent(this@RunActivity, MorningCheckActivity::class.java).putExtra("start", !Recovery.checkedToday(this@RunActivity))) }
            readySub.setOnClickListener { ready.performClick() }
            addView(View(this@RunActivity).apply { setBackgroundColor(0x22FFFFFF) }, LinearLayout.LayoutParams(MATCH_PARENT, dp(1)).apply { topMargin = dp(6); bottomMargin = dp(6) })
            val openW = { Run.workout?.let { showWorkoutDetail(it, "Today", chosen = true) } ?: showWorkoutPicker() }
            addView(label("", Color.WHITE, 15f, true).also { startValues["workout"] = it; it.setOnClickListener { openW() } })
            addView(label("", C.MUTED, 10f).apply { startValues["workoutOutline"] = this; visibility = View.GONE; setOnClickListener { openW() } })
            addView(label("", 0xFF46C46E.toInt(), 11f, true).apply { startValues["trailLine"] = this; visibility = View.GONE })
            // FUEL — same lines as the phone's Today card.
            addView(View(this@RunActivity).apply { setBackgroundColor(0x22FFFFFF) }, LinearLayout.LayoutParams(MATCH_PARENT, dp(1)).apply { topMargin = dp(6); bottomMargin = dp(6) })
            addView(column().apply {
                val N = io.github.michealsizemore1.beyondstride.Nutrition
                val nd = N.today(io.github.michealsizemore1.beyondstride.NutritionStore.days(this@RunActivity))
                addView(head("FUEL"))
                if (nd == null || nd.count == 0) addView(label("No food logged today yet", C.MUTED, 10f))
                else {
                    // A glance (same as the phone's Today card): calories, both scores on one line; the rest is on Nutrition.
                    addView(label(N.line(nd), if (nd.target > 0 && nd.left < 0) 0xFFF2C94C.toInt() else Color.WHITE, 11f, true))
                    fun sc(v: Double) = N.statusColor(when { v >= 7 -> "Met"; v >= 5 -> "Close"; else -> "Low" })
                    val q = io.github.michealsizemore1.beyondstride.Food.dayQuality(nd)
                    val parts = listOfNotNull(q?.let { "Food " + String.format(java.util.Locale.US, "%.1f", it.score) }, if (nd.micros.isNotEmpty()) "Micros " + String.format(java.util.Locale.US, "%.1f", nd.microScore) else null)
                    if (parts.isNotEmpty()) addView(label(parts.joinToString(" · ") + " ▸", sc(listOfNotNull(q?.score, nd.microScore.takeIf { nd.micros.isNotEmpty() }).minOrNull() ?: 5.0), 10f, true))
                }
                N.rehydrate(nd)?.takeIf { it.needOz > 0 }?.let { h -> addView(label("💧 Drink ~${h.needOz} oz more", N.WATER, 10f, true)) }
                setOnClickListener { showNutrition() }
            })
            Thread {
                val r = try { Recovery.result(this@RunActivity) } catch (e: Exception) { null }
                val checked = Recovery.checkedToday(this@RunActivity)
                runOnUiThread {
                    if (r != null) { readyAdvice.text = r.advice.substringBefore(". ") + "."; readyAdvice.visibility = View.VISIBLE }
                    if (r != null && checked) {
                        ready.text = "${r.score}  ${r.label}"; ready.setTextColor(r.color)
                        readySub.text = r.parts.filter { it.pts >= 0 }.joinToString(" · ") { "${it.name} ${it.value}" }
                    } else {
                        ready.text = "Morning check ▸"; ready.setTextColor(C.ACCENT)
                        readySub.text = r?.let { "${it.score} · ${it.label} without HRV" } ?: "2½ min lying still with your strap"
                    }
                }
            }.start()
        })
        col.addView(label("START", Color.BLACK, 20f, true).apply {
            startValues["startBtn"] = this
            background = rounded(C.ACCENT, 30)
            setPadding(0, dp(12), 0, dp(12))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }
            setOnClickListener { startRun() }
        })

        // 2. Command Center + LiveTrack (same buttons as the phone).
        val ps = getSharedPreferences("phonestatus", 0)
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) }
            val cc = ps.getInt("ccUnsent", 0)
            val lp = { last: Boolean -> LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply { if (!last) marginEnd = dp(4) } }
            addView(column().apply {
                gravity = Gravity.CENTER; background = rounded(0xFF7B61FF.toInt(), 14); setPadding(dp(2), dp(6), dp(2), dp(6))
                addView(label("Command Center", Color.WHITE, 11f, true))
                addView(label(if (ps.contains("at")) (if (cc == 0) "✓ sent" else "Send $cc") else "—", 0xDDFFFFFF.toInt(), 9f))
                setOnClickListener {
                    RunSync.tellPhone(this@RunActivity, "/cmd/cc") { ok -> flash(if (ok) "Tap the notification on your phone" else "Phone not connected") }
                }
            }, lp(false))
            addView(column().apply {
                gravity = Gravity.CENTER; setPadding(dp(2), dp(6), dp(2), dp(6))
                val state = label("", 0xDDFFFFFF.toInt(), 9f)
                addView(label("LiveTrack", Color.WHITE, 11f, true)); addView(state)
                fun show() {
                    val on = prefs.getBoolean("liveOn", false)
                    background = rounded(if (on) 0xFF1F6FB2.toInt() else 0xFF50555E.toInt(), 14)
                    state.text = if (on) "● ON" else "Off"
                }
                show()
                setOnClickListener {
                    if (prefs.getString("liveDb", "").isNullOrBlank()) { flash("Set up LiveTrack in the phone app first"); return@setOnClickListener }
                    val on = !prefs.getBoolean("liveOn", false)
                    prefs.edit().putBoolean("liveOn", on).apply()
                    show(); flash(if (on) "LiveTrack on — contacts get your link at Start" else "LiveTrack off")
                    if (on && checkSelfPermission(android.Manifest.permission.SEND_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                        requestPermissions(arrayOf(android.Manifest.permission.SEND_SMS), 7)
                }
            }, lp(true))
        })

        // 3. THIS WEEK: miles, runs, training load.
        val (meters, runs) = weekTotals()
        val (monthMeters, _) = weekTotals(month = true)
        col.addView(card().apply {
            addView(head("THIS WEEK"))
            addView(LinearLayout(this@RunActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                // Same miles as the phone (Activities, every source) when the phone has sent this week's; else the watch's own runs.
                val pm = io.github.michealsizemore1.beyondstride.NutritionStore.mileage(this@RunActivity)
                fun mi(v: Double) = String.format(java.util.Locale.US, "%.2f", v)
                addView(column().apply { addView(label(pm?.let { mi(it.optDouble("week")) } ?: Run.miles(meters), Color.WHITE, 18f, true)); addView(label("miles", C.MUTED, 9f)) }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(column().apply { addView(label("${pm?.optInt("runs") ?: runs}", Color.WHITE, 18f, true)); addView(label("runs", C.MUTED, 9f)) }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(column().apply { addView(label(pm?.takeIf { it.optString("ym") == java.time.LocalDate.now().toString().take(7) }?.let { mi(it.optDouble("month")) } ?: Run.miles(monthMeters), Color.WHITE, 18f, true)); addView(label("this month", C.MUTED, 9f)) }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            })
            val loadL = label("", Color.WHITE, 12f, true).apply { visibility = View.GONE }
            addView(loadL)
            Thread {
                val now = System.currentTimeMillis()
                val rs = RunStore.list(this@RunActivity).mapNotNull { RunStore.read(it) }.filter { now - it.optLong("start") < 56 * 86_400_000L }
                if (rs.isEmpty()) return@Thread
                val ld = I.load(rs, now)
                runOnUiThread {
                    loadL.text = "Training load (stress): ${ld.status}" + (ld.ratio?.let { String.format(java.util.Locale.US, " · ratio %.2f", it) } ?: "") + " · ${ld.acute} stress, 7 days"
                    loadL.setTextColor(ld.color); loadL.visibility = View.VISIBLE
                }
            }.start()
        })

        // 4. LAST RUN (same as the phone's).
        RunStore.list(this).maxByOrNull { it.nameWithoutExtension.toLongOrNull() ?: 0L }?.let { RunStore.read(it) }?.let { run ->
            col.addView(card().apply {
                addView(head("LAST RUN"))
                val st = run.optLong("start")
                val title = run.optJSONObject("workout")?.optString("name")?.takeIf { it.isNotBlank() && it != "Free run" }
                    ?: run.optJSONObject("trail")?.optString("name")?.takeIf { it.isNotBlank() }
                    ?: when (java.util.Calendar.getInstance().apply { timeInMillis = st }.get(java.util.Calendar.HOUR_OF_DAY)) {
                        in 4..11 -> "Morning Run"; in 12..16 -> "Afternoon Run"; in 17..20 -> "Evening Run"; else -> "Night Run" }
                addView(label(title, Color.WHITE, 13f, true))
                addView(label(java.text.SimpleDateFormat("EEE, MMM d · h:mm a", java.util.Locale.getDefault()).format(java.util.Date(st)), C.MUTED, 9f))
                val dist = run.optDouble("distance"); val secs = run.optInt("movingSeconds")
                addView(LinearLayout(this@RunActivity).apply {
                    orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(4), 0, 0)
                    fun stat(v: String, l: String, c: Int = Color.WHITE) = addView(column().apply { addView(label(v, c, 13f, true)); addView(label(l, C.MUTED, 8f)) }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                    stat(Run.miles(dist), "miles"); stat(io.github.michealsizemore1.beyondstride.RunMath.clock(secs), "time")
                    stat(io.github.michealsizemore1.beyondstride.RunMath.paceFor(secs, dist), "pace /mi")
                })
                val extra = listOfNotNull(run.optInt("avgHr").takeIf { it > 0 }?.let { "HR $it" }, run.optInt("avgPower").takeIf { it > 0 }?.let { "$it W" }).joinToString(" · ")
                if (extra.isNotEmpty()) addView(label(extra, C.MUTED, 10f))
                setOnClickListener { startActivity(Intent(this@RunActivity, RunHistoryActivity::class.java)) }
            })
        }

        // 5. Sensors, compact (watch only).
        col.addView(card().apply {
            fun row(name: String, key: String) = LinearLayout(this@RunActivity).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(label(name, C.MUTED, 10f).apply { gravity = Gravity.START }, LinearLayout.LayoutParams(dp(44), WRAP_CONTENT))
                addView(label("", Color.WHITE, 10f, true).apply { gravity = Gravity.START; maxLines = 2; startValues[key] = this }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            }
            addView(row("Stryd", "stryd")); addView(row("HR", "hr")); addView(row("GPS", "gps"))
            setOnClickListener {
                if (!Run.strydConnected) RunService.instance?.let { s -> if (prefs.getLong("strydFreeUntil", 0L) > System.currentTimeMillis()) s.connectStryd() else s.pairStryd() }
                if (Run.useStrap && !Run.strapActive) RunService.instance?.pairStrap()
            }
        })

        // 6. Menu.
        fun navButton(text: String, color: Int, textColor: Int, onClick: () -> Unit) = label(text, textColor, 14f, true).apply {
            background = rounded(color, 22)
            setPadding(0, dp(9), 0, dp(9))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) }
            setOnClickListener { onClick() }
        }
        Run.nav?.trail?.let { t -> col.addView(navButton("Directions to trail start", C.CARD, Color.WHITE) { showDirections(t) }) }
        // Menu row — same as the phone's bottom tabs: Today · Fitness · Plan · Runs (Settings under it).
        col.addView(View(this).apply { setBackgroundColor(0x22FFFFFF) }, LinearLayout.LayoutParams(MATCH_PARENT, dp(1)).apply { topMargin = dp(10) })
        col.addView(menuRow(0))
        col.addView(label("⚙ Settings", 0xFFC9D1CE.toInt(), 14f, true).apply {
            setPadding(0, dp(10), 0, dp(6)); setOnClickListener { showSettings = true; startBuilt = false; renderStart() }
        })

        root.removeAllViews()
        root.addView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(col); requestFocus() })
    }

    /** After a run: back to the Running home screen (sensors on again, ready for the next run). */
    private fun backToHome() {
        screen = Screen.START
        summary = null
        showSettings = false; overlayOpen = false; editorRotary = null
        liveBuilt = false; startBuilt = false
        if (!RunService.running) RunService.start(this)
        render()
    }

    private fun startRun() {
        if (!RunService.running) RunService.start(this)
        RunService.instance?.connectStryd()   // a new run always wants the pod
        Run.start()
        Run.readinessAtStart = try { Recovery.result(this)?.json() } catch (e: Exception) { null }
        // Open on your first data screen (the workout step and trail map are a swipe to the left).
        screen = Screen.LIVE; page = pageOrder().indexOf(100).takeIf { it >= 0 } ?: 1; liveBuilt = false
        render()
    }

    /** All the run settings (the phone's Run settings send zones here too). */
    private fun card2() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(8), dp(12), dp(8))
        background = rounded(C.CARD, 16)
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }
    }

    private fun buildSettings() {
        val w = resources.displayMetrics.widthPixels
        val col = column().apply { setPadding(w / 8, w / 7, w / 8, w / 5) }
        col.addView(label("Settings", Color.WHITE, 18f, true))

        val cards = prefs.getString("menuStyle", "cards") != "classic"
        val icons = mapOf("live" to "◉", "textScale" to "Aa", "trailMap" to "▲", "hrcap" to "♡", "screensRow" to "▦", "voiceRow" to "♪",
            "bottomBtn" to "●", "keepOn" to "☼", "source" to "➤", "stryd" to "S", "hrmax" to "∧", "hrrest" to "∨", "cp" to "W",
            "cpauto" to "↻", "cal" to "±", "gps" to "⌖", "hrmode" to "♡", "hr" to "♥", "autopause" to "Ⅱ", "track" to "⋯", "menuStyle" to "☰", "bgStyle" to "◐")
        fun row(key: String, title: String, onTap: (() -> Unit)?) = LinearLayout(this).apply {
            if (cards) {
                // Card style: icon · name · value (green) · ›
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(8), dp(8), dp(8))
                background = rounded(C.CARD, 16)
                addView(label(icons[key] ?: "•", C.ACCENT, 15f, true), LinearLayout.LayoutParams(dp(26), WRAP_CONTENT))
                addView(column().apply {
                    gravity = Gravity.START; setPadding(dp(6), 0, dp(4), 0)
                    addView(label(title, Color.WHITE, 12f).apply { gravity = Gravity.START })
                    addView(label("", 0xFF5FD38D.toInt(), 11f).apply { gravity = Gravity.START }.also { startValues[key] = it })
                }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                if (onTap != null) addView(label("›", C.MUTED, 16f), LinearLayout.LayoutParams(dp(12), WRAP_CONTENT))
            } else {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(6), dp(12), dp(6))
                background = rounded(C.CARD, 14)
                addView(label(title, C.MUTED, 11f))
                addView(label("", Color.WHITE, 14f, true).also { startValues[key] = it })
            }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(5) }
            if (onTap != null) setOnClickListener { onTap(); if (editorRotary == null && !overlayOpen) renderStart() }
        }

        col.addView(row("live", "LiveTrack (GPS on, shares your run)") {
            if (prefs.getString("liveDb", "").isNullOrBlank()) flash("Set up LiveTrack in the phone app first")
            else prefs.edit().putBoolean("liveOn", !prefs.getBoolean("liveOn", false)).apply()
        })
        col.addView(row("textScale", "Text size") {
            val opts = listOf(1f, 1.25f, 1.5f)
            val cur = prefs.getFloat("textScale", 1.25f)
            val next = opts[(opts.indexOfFirst { kotlin.math.abs(it - cur) < 0.01f } + 1) % opts.size]
            prefs.edit().putFloat("textScale", next).apply()
            startBuilt = false; liveBuilt = false; builtScreen = -1; render()
        })
        col.addView(row("trailMap", "Topo map on trail screen (saved on watch)") {
            prefs.edit().putBoolean("trailMapTiles", !prefs.getBoolean("trailMapTiles", true)).apply()
        })
        col.addView(row("hrcap", "Easy-day heart-rate cap") {
            val next = when (Run.hrCapMode) { "off" -> "easy"; "easy" -> "all"; else -> "off" }
            Run.hrCapMode = next; prefs.edit().putString("hrCap", next).apply()
        })
        col.addView(row("screensRow", "Data screens (during a run)") { showScreensEditor() })
        col.addView(row("voiceRow", "Audio prompts") { showVoiceSettings() })
        col.addView(row("bottomBtn", "Bottom button in a run: lap · double-press finish") {
            prefs.edit().putBoolean("bottomBtn", !prefs.getBoolean("bottomBtn", true)).apply()
        })
        col.addView(row("keepOn", "Keep screen on in BeyondStride") {
            val on = !prefs.getBoolean("keepOn", true)
            prefs.edit().putBoolean("keepOn", on).apply()
            if (on) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        })
        col.addView(row("source", "Pace & distance") {
            Run.source = if (Run.source == Run.Source.STRYD) Run.Source.GPS else Run.Source.STRYD
            prefs.edit().putString("source", Run.source.name.lowercase()).apply()
        })
        col.addView(row("stryd", "Stryd footpod (tap to re-pair)") {
            if (!Run.strydConnected) RunService.instance?.pairStryd()
        })
        col.addView(row("hrmax", "Max heart rate (HR zones)") {
            editNumber("MAX HEART RATE", Run.hrMax, 1.0, 140.0, 230.0, { "${it.toInt()} bpm" }) {
                Run.hrMax = it; prefs.edit().putFloat("hrMax", it.toFloat()).apply()
            }
        })
        col.addView(row("hrrest", "Resting heart rate (HR zones)") {
            editNumber("RESTING HEART RATE", Run.hrRest, 0.5, 30.0, 90.0, { "${fmtHr(it)} bpm" }) {
                Run.hrRest = it; prefs.edit().putFloat("hrRest", it.toFloat()).apply()
            }
        })
        col.addView(row("cp", "Critical Power (power zones)") {
            editNumber("CRITICAL POWER", Run.criticalPower.toDouble(), 1.0, 100.0, 600.0, { "${it.toInt()} W" }) {
                Run.criticalPower = it.toInt(); prefs.edit().putInt("cp", Run.criticalPower).apply()
            }
        })
        col.addView(row("cpauto", "Update CP from your runs") {
            val next = when (prefs.getString("cpAuto", "ask")) { "ask" -> "auto"; "auto" -> "off"; else -> "ask" }
            prefs.edit().putString("cpAuto", next).apply(); renderStart()
        })
        col.addView(row("cal", "Stryd calibration") {
            editNumber("STRYD CALIBRATION", Run.strydCalibration, 0.005, 0.900, 1.100,
                { String.format(java.util.Locale.US, "%.3f", it) }) {
                Run.strydCalibration = it; prefs.edit().putFloat("cal", it.toFloat()).apply()
            }
        }.also { calRow = it })
        col.addView(row("gps", "GPS", null))
        col.addView(row("hrmode", "Heart-rate sensor") {
            Run.useStrap = !Run.useStrap
            prefs.edit().putBoolean("useStrap", Run.useStrap).apply()
            RunService.instance?.applyHrMode()
        })
        col.addView(row("hr", "Heart rate (tap to re-pair strap)") {
            if (Run.useStrap && !Run.strapActive) RunService.instance?.pairStrap()
        })
        col.addView(row("autopause", "Auto-pause") {
            Run.autoPause = !Run.autoPause; prefs.edit().putBoolean("autoPause", Run.autoPause).apply()
        })
        col.addView(row("track", "GPS map track (uses battery)") {
            Run.recordGpsTrack = !Run.recordGpsTrack; prefs.edit().putBoolean("track", Run.recordGpsTrack).apply()
        }.also { trackRow = it })

        col.addView(row("bgStyle", "Background") {
            prefs.edit().putString("bgStyle", if (io.github.michealsizemore1.beyondstride.Theme.light) "dark" else "light").apply()
            io.github.michealsizemore1.beyondstride.Theme.load(this, "run"); startBuilt = false
        })
        col.addView(row("menuStyle", "Menu style") {
            prefs.edit().putString("menuStyle", if (cards) "classic" else "cards").apply(); startBuilt = false
        })
        // App update (from GitHub), same as the phone's Settings → About.
        run {
            val U = io.github.michealsizemore1.beyondstride.Updater
            val box = card2()
            val t = label("", Color.WHITE, 12f, true); val st = label("", C.MUTED, 10f)
            fun showUp() {
                val r = U.available(this)
                t.text = if (r != null) "⬆ Install build ${r.build}" else "Check for updates"
                st.text = if (r != null) "You're on build ${U.currentBuild(this)}" else "Up to date · build ${U.currentBuild(this)}"
            }
            box.addView(t); box.addView(st); showUp()
            box.setOnClickListener {
                val r = U.available(this)
                if (r != null) U.start(this, r) { st.text = it }
                else { st.text = "Checking…"; U.forceNext(this); Thread { U.check(this, true); runOnUiThread { showUp() } }.start() }
            }
            col.addView(box)
        }
        col.addView(label("Done", Color.BLACK, 16f, true).apply {
            background = rounded(C.ACCENT, 24)
            setPadding(0, dp(10), 0, dp(10))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) }
            setOnClickListener { showSettings = false; startBuilt = false; renderStart() }
        })
        col.addView(label("Watch app " + packageManager.getPackageInfo(packageName, 0).versionName, C.DIM, 10f).apply {
            setPadding(0, dp(12), 0, 0)
        })

        root.removeAllViews()
        root.addView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(col); requestFocus() })
    }

    /** Full-screen number picker: bezel or − / + to change, Save to keep. */
    private fun editNumber(
        title: String, initial: Double, step: Double, min: Double, max: Double,
        format: (Double) -> String, onSave: (Double) -> Unit,
    ) {
        var value = initial.coerceIn(min, max)
        val w = resources.displayMetrics.widthPixels
        val col = column().apply {
            gravity = Gravity.CENTER
            setPadding(w / 8, w / 8, w / 8, w / 8)
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
        col.addView(label(title, C.ACCENT, 12f, true).apply { letterSpacing = 0.1f })
        val valueView = label(format(value), Color.WHITE, 34f, true)
        fun change(steps: Int) {
            value = (Math.round((value + steps * step) / step) * step).coerceIn(min, max)
            valueView.text = format(value)
        }
        fun small(text: String, steps: Int) = label(text, Color.WHITE, 24f, true).apply {
            background = rounded(C.CARD, 22)
            setPadding(0, dp(4), 0, dp(4))
            setOnClickListener { change(steps) }
            setOnLongClickListener { change(steps * 10); true }
        }
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(small("−", -1), LinearLayout.LayoutParams(dp(44), WRAP_CONTENT))
            addView(valueView, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(small("+", 1), LinearLayout.LayoutParams(dp(44), WRAP_CONTENT))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }
        })
        col.addView(label("Turn bezel · hold ± for big steps", C.DIM, 10f).apply { setPadding(0, dp(4), 0, 0) })
        fun close() { editorRotary = null; startBuilt = false; renderStart() }
        col.addView(label("SAVE", Color.BLACK, 16f, true).apply {
            background = rounded(C.ACCENT, 24)
            setPadding(0, dp(10), 0, dp(10))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) }
            setOnClickListener { onSave(value); close() }
        })
        col.addView(label("Cancel", C.MUTED, 13f, true).apply {
            setPadding(0, dp(10), 0, 0)
            setOnClickListener { close() }
        })
        editorRotary = { change(it) }
        startBuilt = false
        root.removeAllViews()
        root.addView(col)
        root.isFocusable = true; root.isFocusableInTouchMode = true; root.requestFocus()
    }

    // ---------------------------------------------------------------- trails (no phone needed: GPS + LTE)

    private var trailCache: Pair<Long, List<io.github.michealsizemore1.beyondstride.Trail>>? = null
    private var trailStatus = ""

    private var trailKey = ""
    private var trailShowAll = false

    private fun showTrails(): Unit = overlay { col ->
        val place = io.github.michealsizemore1.beyondstride.TrailSearch.area(this, "run")
        val radius = io.github.michealsizemore1.beyondstride.TrailSearch.radiusMi(this, "run")
        val mode = io.github.michealsizemore1.beyondstride.TrailSearch.sortMode(this, "run")
        val band = io.github.michealsizemore1.beyondstride.TrailSearch.lengthBand(this, "run")
        // Search settings changed (here or on the phone) → search again.
        val key = "${place?.encode()}|$radius"
        if (key != trailKey) { trailKey = key; trailCache = null; trailStatus = ""; trailShowAll = false }
        val from = place?.label?.substringBefore(",")
        col.addView(label(if (from == null) "Trails near me" else "Trails · $from", Color.WHITE, 18f, true))
        val saved = io.github.michealsizemore1.beyondstride.Courses.list(this)
        if (saved.isNotEmpty()) col.addView(card("★ Saved courses (${saved.size})", saved.take(2).joinToString(", ") { it.name }, 0xFFD4E635.toInt()) { showCourses() })
        col.addView(card("Where: " + (from ?: "Near me"), "Tap to change", C.ACCENT) { showTrailWhere() })
        col.addView(card("Within $radius mi · " + (io.github.michealsizemore1.beyondstride.TrailSearch.SORTS.first { it.first == mode }.second.lowercase()) + " first",
            (if (band > 0) io.github.michealsizemore1.beyondstride.TrailSearch.LENGTHS[band].first + " · " else "") + "Tap to change") { showTrailRange() })
        if (Run.nav != null) col.addView(card("No trail", "Clear ${Run.nav?.trail?.name}", C.STOP) {
            Run.chooseTrail(null, false); io.github.michealsizemore1.beyondstride.TrailSync.choose(this, null, false, "watch")
            overlayOpen = false; startBuilt = false; renderStart()
        })
        val cached = trailCache?.takeIf { System.currentTimeMillis() - it.first < 30 * 60_000 }?.second
        if (cached == null) {
            col.addView(label(trailStatus.ifBlank { "Finding your location…" }, C.MUTED, 12f).apply { setPadding(0, dp(10), 0, 0) })
            if (trailStatus.isBlank() || trailStatus.startsWith("Finding")) loadTrails()
            else col.addView(card("Try again", "", C.ACCENT) { trailStatus = ""; showTrails() })
            return@overlay
        }
        if (cached.isEmpty()) col.addView(label("No mapped trails within $radius miles.", C.MUTED, 12f))
        val sorted = io.github.michealsizemore1.beyondstride.TrailSearch.let { it.sort(it.byLength(cached, band), mode) }
        if (sorted.isEmpty() && cached.isNotEmpty()) col.addView(label("No trails ${io.github.michealsizemore1.beyondstride.TrailSearch.LENGTHS[band].first} here — change the length.", C.MUTED, 12f))
        (if (trailShowAll) sorted else sorted.take(25)).forEach { t ->
            val mi = t.lengthM / 1609.344
            col.addView(card(t.name, String.format(java.util.Locale.US, "%.1f mi %s · %s · %s", mi, if (t.loop) "loop" else "one way",
                t.difficulty.takeIf { t.elevations != null || t.sac.isNotBlank() } ?: (if (t.surface.isNotBlank()) t.surface else "trail"),
                distText(t.distToStartM) + if (from == null) " away" else " from $from")) { showTrail(t) })
        }
        if (!trailShowAll && sorted.size > 25) col.addView(card("Show all ${sorted.size}", "", C.ACCENT) { trailShowAll = true; showTrails() })
        col.addView(card("Refresh", "Search again from here", C.ACCENT) { trailCache = null; trailStatus = ""; showTrails() })
    }

    /** Saved courses (same list as the phone). */
    private fun showCourses(): Unit = overlay { col ->
        col.addView(label("Saved courses", Color.WHITE, 18f, true))
        val here = if (Run.gpsFix && System.currentTimeMillis() - Run.gpsLastUpdate < 10 * 60_000) doubleArrayOf(Run.gpsLat, Run.gpsLon) else lastKnown()
        val list = io.github.michealsizemore1.beyondstride.Courses.list(this)
        if (list.isEmpty()) col.addView(label("None yet. Open a trail and tap Save course, or save a run's route on the phone.", C.MUTED, 11f))
        list.forEach { c ->
            here?.let { me -> c.distToStartM = c.points.minOf { io.github.michealsizemore1.beyondstride.Geo.dist(it, me) } }
            col.addView(card(c.name, String.format(java.util.Locale.US, "%.1f mi %s", c.lengthM / 1609.344, if (c.loop) "loop" else "one way") +
                (if (here != null) " · " + distText(c.distToStartM) + " away" else "")) { showTrail(c) })
        }
        col.addView(card("Back", "", C.ACCENT) { showTrails() })
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun lastKnown(): DoubleArray? = try {
        val lm = getSystemService(android.location.LocationManager::class.java)
        listOf(android.location.LocationManager.GPS_PROVIDER, android.location.LocationManager.NETWORK_PROVIDER, android.location.LocationManager.FUSED_PROVIDER)
            .mapNotNull { try { lm.getLastKnownLocation(it) } catch (e: Exception) { null } }
            .maxByOrNull { it.time }?.let { doubleArrayOf(it.latitude, it.longitude) }
    } catch (e: Exception) { null }

    /** Google Maps directions to the trail start — on the watch, or on the phone if the watch has no Maps. */
    private fun showDirections(t: io.github.michealsizemore1.beyondstride.Trail): Unit = overlay { col ->
        val s0 = t.points.first()
        col.addView(label("Directions to start", Color.WHITE, 18f, true))
        col.addView(label(t.name, C.MUTED, 11f))
        fun go(mode: String) {
            if (!io.github.michealsizemore1.beyondstride.Directions.open(this, s0[0], s0[1], mode)) {
                RunSync.tellPhone(this, "/cmd/directions", "${s0[0]},${s0[1]},$mode,${t.name}".toByteArray()) { ok ->
                    runOnUiThread { flash(if (ok) "Sent to your phone" else "No Google Maps here or phone") }
                }
            }
        }
        col.addView(card("Walk / run there", "Google Maps on the watch", C.ACCENT) { go("w") })
        col.addView(card("Drive there", "Google Maps on the watch", C.ACCENT) { go("d") })
        col.addView(card("Send to phone", "Directions notification on the phone") {
            RunSync.tellPhone(this, "/cmd/directions", "${s0[0]},${s0[1]},d,${t.name}".toByteArray()) { ok ->
                runOnUiThread { flash(if (ok) "Sent to your phone" else "Phone not reachable") }
            }
        })
        col.addView(card("Back", "", C.ACCENT) { showTrail(t) })
    }

    /** Where to search: near me, a recent place, or type/say a new one. */
    private fun showTrailWhere(): Unit = overlay { col ->
        val TSr = io.github.michealsizemore1.beyondstride.TrailSearch
        col.addView(label("Search where?", Color.WHITE, 18f, true))
        col.addView(card("Near me", "Uses GPS", C.ACCENT) { TSr.setArea(this, "run", "watch", null); showTrails() })
        recentPlaces().forEach { pl ->
            col.addView(card(pl.label.substringBefore(","), pl.label.substringAfter(", ", "")) { TSr.setArea(this, "run", "watch", pl); showTrails() })
        }
        col.addView(label("Or type a town:", C.MUTED, 11f).apply { setPadding(0, dp(10), 0, 0) })
        val input = android.widget.EditText(this).apply {
            hint = "e.g. Boise"; setSingleLine(); setTextColor(Color.WHITE); setHintTextColor(C.MUTED); textSize = 14f
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            background = rounded(C.CARD, 14); setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        val msg = label("", C.MUTED, 11f)
        fun go() {
            val q = input.text.toString().trim(); if (q.isEmpty()) return
            msg.text = "Looking up $q…"
            Thread {
                val found = try { TSr.geocode(q) } catch (e: Exception) { null }
                runOnUiThread {
                    when {
                        found == null -> msg.text = "No connection"
                        found.isEmpty() -> msg.text = "No place called \"$q\""
                        found.size == 1 -> { TSr.setArea(this, "run", "watch", found[0]); rememberPlace(found[0]); showTrails() }
                        else -> overlay { c2 ->
                            c2.addView(label("Which one?", Color.WHITE, 18f, true))
                            found.forEach { pl -> c2.addView(card(pl.label.substringBefore(","), pl.label.substringAfter(", ", "")) {
                                TSr.setArea(this, "run", "watch", pl); rememberPlace(pl); showTrails() }) }
                            c2.addView(card("Back", "", C.ACCENT) { showTrailWhere() })
                        }
                    }
                }
            }.start()
        }
        input.setOnEditorActionListener { _, _, _ -> go(); true }
        col.addView(input)
        col.addView(card("Search", "", C.ACCENT) { go() })
        col.addView(msg)
        col.addView(card("Back", "", C.ACCENT) { showTrails() })
    }

    /** How far, and sort order. */
    private fun showTrailRange(): Unit = overlay { col ->
        val TSr = io.github.michealsizemore1.beyondstride.TrailSearch
        val r = TSr.radiusMi(this, "run"); val mode = TSr.sortMode(this, "run"); val band = TSr.lengthBand(this, "run")
        col.addView(label("How far?", Color.WHITE, 18f, true))
        TSr.RADII.forEach { mi -> col.addView(card((if (mi == r) "✓ " else "") + "Within $mi mi", "", if (mi == r) C.ACCENT else Color.WHITE) {
            TSr.setRadius(this, "run", "watch", mi); showTrailRange() }) }
        col.addView(label("Sort", C.MUTED, 11f).apply { setPadding(0, dp(10), 0, 0) })
        TSr.SORTS.forEach { (m, name) -> col.addView(card((if (m == mode) "✓ " else "") + "$name first", "", if (m == mode) C.ACCENT else Color.WHITE) {
            TSr.setSort(this, "run", "watch", m); showTrailRange() }) }
        col.addView(label("Trail length", C.MUTED, 11f).apply { setPadding(0, dp(10), 0, 0) })
        TSr.LENGTHS.forEachIndexed { i, (name, _, _) -> col.addView(card((if (i == band) "✓ " else "") + name, "", if (i == band) C.ACCENT else Color.WHITE) {
            TSr.setLength(this, "run", "watch", i); showTrailRange() }) }
        col.addView(card("Done", "", C.ACCENT) { showTrails() })
    }

    private fun recentPlaces(): List<io.github.michealsizemore1.beyondstride.TrailSearch.Place> {
        val list = (prefs.getString("trailRecent", "") ?: "").split("\n").mapNotNull { line ->
            line.split("|").takeIf { it.size == 3 }?.let { io.github.michealsizemore1.beyondstride.TrailSearch.Place(it[0], it[1].toDoubleOrNull() ?: return@mapNotNull null, it[2].toDoubleOrNull() ?: return@mapNotNull null) }
        }.toMutableList()
        io.github.michealsizemore1.beyondstride.TrailSearch.area(this, "run")?.let { cur -> if (list.none { it.label == cur.label }) list.add(0, cur) }
        return list.take(5)
    }
    private fun rememberPlace(pl: io.github.michealsizemore1.beyondstride.TrailSearch.Place) {
        val l = listOf(pl) + recentPlaces().filter { it.label != pl.label }
        prefs.edit().putString("trailRecent", l.take(5).joinToString("\n") { it.encode() }).apply()
    }

    private fun distText(m: Double) = if (m < 160) "${(m * 3.28084).toInt()} ft" else String.format(java.util.Locale.US, "%.1f mi", m / 1609.344)

    /** Gets a location (GPS, or the last known one if recent), then trails around it from OpenStreetMap. */
    @android.annotation.SuppressLint("MissingPermission")
    private fun loadTrails() {
        io.github.michealsizemore1.beyondstride.TrailSearch.area(this, "run")?.let { pl ->
            trailStatus = "Looking for trails…"
            Thread {
                trailStatus = try {
                    trailCache = System.currentTimeMillis() to io.github.michealsizemore1.beyondstride.Trails.fetchNearby(pl.lat, pl.lon,
                        (io.github.michealsizemore1.beyondstride.TrailSearch.radiusMi(this, "run") * 1609.344).toInt()); ""
                } catch (e: Exception) { "Couldn't get trails: ${e.message}" }
                runOnUiThread { if (overlayOpen) showTrails() }
            }.start()
            return
        }
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            trailStatus = "Allow location for BeyondStride to find trails"; return
        }
        trailStatus = "Finding your location…"
        val lm = getSystemService(android.location.LocationManager::class.java)
        fun search(lat: Double, lon: Double) {
            trailStatus = "Looking for trails…"
            runOnUiThread { if (overlayOpen) showTrails() }
            Thread {
                trailStatus = try {
                    val list = io.github.michealsizemore1.beyondstride.Trails.fetchNearby(lat, lon,
                        (io.github.michealsizemore1.beyondstride.TrailSearch.radiusMi(this, "run") * 1609.344).toInt())
                    trailCache = System.currentTimeMillis() to list
                    ""
                } catch (e: Exception) { "Couldn't get trails: ${e.message}" }
                runOnUiThread { if (overlayOpen) showTrails() }
            }.start()
        }
        val recent = listOf(android.location.LocationManager.GPS_PROVIDER, android.location.LocationManager.NETWORK_PROVIDER, android.location.LocationManager.FUSED_PROVIDER)
            .mapNotNull { try { lm.getLastKnownLocation(it) } catch (e: Exception) { null } }
            .filter { System.currentTimeMillis() - it.time < 10 * 60_000 }.maxByOrNull { it.time }
        if (recent != null) { search(recent.latitude, recent.longitude); return }
        if (Run.gpsFix && System.currentTimeMillis() - Run.gpsLastUpdate < 60_000) { search(Run.gpsLat, Run.gpsLon); return }
        try {
            lm.getCurrentLocation(android.location.LocationManager.GPS_PROVIDER, null, mainExecutor) { loc ->
                if (loc != null) search(loc.latitude, loc.longitude)
                else { trailStatus = "No GPS fix — step outside and try again"; if (overlayOpen) showTrails() }
            }
        } catch (e: Exception) { trailStatus = "Location is off"; }
    }

    /** One trail: facts, elevation profile, and how to run it. */
    private fun showTrail(t: io.github.michealsizemore1.beyondstride.Trail): Unit = overlay { col ->
        val mi = t.lengthM / 1609.344
        col.addView(label(t.name, Color.WHITE, 16f, true))
        val facts = mutableListOf(String.format(java.util.Locale.US, "%.2f mi %s", mi, if (t.loop) "loop" else "one way"))
        if (t.elevations != null) facts += "${(t.climbM * 3.28084).toInt()} ft climb"
        facts += t.difficulty
        if (t.surface.isNotBlank()) facts += t.surface
        if (t.distToStartM > 0) facts += distText(t.distToStartM) + " away"
        col.addView(label(facts.joinToString(" · "), C.MUTED, 11f))
        val w = resources.displayMetrics.widthPixels * 3 / 4
        val profile = android.widget.ImageView(this)
        col.addView(profile, LinearLayout.LayoutParams(w, dp(50)).apply { topMargin = dp(6); gravity = Gravity.CENTER_HORIZONTAL })
        fun showProfile() { TrailProfile.bitmap(t, w, dp(50), resources.displayMetrics.density)?.let { profile.setImageBitmap(it) } }
        if (t.elevations == null) {
            col.addView(label("Loading elevation…", C.DIM, 10f).also { status ->
                Thread {
                    val ok = try { io.github.michealsizemore1.beyondstride.Trails.fetchElevation(t); true } catch (e: Exception) { false }
                    runOnUiThread { if (ok) { status.text = ""; showTrail(t) } else status.text = "Elevation unavailable" }
                }.start()
            })
        } else showProfile()
        fun choose(outAndBack: Boolean) {
            Run.chooseTrail(t, outAndBack); TrailTiles.ensure(this, t)   // save the topo map now (home Wi-Fi)
            io.github.michealsizemore1.beyondstride.TrailSync.choose(this, t, outAndBack, "watch")   // shows on the phone too
            overlayOpen = false; startBuilt = false; renderStart()
            flash("Trail set — press Start")
        }
        if (t.loop) col.addView(card("Run this loop", String.format(java.util.Locale.US, "%.2f mi", mi), C.ACCENT) { choose(false) })
        else {
            col.addView(card("Out & back", String.format(java.util.Locale.US, "%.2f mi · turn around at the end", mi * 2), C.ACCENT) { choose(true) })
            col.addView(card("One way", String.format(java.util.Locale.US, "%.2f mi", mi), C.ACCENT) { choose(false) })
        }
        // Topo map for this trail: download now (e.g. at home on Wi-Fi), saved on the watch.
        if (TrailTiles.enabled(this)) {
            val ready = TrailTiles.isSaved(this, t)
            val mapCard = card(if (ready) "Map saved ✓" else "Download map", if (ready) "Works on the trail with no signal" else "Do this at home on Wi-Fi", if (ready) 0xFF46C46E.toInt() else C.ACCENT) {
                TrailTiles.ensure(this, t, force = true); flash("Downloading map…")
            }
            col.addView(mapCard)
            // Live progress while this screen is open.
            val tick = object : Runnable { override fun run() {
                if (!mapCard.isAttachedToWindow) return
                if (TrailTiles.readyFor == t.id) (mapCard.getChildAt(0) as? TextView)?.apply { text = "Map saved ✓"; setTextColor(0xFF46C46E.toInt()) }
                (mapCard.getChildAt(1) as? TextView)?.text = if (TrailTiles.readyFor == t.id) "Saved — works with no signal"
                    else TrailTiles.status.ifBlank { "Do this at home on Wi-Fi" }
                handler.postDelayed(this, 1000)
            } }
            handler.postDelayed(tick, 1000)
        }
        col.addView(card("Directions to start", "Google Maps", Color.WHITE) { showDirections(t) })
        val isSaved = io.github.michealsizemore1.beyondstride.Courses.isSaved(this, t.id)
        col.addView(card(if (isSaved) "★ Saved course" else "☆ Save course", if (isSaved) "Tap to remove" else "Also saves on the phone", 0xFFD4E635.toInt()) {
            if (isSaved) io.github.michealsizemore1.beyondstride.Courses.remove(this, t.id, "watch") else io.github.michealsizemore1.beyondstride.Courses.save(this, t, "watch")
            flash(if (isSaved) "Removed" else "Saved"); showTrail(t)
        })
        col.addView(card("Back", "", C.ACCENT) { if (isSaved || io.github.michealsizemore1.beyondstride.Courses.isSaved(this, t.id)) showCourses() else showTrails() })
    }

    // ---------------------------------------------------------------- audio prompts settings

    private fun showVoiceSettings(): Unit = overlay { col ->
        col.addView(label("Audio prompts", Color.WHITE, 18f, true))
        col.addView(label("Voice: " + Speaker.status + ". Plays through Bluetooth headphones when connected, " +
            "otherwise the watch speaker. Volume = watch media volume.", C.MUTED, 10f))
        fun toggle(key: String, title: String, def: Boolean = true) =
            card(title, if (prefs.getBoolean(key, def)) "On" else "Off", if (prefs.getBoolean(key, def)) C.ACCENT else Color.WHITE) {
                prefs.edit().putBoolean(key, !prefs.getBoolean(key, def)).apply(); showVoiceSettings()
            }
        col.addView(toggle("voice", "Audio prompts"))
        col.addView(toggle("voiceLaps", "Each lap / mile: time, pace, HR, power"))
        val every = prefs.getInt("voiceEvery", 0)
        col.addView(card("Time update every", if (every == 0) "Off" else "$every minutes", C.ACCENT) {
            val next = listOf(0, 5, 10, 15, 20, 30).let { it[(it.indexOf(every) + 1) % it.size] }
            prefs.edit().putInt("voiceEvery", next).apply(); showVoiceSettings()
        })
        col.addView(toggle("voiceSteps", "Workout steps (what's next)"))
        col.addView(toggle("voiceAlerts", "Target alerts (speed up / ease off)"))
        val music = prefs.getString("voiceMusic", "duck")
        col.addView(card("Music while speaking", if (music == "pause") "Pause it" else "Lower it", C.ACCENT) {
            prefs.edit().putString("voiceMusic", if (music == "pause") "duck" else "pause").apply(); showVoiceSettings()
        })
        col.addView(toggle("voiceMiles", "Mile alerts on easy (zone 2) days"))
        col.addView(toggle("voiceBoost", "Louder prompts (turns volume up while speaking)"))
        col.addView(card("Test the voice", "", C.ACCENT) {
            Speaker.say("Lap 1. 8 minutes 32 seconds. Pace 8 32. Heart rate 142", force = true)
        })
        col.addView(card("Done", "", C.ACCENT) { overlayOpen = false; startBuilt = false; renderStart() })
    }

    // ---------------------------------------------------------------- data screens editor (watch)

    private fun saveScreens() {
        prefs.edit().putString("screens", screens.toString()).apply()
        builtScreen = -1
        // (sent to the phone by SettingsSync, like every other setting)
    }

    private fun overlay(build: (LinearLayout) -> Unit) {
        overlayOpen = true; startBuilt = false
        val w = resources.displayMetrics.widthPixels
        val col = column().apply { setPadding(w / 8, w / 7, w / 8, w / 4) }
        build(col)
        root.removeAllViews()
        root.addView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(col); requestFocus() })
    }

    private fun card(title: String, sub: String, titleColor: Int = Color.WHITE, onTap: () -> Unit) = LinearLayout(this).apply {
        val cards = prefs.getString("menuStyle", "cards") != "classic"
        background = rounded(C.CARD, 14)
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(5) }
        if (cards && sub.isNotBlank()) {
            // Card style: name · value (green) · ›
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(7), dp(8), dp(7))
            addView(column().apply {
                gravity = Gravity.START
                addView(label(title, if (titleColor == C.ACCENT) Color.WHITE else titleColor, 13f, true).apply { gravity = Gravity.START })
                addView(label(sub, 0xFF5FD38D.toInt(), 11f).apply { gravity = Gravity.START })
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(label("›", C.MUTED, 16f), LinearLayout.LayoutParams(dp(12), WRAP_CONTENT))
        } else {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(7), dp(12), dp(7))
            addView(label(title, titleColor, 14f, true))
            if (sub.isNotBlank()) addView(label(sub, C.MUTED, 10f))
        }
        setOnClickListener { onTap() }
    }

    /** List of screens: tap one to change it. */
    private fun showScreensEditor() = overlay { col ->
        val RF = io.github.michealsizemore1.beyondstride.RunFields
        col.addView(label("Data screens", Color.WHITE, 18f, true))
        col.addView(label("Swipe through these during a run. Also editable on the phone.", C.MUTED, 10f))
        for (i in 0 until screens.length()) {
            val sc = screens.getJSONObject(i)
            val f = sc.optJSONArray("fields")
            val names = (0 until RF.size(sc.optString("layout", "6"))).joinToString(" · ") { RF.name(f?.optString(it).orEmpty()) }
            col.addView(card("Screen ${i + 1}", names) { showScreenEditor(i) })
        }
        col.addView(card("+ Add a screen", "", C.ACCENT) {
            screens.put(org.json.JSONObject().put("layout", "4").put("fields", org.json.JSONArray(listOf("time", "pace", "hr", "distance"))))
            saveScreens(); showScreenEditor(screens.length() - 1)
        })
        col.addView(card("Done", "", C.ACCENT) { overlayOpen = false; startBuilt = false; renderStart() })
    }

    /** One screen: its layout and each spot, top to bottom. */
    private fun showScreenEditor(i: Int): Unit = overlay { col ->
        val RF = io.github.michealsizemore1.beyondstride.RunFields
        val sc = screens.getJSONObject(i)
        val layout = RF.layout(sc.optString("layout", "6"))
        val fields = sc.optJSONArray("fields") ?: org.json.JSONArray().also { sc.put("fields", it) }
        while (fields.length() < layout.rows.sum()) fields.put("time")
        col.addView(label("Screen ${i + 1}", Color.WHITE, 18f, true))
        col.addView(card("Layout", layout.name, C.ACCENT) {
            // Tap to step through the layouts.
            val next = RF.LAYOUTS[(RF.LAYOUTS.indexOf(layout) + 1) % RF.LAYOUTS.size]
            sc.put("layout", next.id); saveScreens(); showScreenEditor(i)
        })
        var k = 0
        layout.rows.forEachIndexed { r, count ->
            repeat(count) { c ->
                val idx = k++
                val where = when {
                    layout.rows.size == 1 -> "Number"
                    r == 0 -> "Top"
                    r == layout.rows.size - 1 && count == 1 -> "Bottom"
                    count == 1 -> "Row ${r + 1}"
                    else -> "Row ${r + 1} " + (if (c == 0) "left" else "right")
                }
                col.addView(card(RF.name(fields.optString(idx)), where) { pickMetric(i, idx) })
            }
        }
        if (screens.length() > 1) col.addView(card("Remove this screen", "", C.STOP) {
            screens.remove(i); saveScreens(); showScreensEditor()
        })
        col.addView(card("Back", "", C.ACCENT) { showScreensEditor() })
    }

    private fun pickMetric(i: Int, idx: Int) = overlay { col ->
        val RF = io.github.michealsizemore1.beyondstride.RunFields
        col.addView(label("Show", Color.WHITE, 18f, true))
        RF.METRICS.forEach { (key, name) ->
            col.addView(card(name, "") {
                screens.getJSONObject(i).optJSONArray("fields")?.put(idx, key)
                saveScreens(); showScreenEditor(i)
            })
        }
    }

    /** Pick today's planned workout, one from the library, or a free run. */
    /** One workout in full (like the phone): every step with its targets, and the notes (e.g. strength exercises). */
    private fun showWorkoutDetail(wk: org.json.JSONObject, dayLabel: String, chosen: Boolean = false) {
        overlayOpen = true; startBuilt = false
        val w = resources.displayMetrics.widthPixels
        val col = column().apply { setPadding(w / 9, w / 8, w / 9, w / 5) }
        if (dayLabel.isNotBlank()) col.addView(label(dayLabel.uppercase(), C.ACCENT, 11f, true))
        col.addView(label(wk.optString("name"), Color.WHITE, 17f, true))
        val steps = io.github.michealsizemore1.beyondstride.Workouts.outline(wk, Run.hrMax, Run.hrRest, Run.criticalPower)
        if (steps.isNotEmpty()) col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8)); background = rounded(C.CARD, 16)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) }
            addView(label("STEPS", C.ACCENT, 10f, true).apply { gravity = Gravity.START })
            addView(label(steps.joinToString("\n"), Color.WHITE, 12f).apply { gravity = Gravity.START })
        })
        val notes = io.github.michealsizemore1.beyondstride.Workouts.cleanNotes(wk.optString("notes"))
        if (notes.isNotBlank()) col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8)); background = rounded(C.CARD, 16)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) }
            addView(label("NOTES", C.ACCENT, 10f, true).apply { gravity = Gravity.START })
            addView(label(notes, Color.WHITE, 12f).apply { gravity = Gravity.START })
        })
        fun btn(t: String, bg: Int, fg: Int, go: () -> Unit) = col.addView(label(t, fg, 14f, true).apply {
            background = rounded(bg, 22); setPadding(0, dp(9), 0, dp(9))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }
            setOnClickListener { go() }
        })
        if (chosen) btn("Change workout", C.CARD, Color.WHITE) { showWorkoutPicker() }
        else btn("Use this workout", C.ACCENT, Color.BLACK) { Run.chooseWorkout(wk); overlayOpen = false; startBuilt = false; renderStart() }
        btn("Back", C.CARD, Color.WHITE) { if (chosen) { overlayOpen = false; startBuilt = false; renderStart() } else showWorkoutPicker() }
        root.removeAllViews()
        root.addView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(col); requestFocus() })
    }

    /** Nutrition (from the Command Center via the phone): today's calories vs target, macros, meals, last few days. */
    /** Fluids (± a cup) and wake-up / post-run weight for today, like the phone. Sent to the phone, then the Command Center. */
    private var wakeEdit = 0.0; private var postEdit = 0.0
    private fun showFluids(fresh: Boolean = true): Unit = overlay { col ->
        val N = io.github.michealsizemore1.beyondstride.Nutrition
        val H = io.github.michealsizemore1.beyondstride.Hydration
        val date = nutDay()
        val days = io.github.michealsizemore1.beyondstride.NutritionStore.days(this)
        val t = days.lastOrNull { it.date == date }
        if (fresh) {
            val lastW = days.lastOrNull { it.weight > 0 }?.weight ?: 170.0
            wakeEdit = t?.weight?.takeIf { it > 0 } ?: 0.0
            postEdit = t?.postWeight?.takeIf { it > 0 } ?: 0.0
            if (lastW > 0) prefs.edit().putFloat("lastWeight", lastW.toFloat()).apply()
        }
        col.addView(label("Fluids & weight", Color.WHITE, 17f, true))
        col.addView(label("${t?.waterOz ?: 0} oz today", N.WATER, 13f, true))
        fun stepper(name: String, value: String, minus: () -> Unit, plus: () -> Unit) = col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = rounded(C.CARD, 16); setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(5) }
            addView(label("−", C.ACCENT, 22f, true).apply { setOnClickListener { minus() } }, LinearLayout.LayoutParams(dp(36), WRAP_CONTENT))
            addView(column().apply {
                addView(label(name, C.MUTED, 10f)); addView(label(value, Color.WHITE, 14f, true))
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(label("+", C.ACCENT, 22f, true).apply { setOnClickListener { plus() } }, LinearLayout.LayoutParams(dp(36), WRAP_CONTENT))
        })
        val names = mapOf("water" to "Water", "sports" to "Sports drinks", "other" to "Other drinks")
        H.FLUIDS.forEach { f ->
            val v = when (f) { "water" -> t?.water; "sports" -> t?.sportsOz; else -> t?.otherOz } ?: 0
            stepper(names[f]!!, "$v oz", { watchFluid(f, -H.STEP) }, { watchFluid(f, H.STEP) })
        }
        val start = prefs.getFloat("lastWeight", 170f).toDouble()
        fun step(cur: Double, d: Double) = Math.round(((if (cur > 0) cur else start) + d) * 10) / 10.0
        stepper("Wake-up weight", if (wakeEdit > 0) "${H.lb(wakeEdit)} lb" else "— tap + / −", { wakeEdit = step(wakeEdit, -0.2); showFluids(false) }, { wakeEdit = step(wakeEdit, 0.2); showFluids(false) })
        stepper("Post-run weight", if (postEdit > 0) "${H.lb(postEdit)} lb" else "— tap + / −", { postEdit = step(postEdit, -0.2); showFluids(false) }, { postEdit = step(postEdit, 0.2); showFluids(false) })
        val changed = wakeEdit != (t?.weight ?: 0.0) || postEdit != (t?.postWeight ?: 0.0)
        if (changed) col.addView(label("Save weights", Color.BLACK, 14f, true).apply {
            background = rounded(C.ACCENT, 24); setPadding(0, dp(8), 0, dp(8))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }
            setOnClickListener {
                if (wakeEdit != (t?.weight ?: 0.0)) watchFluid("weight", value = wakeEdit, redraw = false)
                if (postEdit != (t?.postWeight ?: 0.0)) watchFluid("postWeight", value = postEdit, redraw = false)
                flash("Weights saved"); showFluids()
            }
        })
        col.addView(label("Back", C.MUTED, 13f, true).apply {
            setPadding(0, dp(12), 0, dp(4)); setOnClickListener { showNutrition() }
        })
    }

    /** Shows the change at once and sends it to the phone (which saves it and passes it to the Command Center). */
    private fun watchFluid(f: String, delta: Int = 0, value: Double = 0.0, redraw: Boolean = true) {
        val H = io.github.michealsizemore1.beyondstride.Hydration
        val S = io.github.michealsizemore1.beyondstride.NutritionStore
        val date = nutDay()
        val days = S.days(this)
        val cur = days.lastOrNull { it.date == date } ?: H.blank(date)
        var d = delta
        val upd = when (f) {
            "water" -> { val n = (cur.water + delta).coerceAtLeast(0); d = n - cur.water; cur.copy(water = n) }
            "sports" -> { val n = (cur.sportsOz + delta).coerceAtLeast(0); d = n - cur.sportsOz; cur.copy(sportsOz = n) }
            "other" -> { val n = (cur.otherOz + delta).coerceAtLeast(0); d = n - cur.otherOz; cur.copy(otherOz = n) }
            "weight" -> cur.copy(weight = value)
            else -> cur.copy(postWeight = value)
        }.let { it.copy(waterOz = it.water + it.sportsOz + it.otherOz) }
        if (f in H.FLUIDS && d == 0) return
        S.putLocal(this, (days.filter { it.date != date } + upd).sortedBy { it.date })
        RunSync.tellPhone(this, "/cmd/fluid", H.encode(date, f, d, value).toByteArray()) { ok -> if (!ok) flash("Phone not connected — not saved") }
        if (redraw) showFluids(false)
    }

    /** Quick add (watch): a favorite or recent food into a meal; the phone logs it and tells the Command Center. */
    private var quickMeal = "snack"
    private var quickQuery = ""
    private var quickSource = "OPEN FOOD FACTS"
    private var quickOnline: List<io.github.michealsizemore1.beyondstride.Food.Item>? = null
    private fun defaultMeal() = when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
        in 4..10 -> "breakfast"; in 11..14 -> "lunch"; in 17..21 -> "dinner"; else -> "snack" }

    private fun showQuickAdd(): Unit = overlay { col ->
        val N = io.github.michealsizemore1.beyondstride.Nutrition
        val F = io.github.michealsizemore1.beyondstride.Food
        val S = io.github.michealsizemore1.beyondstride.NutritionStore
        col.addView(label("Quick add", Color.WHITE, 17f, true))
        // Meal: tap to change.
        col.addView(label("${N.MEAL_NAMES[quickMeal] ?: quickMeal}  ▾", Color.BLACK, 13f, true).apply {
            background = rounded(C.ACCENT, 18); setPadding(0, dp(6), 0, dp(6))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) }
            setOnClickListener { quickMeal = N.MEAL_ORDER[(N.MEAL_ORDER.indexOf(quickMeal) + 1) % N.MEAL_ORDER.size]; showQuickAdd() }
        })
        // Search: type or speak (the watch keyboard has a mic). Searches your foods; 🔎 Online asks Open Food Facts.
        col.addView(android.widget.EditText(this).apply {
            hint = "🔍 Search foods"; setText(quickQuery); setSingleLine(); gravity = Gravity.CENTER
            setTextColor(Color.WHITE); setHintTextColor(C.MUTED); textSize = RunViews.scaled(this@RunActivity, 12f)
            background = rounded(C.CARD, 18); setPadding(dp(10), dp(6), dp(10), dp(6))
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) }
            setOnEditorActionListener { v, _, _ -> quickQuery = v.text.toString().trim(); quickOnline = null; showQuickAdd(); true }
        })
        if (quickQuery.isNotEmpty()) col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(5) }
            addView(label("🔎 Web", Color.WHITE, 11f, true).apply {
                background = rounded(C.CARD, 14); setPadding(0, dp(5), 0, dp(5))
                setOnClickListener {
                    quickOnline = emptyList(); flash("Searching…")
                    val q = quickQuery; val m = quickMeal
                    Thread {
                        val r = io.github.michealsizemore1.beyondstride.FoodSearch.search(q, m)
                        runOnUiThread { if (overlayOpen) { quickOnline = r; quickSource = "OPEN FOOD FACTS"; if (r.isEmpty()) flash("Nothing found online"); showQuickAdd() } }
                    }.start()
                }
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(4) })
            addView(label("✨ AI", Color.WHITE, 11f, true).apply {
                background = rounded(0xFF3B3172.toInt(), 14); setPadding(0, dp(5), 0, dp(5))
                setOnClickListener {
                    // The phone runs the AI search (your key stays there) and sends the results back.
                    val id = System.currentTimeMillis().toString(); val q = quickQuery; val m = quickMeal
                    quickOnline = emptyList(); flash("Asking AI…")
                    val msg = org.json.JSONObject().put("id", id).put("q", q).put("meal", m).toString()
                    RunSync.tellPhone(this@RunActivity, "/cmd/aifood", msg.toByteArray()) { ok ->
                        if (!ok) { flash("Phone not connected"); return@tellPhone }
                        Thread {
                            val sp = getSharedPreferences("aifood", 0)
                            var waited = 0
                            while (waited < 45_000 && sp.getString("id", "") != id) { Thread.sleep(500); waited += 500 }
                            val got = sp.getString("id", "") == id
                            val res = if (got) io.github.michealsizemore1.beyondstride.Food.parseList(org.json.JSONArray(sp.getString("json", "[]"))) else emptyList()
                            val err = if (got) sp.getString("err", "") ?: "" else "No answer from the phone"
                            runOnUiThread {
                                if (!overlayOpen) return@runOnUiThread
                                if (err.isNotEmpty()) flash(err) else if (res.isEmpty()) flash("AI found nothing")
                                quickOnline = res; quickSource = "AI ESTIMATE"; showQuickAdd()
                            }
                        }.start()
                    }
                }
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(4) })
            addView(label("✕", C.MUTED, 11f, true).apply {
                background = rounded(C.CARD, 14); setPadding(0, dp(5), 0, dp(5))
                setOnClickListener { quickQuery = ""; quickOnline = null; showQuickAdd() }
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        })
        val seen = HashSet<String>()
        val recent = S.days(this).sortedByDescending { it.date }.flatMap { it.foods.reversed() }
        fun hit(n: String) = quickQuery.split(" ").filter { it.isNotBlank() }.all { n.contains(it, ignoreCase = true) }
        val foods = quickOnline?.takeIf { it.isNotEmpty() }
            ?: if (quickQuery.isEmpty()) (S.quick(this) + recent).filter { it.name.isNotBlank() && seen.add(it.name.lowercase().trim()) }.take(40)
            else (S.quick(this) + recent + S.library(this)).filter { it.name.isNotBlank() && hit(it.name) && seen.add(it.name.lowercase().trim()) }.take(30)
        if (quickOnline?.isNotEmpty() == true) col.addView(label(quickSource, C.ACCENT, 9f, true).apply { setPadding(0, dp(6), 0, 0) })
        if (foods.isEmpty()) col.addView(label(if (quickQuery.isEmpty()) "Log a few foods on the phone first — favorites and recent foods show here."
            else "No match in your foods — tap 🔎 Online.", C.MUTED, 10f).apply { setPadding(0, dp(6), 0, 0) })
        foods.forEach { f ->
            col.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; background = rounded(C.CARD, 14); setPadding(dp(10), dp(6), dp(10), dp(6))
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(5) }
                addView(label(f.name, Color.WHITE, 11f, true))
                addView(label("${F.fmt(f.servings)} × ${f.servingLabel} · ${f.kcal} kcal", C.MUTED, 9f))
                setOnClickListener {
                    val item = f.copy(id = F.newId(), meal = quickMeal)
                    val date = nutDay()
                    val days = S.days(this@RunActivity)
                    val cur = days.lastOrNull { it.date == date } ?: io.github.michealsizemore1.beyondstride.Hydration.blank(date)
                    S.putLocal(this@RunActivity, (days.filter { it.date != date } + F.summarize(cur, cur.foods + item)).sortedBy { it.date })
                    val msg = org.json.JSONObject().put("date", date).put("op", "foodAdd").put("item", F.toJson(item)).toString()
                    RunSync.tellPhone(this@RunActivity, "/cmd/food", msg.toByteArray()) { ok -> flash(if (ok) "Added ${f.name}" else "Phone not connected — not saved") }
                }
            })
        }
        col.addView(label("Back", C.MUTED, 13f, true).apply { setPadding(0, dp(12), 0, dp(4)); setOnClickListener { showNutrition() } })
    }

    /** One week chart filling the watch screen, with each day's number. Back returns to Nutrition. */
    private fun showChartFull(title: String, vals: List<Double?>, unit: String): Unit = overlay { col ->
        val W = io.github.michealsizemore1.beyondstride.WeekChart
        col.addView(label(title, C.ACCENT, 13f, true))
        col.addView(io.github.michealsizemore1.beyondstride.WeekChart(this, vals, small = false, showValues = true, label = title.substringBefore(" ("), unit = unit), LinearLayout.LayoutParams(MATCH_PARENT, (resources.displayMetrics.heightPixels * 0.5).toInt()))
        col.addView(label(W.summary(vals, unit), Color.WHITE, 10f).apply { setPadding(0, dp(4), 0, 0) })
        col.addView(label("Back", C.MUTED, 13f, true).apply { setPadding(0, dp(10), 0, dp(4)); setOnClickListener { showNutrition() } })
    }

    /** Adds foods to today (shown at once) and sends them to the phone, which logs them and tells the Command Center. */
    private fun watchAddFoods(items: List<io.github.michealsizemore1.beyondstride.Food.Item>, done: String) {
        val F = io.github.michealsizemore1.beyondstride.Food; val S = io.github.michealsizemore1.beyondstride.NutritionStore
        val date = nutDay()
        val days = S.days(this)
        val cur = days.lastOrNull { it.date == date } ?: io.github.michealsizemore1.beyondstride.Hydration.blank(date)
        S.putLocal(this, (days.filter { it.date != date } + F.summarize(cur, cur.foods + items)).sortedBy { it.date })
        var sent = 0; var failed = false
        items.forEach { item ->
            val msg = org.json.JSONObject().put("date", date).put("op", "foodAdd").put("item", F.toJson(item)).toString()
            RunSync.tellPhone(this, "/cmd/food", msg.toByteArray()) { ok ->
                sent++; if (!ok) failed = true
                if (sent == items.size) flash(if (failed) "Phone not connected — not saved" else done)
            }
        }
    }

    /** Meal ideas (20/60/20), same as the phone. */
    private var ideasFilter = "all"
    private fun showMealIdeas(): Unit = overlay { col ->
        val MI = io.github.michealsizemore1.beyondstride.MealIdeas
        val N = io.github.michealsizemore1.beyondstride.Nutrition
        col.addView(label("Meal ideas", Color.WHITE, 17f, true))
        col.addView(label("20% protein · 60% carbs · 20% fat", C.MUTED, 9f))
        col.addView(label((if (ideasFilter == "all") "All meals" else N.MEAL_NAMES[ideasFilter] ?: ideasFilter) + "  ▾", Color.BLACK, 12f, true).apply {
            background = rounded(C.ACCENT, 18); setPadding(0, dp(5), 0, dp(5))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(5) }
            setOnClickListener { val o = listOf("all", "breakfast", "lunch", "dinner", "snack"); ideasFilter = o[(o.indexOf(ideasFilter) + 1) % o.size]; showMealIdeas() }
        })
        MI.READY.filter { ideasFilter == "all" || it.meal == ideasFilter }.forEach { m ->
            col.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; background = rounded(C.CARD, 14); setPadding(dp(10), dp(6), dp(10), dp(6))
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(5) }
                addView(label(m.label, Color.WHITE, 11f, true))
                addView(label("${N.MEAL_NAMES[m.meal]} · ${Math.round(m.kcal)} kcal", C.MUTED, 9f))
                addView(label(m.parts.joinToString(" · ") { it.name }, C.DIM, 8f))
                addView(label("＋ Log", C.ACCENT, 11f, true))
                setOnClickListener { watchAddFoods(m.items(), "Logged ${m.label}"); showNutrition() }
            })
        }
        col.addView(label("Back", C.MUTED, 13f, true).apply { setPadding(0, dp(12), 0, dp(4)); setOnClickListener { showNutrition() } })
    }

    /** Copy day: yesterday's foods (everything or one meal) onto today. */
    private fun showCopyDay(): Unit = overlay { col ->
        val N = io.github.michealsizemore1.beyondstride.Nutrition
        val F = io.github.michealsizemore1.beyondstride.Food
        val days = io.github.michealsizemore1.beyondstride.NutritionStore.days(this).filter { it.foods.isNotEmpty() && it.date != io.github.michealsizemore1.beyondstride.Readiness.today() }
            .sortedByDescending { it.date }.take(5)
        col.addView(label("Copy to today", Color.WHITE, 17f, true))
        if (days.isEmpty()) col.addView(label("No earlier logged days on the watch yet.", C.MUTED, 10f))
        days.forEach { d ->
            val dd = java.time.LocalDate.parse(d.date)
            val name = if (dd == java.time.LocalDate.now().minusDays(1)) "Yesterday" else dd.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() } + " " + dd.monthValue + "/" + dd.dayOfMonth
            col.addView(label(name.uppercase(), C.ACCENT, 9f, true).apply { setPadding(0, dp(8), 0, 0) })
            (listOf("all") + N.MEAL_ORDER.filter { m -> d.foods.any { it.meal == m } }).forEach { m ->
                val items = d.foods.filter { m == "all" || it.meal == m }
                col.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; background = rounded(C.CARD, 14); setPadding(dp(10), dp(5), dp(10), dp(5))
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(4) }
                    addView(label(if (m == "all") "Everything" else N.MEAL_NAMES[m] ?: m, Color.WHITE, 11f, true))
                    addView(label("${items.size} foods · ${N.kcal(items.sumOf { it.kcal })} kcal", C.MUTED, 9f))
                    setOnClickListener { watchAddFoods(items.map { it.copy(id = F.newId()) }, "Copied ${items.size} foods"); showNutrition() }
                })
                // Single foods under each meal: tap one to copy just that food.
                if (m != "all") items.forEach { f ->
                    col.addView(label("＋ ${f.name} · ${f.kcal}", C.MUTED, 9f).apply {
                        setPadding(dp(10), dp(3), 0, dp(1))
                        setOnClickListener { watchAddFoods(listOf(f.copy(id = F.newId())), "Copied ${f.name}"); showNutrition() }
                    })
                }
            }
        }
        col.addView(label("Back", C.MUTED, 13f, true).apply { setPadding(0, dp(12), 0, dp(4)); setOnClickListener { showNutrition() } })
    }

    /** Calorie target breakdown and settings (from the Today card's Target line). */
    private fun showCalorieTarget(): Unit = overlay { col ->
        val CT = io.github.michealsizemore1.beyondstride.CalorieTarget
        val c = io.github.michealsizemore1.beyondstride.NutritionStore.calc(this)
        val t = io.github.michealsizemore1.beyondstride.Nutrition.today(io.github.michealsizemore1.beyondstride.NutritionStore.days(this))
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL

                val st = io.github.michealsizemore1.beyondstride.CalorieTarget.Settings.from(c.optJSONObject("settings"))
                addView(label("Calorie target", Color.WHITE, 16f, true))
                addView(label("${CT.kcal(c.optInt("target"))} kcal", 0xFF5FD38D.toInt(), 18f, true))
                addView(label("Range ${CT.kcal(maxOf(1200, c.optInt("target") - c.optInt("range")))}–${CT.kcal(c.optInt("target") + c.optInt("range"))}", C.MUTED, 9f))
                c.optString("preset").takeIf { it.isNotEmpty() }?.let { addView(label(it, 0xFF93C5FD.toInt(), 9f, true)) }
                addView(label("${CT.kcal(c.optInt("base"))} rest + ${CT.kcal(c.optInt("replace"))} workout" +
                    c.optInt("goalAdjust").let { g -> if (g != 0) (if (g > 0) " + " else " − ") + kotlin.math.abs(g) + " goal" else "" }, Color.WHITE, 9f))
                t?.takeIf { it.count > 0 }?.let { d -> addView(label("Logged ${CT.kcal(d.cal)} · " + if (d.cal > c.optInt("target")) "over ${CT.kcal(d.cal - c.optInt("target"))}" else "${CT.kcal(c.optInt("target") - d.cal)} left", C.MUTED, 9f)) }
                fun send(s: io.github.michealsizemore1.beyondstride.CalorieTarget.Settings) {
                    RunSync.tellPhone(this@RunActivity, "/cmd/calset", s.json().toString().toByteArray()) { ok -> flash(if (ok) "Saved — updating…" else "Phone not connected") }
                }
                addView(LinearLayout(this@RunActivity).apply {
                    orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(5), 0, 0)
                    addView(label((CT.GOALS.firstOrNull { it.first == st.goal }?.second ?: "Maintain").replace(" weight", ""), Color.WHITE, 10f, true).apply {
                        background = rounded(0xFF26302C.toInt(), 12); setPadding(0, dp(4), 0, dp(4))
                        setOnClickListener { val i = CT.GOALS.indexOfFirst { it.first == st.goal }; send(st.copy(goal = CT.GOALS[(i + 1) % CT.GOALS.size].first)) }
                    }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(4) })
                    addView(label("Replace ${st.refuel}%", Color.WHITE, 10f, true).apply {
                        background = rounded(0xFF26302C.toInt(), 12); setPadding(0, dp(4), 0, dp(4))
                        setOnClickListener { val i = CT.REFUELS.indexOf(st.refuel); send(st.copy(refuel = CT.REFUELS[(i + 1) % CT.REFUELS.size])) }
                    }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                })
        })
        col.addView(label("Back", C.MUTED, 13f, true).apply { setPadding(0, dp(12), 0, dp(4)); setOnClickListener { showNutrition() } })
    }

    /** The day on the Nutrition screen (null = today). Fluids, weights and Quick add go to it. */
    private var nutDate: String? = null
    private fun nutDay() = nutDate ?: io.github.michealsizemore1.beyondstride.Readiness.today()

    private fun showNutrition() {
        overlayOpen = true; startBuilt = false
        val N = io.github.michealsizemore1.beyondstride.Nutrition
        val days = io.github.michealsizemore1.beyondstride.NutritionStore.days(this)
        val w = resources.displayMetrics.widthPixels
        val col = column().apply { setPadding(w / 9, w / 8, w / 9, w / 5) }
        fun box(build: LinearLayout.() -> Unit) = col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8)); background = rounded(C.CARD, 16)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) }; build()
        })
        col.addView(label("Nutrition", Color.WHITE, 20f, true))
        // Day stepping, like the phone (the watch keeps the last week).
        val todayS = io.github.michealsizemore1.beyondstride.Readiness.today()
        val shown = nutDay()
        val have = (days.map { it.date } + todayS).distinct().sorted()
        val idx = have.indexOf(shown)
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(4) }
            fun arrow(tx: String, to: String?) = addView(label(tx, if (to != null) Color.BLACK else C.DIM, 16f, true).apply {
                background = rounded(if (to != null) C.ACCENT else C.CARD, 14); setPadding(0, dp(3), 0, dp(4))
                if (to != null) setOnClickListener { nutDate = if (to == todayS) null else to; showNutrition() } }, LinearLayout.LayoutParams(dp(34), WRAP_CONTENT))
            arrow("‹", have.getOrNull(idx - 1))
            addView(label(if (shown == todayS) "Today" else java.time.LocalDate.parse(shown).format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d", java.util.Locale.US)), Color.WHITE, 12f, true),
                LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            arrow("›", have.getOrNull(idx + 1))
        })
        val t = N.today(days, shown)
        box {
            addView(label(if (shown == todayS) "TODAY" else java.time.LocalDate.parse(shown).format(java.time.format.DateTimeFormatter.ofPattern("EEEE", java.util.Locale.US)).uppercase(), C.ACCENT, 11f, true))
            if (t == null || t.count == 0) addView(label(if (shown == todayS) "No food logged today yet" else "No food logged this day", C.MUTED, 11f))
            else {
                addView(label(N.kcal(t.cal) + " kcal", Color.WHITE, 20f, true))
                if (t.target > 0) addView(label(if (t.left >= 0) "${N.kcal(t.left)} left of ${N.kcal(t.target)}" else "${N.kcal(-t.left)} over ${N.kcal(t.target)}",
                    if (t.left >= 0) 0xFF5FD38D.toInt() else 0xFFF2C94C.toInt(), 12f, true))
                val (pp, cp, fp) = t.ratios()
                addView(label(N.macros(t) + "  ($pp/$cp/$fp%)", C.MUTED, 10f))
                if (t.active > 0) addView(label("Target includes ${N.kcal(t.active)} active kcal", C.DIM, 9f))
            }
            io.github.michealsizemore1.beyondstride.NutritionStore.calc(this@RunActivity).takeIf { it.has("target") }?.let { c ->
                val g = c.optInt("goalAdjust")
                addView(label("Target ${N.kcal(c.optInt("target"))}" + (if (c.optString("preset").isNotEmpty()) " · hard day" else if (c.optInt("replace") > 0) " · runs +${c.optInt("replace")}" else "") +
                    (if (g != 0) (if (g > 0) " · +" else " · −") + kotlin.math.abs(g) else "") + " ▸", C.ACCENT, 10f, true).apply {
                    setPadding(0, dp(3), 0, 0); setOnClickListener { showCalorieTarget() } })
            }
            // Fluids and post-run rehydration, part of Today. Tap to add a cup or enter weights.
            addView(label("Fluids ${t?.waterOz ?: 0} oz", Color.WHITE, 12f, true).apply { setPadding(0, dp(4), 0, 0) })
            if (t != null && t.water + t.sportsOz + t.otherOz > 0) addView(label(N.fluids(t), C.MUTED, 10f))
            N.rehydrate(t)?.let { h ->
                addView(label("Lost ${String.format(java.util.Locale.US, "%.1f", h.lostLb)} lb → ~${h.targetOz} oz", C.MUTED, 10f))
                addView(label(if (h.needOz == 0) "Rehydrated ✓" else "Drink ~${h.needOz} oz more", if (h.needOz == 0) 0xFF5FD38D.toInt() else N.WATER, 12f, true))
            }
            addView(label("💧 Fluids & weight ▸", N.WATER, 11f, true).apply {
                background = rounded(0xFF26302C.toInt(), 14); setPadding(0, dp(5), 0, dp(5))
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(5) }
            })
            setOnClickListener { showFluids() }
        }
        if (t != null && t.count > 0) box {
            addView(label("MACROS & MICRONUTRIENTS", C.ACCENT, 11f, true))
            io.github.michealsizemore1.beyondstride.Food.dayQuality(t)?.let { q -> qualityBar(this, "Food quality", q.score, q.label) }
            t.macroTiles()?.let { tiles ->
                addView(label("Macro ratio", Color.WHITE, 11f, true).apply { setPadding(0, dp(5), 0, 0) })
                tiles.forEach { m -> addView(label("${m.name} " + String.format(java.util.Locale.US, "%.0f", m.actual) + "% / ${m.goal}% · ${m.status}",
                    when (m.status) { "On target" -> 0xFF4ADE80.toInt(); "High" -> 0xFFF87171.toInt(); else -> 0xFFFBBF24.toInt() }, 10f, true)) }
            }
            if (t.micros.isNotEmpty()) qualityBar(this, "Micronutrients", t.microScore, t.microLabel())
            t.microsByNeed().forEach { m ->
                addView(label("${m.name.replace("Vitamin ", "Vit ")}  ${m.shown()}", Color.WHITE, 10f).apply { setPadding(0, dp(2), 0, 0) })
                addView(label("${m.pct.toInt()}% · ${m.status}", N.statusColor(m.status), 11f, true))
            }
        }
        col.addView(label("🍎 Quick add food ▸", Color.BLACK, 13f, true).apply {
            background = rounded(C.ACCENT, 20); setPadding(0, dp(8), 0, dp(8))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) }
            setOnClickListener { quickMeal = defaultMeal(); quickQuery = ""; quickOnline = null; showQuickAdd() }
        })
        // Same tools as the phone (the photo and AI ones need the phone's camera / key).
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(5) }
            addView(label("🥗 Ideas", Color.WHITE, 11f, true).apply { background = rounded(0xFF059669.toInt(), 14); setPadding(0, dp(6), 0, dp(6)); setOnClickListener { showMealIdeas() } },
                LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(4) })
            addView(label("📅 Copy day", Color.WHITE, 11f, true).apply { background = rounded(0xFF16A085.toInt(), 14); setPadding(0, dp(6), 0, dp(6)); setOnClickListener { showCopyDay() } },
                LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        })
        if (t != null && t.count > 0) box {
            addView(label("MEALS", C.ACCENT, 11f, true))
            (N.MEAL_ORDER + t.meals.keys.filter { it !in N.MEAL_ORDER }).filter { (t.meals[it] ?: 0) > 0 || t.items.any { i -> i.meal == it } }.forEach { m ->
                addView(label("${N.MEAL_NAMES[m] ?: m}  ${N.kcal(t.meals[m] ?: 0)} kcal", Color.WHITE, 12f, true).apply { setPadding(0, dp(4), 0, 0) })
                if (t.foods.isNotEmpty()) t.foods.filter { it.meal == m }.forEach { f -> addView(label("${f.name} · ${io.github.michealsizemore1.beyondstride.Food.fmt(f.servings)}× · ${N.kcal(f.kcal)}", C.MUTED, 9f)) }
                else t.items.filter { it.meal == m }.forEach { i -> addView(label("${i.name} · ${N.kcal(i.cal)}", C.MUTED, 9f)) }
            }
        }
        val week = days.filter { it.count > 0 }.takeLast(7)
        if (week.isNotEmpty()) box {
            addView(label("LAST 7 DAYS", C.ACCENT, 11f, true))
            val maxCal = maxOf(week.maxOf { maxOf(it.cal, it.target) }, 1)
            addView(bars(week.map { d ->
                val ok = d.target <= 0 || d.cal.toDouble() / d.target in 0.85..1.15
                Triple("${(d.cal + 50) / 100 / 10.0}k", java.time.LocalDate.parse(d.date).dayOfWeek.name.take(1),
                    (6 + d.cal * 30 / maxCal) to (if (ok) 0xFF5FD38D.toInt() else 0xFFF2C94C.toInt()))
            }))
            val withT = week.filter { it.target > 0 }
            addView(label("Avg ${N.kcal(week.map { it.cal }.average().toInt())} kcal" + (if (withT.isNotEmpty()) " · target ${N.kcal(withT.map { it.target }.average().toInt())}" else ""), Color.WHITE, 10f))
            addView(label("P ${week.map { it.prot }.average().toInt()} · C ${week.map { it.carbs }.average().toInt()} · F ${week.map { it.fat }.average().toInt()} g a day", C.MUTED, 9f))
        }
        // THIS WEEK: the same three charts as the phone (Mon–Sun).
        run {
            val W = io.github.michealsizemore1.beyondstride.WeekChart
            val byDate = days.associateBy { it.date }
            val dates = W.weekDates()
            fun chart(title: String, color: Int, unit: String, v: (io.github.michealsizemore1.beyondstride.Nutrition.Day) -> Double) = box {
                val vals = dates.map { dt -> byDate[dt]?.let(v)?.takeIf { it > 0 } }
                addView(label(title, C.ACCENT, 10f, true))
                addView(io.github.michealsizemore1.beyondstride.WeekChart(this@RunActivity, vals, color, small = true), LinearLayout.LayoutParams(MATCH_PARENT, dp(90)))
                addView(label(W.summary(vals, unit).replace(" weekly average", " avg"), C.MUTED, 8f))
                addView(label("Tap to enlarge", C.DIM, 7f))
                setOnClickListener { showChartFull(title, vals, unit) }
            }
            col.addView(label("THIS WEEK", C.ACCENT, 11f, true).apply { setPadding(0, dp(8), 0, 0) })
            chart("Calories", 0xFFF39C12.toInt(), "") { if (it.count > 0) it.cal.toDouble() else 0.0 }
            chart("Water", 0xFF3498DB.toInt(), " oz") { (if (it.water + it.sportsOz + it.otherOz > 0) it.water else it.waterOz).toDouble() }
            chart("Total liquids (excl. caffeine)", 0xFF16A085.toInt(), " oz") { it.waterOz.toDouble() }
        }
        col.addView(label("Food logged on the phone or here shows in the Command Center too.", C.DIM, 9f).apply { setPadding(0, dp(8), 0, 0) })
        col.addView(View(this).apply { setBackgroundColor(0x22FFFFFF) }, LinearLayout.LayoutParams(MATCH_PARENT, dp(1)).apply { topMargin = dp(10) })
        col.addView(menuRow(2))
        root.removeAllViews()
        root.addView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(col); requestFocus() })
    }

    /** Activities (like the phone's tab, compact): today's plan workout, mileage, streak + load, records, recent activities. */
    private var actPlanOpen = false
    private fun showActivities() {
        overlayOpen = true; startBuilt = false
        val w = resources.displayMetrics.widthPixels
        val col = column().apply { setPadding(w / 9, w / 8, w / 9, w / 5) }
        fun box(build: LinearLayout.() -> Unit) = col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8)); background = rounded(C.CARD, 16)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) }; build()
        })
        val ORANGE = 0xFFE67E22.toInt()
        col.addView(label("Activities", Color.WHITE, 20f, true))
        val a = io.github.michealsizemore1.beyondstride.NutritionStore.acts(this)
        if (a == null) col.addView(label("Open Activities on the phone once to send them here.", C.MUTED, 10f))
        else {
            col.addView(label("${a.optInt("count")} activities", C.MUTED, 10f))
            // Training plan — today (tap to open / close).
            a.optJSONObject("plan")?.let { p -> box {
                background = rounded(0xFF2A2F5A.toInt(), 16)
                addView(label((if (actPlanOpen) "▼ " else "▶ ") + "🏃 Plan · Week ${p.optInt("week")}, Day ${p.optInt("day")}", Color.WHITE, 11f, true))
                p.optString("race").takeIf { it.isNotEmpty() }?.let { r -> try { val dd = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), java.time.LocalDate.parse(r)).toInt()
                    if (dd >= 0) addView(label(if (dd == 0) "🏁 Race day!" else "🏁 $dd days to race day", 0xCCFFFFFF.toInt(), 9f)) } catch (e: Exception) { } }
                if (actPlanOpen) addView(label(p.optString("text"), Color.WHITE, 10f).apply { gravity = Gravity.START; setPadding(0, dp(4), 0, 0) })
                setOnClickListener { actPlanOpen = !actPlanOpen; showActivities() }
            } }
            // Mileage.
            col.addView(label("MILEAGE", C.ACCENT, 11f, true).apply { setPadding(0, dp(8), 0, 0) })
            val ms = a.optJSONArray("mileage")
            if (ms != null) for (i in 0 until ms.length()) { val m = ms.getJSONObject(i); box {
                addView(LinearLayout(this@RunActivity).apply { orientation = LinearLayout.HORIZONTAL
                    addView(label(m.optString("t"), ORANGE, 11f, true).apply { gravity = Gravity.START }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                    addView(label(String.format(java.util.Locale.US, "%.1f mi", m.optDouble("mi")), Color.WHITE, 13f, true)) })
                val min = m.optInt("min")
                addView(label(listOfNotNull(m.optString("pace").ifEmpty { null }?.let { "$it/mi" }, m.optInt("w").takeIf { it > 0 }?.let { "$it W" },
                    if (min > 0) (if (min < 60) "${min}m" else "${min / 60}h ${min % 60}m") else null).joinToString(" · ").ifEmpty { "—" }, C.MUTED, 9f))
                val g = m.optDouble("goal")
                if (g > 0) { val pct = Math.min(100, Math.round(m.optDouble("mi") / g * 100).toInt())
                    addView(android.widget.ProgressBar(this@RunActivity, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = pct
                        progressTintList = android.content.res.ColorStateList.valueOf(if (pct >= 100) 0xFF27AE60.toInt() else ORANGE) }, LinearLayout.LayoutParams(MATCH_PARENT, dp(6)).apply { topMargin = dp(3) })
                    addView(label(String.format(java.util.Locale.US, "%.1f / %s mi (%d%%)", m.optDouble("mi"), if (g == Math.floor(g)) g.toLong().toString() else g.toString(), pct), C.MUTED, 8f)) }
            } }
            // Streak and Training Load Ratio (miles).
            box {
                if (a.optInt("streak") > 0) addView(label("🔥 ${a.optInt("streak")}-week streak", ORANGE, 12f, true))
                if (a.has("acwr")) { val r = a.optDouble("acwr")
                    val (c, l) = when { r < 0.8 -> 0xFF5DADE2.toInt() to "Low load (easing back)"; r <= 1.3 -> 0xFF27AE60.toInt() to "Sweet spot"; r <= 1.5 -> 0xFFF39C12.toInt() to "Elevated — watch for soreness"; else -> 0xFFE74C3C.toInt() to "High — too much too soon" }
                    addView(label("⚡ Training Load Ratio (miles)", C.MUTED, 9f, true).apply { setPadding(0, dp(3), 0, 0) })
                    addView(label(String.format(java.util.Locale.US, "%.2f · %s", r, l), c, 12f, true))
                    addView(label(String.format(java.util.Locale.US, "%.1f mi/day (7d) vs %.1f (28d)", a.optDouble("acute"), a.optDouble("chronic")), C.DIM, 8f)) }
            }
            // Personal Records.
            a.optJSONArray("prs")?.takeIf { it.length() > 0 }?.let { prs -> box {
                addView(label("🏆 PERSONAL RECORDS", C.ACCENT, 10f, true))
                for (i in 0 until prs.length()) { val r = prs.getJSONObject(i)
                    addView(LinearLayout(this@RunActivity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(2), 0, 0)
                        addView(label(r.optString("l"), C.MUTED, 9f).apply { gravity = Gravity.START }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                        addView(label(r.optString("v"), ORANGE, 10f, true)) }) }
            } }
            // Recent activities.
            col.addView(label("RECENT", C.ACCENT, 11f, true).apply { setPadding(0, dp(8), 0, 0) })
            a.optJSONArray("recent")?.let { rs -> for (i in 0 until rs.length()) { val r = rs.getJSONObject(i); box {
                addView(LinearLayout(this@RunActivity).apply { orientation = LinearLayout.HORIZONTAL
                    addView(label(r.optString("title"), Color.WHITE, 11f, true).apply { gravity = Gravity.START; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                    addView(label(r.optString("src"), C.DIM, 8f)) })
                addView(label(try { java.time.LocalDate.parse(r.optString("date").take(10)).format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d", java.util.Locale.US)) } catch (e: Exception) { r.optString("date") }, C.MUTED, 9f).apply { gravity = Gravity.START })
                addView(label(listOfNotNull(r.optDouble("mi").takeIf { it > 0 }?.let { String.format(java.util.Locale.US, "%.2f mi", it) }, r.optString("dur").ifEmpty { null },
                    r.optString("pace").ifEmpty { null }?.let { "$it/mi" }, r.optDouble("hr").takeIf { it > 0 }?.let { "${it.toInt()} bpm" }, r.optDouble("w").takeIf { it > 0 }?.let { "${it.toInt()} W" }).joinToString(" · "), Color.WHITE, 9f).apply { gravity = Gravity.START })
            } } }
        }
        col.addView(label("Runs on this watch ▸", C.ACCENT, 12f, true).apply { setPadding(0, dp(10), 0, 0)
            setOnClickListener { startActivity(Intent(this@RunActivity, RunHistoryActivity::class.java)) } })
        col.addView(View(this).apply { setBackgroundColor(0x22FFFFFF) }, LinearLayout.LayoutParams(MATCH_PARENT, dp(1)).apply { topMargin = dp(10) })
        col.addView(menuRow(4))
        root.removeAllViews()
        root.addView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(col); requestFocus() })
    }

    /** Same tabs as the phone. [selected]: 0 Today, 1 Fitness, 2 Nutrition, 3 Plan, 4 Runs. */
    private fun menuRow(selected: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }
        listOf<Triple<String, String, () -> Unit>>(
            Triple("☀", "Today", { overlayOpen = false; startBuilt = false; renderStart() }),
            Triple("♥", "Fitness", { showFitness() }),
            Triple("🍎", "Nutrition", { nutDate = null; showNutrition() }),
            Triple("▦", "Plan", { showWorkoutPicker() }),
            Triple("≡", "Activities", { showActivities() }),
        ).forEachIndexed { idx, (glyph, name, go) ->
            val on = idx == selected
            addView(column().apply {
                gravity = Gravity.CENTER
                addView(label(glyph, if (on) Color.BLACK else Color.WHITE, 17f, true).apply {
                    background = rounded(if (on) C.ACCENT else C.CARD, 18); setPadding(0, dp(5), 0, dp(5)) },
                    LinearLayout.LayoutParams(dp(34), WRAP_CONTENT))
                addView(label(name, if (on) Color.WHITE else 0xFFC9D1CE.toInt(), 9f, true).apply { setPadding(0, dp(2), 0, 0) })
                if (!on) setOnClickListener { go() }
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
    }

    /** Fitness page (like the phone's Fitness tab): readiness, then Zone 2, decoupling, CP, load and personal bests. */
    /** Small bar chart (watch): one bar per item with a value on top and a label under it. */
    private fun bars(items: List<Triple<String, String, Pair<Int, Int>>>) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM
        setPadding(0, dp(6), 0, dp(2))
        items.forEach { (top, bottom, hc) ->
            addView(column().apply {
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                if (top.isNotEmpty()) addView(label(top, C.MUTED, 7f))
                addView(View(this@RunActivity).apply { background = rounded(hc.second, 2) }, LinearLayout.LayoutParams(dp(7), dp(hc.first)))
                if (bottom.isNotEmpty()) addView(label(bottom, C.DIM, 7f))
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
    }

    /** Fitness — same sections, in the same order, as the phone's Fitness tab. */
    private fun showFitness() {
        overlayOpen = true; startBuilt = false
        val w = resources.displayMetrics.widthPixels
        val col = column().apply { setPadding(w / 9, w / 8, w / 9, w / 5) }
        val I = io.github.michealsizemore1.beyondstride.Insights
        val RM = io.github.michealsizemore1.beyondstride.RunMath
        val D = io.github.michealsizemore1.beyondstride.Decoupling
        val R = io.github.michealsizemore1.beyondstride.Readiness
        fun card() = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(C.CARD, 16)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) }
        }
        fun head(t: String) = label(t, C.ACCENT, 11f, true).apply { letterSpacing = 0.08f; setPadding(0, dp(8), 0, 0) }
        col.addView(label("Fitness", Color.WHITE, 20f, true))
        val body = column(); col.addView(body)
        val wait = label("Loading…", C.DIM, 10f); body.addView(wait)
        val md = java.text.SimpleDateFormat("M/d", java.util.Locale.US); val mmmd = java.text.SimpleDateFormat("MMM d", java.util.Locale.US)
        Thread {
            val now = System.currentTimeMillis()
            val all = RunStore.list(this@RunActivity).take(200).mapNotNull { RunStore.read(it) }
            val (z2now, z2before) = I.z2Trend(all, Run.hrMax, Run.hrRest, now)
            val dec = all.take(120).filter { now - it.optLong("start") < 28 * 86_400_000L }
                .mapNotNull { r -> D.of(r)?.takeIf { it.steady && it.minutes >= 30 && it.main != null }?.main }
            val cpE = try { io.github.michealsizemore1.beyondstride.CriticalPower.estimate(all, now, currentCp = Run.criticalPower) } catch (e: Exception) { null }
            val rec = I.records(all)
            // Personal bests saved so a new best is announced right away.
            prefs.edit().putString("pbRecords", org.json.JSONObject().also { o -> rec.forEach { (k, v) -> o.put(k, org.json.JSONObject().put("sec", v.sec).put("start", v.start)) } }.toString()).apply()
            val ld = if (all.isNotEmpty()) I.load(all, now) else null
            val z2 = all.mapNotNull { r -> I.zone2(r, Run.hrMax, Run.hrRest)?.let { r.optLong("start") to it } }.take(8)
            val decPts = all.take(120).mapNotNull { r -> D.of(r)?.takeIf { it.steady && it.minutes >= 30 && it.main != null }?.let { r.optLong("start") to it.main!! } }.take(8)
            val nights = io.github.michealsizemore1.beyondstride.ReadinessSync.nights(this@RunActivity)
                .filter { it.sleepHr > 0 && R.daysBetween(it.day, R.today()) in 0..13 }.sortedBy { it.day }
            runOnUiThread {
                body.removeAllViews()
                // OVERVIEW: the phone's four tiles.
                body.addView(head("OVERVIEW"))
                fun tile(t: String, v: String, sub: String, color: Int) = LinearLayout(this@RunActivity).apply {
                    orientation = LinearLayout.VERTICAL; background = rounded(C.CARD, 14); setPadding(dp(6), dp(6), dp(6), dp(6))
                    addView(label(t, C.MUTED, 8f, true)); addView(label(v, color, 13f, true)); addView(label(sub, C.MUTED, 8f))
                }
                fun row(a: View, b: View) = body.addView(LinearLayout(this@RunActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(5) }
                    addView(a, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply { marginEnd = dp(4) }); addView(b, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f))
                })
                val latestPb = rec.maxByOrNull { it.value.start }
                row(tile("ZONE 2 PACE", z2now?.let { RM.paceFor(it, RM.METERS_PER_MILE) + "/mi" } ?: "—",
                        z2now?.let { n -> z2before?.let { b -> val d = b - n; if (d >= 0) "${d}s faster" else "${-d}s slower" } ?: "last 4 weeks" } ?: "no Z2 runs yet", Color.WHITE),
                    tile("DECOUPLING", if (dec.isEmpty()) "—" else D.text(dec.average()), if (dec.isEmpty()) "needs 30+ min steady" else D.rating(dec.average()),
                        if (dec.isEmpty()) Color.WHITE else D.color(dec.average())))
                row(tile("CRITICAL POWER", "${Run.criticalPower} W", cpE?.cp?.let { if (kotlin.math.abs(it - Run.criticalPower) >= 3) "runs say $it W" else "matches runs" } ?: "needs a hard effort", Color.WHITE),
                    tile("FASTEST EFFORTS", latestPb?.let { I.clock(it.value.sec) } ?: "—",
                        latestPb?.let { I.pbName(it.key).replace("Half marathon", "Half") + " · " + mmmd.format(java.util.Date(it.value.start)) } ?: "after first runs",
                        if (latestPb != null) 0xFFF2C94C.toInt() else Color.WHITE))

                // RESTING HEART RATE: sleeping HR by night.
                body.addView(head("RESTING HEART RATE"))
                body.addView(card().apply {
                    if (nights.isEmpty()) addView(label("From sleeping heart rate in Samsung Health (phone: Readiness → Connect sleep).", C.MUTED, 10f))
                    else {
                        val tr = R.rhrTrend(nights)
                        addView(label(String.format(java.util.Locale.US, "%.1f bpm", tr?.recent ?: nights.last().sleepHr.toDouble()), Color.WHITE, 15f, true))
                        addView(label("7-night average" + (tr?.change?.let { String.format(java.util.Locale.US, " · %s%.1f", if (it > 0) "+" else "", it) } ?: ""), C.MUTED, 9f))
                        val shown = nights.takeLast(7)
                        val lo = shown.minOf { it.sleepHr } - 3; val hi = maxOf(shown.maxOf { it.sleepHr }, lo + 6)
                        addView(bars(shown.mapIndexed { i, n -> Triple("${n.sleepHr}", if (i == 0 || i == shown.lastIndex) n.day.substring(5).replace("-", "/") else "",
                            (6 + (n.sleepHr - lo) * 28 / (hi - lo)) to 0xFFFF6B6B.toInt()) }))
                        tr?.let { addView(label(it.note, it.color, 9f)) }
                    }
                })

                if (ld != null) {
                    // TRAINING LOAD.
                    body.addView(head("TRAINING LOAD (STRESS)"))
                    body.addView(card().apply {
                        addView(label(ld.status, ld.color, 14f, true))
                        addView(label("${ld.acute} stress · " + String.format(java.util.Locale.US, "%.1f", ld.milesWeek) + " mi, last 7 days", Color.WHITE, 10f))
                        ld.ratio?.let { addView(label(String.format(java.util.Locale.US, "Ratio %.2f (0.8–1.3 healthy) · usual %.1f mi", it, ld.milesAvg), ld.color, 9f)) }
                        addView(label(ld.advice, C.MUTED, 9f).apply { setPadding(0, dp(2), 0, 0) })
                    })
                }

                // ZONE 2 FITNESS: pace at Zone 2 heart rate, run by run.
                if (z2.isNotEmpty()) {
                    body.addView(head("ZONE 2 FITNESS"))
                    body.addView(card().apply {
                        z2now?.let { n -> addView(label("Zone 2 pace " + RM.paceFor(n, RM.METERS_PER_MILE) + " /mi", Color.WHITE, 12f, true)) }
                        addView(label(z2now?.let { n -> z2before?.let { b -> val d = b - n; if (d >= 0) "$d s/mi faster than the 4 weeks before" else "${-d} s/mi slower than the 4 weeks before" } }
                            ?: "Faster at the same heart rate = fitter.", C.MUTED, 9f))
                        val paces = z2.map { it.second.secPerMile }; val slow = paces.max() + 15; val fast = paces.min() - 15
                        addView(bars(z2.reversed().map { (st, z) -> Triple(RM.paceFor(z.secPerMile, RM.METERS_PER_MILE), md.format(java.util.Date(st)),
                            (6 + (slow - z.secPerMile) * 28 / maxOf(1, slow - fast)) to 0xFF22C55E.toInt()) }))
                    })
                }

                // PERSONAL BESTS.
                if (rec.isNotEmpty()) {
                    body.addView(head("FASTEST EFFORTS"))
                    body.addView(card().apply {
                        rec.forEach { (k, pb) ->
                            addView(LinearLayout(this@RunActivity).apply {
                                orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(2), 0, dp(2))
                                addView(label(I.pbName(k).replace("Half marathon", "Half"), Color.WHITE, 11f, true).apply { gravity = Gravity.START }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                                addView(label(I.clock(pb.sec) + "  " + mmmd.format(java.util.Date(pb.start)), 0xFFF2C94C.toInt(), 11f, true).apply { gravity = Gravity.END },
                                    LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
                            })
                        }
                    })
                }

                // AEROBIC DECOUPLING · STEADY RUNS.
                if (decPts.size >= 2) {
                    body.addView(head("DECOUPLING"))
                    body.addView(card().apply {
                        val recent = decPts.filter { now - it.first < 28 * 86_400_000L }.map { it.second }
                        if (recent.isNotEmpty()) addView(label(D.text(recent.average()) + " average, 4 weeks", D.color(recent.average()), 12f, true))
                        addView(label("Steady runs of 30+ min", C.MUTED, 9f))
                        addView(bars(decPts.reversed().map { (st, v) -> Triple(String.format(java.util.Locale.US, "%.0f", v), md.format(java.util.Date(st)),
                            (4 + v.coerceIn(0.0, 15.0) * 2).toInt() to D.color(v)) }))
                    })
                }
                if (all.isEmpty()) body.addView(label("Finish a run and your fitness shows here.", C.MUTED, 11f))
            }
        }.start()

        col.addView(View(this).apply { setBackgroundColor(0x22FFFFFF) }, LinearLayout.LayoutParams(MATCH_PARENT, dp(1)).apply { topMargin = dp(10) })
        col.addView(menuRow(1))
        root.removeAllViews()
        root.addView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(col); requestFocus() })
    }

    private fun showWorkoutPicker() {
        overlayOpen = true
        startBuilt = false
        val w = resources.displayMetrics.widthPixels
        val col = column().apply { setPadding(w / 8, w / 7, w / 8, w / 4) }
        col.addView(label("Plan", Color.WHITE, 18f, true))
        // Trails & courses (same as the phone's Plan tab).
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = rounded(C.CARD, 14)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(5) }
            addView(label("⛰ Trails & courses", Color.WHITE, 14f, true))
            addView(label(Run.nav?.trail?.name?.let { "Chosen: $it" } ?: "Find trails near you", if (Run.nav != null) 0xFF46C46E.toInt() else C.MUTED, 10f))
            setOnClickListener { showTrails() }
        })
        col.addView(label("WORKOUTS", C.ACCENT, 11f, true).apply { setPadding(0, dp(8), 0, 0) })
        fun item(title: String, sub: String, workout: org.json.JSONObject?) = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = rounded(C.CARD, 14)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(5) }
            addView(label(title, Color.WHITE, 14f, true))
            if (sub.isNotBlank()) addView(label(sub, C.MUTED, 10f))
            setOnClickListener {
                if (workout == null) { Run.chooseWorkout(null); overlayOpen = false; startBuilt = false; renderStart() }
                else showWorkoutDetail(workout, title.substringBefore(":").takeIf { title.contains(":") } ?: "")
            }
        }
        fun outline(x: org.json.JSONObject) =
            io.github.michealsizemore1.beyondstride.Workouts.outline(x, Run.hrMax, Run.hrRest, Run.criticalPower).take(3).joinToString(" · ")
        col.addView(item("Free run", "No targets", null))
        // The whole plan from today on, by week (like the phone's Plan tab).
        val dayName = java.text.SimpleDateFormat("EEE M/d", java.util.Locale.getDefault())
        val weekName = java.text.SimpleDateFormat("'WEEK OF' MMM d", java.util.Locale.getDefault())
        val keyFmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        var lastWeek = ""
        WatchWorkouts.upcoming(this, 400).forEach { (d, wk) ->
            val date = keyFmt.parse(d) ?: return@forEach
            val wkStart = java.util.Calendar.getInstance().apply { time = date; set(java.util.Calendar.DAY_OF_WEEK, firstDayOfWeek) }.time
            val header = weekName.format(wkStart).uppercase()
            if (header != lastWeek) { lastWeek = header; col.addView(label(header, C.MUTED, 10f, true).apply { setPadding(0, dp(8), 0, 0) }) }
            val label = if (d == keyFmt.format(java.util.Date())) "Today" else dayName.format(date)
            col.addView(item("$label: " + wk.optString("name"), outline(wk), wk))
        }
        if (WatchWorkouts.library(this).isNotEmpty()) col.addView(label("LIBRARY", C.MUTED, 10f, true).apply { setPadding(0, dp(10), 0, 0) })
        WatchWorkouts.library(this).forEach { col.addView(item(it.optString("name"), outline(it), it)) }
        if (WatchWorkouts.library(this).isEmpty())
            col.addView(label("Build workouts in BeyondStride on the phone — they show up here.", C.DIM, 11f).apply { setPadding(0, dp(10), 0, 0) })
        col.addView(View(this).apply { setBackgroundColor(0x22FFFFFF) }, LinearLayout.LayoutParams(MATCH_PARENT, dp(1)).apply { topMargin = dp(10) })
        col.addView(menuRow(3))
        root.removeAllViews()
        root.addView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(col); requestFocus() })
    }

    @Deprecated("Deprecated in Java")
    /**
     * The bottom (back) button. With swipe-to-close turned off for Running, this is only ever the button:
     * during a run = lap / double-press finish, elsewhere = go back (it never starts a run).
     */
    private fun handleBack() {
        if (wrongDevice) { finish(); return }
        when {
            screen == Screen.LIVE -> if (prefs.getBoolean("bottomBtn", true)) bottomInRun()
            editorRotary != null || overlayOpen -> { editorRotary = null; overlayOpen = false; startBuilt = false; renderStart() }
            screen == Screen.START && showSettings -> { showSettings = false; startBuilt = false; renderStart() }
            screen == Screen.SUMMARY -> backToHome()
            else -> finish()
        }
    }

    /** Back gesture (swipe): navigation only — never starts or ends a run, never leaves mid-run. */
    override fun onBackPressed() {
        if (screen == Screen.LIVE && !wrongDevice) { moveTaskToBack(true); return }   // run keeps recording
        if (editorRotary != null || overlayOpen) { editorRotary = null; overlayOpen = false; startBuilt = false; renderStart(); return }
        if (screen == Screen.START && showSettings) { showSettings = false; startBuilt = false; renderStart(); return }
        if (screen == Screen.SUMMARY) { backToHome(); return }
        @Suppress("DEPRECATION") super.onBackPressed()
    }

    // ---------------------------------------------------------------- bottom button

    /** Last few button events, shown in Settings. */
    private fun logKey(line: String) {
        val t = java.text.SimpleDateFormat("h:mm:ss", java.util.Locale.US).format(java.util.Date())
        val old = prefs.getString("buttonLog", "").orEmpty().lines().filter { it.isNotBlank() }.take(4)
        prefs.edit().putString("buttonLog", (listOf("$t $line") + old).joinToString("\n")).apply()
    }

    /**
     * Bottom button: during a run, one press = lap (next step in a
     * workout), a quick double press = finish and save. Elsewhere (settings, lists) it's a normal back.
     */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (wrongDevice) return super.dispatchKeyEvent(event)
        if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0)
            logKey("${android.view.KeyEvent.keyCodeToString(event.keyCode)} · focus=${hasWindowFocus()} · screen=$screen")
        if (event.keyCode != android.view.KeyEvent.KEYCODE_BACK || !prefs.getBoolean("bottomBtn", true)) return super.dispatchKeyEvent(event)
        // Only during a run. Runs start from the START button only — never from the bottom button.
        if (screen != Screen.LIVE) return super.dispatchKeyEvent(event)
        if (event.action == android.view.KeyEvent.ACTION_UP && !event.isCanceled) bottomInRun()
        return true   // consume down and up so the system doesn't also go back
    }

    private var pendingLap: Runnable? = null

    private fun bottomInRun() {
        val waiting = pendingLap
        if (waiting != null) {   // second press within the window: finish
            handler.removeCallbacks(waiting); pendingLap = null
            if (Run.state != Run.State.RUNNING) Run.resume()
            pulse(longArrayOf(0, 200, 100, 200, 100, 700))
            finishRun()
            return
        }
        if (confirmingFinish) { pulse(longArrayOf(0, 200, 100, 200, 100, 700)); finishRun(); return }
        val lap = Runnable { pendingLap = null; lapNow() }
        pendingLap = lap
        handler.postDelayed(lap, 450)
    }

    // ---------------------------------------------------------------- live pages

    private var liveBuilt = false
    private lateinit var liveTitle: TextView
    private lateinit var fieldA: Pair<TextView, TextView>
    private lateinit var fieldB: Pair<TextView, TextView>
    private lateinit var fieldC: Pair<TextView, TextView>
    private lateinit var fieldD: Pair<TextView, TextView>
    private lateinit var dataBox: LinearLayout
    private lateinit var zoneBar: LinearLayout
    private lateinit var controlsBox: LinearLayout
    private lateinit var dots: TextView
    private lateinit var gestures: GestureDetector

    private fun field(valueSize: Float): Pair<LinearLayout, Pair<TextView, TextView>> {
        val v = label("--", Color.WHITE, valueSize, true)
        val l = label("", C.MUTED, 11f)
        val box = column().apply { addView(v); addView(l) }
        return box to (v to l)
    }

    private fun buildLive() {
        liveBuilt = true
        startBuilt = false
        val w = resources.displayMetrics.widthPixels
        root.removeAllViews()

        val page = column().apply {
            gravity = Gravity.CENTER
            setPadding(w / 10, w / 9, w / 10, w / 10)
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
        pageView = page
        customBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
        builtScreen = -1
        liveTitle = label("", C.ACCENT, 12f, true).apply { letterSpacing = 0.1f }
        page.addView(liveTitle)

        dataBox = column()
        val (a, fa) = field(40f); fieldA = fa
        val (b, fb) = field(26f); fieldB = fb
        val (c, fc) = field(26f); fieldC = fc
        val (d, fd) = field(24f); fieldD = fd
        dataBox.addView(a)
        dataBox.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(b, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(c, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(4) }
        })
        dataBox.addView(d.apply { setPadding(0, dp(4), 0, 0) })
        zoneBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            Run.ZONE_COLORS.forEach { color ->
                addView(View(context).apply { background = rounded(color, 3) },
                    LinearLayout.LayoutParams(0, dp(6), 1f).apply { marginStart = dp(2); marginEnd = dp(2) })
            }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) }
        }
        dataBox.addView(zoneBar)
        page.addView(dataBox)

        controlsBox = column().apply { visibility = View.GONE }
        page.addView(controlsBox)

        dots = label("", C.DIM, 12f).apply { setPadding(0, dp(6), 0, 0) }
        page.addView(dots)
        root.addView(page)
        root.addView(customBox)
        trailView = TrailMapView(this).apply { visibility = View.GONE; layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT) }
        root.addView(trailView)

        gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (abs(vx) > abs(vy) && abs(vx) > 400) { turnPage(if (vx < 0) 1 else -1); return true }
                return false
            }
            // Anywhere on a data screen: double-tap = lap, press and hold = finish.
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (!onDataScreen() || true /* screen gestures retired — the bottom button does lap/finish */) return false
                lapNow(); return true
            }
            override fun onLongPress(e: MotionEvent) {
                if (!onDataScreen() || true /* screen gestures retired — the bottom button does lap/finish */) return
                openFinish()
            }
        })
        root.isFocusable = true
        root.isFocusableInTouchMode = true
        root.requestFocus()
    }

    // ---------------------------------------------------------------- watch buttons

    /** On a run data screen (not Controls or the Finish screen). */
    private fun onDataScreen(): Boolean {
        if (screen != Screen.LIVE || confirmingFinish) return false
        return pageOrder().getOrNull(page) != 0
    }

    /** Lap now (next step in a workout), with a buzz and a short note on screen. */
    private fun lapNow() {
        if (!Run.isActive) return
        pulse(longArrayOf(0, 150))
        val s = Run.currentStep
        val lastTimedStep = Run.hasWorkout && !Run.workoutDone && Run.stepIndex >= Run.workoutSteps.size - 1 &&
            s?.optString("dur") in setOf("time", "dist")
        if (Run.hasWorkout && !Run.workoutDone && !lastTimedStep) { Run.nextWorkoutStep(auto = false); flash("NEXT STEP") }
        else { Run.lap(); flash("LAP ${Run.laps.size}") }   // last step (e.g. "Easy 6 mi"): just a lap, workout keeps going
    }

    /** Pause and show the Finish screen with a big SAVE button. */
    private fun openFinish() {
        Run.pause()
        confirmingFinish = true; page = 0
        pulse(longArrayOf(0, 500)); renderLive()
    }

    private fun pulse(p: LongArray) {
        try { getSystemService(android.os.Vibrator::class.java)?.vibrate(android.os.VibrationEffect.createWaveform(p, -1)) } catch (e: Exception) { }
    }

    private fun flash(text: String) = android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show()

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (wrongDevice) return super.dispatchTouchEvent(ev)
        if (screen == Screen.LIVE && liveBuilt && gestures.onTouchEvent(ev)) return true
        return super.dispatchTouchEvent(ev)
    }

    /** Turning the bezel also flips pages. */
    override fun onGenericMotionEvent(ev: MotionEvent): Boolean {
        if (wrongDevice) return super.onGenericMotionEvent(ev)
        val ed = editorRotary
        if (ed != null && ev.action == MotionEvent.ACTION_SCROLL && ev.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            ed(if (-ev.getAxisValue(MotionEvent.AXIS_SCROLL) > 0) 1 else -1)
            return true
        }
        if (screen == Screen.LIVE && ev.action == MotionEvent.ACTION_SCROLL && ev.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            val now = System.currentTimeMillis()
            if (now - lastRotary > 350) {
                lastRotary = now
                turnPage(if (-ev.getAxisValue(MotionEvent.AXIS_SCROLL) > 0) 1 else -1)
            }
            return true
        }
        return super.onGenericMotionEvent(ev)
    }

    private fun turnPage(delta: Int) {
        page = (page + delta).coerceIn(0, pageOrder().size - 1)
        confirmingFinish = false
        renderLive()
    }

    private fun show(f: Pair<TextView, TextView>, value: String, label: String, color: Int = Color.WHITE) {
        f.first.text = value; f.first.setTextColor(color); f.second.text = label
    }

    private fun renderLive() {
        if (screen != Screen.LIVE) return   // e.g. right after SAVE: the summary is showing
        if (!liveBuilt) buildLive()
        val paused = Run.state == Run.State.PAUSED || Run.state == Run.State.AUTO_PAUSED
        root.setBackgroundColor(if (paused) 0xFF2A1F05.toInt() else Color.BLACK)
        val order = pageOrder()
        if (this.page >= order.size) this.page = order.size - 1
        dots.text = order.indices.joinToString(" ") { if (it == this.page) "●" else "○" }
        val page = order[this.page]   // which screen this position shows
        fieldD.first.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)

        val stateNow = when (Run.state) { Run.State.PAUSED -> "PAUSED"; Run.State.AUTO_PAUSED -> "AUTO-PAUSED"; else -> null }
        if (page == 8) {
            pageView.visibility = View.GONE; customBox.visibility = View.GONE
            trailView.visibility = View.VISIBLE; trailView.invalidate(); return
        }
        trailView.visibility = View.GONE
        if (page >= 100) { renderScreen(page - 100, stateNow, order); return }
        pageView.visibility = View.VISIBLE
        customBox.visibility = View.GONE
        if (page == 0) { renderControls(paused); return }
        dataBox.visibility = View.VISIBLE
        controlsBox.visibility = View.GONE

        val stateText = when (Run.state) {
            Run.State.PAUSED -> "PAUSED"
            Run.State.AUTO_PAUSED -> "AUTO-PAUSED"
            else -> null
        }
        val zi = Run.zoneIndex(Run.power3s)
        val hz = Run.hrZoneIndex(Run.heartRate)
        val hrColor = if (hz >= 0) Run.ZONE_COLORS[hz] else 0xFFFF6B6B.toInt()
        val hrZoneText = if (hz >= 0) "Z${hz + 1} · ${Run.HR_ZONE_NAMES[hz]}" else if (Run.heartRate > 0) "below Z1" else "heart rate"
        val pageName = when (page) {
            7 -> stepTitle()
            1 -> "RUN"
            2 -> if (hz >= 0) "HR Z${hz + 1} · ${Run.HR_ZONE_NAMES[hz].uppercase()}" else "HEART RATE"
            3 -> if (zi >= 0) "Z${zi + 1} · ${Run.ZONES[zi].name.uppercase()}" else "POWER"
            4 -> "LAP ${Run.lapNumber}"
            5 -> "AVERAGES"
            else -> "STRESS"
        }
        liveTitle.text = stateText ?: pageName
        liveTitle.setTextColor(when {
            stateText != null -> C.PAUSE
            page == 2 && hz >= 0 -> hrColor
            page == 3 && zi >= 0 -> Run.ZONES[zi].color
            else -> C.ACCENT
        })
        // Zone bar: heart-rate zone on the main and heart pages, power zone on the power page.
        val barZone = when (page) { 1, 2 -> hz; 3 -> zi; else -> -2 }
        if (page == 7) { zoneBar.visibility = View.GONE; renderStep(stateText); return }
        zoneBar.visibility = if (barZone == -2) View.GONE else View.VISIBLE
        for (i in 0 until zoneBar.childCount) zoneBar.getChildAt(i).alpha = if (i == barZone) 1f else 0.22f

        val hr = if (Run.heartRate > 0) "${Run.heartRate}" else "--"
        when (page) {
            1 -> {
                show(fieldA, Run.clock(Run.movingSeconds), "time")
                show(fieldB, Run.miles(Run.distance), "miles")
                show(fieldC, Run.pace(Run.currentSpeed), "pace /mi", C.ACCENT)
                show(fieldD, hr, hrZoneText, hrColor)
            }
            2 -> {
                show(fieldA, hr, "bpm", hrColor)
                show(fieldB, if (Run.lapHr > 0) "${Run.lapHr}" else "--", "lap HR")
                show(fieldC, if (Run.avgHr > 0) "${Run.avgHr}" else "--", "avg HR")
                val b = Run.hrBounds()
                show(fieldD, if (Run.heartRate > 0) "${Run.hrrPercent(Run.heartRate)}%" else "--",
                    if (hz in 0..3) "HRR · Z${hz + 1} ${b[hz]}–${b[hz + 1] - 1}" else if (hz == 4) "HRR · Z5 ${b[4]}+" else "of HR reserve",
                    hrColor)
            }
            3 -> {
                show(fieldA, if (Run.strydConnected) "${Run.power3s}" else "--", "watts (3s)",
                    if (zi >= 0) Run.ZONES[zi].color else C.ACCENT)
                show(fieldB, if (Run.strydConnected) "${Run.power10s}" else "--", "10s W")
                show(fieldC, if (Run.lapPower > 0) "${Run.lapPower}" else "--", "lap W")
                show(fieldD,
                    if (Run.power3s > 0) "${(Run.power3s * 100.0 / Run.criticalPower).toInt()}%" else "--",
                    "of CP (${Run.criticalPower} W)", if (zi >= 0) Run.ZONES[zi].color else Color.WHITE)
            }
            4 -> {
                show(fieldA, Run.clock(Run.lapSeconds), "lap time")
                show(fieldB, Run.miles(Run.lapMeters), "lap mi")
                show(fieldC, Run.paceFor(Run.lapSeconds, Run.lapMeters), "lap pace", C.ACCENT)
                show(fieldD, if (Run.lapHr > 0) "${Run.lapHr}" else "--", "lap HR",
                    Run.hrZoneIndex(Run.lapHr).let { if (it >= 0) Run.ZONE_COLORS[it] else Color.WHITE })
            }
            6 -> {
                show(fieldA, "${Run.stress}", "stress (est.)", C.ACCENT)
                show(fieldB, if (Run.normalizedPower > 0) "${Run.normalizedPower}" else "--", "norm W")
                show(fieldC, if (Run.intensity > 0) String.format(java.util.Locale.US, "%.2f", Run.intensity) else "--", "intensity")
                show(fieldD, (if (Run.strydCadence > 0) "${Run.strydCadence}" else "--") +
                    (if (Run.strydStride > 0.2) String.format(java.util.Locale.US, " · %.2fm", Run.strydStride * Run.strydCalibration) else ""),
                    if (Run.strydStride > 0.2) "cadence · stride" else "cadence")
            }
            else -> {
                show(fieldA, Run.pace(Run.avgSpeed), "avg pace /mi", C.ACCENT)
                show(fieldB, if (Run.avgHr > 0) "${Run.avgHr}" else "--", "avg HR")
                show(fieldC, if (Run.avgPower > 0) "${Run.avgPower}" else "--", "avg W")
                show(fieldD, if (Run.avgCadence > 0) "${Run.avgCadence}" else "--", "avg cadence")
            }
        }
    }

    // ---------------------------------------------------------------- workout step page

    private fun stepTitle(): String {
        if (Run.workoutDone) return "WORKOUT DONE"
        val s = Run.currentStep ?: return "WORKOUT"
        val kind = io.github.michealsizemore1.beyondstride.Workouts.kindName(s.optString("kind")).uppercase()
        return if (s.has("rep")) "$kind · ${s.optInt("rep")}/${s.optInt("reps")}"
               else "$kind · ${Run.stepIndex + 1}/${Run.workoutSteps.size}"
    }

    private fun renderStep(stateText: String?) {
        if (stateText != null) { liveTitle.text = stateText; liveTitle.setTextColor(C.PAUSE) }
        else { liveTitle.text = stepTitle(); liveTitle.setTextColor(C.ACCENT) }
        if (Run.workoutDone) {
            show(fieldA, Run.clock(Run.movingSeconds), "total time")
            show(fieldB, Run.miles(Run.distance), "miles")
            show(fieldC, Run.pace(Run.currentSpeed), "pace", C.ACCENT)
            show(fieldD, "✓", "keep running or finish", C.OK)
            return
        }
        val s = Run.currentStep ?: return
        // Big number: what's left in the step.
        val rem = Run.stepRemaining
        when (s.optString("dur")) {
            "time" -> show(fieldA, Run.clock(rem?.toInt() ?: 0), "left in step")
            "dist" -> show(fieldA, rem?.let { if (s.optInt("value") < 1600) "${it.toInt()} m" else Run.miles(it) } ?: "--",
                if (s.optInt("value") < 1600) "left in step" else "mi left in step")
            else -> show(fieldA, Run.clock(Run.stepSeconds), "tap NEXT STEP when done")
        }
        // Target and where you are against it.
        val target = s.optString("target")
        val (lo, hi) = Run.stepTarget
        val v = Run.stepValue()
        // Easy step under the heart-rate cap: below the zone is fine — no "speed up".
        val easyOk = Run.capApplies() && target == "hr" && Run.stepStatus == -1
        val color = if (easyOk) C.OK else when (Run.stepStatus) { 0 -> C.OK; null -> Color.WHITE; else -> if (
            (target == "pace" && Run.stepStatus == 1) || (target != "pace" && Run.stepStatus == -1)) 0xFF4FA3E0.toInt() else C.STOP }
        val hint = if (easyOk) "easy ✓" else when (Run.stepStatus) {
            0 -> "on target"
            -1 -> if (target == "pace") "ease off" else "speed up"
            1 -> if (target == "pace") "speed up" else "ease off"
            else -> ""
        }
        when (target) {
            "hr" -> show(fieldB, v?.toString() ?: "--", "HR $lo–$hi", color)
            "power" -> show(fieldB, v?.toString() ?: "--", "W $lo–$hi", color)
            "pace" -> show(fieldB, v?.let { Run.clock(it) } ?: "--", "${Run.clock(lo)}–${Run.clock(hi)}", color)
            else -> show(fieldB, if (Run.heartRate > 0) "${Run.heartRate}" else "--", "HR", Color.WHITE)
        }
        show(fieldC, Run.paceFor(Run.stepSeconds, Run.stepMeters), if (hint.isNotEmpty()) hint else "step pace",
            if (hint.isNotEmpty()) color else C.ACCENT)
        val next = Run.nextStep
        show(fieldD, if (next == null) "Last step" else "Next: " +
            io.github.michealsizemore1.beyondstride.Workouts.kindName(next.optString("kind")) + " " +
            io.github.michealsizemore1.beyondstride.Workouts.durText(next), "", C.MUTED)
        fieldD.first.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
    }

    // ---------------------------------------------------------------- custom data screens

    private lateinit var pageView: View
    private lateinit var trailView: TrailMapView
    private lateinit var customBox: LinearLayout
    private val cells = mutableListOf<Pair<String, Pair<TextView, TextView>>>()   // key → (label, value)
    private lateinit var customState: TextView
    private lateinit var customDots: TextView

    /** Builds a screen: rows of cells, each a small label over a number that fills its space. */
    private fun buildScreen(i: Int) {
        builtScreen = i
        cells.clear()
        customBox.removeAllViews()
        val w = resources.displayMetrics.widthPixels
        val sc = screens.optJSONObject(i) ?: return
        val layout = io.github.michealsizemore1.beyondstride.RunFields.layout(sc.optString("layout", "6"))
        val fields = sc.optJSONArray("fields")
        customBox.setPadding(0, w / 22, 0, w / 34)
        customState = label("", C.PAUSE, 10f, true).apply { visibility = View.GONE }
        customBox.addView(customState, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        var k = 0
        layout.rows.forEachIndexed { r, count ->
            val edge = r == 0 || r == layout.rows.size - 1
            // Round screen: the top and bottom rows are narrower.
            val inset = if (edge && layout.rows.size > 2) w / 6 else w / 26
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(inset, dp(1), inset, dp(1))
            }
            repeat(count) {
                val key = fields?.optString(k).orEmpty().ifBlank { "time" }; k++
                val cell = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    if (count > 1) { background = rounded(C.CARD, 10); setPadding(dp(1), 0, dp(1), 0) }
                }
                val lab = label(io.github.michealsizemore1.beyondstride.RunFields.name(key), C.MUTED, 9f).apply { includeFontPadding = false }
                val value = TextView(this).apply {
                    gravity = Gravity.CENTER
                    maxLines = 1
                    includeFontPadding = false   // digits fill the whole cell
                    setPadding(0, 0, 0, 0)
                    setTextColor(Color.WHITE)
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    setAutoSizeTextTypeUniformWithConfiguration(10, 80, 1, TypedValue.COMPLEX_UNIT_SP)
                }
                cell.addView(lab, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
                cell.addView(value, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
                row.addView(cell, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply { if (count > 1) { marginStart = dp(2); marginEnd = dp(2) } })
                cells += key to (lab to value)
            }
            // The grid rows get a bit more height than the narrow top and bottom rows.
            customBox.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, 0, if (count > 1) 1.2f else 1f).apply { topMargin = dp(1) })
        }
        customDots = label("", C.DIM, 7f).apply { includeFontPadding = false }
        customBox.addView(customDots, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    private fun renderScreen(i: Int, stateText: String?, order: List<Int>) {
        pageView.visibility = View.GONE
        customBox.visibility = View.VISIBLE
        if (builtScreen != i) buildScreen(i)
        customState.text = stateText ?: ""
        customState.visibility = if (stateText == null) View.GONE else View.VISIBLE
        customDots.text = order.indices.joinToString(" ") { if (it == this.page) "●" else "○" }
        cells.forEach { (key, views) ->
            val (v, color) = metric(key)
            views.second.text = v
            views.second.setTextColor(color)
        }
    }

    /** Current value and color for one metric. */
    private fun metric(key: String): Pair<String, Int> {
        val w = Color.WHITE
        fun n(v: Int) = if (v > 0) "$v" else "--"
        val hz = Run.hrZoneIndex(Run.heartRate)
        val zi = Run.zoneIndex(Run.power3s)
        fun hrColor(bpm: Int) = Run.hrZoneIndex(bpm).let { if (it >= 0) Run.ZONE_COLORS[it] else w }
        fun pwColor(watts: Int) = Run.zoneIndex(watts).let { if (it >= 0) Run.ZONE_COLORS[it] else w }
        return when (key) {
            "time" -> Run.clock(Run.movingSeconds) to w
            "elapsed" -> Run.clock(Run.elapsedSeconds) to w
            "clock" -> java.text.SimpleDateFormat("h:mm", java.util.Locale.getDefault()).format(java.util.Date()) to w
            "distance" -> Run.miles(Run.distance) to w
            "pace" -> Run.pace(Run.currentSpeed) to C.ACCENT
            "avgPace" -> Run.pace(Run.avgSpeed) to C.ACCENT
            "hr" -> n(Run.heartRate) to hrColor(Run.heartRate)
            "avgHr" -> n(Run.avgHr) to hrColor(Run.avgHr)
            "maxHr" -> n(Run.maxHr) to hrColor(Run.maxHr)
            "hrZone" -> (if (hz >= 0) "Z${hz + 1}" else "--") to (if (hz >= 0) Run.ZONE_COLORS[hz] else w)
            "hrr" -> (if (Run.heartRate > 0) "${Run.hrrPercent(Run.heartRate)}%" else "--") to hrColor(Run.heartRate)
            "power" -> (if (Run.strydConnected) n(Run.power3s) else "--") to pwColor(Run.power3s)
            "power10" -> (if (Run.strydConnected) n(Run.power10s) else "--") to pwColor(Run.power10s)
            "avgPower" -> n(Run.avgPower) to pwColor(Run.avgPower)
            "np" -> n(Run.normalizedPower) to pwColor(Run.normalizedPower)
            "pctCp" -> (if (Run.power3s > 0) "${Run.power3s * 100 / Run.criticalPower.coerceAtLeast(1)}%" else "--") to pwColor(Run.power3s)
            "powerZone" -> (if (zi >= 0) "Z${zi + 1}" else "--") to (if (zi >= 0) Run.ZONE_COLORS[zi] else w)
            "cadence" -> n(Run.strydCadence) to w
            "avgCadence" -> n(Run.avgCadence) to w
            "stride" -> (if (Run.strydStride > 0.2) String.format(java.util.Locale.US, "%.2f", Run.strydStride * Run.strydCalibration) else "--") to w
            "lapTime" -> Run.clock(Run.lapSeconds) to w
            "lapDist" -> Run.miles(Run.lapMeters) to w
            "lapPace" -> Run.paceFor(Run.lapSeconds, Run.lapMeters) to C.ACCENT
            "lapHr" -> n(Run.lapHr) to hrColor(Run.lapHr)
            "lapPower" -> n(Run.lapPower) to pwColor(Run.lapPower)
            "lapNum" -> "${Run.lapNumber}" to w
            "lastLap" -> (Run.laps.lastOrNull()?.let { Run.clock(it.seconds) } ?: "--") to w
            "stress" -> "${Run.stress}" to C.ACCENT
            "intensity" -> (if (Run.intensity > 0) String.format(java.util.Locale.US, "%.2f", Run.intensity) else "--") to w
            else -> "--" to w
        }
    }

    private fun renderControls(paused: Boolean) {
        dataBox.visibility = View.GONE
        controlsBox.visibility = View.VISIBLE
        liveTitle.text = if (confirmingFinish) "FINISH RUN?" else Run.clock(Run.movingSeconds) + " · " + Run.miles(Run.distance) + " mi"
        liveTitle.setTextColor(if (paused) C.PAUSE else C.ACCENT)
        if (controlsBox.childCount > 0 && controlsBox.tag == "$confirmingFinish$paused") return
        controlsBox.tag = "$confirmingFinish$paused"
        controlsBox.removeAllViews()

        fun button(text: String, color: Int, textColor: Int = Color.BLACK, action: () -> Unit) =
            label(text, textColor, 17f, true).apply {
                background = rounded(color, 26)
                setPadding(0, dp(11), 0, dp(11))
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(7) }
                setOnClickListener { action(); renderLive() }
            }

        if (confirmingFinish) {
            // Big targets you can hit mid-stride.
            controlsBox.addView(button("SAVE", C.OK) { pulse(longArrayOf(0, 200, 100, 200, 100, 700)); finishRun() }.apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f); setPadding(0, dp(26), 0, dp(26))
            })
            controlsBox.addView(button("RESUME", C.ACCENT) { confirmingFinish = false; Run.resume() }.apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f); setPadding(0, dp(16), 0, dp(16))
            })
            controlsBox.addView(label("Discard run", C.STOP, 11f, true).apply {
                setPadding(0, dp(10), 0, 0)
                setOnClickListener { Run.discard(this@RunActivity); RunService.stop(this@RunActivity); finish() }
            })
        } else {
            controlsBox.addView(if (paused) button("RESUME", C.ACCENT) { Run.resume() }
                                else button("PAUSE", C.PAUSE) { Run.pause() })
            if (Run.hasWorkout && !Run.workoutDone) controlsBox.addView(button("NEXT STEP", C.CARD, Color.WHITE) { Run.nextWorkoutStep(auto = false) })
            else controlsBox.addView(button("LAP", C.CARD, Color.WHITE) { Run.lap() })
            controlsBox.addView(button("FINISH", C.STOP, Color.WHITE) { Run.pause(); confirmingFinish = true })
        }
    }

    // ---------------------------------------------------------------- summary

    private fun finishRun() {
        val usedTrail = Run.nav != null
        // Free the pod for 10 minutes so the Stryd app can sync it (Air Power etc.).
        prefs.edit().putLong("strydFreeUntil", System.currentTimeMillis() + 10 * 60_000L).apply()
        val json = Run.finish(this)
        if (usedTrail) io.github.michealsizemore1.beyondstride.TrailSync.choose(this, null, false, "watch")
        Speaker.finished(json)
        RunSync.pushAll(this)
        RunService.stop(this)
        summary = json
        screen = Screen.SUMMARY
        liveBuilt = false
        root.removeAllViews()
        root.setBackgroundColor(C.BG)
        root.addView(RunViews.summary(this, json, onDone = { backToHome() }))
    }

    // ---------------------------------------------------------------- helpers

    private fun fmtHr(v: Double) = if (v % 1.0 == 0.0) "${v.toInt()}" else String.format(java.util.Locale.US, "%.1f", v)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** The phone's two quality bars (Daily food quality, Top 7 micronutrient quality), compact. */
    private fun qualityBars(box: LinearLayout, d: io.github.michealsizemore1.beyondstride.Nutrition.Day) {
        val N = io.github.michealsizemore1.beyondstride.Nutrition
        fun bar(title: String, score: Double, lbl: String) {
            box.addView(label("$title  " + String.format(java.util.Locale.US, "%.1f", score) + " · " + lbl, N.statusColor(when { score >= 7 -> "Met"; score >= 5 -> "Close"; else -> "Low" }), 10f, true).apply { setPadding(0, dp(4), 0, 0) })
            val pct = ((score - 1) / 9).coerceIn(0.0, 1.0).toFloat()
            box.addView(object : android.view.View(this) {
                override fun onDraw(c: android.graphics.Canvas) {
                    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG); val h = height.toFloat(); val r = h / 2; val bh = h * 0.6f
                    p.shader = android.graphics.LinearGradient(0f, 0f, width.toFloat(), 0f, intArrayOf(0xFFDC2626.toInt(), 0xFFF97316.toInt(), 0xFFFACC15.toInt(), 0xFF84CC16.toInt(), 0xFF16A34A.toInt()), null, android.graphics.Shader.TileMode.CLAMP)
                    c.drawRoundRect(0f, (h - bh) / 2, width.toFloat(), (h + bh) / 2, bh / 2, bh / 2, p); p.shader = null
                    val x = (width * pct).coerceIn(r, width - r); p.color = Color.WHITE; c.drawCircle(x, h / 2, r * 0.9f, p)
                    p.color = 0xFF0F172A.toInt(); p.style = android.graphics.Paint.Style.STROKE; p.strokeWidth = r * 0.32f; c.drawCircle(x, h / 2, r * 0.74f, p)
                }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(12)).apply { topMargin = dp(2) })
        }
        io.github.michealsizemore1.beyondstride.Food.dayQuality(d)?.let { bar("Food quality", it.score, it.label) }
        if (d.micros.isNotEmpty()) bar("Micronutrients", d.microScore, d.microLabel())
    }
    private fun qualityBar(box: LinearLayout, title: String, score: Double, lbl: String) {
        val N = io.github.michealsizemore1.beyondstride.Nutrition
        box.addView(label("$title  " + String.format(java.util.Locale.US, "%.1f", score) + " · " + lbl, N.statusColor(when { score >= 7 -> "Met"; score >= 5 -> "Close"; else -> "Low" }), 10f, true).apply { setPadding(0, dp(4), 0, 0) })
        val pct = ((score - 1) / 9).coerceIn(0.0, 1.0).toFloat()
        box.addView(object : android.view.View(this) {
            override fun onDraw(c: android.graphics.Canvas) {
                val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG); val h = height.toFloat(); val r = h / 2; val bh = h * 0.6f
                p.shader = android.graphics.LinearGradient(0f, 0f, width.toFloat(), 0f, intArrayOf(0xFFDC2626.toInt(), 0xFFF97316.toInt(), 0xFFFACC15.toInt(), 0xFF84CC16.toInt(), 0xFF16A34A.toInt()), null, android.graphics.Shader.TileMode.CLAMP)
                c.drawRoundRect(0f, (h - bh) / 2, width.toFloat(), (h + bh) / 2, bh / 2, bh / 2, p); p.shader = null
                val x = (width * pct).coerceIn(r, width - r); p.color = Color.WHITE; c.drawCircle(x, h / 2, r * 0.9f, p)
                p.color = 0xFF0F172A.toInt(); p.style = android.graphics.Paint.Style.STROKE; p.strokeWidth = r * 0.32f; c.drawCircle(x, h / 2, r * 0.74f, p)
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(12)).apply { topMargin = dp(2) })
    }
    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radiusDp).toFloat() }
    private fun column() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    }
    private fun label(text: String, color: Int, size: Float, bold: Boolean = false) = TextView(this).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, RunViews.scaled(this@RunActivity, size))
        gravity = Gravity.CENTER
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    }
}
