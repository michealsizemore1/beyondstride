package io.github.michealsizemore1.beyondstride.run

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.LocationManager
import android.view.Gravity
import android.widget.LinearLayout
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Weather widget for the Today screen (Open-Meteo, free, no key; refreshed every 20 minutes). */
object TodayWeather {
    private fun p(ctx: Context) = ctx.getSharedPreferences("todayweather", 0)

    @SuppressLint("MissingPermission")
    private fun where(ctx: Context): DoubleArray {
        if (ctx.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) try {
            val lm = ctx.getSystemService(LocationManager::class.java)
            lm.getProviders(true).mapNotNull { try { lm.getLastKnownLocation(it) } catch (e: Exception) { null } }.maxByOrNull { it.time }
                ?.let { return doubleArrayOf(it.latitude, it.longitude) }
        } catch (e: Exception) { }
        return doubleArrayOf(43.6121, -116.3915)   // Meridian, ID
    }

    @Suppress("DEPRECATION")
    private fun place(ctx: Context, la: Double, lo: Double): String = try {
        android.location.Geocoder(ctx, Locale.US).getFromLocation(la, lo, 1)?.firstOrNull()?.let { it.locality ?: it.subAdminArea } ?: "Meridian"
    } catch (e: Exception) { p(ctx).getString("place", "Meridian") ?: "Meridian" }

    fun stale(ctx: Context) = System.currentTimeMillis() - p(ctx).getLong("at", 0L) > 20 * 60_000L

    /** Fetches the forecast (call off the main thread). Returns true when new data arrived. */
    fun fetch(ctx: Context, force: Boolean = false): Boolean {
        if (!force && !stale(ctx)) return false
        return try {
            val (la, lo) = where(ctx).let { it[0] to it[1] }
            val c = (URL("https://api.open-meteo.com/v1/forecast?latitude=$la&longitude=$lo" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m,dew_point_2m,wind_speed_10m,wind_gusts_10m,weather_code,is_day" +
                "&hourly=temperature_2m,precipitation_probability,weather_code,is_day" +
                "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset,uv_index_max" +
                "&temperature_unit=fahrenheit&wind_speed_unit=mph&timezone=auto&forecast_days=2").openConnection() as HttpURLConnection)
                .apply { connectTimeout = 12_000; readTimeout = 15_000 }
            if (c.responseCode != 200) return false
            val body = c.inputStream.bufferedReader().readText()
            p(ctx).edit().putString("wx", body).putLong("at", System.currentTimeMillis()).putString("place", place(ctx, la, lo)).apply()
            true
        } catch (e: Exception) { false }
    }

    fun icon(code: Int, day: Boolean = true) = when (code) {
        0 -> if (day) "☀️" else "🌙"; 1 -> if (day) "🌤️" else "🌙"; 2 -> "⛅"; 3 -> "☁️"
        45, 48 -> "🌫️"; in 51..57 -> "🌦️"; in 61..67, in 80..82 -> "🌧️"
        in 71..77, 85, 86 -> "🌨️"; in 95..99 -> "⛈️"; else -> "🌡️"
    }
    private fun word(code: Int) = when (code) {
        0 -> "Clear"; 1 -> "Mostly clear"; 2 -> "Partly cloudy"; 3 -> "Cloudy"; 45, 48 -> "Fog"
        in 51..57 -> "Drizzle"; in 61..67 -> "Rain"; in 71..77 -> "Snow"; in 80..82 -> "Showers"; 85, 86 -> "Snow showers"
        in 95..99 -> "Thunderstorms"; else -> ""
    }
    private fun r(d: Double) = Math.round(d).toString()
    private fun clock(iso: String) = try { java.time.LocalDateTime.parse(iso).format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", Locale.US)) } catch (e: Exception) { "" }

    /** "Tomorrow 7–10 AM: 34–41°, ☀️ Clear, 10% rain" from the cached forecast. */
    fun tomorrowMorning(ctx: Context): String? = try {
        val o = JSONObject(p(ctx).getString("wx", "") ?: ""); val h = o.getJSONObject("hourly")
        val t = h.getJSONArray("time"); val tomorrow = java.time.LocalDate.now().plusDays(1).toString()
        val idx = (0 until t.length()).filter { t.optString(it).startsWith(tomorrow) && t.optString(it).substring(11, 13).toInt() in 7..10 }
        if (idx.isEmpty()) null else {
            val temps = idx.map { h.getJSONArray("temperature_2m").optDouble(it) }
            val rain = idx.maxOf { h.optJSONArray("precipitation_probability")?.optInt(it) ?: 0 }
            val code = h.getJSONArray("weather_code").optInt(idx[idx.size / 2])
            "Tomorrow 7–10 AM: ${r(temps.min())}–${r(temps.max())}°, ${icon(code)} ${word(code)}" + if (rain >= 20) ", $rain% rain" else ""
        }
    } catch (e: Exception) { null }

    /** Fills [card] with the cached weather. */
    fun fill(ctx: Context, card: LinearLayout) {
        card.removeAllViews()
        val dp = { v: Int -> RunUi.dp(ctx, v) }
        val o = try { JSONObject(p(ctx).getString("wx", "") ?: "") } catch (e: Exception) { null }
        val cur = o?.optJSONObject("current")
        if (cur == null) {
            card.addView(RunUi.text(ctx, "🌡️ Weather — loading… (tap to refresh)", RunUi.MUTED, 14f, true)); return
        }
        val code = cur.optInt("weather_code"); val day = cur.optInt("is_day", 1) == 1
        val t = cur.optDouble("temperature_2m"); val fl = cur.optDouble("apparent_temperature")
        val dew = cur.optDouble("dew_point_2m"); val rh = cur.optDouble("relative_humidity_2m")
        val wind = cur.optDouble("wind_speed_10m"); val gust = cur.optDouble("wind_gusts_10m")
        val d = o.optJSONObject("daily")
        val hi = d?.optJSONArray("temperature_2m_max")?.optDouble(0); val lo = d?.optJSONArray("temperature_2m_min")?.optDouble(0)
        val rain = d?.optJSONArray("precipitation_probability_max")?.optInt(0) ?: 0
        val place = p(ctx).getString("place", "Meridian")

        // Big line: icon · temp · place.
        card.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(RunUi.text(ctx, icon(code, day), Color.WHITE, 34f).apply { setPadding(0, 0, dp(10), 0) })
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(RunUi.text(ctx, "${r(t)}°  $place", Color.WHITE, 22f, true))
                addView(RunUi.text(ctx, listOfNotNull(word(code).ifBlank { null },
                    if (Math.abs(fl - t) >= 3) "feels ${r(fl)}°" else null,
                    if (hi != null && lo != null) "H ${r(hi)}° · L ${r(lo)}°" else null).joinToString(" · "), RunUi.MUTED, 13f))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(RunUi.text(ctx, "☔ $rain%", if (rain >= 40) 0xFF7FB8FF.toInt() else RunUi.MUTED, 14f, true))
        })
        // Details.
        card.addView(RunUi.text(ctx, "Wind ${r(wind)} mph" + (if (gust - wind >= 8) " (gusts ${r(gust)})" else "") +
            " · Dew pt ${r(dew)}° · Humidity ${r(rh)}%", RunUi.MUTED, 12f).apply { setPadding(0, dp(6), 0, 0) })
        val sun = d?.optJSONArray("sunrise")?.optString(0)?.let(::clock).orEmpty(); val set = d?.optJSONArray("sunset")?.optString(0)?.let(::clock).orEmpty()
        val uv = d?.optJSONArray("uv_index_max")?.optDouble(0) ?: 0.0
        if (sun.isNotBlank()) card.addView(RunUi.text(ctx, "🌅 $sun  ·  🌇 $set" + (if (uv >= 3) "  ·  UV ${r(uv)}" else ""), RunUi.MUTED, 12f))

