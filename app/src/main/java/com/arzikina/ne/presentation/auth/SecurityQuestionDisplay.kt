package com.arzikina.ne.presentation.auth

import androidx.annotation.StringRes
import com.arzikina.ne.R
import com.arzikina.ne.domain.model.SecurityQuestion

/**
 * Seul endroit de l'app qui associe une [SecurityQuestion] (clé de domaine,
 * sans texte — voir sa KDoc) à sa chaîne localisée. Utilisé par Inscription
 * (liste déroulante de choix) et "Mot de passe oublié" (réaffichage de la
 * question choisie) : les deux écrans partagent cette seule source de
 * vérité plutôt que de dupliquer le mapping chacun de leur côté.
 */
@StringRes
fun SecurityQuestion.displayTextRes(): Int = when (this) {
    SecurityQuestion.FIRST_PET_NAME -> R.string.security_question_first_pet_name
    SecurityQuestion.BIRTH_CITY -> R.string.security_question_birth_city
    SecurityQuestion.MOTHER_MAIDEN_NAME -> R.string.security_question_mother_maiden_name
    SecurityQuestion.FAVORITE_TEACHER -> R.string.security_question_favorite_teacher
    SecurityQuestion.CHILDHOOD_BEST_FRIEND -> R.string.security_question_childhood_best_friend
}
