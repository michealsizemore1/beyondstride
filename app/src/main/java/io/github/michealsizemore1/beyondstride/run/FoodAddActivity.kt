package io.github.michealsizemore1.beyondstride.run

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import io.github.michealsizemore1.beyondstride.Food
import io.github.michealsizemore1.beyondstride.Nutrition

/** Add food: recent foods, your foods (Command Center database), saved meals, online search, barcode, or by hand. */
class FoodAddActivity : Activity() {
    private var meal = "snack"
    private var query = ""
    private var online: List<Food.Item>? = null
    private var searching = false
    private var onlineTitle = "ONLINE · OPEN FOOD FACTS"
    private var photoUri: android.net.Uri? = null
    private lateinit var list: LinearLayout
    private var tab = "recent"
    private var sort = "saved"

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        io.github.michealsizemore1.beyondstride.Theme.load(this, "runsettings")
        meal = intent.getStringExtra("meal") ?: "snack"
        build()
        
        // Opened from a Nutrition tile: go straight to the camera, a picture, or the barcode scanner.
        if (b == null) when (intent.getStringExtra("action")) {
            "camera" -> takePhoto(); "upload" -> choosePhoto(); "barcode" -> scan()
        }
    }

    private fun dp(v: Int) = RunUi.dp(this, v)

    private fun build() {
        val ctx = this
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(40), dp(16), dp(32)) }
        col.addView(RunUi.text(this, "Add food", Color.WHITE, 26f, true))

        // Which meal.
        col.addView(android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, dp(4))
                Nutrition.MEAL_ORDER.forEach { m ->
                    addView(RunUi.text(ctx, Nutrition.MEAL_NAMES[m] ?: m, if (m == meal) Color.BLACK else Color.WHITE, 13f, true).apply {
                        background = RunUi.rounded(if (m == meal) RunUi.ACCENT else RunUi.CARD, dp(14).toFloat())
                        setPadding(dp(12), dp(6), dp(12), dp(6))
                        setOnClickListener { meal = m; build() }
                    }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(6) })
                }
            })
        })

        // Search box: filters your foods as you type; Search online for anything else.
        val search = EditText(this).apply {
            hint = "What did you eat?"; setText(query); setSingleLine(); textSize = 16f
            setTextColor(Color.WHITE); setHintTextColor(RunUi.MUTED)
            background = RunUi.rounded(RunUi.CARD, dp(12).toFloat()); setPadding(dp(14), dp(10), dp(14), dp(10))
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(e: android.text.Editable?) { query = e?.toString() ?: "" }
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            })
            setOnEditorActionListener { _, _, _ -> if (AiFood.hasKey(this@FoodAddActivity)) aiSearch() else searchOnline(); true }
        }
        col.addView(search, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })

        // Every way to add one food: Barcode · Label photo · Manual, then AI search · Online.
        fun btnRow(vararg b: Triple<String, Int, () -> Unit>) = col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0)
            b.forEachIndexed { i, (t, c, go) ->
                addView(RunUi.button(ctx, t, c, Color.WHITE, go).apply { maxLines = 1 },
                    LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { if (i < b.size - 1) marginEnd = dp(6) })
            }
        })
        btnRow(Triple("▥ Barcode", 0xFF0F766E.toInt()) { scan() }, Triple("🏷 Label photo", 0xFFC0640F.toInt()) { labelPhoto() },
            Triple("✎ Manual", RunUi.CARD) { FoodUi.manual(this@FoodAddActivity, meal) { } })
        btnRow(Triple("✨ AI search", 0xFF3B3172.toInt()) { aiSearch() }, Triple("🔎 Online", RunUi.CARD) { searchOnline() })

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(list)
        fill()
        setContentView(ScrollView(this).apply { setBackgroundColor(RunUi.BG); addView(col, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)) })
    }

    private fun matches(name: String) = query.isBlank() || query.trim().split(" ").all { name.contains(it, ignoreCase = true) }

    private fun row(f: Food.Item, sub: String? = null) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        background = RunUi.rounded(RunUi.CARD, dp(12).toFloat()); setPadding(dp(12), dp(8), dp(12), dp(8))
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) }
        addView(LinearLayout(this@FoodAddActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(RunUi.text(this@FoodAddActivity, f.name, Color.WHITE, 14f, true))
            addView(RunUi.text(this@FoodAddActivity, sub ?: (f.servingLabel + " · P ${(f.prot ?: 0.0).toInt()} · C ${(f.carbs ?: 0.0).toInt()} · F ${(f.fat ?: 0.0).toInt()} g"), RunUi.MUTED, 11f))
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        addView(RunUi.text(this@FoodAddActivity, "${((f.cal ?: 0.0) * f.servings).toInt()}", RunUi.ACCENT, 14f, true))
        addView(RunUi.text(this@FoodAddActivity, "  ＋", RunUi.ACCENT, 18f, true))
        setOnClickListener { FoodUi.confirmAdd(this@FoodAddActivity, f, meal) { } }
    }

    private fun note(t: String) = list.addView(RunUi.text(this, t, RunUi.MUTED, 13f).apply { setPadding(dp(4), dp(10), dp(4), 0) })

    private fun fill() {
        if (!::list.isInitialized) return
        list.removeAllViews()
        online?.let { res ->
            list.addView(RunUi.section(this, onlineTitle))
            if (res.isEmpty()) note("Nothing found for “$query”. Try fewer words, the brand name, or Manual.")
            res.forEach { list.addView(row(it)) }
            return
        }
        if (searching) { note("Searching…"); return }
        note("New food: type what you ate, then tap ✨ AI search or 🔎 Online — or use Barcode, Label or Manual.\n\nFoods you've had before are on the Nutrition tab under your meals (Recent, Food Database, Healthy 7+, Saved Meals).")
    }

    private fun searchOnline() {
        val q = query.trim()
        if (q.length < 2) { Toast.makeText(this, "Type what you ate first", Toast.LENGTH_SHORT).show(); return }
        searching = true; online = null; fill()
        Thread {
            val r = FoodLibrary.search(q, meal)
            runOnUiThread { searching = false; onlineTitle = "ONLINE · OPEN FOOD FACTS"; online = r; if (!isFinishing) fill() }
        }.start()
    }

    private fun needKey(): Boolean {
        if (AiFood.hasKey(this)) return false
        android.app.AlertDialog.Builder(this).setTitle("Add your Anthropic key")
            .setMessage("AI search and label photos use your Anthropic API key (the same one the Command Center uses). Add it in Settings → AI food search.")
            .setPositiveButton("Open Settings") { _, _ -> startActivity(android.content.Intent(this, RunSettingsActivity::class.java).putExtra("page", "ai")) }
            .setNegativeButton("Cancel", null).show()
        return true
    }

    /** AI search: any food or drink, any wording ("grande oat latte", "2 eggs scrambled in butter"). */
    private fun aiSearch() {
        if (needKey()) return
        val q = query.trim()
        if (q.length < 2) { Toast.makeText(this, "Type what you ate first", Toast.LENGTH_SHORT).show(); return }
        searching = true; online = null; fill()
        Thread {
            val (r, err) = try { AiFood.search(this, q, meal) to null } catch (e: Exception) { emptyList<Food.Item>() to (e.message ?: "AI search failed") }
            runOnUiThread {
                searching = false; onlineTitle = "AI ESTIMATE · CHECK BEFORE ADDING"; online = r
                if (err != null) Toast.makeText(this, err, Toast.LENGTH_LONG).show()
                if (!isFinishing) fill()
            }
        }.start()
    }

    /** Nutrition label: take a photo or choose one (screenshots of apps or menus work too). */
    private fun labelPhoto() {
        if (needKey()) return
        android.app.AlertDialog.Builder(this).setTitle("Nutrition label")
            .setItems(arrayOf("Take a photo", "Choose a picture")) { _, which -> if (which == 0) takePhoto() else choosePhoto() }.show()
    }

    private fun takePhoto() {
        if (needKey()) return
        val dir = java.io.File(cacheDir, "photos").apply { mkdirs() }
        val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.files", java.io.File(dir, "label.jpg"))
        photoUri = uri
        try {
            startActivityForResult(android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri)
                .addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION), 41)
        } catch (e: Exception) { Toast.makeText(this, "No camera app", Toast.LENGTH_SHORT).show() }
    }

    private fun choosePhoto() {
        if (needKey()) return
        startActivityForResult(android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).setType("image/*"), 42)
    }

    @Deprecated("Activity result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = (if (requestCode == 41) photoUri else data?.data) ?: return
        Toast.makeText(this, "Reading the label…", Toast.LENGTH_SHORT).show()
        Thread {
            val bytes = AiFood.jpeg(this, uri)
            val (f, err) = if (bytes == null) null to "Couldn't open that picture" else
                try { AiFood.label(this, bytes, meal) to null } catch (e: Exception) { null to (e.message ?: "Couldn't read the label") }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (f == null) Toast.makeText(this, err ?: "Couldn't read the label", Toast.LENGTH_LONG).show()
                else FoodUi.confirmAdd(this, f, meal) { }
            }
        }.start()
    }

    private fun scan() {
        try {
            val opts = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_13, com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_8,
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_UPC_A, com.google.mlkit.vision.barcode.common.Barcode.FORMAT_UPC_E).build()
            com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(this, opts).startScan()
                .addOnSuccessListener { bc ->
                    val code = bc.rawValue?.filter { it.isDigit() } ?: return@addOnSuccessListener
                    Toast.makeText(this, "Looking up $code…", Toast.LENGTH_SHORT).show()
                    Thread {
                        val f = FoodLibrary.barcode(code, meal)
                        runOnUiThread {
                            if (isFinishing) return@runOnUiThread
                            if (f == null) Toast.makeText(this, "Barcode $code isn't in Open Food Facts — use Manual.", Toast.LENGTH_LONG).show()
                            else FoodUi.confirmAdd(this, f, meal) { }
                        }
                    }.start()
                }
                .addOnFailureListener { Toast.makeText(this, "Scanner unavailable: ${it.message}", Toast.LENGTH_LONG).show() }
        } catch (e: Exception) { Toast.makeText(this, "Scanner unavailable", Toast.LENGTH_LONG).show() }
    }
}
