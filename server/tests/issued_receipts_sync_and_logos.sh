#!/usr/bin/env bash
# Recette de la synchronisation « Gestion de reçus » (migration 011) : profil émetteur, reçus,
# isolation entre utilisateurs, logos (upload/téléchargement/refus), quota, suppression de compte.
# Crée deux comptes jetables et supprime le premier à la fin. À lancer sur une base de TEST :
#   BASE_URL=http://127.0.0.1:8098/api bash server/tests/issued_receipts_sync_and_logos.sh
# Optionnel : LOGOS_DIR=<dossier issuer_logos du serveur local> pour vérifier aussi le nettoyage disque.
B=${BASE_URL:-http://127.0.0.1:8098/api}
T=$(mktemp -d)
ok=0; ko=0
chk(){ if [ "$2" = "$3" ]; then ok=$((ok+1)); echo "OK   $1"; else ko=$((ko+1)); echo "FAIL $1 (attendu '$3', obtenu '$2')"; fi; }
reg(){ curl -s -X POST $B/auth/register.php -H 'Content-Type: application/json' -d "{\"fullName\":\"$1\",\"username\":\"$2\",\"email\":\"$2@t.io\",\"password\":\"Passw0rd!x\"}"; }
S=$(date +%s)
A=$(reg Alice al$S); Bo=$(reg Bob bo$S)
TA=$(echo "$A" | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])'); UA=$(echo "$A" | python3 -c 'import sys,json;print(json.load(sys.stdin)["userId"])')
TB=$(echo "$Bo" | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])'); UB=$(echo "$Bo" | python3 -c 'import sys,json;print(json.load(sys.stdin)["userId"])')
push(){ curl -s -X POST $B/sync/push.php -H "Authorization: Bearer $1" -H 'Content-Type: application/json' -d "$2"; }
pull(){ curl -s "$B/sync/pull.php?entity_type=$2&updated_after=0" -H "Authorization: Bearer $1"; }
J(){ python3 -c "import sys,json;d=json.load(sys.stdin);print($1)"; }
PID=11111111-1111-4111-8111-111111111111
# --- profil
R=$(push $TA '{"entityType":"issuer_profiles","operations":[{"operation":"CREATE","entity":{"id":"'$PID'","baseVersion":null,"businessName":"Boutique Aïssa","address":"Niamey","phone":"+227 90","email":null,"taxId":"NIF-1","logoFileName":"aaaa-1.png","defaultIssuedByName":"Aïssa","defaultIssuedByTitle":null,"createdAt":1,"updatedAt":1}}]}')
chk "profil créé" "$(echo $R | J 'd["results"][0]["status"]')" accepted
R=$(push $TA '{"entityType":"issuer_profiles","operations":[{"operation":"CREATE","entity":{"id":"'$PID'","baseVersion":null,"businessName":"X","address":"X","phone":"X","createdAt":1,"updatedAt":1}}]}')
chk "CREATE rejoué = idempotent (état serveur inchangé)" "$(echo $R | J 'd["results"][0]["serverEntity"]["businessName"]')" "Boutique Aïssa"
R=$(push $TA '{"entityType":"issuer_profiles","operations":[{"operation":"UPDATE","entity":{"id":"'$PID'","baseVersion":1,"businessName":"Boutique B","address":"Niamey","phone":"+227 90","taxId":null,"createdAt":1,"updatedAt":2}}]}')
chk "UPDATE profil" "$(echo $R | J 'd["results"][0]["serverEntity"]["businessName"]')" "Boutique B"
chk "champ nullable absent conservé (email/logo)" "$(echo $R | J 'd["results"][0]["serverEntity"]["logoFileName"]')" "aaaa-1.png"
chk "null explicite efface (taxId)" "$(echo $R | J 'd["results"][0]["serverEntity"]["taxId"]')" "None"
# deuxième profil même utilisateur (id différent) => refusé proprement (unique)
R=$(push $TA '{"entityType":"issuer_profiles","operations":[{"operation":"CREATE","entity":{"id":"22222222-2222-4222-8222-222222222222","baseVersion":null,"businessName":"Autre","address":"A","phone":"P","createdAt":1,"updatedAt":1}}]}')
chk "2e profil du même utilisateur refusé (erreur propre)" "$(echo $R | J 'd["results"][0]["status"]')" error
# --- reçus
RID=aaaaaaaa-0000-4000-8000-000000000001
PR='{\"lines\":[{\"label\":\"Sac\",\"quantityMilli\":2000,\"unitPriceMinor\":500000}],\"taxBasisPoints\":1800}'
R=$(push $TA '{"entityType":"issued_receipts","operations":[{"operation":"CREATE","entity":{"id":"'$RID'","baseVersion":null,"number":"Recu-2026-001","issuedAt":1790000000000,"customerName":"Moussa","customerPhone":null,"description":"Loyer","amountMinor":1180000,"currencyCode":"XOF","paymentMethod":"CASH","transactionReference":null,"notes":null,"issuedByName":null,"issuedByTitle":null,"pricingJson":"'"$PR"'","issuerBusinessName":"Boutique B","issuerAddress":"Niamey","issuerPhone":"+227 90","issuerEmail":null,"issuerTaxId":null,"issuerLogoFileName":"aaaa-1.png","createdAt":5,"updatedAt":5}}]}')
chk "reçu créé" "$(echo $R | J 'd["results"][0]["status"]')" accepted
chk "montant (entier 64 bits)" "$(echo $R | J 'd["results"][0]["serverEntity"]["amountMinor"]')" 1180000
chk "pricingJson conservé tel quel" "$(echo $R | J 'json.loads(d["results"][0]["serverEntity"]["pricingJson"])["taxBasisPoints"]')" 1800
# doublon de numéro depuis un autre appareil (autre id) => accepté
R=$(push $TA '{"entityType":"issued_receipts","operations":[{"operation":"CREATE","entity":{"id":"aaaaaaaa-0000-4000-8000-000000000002","baseVersion":null,"number":"Recu-2026-001","issuedAt":1790000000001,"customerName":"Awa","description":"Autre","amountMinor":100,"currencyCode":"XOF","paymentMethod":"CASH","issuerBusinessName":"B","issuerAddress":"N","issuerPhone":"1","createdAt":6,"updatedAt":6}}]}')
chk "même numéro, autre appareil : accepté (le client renumérote)" "$(echo $R | J 'd["results"][0]["status"]')" accepted
R=$(push $TA '{"entityType":"issued_receipts","operations":[{"operation":"UPDATE","entity":{"id":"'$RID'","baseVersion":1,"number":"Recu-2026-001","issuedAt":1790000000000,"customerName":"Moussa K.","description":"Loyer","amountMinor":1180000,"currencyCode":"XOF","paymentMethod":"MOBILE_MONEY","pricingJson":null,"issuerBusinessName":"Boutique B","issuerAddress":"Niamey","issuerPhone":"+227 90","createdAt":5,"updatedAt":7}}]}')
chk "modification reçu (version 2)" "$(echo $R | J 'd["results"][0]["serverEntity"]["version"]')" 2
chk "pricingJson null efface" "$(echo $R | J 'd["results"][0]["serverEntity"]["pricingJson"]')" None
R=$(push $TA '{"entityType":"issued_receipts","operations":[{"operation":"UPDATE","entity":{"id":"'$RID'","baseVersion":1,"customerName":"Ancien client","number":"Recu-2026-001","issuedAt":1,"description":"d","amountMinor":1,"currencyCode":"XOF","paymentMethod":"CASH","issuerBusinessName":"B","issuerAddress":"N","issuerPhone":"1","updatedAt":8}}]}')
chk "conflit de version journalisé (LWW)" "$(echo $R | J 'd["results"][0]["status"]')" conflict_resolved
# --- pull + isolation
chk "pull A voit 2 reçus" "$(pull $TA issued_receipts | J 'len(d["entities"])')" 2
chk "pull A voit son profil" "$(pull $TA issuer_profiles | J 'len(d["entities"])')" 1
chk "pull B (autre utilisateur) ne voit rien" "$(pull $TB issued_receipts | J 'len(d["entities"])')" 0
chk "pull B profil vide" "$(pull $TB issuer_profiles | J 'len(d["entities"])')" 0
R=$(push $TB '{"entityType":"issued_receipts","operations":[{"operation":"UPDATE","entity":{"id":"'$RID'","baseVersion":2,"number":"HACK","issuedAt":1,"customerName":"x","description":"d","amountMinor":1,"currencyCode":"XOF","paymentMethod":"CASH","issuerBusinessName":"B","issuerAddress":"N","issuerPhone":"1"}}]}')
chk "B ne peut pas modifier le reçu de A (not_found)" "$(echo $R | J 'd["results"][0]["errorCode"]')" not_found
R=$(push $TA '{"entityType":"issued_receipts","operations":[{"operation":"DELETE","entity":{"id":"aaaaaaaa-0000-4000-8000-000000000002","baseVersion":1}}]}')
chk "suppression douce" "$(echo $R | J 'd["results"][0]["serverEntity"]["deletedAt"] is not None')" True
# --- logos
T="$T" python3 - <<'PY'
import struct,zlib,os
T=os.environ['T']
def png(w,h):
    raw=b''.join(b'\x00'+b'\xff\x00\x00'*w for _ in range(h))
    def ch(t,d): c=struct.pack('>I',len(d))+t+d; return c+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
    return b'\x89PNG\r\n\x1a\n'+ch(b'IHDR',struct.pack('>IIBBBBB',w,h,8,2,0,0,0))+ch(b'IDAT',zlib.compress(raw))+ch(b'IEND',b'')
