package com.arzikina.ne.domain.model

/**
 * Question de sécurité choisie à l'inscription, utilisée pour vérifier
 * l'identité de la personne avant une réinitialisation locale du mot de
 * passe (écran "Mot de passe oublié") — seul mécanisme de preuve d'identité
 * possible en l'absence de tout backend (pas d'e-mail, pas de SMS).
 *
 * Une liste FERMÉE de questions prédéfinies (plutôt qu'un champ libre) :
 * une question inventée par l'utilisateur risque d'être trop faible ("quel
 * est mon prénom ?") ou de se retrouver vide par oubli. Ceci ne remplace pas
 * une vraie question de sécurité côté serveur (facilement contournable par
 * un proche qui connaît la réponse) — proportionné au modèle de menace
 * assumé ailleurs dans ce module (voir `util/PasswordHasher`) : protéger
 * contre un autre utilisateur du même appareil, pas contre une attaque
 * ciblée sophistiquée.
 *
 * Aucun texte affichable ici (même principe que [AuthError]) : c'est la
 * présentation qui associe chaque valeur à sa chaîne localisée
 * (`strings.xml`), pour rester compatible avec la future prise en charge
 * multilingue sans jamais devoir migrer une question déjà stockée en base.
 */
enum class SecurityQuestion {
    FIRST_PET_NAME,
    BIRTH_CITY,
    MOTHER_MAIDEN_NAME,
    FAVORITE_TEACHER,
    CHILDHOOD_BEST_FRIEND
}
