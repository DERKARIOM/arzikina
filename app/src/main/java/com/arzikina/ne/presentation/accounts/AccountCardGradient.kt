package com.arzikina.ne.presentation.accounts

import android.graphics.Color
import android.graphics.drawable.GradientDrawable

/**
 * Dégradé diagonal (façon carte VISA, voir maquette "RÉORGANISATION – PAGE
 * COMPTE") calculé à partir de [com.arzikina.ne.domain.model.Account.colorArgb]
 * — pas de nouveau champ à stocker : la couleur choisie à la création du
 * compte (voir [com.arzikina.ne.presentation.components.ColorPickerAdapter])
 * sert de base, éclaircie en haut et assombrie en bas (composante V du HSV),
 * même teinte conservée. Pas de rayon de coin ici : la carte est posée à
 * l'intérieur d'une `MaterialCardView` qui découpe déjà son contenu selon sa
 * propre forme (même principe que les cartes "postcard" existantes, ex.
 * `accountSummaryCard` sur "Détail du compte"), donc un simple rectangle
 * suffit.
 */
object AccountCardGradient {
    private const val LIGHTEN_FACTOR = 1.15f
    private const val DARKEN_FACTOR = 0.70f

    fun create(colorArgb: Long): GradientDrawable {
        val base = colorArgb.toInt()
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(adjustValue(base, LIGHTEN_FACTOR), adjustValue(base, DARKEN_FACTOR))
        )
    }

    private fun adjustValue(color: Int, factor: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] * factor).coerceIn(0f, 1f)
        return Color.HSVToColor(Color.alpha(color), hsv)
    }
}
