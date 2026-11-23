package io.github.michealsizemore1.beyondstride.run

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService

/**
 * 🎧 What Audible is playing (title, position, length), read the same way the lock screen's media controls do.
 * Android only shares this with an app you've allowed under "Notification access"; BeyondStride only looks at the
 * Audible player and keeps nothing. Phone only.
 */
object AudibleNow {
    const val PKG = "com.audible.application"
    class Info(val title: String, val posMs: Long, val durMs: Long, val playing: Boolean)

    fun allowed(ctx: Context): Boolean {
        val s = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
        return s.split(':').any { ComponentName.unflattenFromString(it)?.packageName == ctx.packageName }
    }
    fun askIntent() = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun read(ctx: Context): Info? = try {
        if (!allowed(ctx)) null else {
            val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val c = msm.getActiveSessions(ComponentName(ctx, AudibleListener::class.java)).firstOrNull { it.packageName == PKG }
            val md = c?.metadata; val st = c?.playbackState
            if (c == null || md == null || st == null) null else {
                val dur = md.getLong(MediaMetadata.METADATA_KEY_DURATION)
                val playing = st.state == PlaybackState.STATE_PLAYING
                var pos = st.position
                if (playing && st.lastPositionUpdateTime > 0) pos += ((SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
                val title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: "Audible"
                Info(title, pos.coerceIn(0, if (dur > 0) dur else Long.MAX_VALUE), dur, playing)
            }
        }
    } catch (e: Exception) { null }

    fun clock(ms: Long): String { val s = ms / 1000; return if (s >= 3600) String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) else String.format("%02d:%02d", s / 60, s % 60) }
}

/** Needed only so Android lets BeyondStride see Audible's player (Settings → Notification access). Does nothing else. */
class AudibleListener : NotificationListenerService()
