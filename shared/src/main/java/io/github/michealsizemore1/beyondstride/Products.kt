package io.github.michealsizemore1.beyondstride

/**
 * The Command Center's quick-add products (label values, not estimates): sports nutrition
 * (GU, SiS, Premier Protein) and supplements.
 */
object Products {
    data class Product(val brand: String, val line: String, val name: String, val serving: String,
                       val cal: Double, val prot: Double, val fat: Double, val carbs: Double, val micros: Map<String, Double>) {
        fun item(meal: String) = Food.Item(Food.newId(), meal, if (meal == "sports") "$line – $name" else "$brand $name", serving, 1.0,
            cal.takeIf { it > 0 }, prot.takeIf { it > 0 }, fat.takeIf { it > 0 }, carbs.takeIf { it > 0 }, micros)
        /** "1 packet (32g) · 100 cal · 22g carb · 100mg sodium …" */
        fun preview(): String {
            val b = mutableListOf(serving, "${cal.toInt()} cal")
            if (carbs > 0) b += "${Food.fmt(carbs)}g carb"
            micros["sodium"]?.let { b += "${it.toInt()}mg sodium" }; micros["potassium"]?.let { b += "${it.toInt()}mg potassium" }
            micros["caffeine"]?.let { b += "${it.toInt()}mg caffeine" }
            if (prot > 0) b += "${Food.fmt(prot)}g protein"
            micros.filterKeys { it !in listOf("sodium", "potassium", "caffeine") }.forEach { (k, v) -> b += "$k ${Food.fmt(v)}" }
            return b.joinToString(" · ")
        }
    }
    val BRANDS = listOf("" to "All Brands", "GU" to "GU Energy", "SiS" to "SiS", "Premier Protein" to "Premier Protein")

