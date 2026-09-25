-- Arzikina — L'objectif d'épargne devient un TYPE DE COMPTE (`accounts.type = 'SAVINGS_GOAL'`).
--
-- Contexte : jusqu'ici, un objectif d'épargne vivait dans sa propre table `savings_goals`
-- (utilitaire « Épargne ») avec un `current_amount` saisi à la main, sans aucune transaction. Il est
-- désormais un compte à part entière : solde = solde initial + transactions/transferts (même calcul
-- que tous les comptes, voir `sync/account_balances.php`), plus un montant cible.
-- Équivalent Android : `Migration30To31.kt` (colonnes) + `LegacySavingsGoalMigrator.kt` (données).
--
-- À exécuter MANUELLEMENT sur la base de production (aucun outil de migration automatique dans ce
-- projet — même convention que 001 à 005), AVANT de publier la version Android/Web qui envoie ces
-- champs. L'ALTER (étape 1) ne s'exécute qu'une fois ; les étapes 2 et 3 (données) sont
-- IDEMPOTENTES : relancées, elles ne créent aucun doublon (vérifié sur MariaDB).
--
-- NON DESTRUCTIF : aucune ligne n'est supprimée physiquement. Les anciens objectifs sont
-- simplement marqués supprimés (`deleted_at`, suppression douce habituelle du projet) une fois leur
-- compte créé — leurs données restent intactes dans `savings_goals` (y compris `deadline`, qui n'a
-- pas d'équivalent dans le nouveau modèle).

-- 1. Colonnes propres aux objectifs — NULLABLES (NULL pour tout compte qui n'est pas un objectif).
ALTER TABLE accounts
    ADD COLUMN savings_target_amount BIGINT NULL,
    ADD COLUMN savings_description TEXT NULL;

-- 2. Conversion des anciens objectifs actifs en comptes `SAVINGS_GOAL`.
--
-- `id` du compte = `id` de l'ancien objectif (UUID) : c'est ce qui garantit l'absence de doublon.
-- Chaque appareil Android convertit lui aussi ses objectifs locaux avec CE MÊME UUID (voir
-- `LegacySavingsGoalMigrator`) : son push CREATE tombe alors sur la ligne déjà créée ici, que
-- `push.php` renvoie telle quelle (création idempotente) au lieu d'en insérer une seconde.
--
-- `initial_balance_minor = current_amount` : l'ancien montant épargné devient le solde initial
-- (aucune transaction n'existait pour lui). `is_excluded_from_statistics = 1` : cet argent n'était
-- jusqu'ici compté dans AUCUN solde total — l'inclure d'office ferait bondir le solde total de
-- l'utilisateur (voire compter deux fois un montant déjà présent sur un autre compte). L'utilisateur
-- peut réactiver l'inclusion depuis « Modifier le compte ». Les objectifs créés APRÈS cette
-- migration sont, eux, des comptes normaux inclus par défaut.
--
-- Icône `SAVINGS`, couleur 0xFF10B981 = 4279286145 : couleur par défaut du sélecteur de couleur des
-- comptes (voir `ColorPalette.kt`), pour qu'elle reste sélectionnée dans « Modifier le compte ».
-- La couleur principale Arzikina #42B998 est, elle, celle des barres de progression.
-- `display_order` : ajoutés à la fin de la liste de l'utilisateur, dans l'ordre de création (même
-- calcul portable sans fonction fenêtrée que 004).
SET @now_ms = CAST(ROUND(UNIX_TIMESTAMP(NOW(3)) * 1000) AS SIGNED);

INSERT INTO accounts (
    id, user_id, name, icon, color_argb, currency_code, initial_balance_minor, type,
    card_last_four_digits, card_expiry_month, card_expiry_year, is_excluded_from_statistics,
    mobile_money_package_name, display_order, savings_target_amount, savings_description,
    created_at, updated_at, deleted_at, version
)
SELECT
    g.id,
    g.user_id,
    g.name,
    'SAVINGS',
    4279286145,
    g.currency_code,
    GREATEST(g.current_amount, 0),
    'SAVINGS_GOAL',
    NULL, NULL, NULL,
    1,
    NULL,
    COALESCE((SELECT MAX(a.display_order) + 1 FROM accounts a WHERE a.user_id = g.user_id), 0)
        + (
            SELECT COUNT(*)
            FROM savings_goals g2
            WHERE g2.user_id = g.user_id
              AND g2.deleted_at IS NULL
              AND (g2.created_at < g.created_at OR (g2.created_at = g.created_at AND g2.id < g.id))
        ),
    GREATEST(g.target_amount, 1),
    NULL,
    g.created_at,
    @now_ms,
    NULL,
    1
FROM savings_goals g
WHERE g.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM accounts x WHERE x.id = g.id);

-- 3. Suppression DOUCE des anciens objectifs désormais représentés par un compte : les appareils
-- la reçoivent au prochain pull (`savings_goals` reste synchronisée pour cela), ce qui les empêche
-- de réapparaître ailleurs. `updated_at` avancé pour que le pull incrémental la voie.
UPDATE savings_goals g
JOIN accounts a ON a.id = g.id
SET g.deleted_at = @now_ms,
    g.updated_at = @now_ms,
    g.version = g.version + 1
WHERE g.deleted_at IS NULL;
