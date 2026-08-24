package com.arzikina.ne.data.security

/**
 * Chiffrement AES/GCM du token de session émis par le serveur de synchronisation (voir
 * `server/api/auth/login.php` et `domain/repository/SyncAuthRepository`), via une clé Android
 * Keystore DISTINCTE de celle de [CardCipher] (voir la KDoc de [AesGcmKeystoreCipher] pour le
 * raisonnement sur l'isolation des clés par usage).
 *
 * Le token chiffré (+ son IV) est persisté par `SyncAuthStore` dans un DataStore dédié —
 * jamais en clair, jamais dans Room, jamais journalisé (voir `data/repository/SyncAuthStore.kt`).
 */
object TokenCipher {
    private const val KEY_ALIAS = "arzikina_sync_token_key"
    private val cipher = AesGcmKeystoreCipher(KEY_ALIAS)

    fun encrypt(plainText: String): AesGcmKeystoreCipher.Encrypted = cipher.encrypt(plainText)

    fun decrypt(ciphertextBase64: String, ivBase64: String): String = cipher.decrypt(ciphertextBase64, ivBase64)
}
