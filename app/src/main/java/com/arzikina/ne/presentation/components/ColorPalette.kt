package com.arzikina.ne.presentation.components

/**
 * Palette de couleurs proposée par le sélecteur de couleur des formulaires
 * Compte et Catégorie (voir [ColorPickerAdapter]). Centralisée ici pour que
 * les deux formulaires restent visuellement cohérents sans dupliquer la
 * liste. Valeurs au format ARGB (voir [com.arzikina.ne.domain.model.Account.colorArgb]).
 */
object ColorPalette {
    val COLORS: List<Long> = listOf(
        0xFF10B981L, // émeraude (couleur par défaut)
        0xFFEF4444L, // rouge
        0xFFF59E0BL, // ambre
        0xFF3B82F6L, // bleu
        0xFF8B5CF6L, // violet
        0xFFEC4899L, // rose
        0xFF14B8A6L, // turquoise
        0xFF6366F1L, // indigo
        0xFF84CC16L, // vert lime
        0xFF64748BL  // gris ardoise
    )
}
