# Sync Engine — Scénarios de validation manuelle

Le document original (« la demande », section 27) qui listait les 8 scénarios attendus n'est pas
versionné dans ce dépôt — seule sa référence subsiste dans
`docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md`, section 10, point 7. La liste ci-dessous reconstruit un
protocole équivalent à partir des garanties réellement documentées et implémentées (voir sections 8
et 9 de ce même audit) : file `sync_queue`, Last-Write-Wins avec journal `sync_conflicts`,
suppression douce, backfill au login, retry des entrées `FAILED`, déclenchement automatique.

Statut de chaque scénario tenu à jour au fil des exécutions (✅ validé / ❌ bug trouvé / ⏳ pas encore
testé). Entités disponibles pour les tests : `categories`, `savings_goals`, `financial_plans`,
`persons`, `accounts`, `transactions`, `budgets`, `loans`, `loan_payments`, `recurring_transactions`,
`recurring_transaction_occurrences`.

Outils utiles : bouton **Synchroniser maintenant** (Paramètres), indicateur d'état sur `syncRow`,
Database Inspector d'Android Studio (table `sync_queue`, colonnes `status`/`errorMessage`), la
collection Postman `Arzikina-Sync-API` (Push/Pull manuels), et l'accès direct à la base MySQL
(phpMyAdmin/Adminer) pour vérifier l'état réel côté serveur.

---

## 1. Création simple + propagation

1. Sur l'appareil A (connecté au serveur de sync), crée une nouvelle donnée (ex. une catégorie).
2. Ouvre Paramètres → vérifie que l'indicateur passe par « N en attente » puis « À jour » (push
   automatique ou manuel).
3. Vérifie côté serveur (phpMyAdmin/Postman Pull) que la ligne existe bien, avec un `id` UUID et
   `version = 1`.
4. Sur un second appareil (ou après déconnexion/reconnexion du même compte, ce qui revient à un
   pull « depuis zéro » si `SyncCursorStore` est réinitialisé), déclenche une synchronisation et
   vérifie que la donnée apparaît.

**Couvre aussi implicitement** : le rattrapage (backfill) déjà validé sur les catégories par défaut.

**Spécifique à `budgets`** (référence croisée vers `categories`, voir `BudgetSyncPayload.kt` et
`SyncEngineImpl.applyBudgetServerState`, étape 18) : avant de tester, supprime la contrainte réelle
`fk_budgets_category` côté MySQL (voir `database/migrations/001_initial_schema.sql`) :
```sql
ALTER TABLE budgets DROP FOREIGN KEY fk_budgets_category;
```
Crée ensuite un budget sur une catégorie encore jamais synchronisée (les deux dans la foulée, sans
synchroniser entre les deux). Après synchronisation, vérifie côté serveur (Postman Pull `budgets`)
que `categorySyncId` correspond bien au `syncId` réel de la catégorie — jamais une chaîne vide ni
l'`id` local.

**Spécifique à `loans`/`loan_payments`** (références croisées multiples, voir `LoanSyncPayload.kt`
et `SyncEngineImpl.applyLoanServerState`/`applyLoanPaymentServerState`, étape 19) : avant de tester,
supprime les 4 contraintes réelles côté MySQL (voir `database/migrations/001_initial_schema.sql`) :
```sql
ALTER TABLE loans DROP FOREIGN KEY fk_loans_person;
ALTER TABLE loans DROP FOREIGN KEY fk_loans_account;
ALTER TABLE loan_payments DROP FOREIGN KEY fk_loan_payments_loan;
ALTER TABLE loan_payments DROP FOREIGN KEY fk_loan_payments_account;
```
Crée un prêt/emprunt (génère atomiquement sa transaction de décaissement), puis un remboursement
(génère sa propre transaction ET met à jour le prêt parent). Synchronise, puis vérifie côté serveur
(Postman Pull `loans` et `loan_payments`) que `personSyncId`/`accountSyncId`/`transactionSyncId`
(sur `loans`) et `loanSyncId`/`accountSyncId`/`transactionSyncId` (sur `loan_payments`) correspondent
tous aux vrais `syncId` — jamais une chaîne vide. Vérifie aussi que le remboursement a bien fait
progresser `amountRepaid`/`status` du prêt CÔTÉ SERVEUR (une `UPDATE` distincte de `loans`, `version`
incrémentée).

