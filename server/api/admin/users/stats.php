<?php

declare(strict_types=1);

require_once __DIR__ . '/../../config/database.php';
require_once __DIR__ . '/../../utils/admin_auth.php';
require_once __DIR__ . '/../../utils/admin_http.php';

/**
 * GET /api/admin/users/stats.php[?days=30] — permission `users.stats` (Admin, Super Admin).
 *
 * Statistiques calculées en base (aucune valeur fictive) sur les comptes NON supprimés :
 *   total, aujourd'hui, 7 derniers jours, 30 derniers jours, répartition par méthode, et la série
 *   journalière des inscriptions sur `days` jours (7 à 365, défaut 30).
 *
 * Calendrier : « aujourd'hui » = depuis minuit à Africa/Niamey ; « 7 derniers jours » = aujourd'hui
 * + les 6 jours précédents (même définition que la série, donc jamais d'écart ni de double
 * comptage). Regroupement journalier par décalage UTC fixe : exact pour Niamey (UTC+1, sans heure
 * d'été).
 *
 * Pas d'indicateur « comptes actifs/inactifs » : le projet n'en a pas de définition fiable.
 */

requireHttpMethod('GET');
$pdo = getDatabaseConnection();
requirePermission($pdo, 'users.stats');

$days = filter_var($_GET['days'] ?? 30, FILTER_VALIDATE_INT, ['options' => ['min_range' => 7, 'max_range' => 365]]);
if ($days === false) {
    sendError('invalid_days', 'days doit être un entier entre 7 et 365.', 400);
}

$now = nowMillis();
$todayStart = projectDayStartMillis($now);
$offset = projectUtcOffsetMillis($now);
$rangeStart = projectDayStartMillis($now, $days - 1);

try {
    $countSince = static function (int $since) use ($pdo): int {
        $stmt = $pdo->prepare('SELECT COUNT(*) FROM users WHERE deleted_at IS NULL AND created_at >= :since');
        $stmt->execute(['since' => $since]);
        return (int) $stmt->fetchColumn();
    };

    $total = (int) $pdo->query('SELECT COUNT(*) FROM users WHERE deleted_at IS NULL')->fetchColumn();
    $byMethod = $pdo->query(
        'SELECT (google_id IS NOT NULL) AS has_google, COUNT(*) AS n FROM users WHERE deleted_at IS NULL GROUP BY has_google'
    )->fetchAll();

    $series = $pdo->prepare(
        'SELECT FLOOR((created_at + :offset) / 86400000) AS day_index, COUNT(*) AS n
         FROM users WHERE deleted_at IS NULL AND created_at >= :start AND created_at <= :end
         GROUP BY day_index'
    );
    $series->execute(['offset' => $offset, 'start' => $rangeStart, 'end' => $now]);
    $perDay = [];
    foreach ($series->fetchAll() as $r) {
        $perDay[(int) $r['day_index']] = (int) $r['n'];
    }

    $before = $pdo->prepare('SELECT COUNT(*) FROM users WHERE deleted_at IS NULL AND created_at < :start');
    $before->execute(['start' => $rangeStart]);
    $totalBeforeRange = (int) $before->fetchColumn();

    $today = $countSince($todayStart);
    $last7 = $countSince(projectDayStartMillis($now, 6));
    $last30 = $countSince(projectDayStartMillis($now, 29));
} catch (Throwable $e) {
    error_log('Arzikina API — admin/users/stats : ' . $e->getMessage());
    sendError('server_error', 'Statistiques indisponibles, réessaie plus tard.', 500);
}

// Série complète (jours sans inscription = 0), du plus ancien au plus récent.
$timeline = [];
for ($i = $days - 1; $i >= 0; $i--) {
    $dayStart = projectDayStartMillis($now, $i);
    $timeline[] = [
        'date' => (new DateTimeImmutable('@' . intdiv($dayStart, 1000)))
            ->setTimezone(new DateTimeZone(PROJECT_TIMEZONE))->format('Y-m-d'),
        'count' => $perDay[(int) floor(($dayStart + $offset) / 86400000)] ?? 0,
    ];
}

$google = 0;
$email = 0;
foreach ($byMethod as $r) {
    if ((int) $r['has_google'] === 1) {
        $google = (int) $r['n'];
    } else {
        $email = (int) $r['n'];
    }
}

sendJson([
    'timezone' => PROJECT_TIMEZONE,
    'total' => $total,
    'today' => $today,
    'last7Days' => $last7,
    'last30Days' => $last30,
    'byAuthMethod' => ['google' => $google, 'email' => $email],
    'totalBeforeRange' => $totalBeforeRange,
    'timeline' => $timeline,
]);
