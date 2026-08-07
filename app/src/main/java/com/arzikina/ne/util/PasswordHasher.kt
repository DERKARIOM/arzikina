package com.arzikina.ne.util

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hachage et vérification de mot de passe pour l'authentification locale
 * (voir `data/repository/AuthRepositoryImpl`) — un mot de passe en clair
 * n'est JAMAIS écrit en base de données, seul le résultat de [hash] l'est
 * (voir `data/local/entity/UserEntity.passwordHash`).
 *
 * Choix technique — PBKDF2WithHmacSHA256 (API `javax.crypto` du JDK) plutôt
 * que BCrypt/Argon2 : ces derniers offrent une meilleure résistance au
 * calcul sur GPU pour un budget d'itérations équivalent, mais nécessitent
 * une dépendance tierce. PBKDF2 est disponible nativement sur toute cible
 * `minSdk` de ce projet (26+), sans ajout de dépendance, ce qui suffit pour
 * une authentification strictement locale (le scénario à défendre est le
 * vol de l'appareil/de la base SQLite, pas une attaque à grande échelle sur
 * un serveur exposé). Si le besoin de résistance augmente plus tard
 * (ex. lors du passage à une authentification en ligne), le format de sortie
 * inclut déjà l'algorithme et le nombre d'itérations (voir [hash]) : on
 * pourra migrer les mots de passe vers Argon2 utilisateur par utilisateur,
 * à la prochaine connexion réussie, sans casser les comptes existants.
 *
 * Sel aléatoire de 16 octets PAR utilisateur (jamais réutilisé) : empêche
 * une table arc-en-ciel précalculée et garantit que deux utilisateurs avec
 * le même mot de passe ont des hachages différents.
 */
object PasswordHasher {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val FORMAT_PREFIX = "pbkdf2"

    /**
     * 15 000 itérations : très en dessous des ~600 000 recommandées par
     * l'OWASP pour PBKDF2-SHA256 côté serveur, choisi ici pour rester
     * fluide sur des appareils Android d'entrée de gamme (connexion quasi
     * instantanée). Le nombre d'itérations est stocké dans le hachage
     * lui-même (voir [hash]) : l'augmenter plus tard n'invalide pas les
     * mots de passe déjà enregistrés, [verify] relit la valeur utilisée à
     * l'origine pour chaque utilisateur.
     */
    private const val ITERATIONS = 15_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16

    private val secureRandom = SecureRandom()

    /** Retourne une chaîne auto-descriptive `pbkdf2$<itérations>$<sel base64>$<hachage base64>`, prête à stocker telle quelle. */
    fun hash(rawPassword: String): String {
        val salt = ByteArray(SALT_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val derived = deriveKey(rawPassword, salt, ITERATIONS)
        return listOf(
            FORMAT_PREFIX,
            ITERATIONS.toString(),
            Base64.getEncoder().encodeToString(salt),
            Base64.getEncoder().encodeToString(derived)
        ).joinToString("$")
    }

    /** `false` si [rawPassword] ne correspond pas à [encodedHash], ou si ce dernier est mal formé. */
    fun verify(rawPassword: String, encodedHash: String): Boolean {
        val parts = encodedHash.split("$")
        if (parts.size != 4 || parts[0] != FORMAT_PREFIX) return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val salt = runCatching { Base64.getDecoder().decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { Base64.getDecoder().decode(parts[3]) }.getOrNull() ?: return false
        val actual = deriveKey(rawPassword, salt, iterations)
        // Comparaison à temps constant : évite qu'un attaquant local mesure le
        // temps de réponse pour deviner le mot de passe octet par octet.
        return MessageDigest.isEqual(actual, expected)
    }

    private fun deriveKey(rawPassword: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(rawPassword.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
        return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
    }
}
