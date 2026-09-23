package com.naniger.arzikina.presentation.statistics

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import com.naniger.arzikina.util.Money
import com.google.android.material.color.MaterialColors
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Diagramme en bâtons de la répartition des dépenses par catégorie, EN REMPLACEMENT de
 * [CategoryPieView] (anneau) — demande explicite : une barre par catégorie, colorée comme la
 * légende ([CategoryBreakdownAdapter]), avec un axe des valeurs à gauche (lignes pointillées), à la
 * manière du graphique équivalent de la version Web (`arzikina-web-sync/src/routes/statistiques.tsx`,
 * `BarChart` recharts). Pas de nom de catégorie sous les barres (retiré sur demande explicite) :
 * [CategoryBreakdownAdapter] (la légende juste en dessous, voir `StatisticsFragment.render`) reste
 * la seule source de ce libellé pour cet écran.
 *
 * Dessinée directement sur un [Canvas] plutôt qu'avec Vico, pour la même raison que
 * [CategoryPieView] documentait déjà pour l'ancien anneau : une couleur DIFFÉRENTE par barre n'est
 * pas un cas d'usage nativement supporté par une série Vico unique (une série = une seule couleur) ;
 * multiplier les séries à un seul point chacune aurait été plus fragile qu'un dessin direct — même
 * compromis, pas une défiance de principe envers Vico (le graphique "Évolution" plus haut continue
 * de l'utiliser sans souci pour ses deux séries à couleur fixe).
 */
class CategoryBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /**
     * Une barre : [amountMinor] en unité mineure (voir [com.naniger.arzikina.domain.model.CurrencyAmount]),
     * toujours > 0. [label] n'est actuellement PAS dessiné (voir la doc de tête de la classe) —
     * conservé dans le modèle pour rester auto-descriptif côté appelant ([StatisticsFragment]) et en
     * cas de besoin futur (ex. accessibilité), sans dupliquer un second type de données proche de
     * [com.naniger.arzikina.presentation.statistics.CategoryBreakdownItem].
     */
    data class Bar(val label: String, val amountMinor: Long, @ColorInt val color: Int)

    /** Ré-affiche automatiquement le graphique lorsque les barres changent. */
    var bars: List<Bar> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val gridLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        pathEffect = DashPathEffect(floatArrayOf(4f * density, 4f * density), 0f)
    }

    private val axisLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.RIGHT
    }

    private val barRect = RectF()

    // Pas de décimales sur l'axe (voir niceCeiling : toujours un multiple "rond") — même
    // NumberFormat.getNumberInstance(Locale.FRENCH) que Money.kt, pour le même séparateur de
    // milliers que partout ailleurs dans l'app.
    private val axisNumberFormat = NumberFormat.getNumberInstance(Locale.FRENCH).apply {
        maximumFractionDigits = 0
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bars.isEmpty() || width <= 0 || height <= 0) return

        val axisColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0)
        gridLinePaint.color = axisColor
        gridLinePaint.alpha = GRID_LINE_ALPHA
        axisLabelPaint.color = axisColor

        val majorAmounts = bars.map { Money.toMajorDouble(it.amountMinor) }
        val niceMax = niceCeiling((majorAmounts.maxOrNull() ?: 1.0).coerceAtLeast(1.0))
        val gridValues = (0..GRID_STEPS).map { step -> niceMax * step / GRID_STEPS }

        val gap = LABEL_GAP_DP * density
        val leftPadding = gridValues.maxOf { axisLabelPaint.measureText(axisNumberFormat.format(it)) } + gap
        // Plus de libellé de catégorie sous les barres (voir Bar.label) : la seule réserve de bas
        // de graphique est un petit espace de respiration, pas une hauteur de texte.
        val bottomPadding = gap
        val topPadding = axisLabelPaint.textSize / 2f

        val chartLeft = leftPadding
        val chartRight = width.toFloat()
        val chartTop = topPadding
        val chartBottom = height - bottomPadding
        val chartHeight = chartBottom - chartTop
        if (chartHeight <= 0f || chartRight <= chartLeft) return

        for (step in 0..GRID_STEPS) {
            val value = niceMax * step / GRID_STEPS
            val y = chartBottom - (chartHeight * step / GRID_STEPS)
            canvas.drawLine(chartLeft, y, chartRight, y, gridLinePaint)
            canvas.drawText(axisNumberFormat.format(value), chartLeft - gap, y + axisLabelPaint.textSize / 3f, axisLabelPaint)
        }

        val slotWidth = (chartRight - chartLeft) / bars.size
        val barWidth = slotWidth * BAR_WIDTH_RATIO
        bars.forEachIndexed { index, bar ->
            val slotCenter = chartLeft + slotWidth * (index + 0.5f)
            val barHeight = (chartHeight * (Money.toMajorDouble(bar.amountMinor) / niceMax)).toFloat()
            val top = chartBottom - barHeight
            barRect.set(slotCenter - barWidth / 2f, top, slotCenter + barWidth / 2f, chartBottom)
            barPaint.color = bar.color
            val radius = barWidth / 3f
            canvas.drawRoundRect(barRect, radius, radius, barPaint)
        }
    }

    /**
     * Arrondit [value] au multiple "rond" supérieur le plus proche (1/2/5 × 10^n) — même principe
     * que les graphiques usuels (recharts, etc.) pour des graduations d'axe lisibles ("6 000 000",
     * "4 500 000"...) plutôt qu'un maximum brut arbitraire.
     */
    private fun niceCeiling(value: Double): Double {
        val exponent = floor(log10(value))
        val magnitude = 10.0.pow(exponent)
        val residual = value / magnitude
        val niceResidual = when {
            residual <= 1.0 -> 1.0
            residual <= 2.0 -> 2.0
            residual <= 5.0 -> 5.0
            else -> 10.0
        }
        return niceResidual * magnitude
    }

    private companion object {
        /** Nombre d'intervalles de l'axe des valeurs (5 lignes : 0 + 4 paliers). */
        const val GRID_STEPS = 4
        const val GRID_LINE_ALPHA = 90
        const val BAR_WIDTH_RATIO = 0.55f
        const val LABEL_GAP_DP = 6f
    }
}