**Spécifique à `recurring_transactions`/`recurring_transaction_occurrences`** (références croisées,
voir `RecurringTransactionSyncPayload.kt` et
`SyncEngineImpl.applyRecurringTransactionServerState`/`applyRecurringTransactionOccurrenceServerState`,
étape 20) : avant de tester, supprime les 3 contraintes réelles côté MySQL (voir
`database/migrations/001_initial_schema.sql`, section 6 "Automatisation") :
```sql
ALTER TABLE recurring_transactions DROP FOREIGN KEY fk_recurring_transactions_account;
ALTER TABLE recurring_transactions DROP FOREIGN KEY fk_recurring_transactions_category;
ALTER TABLE recurring_transaction_occurrences DROP FOREIGN KEY fk_occurrences_rule;
```
Crée une règle récurrente (compte + catégorie encore jamais synchronisés, dans la foulée, sans
synchroniser entre les deux), attends (ou force) la génération d'au moins une occurrence `PENDING`,
puis accepte-la (crée sa transaction). Synchronise, puis vérifie côté serveur (Postman Pull
`recurring_transactions` et `recurring_transaction_occurrences`) que `accountSyncId`/`categorySyncId`
(sur la règle) et `recurringTransactionSyncId`/`transactionSyncId` (sur l'occurrence acceptée)
correspondent tous aux vrais `syncId` — jamais une chaîne vide. Vérifie aussi que
`nextExecutionDate`/`isActive` de la règle apparaissent bien à jour CÔTÉ SERVEUR après la génération
(une `UPDATE` distincte de `recurring_transactions`, `version` incrémentée) — c'est un effet de bord
de `generateMissingOccurrences`, pas une action utilisateur directe, facile à oublier de tester.

---

## 2. Modification simple + propagation

1. Modifie une donnée déjà synchronisée (nom, montant...) sur l'appareil A.
2. Synchronise, vérifie côté serveur que `version` a progressé (`version + 1`) et que les champs
   modifiés sont à jour.
3. Synchronise un second appareil/session déjà en possession de l'ancienne version, vérifie qu'il
   reçoit la modification.

---

## 3. Suppression douce + propagation

1. Supprime une donnée synchronisée sur l'appareil A.
2. Vérifie qu'elle disparaît immédiatement de l'écran local (liste active).
3. Synchronise, puis vérifie côté serveur : la ligne existe TOUJOURS en base, mais avec
   `deleted_at` renseigné — jamais un `DELETE` SQL réel.
4. Synchronise un second appareil qui avait cette donnée : elle doit disparaître de sa liste active
   après le pull.
5. **Spécifique à `financial_plans`** : vérifie en plus que les dépenses prévues
   (`financial_plan_items`) associées disparaissent aussi de l'écran (suppression douce en cascade
   explicite, voir `FinancialPlanRepositoryImpl.deletePlan`) — cette table n'est pas synchronisée,
   la vérification se fait uniquement en local.
6. **Spécifique à `accounts`** (cascade la plus complexe, voir `AccountRepositoryImpl.deleteAccount`,
   étape 16.1, mise à jour étape 19.5) : avant de supprimer, crée un compte avec au moins une
   transaction simple, un prêt dont ce compte est le compte principal, ET un remboursement fait
   DEPUIS ce compte pour un prêt dont le compte principal est différent. Après suppression, vérifie
   en base (Database Inspector) :
   - `accounts` : la ligne existe TOUJOURS, avec `deletedAt` renseigné (jamais un `DELETE`).
   - `transactions`/`loans`/`loan_payments` liés : depuis l'étape 19, tous SOFT-supprimés (`deletedAt`
     renseigné, ligne toujours présente) — plus une suppression physique, ces trois tables sont
     désormais synchronisées.
   - le prêt dont le compte principal était DIFFÉRENT : son `amountRepaid`/`remainingAmount`/`status`
     ont bien été recalculés (le remboursement fait depuis le compte supprimé ne compte plus), ET
     cette mise à jour apparaît côté serveur après synchronisation (`version` incrémentée).
   - **Régression critique à vérifier** (bug réel trouvé et corrigé à l'étape 19.5b, voir
     `AccountDao.getByIdIncludingDeleted`) : AVANT ce correctif, synchroniser après une suppression
     de compte avec des transactions plantait l'enfilage (`error("Compte introuvable...")`) — la
     synchronisation doit maintenant se terminer sur "À jour", sans entrée `FAILED` avec ce message
     dans `sync_queue`.
