package com.arzikina.ne.presentation.components

/**
 * Palette de couleurs proposée par le sélecteur de couleur des formulaires
 * Compte, Catégorie et Planification (voir [ColorPickerAdapter]). Centralisée ici pour que ces
 * formulaires restent visuellement cohérents sans dupliquer la
 * liste. Valeurs au format ARGB (voir [com.arzikina.ne.domain.model.Account.colorArgb]).
 *
 * NON MODIFIÉE par l'Étape 3 de "Planification" malgré la tentation d'y ajouter la couleur par
 * défaut d'une planification (0xFF42B998, voir [com.arzikina.ne.domain.model.FinancialPlan.colorArgb]) :
 * [com.arzikina.ne.presentation.utilities.loans.personAvatarColorArgb] dérive un INDEX dans cette
 * liste par hash-modulo du nom d'une personne (Prêts/Emprunts) — changer la TAILLE de [COLORS]
 * (même en ajoutant une seule couleur) change silencieusement la couleur d'avatar de TOUTES les
 * personnes déjà enregistrées, pas seulement des nouvelles. Le formulaire de planification utilise
 * donc directement 0xFF10B981 (première couleur ci-dessous) comme sélection par défaut plutôt que
 * d'étendre cette liste (voir `FinancialPlanFormState.DEFAULT_COLOR_ARGB`).
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
