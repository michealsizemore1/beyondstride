package io.github.michealsizemore1.beyondstride.run

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Food pictures: product image links (downloaded once, kept on the phone) or saved photos (data:…). */
object Thumbs {
    private val mem = android.util.LruCache<String, Bitmap>(120)
    private val pool = Executors.newFixedThreadPool(3)

    private fun decode(bytes: ByteArray): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var s = 1; while (maxOf(o.outWidth, o.outHeight) / s > 256) s *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s })
    }

    /** Shows [src] in [iv] (hidden when there's no picture). */
    fun show(iv: ImageView, src: String) {
        if (src.isBlank()) { iv.visibility = android.view.View.GONE; return }
        iv.visibility = android.view.View.VISIBLE
        iv.tag = src
        mem.get(src)?.let { iv.setImageBitmap(it); return }
        iv.setImageDrawable(null)
        val ctx = iv.context.applicationContext
        pool.execute {
            val bmp = try {
                if (src.startsWith("data:")) decode(android.util.Base64.decode(src.substringAfter(","), android.util.Base64.DEFAULT))
                else {
                    val f = File(File(ctx.cacheDir, "thumbs").apply { mkdirs() }, src.hashCode().toString())
                    if (!f.exists()) {
                        val c = (URL(src).openConnection() as HttpURLConnection).apply { connectTimeout = 10_000; readTimeout = 15_000; setRequestProperty("User-Agent", "BeyondStride-app") }
                        f.writeBytes(c.inputStream.readBytes())
                    }
                    decode(f.readBytes())
                }
            } catch (e: Exception) { null }
            if (bmp != null) mem.put(src, bmp)
            iv.post { if (iv.tag == src) { if (bmp != null) iv.setImageBitmap(bmp) else iv.visibility = android.view.View.GONE } }
        }
    }

    /** A rounded square picture view, [size] dp. */
    fun view(ctx: android.content.Context, size: Int, src: String) = ImageView(ctx).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        clipToOutline = true
        background = RunUi.rounded(0xFF2F3A35.toInt(), RunUi.dp(ctx, 8).toFloat())
        layoutParams = android.widget.LinearLayout.LayoutParams(RunUi.dp(ctx, size), RunUi.dp(ctx, size)).apply { marginEnd = RunUi.dp(ctx, 10) }
        show(this, src)
    }
}
