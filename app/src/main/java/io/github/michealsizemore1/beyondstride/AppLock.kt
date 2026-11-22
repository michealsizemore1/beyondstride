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
 * opens and after it has been away for 5 minutes. Needs a screen lock on the phone; without one the app just opens.
 * On/off in Settings → App lock. Phone only (the watch has no lock).
 */
object AppLock {
    const val RELOCK_AFTER_MS = 5 * 60_000L
    private const val TAG = "bs_applock"
    private var unlocked = false
    private var leftAt = 0L
    private var started = 0
    private var prompting = false

    fun enabled(ctx: Context) = ctx.getSharedPreferences("applock", 0).getBoolean("enabled", true)
    fun setEnabled(ctx: Context, on: Boolean) { ctx.getSharedPreferences("applock", 0).edit().putBoolean("enabled", on).apply(); unlocked = true }   // you're already in: the lock starts next time
    /** The phone has a screen lock (and a new enough Android for the fingerprint prompt). */
    fun available(ctx: Context) = Build.VERSION.SDK_INT >= 28 &&
        (ctx.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isDeviceSecure == true
    private fun needed(ctx: Context) = !unlocked && enabled(ctx) && available(ctx)

    fun install(app: Application) = app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(a: Activity) {
            if (started == 0 && unlocked && leftAt > 0 && System.currentTimeMillis() - leftAt > RELOCK_AFTER_MS) unlocked = false
            started++
        }
        override fun onActivityStopped(a: Activity) { started = (started - 1).coerceAtLeast(0); if (started == 0) leftAt = System.currentTimeMillis() }
        override fun onActivityResumed(a: Activity) { if (needed(a)) cover(a) else uncover(a) }
        override fun onActivityCreated(a: Activity, b: Bundle?) {}
        override fun onActivityPaused(a: Activity) {}
        override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
        override fun onActivityDestroyed(a: Activity) {}
    })

    /** Hide the screen behind a lock panel and ask. */
    private fun cover(a: Activity) {
        val root = a.window.decorView as? ViewGroup ?: return
        if (root.findViewWithTag<View>(TAG) == null) {
            val dp = { v: Int -> RunUi.dp(a, v) }
            root.addView(FrameLayout(a).apply {
                tag = TAG; setBackgroundColor(RunUi.BG); isClickable = true; isFocusable = true
                addView(LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                    addView(TextView(a).apply { text = "🔒"; textSize = 48f; gravity = Gravity.CENTER })
                    addView(TextView(a).apply { text = "BeyondStride is locked"; textSize = 20f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(6)) })
                    addView(TextView(a).apply { text = "Use your fingerprint or screen lock"; textSize = 14f; setTextColor(RunUi.MUTED); gravity = Gravity.CENTER })
                    addView(RunUi.button(a, "Unlock", RunUi.ACCENT) { ask(a) }, LinearLayout.LayoutParams(dp(200), ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(24) })
                }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        ask(a)
    }
    private fun uncover(a: Activity) { (a.window.decorView as? ViewGroup)?.let { r -> r.findViewWithTag<View>(TAG)?.let { r.removeView(it) } } }

    private fun ask(a: Activity) {
        if (prompting || Build.VERSION.SDK_INT < 28) return
        prompting = true
        val b = BiometricPrompt.Builder(a).setTitle("Unlock BeyondStride").setSubtitle("Use your fingerprint or screen lock")
        if (Build.VERSION.SDK_INT >= 30) b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        else @Suppress("DEPRECATION") b.setDeviceCredentialAllowed(true)
        try {
            b.build().authenticate(CancellationSignal(), a.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult?) { prompting = false; unlocked = true; uncover(a) }
                override fun onAuthenticationError(code: Int, msg: CharSequence?) { prompting = false }   // stays locked: tap Unlock to try again
            })
        } catch (e: Exception) { prompting = false; unlocked = true; uncover(a) }   // never lock you out because the prompt itself failed
    }
}
