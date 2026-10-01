-- Arzikina — « Transformer un prêt/emprunt en cadeau ».
--
-- Ajoute sur `loans` :
-- - `gifted_amount`       : part du solde restant transformée en cadeau (0 = jamais transformé) ;
-- - `gift_transaction_id` : UUID de la transaction « Cadeaux » qui porte ce montant (NULL sinon).
--                           Égal à `transaction_id` quand rien n'avait été remboursé (la transaction
--                           de décaissement est alors reclassée sur place, sans nouvelle ligne) ;
-- - `gifted_at`           : instant de la transformation (epoch millis, NULL sinon).
-- Le nouveau statut `GIFTED` tient dans `status VARCHAR(32)` existant : aucune contrainte à adapter.
-- Équivalent Android : `Migration32To33.kt`.
--
-- À exécuter MANUELLEMENT sur la base de production (même convention que 001 à 007), AVANT de
-- publier la version Android/Web qui envoie `giftedAmount`/`giftTransactionSyncId`/`giftedAt`
-- (sinon `push.php` tenterait d'écrire des colonnes inexistantes).
--
-- Non destructif : colonnes additives, `gifted_amount` à 0 pour tous les prêts/emprunts existants
-- (exactement leur état réel), aucune transaction modifiée. La table `transactions` n'est PAS
-- touchée : la traçabilité « transformé depuis un prêt/emprunt » est portée par le prêt.
--
-- SANS FOREIGN KEY sur `gift_transaction_id` (même raisonnement que `loans.transaction_id`) : la
-- transaction cadeau peut arriver dans un push ultérieur. Index pour retrouver rapidement le
-- prêt/emprunt d'origine d'une transaction cadeau (détail de transaction côté Web).
ALTER TABLE loans
    ADD COLUMN gifted_amount BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN gift_transaction_id CHAR(36) NULL,
    ADD COLUMN gifted_at BIGINT NULL,
    ADD KEY idx_loans_gift_transaction (gift_transaction_id);
