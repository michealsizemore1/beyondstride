package io.github.michealsizemore1.beyondstride

import android.app.Activity
import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import android.graphics.Color
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.github.michealsizemore1.beyondstride.run.RunUi

/**
 * 🔒 App lock (like the Retirement Planner's): asks for your fingerprint or the phone's screen lock when BeyondStride
 * opens, every time you close it (swipe it away or back out), when the screen turns off, and after 5 minutes away.
 * (The app keeps running in the background for steps, LiveTrack and sync, so "opening" alone wasn't enough.) Needs a screen lock on the phone; without one the app just opens.
 * On/off in Settings → App lock. Phone only (the watch has no lock).
 */
object AppLock {
    const val RELOCK_AFTER_MS = 5 * 60_000L   // away longer than this (home, another app, screen off) asks again
    private const val TAG = "bs_applock"
    private var unlocked = false
    private var leftAt = 0L
    private var started = 0
    private var alive = 0        // screens that exist; 0 = you closed the app
    private var prompting = false

    fun enabled(ctx: Context) = ctx.getSharedPreferences("applock", 0).getBoolean("enabled", true)
    fun setEnabled(ctx: Context, on: Boolean) { ctx.getSharedPreferences("applock", 0).edit().putBoolean("enabled", on).apply(); unlocked = true }   // you're already in: the lock starts next time
    /** The phone has a screen lock (and a new enough Android for the fingerprint prompt). */
    fun available(ctx: Context) = Build.VERSION.SDK_INT >= 28 &&
        (ctx.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isDeviceSecure == true
    private fun needed(ctx: Context) = !unlocked && enabled(ctx) && available(ctx)

    fun install(app: Application) {
        // Starting fresh right after an app update you installed (or a restart while you were only briefly away):
        // you were just in the app, so don't ask again.
        try {
            val pr = app.getSharedPreferences("applock", 0); val now = System.currentTimeMillis()
            val ver = app.packageManager.getPackageInfo(app.packageName, 0).let { if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode.toLong() }
            val prevVer = pr.getLong("ver", -1L); val activeAt = pr.getLong("activeAt", 0L)
            if (activeAt > 0 && (now - activeAt < RELOCK_AFTER_MS || (prevVer != -1L && prevVer != ver && now - activeAt < 30 * 60_000L))) { unlocked = true; leftAt = activeAt }
            pr.edit().putLong("ver", ver).apply()
        } catch (e: Exception) { }
        // (Screen off no longer locks at once: it counts as time away, like going home.)
        app.registerActivityLifecycleCallbacks(callbacks)
    }
    private val callbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(a: Activity) {
            if (started == 0 && unlocked && leftAt > 0 && System.currentTimeMillis() - leftAt > RELOCK_AFTER_MS) { unlocked = false; note(a, "Locked: app was out of sight") }
            started++
        }
        override fun onActivityPostStarted(a: Activity) { if (needed(a)) cover(a, ask = false) }
        override fun onActivityStopped(a: Activity) { started = (started - 1).coerceAtLeast(0); if (started == 0) leftAt = System.currentTimeMillis(); active(a) }
        override fun onActivityResumed(a: Activity) { if (needed(a)) cover(a) else { uncover(a); active(a) } }
        override fun onActivityCreated(a: Activity, b: Bundle?) { alive++ }
        override fun onActivityPaused(a: Activity) {}
        override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
        override fun onActivityDestroyed(a: Activity) { alive = (alive - 1).coerceAtLeast(0); if (alive == 0 && !a.isChangingConfigurations) { unlocked = false; a.getSharedPreferences("applock", 0).edit().putLong("activeAt", 0L).apply() } }   // closed: ask next time
    }

    /** Hide the screen behind a lock panel and ask. */
    private fun cover(a: Activity, ask: Boolean = true) {
        val root = a.window.decorView as? ViewGroup ?: return
        if (root.findViewWithTag<View>(TAG) == null) {
            val dp = { v: Int -> RunUi.dp(a, v) }
            root.addView(FrameLayout(a).apply {
                tag = TAG; setBackgroundColor(RunUi.BG); isClickable = true; isFocusable = true
                addView(LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                    addView(TextView(a).apply { text = "🔒"; textSize = 48f; gravity = Gravity.CENTER })
                    addView(TextView(a).apply { text = "BeyondStride is locked"; textSize = 20f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(6)) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                    addView(TextView(a).apply { text = "Use your fingerprint or screen lock"; textSize = 14f; setTextColor(RunUi.MUTED); gravity = Gravity.CENTER }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                    addView(RunUi.button(a, "Unlock", RunUi.ACCENT) { ask(a) }, LinearLayout.LayoutParams(dp(200), ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(24) })
                    setPadding(dp(24), 0, dp(24), 0)
                }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        if (ask) ask(a)
    }
    /** Last moment you were in the app unlocked (survives a restart, e.g. installing an update). */
    private fun active(ctx: Context) { if (unlocked) ctx.getSharedPreferences("applock", 0).edit().putLong("activeAt", System.currentTimeMillis()).apply() }
    private fun uncover(a: Activity) { (a.window.decorView as? ViewGroup)?.let { r -> r.findViewWithTag<View>(TAG)?.let { r.removeView(it) } } }

    /** What the lock last did (shown in Settings → App lock), to see why it did or didn't ask. */
    fun last(ctx: Context) = ctx.getSharedPreferences("applock", 0).getString("last", "") ?: ""
    private fun note(ctx: Context, what: String) {
        val t = java.text.SimpleDateFormat("MMM d, h:mm:ss a", java.util.Locale.US).format(java.util.Date())
        ctx.getSharedPreferences("applock", 0).edit().putString("last", "$t · $what").apply()
    }
    private var fails = 0

    private fun ask(a: Activity) {
        if (prompting || Build.VERSION.SDK_INT < 28) return
        prompting = true; note(a, "Asked for fingerprint")
        val b = BiometricPrompt.Builder(a).setTitle("Unlock BeyondStride").setSubtitle("Use your fingerprint or screen lock")
        if (Build.VERSION.SDK_INT >= 30) b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        else @Suppress("DEPRECATION") b.setDeviceCredentialAllowed(true)
        try {
            b.build().authenticate(CancellationSignal(), a.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult?) { prompting = false; fails = 0; unlocked = true; uncover(a); active(a); note(a, "Unlocked") }
                override fun onAuthenticationError(code: Int, msg: CharSequence?) { prompting = false; note(a, "Not unlocked ($code: $msg) · tap Unlock") }   // stays locked
            })
        } catch (e: Exception) {
            prompting = false; note(a, "Prompt failed: ${e.javaClass.simpleName} ${e.message ?: ""}")
            if (++fails >= 3) { fails = 0; unlocked = true; uncover(a) }   // never lock you out because the prompt itself keeps failing
        }
    }
}
