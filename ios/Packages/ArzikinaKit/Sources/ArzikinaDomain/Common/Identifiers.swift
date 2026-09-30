/// Identifiant d'une entité métier : l'UUID partagé par Android (`syncId`), le serveur (`id`) et le
/// Web. Contrairement à Android, qui garde en plus un `id` Long local pour Room (héritage d'avant la
/// synchronisation), iOS n'a aucun historique local : il utilise DIRECTEMENT l'identifiant global,
/// comme le Web. Toutes les références entre entités (`accountId`, `categoryId`…) sont donc des UUID,
/// exactement ceux qui circulent dans l'API (`accountSyncId`, `categorySyncId`…).
public typealias EntityID = String

/// Instant en millisecondes depuis l'epoch (UTC) — même représentation qu'Android (`Long`), que
/// l'API et MySQL. Les conversions en jour calendaire passent toujours par un `Calendar` explicite
/// (voir `ArzikinaCalendar`), jamais par l'horloge ou le fuseau implicites.
public typealias EpochMillis = Int64

/// Montant en unité MINEURE (voir `Money.minorUnitsPerMajor`), comme partout dans Arzikina.
public typealias MinorUnits = Int64
