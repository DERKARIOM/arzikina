package com.arzikina.ne.util

/**
 * Règles de validation de FORMAT pour l'authentification (champs
 * obligatoires, format e-mail, longueur du mot de passe, correspondance des
 * mots de passe...) — délibérément séparées de la vérification d'UNICITÉ
 * (nom d'utilisateur / e-mail déjà pris), qui nécessite un accès à la base
 * de données et reste du ressort de
 * [com.arzikina.ne.domain.repository.AuthRepository].
 *
 * Fonctions pures, sans dépendance Android : utilisables aussi bien par les
 * ViewModels (retour instantané pendant la saisie, avant tout appel
 * réseau/DB) que par `AuthRepositoryImpl` (filet de sécurité final avant
 * d'écrire en base), sans dupliquer les règles à deux endroits.
 */
object AuthValidator {

    /** Volontairement permissif (pas de RFC 5322 complet) : suffisant pour rejeter les erreurs de saisie évidentes. */
    private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    /** Lettres, chiffres, point et underscore uniquement — évite les noms d'utilisateur ambigus dans une future URL de profil public. */
    private val USERNAME_REGEX = Regex("^[a-zA-Z0-9._]+$")

    const val MIN_USERNAME_LENGTH = 3
    const val MAX_USERNAME_LENGTH = 30
    const val MIN_PASSWORD_LENGTH = 8
    const val MIN_SECURITY_ANSWER_LENGTH = 2

    fun isValidEmail(email: String): Boolean = EMAIL_REGEX.matches(email.trim())

    fun isValidUsername(username: String): Boolean {
        val trimmed = username.trim()
        return trimmed.length in MIN_USERNAME_LENGTH..MAX_USERNAME_LENGTH && USERNAME_REGEX.matches(trimmed)
    }

    fun isPasswordLongEnough(password: String): Boolean = password.length >= MIN_PASSWORD_LENGTH

    fun doPasswordsMatch(password: String, confirmation: String): Boolean = password == confirmation

    fun isSecurityAnswerLongEnough(answer: String): Boolean =
        normalizeSecurityAnswer(answer).length >= MIN_SECURITY_ANSWER_LENGTH

    /**
     * Normalisation (espaces + casse) appliquée à LA FOIS à l'inscription
     * (avant hachage, voir `AuthRepositoryImpl.register`) et à la
     * réinitialisation (avant vérification) : une réponse ne doit pas
     * échouer juste parce qu'elle a été saisie "Paris" une fois et "paris "
     * l'autre — la sécurité de cette question repose sur la CONNAISSANCE de
     * la réponse, pas sur sa mise en forme exacte.
     */
    fun normalizeSecurityAnswer(answer: String): String = answer.trim().lowercase()
}
