// swift-tools-version:5.9
//
// ArzikinaKit — code partagé de l'app iOS, SANS dépendance à SwiftUI/UIKit.
//
// - ArzikinaDomain : modèles métier et règles de calcul (montants, soldes, objectifs d'épargne,
//   statut des prêts, budgets, récurrences, planifications, validation de l'authentification).
//   Portage fidèle du domaine Android, vérifié par les jeux de tests PARTAGÉS de
//   `shared/test-fixtures/` (voir Tests/).
// - ArzikinaData : accès à l'API Arzikina (client HTTP, DTO), session (Keychain), base locale
//   SQLite (GRDB, une base par utilisateur, migrations versionnées) et implémentations des dépôts
//   du domaine.
//
// Seule dépendance externe : GRDB (https://github.com/groue/GRDB.swift, licence MIT), l'équivalent
// de Room côté Android. GRDB 7 exige Swift 6.1 (Xcode 16.3+).
//
// Compilable et testable sur macOS ET Linux (`swift test`) : la CI exécute les tests sur un runner
// Linux, bien moins coûteux qu'un runner macOS (le stockage Keychain, propre aux plateformes Apple,
// y est remplacé par un stockage en mémoire dans les tests).
import PackageDescription

let package = Package(
    name: "ArzikinaKit",
    platforms: [
        .iOS(.v17),
        .macOS(.v14)
    ],
    products: [
        .library(name: "ArzikinaDomain", targets: ["ArzikinaDomain"]),
        .library(name: "ArzikinaData", targets: ["ArzikinaData"])
    ],
    dependencies: [
        // Correctifs (7.11.x) acceptés automatiquement ; une version mineure suivante se valide à la main.
        .package(url: "https://github.com/groue/GRDB.swift.git", .upToNextMinor(from: "7.11.1"))
    ],
    targets: [
        .target(name: "ArzikinaDomain"),
        .target(name: "ArzikinaData", dependencies: [
            "ArzikinaDomain",
            .product(name: "GRDB", package: "GRDB.swift")
        ]),
        .testTarget(name: "ArzikinaDomainTests", dependencies: ["ArzikinaDomain"]),
        .testTarget(name: "ArzikinaDataTests", dependencies: ["ArzikinaData", "ArzikinaDomain"])
    ]
)
