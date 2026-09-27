# Prêts / Emprunts : date + heure

## Avant ce changement
- `loans.start_date` et `loans.due_date` sont des `BIGINT` (millisecondes). Ce sont des instants
  complets, pas des `DATE` : la base pouvait déjà stocker une heure.
- **Android** enregistrait l'heure actuelle quand la date n'était pas touchée, et minuit local
  quand une date était choisie dans le sélecteur.
- **Web** enregistrait toujours minuit local.
- Aucune des deux interfaces ne permettait de saisir ou d'afficher une heure.
- Il n'existait pas d'écran pour modifier un prêt ou un emprunt, ni sur Android ni sur le Web.

## Maintenant
- **Heure du prêt** : un sélecteur d'heure (natif Android / `<input type="time">` Web) est ajouté à
  la création. Date et heure forment **un seul instant**, stocké dans `start_date`.
  - Pas de migration, pas de nouvelle colonne, pas de changement d'API : même champ `startDate`
    dans `push.php` et `pull.php`.
- **Modification** : action « Modifier la date et l'heure » dans le détail du prêt/emprunt.
  - Elle change uniquement `startDate`, et réaligne la transaction de décaissement sur la même
    date/heure.
  - Montant, compte, échéance et remboursements ne changent pas.
- **Échéance** : inchangée, elle reste une date. Le retard se calcule au jour près, et aucun rappel
  n'existe pour les prêts.
- **Validations** (par jour, comme avant) :
  - l'échéance doit tomber un jour après le début ;
  - un versement ne peut pas être antérieur au jour du début ;
  - un premier versement le jour même est placé juste après le décaissement ;
  - toute heure invalide (25:90…) est refusée.
- **Anciennes données** : une date à minuit local pile (00:00:00.000, ce qu'enregistrait le
  sélecteur de date) est traitée comme « sans heure ». Aucune heure n'est affichée, et rien n'est
  réécrit. Seule conséquence : un prêt volontairement saisi à 00:00 s'affiche sans heure.
- **Fuseau horaire** : même stratégie que `transactions.date`.
  - L'heure saisie est interprétée dans le fuseau **local** de l'appareil ou du navigateur, puis
    stockée comme instant absolu. PHP et MySQL ne font aucune conversion.
  - À l'affichage, l'instant est converti dans le fuseau local du lecteur : même heure entre
    appareils du même fuseau. Dans un autre fuseau, on voit l'heure locale correspondante (08:00 à
    Niamey = 09:00 à Paris), comme pour les transactions.
- **Tri** :
  - Web : « Plus récent / Plus ancien » trie déjà par `startDate`, donc l'heure est prise en compte.
  - Android : la liste est triée par échéance, ce qui est inchangé.

## Fichiers
- **Android** : `util/LoanDateTime.kt`, `LoanFormViewModel/Fragment`, `LoanDetailViewModel`,
  `LoanDetailFragment`, `LoanDetailAdapter`, `fragment_loan_form.xml`,
  `item_loan_detail_header.xml`, `menu/loan_detail_menu.xml`, textes FR/EN.
- **Web** (`arzikina-web-sync`) : `lib/loan-datetime.ts`, `components/loan-dialog.tsx`,
  `components/loan-date-time-dialog.tsx`, `routes/prets.$loanId.tsx`.