open(T+'/logo.png','wb').write(png(8,8))
open(T+'/fake.png','wb').write(b'<?php echo 1; ?>')
open(T+'/big.png','wb').write(b'\x89PNG\r\n\x1a\n'+b'0'*2_100_000)
PY
up(){ curl -s -o $T/up.out -w "%{http_code}" -X POST $B/issuer/upload_logo.php -H "Authorization: Bearer $1" -F "name=$2" -F "logo=@$3"; }
chk "upload logo valide" "$(up $TA aaaa-1.png $T/logo.png)" 200
chk "upload rejoué (idempotent)" "$(up $TA aaaa-1.png $T/logo.png; python3 -c "import json;print('',end='')")" 200
chk "alreadyExisted=true" "$(cat $T/up.out | J 'd["alreadyExisted"]')" True
chk "faux PNG (php) refusé" "$(up $TA bbbb-2.png $T/fake.png)" 400
chk "extension ≠ type réel refusée" "$(up $TA cccc-3.jpg $T/logo.png)" 400
chk "nom avec traversée refusé" "$(up $TA ../evil.png $T/logo.png)" 400
chk "trop gros refusé" "$(up $TA dddd-4.png $T/big.png)" 400
chk "sans token refusé" "$(curl -s -o /dev/null -w '%{http_code}' -X POST $B/issuer/upload_logo.php -F name=eeee-5.png -F logo=@$T/logo.png)" 401
g(){ curl -s -o $T/dl.out -w "%{http_code}" "$B/issuer/get_logo.php?name=$2" -H "Authorization: Bearer $1"; }
chk "A télécharge son logo" "$(g $TA aaaa-1.png)" 200
chk "contenu identique" "$(cmp -s $T/dl.out $T/logo.png && echo same)" same
chk "B ne voit pas le logo de A (404)" "$(g $TB aaaa-1.png)" 404
chk "nom invalide (traversée) refusé" "$(g $TA '../../config/secrets.php')" 400
chk "logo absent 404" "$(g $TA zzzz-9.png)" 404
# quota
for i in $(seq 1 49); do up $TA "q$i-x.png" $T/logo.png >/dev/null; done
chk "quota atteint (50 fichiers) => 409" "$(up $TA qover-x.png $T/logo.png)" 409
# --- suppression de compte
chk "suppression du compte A" "$(curl -s -o /dev/null -w '%{http_code}' -X POST $B/auth/delete_account.php -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d '{"password":"Passw0rd!x"}')" 200
[ -n "$LOGOS_DIR" ] && chk "dossier de logos supprimé" "$([ -d "$LOGOS_DIR/$UA" ] && echo present || echo absent)" absent
echo "---- OK=$ok KO=$ko"
rm -rf "$T"
[ "$ko" -eq 0 ]
