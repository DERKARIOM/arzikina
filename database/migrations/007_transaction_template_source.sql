-- Arzikina — « Créer un modèle à partir d'une transaction ».
--
-- Ajoute la SEULE relation transaction ↔ modèle du projet, portée par le MODÈLE :
-- `transaction_templates.source_transaction_id` = UUID de la transaction à partir de laquelle le
-- modèle a été créé (NULL sinon). La table `transactions` n'est PAS modifiée : une transaction
-- existante reste strictement identique (montant, date, compte, `updated_at`, `version`…).
-- Équivalent Android : `Migration31To32.kt` (colonne `sourceTransactionId`).
--
-- À exécuter MANUELLEMENT sur la base de production (même convention que 001 à 006), AVANT de
-- publier la version Android/Web qui envoie `sourceTransactionSyncId`.
--
-- Non destructif : colonne NULLABLE, aucun modèle ni aucune transaction existants modifiés, aucun
-- modèle créé automatiquement pour les anciennes transactions.
--
-- SANS FOREIGN KEY (même raisonnement que `account_id`/`category_id`, voir 005) : un modèle peut
-- arriver avant sa transaction d'origine lors d'une synchronisation, et supprimer la transaction ne
-- doit jamais supprimer ni bloquer le modèle, qui en est indépendant. Index pour retrouver
-- rapidement « le modèle créé à partir de cette transaction » (Web).
ALTER TABLE transaction_templates
    ADD COLUMN source_transaction_id CHAR(36) NULL,
    ADD KEY idx_transaction_templates_source_transaction (source_transaction_id);
