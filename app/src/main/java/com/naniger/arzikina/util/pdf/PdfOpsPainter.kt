package com.naniger.arzikina.util.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.naniger.arzikina.domain.issuedreceipt.pdf.PdfAlign
import com.naniger.arzikina.domain.issuedreceipt.pdf.PdfOp

/**
 * Dessine des [PdfOp] sur un [Canvas]. UNIQUE code de dessin du reçu : il sert au PDF
 * (canvas de `PdfDocument`) ET à l'aperçu à l'écran (canvas de la vue, mis à l'échelle), donc
 * l'aperçu est identique au document final.
 */
class PdfOpsPainter(private val paints: PdfPaints) {

    private val fillPaint = Paint().apply { style = Paint.Style.FILL }
    private val strokePaint = Paint().apply { style = Paint.Style.STROKE }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val logoBounds = RectF()

    fun draw(canvas: Canvas, ops: List<PdfOp>, logo: Bitmap?) {
        for (op in ops) {
            when (op) {
                is PdfOp.Text -> {
                    val paint = paints.paintFor(op.style)
                    val x = if (op.align == PdfAlign.RIGHT) op.x - paint.measureText(op.text) else op.x
                    canvas.drawText(op.text, x, op.baselineY, paint)
                }
                is PdfOp.Rect -> {
                    fillPaint.color = op.fillColor
                    canvas.drawRect(op.x, op.y, op.x + op.width, op.y + op.height, fillPaint)
                }
                is PdfOp.Line -> {
                    strokePaint.color = op.color
                    strokePaint.strokeWidth = op.widthPt
                    canvas.drawLine(op.x1, op.y1, op.x2, op.y2, strokePaint)
                }
                is PdfOp.Logo -> if (logo != null && !logo.isRecycled) {
                    logoBounds.set(op.x, op.y, op.x + op.width, op.y + op.height)
                    canvas.drawBitmap(logo, null, logoBounds, bitmapPaint)
                }
            }
        }
    }
}
