package com.arzikina.ne.domain.model

/**
 * Un reçu PDF centralisé dans Arsikina — voir cahier des charges "Gestion des reçus". Deux origines
 * possibles, jamais distinguées après coup (même table, même modèle) : reçu depuis une autre
 * application via le partage Android (`ACTION_SEND`), ou importé manuellement par l'utilisateur
 * (sélecteur de fichiers) — voir `ReceiptRepository.saveSharedReceipt`/`saveImportedReceipt`.
 *
 * @param fileName Nom AFFICHÉ, renommable par l'utilisateur (voir `ReceiptRepository.renameReceipt`)
 * — totalement indépendant du nom physique réel sur le disque (voir [localPath]), qui n'est JAMAIS
 * modifié après création : renommer un reçu ne touche donc jamais le fichier lui-même, seulement
 * cette métadonnée.
 * @param localPath Chemin RELATIF au répertoire privé de stockage des reçus (voir
 * `work/receipts/ReceiptFileStorage`, pas un chemin absolu : un chemin absolu dépendrait de
 * l'installation courante et casserait après une restauration de sauvegarde sur un autre appareil.
 * Nom physique généré (UUID), jamais dérivé de [fileName] — évite toute collision ou caractère
 * invalide, voir cahier des charges section 17.
 * @param receivedAt date de réception/import (millis epoch) — c'est la date AFFICHÉE et utilisée
 * pour le tri/regroupement, distincte de [createdAt]/[updatedAt] (purement techniques, même
 * convention que les autres entités du projet, voir [RecurringTransaction]).
 * @param sourceApp nom de package de l'application source (ex. "com.airtel.money"), si Android a pu
 * le fournir de façon fiable — `null` sinon, jamais déduit autrement.
 * @param sourceName nom lisible de l'application source (résolu depuis [sourceApp] via
 * `PackageManager`, voir `ReceiptRepository`) — `null` si non disponible ; l'affichage "Provenance
 * inconnue" est de la responsabilité de la couche présentation, jamais stocké tel quel ici (cahier
 * des charges : "ne pas inventer la provenance").
 * @param amountMinor montant du reçu en unité mineure, `null` dans cette première version (aucune
 * extraction automatique du contenu du PDF, voir cahier des charges section 6) — champ réservé pour
 * une évolution future, jamais renseigné pour l'instant.
 */
data class Receipt(
    val id: Long = 0L,
    val fileName: String,
    val localPath: String,
    val receivedAt: Long,
    val fileSize: Long,
    val mimeType: String,
    val sourceApp: String? = null,
    val sourceName: String? = null,
    val amountMinor: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)
