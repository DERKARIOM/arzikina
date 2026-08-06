package com.arzikina.ne.domain.repository

import com.arzikina.ne.domain.model.BackupResult
import java.io.InputStream
import java.io.OutputStream

/**
 * Export/import de l'intégralité des données locales (comptes, catégories,
 * transactions, budgets, objectifs d'épargne, préférences) au format JSON.
 *
 * Les flux ([InputStream]/[OutputStream]) plutôt qu'un `Uri` ou un chemin de
 * fichier : ce sont de simples types JVM (pas de dépendance Android), et
 * c'est à la couche presentation de résoudre l'emplacement choisi par
 * l'utilisateur (Storage Access Framework) vers un flux concret. Cette
 * interface ne sait rien d'Android ni du format JSON — seule
 * [com.arzikina.ne.data.repository.BackupRepositoryImpl] en dépend.
 *
 * Prépare la synchronisation cloud future : le même format d'export pourra
 * servir de charge utile à envoyer à un backend, sans changer ce contrat.
 */
interface BackupRepository {
    suspend fun exportBackup(outputStream: OutputStream): BackupResult
    suspend fun importBackup(inputStream: InputStream): BackupResult
}
