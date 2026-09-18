-- Arzikina — Synchronisation des modèles de transaction ("Marketplace personnelle").
--
-- Contexte : cette fonctionnalité existe déjà côté Android (voir
-- `domain/model/TransactionTemplate.kt`/`data/local/entity/TransactionTemplateEntity.kt`,
-- Migrations Room 28→29 et 29→30) mais a été construite DÉLIBÉRÉMENT sans synchronisation serveur
-- (voir la KDoc de tête de `TransactionTemplateRepositoryImpl`) — les colonnes `syncId`/
-- `deletedAt`/`version` étaient déjà posées côté Android, jamais poussées. Cette migration ajoute
-- leur pendant serveur, même principe que `recurring_transactions`/`budgets`/`loans` (voir
-- `entity_sync_configs.php`, entrée `transaction_templates` ajoutée en même temps que ce script).
--
-- À exécuter MANUELLEMENT sur la base de production (aucun outil de migration automatique dans ce
-- projet — même convention que 001/002/003/004).
--
-- `category_id`/`account_id` : SANS `FOREIGN KEY` dès la création, contrairement au schéma initial
-- de `recurring_transactions`/`budgets`/`loans` (qui avaient des contraintes réelles retirées après
-- coup, voir les commentaires `entity_sync_configs.php` correspondants — "à supprimer avant
-- déploiement"). Un modèle peut être créé hors ligne avant que son compte/sa catégorie n'ait été
-- confirmé(e) par le serveur (compte/catégorie tout juste créés sur le même appareil, pas encore
-- synchronisés) : une contrainte stricte ferait échouer ce push au lieu de le laisser attendre le
-- push suivant, une fois le compte/la catégorie connus du serveur — leçon déjà tirée pour les
-- entités précédentes, appliquée ici directement plutôt que corrigée après coup.
--
-- `category_id` en `NOT NULL` (contrairement à `recurring_transactions.category_id`, nullable) :
-- un modèle ne représente jamais un virement entre comptes propres (voir la doc de tête de
-- `TransactionTemplate.kt`, "TOUJOURS renseigné").
--
-- `default_hour`/`default_minute` NULLABLES : "Heure par défaut", extension optionnelle du cahier
-- des charges — `NULL` = pas d'heure par défaut sur ce modèle, même convention que côté Android.
CREATE TABLE IF NOT EXISTS transaction_templates (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    name VARCHAR(191) NOT NULL,
    type VARCHAR(16) NOT NULL,
    amount BIGINT NOT NULL,
    category_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    description TEXT NOT NULL,
    is_favorite TINYINT(1) NOT NULL DEFAULT 0,
    default_hour TINYINT NULL,
    default_minute TINYINT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT NULL,
    version INT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    KEY idx_transaction_templates_user_updated (user_id, updated_at),
    KEY idx_transaction_templates_account (account_id),
    KEY idx_transaction_templates_category (category_id),
    CONSTRAINT fk_transaction_templates_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
