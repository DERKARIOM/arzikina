package com.naniger.arzikina.domain.model

/**
 * Modèle de transaction réutilisable (cahier des charges "Marketplace personnelle" — voir
 * `presentation/utilities/marketplace/MarketplaceFragment`). PURE bibliothèque personnelle et
 * locale : ne représente jamais une transaction réelle, seulement un RACCOURCI vers la création
 * d'une transaction pré-remplie (voir `TransactionTemplateRepository`, action "Acheter").
 *
 * Volontairement plus simple que [Transaction]/[RecurringTransaction] : pas de
 * [TransactionType.TRANSFER] (un modèle représente une dépense ou un revenu habituel, jamais un
 * virement entre comptes propres — voir cahier des charges section 3, "Type : Dépense / Revenu"),
 * donc [categoryId] est TOUJOURS renseigné (contrairement à [Transaction.categoryId], nullable
 * uniquement pour ce cas de transfert qui n'existe pas ici).
 *
 * Ne stocke jamais [amount]/[categoryId]/[accountId] "au moment de l'achat" : utiliser un modèle
 * pour créer une transaction (voir `TransactionFormFragment`, arguments `preset*`) ne modifie
 * JAMAIS ce modèle (cahier des charges section 5, "Le modèle ne doit pas être modifié") — seule une
 * action EXPLICITE de modification (écran d'édition du modèle) appelle
 * `TransactionTemplateRepository.saveTemplate`.
 *
 * @param id 0L tant que le modèle n'a pas encore été enregistré en base.
 * @param name nom court affiché sur la Card (ex. "Déjeuner") — distinct de [description], qui
 * préremplit le champ description du formulaire de transaction.
 * @param categoryId détermine aussi l'icône/couleur affichée sur la Card (voir `Category.icon`) —
 * pas de champ icône séparé sur ce modèle : Room a déjà un convertisseur non-nullable pour
 * [CategoryIcon] (voir `Category.icon`), en ajouter un second nullable pour ce cas entrerait en
 * conflit de signature. Un champ `customIcon` optionnel pourra être ajouté plus tard par une
 * migration additive si le besoin se confirme, sans casser ce modèle.
 * @param isFavorite affiché en tête de liste (cahier des charges section 7, "Mes favoris").
 * @param defaultHour heure par défaut (0-23) appliquée au moment de l'achat (voir
 * `MarketplaceFragment.onBuyClicked`) : préremplit l'heure de la transaction créée à la place de
 * l'heure actuelle, sur la date DU JOUR (jamais une date figée). `null` = pas d'heure par défaut,
 * comportement inchangé (heure actuelle). Toujours `null` en même temps que [defaultMinute] — même
 * convention deux-`Int` que [com.naniger.arzikina.domain.model.RecurringTransaction.triggerHour], choisie
 * plutôt qu'un `LocalTime`/`Instant` car aucun `TypeConverter` n'existe pour ces types dans ce
 * projet (voir `Converters.kt`) ; ici nullable (contrairement à `RecurringTransaction`, où l'heure
 * est obligatoire) car cette fonctionnalité est explicitement OPTIONNELLE.
 * @param defaultMinute minute par défaut (0-59), voir [defaultHour].
 */
data class TransactionTemplate(
    val id: Long = 0L,
    val name: String,
    val type: TransactionType,
    val amount: Long,
    val categoryId: Long,
    val accountId: Long,
    val description: String = "",
    val isFavorite: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val defaultHour: Int? = null,
    val defaultMinute: Int? = null
)
