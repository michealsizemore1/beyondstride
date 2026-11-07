package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import io.github.michealsizemore1.beyondstride.Food
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * AI food search and nutrition-label reading with your Anthropic key — same prompts as the Command Center.
 * The key is kept only on this phone (not in backups, not sent to the watch or the Command Center).
 */
object AiFood {
    private fun p(ctx: Context) = ctx.getSharedPreferences("ai", 0)
    fun key(ctx: Context) = p(ctx).getString("key", "") ?: ""
    fun hasKey(ctx: Context) = key(ctx).startsWith("sk-")
    fun setKey(ctx: Context, k: String) = p(ctx).edit().putString("key", k.trim()).apply()

    private val LABELS = mapOf("fiber" to "Fiber g", "sugar" to "Sugar g", "sodium" to "Sodium mg", "vitC" to "Vitamin C mg", "vitD" to "Vitamin D mcg",
        "calcium" to "Calcium mg", "iron" to "Iron mg", "potassium" to "Potassium mg", "caffeine" to "Caffeine mg", "magnesium" to "Magnesium mg",
        "vitB12" to "Vitamin B12 mcg", "zinc" to "Zinc mg")
    private fun schema() = Food.MICROS.joinToString("\n") { "- \"$it\": ${LABELS[it]} per serving as a plain number" }

    /** Sends [content] to Claude and returns the reply text (or throws with a readable message). */
    private fun ask(ctx: Context, model: String, maxTokens: Int, content: JSONArray): String {
        val c = (URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 20_000; readTimeout = 60_000
            setRequestProperty("x-api-key", key(ctx)); setRequestProperty("anthropic-version", "2023-06-01"); setRequestProperty("content-type", "application/json")
        }
        val body = JSONObject().put("model", model).put("max_tokens", maxTokens)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val ok = c.responseCode in 200..299
        val txt = (if (ok) c.inputStream else c.errorStream).bufferedReader().readText()
        val o = JSONObject(txt)
        if (!ok) throw Exception(o.optJSONObject("error")?.optString("message") ?: "HTTP ${c.responseCode}")
        return o.optJSONArray("content")?.optJSONObject(0)?.optString("text")?.trim() ?: ""
    }

    /** A plain question → the answer text (Fuel AI). Call off the main thread. */
    fun askText(ctx: Context, prompt: String, maxTokens: Int = 3000) =
        ask(ctx, "claude-haiku-4-5-20251001", maxTokens, JSONArray().put(JSONObject().put("type", "text").put("text", prompt)))

    private fun json(text: String): JSONObject? = try { JSONObject(text) } catch (e: Exception) {
        Regex("\\{[\\s\\S]*\\}").find(text)?.value?.let { try { JSONObject(it) } catch (e2: Exception) { null } }
    }

    private fun item(f: JSONObject, meal: String, fallbackName: String): Food.Item {
        val micros = HashMap<String, Double>()
        Food.MICROS.forEach { k -> f.optDouble(k).takeIf { !it.isNaN() && it > 0 }?.let { micros[k] = it } }
        fun d(k: String) = f.optDouble(k).takeIf { !it.isNaN() }
        return Food.Item(Food.newId(), meal, f.optString("name").ifBlank { fallbackName }, f.optString("servingLabel").ifBlank { "1 serving" }, 1.0,
            d("cal"), d("prot"), d("fat"), d("carbs"), micros)
    }

    /** "Banana", "grande oat latte" … → 1–3 servings with full nutrition. Call off the main thread. */
    fun search(ctx: Context, q: String, meal: String): List<Food.Item> {
        val prompt = "Return nutrition facts for: $q\n" +
            "Respond with ONLY a single JSON object -- no markdown, no code fences, no explanation, nothing else before or after. The JSON must be {\"foods\":[...]}  with 1 to 3 entries (one per common serving size, if relevant). Each entry must have these exact keys:\n" +
            "- \"name\": the food name, including the serving size if relevant (for example \"Banana, medium\")\n" +
            "- \"servingLabel\": the serving size as a short string (for example \"1 medium (118g)\")\n" +
            "- \"cal\": Calories per serving as a plain number\n- \"prot\": Protein grams per serving as a plain number\n" +
            "- \"fat\": Total Fat grams per serving as a plain number\n- \"carbs\": Total Carbohydrate grams per serving as a plain number\n" + schema() + "\n" +
            "Use your best nutritional knowledge for realistic values, including every listed micronutrient -- do not skip any of them. All numeric values must be numbers, not strings. " +
            "Use 0 only when a value is genuinely negligible or absent, never to mean unknown. Pay special attention to caffeine: for coffee, espresso, lattes, tea, energy drinks and caffeinated sodas give a realistic non-zero milligram estimate; only use 0 if decaf or genuinely caffeine-free."
        val text = ask(ctx, "claude-haiku-4-5-20251001", 2000, JSONArray().put(JSONObject().put("type", "text").put("text", prompt)))
        val a = json(text)?.optJSONArray("foods") ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { item(it, meal, q) }
    }

    /** A photo of a nutrition label (or an app / menu screenshot) → one food. Call off the main thread. */
    fun label(ctx: Context, jpeg: ByteArray, meal: String): Food.Item? {
        val b64 = android.util.Base64.encodeToString(jpeg, android.util.Base64.NO_WRAP)
        val prompt = "Read this nutrition information image carefully — it may be a photo of a food package label, or a screenshot from a nutrition app, recipe site, or restaurant menu. " +
            "Extract the per-serving Nutrition Facts and any visible product or food name. Respond with ONLY a single JSON object with these keys: " +
            "\"name\", \"servingLabel\" (the exact serving size printed, with its unit, e.g. \"1 cup (240 mL)\"; \"1 serving\" only if unreadable), \"cal\", \"prot\", \"fat\", \"carbs\", and\n" + schema() +
            "\nAll numbers must be plain numbers per serving. Use 0 only when the label shows 0 or the nutrient is absent."
        val content = JSONArray()
            .put(JSONObject().put("type", "image").put("source", JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", b64)))
            .put(JSONObject().put("type", "text").put("text", prompt))
        val o = json(ask(ctx, "claude-sonnet-4-6", 700, content)) ?: return null
        return item(o, meal, "Scanned food")
    }

    /** Shrinks a photo so it uploads quickly but stays readable. */
    fun jpeg(ctx: Context, uri: android.net.Uri): ByteArray? = try {
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
        var sample = 1; while (maxOf(opts.outWidth, opts.outHeight) / sample > 2400) sample *= 2
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }) }
        bmp?.let { b ->
            val scale = minOf(1.0, 1600.0 / maxOf(b.width, b.height))
            val s = if (scale < 1) android.graphics.Bitmap.createScaledBitmap(b, (b.width * scale).toInt(), (b.height * scale).toInt(), true) else b
            java.io.ByteArrayOutputStream().also { s.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
        }
    } catch (e: Exception) { null }
}
