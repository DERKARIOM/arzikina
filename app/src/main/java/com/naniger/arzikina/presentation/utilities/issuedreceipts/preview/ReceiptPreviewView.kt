package com.naniger.arzikina.presentation.utilities.issuedreceipts.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.naniger.arzikina.domain.issuedreceipt.pdf.PdfPagePlan
import com.naniger.arzikina.domain.issuedreceipt.pdf.ReceiptPdfContent
import com.naniger.arzikina.domain.issuedreceipt.pdf.ReceiptPdfLayoutEngine
import com.naniger.arzikina.domain.issuedreceipt.pdf.ReceiptPdfSpec
import com.naniger.arzikina.util.pdf.PdfOpsPainter
import com.naniger.arzikina.util.pdf.PdfPaints

/**
 * Aperçu du reçu : redessine EXACTEMENT les pages du PDF (même moteur de mise en page, même code de
 * dessin) à l'échelle de l'écran, sur un fond « papier » blanc quel que soit le thème. Les pages
 * s'empilent verticalement ; la vue se place dans un `ScrollView`.
 */
class ReceiptPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paints = PdfPaints()
    private val painter = PdfOpsPainter(paints)
    private var pages: List<PdfPagePlan> = emptyList()
    private var logo: Bitmap? = null

    private val density = resources.displayMetrics.density
    private val gap = 12f * density
    private val paperPaint = Paint().apply { color = Color.WHITE }
    private val borderPaint = Paint().apply {
        color = 0xFFD1D5DB.toInt()
        style = Paint.Style.STROKE
        strokeWidth = density
    }

    /** Recalcule la mise en page puis redessine. Appelé à chaque modification du formulaire. */
    fun show(content: ReceiptPdfContent, logo: Bitmap?) {
        this.logo = logo
        val newPages = ReceiptPdfLayoutEngine.layout(content, paints)
        val heightChanged = newPages.size != pages.size
        pages = newPages
        if (heightChanged) requestLayout()
        invalidate()
    }

    private fun scale(): Float = (width - 2 * gap).coerceAtLeast(1f) / ReceiptPdfSpec.PAGE_WIDTH

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val pageHeight = (width - 2 * gap).coerceAtLeast(1f) / ReceiptPdfSpec.PAGE_WIDTH * ReceiptPdfSpec.PAGE_HEIGHT
        val count = pages.size.coerceAtLeast(1)
        setMeasuredDimension(width, (count * pageHeight + (count + 1) * gap).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val scale = scale()
        val pageHeight = ReceiptPdfSpec.PAGE_HEIGHT * scale
        pages.forEachIndexed { index, page ->
            val top = gap + index * (pageHeight + gap)
            canvas.drawRect(gap, top, gap + ReceiptPdfSpec.PAGE_WIDTH * scale, top + pageHeight, paperPaint)
            canvas.drawRect(gap, top, gap + ReceiptPdfSpec.PAGE_WIDTH * scale, top + pageHeight, borderPaint)
            val save = canvas.save()
            canvas.translate(gap, top)
            canvas.scale(scale, scale)
            canvas.clipRect(0f, 0f, ReceiptPdfSpec.PAGE_WIDTH, ReceiptPdfSpec.PAGE_HEIGHT)
            painter.draw(canvas, page.ops, logo)
            canvas.restoreToCount(save)
        }
    }
}
