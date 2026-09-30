#!/usr/bin/env bash
# Vérifie qu'une IPA générée par la CI est exploitable par Sideloadly, AVANT de la publier.
# Chaque contrôle échoue avec un message explicite (lisible depuis l'onglet Actions sous Windows).
#
# Usage : verify_ipa.sh <fichier.ipa> <bundle_id_attendu> <version_attendue> <build_attendu>
# Nécessite macOS (PlistBuddy, lipo, codesign) — exécuté par .github/workflows/ios-build.yml.
set -euo pipefail

IPA="${1:?Chemin du fichier IPA manquant}"
EXPECTED_BUNDLE_ID="${2:?Bundle identifier attendu manquant}"
EXPECTED_VERSION="${3:?Version attendue manquante}"
EXPECTED_BUILD="${4:?Numéro de build attendu manquant}"

fail() { echo "::error title=Vérification IPA::$*"; exit 1; }
ok() { echo "✅ $*"; }

[[ -f "$IPA" ]] || fail "IPA introuvable : $IPA"
[[ -s "$IPA" ]] || fail "IPA vide (0 octet) : $IPA"
ok "IPA présente : $IPA ($(du -h "$IPA" | cut -f1))"

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT
unzip -q "$IPA" -d "$WORK_DIR" || fail "IPA illisible (archive zip invalide)"

APP_DIR="$(find "$WORK_DIR/Payload" -maxdepth 1 -type d -name '*.app' | head -n 1)"
[[ -n "$APP_DIR" ]] || fail "Structure invalide : aucun Payload/*.app dans l'IPA"
ok "Application trouvée : Payload/$(basename "$APP_DIR")"

PLIST="$APP_DIR/Info.plist"
[[ -f "$PLIST" ]] || fail "Info.plist absent de l'application"
plist_value() { /usr/libexec/PlistBuddy -c "Print :$1" "$PLIST" 2>/dev/null || true; }

BUNDLE_ID="$(plist_value CFBundleIdentifier)"
VERSION="$(plist_value CFBundleShortVersionString)"
BUILD="$(plist_value CFBundleVersion)"
EXECUTABLE="$(plist_value CFBundleExecutable)"
MIN_OS="$(plist_value MinimumOSVersion)"
PLATFORM="$(plist_value DTPlatformName)"

[[ "$BUNDLE_ID" == "$EXPECTED_BUNDLE_ID" ]] || fail "Bundle identifier incorrect : '$BUNDLE_ID' (attendu '$EXPECTED_BUNDLE_ID')"
ok "Bundle identifier : $BUNDLE_ID"
[[ "$VERSION" == "$EXPECTED_VERSION" ]] || fail "Version incorrecte : '$VERSION' (attendue '$EXPECTED_VERSION', voir MARKETING_VERSION dans ios/project.yml)"
ok "Version : $VERSION"
[[ "$BUILD" == "$EXPECTED_BUILD" ]] || fail "Build incorrect : '$BUILD' (attendu '$EXPECTED_BUILD', voir CURRENT_PROJECT_VERSION dans ios/project.yml)"
ok "Build : $BUILD"
[[ "$PLATFORM" == "iphoneos" ]] || fail "Plateforme incorrecte : '$PLATFORM' (attendu 'iphoneos' — un build Simulator ne s'installe pas sur iPhone)"
ok "Plateforme : $PLATFORM (iOS minimum $MIN_OS)"

BINARY="$APP_DIR/$EXECUTABLE"
[[ -n "$EXECUTABLE" && -f "$BINARY" ]] || fail "Exécutable '$EXECUTABLE' absent de l'application"
ARCHS="$(lipo -archs "$BINARY")"
[[ " $ARCHS " == *" arm64 "* ]] || fail "Architecture incorrecte : '$ARCHS' (arm64 requis pour un iPhone)"
ok "Architecture(s) : $ARCHS"

[[ -f "$APP_DIR/Assets.car" ]] || fail "Catalogue d'assets (icône, couleurs) absent : Assets.car"
ok "Assets compilés (icône, couleurs de marque)"

for LANG in fr en; do
  [[ -d "$APP_DIR/$LANG.lproj" ]] || fail "Traductions '$LANG' absentes ($LANG.lproj) — vérifier Localizable.xcstrings"
done
ok "Langues embarquées : fr, en"

# Information seulement : l'IPA est VOLONTAIREMENT non signée, Sideloadly la signe à l'installation.
if codesign -dv "$APP_DIR" >/dev/null 2>&1; then
  echo "ℹ️  L'application porte une signature (inattendu, sans conséquence : Sideloadly re-signe)."
else
  ok "Application non signée (attendu — signature faite par Sideloadly)"
fi

echo "🎉 IPA valide : $BUNDLE_ID $VERSION ($BUILD) — prête pour Sideloadly."
