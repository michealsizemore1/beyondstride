package io.github.michealsizemore1.beyondstride

import kotlin.math.abs

/**
 * Meal ideas at 20% protein / 60% carbs / 20% fat — the Command Center's eight ready-made meals, plus
 * combinations of one or two of your own foods that come closest to that ratio.
 */
object MealIdeas {
    data class Part(val name: String, val amount: String, val cal: Double, val prot: Double, val carbs: Double, val fat: Double)
    data class Idea(val id: String, val meal: String, val label: String, val parts: List<Part>) {
        val kcal get() = parts.sumOf { it.cal }; val prot get() = parts.sumOf { it.prot }
        val carbs get() = parts.sumOf { it.carbs }; val fat get() = parts.sumOf { it.fat }
        fun items(meal: String = this.meal) = parts.map { Food.Item(Food.newId(), meal, it.name, it.amount, 1.0, it.cal, it.prot, it.fat, it.carbs) }
    }

    val READY = listOf(
        Idea("mm1", "breakfast", "Oats with Egg Whites & Almond Butter", listOf(
            Part("Rolled oats (dry)", "89g (~1 cup)", 351.0, 15.0, 59.0, 6.1),
            Part("Liquid egg whites", "37g (~2½ tbsp)", 18.0, 4.0, 0.3, 0.1),
            Part("Almond butter", "5g (~1 tsp)", 33.0, 1.1, 1.0, 2.8))),
        Idea("mm2", "breakfast", "Peanut Butter Banana Toast", listOf(
            Part("Whole wheat bread", "144g (~2½ slices)", 355.0, 18.7, 59.0, 4.9),
            Part("Peanut butter", "9g (~2 tsp)", 57.0, 2.3, 1.8, 4.5),
            Part("Banana", "10g (a couple thin slices)", 10.0, 0.1, 2.3, 0.0))),
        Idea("mm3", "lunch", "Chicken, Rice & Olive Oil Bowl", listOf(
            Part("Brown rice (cooked)", "351g (~1¾ cups)", 395.0, 9.1, 82.5, 3.2),
            Part("Chicken breast (cooked)", "59g (~2 oz)", 92.0, 18.3, 0.0, 2.1),
            Part("Olive oil", "7g (~1½ tsp)", 63.0, 0.0, 0.0, 7.0))),
        Idea("mm4", "lunch", "Turkey, Sweet Potato & Avocado Plate", listOf(
            Part("Sweet potato (baked)", "368g (~1½ medium)", 339.0, 7.4, 76.2, 0.6),
            Part("Turkey breast (cooked)", "62g (~2 oz)", 80.0, 18.6, 0.0, 0.6),
            Part("Avocado", "75g (~half a medium)", 131.0, 1.5, 6.4, 11.0))),
        Idea("mm5", "dinner", "Salmon, Quinoa & Broccoli", listOf(
            Part("Quinoa (cooked)", "335g (~1⅔ cups)", 402.0, 14.7, 71.4, 6.4),
            Part("Salmon (cooked)", "44g (~1½ oz)", 89.0, 9.0, 0.0, 5.9),
            Part("Broccoli (cooked)", "260g (~2 cups)", 109.0, 6.2, 18.7, 1.0))),
        Idea("mm6", "dinner", "Lentil & Rice Bowl", listOf(
            Part("Brown rice (cooked)", "121g (~0.6 cup)", 136.0, 3.1, 28.4, 1.1),
            Part("Lentils (cooked)", "271g (~1⅓ cups)", 324.0, 24.4, 54.2, 1.1),
            Part("Olive oil", "10g (~2 tsp)", 90.0, 0.0, 0.0, 10.0))),
        Idea("mm7", "snack", "Apple, Cottage Cheese & Almonds", listOf(
            Part("Apple", "212g (~1 large)", 123.0, 0.6, 29.3, 0.4),
            Part("Nonfat cottage cheese", "69g (~1/3 cup)", 44.0, 8.6, 1.9, 0.2),
            Part("Almonds", "9g (~7 almonds)", 56.0, 1.9, 1.9, 4.5))),
        Idea("mm8", "snack", "Blueberries, Cottage Cheese & Walnuts", listOf(
            Part("Blueberries", "209g (~1⅓ cups)", 133.0, 1.5, 30.3, 0.6),
            Part("Nonfat cottage cheese", "69g (~1/3 cup)", 44.0, 8.6, 1.9, 0.2),
            Part("Walnuts", "6g (~1½ walnuts, chopped)", 42.0, 0.9, 0.8, 3.9)))
    )

    fun pct(kcal: Double, prot: Double, carbs: Double, fat: Double): Triple<Int, Int, Int> =
        if (kcal <= 0) Triple(0, 0, 0) else Triple(Math.round(prot * 4 / kcal * 100).toInt(), Math.round(carbs * 4 / kcal * 100).toInt(), Math.round(fat * 9 / kcal * 100).toInt())

    private fun dev(p: Double, c: Double, f: Double, kcal: Double): Double {
        if (kcal <= 0) return Double.MAX_VALUE
        return abs(p * 4 / kcal * 100 - 20) + abs(c * 4 / kcal * 100 - 60) + abs(f * 9 / kcal * 100 - 20)
    }

    /** Up to six 1–2 food combinations from [pool] (your foods) closest to 20/60/20, 150–900 kcal. */
    fun personal(pool: List<Food.Item>): List<List<Food.Item>> {
        val foods = pool.filter { (it.cal ?: 0.0) > 0 && it.prot != null && it.carbs != null && it.fat != null }.take(300)
        data class R(val items: List<Food.Item>, val dev: Double)
        val res = ArrayList<R>()
        fun add(list: List<Food.Item>) {
            val k = list.sumOf { it.cal!! }; if (k < 150 || k > 900) return
            res += R(list, dev(list.sumOf { it.prot!! }, list.sumOf { it.carbs!! }, list.sumOf { it.fat!! }, k))
        }
        foods.forEach { add(listOf(it)) }
        for (i in foods.indices) for (j in i + 1 until foods.size) add(listOf(foods[i], foods[j]))
        val seen = HashSet<String>()
        return res.sortedBy { it.dev }.filter { seen.add(it.items.map { f -> f.name.lowercase() }.sorted().joinToString("|")) }.take(6).map { it.items }
    }
}