7. **Spécifique à `persons`** (étape 19.5) : avant de supprimer, crée une personne avec un prêt et au
   moins un remboursement. Après suppression, vérifie que `persons`/`loans`/`loan_payments`/
   `transactions` liés sont tous SOFT-supprimés et bien enfilés (aucune erreur "Personne
   introuvable" côté `sync_queue`, même régression que le point 6).
8. **Spécifique à `recurring_transactions`** (étape 20.4, voir
   `RecurringTransactionRepositoryImpl.deleteRecurringTransaction`) : avant de supprimer, crée une
   règle récurrente avec au moins une occurrence déjà acceptée (donc avec sa propre transaction) ET
   une occurrence encore `PENDING`. Après suppression de la règle, vérifie en base (Database
   Inspector) :
   - `recurring_transactions` : la ligne existe TOUJOURS, avec `deletedAt` renseigné.
   - `recurring_transaction_occurrences` : TOUTES les occurrences (traitées ET `PENDING`) sont
     SOFT-supprimées — la cascade SQLite `CASCADE` ne se déclenche jamais sur cet `UPDATE`, voir la
     KDoc de `RecurringTransactionDao.softDeleteById`.
   - la transaction liée à l'occurrence déjà acceptée : SOFT-supprimée elle aussi (voir
     `deleteRecurringTransaction`, nettoyage explicite AVANT la règle elle-même).
   - synchronise : aucune entrée `FAILED` dans `sync_queue` (même régression potentielle que les
     points 6/7 si un résolveur utilisait `getById` au lieu de `getByIdIncludingDeleted` — ici non
     applicable, `deleteRecurringTransaction` enfile chaque ligne AVANT que la suivante ne la
     référence comme déjà supprimée, à revérifier si le code évolue).

---

## 4. Conflit (Last-Write-Wins)

1. Sur l'appareil A, modifie une donnée déjà synchronisée, mais SANS synchroniser tout de suite
   (reste hors-ligne ou n'appuie pas sur le bouton).
2. Sur un second appareil B (même donnée, déjà pullée avant l'étape 1), modifie la MÊME donnée
   différemment, et synchronise B en premier.
3. Synchronise ensuite A : son `baseVersion` ne correspond plus à la version serveur (déjà avancée
   par B) → conflit détecté.
4. Vérifie côté serveur (table `sync_conflicts`) qu'une ligne a bien été journalisée (payload
   perdant + gagnant).
5. Vérifie que l'appareil dont l'écriture est la plus ANCIENNE (`updatedAt` le plus petit) adopte
   bien l'état du serveur (celui de l'autre appareil) après le push — pas de valeur perdue
   silencieusement, l'écran doit refléter la version gagnante.

---

## 5. Mode hors-ligne prolongé

1. Coupe le réseau de l'appareil (mode avion).
2. Crée/modifie/supprime plusieurs données (idéalement sur les 3 entités câblées).
3. Vérifie via le Database Inspector que `sync_queue` contient bien une entrée `PENDING` par
   écriture, jamais perdue.
4. Réactive le réseau, vérifie que la synchronisation se déclenche automatiquement (voir scénario 6
   ci-dessous) et que toutes les entrées passent à `SYNCED`.

---

## 6. Déclenchement automatique (sans toucher le bouton)

1. Mets l'appareil hors ligne, crée une donnée.
2. Réactive le réseau SANS ouvrir l'écran Paramètres ni appuyer sur « Synchroniser maintenant ».
3. Attends quelques secondes, puis ouvre Paramètres : l'indicateur doit déjà afficher « À jour »
   (déclenché par `SyncConnectivityObserver`, voir `work/`).
4. Optionnel : vérifie qu'un cycle périodique (toutes les 6h, `SyncWorkScheduler`) se déclenche
   aussi sans action utilisateur — difficile à observer sans attendre, à considérer comme déjà
   couvert par la lecture de code plutôt qu'un test chronométré.

---

## 7. Reprise après échec (déjà rencontré en pratique)