        // Next hours.
        val h = o.optJSONObject("hourly")
        val times = h?.optJSONArray("time"); val ht = h?.optJSONArray("temperature_2m"); val hc = h?.optJSONArray("weather_code")
        val hp = h?.optJSONArray("precipitation_probability"); val hd = h?.optJSONArray("is_day")
        if (times != null && ht != null) {
            val nowKey = java.time.LocalDateTime.now().withMinute(0).withSecond(0).withNano(0).toString().take(13)
            var start = (0 until times.length()).indexOfFirst { times.optString(it).take(13) >= nowKey }
            if (start < 0) start = 0
            card.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0)
                for (i in start + 1 until Math.min(times.length(), start + 7)) {
                    val lbl = try { java.time.LocalDateTime.parse(times.optString(i)).format(java.time.format.DateTimeFormatter.ofPattern("ha", Locale.US)).lowercase().dropLast(1) } catch (e: Exception) { "" }
                    addView(LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                        addView(RunUi.text(ctx, lbl, RunUi.MUTED, 11f))
                        addView(RunUi.text(ctx, icon(hc?.optInt(i) ?: 0, (hd?.optInt(i) ?: 1) == 1), Color.WHITE, 17f))
                        addView(RunUi.text(ctx, "${r(ht.optDouble(i))}°", Color.WHITE, 13f, true))
                        val pp = hp?.optInt(i) ?: 0
                        addView(RunUi.text(ctx, if (pp >= 20) "$pp%" else " ", 0xFF7FB8FF.toInt(), 10f))
                    }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                }
            })
        }
        // Running note.
        val heat = io.github.michealsizemore1.beyondstride.Heat
        val note = when {
            t >= 60 -> try { heat.pct(t, rh).takeIf { it >= 1 }?.let { "🏃 Heat now: ~${String.format(Locale.US, "%.1f", it)}% slower at the same effort" } } catch (e: Exception) { null }
            fl <= 25 -> "🏃 Cold — layer up, cover hands and ears"
            rain >= 60 -> "🏃 Rain likely — pick your window or plan for wet roads"
            else -> null
        }
        note?.let { card.addView(RunUi.text(ctx, it, RunUi.ACCENT, 12f, true).apply { setPadding(0, dp(8), 0, 0) }) }
        val mins = (System.currentTimeMillis() - p(ctx).getLong("at", 0L)) / 60_000
        card.addView(RunUi.text(ctx, "Updated ${if (mins < 1) "just now" else "$mins min ago"} · tap to refresh", 0xFF6F7A76.toInt(), 10f).apply { setPadding(0, dp(6), 0, 0) })
    }
}
