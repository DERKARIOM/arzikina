package com.naniger.arzikina.presentation.utilities.loans

import android.content.Context
import androidx.annotation.StringRes
import com.naniger.arzikina.R
import com.naniger.arzikina.domain.model.LoanType
import com.naniger.arzikina.util.FrenchElision

/**
 * Description automatique de la transaction cadeau (« Transformer en cadeau ») :
 * - prêt accordé : « Cadeau à {personne} » / « Gift to {person} » ;
 * - emprunt : « Cadeau de la part de {personne} » / « Gift from {person} », avec élision
 *   française devant une voyelle (« de la part d'Aïcha », voir [FrenchElision]).
 *
 * Construite dans la langue ACTIVE au moment de la transformation, puis enregistrée comme un texte
 * libre (même statut qu'une description saisie à la main) : elle ne change pas si l'utilisateur
 * change de langue ensuite. Le NOM RÉEL de la personne du prêt/emprunt est toujours utilisé.
 */
object LoanGiftDescription {

    fun build(context: Context, type: LoanType, personName: String): String {
        val name = personName.trim()
        // Personne introuvable (ne devrait pas arriver : clé étrangère CASCADE) — repli neutre.
        if (name.isEmpty()) return context.getString(R.string.loans_status_gifted)
        return context.getString(templateRes(type, name), name)
    }

    /** Partie PURE (sans Context) du choix de gabarit, testable isolément. */
    @StringRes
    fun templateRes(type: LoanType, personName: String): Int = when (type) {
        LoanType.LENT -> R.string.loan_gift_description_to
        LoanType.BORROWED -> if (FrenchElision.requiresElision(personName)) {
            R.string.loan_gift_description_from_elided
        } else {
            R.string.loan_gift_description_from
        }
    }
}
