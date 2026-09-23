package com.naniger.arzikina.domain.model

/**
 * Fournisseurs de connexion sociale envisagés pour une future étape (aucun
 * SDK Google/Apple/Facebook n'est ajouté au projet tant que cette
 * fonctionnalité n'est pas réellement développée — voir instructions projet,
 * "anticiper sans complexifier maintenant").
 *
 * Voir [com.naniger.arzikina.domain.repository.SocialAuthRepository].
 */
enum class SocialAuthProvider { GOOGLE, APPLE, FACEBOOK }
