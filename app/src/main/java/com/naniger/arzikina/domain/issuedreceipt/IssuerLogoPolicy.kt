package com.naniger.arzikina.domain.issuedreceipt

/**
 * Règles du logo d'émetteur, en logique pure (testée unitairement) : limites acceptées et calculs de
 * dimensions. Le traitement réel de l'image (décodage, rotation, encodage) vit côté données, dans
 * `IssuerLogoProcessor`.
 */
object IssuerLogoPolicy {

    /** Taille maximale du fichier source importé (5 Mo, comme la photo de profil). */
    const val MAX_SOURCE_BYTES: Int = 5_000_000

    /** Plus grand côté du logo conservé : assez net pour un PDF A4, léger à stocker et synchroniser. */
    const val MAX_DIMENSION_PX: Int = 512

    /** Garde-fou contre une image gigantesque (« bombe de décompression ») : au-delà, refus. */
    const val MAX_SOURCE_PIXELS: Long = 40_000_000L

    /** Types RÉELS acceptés (détectés dans le contenu, jamais d'après l'extension ni le type déclaré). */
    val ACCEPTED_MIME_TYPES: Set<String> = setOf("image/png", "image/jpeg", "image/webp")

    /** Dimensions finales : ratio conservé (jamais de déformation), jamais d'agrandissement, au moins 1 px par côté. */
    fun targetSize(width: Int, height: Int, maxDimension: Int = MAX_DIMENSION_PX): Pair<Int, Int> {
        require(width > 0 && height > 0 && maxDimension > 0)
        val longest = maxOf(width, height)
        if (longest <= maxDimension) return width to height
        val scale = maxDimension.toDouble() / longest
        return Math.round(width * scale).toInt().coerceAtLeast(1) to Math.round(height * scale).toInt().coerceAtLeast(1)
    }

    /**
     * Facteur de sous-échantillonnage (puissance de 2) pour décoder une grande image sans la charger
     * entièrement en mémoire : le plus grand facteur qui laisse les DEUX côtés au moins égaux à la cible
     * (le redimensionnement final se fait ensuite avec [targetSize]).
     */
    fun sampleSize(width: Int, height: Int, maxDimension: Int = MAX_DIMENSION_PX): Int {
        require(width > 0 && height > 0 && maxDimension > 0)
        var sample = 1
        while (width / (sample * 2) >= maxDimension && height / (sample * 2) >= maxDimension) sample *= 2
        return sample
    }

    /**
     * Type réel d'une image d'après ses premiers octets (« magic numbers »), ou `null` si ce n'est ni
     * PNG, ni JPEG, ni WebP. Un fichier renommé en `.png` mais qui n'en est pas un est donc refusé.
     */
    fun detectMimeType(header: ByteArray): String? {
        fun at(i: Int): Int = if (i < header.size) header[i].toInt() and 0xFF else -1
        return when {
            at(0) == 0x89 && at(1) == 0x50 && at(2) == 0x4E && at(3) == 0x47 &&
                at(4) == 0x0D && at(5) == 0x0A && at(6) == 0x1A && at(7) == 0x0A -> "image/png"
            at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF -> "image/jpeg"
            at(0) == 'R'.code && at(1) == 'I'.code && at(2) == 'F'.code && at(3) == 'F'.code &&
                at(8) == 'W'.code && at(9) == 'E'.code && at(10) == 'B'.code && at(11) == 'P'.code -> "image/webp"
            else -> null
        }
    }

    private val safeFileName = Regex("^[A-Za-z0-9-]{1,64}\\.(png|jpg)$")

    /**
     * Un nom de fichier de logo est TOUJOURS un nom simple généré par l'application. Cette garde
     * empêche toute traversée de répertoire (`../`) si un nom venait un jour d'ailleurs (synchronisation).
     */
    fun isSafeFileName(name: String): Boolean = safeFileName.matches(name)

    /** Taille maximale d'un logo reçu par synchronisation (même plafond que le serveur : 2 Mo). */
    const val MAX_SYNCED_BYTES: Int = 2_000_000

    /**
     * Un logo téléchargé du serveur n'est JAMAIS cru sur parole : nom sûr, taille bornée, et type RÉEL
     * (octets magiques) PNG/JPEG cohérent avec l'extension du nom.
     */
    fun isValidSyncedLogo(fileName: String, bytes: ByteArray): Boolean {
        if (!isSafeFileName(fileName) || bytes.isEmpty() || bytes.size > MAX_SYNCED_BYTES) return false
        val mime = detectMimeType(bytes.copyOf(minOf(bytes.size, 16)))
        return (mime == "image/png" && fileName.endsWith(".png")) ||
            (mime == "image/jpeg" && fileName.endsWith(".jpg"))
    }
}
