# Arzikina Web — Cahier des charges

Document d'analyse et de conception, écrit après lecture directe du dépôt Android, du serveur PHP (`server/api/`) et du schéma MySQL (`database/migrations/001_initial_schema.sql`) — aucune hypothèse, aucune supposition sur une architecture qui n'existe pas (en particulier : **pas de Supabase**, **pas de REST classique par ressource**). Complète `docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md` (architecture de synchronisation Android, déjà implémentée et validée en production) plutôt que de le redire — ce document se concentre sur ce qui change pour accueillir un client Web.

---

## 0. Correction architecturale majeure à valider en premier

La demande initiale suppose une API PHP "classique" : un fichier par ressource, des méthodes GET/POST/PUT/DELETE par entité (`accounts.php`, `transactions.php`...). **Ce n'est pas ce qui existe.** Après lecture complète de `server/api/`, l'API réelle ne contient que **trois fichiers exécutables** :

| Fichier | Rôle |
|---|---|
| `api/auth/login.php` | Authentification, émet un token |
| `api/sync/push.php` | Écrit CREATE/UPDATE/DELETE, pour **n'importe quelle entité**, via un paramètre `entityType` |
| `api/sync/pull.php` | Lit les changements d'**n'importe quelle entité** depuis un horodatage, via un paramètre `entity_type` |

Il n'existe **aucun** `GET /api/accounts`, aucun `POST /api/transactions`. Le comportement par entité (nom de table, colonnes, nullabilité) est **déclaratif**, dans un registre unique (`api/config/entity_sync_configs.php`, `ENTITY_CONFIGS`) que ces deux fichiers lisent pour construire leurs requêtes SQL dynamiquement. C'est le mécanisme qui alimente le Sync Engine Android (`SyncEngineImpl.kt`), déjà en production pour 13 des 16 entités métier.

**Conséquence directe pour le Web : deux options architecturales possibles.**

**Option A — le Web parle le même protocole que Android (recommandée).**
Le navigateur appelle `pull.php`/`push.php` exactement comme le fait l'app Android (mêmes `entityType`, même token Bearer, même `serverTime`/`updated_after`, même détection de conflit LWW). Aucune ligne de PHP à écrire pour le CRUD ; le Web reconstruit ses vues (soldes, listes, agrégats) côté client à partir des lignes plates reçues, exactement comme le fait chaque `*RepositoryImpl.kt` côté Android aujourd'hui.
- Avantages : zéro nouveau code serveur, zéro risque de régression pour Android (le fichier `entity_sync_configs.php` est déjà générique et partagé), garantie stricte "mêmes données, mêmes règles de conflit" entre les deux clients, cohérent avec la consigne "réutiliser l'API existante si elle est propre".
- Inconvénients : le Web doit faire lui-même les jointures (ex. joindre `transactions` à `accounts`/`categories` pour un tableau de bord), et le chargement initial doit paginer par lots de 500 lignes (`pull.php`, limite documentée) — pas gênant pour un usage personnel, mais à prévoir dans le client JS.

