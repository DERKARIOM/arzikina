<?php

declare(strict_types=1);

/**
 * Politique de version Android — LOGIQUE UNIQUE partagée par l'endpoint public
 * (`api/app/version_policy.php`) et, plus tard, par le contrôle appliqué aux endpoints protégés
 * (étape « enforcement »). Toute comparaison se fait sur le `versionCode` ENTIER, jamais sur le nom
 * de version (« 1.10.0 » < « 1.9.0 » en comparaison de chaînes).
 */

/**
 * @return array{minVersionCode: int, forceUpdate: bool, updateUrl: string, blockUnversionedClients: bool,
 *               maintenanceEnabled: bool, maintenanceMessage: ?string}
 */
function loadVersionPolicy(PDO $pdo): array
{
    $row = $pdo->query('SELECT * FROM app_version_policy WHERE id = 1 LIMIT 1')->fetch();
    if ($row === false) {
        // Table vide (migration non exécutée jusqu'au bout) : politique neutre, rien n'est bloqué.
        return [
            'minVersionCode' => 0,
            'forceUpdate' => false,
            'updateUrl' => 'https://play.google.com/store/apps/details?id=com.naniger.arzikina',
            'blockUnversionedClients' => false,
            'maintenanceEnabled' => false,
            'maintenanceMessage' => null,
        ];
    }
    return [
        'minVersionCode' => (int) $row['min_version_code'],
        'forceUpdate' => (bool) $row['force_update'],
        'updateUrl' => (string) $row['update_url'],
        'blockUnversionedClients' => (bool) $row['block_unversioned_clients'],
        'maintenanceEnabled' => (bool) $row['maintenance_enabled'],
        'maintenanceMessage' => $row['maintenance_message'] !== null ? (string) $row['maintenance_message'] : null,
    ];
}

/**
 * Dernière version PUBLIÉE (statut `published`, plus grand `version_code`), ou null.
 *
 * @return array{versionCode: int, versionName: string, releaseNotes: ?string}|null
 */
function loadLatestPublishedVersion(PDO $pdo): ?array
{
    $row = $pdo->query(
        "SELECT version_code, version_name, release_notes FROM android_versions
         WHERE status = 'published' ORDER BY version_code DESC LIMIT 1"
    )->fetch();
    if ($row === false) {
        return null;
    }
    return [
        'versionCode' => (int) $row['version_code'],
        'versionName' => (string) $row['version_name'],
        'releaseNotes' => $row['release_notes'] !== null ? (string) $row['release_notes'] : null,
    ];
}

/**
 * Nom lisible (`versionName`) de la version portant ce `versionCode`, ou null si inconnue.
 * Sert à afficher « version requise 1.3.0 » plutôt qu'un simple numéro dans l'app.
 */
function loadVersionNameByCode(PDO $pdo, int $versionCode): ?string
{
    if ($versionCode <= 0) {
        return null;
    }
    $stmt = $pdo->prepare('SELECT version_name FROM android_versions WHERE version_code = :code LIMIT 1');
    $stmt->execute(['code' => $versionCode]);
    $name = $stmt->fetchColumn();
    return $name === false ? null : (string) $name;
}

/**
 * Extrait le versionCode annoncé par le client (en-tête `X-App-Version-Code`), ou null s'il est
 * absent/invalide (anciennes installations, console Web, outils).
 */
function readClientVersionCode(): ?int
{
    $raw = $_SERVER['HTTP_X_APP_VERSION_CODE'] ?? '';
    return ($raw !== '' && ctype_digit($raw) && strlen($raw) <= 9) ? (int) $raw : null;
}

/**
 * Évalue un client face à la politique.
 *
 * Règles :
 *   - bloqué (`updateRequired`) ⇔ forceUpdate ET versionCode < minVersionCode ;
 *   - client sans version : bloqué seulement si `blockUnversionedClients` ET forceUpdate ;
 *   - `updateAvailable` ⇔ une version publiée plus récente existe (simple proposition, jamais un
 *     blocage) ;
 *   - une version plus récente publiée n'obsolète JAMAIS les anciennes : seule la politique décide.
 *
 * @param array<string, mixed> $policy Résultat de {@see loadVersionPolicy()}
 * @param array<string, mixed>|null $latest Résultat de {@see loadLatestPublishedVersion()}
 * @return array{updateRequired: bool, updateAvailable: bool}
 */
function evaluateClientVersion(array $policy, ?array $latest, ?int $clientVersionCode): array
{
    if ($clientVersionCode === null) {
        return [
            'updateRequired' => $policy['forceUpdate'] && $policy['blockUnversionedClients'],
            'updateAvailable' => false,
        ];
    }
    return [
        'updateRequired' => $policy['forceUpdate'] && $clientVersionCode < $policy['minVersionCode'],
        'updateAvailable' => $latest !== null && $clientVersionCode < $latest['versionCode'],
    ];
}

/**
 * Refuse la requête si cette version de l'app est obsolète (HTTP 426 `upgrade_required`) ou si le
 * serveur est en maintenance (HTTP 503 `maintenance`). Appelée par les endpoints protégés
 * (voir `requireAuthenticatedUser`) et par les routes de connexion/inscription.
 *
 * Même règle que l'endpoint public (`evaluateClientVersion`), donc une version que l'app croit
 * autorisée l'est aussi pour le serveur. Les clients qui n'envoient pas `X-App-Version-Code`
 * (anciennes installations, console Web) ne sont refusés que si `block_unversioned_clients` est actif.
 *
 * EXEMPTIONS VOLONTAIRES (ne jamais appeler cette fonction depuis) : l'endpoint public de politique,
 * `auth/logout.php` et `auth/delete_account.php` (un utilisateur bloqué doit pouvoir se déconnecter
 * et supprimer son compte), et tout `api/admin/*` (un administrateur doit pouvoir corriger la
 * politique même après une erreur).
 *
 * Échec ouvert : si la politique est illisible (migration absente, base indisponible), la requête
 * continue — une panne de ce contrôle ne doit pas couper toute l'API.
 */
function enforceClientVersionPolicy(PDO $pdo): void
{
    try {
        $policy = loadVersionPolicy($pdo);
    } catch (Throwable $e) {
        error_log('Arzikina API — contrôle de version ignoré : ' . $e->getMessage());
        return;
    }

    $status = evaluateClientVersion($policy, null, readClientVersionCode());
    if ($status['updateRequired']) {
        header('Cache-Control: no-store');
        sendJson([
            'error' => 'upgrade_required',
            'message' => 'Cette version d\'Arzikina n\'est plus prise en charge. Mettez l\'application à jour.',
            'minVersionCode' => $policy['minVersionCode'],
            'updateUrl' => $policy['updateUrl'],
        ], 426);
    }
    if ($policy['maintenanceEnabled']) {
        header('Cache-Control: no-store');
        sendJson([
            'error' => 'maintenance',
            'message' => $policy['maintenanceMessage'] ?? 'Arzikina est momentanément indisponible. Réessayez dans quelques instants.',
        ], 503);
    }
}
