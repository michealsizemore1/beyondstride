package io.github.michealsizemore1.beyondstride.run

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

/** The text of an uploaded PDF plan (what the Command Center read with pdf.js). Call off the main thread. */
object PdfText {
    fun extract(ctx: Context, bytes: ByteArray): String? = try {
        PDFBoxResourceLoader.init(ctx.applicationContext)
        PDDocument.load(bytes).use { doc ->
            val s = PDFTextStripper().apply { sortByPosition = true; wordSeparator = " "; lineSeparator = "\n" }
            val sb = StringBuilder()
            for (i in 1..doc.numberOfPages) { s.startPage = i; s.endPage = i; sb.append(s.getText(doc).trim()).append("\n\n") }
            sb.toString().trim().ifEmpty { null }
        }
    } catch (e: Throwable) { null }
}
