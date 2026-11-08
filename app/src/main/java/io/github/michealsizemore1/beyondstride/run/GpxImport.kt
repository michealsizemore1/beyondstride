package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import android.net.Uri
import io.github.michealsizemore1.beyondstride.Courses
import io.github.michealsizemore1.beyondstride.Geo
import io.github.michealsizemore1.beyondstride.Trail
import io.github.michealsizemore1.beyondstride.Trails
import org.xmlpull.v1.XmlPullParser

/**
 * A GPX course or route (Garmin Connect, Strava, AllTrails, Komoot…) saved as a course:
 * shows under Saved courses here and on the watch, ready to follow when you press Start.
 */
object GpxImport {
    /** Reads the file and saves it. Returns the saved course, or null with [error] set. */
    var error = ""
    fun import(ctx: Context, uri: Uri): Trail? {
        error = ""
        return try {
            val pts = ArrayList<DoubleArray>(); val ele = ArrayList<Double>(); var name = ""
            ctx.contentResolver.openInputStream(uri)!!.use { ins ->
                val p = android.util.Xml.newPullParser(); p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false); p.setInput(ins, null)
                var inPt = false; var curEle = Double.NaN; var depthName = ""
                var ev = p.eventType
                while (ev != XmlPullParser.END_DOCUMENT) {
                    when (ev) {
                        XmlPullParser.START_TAG -> when (p.name.substringAfter(':')) {
                            "trkpt", "rtept" -> { val la = p.getAttributeValue(null, "lat")?.toDoubleOrNull(); val lo = p.getAttributeValue(null, "lon")?.toDoubleOrNull()
                                if (la != null && lo != null) { pts += doubleArrayOf(la, lo); inPt = true; curEle = Double.NaN } }
                            "ele" -> if (inPt) curEle = p.nextText().trim().toDoubleOrNull() ?: Double.NaN
                            "name" -> if (name.isEmpty() && !inPt) name = p.nextText().trim()
                        }
                        XmlPullParser.END_TAG -> if (p.name.substringAfter(':') in setOf("trkpt", "rtept") && inPt) { ele += curEle; inPt = false }
                    }
                    ev = p.next()
                }
            }
            if (pts.size < 2) { error = "That file has no route points (is it a GPX course?)"; return null }
            val len = Geo.length(pts)
            if (len < 100) { error = "That route is too short"; return null }
            val loop = Geo.dist(pts.first(), pts.last()) < maxOf(100.0, len * 0.03)
            val label = name.ifEmpty { uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Imported course" }.take(60)
            val t = Trail("gpx" + System.currentTimeMillis(), label, Trails.simplify(pts, 5.0), len, loop, "", "")
            Courses.save(ctx, t, "phone")
            t
        } catch (e: Exception) { error = "Couldn't read that file: ${e.message}"; null }
    }
}
