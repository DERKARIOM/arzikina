# Sync Engine — Scénarios de validation manuelle

Le document original (« la demande », section 27) qui listait les 8 scénarios attendus n'est pas
versionné dans ce dépôt — seule sa référence subsiste dans
`docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md`, section 10, point 7. La liste ci-dessous reconstruit un
protocole équivalent à partir des garanties réellement documentées et implémentées (voir sections 8
et 9 de ce même audit) : file `sync_queue`, Last-Write-Wins avec journal `sync_conflicts`,
suppression douce, backfill au login, retry des entrées `FAILED`, déclenchement automatique.

Statut de chaque scénario tenu à jour au fil des exécutions (✅ validé / ❌ bug trouvé / ⏳ pas encore
testé). Entités disponibles pour les tests : `categories`, `savings_goals`, `financial_plans`.

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
| 1 | Création + propagation | ✅ | |
| 2 | Modification + propagation | ✅ | |
| 3 | Suppression douce + propagation | ✅ | |
| 4 | Conflit (LWW) | ✅ | |
| 5 | Mode hors-ligne prolongé | ✅ | |
| 6 | Déclenchement automatique | ✅ | |
| 7 | Reprise après échec | ✅ (involontaire) | Bug trouvé et corrigé — voir `fix/sync-engine-retry-failed-entries` |
| 8 | Isolation multi-utilisateur | ✅ | |
