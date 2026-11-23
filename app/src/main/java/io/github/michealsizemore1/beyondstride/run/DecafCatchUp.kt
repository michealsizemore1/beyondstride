package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import io.github.michealsizemore1.beyondstride.Food

/**
 * One time (Nov 22 2026): the Caramel Brulée is always decaf, so it counts toward liquids since build 354. Fluids are
 * added up when a food is logged, so every Caramel Brulée logged before then was never counted: add those ounces now.
 */
object DecafCatchUp {
    private const val CUTOFF = 1795371600000L   // Nov 22 2026, 11:20 AM Mountain (build 354 went out); later ones already count
    private val BRULEE = Regex("caramel\\s*br[uû]l[eé]e?", RegexOption.IGNORE_CASE)

    private const val COCOA_CUTOFF = 1795399200000L   // Nov 22 2026, 7 PM Mountain: hot chocolate counts from here on

    fun run(ctx: Context) { decaf(ctx); cocoa(ctx) }

    /** One time: hot chocolate (Swiss Miss) logged before it counted toward liquids — 10 oz a packet. */
    private fun cocoa(ctx: Context) {
        val pr = ctx.getSharedPreferences("hydration", 0); if (pr.getBoolean("cocoaFix1", false)) return
        pr.edit().putBoolean("cocoaFix1", true).apply()
        val all = NutriDay.allJson(ctx)
        all.keys().asSequence().toList().forEach { date ->
            val a = all.optJSONObject(date)?.optJSONArray("foods") ?: return@forEach
            var oz = 0.0
            for (k in 0 until a.length()) { val f = a.optJSONObject(k) ?: continue
                if (f.optString("meal") != "liquids" || !Food.HOT_COCOA.containsMatchIn(f.optString("name"))) continue
                if (Food.fluidOz(f.optString("servingLabel")) > 0) continue      // had ounces in its serving: may have counted already
                val at = Regex("^f(\\d{12,})").find(f.optString("id"))?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                if (at >= COCOA_CUTOFF) continue
                oz += Food.COCOA_OZ * (f.optDouble("servings", 1.0).takeIf { !it.isNaN() && it > 0 } ?: 1.0) }
            if (oz >= 1) HydrationSync.change(ctx, "other", Math.round(oz).toInt(), date = date)
        }
    }

    private fun decaf(ctx: Context) {
        val pr = ctx.getSharedPreferences("hydration", 0); if (pr.getBoolean("decafFix2", false)) return
        pr.edit().putBoolean("decafFix2", true).apply()
        val all = NutriDay.allJson(ctx)
        all.keys().asSequence().toList().forEach { date ->
            val a = all.optJSONObject(date)?.optJSONArray("foods") ?: return@forEach
            var oz = 0.0
            for (k in 0 until a.length()) { val f = a.optJSONObject(k) ?: continue
                if (f.optString("meal") != "liquids" || !BRULEE.containsMatchIn(f.optString("name"))) continue
                val at = Regex("^f(\\d{12,})").find(f.optString("id"))?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                if (at >= CUTOFF) continue
                oz += Food.fluidOz(f.optString("servingLabel")) * (f.optDouble("servings", 1.0).takeIf { !it.isNaN() && it > 0 } ?: 1.0) }
            if (oz >= 1) HydrationSync.change(ctx, "other", Math.round(oz).toInt(), date = date)
        }
    }
}
