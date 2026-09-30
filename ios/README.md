# Arzikina iOS

Application iOS native (Swift, SwiftUI, iOS 17 minimum) d'Arzikina.

- **Bundle identifier** : `com.naniger.arzikina` (fixe, ne jamais le changer)
- **Projet Xcode** : généré à partir de `project.yml` par [XcodeGen](https://github.com/yonaskolb/XcodeGen). Le `.xcodeproj` n'est pas versionné.
- **Langues** : français (référence) et anglais, dans `Arzikina/Resources/Localizable.xcstrings`.
- **Build, IPA et installation** : voir [`docs/IOS_BUILD.md`](../docs/IOS_BUILD.md).

## Sur un Mac (facultatif)

```bash
brew install xcodegen
cd ios && xcodegen generate && open Arzikina.xcodeproj
```

## Organisation

```
Arzikina/
├── App/            Point d'entrée, onglets (TabView), infos de version
├── DesignSystem/   Couleurs de marque, thème, styles de cartes
├── Components/     Vues réutilisables
├── Features/       Un dossier par fonctionnalité (Dashboard, Accounts, Reports, Settings…)
└── Resources/      Assets (icône, couleurs), traductions
scripts/            Scripts CI (vérification de l'IPA)
```
