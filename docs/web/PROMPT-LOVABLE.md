# Prompt final — à copier-coller dans Lovable

> Copier tout le contenu ci-dessous (à partir de "Contexte") dans la première conversation Lovable du projet. Remplacer `<URL_API>` par l'URL réelle de l'API une fois l'accès réseau réglé (voir cahier des charges, section 15) — un tunnel HTTPS de développement ou un domaine.

---

## Contexte

Arsikina est une application de gestion financière personnelle. Une application Android native existe déjà, en production, avec des utilisateurs réels et des données financières réelles. Tu construis la version Web de la MÊME application, pas un nouveau produit indépendant.

**Contraintes non négociables :**
- **N'utilise PAS Supabase**, ni aucun backend-as-a-service (Firebase, PocketBase, etc.).
- **Ne crée pas de nouvelle base de données.** La base MySQL `arzikina` existe déjà et contient de vraies données.
- **Le navigateur ne doit jamais se connecter directement à MySQL.** Toute lecture et toute écriture passent par l'API PHP existante, décrite ci-dessous.
- **Ne réinvente pas de système d'authentification.** Un seul système d'utilisateurs, déjà en place côté serveur.

## Infrastructure existante (réelle, pas une supposition)

```
Application Web (toi)          Application Android (existante)
        │                              │
        └──────────────┬───────────────┘
                        ▼
              API PHP (Apache2)
     api/auth/login.php · api/sync/push.php · api/sync/pull.php
                        ▼
              MySQL — base `arzikina`
```

L'API n'est **pas** une API REST classique avec un endpoint par ressource. C'est une API générique à 3 endpoints, pilotée par un paramètre `entityType` :

### 1. `POST <URL_API>/api/auth/login.php`
Corps : `{ "identifier": "<username ou email>", "password": "...", "deviceId": "<uuid généré côté navigateur>", "deviceLabel": "Navigateur — <user agent>" }`
Réponse 200 : `{ "token": "<chaîne hex>", "userId": "<uuid>", "expiresAt": <millis epoch> }`
Erreurs : `401 { "error": "invalid_credentials", "message": "..." }` si identifiant ou mot de passe incorrect.

Stocke `token` (mémoire + `localStorage`). Envoie-le dans l'en-tête `Authorization: Bearer <token>` sur CHAQUE appel suivant.

### 2. `GET <URL_API>/api/sync/pull.php?entity_type=<type>&updated_after=<millis>`
Renvoie toutes les lignes de `<type>` appartenant à l'utilisateur authentifié, modifiées après `updated_after` (0 = tout).
Réponse : `{ "entities": [ {...} ], "serverTime": <millis> }`. Conserve `serverTime` comme prochain `updated_after` pour cette entité. Si `entities.length === 500` (lot plafonné), rappelle immédiatement avec ce nouveau curseur — il y a peut-être plus de données à récupérer.
Une ligne avec `deletedAt` non nul a été supprimée : retire-la de l'état local, ne l'affiche pas.

### 3. `POST <URL_API>/api/sync/push.php`
Corps : `{ "entityType": "<type>", "operations": [ { "operation": "CREATE"|"UPDATE"|"DELETE", "entity": { "id": "<uuid généré côté client>", "baseVersion": <version connue ou null pour un CREATE>, ...champs de l'entité en camelCase... } } ] }`
Réponse : `{ "results": [ { "status": "accepted"|"conflict_resolved"|"error", "entityId": "...", "serverEntity": {...} } ], "serverTime": <millis> }`.
**Applique toujours `serverEntity` à l'état local**, même quand `status === "accepted"` — c'est l'état confirmé par le serveur (version/horodatage à jour).
Si `status === "conflict_resolved"` : une autre modification (faite entre-temps sur un autre appareil) a été retenue à la place de la tienne. Affiche un message clair à l'utilisateur ("Cette donnée a été modifiée ailleurs, la version la plus récente a été conservée") et affiche `serverEntity`, jamais ta version locale écrasée silencieusement.

### Les 13 `entityType` valides et leurs champs (camelCase, exactement comme reçus/envoyés)

```
categories                { name, icon, colorArgb, type }
accounts                  { name, icon, colorArgb, currencyCode, initialBalanceMinor, type,
                             cardLastFourDigits?, cardExpiryMonth?, cardExpiryYear?,
                             isExcludedFromStatistics, mobileMoneyPackageName?, displayOrder,
                             savingsTargetAmount?, savingsDescription? }
                             # type = "SAVINGS_GOAL" : objectif d'épargne (voir docs/OBJECTIF-EPARGNE-COMPTE.md)
persons                   { name, phone? }
transactions               { amount, type, accountSyncId, transferAccountSyncId?, categorySyncId?,
                             date, description, latitude?, longitude?, paymentMethod?,
                             feeTransactionSyncId?, feeType? }
budgets                   { categorySyncId, period, limitAmount, currencyCode, startDate?, endDate? }
savings_goals              { name, targetAmount, currentAmount, currencyCode, deadline? }   # ANCIEN système, ne plus afficher
loans                     { personSyncId, accountSyncId, type, amount, amountRepaid,
                             remainingAmount, startDate, dueDate, reason, reasonCustomText?,
                             repaymentMode, description, status, transactionSyncId }
loan_payments              { loanSyncId, accountSyncId, amount, date, note, transactionSyncId }
recurring_transactions     { type, amount, accountSyncId, categorySyncId?, description,
                             paymentMethod?, startDate, endDate?, frequency, nextExecutionDate,
                             isActive, triggerHour, triggerMinute }
recurring_transaction_occurrences  { recurringTransactionSyncId, scheduledDate, status,
                             transactionSyncId?, processedAt? }
financial_plans            { name, description?, availableAmount, targetAmount?, periodType,
                             startDate?, endDate?, icon, colorArgb, status }
financial_plan_items       { planSyncId, name, amount, actualAmount?, categorySyncId?,
                             description?, plannedDate?, priority, status, transactionSyncId? }
user_preferences           { themeMode, currencyCode }   ← au plus 1 ligne par utilisateur
```

