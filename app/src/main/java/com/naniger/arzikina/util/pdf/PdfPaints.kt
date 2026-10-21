package com.naniger.arzikina.util.pdf

import android.graphics.Paint
import android.graphics.Typeface
import com.naniger.arzikina.domain.issuedreceipt.pdf.PdfFont
import com.naniger.arzikina.domain.issuedreceipt.pdf.PdfTextMeasurer
import com.naniger.arzikina.domain.issuedreceipt.pdf.PdfTextStyle

/**
 * `Paint` mis en cache par style, partagé entre la mesure du texte (mise en page) et son dessin :
 * la largeur mesurée est exactement celle dessinée, dans le PDF comme dans l'aperçu à l'écran.
 * Polices système standard uniquement. Non thread-safe : une instance par génération / par vue.
 */
class PdfPaints : PdfTextMeasurer {
    private val cache = HashMap<PdfTextStyle, Paint>()

    fun paintFor(style: PdfTextStyle): Paint = cache.getOrPut(style) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = if (style.font == PdfFont.BOLD) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            textSize = style.sizePt
            color = style.color
        }
    }

    override fun width(text: String, style: PdfTextStyle): Float = paintFor(style).measureText(text)
}