**Option B — endpoints Web dédiés en lecture, en plus du protocole de sync existant.**
Ajouter des fichiers PHP `api/web/*.php`, purement additifs, qui interrogent MySQL directement pour des besoins de confort (ex. `GET /api/web/dashboard-summary.php` qui renvoie un JSON déjà agrégé). Les écritures continueraient IMPÉRATIVEMENT à passer par `push.php` (jamais un nouvel endpoint d'écriture séparé), pour ne jamais dupliquer la logique de version/conflit.
- Avantages : réponses plus riches, moins de calcul côté navigateur.
- Inconvénients : nouvelle surface à sécuriser et maintenir, risque de dupliquer une règle métier (ex. calcul de solde) entre PHP et Kotlin si on n'y prend pas garde, plus de code à écrire avant la première page utilisable.

**Recommandation : Option A pour tout le chantier initial (Phases 6 à 17), Option B seulement plus tard, entité par entité, UNIQUEMENT si un écran précis s'avère trop lent ou trop complexe à agréger côté client** — exactement la logique "évolution progressive sans casser l'existant" demandée. Le reste de ce document est écrit selon l'Option A ; il note explicitement où l'Option B pourrait s'insérer sans rien casser.

---

## 1. Architecture actuelle — Application Android

```
UI (Fragment + View Binding, Material Design — pas de Compose)
   ↓
ViewModel (StateFlow, un par écran, Hilt)
   ↓
Repository (interface domain/repository/, implémentation data/repository/)
   ↓
Room (SQLite local, base UNIQUE, version 25, 17 entités) ──┐
   ↓                                                        │ Sync Queue (sync_queue,
Sync Engine (SyncEngineImpl.kt, WorkManager + manuel)       │ PENDING → SYNCING →
   ↓                                                        │ SYNCED/FAILED)
HTTPS/HTTP → api/sync/push.php + api/sync/pull.php ─────────┘
   ↓
MySQL (base `arzikina`)
```

Clean Architecture stricte : le domaine (`domain/model/*`) ne connaît jamais Room ni `userId` — c'est `data/repository/*Impl.kt` qui filtre par utilisateur courant (`SessionManager`) avant de mapper vers/depuis le domaine.

### 1.1 Les 16 entités métier synchronisables (+ 1 exclue) + la file d'attente locale

| # | Entity Room | Table MySQL | Statut synchronisation | Notes |
|---|---|---|---|---|
| 1 | `AccountEntity` | `accounts` | ✅ Synchronisée | Comptes (espèces, banque, carte, Mobile Money) |
| 2 | `CategoryEntity` | `categories` | ✅ Synchronisée | Catégories de transaction |
| 3 | `TransactionEntity` | `transactions` | ✅ Synchronisée | Dépense/revenu/transfert, inclut les frais |
| 4 | `BudgetEntity` | `budgets` | ✅ Synchronisée | Budget par catégorie |
| 5 | `SavingsGoalEntity` | `savings_goals` | ✅ Synchronisée | Objectifs d'épargne (écran non relié côté Android actuellement) |
| 6 | `PersonEntity` | `persons` | ✅ Synchronisée | Personnes liées aux prêts/emprunts |
| 7 | `LoanEntity` | `loans` | ✅ Synchronisée | Prêts/emprunts |
| 8 | `LoanPaymentEntity` | `loan_payments` | ✅ Synchronisée | Remboursements |
| 9 | `RecurringTransactionEntity` | `recurring_transactions` | ✅ Synchronisée | Règles d'automatisation |
| 10 | `RecurringTransactionOccurrenceEntity` | `recurring_transaction_occurrences` | ✅ Synchronisée | Occurrences générées d'une règle |
| 11 | `FinancialPlanEntity` | `financial_plans` | ✅ Synchronisée | Planifications par projet |
| 12 | `FinancialPlanItemEntity` | `financial_plan_items` | ✅ Synchronisée | Dépenses prévues d'une planification |
| 13 | *(DataStore)* `UserPreferences` (theme/devise) | `user_preferences` | ✅ Synchronisée | Pas d'entité Room source ; `biometricLockEnabled` exclu (par appareil) |
| 14 | `ReceiptEntity` | `receipts` | ⚠️ Table MySQL créée, **sync PAS encore câblée** | Pas dans `ENTITY_CONFIGS` ; pas d'`api/receipts/upload.php` — voir section 19, Phase future |
| 15 | `UserEntity` | `users` | ❌ Exclue (limitation d'outillage KSP, pas architecturale) | Voir `AUDIT-ET-ARCHITECTURE-SYNC.md`, décision 6.5 |
| 16 | `CardSecretEntity` | — (aucune table) | ❌ Exclue (décision produit) | Chiffré via Android Keystore, non transportable — voir décision 6.2 |
| 17 | `SyncQueueEntity` | — (n'existe pas côté serveur) | — | File d'attente LOCALE uniquement, jamais synchronisée elle-même |

`SUPPORTED_ENTITY_TYPES` actuel côté Android (`SyncEngineImpl.kt`) : `categories`, `budgets`, `savings_goals`, `financial_plans`, `persons`, `accounts`, `transactions`, `loans`, `loan_payments`, `recurring_transactions`, `recurring_transaction_occurrences`, `financial_plan_items`, `user_preferences` — **13 valeurs**, exactement celles présentes dans `ENTITY_CONFIGS` côté PHP (section 4).

### 1.2 Écrans existants (inventaire réel, `presentation/`)

| Domaine | Fragments Android | Équivalent Web attendu (section 14) |
|---|---|---|
| Authentification | `LoginFragment`, `RegisterFragment`, `ForgotPasswordFragment` | Connexion, Inscription (si activée côté Web), Mot de passe oublié |
| Dashboard | `DashboardFragment` | Dashboard |
| Comptes | `AccountsFragment`, `AccountDetailFragment`, `AccountFormFragment` | Comptes, Cartes bancaires (sous-partie d'un compte de type carte) |
| Transactions | `TransactionsFragment`, `TransactionFormFragment` | Transactions |
| Catégories | `CategoriesFragment`, `CategoryFormFragment` | Sous-écran des Paramètres ou des Transactions |
| Budgets | `BudgetFragment`, `BudgetFormFragment` | Budgets |
| Épargne | `SavingsGoalsViewModel`/écran non relié dans la nav actuelle | Objectifs d'épargne (si activé) |
| Planifications | `FinancialPlansFragment`, `FinancialPlanDetailFragment`, `FinancialPlanFormFragment`, `FinancialPlanItemFormFragment`, `FinancialPlanItemConvertFragment` | Planifications |
| Automatisations | `RecurringTransactionsFragment`, `RecurringTransactionFormFragment`, `RecurringOccurrenceQueueDialogFragment` | Automatisations |
| Prêts/Emprunts | `LoansFragment`, `LoanDetailFragment`, `LoanFormFragment`, `LoanPaymentFormFragment`, `LoanStatisticsFragment` | Prêts, Emprunts |
| Reçus | `ReceiptsFragment`, `ReceiptDetailFragment`, `ReceiptPdfViewerFragment` | Reçus (sync pas encore prête, voir 1.1) |
| Statistiques | `StatisticsFragment` | Statistiques |
| Utilitaires | `AllUtilitiesFragment` | Utilitaires (grille de raccourcis) |
| Paramètres/Profil | `SettingsFragment`, `ProfileFragment`, `ChangePasswordFragment`, `SecurityQuestionUpdateFragment`, `BiometricLockFragment`, `BackupFragment`, `SyncLoginFragment` | Paramètres, Profil, Synchronisation |

### 1.3 Mécanismes déjà en place, à connaître avant de toucher au serveur

- **Authentification locale** (`AuthRepository`, PBKDF2-SHA256 via `util/PasswordHasher`) : gère les profils LOCAUX à un appareil (hors-ligne). **Distincte** de l'authentification serveur (`SyncAuthRepository`, `api/auth/login.php`) qui gère la connexion multi-appareils. Le Web n'a besoin QUE de la seconde.
- **Sync Engine** (`SyncEngineImpl.kt`) : `pushPendingChanges()` (vide `sync_queue`, groupé par `entityType`, gère le retry avec backoff exponentiel `[30s, 1min, 5min, 30min, 1h...]`), `pullRemoteChanges()` (incrémental via curseur `updated_after`, s'arrête proprement sur une entité en échec sans bloquer les autres — voir bug 22.5b corrigé), `observeQueueStatus()` (indicateur visuel), `enqueueUnsyncedLocalData()` (rattrapage après un premier login serveur).
- **Résolution de références croisées** : chaque colonne `*_id` référençant une autre entité synchronisée (ex. `transactions.account_id`, `loans.person_id`) stocke en réalité le `syncId` (UUID) de la ligne référencée, résolu par un `*SyncEnqueuer` ou une méthode privée `resolveXSyncId` dans chaque repository, avec repli `getByIdIncludingDeleted` pour gérer le cas où la ligne référencée vient d'être supprimée dans la même cascade.
- **Sauvegarde/restauration fichier** (`BackupRepositoryImpl`) : mécanisme SÉPARÉ et INCHANGÉ, export/import JSON local, avec sa propre table de correspondance d'identifiants (`idMap`). Redevient un mécanisme secondaire (migration d'appareil) maintenant que la synchronisation serveur existe — **aucune raison d'y toucher pour le Web**.
- **Sécurité locale** : mot de passe et réponse de sécurité en PBKDF2-SHA256 ; numéro de carte/CVV chiffrés AES/GCM via Android Keystore (`CardCipher`) — **jamais transportables**, voir 1.1.

---

## 2. Architecture actuelle — Serveur (Apache2 / PHP / MySQL)

```
server/
├── api/
│   ├── auth/login.php              POST — authentification, émission de token
│   ├── sync/push.php               POST — écriture générique (CREATE/UPDATE/DELETE)
│   ├── sync/pull.php               GET  — lecture incrémentale générique
│   ├── config/database.php         connexion PDO partagée (singleton par requête)
│   ├── config/entity_sync_configs.php   registre déclaratif des 13 entités synchronisées
│   ├── config/secrets.example.php  modèle (pas de vrai secret) pour la clé de token
│   ├── middleware/auth_middleware.php   vérifie `Authorization: Bearer <token>`
│   └── utils/{case_convert,json_response,uuid}.php   fonctions partagées, anti-duplication
└── postman/Arzikina-API.postman_collection.json   collection de test (partiellement à jour)
```

- **Connexion MySQL** : `PDO`, `ERRMODE_EXCEPTION`, `FETCH_ASSOC`, `EMULATE_PREPARES => false` (vraies requêtes préparées côté serveur — protection injection SQL), charset forcé `utf8mb4`. Identifiants réels dans `/home/Admin/config_arzikina.php`, **hors racine web, jamais dans Git** — `api/config/database.php` réutilise ces constantes telles quelles, ne les duplique jamais.
- **Secrets API** (clé de signature de token, durée de vie) dans `/home/Admin/config_arzikina_secrets.php`, même principe. `TOKEN_EXPIRY_SECONDS = 30 jours` actuellement (voir section 10 pour la recommandation spécifique au Web).
- **Aucune donnée sensible dans le code versionné** : confirmé par lecture directe, `secrets.example.php` ne contient qu'un gabarit.
- **Réponses JSON uniformes** (`json_response.php`) : `sendJson()`/`sendError()` — codes d'erreur COURTS et STABLES (`invalid_credentials`, `invalid_token`, `unsupported_entity_type`, `not_found`, `invalid_body`...), jamais un message d'exception PHP brut renvoyé au client (`config/database.php` : une `PDOException` de connexion part uniquement dans `error_log`, jamais dans la réponse).

---

## 3. Modèle de données MySQL (schéma réel, `database/migrations/001_initial_schema.sql`)

18 tables, moteur **InnoDB**, charset **utf8mb4**. Toutes les tables métier partagent 4 colonnes de synchronisation : `created_at BIGINT`, `updated_at BIGINT`, `deleted_at BIGINT NULL` (suppression douce), `version INT` (détection de conflit optimiste). Clé primaire `CHAR(36)` (UUID) sur chaque table métier — **c'est la même valeur que `syncId` côté Android**, jamais l'`id` `Long` local.

| Table | Colonnes spécifiques (hors sync communes) | Contrainte notable |
|---|---|---|
| `users` | full_name, username (UNIQUE), email (UNIQUE), phone_number, password_hash, security_question, security_answer_hash | `password_hash` calculé CÔTÉ SERVEUR (`password_hash()` PHP), sans rapport avec le PBKDF2 local Android |
| `auth_tokens` | user_id, token_hash (SHA-256, UNIQUE), device_id, device_label, expires_at, revoked_at, last_used_at | Table technique, pas d'`id` UUID (auto-increment) |
| `user_preferences` | user_id, theme_mode, currency_code | `UNIQUE KEY uq_user_preferences_user` — **au plus une ligne par utilisateur**, seule table de ce type |
| `accounts` | name, icon, color_argb, currency_code, initial_balance_minor, type, card_last_four_digits, card_expiry_month, card_expiry_year, is_excluded_from_statistics, mobile_money_package_name | — |
| `categories` | name, icon, color_argb, type | — |
| `persons` | name, phone | — |
| `transactions` | amount, type, account_id, transfer_account_id, category_id, date, description, latitude, longitude, payment_method, fee_transaction_id, fee_type, receipt_id | `fee_transaction_id`/`receipt_id` **sans FOREIGN KEY** (cohérence applicative, pas SQL — une transaction peut arriver avant sa transaction de frais) |
| `budgets` | category_id, period, limit_amount, currency_code, start_date, end_date | ⚠️ voir note ci-dessous sur les FK à retirer |
| `savings_goals` | name, target_amount, current_amount, currency_code, deadline | — |
| `loans` | person_id, account_id, type, amount, amount_repaid, remaining_amount, start_date, due_date, reason, reason_custom_text, repayment_mode, description, status, transaction_id | idem |
| `loan_payments` | loan_id, account_id, amount, date, note, transaction_id | idem |
| `recurring_transactions` | type, amount, account_id, category_id, description, payment_method, start_date, end_date, frequency, next_execution_date, is_active, trigger_hour, trigger_minute | idem |
| `recurring_transaction_occurrences` | recurring_transaction_id, scheduled_date, status, transaction_id, processed_at | `UNIQUE (recurring_transaction_id, scheduled_date)` |
| `financial_plans` | name, description, available_amount, target_amount, period_type, start_date, end_date, icon, color_argb, status | — |
| `financial_plan_items` | plan_id, name, amount, actual_amount, category_id, description, planned_date, priority, status, transaction_id | idem |
| `receipts` | file_name, file_path (chemin **serveur**, jamais un chemin local Android), received_at, file_size, mime_type, source_app, source_name, amount_minor | Table créée, **pas encore dans le registre de sync** (`ENTITY_CONFIGS`) |
| `sync_conflicts` | user_id, entity_type, entity_id, losing_payload (JSON), winning_payload (JSON), created_at | Table d'audit uniquement, jamais poussée/tirée comme une entité normale |

**Point technique corrigé pendant cette analyse** : le fichier `001_initial_schema.sql` contenait DEUX définitions de `user_preferences` (une ancienne, sans colonne `id`, section 1 ; la bonne, section 10). Sur une base vide, la première s'exécutait et la seconde ne faisait plus rien (`CREATE TABLE IF NOT EXISTS`) — c'est exactement le bug de déploiement rencontré en production lors de l'étape 22 du chantier de synchronisation, corrigé manuellement sur le serveur à l'époque mais jamais reporté dans le fichier source. **Corrigé dans ce fichier au moment de la rédaction de ce document** (bloc obsolète retiré) — le fichier est maintenant rejouable tel quel sur une base vide.

**Point à vérifier avant tout chantier Web (non corrigé ici, nécessite un accès à la base réelle)** : les commentaires de `entity_sync_configs.php` indiquent que plusieurs contraintes `FOREIGN KEY` du schéma initial (`fk_budgets_category`, `fk_loans_person`, `fk_loans_account`, `fk_loan_payments_loan`, `fk_loan_payments_account`, `fk_recurring_transactions_account`, `fk_recurring_transactions_category`, `fk_occurrences_rule`, `fk_plan_items_plan`, `fk_plan_items_category`) devaient être supprimées avant déploiement de chaque étape correspondante (une ligne créée hors-ligne peut référencer une ligne pas encore confirmée par le serveur). Le fichier SQL source les contient encore telles quelles. Comme la synchronisation de ces entités est validée en production, ces `ALTER TABLE ... DROP FOREIGN KEY` ont presque certainement déjà été appliqués manuellement sur le serveur réel — **mais ce fichier ne reflète plus l'état réel de la base**, à vérifier par une inspection directe (`SHOW CREATE TABLE ...`) avant d'écrire une nouvelle migration dessus, pour ne pas repartir d'une hypothèse fausse.

---

## 4. Documentation API (les 3 endpoints réels)

### 4.1 `POST /api/auth/login.php`

| | |
|---|---|
| Authentification requise | Non |
| Corps (JSON) | `{ "identifier": "<username ou email>", "password": "...", "deviceId": "...", "deviceLabel": "..." }` |
| Réponse 200 | `{ "token": "<hex 64 car.>", "userId": "<uuid>", "expiresAt": <millis epoch> }` |
| Erreurs | `400 invalid_body` (champs manquants) · `401 invalid_credentials` (message IDENTIQUE si l'identifiant n'existe pas ou si le mot de passe est faux — anti-énumération) · `405 method_not_allowed` |
| Sécurité | Requête préparée à deux espaces réservés distincts pour le même identifiant (contrainte du driver MySQL en requêtes préparées natives) ; `password_verify()` (Argon2id/bcrypt) ; délai fixe (`usleep(150000)`) dans les deux branches pour atténuer une attaque temporelle ; **pas encore de verrou anti-bruteforce par IP/compte** (limitation connue, documentée, à traiter — voir section 10) |
| `deviceId`/`deviceLabel` | Facultatifs, stockés sur la ligne `auth_tokens` (pour un futur écran "appareils connectés") |

Pour le Web : `deviceLabel` peut valoir par exemple `"Navigateur — Chrome / Windows"`, généré côté client à partir de `navigator.userAgent`, purement informatif.

### 4.2 `POST /api/sync/push.php`

| | |
|---|---|
| Authentification requise | Oui — `Authorization: Bearer <token>` |
| Corps (JSON) | `{ "entityType": "categories", "operations": [ { "operation": "CREATE"\|"UPDATE"\|"DELETE", "entity": { "id": "<uuid>", "baseVersion": <int\|null>, ...champs spécifiques camelCase... } } ] }` |
| Réponse 200 | `{ "results": [ { "status": "accepted"\|"conflict_resolved"\|"error", "entityId": "<uuid>", "serverEntity": {...} } ], "serverTime": <millis> }` — `results[i]` correspond toujours à `operations[i]` |
| Erreurs par opération | `errorCode: "not_found"` (UPDATE/DELETE sur une ligne inexistante pour cet utilisateur) · `"invalid_operation_type"` · `"invalid_operation"` (structure invalide) |
| Erreurs globales | `400 unsupported_entity_type` · `400 invalid_body` · `405 method_not_allowed` · `401` (token) |
| `entityType` valides | Les 13 clés de `ENTITY_CONFIGS` — voir tableau section 1.1 |
| Sécurité | `entity.userId`, même présent dans le payload, est **toujours ignoré** : le propriétaire réel est celui du token, jamais une valeur envoyée par le client — isolation multi-utilisateurs structurelle |
| Idempotence | Un CREATE renvoyé deux fois (ex. timeout réseau sans réponse reçue) ne recrée pas la ligne, renvoie l'état déjà existant |
| Conflit | Last-Write-Wins sur `updatedAt` (voir section 7) — `serverEntity` renvoie TOUJOURS l'état final côté serveur, à appliquer localement même en cas de simple `accepted` |

Exemple concret (CREATE d'une catégorie) :
```json
POST /api/sync/push.php
Authorization: Bearer 7f3a9c...
{
  "entityType": "categories",
  "operations": [
    {
      "operation": "CREATE",
      "entity": {
        "id": "8e6d3b1a-...",
        "name": "Transport",
        "icon": "ic_category_transport",
        "colorArgb": -16537100,
        "type": "EXPENSE",
        "createdAt": 1787000000000,
        "updatedAt": 1787000000000
      }
    }
  ]
}
```
Réponse :
```json
{
  "results": [
    { "status": "accepted", "entityId": "8e6d3b1a-...", "serverEntity": { "id": "8e6d3b1a-...", "userId": "...", "name": "Transport", "icon": "ic_category_transport", "colorArgb": -16537100, "type": "EXPENSE", "createdAt": 1787000000000, "updatedAt": 1787000000000, "deletedAt": null, "version": 1 } }
  ],
  "serverTime": 1787000000123
}
```

### 4.3 `GET /api/sync/pull.php?entity_type=<type>&updated_after=<millis>`

| | |
|---|---|
| Authentification requise | Oui — `Authorization: Bearer <token>` |
| Paramètres | `entity_type` (obligatoire, une des 13 valeurs) · `updated_after` (millis epoch, `0` pour tout récupérer) |
| Réponse 200 | `{ "entities": [ {...} ], "serverTime": <millis> }` |
| Pagination | Lot plafonné à **500 lignes**, triées par `updated_at ASC` : si `entities.length === 500`, rappeler avec `updated_after = serverTime` reçu jusqu'à un lot plus petit |
| Erreurs | `400 unsupported_entity_type` · `405 method_not_allowed` · `401` (token) |
| Suppressions | Une ligne supprimée apparaît dans la réponse avec `deletedAt` non nul — le client doit la retirer localement, pas l'ignorer |

Pour le Web (Option A, section 0) : au premier login, appeler `pull.php` avec `updated_after=0` pour **chacune** des 13 `entityType`, en paginant par lots de 500, puis conserver le dernier `serverTime` reçu comme curseur pour les pulls suivants — exactement l'algorithme de `SyncEngineImpl.pullRemoteChanges()` côté Android, transposé en JavaScript/TypeScript.

### 4.4 Endpoints qui N'EXISTENT PAS encore (à ne pas supposer)

- Inscription (`register.php`) — création d'utilisateur serveur non exposée par API à ce jour (voir commentaire de tête de `login.php` : création manuelle en SQL pour les tests). **À trancher avant la Phase 6** (section 19) : le Web doit-il permettre l'inscription, ou rester réservé aux comptes déjà créés via l'app Android ?
- Rafraîchissement de token (`refresh.php`) — mentionné en commentaire comme travail futur, le token doit être renvoyé au login uniquement pour l'instant (30 jours de validité).
- Upload de reçu (`receipts/upload.php`) — la table `receipts` existe, `entity_sync_configs.php` ne la contient pas encore.
- Toute route d'agrégation (dashboard, statistiques précalculées) — voir Option B, section 0.

---

## 5. Architecture cible

```
┌────────────────────────┐        ┌──────────────────────────┐
│   Application Web       │        │    Application Android    │
│   (Lovable)              │        │    (Kotlin, Room, MVVM)   │
└───────────┬──────────────┘        └────────────┬───────────────┘
            │ HTTPS (prod) / HTTP réseau local (dev)             │ HTTPS/HTTP
            │ Authorization: Bearer <token>                       │ Authorization: Bearer <token>
            ▼                                                      ▼
┌─────────────────────────────────────────────────────────────────┐
│                    API PHP (Apache2)                              │
│   api/auth/login.php · api/sync/push.php · api/sync/pull.php      │
│   (MÊME registre ENTITY_CONFIGS pour les deux clients)            │
└───────────────────────────────┬───────────────────────────────────┘
                                 ▼
                     ┌───────────────────────┐
                     │   MySQL — base `arzikina` │
                     └───────────────────────┘
```

Le navigateur **ne se connecte jamais** à MySQL. Aucune donnée MySQL, aucun identifiant, aucun secret dans le JavaScript livré au navigateur — uniquement l'URL de base de l'API et le token de session obtenu après login (en mémoire/`localStorage`, jamais en dur dans le code).

---

## 6. Stratégie de synchronisation Web

Le Web n'a **pas de base locale** (contrairement à Android, qui doit fonctionner hors-ligne) : chaque écran lit directement via `pull.php` (avec mise en cache mémoire côté SPA pour éviter de re-fetcher à chaque clic) et écrit directement via `push.php`. Deux modes possibles pour rester à jour :

1. **Au chargement / changement d'écran** : `pull.php?updated_after=<dernier curseur connu pour cette entité>` — rapide, incrémental, aucune donnée superflue.
2. **En arrière-plan, à intervalle régulier** (voir section 11, temps réel) : re-pull silencieux pour détecter les changements faits depuis Android ou un autre onglet.

Scénario bout en bout (repris de la demande, confirmé faisable avec l'API existante) :
```
Android crée une transaction
  → écriture Room locale (immédiate)
  → sync_queue (PENDING)
  → SyncEngine.pushPendingChanges() → POST /api/sync/push.php (entityType=transactions)
  → ligne insérée dans MySQL, version=1

Web ouvre l'écran Transactions
  → GET /api/sync/pull.php?entity_type=transactions&updated_after=<curseur Web>
  → la nouvelle transaction apparaît dans la réponse (updatedAt > curseur)
  → affichée, curseur Web mis à jour avec le serverTime reçu
```

Et dans l'autre sens (Web → Android), symétrique : le Web appelle `push.php`, la ligne existe en base, Android la reçoit à son prochain `pullRemoteChanges()` (déclenché au démarrage, au retour au premier plan, au retour réseau, ou périodiquement en tâche de fond — mécanisme déjà en place, aucun changement requis côté Android).

---

## 7. Fonctionnement offline/online

**Le Web n'a pas besoin d'un mode hors-ligne complet** (contrairement à Android, qui doit rester utilisable sans réseau) — un navigateur suppose une connexion. Prévoir seulement :
- Détection de perte de connexion (`navigator.onLine` + gestion des erreurs réseau sur `fetch`) → bannière "Connexion perdue, tentative de reconnexion..." plutôt qu'un écran cassé.
- File d'attente **légère et éphémère** en mémoire (pas persistée en `localStorage` par défaut, pour ne pas rejouer une écriture obsolète après un rechargement de page à un instant différent) : si une écriture échoue pour une raison réseau, proposer un bouton "Réessayer" explicite plutôt qu'une reprise automatique silencieuse — cohérent avec le principe "jamais de perte silencieuse d'une donnée financière" déjà appliqué côté Android.
- Si un vrai mode hors-ligne Web devient un besoin plus tard (PWA, Service Worker + IndexedDB), il pourra réutiliser EXACTEMENT le même protocole `sync_queue`/`push.php`/`pull.php` — l'architecture ne l'exclut pas, elle n'est simplement pas nécessaire dès la v1.

---

## 8. Gestion des conflits

**Réutilisée telle quelle, aucun nouveau mécanisme à écrire.** `push.php` applique déjà Last-Write-Wins sur `updatedAt`, avec journalisation systématique dans `sync_conflicts` (jamais de perte silencieuse, voir section 3). Le Web doit simplement :
- Envoyer `baseVersion` (la `version` connue au moment de la modification) sur chaque UPDATE/DELETE, exactement comme Android.
- Toujours appliquer le `serverEntity` renvoyé, y compris quand `status === "conflict_resolved"` (la modification Web a été écartée, la ligne serveur — issue d'un autre appareil — doit remplacer ce que l'écran Web affichait) : afficher un message explicite ("Cette donnée a été modifiée ailleurs entre-temps, la version la plus récente a été conservée") plutôt que de masquer le conflit.
- Cas concret déjà couvert (budget 50 000 vs 60 000 modifié simultanément) : la modification avec le `updatedAt` le plus récent gagne, l'autre reste consultable dans `sync_conflicts` (pas encore d'écran dédié, ni Android ni Web — amélioration future possible, hors périmètre v1).

---

## 9. Authentification

**Un seul système d'utilisateurs**, celui déjà en place côté serveur (table `users`, `api/auth/login.php`) — pas de second système à créer. Un compte Arzikina se connecte indifféremment sur Android et sur le Web, retrouve les mêmes données.

- Web : formulaire de connexion → `POST /api/auth/login.php` → conserver `token` (en mémoire + `localStorage` pour survivre à un rechargement de page) et `expiresAt`.
- Chaque appel `push.php`/`pull.php` envoie `Authorization: Bearer <token>`.
- Déconnexion : simplement oublier le token côté client (pas de route `logout.php` serveur à ce jour — **à ajouter si une révocation explicite est souhaitée**, techniquement immédiate : `UPDATE auth_tokens SET revoked_at = ... WHERE token_hash = ...`, additif, ne casse rien).
- **Inscription** : décision à prendre (voir 4.4) — soit le Web reste réservé aux comptes déjà créés via Android/SQL manuel pour l'instant, soit on écrit `api/auth/register.php` (additif, même registre de sécurité que `login.php` : `password_hash()`, unicité `username`/`email` déjà garantie par les contraintes `UNIQUE` de la table `users`).

---

## 10. Sécurité

Déjà en place côté serveur (confirmé par lecture directe, pas une supposition) :
- Requêtes préparées partout (`PDO::ATTR_EMULATE_PREPARES => false`) — protection injection SQL.
- Mots de passe : jamais stockés ni journalisés en clair ; `password_verify()` côté serveur.
- Tokens : jamais stockés en clair (`token_hash` SHA-256 uniquement), expiration, révocation possible.
- `entity.userId` client toujours ignoré au profit du token — isolation stricte par utilisateur.
- Aucun message d'exception brut renvoyé au client.

À ajouter/valider spécifiquement pour le Web :
- **HTTPS obligatoire en production** (voir section 14) — le mot de passe circule en clair sur le fil TLS uniquement au login, jamais autrement ; en HTTP simple (réseau local de développement), c'est un choix assumé et documenté pour le développement uniquement, jamais pour un accès Internet.
- **CORS** : `api/sync/*` et `api/auth/*` doivent répondre avec un en-tête `Access-Control-Allow-Origin` limité au(x) domaine(s) réel(s) de l'app Web (jamais `*` en production), `Access-Control-Allow-Headers: Authorization, Content-Type`. Actuellement absent des fichiers lus — **à ajouter**, additif, n'affecte pas Android (qui n'est pas soumis à CORS, ce n'est pas un navigateur).
- **CSRF** : non applicable tel quel — l'authentification est par en-tête `Authorization: Bearer`, pas par cookie de session, donc pas de risque CSRF classique (un site tiers ne peut pas forcer l'ajout de cet en-tête). Rester vigilant si un mécanisme de cookie est introduit plus tard.
- **Stockage du token côté navigateur** : `localStorage` est exposé à une XSS si l'app Lovable en contient une (contrairement à l'Android Keystore, matériel et isolé par app). Mitigations recommandées : Content-Security-Policy stricte dans l'app Lovable, ne jamais injecter de HTML non échappé (descriptions de transaction, noms de catégorie personnalisés...), et envisager un `TOKEN_EXPIRY_SECONDS` **plus court pour le Web** que pour Android (ex. 24h au lieu de 30 jours) — nécessite un petit ajustement de `login.php` (accepter un champ `platform` optionnel dans le corps, choisir la durée en fonction), additif et rétrocompatible (Android n'envoie pas ce champ, garde son comportement actuel par défaut).
- **Rate limiting / anti-bruteforce sur le login** : limitation déjà documentée comme non traitée dans le code actuel (voir 4.1) — devient plus pressant avec une surface Web publique. Recommandation : compteur d'échecs par identifiant + IP dans une nouvelle table légère (`login_attempts`), verrouillage temporaire après N échecs — chantier additif, ne modifie aucune table existante.
- **Isolation des données par utilisateur** : déjà garantie structurellement (section 9).
- **Logs sans données sensibles** : `error_log()` actuel ne journalise que des messages techniques (`echec de connexion base de donnees`), jamais un mot de passe ni un contenu de transaction — à maintenir pour tout nouveau code.

---

## 11. Temps réel

Infrastructure actuelle : PHP classique sur Apache2 (un processus par requête, pas de processus long-vivant) — pas de WebSocket natif sans composant supplémentaire (ex. un serveur Node/Ratchet séparé, hors du périmètre "réutiliser l'existant").

| Option | Faisabilité avec l'infra actuelle | Recommandation |
|---|---|---|
| Polling périodique (`pull.php` toutes les 15–30s sur l'écran actif) | ✅ Immédiate, zéro nouveau composant | **Recommandé pour la v1** — usage personnel/familial, pas de besoin de latence sub-seconde |
| Long polling | ⚠️ Possible mais tient mal avec le modèle "un process PHP par requête" d'Apache/mod_php (bloquerait un worker Apache par connexion en attente) | Non recommandé sans passer à PHP-FPM + réglages dédiés |
| Server-Sent Events (SSE) | ⚠️ Même limite qu'au-dessus (connexion longue tenue) | Idem, à réévaluer seulement si l'infra évolue (PHP-FPM, ou passerelle dédiée) |
| WebSocket | ❌ Nécessite un serveur applicatif séparé (Node, Swoole, Ratchet...) | Hors périmètre actuel — à reconsidérer si Arzikina grandit vers plusieurs utilisateurs simultanés collaborant sur les mêmes données, ce qui n'est pas le cas ici (un compte = une personne) |

**Recommandation retenue : polling léger**, avec un intervalle raisonnable (15–30s) sur les écrans où la fraîcheur compte (Dashboard, Transactions), et un pull explicite au focus de l'onglet (`document.visibilitychange`) pour éviter de manquer un changement fait sur Android entre deux polls.

---

## 12. UX/UI

- Couleur principale : `#42B998` (confirmée dans `colors.xml`, `arzikina_primary`).
- Identité déjà posée côté Android à reprendre sans la copier au pixel près : fond d'en-tête brun/ambré fixe (`arzikina_dashboard_header_*`) sur le Dashboard, cartes arrondies (`corner_radius_medium` = 16dp, `corner_radius_large` = 10dp), carte "Solde total" façon carte bancaire virtuelle avec dégradé.
- Mode clair ET sombre — déjà modélisé côté données (`ThemeMode` synchronisé via `user_preferences`, section 1.1) : le Web doit lire/écrire la MÊME préférence, pas une préférence Web séparée.
- Formatage montants (règle stricte, déjà appliquée par `util/Money` côté Android — **à reproduire à l'identique côté Web**, pas de réinterprétation) :
  - Dépense : `500 CFA` en rouge (`expense_red`), jamais `-500 CFA`.
  - Revenu : `500 CFA` en vert (`income_green`), jamais `+500 CFA`.
  - Séparateur de milliers : `10 000 CFA`, jamais `10000.00`.
- Devise multi-support (`SupportedCurrency`, XOF par défaut) — pas de conversion de change nulle part (ni Android ni serveur), le Web ne doit pas en introduire une non plus.

---

## 13. Responsive

- **Desktop** : sidebar de navigation persistante (équivalent des sections listées en 1.2).
- **Mobile Web / Tablette** : navigation compacte, idéalement une bottom navigation reprenant la structure déjà éprouvée côté Android (`bottom_nav_menu.xml` : Dashboard, Transactions, Compte, Autre — à confirmer/adapter selon les priorités Web).

---

## 14. Architecture applicative Lovable

Séparation stricte, aucune règle métier dans les composants d'affichage :

```
UI (pages/composants)
   ↓
Services (un par domaine métier)
   ↓
API Client (un seul module : gère le token, les en-têtes, le retry réseau, le parsing JSON)
   ↓
api/auth/login.php + api/sync/push.php + api/sync/pull.php
```

Services proposés (calqués sur les repositories Android, section 1) :

| Service | Domaine | Entités `pull`/`push` couvertes |
|---|---|---|
| `AuthService` | Connexion, token | `api/auth/login.php` |
| `AccountService` | Comptes, cartes | `accounts` |
| `TransactionService` | Transactions, frais, transferts | `transactions` |
| `CategoryService` | Catégories | `categories` |
| `BudgetService` | Budgets | `budgets` |
| `SavingsGoalService` | Objectifs d'épargne | `savings_goals` |
| `PlanningService` | Planifications par projet | `financial_plans`, `financial_plan_items` |
| `AutomationService` | Automatisations (transactions récurrentes) | `recurring_transactions`, `recurring_transaction_occurrences` |
| `LoanService` | Prêts/emprunts | `persons`, `loans`, `loan_payments` |
| `ReceiptService` | Reçus | ⚠️ pas encore synchronisé côté serveur (section 1.1) — prévoir l'interface dès maintenant, l'implémenter seulement à la Phase correspondante |
| `StatisticsService` | Agrégations pour l'écran Statistiques | Recalculé côté client à partir de `TransactionService`/`AccountService`, comme `PersonalStatistics`/`BudgetProgress` côté Android (aucune donnée serveur dédiée) |
| `SyncService` | Curseurs `updated_after` par entité, orchestration pull/push, détection d'état (à jour / en attente / erreur) | Toutes |
| `SettingsService` | Thème, devise | `user_preferences` |

`SyncService` est le point central à concevoir avec le plus de soin : il doit reproduire, en JavaScript/TypeScript, la boucle déjà validée côté Kotlin (`SyncEngineImpl.pullEntityType`, `pushBatch`) — un curseur par `entityType`, jamais un curseur global, et une gestion d'erreur qui ne bloque jamais les 12 autres entités si une seule échoue (voir le bug 22.5b, déjà corrigé côté Android, à ne pas réintroduire côté Web).

---

## 15. Environnements dev/prod

| | Développement | Production |
|---|---|---|
| Accès réseau | `192.168.49.1` — adresse privée, **jamais routable depuis Internet** (confirmé par test direct depuis un environnement cloud externe lors de l'audit initial : ping/connexion TCP échouent, "réseau inaccessible") | Domaine public + reverse proxy |
| Protocole | HTTP simple accepté (réseau local maîtrisé uniquement) | **HTTPS obligatoire** (Let's Encrypt si domaine, sinon certificat auto-signé + épinglage, uniquement en dernier recours) |
| Comment Lovable atteint le serveur en dev | Le poste qui exécute/prévisualise l'app Web doit être sur le MÊME réseau local que `192.168.49.1`, ou passer par un tunnel (voir ci-dessous) | — |
| Recommandation dev | Un tunnel HTTPS temporaire (ex. `ngrok`, `cloudflared tunnel`) pointé vers le port Apache local, le temps du développement dans Lovable (environnement cloud, pas sur le réseau local) — évite d'exposer `192.168.49.1` tel quel et donne déjà une URL HTTPS à l'app Web dès le développement | — |
| Recommandation prod | Nom de domaine → reverse proxy (Nginx ou Apache lui-même en frontal) → HTTPS terminé au reverse proxy → requêtes relayées en interne vers Apache/PHP. **MySQL jamais exposé sur Internet, port fermé au firewall sauf accès local/VPN** | — |
| Firewall | — | N'ouvrir que les ports HTTP(S) nécessaires ; MySQL (3306) fermé à toute IP publique, y compris avec mot de passe — l'API PHP reste le SEUL point d'accès aux données |
| VPN | Non nécessaire si un vrai nom de domaine + HTTPS est mis en place | Alternative si l'on préfère ne jamais exposer l'API publiquement (Web accessible uniquement via VPN vers le réseau local) — à trancher selon l'usage réel (accès depuis l'extérieur du domicile ou non) |

**Point bloquant à trancher avant la Phase 6** (section 19) : Lovable exécute et prévisualise l'app dans un environnement cloud, qui ne peut PAS atteindre `192.168.49.1` tel quel. Une des deux solutions ci-dessus (tunnel HTTPS temporaire, ou domaine + reverse proxy dès le début) doit être mise en place AVANT la première connexion réussie Web ↔ API, sans quoi aucun développement Lovable ne pourra être testé contre les vraies données.

---

## 16. Tests

Scénarios obligatoires (repris de la demande, tous réalisables avec l'API existante décrite section 4) :

1. Transaction créée sur Android → visible sur Web (`pull.php` après `push.php` Android).
2. Transaction créée sur Web → récupérée par Android (`pullRemoteChanges()` déjà déclenché automatiquement côté Android).
3. Transaction modifiée sur Android → modification visible sur Web.
4. Transaction modifiée sur Web → modification récupérée par Android.
5. Création hors-ligne (Android) → synchronisation au retour réseau (mécanisme déjà validé, `SyncConnectivityObserver`).
6. Suppression hors-ligne (Android) → suppression douce propagée au serveur → Web reçoit `deletedAt` non nul au prochain pull.
7. Modification simultanée Web + Android sur la même ligne → conflit résolu LWW, journalisé dans `sync_conflicts` (section 8).
8. Perte de connexion Web pendant un push → aucune perte silencieuse, message explicite + bouton "Réessayer" (section 7).

À ajouter, spécifiques au Web :
9. Token expiré ou révoqué → réponse `401 invalid_token` gérée proprement (redirection vers la connexion, pas un écran cassé).
10. CORS mal configuré → détecté en développement avant la mise en production (un appel bloqué par le navigateur ne doit jamais ressembler à un bug applicatif silencieux).
11. Formatage des montants (section 12) testé sur au moins un cas dépense/revenu par devise supportée.

---

## 17. Déploiement

Ordre recommandé, cohérent avec le plan de migration déjà validé côté sync (`AUDIT-ET-ARCHITECTURE-SYNC.md`, section 10) :
1. Vérifier/corriger l'état réel du schéma MySQL en production (section 3 : confirmer les `FOREIGN KEY` réellement retirées).
2. Mettre en place l'accès réseau (tunnel dev, ou domaine+HTTPS si on saute directement à un hébergement accessible).
3. Ajouter les en-têtes CORS sur `api/sync/*`/`api/auth/*` (additif, section 10).
4. Développer l'app Lovable phase par phase (section 19), chaque phase testée contre le VRAI serveur (dev), jamais de données fictives une fois l'API atteinte.
5. Bascule production : domaine, HTTPS, reverse proxy, firewall MySQL (section 15).

---

## 18. Sauvegarde

- **MySQL** : aucune stratégie de sauvegarde automatique constatée dans les fichiers lus — à mettre en place indépendamment du chantier Web (ex. `mysqldump` quotidien programmé, conservation glissante de plusieurs jours/semaines, testé par une restauration réelle au moins une fois). Hors périmètre strictement "Web", mais devient plus critique dès qu'un second client (Web) écrit dans la même base.
- **Côté application** : le mécanisme de sauvegarde/restauration fichier Android (`BackupRepositoryImpl`, section 1.3) reste un filet de sécurité complémentaire, propre à chaque appareil — ne remplace pas une sauvegarde serveur.

---

## 19. Roadmap (phases, reprend la structure demandée)

| Phase | Contenu | État |
|---|---|---|
| 1 | Analyse du dépôt Android | ✅ Fait — ce document, section 1 |
| 2 | Analyse du serveur PHP | ✅ Fait — section 2, 4 |
| 3 | Analyse de MySQL | ✅ Fait — section 3 |
| 4 | Documentation de l'API existante | ✅ Fait — section 4 |
| 5 | Amélioration éventuelle de l'API | Additifs identifiés : CORS (10), `register.php` optionnel (9), `logout.php` optionnel (9), rate limiting login (10), sync `receipts` (1.1) — aucun n'est bloquant pour démarrer |
| 6 | Authentification Web | À faire — dépend de la Phase "accès réseau" (section 15) réglée en premier |
| 7 | Connexion Web ↔ API PHP | À faire — `SyncService`/`AuthService` (section 14) |
| 8 | Dashboard | À faire |
| 9 | Comptes | À faire |
| 10 | Transactions | À faire |
| 11 | Budgets | À faire |
| 12 | Planifications | À faire |
| 13 | Automatisations | À faire |
| 14 | Prêts/Emprunts | À faire |
| 15 | Reçus | À faire — **dépend d'abord d'un chantier serveur séparé** (ajout à `ENTITY_CONFIGS` + `api/receipts/upload.php`, non fait à ce jour) |
| 16 | Statistiques | À faire |
| 17 | Synchronisation (polling, indicateur d'état) | À faire |
| 18 | Sécurité (CORS, rate limiting, durée de token Web) | À faire, en partie en parallèle des phases précédentes |
| 19 | Tests (section 16) | À faire, à chaque phase, pas seulement à la fin |
| 20 | Déploiement (section 17) | À faire |

---

## 20. Matrice de compatibilité fonctionnelle

| Fonctionnalité | Android | Web (cible) | Entité(s) API | Sync |
|---|---|---|---|---|
| Comptes (espèces, banque, carte, Mobile Money) | ✅ | À construire | `accounts` | ✅ |
| Transactions (dépense/revenu/transfert + frais) | ✅ | À construire | `transactions` | ✅ |
| Catégories | ✅ | À construire | `categories` | ✅ |
| Budgets | ✅ | À construire | `budgets` | ✅ |
| Objectifs d'épargne | ⚠️ Écran non relié dans la nav actuelle | À construire (si activé) | `savings_goals` | ✅ |
| Planifications | ✅ | À construire | `financial_plans`, `financial_plan_items` | ✅ |
| Automatisations | ✅ | À construire | `recurring_transactions`, `recurring_transaction_occurrences` | ✅ |
| Prêts/Emprunts | ✅ | À construire | `persons`, `loans`, `loan_payments` | ✅ |
| Reçus | ✅ (local uniquement) | À construire | `receipts` | ❌ Pas encore synchronisé (voir Phase 5/15) |
| Cartes bancaires (numéro/CVV) | ✅ (chiffré Keystore) | ❌ Hors périmètre (non transportable, décision 6.2) | — | ❌ Exclu volontairement |
| Statistiques | ✅ | À construire | Dérivé de `transactions`/`accounts` | — |
| Thème clair/sombre, devise | ✅ | À construire | `user_preferences` | ✅ |
| Verrouillage biométrique | ✅ (par appareil) | ❌ Non applicable (concept Web différent, voir 2FA/session si besoin plus tard) | — | ❌ Exclu par design |
| Sauvegarde/restauration fichier | ✅ | Hors périmètre (mécanisme propre à un appareil) | — | — |

---

## 21. Critères d'acceptation

- [ ] Une connexion créée sur Android (`username`/`password`) permet de se connecter sur le Web avec les mêmes identifiants.
- [ ] Les 8 scénarios de test bidirectionnels (section 16, points 1 à 8) passent sur le VRAI serveur (pas une donnée simulée).
- [ ] Aucune requête du navigateur ne touche MySQL directement (vérifiable : le navigateur ne connaît que l'URL de l'API).
- [ ] Aucun secret (mot de passe MySQL, clé de signature de token) n'apparaît dans le bundle JavaScript livré au navigateur (vérifiable par inspection du code source livré).
- [ ] Le formatage des montants (section 12) est strictement identique à Android sur au moins 3 devises.
- [ ] Un conflit simultané Web/Android sur la même ligne se résout sans perte silencieuse (vérifiable dans `sync_conflicts`).
- [ ] L'app Android continue de fonctionner à l'identique après toute modification serveur liée au Web (CORS, `register.php`...) — non-régression vérifiée manuellement à chaque étape serveur.
- [ ] HTTPS actif en production, MySQL fermé à toute IP publique.

---

## Annexe — Risques et points ouverts (résumé, à trancher avec l'utilisateur avant de coder)

1. **Option A vs B** (section 0) — recommandation : A.
2. **Accès réseau dev** (section 15) — nécessite un tunnel ou un domaine avant toute connexion réelle depuis Lovable.
3. **État réel des `FOREIGN KEY`** en production (section 3) — à vérifier par une inspection directe du serveur avant d'écrire une nouvelle migration.
4. **Inscription Web** (section 4.4, 9) — activer ou non un `register.php` ?
5. **Durée de token pour le Web** (section 10) — garder 30 jours comme Android, ou une durée plus courte spécifique au navigateur ?
6. **Reçus** — chantier serveur séparé à planifier avant la Phase 15 (section 19).
7. **CORS** — à ajouter, ne casse rien côté Android mais nécessite un accès au serveur pour être déployé.
