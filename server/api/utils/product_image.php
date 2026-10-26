<?php

declare(strict_types=1);

require_once __DIR__ . '/uuid.php';

/**
 * Stockage PRIVÉ des photos de produits (module Boutique).
 *
 * Même modèle que les logos d'émetteur (`utils/issuer_logo.php`) : les fichiers vivent dans
 * `product_images/{user_id}/{nom}` (dossier protégé par `.htaccess`, voir `server/product_images/`) et ne sont
 * accessibles que par `api/shop/get_product_image.php` (Bearer valide, dossier de l'utilisateur authentifié
 * uniquement : le chemin est construit à partir du token, jamais d'un paramètre).
 *
 * Les noms sont ceux générés par les clients (UUID + `.jpg`, voir `ProductImageStore` Android) et sont
 * IMMUABLES : une photo remplacée reçoit un nouveau nom. Un envoi est donc idempotent. Les anciens fichiers ne
 * sont pas purgés (une synchronisation en retard peut encore les citer) ; le quota borne l'espace utilisé.
 * Le client réduit chaque photo à 1024 px maximum (JPEG, quelques centaines de Ko).
 */

/** 2 Mo par photo. */
const PRODUCT_IMAGE_MAX_BYTES = 2_000_000;

/** Plafond de fichiers par utilisateur (anti-abus), largement au-dessus d'un catalogue réaliste. */
const PRODUCT_IMAGE_MAX_FILES_PER_USER = 3000;

/** UUID (ou assimilé) + `.jpg` : empêche toute traversée de dossier. Seul le JPEG est produit par les clients. */
function isValidProductImageName(string $name): bool
{
    return preg_match('/^[A-Za-z0-9-]{1,64}\.jpg$/', $name) === 1;
}

/** Dossier de l'utilisateur ; `null` si [$userId] n'est pas un UUID. */
function productImageDirectory(string $userId): ?string
{
    return isValidUuid($userId) ? __DIR__ . '/../../product_images/' . $userId : null;
}

/** Vrai si les OCTETS du fichier sont un JPEG (jamais d'après l'extension ni le Content-Type annoncés). */
function isJpegFile(string $path): bool
{
    $handle = @fopen($path, 'rb');
    if ($handle === false) {
        return false;
    }
    $head = (string) fread($handle, 3);
    fclose($handle);
    return strncmp($head, "\xFF\xD8\xFF", 3) === 0;
}

/** Supprime tout le dossier de photos de l'utilisateur (suppression de compte). */
function deleteProductImagesForUser(string $userId): void
{
    $directory = productImageDirectory($userId);
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
