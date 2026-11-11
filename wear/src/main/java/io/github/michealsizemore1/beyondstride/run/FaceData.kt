package io.github.michealsizemore1.beyondstride.run

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import io.github.michealsizemore1.beyondstride.NutritionStore
import org.json.JSONObject
import java.util.Locale

/**
 * BeyondStride's watch-face data ("complications"): Body Battery, weather, calories left, protein, fluids, readiness,
 * today's workout and weekly miles. Any watch face can show them — including the BeyondStride face.
 * They read what the phone sends the watch, and refresh when new data arrives (and every 10 minutes).
 */
object FaceData {
    private fun pt(s: String) = PlainComplicationText.Builder(s).build()
    private fun tap(ctx: Context) = PendingIntent.getActivity(ctx, 0, Intent(ctx, RunActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
    fun acts(ctx: Context): JSONObject? = NutritionStore.acts(ctx)
    fun today(ctx: Context) = NutritionStore.days(ctx).lastOrNull { it.date == java.time.LocalDate.now().toString() }
    private fun todayKey() = java.time.LocalDate.now().toString()

    /** One value: short text, optional title, and a range for the ring. */
    data class V(val text: String, val title: String = "", val value: Float? = null, val max: Float = 100f, val long: String? = null, val desc: String = "", val min: Float = 0f)

    fun build(ctx: Context, type: ComplicationType, v: V?): ComplicationData {
        if (v == null) return NoDataComplicationData()
        val desc = pt(v.desc.ifEmpty { v.title + " " + v.text })
        return when (type) {
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder((v.value ?: 0f).coerceIn(v.min, v.max), v.min, v.max, desc)
                .setText(pt(v.text)).apply { if (v.title.isNotEmpty()) setTitle(pt(v.title)) }.setTapAction(tap(ctx)).build()
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(pt(v.long ?: listOf(v.title, v.text).filter { it.isNotEmpty() }.joinToString(" ")), desc)
                .apply { if (v.title.isNotEmpty() && v.long != null) setTitle(pt(v.title)) }.setTapAction(tap(ctx)).build()
            else -> ShortTextComplicationData.Builder(pt(v.text), desc).apply { if (v.title.isNotEmpty()) setTitle(pt(v.title)) }.setTapAction(tap(ctx)).build()
        }
    }

    fun battery(ctx: Context): V? = acts(ctx)?.optJSONObject("battery")?.takeIf { it.optString("date") == todayKey() }?.let {
        V("${it.optInt("now")}", "body", it.optInt("now").toFloat(), desc = "Body Battery ${it.optInt("now")} ${it.optString("label")}") }
    fun weather(ctx: Context): V? = acts(ctx)?.optJSONObject("weather")?.takeIf { System.currentTimeMillis() - it.optLong("at") < 6 * 3600_000L }?.let {
        V("${it.optString("temp")}°", it.optString("icon"), long = "${it.optString("icon")} ${it.optString("temp")}° ${it.optString("cond")} · H ${it.optString("hi")}° L ${it.optString("lo")}°",
            desc = "${it.optString("temp")} degrees ${it.optString("cond")}") }
    fun calories(ctx: Context): V? = today(ctx)?.let { d ->
        if (d.target <= 0) V(String.format(Locale.US, "%,d", d.cal), "kcal") else {
            val left = d.target - d.cal
            V(String.format(Locale.US, "%,d", Math.abs(left)), if (left >= 0) "left" else "over", d.cal.toFloat(), d.target.toFloat(), desc = "Calories $left left of ${d.target}")
        } }
    /**
     * A macro as its share of today's calories vs your ratio goal: "25%", with a dot the face draws — green on target (within 3%),
     * orange a little off (within 8%), red well off. The ring fills toward the goal %.
     */
    private fun macro(ctx: Context, name: String, label: String, grams: (io.github.michealsizemore1.beyondstride.Nutrition.Day) -> Double): V? = today(ctx)?.let { d ->
        val tile = d.macroTiles()?.firstOrNull { it.name == name }
        val kcal = d.prot * 4 + d.carbs * 4 + d.fat * 9
        if (kcal <= 0) return@let V("--", label, 0f, 100f, desc = "$name: nothing logged yet", min = -0.03f)
        val pct = tile?.actual ?: (grams(d) * (if (name == "Fat") 9 else 4) / kcal * 100)
        val goal = tile?.goal ?: 0
        val diff = Math.abs(pct - goal)
        // The face can't color emoji, so the status rides in the ring's minimum (a tiny, invisible offset) and the face draws the dot:
        // 0 = green, -0.01 = orange, -0.02 = red, -0.03 = no goal.
        val status = if (goal <= 0) -0.03f else if (diff <= 3) 0f else if (diff <= 8) -0.01f else -0.02f
        V("${Math.round(pct)}%", label, pct.toFloat(), if (goal > 0) goal.toFloat() else 100f,
            desc = "$name ${Math.round(pct)} percent of calories" + (if (goal > 0) ", goal $goal percent" else "") + when (status) { 0f -> ", on target"; -0.01f -> ", a little off"; -0.02f -> ", off target"; else -> "" }, min = status)
    }
    fun protein(ctx: Context): V? = macro(ctx, "Protein", "prot") { it.prot }
    fun carbs(ctx: Context): V? = macro(ctx, "Carbs", "carbs") { it.carbs }
    fun fat(ctx: Context): V? = macro(ctx, "Fat", "fat") { it.fat }
    fun fluids(ctx: Context): V? {
        val d = today(ctx); val t = acts(ctx)?.optJSONObject("face")?.optInt("fluidTarget", 0) ?: 0
        val oz = d?.waterOz ?: 0
        return V("${oz}oz", "fluid", oz.toFloat(), if (t > 0) t.toFloat() else 100f, desc = "Fluids $oz of $t ounces")
    }
    fun readiness(ctx: Context): V? = acts(ctx)?.optJSONObject("face")?.takeIf { it.optString("date") == todayKey() && it.has("ready") }?.let {
        V("${it.optInt("ready")}", "ready", it.optInt("ready").toFloat(), desc = "Readiness ${it.optInt("ready")} ${it.optString("readyLabel")}") }
    fun workout(ctx: Context): V? {
        val f = acts(ctx)?.optJSONObject("face")?.takeIf { it.optString("date") == todayKey() }
        val w = f?.optString("workout").orEmpty().ifEmpty { "Rest day" }
        val mi = f?.optDouble("planMi", 0.0) ?: 0.0
        val wk = acts(ctx)?.optJSONArray("mileage")?.optJSONObject(0)
        val week = wk?.let { " · wk ${it.optDouble("mi")}" + (if (it.optDouble("goal") > 0) "/${it.optDouble("goal").toInt()}" else "") + " mi" }.orEmpty()
        return V(if (mi > 0) "${trim(mi)}mi" else "Rest", "today", long = w, desc = "Today: $w$week")
    }
    fun weekMiles(ctx: Context): V? = acts(ctx)?.optJSONArray("mileage")?.optJSONObject(0)?.let {
        val mi = it.optDouble("mi"); val g = it.optDouble("goal")
        V(trim(mi), "wk mi", mi.toFloat(), if (g > 0) g.toFloat() else 40f, desc = "This week ${trim(mi)} of ${g.toInt()} miles") }
    private fun trim(d: Double) = if (d == Math.floor(d)) d.toInt().toString() else String.format(Locale.US, "%.1f", d)

    val SERVICES = listOf(BatteryComplication::class.java, WeatherComplication::class.java, CaloriesComplication::class.java, ProteinComplication::class.java,
        FluidsComplication::class.java, CarbsComplication::class.java, FatComplication::class.java, ReadinessComplication::class.java, WorkoutComplication::class.java, WeekMilesComplication::class.java)
    /** New data from the phone: every face showing these refreshes. */
    fun refresh(ctx: Context) = SERVICES.forEach { c -> try { ComplicationDataSourceUpdateRequester.create(ctx, ComponentName(ctx, c)).requestUpdateAll() } catch (e: Exception) { } }
}

abstract class FaceComplication : ComplicationDataSourceService() {
    abstract fun value(ctx: Context): FaceData.V?
    abstract val sample: FaceData.V
    override fun onComplicationRequest(request: ComplicationRequest, listener: ComplicationRequestListener) {
        listener.onComplicationData(try { FaceData.build(this, request.complicationType, value(this)) } catch (e: Exception) { NoDataComplicationData() })
    }
    override fun getPreviewData(type: ComplicationType): ComplicationData = FaceData.build(this, type, sample)
}
class BatteryComplication : FaceComplication() { override fun value(ctx: Context) = FaceData.battery(ctx); override val sample = FaceData.V("72", "body", 72f) }
class WeatherComplication : FaceComplication() { override fun value(ctx: Context) = FaceData.weather(ctx); override val sample = FaceData.V("54°", "☀️", long = "☀️ 54° Clear · H 61° L 38°") }
class CaloriesComplication : FaceComplication() { override fun value(ctx: Context) = FaceData.calories(ctx); override val sample = FaceData.V("1,240", "left", 1800f, 3000f) }
class ProteinComplication : FaceComplication() { override fun value(ctx: Context) = FaceData.protein(ctx); override val sample = FaceData.V("25%", "prot", 25f, 25f) }
class FluidsComplication : FaceComplication() { override fun value(ctx: Context) = FaceData.fluids(ctx); override val sample = FaceData.V("64oz", "fluid", 64f, 110f) }
class ReadinessComplication : FaceComplication() { override fun value(ctx: Context) = FaceData.readiness(ctx); override val sample = FaceData.V("80", "ready", 80f) }
class WorkoutComplication : FaceComplication() { override fun value(ctx: Context) = FaceData.workout(ctx); override val sample = FaceData.V("5mi", "today", long = "Easy Run 5 mi") }
class WeekMilesComplication : FaceComplication() { override fun value(ctx: Context) = FaceData.weekMiles(ctx); override val sample = FaceData.V("12", "wk mi", 12f, 40f) }
class CarbsComplication : FaceComplication() { override fun value(ctx: Context) = FaceData.carbs(ctx); override val sample = FaceData.V("54%", "carbs", 54f, 60f, min = -0.01f) }
class FatComplication : FaceComplication() { override fun value(ctx: Context) = FaceData.fat(ctx); override val sample = FaceData.V("27%", "fat", 15f, 15f, min = -0.02f) }
