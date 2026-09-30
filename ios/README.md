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
├── App/            Point d'entrée, composition des dépendances, session, onglets (TabView)
├── DesignSystem/   Couleurs de marque, thème, styles de cartes
├── Components/     Vues réutilisables
├── Features/       Un dossier par fonctionnalité (Dashboard, Accounts, Reports, Settings…)
└── Resources/      Assets (icône, couleurs), traductions
scripts/            Scripts CI (vérification de l'IPA)
Packages/
└── ArzikinaKit/    Package Swift local, sans SwiftUI
    ├── Sources/ArzikinaDomain/   Modèles et règles métier (Money, soldes, objectifs d'épargne,
    │                             prêts, budgets, récurrences, planifications, authentification)
    ├── Sources/ArzikinaData/     Client de l'API (URLSession), DTO, session dans le Keychain,
    │                             implémentations des dépôts (connexion, inscription…)
    └── Tests/                    Tests, dont les jeux partagés avec Android (shared/test-fixtures)
```

## Tester la logique métier

La logique métier ne dépend ni de SwiftUI ni d'Xcode. Ses tests tournent sur un runner Linux
(workflow « Shared Rules Tests »), ou sur n'importe quelle machine qui a Swift installé :

```bash
cd ios/Packages/ArzikinaKit && swift test
```

Les jeux de tests de `shared/test-fixtures/` sont communs à Android et iOS : voir leur
[README](../shared/test-fixtures/README.md).
