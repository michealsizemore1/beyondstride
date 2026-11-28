package io.github.michealsizemore1.beyondstride

import android.content.Context

/**
 * Screen look. Background: a lighter grey-green so the dark tiles stand out, or the original near-black.
 *
 * 🎨 Seasonal themes (Nov 27 2026; Settings → Theme, phone and watch): Halloween, Thanksgiving, Christmas, New Year's,
 * Winter, Spring and 4th of July — the themes the old Command Center had (its "Original" was left out on purpose).
 * Each has its own background, tiles, accent, a colored tile edge and bottom bar, and (phone only) a falling effect.
 * "Off" brings back the usual colors exactly.
 */
object Theme {
    const val LIGHT_BG = 0xFF48544F.toInt()
    const val DARK_BG = 0xFF121A17.toInt()
    const val ACCENT = 0xFFD4E635.toInt()
    const val CARD = 0xFF1C2421.toInt()

    /** Falling effect: dots in these colors (snow, confetti, sparks) and/or a few emoji (leaves, petals, bats). */
    class Fx(val dots: IntArray, val emoji: List<String> = emptyList(), val count: Int = 45, val speed: Float = 1f, val up: Boolean = false)
    class Palette(val id: String, val label: String, val emoji: String, val lightBg: Int, val darkBg: Int, val card: Int, val accent: Int,
                  val edge: Int, val bar: Int, val line: Int, val fx: Fx)

    val PALETTES: List<Palette> = listOf(
        Palette("halloween", "Halloween", "🎃", 0xFF2E1A36.toInt(), 0xFF140B18.toInt(), 0xFF1C1022.toInt(), 0xFFFF8A1F.toInt(),
            0xB3FF6B00.toInt(), 0xFF2A0F33.toInt(), 0xFFFF6B00.toInt(), Fx(intArrayOf(0xFFFF8A1F.toInt(), 0xFFB45CFF.toInt()), listOf("🦇"), 22, 0.6f, up = true)),
        Palette("thanksgiving", "Thanksgiving", "🦃", 0xFF4A2C17.toInt(), 0xFF22140A.toInt(), 0xFF2B1A0E.toInt(), 0xFFF5A524.toInt(),
            0xB3D97706.toInt(), 0xFF3A1F0C.toInt(), 0xFFD97706.toInt(), Fx(intArrayOf(), listOf("🍂", "🍁", "🍂"), 14, 0.7f)),
        Palette("christmas", "Christmas", "🎄", 0xFF1F4A33.toInt(), 0xFF0D2016.toInt(), 0xFF13261B.toInt(), 0xFFF4C542.toInt(),
            0xB3B3262E.toInt(), 0xFF3B0F15.toInt(), 0xFFB3262E.toInt(), Fx(intArrayOf(0xFFFFFFFF.toInt()))),
        Palette("newyear", "New Year's", "🎉", 0xFF22223D.toInt(), 0xFF0B0B16.toInt(), 0xFF151528.toInt(), 0xFFFFC83D.toInt(),
            0xB3D4AF37.toInt(), 0xFF15152B.toInt(), 0xFFD4AF37.toInt(), Fx(intArrayOf(0xFFFFC83D.toInt(), 0xFFE5E7EB.toInt(), 0xFFF472B6.toInt(), 0xFF60A5FA.toInt()), count = 55, speed = 1.3f)),
        Palette("winter", "Winter", "❄️", 0xFF1E3A52.toInt(), 0xFF0B1A26.toInt(), 0xFF132433.toInt(), 0xFF8AD8FF.toInt(),
            0x99BAE6FD.toInt(), 0xFF0C2638.toInt(), 0xFF7DD3FC.toInt(), Fx(intArrayOf(0xFFFFFFFF.toInt(), 0xFFE0F2FE.toInt()), count = 60)),
        Palette("spring", "Spring", "🌸", 0xFF2F5A3A.toInt(), 0xFF13261A.toInt(), 0xFF1A2E20.toInt(), 0xFFF9A8D4.toInt(),
            0x99F472B6.toInt(), 0xFF1D3A26.toInt(), 0xFFF472B6.toInt(), Fx(intArrayOf(0xFFFBCFE8.toInt()), listOf("🌸"), 24, 0.6f)),
        Palette("july4", "4th of July", "🎆", 0xFF1E2A4A.toInt(), 0xFF0D1428.toInt(), 0xFF151E38.toInt(), 0xFFFF7A7A.toInt(),
            0xB3DC2626.toInt(), 0xFF7F1D1D.toInt(), 0xFFFFFFFF.toInt(), Fx(intArrayOf(0xFFEF4444.toInt(), 0xFFFFFFFF.toInt(), 0xFF3B82F6.toInt()), count = 40, speed = 0.9f)))

    @Volatile var light = true
    /** The seasonal theme in use, or null for the usual colors. */
    @Volatile var season: Palette? = null
    /** The falling effect (phone only). */
    @Volatile var effects = true
    val xmas: Boolean get() = season != null   // any seasonal theme is on (name kept from the first, Christmas-only version)
    val bg: Int get() = season?.let { if (light) it.lightBg else it.darkBg } ?: if (light) LIGHT_BG else DARK_BG
    val accent: Int get() = season?.accent ?: ACCENT
    val card: Int get() = season?.card ?: CARD
    fun byId(id: String?) = PALETTES.firstOrNull { it.id == id }

    fun load(ctx: Context, prefsName: String) {
        val p = ctx.getSharedPreferences(prefsName, 0)
        light = p.getString("bgStyle", "light") != "dark"
        // The first version only had Christmas ("xmas" on/off): carry that over.
        val id = p.getString("season", null) ?: if (p.getBoolean("xmas", false)) "christmas" else ""
        season = byId(id)
        effects = p.getBoolean("xmasSnow", true)
    }
}
