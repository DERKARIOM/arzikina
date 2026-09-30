# Arzikina iOS : développer sous Windows, installer sur iPhone

Chaîne complète, sans Mac :

```
Windows (Claude Code) → git push → GitHub Actions (macOS + Xcode)
      → IPA NON SIGNÉE → GitHub Release / artifact → Windows → Sideloadly → iPhone
```

Le Mac est remplacé par un runner macOS de GitHub Actions, qui compile l'application. La
**signature** n'est pas faite par la CI : c'est Sideloadly qui signe sur Windows avec votre
identifiant Apple, au moment de l'installation.

---

## 1. Contenu du dépôt côté iOS

| Chemin | Rôle |
|---|---|
| `ios/project.yml` | Description du projet Xcode (outil **XcodeGen**). C'est la seule source de vérité : fichiers, bundle id, versions, Info.plist. |
| `ios/Arzikina/` | Code SwiftUI (`App/`, `DesignSystem/`, `Components/`, `Features/`) et ressources (`Resources/Assets.xcassets`, `Resources/Localizable.xcstrings`). |
| `ios/scripts/verify_ipa.sh` | Contrôles de l'IPA avant publication |
| `.github/workflows/ios-build.yml` | Workflow de build |

Le fichier `Arzikina.xcodeproj` **n'est pas dans le dépôt**. Il est généré à la volée (`xcodegen generate`). On ne le modifie jamais à la main.

---

## 2. Cloner le projet sur Windows

