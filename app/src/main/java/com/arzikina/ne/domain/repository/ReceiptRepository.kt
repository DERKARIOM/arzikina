package com.arzikina.ne.domain.repository

import com.arzikina.ne.domain.model.Receipt
import kotlinx.coroutines.flow.Flow

/**
 * Contrat d'accès aux données des reçus PDF (voir [Receipt]) — cahier des charges "Gestion des
 * reçus". Voir [AccountRepository] pour le raisonnement derrière cette séparation
 * domaine/implémentation.
 *
 * Aucun type Android (`Uri`, `Context`...) dans ce contrat — [importReceipt] prend un [String] (voir
 * sa doc), même principe que le reste des interfaces `domain/repository` de ce projet : la copie
 * physique réelle du fichier (voir `data/receipts/ReceiptFileStorage`) reste un détail
 * d'implémentation propre à `ReceiptRepositoryImpl`, jamais exposé ici.
 */
interface ReceiptRepository {

    /** Flux réactif de tous les reçus de l'utilisateur connecté, du plus récent au plus ancien
     * (voir `ReceiptDao.observeAllForUser`). */
    fun observeReceipts(): Flow<List<Receipt>>

    suspend fun getReceipt(id: Long): Receipt?

    /**
     * Nom d'affichage d'origine de [sourceUri] (voir `ReceiptFileStorage.queryDisplayName`) — `null`
     * si le fournisseur de contenu source ne l'expose pas. Exposé séparément d'[importReceipt]
     * (plutôt que résolu silencieusement à l'intérieur) : l'appelant (voir
     * `ReceiptsViewModel.importReceipt`) doit pouvoir choisir SON propre nom de repli localisé
     * (`getString(...)`, une responsabilité de présentation) si `null` est retourné — cette interface
     * ne connaît elle-même aucune ressource `strings.xml`.
     */
    suspend fun resolveDisplayName(sourceUri: String): String?

    /**
     * Point d'entrée UNIQUE pour un NOUVEAU reçu, qu'il provienne du partage Android (`ACTION_SEND`)
     * ou d'un import manuel (sélecteur de fichiers) — cahier des charges sections 2/14. Copie
     * intégralement le fichier désigné par [sourceUri] dans le stockage privé de l'application (voir
     * `ReceiptFileStorage.copyToPrivateStorage` : jamais une dépendance à l'URI temporaire
     * elle-même) PUIS enregistre ses métadonnées.
     *
     * @param sourceUri représentation texte de l'URI source (`Uri.toString()` côté appelant) —
     * jamais de type `android.net.Uri` exposé par cette interface, voir sa doc de tête.
     * @param displayName nom affiché initial (voir [Receipt.fileName]), modifiable ensuite via
     * [saveReceipt].
     * @param mimeType type MIME réel du fichier (cahier des charges section 3).
     * @param sourceApp/[sourceName] voir [Receipt] — `null` si non déterminable, jamais inventé
     * (cahier des charges section 3 : "ne pas inventer la provenance").
     *
     * Retourne l'id définitif du reçu créé.
     */
    suspend fun importReceipt(
        sourceUri: String,
        displayName: String,
        mimeType: String,
        sourceApp: String?,
        sourceName: String?
    ): Long

    /**
     * Met à jour les métadonnées d'un reçu EXISTANT (utilisé pour "Renommer le reçu", voir cahier
     * des charges section 10 — ne modifie alors que [Receipt.fileName]/[Receipt.updatedAt], jamais
     * [Receipt.localPath] ni le fichier physique). Pour créer un nouveau reçu, voir [importReceipt]
     * (seul point d'entrée pour une création, jamais cette méthode).
     *
     * @throws IllegalStateException si [Receipt.id] ne correspond à aucun reçu existant.
     * Retourne l'id du reçu mis à jour.
     */
    suspend fun saveReceipt(receipt: Receipt): Long

    /**
     * Supprime le reçu : la ligne Room ET son fichier physique associé (voir
     * `ReceiptFileStorage.deleteFile`) — cahier des charges section 10 : "éviter les fichiers
     * orphelins dans le stockage". Ne fait rien si [id] ne correspond à aucun reçu existant (déjà
     * supprimé, appel idempotent).
     */
    suspend fun deleteReceipt(id: Long)
}
