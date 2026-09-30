# Jeux de tests partagés Android ↔ iOS

Ces fichiers JSON décrivent le **comportement attendu** des règles métier d'Arzikina. Ils sont
exécutés **tels quels** par les deux applications :

| Plateforme | Test | Code vérifié |
|---|---|---|
| Android | `app/src/test/java/com/naniger/arzikina/SharedFixturesTest.kt` | `util/Money.kt`, `util/SavingsGoalProgress.kt`, `domain/model/LoanStatus.kt`, `presentation/accounts/AccountBalances.kt`, `util/BudgetProgress.kt`, `util/BudgetPace.kt`, `util/BudgetPeriodStatus.kt`, `domain/model/RecurringFrequency.kt`, `util/FinancialPlanProgress.kt`, `util/AuthValidator.kt` |
| iOS | `ios/Packages/ArzikinaKit/Tests/ArzikinaDomainTests/SharedFixtureTests.swift` | `ios/Packages/ArzikinaKit/Sources/ArzikinaDomain/Rules/*` |

Si une règle diverge entre les deux plateformes, au moins un des deux tests échoue. La CI les
lance tous les deux (`.github/workflows/shared-rules-tests.yml`).

| Fichier | Règle |
|---|---|
| `money.json` | Saisie → unité mineure (×100 pour **toutes** les devises), formats de saisie et d'affichage |
| `account-balances.json` | Solde courant (revenus, dépenses, transferts entrants et sortants) |
| `savings-goal.json` | Progression d'un objectif d'épargne |
| `loan-status.json` | Statut d'un prêt (remboursé, à venir, en cours, en retard) |
| `budget.json` | Dépensé, progression, période (fixe ou récurrente) et rythme d'un budget |
| `recurrence.json` | Prochaine échéance d'une automatisation et échéances dues |
| `financial-plan.json` | Total prévu, reste et progression d'une planification |
| `auth-validation.json` | Format de l'e-mail, du nom d'utilisateur, du mot de passe et de la réponse de sécurité |

## Conventions

- **Montants** : unité mineure (entier). `null` = aucun résultat (ex. saisie invalide).
- **Instants** : date locale `AAAA-MM-JJTHH:MM`, ou `AAAA-MM-JJ` pour un début de journée, interprétée dans le `timeZone` du fichier (ou du cas, s'il en précise un). Les résultats ne dépendent donc jamais du fuseau de la machine de test.
- **Énumérations** : noms identiques à l'API et à Android (`"EXPENSE"`, `"OVERDUE"`…).
- **Formatage** : l'affichage utilise l'espace fine insécable U+202F comme séparateur de milliers. La saisie utilise une espace normale.

## Modifier une règle métier

1. Mettre d'abord à jour (ou compléter) le fichier JSON concerné.
2. Adapter le code Android **et** le code Swift.
3. Les deux tests doivent passer : `.\gradlew.bat :app:testDebugUnitTest --tests "com.naniger.arzikina.SharedFixturesTest"` pour Android, et la CI pour Swift.
