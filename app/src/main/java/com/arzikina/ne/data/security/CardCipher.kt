package com.arzikina.ne.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Chiffrement AES/GCM du numéro complet et du CVV d'une carte de crédit (voir
 * `domain/model/AccountType.CREDIT_CARD`), via une clé protégée par l'Android Keystore : la clé
 * elle-même n'existe JAMAIS en clair côté application ni en base — seul le système d'exploitation
 * (matériel sécurisé du téléphone quand disponible) peut l'utiliser pour chiffrer/déchiffrer.
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
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "arzikina_card_secrets_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val KEY_SIZE_BITS = 256

    data class Encrypted(val ciphertextBase64: String, val ivBase64: String)

    fun encrypt(plainText: String): Encrypted {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Encrypted(
            ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        )
    }

    fun decrypt(ciphertextBase64: String, ivBase64: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val ciphertext = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    /** Clé générée une seule fois puis réutilisée (voir Android Keystore) : régénérée uniquement
     * si l'app est réinstallée ou les données effacées, auquel cas les secrets déjà chiffrés
     * deviennent illisibles — cohérent avec une réinstallation qui repart d'une base vide. */
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}
