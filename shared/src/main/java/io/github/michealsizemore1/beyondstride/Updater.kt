package io.github.michealsizemore1.beyondstride

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app updates (phone and watch). Checks the latest GitHub release of BeyondStride, downloads this
 * device's file (BeyondStride-PHONE.apk or BeyondStride-WATCH.apk) and hands it to Android's installer.
 * Android always asks once; after BeyondStride has installed itself the first time, later updates can
 * go through with no extra screen (Android 12+ lets an app update itself).
 */
object Updater {
    private const val API = "https://api.github.com/repos/michealsizemore1/beyondstride/releases/latest"
    data class Release(val build: Int, val url: String, val notes: String)

    private fun p(ctx: Context) = ctx.getSharedPreferences("updater", 0)

    /** This app's build number (phone 1000000+N, watch 1100000+N). */
    fun currentBuild(ctx: Context): Int = try {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
        (code % 100_000).toInt()
    } catch (e: Exception) { 0 }

    /** The newer release found by the last check, or null if up to date. */
    fun available(ctx: Context): Release? {
        val b = p(ctx).getInt("build", 0); val u = p(ctx).getString("url", null) ?: return null
        return if (b > currentBuild(ctx)) Release(b, u, p(ctx).getString("notes", "") ?: "") else null
    }

    /** Asks GitHub for the latest build (at most every [minGapMs]). Call off the main thread. */
    fun check(ctx: Context, isWatch: Boolean, minGapMs: Long = 3 * 3_600_000L): Release? {
        val sp = p(ctx)
        if (System.currentTimeMillis() - sp.getLong("checked", 0) < minGapMs) return available(ctx)
        try {
            val c = (URL(API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000; readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json"); setRequestProperty("User-Agent", "BeyondStride-app")
            }
            if (c.responseCode != 200) return available(ctx)
            val o = JSONObject(c.inputStream.bufferedReader().readText())
            val build = o.optString("tag_name").removePrefix("build-").toIntOrNull() ?: return available(ctx)
            val want = if (isWatch) "BeyondStride-WATCH.apk" else "BeyondStride-PHONE.apk"
            val assets = o.optJSONArray("assets")
            var url: String? = null
            if (assets != null) for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name") == want) url = a.optString("browser_download_url")
            }
            sp.edit().putLong("checked", System.currentTimeMillis()).putInt("build", build)
                .putString("url", url).putString("notes", o.optString("name")).apply()
        } catch (e: Exception) { }
        return available(ctx)
    }

    /** Can this app hand files to the installer? (Android's "Install unknown apps" switch for BeyondStride.) */
    fun canInstall(ctx: Context) = Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    /**
     * The whole tap flow for an Install button: asks for the "Install unknown apps" switch the first time,
     * otherwise downloads and installs, reporting progress through [status] (on the main thread).
     */
    fun start(act: android.app.Activity, r: Release, status: (String) -> Unit) {
        if (!canInstall(act)) {
            status("Turn on \"Allow from this source\", then come back and tap Install again")
            try { act.startActivity(permissionIntent(act)) } catch (e: Exception) {
                try { act.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + act.packageName))) } catch (e2: Exception) { }
            }
            return
        }
        status("Downloading build ${r.build}…")
        Thread {
            var last = -1
            val err = install(act, r) { pct -> if (pct / 5 != last) { last = pct / 5; act.runOnUiThread { status("Downloading build ${r.build}… $pct%") } } }
            act.runOnUiThread { status(err ?: "Installing build ${r.build}… BeyondStride will close and reopen updated") }
        }.start()
    }

    const val FACE_PKG = "io.github.michealsizemore1.beyondstride.watchface"
    fun faceInstalled(ctx: Context) = try { ctx.packageManager.getPackageInfo(FACE_PKG, 0); true } catch (e: Exception) { false }
    /** Downloads the latest BeyondStride watch face (BeyondStride-FACE.apk) and installs it. Returns an error or null. Call off the main thread. */
    fun installFace(ctx: Context, progress: (Int) -> Unit = {}): String? {
        val url = try {
            val c = (URL(API).openConnection() as HttpURLConnection).apply { connectTimeout = 12_000; readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json"); setRequestProperty("User-Agent", "BeyondStride-app") }
            val a = JSONObject(c.inputStream.bufferedReader().readText()).optJSONArray("assets")
            (0 until (a?.length() ?: 0)).map { a!!.getJSONObject(it) }.firstOrNull { it.optString("name") == "BeyondStride-FACE.apk" }?.optString("browser_download_url")
        } catch (e: Exception) { null } ?: return "Couldn't find the watch face download — check the watch is online."
        return install(ctx, Release(0, url, ""), progress, FACE_PKG, 10_000)
    }

    /** Forget the last check so the next [check] asks GitHub right away. */
    fun forceNext(ctx: Context) = p(ctx).edit().putLong("checked", 0).apply()
    fun lastChecked(ctx: Context) = p(ctx).getLong("checked", 0)

    /** Opens the "Install unknown apps" switch for BeyondStride. */
    fun permissionIntent(ctx: Context) =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Downloads and installs [r]. [progress] gets 0–100; returns an error message or null when handed to Android.
     * Call off the main thread.
     */
    fun install(ctx: Context, r: Release, progress: (Int) -> Unit = {}, pkg: String = ctx.packageName, minBytes: Long = 100_000): String? {
        val file = File(ctx.cacheDir, "update-${r.build}.apk")
        try {
            val c = (URL(r.url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true; connectTimeout = 15_000; readTimeout = 30_000
                setRequestProperty("User-Agent", "BeyondStride-app")
            }
            val total = c.contentLengthLong
            c.inputStream.use { ins -> file.outputStream().use { out ->
                val buf = ByteArray(64 * 1024); var done = 0L; var n: Int
                while (ins.read(buf).also { n = it } > 0) { out.write(buf, 0, n); done += n; if (total > 0) progress((done * 100 / total).toInt()) }
            } }
            if (file.length() < minBytes) return "The download looks incomplete — try again."
            val pi = ctx.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(pkg)
                if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val id = pi.createSession(params)
            pi.openSession(id).use { s ->
                s.openWrite("BeyondStride.apk", 0, file.length()).use { out -> file.inputStream().use { it.copyTo(out) }; s.fsync(out) }
                val intent = Intent(ctx, UpdateReceiver::class.java).setPackage(ctx.packageName)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                s.commit(PendingIntent.getBroadcast(ctx, id, intent, flags).intentSender)
            }
            return null
        } catch (e: Exception) {
            return "Update failed: ${e.message ?: e.javaClass.simpleName}"
        } finally {
            // The installer has its own copy once committed.
            file.delete()
        }
    }
}

/** Android's installer reports back here; when it needs your OK, its confirm screen is opened. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    else intent.getParcelableExtra(Intent.EXTRA_INTENT)) ?: return
                ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> { }
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Install didn't finish"
                android.widget.Toast.makeText(ctx, "Update: $msg", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
}