    val SPORTS = listOf(
        Product("GU", "GU Energy Gel", "Sour Peach", "1 packet (32g)", 100.0, 0.0, 0.0, 22.0, mapOf("sodium" to 100.0, "potassium" to 20.0)),
        Product("GU", "GU Energy Gel", "Chocolate Outrage", "1 packet (32g)", 100.0, 0.0, 0.0, 22.0, mapOf("sodium" to 100.0, "potassium" to 20.0, "caffeine" to 20.0)),
        Product("GU", "GU Energy Gel", "Tri-Berry", "1 packet (32g)", 100.0, 0.0, 0.0, 22.0, mapOf("sodium" to 100.0, "potassium" to 20.0, "caffeine" to 20.0)),
        Product("GU", "GU Energy Gel", "Vanilla Bean", "1 packet (32g)", 100.0, 0.0, 0.0, 22.0, mapOf("sodium" to 125.0, "potassium" to 14.0)),
        Product("GU", "GU Energy Gel", "Strawberry Banana", "1 packet (32g)", 100.0, 0.0, 0.0, 23.0, mapOf("sodium" to 55.0, "potassium" to 30.0)),
        Product("GU", "GU Energy Gel", "Cola Me Happy", "1 packet (32g)", 100.0, 0.0, 0.0, 22.0, mapOf("sodium" to 100.0, "potassium" to 20.0, "caffeine" to 40.0)),
        Product("GU", "GU Energy Gel", "Salted Caramel", "1 packet (32g)", 100.0, 0.0, 0.0, 22.0, mapOf("sodium" to 125.0, "potassium" to 20.0, "caffeine" to 20.0)),
        Product("GU", "GU Energy Gel", "Pumpkin Spice", "1 packet (32g)", 100.0, 0.0, 0.0, 22.0, mapOf("sodium" to 100.0, "potassium" to 20.0)),
        Product("GU", "GU Energy Gel", "Birthday Cake", "1 packet (32g)", 100.0, 0.0, 0.0, 22.0, mapOf("sodium" to 100.0, "potassium" to 20.0)),
        Product("GU", "GU Energy Gel", "Campfire S'mores", "1 packet (32g)", 100.0, 0.0, 0.0, 22.0, mapOf("sodium" to 100.0, "potassium" to 20.0)),
        Product("GU", "GU Energy Gel", "Jet Blackberry", "1 packet (32g)", 100.0, 0.0, 0.0, 22.0, mapOf("sodium" to 100.0, "potassium" to 20.0, "caffeine" to 40.0)),
        Product("GU", "GU Energy Gel", "Lemon Sublime", "1 packet (32g)", 100.0, 0.0, 0.0, 22.0, mapOf("sodium" to 100.0, "potassium" to 20.0)),
        Product("GU", "GU Energy Gel", "Salted Watermelon", "1 packet (32g)", 100.0, 0.0, 0.0, 22.0, mapOf("sodium" to 125.0, "potassium" to 35.0, "caffeine" to 20.0)),
        Product("GU", "GU Energy Gel", "Mandarin Orange", "1 packet (32g)", 100.0, 0.0, 0.0, 22.0, mapOf("sodium" to 100.0, "potassium" to 20.0, "caffeine" to 20.0)),
        Product("GU", "GU Energy Gel", "Espresso Love", "1 packet (32g)", 100.0, 0.0, 0.0, 22.0, mapOf("sodium" to 100.0, "potassium" to 20.0, "caffeine" to 40.0)),
        Product("GU", "GU Energy Gel", "Caramel Macchiato", "1 packet (32g)", 100.0, 0.0, 0.0, 22.0, mapOf("sodium" to 100.0, "potassium" to 20.0, "caffeine" to 40.0)),
        Product("GU", "GU Energy Gel", "Raspberry Lemonade", "1 packet (32g)", 100.0, 0.0, 0.0, 22.0, mapOf("sodium" to 100.0, "potassium" to 20.0)),
        Product("GU", "GU Roctane Energy Gel", "Cold Brew Coffee", "1 packet (32g)", 100.0, 0.0, 0.0, 21.0, mapOf("sodium" to 125.0, "potassium" to 20.0, "caffeine" to 70.0)),
        Product("GU", "GU Roctane Energy Gel", "Blueberry Pomegranate", "1 packet (32g)", 100.0, 0.0, 0.0, 25.0, mapOf("sodium" to 125.0, "potassium" to 55.0, "caffeine" to 35.0)),
        Product("GU", "GU Roctane Energy Gel", "Chocolate Coconut", "1 packet (32g)", 100.0, 0.0, 0.0, 21.0, mapOf("sodium" to 125.0, "potassium" to 20.0, "caffeine" to 35.0)),
        Product("GU", "GU Roctane Energy Gel", "Vanilla Orange", "1 packet (32g)", 100.0, 0.0, 0.0, 21.0, mapOf("sodium" to 125.0, "potassium" to 20.0, "caffeine" to 35.0)),
        Product("GU", "GU Roctane Energy Gel", "Pineapple", "1 packet (32g)", 100.0, 0.0, 0.0, 21.0, mapOf("sodium" to 125.0, "potassium" to 20.0)),
        Product("GU", "GU Roctane Energy Gel", "Cherry Lime", "1 packet (32g)", 100.0, 0.0, 0.0, 21.0, mapOf("sodium" to 125.0, "potassium" to 20.0, "caffeine" to 35.0)),
        Product("GU", "GU Roctane Energy Gel", "Strawberry Kiwi", "1 packet (32g)", 100.0, 0.0, 0.0, 21.0, mapOf("sodium" to 125.0, "potassium" to 18.0)),
        Product("GU", "GU Roctane Energy Gel", "Lemonade", "1 packet (32g)", 100.0, 0.0, 0.0, 21.0, mapOf("sodium" to 190.0, "potassium" to 20.0)),
        Product("GU", "GU Roctane Energy Gel", "Sea Salt Chocolate", "1 packet (32g)", 100.0, 0.0, 2.0, 19.0, mapOf("sodium" to 180.0, "potassium" to 20.0, "caffeine" to 35.0)),
        Product("GU", "GU Roctane Energy Gel", "Salted Lime", "1 packet (32g)", 100.0, 0.0, 0.0, 21.0, mapOf("sodium" to 190.0, "potassium" to 20.0, "caffeine" to 35.0)),
        Product("GU", "GU Energy Chews", "Fruit Punch", "1 serving (30g, 8 chews)", 90.0, 0.0, 0.0, 22.0, mapOf("sodium" to 40.0, "potassium" to 10.0)),
        Product("GU", "GU Energy Chews", "Rainbow Fruit Mix", "1 serving (30g, 8 chews)", 90.0, 0.0, 0.0, 22.0, mapOf("sodium" to 40.0, "potassium" to 10.0)),
        Product("GU", "GU Energy Chews", "Orange", "1 serving (30g, 8 chews)", 90.0, 0.0, 0.0, 22.0, mapOf("sodium" to 40.0, "potassium" to 10.0)),
        Product("GU", "GU Energy Chews", "Strawberry", "1 serving (30g, 8 chews)", 90.0, 0.0, 0.0, 22.0, mapOf("sodium" to 40.0, "potassium" to 10.0, "caffeine" to 20.0)),
        Product("GU", "GU Energy Chews", "Blueberry Pomegranate", "1 serving (30g, 8 chews)", 90.0, 0.0, 0.0, 22.0, mapOf("sodium" to 40.0, "potassium" to 10.0)),
        Product("GU", "GU Energy Chews", "Watermelon", "1 serving (30g, 8 chews)", 90.0, 0.0, 0.0, 22.0, mapOf("sodium" to 40.0, "potassium" to 10.0)),
        Product("GU", "GU Energy Chews", "Salted Lime", "1 serving (30g, 8 chews)", 90.0, 0.0, 0.0, 22.0, mapOf("sodium" to 125.0, "potassium" to 10.0)),
        Product("GU", "GU Energy Chews", "Lemonade", "1 serving (30g, 8 chews)", 90.0, 0.0, 0.0, 22.0, mapOf("sodium" to 40.0, "potassium" to 10.0)),
        Product("GU", "GU Hydration Drink Tabs", "Strawberry Lemonade", "1 tab in 16oz water", 10.0, 0.0, 0.0, 3.0, mapOf("sodium" to 320.0, "potassium" to 55.0)),
        Product("GU", "GU Hydration Drink Tabs", "Strawberry Hibiscus", "1 tab in 16oz water", 10.0, 0.0, 0.0, 3.0, mapOf("sodium" to 320.0, "potassium" to 55.0, "caffeine" to 20.0)),
        Product("GU", "GU Hydration Drink Tabs", "Tropical Citrus", "1 tab in 16oz water", 10.0, 0.0, 0.0, 3.0, mapOf("sodium" to 320.0, "potassium" to 55.0)),
        Product("GU", "GU Hydration Drink Tabs", "Orange", "1 tab in 16oz water", 10.0, 0.0, 0.0, 3.0, mapOf("sodium" to 320.0, "potassium" to 55.0)),
        Product("GU", "GU Hydration Drink Tabs", "Tri-Berry", "1 tab in 16oz water", 10.0, 0.0, 0.0, 3.0, mapOf("sodium" to 320.0, "potassium" to 55.0)),
        Product("GU", "GU Hydration Drink Tabs", "Lemon Lime", "1 tab in 16oz water", 10.0, 0.0, 0.0, 3.0, mapOf("sodium" to 320.0, "potassium" to 55.0)),
        Product("GU", "GU Roctane Energy & Hydration Drink Mix", "Lemon Berry", "2 scoops (65g) in 21oz water", 250.0, 0.0, 0.0, 60.0, mapOf("sodium" to 320.0, "potassium" to 80.0, "caffeine" to 35.0)),
        Product("GU", "GU Roctane Energy & Hydration Drink Mix", "Summit Tea", "2 scoops (65g) in 21oz water", 250.0, 0.0, 0.0, 60.0, mapOf("sodium" to 320.0, "potassium" to 80.0, "caffeine" to 35.0)),
        Product("GU", "GU Roctane Energy & Hydration Drink Mix", "Tropical Fruit", "2 scoops (65g) in 21oz water", 250.0, 0.0, 0.0, 60.0, mapOf("sodium" to 320.0, "potassium" to 80.0, "caffeine" to 35.0)),
        Product("GU", "GU Roctane Energy & Hydration Drink Mix", "Grape", "2 scoops (65g) in 21oz water", 250.0, 0.0, 0.0, 60.0, mapOf("sodium" to 320.0, "potassium" to 80.0)),
        Product("GU", "GU Roctane Energy & Hydration Drink Mix", "Strawberry Hibiscus", "2 scoops (65g) in 21oz water", 250.0, 0.0, 0.0, 60.0, mapOf("sodium" to 320.0, "potassium" to 80.0)),
        Product("GU", "GU Roctane Electrolyte Capsules", "Unflavored", "1 capsule", 0.0, 0.0, 0.0, 0.0, mapOf("sodium" to 140.0)),
        Product("GU", "GU Energy Waffle", "Maple Cinnamon", "1 waffle (~30g)", 150.0, 0.0, 0.0, 22.0, mapOf("sodium" to 100.0, "potassium" to 20.0)),
        Product("GU", "GU Energy Waffle", "Campfire S'mores", "1 waffle (~30g)", 150.0, 0.0, 0.0, 22.0, mapOf("sodium" to 100.0, "potassium" to 20.0)),
        Product("GU", "GU Energy Waffle", "Coconut", "1 waffle (~30g)", 150.0, 0.0, 0.0, 22.0, mapOf("sodium" to 100.0, "potassium" to 20.0)),
        Product("GU", "GU Energy Waffle", "Coffee Caramel", "1 waffle (~30g)", 150.0, 0.0, 0.0, 22.0, mapOf("sodium" to 100.0, "potassium" to 20.0, "caffeine" to 20.0)),
        Product("GU", "GU Energy Waffle", "Salted Caramel", "1 waffle (~30g)", 150.0, 0.0, 0.0, 22.0, mapOf("sodium" to 150.0, "potassium" to 20.0)),
        Product("GU", "GU Energy Waffle", "Wild Berries", "1 waffle (~30g)", 150.0, 0.0, 0.0, 22.0, mapOf("sodium" to 100.0, "potassium" to 20.0)),
        Product("GU", "GU Energy Waffle", "Salted Chocolate", "1 waffle (~30g)", 150.0, 0.0, 0.0, 22.0, mapOf("sodium" to 150.0, "potassium" to 20.0)),
        Product("SiS", "SiS GO Isotonic Energy Gel", "Apple", "1 gel (60ml/69g)", 87.0, 0.0, 0.0, 22.0, mapOf("sodium" to 4.0)),
        Product("SiS", "SiS GO Isotonic Energy Gel", "Blackcurrant", "1 gel (60ml/69g)", 87.0, 0.0, 0.0, 22.0, mapOf("sodium" to 4.0)),
        Product("SiS", "SiS GO Isotonic Energy Gel", "Lemon & Lime", "1 gel (60ml/69g)", 87.0, 0.0, 0.0, 22.0, mapOf("sodium" to 4.0)),
        Product("SiS", "SiS GO Isotonic Energy Gel", "Orange", "1 gel (60ml/69g)", 87.0, 0.0, 0.0, 22.0, mapOf("sodium" to 4.0)),
        Product("SiS", "SiS GO Isotonic Energy Gel", "Pineapple", "1 gel (60ml/69g)", 87.0, 0.0, 0.0, 22.0, mapOf("sodium" to 4.0)),
        Product("SiS", "SiS GO Isotonic Energy Gel", "Pink Grapefruit", "1 gel (60ml/69g)", 87.0, 0.0, 0.0, 22.0, mapOf("sodium" to 4.0)),
        Product("SiS", "SiS GO Isotonic Energy Gel", "Tropical", "1 gel (60ml/69g)", 87.0, 0.0, 0.0, 22.0, mapOf("sodium" to 4.0)),
        Product("SiS", "SiS GO Energy + Electrolyte Gel", "Lemon & Mint", "1 gel (60ml/69g)", 87.0, 0.0, 0.0, 22.0, mapOf("sodium" to 118.0, "potassium" to 10.0)),
        Product("SiS", "SiS GO Energy + Electrolyte Gel", "Raspberry", "1 gel (60ml/69g)", 87.0, 0.0, 0.0, 22.0, mapOf("sodium" to 118.0, "potassium" to 10.0)),
        Product("SiS", "SiS GO Energy + Electrolyte Gel", "Salted Caramel", "1 gel (60ml/69g)", 87.0, 0.0, 0.0, 22.0, mapOf("sodium" to 118.0, "potassium" to 10.0)),
        Product("SiS", "SiS GO Energy + Caffeine Gel", "Berry (75mg)", "1 gel (60ml/69g)", 90.0, 0.0, 0.0, 22.0, mapOf("sodium" to 10.0, "caffeine" to 75.0)),
        Product("SiS", "SiS GO Energy + Caffeine Gel", "Cola (75mg)", "1 gel (60ml/69g)", 85.0, 0.0, 0.0, 21.0, mapOf("sodium" to 10.0, "caffeine" to 75.0)),
        Product("SiS", "SiS GO Energy + Caffeine Gel", "Double Espresso (150mg Turbo)", "1 gel (60ml/69g)", 87.0, 0.0, 0.0, 22.0, mapOf("sodium" to 10.0, "caffeine" to 150.0)),
        Product("SiS", "SiS Beta Fuel Gel", "Orange", "1 gel (60ml/86g)", 158.0, 0.0, 0.0, 40.0, mapOf("sodium" to 12.0)),
        Product("SiS", "SiS Beta Fuel Gel", "Strawberry & Lime", "1 gel (60ml/86g)", 158.0, 0.0, 0.0, 40.0, mapOf("sodium" to 12.0)),
        Product("SiS", "SiS Beta Fuel Gel", "Neutral", "1 gel (60ml/86g)", 158.0, 0.0, 0.0, 40.0, mapOf("sodium" to 12.0)),
        Product("SiS", "SiS Beta Fuel Gel + Electrolyte", "Raspberry & Lemon", "1 gel (60ml/86g)", 158.0, 0.0, 0.0, 40.0, mapOf("sodium" to 200.0)),
        Product("SiS", "SiS Beta Fuel Gel + Caffeine", "Apple (200mg)", "1 gel (60ml/86g)", 158.0, 0.0, 0.0, 40.0, mapOf("sodium" to 12.0, "caffeine" to 200.0)),
        Product("SiS", "SiS Beta Fuel Gel + Caffeine", "Lemon & Lime (200mg)", "1 gel (60ml/86g)", 158.0, 0.0, 0.0, 40.0, mapOf("sodium" to 12.0, "caffeine" to 200.0)),
        Product("SiS", "SiS GO Energy Bar", "Apple & Blackcurrant", "1 bar (40g)", 139.0, 4.5, 2.0, 26.0, mapOf()),
        Product("SiS", "SiS GO Energy Bar", "Banana Fudge", "1 bar (40g)", 139.0, 4.5, 2.0, 26.0, mapOf("potassium" to 121.0)),
        Product("SiS", "SiS GO Energy Bar", "Blueberry", "1 bar (40g)", 139.0, 4.5, 2.0, 26.0, mapOf()),
        Product("SiS", "SiS GO Energy Bar", "Chocolate Fudge", "1 bar (40g)", 139.0, 4.5, 2.0, 26.0, mapOf()),
        Product("SiS", "SiS GO Energy Bar", "Lemon", "1 bar (40g)", 139.0, 4.5, 2.0, 26.0, mapOf()),
        Product("SiS", "SiS GO Energy Bar", "Orange", "1 bar (40g)", 139.0, 4.5, 2.0, 26.0, mapOf()),
        Product("SiS", "SiS GO Energy Bar", "Red Berry", "1 bar (40g)", 139.0, 4.5, 2.0, 26.0, mapOf()),
        Product("SiS", "SiS GO Energy Bar", "Strawberry", "1 bar (40g)", 139.0, 4.5, 2.0, 26.0, mapOf()),
        Product("SiS", "SiS Beta Fuel Bar", "Orange", "1 bar (60g)", 186.0, 0.0, 0.0, 45.0, mapOf("sodium" to 44.0)),
        Product("SiS", "SiS Beta Fuel Bar", "Lemon", "1 bar (60g)", 186.0, 0.0, 0.0, 45.0, mapOf("sodium" to 44.0)),
        Product("SiS", "SiS GO Electrolyte Hydro Tablets", "Berry", "1 tab in 500ml water", 9.0, 0.0, 0.0, 1.0, mapOf("sodium" to 330.0, "potassium" to 62.0)),
        Product("SiS", "SiS GO Electrolyte Hydro Tablets", "Lemon", "1 tab in 500ml water", 9.0, 0.0, 0.0, 1.0, mapOf("sodium" to 330.0, "potassium" to 62.0)),
        Product("SiS", "SiS GO Electrolyte Hydro Tablets", "Mixed", "1 tab in 500ml water", 9.0, 0.0, 0.0, 1.0, mapOf("sodium" to 330.0, "potassium" to 62.0)),
        Product("SiS", "SiS GO Electrolyte Hydro Tablets", "Pineapple & Mango", "1 tab in 500ml water", 9.0, 0.0, 0.0, 1.0, mapOf("sodium" to 330.0, "potassium" to 62.0)),
        Product("SiS", "SiS GO Electrolyte Hydro Tablets", "Pink Grapefruit", "1 tab in 500ml water", 9.0, 0.0, 0.0, 1.0, mapOf("sodium" to 330.0, "potassium" to 62.0)),
        Product("SiS", "SiS GO Electrolyte Hydro Tablets", "Strawberry & Lime", "1 tab in 500ml water", 8.0, 0.0, 0.0, 1.0, mapOf("sodium" to 332.0, "potassium" to 62.0)),
        Product("SiS", "SiS GO Electrolyte Hydro Tablets", "Cola + Caffeine (75mg)", "1 tab in 500ml water", 7.0, 0.0, 0.0, 1.0, mapOf("sodium" to 346.0, "caffeine" to 75.0)),
        Product("SiS", "SiS GO Electrolyte Drink Powder", "Orange", "2 scoops (40g)", 146.0, 0.0, 0.0, 36.0, mapOf("sodium" to 200.0, "potassium" to 60.0)),
        Product("SiS", "SiS GO Electrolyte Drink Powder", "Lemon", "2 scoops (40g)", 146.0, 0.0, 0.0, 36.0, mapOf("sodium" to 200.0, "potassium" to 60.0)),
        Product("SiS", "SiS GO Electrolyte Drink Powder", "Blackcurrant", "2 scoops (40g)", 146.0, 0.0, 0.0, 36.0, mapOf("sodium" to 200.0, "potassium" to 60.0)),
        Product("SiS", "SiS GO Energy Drink Powder", "Orange", "3 scoops (50g)", 189.0, 0.0, 0.0, 47.0, mapOf()),
        Product("SiS", "SiS GO Energy Drink Powder", "Lemon", "3 scoops (50g)", 189.0, 0.0, 0.0, 47.0, mapOf()),
        Product("SiS", "SiS GO Energy Drink Powder", "Blackcurrant", "3 scoops (50g)", 189.0, 0.0, 0.0, 47.0, mapOf()),
        Product("SiS", "SiS REGO Rapid Recovery", "Chocolate", "1 scoop (50g)", 180.0, 20.0, 1.1, 22.0, mapOf("sodium" to 400.0, "potassium" to 2.0)),
        Product("SiS", "SiS REGO Rapid Recovery", "Vanilla", "1 scoop (50g)", 180.0, 20.0, 1.1, 22.0, mapOf("sodium" to 400.0, "potassium" to 2.0)),
        Product("SiS", "SiS REGO Rapid Recovery", "Strawberry", "1 scoop (50g)", 180.0, 20.0, 1.1, 22.0, mapOf("sodium" to 400.0, "potassium" to 2.0)),
        Product("SiS", "SiS REGO Rapid Recovery", "Banana", "1 scoop (50g)", 180.0, 20.0, 1.1, 22.0, mapOf("sodium" to 400.0, "potassium" to 2.0)),
        Product("Premier Protein", "Premier Protein Shake", "Chocolate", "1 shake (11 fl oz)", 160.0, 30.0, 3.0, 4.0, mapOf("sodium" to 230.0)),
        Product("Premier Protein", "Premier Protein Shake", "Vanilla", "1 shake (11 fl oz)", 160.0, 30.0, 3.0, 4.0, mapOf("sodium" to 230.0)),
        Product("Premier Protein", "Premier Protein Shake", "Caramel", "1 shake (11 fl oz)", 160.0, 30.0, 3.0, 2.0, mapOf("sodium" to 350.0)),
        Product("Premier Protein", "Premier Protein Shake", "Cookies and Cream", "1 shake (11 fl oz)", 160.0, 30.0, 3.0, 4.0, mapOf("sodium" to 220.0)),
        Product("Premier Protein", "Premier Protein Shake", "Chocolate Peanut Butter", "1 shake (11 fl oz)", 160.0, 30.0, 3.0, 5.0, mapOf("sodium" to 380.0)),
        Product("Premier Protein", "Premier Protein Shake", "Café Latte", "1 shake (11 fl oz)", 160.0, 30.0, 3.0, 4.0, mapOf("sodium" to 260.0)),
        Product("Premier Protein", "Premier Protein Shake", "Cookie Dough", "1 shake (11 fl oz)", 160.0, 30.0, 3.0, 3.0, mapOf("sodium" to 330.0)),
        Product("Premier Protein", "Premier Protein Shake", "Cinnamon Roll", "1 shake (11 fl oz)", 160.0, 30.0, 3.0, 3.0, mapOf("sodium" to 230.0)),
        Product("Premier Protein", "Premier Protein Shake", "Cake Batter", "1 shake (11 fl oz)", 160.0, 30.0, 3.0, 4.0, mapOf("sodium" to 370.0)),
        Product("Premier Protein", "Premier Protein Shake", "Peaches & Cream", "1 shake (11 fl oz)", 160.0, 30.0, 3.0, 3.0, mapOf("sodium" to 230.0)),
        Product("Premier Protein", "Premier Protein Shake", "Strawberries & Cream", "1 shake (11 fl oz)", 160.0, 30.0, 3.0, 3.0, mapOf("sodium" to 230.0)),
        Product("Premier Protein", "Premier Protein Shake", "Bananas & Cream", "1 shake (11 fl oz)", 160.0, 30.0, 3.0, 4.0, mapOf("sodium" to 230.0)),
        Product("Premier Protein", "Premier Protein Shake", "Lemon Bar", "1 shake (11 fl oz)", 160.0, 30.0, 3.0, 3.0, mapOf("sodium" to 250.0)),
        Product("Premier Protein", "Premier Protein Shake", "Orange Cream Swirl (Limited Edition)", "1 shake (11 fl oz)", 160.0, 30.0, 3.0, 3.0, mapOf("sodium" to 250.0)),
        Product("Premier Protein", "Premier Protein Shake", "Pumpkin Spice (Limited Time)", "1 shake (11 fl oz)", 160.0, 30.0, 3.0, 2.0, mapOf("sodium" to 230.0))
    )

    val SUPPLEMENTS = listOf(
        Product("Nature Made", "Supplement", "Multi For Him 50+ (USP Verified)", "1 tablet", 0.0, 0.0, 0.0, 0.0, mapOf("vitC" to 180.0, "vitD" to 25.0, "calcium" to 160.0, "magnesium" to 100.0, "vitB12" to 25.0, "zinc" to 15.0)),
        Product("Nature Made", "Supplement", "Magnesium Oxide 250mg", "1 tablet", 0.0, 0.0, 0.0, 0.0, mapOf("magnesium" to 250.0)),
        Product("Nature Made", "Supplement", "Iron 65mg (Ferrous Sulfate)", "1 tablet", 0.0, 0.0, 0.0, 0.0, mapOf("iron" to 65.0)),
        Product("Pure Encapsulations", "Supplement", "Potassium Magnesium (citrate)", "1 capsule", 0.0, 0.0, 0.0, 0.0, mapOf("potassium" to 140.0, "magnesium" to 70.0))
    )

    fun lines(brand: String) = SPORTS.filter { brand.isEmpty() || it.brand == brand }.map { it.line }.distinct()
}
