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
 * Chiffrement AES/GCM générique adossé à l'Android Keystore : la clé ne quitte JAMAIS le
 * matériel sécurisé du téléphone (quand disponible) ni même la mémoire de l'application — celle-ci
 * ne manipule que le texte en clair en entrée et le couple (chiffré, IV) en sortie.
 *
 * Extrait de la logique auparavant DUPLIQUÉE entre [CardCipher] (secrets de carte bancaire) et
 * [TokenCipher] (token de session du serveur de synchronisation) — deux besoins métier distincts,
 * mais exactement le même mécanisme cryptographique ; une seule implémentation (voir instructions
 * projet : "évite absolument le code dupliqué"). Chaque appelant fournit son propre [keyAlias] :
 * deux clés Keystore totalement indépendantes, pour qu'une compromission ou une réinitialisation
 * de l'une n'affecte jamais l'autre.
 *
 * Pourquoi pas `androidx.security:security-crypto` (EncryptedSharedPreferences) ? Son statut est
 * ambigu au moment où ce code est écrit : toutes ses API ont été marquées "Deprecated in favour of
 * ... direct use of Android Keystore" dans les versions alpha/beta 2025, mention retirée du
 * changelog de la version stable 1.1.0 sans confirmation claire d'une dé-dépréciation officielle
 * (voir docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md si une section dédiée y est ajoutée). Plutôt que de
 * dépendre d'une lib au statut flou, ce fichier fait directement ce que Google recommandait dans
 * cette même mention : utiliser l'Android Keystore en direct — zéro dépendance supplémentaire, et
 * c'est exactement ce que faisait déjà [CardCipher] avant ce refactor.
 */
class AesGcmKeystoreCipher(private val keyAlias: String) {

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
     *  si l'app est réinstallée ou les données effacées, auquel cas les secrets déjà chiffrés avec
     *  l'ancienne clé deviennent illisibles — comportement attendu dans ce cas. */
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val KEY_SIZE_BITS = 256
    }
}
