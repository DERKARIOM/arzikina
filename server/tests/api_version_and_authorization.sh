#!/usr/bin/env bash
# Tests d'intégration DIRECTS de l'API (sans passer par la console) : contrôle de version,
# maintenance et autorisations d'administration.
#
# À lancer contre un serveur de TEST (jamais la production : le script modifie `app_version_policy`
# et la remet à zéro à la fin) :
#   BASE=http://127.0.0.1:8099/api  MYSQL="mysql -uroot t" \
#   TOKEN_USER=... TOKEN_ADMIN=... TOKEN_SUPER=... bash server/tests/api_version_and_authorization.sh
# Chaque TOKEN_* est un jeton de session valide (login) d'un compte sans rôle / Admin / Super Admin.
set -u
: "${BASE:?}" "${MYSQL:?}" "${TOKEN_USER:?}" "${TOKEN_ADMIN:?}" "${TOKEN_SUPER:?}"
fail=0
call() { # méthode jeton chemin version [corps]
  local h=(); [ -n "$2" ] && h+=(-H "Authorization: Bearer $2"); [ -n "$4" ] && h+=(-H "X-App-Version-Code: $4")
  curl -s -o /dev/null -w "%{http_code}" -X "$1" "${h[@]}" -H 'Content-Type: application/json' -d "${5:-{\}}" "$BASE/$3"
}
check() { if [ "$2" = "$3" ]; then echo "OK    $1 ($2)"; else echo "ECHEC $1 : obtenu $2, attendu $3"; fail=1; fi; }
not426() { if [ "$2" = 426 ] || [ "$2" = 503 ]; then echo "ECHEC $1 : bloqué ($2)"; fail=1; else echo "OK    $1 ($2)"; fi; }
policy() { $MYSQL -e "UPDATE app_version_policy SET min_version_code=$1, force_update=$2, block_unversioned_clients=$3, maintenance_enabled=$4 WHERE id=1"; }
trap 'policy 0 0 0 0' EXIT

echo "# Autorisations (droits vérifiés côté serveur)"
policy 0 0 0 0
for ep in admin/me.php; do
  check "$ep sans jeton" "$(call GET '' $ep '')" 401
  check "$ep jeton invalide" "$(call GET bad $ep '')" 401
done
for ep in admin/users/list.php admin/users/stats.php; do
  check "$ep utilisateur simple" "$(call GET "$TOKEN_USER" $ep '')" 403
  check "$ep Admin" "$(call GET "$TOKEN_ADMIN" $ep '')" 200
  check "$ep Super Admin" "$(call GET "$TOKEN_SUPER" $ep '')" 200
done
for ep in admin/versions/list.php admin/policy/get.php admin/roles/list.php admin/audit/list.php; do
  check "$ep utilisateur simple" "$(call GET "$TOKEN_USER" $ep '')" 403
  check "$ep Admin" "$(call GET "$TOKEN_ADMIN" $ep '')" 403
  check "$ep Super Admin" "$(call GET "$TOKEN_SUPER" $ep '')" 200
done
for ep in admin/versions/save.php admin/versions/set_status.php admin/policy/update.php admin/roles/set.php; do
  check "$ep (écriture) Admin" "$(call POST "$TOKEN_ADMIN" $ep '' '{}')" 403
  check "$ep (écriture) utilisateur simple" "$(call POST "$TOKEN_USER" $ep '' '{}')" 403
done

echo "# Contrôle de version (min 8, obligation active)"
policy 8 1 0 0
check "sync/pull.php v7 refusée" "$(call GET "$TOKEN_USER" sync/pull.php 7)" 426
check "profile/get.php v7 refusée" "$(call GET "$TOKEN_USER" profile/get.php 7)" 426
check "sync/push.php v7 refusée" "$(call POST "$TOKEN_USER" sync/push.php 7)" 426
check "devices/register.php v7 refusée" "$(call POST "$TOKEN_USER" devices/register.php 7)" 426
check "login v7 refusée" "$(call POST '' auth/login.php 7 '{"identifier":"a","password":"b"}')" 426
not426 "profile/get v8 autorisée" "$(call GET "$TOKEN_USER" profile/get.php 8)"
not426 "profile/get v12 autorisée" "$(call GET "$TOKEN_USER" profile/get.php 12)"
not426 "profile/get sans en-tête autorisée" "$(call GET "$TOKEN_USER" profile/get.php '')"
check "jeton invalide reste 401" "$(call GET bad profile/get.php 7)" 401
echo "# Exemptions (v7 bloquée)"
not426 "logout" "$(call POST "$TOKEN_USER" auth/logout.php 7)"
not426 "delete_account" "$(call POST "$TOKEN_USER" auth/delete_account.php 7 '{"password":"x"}')"
check "version_policy public" "$(call GET '' app/version_policy.php 7)" 200
check "admin/me v7" "$(call GET "$TOKEN_SUPER" admin/me.php 7)" 200
check "admin/policy/get v7" "$(call GET "$TOKEN_SUPER" admin/policy/get.php 7)" 200

echo "# Anciennes installations sans en-tête"
policy 8 1 1 0
check "profile/get sans en-tête bloquée" "$(call GET "$TOKEN_USER" profile/get.php '')" 426
check "console admin jamais bloquée" "$(call GET "$TOKEN_SUPER" admin/me.php '')" 200
policy 99 0 1 0
not426 "obligation désactivée : v7 autorisée" "$(call GET "$TOKEN_USER" profile/get.php 7)"

echo "# Maintenance"
policy 0 0 0 1
check "profile/get en maintenance" "$(call GET "$TOKEN_USER" profile/get.php 8)" 503
not426 "logout en maintenance" "$(call POST "$TOKEN_USER" auth/logout.php 8)"
check "admin en maintenance" "$(call GET "$TOKEN_SUPER" admin/policy/get.php 8)" 200
check "version_policy en maintenance" "$(call GET '' app/version_policy.php 8)" 200

[ "$fail" = 0 ] && echo "TOUS LES TESTS PASSENT" || { echo "DES TESTS ONT ÉCHOUÉ"; exit 1; }
