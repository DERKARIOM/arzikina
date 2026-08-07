package com.arzikina.ne.domain.repository

import com.arzikina.ne.domain.model.AuthResult
import com.arzikina.ne.domain.model.SocialAuthProvider
import com.arzikina.ne.domain.model.User

/**
 * Point d'extension prévu pour une future connexion Google/Apple/Facebook —
 * NON implémenté aujourd'hui (aucune classe n'implémente cette interface,
 * volontairement : voir instructions projet, "anticiper sans complexifier
 * maintenant"). Ne pas ajouter de binding Hilt tant qu'une implémentation
 * réelle n'existe pas — un binding vers rien ferait échouer la compilation.
 *
 * Délibérément séparée de [AuthRepository] plutôt qu'ajoutée dessus :
 * l'authentification LOCALE (mot de passe + question de sécurité) et la
 * connexion SOCIALE sont deux mécanismes de preuve d'identité différents,
 * mais qui doivent aboutir exactement au même résultat pour le reste de
 * l'app — un [User] local existant, avec une session démarrée via
 * [SessionManager.startSession] — pour que le Dashboard, les Comptes, les
 * Transactions, etc. ne fassent jamais la différence entre les deux.
 *
 * Une future implémentation devrait : 1) authentifier via le SDK du
 * fournisseur choisi, 2) trouver ou créer un [User] local correspondant
 * (par e-mail, voir [AuthRepository.isEmailAvailable] et
 * [com.arzikina.ne.data.local.database.NewUserDefaultDataSeeder] pour le
 * peuplement des données par défaut d'un nouveau compte), 3) appeler
 * [SessionManager.startSession] — exactement comme le fait
 * [AuthRepository.login] aujourd'hui pour la connexion locale.
 */
interface SocialAuthRepository {
    suspend fun signIn(provider: SocialAuthProvider): AuthResult<User>
}