Pré-requis : [Git pour Windows](https://git-scm.com/download/win) et une clé SSH ajoutée à votre compte GitHub.

```powershell
cd C:\Users\<vous>\projets
git clone git@github.com:DERKARIOM/arzikina.git
cd arzikina
```

## 3. Développer avec Claude Code

- Ouvrez Claude Code sur le dossier du dépôt, puis demandez l'étape suivante (une fonctionnalité à la fois).
- Pour ajouter un écran, créez un fichier `.swift` sous `ios/Arzikina/Features/<Fonctionnalité>/`. XcodeGen le reprend automatiquement : **aucune édition de projet n'est nécessaire**.
- Pour ajouter un texte, ajoutez une clé dans `ios/Arzikina/Resources/Localizable.xcstrings`, **avec la valeur `fr` et la valeur `en`**. Dans le code, utilisez `Text("ma.cle")`.
- Il n'y a ni simulateur iOS ni aperçu SwiftUI sous Windows. Le vrai test, c'est le build CI puis l'iPhone.

## 4. Commit et push

```powershell
git checkout -b feat/ios-ma-fonctionnalite
git add ios docs .github
git commit -m "feat(ios): description courte"
git push -u origin feat/ios-ma-fonctionnalite
```

Ouvrir une Pull Request qui touche `ios/**` déclenche automatiquement une compilation et les vérifications. Aucune IPA n'est publiée à partir d'une PR.

## 5. Lancer un build manuellement

1. GitHub → dépôt → onglet **Actions**
2. Choisir le workflow **« iOS Build »** dans la liste de gauche
3. Cliquer sur **Run workflow**, choisir la branche, puis **Run workflow**
4. Attendre la fin du build (environ 5 à 10 minutes, tout en vert)
5. En bas de la page du run, dans **Artifacts**, télécharger `Arzikina-ios-<version>-<build>` (un `.zip` qui contient `Arzikina.ipa`)

En cas d'échec, ouvrez l'étape en rouge. Le message `Error` explique la cause : erreur de compilation, version incohérente, IPA invalide… Le journal complet de compilation est joint en artifact (`xcodebuild-log`).

## 6. Versions et tags de release

Les versions sont définies **uniquement** dans `ios/project.yml` :

```yaml
MARKETING_VERSION: "0.1.0"        # version visible
CURRENT_PROJECT_VERSION: "1"      # numéro de build
```

| Situation | Action | Résultat |
|---|---|---|
| Nouveau build de la même version | `CURRENT_PROJECT_VERSION: "2"` | 0.1.0 (2) |
| Nouvelle version | `MARKETING_VERSION: "0.1.1"` et `CURRENT_PROJECT_VERSION: "1"` | 0.1.1 (1) |

Pour publier une release :

```powershell
# 1. Commit de la version dans ios/project.yml, sur main
git commit -am "chore(ios): release 0.1.0 (1)"
git push

# 2. Tag qui correspond EXACTEMENT à MARKETING_VERSION
git tag ios-v0.1.0
git push origin ios-v0.1.0
```

Le workflow compile, vérifie, puis crée la Release **« Arzikina iOS v0.1.0 »** avec l'asset `Arzikina.ipa`.

Si le tag ne correspond pas à `MARKETING_VERSION`, le build **échoue volontairement**. Pour corriger :
- supprimer le tag : `git tag -d ios-v0.1.0` puis `git push origin :refs/tags/ios-v0.1.0` ;
- corriger `project.yml` ;
- recréer le tag.

Seuls les tags `ios-v*` publient une release : les autres tags du dépôt ne déclenchent rien.

## 7. Récupérer l'IPA

- **Release** : GitHub → **Releases** → `Arzikina iOS vX.Y.Z` → **Assets** → `Arzikina.ipa`
- **Build manuel** : artifact du run (voir §5). Dézippez-le pour obtenir `Arzikina.ipa`.

## 8. Installer avec Sideloadly

### Installation (une seule fois)

1. **iTunes** et **iCloud**, versions **téléchargées depuis le site d'Apple** et pas celles du Microsoft Store. Sideloadly en a besoin pour dialoguer avec l'iPhone.
2. **Sideloadly** : <https://sideloadly.io>

### À chaque installation

1. Branchez l'iPhone en USB, déverrouillez-le et répondez **« Se fier à cet ordinateur »**.
2. Ouvrez Sideloadly et glissez `Arzikina.ipa` dans la fenêtre.
3. Choisissez l'iPhone et saisissez votre **identifiant Apple**, puis cliquez sur **Start**. Sideloadly signe l'app avec ce compte (mot de passe demandé, et code 2FA la première fois).
4. Sur l'iPhone :
   - **Réglages › Général › VPN et gestion de l'appareil** → votre identifiant → **Faire confiance** ;
   - **Réglages › Confidentialité et sécurité › Mode développeur** → activer (iOS 16 et suivants). L'iPhone redémarre, puis confirmez.
5. Lancez Arzikina.

Si Sideloadly refuse le bundle id `com.naniger.arzikina` (identifiant déjà réservé chez Apple par un autre compte), cochez dans ses options avancées le changement de bundle id. Cela ne modifie **que la copie installée** : le projet garde `com.naniger.arzikina`.

## 9. Ce qui demande un compte Apple

| | Identifiant Apple **gratuit** | **Apple Developer Program** (99 $/an) |
|---|---|---|
| Installer via Sideloadly | ✅ | ✅ |
| Durée de validité de l'app | **7 jours**, puis réinstaller (les données de l'app sont conservées si l'app n'est pas supprimée) | 1 an |
| Nombre d'apps sideloadées | 3 au maximum, et un nombre limité d'identifiants d'app par semaine | Illimité en pratique |
| Extensions (partage de reçus, widgets) | Chaque extension consomme un identifiant d'app | ✅ |
| Notifications push à distance, TestFlight, App Store | ❌ | ✅ |

## 10. Limites de cette méthode

- Pas de simulateur ni d'aperçu SwiftUI sous Windows : chaque essai visuel demande un build CI (5 à 10 minutes) puis une installation.
- Réinstallation tous les 7 jours avec un compte gratuit.
- La CI **ne garantit pas** que l'app s'installe : elle garantit une IPA valide (architecture arm64, bundle id, version, ressources). L'installation n'est prouvée qu'après un essai Sideloadly réussi.
- Minutes GitHub Actions : gratuites tant que le dépôt est **public**. Si le dépôt devient privé, les minutes macOS sont décomptées environ 10 fois plus vite que les minutes Linux.

## 11. Passer plus tard à TestFlight / App Store

1. S'inscrire à l'**Apple Developer Program**.
2. Enregistrer l'App ID `com.naniger.arzikina` et créer l'app dans **App Store Connect**.
3. Créer une clé **App Store Connect API** et un certificat de distribution. Les stocker **uniquement dans GitHub Secrets**, jamais dans le dépôt.
4. Ajouter au workflow un job séparé de signature et d'upload (`xcodebuild archive` + `-exportArchive`, ou fastlane), déclenché par exemple par des tags `ios-testflight-v*`.
5. Le build non signé actuel reste utile pour les tests rapides avec Sideloadly.

Le bundle id et les numéros de version (`project.yml`) sont déjà compatibles : rien à renommer.
