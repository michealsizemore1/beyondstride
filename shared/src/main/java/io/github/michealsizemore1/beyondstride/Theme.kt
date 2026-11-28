package io.github.michealsizemore1.beyondstride

import android.content.Context

/**
 * Screen look. Background: a lighter grey-green so the dark tiles stand out, or the original near-black.
 * 🎄 Christmas theme (Nov 27 2026, Settings → Christmas theme, phone and watch): evergreen background and tiles,
 * gold accent, red trim; the phone can add falling snow. Turning it off brings back the usual colors exactly.
 */
object Theme {
    const val LIGHT_BG = 0xFF48544F.toInt()
    const val DARK_BG = 0xFF121A17.toInt()
    const val XMAS_LIGHT_BG = 0xFF1F4A33.toInt()   // pine
    const val XMAS_DARK_BG = 0xFF0D2016.toInt()    // deep evergreen
    const val ACCENT = 0xFFD4E635.toInt()
    const val CARD = 0xFF1C2421.toInt()
    const val XMAS_GOLD = 0xFFF4C542.toInt()
    const val XMAS_CARD = 0xFF13261B.toInt()
    const val XMAS_RED = 0xFFE5484D.toInt()
    @Volatile var light = true
    @Volatile var xmas = false
    @Volatile var snow = true
    val bg: Int get() = if (xmas) (if (light) XMAS_LIGHT_BG else XMAS_DARK_BG) else if (light) LIGHT_BG else DARK_BG
    val accent: Int get() = if (xmas) XMAS_GOLD else ACCENT
    val card: Int get() = if (xmas) XMAS_CARD else CARD
    fun load(ctx: Context, prefsName: String) {
        val p = ctx.getSharedPreferences(prefsName, 0)
        light = p.getString("bgStyle", "light") != "dark"
        xmas = p.getBoolean("xmas", false)
        snow = p.getBoolean("xmasSnow", true)
    }
}
