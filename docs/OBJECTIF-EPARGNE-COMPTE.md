# Objectif d'épargne = type de compte

L'objectif d'épargne n'est plus un utilitaire séparé. C'est un **compte** dont le type vaut
`SAVINGS_GOAL`, avec un montant cible en plus.

## 1. Modèle de données

| Champ | Android (Room `accounts`) | MySQL (`accounts`) | Payload sync (`push`/`pull`) |
|---|---|---|---|
| Type | `type = SAVINGS_GOAL` (enum `AccountType`) | `type = 'SAVINGS_GOAL'` | `type` |
| Montant cible (unités mineures, > 0) | `savingsTargetAmount` (nullable) | `savings_target_amount BIGINT NULL` | `savingsTargetAmount` |
| Description facultative | `savingsDescription` (nullable) | `savings_description TEXT NULL` | `savingsDescription` |

- Aucune nouvelle table. Le solde d'un objectif est calculé comme pour tous les comptes :
  `solde initial + revenus − dépenses − transferts sortants + transferts entrants`
  (`computeCurrentBalances` sur Android, `sync/account_balances.php` côté serveur).
- Hors `SAVINGS_GOAL`, les deux colonnes valent toujours `NULL`.
- La progression n'est **jamais stockée**. Elle est recalculée depuis le solde courant
  (`util/SavingsGoalProgress.kt`) :
  - `progression % = floor(max(solde, 0) / cible × 100)`, bornée à `0..100` ;
  - au-delà de la cible : barre pleine (100 %) et statut « Objectif dépassé de X » (jamais « 120 % ») ;
  - solde négatif : épargné = 0, progression = 0 % ;
  - cible absente ou ≤ 0 : aucune progression affichée (le formulaire exige une cible > 0).

## 2. Transformations (même compte, jamais supprimé ni recréé)

- **Compte classique → objectif** : « Modifier le compte » → Type = « Objectif d'épargne » → saisir
  le montant cible → enregistrer. C'est un simple `UPDATE` de la même ligne : l'id/UUID, le solde
  initial, les transactions, la position et les autres champs sont conservés.
- **Objectif → compte classique** : choisir un autre type → confirmation → `savings_target_amount`
  et `savings_description` repassent à `NULL`. Le solde et les transactions sont conservés.

## 3. Synchronisation Android ↔ serveur ↔ Web

- `server/api/config/entity_sync_configs.php` : colonnes `savings_target_amount` et
  `savings_description` ajoutées à `accounts`, en **nullable**.
  - `null` explicite dans le payload : colonne effacée (objectif repassé en compte classique).
  - Champ absent (ancien client) : valeur actuelle conservée (`array_key_exists` dans `push.php`).
- Les anciens clients Android qui reçoivent `type = SAVINGS_GOAL` retombent sur le type local
  (ou `CASH`) sans planter (`runCatching { AccountType.valueOf(...) }`).
- **Web — à implémenter** (le code Web n'est pas dans ce dépôt) :
  1. Lire et écrire `savingsTargetAmount`/`savingsDescription` sur les comptes, et **toujours les
     envoyer** au push (`null` si le compte n'est pas un objectif).
  2. Ajouter « Objectif d'épargne » (`SAVINGS_GOAL`) à la liste des types du formulaire de compte.
     Montant cible obligatoire et > 0.
  3. Carte de compte : « solde / cible », barre de progression et pourcentage, avec les mêmes
     règles qu'en section 1. Page de détail : cible, épargné, restant (ou « dépassé de »), barre
     `#42B998`, description, historique des transactions habituel.
  4. Passage objectif → autre type : demander confirmation, puis envoyer les deux champs à `null`.
  5. Ne plus afficher d'écran « Objectifs d'épargne » basé sur `savings_goals`.

## 4. Migration des anciens objectifs (`savings_goals`) — non destructive

Règles, identiques sur le serveur et sur Android :

- 1 ancien objectif actif → 1 compte `SAVINGS_GOAL` **avec le même UUID**. Deux appareils ou le
  serveur qui migrent le même objectif retombent donc sur la même ligne, ce qui évite les doublons
  (la création est idempotente dans `push.php`) ;
- solde initial = ancien `current_amount`, cible = `target_amount` ; icône `SAVINGS`, ajouté en fin
  de liste ;
- `is_excluded_from_statistics = 1`. Cet argent n'était compté dans aucun solde total : l'inclure
  d'office ferait bondir le solde total de l'utilisateur. On peut réactiver l'inclusion dans
  « Modifier le compte ». Les objectifs créés ensuite sont inclus par défaut, comme tout compte ;
- l'ancien objectif est seulement **supprimé en douceur** (`deleted_at`). La ligne est conservée,
  y compris son échéance (`deadline`), qui n'a pas d'équivalent dans le nouveau modèle.

Mise en œuvre :

- Serveur : `database/migrations/006_savings_goal_accounts.sql`, à exécuter à la main **avant** de
  publier la nouvelle version de l'app. Seule l'étape `ALTER` n'est pas rejouable. Les étapes de
  données sont idempotentes (vérifié sur MariaDB).
- Android :
  - `Migration30To31.kt` ajoute les colonnes ;
  - `LegacySavingsGoalMigrator.kt` convertit les données et enfile leur synchronisation. Il tourne
    au démarrage, après chaque pull et après la restauration d'une ancienne sauvegarde.

## 5. Ordre de déploiement

1. Exécuter `006_savings_goal_accounts.sql` sur la base de production.
2. Déployer `server/api/config/entity_sync_configs.php`.
3. Publier l'app Android (base Room v31), puis la version Web qui gère `SAVINGS_GOAL`.
