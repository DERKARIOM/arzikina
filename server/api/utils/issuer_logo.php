<?php

declare(strict_types=1);

require_once __DIR__ . '/uuid.php';

/**
 * Stockage PRIVÉ des logos d'émetteur de reçus (utilitaire « Gestion de reçus »).
 *
 * Contrairement aux photos de profil (`avatars/`), un logo accompagne des documents
 * professionnels : il n'est JAMAIS servi directement par le serveur web. Les fichiers vivent dans
 * `issuer_logos/{user_id}/{nom}` (dossier protégé par `.htaccess`, voir `server/issuer_logos/`) et
 * ne sont accessibles que par `api/issuer/get_logo.php`, qui exige un Bearer valide ET ne lit que le
 * dossier de l'utilisateur authentifié (isolation structurelle : le chemin est construit à partir du
 * token, jamais d'un paramètre de requête).
 *
 * Les noms sont ceux générés par les clients (UUID + extension, voir `IssuerLogoStorage` Android) et
 * sont IMMUABLES : un logo modifié reçoit un nouveau nom. Un envoi est donc idempotent.
 */

/** 2 Mo : un logo est réduit à 512 px maximum côté client (quelques dizaines de Ko en pratique). */
const ISSUER_LOGO_MAX_BYTES = 2_000_000;

/** Plafond de fichiers par utilisateur (anti-abus) : les anciens logos et signatures restent référencés par les
 *  anciens reçus, il n'y a donc pas de purge automatique. */
const ISSUER_LOGO_MAX_FILES_PER_USER = 100;

/** Même règle que `IssuerLogoPolicy.isSafeFileName` (Android) : empêche toute traversée de dossier. */
function isValidIssuerLogoName(string $name): bool
{
    return preg_match('/^[A-Za-z0-9-]{1,64}\.(png|jpg)$/', $name) === 1;
}

/** Dossier de l'utilisateur ; `null` si [$userId] n'est pas un UUID (jamais construit avec autre chose). */
function issuerLogoDirectory(string $userId): ?string
{
    return isValidUuid($userId) ? __DIR__ . '/../../issuer_logos/' . $userId : null;
}

/**
 * Type d'image détecté sur les OCTETS du fichier (jamais sur l'extension ni le Content-Type annoncés).
 * @return 'png'|'jpg'|null
 */
function detectIssuerLogoExtension(string $path): ?string
{
    $handle = @fopen($path, 'rb');
    if ($handle === false) {
        return null;
    }
    $head = (string) fread($handle, 8);
    fclose($handle);

    if (strncmp($head, "\x89PNG\r\n\x1a\n", 8) === 0) {
        return 'png';
    }
    if (strncmp($head, "\xFF\xD8\xFF", 3) === 0) {
        return 'jpg';
    }
    return null;
}

/** Supprime tout le dossier de logos de l'utilisateur (suppression de compte). */
function deleteIssuerLogosForUser(string $userId): void
{
    $directory = issuerLogoDirectory($userId);
    if ($directory === null || !is_dir($directory)) {
        return;
    }
    foreach (glob($directory . '/*') ?: [] as $file) {
        if (is_file($file)) {
            @unlink($file);
        }
    }
    @rmdir($directory);
}
