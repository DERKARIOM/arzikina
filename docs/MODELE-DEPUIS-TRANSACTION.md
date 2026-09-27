# Créer un modèle à partir d'une transaction

## 1. Relation transaction ↔ modèle

Avant cette fonctionnalité, **aucune relation** n'existait : un modèle (Marketplace personnelle)
n'était qu'un raccourci, et une transaction créée avec « Acheter » ne gardait aucun lien vers lui.

La seule relation ajoutée est portée par le **modèle** :

| Android (Room `transaction_templates`) | MySQL (`transaction_templates`) | Sync (`push`/`pull`) |
|---|---|---|
| `sourceTransactionId` (id local, nullable) | `source_transaction_id CHAR(36) NULL` (UUID, indexé, sans FK) | `sourceTransactionSyncId` |

- La table `transactions` n'est **pas modifiée**. La transaction d'origine reste strictement
  identique : montant, date, compte, type, `updated_at`, `version`.
- La relation est fixée à la création : modifier le modèle ne la change jamais, et un modèle
  dupliqué ne la reprend pas.
- Le modèle reste indépendant. Le modifier ne touche jamais la transaction, et supprimer le modèle
  rend l'action « Créer un modèle » à nouveau disponible pour cette transaction.
- Aucune migration de données : les anciens modèles et les anciennes transactions restent sans
  relation.

## 2. Règles (identiques Android / Web)

Ce que propose le menu ⋮ d'une transaction enregistrée :

| Situation de la transaction | Action proposée |
|---|---|
| Un modèle actif a été créé à partir d'elle | « Voir le modèle » (édition du modèle) |
| Transfert | Aucune |
| Ligne de frais (`fee_type` renseigné) | Aucune |
| Liée à un prêt ou emprunt | Aucune |
| Création d'une transaction (pas encore enregistrée) | Aucune |
| Tout autre cas | « Créer un modèle » |

« Créer un modèle » ouvre le formulaire de modèle existant, prérempli à partir de la transaction
**enregistrée**. Rien n'est créé avant que l'utilisateur valide : le formulaire tient lieu de
confirmation.

Champs préremplis :

| Champ du modèle | Valeur |
|---|---|
| Nom | La description ; à défaut le nom de la catégorie ; à défaut vide (60 caractères au plus) |
| Type | Celui de la transaction (dépense ou revenu) |
| Montant | Montant principal. Les frais sont une transaction séparée, non repris |
| Catégorie | Celle de la transaction ; à choisir si absente |
| Compte | Celui de la transaction |
| Description | Celle de la transaction |

Ne sont jamais repris : date et heure (l'« heure par défaut » reste désactivée), identifiants,
horodatages, reçu, mode de paiement, position, frais, champs de synchronisation.

Anti-doublon : avant d'enregistrer, vérifier qu'aucun modèle actif (`deleted_at IS NULL`) n'a le
même `source_transaction_id`. Si c'est le cas, ne pas en créer un second : afficher « Modèle déjà
créé » et proposer « Voir le modèle ». Android fait cette vérification à l'ouverture du formulaire
**et** au moment d'enregistrer (`TemplateAlreadyLinkedException`).

## 3. Web — à implémenter

1. Au pull des modèles, lire `sourceTransactionSyncId`.
2. Au push d'un modèle, toujours envoyer `sourceTransactionSyncId` (UUID de la transaction ou
   `null`). Un client qui omet ce champ laisse la valeur serveur inchangée.
3. Dans le détail ou le menu d'une transaction, appliquer les règles de la section 2.
4. « Voir le modèle » ouvre le modèle actif dont `sourceTransactionSyncId` = id de la transaction.

## 4. Déploiement

1. Exécuter `database/migrations/007_transaction_template_source.sql` sur la base de production.
2. Déployer `server/api/config/entity_sync_configs.php`.
3. Publier l'application Android (base Room v32), puis le Web.
