<?php

declare(strict_types=1);

/**
 * MODÈLE — ce fichier `.example.php` est versionné dans Git et ne contient AUCUN secret réel,
 * uniquement la structure attendue. Sur le serveur, copier son contenu (avec de vraies valeurs)
 * vers `/home/Admin/config_arzikina_secrets.php` — même dossier et même principe que
 * `/home/Admin/config_arzikina.php` (voir `config/database.php`) : HORS de la racine web
 * (`/var/www/html/...`), jamais dans Git, jamais exposé par une URL.
 *
 * Voir docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md, section 6.1, pour le raisonnement complet sur
 * l'authentification par token.
 */

/**
 * Clé utilisée pour signer les tokens de session émis par `api/auth/login.php`. Chaîne aléatoire
 * longue (≥ 64 caractères hexadécimaux) — génération suggérée, une seule fois, en PHP :
 *
 *     php -r "echo bin2hex(random_bytes(32));"
 *
 * ATTENTION : changer cette clé invalide immédiatement TOUS les tokens actifs (déconnecte tous les
 * appareils de tous les utilisateurs) — à ne faire qu'en cas de compromission suspectée.
 */
define('TOKEN_SIGNING_KEY', 'REMPLACER_PAR_UNE_VALEUR_ALEATOIRE_GENEREE_UNE_SEULE_FOIS');

/**
 * Durée de vie d'un token avant expiration, en secondes. 30 jours par défaut : assez long pour ne
 * jamais redemander le mot de passe à chaque synchronisation (cahier des charges, section 12),
 * assez court pour qu'un token volé ne reste pas exploitable indéfiniment. Un appareil qui se
 * synchronise régulièrement peut renouveler son token avant expiration (voir la logique de
 * `api/auth/refresh.php`, à écrire dans une étape ultérieure).
 */
define('TOKEN_EXPIRY_SECONDS', 60 * 60 * 24 * 30);
