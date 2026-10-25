package com.naniger.arzikina.presentation.shop.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.naniger.arzikina.R

/**
 * Petit histogramme du chiffre d'affaires journalier (au plus ~30 barres). Dessin direct sur le Canvas :
 * aucune allocation pendant onDraw, aucune bibliothèque de graphiques.
 */
class RevenueBarsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var values: List<Long> = emptyList()
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.shop_stock_normal) }
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.arzikina_on_balance_card_variant)
        alpha = 90
    }
    private val rect = RectF()
    private val gapPx = resources.displayMetrics.density * 3f
    private val minBarPx = resources.displayMetrics.density * 2f

    fun setValues(newValues: List<Long>) {
        values = newValues
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val count = values.size
        if (count == 0) return
        val max = values.max().coerceAtLeast(1L)
        val barWidth = ((width - gapPx * (count - 1)) / count).coerceAtLeast(1f)
        val radius = barWidth.coerceAtMost(gapPx * 2)
        values.forEachIndexed { index, value ->
            val left = index * (barWidth + gapPx)
            val barHeight = if (value <= 0L) minBarPx else (height * (value.toFloat() / max)).coerceAtLeast(minBarPx)
            rect.set(left, height - barHeight, left + barWidth, height.toFloat())
            canvas.drawRoundRect(rect, radius, radius, if (value <= 0L) basePaint else barPaint)
        }
    }
}
