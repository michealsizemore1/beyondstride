package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.michealsizemore1.beyondstride.R

/**
 * 🩺 System health (Nov 27 2026): when each sync, background job, backup and data feed last worked, so a problem shows
 * up here before it shows up as wrong numbers. Also notes which screens you haven't opened lately (nothing is removed:
 * it's only there to help decide what to tidy up later).
 *
 * Everything is switchable in Settings → System health: the warnings (a red line on Today and at most one
 * notification a day when something important stops), screen-use tracking, and the Health card on Today.
 */
object SysHealth {
    private fun p(ctx: Context) = ctx.getSharedPreferences("syshealth", 0)

    /** Records that [key] just worked. Cheap; safe from any thread. */
    fun mark(ctx: Context, key: String) { try { p(ctx).edit().putLong("ok_$key", System.currentTimeMillis()).apply() } catch (e: Exception) { } }
    private fun ok(ctx: Context, key: String) = p(ctx).getLong("ok_$key", 0L)

    fun warnOn(ctx: Context) = p(ctx).getBoolean("warn", true)
    fun setWarn(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("warn", on).apply()
    fun trackOn(ctx: Context) = p(ctx).getBoolean("track", true)
    fun setTrack(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("track", on).apply()

    enum class St { OK, LATE, BAD, OFF }
    /** One line on the page. [warnH] = hours before it counts as late (red at 3×). [important] items raise the warnings. */
    class Check(val icon: String, val name: String, val at: Long, val warnH: Double, val important: Boolean, val off: String? = null, val fix: String = "") {
        /** Never worked yet: measured from when this page first started watching (so a fresh install isn't all red). */
        var start = 0L
        fun status(now: Long = System.currentTimeMillis()): St { val t = if (at > 0L) at else start; return when {
            off != null -> St.OFF
            t <= 0L -> St.BAD
            now - t > warnH * 3 * 3_600_000 -> St.BAD
            now - t > warnH * 3_600_000 -> St.LATE
            else -> St.OK
        } }
    }

    fun checks(ctx: Context): List<Check> {
        val cloud = NutritionSync.ready(ctx)
        val sp = { n: String -> ctx.getSharedPreferences(n, 0) }
        val list = mutableListOf(
            Check("⏰", "Background jobs (hourly)", ok(ctx, "hourly"), 4.0, true, fix = "Android may be putting BeyondStride to sleep. Phone Settings → Apps → BeyondStride → Battery → Unrestricted."),
            Check("☁️", "Daily summaries to the cloud", ok(ctx, "daily"), 24.0, true, if (!cloud) "Not linked" else null, "Tap Sync on Today. Needs internet."),
            Check("☁️", "Cloud copy", sp("cloudcopy").getLong("at", 0L), 48.0, false, if (!cloud) "Not linked" else null, "Tap Sync on Today."),
            Check("⇪", "Backup file", Backup.lastAt(ctx), 48.0, true, if (Backup.uri(ctx) == null) "Not set up" else null, "Settings → Backup → Back up now."),
            Check("🌙", "Phone Day Review (11:50 PM)", ok(ctx, "dayreview"), 48.0, false, if (!AiFood.hasKey(ctx) || !cloud) "Needs the AI key and cloud link" else null, "Runs at night when the phone has internet."),
            Check("⌚", "Watch connected", ok(ctx, "watch"), 48.0, false, fix = "Check the watch's Bluetooth and that it's paired with this phone."),
            Check("🌤", "Weather", sp("todayweather").getLong("at", 0L), 6.0, false, fix = "Pull Today's weather card to refresh."),
            Check("📈", "Markets", sp("markets").getLong("at", 0L), 72.0, false, fix = "Tap the Markets card on Today."),
            Check("⬆️", "App update check", sp("updater").getLong("checked", 0L), 48.0, false, fix = "Settings → About → Check for updates."),
            Check("🔄", "Sync button last used", Local.lastSyncNow(ctx), 24.0 * 30, false))
        // Your other apps: when each last shared its numbers (only if installed).
        if (TodaySpending.installed(ctx)) list += Check("💳", "Spending from M&C Money", TodaySpending.read(ctx)?.second ?: 0L, 24.0 * 7, false,
            fix = "Save a transaction in M&C Money, or open it once.")
        if (try { ctx.packageManager.getPackageInfo(TodayRetirement.PKG, 0); true } catch (e: Exception) { false })
            list += Check("🏖️", "Retirement Planner", TodayRetirement.read(ctx)?.second ?: 0L, 24.0 * 30, false, fix = "Open Retirement Planner once.")
        if (TodayHealth.installed(ctx)) list += Check("❤️", "Health Records", TodayHealth.read(ctx)?.second ?: 0L, 24.0 * 30, false, fix = "Open Health Records once.")
        TodayComingUp.SOURCES.filter { TodayComingUp.installed(ctx, it) }.forEach { s ->
            list += Check(s.icon, s.name, TodayComingUp.read(ctx, s)?.second ?: 0L, 24.0 * 30, false, fix = "Open ${s.name} once (and tap Allow on Today's Coming up card).") }
        val start = p(ctx).getLong("start", 0L).let { if (it == 0L) System.currentTimeMillis().also { n -> p(ctx).edit().putLong("start", n).apply() } else it }
        list.forEach { it.start = start }
        return list
    }

    /** Important things that have stopped (red), for the warnings. */
    fun problems(ctx: Context) = try { checks(ctx).filter { it.important && it.status() == St.BAD } } catch (e: Exception) { emptyList() }

    /** Notes when the watch was last connected to the phone. Call off the main thread. */
    fun watchCheck(ctx: Context) {
        try { val nodes = com.google.android.gms.tasks.Tasks.await(com.google.android.gms.wearable.Wearable.getNodeClient(ctx).connectedNodes, 10, java.util.concurrent.TimeUnit.SECONDS)
            if (nodes.isNotEmpty()) mark(ctx, "watch") } catch (e: Exception) { }
    }

    /** Hourly (from Reminders): one notification a day at most, when something important has stopped. */
    fun hourly(ctx: Context) {
        mark(ctx, "hourly"); watchCheck(ctx)
        if (!warnOn(ctx)) return
        val bad = problems(ctx); if (bad.isEmpty()) return
        val today = java.time.LocalDate.now().toString(); if (p(ctx).getString("notified", "") == today) return
        if (java.time.LocalTime.now().hour !in 9..20) return
        p(ctx).edit().putString("notified", today).apply()
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel("syshealth", "System health", NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getActivity(ctx, 970, Intent(ctx, SystemHealthActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val text = bad.joinToString(", ") { it.name } + " stopped working. Tap to see how to fix it."
        try {
            nm.notify(970, android.app.Notification.Builder(ctx, "syshealth").setSmallIcon(R.drawable.ic_stat_run).setContentTitle("🩺 BeyondStride needs a look")
                .setContentText(text).setStyle(android.app.Notification.BigTextStyle().bigText(text)).setContentIntent(pi).setAutoCancel(true)
                .setLocalOnly(true).build())
        } catch (e: SecurityException) { }
    }

    /** A red line for the top of Today when something important stopped, or null. */
    fun todayBanner(act: Activity): View? {
        if (!warnOn(act)) return null
        val bad = problems(act); if (bad.isEmpty()) return null
        val d = { v: Int -> RunUi.dp(act, v) }
        return RunUi.text(act, "🩺 ${bad.joinToString(", ") { it.name }} stopped working · tap to fix", 0xFFFCA5A5.toInt(), 13f, true).apply {
            background = RunUi.rounded(0x33F87171, d(12).toFloat()); setPadding(d(12), d(10), d(12), d(10))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = d(8) }
            setOnClickListener { act.startActivity(Intent(act, SystemHealthActivity::class.java)) }
        }
    }

    // ── Screen use (to help tidy up later; nothing is removed) ──────────
    private val NAMES = mapOf("RunsActivity" to "Today", "LifeActivity" to "Life", "FitnessActivity" to "Fitness", "ActivitiesActivity" to "Activities",
        "NutritionActivity" to "Nutrition", "AppsActivity" to "Apps", "CoachActivity" to "Coach", "SpendingActivity" to "Spending details",
        "MorningReportActivity" to "Morning report", "ReadinessActivity" to "Readiness", "RunDetailActivity" to "Run details", "AllRunsActivity" to "All runs",
        "WorkoutsActivity" to "Workouts", "WorkoutEditActivity" to "Workout editor", "TrailsActivity" to "Trails", "LiveTrackActivity" to "LiveTrack",
        "NutritionToolsActivity" to "Nutrition tools", "FoodAddActivity" to "Add food", "ScreensActivity" to "Watch screens", "RunSettingsActivity" to "Settings",
        "ActivityEditActivity" to "Edit activity", "SystemHealthActivity" to "System health")
    private fun u(ctx: Context) = ctx.getSharedPreferences("screenuse", 0)

    fun install(app: Application) {
        if (u(app).getLong("since", 0L) == 0L) u(app).edit().putLong("since", System.currentTimeMillis()).apply()
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(a: Activity) { if (trackOn(a)) try { u(a).edit().putLong(a.javaClass.simpleName, System.currentTimeMillis()).apply() } catch (e: Exception) { } }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
    }

    /** (screen, last opened or 0) for screens not opened in [days], once tracking has run that long; null before then. */
    fun unused(ctx: Context, days: Int = 30): List<Pair<String, Long>>? {
        val since = u(ctx).getLong("since", 0L); val now = System.currentTimeMillis()
        if (since == 0L || now - since < days * 86_400_000L) return null
        return NAMES.filterKeys { it != "SystemHealthActivity" && it != "RunSettingsActivity" }.map { (k, n) -> n to u(ctx).getLong(k, 0L) }
            .filter { now - it.second > days * 86_400_000L }.sortedBy { it.second }
    }
    fun trackingDays(ctx: Context) = u(ctx).getLong("since", 0L).let { if (it == 0L) 0 else ((System.currentTimeMillis() - it) / 86_400_000L).toInt() }
}

/** Settings → 🩺 System health. */
class SystemHealthActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); render() }
    override fun onResume() { super.onResume(); render() }

    private fun ago(ms: Long): String {
        if (ms <= 0L) return "never"
        val m = (System.currentTimeMillis() - ms) / 60_000L
        return when { m < 1 -> "just now"; m < 60 -> "$m min ago"; m < 48 * 60 -> "${m / 60} h ago"; else -> "${m / 1440} days ago" }
    }

    private fun render() {
        val ctx = this; val d = { v: Int -> RunUi.dp(ctx, v) }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(d(14), d(40), d(14), d(28)) }
        col.addView(RunUi.text(this, "‹ Settings", RunUi.ACCENT, 15f, true).apply { setPadding(0, 0, 0, d(8)); setOnClickListener { finish() } })
        col.addView(RunUi.text(this, "🩺 System health", Color.WHITE, 26f, true))
        val checks = SysHealth.checks(this)
        val bad = checks.count { it.status() == SysHealth.St.BAD }; val late = checks.count { it.status() == SysHealth.St.LATE }
        col.addView(RunUi.text(this, when { bad > 0 -> "$bad need a look" + (if (late > 0) " · $late running late" else ""); late > 0 -> "Everything works · $late running late"; else -> "✅ Everything is working" },
            if (bad > 0) 0xFFF87171.toInt() else if (late > 0) 0xFFFBBF24.toInt() else 0xFF4ADE80.toInt(), 14f, true).apply { setPadding(0, d(4), 0, d(8)) })

        val card = RunUi.card(this).apply { orientation = LinearLayout.VERTICAL }
        checks.forEach { c ->
            val st = c.status()
            val (dot, color) = when (st) { SysHealth.St.OK -> "🟢" to Color.WHITE; SysHealth.St.LATE -> "🟡" to 0xFFFBBF24.toInt(); SysHealth.St.BAD -> "🔴" to 0xFFF87171.toInt(); SysHealth.St.OFF -> "⚪" to RunUi.MUTED }
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, d(8), 0, d(8)) }
            row.addView(LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(RunUi.text(ctx, "$dot  ${c.icon} ${c.name}", color, 14f, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(RunUi.text(ctx, c.off ?: ago(c.at), RunUi.MUTED, 12.5f)) })
            if ((st == SysHealth.St.BAD || st == SysHealth.St.LATE) && c.fix.isNotEmpty())
                row.addView(RunUi.text(this, c.fix, RunUi.MUTED, 12f).apply { setPadding(d(26), d(2), 0, 0) })
            card.addView(row)
        }
        col.addView(card)
        col.addView(RunUi.button(this, "🔄 Sync everything now", RunUi.ACCENT) {
            android.widget.Toast.makeText(ctx, "Syncing…", android.widget.Toast.LENGTH_SHORT).show()
            Thread { try { Local.syncNow(applicationContext) } catch (e: Exception) { }; runOnUiThread { if (!isFinishing) render() } }.start()
        }.apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = d(10) } })

        // Screens you haven't opened lately.
        col.addView(RunUi.text(this, "Screens you haven't used lately", Color.WHITE, 16f, true).apply { setPadding(0, d(20), 0, d(4)) })
        val un = SysHealth.unused(this)
        col.addView(RunUi.text(this, when {
            !SysHealth.trackOn(this) -> "Tracking is off."
            un == null -> "Collecting for 30 days (${SysHealth.trackingDays(this)} so far). Then this lists screens you haven't opened in a month, to help decide what to tidy up. Nothing is ever removed on its own."
            un.isEmpty() -> "You've used every screen in the last 30 days."
            else -> un.joinToString("\n") { "• ${it.first} — " + (if (it.second == 0L) "not opened since tracking began" else "last opened ${ago(it.second)}") } +
                "\n\nNothing is removed on its own. If you never use one, ask Claude to tidy it up."
        }, RunUi.MUTED, 13f))

        // Switches: everything here can be turned off.
        col.addView(RunUi.text(this, "Options", Color.WHITE, 16f, true).apply { setPadding(0, d(20), 0, d(4)) })
        fun sw(label: String, sub: String, on: Boolean, set: (Boolean) -> Unit) = col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, d(8), 0, d(8))
            addView(LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL
                addView(RunUi.text(ctx, label, Color.WHITE, 14f, true)); addView(RunUi.text(ctx, sub, RunUi.MUTED, 12f)) },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(android.widget.Switch(ctx).apply { isChecked = on; setOnCheckedChangeListener { _, v -> set(v); render() } })
        })
        sw("Warn me when something stops", "A red line on Today and at most one notification a day (phone only)", SysHealth.warnOn(this)) { SysHealth.setWarn(this, it) }
        sw("Track which screens I use", "Stays on this phone; only used for the list above", SysHealth.trackOn(this)) { SysHealth.setTrack(this, it) }
        if (TodayComingUp.anyInstalled(this)) sw("Coming up card on Today", "From Dates & Docs and Vehicle Upkeep", TodayComingUp.shown(this)) { TodayComingUp.setShown(this, it) }
        if (TodayHealth.installed(this)) sw("Health card on Today", "From the Health Records app", TodayHealth.shown(this)) { TodayHealth.setShown(this, it) }

        setContentView(ScrollView(this).apply { setBackgroundColor(RunUi.BG); addView(col) })
    }
}
