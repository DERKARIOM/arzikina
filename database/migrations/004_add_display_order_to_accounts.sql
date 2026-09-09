-- Arzikina — Réorganisation des comptes par glisser-déposer (Android) : ordre d'affichage
-- synchronisé, voir `entity_sync_configs.php` (entrée `accounts`) et
-- `app/.../data/local/database/Migration27To28.kt` (équivalent Room, même raisonnement).
--
-- À exécuter MANUELLEMENT sur la base de production (aucun outil de migration automatique dans ce
-- projet — même convention que 001/002/003). Les deux instructions peuvent être lancées ensemble :
-- contrairement au script 003, il n'y a ici aucune branche conditionnelle à vérifier avant coup.

-- Colonne NOT NULL : `display_order` pilote directement le tri (`ORDER BY display_order ASC`
-- côté Android/Web), une valeur NULL serait ambiguë. `DEFAULT 0` satisfait uniquement la
-- contrainte SQL le temps de l'ALTER — le vrai calcul de position est fait par l'UPDATE suivant,
-- pas par ce défaut.
ALTER TABLE accounts
    ADD COLUMN display_order INT NOT NULL DEFAULT 0;

-- Rattrapage explicite de l'ordre déjà affiché AVANT cette migration (tri implicite par ancienneté
-- de création) : compte, pour chaque compte, le nombre de comptes plus anciens du même
-- utilisateur — équivalent d'un ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY created_at) sans
-- dépendre d'une version de MySQL supportant les fonctions fenêtrées (portable, même approche que
-- la migration Room correspondante). Sans ce rattrapage, tous les comptes existants partageraient
-- `display_order = 0` et se retrouveraient dans un ordre arbitraire côté client.
--
-- Table dérivée `ord` (PAS une sous-requête corrélée directe dans le SET) : MySQL refuse de
-- mettre à jour une table tout en la lisant directement dans la même requête
-- (`#1093 - You can't specify target table 'a' for update in FROM clause`) — passer par une
-- sous-requête dans le FROM/JOIN la matérialise d'abord en table temporaire, ce qui contourne
-- cette restriction proprement (aucun changement de résultat, juste de forme).
--
-- Tri par (created_at, id) — PAS created_at seul : plusieurs comptes créés en lot partagent
-- exactement le même `created_at` (ex. les 5 comptes par défaut semés à l'inscription, voir
-- DefaultAccounts.seed côté Android, tous horodatés au même `now`) — sans ce départage par `id`
-- (constaté en vérifiant le résultat sur la base réelle : 4 comptes à `display_order = 0`, 5 à
-- `13`), leur ordre relatif resterait indéterminé, potentiellement différent entre MySQL et
-- SQLite (aucun des deux moteurs ne garantit un ordre stable pour des valeurs égales). `id` est
-- arbitraire (UUID) mais STABLE : le même calcul, exécuté deux fois, donne toujours le même
-- résultat — c'est tout ce qui compte ici (voir aussi Migration27To28.kt, même tri appliqué
-- côté Room pour que les deux bases convergent vers un ordre identique).
UPDATE accounts a
JOIN (
    SELECT t.id,
           (
               SELECT COUNT(*)
               FROM accounts b
               WHERE b.user_id = t.user_id
                 AND (b.created_at < t.created_at
                      OR (b.created_at = t.created_at AND b.id < t.id))
           ) AS computed_order
    FROM accounts t
) AS ord ON ord.id = a.id
SET a.display_order = ord.computed_order;
