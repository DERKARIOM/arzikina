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
 * Note de déploiement : ce fichier va dans `/var/www/html/arzikina/api/config/database.php` sur le
 * serveur. `/home/Admin/config_arzikina.php` est déjà accessible en lecture par PHP-Apache à ce
 * chemin absolu (utilisé tel quel par `connectBDD.php`), donc aucun ajustement de chemin n'est
 * nécessaire ici.
 */

require_once '/home/Admin/config_arzikina.php';

// Clé de signature des tokens de session et autres secrets PROPRES à l'API (distincts des
// identifiants de connexion MySQL ci-dessus) — voir secrets.example.php pour le modèle à copier en
// `/home/Admin/config_arzikina_secrets.php` sur le serveur (jamais dans Git non plus).
require_once '/home/Admin/config_arzikina_secrets.php';

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
