import GRDB

/// Conversion générique entre une ligne de la base locale et le JSON de l'API, pilotée par
/// `SyncEntitySchema` (aucun code spécifique à une entité).
enum SyncRowCodec {

    // MARK: - Local → API (push)

    /// Charge l'entité [id] et la met au format attendu par `push.php` pour [operation].
    /// `nil` si la ligne n'existe plus localement.
    static func payload(
        _ db: Database,
        schema: SyncEntitySchema,
        id: String,
        operation: SyncOperation
    ) throws -> [String: JSONValue]? {
        guard let row = try Row.fetchOne(db, sql: "SELECT * FROM \(schema.table) WHERE id = ?", arguments: [id]) else {
            return nil
        }
        var entity: [String: JSONValue] = [
            "id": .string(id),
            // Version serveur sur laquelle repose la modification : sert au serveur à détecter
            // un conflit (quelqu'un d'autre a modifié l'entité entre-temps).
            "baseVersion": .int(row["version"] ?? 0)
        ]
        // `push.php` n'utilise aucun autre champ pour une suppression (douce).
        guard operation != .delete else { return entity }

        entity["createdAt"] = .int(row["createdAt"] ?? 0)
        for field in schema.fields {
            let value = encode(row[field.local] as DatabaseValue, kind: field.kind)
            if field.sentOnlyWhenSet, value.isNull || value.int64Value == 0 { continue }
            entity[field.payload] = value
        }
        return entity
    }

    /// Les champs facultatifs vides sont envoyés EXPLICITEMENT à `null` : pour `push.php`, un
    /// champ absent signifie « conserver la valeur du serveur », un `null` signifie « effacer ».
    private static func encode(_ value: DatabaseValue, kind: SyncEntitySchema.Kind) -> JSONValue {
        switch value.storage {
        case .null:
            return .null
        case .int64(let number):
            return kind == .real ? .double(Double(number)) : .int(number)
        case .double(let number):
            return kind == .real ? .double(number) : .int(Int64(number))
        case .string(let text):
            return .string(text)
        case .blob:
            return .null
        }
    }

    // MARK: - API → local (pull et réponses de push)

    /// Ligne locale (colonnes → valeurs) décrite par une entité du serveur ; `nil` si
    /// l'entité est inexploitable (identifiant ou horodatages manquants).
    static func localValues(from entity: [String: JSONValue], schema: SyncEntitySchema) -> (id: String, values: [String: DatabaseValue])? {
        guard let id = entity["id"]?.stringValue, !id.isEmpty,
              let updatedAt = entity["updatedAt"]?.int64Value
        else { return nil }

        var values: [String: DatabaseValue] = [
            "id": id.databaseValue,
            "createdAt": (entity["createdAt"]?.int64Value ?? updatedAt).databaseValue,
            "updatedAt": updatedAt.databaseValue,
            "deletedAt": (entity["deletedAt"]?.int64Value).map(\.databaseValue) ?? .null,
            "version": (entity["version"]?.int64Value ?? 0).databaseValue
        ]
        for field in schema.fields {
            values[field.local] = decode(entity[field.payload], field: field)
        }
        return (id, values)
    }

    /// Valeur locale d'un champ. Un champ obligatoire absent ou illisible reçoit la même valeur
    /// par défaut que celle qu'appliquerait `push.php` (`''`, `0`) : la ligne reste utilisable.
    private static func decode(_ value: JSONValue?, field: SyncEntitySchema.Field) -> DatabaseValue {
        let decoded: DatabaseValue? = {
            guard let value, !value.isNull else { return nil }
            switch field.kind {
            case .text: return value.stringValue?.databaseValue
            case .integer: return value.int64Value?.databaseValue
            case .bool: return value.int64Value.map { ($0 != 0).databaseValue }
            case .real: return value.doubleValue?.databaseValue
            }
        }()
        if let decoded { return decoded }
        if field.nullable { return .null }
        switch field.kind {
        case .text: return "".databaseValue
        case .integer: return Int64(0).databaseValue
        case .bool: return false.databaseValue
        case .real: return Double(0).databaseValue
        }
    }

    /// Insère ou remplace la ligne [values] dans la table de [schema].
    static func upsert(_ db: Database, schema: SyncEntitySchema, values: [String: DatabaseValue]) throws {
        let columns = Array(values.keys).sorted()
        let placeholders = Array(repeating: "?", count: columns.count).joined(separator: ", ")
        let assignments = columns.filter { $0 != "id" }.map { "\($0) = excluded.\($0)" }.joined(separator: ", ")
        try db.execute(
            sql: """
            INSERT INTO \(schema.table) (\(columns.joined(separator: ", "))) VALUES (\(placeholders))
            ON CONFLICT(id) DO UPDATE SET \(assignments)
            """,
            arguments: StatementArguments(columns.map { (values[$0] ?? .null) as (any DatabaseValueConvertible)? })
        )
    }

    /// Met à jour la seule version serveur connue d'une entité (sans toucher à son contenu).
    static func setVersion(_ db: Database, schema: SyncEntitySchema, id: String, version: Int64) throws {
        try db.execute(sql: "UPDATE \(schema.table) SET version = ? WHERE id = ?", arguments: [version, id])
    }
}
