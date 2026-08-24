package com.arzikina.ne.data.security

/**
 * Chiffrement AES/GCM du numéro complet et du CVV d'une carte de crédit (voir
 * `domain/model/AccountType.CREDIT_CARD`), via une clé protégée par l'Android Keystore : la clé
 * elle-même n'existe JAMAIS en clair côté application ni en base — seul le système d'exploitation
 * (matériel sécurisé du téléphone quand disponible) peut l'utiliser pour chiffrer/déchiffrer.
 *
 * Délègue le mécanisme cryptographique à [AesGcmKeystoreCipher] (voir sa KDoc) — ne conserve ici
 * que l'alias de clé Keystore, DISTINCT de celui de [TokenCipher] (isolation des clés par usage).
 *
 * ATTENTION — décision assumée, PAS la pratique recommandée par défaut : conserver un CVV, même
 * chiffré, va à l'encontre de la norme PCI-DSS (interdiction de conserver le CVV après la
 * vérification initiale d'une carte). Ce choix a été explicitement demandé et confirmé après
 * avertissement (voir historique du projet, fonctionnalité "afficher le numéro et le CVV depuis
 * le bouton de la carte") : Arzikina ne traite aucun paiement et reste un usage strictement local,
 * mais toute évolution future vers un vrai service de paiement DEVRA revoir ce choix.
 *
 * Objet distinct de [com.arzikina.ne.util.PasswordHasher] : celui-ci fait du hachage à SENS UNIQUE
 * (jamais besoin de retrouver le mot de passe en clair) — ici on a explicitement besoin de pouvoir
 * déchiffrer à la demande de l'utilisateur, un besoin différent qui appelle un mécanisme différent.
 */
object CardCipher {
    private const val KEY_ALIAS = "arzikina_card_secrets_key"
    private val cipher = AesGcmKeystoreCipher(KEY_ALIAS)

    fun encrypt(plainText: String): AesGcmKeystoreCipher.Encrypted = cipher.encrypt(plainText)

    fun decrypt(ciphertextBase64: String, ivBase64: String): String = cipher.decrypt(ciphertextBase64, ivBase64)
}
