-- Arzikina — Mode d'utilisation choisi à l'inscription (« Gestion de boutique », « Finances personnelles »,
-- « Boutique et finances personnelles »).
--
-- Contexte : l'inscription Android propose un choix qui détermine les comptes créés par défaut. Le choix est
-- enregistré sur le serveur pour être retrouvé après une déconnexion, une mise à jour ou un changement d'appareil.
--
-- Valeurs : 'SHOP' | 'PERSONAL' | 'BOTH' (mêmes noms que l'enum Android `UsageMode`, ne jamais les renommer).
-- NULL = « jamais demandé » : utilisateurs inscrits avant ce choix, ou inscrits depuis le web. Rien ne leur est imposé.
--
-- Migration ADDITIVE : aucune ligne existante n'est modifiée. À exécuter UNE fois (phpMyAdmin) ; l'API reste
-- compatible avant et après : register.php n'écrit cette colonne que si le client envoie `usageMode`.

ALTER TABLE users
    ADD COLUMN usage_mode VARCHAR(10) NULL DEFAULT NULL;