1. Coupe volontairement l'accès au serveur (mauvaise URL temporaire, ou coupe le serveur PHP) puis
   crée une donnée et synchronise : l'entrée doit passer `FAILED` (voir Database Inspector,
   `errorMessage` renseigné) et l'indicateur afficher « Erreur de synchronisation ».
2. Rétablis l'accès au serveur.
3. Synchronise à nouveau (bouton ou automatique) : l'entrée `FAILED` doit être retentée et passer
   `SYNCED` — voir le correctif de `SyncEngineImpl.pushPendingChanges` (`PENDING` + `FAILED`).

Déjà validé une première fois involontairement (déploiement serveur en retard sur
`financial_plans`) — à rejouer une fois plus tard pour confirmer que ce n'était pas un hasard.

### Addendum — deux bugs réels trouvés lors du câblage de `transactions` (étape 17.6)

1. **`SYNCING` bloqué indéfiniment** : `SyncEngineImpl.pushBatch` ne rattrapait que `IOException`
   autour de l'appel réseau. Une réponse serveur non-JSON valide (ex. avertissement PHP mélangé au
   corps JSON) levait une `SerializationException`, jamais rattrapée — les entrées restaient
   `SYNCING` pour toujours (exclues de `isEligibleForRetry`, qui ne relit que `PENDING`/`FAILED`).
   Corrigé : `catch (e: Exception)` (avec `catch (e: CancellationException) { throw e }` avant, pour
   ne pas casser l'annulation de coroutine).
2. **`accountSyncId` manquant côté réponse serveur** : `toCamelCaseRow` (côté PHP,
   `utils/case_convert.php`) convertissait mécaniquement `account_id` → `accountId`, jamais
   `accountSyncId` — alors que `TransactionServerStateDto` (côté Kotlin) exige ce nom de champ (voir
   `entity_sync_configs.php`, colonne `account_id` / clé payload `accountSyncId`). Toutes les
   entités précédentes coïncidaient par hasard (`color_argb` → `colorArgb` des deux façons) ;
   `transactions` est la première à casser cette coïncidence. Corrigé : `toCamelCaseRow` prend
   désormais `$config` en paramètre et utilise `$col['payload']` pour les colonnes spécifiques à
   l'entité.

À revalider : relancer une synchronisation complète de `transactions` (l'app affichait « Erreur de
synchronisation » à cause du bug 2 avant ce correctif) et confirmer le retour à « À jour ».

---

## 8. Isolation multi-utilisateur

1. Crée un second compte de test côté serveur (voir la procédure SQL utilisée pour le premier).
2. Connecte l'app à ce second compte (déconnexion du premier, connexion au second).
3. Vérifie que AUCUNE donnée du premier compte n'apparaît après un pull complet.
4. Crée une donnée sous ce second compte, synchronise, vérifie côté serveur qu'elle porte bien le
   `user_id` du second compte — jamais celui du premier, même si le payload envoyé ne contient
   normalement pas ce champ (voir la garde `requireAuthenticatedUser` côté serveur, qui ignore
   toujours tout `userId` reçu dans le corps de la requête).

---

## Suivi

| # | Scénario | Statut | Notes |
|---|----------|--------|-------|
| 1 | Création + propagation | ✅ (categories/savings_goals/financial_plans/persons/accounts/transactions/budgets/loans/loan_payments) — ⏳ `recurring_transactions`/`recurring_transaction_occurrences` | Étape 20 : voir l'addendum "Spécifique à `recurring_transactions`/`recurring_transaction_occurrences`" — nécessite les 3 `ALTER TABLE ... DROP FOREIGN KEY` avant test |
| 2 | Modification + propagation | ✅ | |
| 3 | Suppression douce + propagation | ✅ (categories/.../accounts/persons pré-étape 20) — ⏳ point 8 (recurring_transactions, étape 20.4) | Régression critique déjà revalidée : `getByIdIncludingDeleted` (étape 19.5b) |
| 4 | Conflit (LWW) | ✅ | |
| 5 | Mode hors-ligne prolongé | ✅ | |
| 6 | Déclenchement automatique | ✅ | |
| 7 | Reprise après échec | ✅ (involontaire) | Bug trouvé et corrigé — voir `fix/sync-engine-retry-failed-entries` |
| 8 | Isolation multi-utilisateur | ✅ | |
