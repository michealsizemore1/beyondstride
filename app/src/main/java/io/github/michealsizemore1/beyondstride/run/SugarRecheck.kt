package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import io.github.michealsizemore1.beyondstride.Food
import io.github.michealsizemore1.beyondstride.Nutrition
import io.github.michealsizemore1.beyondstride.Readiness

/**
 * One time (Nov 24 2026): natural sugar now covers vegetables too and is judged more carefully (Food.naturalSugar).
 * Re-adds up every day already logged so past days' added-sugar max, micronutrients and score use the new rule, then
 * re-sends the daily summaries so Nutrition Monthly/Yearly match. Foods logged from now on use the rule as they're added.
 */
object SugarRecheck {
    fun run(ctx: Context) {
        val pr = ctx.getSharedPreferences("nutfix", 0); if (pr.getBoolean("sugarNat1", false)) return
        val all = NutriDay.allJson(ctx)
        val days = try { Nutrition.parse(all.toString()) } catch (e: Exception) { emptyList() }
        val redone = days.filter { it.foods.isNotEmpty() }.map { Food.summarize(it, it.foods) }
        if (redone.isNotEmpty()) NutriDay.putAll(ctx, redone)
        pr.edit().putBoolean("sugarNat1", true).apply()
        try { Local.refill(ctx, Readiness.today()) } catch (e: Exception) { }   // refreshes the last 3 weeks the screens show
        DailySummary.resendAll(ctx)
    }
}
