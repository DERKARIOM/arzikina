package com.arzikina.ne.presentation.dashboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.arzikina.ne.R
import kotlin.math.max

/**
 * Mini graphique en barres (revenu/dépense du mois) à coins arrondis façon
 * "pilule" — voir la carte "Dépenses vs revenus" de [DashboardFragment].
 *
 * Vue personnalisée dessinée au Canvas plutôt qu'un graphique Vico : pour un
 * visuel aussi simple (2 barres), une vue custom est plus légère et ne
 * dépend d'aucun invariant interne de bibliothèque (voir CategoryPieView pour
 * le même raisonnement, adopté après un crash récurrent et non résolu de
 * Vico PieChartView côté Statistiques).
 */
class IncomeExpenseBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** Montants en unité mineure (voir [com.arzikina.ne.domain.model.CurrencyAmount]), toujours positifs ou nuls. */
    var income: Long = 0L
        set(value) { field = value; invalidate() }

    var expense: Long = 0L
        set(value) { field = value; invalidate() }

    @ColorInt
    var incomeColor: Int = ContextCompat.getColor(context, R.color.income_green)
        set(value) { field = value; invalidate() }

    @ColorInt
    var expenseColor: Int = ContextCompat.getColor(context, R.color.expense_red)
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barRect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val maxValue = max(income, expense).coerceAtLeast(1L).toFloat()
        val barWidth = width * BAR_WIDTH_RATIO
        val gap = width - (barWidth * 2f)
        val minBarHeight = height * MIN_BAR_HEIGHT_RATIO

        drawBar(canvas, left = 0f, width = barWidth, value = income, maxValue = maxValue, minHeight = minBarHeight, color = incomeColor)
        drawBar(canvas, left = barWidth + gap, width = barWidth, value = expense, maxValue = maxValue, minHeight = minBarHeight, color = expenseColor)
    }

    private fun drawBar(canvas: Canvas, left: Float, width: Float, value: Long, maxValue: Float, minHeight: Float, @ColorInt color: Int) {
        val proportionalHeight = height * (value.toFloat() / maxValue)
        val barHeight = max(minHeight, proportionalHeight)
        val top = height - barHeight
        barRect.set(left, top, left + width, height.toFloat())
        paint.color = color
        val radius = width / 4f
        canvas.drawRoundRect(barRect, radius, radius, paint)
    }

    private companion object {
        /** Chaque barre occupe 35% de la largeur ; le reste forme l'espace entre les deux. */
        const val BAR_WIDTH_RATIO = 0.35f

        /** Hauteur minimale (même à 0) pour que la barre reste visible sous forme de pilule. */
        const val MIN_BAR_HEIGHT_RATIO = 0.08f
    }
}
