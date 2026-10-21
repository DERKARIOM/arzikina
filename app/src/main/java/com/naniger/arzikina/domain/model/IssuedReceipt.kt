package com.naniger.arzikina.domain.model

/**
 * Reçu ÉMIS par l'utilisateur à l'un de ses clients (utilitaire « Gestion de reçus »).
 *
 * À ne pas confondre avec [Receipt], le justificatif PDF IMPORTÉ depuis une autre application
 * (utilitaire « Justificatifs ») : les deux fonctionnalités sont indépendantes, d'où le préfixe
 * `Issued` partout (tables `issued_receipts`/`issuer_profiles`).
 *
 * Un reçu est un document FIGÉ : [issuer] est une copie des coordonnées de l'émetteur au moment de
 * l'émission, de sorte que modifier plus tard le [IssuerProfile] ne réécrit jamais l'historique.
 *
 * Montants : unité mineure ([Long], voir `util/Money`), jamais de flottant.
 *
 * @property number numéro du reçu (ex. « Recu-2026-001 »). Vide à la création = généré
 * automatiquement par le repository ; IMMUABLE après création (un numéro déjà remis à un client ne
 * doit jamais changer).
 * @property issuedAt date d'émission (millisecondes epoch, début de journée locale).
 * @property amountMinor TOTAL payé. Si [pricing] est renseigné, il est recalculé à partir de
 * celui-ci à l'enregistrement (source de vérité unique) ; sinon c'est le montant saisi.
 * @property pricing détail quantités/prix/remise/taxe, `null` = reçu simple (un seul montant).
 * @property pdfFileName nom du PDF déjà généré (relatif au dossier de stockage des reçus émis),
 * `null` = pas encore généré OU périmé par une modification (voir `IssuedReceiptRepository`).
 */
data class IssuedReceipt(
    val id: Long = 0L,
    val number: String = "",
    val issuedAt: Long,
    val customerName: String,
    val customerPhone: String? = null,
    val description: String,
    val amountMinor: Long,
    val currencyCode: String,
    val paymentMethod: PaymentMethod,
    val transactionReference: String? = null,
    val notes: String? = null,
    val issuedByName: String? = null,
    val issuedByTitle: String? = null,
    val pricing: ReceiptPricing? = null,
    val issuer: IssuerSnapshot,
    val pdfFileName: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

/** Ligne de détail : [quantityMilli] en millièmes (1 unité = 1000, 2,5 kg = 2500). */
data class ReceiptLine(
    val label: String,
    val quantityMilli: Long,
    val unitPriceMinor: Long
)

/** Remise appliquée au sous-total, avant taxe. */
sealed interface ReceiptDiscount {
    data object None : ReceiptDiscount

    /** [basisPoints] : 1 % = 100 (pour accepter 7,5 % = 750). */
    data class Percent(val basisPoints: Int) : ReceiptDiscount

    data class Fixed(val amountMinor: Long) : ReceiptDiscount
}

/**
 * Détail de tarification d'un reçu : [lines] (prix hors taxe), [discount] puis taxe de
 * [taxBasisPoints] (1 % = 100) appliquée sur le montant après remise.
 */
data class ReceiptPricing(
    val lines: List<ReceiptLine>,
    val discount: ReceiptDiscount = ReceiptDiscount.None,
    val taxBasisPoints: Int = 0
)

/** Coordonnées de l'émetteur figées dans un reçu (voir [IssuedReceipt]). */
data class IssuerSnapshot(
    val businessName: String,
    val address: String,
    val phone: String,
    val email: String? = null,
    val taxId: String? = null,
    val logoFileName: String? = null,
    /** Signature figée dans CE reçu (`null` = reçu non signé) : copie du nom, le fichier reste immuable. */
    val signatureFileName: String? = null
)
