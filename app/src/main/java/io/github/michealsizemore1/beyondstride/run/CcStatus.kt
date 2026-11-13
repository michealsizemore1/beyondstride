package io.github.michealsizemore1.beyondstride.run

import android.content.Context

/** "Stand-alone · cloud copy 5 min ago": the status line under the app's screens. */
object CcStatus {
    /** The line and its color (green when the cloud copy is recent, yellow when it's off or not made yet). */
    fun line(ctx: Context): Pair<String, Int> {
        val at = CloudCopy.lastAt(ctx)
        val t = if (!CloudCopy.ready(ctx)) "Stand-alone · cloud copy off (set up the database in LiveTrack settings)"
            else if (at <= 0) "Stand-alone · cloud copy not made yet" else "Stand-alone · cloud copy " + android.text.format.DateUtils.getRelativeTimeSpanString(at)
        return t to (if (CloudCopy.ready(ctx) && at > 0) 0xFF5FD38D.toInt() else 0xFFF2C94C.toInt())
    }
}
