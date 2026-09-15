<?php

declare(strict_types=1);

/**
 * Connexion PDO partagée par tous les points d'entrée de l'API Arzikina (api/auth/*, api/sync/*,
 * api/receipts/*...). Voir docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md, sections 1 et 10.
 *
 * Réutilise EXACTEMENT le même principe que l'actuel `connectBDD.php` déjà en place sur le serveur
 * (`/var/www/html/arzikina/connectBDD.php`) : les identifiants réels (host, nom de base,
 * utilisateur, mot de passe) restent dans `/home/Admin/config_arzikina.php`, un fichier HORS de la
 * racine web et jamais versionné dans Git — ce fichier ne duplique aucun secret, il se contente de
 * réutiliser les constantes déjà définies là-bas (Host, NomDB, NomUtilisateur, MotDePasse).
 *
 * Différence avec `connectBDD.php` : la connexion est encapsulée dans une fonction
 * (`getDatabaseConnection()`) avec un cache statique, pour que chaque endpoint API récupère LA MÊME
 * instance PDO sans dupliquer le bloc try/catch, et sans jamais renvoyer un message d'erreur brut
 * (potentiellement sensible) au client — voir cahier des charges, section sécurité.
 *
 * Note de déploiement (portable, voir docs/DEPLOIEMENT-HOSTINGER.md) : `config_arzikina.php` et
 * `config_arzikina_secrets.php` vivent à la racine du compte d'hébergement (`$HOME`, HORS de
 * `public_html`, jamais accessibles par une URL, jamais dans Git) — PAS à un chemin absolu codé en
 * dur comme `/home/Admin/...`, qui ne correspond qu'au compte du tout premier serveur sur lequel ce
 * code a tourné et casserait silencieusement sur tout autre compte (nom d'utilisateur différent).
 * `getenv('HOME')` est déjà positionné correctement par PHP-FPM/Apache pour l'utilisateur du compte
 * sur un hébergement mutualisé Hostinger — repli sur un chemin relatif si absent (cas rare), plutôt
 * que de planter sans message exploitable.
 */

$accountHomeDir = getenv('HOME') ?: dirname(__DIR__, 4);

require_once $accountHomeDir . '/config_arzikina.php';

// Clé de signature des tokens de session et autres secrets PROPRES à l'API (distincts des
// identifiants de connexion MySQL ci-dessus) — voir secrets.example.php pour le modèle, à copier au
// même endroit que config_arzikina.php ci-dessus (jamais dans Git).
require_once $accountHomeDir . '/config_arzikina_secrets.php';

/**
 * Retourne la connexion PDO partagée, en la créant si besoin (singleton simple par requête HTTP —
 * chaque appel PHP est de toute façon un nouveau process, pas de partage entre requêtes).
 *
 * En cas d'échec de connexion : réponse JSON générique (503), le détail technique part uniquement
 * dans le journal serveur (`error_log`) — ne JAMAIS exposer un message d'exception PDO au client
 * (peut révéler host/nom de base/utilisateur).
 */
function getDatabaseConnection(): PDO
{
    static $pdo = null;

    if ($pdo !== null) {
        return $pdo;
    }

    $options = [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        // Vraies requêtes préparées côté serveur MySQL (pas d'émulation côté client) — protection
        // contre l'injection SQL, voir cahier des charges section sécurité. Déjà activé dans
        // connectBDD.php, conservé ici à l'identique.
        PDO::ATTR_EMULATE_PREPARES => false,
        PDO::MYSQL_ATTR_INIT_COMMAND => "SET NAMES 'utf8mb4'",
    ];

    try {
        $pdo = new PDO('mysql:host=' . Host . ';dbname=' . NomDB, NomUtilisateur, MotDePasse, $options);
    } catch (PDOException $e) {
        error_log('Arzikina API — echec de connexion base de donnees : ' . $e->getMessage());
        http_response_code(503);
        header('Content-Type: application/json');
        echo json_encode(['error' => 'service_unavailable']);
        exit;
    }

    return $pdo;
}
