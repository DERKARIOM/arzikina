package com.arzikina.ne.domain.model

/**
 * Utilisateur de l'application, tel qu'exposé au reste du domaine et à la
 * présentation (écran Profil, filtrage des données par [id], etc.).
 *
 * Ne contient AUCUNE information d'identification (mot de passe, haché ou
 * non) : ces détails restent strictement confinés à la couche data (voir
 * `data/local/entity/UserEntity` et `data/repository/AuthRepositoryImpl`).
 * Le domaine et la présentation n'ont jamais besoin de manipuler un mot de
 * passe, seulement de déclencher des opérations (connexion, changement de
 * mot de passe...) via [com.arzikina.ne.domain.repository.AuthRepository].
 *
 * [id] est la clé étrangère `userId` que porteront toutes les autres tables
 * (comptes, transactions, catégories, budgets, objectifs d'épargne...) afin
 * d'isoler complètement les données de chaque utilisateur sur un même
 * appareil.
 */
data class User(
    val id: Long,
    val fullName: String,
    val username: String,
    val email: String,
    val phoneNumber: String?,
    /** URI locale (voir Storage Access Framework) de la photo de profil, ou `null`. */
    val profilePhotoUri: String?,
    val createdAt: Long
)