Chaque entité, en plus de ses champs propres, porte toujours : `id` (UUID v4 généré côté client à la création), `createdAt`, `updatedAt` (millis epoch), `deletedAt` (toujours `null` sauf suppression), `version` (entier, commence à 1).

**Les champs `*SyncId` référencent l'`id` (UUID) d'une autre entité de cette même liste** — jamais un identifiant technique séparé. Exemple : une transaction avec `accountSyncId: "8e6d..."` référence le compte dont `id === "8e6d..."`.

### Ce qui N'EXISTE PAS côté serveur (ne l'invente pas, ne le suppose pas)

- Pas d'endpoint d'inscription — pour l'instant, considère que tout compte utilisé sur le Web a déjà été créé (via l'app Android ou manuellement). Si l'inscription Web est nécessaire, demande d'abord — c'est un choix produit, pas un détail technique.
- Pas d'endpoint de déconnexion serveur — une déconnexion Web se contente d'oublier le token localement.
- Pas d'entité `receipts` dans la liste ci-dessus — les reçus PDF ne sont pas encore synchronisables entre appareils. Ne construis pas cet écran avant qu'on te le demande explicitement.
- Pas de route d'agrégation/statistiques précalculée — calcule le Dashboard et les Statistiques côté client à partir de `transactions`/`accounts`/`budgets` déjà récupérés, exactement comme le fait l'app Android.

## Règles d'affichage à respecter STRICTEMENT

- Dépense : `500 CFA` (ou la devise du compte) en **rouge**. Jamais `-500 CFA`.
- Revenu : `500 CFA` en **vert**. Jamais `+500 CFA`.
- Montants avec séparateur de milliers : `10 000 CFA`. Jamais `10000.00`.
- Couleur principale de l'interface : `#42B998`.
- Prévoir mode clair et mode sombre — la préférence vient de `user_preferences.themeMode` (`SYSTEM`/`LIGHT`/`DARK`), pas d'un réglage Web séparé.
- Comptes exclus des statistiques (`accounts.isExcludedFromStatistics`) : à respecter dans tout calcul agrégé (Dashboard, Statistiques) — leur solde compte dans le total mais pas dans les moyennes/agrégats "personnels".

## Architecture attendue côté code

```
UI (pages / composants)
   ↓
Services métier (AuthService, AccountService, TransactionService, CategoryService,
                 BudgetService, PlanningService, AutomationService,
                 LoanService, StatisticsService, SyncService, SettingsService)
   ↓
Un seul client API (gère le token, les en-têtes, le retry réseau)
   ↓
api/auth/login.php · api/sync/push.php · api/sync/pull.php
```

Aucune règle métier dans les composants d'affichage — ils appellent des services, jamais `fetch` directement. `SyncService` centralise un curseur `updated_after` PAR `entityType` (pas un curseur global) et orchestre le pull initial (paginé par lots de 500, section pull.php ci-dessus) puis les pulls périodiques.

## Synchronisation / temps réel

Pas de WebSocket disponible sur l'infrastructure actuelle (Apache2/PHP classique). Utilise un **polling léger** : re-pull automatique toutes les 15 à 30 secondes sur les écrans actifs (Dashboard, Transactions), plus un pull immédiat quand l'onglet redevient visible (`document.visibilitychange`). Affiche un indicateur d'état simple : "✓ Synchronisé" / "↻ synchronisation..." / "⚠ erreur, réessayer" — pas de détail technique exposé à l'utilisateur.

## Structure des pages attendue

Connexion · Dashboard · Comptes (avec cartes bancaires en sous-vue d'un compte de type carte) · Transactions · Budgets · Planifications · Automatisations · Prêts/Emprunts · Statistiques · Utilitaires (grille de raccourcis) · Paramètres · Profil · Synchronisation (état, dernière synchro, bouton manuel).

Responsive : sidebar sur desktop, navigation compacte/bottom navigation sur mobile Web.

## Sécurité

- Jamais de mot de passe MySQL, de clé secrète ou d'identifiant de base de données dans le code JavaScript. Le seul secret manipulé côté client est le token de session, obtenu au login.
- N'affiche jamais en clair par défaut les informations sensibles d'un compte (numéro de carte, CVV — d'ailleurs non synchronisés, l'app Android les garde chiffrés localement et ne les envoie jamais au serveur).
- Échappe systématiquement tout texte libre saisi par l'utilisateur (description de transaction, nom de catégorie...) avant affichage — protection XSS, d'autant plus importante que le token de session vit en `localStorage`.

## Ce que tu dois me demander avant de commencer à coder, si ce n'est pas déjà précisé

1. L'URL réelle de l'API à utiliser pour le développement (le serveur est sur un réseau local, `<URL_API>` doit être remplacé par une adresse joignable depuis Lovable — probablement un tunnel HTTPS temporaire).
2. Si l'inscription doit être possible depuis le Web, ou si seuls les comptes déjà existants doivent pouvoir se connecter.
3. Si les comptes de type carte bancaire doivent apparaître sur le Web alors que leur numéro/CVV ne sont, par design, jamais synchronisés (seuls les 4 derniers chiffres et l'expiration le sont).

Ne code rien avant d'avoir une réponse à ces trois points s'ils ne sont pas déjà tranchés dans la conversation.
