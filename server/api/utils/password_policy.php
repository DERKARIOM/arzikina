<?php

declare(strict_types=1);

/**
 * Règles d'un nouveau mot de passe (endpoint `auth/set_password.php`).
 *
 * Le minimum (8) est le même que `MIN_PASSWORD_LENGTH` de `auth/register.php` : ce fichier ne touche
 * volontairement pas à `register.php` (déjà déployé) pour qu'un déploiement partiel ne puisse jamais
 * casser l'inscription. Le maximum protège le serveur contre des mots de passe démesurés à hacher
 * (bcrypt ignore de toute façon tout ce qui dépasse 72 octets).
 */

const PASSWORD_MIN_LENGTH = 8;
const PASSWORD_MAX_LENGTH = 128;

/**
 * @return string|null `null` si le mot de passe est acceptable, sinon le code d'erreur stable
 *                     (`password_too_short` / `password_too_long`).
 */
function passwordPolicyError(string $password): ?string
{
    if (strlen($password) < PASSWORD_MIN_LENGTH) {
        return 'password_too_short';
    }
    if (strlen($password) > PASSWORD_MAX_LENGTH) {
        return 'password_too_long';
    }
    return null;
}
