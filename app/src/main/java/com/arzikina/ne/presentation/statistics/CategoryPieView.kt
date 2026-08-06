package com.arzikina.ne.presentation.statistics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import kotlin.math.min

/**
 * Anneau de répartition ("donut") dessiné directement sur un [Canvas], en
 * remplacement de `com.patrykandpatrick.vico.views.pie.PieChartView`.
 *
 * Motif : `PieChartView` (module Vico `views`, en maintenance — corrections
 * critiques uniquement) lève `IllegalArgumentException: The outer size must
 * be greater than the inner size.` de façon reproductible dès qu'un modèle
 * non vide lui est fourni sur cet écran, malgré une taille de vue fixe et
 * valide — un problème interne à la bibliothèque que deux correctifs
 * successifs (dont un contournement de timing de mise en page) n'ont pas
 * résolu. Plutôt que de continuer à deviner un comportement interne non
 * vérifiable, cette vue custom couvre exactement notre besoin (proportions
 * par catégorie, couleurs dynamiques par catégorie) avec une API Android
 * standard (`Canvas.drawArc`) qui ne peut pas lever cette exception, quelle
 * que soit la taille de la vue ou le nombre de tranches.
 *
 * Le graphique en barres de l'évolution mensuelle (`CartesianChartView`)
 * n'est pas concerné : son code source ne comporte pas d'invariant de ce
 * type et n'a provoqué aucun crash.
 */
class CategoryPieView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** Une tranche de l'anneau : [fraction] dans `[0, 1]`, part du total représentée. */
    data class Slice(val fraction: Float, @ColorInt val color: Int)

    /** Ré-affiche automatiquement l'anneau lorsque les tranches changent. */
    var slices: List<Slice> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val arcBounds = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (slices.isEmpty() || width <= 0 || height <= 0) return

        val strokeWidth = min(width, height) * STROKE_WIDTH_RATIO
        paint.strokeWidth = strokeWidth
        val inset = strokeWidth / 2f
        arcBounds.set(inset, inset, width - inset, height - inset)

        var startAngle = START_ANGLE_DEGREES
        for (slice in slices) {
            if (slice.fraction <= 0f) continue
            val sweepAngle = slice.fraction * FULL_CIRCLE_DEGREES
            paint.color = slice.color
            canvas.drawArc(arcBounds, startAngle, sweepAngle, false, paint)
            startAngle += sweepAngle
        }
    }

    private companion object {
        const val STROKE_WIDTH_RATIO = 0.22f
        const val START_ANGLE_DEGREES = -90f
        const val FULL_CIRCLE_DEGREES = 360f
    }
}
